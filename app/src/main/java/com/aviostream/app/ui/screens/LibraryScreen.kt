package com.aviostream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviostream.app.data.CustomListsRepository
import com.aviostream.app.data.FavoritePeopleRepository
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.db.DownloadItem
import com.aviostream.app.data.download.DownloadManager
import com.aviostream.app.data.db.CustomList
import com.aviostream.app.data.simkl.SimklApi
import com.aviostream.app.data.tracking.libraryProviders
import com.aviostream.app.data.tracking.progressProviders
import com.aviostream.app.data.trakt.TraktApi
import com.aviostream.app.data.trakt.TraktManager
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.OrbitDots
import com.aviostream.app.ui.components.CustomPosterScreen
import com.aviostream.app.ui.components.PosterCard
import com.aviostream.app.ui.components.SafePosterImage
import com.aviostream.app.ui.components.SectionTitle
import com.aviostream.app.ui.components.liquidGlass
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Library (owner's design, 2026-10-24): one home for everything that is
 * the user's — Up Next from Trakt/Simkl progress (continue where you
 * left in any app; the player already scrobbles back), the Trakt and
 * Simkl watchlists, the local watchlist, and custom lists. Replaces
 * the Live tab in the dock; the old Watchlist/Lists screens stay as
 * the "See all" destinations.
 */
private data class UpNextCard(
    val imdbId: String,
    val showTitle: String,
    val service: String,
    val nextLabel: String,
    val metaLine: String,
    val progress: Float?,
    val poster: String?,
    val still: String?,
    val lastWatchedMs: Long
)

@Composable
fun LibraryScreen(
    repository: StreamRepository,
    listsRepository: CustomListsRepository,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    onOpenList: (listId: String) -> Unit,
    onGoToWatchlist: () -> Unit,
    onGoToLists: () -> Unit,
    onGoToDownloads: () -> Unit = {},
    onPlayDownload: (filePath: String, title: String) -> Unit = { _, _ -> },
    // Step 58: favorite people rail on the Saved tab.
    favoritePeopleRepository: FavoritePeopleRepository,
    onOpenPerson: (personId: Int, name: String) -> Unit = { _, _ -> },
    onConnect: () -> Unit
) {
    val settings = repository.settings
    val palette = LocalPalette.current
    val traktLinked = !settings.traktAccessToken.isNullOrBlank()
    val simklLinked = !settings.simklToken.isNullOrBlank()
    val librarySource = settings.trackingLibrarySource
    val progressSource = settings.trackingProgressSource

    var loading by remember { mutableStateOf(traktLinked || simklLinked) }
    // Per-source load failures (Nuvio per-source error card + Retry):
    // a linked service whose watchlist call failed or returned nothing.
    var traktLoadError by remember { mutableStateOf(false) }
    var simklLoadError by remember { mutableStateOf(false) }
    var libraryOffline by remember { mutableStateOf(false) }
    // Retry bumps this to re-run the load below.
    var libraryRefreshTick by remember { mutableStateOf(0) }
    val onRetryLibrary: () -> Unit = { libraryRefreshTick++ }
    var upNext by remember { mutableStateOf<List<UpNextCard>>(emptyList()) }
    var traktWatchlist by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    var simklWatchlist by remember { mutableStateOf<List<MetaPreview>>(emptyList()) }
    /** Personal Trakt lists (Step 33): (list, first 20 posters). */
    var traktLists by remember {
        mutableStateOf<List<Pair<TraktApi.TraktList, List<MetaPreview>>>>(emptyList())
    }

    val context = LocalContext.current
    val downloadManager = remember { DownloadManager.get(context) }
    val downloads by downloadManager.observeAll().collectAsState(initial = emptyList())
    // Step 48: live progress for the Downloaded tab.
    val activeProgress by downloadManager.activeDownloads.collectAsState()
    val completedDownloads = remember(downloads) {
        downloads.filter { it.status == "COMPLETED" }
    }

    val myWatchlist by repository.watchlist.collectAsState(initial = emptyList())
    val myLists by listsRepository.lists.collectAsState(initial = emptyList())
    // Step 58: favorite people (pinned first) for the Saved tab rail.
    val favoritePeopleState by favoritePeopleRepository.uiState.collectAsState()
    // Step 47: all custom-list items + watched marks feed the Saved tab.
    val allListItems by listsRepository.allItems.collectAsState(initial = emptyList())
    val watchedKeys by com.aviostream.app.data.WatchedMarks.keys.collectAsState()
    val syncedIds = remember(settings.librarySyncedKeys) {
        settings.librarySyncedKeys.map { it.substringAfter('|') }.toSet()
    }

    LaunchedEffect(traktLinked, simklLinked, librarySource, progressSource, libraryRefreshTick) {
        if (!traktLinked && !simklLinked) {
            loading = false
            return@LaunchedEffect
        }
        // Step 52: only the picked sources load — "all" keeps the old
        // merged behaviour; an unlinked pick falls back to all linked.
        val libraryProviders = settings.libraryProviders()
        val progressProviders = settings.progressProviders()
        if (libraryProviders.isEmpty() && progressProviders.isEmpty()) {
            traktWatchlist = emptyList()
            simklWatchlist = emptyList()
            upNext = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        // Reset first: a source change that excludes a service must not
        // leave its previous titles on screen.
        traktWatchlist = emptyList()
        simklWatchlist = emptyList()
        traktLists = emptyList()
        upNext = emptyList()
        traktLoadError = false
        simklLoadError = false
        libraryOffline = !isNetworkAvailable(context)
        val traktApi = TraktApi()
        val simklApi = SimklApi()
        val traktToken = if (traktLinked) {
            runCatching { TraktManager(settings).validAccessToken() }.getOrNull()
        } else null
        val simklToken = settings.simklToken?.takeIf { it.isNotBlank() }

        // Posters/stills by IMDb id, fetched a few at a time.
        suspend fun metaFor(type: String, imdb: String) =
            runCatching { repository.getMetaFromAny(null, type, imdb) }.getOrNull()
        // Nuvio hydrates release info per item in chunks of 4 — same
        // here: bounded parallel fetches instead of one burst of 20.
        suspend fun previews(entries: List<Pair<String, Pair<String, String>>>): List<MetaPreview> =
            coroutineScope {
                entries.chunked(4).flatMap { chunk ->
                    chunk.map { (type, entry) ->
                        async {
                            val (imdb, title) = entry
                            val meta = metaFor(type, imdb)
                            MetaPreview(
                                id = imdb, type = type,
                                name = meta?.name ?: title,
                                poster = meta?.poster,
                                // Step 47: release year for the Saved sort.
                                releaseInfo = meta?.releaseInfo,
                                // Full ISO date + genres feed the Saved
                                // release-date sort and the genre facet.
                                releaseDate = com.aviostream.app.data.parseReleaseDate(meta?.releaseInfo),
                                genres = meta?.genres.orEmpty()
                            )
                        }
                    }.awaitAll()
                }
            }

        val cards = mutableListOf<UpNextCard>()
        if (traktToken != null && "trakt" in libraryProviders) {
            val watchlistEntries = runCatching { traktApi.getWatchlist(traktToken) }.getOrNull()
            if (watchlistEntries == null) traktLoadError = true
            watchlistEntries?.let { entries ->
                traktWatchlist = previews(entries.take(20).map { it.type to (it.imdbId to it.title) })
            }
            // Personal Trakt lists (Step 33, Nuvio TraktLibraryRepository):
            // first 5 lists, first 20 posters each, fetched in parallel.
            // A list that fails to load is skipped, never fatal.
            runCatching { traktApi.getLists(traktToken) }.getOrNull()?.take(5)?.let { lists ->
                traktLists = coroutineScope {
                    lists.map { list ->
                        async {
                            val items = runCatching { traktApi.getListItems(traktToken, list.id) }
                                .getOrNull().orEmpty()
                            list to previews(items.take(20).map { it.type to (it.imdbId to it.title) })
                        }
                    }.awaitAll().filter { (_, items) -> items.isNotEmpty() }
                }
            }
        }
        if (traktToken != null && "trakt" in progressProviders) {
            val traktUpNext = runCatching { traktApi.getUpNext(traktToken) }.getOrNull().orEmpty()
            for (e in traktUpNext) {
                val meta = metaFor("series", e.imdbId)
                val still = meta?.videos?.firstOrNull {
                    it.season == e.nextSeason && it.episode == e.nextEpisode
                }?.thumbnail
                cards += UpNextCard(
                    imdbId = e.imdbId,
                    showTitle = meta?.name ?: e.title,
                    service = "Trakt",
                    nextLabel = "S${e.nextSeason} · E${e.nextEpisode}" +
                        (e.nextTitle?.let { " — $it" } ?: ""),
                    metaLine = "You watched S${e.lastSeason} · E${e.lastEpisode} · on Trakt",
                    progress = if (e.airedCount > 0) {
                        e.watchedCount.toFloat() / e.airedCount.toFloat()
                    } else null,
                    poster = meta?.poster,
                    still = still,
                    lastWatchedMs = e.lastWatchedAtMs
                )
            }
        }
        if (simklToken != null && "simkl" in libraryProviders) {
            val planEntries = runCatching { simklApi.getPlanToWatch(simklToken) }.getOrNull()
            if (planEntries == null) simklLoadError = true
            planEntries?.let { entries ->
                simklWatchlist = previews(entries.take(20).map { it.type to (it.imdbId to it.title) })
            }
        }
        if (simklToken != null && "simkl" in progressProviders) {
            val traktIds = cards.map { it.imdbId }.toSet()
            val watching = runCatching { simklApi.getWatching(simklToken) }.getOrNull()
                ?.filter { it.imdbId !in traktIds }.orEmpty()
            for (e in watching) {
                val meta = metaFor("series", e.imdbId)
                val lastMs = e.lastWatchedAt?.let {
                    runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
                } ?: 0L
                val counts = if (e.watchedEpisodes != null && e.totalEpisodes != null) {
                    "${e.watchedEpisodes} of ${e.totalEpisodes} episodes watched · on Simkl"
                } else "In progress · on Simkl"
                cards += UpNextCard(
                    imdbId = e.imdbId,
                    showTitle = meta?.name ?: e.title,
                    service = "Simkl",
                    nextLabel = "Continue watching",
                    metaLine = counts,
                    progress = if (e.watchedEpisodes != null && e.totalEpisodes != null && e.totalEpisodes > 0) {
                        e.watchedEpisodes.toFloat() / e.totalEpisodes.toFloat()
                    } else null,
                    poster = meta?.poster,
                    still = null,
                    lastWatchedMs = lastMs
                )
                }
        }
        upNext = cards.sortedByDescending { it.lastWatchedMs }.take(10)
        loading = false
    }

    // Step 47: Saved tab (unified grid) vs Sections tab (the existing
    // Up Next / watchlists / lists / downloads view). Sections stays the
    // default so the current Library view does not move.
    var libraryTab by rememberSaveable { mutableStateOf("sections") }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
            Text(
                "Library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ServiceChip("Trakt", traktLinked, settings.traktUsername, Modifier.weight(1f), onConnect)
                ServiceChip("Simkl", simklLinked, settings.simklUsername, Modifier.weight(1f), onConnect)
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item(key = "tab_saved") {
                    FilterChip(
                        selected = libraryTab == "saved",
                        onClick = { libraryTab = "saved" },
                        label = { Text("Saved") }
                    )
                }
                item(key = "tab_sections") {
                    FilterChip(
                        selected = libraryTab == "sections",
                        onClick = { libraryTab = "sections" },
                        label = { Text("Sections") }
                    )
                }
                item(key = "tab_downloaded") {
                    FilterChip(
                        selected = libraryTab == "downloaded",
                        onClick = { libraryTab = "downloaded" },
                        label = { Text("Downloaded") }
                    )
                }
                item(key = "tab_calendar") {
                    FilterChip(
                        selected = libraryTab == "calendar",
                        onClick = { libraryTab = "calendar" },
                        label = { Text("Calendar") }
                    )
                }
            }
        }

        if (libraryTab == "saved") {
            Box(Modifier.weight(1f)) {
                LibrarySavedScreen(
                    myWatchlist = myWatchlist,
                    myLists = myLists,
                    allListItems = allListItems,
                    traktWatchlist = traktWatchlist,
                    simklWatchlist = simklWatchlist,
                    watchedKeys = watchedKeys,
                    settings = settings,
                    onOpenDetail = onOpenDetail,
                    favoritePeople = favoritePeopleState.displayPeople,
                    onOpenPerson = onOpenPerson,
                    traktLoadError = traktLoadError,
                    simklLoadError = simklLoadError,
                    isOffline = libraryOffline,
                    onRetry = onRetryLibrary
                )
            }
            return@Column
        }

        // Step 48: Downloaded as a real mode — in-progress cards with
        // pause/resume, then completed downloads grouped per show.
        if (libraryTab == "downloaded") {
            Box(Modifier.weight(1f)) {
                LibraryDownloadedTab(
                    downloads = downloads,
                    activeProgress = activeProgress,
                    manager = downloadManager,
                    onPlayDownload = onPlayDownload,
                    onGoToDownloads = onGoToDownloads
                )
            }
            return@Column
        }

        // Step 49: release calendar — episodes airing in the next 30 days
        // from the library, grouped by air date.
        if (libraryTab == "calendar") {
            Box(Modifier.weight(1f)) {
                LibraryCalendarTab(
                    repository = repository,
                    watchedKeys = watchedKeys,
                    myWatchlist = myWatchlist,
                    allListItems = allListItems,
                    traktWatchlist = traktWatchlist,
                    simklWatchlist = simklWatchlist,
                    onOpenDetail = onOpenDetail
                )
            }
            return@Column
        }

        Box(Modifier.weight(1f)) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        if (!traktLinked && !simklLinked) {
            item(key = "connect_card") {
                ConnectCard(onConnect)
            }
        }

        if (loading && upNext.isEmpty() && traktWatchlist.isEmpty() && simklWatchlist.isEmpty()) {
            item(key = "lib_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbitDots(modifier = Modifier.size(38.dp))
                }
            }
        }

        if (upNext.isNotEmpty()) {
            item(key = "upnext_title") {
                SectionTitle("Up Next")
            }
            items(upNext, key = { "up_${it.service}_${it.imdbId}" }) { card ->
                UpNextRow(card) { onOpenDetail("", "series", card.imdbId) }
            }
        }

        if (traktWatchlist.isNotEmpty()) {
            item(key = "trakt_wl") {
                FeedRow("Watchlist", "TRAKT", Color(0xFFFF858D), traktWatchlist, onOpenDetail)
            }
        }

        // Personal Trakt lists (Step 33): one poster row per list.
        traktLists.forEach { (list, items) ->
            item(key = "trakt_list_${list.id}") {
                FeedRow(list.name, "TRAKT LIST", Color(0xFFFF858D), items, onOpenDetail)
            }
        }
        if (simklWatchlist.isNotEmpty()) {
            item(key = "simkl_wl") {
                FeedRow("Watchlist", "SIMKL", Color(0xFFFFB35C), simklWatchlist, onOpenDetail)
            }
        }

        if (myWatchlist.isNotEmpty()) {
            item(key = "my_wl_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Watchlist",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToWatchlist() }
                    )
                }
            }
            item(key = "my_wl_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(myWatchlist.take(15), key = { it.key }) { item ->
                        PosterCard(
                            meta = MetaPreview(
                                id = item.metaId, type = item.type,
                                name = item.name, poster = item.poster
                            ),
                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) },
                            surface = CustomPosterScreen.LIBRARY
                        )
                    }
                }
            }
        }

        if (completedDownloads.isNotEmpty()) {
            item(key = "dl_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToDownloads() }
                    )
                }
            }
            item(key = "dl_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(completedDownloads.take(15), key = { it.id }) { item ->
                        DownloadCard(item) {
                            onPlayDownload(item.filePath, item.title)
                        }
                    }
                }
            }
        }

        if (myLists.isNotEmpty()) {
            item(key = "my_lists_title") {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "My Lists",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "See all",
                        color = palette.accent,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.clickable { onGoToLists() }
                    )
                }
            }
            item(key = "my_lists_cards") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    myLists.take(2).forEach { list ->
                        ListCollageCard(
                            list = list,
                            listsRepository = listsRepository,
                            modifier = Modifier.weight(1f)
                        ) { onOpenList(list.id) }
                    }
                    if (myLists.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        if (!loading && upNext.isEmpty() && traktWatchlist.isEmpty() && simklWatchlist.isEmpty() &&
            traktLists.isEmpty() &&
            myWatchlist.isEmpty() && myLists.isEmpty()
        ) {
            item(key = "lib_empty") {
                LibraryEmptyStateCard(
                    isOffline = libraryOffline,
                    failedSources = buildList {
                        if (traktLoadError) add("Trakt")
                        if (simklLoadError) add("Simkl")
                    },
                    librarySource = librarySource,
                    anyLinked = traktLinked || simklLinked,
                    onRetry = onRetryLibrary
                )
            }
        }
    }
        }
    }
}

/** Is there any usable network right now (same check DownloadManager uses). */
private fun isNetworkAvailable(context: android.content.Context): Boolean {
    return try {
        val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as? android.net.ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) {
        true
    }
}

/**
 * Per-source empty / error / offline card (Nuvio HomeEmptyStateCard +
 * NuvioNetworkOfflineCard, scoped port): the message names the source
 * that failed or is empty, and a Retry button re-runs the library load.
 */
@Composable
private fun LibraryEmptyStateCard(
    isOffline: Boolean,
    failedSources: List<String>,
    librarySource: String,
    anyLinked: Boolean,
    onRetry: () -> Unit
) {
    val title: String
    val body: String
    val showRetry: Boolean
    when {
        isOffline && anyLinked -> {
            title = "You're offline"
            body = "Your saved titles need a connection — Trakt and Simkl will load again when you're back online. Your local watchlist is unaffected."
            showRetry = true
        }
        failedSources.isNotEmpty() -> {
            val names = failedSources.joinToString(" and ")
            title = "$names couldn't load"
            body = "We couldn't reach $names just now — your saved titles are still there. Check your connection and try again."
            showRetry = true
        }
        librarySource == "trakt" -> {
            title = "Nothing saved on Trakt yet"
            body = "Your Trakt watchlist is empty — add a title on Trakt or in this app and it will show up here."
            showRetry = false
        }
        librarySource == "simkl" -> {
            title = "Nothing saved on Simkl yet"
            body = "Your Simkl Plan to Watch list is empty — add a title on Simkl or in this app and it will show up here."
            showRetry = false
        }
        librarySource == "local" -> {
            title = "Nothing saved locally yet"
            body = "Add titles to your watchlist or a list and they will show up here."
            showRetry = false
        }
        else -> {
            title = "Nothing here yet"
            body = if (anyLinked) {
                "Your watchlists and progress will show up once there is something to show."
            } else {
                "Add something to your watchlist, or connect Trakt/Simkl above."
            }
            showRetry = false
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 36dp icon tile (Nuvio settings row tile): accent @ 0.12, 12dp radius.
        Box(
            Modifier
                .size(36.dp)
                .background(
                    LocalPalette.current.accent.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (isOffline || failedSources.isNotEmpty()) "!" else "☆",
                color = LocalPalette.current.accent,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            // Nuvio muted subtitle #969CA3.
            color = Color(0xFF969CA3),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (showRetry) {
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text("Retry", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatDownloadSize(totalBytes: Long): String {
    if (totalBytes <= 0L) return ""
    val gb = totalBytes / 1_073_741_824.0
    if (gb >= 1.0) return "%.1f GB".format(gb)
    val mb = totalBytes / 1_048_576.0
    if (mb >= 1.0) return "%d MB".format(mb.toLong())
    return "%d KB".format(totalBytes / 1024L)
}

/**
 * Downloads carry no posters, so the Library tile is a glass card with
 * the title's initial, a download badge and the file size — tap plays
 * the file, same as the Downloads screen.
 */
/**
 * Step 49: release calendar (Nuvio library/LibraryReleaseCalendarPanel,
 * scoped port — a date-grouped list instead of Nuvio's month grid).
 * Episodes airing in the next 30 days from the library (watched series +
 * saved series), unwatched, grouped by air date. Tap opens the series.
 */
@Composable
private fun LibraryCalendarTab(
    repository: StreamRepository,
    watchedKeys: Set<String>,
    myWatchlist: List<com.aviostream.app.data.db.WatchlistItem>,
    allListItems: List<com.aviostream.app.data.db.CustomListItem>,
    traktWatchlist: List<MetaPreview>,
    simklWatchlist: List<MetaPreview>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val palette = LocalPalette.current
    var days by remember { mutableStateOf<List<com.aviostream.app.data.ReleaseDay>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var refreshTick by remember { mutableStateOf(0) }
    val savedSeriesIds = remember(myWatchlist, allListItems, traktWatchlist, simklWatchlist) {
        (
            myWatchlist.filter { it.type == "series" }.map { it.metaId } +
                allListItems.filter { it.type == "series" }.map { it.metaId } +
                traktWatchlist.filter { it.type == "series" }.map { it.id } +
                simklWatchlist.filter { it.type == "series" }.map { it.id }
            ).distinct()
    }
    LaunchedEffect(refreshTick) {
        loading = true
        days = com.aviostream.app.data.NextUpResolver.resolveUpcomingReleases(
            repository, watchedKeys, savedSeriesIds
        )
        loading = false
    }
    val today = java.time.LocalDate.now()
    val dayFormat = remember { java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item(key = "cal_header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Release calendar · next 30 days",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        com.aviostream.app.data.NextUpResolver.clearCache()
                        refreshTick++
                    }
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh calendar",
                        tint = TextSecondary
                    )
                }
            }
        }

        if (loading && days.isEmpty()) {
            item(key = "cal_loading") {
                Box(
                    Modifier.fillMaxWidth().padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    OrbitDots(modifier = Modifier.size(38.dp))
                }
            }
        }

        days.forEach { day ->
            val label = when (day.date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> day.date.format(dayFormat)
            }
            item(key = "cal_day_${day.date}") {
                SectionTitle("$label · ${day.episodes.size} ${if (day.episodes.size == 1) "episode" else "episodes"}")
            }
            items(day.episodes, key = { "${it.showId}_${it.season}_${it.episode}" }) { episode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail("", "series", episode.showId) }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SafePosterImage(
                        model = episode.poster,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            episode.showName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${episode.episodeLabel} · ${episode.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        if (day.date == today) "Today" else if (day.date == today.plusDays(1)) "Tomorrow" else day.date.format(
                            java.time.format.DateTimeFormatter.ofPattern("MMM d")
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.accent
                    )
                }
            }
        }

        if (!loading && days.isEmpty()) {
            item(key = "cal_empty") {
                Text(
                    "No upcoming episodes in the next 30 days from your library — watch an episode or save a series and its next air dates will show up here.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
                )
            }
        }
    }
}

/**
 * Step 48: Downloaded as a real Library mode (Nuvio
 * library/LibraryDownloadedContent.kt, scoped port). In-progress downloads
 * get live progress cards with pause/resume; completed downloads are
 * grouped per show (movies in one row), each opening offline playback.
 * The full Downloads screen stays one tap away via "See all".
 */
@Composable
private fun LibraryDownloadedTab(
    downloads: List<DownloadItem>,
    activeProgress: Map<String, DownloadManager.DownloadProgress>,
    manager: DownloadManager,
    onPlayDownload: (filePath: String, title: String) -> Unit,
    onGoToDownloads: () -> Unit
) {
    val palette = LocalPalette.current
    val active = remember(downloads) {
        downloads.filter { it.status in listOf("QUEUED", "DOWNLOADING", "PAUSED", "FAILED") }
    }
    val completed = remember(downloads) {
        downloads.filter { it.status == "COMPLETED" }
    }
    val showGroups = remember(completed) {
        completed.filter { it.showKey.isNotEmpty() }.groupBy { it.showKey }
    }
    val movies = remember(completed) {
        completed.filter { it.showKey.isEmpty() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp)
    ) {
        item(key = "dl_tab_header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Downloaded",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "See all",
                    color = palette.accent,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable { onGoToDownloads() }
                )
            }
        }

        if (active.isNotEmpty()) {
            item(key = "dl_active_title") {
                SectionTitle("In progress")
            }
            items(active, key = { "active_${it.id}" }) { item ->
                val progress = activeProgress[item.id]
                val downloaded = progress?.downloadedBytes ?: item.downloadedBytes
                val total = progress?.totalBytes?.takeIf { it > 0 } ?: item.totalBytes.takeIf { it > 0 } ?: 0L
                val fraction = if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else null
                GlassCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    when {
                                        item.status == "FAILED" -> "Failed — tap resume to retry"
                                        item.status == "PAUSED" -> "Paused · ${manager.formatBytes(downloaded)}" +
                                            if (total > 0) " of ${manager.formatBytes(total)}" else ""
                                        total > 0 -> "${manager.formatBytes(downloaded)} of ${manager.formatBytes(total)}"
                                        else -> manager.formatBytes(downloaded)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (item.status == "FAILED") MaterialTheme.colorScheme.error else TextSecondary
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (item.status in listOf("DOWNLOADING", "QUEUED")) {
                                        manager.pauseDownload(item.id)
                                    } else {
                                        manager.resumeDownload(item.id)
                                    }
                                }
                            ) {
                                Icon(
                                    if (item.status in listOf("DOWNLOADING", "QUEUED")) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (item.status in listOf("DOWNLOADING", "QUEUED")) "Pause" else "Resume",
                                    tint = TextPrimary
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.accent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = palette.accent,
                                trackColor = Color.White.copy(alpha = 0.12f)
                            )
                        }
                    }
                }
            }
        }

        showGroups.forEach { (showKey, items) ->
            val name = items.firstOrNull()?.seriesName?.takeIf { it.isNotBlank() } ?: showKey
            item(key = "dl_group_$showKey") {
                SectionTitle(name)
            }
            item(key = "dl_row_$showKey") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        DownloadCard(item) {
                            onPlayDownload(item.filePath, item.title)
                        }
                    }
                }
            }
        }

        if (movies.isNotEmpty()) {
            item(key = "dl_movies_title") {
                SectionTitle("Movies")
            }
            item(key = "dl_movies_row") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(movies, key = { it.id }) { item ->
                        DownloadCard(item) {
                            onPlayDownload(item.filePath, item.title)
                        }
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item(key = "dl_tab_empty") {
                Text(
                    "Nothing downloaded yet — download a stream from any title and it will show up here, ready to play offline.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 28.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadCard(
    item: com.aviostream.app.data.db.DownloadItem,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    // Step 46: cached poster for offline browsing (movies: the meta key is
    // the item id; series episodes: the show key). Falls back to the
    // first-letter tile when nothing was cached.
    val context = LocalContext.current
    val poster = remember(item.id) {
        com.aviostream.app.data.download.OfflineShowMetadata.posterFile(
            context, item.showKey.ifEmpty { item.id }
        )
    }
    Column(
        modifier = Modifier
            .width(104.dp)
            .liquidGlass(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(9.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            if (poster != null) {
                SafePosterImage(
                    model = poster,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    item.title.take(1).uppercase(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(palette.accent, RoundedCornerShape(50))
                    .size(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("↓", color = Color(0xFF04140B), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            item.title,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        formatDownloadSize(item.totalBytes).takeIf { it.isNotBlank() }?.let { size ->
            Text(size, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ServiceChip(
    name: String,
    linked: Boolean,
    username: String?,
    modifier: Modifier = Modifier,
    onConnect: () -> Unit
) {
    val palette = LocalPalette.current
    Row(
        modifier = modifier
            .background(
                if (linked) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                RoundedCornerShape(50)
            )
            .border(
                1.dp,
                if (linked) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.25f),
                RoundedCornerShape(50)
            )
            .clickable(enabled = !linked) { onConnect() }
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (linked) {
            Box(
                Modifier.size(7.dp).background(palette.accent, RoundedCornerShape(50))
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (username.isNullOrBlank()) name else "$name · $username",
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Text(
                "$name — connect",
                color = TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ConnectCard(onConnect: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .background(
                Brush.horizontalGradient(
                    listOf(
                        palette.accent.copy(alpha = 0.12f),
                        palette.accent2.copy(alpha = 0.12f)
                    )
                ),
                RoundedCornerShape(16.dp)
            )
            .border(1.dp, palette.accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "Bring your Trakt & Simkl",
                color = TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Connect once and your watchlists, progress and next episodes follow you here — and back.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                    RoundedCornerShape(50)
                )
                .clickable { onConnect() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                "Connect",
                color = Color(0xFF04140B),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun UpNextRow(card: UpNextCard, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .liquidGlass(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(104.dp)
                .height(62.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            SafePosterImage(
                model = card.still ?: card.poster,
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(palette.accent, RoundedCornerShape(50))
                    .size(19.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = Color(0xFF04140B), fontSize = 8.sp)
            }
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(
                card.showTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.nextLabel,
                color = TextPrimary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                card.metaLine,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
            card.progress?.let { p ->
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.13f), RoundedCornerShape(50))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(p.coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(
                                Brush.horizontalGradient(listOf(palette.accent, palette.accent2)),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedRow(
    title: String,
    serviceTag: String,
    tagColor: Color,
    metas: List<MetaPreview>,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    Column(Modifier.padding(top = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                serviceTag,
                color = tagColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp
            )
        }
        // A feed can repeat a title; LazyRow keys must stay unique or
        // Compose throws the duplicate-key crash (same class as ActorScreen).
        val uniqueMetas = metas.distinctBy { it.type + "|" + it.id }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            items(uniqueMetas, key = { "${it.type}_${it.id}" }) { meta ->
                PosterCard(
                    meta = meta,
                    onClick = { onOpenDetail("", meta.type, meta.id) },
                    surface = CustomPosterScreen.LIBRARY
                )
            }
        }
    }
}

@Composable
private fun ListCollageCard(
    list: CustomList,
    listsRepository: CustomListsRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val items by listsRepository.observeItems(list.id).collectAsState(initial = emptyList())
    val posters = items.mapNotNull { it.poster }.take(4)
    Column(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(9.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
        ) {
            if (posters.isEmpty()) {
                Text(
                    list.name.take(1).uppercase(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Row(Modifier.fillMaxSize()) {
                    posters.forEach { poster ->
                        SafePosterImage(
                            model = poster,
                            contentDescription = null,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            list.name,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${items.size} titles",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
