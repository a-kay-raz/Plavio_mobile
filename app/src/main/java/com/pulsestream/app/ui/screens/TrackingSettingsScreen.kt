package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.WatchProgressPull
import com.pulsestream.app.data.WatchProgressPullResult
import com.pulsestream.app.data.WatchProgressSource
import com.pulsestream.app.data.tracking.TrackingLibrarySource
import com.pulsestream.app.data.tracking.TrackingProgressSource
import com.pulsestream.app.data.tracking.isSimklLinked
import com.pulsestream.app.data.tracking.isTraktLinked
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Step 52: tracking settings page (Nuvio settings/TrackingSettingsPage.kt,
 * scoped port). Picks which linked service feeds the Library, Up Next
 * progress and the ratings sheet. A single source that isn't linked falls
 * back to all linked services (library/progress) or Trakt (ratings) —
 * Nuvio's effective-source fallback.
 */
@Composable
fun TrackingSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit,
) {
    val settings = repository.settings
    // Bumped on every save so the effective-source notes recompose.
    var tick by remember { mutableIntStateOf(0) }

    var libraryPickerOpen by remember { mutableStateOf(false) }
    var progressPickerOpen by remember { mutableStateOf(false) }
    // Step 62: resume-position source + manual pull.
    var resumePickerOpen by remember { mutableStateOf(false) }
    val watchProgressPull = remember { WatchProgressPull(settings) }
    val remotePoints by watchProgressPull.points.collectAsState()
    val scope = rememberCoroutineScope()
    var pulling by remember { mutableStateOf(false) }
    var pullMessage by remember { mutableStateOf<String?>(null) }

    val traktLinked = remember(tick) { settings.isTraktLinked() }
    val simklLinked = remember(tick) { settings.isSimklLinked() }
    val librarySource = remember(tick) {
        TrackingLibrarySource.fromStored(settings.trackingLibrarySource)
    }
    val progressSource = remember(tick) {
        TrackingProgressSource.fromStored(settings.trackingProgressSource)
    }
    // Step 62: resume-position source (local / Trakt / Simkl).
    val resumeSource = remember(tick) {
        WatchProgressSource.fromStored(settings.watchProgressSource)
    }
    val lastPullMs = remember(tick) { settings.lastWatchProgressPullMs }

    fun linkedNote(source: String, linked: Boolean, fallback: String): String? =
        if (source != "all" && source != "local" && !linked) {
            "Not linked — showing $fallback instead."
        } else null

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
                Text(
                    text = "Tracking",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        text = "Linked services",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    TrackingLinkedRow(
                        icon = Icons.Filled.Sync,
                        title = "Trakt",
                        subtitle = "Sync watched history and progress",
                        linked = traktLinked
                    )
                    TrackingLinkedRow(
                        icon = Icons.Filled.CloudQueue,
                        title = "Simkl",
                        subtitle = "Sync watched history and progress",
                        linked = simklLinked
                    )
                    Text(
                        text = "Link services in Settings > Integrations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
                item {
                    TrackingPickerRow(
                        icon = Icons.Filled.Folder,
                        title = "Library source",
                        subtitle = "Which service's watchlist and lists show in the Library",
                        valueLabel = librarySource.label,
                        note = when (librarySource) {
                            TrackingLibrarySource.TRAKT -> linkedNote("trakt", traktLinked, "all linked services")
                            TrackingLibrarySource.SIMKL -> linkedNote("simkl", simklLinked, "all linked services")
                            else -> null
                        },
                        onClick = { libraryPickerOpen = true }
                    )
                }
                item {
                    TrackingPickerRow(
                        icon = Icons.Filled.PlayArrow,
                        title = "Up Next source",
                        subtitle = "Which service's watch progress feeds Up Next",
                        valueLabel = progressSource.label,
                        note = when (progressSource) {
                            TrackingProgressSource.TRAKT -> linkedNote("trakt", traktLinked, "all linked services")
                            TrackingProgressSource.SIMKL -> linkedNote("simkl", simklLinked, "all linked services")
                            else -> null
                        },
                        onClick = { progressPickerOpen = true }
                    )
                }
                // Step 62: resume-position source — where in-progress
                // playback positions come from when this device has none.
                item {
                    TrackingPickerRow(
                        icon = Icons.Filled.Refresh,
                        title = "Resume position source",
                        subtitle = "Pull in-progress positions from another device; local progress always wins when it exists",
                        valueLabel = resumeSource.label,
                        note = when (resumeSource) {
                            WatchProgressSource.TRAKT -> linkedNote("trakt", traktLinked, "local positions only")
                            WatchProgressSource.SIMKL -> linkedNote("simkl", simklLinked, "local positions only")
                            else -> null
                        },
                        onClick = { resumePickerOpen = true }
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (pulling) return@Button
                                pulling = true
                                pullMessage = null
                                scope.launch {
                                    val result = watchProgressPull.pullNow()
                                    pulling = false
                                    pullMessage = when (result) {
                                        is WatchProgressPullResult.Success ->
                                            "Pulled ${result.count} in-progress titles from ${result.source.label}."
                                        is WatchProgressPullResult.Error -> result.message
                                    }
                                    tick++
                                }
                            },
                            enabled = !pulling && resumeSource != WatchProgressSource.LOCAL
                        ) {
                            Text("Pull now")
                        }
                        if (pulling) {
                            CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
                        }
                    }
                    Text(
                        text = if (lastPullMs > 0) {
                            val whenText = java.time.Instant.ofEpochMilli(lastPullMs)
                                .atZone(java.time.ZoneId.systemDefault())
                                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, HH:mm"))
                            "Last pull: $whenText · ${remotePoints.size} in-progress titles stored"
                        } else if (remotePoints.isNotEmpty()) {
                            "${remotePoints.size} in-progress titles stored"
                        } else {
                            "Never pulled yet."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    pullMessage?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }

    if (libraryPickerOpen) {
        TrackingSingleSelectDialog(
            title = "Library source",
            options = TrackingLibrarySource.entries.map { it.stored to it.label },
            selected = librarySource.stored,
            onSelect = {
                settings.trackingLibrarySource = it
                tick++
                libraryPickerOpen = false
            },
            onDismiss = { libraryPickerOpen = false }
        )
    }
    if (progressPickerOpen) {
        TrackingSingleSelectDialog(
            title = "Up Next source",
            options = TrackingProgressSource.entries.map { it.stored to it.label },
            selected = progressSource.stored,
            onSelect = {
                settings.trackingProgressSource = it
                tick++
                progressPickerOpen = false
            },
            onDismiss = { progressPickerOpen = false }
        )
    }
    if (resumePickerOpen) {
        TrackingSingleSelectDialog(
            title = "Resume position source",
            options = WatchProgressSource.entries.map { it.stored to it.label },
            selected = resumeSource.stored,
            onSelect = {
                settings.watchProgressSource = it
                tick++
                resumePickerOpen = false
                // Switching to a remote source pulls immediately so the
                // positions are there when the next title plays.
                if (it != WatchProgressSource.LOCAL.stored) {
                    pulling = true
                    pullMessage = null
                    scope.launch {
                        val result = watchProgressPull.pullNow()
                        pulling = false
                        pullMessage = when (result) {
                            is WatchProgressPullResult.Success ->
                                "Pulled ${result.count} in-progress titles from ${result.source.label}."
                            is WatchProgressPullResult.Error -> result.message
                        }
                        tick++
                    }
                }
            },
            onDismiss = { resumePickerOpen = false }
        )
    }
}

/**
 * Picker rows, restyled 2026-11-22 to the app's standard settings row
 * (SettingsStaticRow): a GlassCard with 14dp inside and 5dp between
 * cards, icon tile on the left. The old plain rows had no card, no icon
 * and only 10dp vertical padding, so they sat tighter and flatter than
 * every other settings row. The LazyColumn already supplies the 16dp
 * horizontal inset, so the card itself adds none.
 */
@Composable
private fun TrackingPickerRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    valueLabel: String,
    note: String?,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        onClick = onClick
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = valueLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                if (note != null) {
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackingLinkedRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    linked: Boolean,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        onClick = null
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (linked) "Linked" else "Not linked",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (linked) LocalPalette.current.accent else TextSecondary
            )
        }
    }
}

@Composable
private fun TrackingSingleSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                itemsIndexed(options, key = { index, _ -> index }) { _, option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option.first) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.first == selected,
                            onClick = { onSelect(option.first) }
                        )
                        Text(option.second, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
