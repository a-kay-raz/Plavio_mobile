package com.pulsestream.app.data

/**
 * Step 44 — Debrid stream preferences + templates (Nuvio debrid/
 * DebridSettings.kt stream prefs, DebridStreamFormatter.kt,
 * DebridStreamFormatterDefaults.kt, DebridStreamTemplateEngine.kt,
 * DebridStreamPresentation.kt — scoped port).
 *
 * Nuvio's full model is far larger (preferred/required/excluded lists for
 * resolutions, qualities, visual tags, audio tags, channels, encodes,
 * languages, release groups, plus a `{path::filter[...]}` template engine).
 * Avio ports the user-visible core on top of the Step 42 Smart pipeline:
 * min quality, DV/HDR/codec filters, preferred/required/excluded tag
 * substrings, and a small `{key}` / `{key||fallback}` template engine.
 *
 * Filters apply to the whole stream list (Avio has one shared list;
 * Nuvio scopes them to managed debrid streams). Templates rename only
 * debrid-resolved rows (sourceName starts with the "⚡" tag the resolver
 * adds); other rows keep their addon name.
 */

data class DebridStreamFilterPrefs(
    val minQualityLines: Int = 0,
    val dvFilter: String = "any",
    val hdrFilter: String = "any",
    val codecFilter: String = "any",
    val preferredTags: Set<String> = emptySet(),
    val requiredTags: Set<String> = emptySet(),
    val excludedTags: Set<String> = emptySet(),
)

/** A stream whose link came from a debrid resolver (tag added in StreamRepository). */
fun isDebridResolvedStream(stream: StreamItem): Boolean =
    stream.sourceName?.startsWith("⚡") == true

/** Short service name for templates: "⚡ TorBox" → "TB", "⚡ PM" → "PM", "⚡ RD" → "RD". */
fun debridServiceShortName(stream: StreamItem): String {
    val tag = stream.sourceName?.substringBefore(" · ")?.trim().orEmpty()
    return when {
        tag.contains("TorBox", ignoreCase = true) -> "TB"
        tag.contains("PM", ignoreCase = true) -> "PM"
        tag.contains("RD", ignoreCase = true) -> "RD"
        tag.startsWith("⚡") -> tag.removePrefix("⚡").trim()
        else -> ""
    }
}

private fun streamTagHaystack(stream: StreamItem): String =
    listOfNotNull(stream.title, stream.name, stream.sourceName)
        .joinToString(" ").lowercase()

/** Does this stream pass the debrid stream filters? */
fun matchesDebridStreamPrefs(
    stream: StreamItem,
    info: SmartStreamInfo,
    prefs: DebridStreamFilterPrefs,
): Boolean {
    if (prefs.minQualityLines > 0) {
        // Unknown resolution fails a minimum, like Nuvio's UNKNOWN resolution.
        val lines = info.resolution?.lines ?: 0
        if (lines < prefs.minQualityLines) return false
    }
    when (prefs.dvFilter) {
        "only" -> if (info.hdr != SmartHdr.DOLBY_VISION) return false
        "exclude" -> if (info.hdr == SmartHdr.DOLBY_VISION) return false
    }
    when (prefs.hdrFilter) {
        "only" -> if (info.hdr == null) return false
        "exclude" -> if (info.hdr != null) return false
    }
    when (prefs.codecFilter) {
        "h264" -> if (info.codec != SmartCodec.H264) return false
        "hevc" -> if (info.codec != SmartCodec.HEVC) return false
        "av1" -> if (info.codec != SmartCodec.AV1) return false
    }
    val haystack = streamTagHaystack(stream)
    if (prefs.requiredTags.isNotEmpty()) {
        if (prefs.requiredTags.any { it !in haystack }) return false
    }
    if (prefs.excludedTags.isNotEmpty()) {
        if (prefs.excludedTags.any { it in haystack }) return false
    }
    return true
}

/** Soft boost: a stream containing any preferred tag sorts ahead of the rest. */
fun hasDebridPreferredTag(stream: StreamItem, prefs: DebridStreamFilterPrefs): Boolean {
    if (prefs.preferredTags.isEmpty()) return false
    val haystack = streamTagHaystack(stream)
    return prefs.preferredTags.any { it in haystack }
}

/**
 * Minimal template engine: `{key}` replaced by the stream's value, empty
 * when unknown; `{key||fallback}` uses the fallback when the value is empty.
 * Supported keys: resolution, quality, codec, hdr, audio, source, size,
 * service, title, name, addon. (Nuvio's full `::filter["a"||"b"]` conditional
 * syntax is not ported.)
 */
fun formatDebridStreamText(template: String, stream: StreamItem, info: SmartStreamInfo): String {
    if (template.isBlank()) return ""
    val values = mapOf(
        "resolution" to (info.resolution?.label ?: ""),
        "quality" to (info.resolution?.label ?: ""),
        "codec" to when (info.codec) {
            SmartCodec.HEVC -> "HEVC"
            SmartCodec.H264 -> "H.264"
            SmartCodec.AV1 -> "AV1"
            SmartCodec.OTHER -> "Other"
            null -> ""
        },
        "hdr" to when (info.hdr) {
            SmartHdr.DOLBY_VISION -> "DV"
            SmartHdr.HDR10 -> "HDR10"
            SmartHdr.HDR10_PLUS -> "HDR10+"
            null -> ""
        },
        "audio" to info.audio.joinToString(" | ") { it.label },
        "source" to (info.source?.label ?: ""),
        "size" to (info.sizeBytes?.let(::formatSmartSize) ?: ""),
        "service" to debridServiceShortName(stream),
        "title" to (stream.title ?: ""),
        "name" to (stream.name ?: ""),
        "addon" to (stream.sourceName ?: ""),
    )
    val placeholder = Regex("\\{([a-zA-Z]+)(?:\\|\\|([^}]*))?\\}")
    val rendered = placeholder.replace(template) { match ->
        val key = match.groupValues[1].lowercase()
        val fallback = match.groupValues[2]
        val value = values[key].orEmpty()
        if (value.isNotEmpty()) value else fallback
    }
    return rendered.replace(Regex("\\s+"), " ").trim()
}

/** Formatted headline for a debrid-resolved row, or null when no name template is set. */
fun debridFormattedName(stream: StreamItem, settings: SettingsStore): String? {
    val template = settings.debridNameTemplate
    if (template.isBlank() || !isDebridResolvedStream(stream)) return null
    return formatDebridStreamText(template, stream, SmartStreamQuality.cached(stream))
        .takeIf { it.isNotBlank() }
}

/** Formatted description line for a debrid-resolved row, or null when no description template is set. */
fun debridFormattedDescription(stream: StreamItem, settings: SettingsStore): String? {
    val template = settings.debridDescriptionTemplate
    if (template.isBlank() || !isDebridResolvedStream(stream)) return null
    return formatDebridStreamText(template, stream, SmartStreamQuality.cached(stream))
        .takeIf { it.isNotBlank() }
}
