package com.pulsestream.app.data

import com.pulsestream.app.data.db.PlaybackState
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Step 49: Next Up on Home + release calendar (Nuvio
 * home/CachedNextUpRelease.kt + HomeScreen Next-Up resolution,
 * watchprogress/ContinueWatchingEnrichmentCache.kt, home/AirDateUtils.kt,
 * library/LibraryReleaseCalendarPanel, scoped port). Shared enrichment:
 * one meta fetch per series (cached in-process) feeds both surfaces.
 *
 * Seed = the latest watched episode per series, from WatchedMarks keys
 * ("e|<seriesId>|<season>|<episode>"). The next episode is the first
 * video after that seed in the series' own episode list. Air dates come
 * from the episode's `released` field (ISO date, "2024-03-01").
 */
data class NextUpEpisode(
    val showId: String,
    val showName: String,
    val poster: String?,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val episodeId: String,
    val airDate: LocalDate?,
    val overview: String?,
    val thumbnail: String?,
) {
    /** "S1 · E4" label for cards and calendar rows. */
    val episodeLabel: String get() = "S$season · E$episode"

    /** True when the episode has aired (or has no air date — watchable now). */
    fun hasAired(today: LocalDate = LocalDate.now()): Boolean =
        airDate == null || !airDate.isAfter(today)
}

data class ReleaseDay(
    val date: LocalDate,
    val episodes: List<NextUpEpisode>,
)

object NextUpResolver {
    private const val MAX_NEXT_UP_SERIES = 10
    private const val MAX_CALENDAR_SERIES = 15
    private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

    private val metaCache = mutableMapOf<String, Pair<Long, MetaDetail?>>()

    fun clearCache() {
        synchronized(metaCache) { metaCache.clear() }
    }

    /** Latest watched (season, episode) per series id. */
    fun latestWatchedBySeries(watchedKeys: Set<String>): Map<String, Pair<Int, Int>> {
        val latest = mutableMapOf<String, Pair<Int, Int>>()
        watchedKeys.forEach { key ->
            val parts = key.split("|")
            if (parts.size != 4 || parts[0] != "e") return@forEach
            val season = parts[2].toIntOrNull() ?: return@forEach
            val episode = parts[3].toIntOrNull() ?: return@forEach
            if (season <= 0 || episode <= 0) return@forEach
            val current = latest[parts[1]]
            if (current == null || season > current.first ||
                (season == current.first && episode > current.second)
            ) {
                latest[parts[1]] = season to episode
            }
        }
        return latest
    }

    private suspend fun metaFor(repository: StreamRepository, seriesId: String): MetaDetail? {
        val now = System.currentTimeMillis()
        synchronized(metaCache) {
            val cached = metaCache[seriesId]
            if (cached != null && now - cached.first < CACHE_TTL_MS) return cached.second
        }
        val meta = runCatching { repository.getMetaFromAny(null, "series", seriesId) }.getOrNull()
        synchronized(metaCache) { metaCache[seriesId] = now to meta }
        return meta
    }

    /** Step 69: the cached meta fetch, shared with ReleaseRadar. */
    suspend fun metaForSeries(repository: StreamRepository, seriesId: String): MetaDetail? =
        metaFor(repository, seriesId)

    /**
     * Most recently played episode per series, by resume-list time (Nuvio
     * "most recently watched episode" — the rewatch seed when the
     * furthest-episode preference is OFF). Joined on the show IMDb id, which is
     * the series key in [watchedKeys] for Cinemeta meta. Series with no
     * resume entry fall back to the furthest-watched seed.
     */
    fun latestPlayedBySeries(
        watchedKeys: Set<String>,
        playbackStates: List<PlaybackState>,
    ): Map<String, Pair<Int, Int>> {
        val furthest = latestWatchedBySeries(watchedKeys)
        if (playbackStates.isEmpty()) return furthest
        val latestPlayed = mutableMapOf<String, Pair<Pair<Int, Int>, Long>>()
        playbackStates.forEach { state ->
            val seriesId = state.imdbId ?: return@forEach
            if (seriesId !in furthest) return@forEach
            if (state.season <= 0 || state.episode <= 0) return@forEach
            val current = latestPlayed[seriesId]
            if (current == null || state.updatedAt > current.second) {
                latestPlayed[seriesId] = (state.season to state.episode) to state.updatedAt
            }
        }
        val seeds = furthest.toMutableMap()
        latestPlayed.forEach { (seriesId, seed) -> seeds[seriesId] = seed.first }
        return seeds
    }

    private fun parseAirDate(released: String?): LocalDate? {
        if (released.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(released.take(10)) }.getOrNull()
    }

    private fun nextEpisodeAfter(meta: MetaDetail, watched: Pair<Int, Int>): NextUpEpisode? {
        val (season, episode) = watched
        val next = meta.videos
            .filter { it.season > 0 && it.episode > 0 }
            .filter { it.season > season || (it.season == season && it.episode > episode) }
            .minWithOrNull(compareBy({ it.season }, { it.episode }))
            ?: return null
        return NextUpEpisode(
            showId = meta.id,
            showName = meta.name ?: meta.id,
            poster = meta.poster,
            season = next.season,
            episode = next.episode,
            episodeTitle = next.title,
            episodeId = next.id,
            airDate = parseAirDate(next.released),
            overview = next.overview,
            thumbnail = next.thumbnail,
        )
    }

    /**
     * "Next episode" cards: one per series with watched episodes, the
     * episode right after the seed episode. [preferFurthestEpisode] (Nuvio
     * upNextFromFurthestEpisode, default true) seeds from the highest
     * season/episode watched; false seeds from the most recently played
     * episode instead (rewatch behaviour — the next episode after the one
     * just played, even if a higher episode was watched before). Aired
     * episodes first (most recently aired first — watchable now), then
     * upcoming by air date. Series whose next episode can't be resolved
     * (meta fetch failed, no later episode) are skipped, never fatal.
     */
    suspend fun resolveNextUp(
        repository: StreamRepository,
        watchedKeys: Set<String>,
        preferFurthestEpisode: Boolean = true,
        playbackStates: List<PlaybackState> = emptyList(),
    ): List<NextUpEpisode> = coroutineScope {
        val seeds = if (preferFurthestEpisode) {
            latestWatchedBySeries(watchedKeys)
        } else {
            latestPlayedBySeries(watchedKeys, playbackStates)
        }
        val today = LocalDate.now()
        seeds.entries.take(MAX_NEXT_UP_SERIES).map { (seriesId, watched) ->
            async {
                val meta = metaFor(repository, seriesId) ?: return@async null
                nextEpisodeAfter(meta, watched)
            }
        }.awaitAll().filterNotNull().sortedWith(
            compareBy<NextUpEpisode> { !it.hasAired(today) }
                .thenByDescending { it.airDate }
                .thenBy { it.showName.lowercase() }
        )
    }

    /**
     * Release calendar: episodes airing in the next [daysAhead] days from
     * the library (watched series + saved series), unwatched, grouped by
     * air date, earliest first. Episodes with no air date never appear
     * here (they have nothing to calendar).
     */
    suspend fun resolveUpcomingReleases(
        repository: StreamRepository,
        watchedKeys: Set<String>,
        savedSeriesIds: List<String>,
        daysAhead: Int = 30,
    ): List<ReleaseDay> = coroutineScope {
        val today = LocalDate.now()
        val end = today.plusDays(daysAhead.toLong())
        val seeds = latestWatchedBySeries(watchedKeys)
        val seriesIds = (seeds.keys + savedSeriesIds).distinct().take(MAX_CALENDAR_SERIES)
        val episodes = seriesIds.map { seriesId ->
            async {
                val meta = metaFor(repository, seriesId) ?: return@async emptyList<NextUpEpisode>()
                val watched = seeds[seriesId]
                meta.videos
                    .filter { it.season > 0 && it.episode > 0 }
                    .filter { video ->
                        // Only episodes after the latest watched one (or all,
                        // for a saved-but-unwatched series).
                        watched == null ||
                            video.season > watched.first ||
                            (video.season == watched.first && video.episode > watched.second)
                    }
                    .mapNotNull { video ->
                        val airDate = parseAirDate(video.released) ?: return@mapNotNull null
                        if (airDate.isBefore(today) || airDate.isAfter(end)) return@mapNotNull null
                        if ("e|$seriesId|${video.season}|${video.episode}" in watchedKeys) return@mapNotNull null
                        NextUpEpisode(
                            showId = meta.id,
                            showName = meta.name ?: meta.id,
                            poster = meta.poster,
                            season = video.season,
                            episode = video.episode,
                            episodeTitle = video.title,
                            episodeId = video.id,
                            airDate = airDate,
                            overview = video.overview,
                            thumbnail = video.thumbnail,
                        )
                    }
            }
        }.awaitAll().flatten()
        episodes.groupBy { it.airDate!! }
            .map { (date, dayEpisodes) ->
                ReleaseDay(date, dayEpisodes.sortedWith(compareBy({ it.showName.lowercase() }, { it.season }, { it.episode })))
            }
            .sortedBy { it.date }
    }
}
