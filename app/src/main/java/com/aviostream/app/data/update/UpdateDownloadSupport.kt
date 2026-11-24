package com.aviostream.app.data.update

import java.util.Locale

/**
 * Pure progress maths for the update download (TEST v0.14.75,
 * item 7): the update popup shows the download's real progress
 * — a bar, a percentage and the MB counts — driven by
 * DownloadManager's byte counters. Everything here is a pure
 * function over plain values so the shaping can be unit-tested
 * without Android.
 */
object UpdateDownloadSupport {

    /**
     * Download fraction in [0, 1], or null when the total size
     * is unknown (DownloadManager reports -1 until the server
     * answers with a length) — null means "show an
     * indeterminate bar", never a fake 0%.
     */
    fun fraction(downloadedBytes: Long, totalBytes: Long): Float? {
        if (totalBytes <= 0L) return null
        if (downloadedBytes <= 0L) return 0f
        return (downloadedBytes.toDouble() / totalBytes.toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /** Whole percent 0..100, or null when indeterminate. */
    fun percent(downloadedBytes: Long, totalBytes: Long): Int? =
        fraction(downloadedBytes, totalBytes)?.let { (it * 100).toInt() }

    /** "18.4 MB / 43.9 MB", or just "18.4 MB" while the total is unknown. */
    fun sizeLabel(downloadedBytes: Long, totalBytes: Long): String {
        val down = mb(downloadedBytes)
        return if (totalBytes > 0L) "$down / ${mb(totalBytes)}" else down
    }

    private fun mb(bytes: Long): String =
        String.format(Locale.US, "%.1f MB", bytes.coerceAtLeast(0L) / 1_000_000.0)
}
