package com.aviostream.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import com.aviostream.app.data.IntroDbApi
import com.aviostream.app.data.PlaybackHeaders
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.data.ResolvedSubtitle
import com.aviostream.app.data.SegmentType
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.SkipSegment
import com.aviostream.app.data.autoSkipStoredValue
import com.aviostream.app.data.resolveSubtitleFiles
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Opens a [PlaybackRequest] in an external video player (VLC, MX Player…)
 * via ACTION_VIEW + the system chooser.
 *
 * The built-in player is untouched — this is only used when the user's
 * "Preferred player" setting says so.
 */
object ExternalPlayer {

    private const val PREFS = "plavio_external_player"
    private const val KEY_HIDE_WARNING = "hide_protected_warning"

    /**
     * True when the stream carries headers beyond the harmless trio —
     * the receiving app gets only the sanitized subset (credentials and
     * cookies are never handed off, see [play]), so a protected link
     * may simply not play outside. Until v0.14.3 such links were
     * hard-blocked; the owner's order (2026-10-26) replaced the block
     * with a one-time warning — see [needsWarning].
     */
    fun hasProtectedHeaders(request: PlaybackRequest): Boolean =
        PlaybackHeaders.buildPlaybackHeaders(request.headers, false).keys.any {
            it.lowercase() !in setOf("user-agent", "accept", "accept-language")
        }

    /** The warning's "Don't show again" choice, persisted per device. */
    fun warningSuppressed(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HIDE_WARNING, false)

    fun setWarningSuppressed(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_HIDE_WARNING, value).apply()
    }

    /**
     * True when the caller should show the protected-link warning
     * popup before handing off: the link is protected AND the user
     * hasn't ticked "Don't show again". Normal links never warn.
     */
    fun needsWarning(context: Context, request: PlaybackRequest): Boolean =
        hasProtectedHeaders(request) && !warningSuppressed(context)

    /**
     * Tries to hand [request] to an external player. Never refuses a
     * link for its headers (owner's order 2026-10-26) — callers gate
     * protected links behind the warning popup via [needsWarning].
     *
     * Step 67: forwards the already-downloaded subtitles ([subtitles],
     * shared via FileProvider) and the skip-segments JSON ([skipSegmentsJson])
     * using Nuvio's external-player intent contract, plus the resume
     * position. Players that don't understand an extra ignore it.
     *
     * @return true if a player was launched; false when no player could
     * handle it (a toast is shown explaining why) — the caller should then
     * fall back to the built-in player.
     */
    fun play(
        context: Context,
        request: PlaybackRequest,
        subtitles: List<ResolvedSubtitle> = emptyList(),
        skipSegmentsJson: String? = null,
        resumePositionMs: Long = 0L,
    ): Boolean {
        val (uri, mime) = resolveUri(context, request.streamUrl)
        if (uri == null) {
            Toast.makeText(
                context,
                "Can't open this stream in an external player",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime ?: "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            // Required when launched from a non-Activity context.
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Title shown by players that support it (VLC, MX Player).
            putExtra("title", request.title)
            // Resume position (MX Player / Just Player / mpv, Vimu).
            if (resumePositionMs > 0L) {
                putExtra("position", resumePositionMs.toInt())
                putExtra("startfrom", resumePositionMs.toInt())
                putExtra("forceresume", true)
                putExtra("from_start", false)
            }
            // Ask the player to report its position back (MX Player).
            putExtra("return_result", true)
            // Skip segments (intro/recap/outro/credits); the receiving
            // player controls their handling.
            skipSegmentsJson?.let { putExtra("skip_segments", it) }
            // VLC reads a String[] "headers" extra. Normalize headers and
            // deliberately omit credentials/cookies: the receiving app and
            // any redirects it follows are outside our origin boundary.
            val headers = PlaybackHeaders.externalPlayerHeaders(request.headers)
            if (headers.isNotEmpty()) {
                putExtra(
                    "headers",
                    headers.map { (k, v) -> "$k: $v" }.toTypedArray()
                )
            }
            // Subtitles: the resolved files are shared via FileProvider.
            // Grant read permission via ClipData — FLAG_GRANT_READ_URI_PERMISSION
            // only covers intent.data, not extras.
            if (subtitles.isNotEmpty()) {
                val subtitleUris = subtitles.map { sub ->
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        sub.file
                    )
                }.toTypedArray()
                val subtitleNames = subtitles.map { it.sub.lang }.toTypedArray()
                val subtitleFilenames = subtitles.map { sub ->
                    val ext = if (sub.mimeType == MimeTypes.TEXT_VTT) ".vtt" else ".srt"
                    "${sub.sub.lang}_subtitle$ext"
                }.toTypedArray()
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val clipData = ClipData(
                    "subtitles",
                    arrayOf("application/x-subrip", "text/vtt"),
                    ClipData.Item(subtitleUris.first())
                )
                subtitleUris.drop(1).forEach { subtitleUri ->
                    clipData.addItem(ClipData.Item(subtitleUri))
                }
                setClipData(clipData)
                // MX Player / mpv-android / Nova
                putExtra("subs", subtitleUris)
                putExtra("subs.name", subtitleNames)
                putExtra("subs.filename", subtitleFilenames)
                putExtra("subs.enable", arrayOf(subtitleUris.first()))
                // Just Player
                putExtra("subtitle_uri", subtitleUris)
                putExtra("subtitle_name", subtitleNames)
                // VLC (single subtitle — use first one)
                putExtra("subtitles_location", subtitleUris.first())
                // Vimu Player
                putExtra("forcedsrt", subtitleUris.first().toString())
            }
        }
        return try {
            context.startActivity(Intent.createChooser(intent, "Play video with"))
            true
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                context,
                "No video player found — install VLC to play externally",
                Toast.LENGTH_LONG
            ).show()
            false
        }
    }

    /** What [prepareLaunch] resolved for the handoff. */
    data class ExternalLaunchExtras(
        val subtitles: List<ResolvedSubtitle>,
        val skipSegmentsJson: String?,
    )

    /**
     * Step 67: prepares the handoff extras — subtitles + skip segments —
     * for an external-player launch. Subtitles come from [alreadyResolved]
     * (the player's downloaded files, selected first) when available;
     * otherwise the request's own subtitle list is resolved (downloaded)
     * for the preferred languages, bounded so a slow download never
     * stalls the launch. Skip segments come from IntroDB, bounded at 4s.
     * Either side degrades to "nothing to forward" on failure/timeout.
     */
    suspend fun prepareLaunch(
        context: Context,
        request: PlaybackRequest,
        settings: SettingsStore,
        alreadyResolved: List<ResolvedSubtitle> = emptyList(),
        selectedSubtitleUrl: String? = null,
        isTv: Boolean = false,
    ): ExternalLaunchExtras {
        val subtitles = if (!settings.forwardSubtitlesToExternalPlayer) {
            emptyList()
        } else if (alreadyResolved.isNotEmpty()) {
            val selected = alreadyResolved.firstOrNull { it.sub.url == selectedSubtitleUrl }
            buildList {
                selected?.let { add(it) }
                for (lang in settings.subtitleLanguages) {
                    alreadyResolved.filter { it.sub.lang.equals(lang, ignoreCase = true) }
                        .forEach { if (!contains(it)) add(it) }
                }
                if (isEmpty()) alreadyResolved.firstOrNull()?.let { add(it) }
            }.take(5)
        } else {
            val preferred = settings.subtitleLanguages
            val wanted = request.subtitles
                .filter { sub -> preferred.any { it.equals(sub.lang, ignoreCase = true) } }
                .ifEmpty { request.subtitles.take(1) }
                .take(5)
            if (wanted.isEmpty()) {
                emptyList()
            } else {
                withTimeoutOrNull(10_000L) {
                    runCatching {
                        resolveSubtitleFiles(
                            wanted, context.cacheDir,
                            requestHeaders = request.headers,
                            streamOriginUrl = request.streamUrl,
                            isTv = isTv
                        )
                    }.getOrDefault(emptyList())
                } ?: emptyList()
            }
        }
        val imdb = request.imdbId
        val skipSegmentsJson = if (settings.sendSkipSegmentsToExternalPlayer && !imdb.isNullOrBlank()) {
            withTimeoutOrNull(4_000L) {
                runCatching {
                    IntroDbApi().fetchSegments(imdb, request.season, request.episode, request.isMovie)
                }.getOrNull()?.let { buildSkipSegmentsJson(it) }
            }
        } else null
        return ExternalLaunchExtras(subtitles, skipSegmentsJson)
    }

    /**
     * Serializes skip segments to Nuvio's external-player JSON contract:
     * a JSON array of objects with `type` (String), `start` (seconds) and
     * `end` (seconds). Post-credits maps to "end-credits" (Nuvio's
     * toSkipSegmentsJson). Returns null when there is nothing to send.
     */
    fun buildSkipSegmentsJson(segments: List<SkipSegment>): String? {
        if (segments.isEmpty()) return null
        val arr = JSONArray()
        for (seg in segments) {
            val obj = JSONObject()
            obj.put(
                "type",
                if (seg.type == SegmentType.POST_CREDITS) "end-credits"
                else seg.type.autoSkipStoredValue()
            )
            obj.put("start", seg.startMs / 1000.0)
            obj.put("end", seg.endMs / 1000.0)
            arr.put(obj)
        }
        return arr.toString()
    }

    /**
     * Maps a stream URL to a (Uri, mimeType) pair an external app can open.
     * Local download files go through FileProvider so other apps can read
     * them; remote URLs are passed through with an inferred container type.
     * Returns null when the URL can't be handed off.
     */
    private fun resolveUri(context: Context, url: String): Pair<Uri?, String?> {
        return try {
            if (url.startsWith("file://", ignoreCase = true)) {
                val path = Uri.parse(url).path ?: return null to null
                val file = File(path)
                if (!file.exists()) return null to null
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                contentUri to "video/*"
            } else {
                val uri = Uri.parse(url) ?: return null to null
                uri to PlaybackHeaders.inferMimeType(url)
            }
        } catch (e: Exception) {
            // Covers IllegalArgumentException from FileProvider (file outside
            // the configured roots) and malformed URLs — caller falls back
            // to the built-in player.
            null to null
        }
    }
}
