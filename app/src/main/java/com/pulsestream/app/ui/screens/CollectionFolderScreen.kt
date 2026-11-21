package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.CollectionRepository
import com.pulsestream.app.data.CollectionSourceResolver
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Step 50: a collection folder's titles as a poster grid (Nuvio
 * collection/FolderDetailScreen.kt, scoped port). Sources resolve live
 * and merge in folder order, deduped by type|id.
 */
@Composable
fun CollectionFolderScreen(
    folderId: String,
    collectionRepository: CollectionRepository,
    repository: StreamRepository,
    onBack: () -> Unit,
    onOpenDetail: (String, String, String) -> Unit,
) {
    var folderTitle by remember { mutableStateOf("") }
    val sources by collectionRepository.observeSources(folderId)
        .collectAsState(initial = emptyList())
    var titles by remember { mutableStateOf<List<MetaPreview>?>(null) }

    LaunchedEffect(folderId) {
        folderTitle = collectionRepository.getFolder(folderId)?.title ?: ""
    }
    LaunchedEffect(folderId, sources) {
        if (sources.isEmpty()) {
            titles = emptyList()
        } else {
            titles = CollectionSourceResolver.resolveFolder(repository, sources)
        }
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = folderTitle.ifEmpty { "Folder" },
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                    titles?.let {
                        Text(
                            text = "${it.size} ${if (it.size == 1) "title" else "titles"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }

            when (val resolved = titles) {
                null -> {
                    Spacer(Modifier.height(32.dp))
                    OrbitDots()
                }
                emptyList<MetaPreview>() -> {
                    Text(
                        text = if (sources.isEmpty()) {
                            "This folder has no sources yet — add one from the editor."
                        } else {
                            "No titles resolved — check the folder's sources (a TMDB key or a linked Trakt account may be needed)."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(120.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(resolved, key = { "${it.type}|${it.id}" }) { preview ->
                            PosterCard(
                                meta = preview,
                                onClick = { onOpenDetail("", preview.type, preview.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
