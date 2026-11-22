package com.pulsestream.app.data.cloudstream

import android.util.Log
import com.pulsestream.app.data.StreamResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Resolves Plavio streams from installed CloudStream providers.
 *
 * Flow per provider: IMDb id → `loadByExternalId` → search fallback by title
 * when the direct load misses → pick the episode matching the requested
 * season/episode (movies and live use the title itself) → `loadLinks` →
 * Plavio [StreamItem]s. Providers run concurrently; one provider failing never
 * fails the others.
 *
 * The search fallback needs a title, which [StreamRepository.streamResultsFlow]
 * does not carry — the call site passes it through [title] when it has meta.
 * Without a title there is no fallback, only the direct IMDb load.
 *
 * Plavio retarget of the TV port's CloudStreamStreamSource: Hilt dropped
 * (Plavio has no DI framework) and the TV AddonStreams return type replaced
 * with Plavio's [StreamResult]. The direct `cs3:<providerId>|<data>` route is
 * kept for Phase 2 Home sections; nothing produces such ids in Phase 1.
 */
object CloudStreamStreamSource {
    private const val TAG = "PlavioCS3"

    const val CS3_VIDEO_ID_PREFIX = "cs3:"

    suspend fun fetchStreams(
        videoId: String,
        season: Int?,
        episode: Int?,
        title: String?,
        type: String,
    ): StreamResult = coroutineScope {
        val runnablePlugins = CloudStreamRepository.runnablePlugins()
        if (runnablePlugins.isEmpty()) return@coroutineScope StreamResult(emptyList(), emptyList())
        val imdbId = videoId
            .substringBefore(':')
            .takeIf { it.matches(Regex("^tt\\d{5,12}$")) }
        val isDirectCs3Video = videoId.startsWith(CS3_VIDEO_ID_PREFIX)
        val jobs = runnablePlugins.map { plugin ->
            async {
                val providerId = plugin.metadata.id.value
                val sources = runCatching {
                    if (isDirectCs3Video) {
                        // Direct route: only the owning provider can resolve it.
                        val rest = videoId.removePrefix(CS3_VIDEO_ID_PREFIX)
                        val targetProviderId = rest.substringBefore('|').trim()
                        if (targetProviderId != providerId) return@runCatching emptyList<CloudStreamPlaybackSource>()
                        val data = rest.substringAfter('|', missingDelimiterValue = "").trim()
                            .takeIf { it.isNotEmpty() } ?: return@runCatching emptyList<CloudStreamPlaybackSource>()
                        CloudStreamRepository.loadLinks(providerId, data).getOrNull().orEmpty()
                    } else {
                        val loadItem = if (imdbId != null) {
                            CloudStreamRepository.loadByExternalId(providerId, imdbId).getOrNull()
                        } else {
                            null
                        }
                        val resolvedItem = loadItem ?: searchFallback(providerId, title, type)
                        val targetData = if (resolvedItem == null) {
                            null
                        } else {
                            selectTargetData(resolvedItem, season, episode)
                        }
                        if (targetData == null) return@runCatching emptyList<CloudStreamPlaybackSource>()
                        CloudStreamRepository.loadLinks(providerId, targetData).getOrNull().orEmpty()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "CloudStream source failed provider=$providerId videoId=$videoId", error)
                }.getOrElse {
                    emptyList()
                }
                plugin to sources
            }
        }
        val paired = jobs.flatMap { job ->
            val (plugin, sources) = job.await()
            sources.map { source -> plugin to source }
        }
        val streams = paired.map { (plugin, source) ->
            source.toPlavioStreamItem(
                providerName = plugin.metadata.name,
                pluginId = plugin.metadata.id.value,
            )
        }
        val subtitles = paired.flatMap { (_, source) -> source.toPlavioSubtitles() }
        StreamResult(streams = streams, subtitles = subtitles)
    }

    private suspend fun searchFallback(
        providerId: String,
        title: String?,
        type: String,
    ): CloudStreamLoadItem? {
        val query = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val results = CloudStreamRepository.search(query, providerId)
            .firstOrNull()?.getOrNull().orEmpty()
        val match = bestSearchMatch(results, query, type) ?: return null
        return CloudStreamRepository.load(providerId, match.data).getOrNull()
    }

    private fun bestSearchMatch(
        items: List<CloudStreamSearchItem>,
        title: String,
        type: String,
    ): CloudStreamSearchItem? {
        val wanted = normalizedTitle(title)
        if (wanted.isEmpty()) return null
        val typed = items.filter { it.type.tvTypeString == type }.ifEmpty { items }
        return typed.firstOrNull { normalizedTitle(it.name) == wanted }
            ?: typed.firstOrNull {
                val itemTitle = normalizedTitle(it.name)
                itemTitle.contains(wanted) || wanted.contains(itemTitle)
            }
    }

    private fun normalizedTitle(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun selectTargetData(
        loadItem: CloudStreamLoadItem,
        season: Int?,
        episode: Int?,
    ): String? {
        if (season == null || episode == null) return loadItem.data
        val target = loadItem.episodes.firstOrNull { it.season == season && it.episode == episode }
            ?: loadItem.episodes.firstOrNull()
        return target?.data
    }
}
