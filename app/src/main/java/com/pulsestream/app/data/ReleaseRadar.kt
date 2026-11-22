package com.pulsestream.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.pulsestream.app.data.db.WatchlistItem

/**
 * Step 69 — Release Radar ("New from your library" row on Home).
 * Port of Nuvio's `buildHomeReleaseRadarItems` (HomePremiumModels.kt)
 * — the data only, never the Premium/Labs paywall scaffolding it
 * lives in.
 *
 * Four legs, in Nuvio's order:
 * 1. Next Up episodes (Step 49) airing in the window.
 * 2. Library series' episodes airing in the window — full meta per
 *    series, via NextUpResolver's shared 6h cache (same enrichment
 *    pipeline as Step 49), watched episodes excluded.
 * 3. Library items' own release dates — SKIPPED: Avio's watchlist
 *    stores only a year (Step 47), too coarse for a 45-day window.
 * 4. Home catalog rows' items releasing in the window.
 *
 * Window: 7 days back .. 45 days ahead (Nuvio's constants), limit 18.
 * Sort: days weight (future first by days, past after: 45 + |days|),
 * then signal weight (NextUp 5, Episode 4, Series 3, Movie 2,
 * Catalog 1), then title — Nuvio's exact weights.
 */
enum class ReleaseRadarCategory { EPISODE, MOVIE, SERIES, NEXT_UP, CATALOG }

data class ReleaseRadarItem(
    val key: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    val releaseDate: LocalDate,
    val daysFromToday: Int,
    val category: ReleaseRadarCategory,
    val metaId: String,
    val metaType: String,
    val season: Int? = null,
    val episode: Int? = null,
)

object ReleaseRadar {
    const val UPCOMING_WINDOW_DAYS = 45
    const val RECENT_WINDOW_DAYS = 7
    const val ITEM_LIMIT = 18
    private const val MAX_LIBRARY_SERIES = 15

    suspend fun build(
        repository: StreamRepository,
        nextUp: List<NextUpEpisode>,
        watchlist: List<WatchlistItem>,
        catalogItems: List<MetaPreview>,
        watchedKeys: Set<String>,
        today: LocalDate = LocalDate.now(),
    ): List<ReleaseRadarItem> = coroutineScope {
        val items = mutableListOf<ReleaseRadarItem>()

        // Leg 1: Next Up episodes in the window.
        for (ep in nextUp) {
            val air = ep.airDate ?: continue
            val days = ChronoUnit.DAYS.between(today, air).toInt()
            if (days !in -RECENT_WINDOW_DAYS..UPCOMING_WINDOW_DAYS) continue
            items += ReleaseRadarItem(
                key = "nextup|${ep.showId}|${ep.season}|${ep.episode}",
                title = ep.showName,
                subtitle = listOfNotNull(ep.episodeLabel, ep.episodeTitle).joinToString(" · "),
                imageUrl = ep.thumbnail ?: ep.poster,
                releaseDate = air,
                daysFromToday = days,
                category = ReleaseRadarCategory.NEXT_UP,
                metaId = ep.showId,
                metaType = "series",
                season = ep.season,
                episode = ep.episode,
            )
        }

        // Leg 2: library series' episodes in the window (meta cached 6h
        // by NextUpResolver; watched episodes excluded).
        val seriesIds = watchlist
            .filter { it.type.isSeriesLikeType() }
            .map { it.metaId }
            .distinct()
            .take(MAX_LIBRARY_SERIES)
        val libraryEpisodes = seriesIds.map { seriesId ->
            async {
                val meta = NextUpResolver.metaForSeries(repository, seriesId) ?: return@async emptyList()
                meta.videos
                    .filter { it.season > 0 && it.episode > 0 }
                    .mapNotNull { video ->
                        val air = parseDate(video.released) ?: return@mapNotNull null
                        val days = ChronoUnit.DAYS.between(today, air).toInt()
                        if (days !in -RECENT_WINDOW_DAYS..UPCOMING_WINDOW_DAYS) return@mapNotNull null
                        if ("e|$seriesId|${video.season}|${video.episode}" in watchedKeys) return@mapNotNull null
                        ReleaseRadarItem(
                            key = "ep|$seriesId|${video.season}|${video.episode}",
                            title = meta.name ?: seriesId,
                            subtitle = listOfNotNull(
                                "S${video.season}E${video.episode}",
                                video.title?.takeIf { it.isNotBlank() }
                            ).joinToString(" · "),
                            imageUrl = video.thumbnail ?: meta.poster,
                            releaseDate = air,
                            daysFromToday = days,
                            category = ReleaseRadarCategory.EPISODE,
                            metaId = seriesId,
                            metaType = "series",
                            season = video.season,
                            episode = video.episode,
                        )
                    }
            }
        }.awaitAll().flatten()
        items += libraryEpisodes

        // Leg 4: home catalog rows' items releasing in the window.
        for (preview in catalogItems.distinctBy { "${it.type}|${it.id}" }) {
            val air = parseDate(preview.releaseInfo) ?: continue
            val days = ChronoUnit.DAYS.between(today, air).toInt()
            if (days !in 0..UPCOMING_WINDOW_DAYS) continue
            val isSeries = preview.type.isSeriesLikeType()
            items += ReleaseRadarItem(
                key = "cat|${preview.type}|${preview.id}",
                title = preview.name ?: preview.id,
                subtitle = if (isSeries) "New season" else null,
                imageUrl = preview.poster,
                releaseDate = air,
                daysFromToday = days,
                category = if (isSeries) ReleaseRadarCategory.SERIES else ReleaseRadarCategory.MOVIE,
                metaId = preview.id,
                metaType = preview.type,
            )
        }

        return@coroutineScope items
            .distinctBy { it.key }
            .sortedWith(
                compareBy<ReleaseRadarItem> { it.daysFromToday.sortWeight() }
                    .thenByDescending { it.category.signalWeight() }
                    .thenBy { it.title.lowercase() }
            )
            .take(ITEM_LIMIT)
    }

    private fun Int.sortWeight(): Int =
        if (this >= 0) this else UPCOMING_WINDOW_DAYS + kotlin.math.abs(this)

    private fun ReleaseRadarCategory.signalWeight(): Int = when (this) {
        ReleaseRadarCategory.NEXT_UP -> 5
        ReleaseRadarCategory.EPISODE -> 4
        ReleaseRadarCategory.SERIES -> 3
        ReleaseRadarCategory.MOVIE -> 2
        ReleaseRadarCategory.CATALOG -> 1
    }

    private fun parseDate(released: String?): LocalDate? {
        if (released.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(released.take(10)) }.getOrNull()
    }

    private fun String.isSeriesLikeType(): Boolean =
        trim().lowercase() in setOf("series", "show", "tv", "tvshow")

    /** "Today" / "Tomorrow" / "In 5d" / "5d ago" / "Mar 12" for cards. */
    fun dateLabel(item: ReleaseRadarItem, today: LocalDate = LocalDate.now()): String {
        return when (item.daysFromToday) {
            0 -> "Today"
            1 -> "Tomorrow"
            -1 -> "Yesterday"
            else -> if (item.daysFromToday > 1) {
                "In ${item.daysFromToday}d"
            } else if (item.daysFromToday < -1) {
                "${-item.daysFromToday}d ago"
            } else {
                item.releaseDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
            }
        }
    }
}
