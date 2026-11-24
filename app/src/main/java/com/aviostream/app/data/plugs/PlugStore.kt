package com.aviostream.app.data.plugs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.security.MessageDigest

/** A plug as installed on this device. */
data class StoredPlug(
    val repoUrl: String,
    val repoName: String,
    val entry: PlugRepoEntry,
    /** Plug body file, relative to the plugs files dir. */
    val file: String,
    /** Absolute URL the body was fetched from (resolved against the repo). */
    val entryUrl: String,
    val enabled: Boolean
) {
    /** Identity for per-plug runtime state (issues, last counts). */
    val key: String get() = "$repoUrl|${entry.id}"
}

data class StoredRepo(
    val url: String,
    val name: String,
    val version: String?,
    val bundled: Boolean,
    val plugs: List<StoredPlug>,
    /**
     * The repo's own switch (design B, v0.14.60): off means none
     * of its plugs run, whatever their own switches say. Persisted;
     * state files written before it existed read as on.
     */
    val enabled: Boolean = true
)

data class PlugStoreState(
    val repos: List<StoredRepo> = emptyList(),
    /**
     * Legacy seed marker from the v0.14.56–.65 Avio Plugs shape.
     * Kept so old state files round-trip unchanged; it drives
     * nothing. PlugX is the only preinstalled repo now — see
     * [plugxSeeded].
     */
    val avioPlugsSeeded: Boolean = false,
    /**
     * True once the PlugX repo has been seeded (v0.14.66).
     * Persisted so a user-removed PlugX repo stays removed —
     * seeding never runs twice.
     */
    val plugxSeeded: Boolean = false,
    /**
     * The master plugs switch beside the page title (design B,
     * v0.14.60): off means no plug runs at all. Persisted; state
     * files written before it existed read as on.
     */
    val plugsEnabled: Boolean = true
) {
    val plugs: List<StoredPlug> get() = repos.flatMap { it.plugs }
}

/** One plug whose body could not be downloaded in an add/refresh. */
data class FailedPlug(val id: String, val name: String, val reason: String)

/**
 * What an add/refresh actually did (v0.14.56): partial installs are
 * a normal outcome, never a vanished repo — [installedCount] of
 * [totalCount] plugs landed, [failed] names the rest.
 */
data class RepoInstallResult(
    val installedCount: Int,
    val totalCount: Int,
    val failed: List<FailedPlug> = emptyList()
)

/**
 * The download half of repo add/refresh (v0.14.56), kept free of
 * Android types so it is unit-testable with a stubbed fetch:
 *  - ONE automatic retry per fetch (manifest callers use
 *    [fetchWithRetry] too) — a single retry on error or non-200.
 *  - Per-plug isolation: a plug whose body fetch fails is SKIPPED
 *    and recorded in the failed list; it never aborts the repo.
 *  - A plug that was already installed keeps its old body, entry
 *    and switch when its re-download fails — a failed refresh
 *    never uninstalls anything.
 *  - Plugs absent from [existing] (new, or skipped last time) are
 *    fetched fresh, so a later refresh retries the missing ones.
 */
internal object PlugDownloader {

    suspend fun fetchWithRetry(
        fetch: suspend (String) -> PlugHttpResponse,
        url: String
    ): PlugHttpResponse {
        val first = runCatching { fetch(url) }.getOrNull()
        if (first != null && first.ok) return first
        return fetch(url)
    }

    suspend fun downloadPlugs(
        repoUrl: String,
        repoName: String,
        entries: List<PlugRepoEntry>,
        existing: StoredRepo?,
        fetch: suspend (String) -> PlugHttpResponse,
        writeBody: suspend (PlugRepoEntry, String) -> String,
        bodyExists: (StoredPlug) -> Boolean
    ): Pair<List<StoredPlug>, List<FailedPlug>> {
        val plugs = ArrayList<StoredPlug>(entries.size)
        val failed = ArrayList<FailedPlug>()
        for (entry in entries) {
            val old = existing?.plugs?.firstOrNull { it.entry.id == entry.id }
            val entryUrl = PlugManifestParser.resolveEntryUrl(repoUrl, entry.filename)
            val needsBody = old == null || old.entry.version != entry.version || !bodyExists(old)
            if (!needsBody && old != null) {
                plugs += StoredPlug(repoUrl, repoName, entry, old.file, entryUrl, old.enabled)
                continue
            }
            val resp = runCatching { fetchWithRetry(fetch, entryUrl) }.getOrNull()
            if (resp == null || !resp.ok) {
                failed += FailedPlug(
                    entry.id, entry.name,
                    resp?.let { "HTTP ${it.status}" } ?: "network error"
                )
                if (old != null && bodyExists(old)) {
                    plugs += StoredPlug(repoUrl, repoName, old.entry, old.file, entryUrl, old.enabled)
                }
                continue
            }
            val fileName = writeBody(entry, resp.body)
            plugs += StoredPlug(
                repoUrl, repoName, entry, fileName, entryUrl,
                // Owner's ruling 2026-11-21: a plug with NO saved
                // switch starts OFF — the manifest's own default
                // never turns a plug on. A saved switch (old != null)
                // is kept exactly, above and here.
                old?.enabled ?: false
            )
        }
        return plugs to failed
    }
}

/** What the PlugX seed step should do with a loaded state. */
internal enum class PlugXSeedAction { NONE, MARK_ONLY, SEED }

/**
 * Seed planning (v0.14.66, owner's order: ONLY PlugX ships
 * preinstalled), pure so it is unit-testable: once the seeded
 * marker is set, nothing ever runs again (a removed PlugX stays
 * removed). A state that already holds the PlugX repo (added by
 * hand) only takes the marker; anything else seeds.
 */
internal fun planPlugXSeed(state: PlugStoreState): PlugXSeedAction = when {
    state.plugxSeeded -> PlugXSeedAction.NONE
    state.repos.any { it.url == PlugStore.PLUGX_REPO_URL } -> PlugXSeedAction.MARK_ONLY
    else -> PlugXSeedAction.SEED
}

/**
 * The seeded state (v0.14.66), pure so it is unit-testable: the
 * PlugX repo is appended as a bare, normal remote-repo entry —
 * no plugs yet; the hub's open-time refresh (the normal sync
 * path) fetches its manifest and downloads its plugs, and from
 * there refresh owns it like any user-added repo. Every other
 * repo is carried over untouched (user-added repos are user
 * data). No legacy bundled repo is dropped here: the fork never
 * shipped one, so no state can carry it.
 */
internal fun applyPlugXSeed(state: PlugStoreState): PlugStoreState {
    val repos = state.repos +
        StoredRepo(
            url = PlugStore.PLUGX_REPO_URL,
            name = PlugStore.PLUGX_REPO_NAME,
            version = null,
            bundled = false,
            plugs = emptyList()
        )
    return state.copy(repos = repos, plugxSeeded = true)
}

/**
 * Plug persistence (Plugs v1): one JSON state file + one body file
 * per plug under filesDir/plugs. Repos are Nuvio-format manifests
 * (see [PlugManifestParser]); adding one downloads every listed plug
 * body immediately, and a manifest version bump on refresh
 * re-downloads changed plugs in the background. Exactly ONE repo
 * ships preinstalled (owner's order 2026-11-21): PlugX, seeded
 * once as a bare normal remote-repo entry, then owned by the
 * normal refresh path, and removable like any other repo.
 */
class PlugStore(context: Context) {
    private val appContext = context.applicationContext
    private val rootDir = File(appContext.filesDir, "plugs")
    private val filesDir = File(rootDir, "files")
    private val stateFile = File(rootDir, "state.json")
    private val mutex = Mutex()

    private val _state = MutableStateFlow(PlugStoreState())
    val state: StateFlow<PlugStoreState> = _state

    @Volatile private var loaded = false
    @Volatile private var seedChecked = false

    suspend fun ensureLoaded() {
        if (loaded) return
        mutex.withLock {
            if (loaded) return
            _state.value = readStateFile()
            loaded = true
        }
        ensureSeeded()
    }

    /**
     * One-time PlugX seed (v0.14.66): inserts the bare PlugX repo
     * entry (see [applyPlugXSeed]) — its plugs arrive through the
     * normal refresh path when the hub is opened. Runs only while
     * the persisted seeded marker is unset — a repo the user
     * removed is never resurrected.
     */
    suspend fun ensureSeeded() {
        if (seedChecked) return
        mutex.withLock {
            if (seedChecked) return
            seedChecked = true
            runCatching { seedPlugXLocked() }
        }
    }

    private suspend fun seedPlugXLocked() {
        when (planPlugXSeed(_state.value)) {
            PlugXSeedAction.NONE -> Unit
            PlugXSeedAction.MARK_ONLY ->
                persistLocked(_state.value.copy(plugxSeeded = true))
            PlugXSeedAction.SEED ->
                persistLocked(applyPlugXSeed(_state.value))
        }
    }

    /**
     * Adds a repo by its manifest URL: fetches + parses the manifest
     * (one automatic retry), downloads every plug body with per-plug
     * isolation, persists. Fails only when the manifest itself fails
     * or ZERO plugs load; a partial install is a success carrying
     * the failed list (v0.14.56 — one dead plug never vanishes a
     * whole repo again). New plugs start OFF (owner's ruling
     * 2026-11-21); the user turns on the ones they want.
     */
    suspend fun addRepo(manifestUrl: String): Result<RepoInstallResult> {
        val url = manifestUrl.trim()
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Paste a valid http(s) repo link"))
        }
        return runCatching {
            val manifestResp = PlugDownloader.fetchWithRetry({ PlugHttp.fetch(it) }, url)
            if (!manifestResp.ok) throw IllegalStateException("Repo link answered HTTP ${manifestResp.status}")
            val manifest = PlugManifestParser.parse(manifestResp.body)
            val (plugs, failed) = downloadAll(url, manifest, existing = null)
            if (plugs.isEmpty()) throw IllegalStateException(noPlugsMessage(failed))
            mutex.withLock {
                val repos = _state.value.repos.filter { it.url != url } +
                    StoredRepo(url, manifest.name, manifest.version, bundled = false, plugs = plugs)
                persistLocked(_state.value.copy(repos = repos))
            }
            RepoInstallResult(plugs.size, manifest.plugs.size, failed)
        }
    }

    /**
     * Re-fetches a repo's manifest; plugs whose listed version moved
     * (or whose body file vanished, or which a previous add/refresh
     * skipped) are re-downloaded with the same retry + isolation as
     * [addRepo]. New plugs arrive OFF (owner's ruling 2026-11-21 —
     * no saved switch, no run); removed plugs are dropped; the
     * user's per-plug switches survive — including for plugs whose
     * re-download just failed (their old body and switch are kept).
     */
    suspend fun refreshRepo(repoUrl: String): Result<RepoInstallResult> {
        return runCatching {
            val manifestResp = PlugDownloader.fetchWithRetry({ PlugHttp.fetch(it) }, repoUrl)
            if (!manifestResp.ok) throw IllegalStateException("Repo answered HTTP ${manifestResp.status}")
            val manifest = PlugManifestParser.parse(manifestResp.body)
            val existing = _state.value.repos.firstOrNull { it.url == repoUrl }
            val (plugs, failed) = downloadAll(repoUrl, manifest, existing)
            if (plugs.isEmpty() && manifest.plugs.isNotEmpty()) {
                throw IllegalStateException(noPlugsMessage(failed))
            }
            mutex.withLock {
                val repos = _state.value.repos.filter { it.url != repoUrl } +
                    StoredRepo(
                        repoUrl, manifest.name, manifest.version, bundled = false,
                        plugs = plugs, enabled = existing?.enabled ?: true
                    )
                persistLocked(_state.value.copy(repos = repos))
            }
            RepoInstallResult(plugs.size, manifest.plugs.size, failed)
        }
    }

    private suspend fun downloadAll(
        repoUrl: String,
        manifest: PlugRepoManifest,
        existing: StoredRepo?
    ): Pair<List<StoredPlug>, List<FailedPlug>> =
        PlugDownloader.downloadPlugs(
            repoUrl = repoUrl,
            repoName = manifest.name,
            entries = manifest.plugs,
            existing = existing,
            fetch = { PlugHttp.fetch(it) },
            writeBody = { entry, body ->
                mutex.withLock { writeBodyLocked(repoUrl, entry, body) }
            },
            bodyExists = { File(filesDir, it.file).exists() }
        )

    private fun noPlugsMessage(failed: List<FailedPlug>): String {
        val first = failed.firstOrNull()
        return "No plugs could be downloaded" +
            (first?.let { " — ${it.name}: ${it.reason}" } ?: "") +
            ". Check the connection and try again."
    }

    suspend fun removeRepo(repoUrl: String) {
        mutex.withLock {
            val repos = _state.value.repos.filter { it.url != repoUrl }
            persistLocked(_state.value.copy(repos = repos))
        }
    }

    suspend fun setEnabled(repoUrl: String, plugId: String, enabled: Boolean) {
        mutex.withLock {
            val repos = _state.value.repos.map { repo ->
                if (repo.url != repoUrl) repo
                else repo.copy(plugs = repo.plugs.map { plug ->
                    if (plug.entry.id == plugId) plug.copy(enabled = enabled) else plug
                })
            }
            persistLocked(_state.value.copy(repos = repos))
        }
    }

    /**
     * Every plug in one repo on or off in a single write (v0.14.63,
     * item H): same persisted state the per-plug switches use — the
     * repo's own switch is NOT touched; this only flips each plug's
     * own flag, so "all off, then enable the few wanted" works.
     */
    suspend fun setAllPlugsEnabled(repoUrl: String, enabled: Boolean) {
        mutex.withLock {
            val repos = _state.value.repos.map { repo ->
                if (repo.url != repoUrl) repo
                else repo.copy(plugs = repo.plugs.map { plug -> plug.copy(enabled = enabled) })
            }
            persistLocked(_state.value.copy(repos = repos))
        }
    }

    /** The repo's own switch (design B): off silences its plugs. */
    suspend fun setRepoEnabled(repoUrl: String, enabled: Boolean) {
        mutex.withLock {
            val repos = _state.value.repos.map { repo ->
                if (repo.url != repoUrl) repo else repo.copy(enabled = enabled)
            }
            persistLocked(_state.value.copy(repos = repos))
        }
    }

    /** The master plugs switch (design B): off silences all plugs. */
    suspend fun setMasterEnabled(enabled: Boolean) {
        mutex.withLock {
            persistLocked(_state.value.copy(plugsEnabled = enabled))
        }
    }

    /** Reads a plug's stored body (null when the file is gone). */
    fun readBody(plug: StoredPlug): String? {
        val f = File(filesDir, plug.file)
        return if (f.exists()) runCatching { f.readText() }.getOrNull() else null
    }

    // ---- persistence -------------------------------------------------

    private fun writeBodyLocked(repoUrl: String, entry: PlugRepoEntry, body: String): String {
        filesDir.mkdirs()
        val repoHash = sha1(repoUrl).take(10)
        val dir = File(filesDir, repoHash).apply { mkdirs() }
        val ext = if (entry.kind == PlugKind.RULE) "json" else "js"
        val name = "${entry.id.replace(Regex("[^A-Za-z0-9_-]"), "_")}.$ext"
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(target)) {
            target.writeText(body)
            tmp.delete()
        }
        return "$repoHash/$name"
    }

    private fun persistLocked(state: PlugStoreState) {
        _state.value = state
        rootDir.mkdirs()
        val tmp = File(rootDir, "state.json.tmp")
        tmp.writeText(serializeState(state))
        if (!tmp.renameTo(stateFile)) {
            stateFile.writeText(serializeState(state))
            tmp.delete()
        }
    }

    private fun readStateFile(): PlugStoreState {
        if (!stateFile.exists()) return PlugStoreState()
        val text = runCatching { stateFile.readText() }.getOrNull() ?: return PlugStoreState()
        val root = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return PlugStoreState()
        val repos = (root["repos"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            val plugs = (o["plugs"] as? JsonArray)?.mapNotNull { pel ->
                val p = pel as? JsonObject ?: return@mapNotNull null
                val entryObj = p["entry"] as? JsonObject ?: return@mapNotNull null
                val entry = PlugRepoEntry(
                    id = entryObj.str("id") ?: return@mapNotNull null,
                    name = entryObj.str("name") ?: entryObj.str("id")!!,
                    description = entryObj.str("description"),
                    version = entryObj.str("version"),
                    author = entryObj.str("author"),
                    kind = if (entryObj.str("kind") == "rule") PlugKind.RULE else PlugKind.JS,
                    filename = entryObj.str("filename") ?: return@mapNotNull null,
                    supportedTypes = entryObj.strList("supportedTypes").ifEmpty { listOf("movie", "tv") },
                    logo = entryObj.str("logo"),
                    enabledByDefault = entryObj.bool("enabledByDefault") ?: true
                )
                StoredPlug(
                    repoUrl = url,
                    repoName = o.str("name") ?: url,
                    entry = entry,
                    file = p.str("file") ?: return@mapNotNull null,
                    entryUrl = p.str("entryUrl") ?: "",
                    // No persisted switch = no saved user state =
                    // OFF (owner's ruling 2026-11-21). The writer
                    // always persists the switch, so this fallback
                    // only meets hand-damaged state files.
                    enabled = p.bool("enabled") ?: false
                )
            } ?: emptyList()
            StoredRepo(
                url = url,
                name = o.str("name") ?: url,
                version = o.str("version"),
                bundled = o.bool("bundled") ?: false,
                plugs = plugs,
                enabled = o.bool("enabled") ?: true
            )
        } ?: emptyList()
        return PlugStoreState(
            repos = repos,
            avioPlugsSeeded = root.bool("avioPlugsSeeded") ?: false,
            plugxSeeded = root.bool("plugxSeeded") ?: false,
            plugsEnabled = root.bool("plugsEnabled") ?: true
        )
    }

    private fun serializeState(state: PlugStoreState): String {
        val reposJson = buildJsonArray {
            for (repo in state.repos) {
                add(buildJsonObject {
                    put("url", repo.url)
                    put("name", repo.name)
                    repo.version?.let { put("version", it) }
                    put("bundled", repo.bundled)
                    put("enabled", repo.enabled)
                    put("plugs", buildJsonArray {
                        for (plug in repo.plugs) {
                            add(buildJsonObject {
                                put("file", plug.file)
                                put("entryUrl", plug.entryUrl)
                                put("enabled", plug.enabled)
                                put("entry", buildJsonObject {
                                    put("id", plug.entry.id)
                                    put("name", plug.entry.name)
                                    plug.entry.description?.let { put("description", it) }
                                    plug.entry.version?.let { put("version", it) }
                                    plug.entry.author?.let { put("author", it) }
                                    put("kind", if (plug.entry.kind == PlugKind.RULE) "rule" else "js")
                                    put("filename", plug.entry.filename)
                                    put("supportedTypes", buildJsonArray {
                                        plug.entry.supportedTypes.forEach { add(JsonPrimitive(it)) }
                                    })
                                    plug.entry.logo?.let { put("logo", it) }
                                    put("enabledByDefault", plug.entry.enabledByDefault)
                                })
                            })
                        }
                    })
                })
            }
        }
        return buildJsonObject {
            put("repos", reposJson)
            put("avioPlugsSeeded", state.avioPlugsSeeded)
            put("plugxSeeded", state.plugxSeeded)
            put("plugsEnabled", state.plugsEnabled)
        }.toString()
    }

    private fun sha1(text: String): String =
        MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNullCompat()

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNullCompat()?.let {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull {
            (it as? JsonPrimitive)?.contentOrNullCompat()
        } ?: emptyList()

    private fun JsonPrimitive.contentOrNullCompat(): String? =
        runCatching { content }.getOrNull()?.takeIf { it.isNotBlank() }

    companion object {
        /** PlugX: the ONE repo that ships preinstalled (owner's
         *  order 2026-11-21) — a normal remote repo, seeded bare
         *  and synced by the ordinary refresh path. */
        const val PLUGX_REPO_URL =
            "https://raw.githubusercontent.com/kotlinsyd/PlugX/main/manifest.json"

        /** Display name for the seeded entry, before its first
         *  manifest sync replaces it with the manifest's own name. */
        const val PLUGX_REPO_NAME = "PlugX"
    }
}
