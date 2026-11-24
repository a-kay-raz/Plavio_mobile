package com.aviostream.app.ui.screens

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
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.SwapHoriz
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
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import com.aviostream.app.ui.tv.tvFocusRing

/**
 * The Engine settings page (Settings → Playback → Engine): which
 * engine plays videos, whether a fatal error retries once on the
 * other engine, and the MPV engine's own options. Reached from the
 * Playback page; the engine choice itself lives in exactly one
 * place — this page.
 */
@Composable
fun EngineSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings

    var enginePickerOpen by remember { mutableStateOf(false) }
    var autoFailover by remember { mutableStateOf(settings.autoEngineFailover) }
    var mpvHwdec by remember { mutableStateOf(settings.mpvHardwareDecoding) }
    var mpvYuv by remember { mutableStateOf(settings.mpvYuv420pFilter) }
    // Refresh key so the engine nav-row subtitle updates after
    // the picker dialog closes.
    var engineUiTick by remember { mutableStateOf(0) }

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
                            text = "Engine",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Which engine plays your videos, and how the second engine behaves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "engine_group") {
                // Read inside the item so recomposition picks up dialog changes.
                engineUiTick
                SettingsSectionHeader("Engine")
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.SmartDisplay,
                            title = "Playback engine",
                            subtitle = "Currently: ${playbackEngineName(settings.playbackEngine)}",
                            onClick = { enginePickerOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.SwapHoriz,
                            title = "Auto-switch engine on error",
                            subtitle = "If a stream fails, retry it once on the other engine at the same position",
                            checked = autoFailover,
                            onCheckedChange = {
                                autoFailover = it
                                settings.autoEngineFailover = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Memory,
                            title = "MPV hardware decoding",
                            subtitle = "Use the device's video chip in the MPV engine — faster and cooler",
                            checked = mpvHwdec,
                            onCheckedChange = {
                                mpvHwdec = it
                                settings.mpvHardwareDecoding = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Palette,
                            title = "MPV compatibility filter",
                            subtitle = "Fixes odd colours on some devices by converting the video in the MPV engine",
                            checked = mpvYuv,
                            onCheckedChange = {
                                mpvYuv = it
                                settings.mpvYuv420pFilter = it
                            }
                        )
                    },
                )
            }
        }
    }

    if (enginePickerOpen) {
        EnginePickerDialog(
            settings = settings,
            onDismiss = { enginePickerOpen = false },
            onDone = { enginePickerOpen = false; engineUiTick++ }
        )
    }
}

/** Display name for a stored playback-engine choice. */
fun playbackEngineName(engine: Int): String = when (engine) {
    1 -> "ExoPlayer"
    2 -> "MPV"
    else -> "Auto"
}

/**
 * Playback engine picker: Auto routes per stream (HDR / Dolby
 * Vision to ExoPlayer, anime to MPV, everything else to
 * ExoPlayer); the other two pin one engine for every stream.
 */
@Composable
fun EnginePickerDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.playbackEngine) }
    val options = listOf(
        Triple(0, "Auto", "Recommended — HDR and Dolby Vision use ExoPlayer, anime uses MPV, the rest use ExoPlayer"),
        Triple(1, "ExoPlayer", "The standard engine for every stream"),
        Triple(2, "MPV", "The specialist engine for every stream it can play")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.background,
        title = { Text("Playback engine", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Two engines are built in. Auto picks per stream; a failed stream retries once on the other engine when auto-switch is on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(options, key = { it.first }) { (value, label, detail) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = value }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == value,
                                onClick = { selected = value },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label, color = TextPrimary)
                                Text(
                                    detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.playbackEngine = selected
                onDone()
            }) {
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
