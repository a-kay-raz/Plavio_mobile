package com.pulsestream.app.data

/**
 * Curated What's New list — pointwise user-facing changes only, no
 * commits. The What's New screen shows exactly this list for the
 * current version, so update it with each release: replace the points
 * with the new version's changes.
 */
object WhatsNew {
    /** One user-facing change, shown as a single bullet. */
    val changes: List<String> = listOf(
        "CloudStream plugins — add a plugin repo and install providers from Settings.",
        "Home hero with Play and Details — start a movie or the next episode straight from the hero.",
        "Continue Watching, restyled — episode info, time left, and progress right on the card.",
        "Continue Watching — long-press a poster for Details, Play from beginning, Mark as watched, or Remove.",
        "Hero trailers play like Nuvio — tap to mute, they pause when you scroll away, and fill the hero vertically.",
        "Settings reorganised — Appearance, Layout, Content & Discovery, Playback, Downloads and more, with search.",
        "Smart Streams — results are ranked so the best source comes first.",
        "Downloads manager — per-show downloads with resolution and concurrency controls, plus a custom location.",
        "Live TV — Xtream Codes, Stalker Portal and M3U playlists, with EPG.",
        "Trakt and Simkl — two-way watch-history sync and scrobbling.",
        "Media servers — Jellyfin, Emby, Plex and Silo libraries in one place.",
        "Debrid — Real-Debrid, Premiumize and TorBox for cached streams.",
        "Profiles — local multi-profile with optional PIN, each with its own library.",
        "Library — Saved and Downloaded views, filters, sorting and collections.",
        "Player — skip seconds, swipe gestures, pinch zoom, hold-to-speed, audio boost and subtitle sync.",
        "Cast with profile photos and character names on the details page.",
        "Cinemeta is now the default metadata source, with TMDB enrichment.",
    )
}
