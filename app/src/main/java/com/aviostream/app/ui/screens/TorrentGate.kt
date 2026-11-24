package com.aviostream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.TorrentEngine
import com.aviostream.app.ui.components.SafePosterImage
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/**
 * The player route's torrent gate (ported from upstream v0.14.30):
 * when the pending request names a torrent
 * ([PlaybackRequest.torrentInfoHash] set), the torrent engine
 * resolves it to a local HTTP URL BEFORE the player composes —
 * the player itself never sees a magnet. While the swarm metadata
 * is fetched, a plain loading surface shows (poster + spinner);
 * on failure, a plain message + Back. Non-torrent requests pass
 * straight through, untouched.
 *
 * The gate wraps the player for the whole destination lifetime,
 * so its dispose is exactly "playback exited": the engine
 * session stops there (seed only while playing).
 */
@Composable
fun TorrentGatePlayer(
    repository: StreamRepository,
    onBack: () -> Unit,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit = { _, _, _ -> }
) {
    val initial = remember { repository.pendingPlayback }
    if (initial == null || initial.torrentInfoHash == null) {
        PlayerScreen(repository = repository, onBack = onBack, onOpenDetail = onOpenDetail)
        return
    }
    val context = LocalContext.current
    var resolved by remember { mutableStateOf<PlaybackRequest?>(null) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(initial) {
        val url = TorrentEngine.prepare(
            context = context.applicationContext,
            magnetUri = initial.torrentMagnetUri,
            infoHash = initial.torrentInfoHash,
            fileIdx = initial.torrentFileIdx
        )
        if (url == null) {
            failed = true
        } else {
            val ready = initial.copy(streamUrl = url)
            repository.pendingPlayback = ready
            resolved = ready
        }
    }
    DisposableEffect(initial) {
        onDispose { TorrentEngine.stopCurrent() }
    }

    when {
        resolved != null -> PlayerScreen(repository = repository, onBack = onBack, onOpenDetail = onOpenDetail)
        failed -> Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text(
                    "Couldn't start this torrent",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No peers could be reached for it. Check your connection and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onBack) { Text("Back") }
            }
        }
        else -> Box(
            Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            val art = initial.poster
            if (art != null) {
                SafePosterImage(
                    model = art,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp),
                    color = TextPrimary
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    initial.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Connecting to peers…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}
