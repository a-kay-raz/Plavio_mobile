package com.pulsestream.app.data.download

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.pulsestream.app.data.HlsQualityResolver
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.isPlayableLiveStreamUrl
import com.pulsestream.app.data.pickClosestVariant
import com.pulsestream.app.ui.components.pickAutoplayStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Step 45 — Batch season download (Nuvio downloads/BatchDownloadCoordinator.kt,
 * BatchDownloadQueuer.kt, EpisodeDownloadIndex.kt, EpisodeLabels.kt — scoped port).
 *
 * Turns the episodes chosen in the batch plan sheet into downloads: for each
 * episode, the best stream is picked with the existing autoplay rule
 * ([pickAutoplayStream], episode runtime), HLS is resolved to the preferred
 * resolution variant (Step 35 machinery), then the download is enqueued with
 * the per-show grouping (Step 36). Episodes already COMPLETED for this show
 * are skipped; DASH and stream-less episodes count as failed.
 *
 * Runs sequentially in an app-scoped scope so it survives leaving the Detail
 * screen. Per-episode stream fetch is bounded (10s): the latest emission of
 * the progressive stream flow is used, so a slow addon can't stall the whole
 * season — the pick may come from the first addons to answer rather than the
 * full set.
 */
object BatchDownloadQueuer {
    private const val TAG = "BatchDownload"
    private const val PER_EPISODE_TIMEOUT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun queue(
        context: Context,
        repository: StreamRepository,
        meta: MetaDetail,
        season: Int,
        episodes: List<Video>,
    ) {
        val appContext = context.applicationContext
        val manager = DownloadManager.get(appContext)
        // Step 46: cache the show art/meta once for the whole batch so
        // Downloads/Library can browse the show offline.
        OfflineShowMetadata.save(
            context = appContext,
            key = meta.id,
            title = meta.name ?: "Series",
            type = "series",
            posterUrl = meta.poster,
            backgroundUrl = meta.background,
            description = meta.description,
            genres = meta.genres,
            videos = meta.videos,
        )
        scope.launch {
            val completed = runCatching {
                manager.observeAll().first()
                    .filter { it.showKey == meta.id && it.season == season && it.status == "COMPLETED" }
                    .map { it.episode }.toSet()
            }.getOrElse { emptySet() }
            var started = 0
            var failed = 0
            for (episode in episodes.sortedBy { it.episode }) {
                if (episode.episode in completed) continue
                val ok = runCatching {
                    queueEpisode(appContext, repository, manager, meta, season, episode)
                }.getOrElse { error ->
                    Log.w(TAG, "Batch S${season}E${episode.episode} failed", error)
                    false
                }
                if (ok) started++ else failed++
            }
            if (failed > 0) {
                val message = if (failed == 1) {
                    "1 episode download failed — check Downloads for details."
                } else {
                    "$failed episode downloads failed — check Downloads for details."
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
                }
                Log.w(TAG, "Batch done: $started started, $failed failed")
            } else {
                Log.i(TAG, "Batch done: $started episode downloads started")
            }
        }
    }

    private suspend fun queueEpisode(
        context: Context,
        repository: StreamRepository,
        manager: DownloadManager,
        meta: MetaDetail,
        season: Int,
        episode: Video,
    ): Boolean {
        // Latest emission within the bound, or null when nothing arrived.
        var latest: List<com.pulsestream.app.data.StreamItem>? = null
        var latestSubtitles: List<com.pulsestream.app.data.Subtitle> = emptyList()
        withTimeoutOrNull(PER_EPISODE_TIMEOUT_MS) {
            repository.streamResultsFlow(meta.type, episode.id).collect { result ->
                latest = result.streams
                latestSubtitles = result.subtitles
            }
        }
        val streams = latest.orEmpty()
        val pick = pickAutoplayStream(streams, repository.settings, isEpisode = true)
            ?: return false
        if (manager.isDashDownloadUrl(pick.url)) return false
        val downloadUrl = if (manager.isHlsDownloadUrl(pick.url)) {
            val state = runCatching {
                HlsQualityResolver.resolve(context, pick.url, pick.headers)
            }.getOrNull()
            val variant = state?.variants?.let {
                pickClosestVariant(it, manager.getDownloadPreferredResolution())
            }
            variant?.absoluteUri ?: pick.url
        } else {
            pick.url
        }
        if (!isPlayableLiveStreamUrl(downloadUrl)) return false
        val title = pick.title?.takeIf { it.isNotBlank() }
            ?: pick.name?.takeIf { it.isNotBlank() }
            ?: "${meta.name ?: "Video"} S${season}E${episode.episode}"
        manager.startDownload(
            url = downloadUrl,
            title = title,
            headers = pick.headers,
            showKey = meta.id,
            seriesName = meta.name ?: "",
            season = season,
            episode = episode.episode,
            // Step 46: this episode's subtitles, fetched into sidecars
            // once the download completes.
            subtitles = latestSubtitles,
        )
        DownloadService.start(context)
        return true
    }
}
