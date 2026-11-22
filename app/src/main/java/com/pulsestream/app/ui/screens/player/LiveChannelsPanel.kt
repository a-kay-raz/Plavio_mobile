package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pulsestream.app.data.TvChannelRef
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Step 63 — Live-channels in-player panel (P32).
 * Scoped port of Nuvio player/PlayerLiveChannelsPanel.kt: search the
 * zap list, filter by catalog group, tap a channel to zap to it.
 * The list is the same one the Live tab handed over for zapping
 * (StreamRepository.pendingTvChannels), so it only ever shows the
 * channels visible there.
 *
 * Deviations from Nuvio: no favorites filter (Avio has no TV
 * channel favorites) and no now/next EPG rows (the zap list carries
 * no EPG ids — EPG lives in the Live tab via matchEpgToChannels).
 * Groups come from the channel's catalog name (TvChannelRef.group).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveChannelsPanel(
    channels: List<TvChannelRef>,
    currentVideoId: String,
    onChannelSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val groups = remember(channels) {
        channels.mapNotNull { it.group?.takeIf { g -> g.isNotBlank() } }.distinct().sorted()
    }
    val visibleIndexed = remember(channels, query, selectedGroup) {
        channels.mapIndexed { index, channel -> index to channel }
            .filter { (_, channel) ->
                (selectedGroup == null || channel.group == selectedGroup) &&
                    (query.isBlank() || channel.name.contains(query, ignoreCase = true))
            }
    }
    // Jump to the playing channel on open.
    LaunchedEffect(Unit) {
        val currentIndex = visibleIndexed.indexOfFirst { (_, channel) -> channel.metaId == currentVideoId }
        if (currentIndex > 0) listState.scrollToItem(currentIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = Color(0xFF14141F)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Text(
                "Channels",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search channels") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (groups.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedGroup == null,
                        onClick = { selectedGroup = null },
                        label = { Text("All") }
                    )
                    groups.forEach { group ->
                        FilterChip(
                            selected = selectedGroup == group,
                            onClick = { selectedGroup = group },
                            label = { Text(group, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            if (visibleIndexed.isEmpty()) {
                item {
                    Text(
                        "No channels match.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            items(visibleIndexed, key = { (index, _) -> "ch_$index" }) { (index, channel) ->
                val isCurrent = channel.metaId == currentVideoId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isCurrent) Color.White.copy(alpha = 0.08f) else Color.Transparent
                        )
                        .clickable { onChannelSelected(index) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (channel.poster != null) {
                        AsyncImage(
                            model = channel.poster,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                channel.name.take(1).uppercase(),
                                color = TextPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            channel.name,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        channel.group?.takeIf { it.isNotBlank() }?.let { group ->
                            Text(
                                group,
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (isCurrent) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Playing",
                            color = Color(0xFF4CAF50),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
