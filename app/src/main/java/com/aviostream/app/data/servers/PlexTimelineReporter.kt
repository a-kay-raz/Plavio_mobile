package com.aviostream.app.data.servers

import android.os.SystemClock
import com.aviostream.app.data.PlaybackRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

/**
 * Plex live timeline (Nuvio PlexProvider.report + ServerPlayback):
 * while a Plex title plays, the server learns state + position +
 * duration via GET /:/timeline, so Plex shows now-playing and keeps
 * the resume point live. The old code only marked played at finish
 * (savePlayback threshold -> MediaServerOps.markPlayedByVideoId), so
 * Plex never saw position during playback.
 *
 * Shape mirrors [com.aviostream.app.data.scrobble.ScrobbleManager]:
 * the player calls [tick] once per poll and [shutdown] on dispose.
 * Throttle mirrors Nuvio's ServerPlayback: a progress report at most
 * every [PROGRESS_INTERVAL_MS], immediately on play/pause transitions
 * and on a seek ([SEEK_THRESHOLD_MS]); "stopped" only for a session
 * that actually started playing (a play that never got going has no
 * position worth keeping, and a stop at 0 would clear the resume
 * point — same guard as Nuvio).
 *
 * Plex-only: the videoId must be "server:{serverId}:{contentId}"
 * (same scheme as [MediaServerOps.markPlayedByVideoId]) and the
 * server must be [ServerKind.PLEX]. Anything else is ignored, and a
 * switch away from Plex stops the previous session. All calls are
 * best-effort and never throw; network runs on this class's own scope
 * so the final "stopped" survives player dispose.
 *
 * All [tick] calls must happen on the main thread.
 */
class PlexTimelineReporter(
    private val servers: () -> List<MediaServer>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var activeVideoId: String? = null
    private var activeServer: MediaServer? = null
    private var activeRatingKey: String? = null
    private var sessionId: String? = null
    private var started = false
    private var wasPlaying = false
    private var lastPositionMs = 0L
    private var lastDurationMs = 0L
    private var lastReportAtMs = 0L
    private var lastReportPositionMs = 0L

    fun tick(req: PlaybackRequest, posMs: Long, durMs: Long, playing: Boolean, ended: Boolean) {
        if (req.videoId != activeVideoId) {
            stopActive(lastPositionMs, lastDurationMs)
            activeVideoId = req.videoId
            val parsed = parsePlexVideoId(req)
            activeServer = parsed?.first
            activeRatingKey = parsed?.second
            sessionId = null
            started = false
            wasPlaying = false
            lastPositionMs = posMs
            lastDurationMs = durMs
            lastReportAtMs = 0L
            lastReportPositionMs = 0L
            if (activeServer == null) return
        }
        val server = activeServer ?: return
        val ratingKey = activeRatingKey ?: return
        lastPositionMs = posMs
        lastDurationMs = durMs
        if (ended) {
            stopActive(posMs, durMs)
            reset()
            return
        }
        // A title that never started playing has no position to report.
        if (!playing && !started) return
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastReportAtMs
        val expected = lastReportPositionMs + if (wasPlaying) elapsed else 0L
        when {
            playing && !started -> {
                started = true
                sessionId = newSessionId()
                wasPlaying = true
                send(server, ratingKey, "playing", posMs, durMs)
            }
            playing && !wasPlaying -> {
                wasPlaying = true
                send(server, ratingKey, "playing", posMs, durMs)
            }
            !playing && wasPlaying -> {
                wasPlaying = false
                send(server, ratingKey, "paused", posMs, durMs)
            }
            playing && (elapsed >= PROGRESS_INTERVAL_MS ||
                abs(posMs - expected) > SEEK_THRESHOLD_MS) -> {
                send(server, ratingKey, "playing", posMs, durMs)
            }
        }
    }

    /** Player dispose: final "stopped" for the active Plex title, if any. */
    fun shutdown() {
        stopActive(lastPositionMs, lastDurationMs)
        reset()
    }

    private fun stopActive(posMs: Long, durMs: Long) {
        val server = activeServer ?: return
        val ratingKey = activeRatingKey ?: return
        if (!started) return
        send(server, ratingKey, "stopped", posMs, durMs)
    }

    private fun send(server: MediaServer, ratingKey: String, state: String, posMs: Long, durMs: Long) {
        lastReportAtMs = SystemClock.elapsedRealtime()
        lastReportPositionMs = posMs
        val session = sessionId ?: return
        scope.launch {
            runCatching {
                PlexClient.reportTimeline(server, ratingKey, state, posMs, durMs, session)
            }
        }
    }

    private fun reset() {
        activeVideoId = null
        activeServer = null
        activeRatingKey = null
        sessionId = null
        started = false
        wasPlaying = false
        lastPositionMs = 0L
        lastDurationMs = 0L
        lastReportAtMs = 0L
        lastReportPositionMs = 0L
    }

    /**
     * Same "server:{serverId}:{contentId}" parsing as
     * [MediaServerOps.markPlayedByVideoId]; contentId may carry a
     * "|partKey" suffix, the ratingKey is the part before it.
     * Returns null for live/trailer/non-server/non-Plex playback.
     */
    private fun parsePlexVideoId(req: PlaybackRequest): Pair<MediaServer, String>? {
        if (req.isLive || req.isTrailer) return null
        val videoId = req.videoId
        if (!videoId.startsWith("server:")) return null
        val rest = videoId.removePrefix("server:")
        val serverId = rest.substringBefore(":")
        val contentId = rest.substringAfter(":", "")
        if (serverId.isBlank() || contentId.isBlank()) return null
        val server = servers().firstOrNull { it.id == serverId } ?: return null
        if (server.kind != ServerKind.PLEX) return null
        val ratingKey = contentId.substringBefore("|")
        if (ratingKey.isBlank()) return null
        return server to ratingKey
    }

    /** Nuvio PlexProvider.newPlaySession: 32 hex chars. */
    private fun newSessionId(): String =
        (0 until 32).joinToString("") { Random.nextInt(16).toString(16) }

    companion object {
        /** Nuvio ServerPlayback.PROGRESS_INTERVAL_MS. */
        private const val PROGRESS_INTERVAL_MS = 10_000L

        /** Nuvio ServerPlayback.SEEK_THRESHOLD_MS. */
        private const val SEEK_THRESHOLD_MS = 5_000L
    }
}
