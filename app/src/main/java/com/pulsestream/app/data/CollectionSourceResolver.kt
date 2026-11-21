package com.pulsestream.app.data

import com.pulsestream.app.data.db.CollectionSourceRow
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * Step 50: resolves a collection source (or a whole folder's sources) to
 * the titles it contains (Nuvio collection/CollectionCatalogResolver.kt +
 * TmdbCollectionSourceResolver.kt, scoped port).
 *
 * Providers:
 * - "addon": a Stremio catalog from an installed addon (genre optional).
 * - "tmdb": COLLECTION (TMDB collection id), LIST (TMDB list id) or
 *   DISCOVER (mediaType + sortBy) — needs the Step 6 TMDB key.
 * - "trakt": a Trakt list id stored in [CollectionSourceRow.catalogId]
 *   (Plavio's list id is a String); needs a linked Trakt account.
 * Every failure resolves to an empty list — a bad source never breaks
 * the folder view.
 */
object CollectionSourceResolver {

    suspend fun resolveSource(
        repository: StreamRepository,
        source: CollectionSourceRow,
        limit: Int = 100,
    ): List<MetaPreview> = try {
        when (source.provider.lowercase()) {
            "addon" -> resolveAddon(repository, source, limit)
            "tmdb" -> resolveTmdb(repository.settings, source, limit)
            "trakt" -> resolveTrakt(repository, source, limit)
            else -> emptyList()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** Merges a folder's sources in order, deduped by type|id, capped. */
    suspend fun resolveFolder(
        repository: StreamRepository,
        sources: List<CollectionSourceRow>,
        limit: Int = 100,
    ): List<MetaPreview> {
        val seen = LinkedHashSet<String>()
        val out = mutableListOf<MetaPreview>()
        for (source in sources) {
            for (preview in resolveSource(repository, source, limit)) {
                if (seen.add("${preview.type}|${preview.id}")) out.add(preview)
                if (out.size >= limit) return out
            }
        }
        return out
    }

    private suspend fun resolveAddon(
        repository: StreamRepository,
        source: CollectionSourceRow,
        limit: Int,
    ): List<MetaPreview> {
        val addonId = source.addonId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val catalogId = source.catalogId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val type = source.type?.takeIf { it.isNotBlank() } ?: "movie"
        val addon = repository.addons.first()
            .firstOrNull { it.id == addonId && it.enabled } ?: return emptyList()
        val genre = source.genre?.takeIf { it.isNotBlank() }
        return if (genre != null) {
            repository.api.fetchCatalogGenre(addon.baseUrl, type, catalogId, genre)
        } else {
            repository.api.fetchCatalog(addon.baseUrl, type, catalogId)
        }.take(limit)
    }

    private suspend fun resolveTmdb(
        settings: SettingsStore,
        source: CollectionSourceRow,
        limit: Int,
    ): List<MetaPreview> {
        val tmdbId = source.tmdbId ?: return emptyList()
        return when (source.tmdbSourceType?.uppercase()) {
            "COLLECTION" -> TmdbClient.getCollection(settings, tmdbId)
            "LIST" -> TmdbClient.getTmdbList(settings, tmdbId)
            "DISCOVER" -> TmdbClient.discover(
                settings,
                mediaType = if (source.mediaType == "series") "tv" else "movie",
                sortBy = source.sortBy?.takeIf { it.isNotBlank() } ?: "popularity.desc",
            )
            else -> emptyList()
        }.take(limit)
    }

    private suspend fun resolveTrakt(
        repository: StreamRepository,
        source: CollectionSourceRow,
        limit: Int,
    ): List<MetaPreview> {
        val listId = source.catalogId?.takeIf { it.isNotBlank() } ?: return emptyList()
        val token = runCatching { TraktManager(repository.settings).validAccessToken() }
            .getOrNull() ?: return emptyList()
        val entries = runCatching { TraktApi().getListItems(token, listId) }.getOrNull()
            ?: return emptyList()
        val mediaType = source.mediaType?.takeIf { it.isNotBlank() }
        val filtered = entries.filter { entry ->
            when (mediaType) {
                "movie" -> entry.type == "movie"
                "series" -> entry.type == "show"
                else -> true
            }
        }.take(20)
        // Mirror LibraryScreen.previews(): resolve each IMDb id to a poster.
        val previews = coroutineScope {
            filtered.map { entry ->
                async {
                    val type = if (entry.type == "show") "series" else "movie"
                    val meta = runCatching {
                        repository.getMetaFromAny(null, type, entry.imdbId)
                    }.getOrNull()
                    MetaPreview(
                        id = entry.imdbId, type = type,
                        name = meta?.name ?: entry.title,
                        poster = meta?.poster,
                        releaseInfo = meta?.releaseInfo
                    )
                }
            }.awaitAll()
        }
        val sorted = when (source.sortBy?.lowercase()) {
            "title" -> {
                val byName = previews.sortedBy { it.name.lowercase() }
                if (source.sortHow?.lowercase() == "desc") byName.reversed() else byName
            }
            "released" -> previews.sortedByDescending { it.releaseInfo ?: "" }
            else -> previews
        }
        return sorted.take(limit)
    }
}
