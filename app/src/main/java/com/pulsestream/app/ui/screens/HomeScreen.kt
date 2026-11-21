package com.pulsestream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
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
import com.pulsestream.app.ui.components.LocalPosterStyle
import com.pulsestream.app.ui.components.OrbitDots
import com.pulsestream.app.data.CustomListsRepository
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
                if (cached.isNotEmpty()) {
                    val stale = !repo.isCatalogCacheFresh()
                    uiState = HomeUiState.Content(cached, refreshing = stale)
                    if (stale) {
                        runCatching { repo.refreshCatalogRows().filter { it.type != "tv" } }
                            .onSuccess { uiState = HomeUiState.Content(it) }
                            .onFailure { uiState = HomeUiState.Content(cached) }
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
                uiState = if (rows.isEmpty()) HomeUiState.Empty else HomeUiState.Content(rows)
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
                .onSuccess { uiState = HomeUiState.Content(it) }
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
    onGoToLive: () -> Unit = {},
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
    val (mainWatching, upcomingWatching) = remember(sortedWatching) {
        splitContinueWatchingUpcoming(sortedWatching)
    }
    // Step 49: Next Up — the episode after the latest watched one per
    // series (shared enrichment with the Library calendar).
    val watchedKeys by com.pulsestream.app.data.WatchedMarks.keys.collectAsState()
    var nextUp by remember { mutableStateOf<List<com.pulsestream.app.data.NextUpEpisode>>(emptyList()) }
    LaunchedEffect(watchedKeys) {
        nextUp = com.pulsestream.app.data.NextUpResolver.resolveNextUp(repository, watchedKeys)
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

    /** Details for a resume card: PlavioMeta is the preferred meta addon. */
    fun openItemDetails(item: PlaybackState) {
        val isMovie = item.season == 0 && item.episode == 0
        onOpenDetail(
            "com.plavio.catalogs",
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
                                // Pinned hero (v0.12.5, owner's order): the
                                // hero stays at the top while the catalog
                                // sheet scrolls UP AND OVER it. The hero is
                                // still a normal list item (pager swipe,
                                // tap and TV focus untouched), but its draw
                                // position is counter-translated by exactly
                                // its scrolled-off amount, so it visually
                                // never leaves the top; the sections below
                                // are drawn after it, hence over it. At rest
                                // the translation is 0 and the first section
                                // starts exactly at the hero's bottom
                                // border — the round-5 -22dp static overlap
                                // (which also left a 22dp dead band in the
                                // layout under the first row) is gone.
                                Box(
                                    Modifier.graphicsLayer {
                                        val heroInfo = homeListState.layoutInfo
                                            .visibleItemsInfo
                                            .firstOrNull { it.key == "hero_carousel" }
                                        translationY = if (heroInfo != null && heroInfo.offset < 0)
                                            -heroInfo.offset.toFloat() else 0f
                                    }
                                ) {
                                    HeroCarousel(featured, onOpenDetail, heroFocus)
                                }
                            }
                        }
                        if (settings.continueWatchingVisible && mainWatching.isNotEmpty()) {
                            // Explicit key: this section appears/disappears as the
                            // Flow emits, so an index-based default key would
                            // reshuffle every row below it on each emission.
                            item(key = "continue_watching") {
                              Column(
                                // Scroll-over sheet (v0.12.5): sections
                                // carry the solid app background so the
                                // sheet reads cleanly (headers stay
                                // legible) while it slides over the
                                // pinned hero.
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Continue Watching")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        mainWatching,
                                        key = { index, item -> "${item.key}_$index" }
                                    ) { _, item ->
                                        ContinueWatchingCard(
                                            item,
                                            style = settings.continueWatchingStyle,
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
                        // empty in Plavio today — Continue Watching is resume
                        // positions only, with no release dates — but renders
                        // when entries exist.
                        if (settings.continueWatchingVisible && upcomingWatching.isNotEmpty()) {
                            item(key = "continue_watching_upcoming") {
                              Column(
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Coming up")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        upcomingWatching,
                                        key = { index, item -> "up_${item.key}_$index" }
                                    ) { _, item ->
                                        ContinueWatchingCard(
                                            item,
                                            style = settings.continueWatchingStyle,
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
                        // Step 49: Next Up — one card per series, the
                        // episode right after the latest watched one.
                        if (nextUp.isNotEmpty()) {
                            item(key = "next_up") {
                              Column(
                                Modifier.background(LocalPalette.current.background)
                              ) {
                                SectionTitle("Next Up")
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(
                                        nextUp,
                                        key = { "${it.showId}_${it.season}_${it.episode}" }
                                    ) { episode ->
                                        NextUpCard(episode) {
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                        state.rows.forEachIndexed { rowIndex, row ->
                            // THE v0.3.0 scroll-crash fix: the key MUST include the
                            // catalog TYPE. Cinemeta declares the same catalog ids
                            // ("top", "year", "imdbRating") for both movie and
                            // series — without the type, two rows shared one key
                            // and LazyColumn threw, closing the app on scroll.
                            item(key = "${row.addonId}|${row.type}|${row.catalogId}") {
                              Column(
                                // Scroll-over sheet (v0.12.5): same solid
                                // background as Continue Watching — the
                                // whole sheet slides over the pinned hero
                                // as one readable surface.
                                Modifier.background(LocalPalette.current.background)
                              ) {
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
                                    onMoreClick = { onOpenCatalog(row) }
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    itemsIndexed(
                                        row.items,
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

            // Top-right of Home: the Live pill alone now (the old
            // floating Saved/bookmark button was removed 2026-10-24 —
            // Watchlist + My Lists live in the Library tab).
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live pill (owner's order 2026-10-24): Live left the
                // dock for the Library tab — it lives here now, at
                // Home's top-right. Real glass (owner's device verdict
                // 2026-10-26: the plain liquidGlass call read as a solid
                // pill) — the dock's exact treatment: a fitted backing
                // pill behind glass with explicit frost/darken.
                Box(Modifier.height(42.dp)) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (LocalPalette.current.light) {
                                    Color.White.copy(alpha = 0.82f)
                                } else {
                                    Color.Black.copy(alpha = 0.48f)
                                },
                                RoundedCornerShape(50)
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .liquidGlass(
                                RoundedCornerShape(50),
                                frost = 0.12f,
                                darken = 0.03f
                            )
                            .clickable { onGoToLive() }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .background(Color(0xFFFF4D5E), CircleShape)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Live",
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
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
    if (resumePromptOpen && resumePromptItem != null) {
        val remainingMs = resumePromptItem.durationMs - resumePromptItem.positionMs
        AlertDialog(
            onDismissRequest = { resumePromptOpen = false },
            title = { Text("Continue watching?", color = TextPrimary) },
            text = {
                Column {
                    Text(resumePromptItem.title, color = TextPrimary, fontWeight = FontWeight.Bold)
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
                TextButton(onClick = { resumePromptOpen = false; resumeItem(resumePromptItem) }) {
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
 * Netflix-style hero carousel: full-bleed backdrop pager at the top of
 * Home. Auto-advances every 5s on mobile (pausing while the user is touching
 * it); never auto-advances on TV to avoid stealing frames from D-pad input.
 * Tap opens the title's detail screen.
 *
 * v0.12.4 (owner's Sample C — "immersive"): the pager is now truly
 * full-bleed — edge-to-edge with no side padding, no rounded frame —
 * the list itself is shifted up under the status bar so the art owns
 * the screen's top edge; the swipe dots moved INSIDE the poster
 * (animated, active = elongated mint pill).
 *
 * v0.12.5 (owner's order): ~20% taller again — 380dp (was 316dp) — and
 * the hero is PINNED: it stays at the top while the catalog sheet
 * scrolls up and over it (the pin lives on the hero's list item in
 * [HomeScreen]; the pager itself is unchanged).
 */
@Composable
private fun HeroCarousel(
    featured: List<FeaturedTitle>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    heroFocus: FocusRequester? = null
) {
    val pagerState = rememberPagerState(pageCount = { featured.size })
    val isTv = rememberIsTv()
    // Hero height: TV keeps the 380.dp backdrop hero. On mobile the hero
    // takes 45% of the screen height with the poster filling it
    // edge-to-edge (owner's device verdict 2026-10-24 — 45% chosen after
    // the same-day 40% fix, itself superseding the 60% full-poster
    // version, which stood narrow with blurred side bars and was
    // rejected on sight).
    val heroHeight = if (isTv) 380.dp
        else (LocalConfiguration.current.screenHeightDp * 0.45f).dp + 40.dp

    val reducedMotion = com.pulsestream.app.data.rememberReducedMotion()
    LaunchedEffect(pagerState, featured.size, reducedMotion) {
        // No auto-advance on TV: the 5s full-bleed pager animation steals
        // frames from D-pad browsing on weak TV GPUs/CPUs and its backdrop
        // re-decodes cause jank spikes that coincide with user input.
        if (isTv || reducedMotion) return@LaunchedEffect
        while (featured.size > 1) {
            delay(5000)
            if (!pagerState.isScrollInProgress) {
                val next = (pagerState.currentPage + 1) % featured.size
                runCatching { pagerState.animateScrollToPage(next) }
            }
        }
    }

    Box {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            key = { index -> "hero_${featured[index].meta.id}_$index" }
        ) { index ->
            val item = featured[index]
            val meta = item.meta
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .tvFocusRing(RoundedCornerShape(0.dp))
                    .clickable { onOpenDetail(item.addonId, meta.type, meta.id) }
                    .then(if (heroFocus != null && index == 0) Modifier.focusRequester(heroFocus) else Modifier)
                    .focusable()
            ) {
                if (isTv || meta.poster.isNullOrBlank()) {
                    SafePosterImage(
                        model = meta.background?.takeIf { it.isNotBlank() } ?: meta.poster,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    // Mobile hero: the poster fills the hero edge-to-edge
                    // — full width, ContentScale.Crop, the same treatment
                    // as the detail-page hero (the top/bottom crop of the
                    // 2:3 poster is intended).
                    SafePosterImage(
                        model = meta.poster,
                        contentDescription = meta.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f),
                                Color.Black.copy(alpha = 0.88f)
                            )
                        )
                    )
                )
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = meta.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Hero meta line (Nuvio HomeHeroMetaRows, meta block
                    // only): type • year • ★ rating (one decimal) • first
                    // genre • language. Runtime and status are NOT in the
                    // catalog data — they live only in the full meta, so
                    // showing them here would cost one meta fetch per hero
                    // title; the line shows what the catalog carries.
                    val typeLabel = when (meta.type.trim().lowercase()) {
                        "movie" -> "Movie"
                        "series", "tv" -> "Series"
                        else -> meta.type.replaceFirstChar { it.uppercase() }
                    }
                    val info = listOfNotNull(
                        typeLabel,
                        meta.releaseInfo?.take(4),
                        meta.imdbRating?.toDoubleOrNull()?.takeIf { it > 0.0 }
                            ?.let { "★ ${String.format(java.util.Locale.US, "%.1f", it)}" },
                        meta.genres.firstOrNull(),
                        meta.language?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
                    ).joinToString("  •  ")
                    if (info.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = com.pulsestream.app.ui.components.accentStarText(info, LocalPalette.current.accent),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // Swipe dots INSIDE the poster's lower edge (v0.12.4): the active
        // dot stretches into an elongated mint pill, animated.
        if (featured.size > 1) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(featured.size) { i ->
                    val active = i == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (active) 18.dp else 6.dp,
                        animationSpec = tween(320),
                        label = "heroDot$i"
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .width(dotWidth)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (active) LocalPalette.current.accent
                                else Color.White.copy(alpha = 0.35f)
                            )
                    )
                }
            }
        }
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
 * `splitUpcomingItems`). Always empty in Plavio today: Continue Watching
 * is resume positions only — PlaybackState carries no release date and
 * there are no next-up entries — so the main list passes through whole.
 */
internal fun splitContinueWatchingUpcoming(
    items: List<PlaybackState>,
): Pair<List<PlaybackState>, List<PlaybackState>> = items to emptyList()

/**
 * Step 49: Next Up card — the show poster with the next episode's
 * "S1 · E4" pill, the episode title, and its air date. Tap opens the
 * series detail.
 */
@Composable
private fun NextUpCard(
    episode: NextUpEpisode,
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
                model = tmdbSizedImage(episode.poster, "w342"),
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
    onRemove: () -> Unit,
    onClick: () -> Unit
) {
    // Card style (Nuvio ContinueWatchingSectionStyle): card = current wide
    // thumbnail, wide = larger thumbnail, poster = tall poster card.
    val (cardWidth, thumbHeight) = when (style) {
        "wide" -> 280.dp to 140.dp
        "poster" -> 128.dp to 192.dp
        else -> 200.dp to 112.dp
    }
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
        ) {
            SafePosterImage(
                model = tmdbSizedImage(item.poster, "w780"),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize()
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
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(5) {
                    ShimmerPoster(
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
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
