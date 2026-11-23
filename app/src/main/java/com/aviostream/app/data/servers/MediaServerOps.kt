package com.aviostream.app.data.servers

import com.aviostream.app.data.SettingsStore

/**
 * Step 55: one facade over the four server clients, so screens and
 * playback only deal with [MediaServer] + [ServerItem]. [streamUrl]
 * is the synchronous direct-play URL (Plex/Jellyfin/Emby); Silo needs
 * a negotiated session, so it goes through [preparePlayback] instead.
 */
object MediaServerOps {

    suspend fun getLibraries(
        server: MediaServer,
        settings: SettingsStore? = null
    ): List<ServerLibrary> = when (server.kind) {
        ServerKind.JELLYFIN, ServerKind.EMBY -> MediaBrowserClient.getLibraries(server)
        ServerKind.PLEX -> PlexClient.getSections(server)
        ServerKind.SILO -> SiloClient.getLibraries(server, settings)
    }

    suspend fun getLibraryItems(
        server: MediaServer,
        library: ServerLibrary,
        settings: SettingsStore? = null
    ): List<ServerItem> =
        when (server.kind) {
            ServerKind.JELLYFIN, ServerKind.EMBY -> {
                val type = when (library.kind) {
                    ServerLibraryKind.MOVIE -> "movie"
                    ServerLibraryKind.SERIES -> "series"
                    ServerLibraryKind.MIXED -> "mixed"
                }
                MediaBrowserClient.getItems(server, library.id, type)
            }
            ServerKind.PLEX -> {
                val type = when (library.kind) {
                    ServerLibraryKind.MOVIE -> "movie"
                    ServerLibraryKind.SERIES -> "series"
                    ServerLibraryKind.MIXED -> "movie"
                }
                PlexClient.getSectionItems(server, library.id, type)
            }
            ServerKind.SILO -> SiloClient.getCatalogItems(server, library, settings)
        }

    /** Episodes of a series (or seasons for Plex — see below). */
    suspend fun getSeriesChildren(
        server: MediaServer,
        item: ServerItem,
        settings: SettingsStore? = null
    ): List<ServerItem> =
        when (server.kind) {
            ServerKind.JELLYFIN, ServerKind.EMBY ->
                MediaBrowserClient.getItems(server, item.contentId.substringBefore("|"), "episode")
            ServerKind.PLEX -> PlexClient.getChildren(server, item.contentId.substringBefore("|"))
            ServerKind.SILO -> SiloClient.getSeriesEpisodes(server, item.contentId, settings)
        }

    /** Direct-play URL, or null when the server cannot direct-play (Silo:
     * its session is negotiated — see [preparePlayback]). */
    fun streamUrl(server: MediaServer, item: ServerItem): String? = when (server.kind) {
        ServerKind.JELLYFIN, ServerKind.EMBY -> MediaBrowserClient.streamUrl(server, item)
        ServerKind.PLEX -> PlexClient.streamUrl(server, item)
        ServerKind.SILO -> null
    }

    /** Silo playback session (URL + auth headers for the player), or
     * null when the server refused it. Other kinds have no session to
     * negotiate — use [streamUrl] for them. */
    suspend fun preparePlayback(
        settings: SettingsStore,
        server: MediaServer,
        item: ServerItem
    ): SiloClient.SiloPlaybackSession? = when (server.kind) {
        ServerKind.SILO -> SiloClient.preparePlayback(settings, server, item.contentId)
        else -> null
    }

    /** Mark a title played/unplayed on its server. Best-effort, never throws. */
    suspend fun markPlayed(
        server: MediaServer,
        item: ServerItem,
        played: Boolean,
        settings: SettingsStore? = null
    ): Boolean =
        runCatching {
            when (server.kind) {
                ServerKind.JELLYFIN, ServerKind.EMBY ->
                    MediaBrowserClient.markPlayed(server, item.contentId, played)
                ServerKind.PLEX -> PlexClient.markPlayed(server, item, played)
                ServerKind.SILO -> SiloClient.setPlayed(server, item.contentId, played, settings)
            }
        }.getOrDefault(false)

    /** Mark by videoId ("server:{serverId}:{contentId}") — the savePlayback hook. */
    suspend fun markPlayedByVideoId(
        servers: List<MediaServer>,
        videoId: String,
        season: Int,
        episode: Int,
        played: Boolean,
        settings: SettingsStore? = null
    ): Boolean {
        if (!videoId.startsWith("server:")) return false
        val rest = videoId.removePrefix("server:")
        val serverId = rest.substringBefore(":")
        val contentId = rest.substringAfter(":", "")
        if (serverId.isBlank() || contentId.isBlank()) return false
        val server = servers.firstOrNull { it.id == serverId } ?: return false
        val item = ServerItem(
            contentId = contentId,
            type = if (season > 0 || episode > 0) "episode" else "movie",
            title = "",
            year = null,
            posterUrl = null,
            backdropUrl = null,
            overview = null,
            imdbId = null,
            season = season.takeIf { it > 0 },
            episode = episode.takeIf { it > 0 },
            seriesId = null,
            seriesTitle = null,
            runtimeSecs = null,
            played = played,
            positionSecs = null
        )
        return markPlayed(server, item, played, settings)
    }
}
