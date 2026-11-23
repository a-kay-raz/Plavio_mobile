package com.aviostream.app.data.cloudstream

import com.aviostream.app.data.StreamItem
import com.aviostream.app.data.Subtitle

/**
 * Maps CloudStream playback results onto Avio's [StreamItem].
 *
 * Retarget of the TV port's CloudStreamTvAdapter: the playable URL, quality
 * label (encoded into the name, since StreamItem has no quality field — the
 * prober parses resolution out of the title downstream), request headers and
 * per-stream subtitles. Known loss vs TV: Avio's Subtitle carries no
 * headers or addon attribution, so those are dropped.
 *
 * Magnet links (magnet:?xt=urn:btih:<hash>) become infoHash streams with url
 * "infohash:<hash>", exactly the shape the debrid leg in
 * StreamRepository.streamResultsFlow already resolves for Stremio torrents.
 */
fun CloudStreamPlaybackSource.toAvioStreamItem(providerName: String, pluginId: String): StreamItem {
    val parsedUrl = parseCloudStreamPlaybackUrl(url)
    val requestHeaders = buildMap {
        putAll(parsedUrl.headers)
        putAll(headers)
        referer?.takeIf { it.isNotBlank() }?.let { put("Referer", it) }
    }
    val qualityLabel = quality?.cloudStreamQualityLabel()
    val displayName = listOfNotNull(name.takeIf { it.isNotBlank() }, qualityLabel).joinToString(" · ")
    val magnetHash = parseMagnetInfoHash(parsedUrl.url)
    return if (magnetHash != null) {
        StreamItem(
            url = "infohash:$magnetHash",
            name = displayName,
            title = displayName,
            sourceName = providerName,
            sourceId = pluginId,
            headers = requestHeaders,
            infoHash = magnetHash,
        )
    } else {
        StreamItem(
            url = parsedUrl.url,
            name = displayName,
            title = displayName,
            sourceName = providerName,
            sourceId = pluginId,
            headers = requestHeaders,
        )
    }
}

/** Per-stream subtitles as Avio Subtitle(url, lang) — headers/addon dropped (see header). */
fun CloudStreamPlaybackSource.toAvioSubtitles(): List<Subtitle> =
    subtitles.map { subtitle -> Subtitle(url = subtitle.url, lang = subtitle.language) }

private fun Int.cloudStreamQualityLabel(): String? =
    takeIf { it in knownCloudStreamQualities || (it in 144..4320 && it % 10 == 0) }?.let { "${it}p" }

private val knownCloudStreamQualities = setOf(144, 240, 360, 480, 540, 576, 720, 1080, 1440, 2160, 4320)

private data class ParsedCloudStreamPlaybackUrl(
    val url: String,
    val headers: Map<String, String>,
)

/** CloudStream links can carry headers after `|`, e.g. `https://…|Referer=…&User-Agent=…`. */
private fun parseCloudStreamPlaybackUrl(rawUrl: String): ParsedCloudStreamPlaybackUrl {
    val trimmed = rawUrl.trim()
    val url = trimmed.substringBefore('|').trim()
    val headerPayload = trimmed.substringAfter('|', missingDelimiterValue = "")
    if (headerPayload.isBlank()) return ParsedCloudStreamPlaybackUrl(url = url, headers = emptyMap())

    val headers = headerPayload
        .split('&')
        .mapNotNull { entry ->
            val key = entry.substringBefore('=', missingDelimiterValue = "").trim()
            val value = entry.substringAfter('=', missingDelimiterValue = "").trim()
            if (key.isBlank() || value.isBlank()) null else key to value
        }
        .toMap()
    return ParsedCloudStreamPlaybackUrl(url = url, headers = headers)
}

/** Hex (40 chars) or base32 (32 chars) info hash from a magnet link, else null. */
private fun parseMagnetInfoHash(url: String): String? {
    if (!url.startsWith("magnet:?")) return null
    val hash = url.substringAfter("xt=urn:btih:", missingDelimiterValue = "")
        .substringBefore('&').trim()
    return hash.takeIf { it.length in 32..64 && it.all { c -> c.isLetterOrDigit() } }
}
