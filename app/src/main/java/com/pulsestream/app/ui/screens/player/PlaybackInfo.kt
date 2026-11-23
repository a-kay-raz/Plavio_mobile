package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Playback info (Nuvio PlaybackInfoModal, ExoPlayer half only — no mpv):
 * codec, resolution, frame rate, bitrate and live network speed while
 * playing. Shown as a bottom sheet (Avio's in-player panel shape).
 */
data class PlayerMediaInfo(
    val videoCodec: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val videoFrameRate: Float? = null,
    val videoBitrate: Int? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val audioSampleRate: Int? = null,
    val hdr: List<String> = emptyList(),
)

/** Read the playing stream's formats off the ExoPlayer (Nuvio's Format half). */
fun exoMediaInfo(player: ExoPlayer): PlayerMediaInfo {
    val video = player.videoFormat
    val audio = player.audioFormat
    return PlayerMediaInfo(
        videoCodec = codecLabel(video?.codecs),
        videoWidth = video?.width?.takeIf { it > 0 },
        videoHeight = video?.height?.takeIf { it > 0 },
        videoFrameRate = video?.frameRate?.takeIf { it > 0f },
        videoBitrate = video?.bitrate?.takeIf { it > 0 },
        audioCodec = codecLabel(audio?.codecs),
        audioChannels = audio?.channelCount?.takeIf { it > 0 },
        audioSampleRate = audio?.sampleRate?.takeIf { it > 0 },
        hdr = hdrLabels(video),
    )
}

private fun hdrLabels(format: Format?): List<String> = buildList {
    val codecs = format?.codecs?.lowercase().orEmpty()
    if (codecs.startsWith("dv")) add("DV")
    when (format?.colorInfo?.colorTransfer) {
        C.COLOR_TRANSFER_ST2084 -> add("HDR10")
        C.COLOR_TRANSFER_HLG -> add("HLG")
    }
}

/** Human codec name for an ExoPlayer codec string ("avc1.640028" -> "AVC"). */
private fun codecLabel(codecs: String?): String? {
    val first = codecs?.trim()?.takeIf { it.isNotBlank() }?.substringBefore(',')?.trim()
        ?: return null
    val token = first.substringBefore('.').lowercase()
    return when (token) {
        "avc1", "avc" -> "AVC"
        "hev1", "hvc1", "hevc" -> "HEVC"
        "vp09", "vp9" -> "VP9"
        "vp08", "vp8" -> "VP8"
        "av01", "av1" -> "AV1"
        "dvhe", "dvav", "dvc1", "dvh1" -> "Dolby Vision"
        "mp4a" -> if (first.lowercase().startsWith("mp4a.40.5")) "HE-AAC" else "AAC"
        "ac-3" -> "AC-3"
        "ec-3" -> "E-AC-3"
        "opus" -> "Opus"
        "vorbis" -> "Vorbis"
        "dts" -> "DTS"
        "truehd" -> "TrueHD"
        "alac" -> "ALAC"
        "flac" -> "FLAC"
        "mp3", "mp3a" -> "MP3"
        else -> token.uppercase()
    }
}

/** Nuvio formatNetworkSpeed: bytes/s -> "x.x MB/s" / "x KB/s" / "x B/s". */
fun formatNetworkSpeed(bytesPerSecond: Long): String {
    val value = bytesPerSecond.coerceAtLeast(0L)
    return when {
        value >= 1_000_000L -> {
            val tenths = (value + 50_000L) / 100_000L
            "${tenths / 10}.${tenths % 10} MB/s"
        }
        value >= 1_000L -> "${(value + 500L) / 1_000L} KB/s"
        else -> "$value B/s"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackInfoSheet(
    player: ExoPlayer,
    selectedQualityLabel: String?,
    isAuto: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // Live network speed: ExoPlayer's own bandwidth estimate (the
    // singleton meter the player builder uses by default), re-sampled
    // every second while the sheet is open. The tick also re-reads the
    // formats, so an adaptive switch updates the rows live.
    var speedBps by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            speedBps = DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate
            delay(1000)
        }
    }
    val info = exoMediaInfo(player)

    PlayerOverlayPanel(
        onDismiss = onDismiss
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                Text(
                    "Playback info",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
            }
            item { InfoSectionHeader("Selected Quality") }
            item {
                InfoRow(
                    "Requested Quality",
                    when {
                        isAuto && selectedQualityLabel != null -> "Auto ($selectedQualityLabel)"
                        selectedQualityLabel != null -> selectedQualityLabel
                        else -> "Auto"
                    }
                )
            }
            item { InfoSectionHeader("Playback Engine") }
            item { InfoRow("Engine", "ExoPlayer") }
            if (info.videoCodec != null) {
                item { InfoSectionHeader("Video Stream") }
                item { InfoRow("Codec", info.videoCodec) }
                val w = info.videoWidth
                val h = info.videoHeight
                if (w != null && h != null) {
                    item { InfoRow("Resolution", "${w}x$h") }
                }
                val fps = info.videoFrameRate
                if (fps != null) {
                    val fpsText = if (fps % 1f == 0f) fps.toInt().toString() else fps.toString().take(6).trimEnd('0').trimEnd('.')
                    item { InfoRow("Frame Rate", "$fpsText fps") }
                }
                val hdrLabel = when {
                    "DV" in info.hdr -> "Dolby Vision"
                    "HDR10" in info.hdr -> "HDR10 / PQ"
                    "HLG" in info.hdr -> "HLG"
                    else -> "SDR"
                }
                item { InfoRow("Dynamic Range", hdrLabel) }
                val kbps = info.videoBitrate?.takeIf { it > 0 }?.let { it / 1000 }
                if (kbps != null && kbps > 0) {
                    item { InfoRow("Bitrate", "$kbps kbps") }
                }
            }
            if (info.audioCodec != null) {
                item { InfoSectionHeader("Audio Stream") }
                item { InfoRow("Codec", info.audioCodec) }
                val channels = info.audioChannels
                if (channels != null) {
                    val channelsLabel = when (channels) {
                        1 -> "Mono (1ch)"
                        2 -> "Stereo (2ch)"
                        6 -> "Surround (5.1ch)"
                        8 -> "Surround (7.1ch)"
                        else -> "$channels channels"
                    }
                    item { InfoRow("Channels", channelsLabel) }
                }
                val rate = info.audioSampleRate
                if (rate != null && rate > 0) {
                    item { InfoRow("Sample Rate", if (rate >= 1000) "${rate / 1000} kHz" else "$rate Hz") }
                }
            }
            item { InfoSectionHeader("Network") }
            item { InfoRow("Network speed", formatNetworkSpeed(speedBps / 8)) }
            if (info.videoCodec == null && info.audioCodec == null) {
                item {
                    Text(
                        "No stream format yet — the player is still loading.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        color = LocalPalette.current.accent,
        fontSize = 11.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.Top,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(0.42f)
                .padding(end = 16.dp),
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.58f),
        )
    }
}
