package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.pulsestream.app.ui.tv.rememberIsTv
import com.pulsestream.app.ui.tv.tvFocusRing
import kotlinx.coroutines.delay
import kotlin.math.abs
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.CollectionRepository
import com.pulsestream.app.data.CollectionSourceResolver
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.servers.MediaServer
import com.pulsestream.app.data.servers.MediaServerOps
import com.pulsestream.app.data.servers.ServerItem
import com.pulsestream.app.data.servers.ServerLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.pulsestream.app.data.NextUpEpisode
import com.pulsestream.app.data.PlaybackRequest
import com.pulsestream.app.data.StoredHomeRowPrefs
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.heroSourceKeys
import com.pulsestream.app.data.rowKey
import com.pulsestream.app.data.db.Collection
import com.pulsestream.app.data.db.CollectionFolder
import com.pulsestream.app.data.db.PlaybackState
import com.pulsestream.app.ui.components.ErrorState
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.CardDepthSurface
import com.pulsestream.app.ui.components.CustomPosterResolver
import com.pulsestream.app.ui.components.CustomPosterScreen
import com.pulsestream.app.ui.components.LocalPosterStyle
import com.pulsestream.app.ui.components.cardDepth
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.data.CustomListsRepository
import com.pulsestream.app.data.cloudstream.CloudStreamPluginItem
import com.pulsestream.app.data.cloudstream.CloudStreamRepository
import com.pulsestream.app.ui.components.AddToListSheet
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.components.SectionTitle
import com.pulsestream.app.ui.components.ShimmerPoster
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.components.tmdbSizedImage
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Dock → Home scroll-to-top signal (v0.12.4): the bottom dock's Home tab
 * bumps [requests] whenever it's tapped while unfolded; HomeScreen
 * collects it and smooth-scrolls its list to the very top. A plain
 * counter (not an event bus) so a tap that lands while Home is being
 * restored from the back stack still takes effect on arrival.
 */
object HomeScrollSignal {
    val requests = MutableStateFlow(0)
    fun requestScrollToTop() {
        requests.value += 1
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Content(val rows: List<CatalogRow>, val refreshing: Boolean = false) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(private val repo: StreamRepository) : ViewModel() {
    var uiState by mutableStateOf<HomeUiState>(HomeUiState.Loading)
        private set

    /** Row prefs (hide/order/rename/pin/hero-source) — read after each row load. */
    var homePrefs by mutableStateOf(StoredHomeRowPrefs())
        private set

    /** CS3 provider selection for the Home pill ("all" | "off" | plugin id | ""). */
    var cloudStreamSelection by mutableStateOf("")
        private set

    /** Runnable CS3 providers, for the Home selector pill. */
    var cloudStreamProviders by mutableStateOf<List<CloudStreamPluginItem>>(emptyList())
        private set

    /** Applies a pill choice and reloads Home rows for it. */
    fun updateCloudStreamSelection(selection: String) {
        repo.setCloudStreamHomeSelection(selection)
        cloudStreamSelection = selection
        refresh()
    }

    /** Re-reads the CS3 selection + runnable providers after a row load. */
    private fun syncCloudStreamSelection() {
        cloudStreamSelection = repo.resolveCloudStreamHomeSelection()
        cloudStreamProviders = CloudStreamRepository.runnablePlugins()
    }

    /**
     * CS3 home rows refresh separately, in the background: the Stremio
     * refresh never waits for a slow provider, and the CS3 rows merge in
     * when they arrive. Cached CS3 rows stay visible until then (the
     * Stremio refresh keeps them via its retained-rows logic).
     */
    private fun refreshCloudStreamRows() {
        viewModelScope.launch {
            runCatching { repo.refreshCloudStreamHomeRows() }
                .onSuccess { mergeCloudStreamRows(it) }
        }
    }

    private fun mergeCloudStreamRows(cs3Rows: List<CatalogRow>) {
        val state = uiState
        val fresh = cs3Rows.filter { it.type != "tv" }
        val current = (state as? HomeUiState.Content)?.rows
        if (current != null) {
            val kept = current.filterNot { repo.isCloudStreamRowAddonId(it.addonId) }
            uiState = HomeUiState.Content(
                (kept + fresh).distinctBy { "${it.addonId}|${it.type}|${it.catalogId}" }
            )
        } else if (state is HomeUiState.Empty && fresh.isNotEmpty()) {
            // Cold load with no Stremio rows at all: the CS3 rows ARE Home.
            uiState = HomeUiState.Content(fresh)
        }
        // Loading/Error: a fresh load is in flight and will run its own
        // CS3 refresh — never clobber it with stale rows.
    }

    /**
     * Cache-first loading: show cached catalogs instantly, then quietly
     * refresh in the background — but only when the cache is stale (the
     * splash warm-up may have just refreshed it). [forceRefresh] skips
     * the cache entirely.
     */
    fun load(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (!forceRefresh) {
                val cached = repo.loadCatalogRowsCached().filter { it.type != "tv" }
                // Prefs are read after the row load — the load normalizes
                // them (new rows get records), so this is never stale.
                homePrefs = repo.homeRowPrefs()
                syncCloudStreamSelection()
                if (cached.isNotEmpty()) {
                    val stale = !repo.isCatalogCacheFresh()
                    uiState = HomeUiState.Content(cached, refreshing = stale)
                    if (stale) {
                        runCatching { repo.refreshCatalogRows().filter { it.type != "tv" } }
                            .onSuccess {
                                uiState = HomeUiState.Content(it)
                                refreshCloudStreamRows()
                            }
                            .onFailure {
                                uiState = HomeUiState.Content(cached)
                                refreshCloudStreamRows()
                            }
                    }
                    return@launch
                }
            }
            uiState = HomeUiState.Loading
            try {
                // Live TV has its own tab — never show tv-type rows on Home
                // (hero or cards).
                val rows = repo.refreshCatalogRows().filter { it.type != "tv" }
                homePrefs = repo.homeRowPrefs()
                syncCloudStreamSelection()
                uiState = if (rows.isEmpty()) HomeUiState.Empty else HomeUiState.Content(rows)
                refreshCloudStreamRows()
            } catch (e: Exception) {
                uiState = HomeUiState.Error(e.message ?: "Couldn't load catalogs")
            }
        }
    }

    /**
     * Pull-to-refresh: reload catalogs from the network while keeping the
     * current rows on screen with the refresh indicator.
     */
    fun refresh() {
        val current = (uiState as? HomeUiState.Content)?.rows
        if (current == null) {
            load(forceRefresh = true)
            return
        }
        // Don't stack refreshes.
        if ((uiState as? HomeUiState.Content)?.refreshing == true) return
        viewModelScope.launch {
            uiState = HomeUiState.Content(current, refreshing = true)
            runCatching { repo.refreshCatalogRows().filter { it.type != "tv" } }
                .onSuccess {
                    syncCloudStreamSelection()
                    uiState = HomeUiState.Content(it)
                    refreshCloudStreamRows()
                }
                .onFailure { uiState = HomeUiState.Content(current) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    collectionRepository: CollectionRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onResumePlayback: () -> Unit,
    onGoToAddons: () -> Unit,
    onGoToWatchlist: () -> Unit,
    onGoToLists: () -> Unit,
    onOpenCatalog: (row: CatalogRow) -> Unit,
    onOpenCollectionFolder: (folderId: String) -> Unit = {},
    onPlayServerItem: (PlaybackRequest) -> Unit = {},
    onOpenServerLibrary: (serverId: String, libraryId: String) -> Unit = { _, _ -> },
    onGoToDownloads: () -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(factory = vmFactory { HomeViewModel(repository) })

    // Scroll restore (Nuvio MaintainHomeScrollPosition): the list state
    // is saveable, so backing out of Detail restores the exact spot; it
    // starts from the persisted index so a cold start lands where the
    // last session left off. Hoisted above the uiState switch — the old
    // rememberLazyListState() inside Content reset to the top on every
    // return from Detail.
    val homeListState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = repository.settings.homeScrollIndex,
            firstVisibleItemScrollOffset = repository.settings.homeScrollOffset
        )
    }
    // Persist the spot when Home leaves the composition (Detail open,
    // app closed) so the next cold start restores it.
    DisposableEffect(Unit) {
        onDispose {
            repository.settings.homeScrollIndex = homeListState.firstVisibleItemIndex
            repository.settings.homeScrollOffset = homeListState.firstVisibleItemScrollOffset
        }
    }
    val watching by repository.continueWatching.collectAsState(initial = emptyList())
    val settings = repository.settings
    // Continue Watching prefs (Nuvio H7/H8): sort + split, applied to the
    // raw resume list; the card style + click action are read at the section.
    val sortedWatching = remember(
        watching, settings.continueWatchingSortMode, settings.watchedThresholdPercent
    ) {
        applyContinueWatchingSort(
            watching, settings.continueWatchingSortMode, settings.watchedThresholdPercent
        )
    }
    // Step 49: Next Up — the episode after the seed episode per series
    // (shared enrichment with the Library calendar). The seed follows the
    // "Next Up from furthest episode" setting: furthest-watched by default,
    // most-recently-played for rewatches. The effect restarts only when the
    // watched set or that setting changes — NOT on every resume-position
    // save — so the watching snapshot used for the recency seed is the one
    // current at the last restart.
    val watchedKeys by com.pulsestream.app.data.WatchedMarks.keys.collectAsState()
    var nextUp by remember { mutableStateOf<List<com.pulsestream.app.data.NextUpEpisode>>(emptyList()) }
    LaunchedEffect(watchedKeys, settings.continueWatchingNextUpFromFurthestEpisode) {
        nextUp = com.pulsestream.app.data.NextUpResolver.resolveNextUp(
            repository,
            watchedKeys,
            settings.continueWatchingNextUpFromFurthestEpisode,
            watching,
        )
    }
    // Show-unaired gate (Nuvio showUnairedNextUp): OFF drops episodes that
    // have not aired yet from the Next Up rail entirely.
    val visibleNextUp = remember(nextUp, settings.continueWatchingShowUnairedNextUp) {
        if (settings.continueWatchingShowUnairedNextUp) nextUp
        else nextUp.filter { it.hasAired() }
    }
    // Split-upcoming (Nuvio splitUpcomingItems): with sort mode
    // split_upcoming, unaired next-ups move to their own "Coming up" row.
    val (mainWatching, upcomingWatching) = remember(
        sortedWatching, visibleNextUp, settings.continueWatchingSortMode
    ) {
        splitContinueWatchingUpcoming(sortedWatching, visibleNextUp, settings.continueWatchingSortMode)
    }
    // The Next Up rail shows the visible next-ups minus the ones the split
    // moved to "Coming up".
    val railNextUp = remember(visibleNextUp, upcomingWatching) {
        visibleNextUp - upcomingWatching.toSet()
    }
    // Step 51: collections as Home rows + hero sources (Nuvio
    // HomeCollectionRowSection + collectionHeroSources). Pinned
    // collections feed the hero: their folders' first titles resolve
    // once per collection set and merge ahead of the row hero titles.
    val collections by collectionRepository.collections.collectAsState(initial = emptyList())
    val pinnedCollections = remember(collections) { collections.filter { it.pinToTop } }
    var collectionHeroTitles by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    LaunchedEffect(pinnedCollections) {
        if (pinnedCollections.isEmpty()) {
            collectionHeroTitles = emptyList()
            return@LaunchedEffect
        }
        val titles = mutableListOf<MetaPreview>()
        for (collection in pinnedCollections) {
            for (folder in collectionRepository.getFolders(collection.id)) {
                val sources = collectionRepository.getSources(folder.id)
                if (sources.isEmpty()) continue
                titles += CollectionSourceResolver.resolveFolder(repository, sources, limit = 20).take(4)
            }
        }
        collectionHeroTitles = titles.distinctBy { "${it.type}|${it.id}" }
    }
    // Step 55: linked media servers — reloaded every time Home enters
    // composition (returning from the Media Servers page re-reads the
    // encrypted store, so a just-added server shows at once).
    var mediaServers by remember { mutableStateOf<List<MediaServer>>(emptyList()) }
    LaunchedEffect(Unit) {
        mediaServers = withContext(Dispatchers.IO) { settings.getMediaServers() }
    }
    // Resume prompt on launch (Nuvio ResumePromptRepository, scoped
    // port): prompt ONLY when the last session ended inside the player.
    // The flag is armed when the player opens and cleared when it is
    // left normally, so a set flag at launch means the app was killed
    // or swiped away mid-playback. Consumed once here — the flag is
    // cleared before the dialog shows, so Dismiss / Resume never
    // re-prompts. The old `remember` guard died every time Home left
    // the composition (Detail / player), which is why the dialog kept
    // coming back on every return to Home.
    var resumePromptOpen by remember { mutableStateOf(false) }
    var resumePromptItem by remember { mutableStateOf<PlaybackState?>(null) }
    LaunchedEffect(sortedWatching) {
        if (resumePromptItem != null) return@LaunchedEffect
        if (!settings.continueWatchingResumePrompt) return@LaunchedEffect
        if (!settings.resumePromptWasInPlayer) return@LaunchedEffect
        if (sortedWatching.isEmpty()) return@LaunchedEffect
        val videoId = settings.resumePromptLastVideoId
        // Consume first: whatever happens next, this launch never prompts again.
        settings.resumePromptWasInPlayer = false
        settings.resumePromptLastVideoId = ""
        val item = if (videoId.isBlank()) sortedWatching.firstOrNull()
            else sortedWatching.firstOrNull { it.videoId == videoId }
        if (item != null) {
            resumePromptItem = item
            resumePromptOpen = true
        }
    }
    // CS3 (Phase 2): when the plugin registry changes (install/enable/
    // disable/remove) or the runnable-provider count changes, Home rows
    // must re-fetch — mirror of Infuse's MainAppContent refresh key
    // ("${registryRevision}:${runnableCount}"). The first emission is
    // skipped: the initial load already fetches. Guarded to Content so a
    // registry change during the first load never restarts it.
    val cs3State by CloudStreamRepository.uiState.collectAsState()
    val cs3RefreshKey = "${cs3State.registryRevision}:${cs3State.plugins.count { it.isRunnable }}"
    var seenCs3RefreshKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cs3RefreshKey) {
        if (seenCs3RefreshKey == null) {
            seenCs3RefreshKey = cs3RefreshKey
            return@LaunchedEffect
        }
        seenCs3RefreshKey = cs3RefreshKey
        if (vm.uiState is HomeUiState.Content) vm.refresh()
    }
    val scope = rememberCoroutineScope()

    /** Shared by the CW card tap and the resume prompt: resume the saved position. */
    fun resumeItem(item: PlaybackState) {
        repository.pendingPlayback = PlaybackRequest(
            streamUrl = item.streamUrl,
            title = item.title,
            poster = item.poster,
            videoId = item.videoId,
            imdbId = item.imdbId,
            season = item.season,
            episode = item.episode,
            isMovie = item.season == 0 && item.episode == 0,
            type = if (item.season == 0 && item.episode == 0) "movie" else "series"
        )
        onResumePlayback()
    }

    /** Details for a resume card: Cinemeta is the preferred meta addon. */
    fun openItemDetails(item: PlaybackState) {
        val isMovie = item.season == 0 && item.episode == 0
        onOpenDetail(
            "com.linvo.cinemeta",
            if (isMovie) "movie" else "series",
            item.videoId.substringBefore(":")
        )
    }

    /** CW tap (Nuvio ContinueWatchingClickAction): stream, or details when set. */
    fun onWatchingClick(item: PlaybackState) {
        if (settings.continueWatchingClickAction == "details") openItemDetails(item)
        else resumeItem(item)
    }
    // Long-press context menu target: (addonId, meta) for the pressed poster.
    var contextMenuTarget by remember { mutableStateOf<Pair<String, MetaPreview>?>(null) }
    // Add-to-list sheet target: (addonId, meta).
    var addToListTarget by remember { mutableStateOf<Pair<String, MetaPreview>?>(null) }
    // Watchlist lives ONLY in the Watchlist tab (top-right bookmark) — it is
    // deliberately not shown on Home (v0.8.21 behavior fix).
    // Reload when Home catalog visibility changes in Settings.
    val catalogVersion by repository.catalogVisibility.collectAsState(initial = 0)

    LaunchedEffect(catalogVersion) { vm.load() }

    // The status-bar inset the Scaffold already padded away above this
    // screen: the Content list shifts up by exactly this amount so the
    // immersive hero reaches the very top of the screen, under the
    // status bar (v0.12.4, Sample C).
    val density = LocalDensity.current
    val statusBarDp = with(density) { WindowInsets.statusBars.getTop(density).toDp() }
    val statusBarPx = with(density) { statusBarDp.roundToPx() }

    // Android TV: initial D-pad focus target (the hero card).
    val isTv = rememberIsTv()
    val heroFocus = remember { FocusRequester() }
    // Land focus on the hero ONCE per Home visit — never yank it back on
    // every catalog refresh while the user is browsing the rows.
    var heroFocusDone by remember { mutableStateOf(false) }

    GlowBackground {
        // No header (v0.12.3, owner's order): the "Plavio" wordmark +
        // tagline row is gone, so the screen starts clear and the hero
        // poster owns the full top of the screen. The one functional
        // control the header held — the Saved (Watchlist / My Lists)
        // button — floats overlaid at the top end (below, after the
        // content states).
        Box(Modifier.fillMaxSize()) {
            when (val state = vm.uiState) {
                is HomeUiState.Loading -> HomeSkeleton()
                is HomeUiState.Error -> ErrorState(state.message, onRetry = { vm.load() })
                is HomeUiState.Empty -> NoAddonsEmptyState(
                    repository = repository,
                    onGoToAddons = onGoToAddons,
                    onReload = { vm.load() }
                )
                is HomeUiState.Content -> {
                    // Hero carousel data: computed once per row-set, outside the
                    // LazyColumn DSL (remember can't run inside the list scope).
                    // The hero feeds ONLY from the rows picked as hero sources
                    // (max 2, Nuvio) — not from every row — and takes up to 8
                    // titles, shuffled by featuredTitles' backdrop-first order.
                    val prefs = vm.homePrefs
                    val heroRows = remember(state.rows, prefs) {
                        val heroKeys = prefs.heroSourceKeys()
                        state.rows.filter { row -> row.rowKey() in heroKeys }
                    }
                    // Step 51: pinned collections feed the hero ahead of
                    // the row-based titles (Nuvio collectionHeroSources).
                    val featured = remember(heroRows, collectionHeroTitles) {
                        val fromCollections = collectionHeroTitles.map { FeaturedTitle("", it) }
                        (fromCollections + featuredTitles(heroRows, max = 8))
                            .distinctBy { it.meta.id }.take(8)
                    }
                    // Step 51: collection sections, pinned first.
                    val orderedCollections = remember(collections) {
                        collections.sortedWith(
                            compareByDescending<Collection> { it.pinToTop }
                                .thenBy { it.sortOrder }
                        )
                    }
                    // Step 69: Release Radar — new episodes/seasons from
                    // the library + new releases in the home rows. Rebuilt
                    // when any input changes; the library leg reuses
                    // NextUpResolver's 6h meta cache.
                    val watchlist by repository.watchlist.collectAsState(initial = emptyList())
                    val catalogItems = remember(state.rows) { state.rows.flatMap { it.items } }
                    var radarItems by remember { mutableStateOf<List<com.pulsestream.app.data.ReleaseRadarItem>>(emptyList()) }
                    LaunchedEffect(nextUp, watchlist, catalogItems, watchedKeys) {
                        radarItems = com.pulsestream.app.data.ReleaseRadar.build(
                            repository, nextUp, watchlist, catalogItems, watchedKeys
                        )
                    }
                    // Dock Home-tap → scroll to the very top (v0.12.4).
                    // handledScrollRequest is saveable so a request fired
                    // while Home sat in the back stack is honoured once,
                    // on return, and never replayed on later visits.
                    var handledScrollRequest by rememberSaveable {
                        mutableStateOf(HomeScrollSignal.requests.value)
                    }
                    val scrollRequests by HomeScrollSignal.requests.collectAsState()
                    LaunchedEffect(scrollRequests) {
                        if (scrollRequests != handledScrollRequest) {
                            handledScrollRequest = scrollRequests
                            runCatching { homeListState.animateScrollToItem(0) }
                        }
                    }
                    // TV: land D-pad focus on the hero once content is in.
                    LaunchedEffect(isTv, featured.size) {
                        if (isTv && featured.isNotEmpty() && !heroFocusDone) {
                            heroFocusDone = true
                            delay(600)
                            runCatching { heroFocus.requestFocus() }
                        }
                    }
                    val pullRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = state.refreshing,
                        onRefresh = { vm.refresh() },
                        modifier = Modifier.fillMaxSize(),
                        state = pullRefreshState,
                        indicator = {
                            // Refresh indicator: the signature orbit
                            // (owner's order 2026-10-26 — supersedes the
                            // Wave dots pick of 2026-10-25; the orbit is
                            // the app's one loading circle everywhere).
                            if (state.refreshing || pullRefreshState.distanceFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 18.dp)
                                ) {
                                    OrbitDots(modifier = Modifier.size(38.dp))
                                }
                            }
                        }
                    ) {
                    LazyColumn(
                        state = homeListState,
                        modifier = Modifier
                            .fillMaxSize()
                            // Full-bleed BOTH ends (v0.12.12): the column
                            // is measured statusBarPx TALLER than its
                            // parent and placed shifted up by the same
                            // amount, so the hero reaches the screen's
                            // top edge AND the viewport reaches the
                            // physical bottom edge — cards scroll under
                            // and below the floating dock to the very
                            // edge. The old plain .offset() shifted the
                            // column up WITHOUT the extra height, ending
                            // the viewport short of the bottom and
                            // leaving a solid strip under the dock.
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(
                                        minHeight = constraints.maxHeight + statusBarPx,
                                        maxHeight = constraints.maxHeight + statusBarPx
                                    )
                                )
                                layout(placeable.width, placeable.height) {
                                    placeable.place(0, -statusBarPx)
                                }
                            },
                        // Extra bottom room so the last row scrolls clear
                        // of the floating dock (content now runs under it).
                        contentPadding = PaddingValues(bottom = 104.dp)
                    ) {
                        // Hero carousel: featured titles with backdrop art, first item
                        // so it's the first thing on screen. Explicit key keeps it
                        // stable while rows refresh underneath. Off when the user
                        // turned the hero off in Home catalogs settings.
                        if (prefs.heroEnabled && featured.isNotEmpty()) {
                            item(key = "hero_carousel") {
                                // Scroll-away hero (Nuvio HomeHeroSection):
                                // a normal list item with parallax — the
                                // pinned-hero sheet (v0.12.5) is gone, the
                                // sections below no longer slide over it.
                                HeroCarousel(
                                    featured = featured,
                                    repository = repository,
                                    listState = homeListState,
                                    onOpenDetail = onOpenDetail,
                                    heroFocus = heroFocus
                                )
                            }
                        }
                        if (settings.continueWatchingVisible && mainWatching.isNotEmpty()) {
                            // Explicit key: this section appears/disappears as the
                            // Flow emits, so an index-based default key would
                            // reshuffle every row below it on each emission.
                            item(key = "continue_watching") {
                              Column(
                                // Solid section background (was the
                                // scroll-over sheet over the pinned hero,
                                // now just the section surface).
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Continue Watching")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(
                                        mainWatching,
                                        key = { index, item -> "${item.key}_$index" }
                                    ) { _, item ->
                                        ContinueWatchingCard(
                                            item,
                                            style = settings.continueWatchingStyle,
                                            watchedThresholdPercent = settings.watchedThresholdPercent,
                                            onRemove = {
                                                scope.launch {
                                                    repository.deleteSavedPosition(item.key)
                                                }
                                            }
                                        ) {
                                            onWatchingClick(item)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // Split-upcoming second section (Nuvio splitUpcomingItems):
                        // with sort mode split_upcoming, unaired next-up
                        // episodes live here, nearest air date first.
                        if (settings.continueWatchingVisible && upcomingWatching.isNotEmpty()) {
                            item(key = "continue_watching_upcoming") {
                              Column(
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Coming up")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        upcomingWatching,
                                        key = { "${it.showId}_${it.season}_${it.episode}" }
                                    ) { episode ->
                                        NextUpCard(
                                            episode,
                                            preferEpisodeThumbnails = settings.continueWatchingPreferEpisodeThumbnails,
                                        ) {
                                            onOpenDetail("", "series", episode.showId)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // Step 49: Next Up — one card per series, the
                        // episode right after the seed one. With sort mode
                        // split_upcoming the unaired ones live in the
                        // "Coming up" row above instead.
                        if (railNextUp.isNotEmpty()) {
                            item(key = "next_up") {
                              Column(
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Next Up")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        railNextUp,
                                        key = { "${it.showId}_${it.season}_${it.episode}" }
                                    ) { episode ->
                                        NextUpCard(
                                            episode,
                                            preferEpisodeThumbnails = settings.continueWatchingPreferEpisodeThumbnails,
                                        ) {
                                            onOpenDetail("", "series", episode.showId)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // Step 69: Release Radar — new from your library.
                        if (radarItems.isNotEmpty()) {
                            item(key = "release_radar") {
                              Column(
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Release Radar")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(
                                        radarItems,
                                        key = { it.key }
                                    ) { item ->
                                        ReleaseRadarCard(item) {
                                            onOpenDetail("", item.metaType, item.metaId)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // Watchlist intentionally NOT shown here (v0.8.21):
                        // bookmarked titles live only in the Watchlist tab.
                        // CS3 (Phase 2): the provider selector pill sits
                        // directly above the first CS3 row (Infuse's
                        // CloudStreamProviderSelectorHeader placement).
                        val firstCs3RowIndex = state.rows.indexOfFirst { it.addonId.startsWith("cs3:") }
                        state.rows.forEachIndexed { rowIndex, row ->
                            if (rowIndex == firstCs3RowIndex && vm.cloudStreamProviders.isNotEmpty()) {
                                item(key = "cloudstream_provider_selector") {
                                    CloudStreamProviderSelectorHeader(
                                        providers = vm.cloudStreamProviders,
                                        selectedProviderId = vm.cloudStreamSelection,
                                        onSelect = { vm.updateCloudStreamSelection(it) },
                                    )
                                }
                            }
                            // THE v0.3.0 scroll-crash fix: the key MUST include the
                            // catalog TYPE. Cinemeta declares the same catalog ids
                            // ("top", "year", "imdbRating") for both movie and
                            // series — without the type, two rows shared one key
                            // and LazyColumn threw, closing the app on scroll.
                            item(key = "${row.addonId}|${row.type}|${row.catalogId}") {
                              Column(
                                // Same solid background as Continue
                                // Watching (was the scroll-over sheet).
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                // Typed local: an `if (c) null else { ... }`
                                // argument cannot carry a lambda literal in
                                // the else branch (nullable expected type
                                // parses the braces as a block, Unit?).
                                val openCatalogAction: () -> Unit = { onOpenCatalog(row) }
                                SectionTitle(
                                    row.catalogName,
                                    // Don't repeat the addon name when the catalog name
                                    // already starts with it ("PlavioMeta: Trending
                                    // Movies" + "PlavioMeta" looks broken). The whole
                                    // subtitle goes away with "Show catalog type" off.
                                    row.addonName.takeIf {
                                        prefs.showCatalogType &&
                                            !row.catalogName.startsWith(it, ignoreCase = true)
                                    },
                                    onMoreClick = if (row.addonId.startsWith("cs3:")) null
                                        else openCatalogAction,
                                    // Nuvio catalog rows use the compact 32dp View All pill.
                                    compact = true
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Nuvio row preview cap: 18 items per shelf
                                    // row; the rest live behind View All.
                                    itemsIndexed(
                                        row.items.take(18),
                                        key = { index, meta -> "${row.addonId}_${meta.id}_$index" }
                                    ) { index, meta ->
                                        PosterCard(
                                            meta = meta,
                                            showRatingChip = true,
                                            onClick = { onOpenDetail(row.addonId, meta.type, meta.id) },
                                            onLongClick = { contextMenuTarget = row.addonId to meta }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                              }
                            }
                        }
                        // No CS3 rows on Home (selection "off", or the
                        // fetch failed) but providers exist: the pill still
                        // shows, at the end of the catalog rows, so the
                        // user can always switch back from "off".
                        if (firstCs3RowIndex == -1 && vm.cloudStreamProviders.isNotEmpty()) {
                            item(key = "cloudstream_provider_selector") {
                                CloudStreamProviderSelectorHeader(
                                    providers = vm.cloudStreamProviders,
                                    selectedProviderId = vm.cloudStreamSelection,
                                    onSelect = { vm.updateCloudStreamSelection(it) },
                                )
                            }
                        }
                        // Step 51: collection sections (Nuvio
                        // HomeCollectionRowSection) — each collection
                        // renders its folders as cards; pinned first.
                        // Tap a folder to open its titles as a full grid.
                        orderedCollections.forEach { collection ->
                            item(key = "collection_${collection.id}") {
                                CollectionHomeSection(
                                    collection = collection,
                                    collectionRepository = collectionRepository,
                                    repository = repository,
                                    onOpenFolder = onOpenCollectionFolder
                                )
                            }
                        }
                        // Step 55: media server rows — one row per library
                        // of each linked server, first 20 titles.
                        mediaServers.forEach { server ->
                            item(key = "server_${server.id}") {
                                ServerHomeSection(
                                    server = server,
                                    onPlayServerItem = onPlayServerItem,
                                    onOpenServerLibrary = onOpenServerLibrary
                                )
                            }
                        }
                    }
                    }
                }
            }

        }
    }

    // Long-press context menu: Add to Watchlist / Add to My List.
    contextMenuTarget?.let { (addonId, meta) ->
        Dialog(onDismissRequest = { contextMenuTarget = null }) {
            Column(
                modifier = Modifier
                    .liquidGlass(RoundedCornerShape(20.dp))
                    .padding(vertical = 8.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text(
                    text = meta.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.toggleWatchlist(addonId, meta.type, meta.id, meta.name, meta.poster)
                        }
                        contextMenuTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add to Watchlist", color = TextPrimary)
                }
                TextButton(
                    onClick = {
                        addToListTarget = addonId to meta
                        contextMenuTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Filled.FormatListBulleted,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Add to My List", color = TextPrimary)
                }
            }
        }
    }

    // Add-to-list bottom sheet.
    addToListTarget?.let { (addonId, meta) ->
        AddToListSheet(
            listsRepository = listsRepository,
            addonId = addonId,
            type = meta.type,
            metaId = meta.id,
            name = meta.name,
            poster = meta.poster,
            onDismiss = { addToListTarget = null }
        )
    }

    // Resume prompt on launch: the interrupted title, offered once.
    // Capture into a local val: resumePromptItem is a delegated property
    // (by remember), so the compiler forbids smart-casting it after a null check.
    val promptItem = resumePromptItem
    if (resumePromptOpen && promptItem != null) {
        val remainingMs = promptItem.durationMs - promptItem.positionMs
        AlertDialog(
            onDismissRequest = { resumePromptOpen = false },
            title = { Text("Continue watching?", color = TextPrimary) },
            text = {
                Column {
                    Text(promptItem.title, color = TextPrimary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (remainingMs > 60_000) "${formatRemaining(remainingMs)} left"
                        else "Tap Resume to pick up where you left off",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { resumePromptOpen = false; resumeItem(promptItem) }) {
                    Text("Resume", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { resumePromptOpen = false }) {
                    Text("Dismiss", color = TextSecondary)
                }
            }
        )
    }
}

/** One featured title for the hero carousel: its source addon plus metadata. */
private data class FeaturedTitle(val addonId: String, val meta: MetaPreview)

/**
 * Picks up to 6 featured titles for the hero carousel: titles with real
 * backdrop art first (falling back to poster-only titles so the carousel
 * never comes up empty when data is thin).
 */
private fun featuredTitles(rows: List<CatalogRow>, max: Int = 6): List<FeaturedTitle> {
    val all = rows.flatMap { row -> row.items.map { FeaturedTitle(row.addonId, it) } }
        .distinctBy { it.meta.id }
    if (all.isEmpty()) return emptyList()
    val withBackdrop = all.filter { !it.meta.background.isNullOrBlank() }
    return (withBackdrop + all).distinctBy { it.meta.id }.take(max)
}

/**
 * Nuvio-style home hero (HomeHeroSection, FULL_BLEED): backdrop-first art
 * at base scale 1.14x with parallax scroll-away, logo (or centred
 * displaySmall title), two meta rows, 2-line overview, Details pill, and
 * page dots below the content. Auto-advances every 8s. Replaces the
 * pinned hero (v0.12.5) — the hero is a normal list item again and
 * scrolls away; sections no longer slide over it.
 *
 * Richer meta rows need the full meta per hero title (runtime, age
 * rating, description, logo live only there) — like Nuvio, the current
 * page and its neighbours fetch once and merge into the catalog data.
 */
private data class HeroMetaExtras(
    val runtime: String? = null,
    val certification: String? = null,
    val status: String? = null,
    val description: String? = null,
    val logo: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String> = emptyList(),
    val background: String? = null
)

@Composable
private fun HeroCarousel(
    featured: List<FeaturedTitle>,
    repository: StreamRepository,
    listState: LazyListState,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    heroFocus: FocusRequester? = null
) {
    val pagerState = rememberPagerState(pageCount = { featured.size })
    val isTv = rememberIsTv()
    // Nuvio hero height (homeHeroLayout): phone portrait = width x 1.5;
    // landscape = viewport x 0.82 clamped 360-760dp. TV keeps 380dp.
    val config = LocalConfiguration.current
    val heroHeight = if (isTv) 380.dp
        else if (config.screenWidthDp > config.screenHeightDp)
            (config.screenHeightDp * 0.82f).dp.coerceIn(360.dp, 760.dp)
        else (config.screenWidthDp * 1.5f).dp

    val reducedMotion = com.pulsestream.app.data.rememberReducedMotion()
    LaunchedEffect(pagerState, featured.size, reducedMotion) {
        // No auto-advance on TV: the 8s full-bleed pager animation steals
        // frames from D-pad browsing on weak TV GPUs/CPUs and its backdrop
        // re-decodes cause jank spikes that coincide with user input.
        if (isTv || reducedMotion) return@LaunchedEffect
        while (featured.size > 1) {
            delay(8000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % featured.size
                runCatching { pagerState.animateScrollToPage(next) }
            }
        }
    }

    // Full meta for the hero titles, fetched once per title (current page
    // + neighbours, Nuvio-style) and merged over the catalog preview.
    var heroExtras by remember { mutableStateOf<Map<String, HeroMetaExtras>>(emptyMap()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState.currentPage, featured) {
        val current = pagerState.currentPage
        listOf(current - 1, current, current + 1)
            .filter { it in featured.indices }
            .forEach { index ->
                val item = featured[index]
                val key = "${item.meta.type}|${item.meta.id}"
                if (!heroExtras.containsKey(key)) {
                    scope.launch {
                        val detail = runCatching {
                            repository.getMetaFromAny(null, item.meta.type, item.meta.id)
                        }.getOrNull() ?: return@launch
                        heroExtras = heroExtras + (key to HeroMetaExtras(
                            runtime = detail.runtime,
                            certification = detail.certification,
                            status = detail.status,
                            description = detail.description,
                            logo = detail.logo,
                            releaseInfo = detail.releaseInfo,
                            imdbRating = detail.imdbRating,
                            genres = detail.genres,
                            background = detail.background
                        ))
                    }
                }
            }
    }

    // Scroll parallax (Nuvio HERO_BACKGROUND_PARALLAX 0.055 / content 0.18 /
    // scroll 0.3, base scale 1.14x): the backdrop lags the list and grows
    // slightly as the hero scrolls away.
    val density = LocalDensity.current
    val heroHeightPx = with(density) { heroHeight.toPx() }
    val scrollOffsetPx by remember(listState, heroHeightPx) {
        derivedStateOf {
            when {
                listState.firstVisibleItemIndex > 0 -> heroHeightPx
                else -> listState.firstVisibleItemScrollOffset.toFloat()
            }
        }
    }
    val heroWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val bgColor = LocalPalette.current.background

    Box(
        Modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clip(RectangleShape)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> "hero_${featured[index].meta.id}_$index" }
        ) { index ->
            val item = featured[index]
            val meta = item.meta
            val extras = heroExtras["${meta.type}|${meta.id}"]
            // Merged view: catalog data first, full-meta gaps filled in.
            val displayMeta = meta.copy(
                logo = meta.logo ?: extras?.logo,
                description = meta.description ?: extras?.description,
                releaseInfo = meta.releaseInfo ?: extras?.releaseInfo,
                imdbRating = meta.imdbRating ?: extras?.imdbRating,
                genres = if (meta.genres.isEmpty()) extras?.genres.orEmpty() else meta.genres,
                background = meta.background ?: extras?.background
            )
            val pageOffset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRing(RoundedCornerShape(0.dp))
                    .clickable { onOpenDetail(item.addonId, meta.type, meta.id) }
                    .then(if (heroFocus != null && index == 0) Modifier.focusRequester(heroFocus) else Modifier)
                    .focusable()
            ) {
                // Backdrop-first art (Nuvio): banner, else poster.
                SafePosterImage(
                    model = displayMeta.background?.takeIf { it.isNotBlank() } ?: displayMeta.poster,
                    contentDescription = displayMeta.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = scrollOffsetPx * 0.3f
                            val inc = if (scrollOffsetPx < 0f) abs(scrollOffsetPx) * 0.002f
                                else scrollOffsetPx * 0.0001f
                            val scale = 1.14f * (1f + inc).coerceAtMost(1.3f)
                            scaleX = scale
                            scaleY = scale
                        },
                    contentScale = ContentScale.Crop
                )
                // Full-height gradient fade (Nuvio alphas 0.02/0.12/0.34/0.78)
                // + 220dp bottom fade to the background.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                bgColor.copy(alpha = 0.02f),
                                bgColor.copy(alpha = 0.12f),
                                bgColor.copy(alpha = 0.34f),
                                bgColor.copy(alpha = 0.78f)
                            )
                        )
                    )
                )
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, bgColor)
                            )
                        )
                )
                // Content: logo or centred title, two meta rows, overview,
                // Details pill, dots — Nuvio HeroContentBlock, centred on
                // mobile, with swipe parallax (0.18x) on the content.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
                        .graphicsLayer {
                            val visibility = (1f - abs(pageOffset)).coerceIn(0f, 1f)
                            alpha = ((visibility - 0.5f) * 2f).coerceIn(0f, 1f)
                            translationX = -pageOffset * heroWidthPx * 0.18f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    var logoLoadError by remember(displayMeta.type, displayMeta.id, displayMeta.logo) {
                        mutableStateOf(false)
                    }
                    val logoUrl = displayMeta.logo?.takeIf { it.isNotBlank() }
                    if (logoUrl != null && !logoLoadError) {
                        AsyncImage(
                            model = logoUrl,
                            contentDescription = displayMeta.name,
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .aspectRatio(2.6f)
                                .clickable { onOpenDetail(item.addonId, meta.type, meta.id) },
                            alignment = Alignment.Center,
                            contentScale = ContentScale.Fit,
                            onError = { logoLoadError = true }
                        )
                    } else {
                        Text(
                            text = displayMeta.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenDetail(item.addonId, meta.type, meta.id) },
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HeroMetaRows(displayMeta, extras)
                    val synopsis = displayMeta.description?.trim()?.takeIf { it.isNotBlank() }
                    if (synopsis != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = synopsis,
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary.copy(alpha = 0.72f),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable { onOpenDetail(item.addonId, meta.type, meta.id) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = TextPrimary
                            )
                            Text(
                                text = "Details",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                        }
                    }
                    // Page dots below the content (Nuvio HeroPageIndicator):
                    // 8dp, the active dot stretches to 32dp; tap jumps.
                    if (featured.size > 1) {
                        Spacer(Modifier.height(18.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(featured.size) { i ->
                                val active = i == pagerState.currentPage
                                val dotWidth by animateDpAsState(
                                    targetValue = if (active) 32.dp else 8.dp,
                                    animationSpec = tween(320),
                                    label = "heroDot$i"
                                )
                                Box(
                                    Modifier
                                        .clickable {
                                            scope.launch {
                                                runCatching { pagerState.animateScrollToPage(i) }
                                            }
                                        }
                                        .width(dotWidth)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            if (active) TextPrimary.copy(alpha = 0.92f)
                                            else Color.White.copy(alpha = 0.35f)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Nuvio HomeHeroMetaRows: row 1 = type • genre • runtime • date • IMDb;
 * row 2 = age/status badge + IMDb + language. Every field is optional —
 * a missing field drops its dot too. The IMDb rating sits in row 1 only
 * for movies with no badges; otherwise it drops to row 2.
 */
@Composable
private fun HeroMetaRows(meta: MetaPreview, extras: HeroMetaExtras?) {
    val typeLabel = when (meta.type.trim().lowercase()) {
        "movie" -> "Movie"
        "series", "tv" -> "Series"
        else -> meta.type.replaceFirstChar { it.uppercase() }
    }
    val genre = meta.genres.firstOrNull()?.takeIf { it.isNotBlank() }
    val runtime = extras?.runtime?.takeIf { it.isNotBlank() }
    val release = formatHeroReleaseDate(meta.releaseDate ?: meta.releaseInfo ?: extras?.releaseInfo)
    val imdb = (meta.imdbRating ?: extras?.imdbRating)?.toDoubleOrNull()?.takeIf { it > 0.0 }
        ?.let { String.format(java.util.Locale.US, "%.1f", it) }
    val isSeries = meta.type.trim().lowercase() in listOf("series", "tv")
    val badges = listOfNotNull(
        extras?.certification?.trim()?.takeIf { it.isNotBlank() },
        extras?.status?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    )
    val language = meta.language?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    val primaryImdb = if (!isSeries && badges.isEmpty()) imdb else null
    val secondaryImdb = if (primaryImdb == null) imdb else null

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroMetaText(typeLabel)
            genre?.let { HeroMetaDot(); HeroMetaText(it, Modifier.weight(1f, fill = false)) }
            runtime?.let { HeroMetaDot(); HeroMetaText(it) }
            release?.let { HeroMetaDot(); HeroMetaText(it) }
            primaryImdb?.let { HeroMetaDot(); HeroImdbRating(it) }
        }
        if (badges.isNotEmpty() || secondaryImdb != null || language != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (badges.isNotEmpty()) {
                    HeroMetaBadge(badges.first(), badges.getOrNull(1))
                }
                secondaryImdb?.let {
                    if (badges.isNotEmpty()) HeroMetaDot()
                    HeroImdbRating(it)
                }
                language?.let {
                    if (badges.isNotEmpty() || secondaryImdb != null) HeroMetaDot()
                    HeroMetaText(it)
                }
            }
        }
    }
}

@Composable
private fun HeroMetaText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HeroMetaDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(TextPrimary.copy(alpha = 0.7f))
    )
}

@Composable
private fun HeroImdbRating(rating: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "IMDb",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = TextPrimary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = rating,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = Color(0xFFF5C518),
            maxLines = 1
        )
    }
}

@Composable
private fun HeroMetaBadge(text: String, secondaryText: String?) {
    Box(
        modifier = Modifier
            .border(1.dp, TextPrimary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = if (secondaryText != null) "$text • $secondaryText" else text,
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/** "2024-05-17" -> "May 17, 2024"; anything else passes through as-is. */
private fun formatHeroReleaseDate(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    return try {
        val date = LocalDate.parse(value.take(10))
        date.format(DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US))
    } catch (_: Exception) {
        value
    }
}

/**
 * Continue Watching order (Nuvio `applyStreamingStyleSort`, adapted to
 * Plavio's data): default = most recently watched first (the stored order);
 * streaming-style = unfinished first by recency, finished last. Finished
 * means at the watched threshold (Step 2).
 */
internal fun applyContinueWatchingSort(
    items: List<PlaybackState>,
    mode: String,
    watchedThresholdPercent: Int,
): List<PlaybackState> {
    if (mode != "streaming") return items
    val (unfinished, finished) = items.partition { item ->
        item.durationMs <= 0 ||
            item.positionMs * 100L < item.durationMs * watchedThresholdPercent
    }
    return unfinished + finished
}

/**
 * Splits unaired next-up items out of the main list (Nuvio
 * `splitUpcomingItems`). Plavio's resume list (PlaybackState) carries no
 * release dates and no next-up entries, so the split runs over the
 * resolved Next Up episodes: with sort mode "split_upcoming", next-ups
 * whose air date is in the future move to their own row, nearest air date
 * first (unknown air dates count as aired and stay in the main rail).
 * Any other sort mode returns the resume list whole with no split.
 */
internal fun splitContinueWatchingUpcoming(
    items: List<PlaybackState>,
    nextUp: List<NextUpEpisode>,
    sortMode: String,
    today: LocalDate = LocalDate.now(),
): Pair<List<PlaybackState>, List<NextUpEpisode>> {
    if (sortMode != "split_upcoming") return items to emptyList()
    val upcoming = nextUp
        .filter { it.airDate != null && it.airDate.isAfter(today) }
        .sortedBy { it.airDate }
    return items to upcoming
}

/**
 * Step 49: Next Up card — the episode still (or the show poster when
 * "Prefer episode thumbnails" is off) with the next episode's "S1 · E4"
 * pill, the episode title, and its air date. Tap opens the series detail.
 */
@Composable
private fun NextUpCard(
    episode: NextUpEpisode,
    preferEpisodeThumbnails: Boolean = true,
    onClick: () -> Unit
) {
    val today = LocalDate.now()
    val dateFormat = remember { DateTimeFormatter.ofPattern("MMM d") }
    val airLabel = when {
        episode.airDate == null -> "Air date unknown"
        episode.airDate.isAfter(today) -> "Airs ${episode.airDate.format(dateFormat)}"
        episode.airDate == today -> "Aired today"
        else -> "Aired ${episode.airDate.format(dateFormat)}"
    }
    val posterStyle = LocalPosterStyle.current
    Column(
        modifier = Modifier
            .width(posterStyle.cardWidth)
            .tvFocusRing(RoundedCornerShape(posterStyle.cornerRadius))
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterStyle.cardHeight)
                .liquidGlass(RoundedCornerShape(posterStyle.cornerRadius))
        ) {
            SafePosterImage(
                model = tmdbSizedImage(
                    if (preferEpisodeThumbnails) episode.thumbnail ?: episode.poster else episode.poster,
                    "w342"
                ),
                contentDescription = episode.showName,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color(0xFF040A12).copy(alpha = 0.72f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    episode.episodeLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            episode.showName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            episode.episodeTitle?.takeIf { it.isNotBlank() } ?: episode.episodeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            airLabel,
            style = MaterialTheme.typography.bodySmall,
            color = if (episode.hasAired(today)) TextSecondary else LocalPalette.current.accent
        )
    }
}

/**
 * Step 69: Release Radar card — the poster with a date pill
 * (Today / Tomorrow / In 5d / 5d ago), the title, and the episode
 * or "New season" line. Tap opens the title's detail page.
 */
@Composable
private fun ReleaseRadarCard(
    item: com.pulsestream.app.data.ReleaseRadarItem,
    onClick: () -> Unit
) {
    val posterStyle = LocalPosterStyle.current
    Column(
        modifier = Modifier
            .width(posterStyle.cardWidth)
            .tvFocusRing(RoundedCornerShape(posterStyle.cornerRadius))
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(posterStyle.cardHeight)
                .liquidGlass(RoundedCornerShape(posterStyle.cornerRadius))
        ) {
            SafePosterImage(
                model = tmdbSizedImage(item.imageUrl, "w342"),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0xFF040A12).copy(alpha = 0.72f), RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    com.pulsestream.app.data.ReleaseRadar.dateLabel(item),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.5.sp, fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        item.subtitle?.let { subtitle ->
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    item: PlaybackState,
    style: String = "card",
    watchedThresholdPercent: Int = 90,
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    // Card style (Nuvio ContinueWatchingSectionStyle): card = current wide
    // thumbnail, wide = larger thumbnail, poster = tall poster card.
    // Default card is 247x140dp (Nuvio CW card).
    val (cardWidth, thumbHeight) = when (style) {
        "wide" -> 280.dp to 140.dp
        "poster" -> 128.dp to 192.dp
        else -> 247.dp to 140.dp
    }
    // Nuvio blurUnwatchedEpisodes: an episode thumb blurs 18dp while the
    // episode is unwatched (below the watched threshold).
    val isEpisode = item.season > 0 || item.episode > 0
    val isWatched = item.durationMs > 0 &&
        item.positionMs * 100L >= item.durationMs * watchedThresholdPercent
    val blurThumb = isEpisode && !isWatched
    // W10: custom poster source (shape = "landscape" — CW thumbs are wide)
    // resolves against the playback video id; falls back to the stored art.
    val posterStyle = LocalPosterStyle.current
    val cwPattern = posterStyle.customPosterPatternFor(CustomPosterScreen.CONTINUE_WATCHING)
    val cwCustom = if (cwPattern.isNotBlank()) {
        CustomPosterResolver.resolve(
            cwPattern,
            CustomPosterResolver.extractIds(item.videoId, item.imdbId),
            type = if (isEpisode) "series" else "movie",
            shape = "landscape"
        )
    } else null
    Column(
        modifier = Modifier
            .width(cardWidth)
            .tvFocusRing(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .focusable()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .liquidGlass(RoundedCornerShape(14.dp))
                .cardDepth(RoundedCornerShape(14.dp), CardDepthSurface.ContinueWatching)
        ) {
            SafePosterImage(
                model = cwCustom ?: tmdbSizedImage(item.poster, "w780"),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
                    .then(if (blurThumb) Modifier.blur(18.dp) else Modifier)
            )
            // Visible remove button: live channels can leak into Continue
            // Watching with no expiry, so the user must be able to clear
            // any entry by hand.
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.65f))
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove from Continue Watching",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (item.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (item.positionMs.toFloat() / item.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp),
                    color = LocalPalette.current.accent,
                    trackColor = Color.Black.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        val remainingMs = item.durationMs - item.positionMs
        // H-A (owner's pick 2026-10-24): the slot under a Continue
        // Watching title carries the time left as a quiet micro label —
        // the same slot catalog cards use for the popularity meter, and
        // no meter here. (PlaybackState has no rating, so no rating
        // chip on these cards.)
        val slotStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        if (remainingMs > 60_000) {
            Text(
                text = "${formatRemaining(remainingMs)} LEFT".uppercase(),
                style = slotStyle,
                color = TextSecondary,
                maxLines = 1
            )
        } else {
            Text(
                text = "RESUME",
                style = slotStyle,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

/** "50m left" / "1h 20m left" for Continue Watching cards. */
private fun formatRemaining(ms: Long): String {
    val totalMin = (ms / 60_000).toInt().coerceAtLeast(1)
    val h = totalMin / 60
    val m = totalMin % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Starter addons for the guided empty-state steps (Sample C). */
private const val META_ADDON_URL = "https://meta.plavio.site/manifest.json"
private const val STREAMS_ADDON_URL = "https://hd.plavio.site/manifest.json"

private enum class SetupStepState { Idle, Adding, Added, Failed }

/**
 * "No addons yet" — Sample C (owner's pick 2026-10-24): two guided
 * steps instead of one vague button. Metadata first, streams second,
 * each with its own one-tap Add (the same install path as onboarding);
 * the ghost button below opens the full Addons screen for any URL.
 */
@Composable
private fun NoAddonsEmptyState(
    repository: StreamRepository,
    onGoToAddons: () -> Unit,
    onReload: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var metaStep by remember { mutableStateOf(SetupStepState.Idle) }
    var streamsStep by remember { mutableStateOf(SetupStepState.Idle) }
    var metaFail by remember { mutableStateOf<String?>(null) }
    var streamsFail by remember { mutableStateOf<String?>(null) }

    fun add(
        url: String,
        set: (SetupStepState) -> Unit,
        setFail: (String?) -> Unit
    ) {
        set(SetupStepState.Adding)
        setFail(null)
        scope.launch {
            val result = repository.addAddonFromUrl(url)
            if (result.isSuccess) {
                set(SetupStepState.Added)
                onReload()
            } else {
                // Show the real reason: a server that answers (e.g.
                // PlavioMeta's disabled deployment → HTTP 402) is not the
                // user's connection.
                setFail(repository.addonInstallFailureMessage(result.exceptionOrNull()))
                set(SetupStepState.Failed)
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 58.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Set up your home",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        Text(
            "Two kinds of addons fill this screen. Add one of each, or any addon by URL.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(2.dp))
        SetupStepRow(
            number = 1,
            title = "Metadata",
            subtitle = "Posters, cast and episode guides \u2014 e.g. PlavioMeta",
            state = metaStep,
            failText = metaFail,
            onAdd = { add(META_ADDON_URL, { metaStep = it }, { metaFail = it }) }
        )
        SetupStepRow(
            number = 2,
            title = "Streams",
            subtitle = "Movies and shows to actually play \u2014 e.g. PlavioHD",
            state = streamsStep,
            failText = streamsFail,
            onAdd = { add(STREAMS_ADDON_URL, { streamsStep = it }, { streamsFail = it }) }
        )
        Text(
            "Or tap \u201CAdd an addon\u201D below to paste any addon URL.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            Modifier
                .fillMaxWidth()
                .background(GlassWhite, RoundedCornerShape(50))
                .border(1.dp, GlassBorder, RoundedCornerShape(50))
                .clickable { onGoToAddons() }
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Add an addon",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun SetupStepRow(
    number: Int,
    title: String,
    subtitle: String,
    state: SetupStepState,
    onAdd: () -> Unit,
    failText: String? = null
) {
    val palette = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlass(RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .size(26.dp)
                .background(palette.accent.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = palette.accentText
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            if (state == SetupStepState.Failed) {
                Text(
                    failText ?: "Couldn't add it \u2014 check your connection and try again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        when (state) {
            SetupStepState.Adding -> OrbitDots(modifier = Modifier.size(22.dp))
            SetupStepState.Added -> Icon(
                Icons.Filled.Check,
                contentDescription = "Added",
                tint = palette.accent,
                modifier = Modifier.size(22.dp)
            )
            else -> Box(
                Modifier
                    .background(palette.accent, RoundedCornerShape(50))
                    .clickable { onAdd() }
                    .padding(horizontal = 13.dp, vertical = 7.dp)
            ) {
                Text(
                    if (state == SetupStepState.Failed) "Retry" else "Add",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF04120B)
                )
            }
        }
    }
}

/**
 * Cold-start skeleton (Nuvio HomeSkeletonLoading): a shimmer hero +
 * shimmer rows in place of the old text spinner, built from the existing
 * ShimmerPoster so the sweep matches Search's skeleton.
 */
@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize()) {
        // Hero block: full-bleed poster shape.
        ShimmerPoster(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        )
        repeat(3) {
            Spacer(Modifier.height(18.dp))
            // Row title bar.
            ShimmerPoster(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(140.dp)
                    .height(18.dp)
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(5) {
                    ShimmerPoster(
                        modifier = Modifier
                            .width(128.dp)
                            .height(190.dp)
                    )
                }
            }
        }
    }
}

/**
 * Step 51: one collection as a Home section (Nuvio
 * HomeCollectionRowSection) — the collection title + a row of folder
 * cards. Tap a folder to open its titles as a full grid.
 */
@Composable
private fun CollectionHomeSection(
    collection: Collection,
    collectionRepository: CollectionRepository,
    repository: StreamRepository,
    onOpenFolder: (String) -> Unit,
) {
    val folders by collectionRepository.observeFolders(collection.id)
        .collectAsState(initial = emptyList())
    if (folders.isEmpty()) return
    Column(Modifier.background(LocalPalette.current.background)) {
        SectionTitle(collection.title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(folders, key = { it.id }) { folder ->
                CollectionFolderCard(folder, collectionRepository, repository) {
                    onOpenFolder(folder.id)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/**
 * Step 55: one media server's libraries as Home rows (Nuvio
 * HomeCollectionRowSection shape). Libraries + first 20 titles load
 * once per server; a movie tap plays directly, a series tap opens
 * the library screen, and a Silo movie tap opens the library screen
 * too (Silo has no direct-play URL yet).
 */
@Composable
private fun ServerHomeSection(
    server: MediaServer,
    onPlayServerItem: (PlaybackRequest) -> Unit,
    onOpenServerLibrary: (serverId: String, libraryId: String) -> Unit,
) {
    var libraries by remember { mutableStateOf<List<ServerLibrary>>(emptyList()) }
    var itemsByLibrary by remember { mutableStateOf<Map<String, List<ServerItem>>>(emptyMap()) }
    LaunchedEffect(server.id) {
        val libs = withContext(Dispatchers.IO) {
            runCatching { MediaServerOps.getLibraries(server) }.getOrNull()
        }.orEmpty()
        libraries = libs
        val loaded = mutableMapOf<String, List<ServerItem>>()
        for (library in libs) {
            loaded[library.id] = withContext(Dispatchers.IO) {
                runCatching { MediaServerOps.getLibraryItems(server, library) }.getOrNull()
            }.orEmpty().take(20)
        }
        itemsByLibrary = loaded
    }
    if (libraries.isEmpty()) return
    Column(Modifier.background(LocalPalette.current.background)) {
        libraries.forEach { library ->
            val items = itemsByLibrary[library.id].orEmpty()
            if (items.isEmpty()) return@forEach
            SectionTitle("${server.name} · ${library.name}")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.contentId }) { item ->
                    PosterCard(
                        meta = MetaPreview(
                            id = "server:${server.id}:${item.contentId}",
                            type = if (item.type == "series") "series" else "movie",
                            name = item.title,
                            poster = item.posterUrl,
                            releaseInfo = item.year?.toString()
                        ),
                        onClick = {
                            when (item.type) {
                                "series" -> onOpenServerLibrary(server.id, library.id)
                                else -> {
                                    val url = MediaServerOps.streamUrl(server, item)
                                    if (url == null) {
                                        onOpenServerLibrary(server.id, library.id)
                                    } else {
                                        onPlayServerItem(
                                            PlaybackRequest(
                                                streamUrl = url,
                                                title = item.title,
                                                poster = item.posterUrl,
                                                videoId = "server:${server.id}:${item.contentId}",
                                                imdbId = item.imdbId,
                                                isMovie = true,
                                                sourceName = server.name
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Step 51: a folder card (Nuvio CollectionFolderCard, scoped). Shape
 * follows the folder's tileShape; the image is the editor's cover URL
 * when set, else the first resolved title's poster, else the cover
 * emoji, else a folder glyph. Titles resolve once per folder visit.
 */
@Composable
private fun CollectionFolderCard(
    folder: CollectionFolder,
    collectionRepository: CollectionRepository,
    repository: StreamRepository,
    onClick: () -> Unit,
) {
    var firstPoster by remember(folder.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(folder.id) {
        val sources = collectionRepository.getSources(folder.id)
        val titles = if (sources.isEmpty()) emptyList()
        else CollectionSourceResolver.resolveFolder(repository, sources, limit = 6)
        firstPoster = titles.firstOrNull()?.poster
    }
    val width = if (folder.tileShape == "landscape") 200.dp else 128.dp
    val ratio = when (folder.tileShape) {
        "landscape" -> 16f / 9f
        "square" -> 1f
        else -> 2f / 3f
    }
    Column(
        modifier = Modifier.width(width).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .clip(RoundedCornerShape(12.dp))
                .background(com.pulsestream.app.ui.theme.BgPanel),
            contentAlignment = Alignment.Center
        ) {
            val image = folder.coverImageUrl?.takeIf { it.isNotBlank() } ?: firstPoster
            if (image != null) {
                SafePosterImage(
                    model = image,
                    contentDescription = folder.title,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (!folder.coverEmoji.isNullOrBlank()) {
                Text(
                    folder.coverEmoji,
                    style = MaterialTheme.typography.displayMedium,
                    color = TextPrimary
                )
            } else {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = folder.title,
                    tint = TextSecondary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            folder.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Phase 2: CS3 provider selector (Infuse's CloudStreamProviderSelectorHeader,
 * Plavio palette — flat panel + accent border, no glass). "All CS3" shows
 * every runnable provider's sections, one provider shows only its sections,
 * "Off" hides CS3 sections. The pill stays visible with no CS3 rows so
 * "Off" can always be undone.
 */
@Composable
private fun CloudStreamProviderSelectorHeader(
    providers: List<CloudStreamPluginItem>,
    selectedProviderId: String,
    onSelect: (String) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val palette = LocalPalette.current
    val selectedName = when (selectedProviderId) {
        "all" -> "All CS3"
        "off" -> "Off"
        "" -> providers.firstOrNull()?.metadata?.name ?: "CloudStream"
        else -> providers.firstOrNull { it.metadata.id.value == selectedProviderId }
            ?.metadata?.name ?: "CloudStream"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.background)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "CloudStream",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.panel.copy(alpha = 0.92f))
                    .border(1.2.dp, palette.accent, RoundedCornerShape(20.dp))
                    .clickable { menuExpanded = !menuExpanded }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedName,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.width(5.dp))
                Icon(
                    imageVector = if (menuExpanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
        if (menuExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.panel.copy(alpha = 0.95f))
                    .border(1.dp, palette.accent.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CloudStreamProviderMenuRow(
                        label = "All providers",
                        selected = selectedProviderId == "all",
                        onClick = { onSelect("all"); menuExpanded = false },
                    )
                    providers.forEach { plugin ->
                        CloudStreamProviderMenuRow(
                            label = plugin.metadata.name,
                            selected = selectedProviderId == plugin.metadata.id.value,
                            onClick = { onSelect(plugin.metadata.id.value); menuExpanded = false },
                        )
                    }
                    CloudStreamProviderMenuRow(
                        label = "Off",
                        selected = selectedProviderId == "off",
                        onClick = { onSelect("off"); menuExpanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun CloudStreamProviderMenuRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.background(palette.accent.copy(alpha = 0.16f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
