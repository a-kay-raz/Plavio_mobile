package com.aviostream.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.update.ApkInstaller
import com.aviostream.app.data.update.UpdateChecker
import com.aviostream.app.data.update.UpdateDownloadSupport
import com.aviostream.app.data.update.UpdateInfo
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/**
 * Shows the update dialog for [info] (if non-null) and handles the
 * download → install flow plus the "Later" dismissal. Shared by the
 * automatic launch popup and the manual Settings check.
 */
@Composable
fun UpdatePrompt(
    info: UpdateInfo?,
    onDismiss: () -> Unit
) {
    var downloading by remember(info) { mutableStateOf(false) }
    // A failed download keeps the dialog open with the real error instead
    // of dismissing silently (or launching the installer on garbage).
    var downloadError by remember(info) { mutableStateOf<String?>(null) }
    // v0.14.30 #49: live progress for the popup — the byte counters
    // ApkInstaller's poller posts (main thread), and the handle the
    // Cancel button aborts through.
    var downloadedBytes by remember(info) { mutableLongStateOf(0L) }
    var totalBytes by remember(info) { mutableLongStateOf(-1L) }
    var downloadHandle by remember(info) {
        mutableStateOf<ApkInstaller.DownloadHandle?>(null)
    }
    val appContext = LocalContext.current.applicationContext
    if (info != null) {
        UpdateDialog(
            info = info,
            downloading = downloading,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            error = downloadError,
            onUpdate = {
                downloading = true
                downloadError = null
                downloadedBytes = 0L
                totalBytes = -1L
                downloadHandle = ApkInstaller.download(
                    appContext,
                    info.apkUrl,
                    info.version,
                    onProgress = { downloaded, total ->
                        downloadedBytes = downloaded
                        totalBytes = total
                    },
                    onDone = {
                        downloading = false
                        downloadHandle = null
                        onDismiss()
                    },
                    onError = { message ->
                        downloading = false
                        downloadHandle = null
                        downloadError = message
                    }
                )
            },
            onLater = {
                UpdateChecker.skip(appContext, info.version)
                onDismiss()
            },
            onCancelDownload = {
                // v0.14.30 #49: Cancel ABORTS the download (the
                // handle kills the transfer and deletes the partial
                // file) and dismisses. It is NOT a skip: nothing is
                // recorded, the persisted pending update is untouched,
                // so the update is offered again normally on the next
                // check — no stuck state.
                downloadHandle?.cancel()
                downloadHandle = null
                downloading = false
                onDismiss()
            }
        )
    }
}

/** Popup shown when a newer Avio release is available. */
@Composable
fun UpdateDialog(
    info: UpdateInfo,
    downloading: Boolean,
    // v0.14.30 #49: live download counters (bytes), driven by
    // ApkInstaller's poller through UpdatePrompt's state — the
    // popup renders from state, never blocks.
    downloadedBytes: Long = 0L,
    totalBytes: Long = -1L,
    error: String? = null,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onCancelDownload: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = { if (!downloading) onLater() },
        title = {
            Text(
                "Update available",
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "v${info.version}",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = LocalPalette.current.accent,
                    textAlign = TextAlign.Center
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        info.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                Spacer(Modifier.height(8.dp))
                if (downloading) {
                    // v0.14.30 #49: the progress is ON SCREEN, inside
                    // the popup — the bar plus percentage and MB
                    // counts, all from the live byte counters.
                    // Unknown total (server hasn't answered with a
                    // length) shows the indeterminate bar + MB so far.
                    val fraction = UpdateDownloadSupport.fraction(
                        downloadedBytes, totalBytes
                    )
                    if (fraction != null) {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth(),
                            color = LocalPalette.current.accent,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = LocalPalette.current.accent,
                            trackColor = Color.White.copy(alpha = 0.12f)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    val percent = UpdateDownloadSupport.percent(
                        downloadedBytes, totalBytes
                    )
                    Text(
                        if (percent != null) {
                            "$percent% · " + UpdateDownloadSupport.sizeLabel(
                                downloadedBytes, totalBytes
                            )
                        } else {
                            UpdateDownloadSupport.sizeLabel(
                                downloadedBytes, totalBytes
                            ) + " downloaded"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onCancelDownload) {
                        Text("Cancel download", color = TextSecondary)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (error != null) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B6B),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    if (downloading) "Downloading… the installer opens when it's done."
                    else "Installs right over your current version — nothing is wiped.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !downloading) {
                Text(if (downloading) "Downloading…" else "Update", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onLater, enabled = !downloading) {
                Text("Later", color = TextSecondary)
            }
        },
        containerColor = LocalPalette.current.background
    )
}
