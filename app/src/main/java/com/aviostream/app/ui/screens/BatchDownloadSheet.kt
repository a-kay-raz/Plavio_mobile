package com.aviostream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.Video
import com.aviostream.app.data.download.DownloadManager
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import java.time.LocalDate

/**
 * Step 45 — Batch season download plan sheet (Nuvio BatchDownloadCoordinator
 * plan dialog, Avio shape): pick episodes of one season to download —
 * All / Unwatched / None quick actions, per-episode checkboxes, already
 * downloaded episodes shown as done and excluded from the pick.
 * Each chosen episode downloads its best stream (the autoplay rule) via
 * [com.aviostream.app.data.download.BatchDownloadQueuer]; the queue runs
 * in the background, progress lives in Downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchDownloadSheet(
    showName: String,
    showId: String,
    season: Int,
    episodes: List<Video>,
    watched: Set<Pair<Int, Int>>,
    onDownload: (List<Video>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val allDownloads by manager.observeAll().collectAsState(initial = emptyList())
    val downloaded = remember(allDownloads, showId, season) {
        allDownloads
            .filter { it.showKey == showId && it.season == season && it.status == "COMPLETED" }
            .map { it.episode }.toSet()
    }
    val downloadable = remember(episodes) {
        episodes.filter { it.season > 0 && it.episode > 0 && isReleased(it) }
            .sortedBy { it.episode }
    }
    var selectedIds by remember(downloadable) {
        mutableStateOf(downloadable.filter { it.episode !in downloaded }.map { it.id }.toSet())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = LocalPalette.current.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Download season $season",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = showName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TextButton(onClick = {
                    selectedIds = downloadable.filter { it.episode !in downloaded }.map { it.id }.toSet()
                }) { Text("All") }
                TextButton(onClick = {
                    selectedIds = downloadable
                        .filter { it.episode !in downloaded && (it.season to it.episode) !in watched }
                        .map { it.id }.toSet()
                }) { Text("Unwatched") }
                TextButton(onClick = { selectedIds = emptySet() }) { Text("None") }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${selectedIds.size} selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().height(320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(downloadable, key = { it.id }) { video ->
                    val isDone = video.episode in downloaded
                    val isWatched = (video.season to video.episode) in watched
                    val isSelected = video.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isDone) {
                                selectedIds = if (isSelected) selectedIds - video.id else selectedIds + video.id
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = isSelected,
                            enabled = !isDone,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + video.id else selectedIds - video.id
                            },
                        )
                        Column(Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(
                                text = "E${video.episode} · ${video.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val status = when {
                                isDone -> "Downloaded"
                                isWatched -> "Watched"
                                else -> video.released?.takeIf { it.isNotBlank() } ?: ""
                            }
                            if (status.isNotBlank()) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary,
                                )
                            }
                        }
                    }
                }
                if (downloadable.isEmpty()) {
                    item {
                        Text(
                            "No downloadable episodes in this season.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                }
            }
            Text(
                "Each episode downloads its best stream (your autoplay rule). Already-downloaded episodes are skipped.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = {
                    val chosen = downloadable.filter { it.id in selectedIds }
                    onDownload(chosen)
                },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = LocalPalette.current.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Download ${selectedIds.size} episode${if (selectedIds.size == 1) "" else "s"}",
                    color = Color(0xFF0A0A12),
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun isReleased(video: Video): Boolean {
    val released = video.released?.takeIf { it.isNotBlank() } ?: return true
    return runCatching {
        LocalDate.parse(released.take(10)) <= LocalDate.now()
    }.getOrDefault(true)
}
