package com.pulsestream.app.data

import com.pulsestream.app.data.simkl.SimklApi
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Step 62 — Watch-progress pull from Trakt/Simkl + source selection
 * (C6, portable half).
 *
 * Nuvio's WatchProgressRepository is a 1,900-line system built on its
 * Supabase delta sync (pullSupabaseDeltaFromServer, Nuvio-server
 * snapshots, metadata-resolution retry coordinators) — that half
 * cannot port 1:1 and was not attempted. This is the portable half:
 * pull in-progress playback positions from Trakt /sync/playback and
 * Simkl /sync/playback (both return a progress PERCENT, no duration),
 * store them per profile, and resume at the pulled position in the
 * player when this device has no local progress for the title.
 *
 * Identity matches Plavio's playbackIdentity (StreamRepository):
 * episodes "ep|<imdb>|<season>|<episode>", movies "id|<imdb>".
 * The percent is converted to milliseconds only once the player
 * reports a real duration (PlayerScreen's deferred remote seek).
 *
 * Deviations from Nuvio:
 * - No Supabase/Nuvio-server snapshot or delta sync; no push of
 *   local progress to a server (scrobble-out in Step 33/34 already
 *   reports start/pause/stop to Trakt/Simkl — that is what populates
 *   their /sync/playback for other devices).
 * - No Trakt history/show-progress sources (those give watched
 *   state, not positions); the selector is local / Trakt / Simkl.
 * - Local progress always wins when it exists for the played URL;
 *   the remote point only applies when this device has none.
 * - Remote-only titles do NOT appear in Continue Watching: a
 *   PlaybackState row needs a stream URL, which neither API returns.
 *   The resume applies once the user opens the title and plays it.
 * - Simkl anime rows map to "series" (same as SimklApi.getPlayback).
 */
enum class WatchProgressSource(val stored: String, val label: String) {
    LOCAL("local", "Local only"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "Simkl");

    companion object {
        fun fromStored(value: String?): WatchProgressSource =
            entries.firstOrNull { it.stored == value } ?: LOCAL
    }
}

data class RemoteResumePoint(
    val identity: String,
    val imdbId: String?,
    val isEpisode: Boolean,
    val season: Int,
    val episode: Int,
    val progressPct: Double,
    val pausedAtMs: Long,
    val title: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("identity", identity)
        put("imdbId", imdbId)
        put("isEpisode", isEpisode)
        put("season", season)
        put("episode", episode)
        put("progressPct", progressPct)
        put("pausedAtMs", pausedAtMs)
        put("title", title)
    }

    companion object {
        fun fromJson(obj: JSONObject): RemoteResumePoint? {
            val identity = obj.optString("identity").takeIf { it.isNotBlank() } ?: return null
            return RemoteResumePoint(
                identity = identity,
                imdbId = obj.optString("imdbId").takeIf { it.isNotBlank() },
                isEpisode = obj.optBoolean("isEpisode", false),
                season = obj.optInt("season", 0),
                episode = obj.optInt("episode", 0),
                progressPct = obj.optDouble("progressPct", 0.0),
                pausedAtMs = obj.optLong("pausedAtMs", 0L),
                title = obj.optString("title", ""),
            )
        }
    }
}

sealed interface WatchProgressPullResult {
    data class Success(val source: WatchProgressSource, val count: Int) : WatchProgressPullResult
    data class Error(val message: String) : WatchProgressPullResult
}

class WatchProgressPull(private val settings: SettingsStore) {
    private val _points = MutableStateFlow(loadPoints())
    val points: StateFlow<Map<String, RemoteResumePoint>> = _points.asStateFlow()

    /** Same identity shape as StreamRepository.playbackIdentity. */
    fun identityFor(imdbId: String?, metaId: String, season: Int, episode: Int): String {
        val id = imdbId?.takeIf { it.isNotBlank() } ?: metaId
        return if (season > 0 || episode > 0) "ep|$id|$season|$episode" else "id|$id"
    }

    fun resumePercentFor(identity: String): Double? = _points.value[identity]?.progressPct

    fun resumeMsFor(identity: String, durationMs: Long): Long? {
        val pct = resumePercentFor(identity) ?: return null
        if (durationMs <= 0 || pct < 1.0 || pct >= 95.0) return null
        return (durationMs * pct / 100.0).toLong().coerceAtLeast(0L)
    }

    suspend fun pullNow(): WatchProgressPullResult {
        val source = WatchProgressSource.fromStored(settings.watchProgressSource)
        if (source == WatchProgressSource.LOCAL) {
            return WatchProgressPullResult.Error("Resume source is Local only — pick Trakt or Simkl to pull.")
        }
        return try {
            val fetched: List<RemoteResumePoint> = when (source) {
                WatchProgressSource.TRAKT -> {
                    val token = TraktManager(settings).validAccessToken()
                        ?: return WatchProgressPullResult.Error("Trakt isn't linked — link it in Integrations first.")
                    TraktApi().getPlaybackProgress(token).mapNotNull { row ->
                        val imdb = row.imdbId ?: return@mapNotNull null
                        RemoteResumePoint(
                            identity = if (row.type == "episode") "ep|$imdb|${row.season}|${row.episode}" else "id|$imdb",
                            imdbId = imdb,
                            isEpisode = row.type == "episode",
                            season = row.season,
                            episode = row.episode,
                            progressPct = row.progressPercent,
                            pausedAtMs = row.pausedAtMs,
                            title = row.title,
                        )
                    }
                }
                WatchProgressSource.SIMKL -> {
                    val token = settings.simklToken
                        ?: return WatchProgressPullResult.Error("Simkl isn't linked — link it in Integrations first.")
                    SimklApi().getPlayback(token).mapNotNull { row ->
                        val imdb = row.imdbId ?: return@mapNotNull null
                        val isEpisode = row.type != "movie"
                        RemoteResumePoint(
                            identity = if (isEpisode) "ep|$imdb|${row.season}|${row.episode}" else "id|$imdb",
                            imdbId = imdb,
                            isEpisode = isEpisode,
                            season = row.season,
                            episode = row.episode,
                            progressPct = row.progressPercent,
                            pausedAtMs = row.playedAtMs,
                            title = row.title,
                        )
                    }
                }
                WatchProgressSource.LOCAL -> emptyList()
            }
            // Merge: newer pausedAt wins per identity; drop finished/untouched rows.
            val merged = _points.value.toMutableMap()
            var kept = 0
            for (point in fetched) {
                if (point.progressPct < 1.0 || point.progressPct >= 95.0) continue
                val cur = merged[point.identity]
                if (cur == null || point.pausedAtMs >= cur.pausedAtMs) merged[point.identity] = point
                kept++
            }
            persist(merged)
            settings.lastWatchProgressPullMs = System.currentTimeMillis()
            WatchProgressPullResult.Success(source, kept)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            WatchProgressPullResult.Error(error.message ?: "Pull failed.")
        }
    }

    private fun loadPoints(): Map<String, RemoteResumePoint> {
        val raw = settings.remoteResumePointsJson
        if (raw.isBlank()) return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val out = LinkedHashMap<String, RemoteResumePoint>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                obj.optJSONObject(key)?.let { RemoteResumePoint.fromJson(it) }?.let { out[key] = it }
            }
            out
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun persist(points: Map<String, RemoteResumePoint>) {
        val obj = JSONObject()
        points.forEach { (key, point) -> obj.put(key, point.toJson()) }
        settings.remoteResumePointsJson = obj.toString()
        _points.value = points
    }
}
