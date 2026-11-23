package com.aviostream.app.ui.screens.player

import com.aviostream.app.data.SkipSegment
import com.aviostream.app.data.SegmentType
import com.aviostream.app.data.Video

/** Next-episode countdown length (Nuvio counts 3…2…1 after pre-resolving the next episode's sources). */
const val NEXT_EPISODE_COUNTDOWN_SECONDS = 3

/** Threshold mode for the next-episode card (Nuvio NextEpisodeThresholdMode). */
const val NEXT_EPISODE_MODE_PERCENTAGE = "percentage"
const val NEXT_EPISODE_MODE_MINUTES = "minutes"

/**
 * The next episode after [currentSeason]/[currentEpisode] in [episodes]
 * (sorted by season/episode). Shuffle mode picks a random other episode
 * instead — the same resolution the end-of-playback advance uses.
 */
fun resolveNextEpisode(
    episodes: List<Video>,
    currentSeason: Int,
    currentEpisode: Int,
    currentVideoId: String,
    shuffle: Boolean
): Video? {
    val eps = episodes.sortedWith(compareBy({ it.season }, { it.episode }))
    if (eps.isEmpty()) return null
    if (shuffle) {
        return eps.filter { it.id != currentVideoId }.randomOrNull() ?: eps.randomOrNull()
    }
    val idx = eps.indexOfFirst { it.id == currentVideoId }
    if (idx >= 0) return eps.getOrNull(idx + 1)
    // Current episode not in the list (e.g. specials ordering): first
    // episode after the current season/episode numbers.
    return eps.firstOrNull { it.season > currentSeason || (it.season == currentSeason && it.episode > currentEpisode) }
}

/**
 * Whether the next-episode card shows at [positionMs] (Nuvio
 * PlayerNextEpisodeRules.shouldShowNextEpisodeCard, simplified: the
 * post-credits-scene sub-branch is omitted — Avio's chapter/IntroDB
 * outro intervals carry no post-credits scene model).
 *
 * With outro segments: when the gap after the latest outro end is wider
 * than the threshold, the threshold decides; when the outro ends close
 * to the file end, the card fires at the earliest outro start.
 * Without outro data, the threshold alone decides.
 */
fun shouldShowNextEpisodeCard(
    positionMs: Long,
    durationMs: Long,
    skipSegments: List<SkipSegment>,
    thresholdMode: String,
    thresholdPercent: Int,
    thresholdMinutesBeforeEnd: Float
): Boolean {
    if (durationMs <= 0L) return false
    val outroSegments = skipSegments.filter { it.type == SegmentType.OUTRO }
    if (outroSegments.isNotEmpty()) {
        val latestOutroEndMs = outroSegments.maxOf { it.endMs }
        val postOutroGapMs = durationMs - latestOutroEndMs
        val userThresholdMs = userThresholdMs(durationMs, thresholdMode, thresholdPercent, thresholdMinutesBeforeEnd)
        return if (postOutroGapMs > userThresholdMs) {
            positionMs >= userThresholdPositionMs(durationMs, thresholdMode, thresholdPercent, thresholdMinutesBeforeEnd)
        } else {
            positionMs >= outroSegments.minOf { it.startMs }
        }
    }
    return positionMs >= userThresholdPositionMs(durationMs, thresholdMode, thresholdPercent, thresholdMinutesBeforeEnd)
}

private fun userThresholdPositionMs(
    durationMs: Long,
    thresholdMode: String,
    thresholdPercent: Int,
    thresholdMinutesBeforeEnd: Float
): Long {
    return when (thresholdMode) {
        NEXT_EPISODE_MODE_MINUTES -> {
            val clampedMinutes = thresholdMinutesBeforeEnd.coerceIn(0f, 3.5f)
            (durationMs - (clampedMinutes * 60_000f).toLong()).coerceAtLeast(0L)
        }
        else -> {
            val clampedPercent = thresholdPercent.coerceIn(97, 100)
            ((clampedPercent / 100.0) * durationMs).toLong()
        }
    }
}

private fun userThresholdMs(
    durationMs: Long,
    thresholdMode: String,
    thresholdPercent: Int,
    thresholdMinutesBeforeEnd: Float
): Long {
    return when (thresholdMode) {
        NEXT_EPISODE_MODE_MINUTES -> {
            val clampedMinutes = thresholdMinutesBeforeEnd.coerceIn(0f, 3.5f)
            (clampedMinutes * 60_000f).toLong()
        }
        else -> {
            val clampedPercent = thresholdPercent.coerceIn(97, 100)
            ((1.0 - clampedPercent / 100.0) * durationMs).toLong()
        }
    }
}
