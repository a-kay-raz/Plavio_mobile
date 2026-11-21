package com.pulsestream.app.data

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.net.ConnectivityManager
import kotlin.math.log2
import kotlin.math.min

/**
 * Step 42 — Smart Streams (Nuvio streams/SmartStreamRanking.kt,
 * SmartStreamQuality.kt, SmartStreamSettings.kt, SmartStreamDevice.kt,
 * SmartStreamLanguages.kt, ported to Plavio's StreamItem + SettingsStore).
 *
 * Scored auto-pick + per-title filter/sort: instant-ness, resolution vs
 * screen, bitrate sweet spot, codec hardware, HDR, source, audio and
 * language — instead of quality-order only. Plavio has no binge groups,
 * no debrid cache-status and no behaviorHints, so those inputs are
 * approximated from title/name markers (see readInstant / readLanguages).
 */

enum class SmartResolution(val lines: Int, val label: String) {
    SD(480, "SD"),
    HD720(720, "720p"),
    FHD1080(1080, "1080p"),
    UHD4K(2160, "4K"),
}

enum class SmartHdr(val rank: Int, val label: String) {
    HDR10(1, "HDR10"),
    HDR10_PLUS(2, "HDR10+"),
    DOLBY_VISION(3, "Dolby Vision"),
}

enum class SmartCodec(val label: String) {
    HEVC("HEVC"),
    H264("H.264"),
    AV1("AV1"),
    OTHER("Other"),
}

enum class SmartAudio(val label: String) {
    ATMOS("Atmos"),
    DOLBY_DIGITAL("DD+ / DD 5.1"),
    DTS_TRUEHD("DTS / TrueHD"),
    STEREO("Stereo"),
}

enum class SmartSource(val rank: Int, val label: String) {
    HDTV(1, "HDTV"),
    WEBRIP(2, "WEBRip"),
    WEBDL(3, "WEB-DL"),
    BLURAY(4, "Blu-ray"),
    REMUX(5, "Remux"),
}

enum class SmartInstant { YES, NO, UNKNOWN }

enum class SmartSortMode(val label: String) {
    BEST_MATCH("Best match"),
    HIGHEST_QUALITY("Highest quality"),
    SMALLEST_FILE("Smallest file"),
    LARGEST_FILE("Largest file"),
    ADDON_ORDER("Addon order"),
}

enum class SmartResolutionLimit(val label: String) {
    AUTO("Auto"),
    P1080("1080p"),
    P4K("4K"),
}

enum class SmartHdrFilter(val label: String) {
    DOLBY_VISION("Dolby Vision"),
    HDR10("HDR10 / HDR10+"),
    SDR("SDR"),
}

data class SmartStreamLanguages(
    val codes: Set<String>,
    val multiAudio: Boolean,
)

data class SmartStreamInfo(
    val resolution: SmartResolution?,
    val hdr: SmartHdr?,
    val codec: SmartCodec?,
    val audio: Set<SmartAudio>,
    val source: SmartSource?,
    val sizeBytes: Long?,
    val instant: SmartInstant,
    val isLowQuality: Boolean,
    val languages: SmartStreamLanguages = SmartStreamLanguages(emptySet(), multiAudio = false),
) {
    fun summary(instantLabel: String?): String = listOfNotNull(
        resolution?.label,
        codec?.takeIf { it != SmartCodec.OTHER }?.label,
        hdr?.label,
        instantLabel?.takeIf { instant == SmartInstant.YES },
        languages.codes.takeIf { it.isNotEmpty() }?.joinToString("/") { it.uppercase() },
        sizeBytes?.let(::formatSmartSize),
    ).joinToString(" · ")
}

fun formatSmartSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1.0) {
        val tenths = (gb * 10).toLong()
        "${tenths / 10}.${tenths % 10} GB"
    } else {
        "${(bytes / 1_000_000.0).toLong()} MB"
    }
}

data class SmartStreamFilters(
    val resolutions: Set<SmartResolution> = emptySet(),
    val hdr: Set<SmartHdrFilter> = emptySet(),
    val codecs: Set<SmartCodec> = emptySet(),
    val audio: Set<SmartAudio> = emptySet(),
    val sources: Set<SmartSource> = emptySet(),
    val maxSizeGb: Int? = null,
    val instantOnly: Boolean = false,
    val hideLowQuality: Boolean = true,
) {
    val activeCount: Int
        get() = resolutions.size + hdr.size + codecs.size + audio.size + sources.size +
            (if (maxSizeGb != null) 1 else 0) + (if (instantOnly) 1 else 0) +
            (if (!hideLowQuality) 1 else 0)
}

data class SmartStreamSettings(
    val sortMode: SmartSortMode = SmartSortMode.BEST_MATCH,
    val filters: SmartStreamFilters = SmartStreamFilters(),
    val resolutionLimit: SmartResolutionLimit = SmartResolutionLimit.AUTO,
    val saveDataOnMobile: Boolean = true,
)

data class SmartScreenInfo(
    val longSidePx: Int,
    val shortSidePx: Int,
    val hdrFormats: Set<SmartHdr>,
    val hevcHardware: Boolean,
    val av1Hardware: Boolean,
    val decodes4k: Boolean,
    val externalDisplay: Boolean,
    val isTablet: Boolean,
) {
    val hdr: Boolean get() = hdrFormats.isNotEmpty()
}

enum class SmartMemoryTier { LOW, LIMITED, AMPLE }

data class SmartDeviceProfile(
    val screen: SmartScreenInfo,
    val onWifi: Boolean,
    val memoryMb: Int,
) {
    val memoryTier: SmartMemoryTier
        get() = when {
            memoryMb < LOW_MEMORY_MB -> SmartMemoryTier.LOW
            memoryMb < AMPLE_MEMORY_MB -> SmartMemoryTier.LIMITED
            else -> SmartMemoryTier.AMPLE
        }

    val maxComfortableLines: Int
        get() = when {
            !screen.decodes4k -> 1080
            memoryTier == SmartMemoryTier.LOW -> 1080
            else -> 2160
        }

    val videoLines: Int
        get() = if (screen.externalDisplay) {
            2160
        } else {
            minOf(screen.shortSidePx, screen.longSidePx * 9 / 16).coerceAtLeast(480)
        }

    val memoryLabel: String
        get() {
            val tenths = (memoryMb * 10 + 512) / 1024
            return "${tenths / 10}.${tenths % 10} GB"
        }

    companion object {
        const val LOW_MEMORY_MB = 3000
        const val AMPLE_MEMORY_MB = 5500
    }
}

/** Device profile from the real device (codecs, screen, memory, wifi); falls back to a 1080p baseline on any failure. */
fun buildSmartDeviceProfile(context: Context): SmartDeviceProfile {
    val appContext = context.applicationContext
    val screen = runCatching { readSmartScreenInfo(appContext) }.getOrElse {
        SmartScreenInfo(
            longSidePx = 1920,
            shortSidePx = 1080,
            hdrFormats = emptySet(),
            hevcHardware = true,
            av1Hardware = false,
            decodes4k = false,
            externalDisplay = false,
            isTablet = false,
        )
    }
    val onWifi = runCatching {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(true)
    val memoryMb = runCatching {
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).memoryClass
    }.getOrDefault(4096)
    return SmartDeviceProfile(screen = screen, onWifi = onWifi, memoryMb = memoryMb)
}

private fun readSmartScreenInfo(context: Context): SmartScreenInfo {
    val dm = context.resources.displayMetrics
    val longSide = maxOf(dm.widthPixels, dm.heightPixels)
    val shortSide = minOf(dm.widthPixels, dm.heightPixels)
    val codecs = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
    fun decoderFor(mime: String) = codecs.firstOrNull { codec ->
        codec.supportedTypes.any { it.equals(mime, ignoreCase = true) }
    }
    val hevc = decoderFor("video/hevc")
    val av1 = decoderFor("video/av01")
    val dv = decoderFor("video/dolby-vision")
    val decodes4k = hevc?.let { codec ->
        runCatching {
            val caps = codec.getCapabilitiesForType("video/hevc")
            val videoCaps = caps.videoCapabilities
            videoCaps != null && videoCaps.supportedWidths.upper >= 3840
        }.getOrDefault(false)
    } == true
    val hdrFormats = buildSet {
        if (dv != null) add(SmartHdr.DOLBY_VISION)
        if (hevc != null) {
            // HEVC Main10 presence is the practical HDR10 signal on Android.
            val hasMain10 = runCatching {
                hevc.getCapabilitiesForType("video/hevc").profileLevels.any { level ->
                    level.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                        level.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                        level.profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus
                }
            }.getOrDefault(false)
            if (hasMain10) {
                add(SmartHdr.HDR10)
                add(SmartHdr.HDR10_PLUS)
            } else if (decodes4k) {
                add(SmartHdr.HDR10)
            }
        }
    }
    val isTablet = context.resources.configuration.smallestScreenWidthDp >= 600
    return SmartScreenInfo(
        longSidePx = longSide,
        shortSidePx = shortSide,
        hdrFormats = hdrFormats,
        hevcHardware = hevc != null,
        av1Hardware = av1 != null,
        decodes4k = decodes4k,
        externalDisplay = false,
        isTablet = isTablet,
    )
}

/** Title/name tag parsing (Nuvio SmartStreamQuality, regexes verbatim). */
object SmartStreamQuality {
    private const val B = "(?<![a-z0-9])"
    private const val E = "(?![a-z0-9])"

    private val res4k = Regex("${B}(2160p|4k|uhd|3840x2160)$E")
    private val res1080 = Regex("${B}(1080p|1080i|fhd|1920x1080)$E")
    private val res720 = Regex("${B}(720p|1280x720)$E")
    private val resSd = Regex("${B}(480p|576p|360p|sd|dvdrip|dvd)$E")

    private val dolbyVision = Regex("${B}(dv|dovi|dolby ?vision)$E")
    private val hdr10Plus = Regex("${B}(hdr10\\+|hdr10plus|hdr10p)")
    private val hdr10 = Regex("${B}(hdr10|hdr|hlg)$E")

    private val hevc = Regex("${B}(hevc|x265|h ?265)$E")
    private val h264 = Regex("${B}(avc|x264|h ?264)$E")
    private val av1 = Regex("${B}av1$E")
    private val otherCodec = Regex("${B}(vp9|xvid|divx|mpeg2)$E")

    private val atmos = Regex("${B}atmos$E")
    private val dolbyDigital = Regex("${B}(ddp|dd\\+|eac3|e ac 3|ac3|dd|dolby digital( plus)?)")
    private val dtsTrueHd = Regex("${B}(dts|truehd|true hd)")
    private val stereo = Regex("${B}(2 0|2ch|stereo)$E")
    private val surround = Regex("${B}(5 1|7 1|6ch|8ch)$E")
    private val aac = Regex("${B}aac")

    private val remux = Regex("${B}remux$E")
    private val bluray = Regex("${B}(bluray|blu ray|bdrip|brrip|bd)$E")
    private val webdl = Regex("${B}(web dl|webdl|web)$E")
    private val webrip = Regex("${B}(webrip|web rip)$E")
    private val hdtv = Regex("${B}(hdtv|pdtv|dsr)$E")
    private val lowQuality = Regex("${B}(cam|camrip|hdcam|hdts|telesync|hdtc|telecine|scr|screener|dvdscr|r5)$E")

    private val instantMarkers = Regex("(\\+]|⚡|${B}(instant|cached)$E)")
    private val notInstantMarkers = Regex("(⬇|⏳|${B}(download|uncached|not cached)$E)")
    private val sizePattern = Regex("(\\d+(?:[.,]\\d+)?)\\s?(tb|gb|gib|mb|mib)$E")
    private val separators = Regex("[._\\-\\[\\]()|/,:]+")

    private const val CACHE_LIMIT = 3000
    private val cache = LinkedHashMap<String, SmartStreamInfo>()

    fun cached(stream: StreamItem): SmartStreamInfo {
        val key = "${stream.sourceId}|${stream.url}|${stream.fileIdx}|${stream.name}|${stream.title}"
        synchronized(cache) { cache[key] }?.let { return it }
        val info = read(stream)
        synchronized(cache) {
            if (cache.size >= CACHE_LIMIT) cache.remove(cache.keys.first())
            cache[key] = info
        }
        return info
    }

    fun read(stream: StreamItem): SmartStreamInfo {
        val raw = listOfNotNull(stream.title, stream.name, stream.sourceName)
            .joinToString(" ").lowercase()
        val text = raw.replace(separators, " ")
        return SmartStreamInfo(
            resolution = readResolution(text),
            hdr = readHdr(text),
            codec = readCodec(text),
            audio = readAudio(text),
            source = readSource(text),
            sizeBytes = readSize(raw),
            instant = readInstant(stream, raw),
            isLowQuality = lowQuality.containsMatchIn(text) &&
                !webdl.containsMatchIn(text) && !bluray.containsMatchIn(text),
            languages = SmartLanguageReader.read(raw, text),
        )
    }

    private fun readResolution(text: String): SmartResolution? = when {
        res4k.containsMatchIn(text) -> SmartResolution.UHD4K
        res1080.containsMatchIn(text) -> SmartResolution.FHD1080
        res720.containsMatchIn(text) -> SmartResolution.HD720
        resSd.containsMatchIn(text) -> SmartResolution.SD
        else -> null
    }

    private fun readHdr(text: String): SmartHdr? = when {
        dolbyVision.containsMatchIn(text) -> SmartHdr.DOLBY_VISION
        hdr10Plus.containsMatchIn(text) -> SmartHdr.HDR10_PLUS
        hdr10.containsMatchIn(text) -> SmartHdr.HDR10
        else -> null
    }

    private fun readCodec(text: String): SmartCodec? = when {
        av1.containsMatchIn(text) -> SmartCodec.AV1
        hevc.containsMatchIn(text) -> SmartCodec.HEVC
        h264.containsMatchIn(text) -> SmartCodec.H264
        otherCodec.containsMatchIn(text) -> SmartCodec.OTHER
        else -> null
    }

    private fun readAudio(text: String): Set<SmartAudio> = buildSet {
        if (atmos.containsMatchIn(text)) add(SmartAudio.ATMOS)
        if (dolbyDigital.containsMatchIn(text)) add(SmartAudio.DOLBY_DIGITAL)
        if (dtsTrueHd.containsMatchIn(text)) add(SmartAudio.DTS_TRUEHD)
        if (stereo.containsMatchIn(text) || (aac.containsMatchIn(text) && !surround.containsMatchIn(text))) {
            add(SmartAudio.STEREO)
        }
    }

    private fun readSource(text: String): SmartSource? = when {
        remux.containsMatchIn(text) -> SmartSource.REMUX
        bluray.containsMatchIn(text) -> SmartSource.BLURAY
        webrip.containsMatchIn(text) -> SmartSource.WEBRIP
        webdl.containsMatchIn(text) -> SmartSource.WEBDL
        hdtv.containsMatchIn(text) -> SmartSource.HDTV
        else -> null
    }

    private fun readSize(raw: String): Long? {
        val match = sizePattern.find(raw) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2]) {
            "tb" -> 1_000_000_000_000.0
            "gb", "gib" -> 1_000_000_000.0
            else -> 1_000_000.0
        }
        return (number * multiplier).toLong().takeIf { it > 0 }
    }

    /**
     * Plavio has no debrid cache-status: a direct http(s) link plays at once
     * (YES), a ⚡/cached marker is instant (YES), an uncached/download marker
     * or an unresolved infohash: is not (NO), everything else is UNKNOWN.
     */
    private fun readInstant(stream: StreamItem, raw: String): SmartInstant {
        if (notInstantMarkers.containsMatchIn(raw)) return SmartInstant.NO
        if (instantMarkers.containsMatchIn(raw)) return SmartInstant.YES
        val url = stream.url
        if (!url.startsWith("infohash:", ignoreCase = true) && !url.startsWith("magnet:", ignoreCase = true)) {
            return SmartInstant.YES
        }
        return SmartInstant.UNKNOWN
    }
}

/** Language tags in stream titles (Nuvio SmartLanguageReader, token/flag maps verbatim). */
object SmartLanguageReader {
    private val tokenCodes: Map<String, String> = buildMap {
        fun add(code: String, vararg tokens: String) = tokens.forEach { put(it, code) }
        add("en", "eng", "english")
        add("it", "ita", "italian", "italiano")
        add("es", "spa", "spanish", "espanol", "español", "castellano", "latino")
        add("fr", "fre", "fra", "french", "francais", "français", "vff", "vfq", "truefrench")
        add("de", "ger", "deu", "german", "deutsch")
        add("pt", "por", "portuguese", "portugues", "português", "dublado")
        add("id", "ind", "indonesian", "indonesia")
        add("ja", "jpn", "japanese")
        add("ko", "kor", "korean")
        add("zh", "zho", "chinese", "mandarin", "cantonese")
        add("hi", "hin", "hindi")
        add("ru", "rus", "russian")
        add("ar", "ara", "arabic")
        add("tr", "tur", "turkish")
        add("nl", "dut", "nld", "dutch")
        add("pl", "pol", "polish")
        add("sv", "swe", "swedish")
        add("th", "tha", "thai")
        add("vi", "vie", "vietnamese")
        add("ms", "msa", "malay")
        add("ta", "tam", "tamil")
        add("te", "tel", "telugu")
        add("uk", "ukr", "ukrainian")
        add("he", "heb", "hebrew")
        add("cs", "cze", "ces", "czech")
        add("hu", "hun", "hungarian")
        add("el", "gre", "ell", "greek")
        add("da", "dan", "danish")
        add("no", "nor", "norwegian")
        add("fi", "fin", "finnish")
        add("ro", "rum", "ron", "romanian")
    }

    private val flagCodes: Map<String, String> = mapOf(
        "🇬🇧" to "en", "🇺🇸" to "en", "🇮🇹" to "it", "🇪🇸" to "es", "🇲🇽" to "es",
        "🇫🇷" to "fr", "🇩🇪" to "de", "🇵🇹" to "pt", "🇧🇷" to "pt", "🇮🇩" to "id",
        "🇯🇵" to "ja", "🇰🇷" to "ko", "🇨🇳" to "zh", "🇹🇼" to "zh", "🇭🇰" to "zh",
        "🇮🇳" to "hi", "🇷🇺" to "ru", "🇸🇦" to "ar", "🇹🇷" to "tr", "🇳🇱" to "nl",
        "🇵🇱" to "pl", "🇸🇪" to "sv", "🇹🇭" to "th", "🇻🇳" to "vi", "🇲🇾" to "ms",
        "🇺🇦" to "uk", "🇮🇱" to "he", "🇨🇿" to "cs", "🇭🇺" to "hu", "🇬🇷" to "el",
        "🇩🇰" to "da", "🇳🇴" to "no", "🇫🇮" to "fi", "🇷🇴" to "ro",
    )

    private val multiTokens = setOf("multi", "dual", "multiaudio", "dualaudio")

    fun read(raw: String, text: String): SmartStreamLanguages {
        val codes = mutableSetOf<String>()
        flagCodes.forEach { (flag, code) -> if (raw.contains(flag)) codes.add(code) }
        text.split(' ').forEach { token -> tokenCodes[token]?.let { codes.add(it) } }
        val multi = multiTokens.any { text.contains(it) }
        return SmartStreamLanguages(codes, multi)
    }
}

data class SmartRankedStream(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val score: Double,
    val index: Int,
)

data class SmartAutoPlayRanking(
    val ranked: List<SmartRankedStream>,
    val goodEnough: Boolean,
)

sealed interface SmartReason {
    data object Instant : SmartReason
    data object NotInstant : SmartReason
    data class SharpestForScreen(val resolution: String, val lines: Int) : SmartReason
    data class SharpestWithinLimit(val resolution: String) : SmartReason
    data class DecodedByChip(val codec: String) : SmartReason
    data object H264Smooth : SmartReason
    data class HdrShown(val format: String) : SmartReason
    data class EnoughMemory(val memory: String) : SmartReason
    data object KeptTo1080 : SmartReason
    data class PreferredLanguage(val code: String) : SmartReason
    data class SensibleSize(val size: String, val onWifi: Boolean) : SmartReason
}

data class SmartPick(
    val stream: StreamItem,
    val info: SmartStreamInfo,
    val reasons: List<SmartReason>,
)

data class SmartTarget(
    val lines: Int,
    val explicitLimit: Int?,
    val sweetLowMbps: Double,
    val sweetHighMbps: Double,
    val saveData: Boolean,
    val languages: List<String> = emptyList(),
)

/** Scored ranking (Nuvio SmartStreamRanking, verbatim scoring, now
 * including the binge-group bonus — Step 60). */
object SmartStreamRanking {
    private const val BINGE_GROUP_BONUS = 25.0
    private const val HEAVY_4K_MBPS = 40.0

    fun runtimeSeconds(isEpisode: Boolean): Int = if (isEpisode) 45 * 60 else 120 * 60

    fun target(
        device: SmartDeviceProfile,
        settings: SmartStreamSettings,
        preferredLanguages: List<String> = emptyList(),
    ): SmartTarget {
        val saveData = !device.onWifi && settings.saveDataOnMobile
        val lines = when (settings.resolutionLimit) {
            SmartResolutionLimit.AUTO -> {
                val capable = if (device.memoryTier == SmartMemoryTier.AMPLE) device.maxComfortableLines else 1080
                val aim = min(device.videoLines, capable)
                if (saveData) min(aim, 1080) else aim
            }
            SmartResolutionLimit.P1080 -> 1080
            SmartResolutionLimit.P4K -> 2160
        }
        val explicitLimit = if (settings.resolutionLimit == SmartResolutionLimit.P1080) 1080 else null
        var low: Double
        var high: Double
        when {
            lines <= 800 -> { low = 1.5; high = 5.0 }
            lines <= 1300 -> { low = 4.0; high = 12.0 }
            lines <= 1700 -> { low = 6.0; high = 25.0 }
            else -> { low = 12.0; high = 40.0 }
        }
        if (saveData) {
            low = min(low, 2.5)
            high = min(high, 6.0)
        }
        return SmartTarget(
            lines = lines,
            explicitLimit = explicitLimit,
            sweetLowMbps = low,
            sweetHighMbps = high,
            saveData = saveData,
            languages = preferredLanguages,
        )
    }

    fun score(
        info: SmartStreamInfo,
        device: SmartDeviceProfile,
        target: SmartTarget,
        runtimeSeconds: Int,
    ): Double {
        var score = 0.0

        score += when (info.instant) {
            SmartInstant.YES -> 40.0
            SmartInstant.UNKNOWN -> 15.0
            SmartInstant.NO -> 0.0
        }

        val lines = info.resolution?.lines
        score += if (lines == null) {
            15.0
        } else {
            30.0 * min(lines, target.lines).toDouble() / target.lines
        }
        if (target.explicitLimit != null && lines != null && lines > target.explicitLimit) score -= 25.0
        if (lines != null && lines > device.maxComfortableLines) score -= 60.0

        val mbps = info.sizeBytes?.let { it * 8.0 / runtimeSeconds / 1_000_000.0 }
        score += when {
            mbps == null -> 4.0
            mbps < target.sweetLowMbps -> 10.0 * mbps / target.sweetLowMbps
            mbps <= target.sweetHighMbps -> 10.0
            else -> {
                val perDoubling = if (target.saveData) 20.0 else 10.0
                10.0 - min(40.0, perDoubling * log2(mbps / target.sweetHighMbps))
            }
        }

        score += when (info.codec) {
            SmartCodec.AV1 -> when {
                device.screen.av1Hardware -> 6.0
                lines != null && lines >= 2160 -> -50.0
                else -> -30.0
            }
            SmartCodec.HEVC -> if (device.screen.hevcHardware) 6.0 else -30.0
            SmartCodec.H264 -> 4.0
            SmartCodec.OTHER -> -5.0
            null -> 0.0
        }

        if (info.hdr != null && device.screen.hdr) {
            if (info.hdr in device.screen.hdrFormats) {
                val bonus = 10.0 + 4.0 * info.hdr.rank
                score += if (target.saveData) bonus / 2 else bonus
            } else if (info.hdr == SmartHdr.DOLBY_VISION) {
                score -= 10.0
            } else {
                score += 4.0
            }
        }

        if (device.memoryTier != SmartMemoryTier.AMPLE && lines != null && lines >= 2160) {
            val heavy = info.source == SmartSource.REMUX || (mbps != null && mbps > HEAVY_4K_MBPS)
            if (heavy) score -= 20.0
        }

        score += when (info.source) {
            SmartSource.REMUX, SmartSource.BLURAY, SmartSource.WEBDL -> 6.0
            SmartSource.WEBRIP -> 3.0
            SmartSource.HDTV -> 1.0
            null -> 2.0
        }
        score += when {
            SmartAudio.ATMOS in info.audio || SmartAudio.DOLBY_DIGITAL in info.audio -> 3.0
            info.audio.isNotEmpty() -> 1.0
            else -> 0.0
        }

        score += languageScore(info.languages, target.languages)

        if (info.isLowQuality) score -= 100.0
        return score
    }

    fun matches(info: SmartStreamInfo, filters: SmartStreamFilters): Boolean {
        if (filters.hideLowQuality && info.isLowQuality) return false
        if (filters.instantOnly && info.instant != SmartInstant.YES) return false
        if (filters.resolutions.isNotEmpty() && info.resolution !in filters.resolutions) return false
        if (filters.hdr.isNotEmpty()) {
            val kind = when (info.hdr) {
                SmartHdr.DOLBY_VISION -> SmartHdrFilter.DOLBY_VISION
                SmartHdr.HDR10, SmartHdr.HDR10_PLUS -> SmartHdrFilter.HDR10
                null -> SmartHdrFilter.SDR
            }
            if (kind !in filters.hdr) return false
        }
        if (filters.codecs.isNotEmpty() && info.codec !in filters.codecs) return false
        if (filters.audio.isNotEmpty() && filters.audio.none { it in info.audio }) return false
        if (filters.sources.isNotEmpty() && info.source !in filters.sources) return false
        val maxGb = filters.maxSizeGb
        if (maxGb != null) {
            val size = info.sizeBytes ?: return false
            if (size > maxGb * 1_000_000_000L) return false
        }
        return true
    }

    fun sorted(ranked: List<SmartRankedStream>, mode: SmartSortMode): List<SmartRankedStream> = when (mode) {
        SmartSortMode.BEST_MATCH, SmartSortMode.ADDON_ORDER ->
            ranked.sortedWith(compareByDescending<SmartRankedStream> { it.score }.thenBy { it.index })
        SmartSortMode.HIGHEST_QUALITY -> ranked.sortedWith(
            compareByDescending<SmartRankedStream> { it.info.resolution?.lines ?: 0 }
                .thenByDescending { it.info.hdr?.rank ?: 0 }
                .thenByDescending { it.info.source?.rank ?: 0 }
                .thenByDescending { it.info.sizeBytes ?: 0L }
                .thenBy { it.index },
        )
        SmartSortMode.SMALLEST_FILE -> ranked.sortedWith(
            compareBy<SmartRankedStream> { it.info.sizeBytes ?: Long.MAX_VALUE }
                .thenByDescending { it.score }
                .thenBy { it.index },
        )
        SmartSortMode.LARGEST_FILE -> ranked.sortedWith(
            compareByDescending<SmartRankedStream> { it.info.sizeBytes ?: -1L }
                .thenByDescending { it.score }
                .thenBy { it.index },
        )
    }

    fun languageScore(languages: SmartStreamLanguages, preferred: List<String>): Double {
        val primary = preferred.firstOrNull() ?: return 0.0
        val secondary = preferred.drop(1).toSet()
        val codes = if (primary == "en" && languages.codes.isEmpty() && !languages.multiAudio) setOf("en") else languages.codes
        return when {
            primary in codes -> 30.0
            codes.any { it in secondary } -> 20.0
            languages.multiAudio && codes.isEmpty() -> 8.0
            codes.isNotEmpty() -> -25.0
            else -> 0.0
        }
    }

    private fun preferredMatch(languages: SmartStreamLanguages, preferred: List<String>): String? =
        preferred.firstOrNull { it in languages.codes }

    fun reasons(info: SmartStreamInfo, device: SmartDeviceProfile, target: SmartTarget): List<SmartReason> = buildList {
        preferredMatch(info.languages, target.languages)?.let { add(SmartReason.PreferredLanguage(it)) }
        when (info.instant) {
            SmartInstant.YES -> add(SmartReason.Instant)
            SmartInstant.NO -> add(SmartReason.NotInstant)
            SmartInstant.UNKNOWN -> Unit
        }
        info.resolution?.let { resolution ->
            if (resolution.lines > target.lines) {
                add(SmartReason.SharpestForScreen(resolution.label, device.videoLines))
            } else {
                add(SmartReason.SharpestWithinLimit(resolution.label))
            }
        }
        when (info.codec) {
            SmartCodec.HEVC -> if (device.screen.hevcHardware) add(SmartReason.DecodedByChip("HEVC"))
            SmartCodec.H264 -> add(SmartReason.H264Smooth)
            SmartCodec.AV1 -> if (device.screen.av1Hardware) add(SmartReason.DecodedByChip("AV1"))
            else -> Unit
        }
        if (info.hdr != null && info.hdr in device.screen.hdrFormats) add(SmartReason.HdrShown(info.hdr.label))
        if (info.resolution != null && info.resolution.lines >= 2160 && device.memoryTier == SmartMemoryTier.AMPLE) {
            add(SmartReason.EnoughMemory(device.memoryLabel))
        }
        if (device.maxComfortableLines < 2160 || device.memoryTier != SmartMemoryTier.AMPLE) {
            if (info.resolution != null && info.resolution.lines <= 1080) add(SmartReason.KeptTo1080)
        }
        info.sizeBytes?.let { size ->
            add(SmartReason.SensibleSize(formatSmartSize(size), device.onWifi))
        }
    }

    fun reasonText(reason: SmartReason): String = when (reason) {
        SmartReason.Instant -> "Instant — cached, plays at once"
        SmartReason.NotInstant -> "Not cached yet"
        is SmartReason.SharpestForScreen -> "Sharpest for your ${reason.lines}p screen (${reason.resolution})"
        is SmartReason.SharpestWithinLimit -> "Best within your limit (${reason.resolution})"
        is SmartReason.DecodedByChip -> "${reason.codec} decoded by your chip"
        SmartReason.H264Smooth -> "H.264 — smooth on any device"
        is SmartReason.HdrShown -> "${reason.format} shown on your screen"
        is SmartReason.EnoughMemory -> "Your device has the memory for it (${reason.memory})"
        SmartReason.KeptTo1080 -> "Kept to 1080p for this device"
        is SmartReason.PreferredLanguage -> "Your language (${reason.code.uppercase()})"
        is SmartReason.SensibleSize -> "Sensible size (${reason.size}${if (reason.onWifi) "" else ", on mobile data"})"
    }

    /** Scored auto-play ranking: hides low quality, prefers instant streams when any exist. */
    fun rankForAutoPlay(
        streams: List<StreamItem>,
        settings: SmartStreamSettings,
        device: SmartDeviceProfile,
        isEpisode: Boolean,
        preferredLanguages: List<String> = emptyList(),
        // Step 60: streams from the episode-just-played's release group
        // earn Nuvio's +25 bonus.
        preferredBingeGroup: String? = null,
    ): SmartAutoPlayRanking {
        val runtime = runtimeSeconds(isEpisode)
        val target = target(device, settings, preferredLanguages)
        val ranked = streams
            .filter { !it.url.startsWith("infohash:", ignoreCase = true) }
            .mapIndexed { index, stream ->
                val info = SmartStreamQuality.cached(stream)
                var streamScore = score(info, device, target, runtime)
                if (preferredBingeGroup != null && stream.bingeGroup == preferredBingeGroup) {
                    streamScore += BINGE_GROUP_BONUS
                }
                SmartRankedStream(stream, info, streamScore, index)
            }
            .filter { matches(it.info, settings.filters.copy(hideLowQuality = true)) }
            .sortedWith(compareByDescending<SmartRankedStream> { it.score }.thenBy { it.index })
            .let { all -> all.filter { it.info.instant != SmartInstant.NO }.ifEmpty { all } }
        val best = ranked.firstOrNull()
        val goodEnough = best != null &&
            best.info.instant == SmartInstant.YES &&
            (best.info.resolution?.lines ?: 0) >= minOf(target.lines, device.maxComfortableLines)
        return SmartAutoPlayRanking(ranked = ranked, goodEnough = goodEnough)
    }

    /** Best pick + its reasons for a stream list (sheet header). */
    fun bestPick(
        streams: List<StreamItem>,
        settings: SmartStreamSettings,
        device: SmartDeviceProfile,
        isEpisode: Boolean,
        preferredLanguages: List<String> = emptyList(),
    ): SmartPick? {
        val runtime = runtimeSeconds(isEpisode)
        val target = target(device, settings, preferredLanguages)
        val candidates = streams
            .filter { !it.url.startsWith("infohash:", ignoreCase = true) }
            .mapIndexed { index, stream ->
                val info = SmartStreamQuality.cached(stream)
                SmartRankedStream(stream, info, score(info, device, target, runtime), index)
            }
            .filterNot { it.info.isLowQuality }
            .ifEmpty {
                streams.filter { !it.url.startsWith("infohash:", ignoreCase = true) }
                    .mapIndexed { index, stream ->
                        val info = SmartStreamQuality.cached(stream)
                        SmartRankedStream(stream, info, score(info, device, target, runtime), index)
                    }
            }
        val top = candidates.maxWithOrNull(compareBy<SmartRankedStream> { it.score }.thenByDescending { it.index })
        return top?.let { SmartPick(it.stream, it.info, reasons(it.info, device, target)) }
    }

    /** Score-sort a filtered list by the chosen sort mode (ADDON_ORDER keeps the input order). */
    fun sortStreams(
        streams: List<StreamItem>,
        settings: SmartStreamSettings,
        device: SmartDeviceProfile,
        isEpisode: Boolean,
        preferredLanguages: List<String> = emptyList(),
    ): List<StreamItem> {
        if (settings.sortMode == SmartSortMode.ADDON_ORDER) return streams
        val runtime = runtimeSeconds(isEpisode)
        val target = target(device, settings, preferredLanguages)
        val ranked = streams.mapIndexed { index, stream ->
            val info = SmartStreamQuality.cached(stream)
            SmartRankedStream(stream, info, score(info, device, target, runtime), index)
        }
        return sorted(ranked, settings.sortMode).map { it.stream }
    }
}
