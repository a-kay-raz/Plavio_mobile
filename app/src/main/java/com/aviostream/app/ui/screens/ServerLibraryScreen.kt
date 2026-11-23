package com.aviostream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.servers.MediaServerOps
import com.aviostream.app.data.servers.ServerItem
import com.aviostream.app.data.servers.ServerLibrary
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.OrbitDots
import com.aviostream.app.ui.components.PosterCard
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One drill-down level: the series/season tapped, and its loaded children. */
private data class DrillLevel(
    val title: String,
    val parent: ServerItem,
    val items: List<ServerItem>
)

/**
 * Step 55: server library browser. A poster grid of the library's
 * titles; tapping a series drills into its seasons/episodes, tapping
 * a movie or an episode plays it directly in the built-in player.
 * Silo titles cannot play yet (its playback session is not ported) —
 * the screen says so instead of failing silently.
 */
@Composable
fun ServerLibraryScreen(
    settings: SettingsStore,
    serverId: String,
    libraryId: String,
    onBack: () -> Unit,
    onPlay: (PlaybackRequest) -> Unit
) {
    val server = remember(serverId) {
        settings.getMediaServers().firstOrNull { it.id == serverId }
    }
    var library by remember { mutableStateOf<ServerLibrary?>(null) }
    var items by remember { mutableStateOf<List<ServerItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var stack by remember { mutableStateOf<List<DrillLevel>>(emptyList()) }

    LaunchedEffect(serverId, libraryId) {
        if (server == null) {
            error = "That server is no longer linked."
            loading = false
            return@LaunchedEffect
        }
        loading = true
        val libraries = withContext(Dispatchers.IO) {
            runCatching { MediaServerOps.getLibraries(server, settings) }.getOrNull()
        }.orEmpty()
        library = libraries.firstOrNull { it.id == libraryId }
        if (library == null) {
            error = "That library wasn't found on ${server.name}."
            loading = false
            return@LaunchedEffect
        }
        items = withContext(Dispatchers.IO) {
            runCatching { MediaServerOps.getLibraryItems(server, library!!, settings) }.getOrNull()
        }.orEmpty()
        loading = false
    }

    // Load children when the stack gains an unloaded level.
    LaunchedEffect(stack.size) {
        val level = stack.lastOrNull() ?: return@LaunchedEffect
        if (level.items.isNotEmpty() || server == null) return@LaunchedEffect
        val children = withContext(Dispatchers.IO) {
            runCatching { MediaServerOps.getSeriesChildren(server, level.parent, settings) }.getOrNull()
        }.orEmpty()
        stack = stack.dropLast(1) + level.copy(items = children)
    }

    fun playItem(item: ServerItem, seriesTitle: String? = null) {
        if (server == null) return
        val url = MediaServerOps.streamUrl(server, item)
        if (url == null) {
            notice = "Playback isn't available for Silo yet — its playback session still needs porting."
            return
        }
        val isEpisode = item.type == "episode"
        val title = if (isEpisode) {
            val series = seriesTitle ?: item.seriesTitle ?: item.title
            val se = listOfNotNull(
                item.season?.let { "S$it" },
                item.episode?.let { "E$it" }
            ).joinToString(" ")
            if (se.isNotBlank()) "$series $se" else item.title
        } else item.title
        onPlay(
            PlaybackRequest(
                streamUrl = url,
                title = title,
                poster = item.posterUrl,
                videoId = "server:${server.id}:${item.contentId}",
                imdbId = item.imdbId,
                season = item.season ?: 0,
                episode = item.episode ?: 0,
                isMovie = item.type == "movie",
                sourceName = server.name,
                seriesName = seriesTitle ?: item.seriesTitle,
                episodeTitle = if (isEpisode) item.title else null
            )
        )
    }

    val currentTitle = stack.lastOrNull()?.title ?: library?.name ?: "Library"
    val seriesTitle = stack.firstOrNull()?.title
    val inDrillDown = stack.isNotEmpty()
    val currentItems = stack.lastOrNull()?.items ?: items

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (stack.isNotEmpty()) stack = stack.dropLast(1)
                    else onBack()
                }) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = currentTitle,
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                    if (server != null) {
                        Text(
                            text = server.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            if (notice != null) {
                Text(
                    notice!!,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    OrbitDots()
                }
                error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = TextSecondary, modifier = Modifier.padding(24.dp))
                }
                inDrillDown -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (currentItems.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text("Nothing here yet.", color = TextSecondary)
                            }
                        }
                    }
                    items(currentItems, key = { it.contentId }) { child ->
                        val isSeason = child.type == "series" && child.season != null
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (isSeason) {
                                        stack = stack + DrillLevel("Season ${child.season}", child, emptyList())
                                    } else if (child.type == "series") {
                                        stack = stack + DrillLevel(child.title, child, emptyList())
                                    } else {
                                        playItem(child, seriesTitle = seriesTitle)
                                    }
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = when {
                                        isSeason -> "Season ${child.season}"
                                        child.type == "episode" -> {
                                            val se = listOfNotNull(
                                                child.season?.let { "S$it" },
                                                child.episode?.let { "E$it" }
                                            ).joinToString(" ")
                                            if (se.isNotBlank()) "$se — ${child.title}" else child.title
                                        }
                                        else -> child.title
                                    },
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                if (child.type == "episode" && child.played) {
                                    Text("Watched", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                }
                            }
                            Icon(
                                if (isSeason || child.type == "series") Icons.Filled.ChevronRight else Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = TextSecondary
                            )
                        }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
                items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("This library is empty.", color = TextSecondary)
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.contentId }) { item ->
                        PosterCard(
                            meta = MetaPreview(
                                id = "server:${server?.id}:${item.contentId}",
                                type = if (item.type == "series") "series" else "movie",
                                name = item.title,
                                poster = item.posterUrl,
                                releaseInfo = item.year?.toString()
                            ),
                            onClick = {
                                when (item.type) {
                                    "movie", "episode" -> playItem(item)
                                    "series" -> stack = stack + DrillLevel(item.title, item, emptyList())
                                }
                            }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }
}
