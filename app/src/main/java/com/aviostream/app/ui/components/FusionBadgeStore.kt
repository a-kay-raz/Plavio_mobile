package com.aviostream.app.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import com.aviostream.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Fusion style badges (v0.14.30 port, feature #15): the ONE home
 * for the user's own formatter/badge sources.
 *
 * Up to [FusionBadgeSources.MAX_SOURCES] sources, each either a
 * badge-pack LINK or a PASTED Fusion-style JSON (the filters[] /
 * Xperience shape the shipped import already parses — see
 * [collectPackEntries]). Each source installs its badge artwork
 * into its own directory; rendering tries the sources in list
 * order, then the house chip (priority: a rule badge with the same
 * name wins; then the user's Fusion sources; a fact no source
 * carries simply shows no Fusion chip — the fork has no built-in
 * Elite pack to fall back to).
 *
 * Fork adaptations vs upstream 0.14.30:
 *  - org.json instead of kotlinx-serialization (Avio does not apply
 *    the serialization compiler plugin — the same rule as the fork's
 *    StreamBadges.kt). The stored shape is unchanged: a JSON array
 *    of flat objects.
 *  - No LEGACY migration: upstream migrates its v0.14.21 single
 *    imported pack into source #1 — the fork never shipped that
 *    pack, so there is nothing to migrate. FusionSourceKind.LEGACY
 *    still decodes (a stored list naming one would round-trip), but
 *    no code in the fork ever creates one.
 *  - No Elite fallback in the intro/rendering: the fork has no
 *    bundled Elite artwork (assets/badges/elite does not exist
 *    here) and its badge system is the Nuvio rule system, not the
 *    upstream fact-pack renderer — Fusion badges ride on the
 *    fork's StreamBadge chips instead (see FusionBadges.kt).
 */

enum class FusionSourceKind { URL, PASTE, LEGACY }

data class FusionBadgeSource(
    val id: String,
    val kind: FusionSourceKind,
    /** Shown on the source's card: the pack's declared name, else the link's host, else "Pasted JSON". */
    val label: String,
    /** The source link for URL sources; null for pasted / legacy sources. */
    val locator: String? = null,
    /** Canonical badge keys this source carries, as installed. */
    val badgeKeys: List<String> = emptyList(),
    /** Group count as parsed: declared groups when the pack declares them, else distinct key families. */
    val groupCount: Int = 0,
    val addedAt: Long = 0L
) {
    val badgeCount: Int get() = badgeKeys.size
}

/** Pure list + shape rules for Fusion sources — unit-tested directly. */
object FusionBadgeSources {
    const val MAX_SOURCES = 3
    const val LEGACY_SOURCE_ID = "legacy"

    /**
     * The stored form: a JSON array of flat objects. Written and
     * read by hand over org.json — this tree deliberately has no
     * serialization compiler plugin (see StremioParser's doc), so
     * no @Serializable anywhere.
     */
    fun encode(sources: List<FusionBadgeSource>): String {
        val arr = JSONArray()
        sources.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("kind", s.kind.name)
            obj.put("label", s.label)
            s.locator?.let { obj.put("locator", it) }
            val keys = JSONArray()
            s.badgeKeys.forEach { keys.put(it) }
            obj.put("badgeKeys", keys)
            obj.put("groupCount", s.groupCount)
            obj.put("addedAt", s.addedAt)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<FusionBadgeSource> {
        val text = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
        val arr = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val kind = runCatching { FusionSourceKind.valueOf(obj.optString("kind")) }.getOrNull()
                ?: return@mapNotNull null
            val keysArr = obj.optJSONArray("badgeKeys")
            val keys = if (keysArr != null) {
                (0 until keysArr.length()).mapNotNull { keysArr.optString(it).takeIf { s -> s.isNotBlank() } }
            } else {
                emptyList()
            }
            FusionBadgeSource(
                id = id,
                kind = kind,
                label = obj.optString("label"),
                locator = obj.optString("locator").takeIf { it.isNotBlank() },
                badgeKeys = keys,
                groupCount = obj.optInt("groupCount", 0),
                addedAt = obj.optLong("addedAt", 0L)
            )
        }
    }

    /** Adds a source; null when the cap is hit or the id is taken. */
    fun add(sources: List<FusionBadgeSource>, source: FusionBadgeSource): List<FusionBadgeSource>? {
        if (sources.size >= MAX_SOURCES) return null
        if (sources.any { it.id == source.id }) return null
        return sources + source
    }

    fun remove(sources: List<FusionBadgeSource>, id: String): List<FusionBadgeSource> =
        sources.filterNot { it.id == id }

    /** Replaces a source in place, keeping its position in the priority order. */
    fun replace(sources: List<FusionBadgeSource>, source: FusionBadgeSource): List<FusionBadgeSource> =
        sources.map { if (it.id == source.id) source else it }

    /**
     * Which source dresses each badge key: the FIRST source in list
     * order that carries the key owns it; later sources only fill
     * keys the earlier ones lack.
     */
    fun keyOwners(sources: List<FusionBadgeSource>): Map<String, Int> {
        val out = LinkedHashMap<String, Int>()
        sources.forEachIndexed { index, source ->
            source.badgeKeys.forEach { key -> if (key !in out) out[key] = index }
        }
        return out
    }

    /**
     * Group count for a source: the pack's declared group count
     * when it declares groups, else the number of distinct badge
     * families among the installed keys (res / src / codec / hdr /
     * audio / ch / lang / tag; atmos and remux are their own).
     */
    fun groupCountFor(declaredGroupCount: Int?, badgeKeys: Collection<String>): Int {
        if (declaredGroupCount != null && declaredGroupCount > 0) return declaredGroupCount
        return badgeKeys.map { key ->
            if (key == "atmos" || key == "remux") key else key.substringBefore('-')
        }.distinct().size
    }
}

/** Outcome of parsing a pasted Fusion pack JSON, before any download happens. */
sealed interface FusionPackParse {
    data class Valid(
        val packName: String?,
        val entries: Map<String, String>,
        val declaredGroupCount: Int?
    ) : FusionPackParse

    data class Invalid(val reason: String) : FusionPackParse
}

/** Mirrors BadgePackImport.collectFromJsonObject: name → image URL pairs, one walk into nested objects. */
private fun collectDirectEntries(obj: JSONObject, into: MutableMap<String, String>) {
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val value = obj.opt(key)) {
            is String -> if (value.startsWith("http")) into[key] = value
            is JSONObject -> collectDirectEntries(value, into)
        }
    }
}

/**
 * Parses a PASTED Fusion-style badge JSON (the filters[] shape, or
 * a direct name → image-URL map). Pure (org.json), so the paste
 * path is unit-tested. A pack is valid only when it names at least
 * one badge streams actually use — garbage is rejected here, before
 * anything touches disk, so a bad paste can never half-apply or
 * disturb the sources already installed.
 */
fun parseFusionPackJson(text: String): FusionPackParse {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return FusionPackParse.Invalid("Paste the pack JSON first")
    val root = runCatching { JSONObject(trimmed) }.getOrNull()
        ?: return FusionPackParse.Invalid("That doesn't look like valid JSON")
    val entries = LinkedHashMap<String, String>()
    var declaredGroups: Int? = null
    val filters = root.optJSONArray("filters")
    if (filters != null) {
        val groupIds = mutableSetOf<String>()
        for (i in 0 until filters.length()) {
            val f = filters.optJSONObject(i) ?: continue
            val name = f.optString("name").takeIf { it.isNotBlank() } ?: continue
            val url = f.optString("imageURL")
                .ifBlank { f.optString("imageUrl") }
                .ifBlank { f.optString("url") }
            if (url.startsWith("http")) entries[name] = url
            f.optString("groupId").takeIf { it.isNotBlank() }?.let { groupIds += it }
        }
        val groups = root.optJSONArray("groups")
        declaredGroups = when {
            groups != null && groups.length() > 0 -> groups.length()
            groupIds.isNotEmpty() -> groupIds.size
            else -> null
        }
    }
    collectDirectEntries(root, entries)
    if (entries.isEmpty()) return FusionPackParse.Invalid("No badges found in that JSON")
    if (entries.keys.none { canonicalBadgeKey(it) != null }) {
        return FusionPackParse.Invalid("None of those badges match the badges streams use")
    }
    return FusionPackParse.Valid(root.optString("name").takeIf { it.isNotBlank() }, entries, declaredGroups)
}

/** Short printed label for a canonical badge key — the house chips' text. */
fun badgeKeyLabel(key: String): String = when (key) {
    "res-2160p" -> "2160p"
    "res-1440p" -> "1440p"
    "res-1080p" -> "1080p"
    "res-720p" -> "720p"
    "res-480p" -> "480p"
    "res-360p" -> "360p"
    "tag-4kuhd" -> "4K UHD"
    "src-bluray" -> "BluRay"
    "src-webdl" -> "WEB-DL"
    "src-webrip" -> "WEBRip"
    "src-hdrip" -> "HDRip"
    "src-dvdrip" -> "DVDRip"
    "src-hdtv" -> "HDTV"
    "src-cam" -> "CAM"
    "remux" -> "REMUX"
    "codec-hevc" -> "HEVC"
    "codec-avc" -> "AVC"
    "codec-av1" -> "AV1"
    "bit-10" -> "10bit"
    "hdr-dv" -> "DV"
    "hdr-hdr10plus" -> "HDR10+"
    "hdr-hdr10" -> "HDR10"
    "hdr-hdr" -> "HDR"
    "audio-truehd" -> "TrueHD"
    "audio-dtshd" -> "DTS-HD"
    "audio-dts" -> "DTS"
    "audio-ddp" -> "DDP"
    "audio-dd" -> "DD"
    "audio-aac" -> "AAC"
    "audio-flac" -> "FLAC"
    "audio-opus" -> "Opus"
    "atmos" -> "Atmos"
    "ch-20" -> "2.0"
    "ch-51" -> "5.1"
    "ch-61" -> "6.1"
    "ch-71" -> "7.1"
    else -> if (key.startsWith("lang-")) key.removePrefix("lang-").uppercase()
    else key.uppercase()
}

// ---------------------------------------------------------------------------
// Storage: prefs hold the source list; each source owns a directory.
// ---------------------------------------------------------------------------

/** Artwork root for Fusion sources. */
fun fusionRootDir(context: Context): File = File(context.filesDir, "badges/fusion")

fun fusionSourceDir(context: Context, sourceId: String): File =
    File(fusionRootDir(context), sourceId)

fun loadFusionSources(settings: SettingsStore): List<FusionBadgeSource> =
    FusionBadgeSources.decode(settings.fusionBadgeSourcesJson)

fun saveFusionSources(settings: SettingsStore, sources: List<FusionBadgeSource>) {
    settings.fusionBadgeSourcesJson =
        if (sources.isEmpty()) null else FusionBadgeSources.encode(sources)
}

/**
 * The source ids the badge renderer should dress from, in priority
 * order — prefs only, no Context.
 */
fun SettingsStore.fusionSourceIds(): List<String> =
    loadFusionSources(this).map { it.id }

/** Downloads + validates badge artwork into [targetDir], swapping atomically (the shipped import's discipline). */
private suspend fun installFusionEntries(
    targetDir: File,
    entries: Map<String, String>
): List<String> = withContext(Dispatchers.IO) {
    val staging = File(targetDir.parentFile, targetDir.name + "-staging")
    staging.deleteRecursively()
    staging.mkdirs()
    val installed = mutableListOf<String>()
    for ((rawName, imageUrl) in entries) {
        if (installed.size >= 96) break
        val key = canonicalBadgeKey(rawName) ?: continue
        if (key in installed) continue
        val bytes = httpGetBytes(imageUrl) ?: continue
        if (bytes.isEmpty() || bytes.size > 2_000_000) continue
        // Only real, decodable images land — a broken "badge" must
        // not shadow the house fallback.
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) == null) continue
        File(staging, "$key.png").writeBytes(bytes)
        installed += key
    }
    if (installed.isEmpty()) {
        staging.deleteRecursively()
        throw IllegalStateException("No usable badges could be downloaded from that source")
    }
    targetDir.deleteRecursively()
    staging.renameTo(targetDir)
    installed
}

private class ParsedFusionBody(
    val entries: Map<String, String>,
    val packName: String?,
    val declaredGroupCount: Int?
)

/** Link bodies ride the paste parser first, then the shipped import's wider shapes (listings, pages). */
private fun parseFusionBody(body: String, baseUrl: String): ParsedFusionBody =
    when (val parsed = parseFusionPackJson(body)) {
        is FusionPackParse.Valid ->
            ParsedFusionBody(parsed.entries, parsed.packName, parsed.declaredGroupCount)
        is FusionPackParse.Invalid -> {
            val entries = collectPackEntries(body, baseUrl)
            if (entries.isEmpty()) throw IllegalStateException(parsed.reason)
            val name = runCatching {
                JSONObject(body.trim()).optString("name").ifBlank { null }
            }.getOrNull()
            ParsedFusionBody(entries, name, null)
        }
    }

private fun newFusionSourceId(): String =
    "src-" + java.util.UUID.randomUUID().toString().take(8)

private fun fusionCapMessage(): String =
    "You can keep up to ${FusionBadgeSources.MAX_SOURCES} Fusion sources — delete one first"

/** Adds a badge-pack LINK as a new Fusion source (re-adding the same link updates that source in place). */
suspend fun addFusionSourceFromUrl(
    context: Context,
    settings: SettingsStore,
    rawUrl: String
): Result<FusionBadgeSource> = runCatching {
    val url = rawUrl.trim()
    require(url.startsWith("https://") || url.startsWith("http://")) {
        "That link doesn't look right"
    }
    val sources = loadFusionSources(settings)
    sources.firstOrNull { it.kind == FusionSourceKind.URL && it.locator == url }?.let {
        return updateFusionSource(context, settings, it.id)
    }
    require(sources.size < FusionBadgeSources.MAX_SOURCES) { fusionCapMessage() }
    val body = httpGetBytes(url)?.toString(Charsets.UTF_8)
        ?: throw IllegalStateException("Couldn't open that link")
    val parsed = parseFusionBody(body, url)
    val id = newFusionSourceId()
    val keys = installFusionEntries(fusionSourceDir(context, id), parsed.entries)
    val source = FusionBadgeSource(
        id = id,
        kind = FusionSourceKind.URL,
        label = parsed.packName ?: url.toHttpUrlOrNull()?.host ?: "Link",
        locator = url,
        badgeKeys = keys,
        groupCount = FusionBadgeSources.groupCountFor(parsed.declaredGroupCount, keys),
        addedAt = System.currentTimeMillis()
    )
    saveFusionSources(settings, FusionBadgeSources.add(sources, source) ?: sources)
    source
}

/**
 * Adds a PASTED Fusion pack JSON as a new Fusion source. The JSON
 * is fully parsed and validated first — a bad paste is rejected
 * with a plain reason and nothing on disk changes.
 */
suspend fun addFusionSourceFromText(
    context: Context,
    settings: SettingsStore,
    text: String
): Result<FusionBadgeSource> = runCatching {
    val parsed = when (val p = parseFusionPackJson(text)) {
        is FusionPackParse.Valid -> p
        is FusionPackParse.Invalid -> throw IllegalStateException(p.reason)
    }
    val sources = loadFusionSources(settings)
    require(sources.size < FusionBadgeSources.MAX_SOURCES) { fusionCapMessage() }
    val id = newFusionSourceId()
    val keys = installFusionEntries(fusionSourceDir(context, id), parsed.entries)
    val source = FusionBadgeSource(
        id = id,
        kind = FusionSourceKind.PASTE,
        label = parsed.packName ?: "Pasted JSON",
        locator = null,
        badgeKeys = keys,
        groupCount = FusionBadgeSources.groupCountFor(parsed.declaredGroupCount, keys),
        addedAt = System.currentTimeMillis()
    )
    saveFusionSources(settings, FusionBadgeSources.add(sources, source) ?: sources)
    source
}

/** Re-fetches a link source and reinstalls its badges in place; a failed update keeps the old badges. */
suspend fun updateFusionSource(
    context: Context,
    settings: SettingsStore,
    id: String
): Result<FusionBadgeSource> = runCatching {
    val sources = loadFusionSources(settings)
    val current = sources.firstOrNull { it.id == id }
        ?: throw IllegalStateException("That source is gone")
    val url = current.locator?.takeIf { current.kind == FusionSourceKind.URL }
        ?: throw IllegalStateException("Only link sources can be updated")
    val body = httpGetBytes(url)?.toString(Charsets.UTF_8)
        ?: throw IllegalStateException("Couldn't open that link")
    val parsed = parseFusionBody(body, url)
    val keys = installFusionEntries(fusionSourceDir(context, id), parsed.entries)
    val updated = current.copy(
        label = parsed.packName ?: current.label,
        badgeKeys = keys,
        groupCount = FusionBadgeSources.groupCountFor(parsed.declaredGroupCount, keys)
    )
    saveFusionSources(settings, FusionBadgeSources.replace(sources, updated))
    updated
}

/** Deletes a source and its artwork; returns the remaining list. */
suspend fun deleteFusionSource(
    context: Context,
    settings: SettingsStore,
    id: String
): List<FusionBadgeSource> = withContext(Dispatchers.IO) {
    val sources = loadFusionSources(settings)
    val remaining = FusionBadgeSources.remove(sources, id)
    saveFusionSources(settings, remaining)
    runCatching { fusionSourceDir(context, id).deleteRecursively() }
    remaining
}
