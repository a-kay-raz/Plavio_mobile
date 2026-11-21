package com.pulsestream.app.ui.screens

import android.view.ViewGroup
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.pulsestream.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import com.pulsestream.app.ui.components.OrbitDots
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.PlaylistRemove
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.ui.components.AddToListSheet
import com.pulsestream.app.ui.components.PlayButton
import com.pulsestream.app.ui.components.WatchedPill
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.data.MetaDetail
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.BrowsingPrivacy
import com.pulsestream.app.data.OmdbClient
import com.pulsestream.app.data.OmdbRating
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.data.UserRatings
import com.pulsestream.app.data.TrailerResolver
import com.pulsestream.app.data.RelatedSection
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamProber
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.Video
import com.pulsestream.app.data.streamQualityLabel
import com.pulsestream.app.data.SUBTITLE_LANGUAGE_OPTIONS
import com.pulsestream.app.ui.components.availableStreamQualities
import com.pulsestream.app.ui.components.parseStreamInfo
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.liquidGlass
import androidx.compose.ui.draw.alpha
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.components.AddonTabs
import com.pulsestream.app.ui.components.pickAutoplayStream
import com.pulsestream.app.ui.components.rememberStreamBrowserState
import com.pulsestream.app.ui.components.StreamCard
import com.pulsestream.app.ui.components.StreamList
import com.pulsestream.app.ui.components.streamListItems
import com.pulsestream.app.ui.components.DownloadConfirmDialog
import com.pulsestream.app.ui.components.StreamTab
import com.pulsestream.app.ui.components.addonKey
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import androidx.compose.ui.focus.focusProperties
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.tv.tvSheetFocus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

sealed interface MetaUiState {
    data object Loading : MetaUiState
    data class Content(val meta: MetaDetail) : MetaUiState
    data class Error(val message: String) : MetaUiState
}

sealed interface StreamsUiState {
    data object Idle : StreamsUiState
    data object Loading : StreamsUiState
    data class Content(val streams: List<StreamItem>, val subtitles: List<Subtitle>, val loadingMore: Boolean = false) : StreamsUiState
    data class Error(val message: String) : StreamsUiState
}

/** Fired when the user pressed Play: play this stream immediately. */
data class AutoPlay(val stream: StreamItem, val subtitles: List<Subtitle>, val fromStart: Boolean = false)

/** State of the "Plavio Pick" background stream check. */
sealed interface PickState {
    data object Idle : PickState
    data object Probing : PickState
    data class Found(val stream: StreamItem) : PickState
    data object None : PickState
}

class DetailViewModel(private val repo: StreamRepository) : ViewModel() {
    /** Set from the composable; used for TV-aware probing headers. */
    var isTv: Boolean = false
    var metaState by mutableStateOf<MetaUiState>(MetaUiState.Loading)
        private set
    var streamsState by mutableStateOf<StreamsUiState>(StreamsUiState.Idle)
        private set
    var selectedSeason by mutableStateOf<Int?>(null)
        private set
    var selectedVideo by mutableStateOf<Video?>(null)
        private set
    /** Shuffle episodes (v0.9.0+): random pick instead of sequential. Backed by SettingsStore. */
    var shuffleEpisodes by mutableStateOf(repo.settings.shuffleEpisodes)
        private set
    var autoPlayEvent by mutableStateOf<AutoPlay?>(null)
        private set
    /** "Plavio Pick": best verified stream, probed in the background. */
    var pickState by mutableStateOf<PickState>(PickState.Idle)
        private set
    /** "More like this" + "More from <actor>" rows from PlavioMeta. */
    var relatedState by mutableStateOf<List<RelatedSection>>(emptyList())
        private set

    /** Set when the user pressed Play while streams were still loading. */
    private var pendingAutoPlay = false
    /** In-flight stream collection — cancelled when a new one starts. */
    private var streamsJob: Job? = null
    /** In-flight Plavio Pick probe — cancelled when streams reload. */
    private var probeJob: Job? = null
    /** Base URL of the addon the title was opened from — tried first for metadata. */
    private var hintBaseUrl: String? = null

    fun load(hintBaseUrl: String?, type: String, id: String) {
        this.hintBaseUrl = hintBaseUrl
        // Drop stale related rows immediately — the new fetch is async and the
        // old title's rows would flash until it returns.
        relatedState = emptyList()
        viewModelScope.launch {
            metaState = MetaUiState.Loading
            try {
                // Metadata may live in a different addon than the catalog:
                // try every meta-capable addon until one succeeds.
                val meta = repo.getMetaFromAny(hintBaseUrl, type, id)
                if (meta == null) {
                    metaState = MetaUiState.Error("Couldn't find details for this title in any installed addon.")
                } else {
                    metaState = MetaUiState.Content(meta)
                    loadRelated(meta.type, meta.id)
                    if (meta.type == "series" && meta.videos.isNotEmpty()) {
                        val seasons = meta.videos.map { it.season }.distinct().sorted()
                        selectSeason(seasons.first(), meta)
                    } else {
                        loadStreams(meta.type, meta.id)
                    }
                }
            } catch (e: Exception) {
                metaState = MetaUiState.Error(e.message ?: "Couldn't load details")
            }
        }
    }

    fun selectSeason(season: Int, meta: MetaDetail) {
        selectedSeason = season
        val episodes = meta.videos.filter { it.season == season }
            .sortedWith(compareBy({ it.episode }, { it.title }))
        val pick = if (shuffleEpisodes) episodes.randomOrNull() else episodes.firstOrNull()
        if (pick != null) selectEpisode(pick)
    }

    /** Toggle shuffle mode; enabling it immediately jumps to a random episode. */
    fun updateShuffle(enabled: Boolean) {
        shuffleEpisodes = enabled
        repo.settings.shuffleEpisodes = enabled
        if (enabled) selectRandomEpisode()
    }

    /** Picks a random episode from the current season and selects it. */
    fun selectRandomEpisode() {
        val meta = (metaState as? MetaUiState.Content)?.meta ?: return
        val episodes = meta.videos.filter { it.season == selectedSeason }
        val pick = episodes.randomOrNull() ?: return
        selectEpisode(pick)
    }

    fun selectEpisode(video: Video) {
        selectedVideo = video
        val meta = (metaState as? MetaUiState.Content)?.meta ?: return
        loadStreams(meta.type, video.id)
    }

    fun retryLoad(repo: StreamRepository, addonId: String, type: String, metaId: String) {
        viewModelScope.launch {
            val baseUrl = repo.getAddonBaseUrl(addonId)
            load(baseUrl, type, metaId)
        }
    }

    /**
     * Streams may live in different addons than catalogs/metadata.
     * Collects the progressive merged flow: sources appear (and autoplay
     * fires) as soon as the first addon answers instead of waiting for
     * every addon — the fix for slow stream loading.
     */
    fun loadStreams(type: String, videoId: String) {
        streamsJob?.cancel()
        probeJob?.cancel()
        pickState = PickState.Idle
        streamsJob = viewModelScope.launch {
            streamsState = StreamsUiState.Loading
            var gotAny = false
            try {
                repo.streamResultsFlow(type, videoId).collect { result ->
                    if (result.streams.isNotEmpty()) {
                        gotAny = true
                        streamsState = StreamsUiState.Content(result.streams, result.subtitles, loadingMore = true)
                        maybeProbePick(result.streams)
                        if (pendingAutoPlay) {
                            pendingAutoPlay = false
                            playBestOrFirst()
                        }
                    }
                    // Empty intermediate emissions: stay on Loading, don't flash an error.
                }
                // v0.8.29 fix: transient failures (cold-start timeouts, dispatcher
                // contention) could empty the first pass while a manual retry
                // succeeded. Do automatic retries with backoff before showing
                // "no sources" so users never see a false empty state.
                var attempt = 0
                while (!gotAny && attempt < 3) {
                    attempt++
                    kotlinx.coroutines.delay(1500L * attempt)
                    repo.streamResultsFlow(type, videoId).collect { result ->
                        if (result.streams.isNotEmpty()) {
                            gotAny = true
                            streamsState = StreamsUiState.Content(result.streams, result.subtitles, loadingMore = true)
                            maybeProbePick(result.streams)
                            if (pendingAutoPlay) {
                                pendingAutoPlay = false
                                playBestOrFirst()
                            }
                        }
                    }
                }
                if (gotAny) {
                    // Collection is over: the Watch Now side-spinner stops,
                    // whatever is on screen is the final list.
                    val settled = streamsState as? StreamsUiState.Content
                    if (settled != null && settled.loadingMore) {
                        streamsState = settled.copy(loadingMore = false)
                    }
                }
                if (!gotAny) {
                    streamsState = StreamsUiState.Error("No playable sources found in any installed addon.")
                }
            } catch (e: Exception) {
                if (!gotAny) streamsState = StreamsUiState.Error(e.message ?: "Couldn't load sources")
            }
        }
    }

    /**
     * Starts (or restarts, when the merged list grew) the background Plavio Pick
     * probe. Keeps an already-found winner — no churn while sources stream in.
     */
    private fun maybeProbePick(streams: List<StreamItem>) {
        // Adaptive quality cap (user's order 2026-10-20): only when the user
        // left quality on "Auto".
        val cap = if (repo.settings.autoplayQuality == "Auto")
            repo.settings.adaptiveQualityCap() else null
        val found = pickState as? PickState.Found
        if (found != null) {
            // Keep the winner — UNLESS it violates the cap. A 4K pick could have
            // locked in from the first addon's early emission, before a 1080p
            // option from a slower addon ever arrived; then re-probe.
            val capRank = cap?.let { com.pulsestream.app.data.StreamProber.qualityRank(it) }
            if (capRank == null ||
                com.pulsestream.app.data.StreamProber.qualityRank(found.stream) <= capRank) return
        }
        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            pickState = PickState.Probing
            val best = StreamProber.findBest(streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }, isTv, cap)
            pickState = if (best != null) PickState.Found(best) else PickState.None
            // Warm the winner's connection so Play starts without the
            // DNS + TLS cost (shared pool — see PlaybackHttp).
            if (best != null) com.pulsestream.app.data.PlaybackHttp.warmUp(best.url)
        }
    }

    /**
     * Play button: always tries the fastest verified stream first.
     * If the background probe already found a winner, play it. If the probe
     * is still running, give it a brief moment, then play the winner or fall
     * back to the first available stream.
     *
     * fromStart (Nuvio "Start from beginning"): the same pick, but the
     * player ignores the saved resume position and the resume prompt.
     * Kept in [playFromStartRequest] so the pending-play path (streams
     * still loading when pressed) and the probe-wait launch carry it too.
     */
    private var playFromStartRequest = false

    fun onPlayPressed(fromStart: Boolean = false) {
        playFromStartRequest = fromStart
        when (val s = streamsState) {
            is StreamsUiState.Content -> playBestOrFirst()
            is StreamsUiState.Error -> { /* nothing playable — leave the error visible */ }
            else -> {
                pendingAutoPlay = true
                // Make sure a load is in flight for the current selection.
                val meta = (metaState as? MetaUiState.Content)?.meta
                if (meta != null && s is StreamsUiState.Idle) {
                    val videoId = if (meta.type == "series") selectedVideo?.id ?: meta.id else meta.id
                    loadStreams(meta.type, videoId)
                }
            }
        }
    }

    private fun playBestOrFirst() {
        val s = streamsState as? StreamsUiState.Content ?: return
        // Only consider playable streams - skip unresolved infohash: placeholders.
        // Debrid resolution replaces these with real HTTP links asynchronously.
        val playableStreams = s.streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
        if (playableStreams.isEmpty()) { pendingAutoPlay = true; return }
        // Settings > Streams autoplay: pick by the user's quality/size rule
        // instead of the probed fastest stream.
        if (repo.settings.streamAutoplay) {
            val ruled = pickAutoplayStream(playableStreams, repo.settings)
            if (ruled != null) {
                autoPlayEvent = AutoPlay(ruled, s.subtitles, playFromStartRequest)
                return
            }
            // No playable streams yet - wait for debrid resolution instead of
            // playing an unresolvable placeholder.
            return
        }
        val pick = pickState as? PickState.Found
        if (pick != null && !pick.stream.url.startsWith("infohash:")) {
            autoPlayEvent = AutoPlay(pick.stream, s.subtitles, playFromStartRequest)
            return
        }
        if (pickState is PickState.Probing) {
            viewModelScope.launch {
                withTimeoutOrNull(2500) { probeJob?.join() }
                val best = pickState as? PickState.Found
                val cur = streamsState as? StreamsUiState.Content ?: return@launch
                val curPlayable = cur.streams.filter { com.pulsestream.app.data.isPlayableLiveStreamUrl(it.url) }
                if (best != null && !best.stream.url.startsWith("infohash:")) autoPlayEvent = AutoPlay(best.stream, cur.subtitles, playFromStartRequest)
                else if (curPlayable.isNotEmpty()) autoPlayEvent = AutoPlay(curPlayable.first(), cur.subtitles, playFromStartRequest)
            }
            return
        }
        if (playableStreams.isNotEmpty()) autoPlayEvent = AutoPlay(playableStreams.first(), s.subtitles, playFromStartRequest)
    }

    fun consumeAutoPlay(): AutoPlay? {
        val e = autoPlayEvent
        autoPlayEvent = null
        // The flag is one-shot per press; never let it leak into a later play.
        playFromStartRequest = false
        return e
    }

    /**
     * "More like this" + "More from <actor>" rows. Needs an IMDb tt id
     * (Cinemeta/PlavioMeta ids); quietly empty for anything else.
     */
    fun loadRelated(type: String, metaId: String) {
        viewModelScope.launch {
            // Metadata flexibility (owner's order 2026-10-26): the rows
            // follow the addon that served this title — /related is
            // tried on every meta addon in preference order, with a
            // generic genre/cast fallback when none serves it.
            relatedState = repo.getRelated(
                type,
                metaId.substringBefore(":"),
                hintBaseUrl
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    addonId: String,
    type: String,
    metaId: String,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (PlaybackRequest) -> Unit = {},
    onOpenActor: (Int, String) -> Unit = { _, _ -> },
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit = { _, _, _ -> }
) {
    val vm: DetailViewModel = viewModel(factory = vmFactory { DetailViewModel(repository) })
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // TV-aware probing: the prober must send the same UA the player will.
    vm.isTv = rememberIsTv()

    // Resolve the addon base URL (from search cache or the database) as a hint,
    // then load. Metadata/streams may come from *other* installed addons.
    LaunchedEffect(addonId, type, metaId) {
        val baseUrl = repository.getAddonBaseUrl(addonId)
        vm.load(baseUrl, type, metaId)
    }

    // Play button: autoplay the first available stream as soon as it's ready.
    LaunchedEffect(vm.autoPlayEvent) {
        vm.consumeAutoPlay()?.let { autoplay ->
            // #12: streams may finish loading after the user already backed
            // out — never navigate to the player from a dead screen.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
            val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
            startPlayback(vm, repository, autoplay.stream, autoplay.subtitles, allStreams, onPlay, fromStart = autoplay.fromStart)
        }
    }

    fun retry() {
        vm.retryLoad(repository, addonId, type, metaId)
    }

    GlowBackground {
        when (val state = vm.metaState) {
            is MetaUiState.Loading -> {
                BackBar(onBack)
                LoadingState("Loading details…")
            }
            is MetaUiState.Error -> {
                BackBar(onBack)
                ErrorState(state.message) { retry() }
            }
            is MetaUiState.Content -> DetailContent(
                vm = vm,
                meta = state.meta,
                addonId = addonId,
                repository = repository,
                listsRepository = listsRepository,
                onBack = onBack,
                onPlay = onPlay,
                onPlayTrailer = onPlayTrailer,
                onOpenActor = onOpenActor,
                onOpenDetail = onOpenDetail
            )
        }
    }
}

/**
 * Builds a [PlaybackRequest] for a stream and hands it to the player.
 * Shared by autoplay, the Play button and tapping a stream card.
 */
private fun startPlayback(
    vm: DetailViewModel,
    repository: StreamRepository,
    stream: StreamItem,
    subtitles: List<Subtitle>,
    allStreams: List<StreamItem>,
    onPlay: () -> Unit,
    fromStart: Boolean = false
) {
    if (stream.url.startsWith("infohash:", true) || stream.url.startsWith("magnet:", true)) return
    val meta = (vm.metaState as? MetaUiState.Content)?.meta ?: return
    val isSeries = meta.type == "series" && vm.selectedVideo != null
    val title = if (isSeries) {
        val v = vm.selectedVideo!!
        "${meta.name} S${v.season}E${v.episode}"
    } else meta.name
    val ids = resolvePlaybackIds(meta, vm.selectedVideo)
    repository.pendingPlayback = PlaybackRequest(
        streamUrl = stream.url,
        headers = stream.headers,
        mimeType = stream.mimeType,
        title = title,
        poster = vm.selectedVideo?.thumbnail ?: meta.poster,
        videoId = vm.selectedVideo?.id ?: meta.id,
        subtitles = subtitles,
        imdbId = ids.imdbId,
        season = ids.season,
        episode = ids.episode,
        isMovie = ids.isMovie,
        type = meta.type,
        streamLabel = streamQualityLabel(stream),
        sourceName = stream.sourceName,
        streams = allStreams,
        episodes = meta.videos,
        metaId = meta.id,
        seriesName = meta.name.takeIf { !ids.isMovie },
        episodeTitle = vm.selectedVideo?.title,
        playFromStart = fromStart
    )
    onPlay()
}

@Composable
private fun BackBar(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(
    vm: DetailViewModel,
    meta: MetaDetail,
    addonId: String,
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onPlayTrailer: (PlaybackRequest) -> Unit = {},
    onOpenActor: (Int, String) -> Unit = { _, _ -> },
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val isSeries = meta.type == "series" && meta.videos.isNotEmpty()
    val seasons = rememberSeasonList(meta)
    val settings = repository.settings
    // Episode browser (Nuvio DetailEpisodeNavigator): search, sort,
    // Go-To, season view mode, blur-unwatched.
    var episodeQuery by rememberSaveable { mutableStateOf("") }
    var episodeSort by rememberSaveable { mutableStateOf("oldest") } // oldest | newest | airdate | unwatched
    var seasonViewMode by remember { mutableStateOf(settings.seasonViewMode) }
    var episodeActionVideo by remember { mutableStateOf<Video?>(null) }
    var goToOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    val episodesRailState = rememberLazyListState()
    val watchedKeys by com.pulsestream.app.data.WatchedMarks.keys.collectAsState()
    // Hoisted out of the LazyColumn DSL: the filter+sort used to re-run
    // on every recomposition of the (stream-driven) content lambda.
    val episodes = remember(meta.videos, vm.selectedSeason, episodeQuery, episodeSort, watchedKeys) {
        val base = meta.videos.filter { it.season == vm.selectedSeason }
        val filtered = if (episodeQuery.isBlank()) base else {
            val q = episodeQuery.trim().lowercase().replace(" ", "")
            base.filter { video ->
                video.title.lowercase().contains(q) ||
                    "e${video.episode}" == q || video.episode.toString() == q ||
                    "s${video.season}e${video.episode}" == q
            }
        }
        when (episodeSort) {
            "newest" -> filtered.sortedWith(compareByDescending({ it.episode }, { it.title }))
            "airdate" -> filtered.sortedByDescending { it.released.orEmpty() }
            "unwatched" -> filtered.sortedWith(
                compareBy(
                    { com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, it.season, it.episode) in watchedKeys },
                    { it.episode },
                    { it.title }
                )
            )
            else -> filtered.sortedWith(compareBy({ it.episode }, { it.title }))
        }
    }
    // Shared scope and watchlist state for hero buttons and play section
    val scope = rememberCoroutineScope()
    val watchKey = "$addonId|${meta.type}|${meta.id}"
    val watchItem by repository.observeWatchlistItem(watchKey).collectAsState(initial = null)
    // "Add to list" bottom sheet state
    var showAddToList by remember { mutableStateOf(false) }
    // Shared stream-browser state: addon tabs + filtered list stay in sync as
    // sources stream in progressively.
    val allStreams = (vm.streamsState as? StreamsUiState.Content)?.streams ?: emptyList()
    val allSubtitles = (vm.streamsState as? StreamsUiState.Content)?.subtitles ?: emptyList()
    // D-A pills (owner's pick 2026-10-24; two rows since 2026-10-26):
    // the stream-data strip sits directly below the hero in a
    // fixed-height slot, computed across the loaded streams (see
    // heroPills). Recomputed as streams stream in; the slot is
    // fixed-height, so late pills never shift the Watch Now button.
    val heroPillRows = remember(allStreams, allSubtitles) {
        heroPills(allStreams, allSubtitles)
    }
    val browserState = rememberStreamBrowserState(allStreams, repository.settings)
    // Mini floating source-picker card (the button next to Play opens this
    // instead of scrolling to the sources section).
    var sourcePickerOpen by remember { mutableStateOf(false) }
    // Full-page sources (long-press Play): title header + addon tabs + stream list.
    var fullPageSourceOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // Zero-edge top (owner's order 2026-10-25): the detail poster runs
    // to the physical top of the display, under the status bar - the
    // time/battery icons sit ON the poster. Same trick Home uses: the
    // column is measured one status-bar taller and placed shifted up
    // by the same amount. The D-A layout itself is unchanged; the
    // hero's top action row gets the inset back as padding so the
    // buttons stay exactly where they were, below the status icons.
    val detailDensity = LocalDensity.current
    val detailStatusDp = with(detailDensity) { WindowInsets.statusBars.getTop(detailDensity).toDp() }
    val detailStatusPx = with(detailDensity) { detailStatusDp.roundToPx() }
    // On TV, land D-pad focus on the Play button when the detail screen opens.
    val isTvDetail = rememberIsTv()
    val playButtonFocus = remember { FocusRequester() }
    var playFocusLanded by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val browsingPrivacy = remember(context) { BrowsingPrivacy(context) }
    // Ratings (Nuvio D5/M4): provider scores + your local star rating.
    val userRatings = remember(context) { UserRatings(context) }
    var userStars by remember(meta.id) { mutableStateOf(userRatings.getRating(meta.id)) }
    var ratingsSheetOpen by remember { mutableStateOf(false) }
    // Hero trailer autoplay (Nuvio D2): the first trailer autoplays muted in
    // the hero once resolved, fading in over the backdrop. Plavio's
    // TrailerStream carries only title + ytId, so there is no official/type
    // metadata to rank by — the first trailer is the candidate.
    val heroTrailer = meta.trailerStreams.firstOrNull()
    var heroTrailerUrl by remember(meta.id) { mutableStateOf<String?>(null) }
    var heroTrailerAudioUrl by remember(meta.id) { mutableStateOf<String?>(null) }
    var heroTrailerReady by remember(meta.id) { mutableStateOf(false) }
    var heroTrailerFinished by remember(meta.id) { mutableStateOf(false) }
    var heroMuted by remember(meta.id) { mutableStateOf(!settings.heroTrailerStartUnmuted) }
    LaunchedEffect(meta.id, heroTrailer?.ytId, settings.heroTrailerPlayback, settings.heroTrailerStartDelaySeconds) {
        heroTrailerUrl = null
        heroTrailerAudioUrl = null
        heroTrailerReady = false
        heroTrailerFinished = false
        val ytId = heroTrailer?.ytId ?: return@LaunchedEffect
        if (!settings.heroTrailerPlayback) return@LaunchedEffect
        val resolved = async {
            runCatching {
                withTimeoutOrNull(30_000) { TrailerResolver.resolvePlayback(ytId) }
            }.getOrNull()
        }
        val delaySeconds = settings.heroTrailerStartDelaySeconds
        if (delaySeconds > 0) delay(delaySeconds * 1000L)
        val playback = resolved.await()
        if (playback == null) {
            heroTrailerFinished = true
        } else {
            heroTrailerUrl = playback.videoUrl
            heroTrailerAudioUrl = playback.audioUrl
        }
    }
    // Stop the hero trailer when the user starts real playback.
    LaunchedEffect(vm.autoPlayEvent) {
        if (vm.autoPlayEvent != null) heroTrailerFinished = true
    }
    var omdbRatings by remember { mutableStateOf<List<OmdbRating>?>(null) }
    // Trakt community rating (Step 33): public endpoint, no login needed.
    var traktTitleRatings by remember { mutableStateOf<TraktApi.TraktTitleRatings?>(null) }
    LaunchedEffect(meta.id) {
        omdbRatings = null
        traktTitleRatings = null
        val key = settings.omdbApiKey
        val imdbId = meta.id.substringBefore(":").takeIf { it.startsWith("tt") }
        if (!key.isNullOrBlank() && imdbId != null) {
            omdbRatings = OmdbClient.fetchRatings(key, imdbId)
        }
        if (imdbId != null) {
            traktTitleRatings = runCatching {
                TraktApi().getTitleRatings(imdbId, meta.type)
            }.getOrNull()
        }
    }
    // Provider scores: OMDb's matrix when loaded, else the meta's IMDb value,
    // plus Trakt's community average when it has one.
    val providerRatings = remember(meta.imdbRating, omdbRatings, traktTitleRatings) {
        val base = if (!omdbRatings.isNullOrEmpty()) omdbRatings!!
            else meta.imdbRating?.takeIf { it.isNotBlank() }
                ?.let { listOf(OmdbRating("Internet Movie Database", it)) } ?: emptyList()
        val trakt = traktTitleRatings?.let {
            OmdbRating("Trakt", "%.1f/10".format(it.rating))
        }
        if (trakt != null) base + trakt else base
    }
    // The user's own Trakt rating (1–10, Step 33): fetched lazily when the
    // ratings sheet opens, so Detail never pays for a full ratings pull.
    var traktUserRating by remember(meta.id) { mutableStateOf<Int?>(null) }
    var traktRatingLoaded by remember(meta.id) { mutableStateOf(false) }
    val traktLinked = !settings.traktAccessToken.isNullOrBlank()
    LaunchedEffect(ratingsSheetOpen) {
        if (!ratingsSheetOpen || traktRatingLoaded) return@LaunchedEffect
        traktRatingLoaded = true
        val token = runCatching { TraktManager(settings).validAccessToken() }.getOrNull()
        val imdbId = meta.id.substringBefore(":").takeIf { it.startsWith("tt") }
        if (token != null && imdbId != null) {
            traktUserRating = runCatching {
                TraktApi().getMyRatings(token, meta.type)[imdbId]
            }.getOrNull()
        }
    }
    // Trakt comments (Step 33): most-liked first, API key only — no login
    // needed. A failed fetch hides the section entirely.
    var traktComments by remember(meta.id) { mutableStateOf<TraktApi.TraktCommentsPage?>(null) }
    var traktCommentsPage by remember(meta.id) { mutableStateOf(1) }
    var traktCommentsLoadingMore by remember { mutableStateOf(false) }
    var commentsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(meta.id) {
        traktComments = null
        traktCommentsPage = 1
        traktCommentsLoadingMore = false
        commentsExpanded = false
        val imdbId = meta.id.substringBefore(":").takeIf { it.startsWith("tt") }
        if (imdbId != null) {
            traktComments = runCatching {
                TraktApi().getComments(meta.type, imdbId, 1)
            }.getOrNull()
        }
    }
    var hideEpisodeDetails by remember { mutableStateOf(browsingPrivacy.hideUnrevealedEpisodes()) }
    var blurUnwatched by remember { mutableStateOf(browsingPrivacy.blurUnwatchedEpisodes()) }
    val episodeShowKey = "$addonId|${meta.type}|${meta.id}"
    var revealedEpisodeIds by remember(episodeShowKey) {
        mutableStateOf(browsingPrivacy.revealedEpisodes(episodeShowKey))
    }
    LaunchedEffect(isTvDetail, playFocusLanded) {
        if (isTvDetail && !playFocusLanded) {
            playFocusLanded = true
            delay(400)
            runCatching { playButtonFocus.requestFocus() }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minHeight = constraints.maxHeight + detailStatusPx,
                        maxHeight = constraints.maxHeight + detailStatusPx
                    )
                )
                layout(placeable.width, placeable.height) {
                    placeable.place(0, -detailStatusPx)
                }
            },
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            // D-A hero (owner's pick 2026-10-24, replacing Sample A):
            // the BACKDROP leads, edge-to-edge at 51% of the screen;
            // the poster rests as a small card on the fade at the
            // bottom-left, with title, facts line and genres beside it.
            // The stream-data pills live below the hero now, in a
            // fixed-height slot, so Watch Now never shifts. The action
            // row (back / watchlist / add-to-list) floats over the top,
            // unchanged. The fade keeps the cinematic navy of the
            // shipped heroes — it is what makes the white overlay text
            // read in both theme modes. No backdrop -> the poster itself
            // is the hero art and no small card is drawn.
            val heroHeight = (LocalConfiguration.current.screenHeightDp * 0.51f).dp
            val heroArt = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster
            val hasBackdrop = !meta.background.isNullOrBlank()
            val trailerUrl = heroTrailerUrl
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
            ) {
                SafePosterImage(
                    model = heroArt,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                // Hero trailer (Nuvio D2): once resolved, the trailer plays
                // over the backdrop and fades in when ready; the fade below
                // stays on top so the title text keeps reading.
                if (trailerUrl != null && !heroTrailerFinished) {
                    val trailerAlpha by animateFloatAsState(
                        targetValue = if (heroTrailerReady) 1f else 0f,
                        label = "heroTrailerAlpha"
                    )
                    Box(
                        Modifier.fillMaxSize().graphicsLayer { alpha = trailerAlpha }
                    ) {
                        HeroTrailerPlayer(
                            videoUrl = trailerUrl,
                            audioUrl = heroTrailerAudioUrl,
                            muted = heroMuted,
                            onReady = { heroTrailerReady = true },
                            onEnded = { heroTrailerFinished = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // The fade: clear until ~40%, then cinematic navy, so
                // the overlaid text reads in both theme modes.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.40f to Color.Transparent,
                            1.0f to Color(0xFF060B16).copy(alpha = 0.95f)
                        )
                    )
                )
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp + detailStatusDp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            scope.launch {
                                repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster)
                            }
                        },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            if (watchItem != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (watchItem != null) "Remove from watchlist" else "Add to watchlist",
                            tint = Color.White
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Hero trailer mute toggle: only while the trailer is
                    // playing in the hero.
                    if (trailerUrl != null && !heroTrailerFinished && heroTrailerReady) {
                        IconButton(
                            onClick = { heroMuted = !heroMuted },
                            modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                        ) {
                            Icon(
                                if (heroMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = if (heroMuted) "Unmute trailer" else "Mute trailer",
                                tint = Color.White
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = { showAddToList = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))
                    ) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = "Add to list",
                            tint = Color.White
                        )
                    }
                }
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // The poster rests as a small card on the fade's
                    // bottom-left — only when the hero art is the
                    // backdrop; in the poster fallback the hero IS the
                    // poster, so no card is drawn.
                    if (hasBackdrop && !meta.poster.isNullOrBlank()) {
                        SafePosterImage(
                            model = meta.poster,
                            contentDescription = null,
                            modifier = Modifier
                                .width(74.dp)
                                .aspectRatio(2f / 3f)
                                .shadow(12.dp, RoundedCornerShape(10.dp))
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            meta.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val factsLine = buildList {
                            meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(formatEpisodeDate(it)) }
                            formatRuntimeForDisplay(meta.runtime)?.let { add(it) }
                            meta.imdbRating?.takeIf { it.isNotBlank() }?.let { add("★ $it") }
                        }.joinToString("  •  ")
                        if (factsLine.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                com.pulsestream.app.ui.components.accentStarText(factsLine, LocalPalette.current.accent),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFC7D2DE),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (meta.genres.isNotEmpty()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                meta.genres.take(3).joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF8FA2B5),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Ratings row (Nuvio DetailRatingsRow): provider scores +
                        // your stars; tap opens the breakdown + Rate sheet.
                        Spacer(Modifier.height(6.dp))
                        DetailRatingsRow(
                            ratings = providerRatings,
                            userStars = userStars,
                            onClick = { ratingsSheetOpen = true }
                        )
                    }
                }
            }
        }
        item {
          Column {
            // D-A: the stream-data pills sit directly below the hero in
            // a FIXED-height slot (room for two pill lines) — when
            // streams land and pills appear, Watch Now below never
            // shifts (the same discipline the shipped build kept by
            // putting the pills inside the fixed hero box).
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(66.dp)
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp)
            ) {
                val (techPills, languagePills) = heroPillRows
                if (techPills.isNotEmpty() || languagePills.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // Row 1: the tech facts.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            techPills.forEach { pill -> HeroPillView(pill) }
                        }
                        // Row 2: every language — one pill each, the
                        // row scrolls sideways instead of hiding any
                        // behind a "+N" (owner's order 2026-10-26).
                        if (languagePills.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.horizontalScroll(rememberScrollState())
                            ) {
                                languagePills.forEach { pill -> HeroPillView(pill) }
                            }
                        }
                    }
                }
            }
            val canPlay = vm.streamsState !is StreamsUiState.Error
            // Resume: check if there's playback progress for this title
            val continueWatching by repository.continueWatching.collectAsState(initial = emptyList())
            val resumeState = continueWatching.find { it.videoId == meta.id || it.key.contains(meta.id) }
            val hasResume = resumeState != null && resumeState.positionMs > 0 && resumeState.durationMs > 0
            val resumeProgress = if (hasResume) resumeState!!.positionMs.toFloat() / resumeState!!.durationMs else 0f
            val playText = if (hasResume) "Continue Watching" else "Watch Now"
            // Play button: full width capsule. Tap = play, long-press = open sources.
            PlayButton(
                onClick = { vm.onPlayPressed() },
                onLongClick = { fullPageSourceOpen = true },
                enabled = canPlay,
                isLoading = vm.streamsState is StreamsUiState.Loading,
                loadingMore = (vm.streamsState as? StreamsUiState.Content)?.loadingMore == true,
                text = playText,
                resumeProgress = if (hasResume) resumeProgress else null,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).focusRequester(playButtonFocus)
            )
            Text(
                "Press & hold Watch Now for sources",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            )
            // Play from start (Nuvio DetailActions secondary action):
            // same stream pick, but the player ignores the saved resume
            // position and the resume prompt — always starts at 0:00.
            // Only shown when there is a position to ignore.
            if (hasResume) {
                TextButton(
                    onClick = { vm.onPlayPressed(fromStart = true) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Icon(
                        Icons.Filled.Replay,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Play from start",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
          }
        }
        if (!meta.description.isNullOrBlank()) {
            item {
                Column {
                    // Nuvio ExpandableDescription: 4 lines collapsed, expands in place.
                    ExpandableDescription(
                        text = meta.description!!,
                        collapsedMaxLines = 4,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                    )
                    if (meta.genres.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Row(
                            Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            meta.genres.take(4).forEach { genre ->
                                Text(
                                    genre,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
        // Trailers section: horizontal cards like "You May Like"
        if (meta.trailerStreams.isNotEmpty()) {
            item(key = "trailers") {
                Text(
                    "Trailers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meta.trailerStreams, key = { it.ytId }) { trailer ->
                        var resolving by remember { mutableStateOf(false) }
                        var trailerUnavailable by remember { mutableStateOf(false) }
                        Card(
                            modifier = Modifier
                                .width(160.dp)
                                .clickable(enabled = !resolving) {
                                    if (resolving) return@clickable
                                    resolving = true
                                    trailerUnavailable = false
                                    scope.launch {
                                        try {
                                            val playback = withTimeoutOrNull(30_000) {
                                                TrailerResolver.resolvePlayback(trailer.ytId)
                                            }
                                            if (playback != null) {
                                                heroTrailerFinished = true
                                                onPlayTrailer(
                                                    PlaybackRequest(
                                                        streamUrl = playback.videoUrl,
                                                        title = "${meta.name} — Trailer",
                                                        poster = meta.background ?: meta.poster,
                                                        videoId = "trailer_${trailer.ytId}",
                                                        isMovie = meta.type == "movie",
                                                        type = meta.type,
                                                        streamLabel = "TRAILER",
                                                        metaId = meta.id,
                                                        isTrailer = true,
                                                        mergeAudioUrl = playback.audioUrl
                                                    )
                                                )
                                            } else {
                                                trailerUnavailable = true
                                            }
                                        } finally {
                                            resolving = false
                                        }
                                    }
                                },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // YouTube thumbnail as poster
                                SafePosterImage(
                                    model = "https://i.ytimg.com/vi/${trailer.ytId}/hqdefault.jpg",
                                    contentDescription = trailer.title,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Dark overlay for play button visibility
                                Box(
                                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                if (resolving) {
                                    OrbitDots(modifier = Modifier.size(26.dp))
                                } else {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play trailer",
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                                }
                            }
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = trailer.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1
                                )
                                if (trailerUnavailable) {
                                    Text(
                                        "Trailer stream unavailable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        // Only actors with a TMDB person id navigate (keyless mode has names only).
        if (meta.cast.isNotEmpty()) {
            item(key = "cast") {
                Text(
                    "Cast",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meta.cast, key = { it.id.toString() + "_" + it.name }) { actor ->
                        // Metadata flexibility (2026-10-26): name-only
                        // cast (keyless addons) navigates too — the actor
                        // page falls back to search by name when no
                        // addon serves /person for the id.
                        val clickable = actor.id > 0 || actor.name.isNotBlank()
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(72.dp)
                                .tvFocusRing(RoundedCornerShape(12.dp))
                                .then(
                                    if (clickable) Modifier.clickable {
                                        onOpenActor(actor.id, actor.name)
                                    }
                                    else Modifier
                                )
                        ) {
                            SafePosterImage(
                                model = actor.photo,
                                contentDescription = actor.name,
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .liquidGlass(CircleShape)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = actor.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (clickable) TextPrimary else TextSecondary,
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Production (Nuvio DetailProductionSection): what the meta
        // actually carries — Director (PlavioMeta + Cinemeta), Writer
        // (Cinemeta only; PlavioMeta serves none), Network (series).
        // Nuvio's version is TMDB production-company logo chips; the
        // meta addons carry no companies, so no logos here.
        val productionRows = listOfNotNull(
            meta.director.takeIf { it.isNotEmpty() }?.let {
                (if (it.size > 1) "Directors" else "Director") to it.joinToString(", ")
            },
            meta.writer.takeIf { it.isNotEmpty() }?.let {
                (if (it.size > 1) "Writers" else "Writer") to it.joinToString(", ")
            },
            meta.network?.takeIf { it.isNotBlank() }?.let { "Network" to it }
        )
        if (productionRows.isNotEmpty()) {
            item(key = "production") {
                Text(
                    "Production",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    productionRows.forEach { (label, value) ->
                        Row {
                            Text(
                                label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextSecondary,
                                modifier = Modifier.width(80.dp)
                            )
                            Text(
                                value,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Trakt comments (Step 33, Nuvio TraktCommentsRepository): most-liked
        // first, spoiler-safe. Shows 3, expands to 20, then loads more pages.
        val commentsPage = traktComments
        if (commentsPage != null && commentsPage.items.isNotEmpty()) {
            item(key = "trakt_comments") {
                Text(
                    "Trakt comments (${commentsPage.itemCount})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val shown = if (commentsExpanded) commentsPage.items.take(20)
                    else commentsPage.items.take(3)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    shown.forEach { comment -> TraktCommentCard(comment) }
                    val canExpand = !commentsExpanded && commentsPage.items.size > 3
                    val canLoadMore = commentsExpanded &&
                        traktCommentsPage < commentsPage.pageCount
                    if (canExpand || canLoadMore) {
                        TextButton(
                            onClick = {
                                if (canExpand) {
                                    commentsExpanded = true
                                    return@TextButton
                                }
                                if (traktCommentsLoadingMore) return@TextButton
                                val imdbId = meta.id.substringBefore(":")
                                    .takeIf { it.startsWith("tt") } ?: return@TextButton
                                traktCommentsLoadingMore = true
                                scope.launch {
                                    val next = runCatching {
                                        TraktApi().getComments(meta.type, imdbId, traktCommentsPage + 1)
                                    }.getOrNull()
                                    if (next != null) {
                                        traktComments = commentsPage.copy(
                                            items = commentsPage.items + next.items,
                                            page = next.page,
                                            pageCount = next.pageCount,
                                            itemCount = next.itemCount
                                        )
                                        traktCommentsPage = next.page
                                    }
                                    traktCommentsLoadingMore = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (canExpand) "Show all ${commentsPage.itemCount} comments"
                                else "Load more comments",
                                color = LocalPalette.current.accent
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // ("You May Like" rail removed, owner's pick 2026-10-24:
        // PlavioMeta's "More like this" rows below replace it —
        // they work for every user, not only TMDB-key holders.)
        // (Available Streams button removed — long-press Play opens the source picker.)
        item {
            if (vm.streamsState is StreamsUiState.Error) {
                Text(
                    (vm.streamsState as StreamsUiState.Error).message,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
        if (isSeries) {
            item(key = "seasons") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Seasons",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    // Season view mode (Nuvio SeasonViewMode): poster row
                    // or compact text list, persisted.
                    IconButton(onClick = {
                        seasonViewMode = if (seasonViewMode == "text") "posters" else "text"
                        settings.seasonViewMode = seasonViewMode
                    }) {
                        Icon(
                            if (seasonViewMode == "text") Icons.Filled.GridView else Icons.Filled.List,
                            contentDescription = if (seasonViewMode == "text") "Poster view" else "List view",
                            tint = TextSecondary
                        )
                    }
                }
                if (seasonViewMode == "text") {
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        seasons.forEach { season ->
                            val count = meta.videos.count { it.season == season }
                            val watchedCount = meta.videos.count {
                                it.season == season &&
                                    com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, it.season, it.episode) in watchedKeys
                            }
                            val isSel = vm.selectedSeason == season
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSel) LocalPalette.current.accent.copy(alpha = 0.15f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .clickable { vm.selectSeason(season, meta) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (season == 0) "Specials" else "Season $season",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "$watchedCount/$count watched",
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                    items(seasons, key = { it }) { season ->
                        val isSel = vm.selectedSeason == season
                        val palette = LocalPalette.current
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(92.dp).clickable { vm.selectSeason(season, meta) }
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(2f / 3f)
                                    .shadow(
                                        if (isSel) 12.dp else 0.dp,
                                        RoundedCornerShape(14.dp),
                                        spotColor = palette.accent.copy(alpha = 0.55f),
                                        ambientColor = palette.accent.copy(alpha = 0.55f)
                                    )
                                    .then(
                                        if (isSel) Modifier.border(
                                            2.dp,
                                            Brush.linearGradient(listOf(palette.accent, palette.accent2)),
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier.border(
                                            1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(14.dp)
                                        )
                                    )
                                    .clip(RoundedCornerShape(14.dp))
                            ) {
                                SafePosterImage(
                                    model = meta.poster ?: meta.background,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                                        )
                                    )
                                )
                                Text(
                                    if (season == 0) "SPECIALS" else "SEASON $season",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 7.dp)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .then(
                                        if (isSel) Modifier.background(
                                            Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                                            RoundedCornerShape(50)
                                        ) else Modifier.background(
                                            Color.White.copy(alpha = 0.06f), RoundedCornerShape(50)
                                        )
                                    )
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    if (season == 0) "Specials" else "Season $season",
                                    color = if (isSel) Color(0xFF04140B) else TextSecondary,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                }
            }
            item(key = "episodes_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Episodes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { vm.updateShuffle(!vm.shuffleEpisodes) }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = if (vm.shuffleEpisodes) "Shuffle on" else "Shuffle off",
                            tint = if (vm.shuffleEpisodes) LocalPalette.current.accent else TextSecondary
                        )
                    }
                    IconButton(onClick = { sortOpen = true }) {
                        Icon(
                            Icons.Filled.Sort,
                            contentDescription = "Sort episodes",
                            tint = TextSecondary
                        )
                    }
                    IconButton(onClick = { goToOpen = true }) {
                        Icon(
                            Icons.Filled.NearMe,
                            contentDescription = "Go to episode",
                            tint = TextSecondary
                        )
                    }
                    if (!isTvDetail) {
                        FilterChip(
                            selected = hideEpisodeDetails,
                            onClick = {
                                val enabled = !hideEpisodeDetails
                                hideEpisodeDetails = enabled
                                browsingPrivacy.setHideUnrevealedEpisodes(enabled)
                            },
                            label = { Text("Hide spoilers") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = blurUnwatched,
                            onClick = {
                                val enabled = !blurUnwatched
                                blurUnwatched = enabled
                                browsingPrivacy.setBlurUnwatchedEpisodes(enabled)
                            },
                            label = { Text("Blur unwatched") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
            // Episode search (Nuvio DetailEpisodeNavigator): filters the
            // rail by title or episode number ("12", "e12", "s1e2").
            item(key = "episodes_search") {
                OutlinedTextField(
                    value = episodeQuery,
                    onValueChange = { episodeQuery = it },
                    label = { Text("Search episodes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                    trailingIcon = {
                        if (episodeQuery.isNotEmpty()) {
                            IconButton(onClick = { episodeQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TextSecondary)
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            item(key = "episodes_rail") {
                LazyRow(
                    state = episodesRailState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(episodes, key = { index, video -> "${video.id}_$index" }) { _, video ->
                        val selected = vm.selectedVideo?.id == video.id
                        val episodeRevealed = video.id in revealedEpisodeIds
                        val hideDetails = BrowsingPrivacy.shouldHideEpisodeDetails(hideEpisodeDetails, episodeRevealed)
                        val isWatched = com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys
                        EpisodeCardA(
                            video = video,
                            selected = selected,
                            hideDetails = hideDetails,
                            isWatched = isWatched,
                            spoilerMode = hideEpisodeDetails,
                            isTv = isTvDetail,
                            blurThumbnail = blurUnwatched && !isWatched,
                            onClick = { vm.selectEpisode(video) },
                            onLongClick = { episodeActionVideo = video },
                            onReveal = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, true) },
                            onHide = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, false) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        // Show Details — glass fact tiles (owner's Sample A pick, 2026-10-24).
        // Only facts the meta actually carries are shown.
        val detailTiles = buildDetailTiles(meta)
        if (detailTiles.size >= 2) {
            item(key = "show_details") {
                Column {
                    Text(
                        "Show Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        detailTiles.chunked(3).forEach { rowTiles ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                rowTiles.forEach { (label, value) ->
                                    Column(
                                        modifier = Modifier.weight(1f)
                                            .liquidGlass(RoundedCornerShape(14.dp))
                                            .padding(horizontal = 11.dp, vertical = 10.dp)
                                    ) {
                                        Text(
                                            label.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 1.2.sp,
                                            maxLines = 1
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            value,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(3 - rowTiles.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // "More like this" + "More from <actor>" rows from PlavioMeta.
        // Quietly absent when the endpoint has nothing (e.g. keyless mode).
        vm.relatedState.forEach { section ->
            item(key = "related_${section.id}") {
                com.pulsestream.app.ui.components.SectionTitle(section.title)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.metas.distinctBy { it.id }, key = { it.id }) { related ->
                        com.pulsestream.app.ui.components.PosterCard(
                            meta = related,
                            // launchSingleTop-style: tapping the title that's already
                            // open is a no-op, so the backstack never stacks the
                            // same detail destination twice.
                            onClick = {
                                if (related.id != meta.id || related.type != meta.type) {
                                    onOpenDetail(addonId, related.type, related.id)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // Mini floating source-picker card: same-size button next to Play opens
    // this with a springy pop instead of scrolling to the sources section.
    // The popup gets the full stream list and runs its own per-addon tabs
    // (the inline section below keeps the shared "All" browser state).
    val streamsContent = vm.streamsState as? StreamsUiState.Content
    // Download confirmation dialog state
    var downloadStream by remember { mutableStateOf<StreamItem?>(null) }
    SourcePickerPopup(
        visible = sourcePickerOpen,
        streams = allStreams,
        loading = vm.streamsState is StreamsUiState.Loading,
        errorMessage = (vm.streamsState as? StreamsUiState.Error)?.message,
        onDismiss = { sourcePickerOpen = false },
        onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id) },
        onPick = { stream ->
            sourcePickerOpen = false
            if (streamsContent != null) {
                startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
            }
        },
        onDownload = { stream -> downloadStream = stream }
    )

    // Source picker (long-press Play): Bottom Sheet (Sample M;
    // owner's pick v0.12.14) — slides over the detail page, which
    // stays dimmed behind it. Same picker body, sheet mode.
    if (fullPageSourceOpen) {
      ModalBottomSheet(
        onDismissRequest = { fullPageSourceOpen = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = LocalPalette.current.background,
        dragHandle = { BottomSheetDefaults.DragHandle() }
      ) {
        FullPageSourcePicker(
            inSheet = true,
            meta = meta,
            streams = allStreams,
            loading = vm.streamsState is StreamsUiState.Loading,
            errorMessage = (vm.streamsState as? StreamsUiState.Error)?.message,
            onBack = { fullPageSourceOpen = false },
            onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id) },
            onPick = { stream ->
                fullPageSourceOpen = false
                if (streamsContent != null) {
                    startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
                }
            },
            onDownload = { stream -> downloadStream = stream }
        )
      }
    }

    // Download confirmation dialog
    downloadStream?.let { stream ->
        DownloadConfirmDialog(
            stream = stream,
            title = meta.name ?: "Video",
            onDismiss = { downloadStream = null }
        )
    }

    // Ratings sheet (Nuvio DetailRatingsSheet + UserRatingSheet): provider
    // breakdown + your local star rating.
    if (ratingsSheetOpen) {
        RatingsSheet(
            title = meta.name,
            ratings = providerRatings,
            userStars = userStars,
            omdbLoaded = omdbRatings != null,
            onRate = { stars ->
                if (stars == null) userRatings.clearRating(meta.id)
                else userRatings.setRating(meta.id, stars)
                userStars = stars
            },
            traktUserRating = traktUserRating,
            traktLinked = traktLinked,
            onTraktRate = { rating ->
                val imdbId = meta.id.substringBefore(":").takeIf { it.startsWith("tt") }
                    ?: return@RatingsSheet
                scope.launch {
                    val token = runCatching {
                        TraktManager(settings).validAccessToken()
                    }.getOrNull() ?: return@launch
                    val ok = if (rating == null) {
                        TraktApi().removeRating(token, meta.type, imdbId, meta.name ?: "")
                    } else {
                        TraktApi().setRating(token, meta.type, imdbId, meta.name ?: "", rating)
                    }
                    if (ok) traktUserRating = rating
                }
            },
            onDismiss = { ratingsSheetOpen = false }
        )
    }

    // "Add to list" bottom sheet
    if (showAddToList) {
        AddToListSheet(
            listsRepository = listsRepository,
            addonId = addonId,
            type = meta.type,
            metaId = meta.id,
            name = meta.name ?: "Video",
            poster = meta.poster,
            onDismiss = { showAddToList = false }
        )
    }

    // Episode watched action sheet (Nuvio EpisodeWatchedActionSheet):
    // long-press an episode card. Mark watched/unwatched for the episode
    // or the whole season. Rate arrives with Step 28 (per-title stars).
    episodeActionVideo?.let { video ->
        val isWatched = com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys
        ModalBottomSheet(
            onDismissRequest = { episodeActionVideo = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF14141F)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
            ) {
                Text(
                    "S${video.season}E${video.episode} · ${video.title}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                EpisodeActionRow(
                    label = if (isWatched) "Mark episode unwatched" else "Mark episode watched",
                    icon = if (isWatched) Icons.Filled.CheckCircleOutline else Icons.Filled.CheckCircle,
                    onClick = {
                        episodeActionVideo = null
                        scope.launch {
                            repository.setEpisodeWatched(meta.id, video.season, video.episode, !isWatched)
                        }
                    }
                )
                EpisodeActionRow(
                    label = "Mark season watched",
                    icon = Icons.Filled.PlaylistAddCheck,
                    onClick = {
                        episodeActionVideo = null
                        scope.launch {
                            repository.setSeasonWatched(meta.id, video.season, meta.videos, true)
                        }
                    }
                )
                EpisodeActionRow(
                    label = "Mark season unwatched",
                    icon = Icons.Filled.PlaylistRemove,
                    onClick = {
                        episodeActionVideo = null
                        scope.launch {
                            repository.setSeasonWatched(meta.id, video.season, meta.videos, false)
                        }
                    }
                )
            }
        }
    }

    // Go-To sheet (Nuvio DetailEpisodeNavigator): jump to a season +
    // episode number, selecting it and scrolling the rail to it.
    if (goToOpen) {
        var goToSeason by remember { mutableStateOf(vm.selectedSeason ?: seasons.firstOrNull() ?: 1) }
        var goToEpisodeText by remember { mutableStateOf("") }
        ModalBottomSheet(
            onDismissRequest = { goToOpen = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = Color(0xFF14141F)
        ) {
            Column(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
            ) {
                Text(
                    "Go to episode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    "Jump to a season and episode number.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    items(seasons, key = { it }) { season ->
                        FilterChip(
                            selected = goToSeason == season,
                            onClick = { goToSeason = season },
                            label = { Text(if (season == 0) "Specials" else "S$season") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                                selectedLabelColor = TextPrimary,
                                containerColor = Color.White.copy(alpha = 0.06f),
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
                OutlinedTextField(
                    value = goToEpisodeText,
                    onValueChange = { goToEpisodeText = it.filter { c -> c.isDigit() }.take(3) },
                    label = { Text("Episode number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val epNum = goToEpisodeText.toIntOrNull() ?: return@Button
                        val target = meta.videos.firstOrNull { it.season == goToSeason && it.episode == epNum }
                            ?: return@Button
                        goToOpen = false
                        if (vm.selectedSeason != goToSeason) vm.selectSeason(goToSeason, meta)
                        vm.selectEpisode(target)
                        scope.launch {
                            val index = episodes.indexOfFirst { it.id == target.id }
                            if (index >= 0) episodesRailState.scrollToItem(index)
                        }
                    },
                    enabled = goToEpisodeText.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Go") }
            }
        }
    }

    // Sort dialog (Nuvio EpisodeSortOrder): oldest/newest first,
    // newest aired, unwatched first. Highest-rated is not offered —
    // per-episode ratings don't exist in the meta.
    if (sortOpen) {
        AlertDialog(
            onDismissRequest = { sortOpen = false },
            title = { Text("Sort episodes", color = TextPrimary) },
            text = {
                Column {
                    listOf(
                        "oldest" to "Oldest first",
                        "newest" to "Newest first",
                        "airdate" to "Newest aired",
                        "unwatched" to "Unwatched first"
                    ).forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { episodeSort = value; sortOpen = false }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = episodeSort == value,
                                onClick = { episodeSort = value; sortOpen = false },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { sortOpen = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun rememberSeasonList(meta: MetaDetail): List<Int> {
    return meta.videos.map { it.season }.distinct().sorted()
}

/** One row of the episode watched action sheet: icon + label, full-width tap. */
@Composable
private fun EpisodeActionRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, style = MaterialTheme.typography.bodyLarge)
    }
}

// ---------------------------------------------------------------------------
// Hero stat tiles (v0.12.4, Sample C)
// ---------------------------------------------------------------------------

/** Display names scanned for in stream text, from the app's canonical language list. */
private val HERO_LANGUAGE_NAMES: List<String> = SUBTITLE_LANGUAGE_OPTIONS.map { it.second }

/** Premium-first ordering for audio format tags. */
private val HERO_AUDIO_RANK: List<String> = listOf(
    "Atmos", "TrueHD", "DTS-HDMA", "DTS-HD", "DTS", "DD+", "DD5.1", "DD", "Dolby", "AAC5.1", "AAC"
)

/** Value accent for a stat tile: gold rating, mint languages, plain rest. */
private enum class HeroTileKind { PLAIN, GOLD, MINT }

/** One Sample E hero fact row: a small-caps light label + a bold value. */
private data class HeroFactRow(
    val label: String,
    val value: String,
    val kind: HeroTileKind = HeroTileKind.PLAIN
)

@Composable
private fun HeroFactRowView(fact: HeroFactRow, shadow: Shadow) {
    val valueColor = when (fact.kind) {
        HeroTileKind.GOLD -> Color(0xFFFFD166)
        HeroTileKind.MINT -> LocalPalette.current.accent
        HeroTileKind.PLAIN -> TextPrimary
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            fact.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp, shadow = shadow),
            color = TextSecondary,
            maxLines = 1
        )
        Spacer(Modifier.width(8.dp))
        Text(
            fact.value,
            style = MaterialTheme.typography.titleSmall.copy(shadow = shadow),
            fontWeight = FontWeight.ExtraBold,
            color = valueColor,
            maxLines = 1
        )
    }
}

/** ISO 639-2 → 639-1 for the subtitle codes addons most often print. */
private val SUBTITLE_CODE3_TO_2: Map<String, String> = mapOf(
    "eng" to "EN", "hin" to "HI", "urd" to "UR", "ara" to "AR", "spa" to "ES",
    "fra" to "FR", "fre" to "FR", "deu" to "DE", "ger" to "DE", "ita" to "IT",
    "por" to "PT", "rus" to "RU", "jpn" to "JA", "kor" to "KO", "zho" to "ZH",
    "chi" to "ZH", "tam" to "TA", "tel" to "TE", "mar" to "MR", "ben" to "BN",
    "pan" to "PA", "guj" to "GU", "tur" to "TR", "per" to "FA", "fas" to "FA",
    "ind" to "ID", "msa" to "MS", "may" to "MS", "tha" to "TH", "vie" to "VI",
    "ukr" to "UK", "pol" to "PL", "nld" to "NL", "dut" to "NL"
)

/** Short display code ("EN") for a subtitle language tag of any shape. */
private fun subtitleDisplayCode(raw: String): String {
    val code = raw.trim().lowercase()
    if (code.length == 2) return code.uppercase()
    SUBTITLE_CODE3_TO_2[code]?.let { return it }
    // Some addons print full names ("English") instead of codes.
    SUBTITLE_LANGUAGE_OPTIONS.firstOrNull { it.second.equals(code, ignoreCase = true) }
        ?.let { return it.first.uppercase() }
    return code.take(3).uppercase()
}

/** One Sample A hero pill: [hot] is the mint-highlighted quality pill. */
private data class HeroPill(val text: String, val hot: Boolean = false)

/**
 * Every audio language the loaded streams offer, in display order
 * (English and Hindi lead — the owner's most-wanted pair — the rest
 * follow the canonical list order). Stream name/title/filename text
 * is the only place addons print languages, word-matched against the
 * canonical names. Untruncated: the pills' second row shows them all.
 */
private fun heroLanguageNames(streams: List<StreamItem>): List<String> {
    val haystack = streams.joinToString("\n") { stream ->
        listOfNotNull(stream.title, stream.name, stream.filename).joinToString(" ")
    }
    if (haystack.isBlank()) return emptyList()
    val found = HERO_LANGUAGE_NAMES.filter { name ->
        Regex("""\b${Regex.escape(name)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(haystack)
    }
    val lead = listOf("English", "Hindi")
    return lead.filter { it in found } + found.filter { it !in lead }
}

/** Display code for a language name ("English" -> "EN"). */
private fun heroLanguageCode(name: String): String =
    SUBTITLE_LANGUAGE_OPTIONS.firstOrNull { it.second == name }?.first?.uppercase()
        ?: name.take(2).uppercase()

/**
 * D-A pills (owner's pick 2026-10-24; two-row split by his order
 * 2026-10-26): the stream-data strip directly below the detail hero.
 * Row 1 = the tech facts — best quality first (mint), Dolby Vision /
 * HDR when any stream carries it, top audio, subtitle count. Row 2 =
 * every audio language, one pill each, nothing hidden behind a "+N".
 * Derived from the same loaded streams as [heroFactRows]; the strip's
 * slot is fixed-height, so pills appearing as streams land never move
 * the Watch Now button. Absent data = no pill.
 */
private fun heroPills(
    streams: List<StreamItem>,
    subtitles: List<Subtitle>
): Pair<List<HeroPill>, List<HeroPill>> {
    if (streams.isEmpty()) return emptyList<HeroPill>() to emptyList()
    val facts = heroFactRows(streams, subtitles).associateBy { it.label }
    val row1 = mutableListOf<HeroPill>()
    facts["Best quality"]?.let { row1 += HeroPill(it.value, hot = true) }
    // DV / HDR live only in stream text — same haystack the facts use.
    val haystack = streams.joinToString("\n") { stream ->
        listOfNotNull(stream.title, stream.name, stream.filename).joinToString(" ")
    }
    when {
        Regex("""dolby\s*vision|\bdv\b""", RegexOption.IGNORE_CASE).containsMatchIn(haystack) ->
            row1 += HeroPill("Dolby Vision")
        Regex("""\bhdr10\+?\b""", RegexOption.IGNORE_CASE).containsMatchIn(haystack) ->
            row1 += HeroPill("HDR")
    }
    facts["Top audio"]?.let { row1 += HeroPill(it.value) }
    facts["Subtitles"]?.let { row1 += HeroPill("+${it.value} subs") }
    val row2 = heroLanguageNames(streams).map { HeroPill(heroLanguageCode(it)) }
    return row1 to row2
}

@Composable
private fun HeroPillView(pill: HeroPill) {
    val palette = LocalPalette.current
    Text(
        pill.text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (pill.hot) palette.accent else Color.White,
        maxLines = 1,
        modifier = Modifier
            .background(
                if (pill.hot) palette.accent.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (pill.hot) palette.accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.16f),
                RoundedCornerShape(50)
            )
            .padding(horizontal = 9.dp, vertical = 3.dp)
    )
}


/**
 * Fact rows for the detail hero's right-side stack (v0.12.5, Sample E —
 * replaces the v0.12.4 stat tiles; same computations): what the loaded
 * addons offer for this title, known BEFORE pressing play, computed
 * across the loaded streams — best quality, top audio format, audio
 * languages (stream name/title/filename text is the only place addons
 * print languages, word-matched against the canonical names,
 * English/Hindi leading as in v0.12.3), subtitle count from the stream
 * result, and the stream / addon counts. The IMDb rating is NOT a row
 * here — it sits in the hero's bottom-left meta line. Absent data
 * yields no row — never a placeholder.
 */
private fun heroFactRows(
    streams: List<StreamItem>,
    subtitles: List<Subtitle>
): List<HeroFactRow> {
    if (streams.isEmpty()) return emptyList()
    val rows = mutableListOf<HeroFactRow>()

    availableStreamQualities(streams).firstOrNull()?.let { quality ->
        rows += HeroFactRow("Best quality", if (quality == "4K") "4K UHD" else quality)
    }

    val topAudio = streams.mapNotNull { parseStreamInfo(it).audio }.distinct()
        .minByOrNull { tag ->
            HERO_AUDIO_RANK.indexOfFirst { tag.equals(it, ignoreCase = true) }
                .let { rank -> if (rank < 0) Int.MAX_VALUE else rank }
        }
    topAudio?.let { rows += HeroFactRow("Top audio", it) }

    val ordered = heroLanguageNames(streams)
    if (ordered.isNotEmpty()) {
        val codes = ordered.map { heroLanguageCode(it) }
        val extra = if (ordered.size > 2) " +${ordered.size - 2}" else ""
        rows += HeroFactRow(
            "Languages",
            codes.take(2).joinToString(" · ") + extra,
            HeroTileKind.MINT
        )
    }

    val subCodes = subtitles.map { subtitleDisplayCode(it.lang) }.distinct()
    if (subCodes.isNotEmpty()) {
        rows += HeroFactRow("Subtitles", "${subCodes.size}")
    }

    val addonCount = streams.map { it.addonKey() }.distinct().size
    rows += HeroFactRow("Streams · $addonCount addons", "${streams.size}")
    return rows
}

private data class PlaybackIds(
    val imdbId: String,
    val season: Int,
    val episode: Int,
    val isMovie: Boolean
)

/**
 * Cinemeta ids are IMDb ids ("tt1234567"); series episode ids look like
 * "tt1234567:1:2". Everything downstream (IntroDB, subtitle search) wants
 * the bare IMDb id plus season/episode.
 */
/**
 * Hero trailer player (Nuvio `HeroTrailerPlayerSurface`): a bare ExoPlayer
 * with no controls, FIT so the trailer fills the hero box. Video-only
 * YouTube qualities are merged with their separate audio track, the same
 * merge the full player applies via `PlaybackRequest.mergeAudioUrl`.
 */
@Composable
private fun HeroTrailerPlayer(
    videoUrl: String,
    audioUrl: String?,
    muted: Boolean,
    onReady: () -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val player = remember(videoUrl, audioUrl) {
        ExoPlayer.Builder(context).build().apply {
            val factory = DefaultMediaSourceFactory(context)
            val videoItem = MediaItem.fromUri(videoUrl)
            val source = if (audioUrl != null) {
                MergingMediaSource(
                    factory.createMediaSource(videoItem),
                    factory.createMediaSource(MediaItem.fromUri(audioUrl))
                )
            } else {
                factory.createMediaSource(videoItem)
            }
            setMediaSource(source)
            repeatMode = Player.REPEAT_MODE_OFF
            volume = if (muted) 0f else 1f
            playWhenReady = true
            prepare()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) onReady()
                if (state == Player.STATE_ENDED) onEnded()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).also { pv ->
                pv.player = player
                pv.useController = false
                pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                pv.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { pv -> pv.player = player },
        modifier = modifier
    )
}

/**
 * Ratings row (Nuvio `DetailRatingsRow`): IMDb / Rotten Tomatoes /
 * Metacritic scores in one line, plus your stars when rated. Always
 * rendered — with no scores it reads "Rate this title" so the Rate
 * action stays reachable.
 */
@Composable
private fun DetailRatingsRow(
    ratings: List<OmdbRating>,
    userStars: Int?,
    onClick: () -> Unit
) {
    val shortLabel = mapOf(
        "Internet Movie Database" to "IMDb",
        "Rotten Tomatoes" to "RT",
        "Metacritic" to "Metacritic",
        "Trakt" to "Trakt"
    )
    val parts = ratings.mapNotNull { rating ->
        shortLabel[rating.source]?.let { label -> "$label ${rating.value}" }
    }.toMutableList()
    if (userStars != null) {
        parts += "You ${"★".repeat(userStars)}${"☆".repeat(5 - userStars)}"
    }
    val text = if (parts.isNotEmpty()) parts.joinToString("  •  ") else "★ Rate this title"
    Text(
        text = com.pulsestream.app.ui.components.accentStarText(text, LocalPalette.current.accent),
        style = MaterialTheme.typography.bodyMedium,
        color = Color(0xFFC7D2DE),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * One Trakt comment (Step 33, Nuvio TraktCommentsRepository): author,
 * likes, the commenter's own rating when present. Spoilers stay hidden
 * behind a tap — the [spoiler] markup is already stripped from the text.
 */
@Composable
private fun TraktCommentCard(comment: TraktApi.TraktComment) {
    var revealed by remember(comment.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1A1A28))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                comment.author,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (comment.rating != null) {
                Text(
                    "★ ${comment.rating}/10",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFFFC107),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            Text(
                "${comment.likes} likes",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )
        }
        if (comment.spoiler && !revealed) {
            TextButton(onClick = { revealed = true }) {
                Text("Spoiler — tap to reveal", color = TextSecondary)
            }
        } else {
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC7D2DE),
                maxLines = 6,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Ratings bottom sheet (Nuvio `DetailRatingsSheet` + `UserRatingSheet`):
 * per-source scores, then your local 1–5 star rating — tap a star to
 * set it, tap the same star again (or Clear) to remove it. The rating
 * persists locally and shows on the row on revisit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingsSheet(
    title: String,
    ratings: List<OmdbRating>,
    userStars: Int?,
    omdbLoaded: Boolean,
    onRate: (Int?) -> Unit,
    traktUserRating: Int? = null,
    traktLinked: Boolean = false,
    onTraktRate: ((Int?) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
        containerColor = Color(0xFF14141F)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Text(
                "Ratings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                title,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            if (ratings.isEmpty()) {
                Text(
                    "No provider scores for this title yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            } else {
                ratings.forEach { rating ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            rating.source,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            rating.value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }
            if (!omdbLoaded) {
                Text(
                    "Add an OMDb key in Settings > Integrations for Rotten Tomatoes and Metacritic scores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Your rating",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Row(
                Modifier.padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                (1..5).forEach { star ->
                    val filled = userStars != null && star <= userStars
                    IconButton(
                        onClick = { onRate(if (userStars == star) null else star) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            if (filled) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "Rate $star star${if (star > 1) "s" else ""}",
                            tint = if (filled) Color(0xFFFFC107) else TextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            if (userStars != null) {
                TextButton(onClick = { onRate(null) }) {
                    Text("Clear rating", color = TextSecondary)
                }
            }
            // Trakt rating (Step 33, Nuvio TraktRatingsProvider): the user's
            // own 1–10 rating on Trakt, set/removed from here when linked.
            if (traktLinked && onTraktRate != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Trakt rating",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onTraktRate(((traktUserRating ?: 1) - 1).coerceAtLeast(1)) },
                        enabled = (traktUserRating ?: 0) > 1,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = "Lower Trakt rating",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Text(
                        "${traktUserRating ?: "—"}/10",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    IconButton(
                        onClick = { onTraktRate(((traktUserRating ?: 0) + 1).coerceAtMost(10)) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Raise Trakt rating",
                            tint = TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    if (traktUserRating != null) {
                        TextButton(onClick = { onTraktRate(null) }) {
                            Text("Remove", color = TextSecondary)
                        }
                    }
                }
                Text(
                    "1–10 on Trakt, synced to your account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun resolvePlaybackIds(meta: MetaDetail, video: Video?): PlaybackIds {
    val vid = video?.id ?: meta.id
    return PlaybackIds(
        imdbId = vid.substringBefore(":"),
        season = video?.season ?: 0,
        episode = video?.episode ?: 0,
        isMovie = meta.type == "movie"
    )
}


/** Rounded-rectangle shape for the picker card — follows the app theme. */
private val PickerShape = RoundedCornerShape(20.dp)

/**
 * Per-addon tab state for the source picker. Unlike the shared inline
 * [com.pulsestream.app.ui.components.StreamBrowserState] (which has an "All"
 * tab), the picker shows one tab per addon and selects the first addon by
 * default.
 */
@Stable
private class PickerTabState(
    val tabs: List<StreamTab>,
    val selectedKey: String?,
    val filtered: List<StreamItem>,
    val onSelect: (String) -> Unit
)

@Composable
private fun rememberPickerTabState(streams: List<StreamItem>): PickerTabState {
    var selectedKey by remember { mutableStateOf<String?>(null) }
    // groupBy preserves first-encounter order; the stream list arrives
    // prioritized (starred addons first), so the first tab is the top addon.
    val tabs = remember(streams) {
        streams.groupBy { it.addonKey() }
            .map { (key, list) -> StreamTab(key, key, list.size) }
    }
    val safeKey = if (tabs.any { it.key == selectedKey }) selectedKey else tabs.firstOrNull()?.key
    val filtered = remember(streams, safeKey) {
        if (safeKey == null) emptyList() else streams.filter { it.addonKey() == safeKey }
    }
    return remember(tabs, safeKey, filtered) {
        PickerTabState(tabs, safeKey, filtered) { selectedKey = it }
    }
}

/**
 * Mini floating source-picker card. The same-size button next to Play pops
 * this open with a springy scale+fade+rise animation; tapping the dimmed
 * scrim or the close button dismisses it the same way in reverse.
 *
 * The card is a plain rounded rectangle in the app theme's panel color.
 * A tab row lists one tab per addon ("Name · count"); tapping a tab filters
 * the list to that addon's streams. On stream failure the REAL error is shown
 * with a working Retry instead of the generic "no sources" line.
 */
@Composable
private fun SourcePickerPopup(
    visible: Boolean,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    if (rememberIsTv()) {
        // TV: show the picker in its OWN window (a Compose Dialog), exactly like
        // Nuvio TV does. A same-window overlay leaves D-pad focus on the screen
        // underneath; a dialog window keeps every key press inside the popup,
        // Back dismisses it, and focus returns to the Source button afterwards.
        if (visible) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = onDismiss,
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                SourcePickerOverlay(true, streams, loading, errorMessage, onDismiss, onRetry, onPick, onDownload)
            }
        }
    } else {
        SourcePickerOverlay(visible, streams, loading, errorMessage, onDismiss, onRetry, onPick, onDownload)
    }
}

@Composable
private fun SourcePickerOverlay(
    visible: Boolean,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    val springy = androidx.compose.animation.core.spring<Float>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
    )
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(
            animationSpec = androidx.compose.animation.core.tween(180)
        ),
        exit = androidx.compose.animation.fadeOut(
            animationSpec = androidx.compose.animation.core.tween(150)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .focusProperties { canFocus = false } // scrim must never take D-pad focus
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.scaleIn(
                    initialScale = 0.82f, animationSpec = springy
                ) + androidx.compose.animation.fadeIn(
                    animationSpec = androidx.compose.animation.core.tween(160)
                ) + androidx.compose.animation.slideInVertically(
                    initialOffsetY = { it / 6 },
                    animationSpec = androidx.compose.animation.core.tween(220)
                ),
                exit = androidx.compose.animation.scaleOut(
                    targetScale = 0.9f,
                    animationSpec = androidx.compose.animation.core.tween(140)
                ) + androidx.compose.animation.fadeOut(
                    animationSpec = androidx.compose.animation.core.tween(140)
                )
            ) {
                // Stop scrim clicks from falling through the card.
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 520.dp)
                        .clip(PickerShape)
                        .background(LocalPalette.current.panel)
                        .border(1.dp, GlassBorder, PickerShape)
                        .focusProperties { canFocus = false } // only the rows inside are focus stops
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        )
                ) {
                    PickerCardBody(
                        streams = streams,
                        loading = loading,
                        errorMessage = errorMessage,
                        onDismiss = onDismiss,
                        onRetry = onRetry,
                        onPick = onPick,
                        onDownload = onDownload
                    )
                }
            }
        }
    }
}

/**
 * Picker card content: header, per-addon tab row, then the filtered stream
 * list (or the loading / real-error / empty states).
 */
@Composable
private fun PickerCardBody(
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null
) {
    val palette = LocalPalette.current
    val tabState = rememberPickerTabState(streams)
    val isTv = rememberIsTv()
    val firstTabFocus = remember { FocusRequester() }
    // On TV, land D-pad focus on the first addon tab once tabs exist.
    var tvFocusLanded by remember { mutableStateOf(false) }
    LaunchedEffect(isTv, tabState.tabs.isNotEmpty(), tvFocusLanded) {
        if (isTv && tabState.tabs.isNotEmpty() && !tvFocusLanded) {
            tvFocusLanded = true
            delay(300)
            runCatching { firstTabFocus.requestFocus() }
        }
    }

    Column(Modifier.fillMaxWidth().tvSheetFocus()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Choose source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = TextSecondary
                )
            }
        }
        if (tabState.tabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(tabState.tabs, key = { _, tab -> tab.key }) { index, tab ->
                    val selected = tab.key == tabState.selectedKey
                    Box(
                        modifier = Modifier
                            .then(if (index == 0) Modifier.focusRequester(firstTabFocus) else Modifier)
                            .tvFocusRing(RoundedCornerShape(50))
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (selected) palette.accent.copy(alpha = 0.22f)
                                else Color.White.copy(alpha = 0.06f)
                            )
                            .border(
                                1.dp,
                                if (selected) palette.accent else GlassBorder,
                                RoundedCornerShape(50)
                            )
                            .clickable { tabState.onSelect(tab.key) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "${tab.label} · ${tab.count}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            color = if (selected) TextPrimary else TextSecondary,
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        when {
            loading && streams.isEmpty() -> {
                Box(
                    Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbitDots(modifier = Modifier.size(34.dp))
                }
            }
            errorMessage != null && streams.isEmpty() -> {
                // Audit #24: show the REAL stream error (not "no sources yet"),
                // with a Retry that re-queries every addon.
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        errorMessage,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = palette.accent)
                    ) {
                        Text("Retry", color = Color(0xFF0A0A12), fontWeight = FontWeight.Bold)
                    }
                }
            }
            streams.isEmpty() -> {
                Text(
                    "No sources found yet — they appear here as each addon answers.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            else -> {
                StreamList(
                    streams = tabState.filtered,
                    onPick = onPick,
                    modifier = Modifier.heightIn(max = 400.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    onDownload = onDownload
                )
            }
        }
    }
}


/** Full-screen picker opened by long-pressing Play. All content scrolls on small screens. */
@Composable
internal fun FullPageSourcePicker(
    meta: MetaDetail,
    streams: List<StreamItem>,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPick: (StreamItem) -> Unit,
    onDownload: ((StreamItem) -> Unit)? = null,
    inSheet: Boolean = false
) {
    val palette = LocalPalette.current
    val browserState = rememberStreamBrowserState(streams)
    androidx.activity.compose.BackHandler(onBack = onBack)
    val pickerBody: @Composable () -> Unit = {
        Column(
            (if (inSheet) Modifier.fillMaxWidth().fillMaxHeight(0.85f)
             else Modifier.fillMaxSize()).tvSheetFocus()
        ) {
            if (inSheet) {
                // Sheet header: the title alone (owner's order
                // 2026-10-25: the "choose a source" suffix removed).
                // The sheet's drag handle sits above this; no back /
                // close row and no banner inside the sheet.
                Text(
                    meta.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 6.dp)
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        "Choose stream",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close stream picker", tint = TextSecondary)
                    }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("stream-picker-list"),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                if (!inSheet) {
                item(key = "poster") {
                    // Design I banner (user's approved design): full-width backdrop
                    // with gradient into the page background, title + count below.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        SafePosterImage(
                            model = meta.background ?: meta.poster,
                            contentDescription = "${meta.name} banner",
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            palette.background.copy(alpha = 0.7f),
                                            palette.background
                                        )
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                meta.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 2
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = com.pulsestream.app.ui.components.accentStarText(
                                    listOfNotNull(
                                        "${streams.size} sources available",
                                        meta.releaseInfo?.takeIf { it.isNotBlank() },
                                        meta.imdbRating?.takeIf { it.isNotBlank() }?.let { "★ $it" }
                                ).joinToString(" · "),
                                LocalPalette.current.accent
                            ),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
                }
                item(key = "filters") {
                    Text(
                        "Add-ons",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    AddonTabs(browserState, compact = true)
                    com.pulsestream.app.ui.components.QualityPills(
                        browserState,
                        compact = true,
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            browserState.selectedQuality?.let { "$it streams" } ?: "Available streams",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${browserState.filtered.size} results",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    if (loading && streams.isNotEmpty()) {
                        androidx.compose.material3.LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            color = palette.accent
                        )
                    }
                }
                when {
                    loading && streams.isEmpty() -> item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            OrbitDots(modifier = Modifier.size(34.dp))
                        }
                    }
                    errorMessage != null && streams.isEmpty() -> item(key = "error") {
                        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)) {
                            Text(errorMessage, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = palette.accent)) {
                                Text("Retry", color = Color(0xFF0A0A12), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    streams.isEmpty() -> item(key = "empty") {
                        Text(
                            "No sources found yet — they appear here as each addon answers.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    browserState.filtered.isEmpty() -> item(key = "no-matches") {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text(
                                "No streams match this add-on and quality. Choose Any or another add-on.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            androidx.compose.material3.TextButton(onClick = {
                                browserState.onSelectTab(com.pulsestream.app.ui.components.STREAM_TAB_ALL)
                                browserState.onSelectQuality(null)
                            }) { Text("Clear filters", color = palette.accent) }
                        }
                    }
                    else -> streamListItems(
                        streams = browserState.filtered,
                        onPick = onPick,
                        onDownload = onDownload,
                        compact = true
                    )
                }
            }
        }
    }
    if (inSheet) pickerBody() else GlowBackground { pickerBody() }
}

/** TMDB recommendation item. */
/**
 * Nuvio ExpandableDescription: collapses to [collapsedMaxLines] and expands
 * in place when the text actually overflows; the "Show more"/"Show less"
 * hint appears only when there is overflow to show.
 */
@Composable
private fun ExpandableDescription(
    text: String,
    collapsedMaxLines: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = TextSecondary,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = canExpand) { expanded = !expanded },
    ) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { result ->
                if (!expanded) canExpand = result.hasVisualOverflow
            },
        )
        if (canExpand) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (expanded) "Show less" else "Show more",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalPalette.current.accent,
            )
        }
    }
}

/** Nuvio formatRuntimeForDisplay: "136 min" / "136" / "2:16" -> "2h 16m"; unparseable passes through. */
private fun formatRuntimeForDisplay(rawRuntime: String?): String? {
    val normalized = rawRuntime?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val totalMinutes = parseRuntimeMinutes(normalized) ?: return normalized
    if (totalMinutes <= 0) return normalized
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun parseRuntimeMinutes(rawRuntime: String): Int? {
    val value = rawRuntime.trim()
    hourMinuteColonRegex.matchEntire(value)?.let { match ->
        val hours = match.groupValues[1].toIntOrNull() ?: return null
        val minutes = match.groupValues[2].toIntOrNull() ?: return null
        return (hours * 60) + minutes
    }
    val hoursToken = hourTokenRegex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    val minutesToken = minuteTokenRegex.find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (hoursToken != null || minutesToken != null) {
        return ((hoursToken ?: 0).coerceAtLeast(0) * 60) + (minutesToken ?: 0).coerceAtLeast(0)
    }
    digitsOnlyRegex.matchEntire(value)?.let { match ->
        return match.groupValues[1].toIntOrNull()?.coerceAtLeast(0)
    }
    return null
}

private val hourTokenRegex = Regex("""(?i)(\d+)\s*h(?:ours?)?""")
private val minuteTokenRegex = Regex("""(?i)(\d+)\s*m(?:in(?:ute)?s?)?""")
private val hourMinuteColonRegex = Regex("""^\s*(\d+)\s*:\s*(\d{1,2})\s*$""")
private val digitsOnlyRegex = Regex("""^\s*(\d+)\s*$""")

/** Facts for the Show Details tiles — only what the meta actually carries. */
private fun buildDetailTiles(meta: MetaDetail): List<Pair<String, String>> {
    val tiles = mutableListOf<Pair<String, String>>()
    meta.status?.takeIf { it.isNotBlank() }?.let { tiles += "Status" to it }
    // Series: a year range from episode air dates says more than the bare year.
    val release = if (meta.type == "series" && meta.videos.isNotEmpty()) {
        val years = meta.videos.mapNotNull { it.released?.take(4)?.toIntOrNull() }
        if (years.isNotEmpty()) {
            val lo = years.min()
            val hi = years.max()
            if (lo != hi) "$lo\u2013$hi" else meta.releaseInfo ?: lo.toString()
        } else meta.releaseInfo
    } else meta.releaseInfo
    release?.takeIf { it.isNotBlank() }?.let { tiles += "Release" to formatEpisodeDate(it) }
    meta.certification?.takeIf { it.isNotBlank() }?.let { tiles += "Certification" to it }
    meta.country?.takeIf { it.isNotBlank() }?.let { tiles += "Origin" to it }
    formatRuntimeForDisplay(meta.runtime)?.let { tiles += "Runtime" to it }
    meta.network?.takeIf { it.isNotBlank() }?.let { tiles += "Network" to it }
    meta.imdbRating?.takeIf { it.isNotBlank() }?.let { tiles += "IMDb" to it }
    if (meta.genres.isNotEmpty()) tiles += "Genres" to meta.genres.take(2).joinToString(", ")
    return tiles.take(6)
}

/** "2011-09-22T00:00:00.000Z" -> "Sep 22, 2011"; anything odd passes through short. */
private fun formatEpisodeDate(released: String?): String {
    if (released.isNullOrBlank()) return ""
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val parts = released.take(10).split("-")
    if (parts.size == 3) {
        val year = parts[0]
        val month = parts[1].toIntOrNull()
        val day = parts[2].toIntOrNull()
        if (month != null && month in 1..12 && day != null) {
            return "${months[month - 1]} $day, $year"
        }
    }
    return released.take(10)
}

/**
 * Sample A episode card (owner's pick, 2026-10-24): still on top with the
 * S/E chip and the WATCHED pill, words below on glass. Tap selects the
 * episode (same behavior as the old list rows).
 */
@Composable
private fun EpisodeCardA(
    video: Video,
    selected: Boolean,
    hideDetails: Boolean,
    isWatched: Boolean,
    spoilerMode: Boolean,
    isTv: Boolean,
    blurThumbnail: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onReveal: () -> Unit,
    onHide: () -> Unit
) {
    val palette = LocalPalette.current
    Column(
        modifier = Modifier
            .width(272.dp)
            .liquidGlass(RoundedCornerShape(18.dp))
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) palette.accent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp)
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp)) {
            if (!hideDetails && video.thumbnail != null) {
                SafePosterImage(
                    video.thumbnail,
                    null,
                    Modifier
                        .fillMaxSize()
                        .then(if (blurThumbnail) Modifier.blur(14.dp) else Modifier)
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(Color(0xFF2A3448), Color(0xFF121826)))
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "S${video.season} \u00b7 E${video.episode}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Box(
                Modifier.padding(9.dp)
                    .background(Color(0xBF05080E), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "S${video.season} \u00b7 E${video.episode}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            if (isWatched && !hideDetails) {
                WatchedPill(Modifier.align(Alignment.BottomStart).padding(9.dp))
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(9.dp)
                        .background(palette.accent, RoundedCornerShape(50))
                        .padding(5.dp)
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color(0xFF04140B),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            if (hideDetails) {
                Text(
                    "Episode details hidden",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isTv) {
                    TextButton(onClick = onReveal) { Text("Reveal") }
                }
            } else {
                Text(
                    "${video.episode}. ${video.title}",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!video.overview.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        video.overview!!,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        formatEpisodeDate(video.released),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall
                    )
                    Spacer(Modifier.weight(1f))
                    if (spoilerMode && !isTv) {
                        Text(
                            "Hide details",
                            color = palette.accent,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable(onClick = onHide)
                        )
                    }
                }
            }
        }
    }
}
