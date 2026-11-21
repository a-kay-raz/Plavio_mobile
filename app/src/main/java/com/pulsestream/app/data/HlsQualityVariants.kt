package com.pulsestream.app.data

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * HLS master-playlist quality variants for the Quality picker (Nuvio
 * `player/PlayerQualitySelector.kt`, ported to Android).
 *
 * For an HLS stream the picker lists the master playlist's actual variants
 * (1080p/720p/480p…) instead of only whole-stream quality buckets. Picking
 * one plays a sanitized playlist — the same master with only that variant
 * (plus its audio group) — written to a temp file in the app cache, so the
 * player pins to the chosen variant instead of adapting on its own.
 */
data class HlsQualityVariant(
    val id: String,
    val name: String,
    val width: Int?,
    val height: Int?,
    val bandwidth: Long?,
    val codecs: String,
    val audioGroupId: String?,
    val absoluteUri: String,
    val playbackUrl: String,
) {
    val isAv1: Boolean
        get() = codecs.split(',').any { codec -> codec.trim().startsWith("av01", ignoreCase = true) }

    val isLikelyHardwareDecodable: Boolean
        get() = !isAv1

    val sortHeight: Int
        get() = height ?: name.filter { it.isDigit() }.toIntOrNull() ?: 0

    val displayLabel: String
        get() = buildString {
            append(qualityName)
            codecLabel.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            bandwidthLabel.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
            if (!isLikelyHardwareDecodable) append(" · Software decode")
        }

    val qualityName: String
        get() = when {
            name.isNotBlank() -> if (name.endsWith("p", ignoreCase = true)) name else "${name}p"
            height != null -> "${height}p"
            width != null -> "${width}w"
            else -> "Unknown"
        }

    private val codecLabel: String
        get() = when {
            isAv1 -> "AV1"
            codecs.contains("hvc1", ignoreCase = true) || codecs.contains("hev1", ignoreCase = true) -> "HEVC"
            codecs.contains("avc1", ignoreCase = true) || codecs.contains("avc3", ignoreCase = true) -> "H.264"
            codecs.isNotBlank() -> codecs.split(',').lastOrNull()?.trim().orEmpty()
            else -> ""
        }

    private val bandwidthLabel: String
        get() {
            val value = bandwidth ?: return ""
            if (value <= 0L) return ""
            val mbps = value / 1_000_000.0
            return if (mbps >= 1.0) {
                "${formatOneDecimal(mbps)} Mbps"
            } else {
                "${formatOneDecimal(value / 1_000.0)} Kbps"
            }
        }
}

data class HlsQualityState(
    val isLoading: Boolean = false,
    val sourceUrl: String = "",
    val variants: List<HlsQualityVariant> = emptyList(),
    val recommendedVariantId: String? = null,
    val errorMessage: String? = null,
) {
    val hasSelectableQualities: Boolean
        get() = variants.size > 1

    fun selectedVariantFor(selectedVariantId: String?): HlsQualityVariant? =
        when {
            selectedVariantId.isNullOrBlank() || selectedVariantId == HLS_QUALITY_AUTO_ID -> {
                variants.firstOrNull { it.id == recommendedVariantId }
            }
            else -> variants.firstOrNull { it.id == selectedVariantId }
        }

    fun playbackUrlFor(selectedVariantId: String?): String? =
        selectedVariantFor(selectedVariantId)?.playbackUrl ?: sourceUrl.takeIf { it.isNotBlank() }
}

const val HLS_QUALITY_AUTO_ID = "auto"

object HlsQualityResolver {
    private const val TAG = "HlsQuality"
    private const val TEMP_PLAYLIST_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

    fun looksLikeHlsPlaylist(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        return path.endsWith(".m3u8") || url.contains(".m3u8", ignoreCase = true)
    }

    /**
     * Fetches the master playlist for [sourceUrl] (with the stream's
     * headers), parses its variants and prepares one sanitized temp playlist
     * per variant. Returns a state with no variants when the URL is not an
     * HLS master, the fetch fails, or no variant could be prepared — the
     * caller falls back to the whole-stream quality buckets then.
     */
    suspend fun resolve(
        context: Context,
        sourceUrl: String,
        requestHeaders: Map<String, String> = emptyMap(),
    ): HlsQualityState {
        val normalizedUrl = sourceUrl.trim()
        if (!looksLikeHlsPlaylist(normalizedUrl)) {
            return HlsQualityState(sourceUrl = normalizedUrl)
        }
        val playlistText = withContext(Dispatchers.IO) {
            runCatching {
                val requestBuilder = Request.Builder().url(normalizedUrl)
                requestHeaders.forEach { (name, value) -> requestBuilder.header(name, value) }
                PlaybackHttp.client(normalizedUrl).newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    response.body?.string()
                }
            }.onFailure { error ->
                Log.w(TAG, "Failed to fetch HLS master playlist", error)
            }.getOrNull()
        } ?: return HlsQualityState(
            sourceUrl = normalizedUrl,
            errorMessage = "Unable to inspect HLS quality variants.",
        )

        if (!playlistText.contains("#EXT-X-STREAM-INF", ignoreCase = true)) {
            return HlsQualityState(sourceUrl = normalizedUrl)
        }

        val parsed = HlsMasterPlaylist.parse(normalizedUrl, playlistText)
        if (parsed.variants.isEmpty()) {
            return HlsQualityState(sourceUrl = normalizedUrl)
        }

        val variants = parsed.variants.mapIndexedNotNull { index, variant ->
            val sanitized = parsed.buildSanitizedMasterPlaylist(variant)
            val playbackUrl = writeTemporaryHlsPlaylist(context, sanitized)
            if (playbackUrl == null) {
                Log.w(TAG, "Failed to write sanitized HLS playlist for variant=${variant.name.ifBlank { variant.id }}")
                null
            } else {
                variant.toUiVariant(index = index, playbackUrl = playbackUrl)
            }
        }

        if (variants.isEmpty()) {
            return HlsQualityState(
                sourceUrl = normalizedUrl,
                errorMessage = "Unable to prepare HLS quality variants.",
            )
        }

        val recommended = chooseRecommendedVariant(variants)
        return HlsQualityState(
            sourceUrl = normalizedUrl,
            variants = variants,
            recommendedVariantId = recommended?.id,
        )
    }

    private fun chooseRecommendedVariant(variants: List<HlsQualityVariant>): HlsQualityVariant? {
        val compatible = variants.filter { it.isLikelyHardwareDecodable }
        val pool = compatible.ifEmpty { variants }
        return pool.maxWithOrNull(compareBy<HlsQualityVariant> { it.sortHeight }.thenBy { it.bandwidth ?: 0L })
    }

    /**
     * Writes a sanitized playlist to the app cache (one file per playlist
     * content, so re-picking the same variant reuses the file) and returns
     * its file:// URL. Temp files older than 7 days are pruned on write.
     */
    fun writeTemporaryHlsPlaylist(context: Context, playlistText: String): String? {
        return runCatching {
            val dir = File(context.cacheDir, "hls-quality").apply { mkdirs() }
            val now = System.currentTimeMillis()
            dir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > TEMP_PLAYLIST_MAX_AGE_MS) file.delete()
            }
            val digest = MessageDigest.getInstance("SHA-1")
                .digest(playlistText.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
            val file = File(dir, "$digest.m3u8")
            if (!file.exists()) file.writeText(playlistText)
            Uri.fromFile(file).toString()
        }.onFailure { error ->
            Log.w(TAG, "Failed to write temporary HLS playlist", error)
        }.getOrNull()
    }
}

private data class HlsMasterPlaylist(
    val baseUrl: String,
    val headerLines: List<String>,
    val mediaLines: List<HlsMediaLine>,
    val variants: List<HlsVariant>,
) {
    fun buildSanitizedMasterPlaylist(selected: HlsVariant): String = buildString {
        val sanitizedHeaderLines = headerLines.ifEmpty { listOf("#EXTM3U") }
        sanitizedHeaderLines.forEach { line ->
            append(line).append('\n')
        }

        val selectedAudioGroup = selected.audioGroupId
        mediaLines
            .filter { media ->
                selectedAudioGroup == null || media.groupId == selectedAudioGroup || media.type != "AUDIO"
            }
            .forEach { media ->
                append(media.rewrittenLine).append('\n')
            }

        append(selected.streamInfoLine).append('\n')
        append(selected.absoluteUri).append('\n')
    }

    companion object {
        fun parse(baseUrl: String, playlistText: String): HlsMasterPlaylist {
            val headerLines = mutableListOf<String>()
            val mediaLines = mutableListOf<HlsMediaLine>()
            val variants = mutableListOf<HlsVariant>()
            val lines = playlistText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                when {
                    line.startsWith("#EXT-X-MEDIA", ignoreCase = true) -> {
                        val attributes = parseAttributes(line.substringAfter(':', ""))
                        val uri = attributes["URI"]
                        mediaLines += HlsMediaLine(
                            type = attributes["TYPE"].orEmpty(),
                            groupId = attributes["GROUP-ID"],
                            rewrittenLine = if (uri != null) {
                                rewriteAttribute(line, "URI", absolutizeHlsUri(baseUrl, uri))
                            } else {
                                line
                            },
                        )
                    }
                    line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                        val attributes = parseAttributes(line.substringAfter(':', ""))
                        val uriIndex = (index + 1 until lines.size)
                            .firstOrNull { candidate -> !lines[candidate].startsWith("#") }
                        if (uriIndex != null) {
                            val variantUri = lines[uriIndex]
                            variants += HlsVariant(
                                id = "q${variants.size}",
                                streamInfoLine = line,
                                name = attributes["NAME"].orEmpty().trim('"'),
                                width = attributes["RESOLUTION"]?.substringBefore('x')?.toIntOrNull(),
                                height = attributes["RESOLUTION"]?.substringAfter('x', "")?.toIntOrNull(),
                                bandwidth = attributes["BANDWIDTH"]?.toLongOrNull(),
                                codecs = attributes["CODECS"].orEmpty(),
                                audioGroupId = attributes["AUDIO"],
                                absoluteUri = absolutizeHlsUri(baseUrl, variantUri),
                            )
                            index = uriIndex
                        }
                    }
                    line.startsWith("#EXTM3U", ignoreCase = true) ||
                        line.startsWith("#EXT-X-VERSION", ignoreCase = true) ||
                        line.startsWith("#EXT-X-INDEPENDENT-SEGMENTS", ignoreCase = true) ||
                        line.startsWith("#EXT-X-START", ignoreCase = true) -> {
                        headerLines += line
                    }
                }
                index++
            }

            return HlsMasterPlaylist(
                baseUrl = baseUrl,
                headerLines = headerLines.distinct(),
                mediaLines = mediaLines,
                variants = variants,
            )
        }
    }
}

private data class HlsMediaLine(
    val type: String,
    val groupId: String?,
    val rewrittenLine: String,
)

private data class HlsVariant(
    val id: String,
    val streamInfoLine: String,
    val name: String,
    val width: Int?,
    val height: Int?,
    val bandwidth: Long?,
    val codecs: String,
    val audioGroupId: String?,
    val absoluteUri: String,
) {
    fun toUiVariant(index: Int, playbackUrl: String): HlsQualityVariant = HlsQualityVariant(
        id = id.ifBlank { "q$index" },
        name = name,
        width = width,
        height = height,
        bandwidth = bandwidth,
        codecs = codecs,
        audioGroupId = audioGroupId,
        absoluteUri = absoluteUri,
        playbackUrl = playbackUrl,
    )
}

private fun parseAttributes(attributeText: String): Map<String, String> {
    val attributes = linkedMapOf<String, String>()
    var index = 0
    while (index < attributeText.length) {
        while (index < attributeText.length && (attributeText[index] == ',' || attributeText[index].isWhitespace())) index++
        val keyStart = index
        while (index < attributeText.length && attributeText[index] != '=') index++
        if (index >= attributeText.length) break
        val key = attributeText.substring(keyStart, index).trim().uppercase()
        index++
        val value = if (index < attributeText.length && attributeText[index] == '"') {
            index++
            val valueStart = index
            while (index < attributeText.length && attributeText[index] != '"') index++
            val quoted = attributeText.substring(valueStart, index)
            if (index < attributeText.length && attributeText[index] == '"') index++
            quoted
        } else {
            val valueStart = index
            while (index < attributeText.length && attributeText[index] != ',') index++
            attributeText.substring(valueStart, index).trim()
        }
        if (key.isNotBlank()) attributes[key] = value
        while (index < attributeText.length && attributeText[index] != ',') index++
    }
    return attributes
}

private fun rewriteAttribute(line: String, attributeName: String, value: String): String {
    val pattern = Regex("$attributeName=\"[^\"]*\"", RegexOption.IGNORE_CASE)
    return if (pattern.containsMatchIn(line)) {
        pattern.replace(line, "$attributeName=\"$value\"")
    } else {
        "$line,$attributeName=\"$value\""
    }
}

private fun absolutizeHlsUri(baseUrl: String, uri: String): String {
    val trimmed = uri.trim().trim('"')
    if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        return trimmed
    }

    val schemeSeparator = baseUrl.indexOf("://")
    if (schemeSeparator <= 0) return trimmed
    val scheme = baseUrl.substring(0, schemeSeparator)
    val afterScheme = baseUrl.substring(schemeSeparator + 3)
    val hostEnd = afterScheme.indexOf('/').let { if (it < 0) afterScheme.length else it }
    val origin = "$scheme://${afterScheme.substring(0, hostEnd)}"

    if (trimmed.startsWith("//")) return "$scheme:$trimmed"
    if (trimmed.startsWith('/')) return origin + trimmed

    val directory = baseUrl.substringBeforeLast('/', missingDelimiterValue = baseUrl)
    return "$directory/$trimmed"
}

private fun formatOneDecimal(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    val asText = rounded.toString()
    return if (asText.endsWith(".0")) asText.dropLast(2) else asText
}
