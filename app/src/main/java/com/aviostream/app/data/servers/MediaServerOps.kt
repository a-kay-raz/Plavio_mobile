package com.aviostream.app.data.servers

/**
 * Step 55: one facade over the four server clients, so screens and
 * playback only deal with [MediaServer] + [ServerItem]. Silo has no
 * playable URL yet (its negotiated playback session is not ported) —
 * [streamUrl] returns null for it and the UI says so instead of
 * failing silently.
 */
object MediaServerOps {

    suspend fun getLibraries(server: MediaServer): List<ServerLibrary> = when (server.kind) {
        ServerKind.JELLYFIN, ServerKind.EMBY -> MediaBrowserClient.getLibraries(server)
        ServerKind.PLEX -> PlexClient.getSections(server)
        ServerKind.SILO -> SiloClient.getLibraries(server)
    }

    suspend fun getLibraryItems(server: MediaServer, library: ServerLibrary): List<ServerItem> =
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
            ServerKind.SILO -> SiloClient.getCatalogItems(server, library)
        }

    /** Episodes of a series (or seasons for Plex — see below). */
    suspend fun getSeriesChildren(server: MediaServer, item: ServerItem): List<ServerItem> =
        when (server.kind) {
            ServerKind.JELLYFIN, ServerKind.EMBY ->
                MediaBrowserClient.getItems(server, item.contentId.substringBefore("|"), "episode")
            ServerKind.PLEX -> PlexClient.getChildren(server, item.contentId.substringBefore("|"))
            ServerKind.SILO -> SiloClient.getSeriesEpisodes(server, item.contentId)
        }

    /** Direct-play URL, or null when the server cannot direct-play (Silo). */
    fun streamUrl(server: MediaServer, item: ServerItem): String? = when (server.kind) {
        ServerKind.JELLYFIN, ServerKind.EMBY -> MediaBrowserClient.streamUrl(server, item)
        ServerKind.PLEX -> PlexClient.streamUrl(server, item)
        ServerKind.SILO -> null
    }

    /** Mark a title played/unplayed on its server. Best-effort, never throws. */
    suspend fun markPlayed(server: MediaServer, item: ServerItem, played: Boolean): Boolean =
        runCatching {
            when (server.kind) {
                ServerKind.JELLYFIN, ServerKind.EMBY ->
                    MediaBrowserClient.markPlayed(server, item.contentId, played)
                ServerKind.PLEX -> PlexClient.markPlayed(server, item, played)
                ServerKind.SILO -> SiloClient.setPlayed(server, item.contentId, played)
            }
        }.getOrDefault(false)

    /** Mark by videoId ("server:{serverId}:{contentId}") — the savePlayback hook. */
    suspend fun markPlayedByVideoId(
        servers: List<MediaServer>,
        videoId: String,
        season: Int,
        episode: Int,
        played: Boolean
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
        return markPlayed(server, item, played)
    }
}
