package com.aviostream.app.ui.screens.player

import com.aviostream.app.data.SegmentType
import com.aviostream.app.data.SkipSegment

/** A chapter of the playing file, as its container lists it. */
data class PlayerChapter(
    val startMs: Long,
    val title: String?,
)

object PlayerChapters {
    const val PREVIOUS_RESTART_THRESHOLD_MS = 3_000L

    fun normalize(chapters: List<PlayerChapter>): List<PlayerChapter> {
        val sorted = chapters.filter { it.startMs >= 0L }.sortedBy { it.startMs }.distinctBy { it.startMs }
        return if (sorted.size < 2) emptyList() else sorted
    }

    fun indexAt(chapters: List<PlayerChapter>, positionMs: Long): Int =
        chapters.indexOfLast { it.startMs <= positionMs }

    fun nextStartMs(chapters: List<PlayerChapter>, positionMs: Long): Long? =
        chapters.firstOrNull { it.startMs > positionMs }?.startMs

    fun previousStartMs(chapters: List<PlayerChapter>, positionMs: Long): Long? {
        val index = indexAt(chapters, positionMs)
        if (index < 0) return null
        val current = chapters[index].startMs
        if (positionMs - current > PREVIOUS_RESTART_THRESHOLD_MS || index == 0) return current
        return chapters[index - 1].startMs
    }
}

fun List<PlayerChapter>.currentChapterIndex(positionMs: Long): Int {
    var index = -1
    for (i in indices) {
        if (this[i].startMs <= positionMs) index = i else break
    }
    return index
}

/** Seekbar tick positions for the chapters (0..1 fractions, first chapter excluded). */
fun List<PlayerChapter>.chapterMarkFractions(durationMs: Long): List<Float> {
    if (durationMs <= 0L) return emptyList()
    return asSequence()
        .map { it.startMs }
        .filter { it > 0L && it < durationMs }
        .map { (it.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }
        .toList()
}

/**
 * Derive skip intervals from container chapters (MKV/Matroska).
 *
 * Chapter titles are matched case-insensitively. Only titles that are essentially
 * the segment label itself (with an optional number suffix) are matched, to avoid
 * false positives on scene-named chapters like "The Opening" or "Alternate Ending".
 *
 * Matched types: OP/Opening, ED/Ending/Credits, Recap/Previously, Intro, Post-Credits.
 * "Intro" is promoted to opening only when no dedicated OP/Opening chapter exists.
 */
object ChapterSkipIntervals {

    private const val MIN_DURATION_SEC = 5.0
    private const val MAX_DURATION_SEC = 240.0 // 4 minutes max for OP/ED/recap

    private val OPENING_PATTERN = Regex("^\\s*(op(ening)?)(\\s*(credits|theme))?\\s*\\d*\\s*$", RegexOption.IGNORE_CASE)
    private val ENDING_PATTERN = Regex("^\\s*(ed|ending)(\\s*(credits|theme))?\\s*\\d*\\s*$", RegexOption.IGNORE_CASE)
    private val CREDITS_PATTERN = Regex("^\\s*(end|closing)?\\s*credits\\s*$", RegexOption.IGNORE_CASE)
    private val RECAP_PATTERN = Regex("^\\s*(recap|previously(\\s+on.*)?)\\s*$", RegexOption.IGNORE_CASE)
    private val INTRO_PATTERN = Regex("^\\s*intro\\s*$", RegexOption.IGNORE_CASE)

    private val POST_CREDITS_PATTERN = Regex("(post|mid|after)[\\s-]credits", RegexOption.IGNORE_CASE)

    fun fromChapters(
        chapters: List<PlayerChapter>,
        durationMs: Long
    ): List<SkipSegment> {
        if (chapters.size < 2 || durationMs <= 0L) return emptyList()

        val classified = chapters.mapIndexed { index, chapter ->
            val endMs = if (index + 1 < chapters.size) {
                chapters[index + 1].startMs
            } else {
                durationMs
            }
            ClassifiedChapter(
                chapter = chapter,
                endMs = endMs,
                type = classifyTitle(chapter.title)
            )
        }

        val hasExplicitOpening = classified.any { it.type == ChapterType.OPENING }
        val resolved = if (hasExplicitOpening) {
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = null) else c
            }
        } else {
            classified.map { c ->
                if (c.type == ChapterType.INTRO) c.copy(type = ChapterType.OPENING) else c
            }
        }

        return resolved.mapNotNull { c ->
            val segmentType = c.type?.segmentType ?: return@mapNotNull null
            val startSec = c.chapter.startMs / 1000.0
            val endSec = c.endMs / 1000.0
            val duration = endSec - startSec
            if (duration < MIN_DURATION_SEC) return@mapNotNull null
            if (c.type != ChapterType.POST_CREDITS && duration > MAX_DURATION_SEC) return@mapNotNull null
            SkipSegment(
                type = segmentType,
                startMs = c.chapter.startMs,
                endMs = c.endMs
            )
        }
    }

    private fun classifyTitle(title: String?): ChapterType? {
        if (title.isNullOrBlank()) return null
        if (POST_CREDITS_PATTERN.containsMatchIn(title)) return ChapterType.POST_CREDITS
        if (OPENING_PATTERN.matches(title)) return ChapterType.OPENING
        if (ENDING_PATTERN.matches(title)) return ChapterType.ENDING
        if (CREDITS_PATTERN.matches(title)) return ChapterType.ENDING
        if (RECAP_PATTERN.matches(title)) return ChapterType.RECAP
        if (INTRO_PATTERN.matches(title)) return ChapterType.INTRO
        return null
    }

    private enum class ChapterType(val segmentType: SegmentType) {
        OPENING(SegmentType.INTRO),
        ENDING(SegmentType.OUTRO),
        RECAP(SegmentType.RECAP),
        INTRO(SegmentType.INTRO),
        POST_CREDITS(SegmentType.POST_CREDITS),
    }

    private data class ClassifiedChapter(
        val chapter: PlayerChapter,
        val endMs: Long,
        val type: ChapterType?
    )
}

/**
 * Merge chapter intervals (higher priority) with IntroDB intervals (lower priority).
 * Per segment type, chapters win; IntroDB fills gaps. For movies, IntroDB takes full
 * priority when it has any data, because it provides movie-credits + post-credits
 * with proper trimming.
 */
fun mergeSkipIntervals(
    apiIntervals: List<SkipSegment>,
    chapterIntervals: List<SkipSegment>,
    isMovie: Boolean = false
): List<SkipSegment> {
    if (chapterIntervals.isEmpty()) return apiIntervals
    if (apiIntervals.isEmpty()) return chapterIntervals
    if (isMovie && apiIntervals.isNotEmpty()) return apiIntervals
    val chosen = LinkedHashMap<SegmentType, SkipSegment>()
    for (interval in chapterIntervals) {
        chosen.getOrPut(interval.type) { interval }
    }
    for (interval in apiIntervals) {
        chosen.getOrPut(interval.type) { interval }
    }
    return chosen.values.toList()
}
