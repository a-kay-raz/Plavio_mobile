package com.pulsestream.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.db.DownloadItem
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.data.download.OfflineShowMetadata
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlassIconButton
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.WifiOnlyDownloadsControl
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBack: () -> Unit = {},
    onPlayFile: (filePath: String, title: String) -> Unit = { _, _ -> },
    onGoToDownloadSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val scope = rememberCoroutineScope()

    val downloads by manager.observeAll().collectAsState(initial = emptyList())
    val activeProgress by manager.activeDownloads.collectAsState()
    var confirmDelete by remember { mutableStateOf<DownloadItem?>(null) }
    var wifiOnly by remember { mutableStateOf(manager.isWifiOnlyDownloads()) }

    LaunchedEffect(Unit) { manager.recoverInterruptedDownloads() }
    // Step 35: segmented storage bar (Nuvio DeviceStorageBar) — total / free
    // from the device, downloads-used from the manager; refreshed every 10s
    // and whenever the download list changes.
    val storageStats by produceState(Triple(0L, 0L, 0L), downloads) {
        while (true) {
            value = Triple(
                manager.storageTotalBytes(),
                manager.storageUsedBytes(),
                manager.storageFreeBytes()
            )
            kotlinx.coroutines.delay(10_000)
        }
    }

    val active = downloads.filter { it.status in listOf("QUEUED", "DOWNLOADING", "PAUSED", "FAILED") }
    val completed = downloads.filter { it.status == "COMPLETED" }

    // Sample E hero (v0.12.4): the first download whose LIVE status is
    // DOWNLOADING gets the big "Now downloading" card; a second active
    // download stays in the list below with its own progress.
    val heroItem = active.firstOrNull { item ->
        (activeProgress[item.id]?.status ?: item.status) == "DOWNLOADING"
    }
    val heroProgress = heroItem?.let { activeProgress[it.id] }
    val listActive = if (heroItem != null) active.filter { it.id != heroItem.id } else active

    // Step 36: per-show grouping — episodes sharing a showKey collapse under
    // one header; movies and ungrouped singles stay flat. Groups sort by
    // their most recent item; items inside sort by season/episode.
    val showGroups = remember(downloads) {
        downloads.filter { it.showKey.isNotBlank() }
            .groupBy { it.showKey }
            .toList()
            .sortedByDescending { (_, items) -> items.maxOf { it.createdAt } }
    }
    val singleActive = remember(listActive) {
        listActive.filter { it.showKey.isBlank() }
    }
    val singleCompleted = remember(completed) {
        completed.filter { it.showKey.isBlank() }
    }

    // Transfer speed for the hero, from progress deltas (the manager
    // exposes bytes only): sample on each progress change, ignore
    // windows under 500ms, smooth 60/40 towards the newest sample.
    var speedSample by remember { mutableStateOf<Triple<String, Long, Long>?>(null) }
    var heroSpeed by remember { mutableStateOf(0L) }
    val heroDownloaded = heroProgress?.downloadedBytes ?: heroItem?.downloadedBytes ?: 0L
    LaunchedEffect(heroItem?.id, heroDownloaded) {
        val current = heroItem
        if (current == null) {
            speedSample = null
            heroSpeed = 0L
            return@LaunchedEffect
        }
        val now = System.currentTimeMillis()
        val prev = speedSample
        if (prev == null || prev.first != current.id) {
            speedSample = Triple(current.id, heroDownloaded, now)
        } else {
            val dtMs = now - prev.third
            if (dtMs >= 500) {
                val instant = ((heroDownloaded - prev.second) * 1000L / dtMs).coerceAtLeast(0)
                heroSpeed = if (heroSpeed <= 0) instant
                else (0.6 * heroSpeed + 0.4 * instant).toLong()
                speedSample = Triple(current.id, heroDownloaded, now)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                navigationIcon = {
                    GlassIconButton(
                        image = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        onClick = onBack,
                        tint = TextPrimary
                    )
                },
                actions = {
                    // Step 35: Downloads settings (resolution, concurrency, toggles).
                    GlassIconButton(
                        image = Icons.Filled.Settings,
                        contentDescription = "Download settings",
                        onClick = onGoToDownloadSettings,
                        tint = TextPrimary
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            WifiOnlyDownloadsControl(
                checked = wifiOnly,
                onCheckedChange = { enabled ->
                    wifiOnly = enabled
                    manager.setWifiOnlyDownloads(enabled)
                    if (!enabled) {
                        manager.resumeQueuedDownloads()
                        DownloadServiceStarter.start(context)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    ) {
                        Icon(
                            Icons.Filled.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("No downloads yet", color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap the download icon next to any stream to save it here.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    // Bottom 104dp: last row scrolls clear of the floating dock.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        GlassCard(modifier = Modifier.fillMaxWidth()) {
                            // Step 35: segmented storage bar (Nuvio
                            // DeviceStorageBar) — downloads / other used / free.
                            val total = storageStats.first
                            val downloadsUsed = storageStats.second
                            val free = storageStats.third
                            val otherUsed = (total - free - downloadsUsed).coerceAtLeast(0L)
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Storage, contentDescription = null, tint = TextSecondary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Storage",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        if (total > 0) "${manager.formatBytes(free)} free of ${manager.formatBytes(total)}"
                                        else "Storage info unavailable",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF2A2A35))
                                ) {
                                    if (total > 0) {
                                        val dlFrac = (downloadsUsed.toFloat() / total).coerceIn(0f, 1f)
                                        val otherFrac = (otherUsed.toFloat() / total).coerceIn(0f, 1f - dlFrac)
                                        val freeFrac = (1f - dlFrac - otherFrac).coerceAtLeast(0f)
                                        if (dlFrac > 0f) Box(
                                            Modifier.fillMaxHeight().weight(dlFrac)
                                                .background(LocalPalette.current.accent)
                                        )
                                        if (otherFrac > 0f) Box(
                                            Modifier.fillMaxHeight().weight(otherFrac)
                                                .background(Color(0xFF5A5A68))
                                        )
                                        if (freeFrac > 0f) Box(Modifier.fillMaxHeight().weight(freeFrac))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Downloads ${manager.formatBytes(downloadsUsed)} · " +
                                        "Other ${manager.formatBytes(otherUsed)} · " +
                                        "Free ${manager.formatBytes(free)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                if (active.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { manager.pauseAll() }) { Text("Pause queue") }
                                        TextButton(onClick = {
                                            manager.resumeQueuedDownloads()
                                            DownloadServiceStarter.start(context)
                                        }) { Text("Resume queue") }
                                    }
                                }
                                // Step 36: retry every failed download at once.
                                if (downloads.any { it.status == "FAILED" }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = {
                                            scope.launch {
                                                manager.retryAllFailed()
                                                DownloadServiceStarter.start(context)
                                            }
                                        }) { Text("Retry all failed") }
                                    }
                                }
                            }
                        }
                    }

                    if (heroItem != null) {
                        item(key = "now_downloading_hero") {
                            NowDownloadingHero(
                                item = heroItem,
                                progress = heroProgress,
                                manager = manager,
                                speedBytesPerSec = heroSpeed,
                                onPause = { manager.pauseDownload(heroItem.id) },
                                onCancel = { manager.cancelDownload(heroItem.id) }
                            )
                        }
                    }

                    // Step 36: per-show groups — a show's episodes collapse
                    // under one header with one pause button. The hero item
                    // is excluded from its group (it already shows up top).
                    showGroups.forEach { (showKey, groupItems) ->
                        val visible = groupItems
                            .filter { it.id != heroItem?.id }
                            .sortedWith(compareBy({ it.season }, { it.episode }, { it.createdAt }))
                        if (visible.isNotEmpty()) {
                            item(key = "show_$showKey") {
                                ShowDownloadGroup(
                                    showKey = showKey,
                                    items = visible,
                                    manager = manager,
                                    activeProgress = activeProgress,
                                    onPlayFile = onPlayFile,
                                    onDelete = { confirmDelete = it }
                                )
                            }
                        }
                    }

                    if (singleActive.isNotEmpty() || (heroItem != null && singleCompleted.isNotEmpty())) {
                        item {
                            Text(
                                // Sample E: with a hero up top, everything
                                // below is one "Up next & finished" list.
                                if (heroItem != null) "Up next & finished" else "Active",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    if (singleActive.isNotEmpty()) {
                        items(singleActive, key = { it.id }) { item ->
                            val progress = activeProgress[item.id]
                            DownloadRow(
                                item = item,
                                progress = progress,
                                manager = manager,
                                onPause = { manager.pauseDownload(item.id) },
                                onResume = {
                                    manager.resumeDownload(item.id)
                                    DownloadServiceStarter.start(context)
                                },
                                onCancel = { manager.cancelDownload(item.id) }
                            )
                        }
                    }

                    if (singleCompleted.isNotEmpty()) {
                        // With a hero, the combined header above already
                        // covers the finished rows (Sample E).
                        if (heroItem == null) {
                            item {
                                Text(
                                    "Completed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        items(singleCompleted, key = { it.id }) { item ->
                            CompletedDownloadRow(
                                item = item,
                                manager = manager,
                                onPlayFile = onPlayFile,
                                onDelete = { confirmDelete = it }
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation
    confirmDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete download?") },
            text = { Text("\"${item.title}\" will be removed from your device.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { manager.deleteDownload(item.id) }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            }
        )
    }
}

/** Quality tag ("1080p") carried in download titles / file names. */
private val DOWNLOAD_QUALITY_REGEX =
    Regex("""\b(2160p|1440p|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)

/** "~40 sec" / "~12 min" / "~1 h 5 min" for the hero's ETA text. */
private fun formatEta(totalSeconds: Long): String = when {
    totalSeconds < 60 -> "$totalSeconds sec"
    totalSeconds < 3600 -> "${totalSeconds / 60} min"
    else -> "${totalSeconds / 3600} h ${(totalSeconds % 3600) / 60} min"
}

/**
 * Sample E hero card (v0.12.4): gradient navy panel with a mint
 * "NOW DOWNLOADING" kicker, the title, a big percent, the
 * "<done> of <total> · <speed> · ~<eta> left" line, a progress bar and
 * REAL Pause / Cancel buttons wired to the same manager calls the row
 * buttons use. Unknown values are omitted, never faked: with an unknown
 * total the downloaded size stands in for the percent and the bar goes
 * indeterminate; speed/ETA appear only once measured.
 */
@Composable
private fun NowDownloadingHero(
    item: DownloadItem,
    progress: DownloadManager.DownloadProgress?,
    manager: DownloadManager,
    speedBytesPerSec: Long,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: -1L
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    val etaSeconds = if (total > 0 && speedBytesPerSec > 1024) {
        (total - downloaded).coerceAtLeast(0) / speedBytesPerSec
    } else null
    val accent = LocalPalette.current.accent

    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            // Onyx neutral (owner's device note 2026-10-26): the old
            // navy gradient read blue on device — the hero now wears
            // the theme's own panel charcoal, accent only in details.
            .background(
                Brush.linearGradient(listOf(palette.panel, palette.panel))
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Column {
            Text(
                "NOW DOWNLOADING",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                fontWeight = FontWeight.ExtraBold,
                color = accent
            )
            Spacer(Modifier.height(6.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(10.dp))
            if (total > 0) {
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
            } else {
                Text(
                    manager.formatBytes(downloaded),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    "downloaded so far",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(Modifier.height(4.dp))
            val subParts = buildList {
                if (total > 0) {
                    add("${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}")
                } else {
                    add(manager.formatBytes(downloaded))
                }
                if (speedBytesPerSec > 0) add("${manager.formatBytes(speedBytesPerSec)}/s")
                etaSeconds?.let { add("~${formatEta(it)} left") }
            }
            Text(
                subParts.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = accent,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = LocalPalette.current.accentText
                    )
                ) {
                    Icon(
                        Icons.Filled.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Pause")
                }
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextPrimary
                    ),
                    border = BorderStroke(1.dp, GlassBorder)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    item: DownloadItem,
    progress: DownloadManager.DownloadProgress?,
    manager: DownloadManager,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit
) {
    val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
    val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: -1L
    val fraction = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
    val percent = (fraction * 100).toInt()
    val status = progress?.status ?: item.status

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (status) {
                            // v0.12.1 polish: explicit percent + sizes.
                            "DOWNLOADING" -> if (total > 0)
                                "$percent% • ${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                            else "Downloading • ${manager.formatBytes(downloaded)}"
                            "PAUSED" -> "Paused" + (if (total > 0)
                                " • $percent% • ${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                            else " • ${manager.formatBytes(downloaded)}")
                            "QUEUED" -> item.error ?: "Waiting…"
                            "FAILED" -> "Failed${item.error?.let { ": $it" } ?: ""}"
                            else -> status
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when (status) {
                    "DOWNLOADING", "QUEUED" -> {
                        GlassIconButton(
                            image = Icons.Filled.Pause,
                            contentDescription = "Pause",
                            onClick = onPause,
                            tint = TextPrimary
                        )
                    }
                    "PAUSED" -> {
                        GlassIconButton(
                            image = Icons.Filled.PlayArrow,
                            contentDescription = "Resume",
                            onClick = onResume,
                            tint = TextPrimary
                        )
                    }
                    "FAILED" -> {
                        GlassIconButton(
                            image = Icons.Filled.Refresh,
                            contentDescription = "Retry",
                            onClick = onResume,
                            tint = TextPrimary
                        )
                    }
                }
                // v0.12.1 fix: this ✕ used to open the DELETE dialog
                // (onDelete) despite its "Cancel" label, and the real
                // onCancel callback was never wired. It now cancels.
                GlassIconButton(
                    image = Icons.Filled.Close,
                    contentDescription = "Cancel download",
                    onClick = onCancel,
                    tint = TextSecondary
                )
            }
            Spacer(Modifier.height(8.dp))
            if (status == "DOWNLOADING" && total <= 0) {
                // Unknown total size: an honest indeterminate bar instead
                // of a determinate one stuck at 0%.
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}


/** One finished download row (extracted from the inline LazyColumn body so show groups can reuse it). */
@Composable
private fun CompletedDownloadRow(
    item: DownloadItem,
    manager: DownloadManager,
    onPlayFile: (filePath: String, title: String) -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    // v0.12.1: a completed row whose file was deleted
            // outside the app used to offer a Play button
            // that could only fail — say so plainly instead.
            // Step 37: content-URI-aware — moved files live in the SAF folder.
            val fileMissing = remember(item.id, item.filePath) { !manager.downloadFileExists(item.filePath) }
            // Step 46: cached poster for offline browsing (movies: the meta
            // key is the item id; series episodes: the show key).
            val context = LocalContext.current
            val poster = remember(item.id) {
                OfflineShowMetadata.posterFile(context, item.showKey.ifEmpty { item.id })
            }
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (poster != null) {
                        SafePosterImage(
                            model = poster,
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Icon(
                            Icons.Filled.Movie,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                fileMissing -> "File missing — it may have been deleted outside the app"
                                manager.isDashDownloadUrl(item.url) -> "Manifest only — not a complete offline download"
                                else -> {
                                    // Sample E row sub (v0.12.4):
                                    // "1.4 GB · 1080p · Ready" —
                                    // quality only when the
                                    // title/file name carries
                                    // one; never guessed.
                                    val sizeText = manager.formatBytes(
                                        item.totalBytes.takeIf { it > 0 }
                                            ?: manager.downloadFileSize(item.filePath)
                                    )
                                    val quality = DOWNLOAD_QUALITY_REGEX.find(item.title)?.value
                                        ?: DOWNLOAD_QUALITY_REGEX.find(item.fileName)?.value
                                    if (quality != null) "$sizeText · ${quality.lowercase()} · Ready"
                                    else "$sizeText · Ready"
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (fileMissing || manager.isDashDownloadUrl(item.url)) MaterialTheme.colorScheme.error else TextSecondary
                        )
                    }
                    if (!fileMissing && !manager.isDashDownloadUrl(item.url)) {
                        GlassIconButton(
                            image = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            onClick = { onPlayFile(item.filePath, item.title) },
                            tint = TextPrimary
                        )
                    }
                    GlassIconButton(
                        image = Icons.Filled.Delete,
                        contentDescription = "Delete",
                        onClick = { onDelete(item) },
                        tint = TextSecondary
                    )
                }
            }
        
}

/**
 * Step 36: one collapsible header per show (Nuvio downloadsShowContent) —
 * the series name, a summary line (Nuvio SummaryText), one pause/resume
 * button for the whole show, and the episode rows when expanded.
 */
@Composable
private fun ShowDownloadGroup(
    showKey: String,
    items: List<DownloadItem>,
    manager: DownloadManager,
    activeProgress: Map<String, DownloadManager.DownloadProgress>,
    onPlayFile: (filePath: String, title: String) -> Unit,
    onDelete: (DownloadItem) -> Unit
) {
    var expanded by rememberSaveable(showKey) { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val name = items.firstOrNull()?.seriesName?.takeIf { it.isNotBlank() } ?: showKey
    val downloading = items.count { it.status == "DOWNLOADING" }
    val queued = items.count { it.status == "QUEUED" }
    val completed = items.count { it.status == "COMPLETED" }
    val failed = items.count { it.status == "FAILED" }
    val paused = items.count { it.status == "PAUSED" }
    val hasActive = downloading + queued > 0
    val summary = buildString {
        append("${items.size} episode${if (items.size == 1) "" else "s"}")
        if (downloading > 0) append(" · $downloading downloading")
        if (queued > 0) append(" · $queued queued")
        if (completed > 0) append(" · $completed done")
        if (failed > 0) append(" · $failed failed")
        if (paused > 0) append(" · $paused paused")
    }

    // Step 46: cached poster for offline browsing (null when the show was
    // downloaded before Step 46 or the poster fetch failed).
    val poster = remember(showKey) { OfflineShowMetadata.posterFile(context, showKey) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (poster != null) {
                    SafePosterImage(
                        model = poster,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                GlassIconButton(
                    image = if (hasActive) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (hasActive) "Pause show" else "Resume show",
                    onClick = {
                        scope.launch {
                            manager.toggleShowPause(showKey)
                            if (!hasActive) DownloadServiceStarter.start(context)
                        }
                    },
                    tint = TextPrimary
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items.forEach { item ->
                        if (item.status == "COMPLETED") {
                            CompletedDownloadRow(
                                item = item,
                                manager = manager,
                                onPlayFile = onPlayFile,
                                onDelete = onDelete
                            )
                        } else {
                            val progress = activeProgress[item.id]
                            DownloadRow(
                                item = item,
                                progress = progress,
                                manager = manager,
                                onPause = { manager.pauseDownload(item.id) },
                                onResume = {
                                    manager.resumeDownload(item.id)
                                    DownloadServiceStarter.start(context)
                                },
                                onCancel = { manager.cancelDownload(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Starts the foreground download service (no-op helper to keep call sites tidy). */
object DownloadServiceStarter {
    fun start(context: android.content.Context) {
        com.pulsestream.app.data.download.DownloadService.start(context)
    }
}
