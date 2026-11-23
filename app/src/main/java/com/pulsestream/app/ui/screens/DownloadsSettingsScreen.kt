package com.pulsestream.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.download.DownloadManager
// DownloadServiceStarter lives in DownloadsScreen.kt, same package — no import.
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.theme.LocalPalette

/**
 * Downloads settings (Step 35 — Nuvio DownloadsSettingsRepository +
 * DownloadPreferredResolution + DownloadConcurrencyLimit).
 *
 * - Wi-Fi only: the pref already existed in DownloadManager; this surface
 *   just exposes it (same toggle as the Downloads screen).
 * - Show download button: hides the download icon on stream rows.
 * - Preferred resolution: the default quality the confirm dialog offers
 *   for HLS streams (720p/1080p/2160p, default 1080p).
 * - Max simultaneous downloads: 1–5, default 3 — rebuilds the manager's
 *   permit pool when changed.
 * - Max concurrent shows (1–5, default 1) + max episodes per series
 *   (1–5, default 1): the Step-36-deferred caps (Nuvio
 *   DownloadConcurrencyLimit.ConcurrentShows / EpisodesPerShow),
 *   enforced by the manager's show-slot + per-show semaphore queue.
 *
 * Row order follows Nuvio's DownloadsSettingsPage: resolution, the three
 * concurrency caps, Wi-Fi only, show button, location.
 *
 * Presentation (§2 of the v1.17 gap report) comes from the shared
 * components — 36dp icon tile, gradient switch, 12sp muted section
 * header, 0.5dp dividers inset 66dp.
 */
@Composable
fun DownloadsSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val settings = repository.settings

    var wifiOnly by remember { mutableStateOf(manager.isWifiOnlyDownloads()) }
    var showButton by remember { mutableStateOf(settings.showDownloadButton) }
    var resolution by remember { mutableIntStateOf(manager.getDownloadPreferredResolution()) }
    var concurrency by remember { mutableIntStateOf(manager.getDownloadConcurrencyLimit()) }
    var maxShows by remember { mutableIntStateOf(manager.getMaxConcurrentShows()) }
    var maxEpisodes by remember { mutableIntStateOf(manager.getMaxEpisodesPerShow()) }
    var resolutionOpen by remember { mutableStateOf(false) }
    var concurrencyOpen by remember { mutableStateOf(false) }
    var showsOpen by remember { mutableStateOf(false) }
    var episodesOpen by remember { mutableStateOf(false) }
    // Step 37: custom download folder (SAF) — system folder picker, the
    // permission survives restarts via takePersistableUriPermission.
    var locationName by remember { mutableStateOf(manager.downloadLocationDisplayName()) }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            manager.setDownloadLocationUri(uri.toString())
            locationName = manager.downloadLocationDisplayName()
        }
    }

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
                            text = "Downloads",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How downloads behave — quality, speed and when they run.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        // Aligned with the title text (16dp screen pad +
                        // 48dp back button + 4dp gap = 68dp from the edge).
                        modifier = Modifier.padding(start = 52.dp, end = 8.dp)
                    )
                }
            }
            item(key = "group") {
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.HighQuality,
                            title = "Preferred resolution",
                            subtitle = "${resolution}p — the quality offered for HLS downloads",
                            onClick = { resolutionOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Layers,
                            title = "Simultaneous downloads",
                            subtitle = "$concurrency at a time",
                            onClick = { concurrencyOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.VideoLibrary,
                            title = "Max concurrent shows",
                            subtitle = if (maxShows == 1) "1 show at a time" else "$maxShows shows at a time",
                            onClick = { showsOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.PlaylistPlay,
                            title = "Max episodes per series",
                            subtitle = if (maxEpisodes == 1) "1 episode of a series at a time" else "$maxEpisodes episodes of a series at a time",
                            onClick = { episodesOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Wifi,
                            title = "Wi-Fi only",
                            subtitle = "Pause downloads until Wi-Fi is available",
                            checked = wifiOnly,
                            onCheckedChange = {
                                wifiOnly = it
                                manager.setWifiOnlyDownloads(it)
                                if (!it) {
                                    manager.resumeQueuedDownloads()
                                    DownloadServiceStarter.start(context)
                                }
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Download,
                            title = "Show download button",
                            subtitle = "The download icon on stream rows",
                            checked = showButton,
                            onCheckedChange = {
                                showButton = it
                                settings.showDownloadButton = it
                            }
                        )
                    },
                    {
                        // Step 37: custom download folder — new downloads
                        // move there on completion; old files stay reachable.
                        SettingsNavRow(
                            icon = Icons.Filled.Folder,
                            title = "Download location",
                            subtitle = locationName ?: "App folder (default)",
                            onClick = { folderPicker.launch(null) }
                        )
                    },
                    {
                        if (locationName != null) {
                            Row(
                                // Aligned with the rows' text column (16dp
                                // group pad + 36dp icon tile + 14dp gap).
                                modifier = Modifier.padding(start = 50.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                            ) {
                                TextButton(onClick = {
                                    manager.setDownloadLocationUri(null)
                                    locationName = null
                                }) { Text("Reset to app folder") }
                            }
                        }
                    }
                )
            }
        }
    }

    if (resolutionOpen) {
        AlertDialog(
            onDismissRequest = { resolutionOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Preferred resolution", color = TextPrimary) },
            text = {
                Column(Modifier.selectableGroup()) {
                    DownloadManager.RESOLUTION_OPTIONS.forEach { height ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = resolution == height,
                                    onClick = {
                                        resolution = height
                                        manager.setDownloadPreferredResolution(height)
                                        resolutionOpen = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = resolution == height,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${height}p", color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { resolutionOpen = false }) { Text("Done") }
            }
        )
    }

    if (concurrencyOpen) {
        AlertDialog(
            onDismissRequest = { concurrencyOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Simultaneous downloads", color = TextPrimary) },
            text = {
                Column(Modifier.selectableGroup()) {
                    (1..5).forEach { limit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = concurrency == limit,
                                    onClick = {
                                        concurrency = limit
                                        manager.setDownloadConcurrencyLimit(limit)
                                        concurrencyOpen = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = concurrency == limit,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("$limit at a time", color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { concurrencyOpen = false }) { Text("Done") }
            }
        )
    }

    if (showsOpen) {
        AlertDialog(
            onDismissRequest = { showsOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Max concurrent shows", color = TextPrimary) },
            text = {
                Column(Modifier.selectableGroup()) {
                    (1..5).forEach { limit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = maxShows == limit,
                                    onClick = {
                                        maxShows = limit
                                        manager.setMaxConcurrentShows(limit)
                                        showsOpen = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = maxShows == limit,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (limit == 1) "1 show at a time" else "$limit shows at a time",
                                color = TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showsOpen = false }) { Text("Done") }
            }
        )
    }

    if (episodesOpen) {
        AlertDialog(
            onDismissRequest = { episodesOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Max episodes per series", color = TextPrimary) },
            text = {
                Column(Modifier.selectableGroup()) {
                    (1..5).forEach { limit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .selectable(
                                    selected = maxEpisodes == limit,
                                    onClick = {
                                        maxEpisodes = limit
                                        manager.setMaxEpisodesPerShow(limit)
                                        episodesOpen = false
                                    },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = maxEpisodes == limit,
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (limit == 1) "1 episode at a time" else "$limit episodes at a time",
                                color = TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { episodesOpen = false }) { Text("Done") }
            }
        )
    }
}
