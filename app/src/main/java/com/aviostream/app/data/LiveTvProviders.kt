package com.aviostream.app.data

/**
 * Live TV provider models (Nuvio `livetv/LiveTvModels.kt` + `LiveTvStreamHeaders.kt`).
 *
 * Xtream Codes and Stalker Portal channels join the Live tab as ordinary
 * catalog rows (addonId "xtream" / "stalker"), so search, category chips,
 * favorites and recents all work unchanged. Only playback differs: Xtream
 * URLs play directly, Stalker needs a create_link call first.
 */
data class LiveTvChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val streamType: String? = null,
    val stalkerCommand: String? = null,
    val epgId: String? = null,
)

data class LiveTvEpgProgram(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val description: String? = null,
    val category: String? = null,
) {
    fun isAiringAt(timeMillis: Long): Boolean = timeMillis in startMillis until endMillis

    fun progressAt(timeMillis: Long): Float {
        val duration = endMillis - startMillis
        if (duration <= 0L) return 0f
        return ((timeMillis - startMillis).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}

data class LiveTvEpgSource(
    val id: String,
    val name: String,
    val url: String,
    val isEnabled: Boolean = true,
)

data class LiveTvStalkerSettings(
    val portalUrl: String = "",
    val macAddress: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = portalUrl.isNotBlank() && macAddress.isNotBlank()
}

data class LiveTvXtreamSettings(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val isEnabled: Boolean = true,
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

/**
 * The user agent live channels are opened with, as Nuvio TV opens them.
 * IPTV panels are made for IPTV players: many refuse a browser's user
 * agent, or answer it with a stream that never starts.
 */
internal const val LIVE_TV_STREAM_USER_AGENT = "VLC/3.0.0 LibVLC/3.0.0"

/** Headers a channel without its own gets: VLC's user agent for web links, none otherwise. */
internal fun defaultLiveTvStreamHeaders(url: String): Map<String, String> =
    if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
        mapOf("User-Agent" to LIVE_TV_STREAM_USER_AGENT)
    } else {
        emptyMap()
    }
