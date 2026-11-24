@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aviostream.app.data

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.io.File

/**
 * Sidecar subtitle engine (ported from upstream v0.14.30, #26):
 *
 * Media3 can only add a [androidx.media3.common.MediaItem.SubtitleConfiguration]
 * track by reloading the media source (setMediaItem + prepare),
 * which wipes the playback buffer — and a side-loaded track whose
 * format was guessed wrong from its URL never produces a cue at
 * all. So addon subtitles do NOT ride the media item here. The
 * picked subtitle is downloaded, parsed OFF the player with
 * Media3's own [DefaultSubtitleParserFactory] (content-sniffed
 * format first, then the URL hint, then every common text format,
 * then a lenient SRT/VTT fallback), and the render loop pushes the
 * currently-active cues straight into the PlayerView's SubtitleView
 * every 100 ms. The video's media period is never touched: no
 * re-buffer, no restart, subtitle delay is a render offset (not a
 * file rewrite), and a failed subtitle can never take playback
 * down with it.
 *
 * ASS/SSA renders as plain cues through the same path (Media3's
 * SsaParser) — the mobile player has no libass engine.
 */

/** One lenient-parser cue (the sync-cue shape). */
internal data class SidecarCue(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String,
)

internal data class SidecarParseResult(
    val cues: List<CuesWithTiming>,
    val effectiveMime: String,
    /** "media3", "lenient" or "none" — which parser produced the cues. */
    val source: String,
)

internal object AvioSidecarSubtitles {
    private val parserFactory = DefaultSubtitleParserFactory()

    /** How often the render loop refreshes the on-screen cues. */
    const val RENDER_INTERVAL_MS = 100L

    /**
     * Whether [subtitle] can be hot-attached without a media reload.
     * ASS/SSA are excluded when [keepAssOnMediaPath] (a libass
     * engine is active for this title): those stay on the
     * media-source path so the libass renderer keeps full styling.
     * Mobile has no libass, so callers pass false.
     */
    fun canAttachViaSidecar(
        subtitle: Subtitle,
        keepAssOnMediaPath: Boolean,
    ): Boolean {
        val mime = mimeTypeFromUrl(subtitle.url)
        if (mime == MimeTypes.TEXT_SSA && keepAssOnMediaPath) return false
        val format = Format.Builder().setSampleMimeType(mime).build()
        return parserFactory.supportsFormat(format)
    }

    /** Loads and parses a resolved (downloaded) subtitle file. */
    fun loadCues(resolved: ResolvedSubtitle): SidecarParseResult {
        val rawText = try {
            resolved.file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.d("AvioSidecar", "read failed ${resolved.sub.url}: ${e.message}")
            return SidecarParseResult(emptyList(), resolved.mimeType, source = "none")
        }
        return parseTimedCuesRobust(rawText, resolved.sub.url)
    }

    /**
     * The URL-extension mime guess. NEVER returns null — an unknown
     * extension defaults to SubRip. A null mime is exactly what
     * leaves extension-less addon tracks listed but cue-less.
     */
    fun mimeTypeFromUrl(url: String): String {
        val normalizedPath =
            url
                .substringBefore('#')
                .substringBefore('?')
                .trimEnd('/')
                .lowercase()
        return when {
            normalizedPath.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            normalizedPath.endsWith(".vtt") || normalizedPath.endsWith(".webvtt") -> MimeTypes.TEXT_VTT
            normalizedPath.endsWith(".ass") || normalizedPath.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            normalizedPath.endsWith(".ttml") || normalizedPath.endsWith(".dfxp") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    /**
     * Sniffs the subtitle format from the body content, falling back
     * to [mimeTypeFromUrl] when ambiguous. Addon URLs often omit
     * extensions (`/download/12345`), so URL-only mime is frequently
     * wrong.
     */
    fun sniffSubtitleMimeType(
        rawText: String,
        sourceUrl: String = "",
    ): String {
        val text = rawText.replace("\uFEFF", "").trimStart()
        if (text.isEmpty()) return mimeTypeFromUrl(sourceUrl)
        if (text.startsWith("WEBVTT", ignoreCase = true)) {
            return MimeTypes.TEXT_VTT
        }
        val head = text.take(4_000)
        if (head.startsWith("[Script Info]", ignoreCase = true) ||
            head.contains("[V4+ Styles]", ignoreCase = true) ||
            head.contains("[V4 Styles]", ignoreCase = true) ||
            Regex("""(?im)^\s*Dialogue:""").containsMatchIn(head)
        ) {
            return MimeTypes.TEXT_SSA
        }
        val lowerHead = head.lowercase()
        if ((lowerHead.startsWith("<?xml") || lowerHead.contains("<tt ")) &&
            (lowerHead.contains("ttml") || lowerHead.contains(":tt") || lowerHead.contains("<tt "))
        ) {
            return MimeTypes.APPLICATION_TTML
        }
        if (Regex(
                """(?m)^\d+\s*\r?\n\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""",
            ).containsMatchIn(text.take(800)) ||
            Regex(
                """(?m)^\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}\s*-->\s*\d{1,2}:\d{2}:\d{2}[,.]\d{1,3}""",
            ).containsMatchIn(text.take(400))
        ) {
            return MimeTypes.APPLICATION_SUBRIP
        }
        return mimeTypeFromUrl(sourceUrl)
    }

    /**
     * Ordered mime candidates for robust parsing: sniffed content
     * first, then the URL hint, then the common text formats.
     */
    fun sidecarMimeCandidates(
        rawText: String,
        sourceUrl: String,
    ): List<String> {
        val sniffed = sniffSubtitleMimeType(rawText, sourceUrl)
        val fromUrl = mimeTypeFromUrl(sourceUrl)
        return linkedSetOf(
            sniffed,
            fromUrl,
            MimeTypes.APPLICATION_SUBRIP,
            MimeTypes.TEXT_VTT,
            MimeTypes.TEXT_SSA,
            MimeTypes.APPLICATION_TTML,
        ).toList()
    }

    /**
     * Tries Media3 parsers across the sniffed + common mime types,
     * then the lenient SRT/VTT fallback. An empty result means the
     * file could not be read as any known subtitle format — the
     * caller deactivates the sidecar quietly and NEVER falls back
     * to a media reload (buffer preservation is the point).
     */
    fun parseTimedCuesRobust(
        rawText: String,
        sourceUrl: String,
    ): SidecarParseResult {
        val cleaned = rawText.replace("\uFEFF", "")
        val candidates = sidecarMimeCandidates(cleaned, sourceUrl)
        for (mime in candidates) {
            val parsed = parseTimedCuesWithMime(cleaned, mime)
            if (parsed.isNotEmpty()) {
                val normalized =
                    if (mime == MimeTypes.TEXT_VTT) normalizeTimedCuePositions(parsed) else parsed
                return SidecarParseResult(normalized, mime, source = "media3")
            }
        }
        val lenient = parseTimedCuesLenient(cleaned, sourceUrl)
        if (lenient.isNotEmpty()) {
            val mime = sniffSubtitleMimeType(cleaned, sourceUrl)
            val normalized =
                if (mime == MimeTypes.TEXT_VTT) normalizeTimedCuePositions(lenient) else lenient
            return SidecarParseResult(normalized, mime, source = "lenient")
        }
        return SidecarParseResult(
            cues = emptyList(),
            effectiveMime = candidates.firstOrNull() ?: MimeTypes.APPLICATION_SUBRIP,
            source = "none",
        )
    }

    private fun parseTimedCuesWithMime(
        rawText: String,
        mimeType: String,
    ): List<CuesWithTiming> {
        val format = Format.Builder().setSampleMimeType(mimeType).build()
        if (!parserFactory.supportsFormat(format)) return emptyList()
        return try {
            val parser = parserFactory.create(format)
            val data = rawText.toByteArray(Charsets.UTF_8)
            val out = ArrayList<CuesWithTiming>(256)
            parser.parse(data, SubtitleParser.OutputOptions.allCues()) { cueGroup ->
                if (cueGroup.startTimeUs != C.TIME_UNSET) {
                    out.add(cueGroup)
                }
            }
            out.sortBy { it.startTimeUs }
            out
        } catch (e: Exception) {
            Log.d("AvioSidecar", "parse failed mime=$mimeType: ${e.message}")
            emptyList()
        }
    }

    /**
     * Lenient SRT/VTT path using [AvioSidecarCueParser] when
     * Media3 rejects a slightly malformed file. Uses each cue's
     * exact end time (not stretched to the next cue's start).
     */
    private fun parseTimedCuesLenient(
        rawText: String,
        sourceUrl: String,
    ): List<CuesWithTiming> {
        val syncCues =
            try {
                AvioSidecarCueParser
                    .parseFromText(rawText, sourceUrl)
                    .filter { it.text.isNotBlank() && it.startTimeMs >= 0L }
            } catch (_: Exception) {
                emptyList()
            }
        if (syncCues.isEmpty()) return emptyList()
        val out = ArrayList<CuesWithTiming>(syncCues.size)
        for (cue in syncCues) {
            val startUs = cue.startTimeMs * 1_000L
            val endUs = (cue.endTimeMs * 1_000L).coerceAtLeast(startUs + 1L)
            val durationUs = (endUs - startUs).coerceAtLeast(1L)
            val textCue = Cue.Builder().setText(cue.text).build()
            out.add(CuesWithTiming(listOf(textCue), startUs, durationUs))
        }
        // collectActiveCues stops at the first cue starting after
        // the playhead, so the list has to be ordered.
        out.sortBy { it.startTimeUs }
        return out
    }

    private fun normalizeTimedCuePositions(cues: List<CuesWithTiming>): List<CuesWithTiming> =
        cues.map { entry ->
            val normalized = entry.cues.map { normalizeBottomCuePosition(it) }
            val durationUs =
                when {
                    entry.durationUs != C.TIME_UNSET -> entry.durationUs
                    entry.endTimeUs != C.TIME_UNSET && entry.startTimeUs != C.TIME_UNSET ->
                        (entry.endTimeUs - entry.startTimeUs).coerceAtLeast(1L)
                    else -> C.TIME_UNSET
                }
            CuesWithTiming(normalized, entry.startTimeUs, durationUs)
        }

    /**
     * TEST v0.14.73 (item 4): the render-time playhead transform
     * for sidecar cues — the per-title OFFSET (ms) and DRIFT
     * (speed permille, 1000 = 1.000×) applied to the LOOKUP
     * position only. The stored cue list is never rewritten, so
     * corrections never compound (the pristine-cue rule).
     * Returns microseconds for [collectActiveCues].
     */
    fun scaledLookupPositionUs(
        positionMs: Long,
        offsetMs: Long,
        speedPermille: Int
    ): Long {
        val shifted = (positionMs - offsetMs).coerceAtLeast(0L)
        val scaled = shifted * speedPermille.coerceIn(900, 1100) / 1000L
        return scaled * 1000L
    }

    /** [cues] must be ordered by start time; the scan stops at the first later cue. */
    fun collectActiveCues(
        cues: List<CuesWithTiming>,
        positionUs: Long,
    ): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        val active = ArrayList<Cue>(4)
        for (entry in cues) {
            if (entry.startTimeUs > positionUs) break
            val end =
                when {
                    entry.endTimeUs != C.TIME_UNSET -> entry.endTimeUs
                    entry.durationUs != C.TIME_UNSET -> entry.startTimeUs + entry.durationUs
                    else -> Long.MAX_VALUE
                }
            if (positionUs < end) {
                active.addAll(entry.cues)
            }
        }
        return active
    }

    /** Change detector for the render loop: unchanged cues are never re-pushed. */
    fun activeCueSignature(cues: List<Cue>): Long {
        if (cues.isEmpty()) return EMPTY_CUE_SIGNATURE
        var hash = cues.size.toLong()
        for (cue in cues) {
            hash = 31L * hash + (cue.text?.hashCode()?.toLong() ?: 0L)
            hash = 31L * hash + cue.line.toBits().toLong()
            hash = 31L * hash + cue.position.toBits().toLong()
            hash = 31L * hash + cue.lineAnchor.toLong()
            hash = 31L * hash + cue.positionAnchor.toLong()
            hash = 31L * hash + cue.size.toBits().toLong()
        }
        return hash
    }

    /**
     * Merges simultaneously active unpositioned text cues into a
     * single multi-line cue, so the subtitle painter never draws
     * colliding lines on top of each other.
     */
    fun mergeOverlappingCues(cues: List<Cue>): List<Cue> {
        if (cues.size <= 1) return cues
        val unpositioned = cues.filter { it.bitmap == null && it.line == Cue.DIMEN_UNSET }
        if (unpositioned.size <= 1) return cues
        if (unpositioned.size < cues.size) {
            val positioned = cues.filterNot { it.bitmap == null && it.line == Cue.DIMEN_UNSET }
            return positioned + mergeOverlappingCues(unpositioned)
        }
        val validTexts = cues.mapNotNull { it.text }.filter { it.isNotBlank() }
        if (validTexts.isEmpty()) return emptyList()
        val hasSpanned = validTexts.any { it is Spanned }
        val mergedText: CharSequence =
            if (hasSpanned) {
                val builder = SpannableStringBuilder()
                for (i in validTexts.indices) {
                    if (i > 0) builder.append('\n')
                    builder.append(validTexts[i])
                }
                builder
            } else {
                validTexts.distinct().joinToString("\n")
            }
        return listOf(cues[0].buildUpon().setText(mergedText).build())
    }

    fun normalizeBottomCuePosition(cue: Cue): Cue {
        if (cue.bitmap != null || cue.verticalType != Cue.TYPE_UNSET || cue.line == Cue.DIMEN_UNSET) {
            return cue
        }
        val placedAwayFromBottom =
            when (cue.lineType) {
                Cue.LINE_TYPE_FRACTION -> cue.line < BOTTOM_CUE_LINE_FRACTION
                Cue.LINE_TYPE_NUMBER -> cue.line >= 0f
                else -> false
            }
        if (!placedAwayFromBottom) return cue
        return cue
            .buildUpon()
            .setLine(Cue.DIMEN_UNSET, Cue.TYPE_UNSET)
            .setLineAnchor(Cue.TYPE_UNSET)
            .build()
    }

    private const val BOTTOM_CUE_LINE_FRACTION = 0.6f
    private const val EMPTY_CUE_SIGNATURE = 0x4156494FL // "AVIO"
}

/**
 * Lenient SRT/VTT text parser (ported with the sidecar engine):
 * tolerates the slightly malformed files Media3's strict parsers
 * reject — missing indices, short timestamps, VTT metadata blocks.
 */
internal object AvioSidecarCueParser {
    private val timestampRegex = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})([.,](\d+))?""")

    fun parseFromText(
        rawText: String,
        sourceUrl: String,
    ): List<SidecarCue> {
        val cleanedText =
            rawText
                .replace("\uFEFF", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
        return if (looksLikeVtt(cleanedText, sourceUrl)) {
            parseVtt(cleanedText)
        } else {
            parseSrt(cleanedText)
        }
    }

    private fun looksLikeVtt(
        text: String,
        sourceUrl: String,
    ): Boolean {
        val normalizedUrl = sourceUrl.substringBefore('?').substringBefore('#').lowercase()
        if (normalizedUrl.endsWith(".vtt") || normalizedUrl.endsWith(".webvtt")) return true
        return text.trimStart().startsWith("WEBVTT")
    }

    private fun parseSrt(text: String): List<SidecarCue> {
        val blocks = text.split(Regex("""\n\s*\n"""))
        val cues = mutableListOf<SidecarCue>()
        for (block in blocks) {
            val lines =
                block
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            var index = 0
            if (lines[index].all { it.isDigit() } && index + 1 < lines.size) {
                index++
            }
            val timing = lines.getOrNull(index) ?: continue
            if (!timing.contains("-->")) continue
            val (startTimeMs, endTimeMs) = parseStartEndTimeMs(timing) ?: continue
            // Skip zero-duration cues (they cause desync after scene transitions).
            if (endTimeMs - startTimeMs <= 0) continue
            val cueText = normalizeCueText(lines.drop(index + 1).joinToString("\n"))
            if (cueText.isBlank()) continue
            cues += SidecarCue(startTimeMs = startTimeMs, endTimeMs = endTimeMs, text = cueText)
        }
        return cues
    }

    private fun parseVtt(text: String): List<SidecarCue> {
        val lines = text.lines().map { it.trimEnd() }
        val cues = mutableListOf<SidecarCue>()
        var cursor = 0
        while (cursor < lines.size) {
            val line = lines[cursor].trim()
            if (line.isBlank()) {
                cursor++
                continue
            }
            if (line.startsWith("WEBVTT")) {
                cursor++
                continue
            }
            // Only skip STYLE/REGION/NOTE when the next line is not cue timings.
            if (isWebVttMetadataBlockHeader(line)) {
                val nextLine = lines.getOrNull(cursor + 1)?.trim().orEmpty()
                if (nextLine.isEmpty() || !nextLine.contains("-->")) {
                    cursor = skipWebVttBlock(lines, cursor + 1)
                    continue
                }
            }
            var timingLine = line
            var textStart = cursor + 1
            if (!timingLine.contains("-->")) {
                timingLine = lines.getOrNull(cursor + 1)?.trim().orEmpty()
                textStart = cursor + 2
            }
            if (!timingLine.contains("-->")) {
                cursor++
                continue
            }
            val parsedTimes = parseStartEndTimeMs(timingLine)
            if (parsedTimes == null) {
                cursor++
                continue
            }
            val (startTimeMs, endTimeMs) = parsedTimes
            if (endTimeMs - startTimeMs <= 0) {
                cursor++
                continue
            }
            val textParts = mutableListOf<String>()
            var i = textStart
            while (i < lines.size && lines[i].isNotBlank()) {
                textParts += lines[i].trim()
                i++
            }
            val cueText = normalizeCueText(textParts.joinToString("\n"))
            if (cueText.isNotBlank()) {
                cues += SidecarCue(startTimeMs = startTimeMs, endTimeMs = endTimeMs, text = cueText)
            }
            cursor = i + 1
        }
        return cues
    }

    private fun isWebVttMetadataBlockHeader(line: String): Boolean =
        line == "STYLE" ||
            line == "REGION" ||
            line == "NOTE" ||
            line.startsWith("NOTE ") ||
            line.startsWith("NOTE\t")

    private fun skipWebVttBlock(
        lines: List<String>,
        start: Int,
    ): Int {
        var cursor = start
        while (cursor < lines.size && lines[cursor].isNotBlank()) {
            cursor++
        }
        return if (cursor < lines.size) cursor + 1 else cursor
    }

    private fun parseStartEndTimeMs(timingLine: String): Pair<Long, Long>? {
        val parts = timingLine.split("-->")
        if (parts.size != 2) return null
        val startTimeMs = parseTimestampMs(parts[0].trim().substringBefore(' ')) ?: return null
        val endTimeMs = parseTimestampMs(parts[1].trim().substringBefore(' ')) ?: return null
        return startTimeMs to endTimeMs
    }

    private fun parseTimestampMs(rawTimestamp: String): Long? {
        val match = timestampRegex.matchEntire(rawTimestamp.trim()) ?: return null
        val hours = match.groupValues[1].toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val millisRaw = match.groupValues[5]
        val millis =
            when (millisRaw.length) {
                0 -> 0L
                1 -> "${millisRaw}00".toLong()
                2 -> "${millisRaw}0".toLong()
                else -> millisRaw.take(3).toLongOrNull() ?: 0L
            }
        return ((hours * 3600L) + (minutes * 60L) + seconds) * 1000L + millis
    }

    private fun normalizeCueText(text: String): String =
        text
            .replace(Regex("""<(?:\d+:)?\d{1,2}:\d{2}(?:[.,]\d+)?>"""), "")
            .replace(Regex("""</?[a-zA-Z0-9._-]+(?: [^>]*)?>"""), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .lines()
            .map { it.replace(Regex("""[ \t]+"""), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
}
