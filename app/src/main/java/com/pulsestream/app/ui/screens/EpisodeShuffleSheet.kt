package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.RandomEpisodePicker
import com.pulsestream.app.data.Video
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Step 41 — shuffle sheet (Nuvio shuffle/EpisodeShuffleSheet.kt,
 * Plavio-native): a random-episode preview with an include-watched toggle,
 * counts, "Shuffle again" (no-repeat session) and "Play random episode".
 * The picker lives with the caller (one session per Detail visit / player
 * request) so re-opens keep the no-repeat history.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeShuffleSheet(
    showName: String,
    picker: RandomEpisodePicker,
    includeWatched: Boolean,
    onIncludeWatchedChange: (Boolean) -> Unit,
    shuffleFollowing: Boolean,
    onShuffleFollowingChange: (Boolean) -> Unit,
    onPlay: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    var includeWatchedState by remember { mutableStateOf(includeWatched) }
    var preview by remember { mutableStateOf(picker.pick(includeWatched)) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalPalette.current.background,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Shuffle episodes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(showName, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Spacer(Modifier.height(12.dp))
            val unwatched = picker.unwatchedCount()
            val total = picker.totalCount()
            Text(
                when {
                    total == 0 -> "No episodes to shuffle yet."
                    unwatched == 0 -> "You're caught up — every episode is watched."
                    else -> "$unwatched unwatched of $total episodes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
            )
            Spacer(Modifier.height(12.dp))
            val picked = preview
            if (picked != null) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "S${picked.season}E${picked.episode}",
                            style = MaterialTheme.typography.labelLarge,
                            color = LocalPalette.current.accent,
                        )
                        if (picker.isWatched(picked)) {
                            Text(
                                "  •  Watched",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextSecondary,
                            )
                        }
                    }
                    Text(
                        picked.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    picked.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Text(
                            overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Include watched episodes",
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = includeWatchedState,
                    onCheckedChange = {
                        includeWatchedState = it
                        onIncludeWatchedChange(it)
                        preview = picker.pick(it)
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Shuffle following episodes",
                    color = TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = shuffleFollowing,
                    onCheckedChange = onShuffleFollowingChange,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { preview?.let(onPlay) },
                enabled = preview != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPalette.current.accent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Play random episode")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { preview = picker.pick(includeWatchedState) },
                enabled = picker.count(includeWatchedState) > 1,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Shuffle again")
            }
        }
    }
}
