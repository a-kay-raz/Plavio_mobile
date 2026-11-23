package com.aviostream.app.data

import android.content.Context
import android.graphics.Typeface
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Step 38 — custom subtitle fonts (Nuvio features/player/SubtitleFonts.kt,
 * ported to Avio's file layout; org.json replaces the serialization plugin
 * Avio does not apply).
 *
 * Built-in ids: "default" | "sans" | "serif" | "mono". Imported fonts are
 * stored as files under filesDir/subtitle-fonts/ with a fonts.json registry;
 * ids are "custom-<uuid>". Only ttf/otf, ≤10 MB, with a valid TrueType /
 * OpenType header + table directory, are accepted.
 */
const val MaxSubtitleFontBytes = 10 * 1024 * 1024

val BuiltInSubtitleFonts = listOf("default", "sans", "serif", "mono")

data class SubtitleFont(val id: String, val name: String)

fun subtitleFontLabel(id: String, fonts: List<SubtitleFont>? = null): String = when (id) {
    "sans" -> "Sans"
    "serif" -> "Serif"
    "mono" -> "Monospace"
    "default" -> "Default"
    else -> fonts?.find { it.id == id }?.name ?: "Default"
}

private fun subtitleFontsDir(context: Context): File =
    File(context.filesDir, "subtitle-fonts").also { if (!it.exists()) it.mkdirs() }

private fun subtitleFontFile(context: Context, id: String): File =
    File(subtitleFontsDir(context), id)

object SubtitleFonts {
    fun installed(context: Context): List<SubtitleFont> = runCatching {
        val registry = subtitleFontFile(context, "fonts.json")
        if (!registry.exists()) return emptyList()
        val json = JSONArray(registry.readText())
        buildList {
            for (i in 0 until json.length()) {
                val obj = json.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                if (id.matches(Regex("custom-[a-f0-9-]{36}")) && name.isNotBlank()) {
                    add(SubtitleFont(id, name))
                }
            }
        }
    }.getOrDefault(emptyList())

    /**
     * Import font bytes. Returns the existing entry when identical bytes
     * were already imported (dedupe). Throws IllegalArgumentException when
     * the file is not a supported font, or when the bytes cannot be loaded
     * as a typeface.
     */
    fun import(context: Context, name: String, bytes: ByteArray): SubtitleFont {
        require(isSupportedSubtitleFont(name, bytes)) { "Not a supported font file" }
        val fonts = installed(context)
        fonts.firstOrNull {
            subtitleFontFile(context, it.id).takeIf { f -> f.exists() }
                ?.readBytes()?.contentEquals(bytes) == true
        }?.let { return it }
        val font = SubtitleFont(
            "custom-${UUID.randomUUID()}",
            name.substringBeforeLast('.').take(80).ifBlank { "Custom font" },
        )
        val file = subtitleFontFile(context, font.id)
        file.writeBytes(bytes)
        try {
            requireNotNull(Typeface.createFromFile(file)) { "Font could not be loaded" }
            val registry = subtitleFontFile(context, "fonts.json")
            val json = JSONArray()
            (fonts + font).forEach { f ->
                json.put(JSONObject().put("id", f.id).put("name", f.name))
            }
            registry.writeText(json.toString())
        } catch (error: Exception) {
            file.delete()
            throw error
        }
        return font
    }

    fun remove(context: Context, id: String) {
        val remaining = installed(context).filterNot { it.id == id }
        val registry = subtitleFontFile(context, "fonts.json")
        val json = JSONArray()
        remaining.forEach { f -> json.put(JSONObject().put("id", f.id).put("name", f.name)) }
        registry.writeText(json.toString())
        subtitleFontFile(context, id).delete()
    }
}

fun isSupportedSubtitleFont(name: String, bytes: ByteArray): Boolean {
    if (name.substringAfterLast('.', "").lowercase() !in setOf("ttf", "otf")) return false
    if (bytes.size !in 12..MaxSubtitleFontBytes) return false
    val signature = bytes.take(4).map { it.toInt() and 255 }
    if (signature != listOf(0, 1, 0, 0) && bytes.decodeToString(0, 4) != "OTTO") return false
    fun uint16(offset: Int) = ((bytes[offset].toInt() and 255) shl 8) or (bytes[offset + 1].toInt() and 255)
    fun uint32(offset: Int): Long = (0..3).fold(0L) { value, i ->
        (value shl 8) or (bytes[offset + i].toLong() and 255)
    }
    val tables = uint16(4)
    if (tables == 0 || 12L + tables * 16L > bytes.size) return false
    return (0 until tables).all { index ->
        val offset = 12 + index * 16
        val start = uint32(offset + 8)
        val length = uint32(offset + 12)
        start <= bytes.size && length <= bytes.size - start
    }
}

/**
 * The android.graphics.Typeface for a subtitle font id, for
 * CaptionStyleCompat's typeface slot. Built-ins map to the platform faces;
 * a custom id loads its file (null when the file is gone or unreadable, so
 * the caller falls back to DEFAULT).
 */
fun subtitleTypeface(context: Context, id: String): Typeface? = when (id) {
    "serif" -> Typeface.SERIF
    "mono" -> Typeface.MONOSPACE
    "sans" -> Typeface.SANS_SERIF
    "default" -> Typeface.DEFAULT
    else -> runCatching {
        subtitleFontFile(context, id).takeIf { it.exists() }?.let { Typeface.createFromFile(it) }
    }.getOrNull()
}
