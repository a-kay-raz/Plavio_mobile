package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing

/**
 * Continue Watching settings (Nuvio ContinueWatchingPreferencesRepository):
 * section visibility, card style, sort mode, tap action, the resume
 * prompt on launch, and the Next Up behaviour — prefer episode
 * thumbnails, furthest-episode seed, and unaired next-ups.
 */
@Composable
fun ContinueWatchingSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var visible by remember { mutableStateOf(settings.continueWatchingVisible) }
    var resumePrompt by remember { mutableStateOf(settings.continueWatchingResumePrompt) }
    var preferEpisodeThumbnails by remember { mutableStateOf(settings.continueWatchingPreferEpisodeThumbnails) }
    var nextUpFromFurthest by remember { mutableStateOf(settings.continueWatchingNextUpFromFurthestEpisode) }
    var showUnairedNextUp by remember { mutableStateOf(settings.continueWatchingShowUnairedNextUp) }
    var styleOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var clickOpen by remember { mutableStateOf(false) }
    // Refresh key so the option-row subtitles update after the dialogs close.
    var uiTick by remember { mutableStateOf(0) }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Continue Watching",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How the resume row on Home looks and behaves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "group") {
                // Read inside the item so recomposition picks up dialog changes.
                uiTick
                SettingsGroup(
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Visibility,
                            title = "Show Continue Watching",
                            subtitle = "The resume row on Home",
                            checked = visible,
                            onCheckedChange = {
                                visible = it
                                settings.continueWatchingVisible = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.ViewModule,
                            title = "Card style",
                            subtitle = cwStyleLabel(settings.continueWatchingStyle),
                            onClick = { styleOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Sort,
                            title = "Sort",
                            subtitle = cwSortLabel(settings.continueWatchingSortMode),
                            onClick = { sortOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.TouchApp,
                            title = "Tap action",
                            subtitle = cwClickLabel(settings.continueWatchingClickAction),
                            onClick = { clickOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Image,
                            title = "Prefer episode thumbnails",
                            subtitle = "Use episode stills instead of show posters in Next Up cards",
                            checked = preferEpisodeThumbnails,
                            onCheckedChange = {
                                preferEpisodeThumbnails = it
                                settings.continueWatchingPreferEpisodeThumbnails = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.SkipNext,
                            title = "Next Up from furthest episode",
                            subtitle = "Base the next episode on the furthest watched one — off uses the most recently played episode instead",
                            checked = nextUpFromFurthest,
                            onCheckedChange = {
                                nextUpFromFurthest = it
                                settings.continueWatchingNextUpFromFurthestEpisode = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Schedule,
                            title = "Show unaired Next Up",
                            subtitle = "Include upcoming episodes in Next Up before they air",
                            checked = showUnairedNextUp,
                            onCheckedChange = {
                                showUnairedNextUp = it
                                settings.continueWatchingShowUnairedNextUp = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.PlayArrow,
                            title = "Resume prompt on launch",
                            subtitle = "Ask to resume when the app was closed while playing",
                            checked = resumePrompt,
                            onCheckedChange = {
                                resumePrompt = it
                                settings.continueWatchingResumePrompt = it
                            }
                        )
                    }
                )
            }
        }
    }

    if (styleOpen) {
        CwOptionsDialog(
            title = "Card style",
            description = "How resume cards look in the Home row.",
            options = listOf(
                "card" to "Card — wide thumbnail with progress",
                "wide" to "Wide — horizontal card with progress",
                "poster" to "Poster — tall poster card"
            ),
            selected = settings.continueWatchingStyle,
            onDismiss = { styleOpen = false },
            onDone = { settings.continueWatchingStyle = it; styleOpen = false; uiTick++ }
        )
    }

    if (sortOpen) {
        CwOptionsDialog(
            title = "Sort",
            description = "The order of the resume row on Home.",
            options = listOf(
                "default" to "Default — most recently watched first",
                "streaming" to "Streaming-style — unfinished first",
                "split_upcoming" to "Split upcoming — unaired next-ups in their own row"
            ),
            selected = settings.continueWatchingSortMode,
            onDismiss = { sortOpen = false },
            onDone = { settings.continueWatchingSortMode = it; sortOpen = false; uiTick++ }
        )
    }

    if (clickOpen) {
        CwOptionsDialog(
            title = "Tap action",
            description = "What happens when you tap a resume card.",
            options = listOf(
                "stream" to "Play the stream",
                "details" to "Open details"
            ),
            selected = settings.continueWatchingClickAction,
            onDismiss = { clickOpen = false },
            onDone = { settings.continueWatchingClickAction = it; clickOpen = false; uiTick++ }
        )
    }
}

private fun cwStyleLabel(style: String): String = when (style) {
    "wide" -> "Wide — larger thumbnail"
    "poster" -> "Poster — tall poster card"
    else -> "Card — wide thumbnail with progress"
}

private fun cwSortLabel(mode: String): String = when (mode) {
    "streaming" -> "Streaming-style — unfinished first"
    "split_upcoming" -> "Split upcoming — unaired next-ups in their own row"
    else -> "Default — most recently watched first"
}

private fun cwClickLabel(action: String): String = when (action) {
    "details" -> "Open details"
    else -> "Play the stream"
}

/** Generic radio-list picker for the Continue Watching option rows. */
@Composable
private fun CwOptionsDialog(
    title: String,
    description: String,
    options: List<Pair<String, String>>,
    selected: String,
    onDismiss: () -> Unit,
    onDone: (String) -> Unit
) {
    var chosen by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.background,
        title = { Text(title, color = TextPrimary) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(options, key = { it.first }) { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosen = value }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = chosen == value,
                                onClick = { chosen = value },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(chosen) }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
