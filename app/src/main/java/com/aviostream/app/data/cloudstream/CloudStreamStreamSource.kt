package com.aviostream.app.data.cloudstream

import android.util.Log
import com.aviostream.app.data.StreamResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Resolves Avio streams from installed CloudStream providers.
 *
 * Flow per provider: IMDb id → `loadByExternalId` → search fallback by title
 * when the direct load misses → pick the episode matching the requested
 * season/episode (movies and live use the title itself) → `loadLinks` →
 * Avio [StreamItem]s. Providers run concurrently; one provider failing never
 * fails the others.
 *
 * The search fallback needs a title, which [StreamRepository.streamResultsFlow]
 * does not carry — the call site passes it through [title] when it has meta,
 * plus the title's year through [year] when known (releaseInfo). Without a
 * title there is no fallback, only the direct IMDb load.
 *
 * Avio retarget of the TV port's CloudStreamStreamSource: Hilt dropped
 * (Avio has no DI framework) and the TV AddonStreams return type replaced
 * with Avio's [StreamResult]. The direct `cs3:<providerId>|<data>` route is
 * kept for Phase 2 Home sections; nothing produces such ids in Phase 1.
 */
object CloudStreamStreamSource {
    private const val TAG = "AvioCS3"

    const val CS3_VIDEO_ID_PREFIX = "cs3:"

    suspend fun fetchStreams(
        videoId: String,
        season: Int?,
        episode: Int?,
        title: String?,
        type: String,
        /** Release year of the wanted title, when known — ranks fallback candidates. */
        year: Int? = null,
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
                        val resolvedItem = loadItem ?: searchFallback(providerId, title, year, type)
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
            source.toAvioStreamItem(
                providerName = plugin.metadata.name,
                pluginId = plugin.metadata.id.value,
            )
        }
        val subtitles = paired.flatMap { (_, source) -> source.toAvioSubtitles() }
        StreamResult(streams = streams, subtitles = subtitles)
    }

    /**
     * Phase 2 fallback — richer than the TV port's exact-or-contains pick.
     * Candidates are RANKED (exact title + matching year beats exact title,
     * which beats a year-matching contains, which beats a bare contains;
     * a year mismatch drags a candidate down) and each of the top 3 is
     * loaded and VERIFIED against the wanted title/year before use, so a
     * provider returning the wrong remake never yields its streams.
     * Per-provider: called inside the caller's runCatching, so one bad
     * provider never breaks the others.
     */
    private suspend fun searchFallback(
        providerId: String,
        title: String?,
        year: Int?,
        type: String,
    ): CloudStreamLoadItem? {
        val query = title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val results = CloudStreamRepository.search(query, providerId)
            .firstOrNull()?.getOrNull().orEmpty()
        for (candidate in rankSearchMatches(results, query, year, type).take(3)) {
            val loaded = CloudStreamRepository.load(providerId, candidate.data).getOrNull()
                ?: continue
            if (matchesWantedTitle(loaded, query, year)) return loaded
        }
        return null
    }

    private fun rankSearchMatches(
        items: List<CloudStreamSearchItem>,
        title: String,
        year: Int?,
        type: String,
    ): List<CloudStreamSearchItem> {
        val wanted = normalizedTitle(title)
        if (wanted.isEmpty()) return emptyList()
        val typed = items.filter { it.type.tvTypeString == type }.ifEmpty { items }
        fun titleScore(item: CloudStreamSearchItem): Int {
            val itemTitle = normalizedTitle(item.name)
            return when {
                itemTitle == wanted -> 100
                itemTitle.startsWith(wanted) || wanted.startsWith(itemTitle) -> 60
                itemTitle.contains(wanted) || wanted.contains(itemTitle) -> 40
                else -> 0
            }
        }
        fun yearScore(item: CloudStreamSearchItem): Int = when {
            year == null || item.year == null -> 0
            item.year == year -> 50
            kotlin.math.abs(item.year - year) == 1 -> 25
            else -> -100
        }
        // A title relation is mandatory — a matching year alone is not a match.
        return typed.filter { titleScore(it) > 0 }
            .sortedByDescending { titleScore(it) + yearScore(it) }
    }

    private fun matchesWantedTitle(
        loaded: CloudStreamLoadItem,
        title: String,
        year: Int?,
    ): Boolean {
        val wanted = normalizedTitle(title)
        if (wanted.isEmpty()) return false
        val loadedTitle = normalizedTitle(loaded.name)
        val titleOk = loadedTitle == wanted ||
            loadedTitle.contains(wanted) || wanted.contains(loadedTitle)
        if (!titleOk) return false
        // Year: only a hard mismatch rejects — an unknown year on either
        // side, or an off-by-one in the metadata, still matches.
        val loadedYear = loaded.year
        if (year != null && loadedYear != null &&
            kotlin.math.abs(loadedYear - year) > 1
        ) return false
        return true
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
