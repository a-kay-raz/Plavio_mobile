package com.pulsestream.app.data

/** Stremio addon protocol data models (https://github.com/Stremio/stremio-addon-sdk). */

data class CatalogRef(
    val type: String,
    val id: String,
    val name: String,
    /**
     * Catalog "extra" keys the addon declares (e.g. "search", "genre").
     * A catalog supports the Stremio search extra only when "search" is here —
     * addons without it answer 404 to search= queries.
     */
    val extra: List<String> = emptyList(),
    /** Genre options the addon declares for this catalog (manifest {name:"genre", options:[...]}). Empty when the catalog has no genre extra. */
    val genreOptions: List<String> = emptyList()
) {
    val supportsSearch: Boolean get() = "search" in extra.map { it.lowercase() }
    val supportsGenre: Boolean get() = "genre" in extra.map { it.lowercase() }
    val supportsSkip: Boolean get() = "skip" in extra.map { it.lowercase() }
}

/** One browsable catalog for Discover (Step 20): addon + catalog identity + genre options. */
data class DiscoverCatalog(
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val type: String,
    val catalogId: String,
    val catalogName: String,
    val genreOptions: List<String> = emptyList()
) {
    /** Stable key for selection persistence ("addonId|type|catalogId"). */
    val key: String get() = "$addonId|$type|$catalogId"
}

/** One Discover page: the display items plus the raw fetched count (skip= counts raw items). */
data class DiscoverPage(val items: List<MetaPreview>, val rawCount: Int)

data class AddonManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val types: List<String>,
    val catalogs: List<CatalogRef>,
    val resources: List<String>,
    /** True when the manifest's behaviorHints mark it as configurable (has a /configure page). */
    val configurable: Boolean = false,
    /** True when behaviorHints.configurationRequired is set: the addon must be configured before use. Also shows the Configure action. */
    val configurationRequired: Boolean = false,
    /** Manifest logo URL — shown as the addon icon in the Addons tab. */
    val logo: String? = null
) {
    val hasCatalog: Boolean get() = resources.contains("catalog")
    val hasMeta: Boolean get() = resources.contains("meta")
    val hasStream: Boolean get() = resources.contains("stream")
    val hasSubtitles: Boolean get() = resources.contains("subtitles")
    val isFullAddon: Boolean get() = hasCatalog && hasMeta && hasStream
}

/**
 * Nuvio isTrackingAddon: a manifest exposing a "player" or "library"
 * resource receives the user's watch activity + library, so installing it
 * needs explicit consent (TrackingAddonConsentDialog).
 */
val AddonManifest.isTrackingAddon: Boolean
    get() = resources.contains("player") || resources.contains("library")

data class MetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    /** Original language code (catalog `originalLanguage`, e.g. "en") — shown on the hero meta line. */
    val language: String? = null,
    /** ISO release/first-air date (yyyy-MM-dd) when the source sends
     * one — drives the person page's Latest/Upcoming rails (Step 58). */
    val releaseDate: String? = null,
    /** TMDB-style popularity when the source sends it — drives the
     * person page's Popular rail (Step 58). */
    val popularity: Double? = null
)

data class Video(
    val id: String,
    val title: String,
    val season: Int = 0,
    val episode: Int = 0,
    val overview: String? = null,
    val thumbnail: String? = null,
    val released: String? = null
)

/** A cast member: TMDB person id (0 when unknown, e.g. keyless Cinemeta), name and photo. */
data class CastMember(
    val id: Int = 0,
    val name: String,
    val photo: String? = null,
    /** Best-known titles from the addon's /person-search (search rows
     * only; cast lists leave it null). */
    val knownFor: String? = null,
    /** Character played (TMDB credits / meta addons that serve it;
     * the second line under the name in the cast rail). */
    val character: String? = null
)

/** Actor filmography from PlavioMeta /person (Step 58: Nuvio
 * PersonDetail fields — biography/birthday/deathday/placeOfBirth/
 * knownFor arrive from PlavioMeta when it serves them, otherwise
 * StreamRepository merges them from TMDB when a key is set). */
data class PersonDetail(
    val id: Int,
    val name: String,
    val photo: String?,
    val movies: List<MetaPreview>,
    val series: List<MetaPreview>,
    val biography: String? = null,
    val birthday: String? = null,
    val deathday: String? = null,
    val placeOfBirth: String? = null,
    val knownFor: String? = null
)

/** A YouTube trailer reference from an addon's meta (PlavioMeta `trailerStreams`). */
data class TrailerStream(
    val title: String,
    val ytId: String
)

data class MetaDetail(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val videos: List<Video> = emptyList(),
    /** YouTube trailers (PlavioMeta `trailerStreams`); empty when the addon provides none. */
    val trailerStreams: List<TrailerStream> = emptyList(),
    /** Top-billed cast (PlavioMeta; names only in keyless/Cinemeta mode). */
    val cast: List<CastMember> = emptyList(),
    /** Extra facts for the Show Details tiles — present only when the meta addon provides them. */
    val runtime: String? = null,
    val status: String? = null,
    val certification: String? = null,
    val country: String? = null,
    val network: String? = null,
    /** Director names (PlavioMeta + Cinemeta serve `director`). */
    val director: List<String> = emptyList(),
    /** Writer names (Cinemeta serves `writer`; PlavioMeta serves none). */
    val writer: List<String> = emptyList()
)

data class StreamItem(
    val url: String,
    val name: String? = null,
    val title: String? = null,
    /** Name of the addon this stream came from (set when merging multi-addon results). */
    val sourceName: String? = null,
    /** Id of the addon this stream came from — used for star priority. */
    val sourceId: String? = null,
    /**
     * Extra HTTP request headers the stream URL needs (parsed from the
     * addon's behaviorHints.proxyHeaders.request). Sent by the player and
     * the stream prober. Empty for normal direct links.
     */
    val headers: Map<String, String> = emptyMap(),
    /**
     * Torrent info hash for P2P/debrid streams (parsed from the addon's
     * "infoHash" field). Null for direct HTTP streams. When set and the
     * stream has no usable URL, TorBox debrid can resolve it to a direct
     * HTTP link (only when a TorBox API key is configured).
     */
    val infoHash: String? = null,
    /** Index of the requested file within a torrent/file pack, when supplied by the addon. */
    val fileIdx: Int? = null,
    /** Exact torrent-relative file path, when supplied by the addon. */
    val filePath: String? = null,
    /** Optional stream MIME hint, from the stream or its behaviorHints. */
    val mimeType: String? = null,
    /** Optional Stremio video metadata retained for downstream playback decisions. */
    val videoSize: Long? = null,
    val filename: String? = null,
    /**
     * Step 60: the release group this stream belongs to — the addon's
     * behaviorHints.bingeGroup when sent, else the release group
     * derived from the filename (trailing -GROUP). Next-episode
     * autoplay prefers streams with the same group as the episode
     * just played, instead of re-ranking from scratch.
     */
    val bingeGroup: String? = null,
    /** Identity only: lets a cached HTTP link replace its pending row across progressive updates. */
    val resolvedInfoHash: String? = null,
    /**
     * Step 61: badges matched from the user's imported badge rules
     * (StreamBadgePresentation). Empty when no rules are imported.
     */
    val badges: List<StreamBadge> = emptyList()
)

data class Subtitle(
    val url: String,
    val lang: String
)

data class StreamResult(
    val streams: List<StreamItem>,
    val subtitles: List<Subtitle>
)

/** A catalog row shown on Home: one catalog of one addon with its items. */
data class CatalogRow(
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val catalogName: String,
    val type: String,
    val catalogId: String,
    val items: List<MetaPreview>
)

/** Cached meta used for search, tagged with its source addon. */
data class CachedMeta(
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val meta: MetaPreview
)

/** Result of a search plus its failure signal (Nuvio SearchEmptyStateReason.RequestFailed). */
data class SearchOutcome(val results: List<CachedMeta>, val remoteFailed: Boolean)

/**
 * Step 71: one search-results section per addon (Nuvio's orderedSections,
 * Plavio scope: per ADDON, not per catalog — one section per addon is the
 * user's own wording, and it matches how the remote fan-out already groups
 * by addon). The server leg ("Plavio Search") is its own section.
 */
data class SearchSection(
    val addonId: String,
    val addonName: String,
    val items: List<CachedMeta>
)

data class SearchSectionsOutcome(
    val sections: List<SearchSection>,
    val remoteFailed: Boolean
)

/** Everything the player needs, passed in-memory via the repository. */
data class PlaybackRequest(
    val streamUrl: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val subtitles: List<Subtitle> = emptyList(),
    /** IMDb id of the title (from Cinemeta metadata) — powers skip-segments + subtitle search. */
    val imdbId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val isMovie: Boolean = false,
    /** Content type for stream re-resolution ("movie"/"series"/"tv"). */
    val type: String = if (isMovie) "movie" else "series",
    /** Display label for the chosen stream, e.g. "1080p". */
    val streamLabel: String? = null,
    /** Addon the chosen stream came from. */
    val sourceName: String? = null,
    /** All fetched streams (in-player Sources switcher); may be empty for resumed items. */
    val streams: List<StreamItem> = emptyList(),
    /** Full episode list for series (in-player Episodes picker). */
    val episodes: List<Video> = emptyList(),
    /** Show/movie id (episode-switching context). */
    val metaId: String = "",
    /** Series name for the player header (null for movies). */
    val seriesName: String? = null,
    /** Episode title for the player header (series only). */
    val episodeTitle: String? = null,
    /** Extra HTTP request headers the stream URL needs (from the addon's proxyHeaders). */
    val headers: Map<String, String> = emptyMap(),
    /**
     * Trailer playback (Trailers tab): skips auto-play-next-episode, resume
     * position saving, and Trakt/Simkl scrobbling.
     */
    val isTrailer: Boolean = false,
    /** Optional separate audio track for video-only sources; normal playback is unchanged. */
    val mergeAudioUrl: String? = null,
    /**
     * Origin master playlist URL when [streamUrl] is a sanitized temp
     * playlist (Step 30 HLS variants): the HTTP factory uses this as the
     * origin so segment requests keep the stream's headers. Null for
     * normal streams.
     */
    val hlsMasterUrl: String? = null,
    /** Play from start (Nuvio detailActionSet): ignore the saved resume
     *  position and suppress the resume prompt — always start at 0:00.
     *  One-shot: the saved position is kept, not erased. */
    val playFromStart: Boolean = false,
    /** Optional addon-provided MIME hint; playback may still probe/self-heal it. */
    val mimeType: String? = null,
    /** Originating Live channel, retained for player zap/source re-resolution. */
    val liveChannel: TvChannelRef? = null
) {
    /** M3U uses type=tv; addon Live requests may use a custom resource type. */
    val isLive: Boolean get() = type == "tv" || liveChannel != null
}

/** One entry on the Trailers tab: catalog item + where to fetch its meta/trailers. */
data class TrailerItem(
    val addonId: String,
    val baseUrl: String,
    val type: String,
    val meta: MetaPreview
)

/** A Live TV channel reference for the player's channel-zap handoff. */
data class TvChannelRef(
    val addonId: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    /** Resource type used by the channel meta and /stream request. */
    val type: String = "tv",
    /** Origin catalog identity; addonId above identifies its owning addon. */
    val catalogId: String? = null,
    /** Catalog display name ("$provider — $group") for grouping in the in-player channels panel. */
    val group: String? = null
)

/** Pulls a quality tag like "1080p"/"4K" out of a stream's name/title, if present. */
fun streamQualityLabel(stream: StreamItem): String? {
    val text = listOfNotNull(stream.name, stream.title).joinToString(" ")
    return Regex("""\b(2160p|1440p|1080p|720p|480p|360p|4K)\b""", RegexOption.IGNORE_CASE)
        .find(text)?.value?.uppercase()
}
