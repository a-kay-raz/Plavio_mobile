package com.aviostream.app.data

import android.content.Context

/**
 * Per-title subtitle style overrides (ported from upstream v0.14.30, #27).
 *
 * The subtitle style used to live in TWO homes writing the SAME global
 * keys: the Settings → Subtitles style dialog and the in-player Style
 * rail. An in-player tweak for one title silently re-styled every
 * other title and fought the Settings page.
 *
 * The fix: Settings stays the ONLY home of the global defaults; the
 * in-player rail now writes a PER-TITLE override held here, seeded
 * from the defaults. Rendering resolves effective = override field ?:
 * global default, per field. The rail's Reset clears only that
 * title's override.
 *
 * Fields mirror the SettingsStore subtitle style set exactly:
 * font size (sp), text colour (ARGB), background colour (ARGB),
 * bold, edge type, edge colour (ARGB), bottom offset, font family.
 * A null field means "inherit the global default".
 *
 * Fork note: font family is the fork's subtitle font id (String:
 * "default" | "sans" | "serif" | "mono" | "custom-<uuid>"), not
 * upstream's Int family index — so custom fonts survive per-title.
 */
data class SubtitleStyleOverride(
    val fontSizeSp: Int? = null,
    val textColor: Int? = null,
    val backgroundColor: Int? = null,
    val bold: Boolean? = null,
    val edgeType: Int? = null,
    val edgeColor: Int? = null,
    val bottomOffset: Int? = null,
    val fontFamily: String? = null
) {
    val isEmpty: Boolean
        get() = fontSizeSp == null && textColor == null &&
            backgroundColor == null && bold == null && edgeType == null &&
            edgeColor == null && bottomOffset == null && fontFamily == null

    /** Compact `k=v;k=v` encoding — pure Kotlin so it unit-tests without Android. */
    fun encode(): String {
        val parts = ArrayList<String>(8)
        fontSizeSp?.let { parts += "fs=$it" }
        textColor?.let { parts += "tc=$it" }
        backgroundColor?.let { parts += "bg=$it" }
        bold?.let { parts += "bd=${if (it) 1 else 0}" }
        edgeType?.let { parts += "et=$it" }
        edgeColor?.let { parts += "ec=$it" }
        bottomOffset?.let { parts += "bo=$it" }
        fontFamily?.let { parts += "ff=$it" }
        return parts.joinToString(";")
    }

    companion object {
        val EMPTY = SubtitleStyleOverride()

        /** Tolerant decode: unknown keys and bad values are skipped. */
        fun decode(raw: String?): SubtitleStyleOverride {
            if (raw.isNullOrBlank()) return EMPTY
            var out = EMPTY
            for (part in raw.split(';')) {
                val eq = part.indexOf('=')
                if (eq <= 0) continue
                val key = part.substring(0, eq)
                val value = part.substring(eq + 1)
                val intVal = value.toIntOrNull()
                out = when (key) {
                    "fs" -> if (intVal != null) out.copy(fontSizeSp = intVal) else out
                    "tc" -> if (intVal != null) out.copy(textColor = intVal) else out
                    "bg" -> if (intVal != null) out.copy(backgroundColor = intVal) else out
                    "bd" -> out.copy(bold = value == "1")
                    "et" -> if (intVal != null) out.copy(edgeType = intVal) else out
                    "ec" -> if (intVal != null) out.copy(edgeColor = intVal) else out
                    "bo" -> if (intVal != null) out.copy(bottomOffset = intVal) else out
                    "ff" -> if (value.isNotBlank()) out.copy(fontFamily = value) else out
                    else -> out
                }
            }
            return out
        }
    }
}

/** The eight style values with every field resolved (no nulls). */
data class SubtitleStyleValues(
    val fontSizeSp: Int,
    val textColor: Int,
    val backgroundColor: Int,
    val bold: Boolean,
    val edgeType: Int,
    val edgeColor: Int,
    val bottomOffset: Int,
    val fontFamily: String
) {
    companion object {
        /** Snapshot of the global defaults held by [SettingsStore]. */
        fun fromGlobals(settings: SettingsStore): SubtitleStyleValues =
            SubtitleStyleValues(
                fontSizeSp = settings.subtitleFontSizeSp,
                textColor = settings.subtitleTextColor,
                backgroundColor = settings.subtitleBackgroundColor,
                bold = settings.subtitleBold,
                edgeType = settings.subtitleEdgeType,
                edgeColor = settings.subtitleEdgeColor,
                bottomOffset = settings.subtitleBottomOffset,
                fontFamily = settings.subtitleFontId
            )
    }
}

/** effective = the override's field where set, else the global default. */
fun SubtitleStyleOverride.resolve(globals: SubtitleStyleValues): SubtitleStyleValues =
    SubtitleStyleValues(
        fontSizeSp = fontSizeSp ?: globals.fontSizeSp,
        textColor = textColor ?: globals.textColor,
        backgroundColor = backgroundColor ?: globals.backgroundColor,
        bold = bold ?: globals.bold,
        edgeType = edgeType ?: globals.edgeType,
        edgeColor = edgeColor ?: globals.edgeColor,
        bottomOffset = bottomOffset ?: globals.bottomOffset,
        fontFamily = fontFamily ?: globals.fontFamily
    )

/** SharedPreferences-backed store for the per-title overrides, keyed by video id. */
object SubtitleStyleOverrides {
    private const val PREFS = "avio_subtitle_style_overrides"

    fun load(context: Context, videoId: String): SubtitleStyleOverride {
        if (videoId.isBlank()) return SubtitleStyleOverride.EMPTY
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(videoId, null)
        return SubtitleStyleOverride.decode(raw)
    }

    /** An empty override removes the title's entry (back to pure defaults). */
    fun save(context: Context, videoId: String, override: SubtitleStyleOverride) {
        if (videoId.isBlank()) return
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (override.isEmpty) edit.remove(videoId) else edit.putString(videoId, override.encode())
        edit.apply()
    }

    fun clear(context: Context, videoId: String) = save(context, videoId, SubtitleStyleOverride.EMPTY)
}
