package com.pulsestream.app.ui.screens

import android.view.ViewGroup
import java.time.LocalDate
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import coil.compose.AsyncImage
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.pulsestream.app.R
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import com.pulsestream.app.data.DetailSectionAvailability
import com.pulsestream.app.data.DetailSectionKey
import com.pulsestream.app.data.DetailSectionSettings
import com.pulsestream.app.data.itemCount
import com.pulsestream.app.data.loadDetailSections
import com.pulsestream.app.data.personInitials
import com.pulsestream.app.data.scrollIndexFor
import com.pulsestream.app.data.visibleOrdered
import com.pulsestream.app.data.SettingsStore
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.TextureView
import androidx.compose.runtime.mutableFloatStateOf
import com.pulsestream.app.data.LetterboxTracker
import com.pulsestream.app.data.TrailerLetterbox
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.ui.components.AddToListSheet
import com.pulsestream.app.ui.components.CardDepthSurface
import com.pulsestream.app.ui.components.CustomPosterScreen
import com.pulsestream.app.ui.components.PlayButton
import com.pulsestream.app.ui.components.cardDepth
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
import com.pulsestream.app.ui.components.ExpandableDescription
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
import com.pulsestream.app.ui.components.SmartStreamSheet
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
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
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

/** State of the "Avio Pick" background stream check. */
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
    /** "Avio Pick": best verified stream, probed in the background. */
    var pickState by mutableStateOf<PickState>(PickState.Idle)
        private set
    /** "More like this" + "More from <actor>" rows from AvioMeta. */
    var relatedState by mutableStateOf<List<RelatedSection>>(emptyList())
        private set

    /** Set when the user pressed Play while streams were still loading. */
    private var pendingAutoPlay = false
    /** In-flight stream collection — cancelled when a new one starts. */
    private var streamsJob: Job? = null
    /** In-flight Avio Pick probe — cancelled when streams reload. */
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
                        loadStreams(meta.type, meta.id, meta.name, parseReleaseYear(meta.releaseInfo))
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

    /** Toggle shuffle mode for the following episodes (random next at end). The jump to a random episode lives in the shuffle sheet now. */
    fun updateShuffle(enabled: Boolean) {
        shuffleEpisodes = enabled
        repo.settings.shuffleEpisodes = enabled
    }

    /**
     * Play an episode at once (Nuvio onEpisodePlayClick): select it and
     * auto-play its best stream once loaded (the pending-play path fires
     * when the new streams arrive). Saved progress resumes unless the
     * episode is already watched — a completed episode restarts from 0.
     */
    fun playEpisode(video: Video, fromStart: Boolean = false) {
        pendingAutoPlay = true
        playFromStartRequest = fromStart
        selectEpisode(video)
    }

    fun selectEpisode(video: Video) {
        selectedVideo = video
        val meta = (metaState as? MetaUiState.Content)?.meta ?: return
        loadStreams(meta.type, video.id, meta.name, parseReleaseYear(meta.releaseInfo))
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
    fun loadStreams(type: String, videoId: String, title: String? = null, year: Int? = null) {
        streamsJob?.cancel()
        probeJob?.cancel()
        pickState = PickState.Idle
        streamsJob = viewModelScope.launch {
            streamsState = StreamsUiState.Loading
            var gotAny = false
            try {
                repo.streamResultsFlow(type, videoId, title = title, year = year).collect { result ->
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
                    repo.streamResultsFlow(type, videoId, title = title, year = year).collect { result ->
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
     * Starts (or restarts, when the merged list grew) the background Avio Pick
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
                    loadStreams(meta.type, videoId, meta.name)
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
        // instead of the probed fastest stream. Step 42: "Auto" picks the
        // Smart Streams scored best (episode runtime for series).
        if (repo.settings.streamAutoplay) {
            val isEpisode = (metaState as? MetaUiState.Content)?.meta?.type == "series"
            val ruled = pickAutoplayStream(playableStreams, repo.settings, isEpisode)
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
     * (Cinemeta/AvioMeta ids); quietly empty for anything else.
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

/** The hero Play button's target under Nuvio's series primary-action rule
 * (SeriesContinuity.decideSeriesPrimaryAction): resume the unfinished
 * episode, else the next released episode after the furthest watched one,
 * else the first released episode. */
private data class SeriesPrimaryAction(
    val video: Video,
    val label: String,
    val isResume: Boolean
)

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
    val watchedAt by com.pulsestream.app.data.WatchedMarks.watchedAt.collectAsState()
    // Step 41: shuffle sheet — one no-repeat picker per Detail visit.
    var shuffleSheetOpen by remember { mutableStateOf(false) }
    // Step 45: batch season download plan sheet.
    var batchSheetOpen by remember { mutableStateOf(false) }
    // Step 51: TMDB collection the movie belongs to (Nuvio TMDB meta
    // belongsToCollection) — name + titles, null when there is none.
    var tmdbCollection by remember {
        mutableStateOf<Pair<com.pulsestream.app.data.TmdbClient.TmdbCollectionRef, List<com.pulsestream.app.data.MetaPreview>>?>(null)
    }
    LaunchedEffect(meta.id, meta.type) {
        tmdbCollection = if (meta.type == "movie") {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getMovieCollection(settings, meta.id)
            }.getOrNull()
        } else null
    }
    // TMDB Enrichment fallbacks (Nuvio TmdbSettings modules): resolve
    // the TMDB id once, then fetch only what the meta addon didn't
    // serve. Every fetch is key-gated + module-gated inside
    // TmdbClient, so a module OFF (or no key) means no call at all.
    // All of these are fallbacks — meta-addon data always wins.
    var tmdbId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(meta.id, meta.type) {
        tmdbId = runCatching {
            com.pulsestream.app.data.TmdbClient.resolveTmdbId(settings, meta.id, meta.type)
        }.getOrNull()
    }
    var tmdbTrailers by remember {
        mutableStateOf<List<com.pulsestream.app.data.TrailerStream>>(emptyList())
    }
    LaunchedEffect(meta.id, meta.type, tmdbId) {
        tmdbTrailers = if (meta.trailerStreams.isEmpty() && tmdbId != null) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getTrailers(settings, meta.type, tmdbId!!)
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val effectiveTrailers =
        if (meta.trailerStreams.isNotEmpty()) meta.trailerStreams else tmdbTrailers
    var tmdbArtwork by remember {
        mutableStateOf<Pair<String?, String?>?>(null)
    }
    LaunchedEffect(meta.id, meta.type, tmdbId) {
        tmdbArtwork =
            if ((meta.background.isNullOrBlank() || meta.poster.isNullOrBlank()) && tmdbId != null) {
                runCatching {
                    com.pulsestream.app.data.TmdbClient.getArtwork(settings, meta.type, tmdbId!!)
                }.getOrNull()
            } else null
    }
    val effectiveBackdrop =
        meta.background?.takeIf { it.isNotBlank() } ?: tmdbArtwork?.second
    val effectivePoster =
        meta.poster?.takeIf { it.isNotBlank() } ?: tmdbArtwork?.first
    var tmdbProductionCompanies by remember { mutableStateOf<List<String>>(emptyList()) }
    var tmdbNetworks by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(meta.id, meta.type, tmdbId) {
        val needsProduction = meta.director.isEmpty() && meta.writer.isEmpty() &&
            meta.network.isNullOrBlank()
        tmdbProductionCompanies = if (needsProduction && tmdbId != null) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getProductionCompanies(
                    settings, meta.type, tmdbId!!
                )
            }.getOrDefault(emptyList())
        } else emptyList()
        tmdbNetworks = if (meta.type == "series" && meta.network.isNullOrBlank() && tmdbId != null) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getNetworks(settings, tmdbId!!)
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val effectiveNetwork =
        meta.network?.takeIf { it.isNotBlank() } ?: tmdbNetworks.firstOrNull()
    var tmdbRecommendations by remember {
        mutableStateOf<List<com.pulsestream.app.data.MetaPreview>>(emptyList())
    }
    LaunchedEffect(meta.id, meta.type, tmdbId, vm.relatedState.isEmpty()) {
        tmdbRecommendations = if (tmdbId != null && vm.relatedState.isEmpty()) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getRecommendations(settings, meta.type, tmdbId!!)
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    var tmdbSeasonPosters by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    LaunchedEffect(meta.id, meta.type, tmdbId) {
        tmdbSeasonPosters = if (meta.type == "series" && tmdbId != null) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getSeasonPosters(settings, tmdbId!!)
            }.getOrDefault(emptyMap())
        } else emptyMap()
    }
    var tmdbSeasonEpisodes by remember {
        mutableStateOf<List<com.pulsestream.app.data.TmdbClient.TmdbEpisode>>(emptyList())
    }
    LaunchedEffect(meta.id, meta.type, tmdbId, vm.selectedSeason) {
        tmdbSeasonEpisodes = if (meta.type == "series" && tmdbId != null) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.getSeasonEpisodes(
                    settings, tmdbId!!, vm.selectedSeason ?: 1
                )
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val episodeEnrichment = remember(tmdbSeasonEpisodes) {
        tmdbSeasonEpisodes.associateBy { it.episode }
    }
    // TMDB cast enrichment (Credits module): photos + character names
    // for the cast rail. The meta addon serves names only in keyless
    // mode, so without this the rail is photo-less (the "▶" glyphs).
    // enrichCast no-ops when the module is off, there is no key, or
    // the cast is already fully enriched; it also fills an empty cast
    // from TMDB's top 15. The rail below uses the enriched list.
    var tmdbCast by remember {
        mutableStateOf<List<com.pulsestream.app.data.CastMember>>(emptyList())
    }
    LaunchedEffect(meta.id, meta.type) {
        tmdbCast = if (!com.pulsestream.app.data.TmdbClient.isFullyEnrichedCast(meta.cast)) {
            runCatching {
                com.pulsestream.app.data.TmdbClient.enrichCast(
                    settings, meta.id, meta.type, meta.cast
                )
            }.getOrDefault(emptyList())
        } else emptyList()
    }
    val effectiveCast = tmdbCast.ifEmpty { meta.cast }
    val shufflePicker = remember(meta.id) {
        com.pulsestream.app.data.RandomEpisodePicker(emptyList(), emptySet())
    }
    LaunchedEffect(meta.videos, watchedKeys) {
        shufflePicker.update(
            meta.videos,
            com.pulsestream.app.data.watchedEpisodeKeys(meta.id, watchedKeys),
        )
    }
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
            "newest" -> filtered.sortedWith(compareByDescending<Video> { it.episode }.thenByDescending { it.title })
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
    val browserState = rememberStreamBrowserState(
        allStreams, repository.settings, isEpisode = meta.type == "series"
    )
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
    // --- Nuvio cinematic hero (v1.17 DetailHero) ---
    // Height = width x 1.33, clamped 420-760dp on phone (TV keeps the
    // old 51%-of-screen hero). Parallax 0.5x + scale 1.08 on the art;
    // overscroll stretch-zoom via HeroStretchState below.
    val heroHeight = if (isTvDetail) {
        (LocalConfiguration.current.screenHeightDp * 0.51f).dp
    } else {
        ((LocalConfiguration.current.screenWidthDp * 1.33f).dp).coerceIn(420.dp, 760.dp)
    }
    val heroHeightPx = with(LocalDensity.current) { heroHeight.roundToPx() }
    val heroStretchState = rememberHeroStretchState(listState)
    // Scroll offset for the parallax: the hero is item 0, so the offset
    // is the first item's scroll until the hero is fully past.
    val heroScrollOffsetPx = remember(listState, heroHeightPx) {
        {
            if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset
            else heroHeightPx
        }
    }
    // Floating glass header (Nuvio DetailFloatingHeader): fades in once
    // the hero has collapsed past the header's own height.
    val floatingHeaderHeightPx = with(LocalDensity.current) { (detailStatusDp + 56.dp).roundToPx() }
    val isHeroCollapsed by remember(listState, heroHeightPx, floatingHeaderHeightPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                (listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset > heroHeightPx - floatingHeaderHeightPx)
        }
    }
    val headerProgress by animateFloatAsState(
        targetValue = if (isHeroCollapsed) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isHeroCollapsed) 150 else 100,
            easing = LinearOutSlowInEasing
        ),
        label = "detail_floating_header_progress"
    )
    // Continue Watching, hoisted: the Play label, the episode progress
    // bars and the resume auto-select below all read the same list.
    val continueWatching by repository.continueWatching.collectAsState(initial = emptyList())
    // Primary Play action (Nuvio SeriesContinuity.decideSeriesPrimaryAction):
    // Resume > Next Up > Play first released. "Released" = no air date,
    // or aired on/before today. Resume wins only when its progress is
    // newer than the latest watched mark (shouldPreferResume); otherwise
    // the button advances to the next released episode after the furthest
    // watched one, so a finished E1 never replays from the hero button.
    fun isEpisodeReleased(video: Video): Boolean {
        val raw = video.released?.takeIf { it.isNotBlank() } ?: return true
        val date = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull() ?: return true
        return !date.isAfter(LocalDate.now())
    }
    val seriesPrimaryAction: SeriesPrimaryAction? = remember(meta, continueWatching, watchedKeys, watchedAt) {
        if (meta.type != "series") null
        else {
            val ordered = meta.videos
                .filter { it.season > 0 && it.episode > 0 }
                .sortedWith(compareBy({ it.season }, { it.episode }))
            val resumeEntry = continueWatching
                .filter { entry ->
                    entry.season > 0 && entry.episode > 0 && entry.positionMs > 0 &&
                        (entry.durationMs <= 0 || entry.positionMs < entry.durationMs)
                }
                .filter { entry ->
                    entry.videoId.contains(meta.id) || entry.key.contains(meta.id) ||
                        entry.imdbId == meta.id
                }
                .maxByOrNull { it.updatedAt }
            val resumeVideo = resumeEntry?.let { entry ->
                meta.videos.firstOrNull { it.season == entry.season && it.episode == entry.episode }
            }
            val latestWatched = com.pulsestream.app.data.NextUpResolver
                .latestWatchedBySeries(watchedKeys)[meta.id]
            val latestWatchedAt = latestWatched?.let { (season, episode) ->
                watchedAt[com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, season, episode)]
            }
            if (resumeVideo != null && resumeEntry != null &&
                (latestWatched == null || latestWatchedAt == null ||
                    resumeEntry.updatedAt > latestWatchedAt)
            ) {
                SeriesPrimaryAction(
                    resumeVideo,
                    "Resume S${resumeVideo.season} E${resumeVideo.episode}",
                    true
                )
            } else {
                val nextUp = latestWatched?.let { (season, episode) ->
                    ordered.firstOrNull {
                        (it.season > season || (it.season == season && it.episode > episode)) &&
                            isEpisodeReleased(it)
                    }
                }
                if (nextUp != null) {
                    SeriesPrimaryAction(nextUp, "Next Up • S${nextUp.season} E${nextUp.episode}", false)
                } else {
                    val first = ordered.firstOrNull { isEpisodeReleased(it) }
                    first?.let { SeriesPrimaryAction(it, "Play S${it.season} E${it.episode}", false) }
                }
            }
        }
    }
    // Primary-action auto-select: open on the episode the Play button
    // will play (resume, next up, or first released) — but only while
    // the selection is still the default first episode (a manual pick is
    // never overridden).
    var primaryApplied by remember(meta.id) { mutableStateOf(false) }
    LaunchedEffect(meta.id, seriesPrimaryAction) {
        val video = seriesPrimaryAction?.video ?: return@LaunchedEffect
        if (primaryApplied) return@LaunchedEffect
        val defaultSeason = seasons.firstOrNull()
        val defaultVideo = defaultSeason?.let { season ->
            meta.videos.filter { it.season == season }
                .sortedWith(compareBy({ it.episode }, { it.title })).firstOrNull()
        }
        if (vm.selectedSeason == defaultSeason && vm.selectedVideo?.id == defaultVideo?.id) {
            vm.selectSeason(video.season, meta)
            vm.selectEpisode(video)
        }
        primaryApplied = true
    }
    // Per-episode watch progress (0..1) for the episode cards: matched
    // by video id, or by season/episode + title identity.
    fun episodeProgress(video: Video): Float? {
        val entry = continueWatching
            .filter { entry ->
                entry.videoId == video.id ||
                    (entry.season == video.season && entry.episode == video.episode &&
                        entry.season > 0 &&
                        (entry.videoId.contains(meta.id) || entry.key.contains(meta.id) ||
                            entry.imdbId == meta.id))
            }
            .maxByOrNull { it.updatedAt }
        return entry?.takeIf { it.durationMs > 0 && it.positionMs > 0 }?.let {
            (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f)
        }
    }
    // Episode card style (Nuvio MetaEpisodeCardStyle): horizontal rail
    // or vertical list. Session-scoped for now — a SettingsStore key
    // (episodeCardStyle) is a follow-up for the settings batch.
    var episodeCardStyle by rememberSaveable { mutableStateOf("rail") }
    val context = androidx.compose.ui.platform.LocalContext.current
    val browsingPrivacy = remember(context) { BrowsingPrivacy(context) }
    // Ratings (Nuvio D5/M4): provider scores + your local star rating.
    val userRatings = remember(context) { UserRatings(context) }
    var userStars by remember(meta.id) { mutableStateOf(userRatings.getRating(meta.id)) }
    var ratingsSheetOpen by remember { mutableStateOf(false) }
    // Hero trailer autoplay (Nuvio D2): the first trailer autoplays muted in
    // the hero once resolved, fading in over the backdrop. Avio's
    // TrailerStream carries only title + ytId, so there is no official/type
    // metadata to rank by — rank by title instead, Nuvio's
    // HeroTrailerSelector order: "Official Trailer" > "Trailer" >
    // "Official" > anything else (first on ties).
    // TMDB trailers fill in when the addon serves none (effectiveTrailers).
    val heroTrailer = effectiveTrailers.maxByOrNull { trailer ->
        val t = trailer.title.lowercase()
        when {
            "official" in t && "trailer" in t -> 70
            "trailer" in t -> 60
            "official" in t -> 50
            else -> 0
        }
    }
    var heroTrailerUrl by remember(meta.id) { mutableStateOf<String?>(null) }
    var heroTrailerAudioUrl by remember(meta.id) { mutableStateOf<String?>(null) }
    var heroTrailerReady by remember(meta.id) { mutableStateOf(false) }
    var heroTrailerFinished by remember(meta.id) { mutableStateOf(false) }
    var heroMuted by remember(meta.id) { mutableStateOf(lastHeroTrailerMuted ?: !settings.heroTrailerStartUnmuted) }
    // Nuvio HeroTrailerAudioState: the mute choice survives across titles.
    val toggleHeroMute = { heroMuted = !heroMuted; lastHeroTrailerMuted = heroMuted }
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
    // Ratings source is Trakt only now (the ported AniList / WeTrakr /
    // MDBList providers were removed 2026-11-22): Trakt community rating
    // (Step 33, public endpoint, no login needed).
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
        val community = traktTitleRatings?.let { OmdbRating("Trakt", "%.1f/10".format(it.rating)) }
        if (community != null) base + community else base
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
    // needed. A failed fetch hides the section entirely. The fetch is
    // skipped entirely while the Comments section is disabled (it is
    // OFF by default) so detail pages make no Trakt comments call.
    // (detailSections is hoisted here so the fetch gate and the section
    // rendering below share one load.)
    val detailSections = loadDetailSections(settings.detailSectionsJson)
    val commentsEnabled = detailSections.isEnabled(DetailSectionKey.COMMENTS)
    var traktComments by remember(meta.id) { mutableStateOf<TraktApi.TraktCommentsPage?>(null) }
    var traktCommentsPage by remember(meta.id) { mutableStateOf(1) }
    var traktCommentsLoadingMore by remember { mutableStateOf(false) }
    var commentsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(meta.id, commentsEnabled) {
        traktComments = null
        traktCommentsPage = 1
        traktCommentsLoadingMore = false
        commentsExpanded = false
        val imdbId = meta.id.substringBefore(":").takeIf { it.startsWith("tt") }
        if (commentsEnabled && imdbId != null) {
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

    // Step 57: section order/visibility/tab layout. The four vals
    // below used to live inside the LazyColumn; they are hoisted here
    // so the section lambdas (and the availability) can share them.
    val productionRows = run {
        val base = listOfNotNull(
            meta.director.takeIf { it.isNotEmpty() }?.let {
                (if (it.size > 1) "Directors" else "Director") to it.joinToString(", ")
            },
            meta.writer.takeIf { it.isNotEmpty() }?.let {
                (if (it.size > 1) "Writers" else "Writer") to it.joinToString(", ")
            },
            effectiveNetwork?.takeIf { it.isNotBlank() }?.let { "Network" to it }
        )
        // TMDB Productions module: company names fill in only when the
        // meta addon serves no production info at all.
        if (base.isNotEmpty()) base
        else listOfNotNull(
            tmdbProductionCompanies.takeIf { it.isNotEmpty() }?.let {
                "Production companies" to it.joinToString(", ")
            },
            effectiveNetwork?.takeIf { it.isNotBlank() }?.let { "Network" to it }
        )
    }
    val collectionSection = tmdbCollection
    val commentsPage = traktComments
    // TMDB Networks module: the Show Details Network tile reads the
    // TMDB network when the meta addon serves none.
    val detailTiles = buildDetailTiles(meta.copy(network = effectiveNetwork))
    val detailAvailability = DetailSectionAvailability(
        hasOverview = !meta.description.isNullOrBlank(),
        hasTrailers = effectiveTrailers.isNotEmpty(),
        hasCast = effectiveCast.isNotEmpty(),
        hasProduction = productionRows.isNotEmpty(),
        hasCollection = collectionSection != null,
        hasComments = commentsPage != null && commentsPage.items.isNotEmpty(),
        isSeries = isSeries,
        hasDetails = detailTiles.size >= 2,
        relatedCount = vm.relatedState.size
    )
    val orderedDetailSections = detailSections.visibleOrdered(detailAvailability)
    // Section bodies as lambdas (moved verbatim from the LazyColumn
    // below): the loop after the hero emits them in settings order.
    val actionsBlock: LazyListScope.() -> Unit = {
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
            // (continueWatching is hoisted to DetailContent scope).
            val resumeState = continueWatching.find { it.videoId == meta.id || it.key.contains(meta.id) }
            val hasResume = resumeState != null && resumeState.positionMs > 0 && resumeState.durationMs > 0
            val resumeProgress = if (hasResume) resumeState!!.positionMs.toFloat() / resumeState!!.durationMs else 0f
            // Dynamic Play label (Nuvio seriesPrimaryAction): Resume >
            // Next Up > Play first released; movies keep the old labels.
            val playText = when {
                seriesPrimaryAction != null -> seriesPrimaryAction.label
                hasResume -> "Continue Watching"
                else -> "Watch Now"
            }
            // Play button: full width capsule. Tap = play the primary
            // episode (selecting it first when it differs), long-press =
            // open sources.
            PlayButton(
                onClick = {
                    val target = seriesPrimaryAction?.video
                    if (target != null && target.id != vm.selectedVideo?.id) vm.playEpisode(target)
                    else vm.onPlayPressed()
                },
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
            // Title-level watched toggle (series only; Nuvio
            // DetailActionButtons "Mark watched"): flips every episode
            // of every season. Movies have no public watched setter in
            // StreamRepository, so they get no toggle in this batch.
            if (isSeries && meta.videos.isNotEmpty()) {
                val allWatched = meta.videos.all { video ->
                    com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys
                }
                TextButton(
                    onClick = {
                        val target = !allWatched
                        scope.launch {
                            seasons.forEach { season ->
                                repository.setSeasonWatched(meta.id, season, meta.videos, target)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                ) {
                    Icon(
                        if (allWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (allWatched) "Mark all unwatched" else "Mark all watched",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
          }
        }
    }

    val overviewBlock: LazyListScope.() -> Unit = {
            item {
                Column {
                    // Facts pills (Nuvio DetailMetaInfo order): release,
                    // seasons, episodes, runtime, age, IMDb. Genres live
                    // in the hero now, not here.
                    val overviewFacts = buildList {
                        meta.releaseInfo?.takeIf { it.isNotBlank() }?.let { add(formatEpisodeDate(it)) }
                        if (isSeries) {
                            val seasonCount = meta.videos.mapNotNull { it.season.takeIf { s -> s > 0 } }.distinct().size
                            if (seasonCount > 0) add("$seasonCount Season${if (seasonCount == 1) "" else "s"}")
                            val episodeCount = meta.videos.distinctBy { "${it.season}:${it.episode}" }.size
                            if (episodeCount > 0) add("$episodeCount Episode${if (episodeCount == 1) "" else "s"}")
                        }
                        formatRuntimeForDisplay(meta.runtime)?.let { add(it) }
                        meta.certification?.takeIf { it.isNotBlank() }?.let { add(it) }
                        meta.imdbRating?.takeIf { it.isNotBlank() }?.let { add("IMDb $it") }
                    }
                    if (overviewFacts.isNotEmpty()) {
                        FlowRow(
                            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            overviewFacts.forEach { fact ->
                                Text(
                                    fact,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextPrimary,
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(50))
                                        .padding(horizontal = 11.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    // Nuvio ExpandableDescription: 4 lines collapsed, expands in place.
                    ExpandableDescription(
                        text = meta.description!!,
                        collapsedMaxLines = 4,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = if (overviewFacts.isNotEmpty()) 12.dp else 16.dp)
                    )
                }
            }
    }

    val trailersBlock: LazyListScope.() -> Unit = {
        // Trailers section: horizontal cards like "You May Like"
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
                    items(effectiveTrailers, key = { it.ytId }) { trailer ->
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
                                                        poster = effectiveBackdrop ?: effectivePoster,
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
                                    .height(90.dp)
                                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                    .cardDepth(
                                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                                        CardDepthSurface.Trailers
                                    ),
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

    val castBlock: LazyListScope.() -> Unit = {
        // Nuvio DetailCastSection (phone <600dp): 80dp circle photo,
        // 92dp item, 16dp gap, name 14sp + character 12sp, both
        // centred, one line. Photos come from the meta addon or the
        // TMDB Credits enrichment above; no photo (or a failed load)
        // shows the actor's initials, like Nuvio.
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
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(effectiveCast, key = { it.id.toString() + "_" + it.name }) { actor ->
                        // Metadata flexibility (2026-10-26): name-only
                        // cast (keyless addons) navigates too — the actor
                        // page falls back to search by name when no
                        // addon serves /person for the id.
                        val clickable = actor.id > 0 || actor.name.isNotBlank()
                        var photoFailed by remember(actor.photo) { mutableStateOf(false) }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(92.dp)
                                .tvFocusRing(RoundedCornerShape(12.dp))
                                .then(
                                    if (clickable) Modifier.clickable {
                                        onOpenActor(actor.id, actor.name)
                                    }
                                    else Modifier
                                )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Color.White.copy(alpha = 0.05f), CircleShape
                                    )
                                    .liquidGlass(CircleShape)
                                    .cardDepth(CircleShape, CardDepthSurface.Cast),
                                contentAlignment = Alignment.Center
                            ) {
                                if (actor.photo != null && !photoFailed) {
                                    AsyncImage(
                                        model = actor.photo,
                                        contentDescription = actor.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        onError = { photoFailed = true }
                                    )
                                } else {
                                    Text(
                                        text = personInitials(actor.name),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextSecondary
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = actor.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (clickable) TextPrimary else TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (!actor.character.isNullOrBlank()) {
                                Text(
                                    text = actor.character,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
    }

    val productionBlock: LazyListScope.() -> Unit = {
        // Production (Nuvio DetailProductionSection): what the meta
        // actually carries — Director (AvioMeta + Cinemeta), Writer
        // (Cinemeta only; AvioMeta serves none), Network (series).
        // Nuvio's version is TMDB production-company logo chips; the
        // meta addons carry no companies, so no logos here.
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

    val collectionBlock: LazyListScope.() -> Unit = {
        // Step 51: TMDB collection (Nuvio belongsToCollection) — "Part of
        // the <name>" with the collection's movies. Movies only; needs a
        // TMDB key, silent otherwise.
        collectionSection?.let { section ->
            item(key = "tmdb_collection") {
                Text(
                    "Part of the ${section.first.name}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(section.second, key = { it.id }) { preview ->
                        com.pulsestream.app.ui.components.PosterCard(
                            meta = preview,
                            onClick = { onOpenDetail("", preview.type, preview.id) },
                            surface = CustomPosterScreen.DETAILS
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val commentsBlock: LazyListScope.() -> Unit = {
        // Trakt comments (Step 33, Nuvio TraktCommentsRepository): most-liked
        // first, spoiler-safe. Shows 3, expands to 20, then loads more pages.
        commentsPage?.let { page ->
            item(key = "trakt_comments") {
                Text(
                    "Trakt comments (${page.itemCount})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                val shown = if (commentsExpanded) page.items.take(20)
                    else page.items.take(3)
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    shown.forEach { comment -> TraktCommentCard(comment) }
                    val canExpand = !commentsExpanded && page.items.size > 3
                    val canLoadMore = commentsExpanded &&
                        traktCommentsPage < page.pageCount
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
                                        traktComments = page.copy(
                                            items = page.items + next.items,
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
                                if (canExpand) "Show all ${page.itemCount} comments"
                                else "Load more comments",
                                color = LocalPalette.current.accent
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    val episodesBlock: LazyListScope.() -> Unit = {
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
                                    .aspectRatio(0.675f)
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
                                    // TMDB Season Posters module: the
                                    // per-season poster when TMDB has one.
                                    model = tmdbSeasonPosters[season] ?: meta.poster ?: meta.background,
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
                        // Guard: if this Text is ever squeezed to ~0dp wide
                        // again, it must truncate on one line — a 0-width
                        // Text wraps "Episodes" into 8 lines and inflates
                        // the whole header Row to ~200dp tall.
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { shuffleSheetOpen = true }) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Shuffle episodes",
                            tint = if (vm.shuffleEpisodes) LocalPalette.current.accent else TextSecondary
                        )
                    }
                    // Step 45: batch season download (same header as Shuffle).
                    if (settings.showDownloadButton) {
                        IconButton(onClick = { batchSheetOpen = true }) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download season",
                                tint = TextSecondary
                            )
                        }
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
                    // Episode card style (Nuvio MetaEpisodeCardStyle):
                    // horizontal rail or vertical list.
                    IconButton(onClick = { episodeCardStyle = if (episodeCardStyle == "list") "rail" else "list" }) {
                        Icon(
                            if (episodeCardStyle == "list") Icons.Filled.ViewModule else Icons.Filled.ViewList,
                            contentDescription = if (episodeCardStyle == "list") "Rail view" else "List view",
                            tint = TextSecondary
                        )
                    }
                }
            }
            // Spoiler filters on their own row: sharing the header Row
            // with the 4 action icons overflowed on a 360dp phone — the
            // "Episodes" title was squeezed to 0dp and "Blur unwatched"
            // was clipped off-screen.
            if (!isTvDetail) {
                item(key = "episodes_filters") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
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
            if (episodeCardStyle == "list") {
                // List style (Nuvio MetaEpisodeCardStyle.List): vertical
                // cards under a sticky season header.
                stickyHeader(key = "episodes_list_header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(LocalPalette.current.background)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (vm.selectedSeason == 0) "Specials" else "Season ${vm.selectedSeason}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${episodes.size} episode${if (episodes.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
                items(episodes, key = { "ep_list_${it.id}" }) { video ->
                    val selected = vm.selectedVideo?.id == video.id
                    val episodeRevealed = video.id in revealedEpisodeIds
                    val hideDetails = BrowsingPrivacy.shouldHideEpisodeDetails(hideEpisodeDetails, episodeRevealed)
                    val isWatched = com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys
                    // TMDB Episodes + Episode Ratings modules: fill a
                    // missing overview/thumbnail/runtime from TMDB, and
                    // show the TMDB rating on the card.
                    val tmdbEp = episodeEnrichment[video.episode]
                    val enrichedVideo = if (tmdbEp != null && settings.tmdbUseEpisodes) {
                        video.copy(
                            overview = video.overview?.takeIf { it.isNotBlank() } ?: tmdbEp.overview,
                            thumbnail = video.thumbnail?.takeIf { it.isNotBlank() } ?: tmdbEp.stillUrl,
                        )
                    } else video
                    val enrichedRuntime = tmdbEp?.runtimeMinutes
                        ?.takeIf { settings.tmdbUseEpisodes }
                        ?.let { formatRuntimeForDisplay(it.toString()) }
                        ?: formatRuntimeForDisplay(meta.runtime)
                    val enrichedRating = tmdbEp?.voteAverage
                        ?.takeIf { settings.tmdbUseEpisodeRatings }
                    EpisodeListCard(
                        video = enrichedVideo,
                        selected = selected,
                        hideDetails = hideDetails,
                        isWatched = isWatched,
                        spoilerMode = hideEpisodeDetails,
                        isTv = isTvDetail,
                        blurThumbnail = blurUnwatched && !isWatched,
                        runtime = enrichedRuntime,
                        progress = episodeProgress(video),
                        tmdbRating = enrichedRating,
                        onClick = { vm.playEpisode(video, fromStart = isWatched) },
                        onLongClick = { episodeActionVideo = video },
                        onReveal = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, true) },
                        onHide = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, false) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            } else {
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
                            // TMDB Episodes + Episode Ratings modules: fill a
                            // missing overview/thumbnail/runtime from TMDB, and
                            // show the TMDB rating on the card.
                            val tmdbEp = episodeEnrichment[video.episode]
                            val enrichedVideo = if (tmdbEp != null && settings.tmdbUseEpisodes) {
                                video.copy(
                                    overview = video.overview?.takeIf { it.isNotBlank() } ?: tmdbEp.overview,
                                    thumbnail = video.thumbnail?.takeIf { it.isNotBlank() } ?: tmdbEp.stillUrl,
                                )
                            } else video
                            val enrichedRuntime = tmdbEp?.runtimeMinutes
                                ?.takeIf { settings.tmdbUseEpisodes }
                                ?.let { formatRuntimeForDisplay(it.toString()) }
                                ?: formatRuntimeForDisplay(meta.runtime)
                            val enrichedRating = tmdbEp?.voteAverage
                                ?.takeIf { settings.tmdbUseEpisodeRatings }
                            EpisodeCardA(
                                video = enrichedVideo,
                                selected = selected,
                                hideDetails = hideDetails,
                                isWatched = isWatched,
                                spoilerMode = hideEpisodeDetails,
                                isTv = isTvDetail,
                                blurThumbnail = blurUnwatched && !isWatched,
                                runtime = enrichedRuntime,
                                progress = episodeProgress(video),
                                tmdbRating = enrichedRating,
                                onClick = { vm.playEpisode(video, fromStart = isWatched) },
                                onLongClick = { episodeActionVideo = video },
                                onReveal = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, true) },
                                onHide = { revealedEpisodeIds = browsingPrivacy.setEpisodeRevealed(episodeShowKey, video.id, false) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
    }

    val detailsBlock: LazyListScope.() -> Unit = {
        // Show Details — glass fact tiles (owner's Sample A pick, 2026-10-24).
        // Only facts the meta actually carries are shown.
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

    val relatedBlock: LazyListScope.() -> Unit = {
        // "More like this" + "More from <actor>" rows from AvioMeta.
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
                            surface = CustomPosterScreen.DETAILS,
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
        // TMDB More Like This module: recommendations fill in only when
        // the meta addons return no related rows at all.
        if (vm.relatedState.isEmpty() && tmdbRecommendations.isNotEmpty()) {
            item(key = "related_tmdb") {
                com.pulsestream.app.ui.components.SectionTitle("More like this")
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(tmdbRecommendations.distinctBy { it.id }, key = { it.id }) { related ->
                        com.pulsestream.app.ui.components.PosterCard(
                            meta = related,
                            onClick = {
                                if (related.id != meta.id || related.type != meta.type) {
                                    onOpenDetail("", related.type, related.id)
                                }
                            }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // The LazyColumn sits in a Box so the floating glass header can
    // overlay it once the hero collapses (Nuvio DetailFloatingHeader).
    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(heroStretchState.nestedScrollConnection)
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
            // Nuvio cinematic hero (v1.17 DetailHero): height = width x
            // 1.33 clamped 420-760dp on phone; parallax 0.5x + scale
            // 1.08 + overscroll stretch-zoom; 7-stop fade to the theme
            // background; title displayLarge centred (logo 60% width /
            // 80dp when the meta carries one); genres only in the hero
            // (max 3) — the full facts pills live in the Overview
            // section. The 74dp poster-in-hero is gone on phone; when
            // there is no backdrop the poster itself IS the hero art.
            // TV keeps the old layout (poster card + left-aligned title).
            // TMDB Artwork module: the backdrop/poster fall back to TMDB
            // when the meta addon serves no art of its own.
            val heroArt = effectiveBackdrop ?: effectivePoster
            val hasBackdrop = !effectiveBackdrop.isNullOrBlank()
            val trailerUrl = heroTrailerUrl
            val fadeColor = LocalPalette.current.background
            val trailerFadeAlpha by animateFloatAsState(
                targetValue = if (heroTrailerReady) 1f else 0f,
                animationSpec = tween(300),
                label = "hero_trailer_fade"
            )
            var logoLoadError by remember(meta.id, meta.logo) { mutableStateOf(false) }
            val logoUrl = meta.logo?.takeIf { it.isNotBlank() && !logoLoadError }
            Box(
                Modifier
                    .fillMaxWidth()
                    .heroStretchHeight(heroHeight) { heroStretchState.stretchPx }
                    .graphicsLayer { clip = true }
            ) {
                // Backdrop (or the poster itself when there is no
                // backdrop), with parallax + scale + stretch-zoom.
                if (heroArt != null) {
                    SafePosterImage(
                        model = heroArt,
                        contentDescription = null,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(heroHeight)
                            .heroStretchZoom { heroStretchState.stretchPx }
                            .graphicsLayer {
                                translationY = heroScrollOffsetPx() * 0.5f
                                scaleX = 1.08f
                                scaleY = 1.08f
                            },
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface)
                    )
                }
                // Hero trailer: once resolved, the trailer plays over the
                // backdrop and fades in when ready; the fade below stays
                // on top so the title text keeps reading. Nuvio
                // DetailHero: it pauses once the hero has scrolled away
                // (playWhenReady gates on !isHeroCollapsed).
                if (trailerUrl != null && !heroTrailerFinished) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .heroStretchZoom { heroStretchState.stretchPx }
                            .graphicsLayer {
                                translationY = heroScrollOffsetPx() * 0.5f
                                scaleX = 1.08f
                                scaleY = 1.08f
                                alpha = trailerFadeAlpha
                            }
                    ) {
                        HeroTrailerPlayer(
                            videoUrl = trailerUrl,
                            audioUrl = heroTrailerAudioUrl,
                            muted = heroMuted,
                            play = !isHeroCollapsed,
                            onReady = { heroTrailerReady = true },
                            onEnded = { heroTrailerFinished = true },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                // Nuvio DetailHero: the whole hero toggles mute once the
                // trailer is ready (no ripple). Composed before the fade
                // and the action row, so the row's buttons stay on top
                // and keep working.
                if (trailerUrl != null && !heroTrailerFinished) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clickable(
                                enabled = heroTrailerReady,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { toggleHeroMute() }
                            )
                    )
                }
                // 7-stop fade to the theme background (Nuvio DetailHero).
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(320.dp)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Transparent,
                                    0.16f to fadeColor.copy(alpha = 0.04f),
                                    0.32f to fadeColor.copy(alpha = 0.14f),
                                    0.50f to fadeColor.copy(alpha = 0.34f),
                                    0.68f to fadeColor.copy(alpha = 0.62f),
                                    0.84f to fadeColor.copy(alpha = 0.84f),
                                    1.00f to fadeColor
                                )
                            )
                        )
                )
                // Top action row: back / watchlist / mute / add-to-list.
                // The watchlist button's long-press opens the Add-to-list
                // sheet (Nuvio DetailActionButtons save long-press).
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp + detailStatusDp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Top hero buttons are 36dp (user 2026-11-23: 25%
                    // smaller than the 48dp default), icons 18dp.
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .combinedClickable(
                                onClick = {
                                    scope.launch {
                                        repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster, com.pulsestream.app.data.parseReleaseYear(meta.releaseInfo))
                                    }
                                },
                                onLongClick = { showAddToList = true }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (watchItem != null) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                            contentDescription = if (watchItem != null) "Remove from watchlist" else "Add to watchlist",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // Hero trailer mute toggle: only while the trailer is
                    // playing in the hero.
                    if (trailerUrl != null && !heroTrailerFinished && heroTrailerReady) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                .clickable(onClick = { toggleHeroMute() }),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (heroMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                                contentDescription = if (heroMuted) "Unmute trailer" else "Mute trailer",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                            .clickable(onClick = { showAddToList = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = "Add to list",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                // Bottom: centred title/logo + genres + ratings (phone);
                // TV keeps the poster card + left-aligned title.
                if (isTvDetail) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        if (hasBackdrop && !effectivePoster.isNullOrBlank()) {
                            SafePosterImage(
                                model = effectivePoster,
                                contentDescription = null,
                                modifier = Modifier
                                    .width(74.dp)
                                    .aspectRatio(0.675f)
                                    .shadow(12.dp, RoundedCornerShape(12.dp))
                                    .clip(RoundedCornerShape(12.dp)),
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
                            Spacer(Modifier.height(6.dp))
                            DetailRatingsRow(
                                ratings = providerRatings,
                                userStars = userStars,
                                onClick = { ratingsSheetOpen = true }
                            )
                        }
                    }
                } else {
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(start = 18.dp, end = 18.dp, bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (logoUrl != null) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = meta.name,
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .height(80.dp),
                                contentScale = ContentScale.Fit,
                                onError = { logoLoadError = true }
                            )
                        } else {
                            Text(
                                meta.name,
                                style = MaterialTheme.typography.displayLarge,
                                color = TextPrimary,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (meta.genres.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                meta.genres.take(3).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
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
        // ("You May Like" rail removed, owner's pick 2026-10-24:
        // AvioMeta's "More like this" rows below replace it —
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
        if (detailSections.tabLayout) {
            stickyHeader(key = "section_tabs") {
                DetailSectionTabRow(
                    settings = detailSections,
                    availability = detailAvailability,
                    listState = listState,
                    onTabClick = { key ->
                        detailSections.scrollIndexFor(key, detailAvailability)?.let { index ->
                            scope.launch { listState.scrollToItem(index) }
                        }
                    }
                )
            }
        }
        for (key in orderedDetailSections) {
            when (key) {
                DetailSectionKey.ACTIONS -> actionsBlock()
                DetailSectionKey.OVERVIEW -> overviewBlock()
                DetailSectionKey.PRODUCTION -> productionBlock()
                DetailSectionKey.CAST -> castBlock()
                DetailSectionKey.COMMENTS -> commentsBlock()
                DetailSectionKey.TRAILERS -> trailersBlock()
                DetailSectionKey.EPISODES -> episodesBlock()
                DetailSectionKey.DETAILS -> detailsBlock()
                DetailSectionKey.COLLECTION -> collectionBlock()
                DetailSectionKey.MORE_LIKE_THIS -> relatedBlock()
            }
        }
    }
        // Floating glass header (Nuvio DetailFloatingHeader): 56dp, fades
        // in once the hero collapses; logo/title centred + save toggle.
        // The back button lives here when collapsed; the hero's own top
        // row covers it while the hero is visible.
        if (headerProgress > 0.01f) {
            DetailFloatingHeader(
                meta = meta,
                isSaved = watchItem != null,
                progress = headerProgress,
                topPadding = detailStatusDp,
                onBack = onBack,
                onToggleSaved = {
                    scope.launch {
                        repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster, com.pulsestream.app.data.parseReleaseYear(meta.releaseInfo))
                    }
                },
                modifier = Modifier.zIndex(2f)
            )
        }
    }

    // Mini floating source-picker card: same-size button next to Play opens
    // this with a springy pop instead of scrolling to the sources section.
    // The popup gets the full stream list and runs its own per-addon tabs
    // (the inline section below keeps the shared "All" browser state).
    val streamsContent = vm.streamsState as? StreamsUiState.Content
    // Batch season download sheet (Step 45): pick episodes of the current
    // season; each downloads its best stream in the background.
    if (batchSheetOpen && isSeries) {
        BatchDownloadSheet(
            showName = meta.name ?: "Series",
            showId = meta.id,
            season = vm.selectedSeason ?: 1,
            episodes = meta.videos.filter { it.season == vm.selectedSeason }.sortedBy { it.episode },
            watched = com.pulsestream.app.data.watchedEpisodeKeys(meta.id, watchedKeys),
            onDownload = { chosen ->
                batchSheetOpen = false
                com.pulsestream.app.data.download.BatchDownloadQueuer.queue(
                    context = context,
                    repository = repository,
                    meta = meta,
                    season = vm.selectedSeason ?: 1,
                    episodes = chosen,
                )
            },
            onDismiss = { batchSheetOpen = false },
        )
    }

    // Shuffle sheet (Step 41): random episode with include-watched toggle,
    // no-repeat session and Play.
    if (shuffleSheetOpen && isSeries) {
        EpisodeShuffleSheet(
            showName = meta.name ?: "Series",
            picker = shufflePicker,
            includeWatched = settings.shuffleIncludeWatched,
            onIncludeWatchedChange = { settings.shuffleIncludeWatched = it },
            shuffleFollowing = vm.shuffleEpisodes,
            onShuffleFollowingChange = { vm.updateShuffle(it) },
            onPlay = { video ->
                shuffleSheetOpen = false
                vm.playEpisode(video)
            },
            onDismiss = { shuffleSheetOpen = false },
        )
    }

    // Download confirmation dialog state
    var downloadStream by remember { mutableStateOf<StreamItem?>(null) }
    SourcePickerPopup(
        visible = sourcePickerOpen,
        streams = allStreams,
        loading = vm.streamsState is StreamsUiState.Loading,
        errorMessage = (vm.streamsState as? StreamsUiState.Error)?.message,
        onDismiss = { sourcePickerOpen = false },
        onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id, meta.name) },
        onPick = { stream ->
            sourcePickerOpen = false
            if (streamsContent != null) {
                startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
            }
        },
        onDownload = if (settings.showDownloadButton) { stream -> downloadStream = stream } else null
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
            onRetry = { vm.loadStreams(meta.type, vm.selectedVideo?.id ?: meta.id, meta.name) },
            onPick = { stream ->
                fullPageSourceOpen = false
                if (streamsContent != null) {
                    startPlayback(vm, repository, stream, streamsContent.subtitles, streamsContent.streams, onPlay)
                }
            },
            onDownload = if (settings.showDownloadButton) { stream -> downloadStream = stream } else null,
            settings = settings
        )
      }
    }

    // Download confirmation dialog
    downloadStream?.let { stream ->
        DownloadConfirmDialog(
            stream = stream,
            title = meta.name ?: "Video",
            onDismiss = { downloadStream = null },
            // Step 36: series episodes group under the show; movies stay flat.
            metaId = if (meta.type == "series") meta.id else "",
            seriesName = meta.name,
            season = vm.selectedVideo?.season ?: 0,
            episode = vm.selectedVideo?.episode ?: 0,
            // Step 46: subtitles + offline show meta.
            subtitles = streamsContent?.subtitles ?: emptyList(),
            posterUrl = meta.poster,
            description = meta.description,
            genres = meta.genres,
            videos = meta.videos
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
            // Step 47: release year for the Library Saved sort.
            year = com.pulsestream.app.data.parseReleaseYear(meta.releaseInfo),
            onDismiss = { showAddToList = false }
        )
    }

    // Episode watched action sheet (Nuvio EpisodeWatchedActionSheet):
    // long-press an episode card. Mark watched/unwatched for the episode,
    // for all released episodes before it, or for the whole season; or
    // open the manual stream picker for this episode. Rate arrives with
    // Step 28 (per-title stars).
    episodeActionVideo?.let { video ->
        val isWatched = com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, video.season, video.episode) in watchedKeys
        ModalBottomSheet(
            onDismissRequest = { episodeActionVideo = null },
            sheetState = rememberModalBottomSheetState(),
            containerColor = LocalPalette.current.background
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
                // Mark previous (Nuvio EpisodeWatchedActionSheet): all
                // released episodes before this one, in (season, episode)
                // order. Shown only when such episodes exist; the label
                // flips on whether they are all watched already.
                val previousEpisodes = meta.videos.filter { other ->
                    other.season > 0 && other.episode > 0 &&
                        (other.season < video.season ||
                            (other.season == video.season && other.episode < video.episode)) &&
                        isEpisodeReleased(other)
                }
                if (previousEpisodes.isNotEmpty()) {
                    val arePreviousWatched = previousEpisodes.all {
                        com.pulsestream.app.data.WatchedMarks.episodeKey(meta.id, it.season, it.episode) in watchedKeys
                    }
                    EpisodeActionRow(
                        label = if (arePreviousWatched) "Mark previous unwatched" else "Mark previous watched",
                        icon = Icons.Filled.DoneAll,
                        onClick = {
                            episodeActionVideo = null
                            scope.launch {
                                for (prev in previousEpisodes) {
                                    repository.setEpisodeWatched(meta.id, prev.season, prev.episode, !arePreviousWatched)
                                }
                            }
                        }
                    )
                }
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
                // Play manually (Nuvio EpisodeWatchedActionSheet): open
                // the manual stream picker for this episode. Nuvio shows
                // this only when stream autoplay is effectively on; in
                // Avio tapping Play always auto-plays a picked stream
                // (ruled pick, or probed fastest when "Autoplay by rule"
                // is off), so the manual override is always offered —
                // the setting only chooses which stream auto-plays. The
                // Watch Now long-press picker stays ungated for the same
                // reason: it is Avio's only manual stream choice.
                EpisodeActionRow(
                    label = "Play manually",
                    icon = Icons.Filled.PlayArrow,
                    onClick = {
                        episodeActionVideo = null
                        vm.selectEpisode(video)
                        fullPageSourceOpen = true
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
            containerColor = LocalPalette.current.background
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
            containerColor = LocalPalette.current.background,
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

/** First 4-digit year in a releaseInfo string ("2016", "2016-03-04") — ranks the CS3 title fallback. */
private fun parseReleaseYear(releaseInfo: String?): Int? =
    releaseInfo?.let { Regex("(19|20)\\d{2}").find(it)?.value?.toIntOrNull() }

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
 * with no controls, ZOOM (centre-crop) so the 16:9 trailer fills the
 * portrait hero instead of letterboxing into a horizontal strip. Video-only
 * YouTube qualities are merged with their separate audio track, the same
 * merge the full player applies via `PlaybackRequest.mergeAudioUrl`.
 * The surface is a TextureView so frames can be sampled for the letterbox
 * zoom, which crops baked-in black bars out of the picture.
 */
/** Last hero-trailer mute choice (Nuvio HeroTrailerAudioState): the mute toggle survives across titles. */
@Volatile
private var lastHeroTrailerMuted: Boolean? = null

@Composable
private fun HeroTrailerPlayer(
    videoUrl: String,
    audioUrl: String?,
    muted: Boolean,
    play: Boolean,
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
            playWhenReady = play
            prepare()
        }
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(play) { player.playWhenReady = play }
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
    // Nuvio HeroTrailerPlayerSurface: the trailer fills the portrait hero
    // (centre-crop, like the texture transform Nuvio applies) instead of
    // FIT, which showed the 16:9 video as a horizontal strip with black
    // bars above and below. The surface is a TextureView (layout
    // trailer_player_view) so frames can be sampled for the letterbox zoom
    // below, which crops baked-in black bars out of the picture.
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var letterboxZoom by remember { mutableFloatStateOf(1f) }
    AndroidView(
        factory = { ctx ->
            // PlayerView in Media3 1.4.1 has no setVideoSurfaceType — the
            // surface type comes from the layout's app:surface_type.
            (LayoutInflater.from(ctx).inflate(R.layout.trailer_player_view, null, false) as PlayerView).also { pv ->
                pv.player = player
                pv.useController = false
                pv.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                textureView = pv.videoSurfaceView as? TextureView
                pv.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
        },
        update = { pv ->
            pv.player = player
            textureView = pv.videoSurfaceView as? TextureView
        },
        modifier = modifier
    )
    // Letterbox sampling (same pattern as TrailerPlayerScreen): every
    // 400ms while playing, past the first 20% of the trailer, downscale
    // the current frame and measure its bars; four stable samples agree
    // and the surface zooms in to crop them out.
    LaunchedEffect(player, textureView) {
        val tv = textureView ?: return@LaunchedEffect
        letterboxZoom = 1f
        val tracker = LetterboxTracker()
        while (true) {
            delay(TrailerLetterbox.SAMPLE_INTERVAL_MS)
            val duration = player.duration
            if (!player.isPlaying || !TrailerLetterbox.isSampleWindowOver(player.currentPosition, duration)) continue
            val frame = runCatching { tv.bitmap }.getOrNull() ?: continue
            val scaled = Bitmap.createScaledBitmap(
                frame, TrailerLetterbox.SAMPLE_WIDTH, TrailerLetterbox.SAMPLE_HEIGHT, true
            )
            if (scaled != frame) frame.recycle()
            val pixels = IntArray(TrailerLetterbox.SAMPLE_WIDTH * TrailerLetterbox.SAMPLE_HEIGHT)
            scaled.getPixels(pixels, 0, TrailerLetterbox.SAMPLE_WIDTH, 0, 0, TrailerLetterbox.SAMPLE_WIDTH, TrailerLetterbox.SAMPLE_HEIGHT)
            scaled.recycle()
            val bar = TrailerLetterbox.detectBar(pixels, TrailerLetterbox.SAMPLE_WIDTH, TrailerLetterbox.SAMPLE_HEIGHT) ?: continue
            tracker.onSample(bar)?.let { letterboxZoom = it }
        }
    }
    LaunchedEffect(letterboxZoom) {
        textureView?.let { it.scaleX = letterboxZoom; it.scaleY = letterboxZoom }
    }
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
        color = TextSecondary,
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
        containerColor = LocalPalette.current.background
    ) {
        // Text padding matches the sources panel / Smart Streams sheet
        // (user 2026-11-23): 16dp sides, 8dp top and bottom — the old
        // 20dp sides with no top padding left the title cramped at the
        // sheet edge and the text sitting further in than the panel's.
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
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
            // The 48dp star buttons inset their 32dp icons by 8dp, so the
            // row is shifted 8dp left to put the first star's visual edge
            // on the text edge (sources panel alignment).
            Row(
                Modifier.padding(vertical = 8.dp).offset(x = (-8).dp),
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
                // No TextButton side padding: the label must sit on the
                // text edge, not 12dp in from it (the default content
                // padding). Vertical padding kept for the touch area.
                TextButton(
                    onClick = { onRate(null) },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)
                ) {
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
    settings: SettingsStore? = null,
    inSheet: Boolean = false
) {
    val palette = LocalPalette.current
    // Step 42: the Detail picker now applies the same Smart filter/sort as
    // the player (no per-source cap here — the full list stays visible).
    val browserState = rememberStreamBrowserState(
        streams, settings, limitPerSource = false, isEpisode = meta.type == "series"
    )
    var smartSheetOpen by remember { mutableStateOf(false) }
    var smartVersion by remember { mutableStateOf(0) }
    val smartActiveCount = if (smartVersion >= 0) settings?.smartStreamSettings()?.filters?.activeCount ?: 0 else 0
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
                        if (settings != null) {
                            TextButton(onClick = { smartSheetOpen = true }) {
                                Text(if (smartActiveCount > 0) "Smart ($smartActiveCount)" else "Smart")
                            }
                        }
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
                        compact = true,
                        settings = settings
                    )
                }
            }
        }
    }
    if (smartSheetOpen && settings != null) {
        SmartStreamSheet(
            streams = streams,
            settings = settings,
            isEpisode = meta.type == "series",
            onChanged = { smartVersion++ },
            onDismiss = { smartSheetOpen = false },
        )
    }
    if (inSheet) pickerBody() else GlowBackground { pickerBody() }
}

/** TMDB recommendation item. */
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
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeCardA(
    video: Video,
    selected: Boolean,
    hideDetails: Boolean,
    isWatched: Boolean,
    spoilerMode: Boolean,
    isTv: Boolean,
    blurThumbnail: Boolean = false,
    runtime: String? = null,
    progress: Float? = null,
    /** TMDB episode rating (Episode Ratings module), null when off/unavailable. */
    tmdbRating: Double? = null,
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
            .cardDepth(RoundedCornerShape(18.dp), CardDepthSurface.EpisodeCards)
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
            // Watch progress along the thumb's bottom edge (Nuvio
            // DetailSeriesContent): only while unfinished.
            if (progress != null && progress > 0f && progress < 1f) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.30f))
                ) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .background(palette.accent)
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
                    val metaLine = listOfNotNull(
                        formatEpisodeDate(video.released).takeIf { it.isNotBlank() },
                        runtime,
                        tmdbRating?.let { "★ %.1f".format(it) }
                    ).joinToString("  •  ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            metaLine,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
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

/**
 * Step 57: the tab row for tab layout — one tab per visible tabbable
 * section; the selected tab follows the scroll position, and tapping
 * a tab scrolls to its section.
 */
@Composable
private fun DetailSectionTabRow(
    settings: DetailSectionSettings,
    availability: DetailSectionAvailability,
    listState: LazyListState,
    onTabClick: (DetailSectionKey) -> Unit
) {
    val ordered = settings.visibleOrdered(availability)
    val tabs = ordered.filter { it.canBeTabbed }
    if (tabs.isEmpty()) return
    // Selected tab follows the scroll position: the section whose item
    // range holds the first visible item.
    val firstVisible = listState.firstVisibleItemIndex
    val selected = remember(firstVisible, ordered) {
        var index = 1 + if (settings.tabLayout) 1 else 0
        var current: DetailSectionKey? = tabs.firstOrNull()
        for (key in ordered) {
            val count = key.itemCount(availability)
            if (firstVisible in index until index + count && key in tabs) current = key
            index += count
        }
        current ?: tabs.first()
    }
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        edgePadding = 16.dp,
        containerColor = LocalPalette.current.background,
        contentColor = TextPrimary
    ) {
        tabs.forEach { key ->
            Tab(
                selected = key == selected,
                onClick = { onTabClick(key) },
                text = { Text(key.title) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// W3 Detail batch (Nuvio v1.17): hero stretch-zoom, floating header and
// the vertical episode List card.
// ---------------------------------------------------------------------------

// Overscroll stretch-zoom for the detail hero (Nuvio
// HeroStretchOverscroll.kt, ported verbatim apart from the KMP
// expect/actual split): pulling down at the top stretches the hero up
// to 200px with 0.55x drag resistance that falls off as the stretch
// grows; releasing springs back. NOTE: like Nuvio, the stretch value
// is pixel-denominated — available.y arrives in px and the 200 cap is
// treated as px (about 77dp at 2.6 density), not converted.
private val HERO_STRETCH_MAX = 200.dp

@Stable
private class HeroStretchState(private val scope: CoroutineScope) {
    val stretch = Animatable(0f)
    val stretchPx: Float get() = stretch.value

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (stretch.value > 0f && available.y < 0f) {
                val consume = minOf(-available.y, stretch.value)
                scope.launch { stretch.snapTo(stretch.value - consume) }
                return Offset(0f, -consume)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (available.y > 0f) {
                val resistance = 0.55f * (1f - stretch.value / HERO_STRETCH_MAX.value)
                scope.launch {
                    stretch.snapTo(
                        (stretch.value + available.y * resistance).coerceAtMost(HERO_STRETCH_MAX.value)
                    )
                }
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (stretch.value > 0f) {
                stretch.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f)
                )
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (stretch.value > 0f) {
                stretch.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 320f)
                )
            }
            return Velocity.Zero
        }
    }
}

@Composable
private fun rememberHeroStretchState(listState: LazyListState): HeroStretchState {
    val scope = rememberCoroutineScope()
    return remember(listState) { HeroStretchState(scope) }
}

private fun Modifier.heroStretchHeight(baseHeight: Dp, stretchPx: () -> Float): Modifier =
    layout { measurable, constraints ->
        val height = baseHeight.roundToPx() + stretchPx().coerceAtLeast(0f).roundToInt()
        val placeable = measurable.measure(
            constraints.copy(minHeight = height, maxHeight = height)
        )
        layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }

private fun Modifier.heroStretchZoom(stretchPx: () -> Float): Modifier =
    graphicsLayer {
        val zoom = 1f + stretchPx().coerceAtLeast(0f) / size.height.coerceAtLeast(1f)
        transformOrigin = TransformOrigin(0.5f, 0f)
        scaleX = zoom
        scaleY = zoom
    }

// Floating glass header (Nuvio DetailFloatingHeader): a 56dp bar that
// fades in once the hero collapses — back button, centred logo (or
// title) and a save toggle. Avio's liquidGlass tints rather than
// blurring the backdrop, so the bar reads as frosted glass over the
// scrolling list (no live backdrop-blur pass exists in this codebase).
@Composable
private fun DetailFloatingHeader(
    meta: MetaDetail,
    isSaved: Boolean,
    progress: Float,
    topPadding: Dp,
    onBack: () -> Unit,
    onToggleSaved: () -> Unit,
    modifier: Modifier = Modifier
) {
    var logoLoadError by remember(meta.id, meta.logo) { mutableStateOf(false) }
    val logoUrl = meta.logo?.takeIf { it.isNotBlank() && !logoLoadError }
    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = progress
                translationY = (-20 * (1f - progress)).dp.toPx()
            }
            .liquidGlass(RectangleShape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = topPadding, start = 16.dp, end = 16.dp)
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
            }
            Box(
                Modifier.weight(1f).padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxWidth(0.6f).height(42.dp),
                        contentScale = ContentScale.Fit,
                        onError = { logoLoadError = true }
                    )
                } else {
                    Text(
                        meta.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            IconButton(onClick = onToggleSaved, modifier = Modifier.size(40.dp)) {
                Icon(
                    if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isSaved) "Saved" else "Save",
                    tint = TextPrimary
                )
            }
        }
    }
}

// Vertical episode card (Nuvio MetaEpisodeCardStyle.List): full-width,
// 120dp tall, thumb left with S·E + watched badges and a progress bar,
// title/date/runtime/overview right.
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun EpisodeListCard(
    video: Video,
    selected: Boolean,
    hideDetails: Boolean,
    isWatched: Boolean,
    spoilerMode: Boolean,
    isTv: Boolean = false,
    blurThumbnail: Boolean = false,
    runtime: String? = null,
    progress: Float? = null,
    /** TMDB episode rating (Episode Ratings module), null when off/unavailable. */
    tmdbRating: Double? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onReveal: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalPalette.current
    Box(
        modifier
            .fillMaxWidth()
            .height(120.dp)
            .liquidGlass(RoundedCornerShape(18.dp))
            .border(
                if (selected) 1.5.dp else 0.5.dp,
                if (selected) palette.accent else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(18.dp)
            )
            .cardDepth(RoundedCornerShape(18.dp), CardDepthSurface.EpisodeCards)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .width(120.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
            ) {
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
                            "S${video.season} · E${video.episode}",
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
                        "S${video.season} · E${video.episode}",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isWatched && !hideDetails) {
                    WatchedPill(Modifier.align(Alignment.TopEnd).padding(9.dp))
                }
                if (progress != null && progress > 0f && progress < 1f) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.30f))
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .background(palette.accent)
                        )
                    }
                }
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
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
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val metaLine = listOfNotNull(
                        formatEpisodeDate(video.released).takeIf { it.isNotBlank() },
                        runtime,
                        tmdbRating?.let { "★ %.1f".format(it) }
                    ).joinToString("  •  ")
                    if (metaLine.isNotBlank()) {
                        Text(
                            metaLine,
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    if (!video.overview.isNullOrBlank()) {
                        Text(
                            video.overview!!,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
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
