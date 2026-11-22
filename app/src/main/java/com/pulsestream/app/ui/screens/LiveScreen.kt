package com.pulsestream.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.LiveTvChannel
import com.pulsestream.app.data.LiveTvOnDemandRepository
import com.pulsestream.app.data.LiveTvSportsRepository
import com.pulsestream.app.data.LiveTvSportsState
import com.pulsestream.app.data.LiveTvVodEpisode
import com.pulsestream.app.data.LiveTvVodItem
import com.pulsestream.app.data.LiveTvVodKind
import com.pulsestream.app.data.currentProgram
import com.pulsestream.app.data.isPlayableLiveStreamUrl
import com.pulsestream.app.data.matchSportsChannels
import com.pulsestream.app.data.normalizeSportsName
import com.pulsestream.app.data.nextProgram
import com.pulsestream.app.data.LIVE_SOURCE_RESOLUTION_TIMEOUT_MS
import com.pulsestream.app.data.liveResolutionFailureMessage
import com.pulsestream.app.data.liveResourceType
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.TvChannelRef
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

internal sealed interface LiveUiState {
    data object Loading : LiveUiState
    data class Content(
        val rows: List<CatalogRow>,
        val m3uRows: List<CatalogRow>,
        val playlists: List<String>,
        val m3uErrors: List<String> = emptyList(),
        /** Now/next programmes by Live tab channel key ("addonId|metaId"). */
        val epgByKey: Map<String, List<com.pulsestream.app.data.LiveTvEpgProgram>> = emptyMap(),
        /** Provider load failures, named (channels from the working providers still show). */
        val providerErrors: List<String> = emptyList()
    ) : LiveUiState
    data class Error(val message: String) : LiveUiState
    data object Empty : LiveUiState
}

internal class LiveViewModel(
    private val repository: com.pulsestream.app.data.StreamRepository
) : androidx.lifecycle.ViewModel() {
    var uiState: LiveUiState by mutableStateOf(LiveUiState.Loading)
        private set
    private var loadJob: Job? = null
    /** Provider channels behind the provider rows (with epgId) — for EPG matching + playback. */
    private var providerChannels: List<com.pulsestream.app.data.LiveTvChannel> = emptyList()
    /** M3U channels synthesized for EPG matching (id "m3u|<url>", name only). */
    private var m3uChannelsForEpg: List<com.pulsestream.app.data.LiveTvChannel> = emptyList()
    /** Provider + M3U rows (the rows backing LiveTvChannel ids) — for sports taps. */
    private var channelRows: List<CatalogRow> = emptyList()
    /** Provider rows cache: same 15-min TTL as the addon TV rows. */
    private var providerCache: Pair<Long, Pair<List<CatalogRow>, List<String>>>? = null
    private var providerCacheSignature: String? = null
    private var epgJob: Job? = null

    init {
        load()
    }

    fun load() {
        // init and ON_RESUME may happen together; never duplicate a refresh.
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            try {
                val previous = uiState as? LiveUiState.Content
                val cached = repository.loadTvRowsCached()
                val playlists = repository.settings.m3uPlaylists
                if (cached.isNotEmpty()) {
                    uiState = LiveUiState.Content(
                        cached, previous?.m3uRows.orEmpty(), playlists,
                        previous?.m3uErrors.orEmpty(), previous?.epgByKey.orEmpty(), previous?.providerErrors.orEmpty()
                    )
                } else if (previous == null) {
                    uiState = LiveUiState.Loading
                }
                val (rows, m3u, provider) = coroutineScope {
                    val addonRows = async { repository.loadTvRows() }
                    val playlistRows = async { repository.loadM3uRows() }
                    val providerRows = async { loadProviderRows() }
                    Triple(addonRows.await(), playlistRows.await(), providerRows.await())
                }
                m3uChannelsForEpg = m3u.rows.flatMap { row ->
                    row.items.map { meta ->
                        com.pulsestream.app.data.LiveTvChannel(id = "m3u|${meta.id}", name = meta.name, streamUrl = meta.id)
                    }
                }
                channelRows = provider.first + m3u.rows
                uiState = if (rows.isEmpty() && m3u.rows.isEmpty() && provider.first.isEmpty()) {
                    // A failed playlist must say so, not show the generic
                    // "no live TV" empty state.
                    if (m3u.errors.isNotEmpty()) LiveUiState.Error(m3u.errors.joinToString("\n"))
                    else if (provider.second.isNotEmpty()) LiveUiState.Error(provider.second.joinToString("\n"))
                    else LiveUiState.Empty
                } else {
                    LiveUiState.Content(
                        rows + provider.first, m3u.rows, playlists, m3u.errors,
                        (uiState as? LiveUiState.Content)?.epgByKey.orEmpty(), provider.second
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val saved = uiState as? LiveUiState.Content
                uiState = if (saved != null) saved.copy(
                    m3uErrors = saved.m3uErrors + "Live refresh failed; showing saved channels. Retry to refresh."
                ) else LiveUiState.Error("Couldn't load live channels. Please retry.")
            }
            if (uiState is LiveUiState.Content) refreshEpg()
        }
    }

    /**
     * Xtream + Stalker channels as catalog rows, grouped by provider and
     * category (like the M3U rows). Cached 15 minutes; a settings change
     * busts the cache immediately. Failures are reported per provider and
     * never hide the other provider's channels.
     */
    private suspend fun loadProviderRows(): Pair<List<CatalogRow>, List<String>> {
        val settings = repository.settings
        val signature = listOf(
            settings.liveTvXtreamServerUrl, settings.liveTvXtreamUsername,
            settings.liveTvXtreamPassword.orEmpty(), settings.liveTvXtreamEnabled,
            settings.liveTvStalkerPortalUrl, settings.liveTvStalkerMac,
            settings.liveTvStalkerUsername, settings.liveTvStalkerPassword.orEmpty(),
            settings.liveTvStalkerEnabled
        ).joinToString("|")
        val now = System.currentTimeMillis()
        providerCache?.let { (ts, cached) ->
            if (signature == providerCacheSignature && now - ts < 15L * 60L * 1000L) {
                return cached
            }
        }
        val channels = mutableListOf<com.pulsestream.app.data.LiveTvChannel>()
        val errors = mutableListOf<String>()
        coroutineScope {
            val xtream = async {
                val s = settings.liveTvXtreamSettings()
                if (s.isConfigured && s.isEnabled) runCatching { com.pulsestream.app.data.fetchXtreamChannels(s) }
                else null
            }
            val stalker = async {
                val s = settings.liveTvStalkerSettings()
                if (s.isConfigured && s.isEnabled) runCatching { com.pulsestream.app.data.fetchStalkerChannels(s) }
                else null
            }
            xtream.await()?.fold(
                onSuccess = { channels += it },
                onFailure = { errors += "Xtream: ${it.message ?: "couldn't connect"}" }
            )
            stalker.await()?.fold(
                onSuccess = { channels += it },
                onFailure = { errors += "Stalker Portal: ${it.message ?: "couldn't connect"}" }
            )
        }
        providerChannels = channels
        val rows = channels.groupBy { it.playlistName ?: "Live" }
            .flatMap { (provider, providerChannels) ->
                val addonId = if (provider == "Xtream") "xtream" else "stalker"
                providerChannels.groupBy { it.group?.takeIf { g -> g.isNotBlank() } ?: "Channels" }
                    .map { (group, groupChannels) ->
                        CatalogRow(
                            addonId = addonId,
                            addonName = provider,
                            baseUrl = "",
                            catalogName = "$provider — $group",
                            type = "tv",
                            catalogId = "$addonId-${group.lowercase().replace(" ", "-")}",
                            items = groupChannels.map { ch ->
                                MetaPreview(
                                    id = ch.id,
                                    type = "tv",
                                    name = ch.name,
                                    poster = ch.logoUrl,
                                    logo = ch.logoUrl
                                )
                            }
                        )
                    }
            }
        val result = rows to errors
        providerCache = now to result
        providerCacheSignature = signature
        return result
    }

    /**
     * EPG refresh (Nuvio refreshEpg, simplified): guides load AFTER the
     * channels publish, so a slow XMLTV feed never blocks the channel
     * list. Sources: Xtream xmltv.php + Stalker get_epg_info + M3U
     * x-tvg-url headers (auto-detect) + manual guide sources. Best
     * effort — a failed guide just leaves its channels without now/next.
     */
    private fun refreshEpg() {
        epgJob?.cancel()
        epgJob = viewModelScope.launch {
            try {
                val settings = repository.settings
                val now = System.currentTimeMillis()
                val guides = mutableListOf<com.pulsestream.app.data.LiveTvEpgGuide>()
                val xtream = settings.liveTvXtreamSettings()
                if (settings.liveTvEpgAutoDetect && xtream.isConfigured && xtream.isEnabled) {
                    runCatching {
                        com.pulsestream.app.data.fetchXmltvGuide(
                            com.pulsestream.app.data.xtreamEpgUrl(xtream), now
                        )
                    }.onSuccess { guides += it }
                }
                val stalker = settings.liveTvStalkerSettings()
                if (stalker.isConfigured && stalker.isEnabled) {
                    runCatching {
                        com.pulsestream.app.data.fetchStalkerEpgGuide(
                            stalker,
                            now - com.pulsestream.app.data.LIVE_TV_EPG_PAST_WINDOW_MILLIS,
                            now + com.pulsestream.app.data.LIVE_TV_EPG_FUTURE_WINDOW_MILLIS
                        )
                    }.onSuccess { guides += it }
                }
                val epgUrls = mutableListOf<String>()
                if (settings.liveTvEpgAutoDetect) {
                    settings.m3uPlaylists.forEach { url ->
                        epgUrls += com.pulsestream.app.data.fetchM3uEpgUrls(url)
                    }
                }
                epgUrls += settings.liveTvEpgSources.filter { it.isEnabled }.map { it.url }
                epgUrls.distinct().forEach { url ->
                    runCatching { com.pulsestream.app.data.fetchXmltvGuide(url, now) }
                        .onSuccess { guides += it }
                }
                if (guides.isEmpty()) return@launch
                val matched = com.pulsestream.app.data.matchEpgToChannels(
                    providerChannels + m3uChannelsForEpg, guides
                )
                // Key by the Live tab's channelKey ("addonId|metaId"):
                // provider ids are "xtream:123" / "stalker:5", M3U ids are
                // already "m3u|<url>".
                val byKey = matched.mapKeys { (channelId, _) ->
                    when {
                        channelId.startsWith("xtream:") -> "xtream|$channelId"
                        channelId.startsWith("stalker:") -> "stalker|$channelId"
                        else -> channelId
                    }
                }
                val current = uiState as? LiveUiState.Content ?: return@launch
                uiState = current.copy(epgByKey = byKey)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // EPG is best-effort: channels stay, the guide just stays empty.
            }
        }
    }

    fun toggleFavorite(key: String) = repository.toggleLiveFavorite(key)

    /** Provider channel by its id ("xtream:123" / "stalker:5"), for playback. */
    fun providerChannel(id: String): com.pulsestream.app.data.LiveTvChannel? =
        providerChannels.firstOrNull { it.id == id }

    /** All provider + M3U channels — the pool sports matching runs against. */
    fun allLiveTvChannels(): List<com.pulsestream.app.data.LiveTvChannel> =
        providerChannels + m3uChannelsForEpg

    /** (row, meta) behind a provider/M3U channel id, so a sports tap can play it. */
    fun channelRowFor(channelId: String): Pair<CatalogRow, MetaPreview>? {
        for (row in channelRows) {
            val meta = row.items.firstOrNull { it.id == channelId } ?: continue
            return row to meta
        }
        return null
    }
}

private fun channelKey(row: CatalogRow, meta: MetaPreview) = "${row.addonId}|${meta.id}"

/**
 * Live tab sections (Nuvio LiveTvScreen has Channels / Guide / Sports /
 * OnDemand; Avio has no guide grid, so Channels / Sports / On Demand).
 */
private enum class LiveTvSection { Channels, Sports, OnDemand }

/**
 * The Live tab: TV channels from addons + M3U playlists, with pinned
 * favorites, recently-watched channels, category chips that actually
 * filter, long-press to pin, and a top-right M3U import.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LiveScreen(
    onPlay: (PlaybackRequest) -> Unit,
    onGoToAddons: () -> Unit,
    repository: com.pulsestream.app.data.StreamRepository,
    onGoToLiveTvSettings: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val vm: LiveViewModel = viewModel(factory = remember {
        object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                LiveViewModel(repository) as T
        }
    })
    var query by remember { mutableStateOf("") }
    var resolvingKey by remember { mutableStateOf<String?>(null) }
    var resolvingJob by remember { mutableStateOf<Job?>(null) }
    var resolveGeneration by remember { mutableIntStateOf(0) }
    var resolveError by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }
    /** Live tab section (Step 32): Channels / Sports scores / On Demand. */
    var section by remember { mutableStateOf(LiveTvSection.Channels) }
    /** Sports filter: "all", "live", or a league id. */
    var sportsFilter by rememberSaveable { mutableStateOf("all") }
    /** On Demand kind + category; null category = All. */
    var vodKind by remember { mutableStateOf(com.pulsestream.app.data.LiveTvVodKind.Movies) }
    var vodCategory by rememberSaveable { mutableStateOf<String?>(null) }
    /** Series whose episode sheet is open (On Demand). */
    var vodSeries by remember { mutableStateOf<com.pulsestream.app.data.LiveTvVodItem?>(null) }
    /** Selected category chip; null = All. */
    var category by remember { mutableStateOf<String?>(null) }
    /** Bumped to re-read favorites/recents after a toggle. */
    var favVersion by remember { mutableIntStateOf(0) }

    // Back clears the query AND closes search — never just one of them.
    BackHandler(enabled = showSearch) {
        query = ""
        showSearch = false
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.load()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // ---- Step 32: Sports scores + On Demand --------------------------------
    val sportsState by LiveTvSportsRepository.uiState.collectAsState()
    val vodState by LiveTvOnDemandRepository.uiState.collectAsState()

    LaunchedEffect(section) {
        if (section == LiveTvSection.Sports) {
            LiveTvSportsRepository.refreshIfStale()
        }
    }
    LaunchedEffect(section, vodKind) {
        if (section == LiveTvSection.OnDemand) {
            LiveTvOnDemandRepository.ensureLoaded(vodKind, repository.settings)
        }
    }
    LaunchedEffect(vodSeries) {
        vodSeries?.let { LiveTvOnDemandRepository.ensureEpisodes(it.remoteId, repository.settings) }
    }
    // Sports → channel matching (Nuvio matchSportsChannels), over the
    // provider + M3U channels the VM already holds. Recomputed when the
    // channel load state changes too, so matches appear once providers load.
    val sportsMatches by produceState(
        emptyMap<String, List<LiveTvChannel>>(),
        sportsState.events, section, vm.uiState
    ) {
        if (section != LiveTvSection.Sports) {
            value = emptyMap()
            return@produceState
        }
        val normalized = vm.allLiveTvChannels().map { it to normalizeSportsName(it.name) }
        value = sportsState.events.associate { event ->
            "${event.league.id}:${event.id}" to matchSportsChannels(event, normalized)
        }
    }

    fun channelRef(row: CatalogRow, meta: MetaPreview) = TvChannelRef(
        addonId = row.addonId,
        metaId = meta.id,
        name = meta.name,
        poster = meta.poster,
        type = liveResourceType(meta.type, row.type),
        catalogId = row.catalogId,
        group = row.catalogName
    )

    fun playProviderChannel(row: CatalogRow, meta: MetaPreview) {
        resolvingJob?.cancel()
        resolvingJob = null
        val generation = ++resolveGeneration
        resolvingKey = null
        resolveError = null
        val key = channelKey(row, meta)
        val channel = vm.providerChannel(meta.id)
        if (channel == null) {
            resolveError = "Channel is no longer in the provider list. Reopen the Live tab to refresh."
            return
        }
        if (row.addonId == "xtream") {
            repository.pushLiveRecent(key)
            favVersion++
            onPlay(
                PlaybackRequest(
                    streamUrl = channel.streamUrl,
                    headers = channel.headers,
                    title = channel.name,
                    poster = channel.logoUrl,
                    type = "tv",
                    videoId = channel.id,
                    streamLabel = "LIVE",
                    sourceName = "Xtream",
                    liveChannel = channelRef(row, meta)
                )
            )
            return
        }
        // Stalker: resolve the real stream URL via create_link first.
        resolvingKey = key
        resolvingJob = scope.launch {
            try {
                val resolved = com.pulsestream.app.data.preparePortalChannelForPlayback(
                    channel, repository.settings.liveTvStalkerSettings()
                )
                if (generation != resolveGeneration) return@launch
                repository.pushLiveRecent(key)
                favVersion++
                onPlay(
                    PlaybackRequest(
                        streamUrl = resolved.streamUrl,
                        headers = resolved.headers,
                        title = resolved.name,
                        poster = resolved.logoUrl,
                        type = "tv",
                        videoId = resolved.id,
                        streamLabel = "LIVE",
                        sourceName = "Stalker Portal",
                        liveChannel = channelRef(row, meta)
                    )
                )
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                if (generation == resolveGeneration) {
                    resolveError = liveResolutionFailureMessage(meta.name)
                }
            } finally {
                if (generation == resolveGeneration) {
                    resolvingJob = null
                    resolvingKey = null
                }
            }
        }
    }

    fun playChannel(row: CatalogRow, meta: MetaPreview, flatList: List<TvChannelRef>) {
        // Player zapping: hand over the whole visible list + this channel's
        // index BEFORE playing, so channel up/down has a valid list to work
        // from on the very first play.
        val idx = flatList.indexOfFirst { it.addonId == row.addonId && it.metaId == meta.id }
        repository.setTvZapList(flatList, if (idx >= 0) idx else 0)
        resolvingJob?.cancel()
        resolvingJob = null
        val generation = ++resolveGeneration
        resolvingKey = null
        resolveError = null
        if (row.addonId == "m3u") {
            // M3U: the channel id IS the stream URL — play it directly.
            // (No resolution step exists here, so this is as "after
            // successful playback start" as the Live tab can know.)
            repository.pushLiveRecent(channelKey(row, meta))
            favVersion++ // re-render recents row
            onPlay(
                PlaybackRequest(
                    streamUrl = meta.id,
                    title = meta.name,
                    poster = meta.poster,
                    type = "tv",
                    videoId = meta.id,
                    streamLabel = "LIVE"
                )
            )
            return
        }
        // Provider channels (Xtream / Stalker): the stream URL is already
        // known — no addon resolution. Stalker resolves its real URL via
        // create_link at play time.
        if (row.addonId == "xtream" || row.addonId == "stalker") {
            playProviderChannel(row, meta)
            return
        }
        val key = channelKey(row, meta)
        resolvingKey = key
        val channel = channelRef(row, meta)
        resolvingJob = scope.launch {
            try {
                // FIX (2026-10-20): Collect streams from ALL addons, not just the first.
                // The old firstOrNull meant if the fastest addon returned dead URLs,
                // working streams from slower addons were never tried - causing the
                // "keep changing sources then can't play" symptom.
                val allStreams = mutableListOf<com.pulsestream.app.data.StreamItem>()
                val seenUrls = mutableSetOf<String>()
                // Bounded window: addons answer in parallel, so most arrive in a
                // few seconds; don't let one slow addon stall channel start.
                withTimeoutOrNull(8_000L) {
                    repository.liveStreamResultsFlow(channel).collect { result ->
                        result.streams.forEach { stream ->
                            if (isPlayableLiveStreamUrl(stream.url) && seenUrls.add(stream.url)) {
                                allStreams.add(stream)
                            }
                        }
                    }
                }
                if (generation != resolveGeneration) return@launch
                // Probe-rank: verified-alive streams first, so the first URL
                // handed to the player isn't a dead one that triggers the
                // source-cycling failover. Budget-bounded; unverified streams
                // stay in the list as failover candidates.
                val streams = com.pulsestream.app.data.StreamProber
                    .rankByProbe(allStreams, isTv = true)
                if (generation != resolveGeneration) return@launch
                val first = streams.firstOrNull()
                if (first == null) {
                    resolveError = "No playable stream found for \"${meta.name}\"."
                } else {
                    // Resolution succeeded — only now does the channel earn a
                    // place in Recently Watched; failed channels stay out.
                    repository.pushLiveRecent(key)
                    favVersion++ // re-render recents row
                    onPlay(
                        PlaybackRequest(
                            streamUrl = first.url,
                            headers = first.headers,
                            title = meta.name,
                            poster = meta.poster,
                            type = channel.type,
                            videoId = meta.id,
                            streamLabel = "LIVE",
                            sourceName = first.sourceName,
                            streams = streams,
                            mimeType = first.mimeType,
                            liveChannel = channel
                        )
                    )
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                if (generation == resolveGeneration) {
                    resolveError = liveResolutionFailureMessage(meta.name)
                }
            } finally {
                if (generation == resolveGeneration) {
                    resolvingJob = null
                    resolvingKey = null
                }
            }
        }
    }

    /**
     * Provider channel playback (Xtream / Stalker): the channel's stream
     * URL + headers are already known, so there is no addon resolution.
     * Xtream plays directly; Stalker resolves its real URL via create_link
     * first. NOTE: provider channels are deliberately left out of the zap
     * list — the player resolves zap targets through addons, which provider
     * ids can't use (follow-up).
     */

    /** Play a provider/M3U channel found by sports matching (Step 32). */
    fun playMatchedChannel(channel: LiveTvChannel) {
        val (row, meta) = vm.channelRowFor(channel.id) ?: return
        // Zap list from the loaded rows (provider rows stay out, Step 31 rule).
        val content = vm.uiState as? LiveUiState.Content
        val flatList = content?.let { c ->
            (c.rows + c.m3uRows).flatMap { r -> r.items.map { channelRef(r, it) } }
                .filter { it.addonId != "xtream" && it.addonId != "stalker" }
        } ?: emptyList()
        playChannel(row, meta, flatList)
    }

    /** Play an On Demand movie through the regular player (Step 32). */
    fun playVodMovie(item: LiveTvVodItem) {
        val playback = LiveTvOnDemandRepository.moviePlayback(item, repository.settings) ?: return
        onPlay(
            PlaybackRequest(
                streamUrl = playback.streamUrl,
                title = playback.title,
                poster = playback.posterUrl,
                videoId = playback.id,
                isMovie = true,
                type = "movie",
                streamLabel = "Xtream VOD",
                sourceName = "Xtream",
                headers = playback.headers
            )
        )
    }

    /** Play an On Demand episode through the regular player (Step 32). */
    fun playVodEpisode(series: LiveTvVodItem, episode: LiveTvVodEpisode) {
        val playback = LiveTvOnDemandRepository.episodePlayback(series, episode)
        onPlay(
            PlaybackRequest(
                streamUrl = playback.streamUrl,
                title = playback.title,
                poster = playback.posterUrl,
                videoId = playback.id,
                season = playback.seasonNumber ?: 0,
                episode = playback.episodeNumber ?: 0,
                type = "series",
                seriesName = playback.title,
                episodeTitle = playback.episodeTitle,
                streamLabel = "Xtream VOD",
                sourceName = "Xtream",
                headers = playback.headers
            )
        )
    }

    Column(Modifier.fillMaxSize()) {
        // ---- Header ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot()
                Spacer(Modifier.width(8.dp))
                Text(
                    "Live TV",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onGoToLiveTvSettings) {
                Icon(
                    Icons.Filled.Settings,
                    "Live TV settings",
                    tint = TextSecondary
                )
            }
            IconButton(onClick = { showSearch = !showSearch }) {
                Icon(
                    if (showSearch) Icons.Filled.Close else Icons.Filled.Search,
                    "Search",
                    tint = TextSecondary
                )
            }
        }

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        if (section == LiveTvSection.OnDemand) "Search on demand…"
                        else "Search live channels…"
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(50)
            )
        }

        // Section switcher (Step 32): Channels / Sports scores / On Demand.
        // Sports is keyless (ESPN); On Demand needs the Xtream login.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            item(key = "section|channels") {
                FilterChip(
                    selected = section == LiveTvSection.Channels,
                    onClick = { section = LiveTvSection.Channels },
                    label = { Text("Channels") },
                    colors = chipColors()
                )
            }
            item(key = "section|sports") {
                val hasLive = sportsState.events.any { it.state == LiveTvSportsState.Live }
                FilterChip(
                    selected = section == LiveTvSection.Sports,
                    onClick = { section = LiveTvSection.Sports },
                    label = { Text(if (hasLive) "Sports • Live" else "Sports") },
                    colors = chipColors()
                )
            }
            item(key = "section|vod") {
                FilterChip(
                    selected = section == LiveTvSection.OnDemand,
                    onClick = { section = LiveTvSection.OnDemand },
                    label = { Text("On Demand") },
                    colors = chipColors()
                )
            }
        }

        if (section == LiveTvSection.Sports) {
            LiveTvSportsSection(
                sportsState = sportsState,
                matchedChannels = sportsMatches,
                filter = sportsFilter,
                onFilterSelected = { sportsFilter = it },
                onChannelClick = ::playMatchedChannel,
                onRefresh = { LiveTvSportsRepository.refresh() }
            )
        } else if (section == LiveTvSection.OnDemand) {
            LiveTvOnDemandSection(
                vodState = vodState,
                kind = vodKind,
                onKindSelected = { vodKind = it; vodCategory = null },
                categoryId = vodCategory,
                onCategorySelected = { vodCategory = it },
                searchQuery = query,
                xtreamConfigured = LiveTvOnDemandRepository.activeSettings(repository.settings) != null,
                onGoToLiveTvSettings = onGoToLiveTvSettings,
                onMovieClick = ::playVodMovie,
                onSeriesClick = { vodSeries = it },
                onRefresh = { LiveTvOnDemandRepository.refresh(vodKind, repository.settings) }
            )
        } else when (val state = vm.uiState) {
            is LiveUiState.Loading -> LoadingState("Finding live channels…")
            is LiveUiState.Error -> ErrorState(state.message, onRetry = { vm.load() })
            is LiveUiState.Empty -> EmptyState(
                title = "No live TV yet",
                message = "Avio doesn't bundle any channels. Install a live TV addon " +
                    "with its manifest URL — or import an M3U playlist from the top-right " +
                    "button — and its channels will appear here. IPTV providers (Xtream / " +
                    "Stalker) are added from the gear icon at the top.",
                actionLabel = "Add an addon",
                onAction = onGoToAddons
            )
            is LiveUiState.Content -> {
                val allRows = remember(state.rows, state.m3uRows) {
                    state.rows + state.m3uRows
                }
                val q = query.trim()
                val queryFiltered = remember(allRows, q) {
                    if (q.isBlank()) allRows
                    else allRows.map { row ->
                        row.copy(items = row.items.filter {
                            it.name.contains(q, ignoreCase = true)
                        })
                    }.filter { it.items.isNotEmpty() }
                }
                // Category chips derived from catalog names (e.g. M3U group-titles
                // like News/Sports/Movies/Kids); tapping one shows ONLY those rows.
                val categories = remember(queryFiltered) {
                    queryFiltered.map { it.catalogName }.distinct()
                }
                val visibleRows = remember(queryFiltered, category) {
                    if (category == null) queryFiltered
                    else queryFiltered.filter { it.catalogName == category }
                }
                // Favorites + recents, resolved against the loaded rows.
                val favorites = remember(allRows, favVersion) {
                    val keys = repository.settings.liveFavorites
                    allRows.flatMap { row ->
                        row.items.filter { channelKey(row, it) in keys }
                            .map { row to it }
                    }.distinctBy { (row, meta) -> channelKey(row, meta) }
                }
                val recents = remember(allRows, favVersion) {
                    val keys = repository.settings.liveRecent
                    keys.mapNotNull { key ->
                        allRows.firstNotNullOfOrNull { row ->
                            row.items.firstOrNull { channelKey(row, it) == key }
                                ?.let { row to it }
                        }
                    }.distinctBy { (row, meta) -> channelKey(row, meta) }
                }
                // Flat visible list for the player zapping handoff.
                // Provider rows stay out: the player resolves zap targets
                // through addons, which provider ids can't use.
                val flatList = remember(visibleRows, favorites, recents) {
                    (favorites + recents + visibleRows.flatMap { row ->
                        row.items.map { row to it }
                    }).distinctBy { (row, meta) -> channelKey(row, meta) }
                        .filter { (row, _) -> row.addonId != "xtream" && row.addonId != "stalker" }
                        .map { (row, meta) -> channelRef(row, meta) }
                }

                @Composable
                fun ChannelCard(row: CatalogRow, meta: MetaPreview) {
                    val key = channelKey(row, meta)
                    val pinned = key in repository.settings.liveFavorites
                    val busy = resolvingKey == key
                    // Channel tile (v0.12.4 Sample C; resized v0.12.5):
                    // a logo tile + name below. v0.12.5 (owner's order):
                    // the tile now matches the Home catalog PosterCard
                    // footprint exactly — 128dp wide with a 180dp art
                    // box — because the old 132×76 landscape tile read
                    // far too small next to Home's cards. Channel art
                    // is wide logos, so it stays ContentScale.Fit,
                    // centered inside the taller box. The
                    // long-press lives ON the tile itself (an inner
                    // clickable under an outer combinedClickable would
                    // consume the press, which is why pinning once
                    // never fired).
                    Column(
                        modifier = Modifier
                            .width(128.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .focusable()
                            .tvFocusRing(RoundedCornerShape(14.dp))
                            .combinedClickable(
                                onClick = { playChannel(row, meta, flatList) },
                                onLongClick = { vm.toggleFavorite(key); favVersion++ }
                            )
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.06f))
                                .border(1.dp, GlassBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            val art = meta.background ?: meta.logo ?: meta.poster
                            if (art != null) {
                                AsyncImage(
                                    model = art,
                                    contentDescription = meta.name,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                    contentScale = ContentScale.Fit
                                )
                            } else {
                                Text(
                                    meta.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextSecondary
                                )
                            }
                            if (pinned) {
                                Icon(
                                    Icons.Filled.Star, "Pinned",
                                    tint = Color(0xFFFFD54F),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(5.dp)
                                        .size(14.dp)
                                )
                            }
                            if (busy) {
                                OrbitDots(modifier = Modifier.padding(8.dp).size(32.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot()
                            Spacer(Modifier.width(5.dp))
                            Text(
                                meta.name,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // EPG now/next (Nuvio LiveTvEpgUi): the programme
                        // on now, with a progress bar. Only when a guide
                        // matched this channel.
                        val nowProgram = state.epgByKey[key]?.currentProgram(System.currentTimeMillis())
                        if (nowProgram != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                nowProgram.title,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(3.dp))
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color.White.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(nowProgram.progressAt(System.currentTimeMillis()))
                                        .height(3.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(LocalPalette.current.accent)
                                )
                            }
                        }
                    }
                }

                @Composable
                fun FeaturedChannelCard(row: CatalogRow, meta: MetaPreview) {
                    val key = channelKey(row, meta)
                    val busy = resolvingKey == key
                    // Sample C featured card (v0.12.4): one wide 16:9
                    // card for whatever is on now — tap plays through
                    // the same path as every channel tile; long-press
                    // pins/unpins like the tiles.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                            .focusable()
                            .tvFocusRing(RoundedCornerShape(18.dp))
                            .combinedClickable(
                                onClick = { playChannel(row, meta, flatList) },
                                onLongClick = { vm.toggleFavorite(key); favVersion++ }
                            )
                    ) {
                        val art = meta.background ?: meta.logo ?: meta.poster
                        if (art != null) {
                            AsyncImage(
                                model = art,
                                contentDescription = meta.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF14324A), Color(0xFF0B1A2C))
                                        )
                                    )
                            )
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.78f)
                                        ),
                                        startY = 140f
                                    )
                                )
                        )
                        if (meta.logo != null && meta.logo != art) {
                            AsyncImage(
                                model = meta.logo,
                                contentDescription = null,
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .padding(12.dp)
                                    .height(26.dp)
                                    .widthIn(max = 110.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LiveDot()
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "LIVE NOW",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        letterSpacing = 1.2.sp
                                    ),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = LocalPalette.current.accent
                                )
                                Text(
                                    "  ·  ${row.catalogName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                meta.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            meta.description?.takeIf { it.isNotBlank() }?.let { desc ->
                                Text(
                                    desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // EPG now/next on the featured card.
                            val featuredEpg = state.epgByKey[channelKey(row, meta)]
                            val featuredNow = featuredEpg?.currentProgram(System.currentTimeMillis())
                            val featuredNext = featuredEpg?.nextProgram(System.currentTimeMillis())
                            if (featuredNow != null || featuredNext != null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    buildString {
                                        featuredNow?.let { append("Now: ${it.title}") }
                                        featuredNext?.let {
                                            if (isNotEmpty()) append("  ·  ")
                                            append("Next: ${it.title}")
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (busy) {
                            OrbitDots(modifier = Modifier.align(Alignment.Center).padding(8.dp).size(32.dp))
                        }
                    }
                }

                resolveError?.let { err ->
                    Text(
                        err,
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
                // Failed playlists are named, not silently dropped.
                state.m3uErrors.forEach { err ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠ $err",
                            color = Color(0xFFFFB74D),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.load() }) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }
                // Failed providers are named the same way.
                state.providerErrors.forEach { err ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "⚠ $err",
                            color = Color(0xFFFFB74D),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { vm.load() }) {
                            Text("Retry", color = LocalPalette.current.accent)
                        }
                    }
                }
                // Featured channel (v0.12.4, Sample C): the first channel
                // of the first non-empty category — Sports preferred when
                // present — taken from the UNFILTERED browsing set. Shown
                // as the wide card at the top of the tab only while
                // browsing (no search text or category chip narrowing
                // the list).
                val featuredRow = remember(categories, visibleRows, query, category) {
                    val browsing = query.isBlank() && category == null
                    if (!browsing) null
                    else {
                        val sportsName = categories.firstOrNull {
                            it.equals("Sports", ignoreCase = true)
                        }
                        sportsName?.let { name ->
                            visibleRows.firstOrNull { it.catalogName == name }
                        } ?: visibleRows.firstOrNull()
                    }
                }
                val featuredChannel = featuredRow?.items?.firstOrNull()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    // Extra bottom room so the last row scrolls clear of
                    // the floating dock (content now runs under it).
                    contentPadding = PaddingValues(bottom = 104.dp)
                ) {
                    // ---- Category chips ----
                    if (categories.size > 1 || (categories.size == 1 && category != null)) {
                        item(key = "live|chips") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                item(key = "chip|all") {
                                    FilterChip(
                                        selected = category == null,
                                        onClick = { category = null },
                                        label = { Text("All") },
                                        colors = chipColors()
                                    )
                                }
                                categories.forEach { cat ->
                                    item(key = "chip|$cat") {
                                        FilterChip(
                                            selected = category == cat,
                                            onClick = { category = if (category == cat) null else cat },
                                            label = { Text(cat) },
                                            colors = chipColors()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // ---- Pinned favorites ----
                    if (favorites.isNotEmpty()) {
                        item(key = "live|favs") {
                            SectionTitle("Pinned channels", "long-press any channel to pin")
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    favorites,
                                    key = { _, pair -> "fav|${channelKey(pair.first, pair.second)}" }
                                ) { _, (row, meta) -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // ---- Featured (Sample C) ----
                    if (featuredRow != null && featuredChannel != null) {
                        item(key = "live|featured") {
                            FeaturedChannelCard(featuredRow, featuredChannel)
                            Spacer(Modifier.height(14.dp))
                        }
                    }
                    // ---- Recently watched ----
                    if (recents.isNotEmpty()) {
                        item(key = "live|recent") {
                            SectionTitle("Recently watched", null)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    recents,
                                    key = { _, pair -> "rec|${channelKey(pair.first, pair.second)}" }
                                ) { _, (row, meta) -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    // ---- Channel rows ----
                    if (visibleRows.isEmpty() && (q.isNotBlank() || category != null)) {
                        item(key = "live|noresult") {
                            EmptyState(
                                title = "No channels found",
                                message = "Nothing matches this filter."
                            )
                        }
                    }
                    visibleRows.forEach { row ->
                        item(key = "live|${row.addonId}|${row.type}|${row.catalogId}") {
                            SectionTitle(row.catalogName, row.addonName)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                itemsIndexed(
                                    row.items,
                                    key = { index, meta -> "live_${row.addonId}_${meta.id}_$index" }
                                ) { _, meta -> ChannelCard(row, meta) }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

        if (showM3uDialog) {
            M3uImportDialog(
                playlists = (vm.uiState as? LiveUiState.Content)?.playlists ?: emptyList(),
                onDismiss = { showM3uDialog = false },
                onAdd = { url ->
                    // Belt-and-braces: addM3uPlaylist returns Result instead of
                    // throwing, but a throw here would still kill the dialog's
                    // coroutine — never let it escape.
                    try {
                        repository.addM3uPlaylist(url).also { result ->
                            if (result.isSuccess) vm.load()
                        }
                    } catch (e: Exception) {
                        Result.failure<Int>(e)
                    }
                },
                onRemove = { url ->
                    scope.launch {
                        repository.removeM3uPlaylist(url)
                        vm.load()
                    }
                }
            )
        }
    }

    // On Demand series sheet (Step 32): episodes for the tapped series.
    vodSeries?.let { series ->
        LiveTvSeriesSheet(
            series = series,
            episodesState = vodState.episodes[series.remoteId]
                ?: com.pulsestream.app.data.LiveTvVodEpisodesState(),
            onEpisodeClick = { episode ->
                playVodEpisode(series, episode)
                vodSeries = null
            },
            onDismiss = { vodSeries = null }
        )
    }
}

/**
 * M3U/IPTV playlist import dialog (top-right button of the Live tab).
 * The playlist is fetched + parsed immediately, so a bad URL fails here
 * with a readable error instead of silently adding nothing.
 */
@Composable
private fun M3uImportDialog(
    playlists: List<String>,
    onDismiss: () -> Unit,
    onAdd: suspend (String) -> Result<Int>,
    onRemove: (String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.panel,
        title = { Text("Import M3U playlist", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (success != null) {
                    Text(success!!, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Text(
                        "Paste an M3U / IPTV playlist URL — its channels appear grouped " +
                            "in the Live tab and play in the Avio player.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; error = null },
                        placeholder = { Text("https://…/playlist.m3u", color = TextSecondary) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LocalPalette.current.accent,
                            unfocusedBorderColor = GlassBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = LocalPalette.current.accent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            error!!,
                            color = Color(0xFFFF6B6B),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (playlists.isNotEmpty() && success == null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Imported playlists",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    playlists.forEach { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                runCatching { java.net.URI(pl).host ?: pl }.getOrDefault(pl),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemove(pl) }) {
                                Icon(
                                    Icons.Filled.Delete, "Remove playlist",
                                    tint = TextSecondary, modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (success == null) {
                TextButton(
                    onClick = {
                        val u = url.trim()
                        if (u.isBlank()) {
                            error = "Paste a playlist URL first."
                            return@TextButton
                        }
                        adding = true
                        error = null
                        scope.launch {
                            val result = onAdd(u)
                            adding = false
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                success = "Imported $count channel${if (count == 1) "" else "s"} ✓"
                            } else {
                                error = result.exceptionOrNull()?.message
                                    ?: "Couldn't import that playlist."
                            }
                        }
                    },
                    enabled = !adding && url.isNotBlank()
                ) {
                    Text(
                        if (adding) "Importing…" else "Import",
                        color = LocalPalette.current.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (success == null) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
            }
        }
    )
}

/** FilterChip colors matching the Avio dark aesthetic. */
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
    selectedLabelColor = TextPrimary,
    containerColor = GlassWhite,
    labelColor = TextSecondary,
    selectedLeadingIconColor = LocalPalette.current.accent
)

/** Live indicator: a mint-green dot with a white center, gently pulsing (owner's order 2026-10-22: green, following the app theme). */
@Composable
private fun LiveDot(modifier: Modifier = Modifier) {
    val reduced = com.pulsestream.app.data.rememberReducedMotion()
    val alpha = if (reduced) 1f else {
        val transition = rememberInfiniteTransition(label = "livedot")
        val animated by transition.animateFloat(initialValue = 0.55f, targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(1100, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse),
            label = "livedotAlpha")
        animated
    }
    Box(
        modifier.size(15.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(Color(0xFF3DDC97).copy(alpha = alpha))
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
