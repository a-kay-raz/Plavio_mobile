package com.aviostream.app.data

import kotlin.math.abs

/**
 * Letterbox detection for trailer playback (Nuvio `trailer/LetterboxDetector`,
 * verbatim math).
 *
 * A trailer whose video is 21:9 inside a 16:9 frame carries baked-in black
 * bars. Sampling a small downscaled frame tells whether bars are present and
 * how tall they are; the player then zooms in to crop them out.
 */
object TrailerLetterbox {
    /** How often a frame is sampled, ms. */
    const val SAMPLE_INTERVAL_MS = 400L

    /** Fraction of the duration after which sampling starts. */
    const val SAMPLE_WINDOW_FRACTION = 0.2f

    /** Downscaled frame size the detector samples. */
    const val SAMPLE_WIDTH = 64
    const val SAMPLE_HEIGHT = 120

    /** Pixels at or below this luma count as black. */
    const val DARK_LUMA = 40

    /** Fraction of non-dark pixels a row may contain and still be a bar. */
    const val BRIGHT_PIXEL_TOLERANCE = 0.03f

    /** Smallest / largest bar height (fraction of frame height) worth zooming for. */
    const val MIN_BAR_FRACTION = 0.03f
    const val MAX_BAR_FRACTION = 0.2f

    /** Inset from the frame edges before scanning for bars. */
    const val EDGE_PADDING = 0.01f

    /**
     * Measures the letterbox bar height as a fraction of the frame height.
     * Returns null when the frame gives no verdict (too bright / too dark
     * everywhere), 0f when there are no bars.
     */
    fun detectBar(pixels: IntArray, width: Int, height: Int): Float? {
        var darkRowsTop = 0
        var darkRowsBottom = 0
        val scanWidth = (width * 0.6f).toInt().coerceAtLeast(1)
        val scanStart = (width - scanWidth) / 2
        val edgePaddingRows = (height * EDGE_PADDING).toInt()

        var row = edgePaddingRows
        while (row < height / 2) {
            if (isRowDark(pixels, width, row, scanStart, scanWidth)) darkRowsTop++ else break
            row++
        }
        row = height - 1 - edgePaddingRows
        while (row >= height / 2) {
            if (isRowDark(pixels, width, row, scanStart, scanWidth)) darkRowsBottom++ else break
            row--
        }

        val barRows = minOf(darkRowsTop, darkRowsBottom)
        if (barRows == 0) return 0f
        val barFraction = barRows.toFloat() / height
        return when {
            barFraction < MIN_BAR_FRACTION -> 0f
            barFraction > MAX_BAR_FRACTION -> null
            else -> barFraction
        }
    }

    private fun isRowDark(
        pixels: IntArray,
        width: Int,
        row: Int,
        startX: Int,
        count: Int
    ): Boolean {
        var bright = 0
        val tolerance = (count * BRIGHT_PIXEL_TOLERANCE).toInt().coerceAtLeast(1)
        for (x in startX until startX + count) {
            val pixel = pixels[row * width + x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            val luma = (r * 299 + g * 587 + b * 114) / 1000
            if (luma > DARK_LUMA) {
                bright++
                if (bright > tolerance) return false
            }
        }
        return true
    }

    /** True once playback has passed the first 20% of the duration — earlier frames are often fades/logos. */
    fun isSampleWindowOver(positionMs: Long, durationMs: Long): Boolean {
        if (durationMs <= 0L) return false
        return positionMs >= (durationMs * SAMPLE_WINDOW_FRACTION).toLong()
    }

    /** Zoom factor that crops a bar of the given height fraction out of the frame. */
    fun zoomFor(barFraction: Float): Float = 1f / (1f - 2f * barFraction)
}

/**
 * Collects bar samples until they are stable, then yields a zoom factor.
 * A zoom is only returned after 4 consecutive samples within 0.015 of each
 * other, so a single dark scene never triggers a zoom.
 */
class LetterboxTracker {
    private var lastBar: Float? = null
    private var stableCount = 0

    fun onSample(barFraction: Float): Float? {
        val last = lastBar
        if (last != null && abs(barFraction - last) <= 0.015f) {
            stableCount++
        } else {
            stableCount = 1
        }
        lastBar = barFraction
        return if (stableCount >= 4) TrailerLetterbox.zoomFor(barFraction) else null
    }

    fun reset() {
        lastBar = null
        stableCount = 0
    }
}
