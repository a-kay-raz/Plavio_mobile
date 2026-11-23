package com.pulsestream.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Process-wide set of "watched" keys so any poster or episode card can
 * show the WATCHED pill without plumbing state through every screen.
 * Keys: "m|<metaId>" for a watched movie, "e|<seriesId>|<season>|<episode>"
 * for a watched episode. Fed by StreamRepository from the watched table.
 */
object WatchedMarks {
    val keys = MutableStateFlow<Set<String>>(emptySet())

    /** When each key was marked, for the series primary-action rule
     * (Nuvio shouldPreferResume: resume wins only if its progress is
     * newer than the latest completion mark). Fed by StreamRepository
     * alongside [keys]. */
    val watchedAt = MutableStateFlow<Map<String, Long>>(emptyMap())

    fun movieKey(metaId: String): String = "m|$metaId"
    fun episodeKey(seriesId: String, season: Int, episode: Int): String =
        "e|$seriesId|$season|$episode"
}
