@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.ui.screens.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * Singleton on-disk playback cache (Settings > Playback > Buffer & Cache).
 * Caches media so re-watches don't re-download. 0 = off (caller skips cache).
 * Recreated if the configured max size changes.
 */
object PlaybackCache {

    @Volatile
    private var instance: Cache? = null

    @Volatile
    private var maxBytes: Long = 0

    @Synchronized
    fun get(context: Context, maxBytes: Long): Cache {
        val current = instance
        if (current != null && this.maxBytes == maxBytes) return current
        try {
            current?.release()
        } catch (_: Exception) {
        }
        val cacheDir = File(context.cacheDir, "playback")
        val evictor = LeastRecentlyUsedCacheEvictor(maxBytes)
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, dbProvider).also {
            instance = it
            this.maxBytes = maxBytes
        }
    }

    /**
     * Auto cache size (Step 39, Nuvio PlaybackDiskCache): 1/5 of the cache
     * volume's free space, clamped to 256 MB – 10 GB.
     */
    fun autoSizeBytes(context: Context): Long {
        val mb = 1024L * 1024L
        return (context.cacheDir.usableSpace / 5L).coerceIn(256L * mb, 10_240L * mb)
    }

    /**
     * Step 70: drop the whole playback cache (Settings → Advanced →
     * "Clear playback cache"). Releases the live instance first, then
     * deletes the cache directory; [get] recreates it on next use.
     */
    @Synchronized
    fun clear(context: Context) {
        try {
            instance?.release()
        } catch (_: Exception) {
        }
        instance = null
        maxBytes = 0
        try {
            File(context.cacheDir, "playback").deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
