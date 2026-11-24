package com.aviostream.app.ui.screens

import android.content.Context
import com.aviostream.app.data.MetaDetail
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.Video
import com.aviostream.app.data.download.BatchDownloadQueuer

/**
 * Auto-download next episode (v0.14.30 port, feature #16).
 *
 * Fork adaptation vs upstream 0.14.30: upstream's file also carries
 * the shared episode-download machinery (resolveEpisodeStreams,
 * startEpisodeDownload, queueEpisodeDownload) — the fork already
 * has that machinery in [BatchDownloadQueuer] (Step 45: same 10 s
 * resolve, same autoplay pick, same HLS/DASH rules, same
 * per-show grouping), so it is NOT duplicated here. Only the
 * auto-download trigger lives in this file, and it queues through
 * [BatchDownloadQueuer.queueSingle] — the same path "Download
 * season" uses, so the quality/size pick and Wi-Fi-only/storage
 * rules all apply.
 *
 * When an episode finishes in the player and the switch is on, the
 * NEXT episode is queued. Silent by design: any failure (no meta,
 * no episode, no stream) simply queues nothing.
 */
object AutoDownloadNext {

    /** The (season, episode) entry of [meta]'s video list, or null. */
    fun findEpisode(meta: MetaDetail, season: Int, episode: Int): Video? =
        meta.videos.firstOrNull { it.season == season && it.episode == episode }

    suspend fun queue(
        repository: StreamRepository,
        context: Context,
        seriesKey: String,
        nextSeason: Int,
        nextEpisode: Int
    ): Boolean = runCatching {
        val meta = repository.getMetaFromAny(null, "series", seriesKey)
            ?: return false
        val video = findEpisode(meta, nextSeason, nextEpisode) ?: return false
        BatchDownloadQueuer.queueSingle(context, repository, meta, video)
    }.getOrDefault(false)
}
