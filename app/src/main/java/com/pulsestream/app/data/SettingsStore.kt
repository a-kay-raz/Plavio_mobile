@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import android.content.Context
import com.pulsestream.app.player.CENTER_MIX_LEVEL_MAX_DB
import com.pulsestream.app.player.CENTER_MIX_LEVEL_MIN_DB
import com.pulsestream.app.ui.components.DefaultCardDepthEdgeCoverage
import com.pulsestream.app.ui.components.DefaultCardDepthEdgeStrength
import com.pulsestream.app.ui.components.DefaultCardDepthSheenStrength
import kotlin.math.roundToInt
import java.io.File
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.media3.ui.AspectRatioFrameLayout
import com.pulsestream.app.ui.theme.AccentTheme

/** Aspect-ratio modes offered by the in-player screen-size button. */
val ASPECT_MODES: List<Pair<Int, String>> = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
    AspectRatioFrameLayout.RESIZE_MODE_FILL to "Fill",
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Zoom"
)

/**
 * Curated subtitle language options offered in the picker
 * (ISO 639-1 code to display name), sorted alphabetically.
 */
val SUBTITLE_LANGUAGE_OPTIONS: List<Pair<String, String>> = listOf(
    "en" to "English",
    "ar" to "Arabic",
    "bn" to "Bengali",
    "bs" to "Bosnian",
    "bg" to "Bulgarian",
    "zh" to "Chinese",
    "hr" to "Croatian",
    "cs" to "Czech",
    "da" to "Danish",
    "nl" to "Dutch",
    "fi" to "Finnish",
    "fr" to "French",
    "de" to "German",
    "el" to "Greek",
    "gu" to "Gujarati",
    "he" to "Hebrew",
    "hi" to "Hindi",
    "hu" to "Hungarian",
    "id" to "Indonesian",
    "it" to "Italian",
    "ja" to "Japanese",
    "ko" to "Korean",
    "ms" to "Malay",
    "mr" to "Marathi",
    "no" to "Norwegian",
    "fa" to "Persian",
    "pl" to "Polish",
    "pt" to "Portuguese",
    "pa" to "Punjabi",
    "ro" to "Romanian",
    "ru" to "Russian",
    "sr" to "Serbian",
    "sk" to "Slovak",
    "sl" to "Slovenian",
    "es" to "Spanish",
    "sv" to "Swedish",
    "ta" to "Tamil",
    "te" to "Telugu",
    "th" to "Thai",
    "tr" to "Turkish",
    "uk" to "Ukrainian",
    "ur" to "Urdu",
    "vi" to "Vietnamese"
).sortedBy { it.second }

/** Display name for a preferred-language code, or the code itself. */
fun subtitleLanguageName(code: String): String =
    SUBTITLE_LANGUAGE_OPTIONS.find { it.first == code }?.second ?: code.uppercase()

/**
 * Audio language options: the same curated ISO 639-1 set as subtitles
 * (an audio track's language tag usually matches these codes).
 */
val AUDIO_LANGUAGE_OPTIONS: List<Pair<String, String>> = SUBTITLE_LANGUAGE_OPTIONS

/** Display name for a preferred audio-language code, or the code itself. */
fun audioLanguageName(code: String): String =
    AUDIO_LANGUAGE_OPTIONS.find { it.first == code }?.second ?: code.uppercase()

/** Subtitle text-color choices (ARGB) offered in Settings > Playback. */
val SUBTITLE_COLOR_OPTIONS: List<Pair<Int, String>> = listOf(
    0xFFFFFFFF.toInt() to "White",
    0xFFFFFF00.toInt() to "Yellow",
    0xFF00FFFF.toInt() to "Cyan",
    0xFF00FF00.toInt() to "Green",
    0xFFFF69B4.toInt() to "Pink",
    0xFFFFA500.toInt() to "Orange",
    0xFFFF0000.toInt() to "Red"
)

/** Subtitle background/shade choices (ARGB; includes semi-transparent). */
val SUBTITLE_BG_OPTIONS: List<Pair<Int, String>> = listOf(
    0x00000000 to "None",
    0xCC000000.toInt() to "Black",
    0xAA222222.toInt() to "Dark gray",
    0xAAFFFFFF.toInt() to "White",
    0xAA0000FF.toInt() to "Blue"
)

/** Subtitle text-size choices (label to scale factor). */
val SUBTITLE_SIZE_OPTIONS: List<Pair<Float, String>> = listOf(
    0.75f to "Small",
    1.0f to "Normal",
    1.3f to "Large",
    1.6f to "Extra large"
)

/** Subtitle edge/outline style choices (label to stored index). */
val SUBTITLE_EDGE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "None",
    1 to "Outline",
    2 to "Drop shadow",
    3 to "Raised",
    4 to "Depressed"
)

fun subtitleColorName(color: Int): String =
    SUBTITLE_COLOR_OPTIONS.find { it.first == color }?.second ?: "Custom"

fun subtitleBgName(color: Int): String =
    SUBTITLE_BG_OPTIONS.find { it.first == color }?.second ?: "Custom"

fun subtitleSizeName(scale: Float): String =
    SUBTITLE_SIZE_OPTIONS.find { it.first == scale }?.second ?: "${(scale * 100).toInt()}%"

fun subtitleEdgeName(index: Int): String =
    SUBTITLE_EDGE_OPTIONS.find { it.first == index }?.second ?: "Drop shadow"

/**
 * True if a subtitle/track language tag matches a preferred language code.
 * Handles ISO 639-1 vs 639-2/B differences ("en" vs "eng", "bs" vs "bos")
 * and OpenSubtitles quirks ("pob" = Brazilian Portuguese) via canonical
 * mapping — plus BCP-47 subtag matching ("en" matches "en-US"/"en_US").
 *
 * Matching is exact or at a subtag boundary only: "hi" must NOT match "hil".
 */
fun subtitleLangMatches(tag: String?, preferredCode: String): Boolean {
    val t = tag?.lowercase()?.trim().orEmpty()
    if (t.isEmpty()) return false
    val p = preferredCode.lowercase().trim()
    val normT = normalizeSubtitleLanguageCode(t)
    val normP = normalizeSubtitleLanguageCode(p)
    if (normT.isEmpty() || normP.isEmpty()) return false
    // Step 38 (Nuvio matchesNormalizedLanguage): a Castilian "es" preference
    // must NOT match a LatAm "es-419" track (and vice versa); same for pt/pt-br.
    if (normP == "es") return normT == "es"
    if (normP == "es-419") return normT == "es-419"
    if (normP == "pt") return normT == "pt"
    if (normP == "pt-br") return normT == "pt-br"
    if (normT == normP) return true
    // Subtag-boundary prefix only — never a bare string prefix.
    fun startsSubtag(longer: String, shorter: String): Boolean =
        longer.length > shorter.length &&
            longer.startsWith(shorter) &&
            longer[shorter.length] == '-'
    return startsSubtag(normT, normP) || startsSubtag(normP, normT)
}

/**
 * Step 38: normalize a language tag to its canonical form (Nuvio
 * SubtitleLanguageMatching.normalizeLanguageCode). Name forms resolve to
 * their variant — "Spanish (Latin America)" → "es-419", "Português (Brasil)"
 * → "pt-br" — and ISO 639-2 codes map to 639-1 via the overrides below.
 * Returns "" for a blank tag.
 */
fun normalizeSubtitleLanguageCode(lang: String?): String {
    val code = lang?.trim()?.lowercase().orEmpty()
    if (code.isBlank()) return ""
    val normalizedCode = code.replace('_', '-')
    val tokenized = normalizedCode
        .replace('-', ' ')
        .replace('.', ' ')
        .replace('/', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val words = tokenized.split(' ').toSet()
    fun containsAny(vararg values: String): Boolean = values.any { value ->
        // "la" is a substring trap ("malayalam", "flamenco") — it only
        // counts as its own token; the rest keep Nuvio's substring rule.
        if (value == "la") words.contains("la") else tokenized.contains(value)
    }
    if (containsAny("portuguese", "portugues")) {
        return when {
            containsAny("brazil", "brasil", "brazilian", "brasileiro", "pt br", "ptbr", "pob", "(br)") ->
                "pt-br"
            containsAny("portugal", "european", "europeu", "iberian", "pt pt", "ptpt") ->
                "pt"
            else -> "pt"
        }
    }
    if (containsAny("spanish", "espanol", "español", "castellano")) {
        return if (containsAny(
                "latin", "latino", "latinoamerica", "latinoamericano",
                "lat am", "latam", "es 419", "es419", "la", "(419)",
            )
        ) "es-419" else "es"
    }
    if (containsAny("bahasa indonesia", "indonesian", "indonesia")) return "id"
    if (containsAny("bahasa malaysia", "bahasa melayu", "malaysian")) return "ms"
    return SUBTITLE_LANGUAGE_OVERRIDES[code] ?: normalizedCode
}

private val SUBTITLE_LANGUAGE_OVERRIDES = mapOf(
    "pt" to "pt", "pt-pt" to "pt", "pt_pt" to "pt", "por" to "pt",
    "pt-br" to "pt-br", "pt_br" to "pt-br", "br" to "pt-br", "pob" to "pt-br",
    "fre" to "fr", "ger" to "de", "deu" to "de", "dut" to "nl", "nld" to "nl",
    "chi" to "zh", "zho" to "zh", "jpn" to "ja", "kor" to "ko", "ara" to "ar",
    "hin" to "hi", "rus" to "ru", "pol" to "pl", "spa" to "es", "spl" to "es-419",
    "es-419" to "es-419", "es_419" to "es-419", "es-la" to "es-419", "es-lat" to "es-419",
    "fra" to "fr", "ita" to "it", "eng" to "en", "swe" to "sv", "nor" to "no",
    "dan" to "da", "fin" to "fi", "tur" to "tr", "ell" to "el", "gre" to "el",
    "heb" to "he", "tha" to "th", "vie" to "vi", "ind" to "id", "msa" to "ms",
    "may" to "ms", "ces" to "cs", "cze" to "cs", "hun" to "hu", "ron" to "ro",
    "rum" to "ro", "ukr" to "uk", "bul" to "bg", "hrv" to "hr", "srp" to "sr",
    "slk" to "sk", "slo" to "sk", "slv" to "sl", "zht" to "zh-tw", "zhs" to "zh-cn",
    "chi-tw" to "zh-tw", "chi-cn" to "zh-cn", "zh-tw" to "zh-tw", "zh_tw" to "zh-tw",
    "zh-cn" to "zh-cn", "zh_cn" to "zh-cn", "cat" to "ca", "alb" to "sq",
    "sqi" to "sq", "bos" to "bs", "mac" to "mk", "mkd" to "mk", "lav" to "lv",
    "lit" to "lt", "est" to "et", "isl" to "is", "ice" to "is", "glg" to "gl",
    "baq" to "eu", "eus" to "eu", "wel" to "cy", "cym" to "cy", "gle" to "ga",
    "ben" to "bn", "tam" to "ta", "tel" to "te", "mal" to "ml", "kan" to "kn",
    "mar" to "mr", "pan" to "pa", "guj" to "gu", "urd" to "ur", "fas" to "fa",
    "per" to "fa", "amh" to "am", "swa" to "sw", "zul" to "zu", "afr" to "af",
    "mlt" to "mt", "bel" to "be", "geo" to "ka", "kat" to "ka", "arm" to "hy",
    "hye" to "hy", "aze" to "az", "kaz" to "kk", "uzb" to "uz"
)

private val SUBTITLE_BRAZILIAN_TAGS = listOf(
    "pt-br", "pt_br", "pob", "brazilian", "brazil", "brasil", "brasileiro", " br", "(br)",
)
private val SUBTITLE_EUROPEAN_PT_TAGS = listOf(
    "pt-pt", "pt_pt", "iberian", "european", "portugal", "europeu", " eu", "(eu)",
)
private val SUBTITLE_LATINO_TAGS = listOf(
    "es-419", "es_419", "es-la", "es-lat", "latino", "latinoamerica",
    "latinoamericano", "latam", "lat am", "latin america",
)
private val SUBTITLE_CASTILIAN_TAGS = listOf(
    "es-es", "es_es", "castilian", "castellano", "spain", "españa", "espana", "iberian",
)
private val SUBTITLE_INDONESIAN_TAGS = listOf(
    "indonesia", "indonesian", "bahasa indonesia",
)

/**
 * Step 38: detect a track's language variant from its language + display
 * name + id (Nuvio SubtitleLanguageMatching.detectTrackLanguageVariant).
 * A bare "es" track whose name says Latino is "es-419"; a bare "pt" track
 * whose name says Brasil is "pt-br".
 */
fun detectTrackLanguageVariant(language: String?, name: String?, trackId: String?): String {
    val baseLang = normalizeSubtitleLanguageCode(language ?: "")
    val haystack = listOfNotNull(name, language, trackId).joinToString(" ").lowercase()
    if (baseLang == "pt" || baseLang == "por") {
        val hasBrazilian = SUBTITLE_BRAZILIAN_TAGS.any { haystack.contains(it) }
        val hasEuropean = SUBTITLE_EUROPEAN_PT_TAGS.any { haystack.contains(it) }
        if (hasBrazilian && !hasEuropean) return "pt-br"
        if (hasEuropean && !hasBrazilian) return "pt"
        return baseLang
    }
    if (baseLang == "es" || baseLang == "spa") {
        val hasLatino = SUBTITLE_LATINO_TAGS.any { haystack.contains(it) }
        val hasCastilian = SUBTITLE_CASTILIAN_TAGS.any { haystack.contains(it) }
        if (hasLatino && !hasCastilian) return "es-419"
        if (hasCastilian && !hasLatino) return "es"
        return baseLang
    }
    if (baseLang == "ms" || baseLang == "msa" || baseLang == "may") {
        if (SUBTITLE_INDONESIAN_TAGS.any { haystack.contains(it) }) return "id"
        return baseLang
    }
    return baseLang
}

/** Quality buckets offered in Settings > Streams (canonical sort keys). */
val QUALITY_ORDER_OPTIONS: List<String> = listOf("2160p", "1080p", "720p", "480p")

/** Autoplay quality choices: "Auto" follows the quality order above. */
val AUTOPLAY_QUALITY_OPTIONS: List<String> = listOf("Auto") + QUALITY_ORDER_OPTIONS

/** Autoplay max-size choices (GB; 0 = no limit). */
val AUTOPLAY_SIZE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "No limit",
    2 to "Under 2 GB",
    5 to "Under 5 GB",
    10 to "Under 10 GB",
    20 to "Under 20 GB"
)

fun autoplaySizeName(gb: Int): String =
    AUTOPLAY_SIZE_OPTIONS.find { it.first == gb }?.second ?: if (gb <= 0) "No limit" else "Under $gb GB"

/**
 * Buffer presets offered in Settings > Playback > Buffer & Cache.
 * Each preset maps to (minBufferMs, maxBufferMs, bufferForPlaybackMs).
 */
val BUFFER_PRESETS: List<Pair<String, Triple<Int, Int, Int>>> = listOf(
    "Low" to Triple(10_000, 30_000, 1_500),
    "Balanced" to Triple(15_000, 50_000, 2_500),
    "Aggressive" to Triple(30_000, 100_000, 5_000)
)

fun bufferPresetName(minMs: Int, maxMs: Int, forPlaybackMs: Int): String =
    BUFFER_PRESETS.find { it.second == Triple(minMs, maxMs, forPlaybackMs) }?.first
        ?: "Custom (${minMs / 1000}s / ${maxMs / 1000}s)"

/** Fine buffer value options (Step 39): per-second tuning instead of presets only. */
val BUFFER_MIN_OPTIONS_S: List<Int> = listOf(5, 10, 15, 30, 60, 120)
val BUFFER_MAX_OPTIONS_S: List<Int> = listOf(10, 30, 50, 100, 150, 300)
val BUFFER_START_OPTIONS_MS: List<Int> = listOf(1_000, 1_500, 2_500, 5_000, 10_000, 15_000, 30_000)
val BUFFER_REBUFFER_OPTIONS_MS: List<Int> = listOf(1_000, 2_500, 5_000, 10_000, 15_000, 30_000)
val BUFFER_BACK_OPTIONS_S: List<Int> = listOf(0, 5, 10, 30, 60)

/** Failover options (Step 40): how many sources to try, and how long a source gets to start. */
val FALLBACK_MAX_SOURCES_OPTIONS: List<Int> = listOf(1, 2, 3, 4, 5, 8, 10)
val FALLBACK_STARTUP_TIMEOUT_OPTIONS_S: List<Int> = listOf(5, 10, 15, 20, 25, 30, 45)

/** Disk cache size options in MB (0 = off). */
val DISK_CACHE_OPTIONS: List<Pair<Int, String>> = listOf(
    0 to "Off",
    100 to "100 MB",
    500 to "500 MB",
    1024 to "1 GB"
)

/** Watched-threshold options in % (Nuvio WatchedThresholdSettings.options). */
val WATCHED_THRESHOLD_OPTIONS: List<Int> = listOf(80, 85, 90, 95, 99)

/** Double-tap skip options, seconds (Nuvio PlayerSkipSecondsOptions). */
val SKIP_SECONDS_OPTIONS: List<Int> = listOf(5, 10, 15, 30)

private fun normalizeSkipSeconds(seconds: Int?): Int =
    seconds?.takeIf { it in SKIP_SECONDS_OPTIONS } ?: 10

/** Hold-to-speed options (Nuvio HoldToSpeedValueDialog): 1.25–3x. */
val HOLD_SPEED_OPTIONS: List<Float> = listOf(1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

private fun normalizeHoldSpeed(speed: Float?): Float =
    speed?.takeIf { it in HOLD_SPEED_OPTIONS } ?: 2f

private fun normalizeResizeMode(mode: Int): Int =
    mode.takeIf { it in ASPECT_MODES.map { m -> m.first } }
        ?: AspectRatioFrameLayout.RESIZE_MODE_FIT

fun diskCacheName(mb: Int): String =
    DISK_CACHE_OPTIONS.find { it.first == mb }?.second
        ?: if (mb <= 0) "Off" else "$mb MB"

/** Which app plays video streams. */
enum class PlayerChoice(val label: String, val blurb: String) {
    BUILT_IN(
        "Built-in player",
        "Plavio's own player — gestures, subtitles, PiP, skip intro"
    ),
    EXTERNAL(
        "External player",
        "Open streams in VLC, MX Player or another app you choose"
    ),
    ASK(
        "Ask every time",
        "Choose the player each time you press play"
    );

    companion object {
        fun fromName(name: String?): PlayerChoice =
            values().find { it.name == name } ?: BUILT_IN
    }
}

/** Tiny SharedPreferences-backed store for user settings (no extra dependency). */
class SettingsStore(
    context: Context,
    /** Step 56: per-profile settings file. The global file ("pulse_settings") holds device-level keys only. */
    prefsName: String = "pulse_settings"
) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    /**
     * Adaptive quality ceiling (user's order 2026-10-20, Netflix/YouTube style):
     * the highest quality this device + network can actually handle.
     * - Device cap: based on the screen's smaller dimension in pixels.
     *   A 720p phone gains nothing from a 4K stream - it just buffers more.
     * - Network cap: based on the system's downstream bandwidth estimate.
     * Returns one of QUALITY_ORDER_OPTIONS ("480p".."2160p").
     */
    fun adaptiveQualityCap(): String {
        // Device cap from screen resolution.
        val metrics = appContext.resources.displayMetrics
        val shortSide = minOf(metrics.widthPixels, metrics.heightPixels)
        val deviceCap = when {
            shortSide >= 1440 -> "2160p" // QHD+ screen: 4K downscale still beats 1080p
            shortSide >= 1080 -> "1080p"
            shortSide >= 720 -> "720p"
            else -> "480p"
        }
        // Network cap from the system's link bandwidth estimate.
        val networkCap = try {
            val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
            val downKbps = caps?.linkDownstreamBandwidthKbps ?: 0
            when {
                downKbps <= 0 -> deviceCap // unknown - trust the device
                downKbps >= 25_000 -> "2160p" // 25+ Mbps for 4K
                downKbps >= 10_000 -> "1080p" // 10+ Mbps for 1080p (headroom)
                downKbps >= 5_000 -> "720p"   // 5+ Mbps for 720p
                else -> "480p"
            }
        } catch (e: Exception) {
            deviceCap
        }
        // The lower of the two wins.
        val ladder = listOf("480p", "720p", "1080p", "2160p")
        return if (ladder.indexOf(networkCap) < ladder.indexOf(deviceCap)) networkCap else deviceCap
    }
    /**
     * Onboarding flag lives in its OWN prefs file: Android auto-backup must
     * never restore onboarding_done=true on a fresh install / new device,
     * or the first-run guide is silently skipped (see backup_rules.xml).
     */
    private val onboardingPrefs: SharedPreferences =
        appContext.getSharedPreferences("pulse_onboarding", Context.MODE_PRIVATE)
    private val secretPrefs: SharedPreferences =
        appContext.getSharedPreferences("pulse_secret_settings", Context.MODE_PRIVATE)

    // Type-tolerant numeric reads. A backup restore (org.json turns 1.0 into
    // an Int) or an older version can store a number under a key in the wrong
    // numeric type, and the strict SharedPreferences getters then throw
    // ClassCastException (Sentry PLAVIO-1). These coerce any stored Number;
    // a missing or non-numeric value falls back to the default, exactly like
    // the typed getters did for absent keys.
    private fun prefFloat(key: String, default: Float): Float =
        (prefs.all[key] as? Number)?.toFloat() ?: default

    private fun prefInt(key: String, default: Int): Int =
        (prefs.all[key] as? Number)?.toInt() ?: default

    private fun prefLong(key: String, default: Long): Long =
        (prefs.all[key] as? Number)?.toLong() ?: default

    init { migrateLegacySecrets() }

    /** Play the first source automatically (v0.2.0 behavior) vs. manual play. */
    var autoplay: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY, value).apply()

    /** Sentry crash reports (owner's order 2026-10-23): ON by default. */
    var sentryEnabled: Boolean
        get() = prefs.getBoolean(KEY_SENTRY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SENTRY_ENABLED, value).apply()

    /** Automatically start the next episode when one finishes (series only). */
    var autoPlayNext: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_NEXT, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_NEXT, value).apply()

    /** Step 60: next-episode autoplay prefers the release group of the
     * episode just played (Nuvio streamAutoPlayPreferBingeGroup,
     * default true). */
    var autoplayPreferBingeGroup: Boolean
        get() = prefs.getBoolean(KEY_AUTOPLAY_PREFER_BINGE_GROUP, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTOPLAY_PREFER_BINGE_GROUP, value).apply()

    /** Play series episodes in random order instead of sequential (v0.9.0+). */
    var shuffleEpisodes: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE_EPISODES, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE_EPISODES, value).apply()

    /** Picture-in-Picture: allow the player to shrink into a floating window (v0.9.0+). */
    var pipEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PIP_ENABLED, value).apply()

    /**
     * New-episode alerts: a daily background check notifies when shows in
     * the watchlist release new episodes. Default ON; the Settings toggle
     * also requests the POST_NOTIFICATIONS runtime permission on Android 13+.
     */
    var episodeNotifications: Boolean
        get() = prefs.getBoolean(KEY_EPISODE_NOTIFICATIONS, true)
        set(value) = prefs.edit().putBoolean(KEY_EPISODE_NOTIFICATIONS, value).apply()

    /**
     * Which player opens streams: the built-in Plavio player, an external
     * app chosen by the system chooser, or ask every time.
     * Stored as [PlayerChoice.name]; defaults to built-in.
     */
    var preferredPlayer: String
        get() = prefs.getString(KEY_PREFERRED_PLAYER, PlayerChoice.BUILT_IN.name)
            ?: PlayerChoice.BUILT_IN.name
        set(value) = prefs.edit().putString(KEY_PREFERRED_PLAYER, value).apply()

    // ------------------------------------------------------------------
    // Buffer & cache (v0.9.0+): player buffering + on-disk playback cache.
    // ------------------------------------------------------------------

    /** Minimum media buffered before playback can proceed (ms). */
    var bufferMinMs: Int
        get() = prefInt(KEY_BUFFER_MIN_MS, 15_000).coerceIn(5_000, 120_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_MIN_MS, value.coerceIn(5_000, 120_000)).apply()

    /** Maximum media buffered ahead during playback (ms). */
    var bufferMaxMs: Int
        get() = prefInt(KEY_BUFFER_MAX_MS, 50_000).coerceIn(10_000, 300_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_MAX_MS, value.coerceIn(10_000, 300_000)).apply()

    /** How much must be buffered before playback starts/resumes (ms). */
    var bufferForPlaybackMs: Int
        get() = prefInt(KEY_BUFFER_FOR_PLAYBACK_MS, 2_500).coerceIn(1_000, 30_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_FOR_PLAYBACK_MS, value.coerceIn(1_000, 30_000)).apply()

    /**
     * Custom per-second buffer values (Step 39, Nuvio
     * customPlaybackBuffersEnabled). Off = presets only: rebuffer is
     * derived (2x start) and no back buffer is kept.
     */
    var customPlaybackBuffersEnabled: Boolean
        get() = prefs.getBoolean(KEY_CUSTOM_BUFFERS, false)
        set(value) = prefs.edit().putBoolean(KEY_CUSTOM_BUFFERS, value).apply()

    /** How much must be buffered after a stall before playback resumes (ms). Only when custom buffers are on. */
    var bufferRebufferMs: Int
        get() = prefInt(KEY_BUFFER_REBUFFER_MS, 5_000).coerceIn(1_000, 30_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_REBUFFER_MS, value.coerceIn(1_000, 30_000)).apply()

    /** Already-played media kept buffered for instant seek-back (ms). Only when custom buffers are on. */
    var bufferBackBufferMs: Int
        get() = prefInt(KEY_BUFFER_BACK_BUFFER_MS, 10_000).coerceIn(0, 60_000)
        set(value) = prefs.edit().putInt(KEY_BUFFER_BACK_BUFFER_MS, value.coerceIn(0, 60_000)).apply()

    /**
     * Size the disk cache from free space (1/5 of free, 256 MB – 10 GB)
     * instead of the fixed size (Nuvio vodDiskCacheAutoSize, default on).
     * Only applies when the disk cache is enabled (size > 0).
     */
    var diskCacheAutoSize: Boolean
        get() = prefs.getBoolean(KEY_DISK_CACHE_AUTO_SIZE, true)
        set(value) = prefs.edit().putBoolean(KEY_DISK_CACHE_AUTO_SIZE, value).apply()

    /**
     * Max sources failover tries before giving up (Step 40, Nuvio
     * streamAutoPlayFallbackMaxSources, default 3). Applies to the generic
     * error + VOD-stall paths; mobile Live keeps its own policy cap.
     */
    var fallbackMaxSources: Int
        get() = prefInt(KEY_FALLBACK_MAX_SOURCES, 3).coerceIn(1, 10)
        set(value) = prefs.edit().putInt(KEY_FALLBACK_MAX_SOURCES, value.coerceIn(1, 10)).apply()

    /**
     * How long a source gets to start before failover tries the next one
     * (Step 40, Nuvio streamAutoPlayFallbackStartupTimeoutSeconds,
     * default 15s, range 5–45s). Applies to the VOD + Live startup
     * watchdogs; mid-play rebuffer timeouts are unchanged.
     */
    var fallbackStartupTimeoutSeconds: Int
        get() = prefInt(KEY_FALLBACK_STARTUP_TIMEOUT_S, 15).coerceIn(5, 45)
        set(value) = prefs.edit().putInt(KEY_FALLBACK_STARTUP_TIMEOUT_S, value.coerceIn(5, 45)).apply()

    /** Shuffle sheet: include watched episodes in the random pool (Step 41). */
    var shuffleIncludeWatched: Boolean
        get() = prefs.getBoolean(KEY_SHUFFLE_INCLUDE_WATCHED, false)
        set(value) = prefs.edit().putBoolean(KEY_SHUFFLE_INCLUDE_WATCHED, value).apply()

    // Smart Streams (Step 42): scored ranking + per-title filter/sort.
    // ------------------------------------------------------------------

    /** Device profile for Smart Streams scoring, cached per process. */
    val smartDeviceProfile: SmartDeviceProfile by lazy { buildSmartDeviceProfile(appContext) }

    /** Sort mode for the stream list: best | quality | smallest | largest | addon. */
    var smartSortMode: String
        get() = prefs.getString(KEY_SMART_SORT_MODE, "best") ?: "best"
        set(value) = prefs.edit().putString(KEY_SMART_SORT_MODE, value).apply()

    /** Resolution limit for the auto-pick target: auto | 1080 | 4k. */
    var smartResolutionLimit: String
        get() = prefs.getString(KEY_SMART_RESOLUTION_LIMIT, "auto") ?: "auto"
        set(value) = prefs.edit().putString(KEY_SMART_RESOLUTION_LIMIT, value).apply()

    /** Cap the auto-pick target at 1080p on mobile data (Nuvio SAVE_DATA). */
    var smartSaveDataOnMobile: Boolean
        get() = prefs.getBoolean(KEY_SMART_SAVE_DATA_MOBILE, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_SAVE_DATA_MOBILE, value).apply()

    private fun smartFilterSet(key: String): Set<String> =
        prefs.getString(key, "")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            ?: emptySet()

    private fun setSmartFilterSet(key: String, value: Set<String>) =
        prefs.edit().putString(key, value.joinToString(",")).apply()

    /** Filter: resolutions ("4K" | "1080p" | "720p" | "SD"); empty = all. */
    var smartFilterResolutions: Set<String>
        get() = smartFilterSet(KEY_SMART_FILTER_RESOLUTIONS)
        set(value) = setSmartFilterSet(KEY_SMART_FILTER_RESOLUTIONS, value)

    /** Filter: HDR ("DV" | "HDR10" | "SDR"); empty = all. */
    var smartFilterHdr: Set<String>
        get() = smartFilterSet(KEY_SMART_FILTER_HDR)
        set(value) = setSmartFilterSet(KEY_SMART_FILTER_HDR, value)

    /** Filter: codecs ("HEVC" | "H264" | "AV1" | "OTHER"); empty = all. */
    var smartFilterCodecs: Set<String>
        get() = smartFilterSet(KEY_SMART_FILTER_CODECS)
        set(value) = setSmartFilterSet(KEY_SMART_FILTER_CODECS, value)

    /** Filter: audio ("ATMOS" | "DD" | "DTS" | "STEREO"); empty = all. */
    var smartFilterAudio: Set<String>
        get() = smartFilterSet(KEY_SMART_FILTER_AUDIO)
        set(value) = setSmartFilterSet(KEY_SMART_FILTER_AUDIO, value)

    /** Filter: sources ("REMUX" | "BLURAY" | "WEBDL" | "WEBRIP" | "HDTV"); empty = all. */
    var smartFilterSources: Set<String>
        get() = smartFilterSet(KEY_SMART_FILTER_SOURCES)
        set(value) = setSmartFilterSet(KEY_SMART_FILTER_SOURCES, value)

    /** Filter: max file size in GB (0 = no limit). */
    var smartFilterMaxSizeGb: Int
        get() = prefInt(KEY_SMART_FILTER_MAX_SIZE_GB, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_SMART_FILTER_MAX_SIZE_GB, value.coerceAtLeast(0)).apply()

    /** Filter: instant (cached) streams only. */
    var smartFilterInstantOnly: Boolean
        get() = prefs.getBoolean(KEY_SMART_FILTER_INSTANT_ONLY, false)
        set(value) = prefs.edit().putBoolean(KEY_SMART_FILTER_INSTANT_ONLY, value).apply()

    /** Filter: hide CAM / screener / low-quality streams (default on). */
    var smartFilterHideLowQuality: Boolean
        get() = prefs.getBoolean(KEY_SMART_FILTER_HIDE_LOW_QUALITY, true)
        set(value) = prefs.edit().putBoolean(KEY_SMART_FILTER_HIDE_LOW_QUALITY, value).apply()

    /** The Smart Streams settings object the ranking reads. */
    fun smartStreamSettings(): SmartStreamSettings = SmartStreamSettings(
        sortMode = when (smartSortMode) {
            "quality" -> SmartSortMode.HIGHEST_QUALITY
            "smallest" -> SmartSortMode.SMALLEST_FILE
            "largest" -> SmartSortMode.LARGEST_FILE
            "addon" -> SmartSortMode.ADDON_ORDER
            else -> SmartSortMode.BEST_MATCH
        },
        filters = SmartStreamFilters(
            resolutions = smartFilterResolutions.mapNotNull { label ->
                when (label) {
                    "4K" -> SmartResolution.UHD4K
                    "1080p" -> SmartResolution.FHD1080
                    "720p" -> SmartResolution.HD720
                    "SD" -> SmartResolution.SD
                    else -> null
                }
            }.toSet(),
            hdr = smartFilterHdr.mapNotNull { label ->
                when (label) {
                    "DV" -> SmartHdrFilter.DOLBY_VISION
                    "HDR10" -> SmartHdrFilter.HDR10
                    "SDR" -> SmartHdrFilter.SDR
                    else -> null
                }
            }.toSet(),
            codecs = smartFilterCodecs.mapNotNull { label ->
                when (label) {
                    "HEVC" -> SmartCodec.HEVC
                    "H264" -> SmartCodec.H264
                    "AV1" -> SmartCodec.AV1
                    "OTHER" -> SmartCodec.OTHER
                    else -> null
                }
            }.toSet(),
            audio = smartFilterAudio.mapNotNull { label ->
                when (label) {
                    "ATMOS" -> SmartAudio.ATMOS
                    "DD" -> SmartAudio.DOLBY_DIGITAL
                    "DTS" -> SmartAudio.DTS_TRUEHD
                    "STEREO" -> SmartAudio.STEREO
                    else -> null
                }
            }.toSet(),
            sources = smartFilterSources.mapNotNull { label ->
                when (label) {
                    "REMUX" -> SmartSource.REMUX
                    "BLURAY" -> SmartSource.BLURAY
                    "WEBDL" -> SmartSource.WEBDL
                    "WEBRIP" -> SmartSource.WEBRIP
                    "HDTV" -> SmartSource.HDTV
                    else -> null
                }
            }.toSet(),
            maxSizeGb = smartFilterMaxSizeGb.takeIf { it > 0 },
            instantOnly = smartFilterInstantOnly,
            hideLowQuality = smartFilterHideLowQuality,
        ),
        resolutionLimit = when (smartResolutionLimit) {
            "1080" -> SmartResolutionLimit.P1080
            "4k" -> SmartResolutionLimit.P4K
            else -> SmartResolutionLimit.AUTO
        },
        saveDataOnMobile = smartSaveDataOnMobile,
    )

    /** Preferred languages for Smart Streams scoring: audio first, then subtitle languages (639-1, distinct). */
    fun smartPreferredLanguages(): List<String> {
        val langs = listOfNotNull(preferredAudioLanguage) + subtitleLanguages
        return langs.map { normalizeSubtitleLanguageCode(it) }
            .map { it.substringBefore('-') }
            .filter { it.isNotBlank() }
            .distinct()
    }

    /** Reset all Smart Streams filters (keeps sort mode + resolution limit). */
    fun resetSmartFilters() {
        smartFilterResolutions = emptySet()
        smartFilterHdr = emptySet()
        smartFilterCodecs = emptySet()
        smartFilterAudio = emptySet()
        smartFilterSources = emptySet()
        smartFilterMaxSizeGb = 0
        smartFilterInstantOnly = false
        smartFilterHideLowQuality = true
    }

    // Debrid stream preferences (Step 44) — applied in applyStreamPreferences.
    // ------------------------------------------------------------------

    /** Minimum resolution for listed streams: "any" | "720" | "1080" | "2160". */
    var debridMinQuality: String
        get() = prefs.getString(KEY_DEBRID_MIN_QUALITY, "any") ?: "any"
        set(value) = prefs.edit().putString(KEY_DEBRID_MIN_QUALITY, value).apply()

    /** Dolby Vision filter: "any" | "only" | "exclude". */
    var debridDvFilter: String
        get() = prefs.getString(KEY_DEBRID_DV_FILTER, "any") ?: "any"
        set(value) = prefs.edit().putString(KEY_DEBRID_DV_FILTER, value).apply()

    /** HDR filter (HDR10 / HDR10+ / DV): "any" | "only" | "exclude". */
    var debridHdrFilter: String
        get() = prefs.getString(KEY_DEBRID_HDR_FILTER, "any") ?: "any"
        set(value) = prefs.edit().putString(KEY_DEBRID_HDR_FILTER, value).apply()

    /** Codec filter: "any" | "h264" | "hevc" | "av1". */
    var debridCodecFilter: String
        get() = prefs.getString(KEY_DEBRID_CODEC_FILTER, "any") ?: "any"
        set(value) = prefs.edit().putString(KEY_DEBRID_CODEC_FILTER, value).apply()

    private fun debridTagSet(key: String): Set<String> =
        prefs.getString(key, "")?.split(",")?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()

    private fun setDebridTagSet(key: String, value: Set<String>) =
        prefs.edit().putString(key, value.joinToString(", ")).apply()

    /** Preferred tag substrings (comma-separated): matching streams sort first. */
    var debridPreferredTags: Set<String>
        get() = debridTagSet(KEY_DEBRID_PREFERRED_TAGS)
        set(value) = setDebridTagSet(KEY_DEBRID_PREFERRED_TAGS, value)

    /** Required tag substrings (comma-separated): a stream must contain all of them. */
    var debridRequiredTags: Set<String>
        get() = debridTagSet(KEY_DEBRID_REQUIRED_TAGS)
        set(value) = setDebridTagSet(KEY_DEBRID_REQUIRED_TAGS, value)

    /** Excluded tag substrings (comma-separated): a stream containing any is hidden. */
    var debridExcludedTags: Set<String>
        get() = debridTagSet(KEY_DEBRID_EXCLUDED_TAGS)
        set(value) = setDebridTagSet(KEY_DEBRID_EXCLUDED_TAGS, value)

    /** Name template for debrid-resolved rows ({resolution} {service} …); blank = keep the addon name. */
    var debridNameTemplate: String
        get() = prefs.getString(KEY_DEBRID_NAME_TEMPLATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEBRID_NAME_TEMPLATE, value).apply()

    /** Description template for debrid-resolved rows; blank = no extra line. */
    var debridDescriptionTemplate: String
        get() = prefs.getString(KEY_DEBRID_DESCRIPTION_TEMPLATE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DEBRID_DESCRIPTION_TEMPLATE, value).apply()

    /** The debrid stream filter prefs the list pipeline reads. */
    fun debridStreamFilterPrefs(): DebridStreamFilterPrefs = DebridStreamFilterPrefs(
        minQualityLines = when (debridMinQuality) {
            "720" -> 720
            "1080" -> 1080
            "2160" -> 2160
            else -> 0
        },
        dvFilter = debridDvFilter,
        hdrFilter = debridHdrFilter,
        codecFilter = debridCodecFilter,
        preferredTags = debridPreferredTags,
        requiredTags = debridRequiredTags,
        excludedTags = debridExcludedTags,
    )

    /**
     * On-disk playback cache size in MB (0 = off). Caches progressive
     * media so re-watches don't re-download. HLS/DASH segments are cached
     * too; live streams are unaffected (they're never re-watched).
     */
    var diskCacheMb: Int
        get() = prefInt(KEY_DISK_CACHE_MB, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_DISK_CACHE_MB, value.coerceAtLeast(0)).apply()

    /**
     * Watched threshold (Nuvio WatchedThresholdSettings): % of a title that
     * must be played before it counts as watched. Default 90; options
     * 80/85/90/95/99. Used by savePlayback's watched mark.
     */
    var watchedThresholdPercent: Int
        get() = prefInt(KEY_WATCHED_THRESHOLD_PERCENT, 90).coerceIn(80, 99)
        set(value) = prefs.edit().putInt(KEY_WATCHED_THRESHOLD_PERCENT, value.coerceIn(80, 99)).apply()

    /** Double-tap skip backward, seconds (Nuvio skipBackwardSeconds). Options 5/10/15/30, default 10. */
    var skipBackwardSeconds: Int
        get() = normalizeSkipSeconds(prefInt(KEY_SKIP_BACKWARD_SECONDS, 10))
        set(value) = prefs.edit().putInt(KEY_SKIP_BACKWARD_SECONDS, normalizeSkipSeconds(value)).apply()

    /** Double-tap skip forward, seconds (Nuvio skipForwardSeconds). */
    var skipForwardSeconds: Int
        get() = normalizeSkipSeconds(prefInt(KEY_SKIP_FORWARD_SECONDS, 10))
        set(value) = prefs.edit().putInt(KEY_SKIP_FORWARD_SECONDS, normalizeSkipSeconds(value)).apply()

    /** Hold-to-speed boost (Nuvio holdToSpeedValue): long-press speed. Options 1.25/1.5/1.75/2/2.5/3, default 2. */
    var holdSpeedValue: Float
        get() = normalizeHoldSpeed(prefFloat(KEY_HOLD_SPEED, 2f))
        set(value) = prefs.edit().putFloat(KEY_HOLD_SPEED, normalizeHoldSpeed(value)).apply()

    /** Master touch-gesture switch (Nuvio touchGesturesEnabled). Off = tap-only: no double-tap skip, drags or hold. */
    var touchGesturesEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOUCH_GESTURES_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TOUCH_GESTURES_ENABLED, value).apply()

    /** Horizontal swipe seeks (Nuvio swipeToSeekEnabled). Only applies while touch gestures are on. */
    var swipeToSeekEnabled: Boolean
        get() = prefs.getBoolean(KEY_SWIPE_TO_SEEK_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SWIPE_TO_SEEK_ENABLED, value).apply()

    /** Resize mode (Nuvio resizeMode): Fit/Fill/Zoom, persisted so it survives restarts. Stored as the raw AspectRatioFrameLayout constant. */
    var resizeMode: Int
        get() = normalizeResizeMode(prefInt(KEY_RESIZE_MODE, AspectRatioFrameLayout.RESIZE_MODE_FIT))
        set(value) = prefs.edit().putInt(KEY_RESIZE_MODE, normalizeResizeMode(value)).apply()

    /** Audio boost, dB (Nuvio audioAmplificationDb): 0 (off) to 10, persisted — the Audio modal's stepper writes it and it applies on the next title. */
    var audioBoostDb: Int
        get() = prefInt(KEY_AUDIO_BOOST_DB, 0).coerceIn(0, 10)
        set(value) = prefs.edit().putInt(KEY_AUDIO_BOOST_DB, value.coerceIn(0, 10)).apply()

    /** Step 70: custom accent hex ("#RRGGBB", empty = use the preset accent). */
    var customAccentHex: String
        get() = prefs.getString(KEY_CUSTOM_ACCENT_HEX, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_ACCENT_HEX, value).apply()

    /** Step 70: launcher icon variant ("default" | "black" | "white"). */
    var appIcon: String
        get() = prefs.getString(KEY_APP_ICON, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_APP_ICON, value).apply()

    /** Step 70: navigation dock style ("adaptive" | "compact"). */
    var dockStyle: String
        get() = prefs.getString(KEY_DOCK_STYLE, "adaptive") ?: "adaptive"
        set(value) = prefs.edit().putString(KEY_DOCK_STYLE, value).apply()

    /** Step 70: poster corner radius, dp (0..24). */
    var posterCornerRadiusDp: Int
        get() = prefInt(KEY_POSTER_CORNER_RADIUS_DP, 12).coerceIn(0, 24)
        set(value) = prefs.edit().putInt(KEY_POSTER_CORNER_RADIUS_DP, value.coerceIn(0, 24)).apply()

    /** Step 70: poster card width, dp (112/128/144). */
    var posterCardWidthDp: Int
        get() = prefInt(KEY_POSTER_CARD_WIDTH_DP, 128).coerceIn(112, 144)
        set(value) = prefs.edit().putInt(KEY_POSTER_CARD_WIDTH_DP, value.coerceIn(112, 144)).apply()

    /** Step 70: hide the title label under poster cards. */
    var posterHideLabels: Boolean
        get() = prefs.getBoolean(KEY_POSTER_HIDE_LABELS, false)
        set(value) = prefs.edit().putBoolean(KEY_POSTER_HIDE_LABELS, value).apply()

    /** W10: catalog landscape mode (Nuvio PosterCustomizationSettingsPage) —
     * catalog rows render 1.77 landscape cards from the title's backdrop art
     * (MetaPreview.background), falling back to the poster. */
    var posterCatalogLandscapeMode: Boolean
        get() = prefs.getBoolean(KEY_POSTER_LANDSCAPE_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_POSTER_LANDSCAPE_MODE, value).apply()

    /** W10: always show the title logo on landscape cards instead of a
     * custom landscape poster (Nuvio alwaysShowLandscapeClearlogo). */
    var posterLandscapeClearlogo: Boolean
        get() = prefs.getBoolean(KEY_POSTER_LANDSCAPE_CLEARLOGO, false)
        set(value) = prefs.edit().putBoolean(KEY_POSTER_LANDSCAPE_CLEARLOGO, value).apply()

    /** W10: custom poster URL pattern with {placeholders} (Nuvio
     * CustomPosterUrlRepository); blank = off. */
    var customPosterPattern: String
        get() = prefs.getString(KEY_CUSTOM_POSTER_PATTERN, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_POSTER_PATTERN, value).apply()

    /** W10: surfaces the custom poster pattern applies to (keys of
     * CustomPosterScreen); empty = all surfaces. */
    var customPosterScreens: Set<String>
        get() = prefs.getStringSet(KEY_CUSTOM_POSTER_SCREENS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CUSTOM_POSTER_SCREENS, value.toSet()).apply()

    /** W10: card depth effect master switch (Nuvio CardDepthStyleRepository). */
    var cardDepthEnabled: Boolean
        get() = prefs.getBoolean(KEY_CARD_DEPTH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_CARD_DEPTH_ENABLED, value).apply()

    /** W10: card depth edge-glow strength, 0..100 (Nuvio default 28 = Subtle). */
    var cardDepthEdgeStrength: Int
        get() = prefInt(KEY_CARD_DEPTH_EDGE, DefaultCardDepthEdgeStrength).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CARD_DEPTH_EDGE, value.coerceIn(0, 100)).apply()

    /** W10: card depth sheen strength, 0..100 (Nuvio default 10 = Soft). */
    var cardDepthSheenStrength: Int
        get() = prefInt(KEY_CARD_DEPTH_SHEEN, DefaultCardDepthSheenStrength).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CARD_DEPTH_SHEEN, value.coerceIn(0, 100)).apply()

    /** W10: card depth edge coverage, 0..100 (Nuvio default 0 = Top). */
    var cardDepthEdgeCoverage: Int
        get() = prefInt(KEY_CARD_DEPTH_COVERAGE, DefaultCardDepthEdgeCoverage).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_CARD_DEPTH_COVERAGE, value.coerceIn(0, 100)).apply()

    /** W10: surfaces the card depth effect applies to (keys of
     * CardDepthSurface); empty = all surfaces. */
    var cardDepthSurfaces: Set<String>
        get() = prefs.getStringSet(KEY_CARD_DEPTH_SURFACES, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_CARD_DEPTH_SURFACES, value.toSet()).apply()

    /** Step 68: show the parental-guide overlay when playback starts. */
    var showParentalGuide: Boolean
        get() = prefs.getBoolean(KEY_SHOW_PARENTAL_GUIDE, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_PARENTAL_GUIDE, value).apply()

    /** Step 67: forward the title's subtitles to the external player. */
    var forwardSubtitlesToExternalPlayer: Boolean
        get() = prefs.getBoolean(KEY_FORWARD_SUBTITLES_TO_EXTERNAL_PLAYER, true)
        set(value) = prefs.edit().putBoolean(KEY_FORWARD_SUBTITLES_TO_EXTERNAL_PLAYER, value).apply()

    /** Step 67: forward intro/recap/outro/credits segments to the external player. */
    var sendSkipSegmentsToExternalPlayer: Boolean
        get() = prefs.getBoolean(KEY_SEND_SKIP_SEGMENTS_TO_EXTERNAL_PLAYER, true)
        set(value) = prefs.edit().putBoolean(KEY_SEND_SKIP_SEGMENTS_TO_EXTERNAL_PLAYER, value).apply()

    /** Step 66: downmix surround to stereo/mono (default off). */
    var downmixEnabled: Boolean
        get() = prefs.getBoolean(KEY_DOWNMIX_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DOWNMIX_ENABLED, value).apply()

    /** Step 66: downmix target channel count (1 = mono, 2 = stereo). */
    var downmixTargetChannels: Int
        get() = prefInt(KEY_DOWNMIX_TARGET_CHANNELS, 2).coerceIn(1, 2)
        set(value) = prefs.edit().putInt(KEY_DOWNMIX_TARGET_CHANNELS, value.coerceIn(1, 2)).apply()

    /** Step 66: centre-channel level in the downmix, dB (Nuvio -10..+30). */
    var centerMixLevelDb: Int
        get() = prefInt(KEY_CENTER_MIX_LEVEL_DB, 0).coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
        set(value) = prefs.edit().putInt(KEY_CENTER_MIX_LEVEL_DB, value.coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)).apply()

    /** Step 66: keep original levels in the downmix (no normalisation). */
    var maintainOriginalAudioOnDownmix: Boolean
        get() = prefs.getBoolean(KEY_MAINTAIN_ORIGINAL_AUDIO_DOWNMIX, true)
        set(value) = prefs.edit().putBoolean(KEY_MAINTAIN_ORIGINAL_AUDIO_DOWNMIX, value).apply()

    /** Step 65: audio delay (lip-sync), ms, +/-60s. One value for the device. */
    var audioDelayMs: Int
        get() = prefInt(KEY_AUDIO_DELAY_MS, 0).coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
        set(value) = prefs.edit().putInt(KEY_AUDIO_DELAY_MS, value.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)).apply()

    /** Home scroll spot (Nuvio HomeScrollPosition): first visible row index, persisted so a cold start lands where the last session left off. */
    var homeScrollIndex: Int
        get() = prefInt(KEY_HOME_SCROLL_INDEX, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_HOME_SCROLL_INDEX, value.coerceAtLeast(0)).apply()

    /** Home scroll spot, pixel offset within the first visible row. */
    var homeScrollOffset: Int
        get() = prefInt(KEY_HOME_SCROLL_OFFSET, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_HOME_SCROLL_OFFSET, value.coerceAtLeast(0)).apply()

    /** Discover selection (Step 20, Nuvio DiscoverSelectionStorage): the picked type ("movie"/"series"), catalog key ("addonId|type|catalogId") and genre — null means "not picked yet". */
    var discoverType: String?
        get() = prefs.getString(KEY_DISCOVER_TYPE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_DISCOVER_TYPE).apply()
            else prefs.edit().putString(KEY_DISCOVER_TYPE, value).apply()
        }

    /** Discover selection: the picked catalog's key. */
    var discoverCatalogKey: String?
        get() = prefs.getString(KEY_DISCOVER_CATALOG_KEY, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_DISCOVER_CATALOG_KEY).apply()
            else prefs.edit().putString(KEY_DISCOVER_CATALOG_KEY, value).apply()
        }

    /** Discover selection: the picked genre, null = all genres. */
    var discoverGenre: String?
        get() = prefs.getString(KEY_DISCOVER_GENRE, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_DISCOVER_GENRE).apply()
            else prefs.edit().putString(KEY_DISCOVER_GENRE, value).apply()
        }

    /** Next-episode card threshold mode (Nuvio nextEpisodeThresholdMode): "percentage" or "minutes". */
    var nextEpisodeThresholdMode: String
        get() = prefs.getString(KEY_NEXT_EPISODE_THRESHOLD_MODE, "percentage") ?: "percentage"
        set(value) = prefs.edit().putString(KEY_NEXT_EPISODE_THRESHOLD_MODE, value).apply()

    /** Next-episode card threshold, percent played (Nuvio nextEpisodeThresholdPercent): 97–100, default 99. */
    var nextEpisodeThresholdPercent: Int
        get() = prefInt(KEY_NEXT_EPISODE_THRESHOLD_PERCENT, 99).coerceIn(97, 100)
        set(value) = prefs.edit().putInt(KEY_NEXT_EPISODE_THRESHOLD_PERCENT, value.coerceIn(97, 100)).apply()

    /** Next-episode card threshold, minutes before the end (Nuvio nextEpisodeThresholdMinutesBeforeEnd): 0–3.5, default 2. */
    var nextEpisodeThresholdMinutes: Float
        get() = prefFloat(KEY_NEXT_EPISODE_THRESHOLD_MINUTES, 2f).coerceIn(0f, 3.5f)
        set(value) = prefs.edit().putFloat(KEY_NEXT_EPISODE_THRESHOLD_MINUTES, value.coerceIn(0f, 3.5f)).apply()

    /** Reuse last link (Nuvio streamReuseLastLinkEnabled): default OFF — replaying a title reuses its last played link within the TTL instead of re-resolving sources. */
    var reuseLastLinkEnabled: Boolean
        get() = prefs.getBoolean(KEY_REUSE_LAST_LINK_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_REUSE_LAST_LINK_ENABLED, value).apply()

    /** Reuse last link cache window, hours (Nuvio streamReuseLastLinkCacheHours): default 24. */
    var reuseLastLinkCacheHours: Int
        get() = prefInt(KEY_REUSE_LAST_LINK_CACHE_HOURS, 24).coerceIn(1, 168)
        set(value) = prefs.edit().putInt(KEY_REUSE_LAST_LINK_CACHE_HOURS, value.coerceIn(1, 168)).apply()

    /** Hero trailer autoplay (Nuvio heroTrailerPlayback, MetaScreenSettingsRepository): default OFF — the first trailer autoplays muted in the detail hero once resolved. */
    var heroTrailerPlayback: Boolean
        get() = prefs.getBoolean(KEY_HERO_TRAILER_PLAYBACK, false)
        set(value) = prefs.edit().putBoolean(KEY_HERO_TRAILER_PLAYBACK, value).apply()

    /** Hero trailer start delay, seconds (Nuvio heroTrailerStartDelaySeconds): 0–10, default 0. */
    var heroTrailerStartDelaySeconds: Int
        get() = prefInt(KEY_HERO_TRAILER_START_DELAY_SECONDS, 0).coerceIn(0, 10)
        set(value) = prefs.edit().putInt(KEY_HERO_TRAILER_START_DELAY_SECONDS, value.coerceIn(0, 10)).apply()

    /** Hero trailer starts with sound (Nuvio heroTrailerStartUnmuted): default false — trailers start muted. */
    var heroTrailerStartUnmuted: Boolean
        get() = prefs.getBoolean(KEY_HERO_TRAILER_START_UNMUTED, false)
        set(value) = prefs.edit().putBoolean(KEY_HERO_TRAILER_START_UNMUTED, value).apply()

    /** Continue Watching section visible on Home (Nuvio CW isVisible). */
    var continueWatchingVisible: Boolean
        get() = prefs.getBoolean(KEY_CW_VISIBLE, true)
        set(value) = prefs.edit().putBoolean(KEY_CW_VISIBLE, value).apply()

    /** Download button on stream rows (Step 35 — Downloads settings). */
    var showDownloadButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DOWNLOAD_BUTTON, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_DOWNLOAD_BUTTON, value).apply()

    /** Continue Watching card style (Nuvio ContinueWatchingSectionStyle): "card" | "wide" | "poster". */
    var continueWatchingStyle: String
        get() = prefs.getString(KEY_CW_STYLE, "card") ?: "card"
        set(value) = prefs.edit().putString(KEY_CW_STYLE, value).apply()

    /** Continue Watching sort (Nuvio ContinueWatchingSortMode): "default" | "streaming" | "split_upcoming". */
    var continueWatchingSortMode: String
        get() = prefs.getString(KEY_CW_SORT_MODE, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_CW_SORT_MODE, value).apply()

    /** Continue Watching tap action (Nuvio ContinueWatchingClickAction): "stream" | "details". */
    var continueWatchingClickAction: String
        get() = prefs.getString(KEY_CW_CLICK_ACTION, "stream") ?: "stream"
        set(value) = prefs.edit().putString(KEY_CW_CLICK_ACTION, value).apply()

    /** Resume prompt on launch (Nuvio showResumePromptOnLaunch): offer to resume when the app reopens after being closed mid-playback. */
    var continueWatchingResumePrompt: Boolean
        get() = prefs.getBoolean(KEY_CW_RESUME_PROMPT, true)
        set(value) = prefs.edit().putBoolean(KEY_CW_RESUME_PROMPT, value).apply()

    /**
     * Prefer episode thumbnails in Next Up cards (Nuvio useEpisodeThumbnails):
     * the episode still when one exists, else the show poster. Default true.
     * Resume cards already store the episode thumbnail as their art when the
     * episode was played from Detail, so the toggle's visible effect is on
     * the Next Up rail (NextUpEpisode carries both thumbnail and poster).
     */
    var continueWatchingPreferEpisodeThumbnails: Boolean
        get() = prefs.getBoolean(KEY_CW_PREFER_EPISODE_THUMBNAILS, true)
        set(value) = prefs.edit().putBoolean(KEY_CW_PREFER_EPISODE_THUMBNAILS, value).apply()

    /**
     * Next Up from the furthest watched episode (Nuvio upNextFromFurthestEpisode):
     * ON (default) seeds Next Up from the highest season/episode watched;
     * OFF seeds from the most recently played episode (recency via the resume
     * list's updatedAt) — the rewatch behaviour. Plain boolean keys, so
     * exportAllPrefs() backs them up automatically (non-secret).
     */
    var continueWatchingNextUpFromFurthestEpisode: Boolean
        get() = prefs.getBoolean(KEY_CW_NEXT_UP_FROM_FURTHEST, true)
        set(value) = prefs.edit().putBoolean(KEY_CW_NEXT_UP_FROM_FURTHEST, value).apply()

    /** Show unaired next-up episodes (Nuvio showUnairedNextUp): OFF hides episodes that have not aired yet. Default true. */
    var continueWatchingShowUnairedNextUp: Boolean
        get() = prefs.getBoolean(KEY_CW_SHOW_UNAIRED_NEXT_UP, true)
        set(value) = prefs.edit().putBoolean(KEY_CW_SHOW_UNAIRED_NEXT_UP, value).apply()

    /**
     * Resume-prompt state (Nuvio ResumePromptRepository, scoped port):
     * armed with the playing video id when the player opens, cleared
     * when the player is left normally. A set flag at launch means the
     * last session ended inside the player — the only case that prompts.
     */
    var resumePromptWasInPlayer: Boolean
        get() = prefs.getBoolean(KEY_RESUME_PROMPT_WAS_IN_PLAYER, false)
        set(value) = prefs.edit().putBoolean(KEY_RESUME_PROMPT_WAS_IN_PLAYER, value).apply()

    /** Video id of the title playing when [resumePromptWasInPlayer] was armed ("" = none). */
    var resumePromptLastVideoId: String
        get() = prefs.getString(KEY_RESUME_PROMPT_LAST_VIDEO_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_RESUME_PROMPT_LAST_VIDEO_ID, value).apply()

    /** Skip-button offset (Nuvio skipIntroOffsetSeconds): shifts when the intro skip button appears, seconds, -60..60, default 0. Positive = later into the intro. */
    var skipIntroOffsetSeconds: Int
        get() = prefInt(KEY_SKIP_INTRO_OFFSET_SECONDS, 0).coerceIn(-60, 60)
        set(value) = prefs.edit().putInt(KEY_SKIP_INTRO_OFFSET_SECONDS, value.coerceIn(-60, 60)).apply()

    /** Auto-skip segment types (Nuvio autoSkipSegmentTypes): stored values "intro"/"recap"/"outro"/"movie-credits". Empty = offer the button only, never auto-skip. */
    var autoSkipSegmentTypes: Set<String>
        get() = prefs.getStringSet(KEY_AUTO_SKIP_SEGMENT_TYPES, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_AUTO_SKIP_SEGMENT_TYPES, value.toSet()).apply()

    /** Season view mode (Nuvio SeasonViewMode): "posters" or "text". */
    var seasonViewMode: String
        get() = prefs.getString(KEY_SEASON_VIEW_MODE, "posters") ?: "posters"
        set(value) = prefs.edit().putString(KEY_SEASON_VIEW_MODE, value).apply()

    // Step 47: Library Saved view (Nuvio LibraryDisplaySettings).
    var librarySort: String
        get() = prefs.getString(KEY_LIBRARY_SORT, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_LIBRARY_SORT, value).apply()
    var libraryLayout: String
        get() = prefs.getString(KEY_LIBRARY_LAYOUT, "rows") ?: "rows"
        set(value) = prefs.edit().putString(KEY_LIBRARY_LAYOUT, value).apply()
    var libraryWatchedFilter: String
        get() = prefs.getString(KEY_LIBRARY_WATCHED_FILTER, "all") ?: "all"
        set(value) = prefs.edit().putString(KEY_LIBRARY_WATCHED_FILTER, value).apply()
    var librarySection: String
        get() = prefs.getString(KEY_LIBRARY_SECTION, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIBRARY_SECTION, value).apply()

    /** Pinned stream sources (Nuvio PinnedStreamSourcesRepository): keys "addonId|sourceName". Pinned sources list first everywhere. */
    var pinnedStreamSources: Set<String>
        get() = prefs.getStringSet(KEY_PINNED_STREAM_SOURCES, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_PINNED_STREAM_SOURCES, value.toSet()).apply()

    /** Keep the screen awake while the player is open. */
    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_KEEP_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_KEEP_SCREEN_ON, value).apply()

    /** Show the "Skip intro / recap / outro" button when segments are known. */
    var showSkipButton: Boolean
        get() = prefs.getBoolean(KEY_SHOW_SKIP, true)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_SKIP, value).apply()

    /** Per-addon flag so pre-installed addons are only auto-installed once. */
    fun isPreinstallDone(manifestId: String): Boolean =
        prefs.getBoolean(KEY_PREINSTALL_PREFIX + manifestId, false)

    fun setPreinstallDone(manifestId: String) =
        prefs.edit().putBoolean(KEY_PREINSTALL_PREFIX + manifestId, true).apply()

    /**
     * Clears every preinstall_done_* marker. Called when the database had
     * to be recreated from corruption: the DB is empty but the flags
     * survived, which would silently skip reinstalling the bundled addons
     * (Cinemeta/OpenSubtitles/SubSense). Clearing them lets
     * ensurePreinstalledAddons() run again.
     */
    fun clearPreinstallFlags() {
        val stale = prefs.all.keys.filter { it.startsWith(KEY_PREINSTALL_PREFIX) }
        if (stale.isNotEmpty()) {
            prefs.edit().apply { stale.forEach(::remove) }.apply()
        }
    }

    /**
     * "System default" subtitle appearance: when true the player skips its
     * own styling (no setStyle()) so Android's Settings > Accessibility >
     * Captions preferences apply instead.
     */
    var useSystemCaptions: Boolean
        get() = prefs.getBoolean(KEY_SYSTEM_CAPTIONS, false)
        set(value) = prefs.edit().putBoolean(KEY_SYSTEM_CAPTIONS, value).apply()

    /** Simkl access token (null = not linked). */
    var simklToken: String?
        get() = getSecret(KEY_SIMKL_TOKEN)
        set(value) = putSecret(KEY_SIMKL_TOKEN, value)

    /** Simkl display name for the linked account. */
    var simklUsername: String?
        get() = prefs.getString(KEY_SIMKL_USERNAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SIMKL_USERNAME).apply()
            else prefs.edit().putString(KEY_SIMKL_USERNAME, value).apply()
        }

    /** TorBox API key (null = not configured). Debrid-only: resolves cached torrent infoHashes to direct HTTP links. */
    var torboxApiKey: String?
        get() = getSecret(KEY_TORBOX_API_KEY)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_TORBOX_API_KEY, value?.trim()?.takeIf { it.isNotBlank() })

    /** Real-Debrid API key (null = not configured). Debrid-only. */
    var realDebridApiKey: String?
        get() = getSecret(KEY_REALDEBRID_API_KEY)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_REALDEBRID_API_KEY, value?.trim()?.takeIf { it.isNotBlank() })

    /** Premiumize API key (null = not configured). Debrid-only (Step 43). */
    var premiumizeApiKey: String?
        get() = getSecret(KEY_PREMIUMIZE_API_KEY)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_PREMIUMIZE_API_KEY, value?.trim()?.takeIf { it.isNotBlank() })

    /**
     * Preferred debrid resolver (Step 43, Nuvio preferredResolverProviderId):
     * "" = automatic (TorBox → Premiumize → Real-Debrid), otherwise
     * "torbox" | "premiumize" | "realdebrid" — the preferred provider is
     * tried first, the rest follow in canonical order.
     */
    var preferredDebridResolver: String
        get() = prefs.getString(KEY_PREFERRED_DEBRID_RESOLVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PREFERRED_DEBRID_RESOLVER, value).apply()

    /**
     * Instant-preparation limit (Step 43, Nuvio instantPlaybackPreparationLimit):
     * max infoHash streams resolved per title, 0 = no cap (current behaviour —
     * resolve all). Nuvio's 0 means "preparation off"; Plavio's resolve is
     * the core path, not a background extra, so 0 here means uncapped.
     */
    var debridInstantPrepareLimit: Int
        get() = prefInt(KEY_DEBRID_INSTANT_PREPARE_LIMIT, 0).coerceIn(0, 5)
        set(value) = prefs.edit().putInt(KEY_DEBRID_INSTANT_PREPARE_LIMIT, value.coerceIn(0, 5)).apply()

    /** TMDB API key (null = not configured). Unlocks TMDB metadata/ratings lookups. */
    var tmdbApiKey: String?
        get() = getSecret(KEY_TMDB_API_KEY)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_TMDB_API_KEY, value?.trim()?.takeIf { it.isNotBlank() })

    /** OMDb API key (null = not configured). Unlocks OMDb ratings lookups. */
    var omdbApiKey: String?
        get() = getSecret(KEY_OMDB_API_KEY)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_OMDB_API_KEY, value?.trim()?.takeIf { it.isNotBlank() })


    /** Trakt access token (null = not linked). */
    var traktAccessToken: String?
        get() = getSecret(KEY_TRAKT_TOKEN)
        set(value) = putSecret(KEY_TRAKT_TOKEN, value)

    /** Trakt refresh token. */
    var traktRefreshToken: String?
        get() = getSecret(KEY_TRAKT_REFRESH)
        set(value) = putSecret(KEY_TRAKT_REFRESH, value)

    /** Epoch millis when the Trakt access token should be refreshed. */
    var traktTokenExpiry: Long
        get() = prefLong(KEY_TRAKT_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_TRAKT_EXPIRY, value).apply()

    /** Trakt display name for the linked account. */
    var traktUsername: String?
        get() = prefs.getString(KEY_TRAKT_USERNAME, null)
        set(value) {
            if (value == null) prefs.edit().remove(KEY_TRAKT_USERNAME).apply()
            else prefs.edit().putString(KEY_TRAKT_USERNAME, value).apply()
        }


    // ---- Media servers (Step 55) ----

    /**
     * Stored server connections (JSON array, encrypted secret — it holds
     * access tokens). Read via [getMediaServers], written via
     * [saveMediaServers].
     */
    fun getMediaServers(): List<com.pulsestream.app.data.servers.MediaServer> =
        com.pulsestream.app.data.servers.decodeServers(getSecret(KEY_MEDIA_SERVERS))

    fun saveMediaServers(servers: List<com.pulsestream.app.data.servers.MediaServer>) {
        if (servers.isEmpty()) putSecret(KEY_MEDIA_SERVERS, null)
        else putSecret(KEY_MEDIA_SERVERS, com.pulsestream.app.data.servers.encodeServers(servers))
    }

    /** Step 57: Detail section order/visibility/tab layout, one JSON blob. */
    var detailSectionsJson: String
        get() = prefs.getString(KEY_DETAIL_SECTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_DETAIL_SECTIONS, value).apply()

    /** Step 58: favorite people blob (JSON, see FavoritePeopleRepository). */
    var favoritePeopleJson: String
        get() = prefs.getString(KEY_FAVORITE_PEOPLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_FAVORITE_PEOPLE, value).apply()

    // Step 61: custom stream badge rule imports (JSON, see StreamBadges.kt).
    var streamBadgeRulesJson: String
        get() = prefs.getString(KEY_STREAM_BADGE_RULES, "") ?: ""
        set(value) = prefs.edit().putString(KEY_STREAM_BADGE_RULES, value).apply()

    // Step 61: show matched image badges in the player header.
    var showStreamBadgesInPlayer: Boolean
        get() = prefs.getBoolean(KEY_SHOW_STREAM_BADGES_IN_PLAYER, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_STREAM_BADGES_IN_PLAYER, value).apply()

    // Step 61: which imported badge source the player uses ("" = the active one).
    var playerBadgeSource: String
        get() = prefs.getString(KEY_PLAYER_BADGE_SOURCE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER_BADGE_SOURCE, value).apply()

    // Step 62: which service's in-progress playback positions resume here.
    var watchProgressSource: String
        get() = prefs.getString(KEY_WATCH_PROGRESS_SOURCE, "local") ?: "local"
        set(value) = prefs.edit().putString(KEY_WATCH_PROGRESS_SOURCE, value).apply()

    // Step 63: player toolbar layouts ("id:v,id:h", see PlayerToolbarLayout.kt).
    var playerBottomLayout: String
        get() = prefs.getString(KEY_PLAYER_BOTTOM_LAYOUT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER_BOTTOM_LAYOUT, value).apply()

    var playerTopLayout: String
        get() = prefs.getString(KEY_PLAYER_TOP_LAYOUT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PLAYER_TOP_LAYOUT, value).apply()

    // Step 64: keep audio playing in the background (VOD only) with
    // notification / lock-screen controls. Default off.
    var backgroundAudioEnabled: Boolean
        get() = prefs.getBoolean(KEY_BACKGROUND_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO, value).apply()

    // Step 62: pulled remote resume points, keyed by playback identity.
    var remoteResumePointsJson: String
        get() = prefs.getString(KEY_REMOTE_RESUME_POINTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_REMOTE_RESUME_POINTS, value).apply()

    // Step 62: last successful watch-progress pull (epoch ms, 0 = never).
    var lastWatchProgressPullMs: Long
        get() = prefs.getLong(KEY_LAST_WATCH_PROGRESS_PULL, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_WATCH_PROGRESS_PULL, value).apply()

    /** Step 60: binge-group cache blob (JSON, see BingeGroupCache). */
    var bingeGroupCacheJson: String
        get() = prefs.getString(KEY_BINGE_GROUP_CACHE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BINGE_GROUP_CACHE, value).apply()

    /** Stable per-install device id for server auth headers. */
    var mediaServerDeviceId: String
        get() = prefs.getString(KEY_MEDIA_SERVER_DEVICE_ID, null)
            ?: java.util.UUID.randomUUID().toString().also {
                prefs.edit().putString(KEY_MEDIA_SERVER_DEVICE_ID, it).apply()
            }
        set(value) = prefs.edit().putString(KEY_MEDIA_SERVER_DEVICE_ID, value).apply()

    // ---- Unified tracking sources (Step 52, Nuvio tracking/TrackingSources) ----

    /**
     * Which services feed the Library (watchlists + lists): "all" (every
     * linked service, the pre-Step-52 behaviour), "local", "trakt",
     * "simkl".
     */
    var trackingLibrarySource: String
        get() = prefs.getString(KEY_TRACKING_LIBRARY_SOURCE, "all") ?: "all"
        set(value) = prefs.edit().putString(KEY_TRACKING_LIBRARY_SOURCE, value).apply()

    /** Which service feeds Up Next progress: same values as [trackingLibrarySource]. */
    var trackingProgressSource: String
        get() = prefs.getString(KEY_TRACKING_PROGRESS_SOURCE, "all") ?: "all"
        set(value) = prefs.edit().putString(KEY_TRACKING_PROGRESS_SOURCE, value).apply()

    // ---- Trakt/Simkl library sync ----

    /**
     * Keys ("type|imdbId") known to be in sync after the last successful
     * library sync — the merge base that lets the next sync tell a local
     * removal from a remote addition.
     */
    var librarySyncedKeys: Set<String>
        get() = prefs.getStringSet(KEY_LIBRARY_SYNCED, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LIBRARY_SYNCED, value).apply()

    /** Epoch millis of the last library sync attempt (0 = never). */
    var librarySyncLastAt: Long
        get() = prefLong(KEY_LIBRARY_SYNC_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_LIBRARY_SYNC_AT, value).apply()

    /** Short human result of the last library sync ("" = never ran). */
    var librarySyncLastResult: String
        get() = prefs.getString(KEY_LIBRARY_SYNC_RESULT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIBRARY_SYNC_RESULT, value).apply()

    /**
     * User-chosen display name for the Account area (owner's order
     * 2026-10-22): when set, the app shows this name instead of
     * presenting the Gmail address as the account name.
     */
    var profileUserName: String
        get() = prefs.getString(KEY_PROFILE_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROFILE_USER_NAME, value.trim()).apply()

    /**
     * Account picture choice (owner's order 2026-10-24): "" = none,
     * "photo" = the user's own photo (see [profilePhotoFile]),
     * "preset:<id>" = a built-in avatar. A plain preference, so it
     * rides the Drive backup with the other settings; the photo bytes
     * ride the backup as their own field (StreamRepository).
     */
    var profileAvatar: String
        get() = prefs.getString(KEY_PROFILE_AVATAR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PROFILE_AVATAR, value).apply()

    /**
     * The user's own cropped profile photo (JPEG, app-private storage).
     * Step 56: per-profile file for the active profile; the legacy
     * single file is used only before the profiles migration runs.
     */
    fun profilePhotoFile(): File {
        val activeId = ProfileStore.activeProfileId(appContext)
        return if (activeId != null) ProfileStore.photoFile(appContext, activeId)
        else File(appContext.filesDir, "profile_photo.jpg")
    }

    fun readProfilePhotoBytes(): ByteArray? = runCatching {
        profilePhotoFile().takeIf { it.exists() }?.readBytes()
    }.getOrNull()

    fun writeProfilePhoto(bytes: ByteArray) {
        profilePhotoFile().writeBytes(bytes)
    }

    fun deleteProfilePhoto() {
        runCatching { profilePhotoFile().delete() }
    }

    /** Accent color theme name (see AccentTheme). */
    var accentName: String
        get() = prefs.getString(KEY_ACCENT, AccentTheme.WHITE.name) ?: AccentTheme.WHITE.name
        set(value) = prefs.edit().putString(KEY_ACCENT, value).apply()

    /** Pure-black backgrounds (AMOLED-friendly). */
    var amoledBlack: Boolean
        get() = prefs.getBoolean(KEY_AMOLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AMOLED, value).apply()

    /** Theme mode: "dark" (default) or "light". */
    var themeMode: String
        get() = prefs.getString(KEY_THEME_MODE, "dark") ?: "dark"
        set(value) = prefs.edit().putString(KEY_THEME_MODE, value).apply()

    /**
     * The six-theme appearance choice (AppTheme name). When never set,
     * it migrates from the old settings so existing users keep their
     * current look: Normal -> Warm Ivory, AMOLED -> True Black, else
     * Onyx (the default). Writing it also keeps the legacy
     * themeMode/amoledBlack fields in sync for older readers.
     */
    var appTheme: String
        get() = prefs.getString(KEY_APP_THEME, null) ?: when {
            themeMode == "light" -> "WARM_IVORY"
            amoledBlack -> "TRUE_BLACK"
            else -> "ONYX"
        }
        set(value) {
            prefs.edit().putString(KEY_APP_THEME, value).apply()
            themeMode = if (value == "WARM_IVORY" || value == "PEARL") "light" else "dark"
            amoledBlack = value == "TRUE_BLACK"
        }

    /**
     * Preferred subtitle languages (ISO 639-1 codes, up to 2).
     * Empty = no preference (first available subtitle is used).
     */
    var subtitleLanguages: List<String>
        get() = prefs.getString(KEY_SUBTITLE_LANGS, "")
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.take(2)
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_SUBTITLE_LANGS, value.take(2).joinToString(","))
            .apply()

    /** First-run onboarding completed (v0.8.21+). False = show the 3-step guide. */
    var onboardingDone: Boolean
        get() {
            if (onboardingPrefs.contains(KEY_ONBOARDING_DONE)) {
                return onboardingPrefs.getBoolean(KEY_ONBOARDING_DONE, false)
            }
            // One-time migration from the old shared file (pre-backup-rules builds).
            val legacy = prefs.getBoolean(KEY_ONBOARDING_DONE, false)
            onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, legacy).apply()
            return legacy
        }
        set(value) = onboardingPrefs.edit().putBoolean(KEY_ONBOARDING_DONE, value).apply()

    /**
     * Preferred audio language (ISO 639-1 code, single). Null = no preference
     * (ExoPlayer's default track selection). When set and a title's audio
     * tracks include it, the player auto-selects it at playback start.
     */
    var preferredAudioLanguage: String?
        get() = prefs.getString(KEY_AUDIO_LANG, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_AUDIO_LANG).apply()
            else prefs.edit().putString(KEY_AUDIO_LANG, value).apply()
        }

    /**
     * Secondary preferred audio language (Step 38, Nuvio
     * resolvePreferredAudioLanguageTargets): tried when no track matches
     * the primary language. Null = no secondary preference.
     */
    var secondaryPreferredAudioLanguage: String?
        get() = prefs.getString(KEY_SECONDARY_AUDIO_LANG, null)?.takeIf { it.isNotBlank() }
        set(value) {
            if (value == null) prefs.edit().remove(KEY_SECONDARY_AUDIO_LANG).apply()
            else prefs.edit().putString(KEY_SECONDARY_AUDIO_LANG, value).apply()
        }

    /** Custom subtitle font id (Step 38): "default" | "sans" | "serif" | "mono" | "custom-<uuid>". */
    var subtitleFontId: String
        get() = prefs.getString(KEY_SUBTITLE_FONT_ID, "default") ?: "default"
        set(value) = prefs.edit().putString(KEY_SUBTITLE_FONT_ID, value).apply()

    /** Strip SDH (deaf/hard-of-hearing) cues — [hearing], (sound), "NAME:" labels (Step 38). */
    var subtitleSdhFilter: Boolean
        get() = prefs.getBoolean(KEY_SUBTITLE_SDH_FILTER, false)
        set(value) = prefs.edit().putBoolean(KEY_SUBTITLE_SDH_FILTER, value).apply()

    /** Subtitle text color (ARGB int). Default: white. */
    var subtitleTextColor: Int
        get() = prefInt(KEY_SUB_COLOR, 0xFFFFFFFF.toInt())
        set(value) = prefs.edit().putInt(KEY_SUB_COLOR, value).apply()

    /** Subtitle text size scale (1.0 = default). Range 0.6 – 2.0.
     *  Mirrored with [subtitleFontSizeSp] (18sp == 1.0) so the Settings
     *  dialog (scale-based) and the player Style rail (sp-based) always
     *  agree on one persisted size. */
    var subtitleTextSizeScale: Float
        get() = prefFloat(KEY_SUB_SIZE, 1.0f)
        set(value) {
            val scale = value.coerceIn(0.6f, 2.0f)
            prefs.edit()
                .putFloat(KEY_SUB_SIZE, scale)
                .putInt(KEY_SUB_FONT_SP, (scale * 18f).roundToInt().coerceIn(12, 40))
                .apply()
        }

    /**
     * Subtitle font size in sp (player Style rail, v0.14.5): 12–40,
     * default 18. Kept in sync with [subtitleTextSizeScale] — writing
     * either one updates the other (18sp == scale 1.0).
     */
    var subtitleFontSizeSp: Int
        get() = if (prefs.contains(KEY_SUB_FONT_SP)) {
            prefInt(KEY_SUB_FONT_SP, 18).coerceIn(12, 40)
        } else {
            (prefFloat(KEY_SUB_SIZE, 1.0f) * 18f).roundToInt().coerceIn(12, 40)
        }
        set(value) {
            val sp = value.coerceIn(12, 40)
            prefs.edit()
                .putInt(KEY_SUB_FONT_SP, sp)
                .putFloat(KEY_SUB_SIZE, sp / 18f)
                .apply()
        }

    /** Subtitle background color (ARGB int). Default: semi-transparent black. */
    var subtitleBackgroundColor: Int
        get() = prefInt(KEY_SUB_BG, 0xCC000000.toInt())
        set(value) = prefs.edit().putInt(KEY_SUB_BG, value).apply()

    /**
     * Subtitle edge style: 0 = none, 1 = outline, 2 = drop shadow,
     * 3 = raised, 4 = depressed. Default: 2 (drop shadow).
     */
    var subtitleEdgeType: Int
        get() = prefInt(KEY_SUB_EDGE, 2)
        set(value) = prefs.edit().putInt(KEY_SUB_EDGE, value.coerceIn(0, 4)).apply()

    /** Bold subtitle text (player Style panel). Default: off. */
    var subtitleBold: Boolean
        get() = prefs.getBoolean(KEY_SUB_BOLD, false)
        set(value) = prefs.edit().putBoolean(KEY_SUB_BOLD, value).apply()

    /** Subtitle outline/edge color (ARGB int). Default: black. */
    var subtitleEdgeColor: Int
        get() = prefInt(KEY_SUB_EDGE_COLOR, 0xFF000000.toInt())
        set(value) = prefs.edit().putInt(KEY_SUB_EDGE_COLOR, value).apply()

    /**
     * Subtitle bottom offset, 0–200 in player Style-rail steps of 5;
     * the player maps it to a SubtitleView bottom-padding fraction so
     * captions can be lifted off the bottom edge. Default: 20.
     */
    var subtitleBottomOffset: Int
        get() = prefInt(KEY_SUB_BOTTOM, 20)
        set(value) = prefs.edit().putInt(KEY_SUB_BOTTOM, value.coerceIn(0, 200)).apply()

    /**
     * Per-video subtitle delay (ms, ±60s) set from the player's Style
     * rail — persisted per video id so a title remembers its sync.
     * Stored as newline-joined "urlEncoded(videoId)=ms" entries,
     * most recent last, capped at 100 titles.
     */
    fun subtitleDelayMsFor(videoId: String): Long {
        val raw = prefs.getString(KEY_SUB_DELAY_MAP, null) ?: return 0L
        val key = java.net.URLEncoder.encode(videoId, "UTF-8")
        for (entry in raw.split("\n")) {
            val eq = entry.lastIndexOf('=')
            if (eq > 0 && entry.substring(0, eq) == key) {
                return entry.substring(eq + 1).toLongOrNull()?.coerceIn(-60_000L, 60_000L) ?: 0L
            }
        }
        return 0L
    }

    fun setSubtitleDelayMsFor(videoId: String, delayMs: Long) {
        val key = java.net.URLEncoder.encode(videoId, "UTF-8")
        val raw = prefs.getString(KEY_SUB_DELAY_MAP, null).orEmpty()
        val entries = raw.split("\n")
            .filter { it.isNotBlank() && !it.startsWith("$key=") }
            .toMutableList()
        entries.add("$key=${delayMs.coerceIn(-60_000L, 60_000L)}")
        prefs.edit()
            .putString(KEY_SUB_DELAY_MAP, entries.takeLast(100).joinToString("\n"))
            .apply()
    }

    /**
     * Global subtitle timing offset in milliseconds (v0.9.0+). Positive =
     * subtitles appear later, negative = earlier. Range ±10 seconds.
     * Applied by rewriting subtitle files with shifted timestamps before
     * they are attached to the player (ExoPlayer has no delay API for
     * side-loaded subtitles).
     */
    var subtitleOffsetMs: Long
        get() = prefLong(KEY_SUBTITLE_OFFSET_MS, 0L).coerceIn(-10_000L, 10_000L)
        set(value) = prefs.edit().putLong(KEY_SUBTITLE_OFFSET_MS, value.coerceIn(-10_000L, 10_000L)).apply()

    /** Pinned Live TV channels, keys "addonId|metaId" ("m3u|url" for M3U). */
    var liveFavorites: Set<String>
        get() = prefs.getStringSet(KEY_LIVE_FAVS, emptySet())?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LIVE_FAVS, value).apply()

    /** Recently watched Live TV channel keys, most-recent first (max 20). */
    var liveRecent: List<String>
        get() = prefs.getString(KEY_LIVE_RECENT, "")
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(20)
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_LIVE_RECENT, value.take(20).joinToString("\n")).apply()

    /** User-added M3U/IPTV playlist URLs. */
    var m3uPlaylists: List<String>
        get() = prefs.getString(KEY_M3U_LISTS, "")
            ?.split("\n")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        set(value) = prefs.edit()
            .putString(KEY_M3U_LISTS, value.joinToString("\n")).apply()

    // ------------------------------------------------------------------
    // Live TV providers (Nuvio LiveTvStorage): Xtream Codes + Stalker
    // Portal. Passwords live in the encrypted secret store; the rest are
    // plain prefs. The Live tab reads these on every load.
    // ------------------------------------------------------------------

    /** Xtream Codes panel URL (e.g. http://panel.example:8080). */
    var liveTvXtreamServerUrl: String
        get() = prefs.getString(KEY_LIVE_TV_XTREAM_SERVER, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_TV_XTREAM_SERVER, value.trim()).apply()

    // ---- Simkl sync engine (Step 34) ----
    // Raw JSON snapshot + pending mutation queue; the engine owns the shape.
    // Plain prefs, not secrets — they hold IMDb ids and statuses only.
    var simklSyncSnapshot: String
        get() = prefs.getString(KEY_SIMKL_SYNC_SNAPSHOT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SIMKL_SYNC_SNAPSHOT, value).apply()

    var simklSyncMutations: String
        get() = prefs.getString(KEY_SIMKL_SYNC_MUTATIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SIMKL_SYNC_MUTATIONS, value).apply()

    /** Xtream Codes username. */
    var liveTvXtreamUsername: String
        get() = prefs.getString(KEY_LIVE_TV_XTREAM_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_TV_XTREAM_USERNAME, value.trim()).apply()

    /** Xtream Codes password (encrypted secret, null = not configured). */
    var liveTvXtreamPassword: String?
        get() = getSecret(KEY_LIVE_TV_XTREAM_PASSWORD)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_LIVE_TV_XTREAM_PASSWORD, value?.trim()?.takeIf { it.isNotBlank() })

    /** Xtream provider enabled (default true — a configured provider loads). */
    var liveTvXtreamEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_TV_XTREAM_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_TV_XTREAM_ENABLED, value).apply()

    /** Stalker Portal URL (portal.php, or the panel base URL). */
    var liveTvStalkerPortalUrl: String
        get() = prefs.getString(KEY_LIVE_TV_STALKER_PORTAL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_TV_STALKER_PORTAL, value.trim()).apply()

    /** Stalker Portal MAC address (e.g. 00:1A:79:XX:XX:XX). */
    var liveTvStalkerMac: String
        get() = prefs.getString(KEY_LIVE_TV_STALKER_MAC, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_TV_STALKER_MAC, value.trim().uppercase()).apply()

    /** Stalker Portal username (optional — many portals need only the MAC). */
    var liveTvStalkerUsername: String
        get() = prefs.getString(KEY_LIVE_TV_STALKER_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LIVE_TV_STALKER_USERNAME, value.trim()).apply()

    /** Stalker Portal password (encrypted secret, null = not configured). */
    var liveTvStalkerPassword: String?
        get() = getSecret(KEY_LIVE_TV_STALKER_PASSWORD)?.takeIf { it.isNotBlank() }
        set(value) = putSecret(KEY_LIVE_TV_STALKER_PASSWORD, value?.trim()?.takeIf { it.isNotBlank() })

    /** Stalker provider enabled (default true — a configured portal loads). */
    var liveTvStalkerEnabled: Boolean
        get() = prefs.getBoolean(KEY_LIVE_TV_STALKER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_TV_STALKER_ENABLED, value).apply()

    /** Manually added XMLTV guide sources, as a JSON array of {id, name, url, isEnabled}. */
    var liveTvEpgSources: List<LiveTvEpgSource>
        get() {
            val raw = prefs.getString(KEY_LIVE_TV_EPG_SOURCES, null) ?: return emptyList()
            return runCatching {
                val array = org.json.JSONArray(raw)
                List(array.length()) { i ->
                    val obj = array.getJSONObject(i)
                    LiveTvEpgSource(
                        id = obj.optString("id").takeIf { it.isNotBlank() } ?: "epg-$i",
                        name = obj.optString("name").takeIf { it.isNotBlank() } ?: obj.optString("url"),
                        url = obj.optString("url"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                    )
                }.filter { it.url.isNotBlank() }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = org.json.JSONArray()
            value.forEach { source ->
                array.put(org.json.JSONObject().apply {
                    put("id", source.id)
                    put("name", source.name)
                    put("url", source.url)
                    put("isEnabled", source.isEnabled)
                })
            }
            prefs.edit().putString(KEY_LIVE_TV_EPG_SOURCES, array.toString()).apply()
        }

    /** Auto-detect EPG feeds from M3U playlist headers + the Xtream xmltv.php feed (default true). */
    var liveTvEpgAutoDetect: Boolean
        get() = prefs.getBoolean(KEY_LIVE_TV_EPG_AUTO_DETECT, true)
        set(value) = prefs.edit().putBoolean(KEY_LIVE_TV_EPG_AUTO_DETECT, value).apply()

    /** Xtream settings assembled from the prefs above. */
    fun liveTvXtreamSettings(): LiveTvXtreamSettings = LiveTvXtreamSettings(
        serverUrl = liveTvXtreamServerUrl,
        username = liveTvXtreamUsername,
        password = liveTvXtreamPassword ?: "",
        isEnabled = liveTvXtreamEnabled,
    )

    /** Stalker settings assembled from the prefs above. */
    fun liveTvStalkerSettings(): LiveTvStalkerSettings = LiveTvStalkerSettings(
        portalUrl = liveTvStalkerPortalUrl,
        macAddress = liveTvStalkerMac,
        username = liveTvStalkerUsername,
        password = liveTvStalkerPassword ?: "",
        isEnabled = liveTvStalkerEnabled,
    )

    /**
     * Epoch millis of the last successful Home catalog refresh.
     * Per-section freshness: the Live tab writes its own rows into the same
     * catalog_cache table, so Home freshness must be tracked separately —
     * otherwise a Live visit would suppress Home's background refresh.
     */
    var homeCatalogsRefreshedAt: Long
        get() = prefLong(KEY_HOME_REFRESHED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_HOME_REFRESHED_AT, value).apply()

    /**
     * CloudStream (CS3) home provider selection — Phase 2 discovery.
     * "" = unset (never resolved), "all" = every runnable provider's
     * sections, "off" = no CS3 sections on Home, otherwise a plugin id
     * ("<repoUrl>#<internalName>"). The first-run default (unset -> first
     * runnable provider, Infuse mirror) runs once, gated by
     * [cloudStreamProviderSelectionRestored] — a persisted flag, NOT an
     * in-memory boolean. The resume prompt's plain remember flag died on
     * every navigation and re-prompted; this gate must survive restarts.
     */
    var cloudStreamProviderId: String
        get() = prefs.getString(KEY_CS3_PROVIDER_ID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CS3_PROVIDER_ID, value).apply()

    /** First-run defaulting for [cloudStreamProviderId] has run. Persisted. */
    var cloudStreamProviderSelectionRestored: Boolean
        get() = prefs.getBoolean(KEY_CS3_PROVIDER_SELECTION_RESTORED, false)
        set(value) = prefs.edit().putBoolean(KEY_CS3_PROVIDER_SELECTION_RESTORED, value).apply()

    /**
     * Home catalog row preferences blob (see HomeRowPrefs.kt): one JSON
     * string holding every row's hide/order/rename/pin/hero-source choice,
     * plus the hero + catalog-type + unreleased switches. Nuvio's model.
     */
    var homeRowPrefsJson: String?
        get() = prefs.getString(KEY_HOME_ROW_PREFS, null)
        set(value) = prefs.edit().putString(KEY_HOME_ROW_PREFS, value).apply()

    // ------------------------------------------------------------------
    // Streams section (v0.8.31+): per-source caps, quality order, autoplay.
    // ------------------------------------------------------------------

    /** Max streams shown per source addon in the stream picker (1–10). */
    var streamsPerSource: Int
        get() = prefInt(KEY_STREAMS_PER_SOURCE, 5).coerceIn(1, 10)
        set(value) = prefs.edit().putInt(KEY_STREAMS_PER_SOURCE, value.coerceIn(1, 10)).apply()

    /**
     * Quality sort priority for the stream picker, top = highest priority.
     * Canonical buckets: "2160p", "1080p", "720p", "480p".
     */
    var qualityOrder: List<String>
        get() {
            val saved = prefs.getString(KEY_QUALITY_ORDER, null)
                ?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
                ?: emptyList()
            val valid = saved.filter { it in QUALITY_ORDER_OPTIONS }
            return if (valid.isEmpty()) QUALITY_ORDER_OPTIONS else valid
        }
        set(value) = prefs.edit()
            .putString(KEY_QUALITY_ORDER, value.filter { it in QUALITY_ORDER_OPTIONS }.joinToString(","))
            .apply()

    /**
     * Stream autoplay: when ON, pressing Play auto-picks the first stream
     * matching the quality priority + size rule instead of the probed best.
     */
    var streamAutoplay: Boolean
        get() = prefs.getBoolean(KEY_STREAM_AUTOPLAY, false)
        set(value) = prefs.edit().putBoolean(KEY_STREAM_AUTOPLAY, value).apply()

    /** Preferred autoplay quality ("Auto" = follow qualityOrder, else a bucket). */
    var autoplayQuality: String
        get() = (prefs.getString(KEY_AUTOPLAY_QUALITY, "Auto") ?: "Auto")
            .takeIf { it == "Auto" || it in QUALITY_ORDER_OPTIONS } ?: "Auto"
        set(value) = prefs.edit()
            .putString(KEY_AUTOPLAY_QUALITY, if (value == "Auto" || value in QUALITY_ORDER_OPTIONS) value else "Auto")
            .apply()

    /** Autoplay max file size in GB (0 = no limit). */
    var autoplayMaxSizeGb: Int
        get() = prefInt(KEY_AUTOPLAY_MAX_SIZE, 0).coerceAtLeast(0)
        set(value) = prefs.edit().putInt(KEY_AUTOPLAY_MAX_SIZE, value.coerceAtLeast(0)).apply()

    private fun migrateLegacySecrets() {
        for (key in SECRET_KEYS) {
            val legacy = prefs.getString(key, null) ?: continue
            if (secretPrefs.getString(key, null) == null) runCatching { putSecret(key, legacy) }
            // Remove plaintext only after the encrypted copy was persisted.
            if (secretPrefs.getString(key, null) != null) prefs.edit().remove(key).apply()
        }
    }

    private fun getSecret(key: String): String? {
        val encoded = secretPrefs.getString(key, null)
        if (encoded != null) return runCatching { decryptSecret(encoded) }.getOrNull()
        val legacy = prefs.getString(key, null) ?: return null
        runCatching { putSecret(key, legacy) }
        if (secretPrefs.getString(key, null) != null) prefs.edit().remove(key).apply()
        return legacy
    }

    private fun putSecret(key: String, value: String?) {
        if (value == null) {
            secretPrefs.edit().remove(key).apply()
            prefs.edit().remove(key).apply()
        } else {
            check(secretPrefs.edit().putString(key, encryptSecret(value)).commit()) { "Could not persist encrypted secret." }
            prefs.edit().remove(key).commit()
        }
    }

    private fun encryptSecret(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    private fun decryptSecret(value: String): String {
        val bytes = Base64.decode(value, Base64.NO_WRAP)
        require(bytes.size > 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(SECRET_KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(SECRET_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    // ------------------------------------------------------------------
    // Backup & restore (v0.9.0+): raw prefs access for JSON export/import.
    // The onboarding prefs file is deliberately excluded — restoring
    // onboarding_done=true on a new device would skip the first-run guide.
    // ------------------------------------------------------------------

    /** Snapshot of every stored preference (excludes the onboarding file). */
    fun exportAllPrefs(): Map<String, *> = prefs.all.filterKeys { it !in SECRET_KEYS }.toMap()

    /**
     * Exports API keys (decrypted) for cloud backup (user's order: simple sync like Nuvio,
     * no separate passphrase). Only includes non-blank keys.
     */
    fun exportApiKeysForBackup(): Map<String, String> {
        val keys = mutableMapOf<String, String>()
        torboxApiKey?.takeIf { it.isNotBlank() }?.let { keys["torbox_api_key"] = it }
        realDebridApiKey?.takeIf { it.isNotBlank() }?.let { keys["realdebrid_api_key"] = it }
        premiumizeApiKey?.takeIf { it.isNotBlank() }?.let { keys["premiumize_api_key"] = it }
        tmdbApiKey?.takeIf { it.isNotBlank() }?.let { keys["tmdb_api_key"] = it }
        omdbApiKey?.takeIf { it.isNotBlank() }?.let { keys["omdb_api_key"] = it }
        traktAccessToken?.takeIf { it.isNotBlank() }?.let { keys["trakt_access_token"] = it }
        traktRefreshToken?.takeIf { it.isNotBlank() }?.let { keys["trakt_refresh_token"] = it }
        simklToken?.takeIf { it.isNotBlank() }?.let { keys["simkl_token"] = it }
        return keys
    }

    /**
     * Restores API keys from cloud backup (user's order: simple sync).
     * Only fills in blank local keys to avoid clobbering newer local values.
     */
    fun importApiKeysFromBackup(keys: Map<String, String>) {
        keys["torbox_api_key"]?.takeIf { it.isNotBlank() && torboxApiKey.isNullOrBlank() }?.let { torboxApiKey = it }
        keys["realdebrid_api_key"]?.takeIf { it.isNotBlank() && realDebridApiKey.isNullOrBlank() }?.let { realDebridApiKey = it }
        keys["premiumize_api_key"]?.takeIf { it.isNotBlank() && premiumizeApiKey.isNullOrBlank() }?.let { premiumizeApiKey = it }
        keys["tmdb_api_key"]?.takeIf { it.isNotBlank() && tmdbApiKey.isNullOrBlank() }?.let { tmdbApiKey = it }
        keys["omdb_api_key"]?.takeIf { it.isNotBlank() && omdbApiKey.isNullOrBlank() }?.let { omdbApiKey = it }
        keys["trakt_access_token"]?.takeIf { it.isNotBlank() && traktAccessToken.isNullOrBlank() }?.let { traktAccessToken = it }
        keys["trakt_refresh_token"]?.takeIf { it.isNotBlank() && traktRefreshToken.isNullOrBlank() }?.let { traktRefreshToken = it }
        keys["simkl_token"]?.takeIf { it.isNotBlank() && simklToken.isNullOrBlank() }?.let { simklToken = it }
    }

    /**
     * Restores preferences from an export map. Unknown keys and wrong-typed
     * values are skipped; StringSet values are restored as string sets.
     */
    fun importAllPrefs(values: Map<String, Any?>) {
        val e = prefs.edit()
        // Backup restore replaces portable settings instead of merging; keep
        // Keystore secrets and local first-run/privacy consent state untouched.
        prefs.all.keys.filterNot { key -> key in SECRET_KEYS || key == KEY_ONBOARDING_DONE ||
            key.contains("privacy", ignoreCase = true) || key.contains("consent", ignoreCase = true) }
            .forEach(e::remove)
        for ((k, v) in values) {
            if (k in SECRET_KEYS || k == KEY_ONBOARDING_DONE || k.contains("privacy", true) || k.contains("consent", true)) continue
            when (v) {
                is Boolean -> e.putBoolean(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Double -> e.putFloat(k, v.toFloat())
                is String -> e.putString(k, v)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    e.putStringSet(k, (v as Set<*>).map { it.toString() }.toSet())
                }
                is List<*> -> {
                    // org.json turns JSON arrays into Lists; prefs string-sets
                    // are the only collection type we store.
                    e.putStringSet(k, v.map { it.toString() }.toSet())
                }
                null -> e.remove(k)
            }
        }
        check(e.commit()) { "Could not apply backup settings." }
    }

    companion object {
        /** Step 56: the active profile's store — what background workers use. */
        fun forActiveProfile(context: Context): SettingsStore {
            val id = ProfileStore.activeProfileId(context)
            return if (id != null) SettingsStore(context, ProfileStore.profilePrefsName(id))
            else SettingsStore(context)
        }

        private const val SECRET_KEY_ALIAS = "plavio.settings.secrets.v1"
        private val SECRET_KEYS = setOf(
            "simkl_token", "torbox_api_key", "realdebrid_api_key", "premiumize_api_key",
            "trakt_access_token", "trakt_refresh_token",
            "media_servers",
            "tmdb_api_key", "omdb_api_key",
            "xtream_password", "stalker_password"
        )
        private const val KEY_AUTOPLAY = "autoplay"
        private const val KEY_SENTRY_ENABLED = "sentry_enabled"
        private const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
        private const val KEY_SHUFFLE_EPISODES = "shuffle_episodes"
        private const val KEY_PIP_ENABLED = "pip_enabled"
        private const val KEY_EPISODE_NOTIFICATIONS = "episode_notifications"
        private const val KEY_PREFERRED_PLAYER = "preferred_player"
        private const val KEY_BUFFER_MIN_MS = "buffer_min_ms"
        private const val KEY_BUFFER_MAX_MS = "buffer_max_ms"
        private const val KEY_BUFFER_FOR_PLAYBACK_MS = "buffer_for_playback_ms"
        private const val KEY_CUSTOM_BUFFERS = "custom_playback_buffers_enabled"
        private const val KEY_BUFFER_REBUFFER_MS = "buffer_rebuffer_ms"
        private const val KEY_BUFFER_BACK_BUFFER_MS = "buffer_back_buffer_ms"
        private const val KEY_DISK_CACHE_AUTO_SIZE = "disk_cache_auto_size"
        private const val KEY_FALLBACK_MAX_SOURCES = "fallback_max_sources"
        private const val KEY_FALLBACK_STARTUP_TIMEOUT_S = "fallback_startup_timeout_seconds"
        private const val KEY_SHUFFLE_INCLUDE_WATCHED = "shuffle_include_watched"
        private const val KEY_SMART_SORT_MODE = "smart_sort_mode"
        private const val KEY_SMART_RESOLUTION_LIMIT = "smart_resolution_limit"
        private const val KEY_SMART_SAVE_DATA_MOBILE = "smart_save_data_on_mobile"
        private const val KEY_SMART_FILTER_RESOLUTIONS = "smart_filter_resolutions"
        private const val KEY_SMART_FILTER_HDR = "smart_filter_hdr"
        private const val KEY_SMART_FILTER_CODECS = "smart_filter_codecs"
        private const val KEY_SMART_FILTER_AUDIO = "smart_filter_audio"
        private const val KEY_SMART_FILTER_SOURCES = "smart_filter_sources"
        private const val KEY_SMART_FILTER_MAX_SIZE_GB = "smart_filter_max_size_gb"
        private const val KEY_SMART_FILTER_INSTANT_ONLY = "smart_filter_instant_only"
        private const val KEY_SMART_FILTER_HIDE_LOW_QUALITY = "smart_filter_hide_low_quality"
        private const val KEY_DEBRID_MIN_QUALITY = "debrid_min_quality"
        private const val KEY_DEBRID_DV_FILTER = "debrid_dv_filter"
        private const val KEY_DEBRID_HDR_FILTER = "debrid_hdr_filter"
        private const val KEY_DEBRID_CODEC_FILTER = "debrid_codec_filter"
        private const val KEY_DEBRID_PREFERRED_TAGS = "debrid_preferred_tags"
        private const val KEY_DEBRID_REQUIRED_TAGS = "debrid_required_tags"
        private const val KEY_DEBRID_EXCLUDED_TAGS = "debrid_excluded_tags"
        private const val KEY_DEBRID_NAME_TEMPLATE = "debrid_name_template"
        private const val KEY_DEBRID_DESCRIPTION_TEMPLATE = "debrid_description_template"
        private const val KEY_DISK_CACHE_MB = "disk_cache_mb"
        private const val KEY_WATCHED_THRESHOLD_PERCENT = "watched_threshold_percent"
        private const val KEY_SKIP_BACKWARD_SECONDS = "skip_backward_seconds"
        private const val KEY_SKIP_FORWARD_SECONDS = "skip_forward_seconds"
        private const val KEY_HOLD_SPEED = "hold_speed_value"
        private const val KEY_TOUCH_GESTURES_ENABLED = "touch_gestures_enabled"
        private const val KEY_SWIPE_TO_SEEK_ENABLED = "swipe_to_seek_enabled"
        private const val KEY_RESIZE_MODE = "resize_mode"
        private const val KEY_AUDIO_BOOST_DB = "audio_boost_db"
        private const val KEY_AUDIO_DELAY_MS = "audio_delay_ms"
        private const val KEY_DOWNMIX_ENABLED = "downmix_enabled"
        private const val KEY_CUSTOM_ACCENT_HEX = "custom_accent_hex"
        private const val KEY_APP_ICON = "app_icon"
        private const val KEY_DOCK_STYLE = "dock_style"
        private const val KEY_POSTER_CORNER_RADIUS_DP = "poster_corner_radius_dp"
        private const val KEY_POSTER_CARD_WIDTH_DP = "poster_card_width_dp"
        private const val KEY_POSTER_HIDE_LABELS = "poster_hide_labels"
        private const val KEY_POSTER_LANDSCAPE_MODE = "poster_catalog_landscape_mode"
        private const val KEY_POSTER_LANDSCAPE_CLEARLOGO = "poster_landscape_clearlogo"
        private const val KEY_CUSTOM_POSTER_PATTERN = "custom_poster_pattern"
        private const val KEY_CUSTOM_POSTER_SCREENS = "custom_poster_screens"
        private const val KEY_CARD_DEPTH_ENABLED = "card_depth_enabled"
        private const val KEY_CARD_DEPTH_EDGE = "card_depth_edge"
        private const val KEY_CARD_DEPTH_SHEEN = "card_depth_sheen"
        private const val KEY_CARD_DEPTH_COVERAGE = "card_depth_coverage"
        private const val KEY_CARD_DEPTH_SURFACES = "card_depth_surfaces"
        private const val KEY_SHOW_PARENTAL_GUIDE = "show_parental_guide"
        private const val KEY_FORWARD_SUBTITLES_TO_EXTERNAL_PLAYER = "forward_subtitles_to_external_player"
        private const val KEY_SEND_SKIP_SEGMENTS_TO_EXTERNAL_PLAYER = "send_skip_segments_to_external_player"
        private const val KEY_DOWNMIX_TARGET_CHANNELS = "downmix_target_channels"
        private const val KEY_CENTER_MIX_LEVEL_DB = "center_mix_level_db"
        private const val KEY_MAINTAIN_ORIGINAL_AUDIO_DOWNMIX = "maintain_original_audio_on_downmix"
        private const val KEY_HOME_SCROLL_INDEX = "home_scroll_index"
        private const val KEY_HOME_SCROLL_OFFSET = "home_scroll_offset"
        private const val KEY_DISCOVER_TYPE = "discover_type"
        private const val KEY_DISCOVER_CATALOG_KEY = "discover_catalog_key"
        private const val KEY_DISCOVER_GENRE = "discover_genre"
        private const val KEY_NEXT_EPISODE_THRESHOLD_MODE = "next_episode_threshold_mode"
        private const val KEY_NEXT_EPISODE_THRESHOLD_PERCENT = "next_episode_threshold_percent"
        private const val KEY_NEXT_EPISODE_THRESHOLD_MINUTES = "next_episode_threshold_minutes"
        private const val KEY_REUSE_LAST_LINK_ENABLED = "reuse_last_link_enabled"
        private const val KEY_REUSE_LAST_LINK_CACHE_HOURS = "reuse_last_link_cache_hours"
        private const val KEY_HERO_TRAILER_PLAYBACK = "hero_trailer_playback"
        private const val KEY_HERO_TRAILER_START_DELAY_SECONDS = "hero_trailer_start_delay_seconds"
        private const val KEY_HERO_TRAILER_START_UNMUTED = "hero_trailer_start_unmuted"
        private const val KEY_CW_VISIBLE = "continue_watching_visible"
        private const val KEY_SHOW_DOWNLOAD_BUTTON = "show_download_button"
        private const val KEY_CW_STYLE = "continue_watching_style"
        private const val KEY_CW_SORT_MODE = "continue_watching_sort_mode"
        private const val KEY_CW_CLICK_ACTION = "continue_watching_click_action"
        private const val KEY_CW_RESUME_PROMPT = "continue_watching_resume_prompt"
        /** Prefer episode thumbnails in CW/Next Up cards (Nuvio useEpisodeThumbnails): default true. */
        private const val KEY_CW_PREFER_EPISODE_THUMBNAILS = "continue_watching_prefer_episode_thumbnails"
        /** Next Up seed from the furthest watched episode (Nuvio upNextFromFurthestEpisode): default true. */
        private const val KEY_CW_NEXT_UP_FROM_FURTHEST = "continue_watching_next_up_from_furthest"
        /** Show unaired next-up episodes (Nuvio showUnairedNextUp): default true. */
        private const val KEY_CW_SHOW_UNAIRED_NEXT_UP = "continue_watching_show_unaired_next_up"
        private const val KEY_RESUME_PROMPT_WAS_IN_PLAYER = "resume_prompt_was_in_player"
        private const val KEY_RESUME_PROMPT_LAST_VIDEO_ID = "resume_prompt_last_video_id"
        private const val KEY_SKIP_INTRO_OFFSET_SECONDS = "skip_intro_offset_seconds"
        private const val KEY_AUTO_SKIP_SEGMENT_TYPES = "auto_skip_segment_types"
        private const val KEY_SEASON_VIEW_MODE = "season_view_mode"
        // Step 47: Library Saved view — sort / layout / watched filter /
        // selected section ("" = all sections).
        private const val KEY_LIBRARY_SORT = "library_sort"
        private const val KEY_LIBRARY_LAYOUT = "library_layout"
        private const val KEY_LIBRARY_WATCHED_FILTER = "library_watched_filter"
        private const val KEY_LIBRARY_SECTION = "library_section"
        private const val KEY_PINNED_STREAM_SOURCES = "pinned_stream_sources"
        private const val KEY_ONBOARDING_DONE = "onboarding_done"
        
        private const val KEY_AUDIO_LANG = "preferred_audio_language"
        private const val KEY_SECONDARY_AUDIO_LANG = "secondary_preferred_audio_language"
        private const val KEY_SUBTITLE_FONT_ID = "subtitle_font_id"
        private const val KEY_SUBTITLE_SDH_FILTER = "subtitle_sdh_filter"
        private const val KEY_SUB_COLOR = "subtitle_text_color"
        private const val KEY_SUB_SIZE = "subtitle_text_size_scale"
        private const val KEY_SUB_BG = "subtitle_background_color"
        private const val KEY_SUB_EDGE = "subtitle_edge_type"
        private const val KEY_SUB_BOLD = "subtitle_bold"
        private const val KEY_SUB_EDGE_COLOR = "subtitle_edge_color"
        private const val KEY_SUB_BOTTOM = "subtitle_bottom_offset"
        private const val KEY_SUB_FONT_SP = "subtitle_font_size_sp"
        private const val KEY_SUB_DELAY_MAP = "subtitle_delay_by_video"
        private const val KEY_SUBTITLE_OFFSET_MS = "subtitle_offset_ms"
        private const val KEY_SYSTEM_CAPTIONS = "use_system_captions"
        private const val KEY_LIVE_FAVS = "live_favorites"
        private const val KEY_LIVE_RECENT = "live_recent"
        private const val KEY_M3U_LISTS = "m3u_playlists"
        private const val KEY_LIVE_TV_XTREAM_SERVER = "live_tv_xtream_server"
        private const val KEY_SIMKL_SYNC_SNAPSHOT = "simkl_sync_snapshot"
        private const val KEY_SIMKL_SYNC_MUTATIONS = "simkl_sync_mutations"
        private const val KEY_LIVE_TV_XTREAM_USERNAME = "live_tv_xtream_username"
        private const val KEY_LIVE_TV_XTREAM_PASSWORD = "xtream_password"
        private const val KEY_LIVE_TV_XTREAM_ENABLED = "live_tv_xtream_enabled"
        private const val KEY_LIVE_TV_STALKER_PORTAL = "live_tv_stalker_portal"
        private const val KEY_LIVE_TV_STALKER_MAC = "live_tv_stalker_mac"
        private const val KEY_LIVE_TV_STALKER_USERNAME = "live_tv_stalker_username"
        private const val KEY_LIVE_TV_STALKER_PASSWORD = "stalker_password"
        private const val KEY_LIVE_TV_STALKER_ENABLED = "live_tv_stalker_enabled"
        private const val KEY_LIVE_TV_EPG_SOURCES = "live_tv_epg_sources"
        private const val KEY_LIVE_TV_EPG_AUTO_DETECT = "live_tv_epg_auto_detect"
        private const val KEY_HOME_REFRESHED_AT = "home_catalogs_refreshed_at"
        private const val KEY_CS3_PROVIDER_ID = "cloudstream_provider_id"
        private const val KEY_CS3_PROVIDER_SELECTION_RESTORED = "cloudstream_provider_selection_restored"
        private const val KEY_HOME_ROW_PREFS = "home_catalog_row_prefs"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_SHOW_SKIP = "show_skip_button"
        private const val KEY_SUBTITLE_LANGS = "subtitle_languages"
        private const val KEY_ACCENT = "accent_theme"
        private const val KEY_PROFILE_USER_NAME = "profile_user_name"
        private const val KEY_PROFILE_AVATAR = "profile_avatar"
        private const val KEY_LIBRARY_SYNCED = "library_synced_keys"
        private const val KEY_LIBRARY_SYNC_AT = "library_sync_last_at"
        private const val KEY_LIBRARY_SYNC_RESULT = "library_sync_last_result"
        private const val KEY_AMOLED = "amoled_black"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_APP_THEME = "app_theme"
        private const val KEY_PREINSTALL_PREFIX = "preinstall_done_"
        private const val KEY_SIMKL_TOKEN = "simkl_token"
        private const val KEY_SIMKL_USERNAME = "simkl_username"
        private const val KEY_TRAKT_TOKEN = "trakt_access_token"
        private const val KEY_TRAKT_REFRESH = "trakt_refresh_token"
        private const val KEY_TRAKT_EXPIRY = "trakt_token_expiry"
        private const val KEY_TRAKT_USERNAME = "trakt_username"
        private const val KEY_MEDIA_SERVERS = "media_servers"
        private const val KEY_DETAIL_SECTIONS = "detail_sections"
        private const val KEY_FAVORITE_PEOPLE = "favorite_people"
        // Step 61: custom stream badges.
        private const val KEY_STREAM_BADGE_RULES = "stream_badge_rules"
        private const val KEY_SHOW_STREAM_BADGES_IN_PLAYER = "show_stream_badges_in_player"
        private const val KEY_PLAYER_BADGE_SOURCE = "player_badge_source"
        // Step 62: watch-progress pull.
        private const val KEY_WATCH_PROGRESS_SOURCE = "watch_progress_source"
        private const val KEY_REMOTE_RESUME_POINTS = "remote_resume_points"
        private const val KEY_LAST_WATCH_PROGRESS_PULL = "last_watch_progress_pull"
        // Step 63: player toolbar layouts.
        private const val KEY_PLAYER_BOTTOM_LAYOUT = "player_bottom_layout"
        private const val KEY_PLAYER_TOP_LAYOUT = "player_top_layout"
        // Step 64: background audio.
        private const val KEY_BACKGROUND_AUDIO = "background_audio"
        private const val KEY_BINGE_GROUP_CACHE = "binge_group_cache"
        private const val KEY_AUTOPLAY_PREFER_BINGE_GROUP = "autoplay_prefer_binge_group"
        private const val KEY_MEDIA_SERVER_DEVICE_ID = "media_server_device_id"
        private const val KEY_TRACKING_LIBRARY_SOURCE = "tracking_library_source"
        private const val KEY_TRACKING_PROGRESS_SOURCE = "tracking_progress_source"
        private const val KEY_TORBOX_API_KEY = "torbox_api_key"
        private const val KEY_REALDEBRID_API_KEY = "realdebrid_api_key"
        private const val KEY_PREMIUMIZE_API_KEY = "premiumize_api_key"
        private const val KEY_PREFERRED_DEBRID_RESOLVER = "preferred_debrid_resolver"
        private const val KEY_DEBRID_INSTANT_PREPARE_LIMIT = "debrid_instant_prepare_limit"
        private const val KEY_TMDB_API_KEY = "tmdb_api_key"
        private const val KEY_OMDB_API_KEY = "omdb_api_key"
        private const val KEY_STREAMS_PER_SOURCE = "streams_per_source"
        private const val KEY_QUALITY_ORDER = "quality_order"
        private const val KEY_STREAM_AUTOPLAY = "stream_autoplay"
        private const val KEY_AUTOPLAY_QUALITY = "autoplay_quality"
        private const val KEY_AUTOPLAY_MAX_SIZE = "autoplay_max_size_gb"
    }
}
