package com.pulsestream.app.data.simkl

import android.util.Log
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.WatchedMarks
import com.pulsestream.app.data.db.AppDatabase
import com.pulsestream.app.data.db.WatchedRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simkl sync engine — real two-way sync (Step 34).
 *
 * Scoped port of Nuvio's `features/simkl/SimklSyncEngine.kt` +
 * `SimklSyncRepository.kt` + `SimklMutationRepository.kt` +
 * `SimklScrobbleReconciliation.kt` + `SimklPlaybackMerge.kt` +
 * `SimklWatchDiagnostics.kt` (1,396 lines, tied to Nuvio's profiles /
 * tracking registry / snapshot model stack, none of which exists in Plavio).
 *
 * Plavio-native shape:
 * - Snapshot persisted as raw JSON in SettingsStore (entries + playback
 *   rows + activities watermark + timestamps) — no profile scaffolding.
 * - `synchronize()`: activities watermark check (skip the fetch when the
 *   watermark is unchanged, like Nuvio's engine); bootstrap on first run;
 *   delta fetch with `date_from` afterwards; removal reconciliation via the
 *   current-ids fetch when Simkl reports removals; playback merge with the
 *   local-newer-wins rule (Simkl publishes /sync/playback a moment after
 *   answering a stop/pause, so a naive read resurrects an older position).
 * - Watched pull: Simkl's completed movies + watched episodes upsert into
 *   the local watched store — the DAO upsert is REPLACE, so re-pulling the
 *   same state can never create duplicates.
 * - Mutations: local watch-state changes enqueue a pending mutation
 *   (history add/remove, list move); `flushMutations()` applies them,
 *   skipping any whose state the snapshot already shows (dedupe), and keeps
 *   failures queued for the next sync.
 * - Diagnostics: a human-readable state flow the Integrations screen shows
 *   (last synced / entries / pending / last error), plus the same Log lines
 *   Nuvio's WatchDiagnostics writes.
 *
 * What is NOT ported: profile-scoped snapshots (Plavio has one profile),
 * the tracking registry's placeholder entries, and applying Simkl playback
 * rows to Continue Watching — a Simkl playback row carries no stream URL,
 * so it cannot resume in Plavio's player; the rows live in the snapshot and
 * the diagnostics, not in the resume list.
 */
object SimklSyncEngine {

    private const val TAG = "SimklSync"
    private const val MAX_QUEUED_MUTATIONS = 500

    private val api = SimklApi()
    private val mutex = Mutex()

    /** One pending local change that has not reached Simkl yet. */
    data class SimklMutation(
        val kind: String, // "history_add" | "history_remove" | "list_move"
        val type: String, // "movie" | "series"
        val imdbId: String,
        val title: String = "",
        val season: Int = 0,
        val episode: Int = 0,
        val status: String? = null, // list_move destination
        val createdAtMs: Long = System.currentTimeMillis()
    )

    /** Persisted snapshot of what Simkl holds for this account. */
    data class SimklSyncSnapshot(
        val entries: List<SimklApi.SimklSyncEntry> = emptyList(),
        val playback: List<SimklApi.SimklPlaybackRow> = emptyList(),
        val watermark: String? = null,
        val lastSyncedAtMs: Long = 0L,
        val lastCheckedAtMs: Long = 0L
    ) {
        fun entryFor(type: String, imdbId: String): SimklApi.SimklSyncEntry? =
            entries.firstOrNull { it.type == type && it.imdbId == imdbId }
    }

    /** Human-readable diagnostics — shown on the Integrations screen. */
    data class SimklSyncDiagnostics(
        val lastSyncedAtMs: Long = 0L,
        val lastCheckedAtMs: Long = 0L,
        val entries: Int = 0,
        val watchedMovies: Int = 0,
        val watchedEpisodes: Int = 0,
        val pendingMutations: Int = 0,
        val lastError: String? = null,
        val isSyncing: Boolean = false
    )

    sealed interface SimklSyncResult {
        data object Unchanged : SimklSyncResult
        data class Synced(val addedMovies: Int, val addedEpisodes: Int, val entries: Int) : SimklSyncResult
        data class Failed(val message: String) : SimklSyncResult
    }

    private val _diagnostics = MutableStateFlow(SimklSyncDiagnostics())
    val diagnostics: StateFlow<SimklSyncDiagnostics> = _diagnostics.asStateFlow()

    // ------------------------------------------------------------------
    // Snapshot + mutation queue persistence (org.json, own shape)
    // ------------------------------------------------------------------

    fun loadSnapshot(settings: SettingsStore): SimklSyncSnapshot {
        val raw = settings.simklSyncSnapshot
        if (raw.isBlank()) return SimklSyncSnapshot()
        return runCatching {
            val root = JSONObject(raw)
            val entries = mutableListOf<SimklApi.SimklSyncEntry>()
            val arr = root.optJSONArray("entries")
            if (arr != null) for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val watched = mutableSetOf<Pair<Int, Int>>()
                val watchedAt = mutableMapOf<Pair<Int, Int>, Long>()
                val eps = e.optJSONArray("episodes")
                if (eps != null) for (j in 0 until eps.length()) {
                    val ep = eps.optJSONObject(j) ?: continue
                    val key = ep.optInt("s") to ep.optInt("e")
                    watched += key
                    watchedAt[key] = ep.optLong("at")
                }
                entries += SimklApi.SimklSyncEntry(
                    type = e.optString("type"),
                    imdbId = e.optString("imdb"),
                    title = e.optString("title"),
                    status = e.optString("status").takeIf { it.isNotBlank() },
                    lastWatchedAtMs = e.optLong("lastWatchedAtMs"),
                    watchedEpisodes = watched,
                    watchedEpisodeAtMs = watchedAt
                )
            }
            val playback = mutableListOf<SimklApi.SimklPlaybackRow>()
            val pArr = root.optJSONArray("playback")
            if (pArr != null) for (i in 0 until pArr.length()) {
                val p = pArr.optJSONObject(i) ?: continue
                playback += SimklApi.SimklPlaybackRow(
                    type = p.optString("type"),
                    imdbId = p.optString("imdb").takeIf { it.isNotBlank() },
                    title = p.optString("title"),
                    season = p.optInt("season"),
                    episode = p.optInt("episode"),
                    progressPercent = p.optDouble("progress"),
                    playedAtMs = p.optLong("playedAtMs")
                )
            }
            SimklSyncSnapshot(
                entries = entries,
                playback = playback,
                watermark = root.optString("watermark").takeIf { it.isNotBlank() },
                lastSyncedAtMs = root.optLong("lastSyncedAtMs"),
                lastCheckedAtMs = root.optLong("lastCheckedAtMs")
            )
        }.getOrDefault(SimklSyncSnapshot())
    }

    fun saveSnapshot(settings: SettingsStore, snapshot: SimklSyncSnapshot) {
        val root = JSONObject()
        val arr = JSONArray()
        snapshot.entries.forEach { e ->
            val obj = JSONObject()
                .put("type", e.type)
                .put("imdb", e.imdbId)
                .put("title", e.title)
                .put("status", e.status ?: "")
                .put("lastWatchedAtMs", e.lastWatchedAtMs)
            val eps = JSONArray()
            e.watchedEpisodes.forEach { (s, ep) ->
                eps.put(JSONObject().put("s", s).put("e", ep)
                    .put("at", e.watchedEpisodeAtMs[s to ep] ?: 0L))
            }
            obj.put("episodes", eps)
            arr.put(obj)
        }
        root.put("entries", arr)
        val pArr = JSONArray()
        snapshot.playback.forEach { p ->
            pArr.put(JSONObject()
                .put("type", p.type)
                .put("imdb", p.imdbId ?: "")
                .put("title", p.title)
                .put("season", p.season)
                .put("episode", p.episode)
                .put("progress", p.progressPercent)
                .put("playedAtMs", p.playedAtMs))
        }
        root.put("playback", pArr)
        root.put("watermark", snapshot.watermark ?: "")
        root.put("lastSyncedAtMs", snapshot.lastSyncedAtMs)
        root.put("lastCheckedAtMs", snapshot.lastCheckedAtMs)
        settings.simklSyncSnapshot = root.toString()
    }

    fun loadMutations(settings: SettingsStore): List<SimklMutation> {
        val raw = settings.simklSyncMutations
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(SimklMutation(
                        kind = o.optString("kind"),
                        type = o.optString("type"),
                        imdbId = o.optString("imdb"),
                        title = o.optString("title"),
                        season = o.optInt("season"),
                        episode = o.optInt("episode"),
                        status = o.optString("status").takeIf { it.isNotBlank() },
                        createdAtMs = o.optLong("createdAtMs")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveMutations(settings: SettingsStore, mutations: List<SimklMutation>) {
        val arr = JSONArray()
        mutations.takeLast(MAX_QUEUED_MUTATIONS).forEach { m ->
            arr.put(JSONObject()
                .put("kind", m.kind)
                .put("type", m.type)
                .put("imdb", m.imdbId)
                .put("title", m.title)
                .put("season", m.season)
                .put("episode", m.episode)
                .put("status", m.status ?: "")
                .put("createdAtMs", m.createdAtMs))
        }
        settings.simklSyncMutations = arr.toString()
    }

    // ------------------------------------------------------------------
    // Enqueue (called from StreamRepository when the user changes state)
    // ------------------------------------------------------------------

    /** Queue a watched/unwatched change. No-op when Simkl is not linked. */
    fun enqueueWatched(
        settings: SettingsStore,
        type: String,
        imdbId: String,
        title: String = "",
        season: Int = 0,
        episode: Int = 0,
        watched: Boolean = true
    ) {
        if (settings.simklToken.isNullOrBlank()) return
        if (!imdbId.startsWith("tt")) return
        val queue = loadMutations(settings).toMutableList()
        // Collapse an opposite pending mutation for the same title.
        queue.removeAll {
            it.type == type && it.imdbId == imdbId && it.season == season &&
                it.episode == episode && it.kind in listOf("history_add", "history_remove")
        }
        queue += SimklMutation(
            kind = if (watched) "history_add" else "history_remove",
            type = type,
            imdbId = imdbId,
            title = title,
            season = season,
            episode = episode
        )
        saveMutations(settings, queue)
        refreshDiagnostics(settings, loadSnapshot(settings), queue.size)
    }

    /** Queue a list-status move (plantowatch / watching / completed / hold / dropped). */
    fun enqueueListMove(settings: SettingsStore, type: String, imdbId: String, title: String, to: String) {
        if (settings.simklToken.isNullOrBlank()) return
        if (!imdbId.startsWith("tt")) return
        val queue = loadMutations(settings).toMutableList()
        queue.removeAll { it.kind == "list_move" && it.type == type && it.imdbId == imdbId }
        queue += SimklMutation(kind = "list_move", type = type, imdbId = imdbId, title = title, status = to)
        saveMutations(settings, queue)
        refreshDiagnostics(settings, loadSnapshot(settings), queue.size)
    }

    // ------------------------------------------------------------------
    // Synchronize
    // ------------------------------------------------------------------

    /**
     * Pull Simkl state into the snapshot + local watched marks, then flush
     * pending mutations. Watermark-gated: when nothing changed on Simkl,
     * only the last-checked time updates and no fetch happens.
     */
    suspend fun synchronize(
        token: String,
        settings: SettingsStore,
        db: AppDatabase,
        force: Boolean = false
    ): SimklSyncResult = mutex.withLock {
        val now = System.currentTimeMillis()
        val snapshot = loadSnapshot(settings)
        setSyncing(true)
        try {
            val activities = api.getActivities(token)
            if (!force && snapshot.watermark != null && activities.all != null &&
                activities.all == snapshot.watermark
            ) {
                val checked = snapshot.copy(lastCheckedAtMs = now)
                saveSnapshot(settings, checked)
                Log.i(TAG, "sync skipped — watermark unchanged (${activities.all})")
                refreshDiagnostics(settings, checked, loadMutations(settings).size)
                setSyncing(false)
                return SimklSyncResult.Unchanged
            }

            val bootstrap = snapshot.entries.isEmpty() && snapshot.watermark == null
            val delta = if (bootstrap) {
                api.getAllItemsFull(token)
            } else {
                api.getAllItemsFull(token, dateFrom = snapshot.watermark)
            }
            // Removal reconciliation: Simkl's delta never lists removed
            // titles, so ask for the current ids when removals changed.
            val removedChanged = activities.movies?.removedFromList != null ||
                activities.shows?.removedFromList != null ||
                activities.anime?.removedFromList != null
            val currentIds = if (!bootstrap && removedChanged) {
                runCatching { api.getAllItemIds(token) }.getOrNull()
            } else {
                null
            }
            val playbackChanged = activities.movies?.playback != null ||
                activities.shows?.playback != null ||
                activities.anime?.playback != null
            val fetchedPlayback = if (bootstrap || playbackChanged) {
                runCatching { api.getPlayback(token) }.getOrNull()
            } else {
                null
            }

            val merged = mergeDelta(snapshot.entries, delta, currentIds)
            val mergedPlayback = mergeFetchedPlayback(snapshot.playback, fetchedPlayback)
            val next = snapshot.copy(
                entries = merged,
                playback = mergedPlayback,
                watermark = activities.all ?: snapshot.watermark,
                lastSyncedAtMs = now,
                lastCheckedAtMs = now
            )
            val pulled = applyWatchedPull(db, merged)
            saveSnapshot(settings, next)
            Log.i(
                TAG,
                "sync ${if (bootstrap) "bootstrap" else "delta"}: ${merged.size} entries, " +
                    "${pulled.first} movies + ${pulled.second} episodes pulled, " +
                    "${mergedPlayback.size} playback rows, watermark=${next.watermark}"
            )
            refreshDiagnostics(settings, next, loadMutations(settings).size)
            setSyncing(false)
            SimklSyncResult.Synced(pulled.first, pulled.second, merged.size)
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            refreshDiagnostics(settings, snapshot, loadMutations(settings).size, lastError = e.message)
            setSyncing(false)
            SimklSyncResult.Failed(e.message ?: "Simkl sync failed")
        }
    }

    /**
     * Apply pending mutations to Simkl. Each batch groups by kind into one
     * POST; a mutation whose state the snapshot already shows is skipped
     * (dedupe). Failures stay queued for the next sync. Returns applied count.
     */
    suspend fun flushMutations(token: String, settings: SettingsStore): Int = mutex.withLock {
        val queue = loadMutations(settings)
        if (queue.isEmpty()) return 0
        val snapshot = loadSnapshot(settings)
        var applied = 0
        val remaining = mutableListOf<SimklMutation>()
        val updatedEntries = snapshot.entries.toMutableList()

        fun entryIndex(m: SimklMutation): Int =
            updatedEntries.indexOfFirst { it.type == m.type && it.imdbId == m.imdbId }

        // history_add / history_remove, one POST each.
        for ((kind, group) in queue.groupBy { it.kind }) {
            when (kind) {
                "history_add", "history_remove" -> {
                    val watched = kind == "history_add"
                    val due = group.filter { m ->
                        val entry = updatedEntries.getOrNull(entryIndex(m))
                        val already = if (m.type == "movie") {
                            entry != null && (entry.status == "completed" || entry.lastWatchedAtMs > 0)
                        } else {
                            entry?.watchedEpisodes?.contains(m.season to m.episode) == true
                        }
                        already != watched
                    }
                    if (due.isEmpty()) {
                        applied += group.size
                        continue
                    }
                    val ok = if (watched) {
                        api.addToHistory(token, due.map {
                            SimklApi.HistoryItem(it.type, it.imdbId, it.title, it.season, it.episode, System.currentTimeMillis())
                        })
                    } else {
                        api.removeFromHistory(token, due.map {
                            SimklApi.HistoryItem(it.type, it.imdbId, it.title, it.season, it.episode)
                        })
                    }
                    if (ok) {
                        applied += group.size
                        // Optimistic snapshot update so the next flush dedupes.
                        due.forEach { m ->
                            val idx = entryIndex(m)
                            if (idx < 0) return@forEach
                            val entry = updatedEntries[idx]
                            updatedEntries[idx] = if (m.type == "movie") {
                                entry.copy(status = if (watched) "completed" else entry.status,
                                    lastWatchedAtMs = if (watched) System.currentTimeMillis() else 0L)
                            } else {
                                val eps = entry.watchedEpisodes.toMutableSet()
                                if (watched) eps += m.season to m.episode else eps -= m.season to m.episode
                                entry.copy(watchedEpisodes = eps)
                            }
                        }
                    } else {
                        remaining += group
                    }
                }
                "list_move" -> {
                    val due = group.filter { m ->
                        updatedEntries.getOrNull(entryIndex(m))?.status != m.status
                    }
                    if (due.isEmpty()) {
                        applied += group.size
                        continue
                    }
                    val byStatus = due.groupBy { it.status ?: "watching" }
                    var allOk = true
                    byStatus.forEach { (to, items) ->
                        if (!api.changeStatus(token, items.map {
                            SimklApi.ListEntry(it.type, it.imdbId, it.title)
                        }, to)) allOk = false
                    }
                    if (allOk) {
                        applied += group.size
                        due.forEach { m ->
                            val idx = entryIndex(m)
                            if (idx >= 0) updatedEntries[idx] = updatedEntries[idx].copy(status = m.status)
                        }
                    } else {
                        remaining += group
                    }
                }
                else -> remaining += group
            }
        }
        if (applied > 0) {
            saveSnapshot(settings, snapshot.copy(entries = updatedEntries))
        }
        if (remaining.size != queue.size) saveMutations(settings, remaining)
        if (applied > 0) Log.i(TAG, "mutations flushed: $applied applied, ${remaining.size} pending")
        refreshDiagnostics(settings, loadSnapshot(settings), remaining.size)
        applied
    }

    // ------------------------------------------------------------------
    // Merge helpers (Nuvio SimklSyncEngine.mergeDelta + SimklPlaybackMerge)
    // ------------------------------------------------------------------

    /**
     * Delta merge: entries keyed by type|imdb; a delta entry replaces the
     * known one (its watched set is authoritative for that title). When
     * [currentIds] is given, entries missing from it were removed on Simkl
     * and leave the snapshot (local watched marks stay — history is not
     * erased by a list removal).
     */
    internal fun mergeDelta(
        known: List<SimklApi.SimklSyncEntry>,
        delta: List<SimklApi.SimklSyncEntry>,
        currentIds: Set<String>?
    ): List<SimklApi.SimklSyncEntry> {
        val byKey = known.associateBy { "${it.type}|${it.imdbId}" }.toMutableMap()
        delta.forEach { byKey["${it.type}|${it.imdbId}"] = it }
        if (currentIds != null) {
            byKey.keys.removeAll { it !in currentIds }
        }
        return byKey.values.toList()
    }

    /**
     * Playback merge: a held (locally recorded) row wins when its playedAt
     * is NEWER than the fetched row for the same title — Simkl publishes
     * /sync/playback a moment after answering a stop/pause, so a naive read
     * resurrects the older position.
     */
    internal fun mergeFetchedPlayback(
        held: List<SimklApi.SimklPlaybackRow>,
        fetched: List<SimklApi.SimklPlaybackRow>?
    ): List<SimklApi.SimklPlaybackRow> {
        if (fetched == null) return held
        val byKey = held.associateBy { playbackKey(it) }.toMutableMap()
        fetched.forEach { row ->
            val key = playbackKey(row)
            val existing = byKey[key]
            if (existing == null || row.playedAtMs >= existing.playedAtMs) {
                byKey[key] = row
            }
        }
        return byKey.values.toList()
    }

    private fun playbackKey(row: SimklApi.SimklPlaybackRow): String =
        "${row.imdbId ?: row.title}|${row.season}|${row.episode}"

    /** Upsert Simkl's watched state into the local watched store. Idempotent. */
    private suspend fun applyWatchedPull(
        db: AppDatabase,
        entries: List<SimklApi.SimklSyncEntry>
    ): Pair<Int, Int> = withContext(Dispatchers.IO) {
        var movies = 0
        var episodes = 0
        val dao = db.watchedDao()
        entries.forEach { entry ->
            if (entry.type == "movie") {
                if (entry.status == "completed" || entry.lastWatchedAtMs > 0) {
                    runCatching {
                        dao.upsert(WatchedRecord(
                            WatchedMarks.movieKey(entry.imdbId),
                            entry.lastWatchedAtMs.takeIf { it > 0 } ?: System.currentTimeMillis()
                        ))
                    }
                    movies++
                }
            } else {
                entry.watchedEpisodes.forEach { (season, episode) ->
                    runCatching {
                        dao.upsert(WatchedRecord(
                            WatchedMarks.episodeKey(entry.imdbId, season, episode),
                            entry.watchedEpisodeAtMs[season to episode]
                                ?.takeIf { it > 0 } ?: System.currentTimeMillis()
                        ))
                    }
                    episodes++
                }
            }
        }
        movies to episodes
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    private fun refreshDiagnostics(
        settings: SettingsStore,
        snapshot: SimklSyncSnapshot,
        pending: Int,
        lastError: String? = null
    ) {
        val current = _diagnostics.value
        _diagnostics.value = SimklSyncDiagnostics(
            lastSyncedAtMs = snapshot.lastSyncedAtMs,
            lastCheckedAtMs = snapshot.lastCheckedAtMs,
            entries = snapshot.entries.size,
            watchedMovies = snapshot.entries.count { it.type == "movie" && (it.status == "completed" || it.lastWatchedAtMs > 0) },
            watchedEpisodes = snapshot.entries.sumOf { it.watchedEpisodes.size },
            pendingMutations = pending,
            lastError = lastError ?: current.lastError,
            isSyncing = current.isSyncing
        )
    }

    private fun setSyncing(syncing: Boolean) {
        _diagnostics.value = _diagnostics.value.copy(isSyncing = syncing)
    }
}
