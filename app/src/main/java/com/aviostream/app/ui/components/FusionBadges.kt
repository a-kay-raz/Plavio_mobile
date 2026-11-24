package com.aviostream.app.ui.components

import android.content.Context
import com.aviostream.app.data.StreamBadge
import com.aviostream.app.data.StreamItem
import java.io.File

/**
 * Fusion badge rendering on the fork (v0.14.30 port, feature #15).
 *
 * Upstream's renderer (StreamBadges.kt — BadgeStrip over its own
 * StreamFacts + a bundled Elite pack) does not fit the fork: the
 * fork has no StreamFacts, no Elite assets, and its badges are the
 * Nuvio rule system (StreamBadge chips from imported rule sets).
 * So Fusion rides on the fork's own chips instead: the canonical
 * keys a stream's parsed facts imply ([fusionBadgeKeys], from
 * [parseStreamInfo]) are dressed from the user's Fusion sources,
 * first source in list order per key, as plain [StreamBadge]s whose
 * image is the installed PNG file. The stream row's existing
 * StreamBadgeChip (Coil) renders them — no new chip needed.
 *
 * Priority: a rule badge with the same name wins — a Fusion badge
 * whose label an existing rule badge already carries is skipped,
 * so the row never shows the same fact twice. A fact no source
 * carries simply gets no Fusion chip (there is no Elite fallback
 * in the fork). HDR, languages, channels, remux and bit-depth keys
 * never fire here: the fork's parseStreamInfo does not extract
 * them.
 */

/** Canonical badge keys for a stream's parsed facts, in card order. */
fun fusionBadgeKeys(stream: StreamItem): List<String> {
    val info = parseStreamInfo(stream)
    return buildList {
        when (info.quality?.uppercase()) {
            "4K", "UHD", "2160P" -> add("res-2160p")
            "1440P" -> add("res-1440p")
            "1080P" -> add("res-1080p")
            "720P" -> add("res-720p")
            "480P" -> add("res-480p")
            "360P" -> add("res-360p")
        }
        when (info.source) {
            "BluRay", "BRRip" -> add("src-bluray")
            "WEB-DL" -> add("src-webdl")
            "WEBRip" -> add("src-webrip")
            "HDRip" -> add("src-hdrip")
            "HDCAM", "HDTS", "TS", "CAM" -> add("src-cam")
        }
        when (info.codec?.uppercase()) {
            "HEVC", "X265" -> add("codec-hevc")
            "AVC", "X264" -> add("codec-avc")
            "AV1" -> add("codec-av1")
        }
        when (info.audio) {
            "Atmos" -> add("atmos")
            "TRUEHD" -> add("audio-truehd")
            "DTS-HD", "DTS-HDMA" -> add("audio-dtshd")
            "DTS" -> add("audio-dts")
            "DD+" -> add("audio-ddp")
            "DD", "DD5.1", "DD7.1" -> add("audio-dd")
            "AAC", "AAC5.1" -> add("audio-aac")
        }
    }
}

/**
 * The Fusion badges for one stream: the first source (in list
 * order) whose installed artwork carries each key dresses it.
 * Only file-existence is checked here — the image itself is
 * decoded by Coil in StreamBadgeChip.
 */
fun fusionBadgesForStream(
    context: Context,
    stream: StreamItem,
    sourceIds: List<String>
): List<StreamBadge> {
    if (sourceIds.isEmpty()) return emptyList()
    return fusionBadgeKeys(stream).mapNotNull { key ->
        val file = sourceIds.firstNotNullOfOrNull { id ->
            File(fusionSourceDir(context, id), "$key.png").takeIf { it.isFile }
        } ?: return@mapNotNull null
        StreamBadge(name = badgeKeyLabel(key), imageURL = "file://" + file.absolutePath)
    }
}

/**
 * Appends Fusion badges to every stream's badge list (after the
 * rule match), skipping any key a rule badge already names, so a
 * fact never shows twice. Streams with no Fusion artwork are
 * returned unchanged.
 */
fun applyFusionBadges(
    context: Context,
    streams: List<StreamItem>,
    sourceIds: List<String>
): List<StreamItem> {
    if (sourceIds.isEmpty() || streams.isEmpty()) return streams
    return streams.map { stream ->
        val fusion = fusionBadgesForStream(context, stream, sourceIds)
        if (fusion.isEmpty()) return@map stream
        val existingNames = stream.badges.mapTo(mutableSetOf()) { it.name.lowercase() }
        val existingUrls = stream.badges.mapTo(mutableSetOf()) { it.imageURL.lowercase() }
        val fresh = fusion.filter { badge ->
            badge.name.lowercase() !in existingNames &&
                badge.imageURL.lowercase() !in existingUrls
        }
        if (fresh.isEmpty()) stream else stream.copy(badges = stream.badges + fresh)
    }
}
