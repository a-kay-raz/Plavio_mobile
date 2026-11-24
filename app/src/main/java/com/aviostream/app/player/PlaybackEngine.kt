package com.aviostream.app.player

import com.aviostream.app.data.PlaybackRequest

/** The two playback engines. */
enum class PlaybackEngine {
    EXO,
    MPV,
}

/**
 * Engine routing: which engine plays a given source.
 *
 * Manual setting wins (Settings → Playback → Engine:
 * 0 = Auto, 1 = ExoPlayer, 2 = MPV). In Auto, HDR / Dolby Vision
 * titles route to ExoPlayer (the second engine tone-maps them
 * wrong) and anime routes to the second engine (its subtitle and
 * container handling suits anime releases); everything else plays
 * on ExoPlayer. The choice is resolved once per source identity
 * and stays frozen until the source changes.
 */
object PlaybackEngineResolver {

    private val HDR_DV_REGEX = Regex("""(?i)\b(hdr|hdr10\+?|dv|dolby\s*vision)\b""")

    private val ANIME_ID_PREFIXES = listOf("kitsu:", "mal:", "anilist:")

    const val SETTING_AUTO = 0
    const val SETTING_EXO = 1
    const val SETTING_MPV = 2

    fun resolve(setting: Int, request: PlaybackRequest): PlaybackEngine = when (setting) {
        SETTING_EXO -> PlaybackEngine.EXO
        SETTING_MPV -> PlaybackEngine.MPV
        else -> resolveAuto(request)
    }

    fun resolveAuto(request: PlaybackRequest): PlaybackEngine {
        val text = listOfNotNull(
            request.streamLabel,
            request.title,
            request.seriesName,
        ).joinToString(" ")
        if (HDR_DV_REGEX.containsMatchIn(text)) return PlaybackEngine.EXO
        val ids = listOf(request.videoId, request.metaId)
        if (ids.any { id -> ANIME_ID_PREFIXES.any { id.startsWith(it, ignoreCase = true) } }) {
            return PlaybackEngine.MPV
        }
        return PlaybackEngine.EXO
    }

    fun other(engine: PlaybackEngine): PlaybackEngine = when (engine) {
        PlaybackEngine.EXO -> PlaybackEngine.MPV
        PlaybackEngine.MPV -> PlaybackEngine.EXO
    }
}
