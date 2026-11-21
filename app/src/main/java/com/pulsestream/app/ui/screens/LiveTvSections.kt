package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsestream.app.data.LiveTvChannel
import com.pulsestream.app.data.LiveTvOnDemandUiState
import com.pulsestream.app.data.LiveTvSportsEvent
import com.pulsestream.app.data.LiveTvSportsState
import com.pulsestream.app.data.LiveTvSportsUiState
import com.pulsestream.app.data.LiveTvVodEpisode
import com.pulsestream.app.data.LiveTvVodEpisodesState
import com.pulsestream.app.data.LiveTvVodItem
import com.pulsestream.app.data.LiveTvVodKind
import com.pulsestream.app.data.filterLiveTvVodItems
import com.pulsestream.app.data.formatSportsStart
import com.pulsestream.app.data.liveTvSportsLeagues
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Live TV Sports + On Demand sections (Nuvio `livetv/LiveTvSports.kt` +
 * `livetv/LiveTvOnDemand.kt` UI, LV3). Rendered by LiveScreen when the
 * section switcher picks Sports or On Demand; they live in this file so
 * LiveScreen.kt stays focused on channels.
 *
 * Sports: ESPN public scoreboards (no login). On Demand: Xtream VOD +
 * series from the Step 31 provider login.
 */

internal const val LIVE_TV_SPORTS_FILTER_ALL = "all"
internal const val LIVE_TV_SPORTS_FILTER_LIVE = "live"

/** FilterChip colors matching the Plavio dark aesthetic (same as LiveScreen's chipColors). */
@Composable
private fun liveTvSectionChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
    selectedLabelColor = TextPrimary,
    containerColor = GlassWhite,
    labelColor = TextSecondary,
    selectedLeadingIconColor = LocalPalette.current.accent
)

// ---------------------------------------------------------------------------------------------
// Sports
// ---------------------------------------------------------------------------------------------

@Composable
fun LiveTvSportsSection(
    sportsState: LiveTvSportsUiState,
    matchedChannels: Map<String, List<LiveTvChannel>>,
    filter: String,
    onFilterSelected: (String) -> Unit,
    onChannelClick: (LiveTvChannel) -> Unit,
    onRefresh: () -> Unit,
) {
    val leagues = remember(sportsState.events) {
        val present = sportsState.events.map { it.league.id }.toSet()
        liveTvSportsLeagues.filter { it.id in present }
    }
    val counts = remember(sportsState.events) {
        buildMap {
            put(LIVE_TV_SPORTS_FILTER_ALL, sportsState.events.size)
            put(LIVE_TV_SPORTS_FILTER_LIVE, sportsState.events.count { it.state == LiveTvSportsState.Live })
            sportsState.events.groupingBy { it.league.id }.eachCount().forEach { (id, count) -> put(id, count) }
        }
    }
    val hasLive = sportsState.events.any { it.state == LiveTvSportsState.Live }
    val visible = remember(sportsState.events, filter) {
        sportsState.events.filter { event ->
            when (filter) {
                LIVE_TV_SPORTS_FILTER_LIVE -> event.state == LiveTvSportsState.Live
                LIVE_TV_SPORTS_FILTER_ALL -> true
                else -> event.league.id == filter
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "sports|filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "sports|filter|all") {
                    FilterChip(
                        selected = filter == LIVE_TV_SPORTS_FILTER_ALL,
                        onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_ALL) },
                        label = { Text("All (${counts[LIVE_TV_SPORTS_FILTER_ALL] ?: 0})") },
                        colors = liveTvSectionChipColors()
                    )
                }
                if (hasLive) {
                    item(key = "sports|filter|live") {
                        FilterChip(
                            selected = filter == LIVE_TV_SPORTS_FILTER_LIVE,
                            onClick = { onFilterSelected(LIVE_TV_SPORTS_FILTER_LIVE) },
                            label = { Text("Live (${counts[LIVE_TV_SPORTS_FILTER_LIVE] ?: 0})") },
                            colors = liveTvSectionChipColors()
                        )
                    }
                }
                leagues.forEach { league ->
                    item(key = "sports|filter|${league.id}") {
                        FilterChip(
                            selected = filter == league.id,
                            onClick = { onFilterSelected(league.id) },
                            label = { Text("${league.label} (${counts[league.id] ?: 0})") },
                            colors = liveTvSectionChipColors()
                        )
                    }
                }
            }
        }
        if (sportsState.isLoading && !sportsState.hasLoaded) {
            item(key = "sports|loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = LocalPalette.current.accent, strokeWidth = 2.dp)
                }
            }
        } else if (visible.isEmpty()) {
            item(key = "sports|empty") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        sportsState.errorMessage
                            ?: "No matches in the next 48 hours. Scores refresh automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    if (sportsState.errorMessage != null) {
                        TextButton(onClick = onRefresh) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }
            }
        } else {
            items(visible, key = { "sports:${it.league.id}:${it.id}" }) { event ->
                LiveTvSportsEventCard(
                    event = event,
                    channels = matchedChannels["${event.league.id}:${event.id}"].orEmpty(),
                    nowMillis = System.currentTimeMillis(),
                    onChannelClick = onChannelClick
                )
            }
        }
        if (sportsState.errorMessage != null && visible.isNotEmpty()) {
            item(key = "sports|error") {
                Text(
                    "⚠ ${sportsState.errorMessage}",
                    color = Color(0xFFFFB74D),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun LiveTvSportsEventCard(
    event: LiveTvSportsEvent,
    channels: List<LiveTvChannel>,
    nowMillis: Long,
    onChannelClick: (LiveTvChannel) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = { channels.firstOrNull()?.let(onChannelClick) },
        enabled = channels.isNotEmpty(),
        color = LocalPalette.current.panel,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = event.league.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LiveTvSportsStatus(event = event, nowMillis = nowMillis)
            }
            if (event.competitors.size >= 2) {
                event.competitors.forEach { competitor ->
                    LiveTvSportsCompetitorRow(
                        competitor = competitor,
                        showScore = event.state != LiveTvSportsState.Upcoming,
                        dimmed = event.state == LiveTvSportsState.Finished &&
                            event.competitors.any { it.isWinner } && !competitor.isWinner,
                    )
                }
            } else {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (event.broadcasts.isNotEmpty()) {
                Text(
                    text = "On ${event.broadcasts.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (channels.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    channels.forEach { channel ->
                        LiveTvSportsChannelChip(channel = channel, onClick = { onChannelClick(channel) })
                    }
                }
            } else if (event.state != LiveTvSportsState.Finished) {
                Text(
                    text = "No channel found for this match.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun LiveTvSportsStatus(event: LiveTvSportsEvent, nowMillis: Long) {
    when (event.state) {
        LiveTvSportsState.Live -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
            if (event.statusDetail.isNotBlank()) {
                Text(
                    text = event.statusDetail,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    maxLines = 1,
                )
            }
        }

        LiveTvSportsState.Upcoming -> Text(
            text = formatSportsStart(event.startMillis, nowMillis),
            style = MaterialTheme.typography.labelMedium,
            color = LocalPalette.current.accent,
            fontWeight = FontWeight.SemiBold,
        )

        LiveTvSportsState.Finished -> Text(
            text = event.statusDetail.ifBlank { formatSportsStart(event.startMillis, nowMillis) },
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
        )
    }
}

@Composable
private fun LiveTvSportsCompetitorRow(
    competitor: com.pulsestream.app.data.LiveTvSportsCompetitor,
    showScore: Boolean,
    dimmed: Boolean,
) {
    val textColor = if (dimmed) TextSecondary else TextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!competitor.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = competitor.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.SportsSoccer,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            text = competitor.name,
            style = MaterialTheme.typography.titleSmall,
            color = textColor,
            fontWeight = if (competitor.isWinner) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showScore && !competitor.score.isNullOrBlank()) {
            Text(
                text = competitor.score,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun LiveTvSportsChannelChip(
    channel: LiveTvChannel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LocalPalette.current.accent.copy(alpha = 0.15f))
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(GlassWhite),
            contentAlignment = Alignment.Center,
        ) {
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelLarge,
            color = LocalPalette.current.accent,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------------------------------------
// On Demand
// ---------------------------------------------------------------------------------------------

@Composable
fun LiveTvOnDemandSection(
    vodState: LiveTvOnDemandUiState,
    kind: LiveTvVodKind,
    onKindSelected: (LiveTvVodKind) -> Unit,
    categoryId: String?,
    onCategorySelected: (String?) -> Unit,
    searchQuery: String,
    xtreamConfigured: Boolean,
    onGoToLiveTvSettings: () -> Unit,
    onMovieClick: (LiveTvVodItem) -> Unit,
    onSeriesClick: (LiveTvVodItem) -> Unit,
    onRefresh: () -> Unit,
) {
    if (!xtreamConfigured) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "On Demand needs an Xtream login",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Add your Xtream Codes provider in Live TV settings — movies and series from that panel appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onGoToLiveTvSettings) {
                Text("Open Live TV settings", color = LocalPalette.current.accent)
            }
        }
        return
    }
    val catalogState = vodState.catalog(kind)
    val filtered = remember(catalogState.catalog.items, categoryId, searchQuery) {
        filterLiveTvVodItems(catalogState.catalog.items, categoryId, searchQuery)
    }
    val counts = remember(catalogState.catalog.items) {
        buildMap<String, Int> {
            put(" all", catalogState.catalog.items.size)
            catalogState.catalog.items.groupingBy { it.categoryId }.eachCount()
                .forEach { (id, count) -> if (id != null) put(id, count) }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "vod|kind") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == LiveTvVodKind.Movies,
                    onClick = { onKindSelected(LiveTvVodKind.Movies) },
                    label = { Text("Movies") },
                    colors = liveTvSectionChipColors()
                )
                FilterChip(
                    selected = kind == LiveTvVodKind.Series,
                    onClick = { onKindSelected(LiveTvVodKind.Series) },
                    label = { Text("Series") },
                    colors = liveTvSectionChipColors()
                )
            }
        }
        if (catalogState.catalog.categories.isNotEmpty()) {
            item(key = "vod|cats") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "vod|cat|all") {
                        FilterChip(
                            selected = categoryId == null,
                            onClick = { onCategorySelected(null) },
                            label = { Text("All (${counts[" all"] ?: 0})") },
                            colors = liveTvSectionChipColors()
                        )
                    }
                    catalogState.catalog.categories.forEach { category ->
                        item(key = "vod|cat|${category.id}") {
                            FilterChip(
                                selected = categoryId == category.id,
                                onClick = { onCategorySelected(category.id) },
                                label = { Text("${category.name} (${counts[category.id] ?: 0})") },
                                colors = liveTvSectionChipColors()
                            )
                        }
                    }
                }
            }
        }
        when {
            catalogState.isLoading && !catalogState.hasLoaded -> {
                item(key = "vod|loading") {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = LocalPalette.current.accent, strokeWidth = 2.dp)
                    }
                }
            }
            catalogState.errorMessage != null -> {
                item(key = "vod|error") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Couldn't load ${if (kind == LiveTvVodKind.Movies) "movies" else "series"}: ${catalogState.errorMessage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        TextButton(onClick = onRefresh) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }
            }
            filtered.isEmpty() -> {
                item(key = "vod|empty") {
                    Text(
                        if (searchQuery.isNotBlank() || categoryId != null) "Nothing matches."
                        else "No ${if (kind == LiveTvVodKind.Movies) "movies" else "series"} on this panel yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            else -> {
                val rows = filtered.chunked(3)
                items(rows.size, key = { index -> "vod-grid:3:${rows[index].first().id}" }) { index ->
                    val row = rows[index]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->
                            LiveTvVodPosterCell(
                                item = item,
                                onClick = {
                                    if (item.kind == LiveTvVodKind.Movies) onMovieClick(item)
                                    else onSeriesClick(item)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveTvVodPosterCell(
    item: LiveTvVodItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(GlassWhite),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(28.dp),
            )
            if (!item.posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.posterUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            item.rating?.let { rating ->
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = formatVodRating(rating),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        item.year?.let { year ->
            Text(
                text = year,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
            )
        }
    }
}

private fun formatVodRating(rating: Double): String {
    val normalized = if (rating > 10.0) rating / 10.0 else rating
    val tenths = (normalized * 10).toInt()
    return "${tenths / 10}.${tenths % 10}"
}

// ---------------------------------------------------------------------------------------------
// Series sheet
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvSeriesSheet(
    series: LiveTvVodItem,
    episodesState: LiveTvVodEpisodesState,
    onEpisodeClick: (LiveTvVodEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val dismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }
    val seasons = remember(episodesState.episodes) { episodesState.episodes.map { it.season }.distinct() }
    var selectedSeason by rememberSaveable(series.id) { mutableStateOf<Int?>(null) }
    val activeSeason = selectedSeason?.takeIf { it in seasons } ?: seasons.firstOrNull()
    val seasonEpisodes = remember(episodesState.episodes, activeSeason) {
        episodesState.episodes.filter { it.season == activeSeason }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalPalette.current.panel,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(84.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GlassWhite),
                    ) {
                        if (!series.posterUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = series.posterUrl,
                                contentDescription = series.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = series.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                        series.year?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        series.plot?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 5,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            if (seasons.size > 1) {
                item(key = "seasons") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        seasons.forEach { season ->
                            item(key = "season|$season") {
                                FilterChip(
                                    selected = season == activeSeason,
                                    onClick = { selectedSeason = season },
                                    label = { Text("Season $season") },
                                    colors = liveTvSectionChipColors()
                                )
                            }
                        }
                    }
                }
            }
            when {
                episodesState.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = LocalPalette.current.accent, strokeWidth = 2.dp)
                    }
                }

                episodesState.errorMessage != null || seasonEpisodes.isEmpty() -> item(key = "empty") {
                    Text(
                        text = episodesState.errorMessage ?: "No episodes found for this series.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }

                else -> items(seasonEpisodes, key = { "episode:${it.id}" }) { episode ->
                    LiveTvEpisodeRow(
                        episode = episode,
                        onClick = {
                            onEpisodeClick(episode)
                            dismiss()
                        },
                    )
                }
            }
            item(key = "bottom-space") { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun LiveTvEpisodeRow(
    episode: LiveTvVodEpisode,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        onClick = onClick,
        color = LocalPalette.current.panel,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(GlassWhite),
                contentAlignment = Alignment.Center,
            ) {
                if (!episode.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = episode.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Episode ${episode.episodeNumber}: ${episode.title}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                episode.durationText?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
                episode.plot?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
