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
import com.pulsestream.app.data.LiveTvOnDemandUiState
import com.pulsestream.app.data.LiveTvVodEpisode
import com.pulsestream.app.data.LiveTvVodEpisodesState
import com.pulsestream.app.data.LiveTvVodItem
import com.pulsestream.app.data.LiveTvVodKind
import com.pulsestream.app.data.filterLiveTvVodItems
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Live TV On Demand section (Nuvio `livetv/LiveTvOnDemand.kt` UI, LV3).
 * Rendered by LiveScreen when the section switcher picks On Demand; it
 * lives in this file so LiveScreen.kt stays focused on channels.
 *
 * On Demand: Xtream VOD + series from the Step 31 provider login.
 */


/** FilterChip colors matching the Avio dark aesthetic (same as LiveScreen's chipColors). */
@Composable
private fun liveTvSectionChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
    selectedLabelColor = TextPrimary,
    containerColor = GlassWhite,
    labelColor = TextSecondary,
    selectedLeadingIconColor = LocalPalette.current.accent
)

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
