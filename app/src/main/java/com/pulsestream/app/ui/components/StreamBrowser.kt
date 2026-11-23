@file:OptIn(ExperimentalLayoutApi::class)
package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import coil.compose.AsyncImage
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.SmartSortMode
import com.pulsestream.app.data.SmartStreamQuality
import com.pulsestream.app.data.SmartStreamRanking
import com.pulsestream.app.data.debridFormattedDescription
import com.pulsestream.app.data.debridFormattedName
import com.pulsestream.app.data.hasDebridPreferredTag
import com.pulsestream.app.data.matchesDebridStreamPrefs
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.ui.glass.ambientLiquidGlass
import com.pulsestream.app.ui.theme.AppPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.tv.tvFocusRing

// ---------------------------------------------------------------------------
// Stream metadata parsing
// ---------------------------------------------------------------------------

/** Human-readable bits extracted from a stream's title/name. */
data class ParsedStreamInfo(
    val quality: String? = null,
    val source: String? = null,
    val codec: String? = null,
    val audio: String? = null,
    val size: String? = null
)

private val QUALITY_RE = Regex("""\b(2160p|1440p|1080p|720p|480p|360p)\b""", RegexOption.IGNORE_CASE)
private val FOUR_K_RE = Regex("""\b(4K|UHD)\b""", RegexOption.IGNORE_CASE)
// Named quality labels (AIOStreams-style formatters print these instead of
// numeric tags): FHD = 1080p, HD = 720p, SD = 480p. Word boundaries keep
// HDRip / HDCAM / HDTV from matching the bare HD rule.
private val FHD_RE = Regex("""\bFHD\b""", RegexOption.IGNORE_CASE)
private val HD_RE = Regex("""\bHD\b""", RegexOption.IGNORE_CASE)
private val SD_RE = Regex("""\bSD\b""", RegexOption.IGNORE_CASE)
private val SOURCE_RE =
    Regex("""\b(Blu[-\s]?Ray|BRRip|WEB[-\s]?DL|WEBRip|HDRip|HDCAM|HDTS|\bTS\b|CAM)\b""", RegexOption.IGNORE_CASE)
private val CODEC_RE = Regex("""\b(x265|HEVC|x264|AVC|AV1|VP9)\b""", RegexOption.IGNORE_CASE)
private val AUDIO_RE = Regex(
    """\b(Atmos|DTS[-\s]?HD(?:\s?MA)?|TrueHD|DTS|DD\+?(?:\s?[57]\.1)?|Dolby(?:\s?Digital)?|AAC(?:\s?5\.1)?)\b""",
    RegexOption.IGNORE_CASE
)
private val SIZE_RE = Regex("""(\d+(?:\.\d+)?\s?(?:GB|MB))""", RegexOption.IGNORE_CASE)

/** Scans a stream's title/name for quality, source, codec, audio and size tags. */
fun parseStreamInfo(stream: StreamItem): ParsedStreamInfo {
    val haystack = listOfNotNull(stream.title, stream.name).joinToString("\n")
    if (haystack.isBlank()) return ParsedStreamInfo()
    val quality = FOUR_K_RE.find(haystack)?.value?.uppercase()
        ?: QUALITY_RE.find(haystack)?.value?.lowercase()
        ?: if (FHD_RE.containsMatchIn(haystack)) "1080p"
        else if (HD_RE.containsMatchIn(haystack)) "720p"
        else if (SD_RE.containsMatchIn(haystack)) "480p"
        else null
    val source = SOURCE_RE.find(haystack)?.value
        ?.replace(Regex("""[-\s]+"""), "-")?.uppercase()
        ?.replace("BLU-RAY", "BluRay")?.replace("WEB-DL", "WEB-DL")
    val codec = CODEC_RE.find(haystack)?.value?.uppercase()
    val audio = AUDIO_RE.find(haystack)?.value
        ?.replace(Regex("""\s+"""), " ")?.trim()
        ?.let { a ->
            when {
                a.equals("Atmos", ignoreCase = true) -> "Atmos"
                a.startsWith("DTS", ignoreCase = true) -> a.uppercase().replace(" ", "")
                a.startsWith("DD", ignoreCase = true) -> a.uppercase().replace(" ", "")
                a.startsWith("Dolby", ignoreCase = true) -> "Dolby"
                else -> a.uppercase()
            }
        }
    val size = SIZE_RE.find(haystack)?.value?.uppercase()?.replace(" ", "")
        ?.let { s -> s.dropLast(2) + " " + s.takeLast(2) }
    return ParsedStreamInfo(quality, source, codec, audio, size)
}

/** Canonical filter bucket for a parsed quality label; unknown labels have no filter bucket. */
fun normalizeStreamQuality(quality: String?): String? {
    val value = quality?.trim()?.lowercase()?.replace(" ", "") ?: return null
    return when (value) {
        "4k", "uhd", "2160p" -> "4K"
        "fhd" -> "1080p"
        "hd" -> "720p"
        "sd" -> "480p"
        "1440p", "1080p", "720p", "480p", "360p" -> value
        else -> null
    }
}

private val STREAM_QUALITY_ORDER = listOf("4K", "1440p", "1080p", "720p", "480p", "360p")

/** Qualities currently represented in [streams], regardless of source or preference caps. */
fun availableStreamQualities(streams: List<StreamItem>): List<String> =
    streams.mapNotNull { normalizeStreamQuality(parseStreamInfo(it).quality) }
        .distinct()
        .sortedBy { STREAM_QUALITY_ORDER.indexOf(it).let { index -> if (index < 0) Int.MAX_VALUE else index } }

/** Applies a canonical quality bucket across the whole combined list, retaining every matching addon. */
fun filterStreamsByQuality(streams: List<StreamItem>, quality: String?): List<StreamItem> {
    val bucket = normalizeStreamQuality(quality) ?: return streams
    return streams.filter { normalizeStreamQuality(parseStreamInfo(it).quality) == bucket }
}

/** Compare display labels after case/spacing/punctuation normalization, retaining original text. */
private fun comparableStreamLabel(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

private fun streamLinesEquivalent(first: String, second: String): Boolean {
    val firstLabel = comparableStreamLabel(first)
    val secondLabel = comparableStreamLabel(second)
    if (firstLabel == secondLabel) return true
    val firstQuality = normalizeStreamQuality(first)
    val secondQuality = normalizeStreamQuality(second)
    return firstQuality != null && firstQuality == secondQuality
}

/** Combine addon title/name while removing repeated and less-informative overlapping lines. */
fun streamDisplayName(title: String?, name: String?, fallbackUrl: String = ""): String {
    val t = title?.trim().orEmpty()
    val n = name?.trim().orEmpty()
    if (t.isBlank() && n.isBlank()) {
        return fallbackUrl.substringAfter("://").substringBefore("/").take(48)
    }

    val lines = mutableListOf<String>()
    (listOf(t, n).filter { it.isNotBlank() }.flatMap { field ->
        field.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }).forEach { candidate ->
        val candidateLabel = comparableStreamLabel(candidate)
        if (candidateLabel.isEmpty()) return@forEach
        val overlaps = lines.indices.filter { index ->
            val existingLabel = comparableStreamLabel(lines[index])
            streamLinesEquivalent(lines[index], candidate) ||
                (existingLabel.isNotEmpty() &&
                    (existingLabel.contains(candidateLabel) || candidateLabel.contains(existingLabel)))
        }
        if (overlaps.isEmpty()) {
            lines += candidate
        } else {
            val richer = overlaps.filter { index ->
                comparableStreamLabel(lines[index]).length < candidateLabel.length
            }
            if (richer.isNotEmpty()) {
                val insertionIndex = richer.minOrNull()!!
                richer.asReversed().forEach { index -> lines.removeAt(index) }
                lines.add(insertionIndex.coerceAtMost(lines.size), candidate)
            }
        }
    }
    return lines.joinToString("\n").ifBlank {
        fallbackUrl.substringAfter("://").substringBefore("/").take(48)
    }
}

/** Best one-line label for a stream card headline. */
fun streamHeadline(stream: StreamItem): String {
    // Prefer name (e.g. "2160p · HDHub") — it includes the provider.
    // Fall back to title's first line, then URL host.
    if (!stream.name.isNullOrBlank()) return stream.name!!
    val firstLine = stream.title?.lineSequence()
        ?.map { it.trim() }?.firstOrNull { it.isNotBlank() }
    if (!firstLine.isNullOrBlank()) return firstLine
    return stream.url.substringAfter("://").substringBefore("/").take(48)
}

/** One authoritative set of labels for a compact source-picker card. */
data class StreamDisplayModel(
    val headline: String,
    val provider: String?,
    val metadata: List<String>
)

/** Keeps parsed details out of the chips when the full filename already shows them. */
fun streamDisplayModel(stream: StreamItem): StreamDisplayModel {
    val headline = streamDisplayName(stream.title, stream.name, stream.url)
    val normalizedHeadline = comparableStreamLabel(headline)
    val providerLabel = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonKey()
    val visibleProvider = providerLabel.takeUnless {
        normalizedHeadline.contains(comparableStreamLabel(it))
    }
    val info = parseStreamInfo(stream)
    val headlineQuality = FOUR_K_RE.find(headline)?.value
        ?: QUALITY_RE.find(headline)?.value
    val representedQuality = normalizeStreamQuality(headlineQuality)
    val details = listOfNotNull(
        info.quality?.uppercase(), info.codec?.uppercase(), info.audio, info.size, info.source
    ).distinct()
    val visibleDetails = details.filterNot {
        (normalizeStreamQuality(it) != null && normalizeStreamQuality(it) == representedQuality) ||
            normalizedHeadline.contains(comparableStreamLabel(it))
    }
    return StreamDisplayModel(headline, visibleProvider, visibleDetails)
}

/** Addon grouping key for tabs. */
fun StreamItem.addonKey(): String = sourceName?.takeIf { it.isNotBlank() }
    ?: name?.takeIf { it.isNotBlank() } ?: "Unknown"

/** Pin identity for a stream (Nuvio PinnedStreamSourcesRepository): addon + name. */
fun pinnedStreamSourceKey(stream: StreamItem): String =
    "${stream.sourceId ?: ""}|${stream.sourceName ?: stream.addonKey()}"

// ---------------------------------------------------------------------------
// Stream preference sorting (Settings > Streams, v0.8.31+)
// ---------------------------------------------------------------------------

/**
 * Canonical quality bucket for sort priority: "2160p" / "1080p" / "720p" /
 * "480p", or "" when the stream carries no recognizable quality tag.
 */
fun canonicalStreamQuality(stream: StreamItem): String {
    val q = parseStreamInfo(stream).quality ?: return ""
    return when {
        q.contains("2160") || q.equals("4K", ignoreCase = true) || q.equals("UHD", ignoreCase = true) -> "2160p"
        q.contains("1080") -> "1080p"
        q.contains("720") -> "720p"
        q.contains("480") -> "480p"
        else -> ""
    }
}

/**
 * File size in GB parsed from the stream info ("9.92 GB" / "430.3 MB").
 * Null when the stream carries no size tag.
 */
fun streamSizeGb(stream: StreamItem): Double? {
    val s = parseStreamInfo(stream).size ?: return null
    val m = Regex("""([\d.]+)\s*(GB|MB)""", RegexOption.IGNORE_CASE).find(s) ?: return null
    val v = m.groupValues[1].toDoubleOrNull() ?: return null
    return if (m.groupValues[2].equals("GB", ignoreCase = true)) v else v / 1024.0
}

/**
 * Applies the user's stream preferences to a raw stream list:
 *  1. per-source cap — at most [SettingsStore.streamsPerSource] streams per
 *     addon, so one source can't flood the picker;
 *  2. Smart Streams (Step 42) — the Smart filters narrow the list, then the
 *     chosen sort mode orders it (Best match = scored ranking; Addon order =
 *     the legacy qualityOrder sort);
 *  3. pinned sources first — a pinned source's streams list first
 *     everywhere, including autoplay picks.
 * The old explicit TorBox-instant-first tier is gone: the score's instant
 * +40 covers cached streams of any source (a cached 720p ≈ 77 beats an
 * uncached 4K ≈ 52). The sort is stable: within the same quality bucket the
 * original (star-priority) order is preserved. Unknown-quality streams sink
 * last.
 */
fun applyStreamPreferences(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore,
    limitPerSource: Boolean = true,
    isEpisode: Boolean = false
): List<StreamItem> {
    val perSource = settings.streamsPerSource
    val pinned = settings.pinnedStreamSources
    val smart = settings.smartStreamSettings()
    // Step 44 — Debrid stream prefs: min quality / DV / HDR / codec /
    // required / excluded tags filter the list; preferred tags boost.
    val debridPrefs = settings.debridStreamFilterPrefs()
    // Filter FIRST, then cap per source. Capping first could discard the
    // very streams the filter would keep — e.g. an addon's first 5 rows
    // are all CAM (hidden) while its 1080p rows sit past the cap — and
    // that addon would show zero links even though it has matching ones.
    val filtered = streams.filter { stream ->
        val info = com.pulsestream.app.data.SmartStreamQuality.cached(stream)
        com.pulsestream.app.data.SmartStreamRanking.matches(info, smart.filters) &&
            matchesDebridStreamPrefs(stream, info, debridPrefs)
    }
    val capped = if (limitPerSource) filtered.groupBy { it.addonKey() }
        .flatMap { (_, list) -> list.take(perSource) } else filtered
    val sorted = if (smart.sortMode == com.pulsestream.app.data.SmartSortMode.ADDON_ORDER) {
        val order = settings.qualityOrder
        capped.sortedWith(compareByDescending<StreamItem> { s ->
            if (pinnedStreamSourceKey(s) in pinned) 1 else 0
        }.thenByDescending { s ->
            if (hasDebridPreferredTag(s, debridPrefs)) 1 else 0
        }.thenBy { s ->
            val idx = order.indexOf(canonicalStreamQuality(s))
            if (idx >= 0) idx else Int.MAX_VALUE
        })
    } else {
        val smartSorted = com.pulsestream.app.data.SmartStreamRanking.sortStreams(
            capped, smart, settings.smartDeviceProfile, isEpisode,
            settings.smartPreferredLanguages()
        )
        val smartIndex = smartSorted.withIndex().associate { it.value to it.index }
        capped.sortedWith(compareByDescending<StreamItem> { s ->
            if (pinnedStreamSourceKey(s) in pinned) 1 else 0
        }.thenByDescending { s ->
            if (hasDebridPreferredTag(s, debridPrefs)) 1 else 0
        }.thenBy { s -> smartIndex[s] ?: Int.MAX_VALUE })
    }
    return sorted
}

/**
 * Picks the autoplay stream under the Settings > Streams rule.
 * Step 42 — when quality is "Auto", the Smart Streams scored ranking
 * decides (instant + resolution vs screen + bitrate sweet spot + codec/HDR
 * + language) instead of the old adaptive cap; an explicit quality still
 * wins over the score. The size limit applies in both paths. Falls back to
 * the first stream when nothing matches, so autoplay never dead-ends on an
 * empty pick.
 * Step 60 — binge groups: when [preferredBingeGroup] is set and any
 * playable stream carries it, the pick is restricted to that group
 * (Nuvio's tryBingeGroupOnly, hard preference, not just the +25
 * ranking bonus — the bonus still orders within the group); when
 * [preferredFilename] is set, exact filename matches come first
 * inside the group. No group match → the whole list, as before.
 */
fun pickAutoplayStream(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore,
    isEpisode: Boolean = false,
    preferredBingeGroup: String? = null,
    preferredFilename: String? = null
): StreamItem? {
    // Filter out unresolved infohash: placeholders - only pick playable URLs.
    // Debrid resolution replaces these with real HTTP links asynchronously.
    val playable = streams.filter { !it.url.startsWith("infohash:") }
    if (playable.isEmpty()) return null
    val prefQ = settings.autoplayQuality
    val maxGb = settings.autoplayMaxSizeGb
    // Step 60: same release group first — only when one actually matches.
    val groupPool = if (preferredBingeGroup != null) {
        playable.filter { it.bingeGroup == preferredBingeGroup }.ifEmpty { null }
    } else null
    val pool = groupPool ?: playable
    // Exact next-filename matches (built from the saved filename) lead
    // inside the pool; the ranking still orders the rest.
    val filenameFirst = if (preferredFilename != null) {
        pool.filter { com.pulsestream.app.data.FilenameEpisodeMatcher.matches(it.filename, preferredFilename) }
    } else emptyList()

    if (prefQ == "Auto") {
        val ranking = com.pulsestream.app.data.SmartStreamRanking.rankForAutoPlay(
            streams = pool,
            settings = settings.smartStreamSettings(),
            device = settings.smartDeviceProfile,
            isEpisode = isEpisode,
            preferredLanguages = settings.smartPreferredLanguages(),
            preferredBingeGroup = preferredBingeGroup,
        )
        val ranked = ranking.ranked.map { it.stream }
        if (ranked.isNotEmpty()) {
            val ordered = filenameFirst.filter { it in ranked } +
                ranked.filter { it !in filenameFirst }
            return ordered.firstOrNull { s ->
                maxGb <= 0 || (streamSizeGb(s)?.let { it <= maxGb } ?: true)
            } ?: ordered.firstOrNull()
        }
        // Ranking empty (everything filtered out): fall through to the legacy sort.
    }
    val order = settings.qualityOrder
    val sorted = pool.sortedWith(compareBy { s ->
        val q = canonicalStreamQuality(s)
        when {
            prefQ != "Auto" && q == prefQ -> -1
            else -> order.indexOf(q).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
    })
    val ordered = filenameFirst.filter { it in sorted } +
        sorted.filter { it !in filenameFirst }
    return ordered.firstOrNull { s ->
        maxGb <= 0 || (streamSizeGb(s)?.let { it <= maxGb } ?: true)
    } ?: ordered.firstOrNull()
}

// ---------------------------------------------------------------------------
// Addon tabs + state
// ---------------------------------------------------------------------------

const val STREAM_TAB_ALL = "__all__"

data class StreamTab(val key: String, val label: String, val count: Int)

@Stable
class StreamBrowserState(
    val tabs: List<StreamTab>,
    val selectedTab: String,
    val filtered: List<StreamItem>,
    val onSelectTab: (String) -> Unit,
    // Quality filter (user's order): dynamic pills, only shown qualities.
    val qualities: List<String>,
    val selectedQuality: String?,
    val onSelectQuality: (String?) -> Unit
)

@Composable
fun rememberStreamBrowserState(
    streams: List<StreamItem>,
    settings: com.pulsestream.app.data.SettingsStore? = null,
    limitPerSource: Boolean = true,
    isEpisode: Boolean = false
): StreamBrowserState {
    // The player shows the complete loaded set; other pickers retain the user's cap.
    // Step 42: the Smart prefs are remember keys, so changing a filter/sort in
    // the Smart sheet re-orders the list immediately. Step 44: same for the
    // debrid stream prefs.
    val ordered = remember(
        streams, settings?.streamsPerSource, settings?.qualityOrder, settings?.pinnedStreamSources,
        settings?.smartSortMode, settings?.smartResolutionLimit, settings?.smartSaveDataOnMobile,
        settings?.smartFilterResolutions, settings?.smartFilterHdr, settings?.smartFilterCodecs,
        settings?.smartFilterAudio, settings?.smartFilterSources, settings?.smartFilterMaxSizeGb,
        settings?.smartFilterInstantOnly, settings?.smartFilterHideLowQuality,
        settings?.debridMinQuality, settings?.debridDvFilter, settings?.debridHdrFilter,
        settings?.debridCodecFilter, settings?.debridPreferredTags, settings?.debridRequiredTags,
        settings?.debridExcludedTags,
        settings?.streamBadgeRulesJson,
        limitPerSource, isEpisode
    ) {
        val base = if (settings != null) applyStreamPreferences(streams, settings, limitPerSource, isEpisode) else streams
        // A filter that matches nothing must not blank the picker while
        // the player happily plays the first stream: fall back to the
        // unfiltered list when the filtered one is empty (same rule as
        // pickAutoplayStream — autoplay never dead-ends on an empty pick).
        // The filter chips stay visible, so the user still sees a filter
        // is on and can change it; the list just never hides playable
        // streams. Genuinely stream-less titles stay empty (streams is
        // empty too), so the empty state is unchanged for them.
        val effective = if (base.isEmpty() && streams.isNotEmpty()) streams else base
        // Step 61: match imported badge rules onto the ordered list.
        val badgeRules = settings?.let { com.pulsestream.app.data.StreamBadgeRules.parseStored(it.streamBadgeRulesJson) }
            ?: com.pulsestream.app.data.StreamBadgeRules()
        if (badgeRules.hasImport) com.pulsestream.app.data.StreamBadgePresentation.applyToStreams(effective, badgeRules) else effective
    }
    // Saveable: the addon tab the user picked survives rotation. A stale
    // key (tab gone after the stream list changed) falls back to "All".
    var selectedTab by rememberSaveable { mutableStateOf(STREAM_TAB_ALL) }
    // Quality filter: null = All qualities. Dynamic — only qualities present
    // in the current stream list get a pill (user's order).
    var selectedQuality by rememberSaveable { mutableStateOf<String?>(null) }
    val tabs = remember(ordered) {
        val groups = ordered.groupBy { it.addonKey() }
        buildList {
            add(StreamTab(STREAM_TAB_ALL, "All", ordered.size))
            groups.entries.sortedByDescending { it.value.size }.forEach { (key, list) ->
                add(StreamTab(key, key, list.size))
            }
        }
    }
    // Base choices on all fresh combined results, not the possibly capped
    // display list. This keeps every quality discoverable in the picker.
    val qualities = remember(streams) { availableStreamQualities(streams) }
    val safeTab = if (tabs.any { it.key == selectedTab }) selectedTab else STREAM_TAB_ALL
    val normalizedSelection = normalizeStreamQuality(selectedQuality)
    val safeQuality = normalizedSelection?.takeIf { it in qualities }
    val filtered = remember(ordered, safeTab, safeQuality) {
        var list = if (safeTab == STREAM_TAB_ALL) ordered
        else ordered.filter { it.addonKey() == safeTab }
        list = filterStreamsByQuality(list, safeQuality)
        list
    }
    return remember(tabs, safeTab, filtered, qualities, safeQuality) {
        StreamBrowserState(
            tabs, safeTab, filtered, { selectedTab = it }, qualities, safeQuality,
            { selectedQuality = normalizeStreamQuality(it) }
        )
    }
}

@Composable
fun AddonTabs(state: StreamBrowserState, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (!compact && state.tabs.size <= 2) return // Preserve the original non-compact behavior.
    val palette = LocalPalette.current
    val tabs = if (compact) state.tabs.sortedBy { tab ->
        when {
            tab.key == STREAM_TAB_ALL -> 0
            tab.label.equals("PlavioHD", ignoreCase = true) -> 1
            tab.label.equals("TMAddon", ignoreCase = true) -> 2
            tab.label.equals("StAddon", ignoreCase = true) -> 3
            else -> 4
        }
    } else state.tabs
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        items(tabs, key = { it.key }) { tab ->
            val selected = tab.key == state.selectedTab
            // Nuvio AddonFilterChip (compact = player Sources): 20dp
            // radius, selected = accent fill + onAccent text, unselected
            // = surfaceCard #222222 + 1dp #252A2A border, h16/v10.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(if (compact) 20.dp else 50.dp))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else if (compact) Color(0xFF222222)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent
                        else if (compact) Color(0xFF252A2A)
                        else GlassBorder,
                        RoundedCornerShape(if (compact) 20.dp else 50.dp)
                    )
                    .clickable(role = Role.Tab) { state.onSelectTab(tab.key) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(if (compact) 20.dp else 50.dp))
                    .padding(
                        horizontal = if (compact) 16.dp else 14.dp,
                        vertical = if (compact) 10.dp else 8.dp
                    )
            ) {
                Text(
                    text = if (compact) tab.label else "${tab.label} • ${tab.count}",
                    style = if (compact) MaterialTheme.typography.labelLarge
                        else MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected && compact) {
                        if (palette.accent.luminance() > 0.5f) Color.Black else Color.White
                    } else if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Quality filter pills (user's order): only qualities present in the stream
 * list get a pill. Tapping one filters across ALL addons.
 */
@Composable
fun QualityPills(state: StreamBrowserState, modifier: Modifier = Modifier, compact: Boolean = false) {
    if (state.qualities.isEmpty() || (!compact && state.qualities.size <= 1)) return // Nothing useful to filter.
    val palette = LocalPalette.current
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = if (compact) 8.dp else 16.dp),
        horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        item(key = "quality_all") {
            val selected = state.selectedQuality == null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.RadioButton) { state.onSelectQuality(null) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    text = if (compact) "Any" else "All",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && compact) Color.Black else if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
        items(state.qualities, key = { "quality_$it" }) { quality ->
            val selected = state.selectedQuality == quality
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected && compact) palette.accent
                        else if (selected) palette.accent.copy(alpha = 0.22f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .border(
                        1.dp,
                        if (selected) palette.accent else GlassBorder,
                        RoundedCornerShape(50)
                    )
                    .clickable(role = Role.RadioButton) { state.onSelectQuality(if (selected) null else quality) }
                    .semantics { this.selected = selected }
                    .tvFocusRing(RoundedCornerShape(50))
                    .padding(horizontal = if (compact) 11.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp)
            ) {
                Text(
                    text = quality.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected && compact) Color.Black else if (selected) TextPrimary else TextSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stream rows
// ---------------------------------------------------------------------------

/**
 * Single stream row: the full title/filename line plus the download
 * action.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
fun StreamCard(
    stream: StreamItem,
    selected: Boolean = false,
    onClick: () -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false,
    settings: SettingsStore? = null
) {
    val palette = LocalPalette.current
    val unresolvedTorrent = stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)
    val info = remember(stream) { parseStreamInfo(stream) }
    // Full filename display (user's order): show the complete title/name,
    // never truncated. Prefer title (usually the full filename).
    val display = remember(stream) { streamDisplayModel(stream) }
    // Step 44 — debrid name/description templates rename debrid-resolved
    // rows; other rows keep their addon name.
    val formattedName = remember(stream, settings?.debridNameTemplate) {
        settings?.let { debridFormattedName(stream, it) }
    }
    val formattedDescription = remember(stream, settings?.debridDescriptionTemplate) {
        settings?.let { debridFormattedDescription(stream, it) }
    }
    val fullName = formattedName ?: display.headline
    val shape = RoundedCornerShape(20.dp)

    if (compact) {
        CompactStreamCard(
            stream = stream,
            selected = selected,
            display = display,
            onClick = onClick,
            onDownload = onDownload,
            headline = fullName,
            description = formattedDescription
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(shape)
                .then(
                    if (selected) Modifier.border(1.5.dp, palette.accent, shape)
                    else Modifier
                )
                .clickable(enabled = !unresolvedTorrent, onClick = onClick)
                .tvFocusRing(shape)
                .padding(20.dp)
        ) {
            // Title — full name, bigger text (user's order). The
            // title/filename line is the whole row content.
            Text(
                text = fullName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 24.sp,
                modifier = Modifier.fillMaxWidth()
            )
            if (formattedDescription != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formattedDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (!unresolvedTorrent && com.pulsestream.app.data.isUnseekableStreamUrl(stream.url)) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Seeking not supported by this host",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF59E0B)
                )
            }
            // Step 61: matched badge chips from the user's imported rules.
            if (stream.badges.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stream.badges.forEach { badge ->
                        StreamBadgeChip(badge = badge, size = StreamBadgeChipSize.STREAM)
                    }
                }
            }

            // Bottom row: download button only (no URLs — user's order).
            if (onDownload != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(
                        onClick = { onDownload(stream) },
                        enabled = !unresolvedTorrent,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompactStreamCard(
    stream: StreamItem,
    selected: Boolean,
    display: StreamDisplayModel,
    onClick: () -> Unit,
    onDownload: ((StreamItem) -> Unit)?,
    headline: String? = null,
    description: String? = null
) {
    val palette = LocalPalette.current
    val shape = RoundedCornerShape(12.dp)
    val unresolvedTorrent = stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)
    // Nuvio StreamCard: glass row (blur 18, static backdrop), min height
    // 68dp, 12dp radius, 14dp padding. Selected = accent-tinted glass +
    // 1dp accent 0.52 border; unselected = white 0.05 glass, no border.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clip(shape)
            .ambientLiquidGlass(
                shape = shape,
                tint = if (selected) palette.accent.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.05f),
                fallback = if (selected) palette.accent.copy(alpha = 0.12f)
                    else Color.White.copy(alpha = 0.05f),
                blurRadius = 18.dp,
                staticFromBackdrop = true,
            )
            .then(
                if (selected) Modifier.border(
                    1.dp, palette.accent.copy(alpha = 0.52f), shape
                ) else Modifier
            )
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(enabled = !unresolvedTorrent, onClick = onClick).tvFocusRing(shape)) {
            Text(
                text = headline ?: display.headline,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                lineHeight = 19.sp
            )
            // Addon name (Nuvio StreamCard shows the addon logo + name):
            // always shown, so the user can see which addon each link is
            // from even when the filename already mentions it (display
            // .provider hides it in that case, which is why it was
            // invisible). Accent so it reads as the source, not the title.
            val providerLabel = stream.sourceName?.takeIf { it.isNotBlank() } ?: stream.addonKey()
            if (providerLabel.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.accent
                )
            }
            if (description != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            if (unresolvedTorrent) {
                Text("Torrent source · cached debrid link required", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            if (!unresolvedTorrent && com.pulsestream.app.data.isUnseekableStreamUrl(stream.url)) {
                Text("Seeking not supported by this host", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF59E0B))
            }
            // Step 61: matched badge chips from the user's imported rules.
            if (stream.badges.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    stream.badges.forEach { badge ->
                        StreamBadgeChip(badge = badge, size = StreamBadgeChipSize.STREAM)
                    }
                }
            }
            // Nuvio CurrentStreamBadge: a 999dp pill (accent fill, onAccent
            // 11sp SemiBold text) — not plain text.
            if (selected) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(palette.accent)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Playing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (palette.accent.luminance() > 0.5f) Color.Black else Color.White
                    )
                }
            }
        }
        if (onDownload != null) {
            IconButton(onClick = { onDownload(stream) }, enabled = !unresolvedTorrent, modifier = Modifier.size(48.dp)) {
                Icon(
                    Icons.Filled.Download,
                    contentDescription = "Download",
                    tint = TextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        IconButton(onClick = onClick, enabled = !unresolvedTorrent, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Play stream",
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// List-level rendering: container adapts to the chosen style
// ---------------------------------------------------------------------------

/**
 * Stream list: one clean layout — title/filename rows only.
 */
@Composable
fun StreamList(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedUrl: String? = null,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false,
    settings: SettingsStore? = null
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = contentPadding
    ) {
        itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
            StreamCard(
                stream = stream,
                selected = stream.url == selectedUrl,
                onClick = { onPick(stream) },
                onDownload = onDownload,
                compact = compact,
                settings = settings
            )
        }
    }
}

/**
 * LazyListScope variant for stream sections nested inside another LazyColumn
 * (e.g. the detail screen's inline sources).
 */
fun LazyListScope.streamListItems(
    streams: List<StreamItem>,
    onPick: (StreamItem) -> Unit,
    selectedUrl: String? = null,
    onDownload: ((StreamItem) -> Unit)? = null,
    compact: Boolean = false,
    settings: SettingsStore? = null
) {
    itemsIndexed(streams, key = { index, s -> "${index}_${s.url}" }) { _, stream ->
        StreamCard(
            stream = stream,
            selected = stream.url == selectedUrl,
            onClick = { onPick(stream) },
            onDownload = onDownload,
            compact = compact,
            settings = settings
        )
    }
}

// ---------------------------------------------------------------------------
// Step 42 — Smart Streams sheet
// ---------------------------------------------------------------------------

/** One selectable pill, same style as [QualityPills]. */
@Composable
private fun SmartChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (selected) palette.accent.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.06f)
            )
            .border(
                1.dp,
                if (selected) palette.accent else GlassBorder,
                RoundedCornerShape(50)
            )
            .clickable(role = Role.RadioButton) { onClick() }
            .semantics { this.selected = selected }
            .tvFocusRing(RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) TextPrimary else TextSecondary,
            maxLines = 1
        )
    }
}

@Composable
private fun SmartFilterGroup(
    title: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = TextPrimary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            SmartChip(
                label = option,
                selected = option in selected,
                onClick = { onToggle(option) }
            )
        }
    }
}

/**
 * Step 42a — Smart Streams sheet (Nuvio SmartStreamControls): sort mode,
 * resolution limit, filter chips (resolution / HDR / codec / audio / source /
 * max size), instant-only + hide-low-quality toggles, and the scored best
 * pick with its reasons. Writes straight into [SettingsStore]; every change
 * calls [onChanged] so the parent list re-sorts immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartStreamSheet(
    streams: List<StreamItem>,
    settings: SettingsStore,
    isEpisode: Boolean,
    onChanged: () -> Unit,
    onDismiss: () -> Unit,
) {
    var tick by remember { mutableStateOf(0) }
    val smart = remember(tick) { settings.smartStreamSettings() }
    val device = settings.smartDeviceProfile
    val langs = remember(tick) { settings.smartPreferredLanguages() }
    val best = remember(streams, tick) {
        SmartStreamRanking.bestPick(streams, smart, device, isEpisode, langs)
    }
    val shownCount = remember(streams, tick) {
        streams.count { SmartStreamRanking.matches(SmartStreamQuality.cached(it), smart.filters) }
    }
    fun bump() {
        tick++
        onChanged()
    }

    // Nuvio-style bottom glass panel (PlayerOverlayPanel) — not a flat
    // Material3 ModalBottomSheet.
    com.pulsestream.app.ui.screens.player.PlayerOverlayPanel(
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Smart Streams",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (smart.filters.activeCount > 0 || smart.sortMode != SmartSortMode.BEST_MATCH) {
                    TextButton(onClick = {
                        settings.resetSmartFilters()
                        settings.smartSortMode = "best"
                        settings.smartResolutionLimit = "auto"
                        bump()
                    }) { Text("Reset") }
                }
            }
            Text(
                text = "Scored by instant-ness, resolution vs your screen, bitrate, codec and language — not quality order alone.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
            if (best != null) {
                Text(
                    text = "Best match: ${best.info.summary("Instant")}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 12.dp)
                )
                val topReasons = best.reasons.take(2)
                if (topReasons.isNotEmpty()) {
                    Text(
                        text = topReasons.joinToString(" · ") { SmartStreamRanking.reasonText(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "Showing $shownCount of ${streams.size} streams",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(top = 12.dp)
            )

            Text(
                text = "Sort",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    "best" to SmartSortMode.BEST_MATCH.label,
                    "quality" to SmartSortMode.HIGHEST_QUALITY.label,
                    "smallest" to SmartSortMode.SMALLEST_FILE.label,
                    "largest" to SmartSortMode.LARGEST_FILE.label,
                    "addon" to SmartSortMode.ADDON_ORDER.label,
                ).forEach { (value, label) ->
                    SmartChip(
                        label = label,
                        selected = settings.smartSortMode == value,
                        onClick = { settings.smartSortMode = value; bump() }
                    )
                }
            }

            Text(
                text = "Resolution limit",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("auto" to "Auto", "1080" to "1080p", "4k" to "4K").forEach { (value, label) ->
                    SmartChip(
                        label = label,
                        selected = settings.smartResolutionLimit == value,
                        onClick = { settings.smartResolutionLimit = value; bump() }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Save data on mobile (cap at 1080p)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.smartSaveDataOnMobile,
                    onCheckedChange = { settings.smartSaveDataOnMobile = it; bump() }
                )
            }

            SmartFilterGroup(
                title = "Resolution",
                options = listOf("4K", "1080p", "720p", "SD"),
                selected = settings.smartFilterResolutions,
                onToggle = { label ->
                    val cur = settings.smartFilterResolutions
                    settings.smartFilterResolutions = if (label in cur) cur - label else cur + label
                    bump()
                }
            )
            SmartFilterGroup(
                title = "HDR",
                options = listOf("DV", "HDR10", "SDR"),
                selected = settings.smartFilterHdr,
                onToggle = { label ->
                    val cur = settings.smartFilterHdr
                    settings.smartFilterHdr = if (label in cur) cur - label else cur + label
                    bump()
                }
            )
            SmartFilterGroup(
                title = "Codec",
                options = listOf("HEVC", "H264", "AV1", "OTHER"),
                selected = settings.smartFilterCodecs,
                onToggle = { label ->
                    val cur = settings.smartFilterCodecs
                    settings.smartFilterCodecs = if (label in cur) cur - label else cur + label
                    bump()
                }
            )
            SmartFilterGroup(
                title = "Audio",
                options = listOf("ATMOS", "DD", "DTS", "STEREO"),
                selected = settings.smartFilterAudio,
                onToggle = { label ->
                    val cur = settings.smartFilterAudio
                    settings.smartFilterAudio = if (label in cur) cur - label else cur + label
                    bump()
                }
            )
            SmartFilterGroup(
                title = "Source",
                options = listOf("REMUX", "BLURAY", "WEBDL", "WEBRIP", "HDTV"),
                selected = settings.smartFilterSources,
                onToggle = { label ->
                    val cur = settings.smartFilterSources
                    settings.smartFilterSources = if (label in cur) cur - label else cur + label
                    bump()
                }
            )

            Text(
                text = "Max file size",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0 to "No limit", 2 to "2 GB", 5 to "5 GB", 10 to "10 GB", 20 to "20 GB")
                    .forEach { (value, label) ->
                        SmartChip(
                            label = label,
                            selected = settings.smartFilterMaxSizeGb == value,
                            onClick = { settings.smartFilterMaxSizeGb = value; bump() }
                        )
                    }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Instant only",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.smartFilterInstantOnly,
                    onCheckedChange = { settings.smartFilterInstantOnly = it; bump() }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hide low quality (CAM / screener)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = settings.smartFilterHideLowQuality,
                    onCheckedChange = { settings.smartFilterHideLowQuality = it; bump() }
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
