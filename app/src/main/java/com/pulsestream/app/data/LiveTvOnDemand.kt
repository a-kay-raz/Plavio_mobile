package com.pulsestream.app.data

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live TV On-Demand (Nuvio `livetv/LiveTvOnDemand.kt`, LV3) — Xtream Codes
 * VOD + series, served through the same provider login as Step 31's live
 * channels. Stalker has no VOD API in Nuvio's port, so On Demand is
 * Xtream-only.
 *
 * Avio note: Nuvio reads the active Xtream settings from its own
 * LiveTvRepository state; here the caller passes the app's SettingsStore
 * (the repository is a singleton object with no settings of its own).
 */

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

enum class LiveTvVodKind { Movies, Series }

data class LiveTvVodCategory(
    val id: String,
    val name: String,
)

data class LiveTvVodItem(
    val id: String,
    val remoteId: String,
    val kind: LiveTvVodKind,
    val name: String,
    val posterUrl: String? = null,
    val categoryId: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val year: String? = null,
    val extension: String? = null,
    val addedSeconds: Long? = null,
)

data class LiveTvVodEpisode(
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String? = null,
    val imageUrl: String? = null,
    val durationText: String? = null,
    val streamUrl: String,
    val extension: String,
)

data class LiveTvVodCatalog(
    val categories: List<LiveTvVodCategory> = emptyList(),
    val items: List<LiveTvVodItem> = emptyList(),
)

/** What the shell needs to open an On Demand title in the regular (seekable) player. */
data class LiveTvVodPlayback(
    val id: String,
    val title: String,
    val streamUrl: String,
    val headers: Map<String, String>,
    val streamType: String?,
    val posterUrl: String?,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
)

data class LiveTvVodCatalogState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val catalog: LiveTvVodCatalog = LiveTvVodCatalog(),
)

data class LiveTvVodEpisodesState(
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val episodes: List<LiveTvVodEpisode> = emptyList(),
)

data class LiveTvOnDemandUiState(
    val movies: LiveTvVodCatalogState = LiveTvVodCatalogState(),
    val series: LiveTvVodCatalogState = LiveTvVodCatalogState(),
    val episodes: Map<String, LiveTvVodEpisodesState> = emptyMap(),
) {
    fun catalog(kind: LiveTvVodKind): LiveTvVodCatalogState = when (kind) {
        LiveTvVodKind.Movies -> movies
        LiveTvVodKind.Series -> series
    }
}

// ---------------------------------------------------------------------------------------------
// Repository (Xtream Codes VOD + series)
// ---------------------------------------------------------------------------------------------

object LiveTvOnDemandRepository {
    private const val TAG = "LiveTvOnDemand"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val _uiState = MutableStateFlow(LiveTvOnDemandUiState())
    val uiState: StateFlow<LiveTvOnDemandUiState> = _uiState.asStateFlow()

    private var settingsSignature: String? = null
    private val catalogJobs = mutableMapOf<LiveTvVodKind, Job>()

    /** Xtream settings that can serve VOD, or null when no Xtream provider is active. */
    fun activeSettings(settings: SettingsStore): LiveTvXtreamSettings? =
        settings.liveTvXtreamSettings().takeIf { it.isEnabled && it.isConfigured }

    private fun resetIfSettingsChanged(settings: LiveTvXtreamSettings) {
        val signature = "${settings.serverUrl}|${settings.username}|${settings.password}"
        if (settingsSignature == signature) return
        settingsSignature = signature
        catalogJobs.values.forEach(Job::cancel)
        catalogJobs.clear()
        _uiState.value = LiveTvOnDemandUiState()
    }

    fun ensureLoaded(kind: LiveTvVodKind, settings: SettingsStore) {
        val active = activeSettings(settings) ?: return
        resetIfSettingsChanged(active)
        val current = _uiState.value.catalog(kind)
        if (current.hasLoaded || current.isLoading) return
        load(kind, active)
    }

    fun refresh(kind: LiveTvVodKind, settings: SettingsStore) {
        val active = activeSettings(settings) ?: return
        resetIfSettingsChanged(active)
        load(kind, active)
    }

    private fun load(kind: LiveTvVodKind, settings: LiveTvXtreamSettings) {
        catalogJobs[kind]?.cancel()
        updateCatalog(kind) { it.copy(isLoading = true, errorMessage = null) }
        catalogJobs[kind] = scope.launch {
            try {
                val catalog = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    fetchXtreamVodCatalog(settings, kind)
                }
                updateCatalog(kind) {
                    LiveTvVodCatalogState(isLoading = false, hasLoaded = true, catalog = catalog)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to load Xtream ${kind.name}", error)
                updateCatalog(kind) {
                    it.copy(isLoading = false, hasLoaded = true, errorMessage = error.message ?: error.toString())
                }
            }
        }
    }

    fun ensureEpisodes(seriesId: String, settings: SettingsStore) {
        val active = activeSettings(settings) ?: return
        val existing = _uiState.value.episodes[seriesId]
        if (existing != null && (existing.isLoading || existing.errorMessage == null)) return
        _uiState.value = _uiState.value.copy(
            episodes = _uiState.value.episodes + (seriesId to LiveTvVodEpisodesState(isLoading = true)),
        )
        scope.launch {
            val result = try {
                val episodes = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    fetchXtreamSeriesEpisodes(active, seriesId)
                }
                LiveTvVodEpisodesState(isLoading = false, episodes = episodes)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to load series $seriesId", error)
                LiveTvVodEpisodesState(isLoading = false, errorMessage = error.message ?: error.toString())
            }
            _uiState.value = _uiState.value.copy(episodes = _uiState.value.episodes + (seriesId to result))
        }
    }

    fun moviePlayback(item: LiveTvVodItem, settings: SettingsStore): LiveTvVodPlayback? {
        val active = activeSettings(settings) ?: return null
        return LiveTvVodPlayback(
            id = item.id,
            title = item.name,
            streamUrl = xtreamMovieStreamUrl(active, item),
            headers = liveTvVodRequestHeaders,
            streamType = item.extension ?: "mp4",
            posterUrl = item.posterUrl,
        )
    }

    fun episodePlayback(series: LiveTvVodItem, episode: LiveTvVodEpisode): LiveTvVodPlayback =
        LiveTvVodPlayback(
            id = "${series.id}:${episode.id}",
            title = series.name,
            streamUrl = episode.streamUrl,
            headers = liveTvVodRequestHeaders,
            streamType = episode.extension,
            posterUrl = series.posterUrl,
            seasonNumber = episode.season,
            episodeNumber = episode.episodeNumber,
            episodeTitle = episode.title,
            episodeThumbnail = episode.imageUrl,
        )

    private fun updateCatalog(kind: LiveTvVodKind, transform: (LiveTvVodCatalogState) -> LiveTvVodCatalogState) {
        val state = _uiState.value
        _uiState.value = when (kind) {
            LiveTvVodKind.Movies -> state.copy(movies = transform(state.movies))
            LiveTvVodKind.Series -> state.copy(series = transform(state.series))
        }
    }
}

internal fun filterLiveTvVodItems(
    items: List<LiveTvVodItem>,
    categoryId: String?,
    searchQuery: String,
): List<LiveTvVodItem> {
    val query = searchQuery.trim()
    return items.filter { item ->
        (categoryId == null || item.categoryId == categoryId) &&
            (query.isEmpty() || item.name.contains(query, ignoreCase = true))
    }
}
