package com.pulsestream.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.HlsQualityResolver
import com.pulsestream.app.data.HlsQualityVariant
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.download.DownloadManager
import com.pulsestream.app.data.download.DownloadService
import com.pulsestream.app.data.download.OfflineShowMetadata
import com.pulsestream.app.data.pickClosestVariant
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Confirmation dialog shown before starting a download.
 * Probes the file size via HEAD request and warns if it's large (>500MB).
 */
@Composable
fun DownloadConfirmDialog(
    stream: StreamItem,
    title: String,
    onDismiss: () -> Unit,
    onStarted: () -> Unit = {},
    // Step 36: per-show grouping — parent meta id ("" = movie/ungrouped),
    // series display name, and the episode position.
    metaId: String = "",
    seriesName: String? = null,
    season: Int = 0,
    episode: Int = 0,
    // Step 46: subtitle URLs captured for this title (fetched into sidecars
    // after the download completes) + art/meta cached for offline browsing.
    subtitles: List<Subtitle> = emptyList(),
    posterUrl: String? = null,
    description: String? = null,
    genres: List<String> = emptyList(),
    videos: List<Video> = emptyList()
) {
    val context = LocalContext.current
    val manager = remember { DownloadManager.get(context) }
    val scope = rememberCoroutineScope()

    var sizeBytes by remember { mutableStateOf<Long?>(null) }
    var probing by remember { mutableStateOf(true) }
    var alreadyDownloaded by remember { mutableStateOf(false) }
    var requestError by remember { mutableStateOf<String?>(null) }
    val dashUnsupported = manager.isDashDownloadUrl(stream.url)
    val isHls = manager.isHlsDownloadUrl(stream.url)
    // Step 35: preferred resolution (Nuvio DownloadPreferredResolution).
    // HLS only — a single-file stream has one quality and nothing to pick.
    var resolution by remember { mutableStateOf(manager.getDownloadPreferredResolution()) }
    var variants by remember { mutableStateOf<List<HlsQualityVariant>?>(null) }
    var variantsLoading by remember { mutableStateOf(false) }
    val chosenVariant = variants?.let { pickClosestVariant(it, resolution) }
    // The download URL: the chosen variant's own playlist for HLS (its
    // absoluteUri, NOT the player's sanitized temp file), else the stream URL.
    val downloadUrl = chosenVariant?.absoluteUri ?: stream.url

    LaunchedEffect(stream.url) {
        alreadyDownloaded = manager.getByUrl(stream.url)?.status == "COMPLETED"
        if (!dashUnsupported && !isHls) sizeBytes = manager.probeSize(stream.url, stream.headers)
        probing = false
    }

    // Step 35: resolve the HLS master once so the resolution row can offer
    // the real variants. A failed resolve keeps today's behaviour (master URL).
    LaunchedEffect(stream.url, isHls) {
        if (!isHls || dashUnsupported) return@LaunchedEffect
        variantsLoading = true
        val state = runCatching {
            HlsQualityResolver.resolve(context, stream.url, stream.headers)
        }.getOrNull()
        variants = state?.variants?.takeIf { it.size > 1 }
        variantsLoading = false
    }

    val enoughStorage = manager.storagePreflight(sizeBytes)
    val isLarge = (sizeBytes ?: -1L) > DownloadManager.LARGE_FILE_WARNING_BYTES
    val displayTitle = stream.title?.takeIf { it.isNotBlank() }
        ?: stream.name?.takeIf { it.isNotBlank() }
        ?: title

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Download", fontWeight = FontWeight.Bold, color = TextPrimary)
        },
        text = {
            Column {
                Text(
                    displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                when {
                    probing -> {
                        Row {
                            OrbitDots(modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Checking file size…",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    dashUnsupported -> {
                        Text(
                            "DASH streams can't be saved as offline downloads yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    isHls -> {
                        Text(
                            "This is an HLS stream. It will be downloaded in segments and saved as a single MP4 file, so the size can't be checked beforehand.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    alreadyDownloaded -> {
                        Text(
                            "This file is already downloaded.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    else -> {
                        val sizeText = sizeBytes?.let {
                            if (it > 0) manager.formatBytes(it) else "Unknown size"
                        } ?: "Unknown size"
                        Text(
                            "Size: $sizeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        if (sizeBytes != null && sizeBytes!! > 0 && !enoughStorage) {
                            Spacer(Modifier.height(8.dp))
                            Text("Not enough free storage for this file plus a safety reserve.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (isLarge) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "⚠️ This file is large (${manager.formatBytes(sizeBytes!!)}). " +
                                    "Make sure you have enough storage and a stable connection.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                // Step 35: resolution choice for HLS (variants resolved above).
                // Single-file streams have one quality — no row for them.
                if (isHls && !dashUnsupported && !alreadyDownloaded) {
                    Spacer(Modifier.height(12.dp))
                    if (variantsLoading) {
                        Text(
                            "Checking available qualities…",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    } else if (variants != null) {
                        Text(
                            "Quality",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            DownloadManager.RESOLUTION_OPTIONS.forEach { height ->
                                val selected = resolution == height
                                FilterChip(
                                    selected = selected,
                                    onClick = { resolution = height },
                                    label = { Text("${height}p") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                        }
                        chosenVariant?.let { variant ->
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Downloads at ${variant.displayLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                requestError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!alreadyDownloaded) {
                        scope.launch(kotlinx.coroutines.NonCancellable) {
                            try {
                                // Step 35: persist the chosen resolution and
                                // download the chosen variant's playlist.
                                // Step 36: show-key plumbing for grouping.
                                manager.setDownloadPreferredResolution(resolution)
                                val downloadId = manager.startDownload(
                                    url = downloadUrl,
                                    title = displayTitle,
                                    headers = stream.headers,
                                    showKey = metaId,
                                    seriesName = seriesName ?: title,
                                    season = season,
                                    episode = episode,
                                    // Step 46: subtitles + offline show meta.
                                    subtitles = subtitles
                                )
                                OfflineShowMetadata.save(
                                    context = context,
                                    key = metaId.ifEmpty { downloadId },
                                    title = seriesName ?: displayTitle,
                                    type = if (metaId.isNotEmpty()) "series" else "movie",
                                    posterUrl = posterUrl,
                                    description = description,
                                    genres = genres,
                                    videos = videos
                                )
                                DownloadService.start(context)
                                onStarted()
                                onDismiss()
                            } catch (e: Exception) {
                                requestError = e.message ?: "Could not start this download."
                            }
                        }
                    } else onDismiss()
                },
                enabled = !probing && !alreadyDownloaded && !dashUnsupported && (isHls || enoughStorage)
            ) {
                Text("Download", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
