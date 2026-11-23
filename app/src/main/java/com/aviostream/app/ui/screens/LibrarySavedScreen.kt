package com.aviostream.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviostream.app.data.LibraryLayoutMode
import com.aviostream.app.data.LibrarySortOption
import com.aviostream.app.data.LibraryWatchedFilter
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.SavedLibraryItem
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.availableLibrarySortOptions
import com.aviostream.app.data.computeLibraryFacets
import com.aviostream.app.data.db.CustomList
import com.aviostream.app.data.db.CustomListItem
import com.aviostream.app.data.db.WatchlistItem
import com.aviostream.app.data.FavoritePerson
import com.aviostream.app.data.dedupeSavedItems
import com.aviostream.app.data.effectiveLibrarySortOption
import com.aviostream.app.data.filterSavedItems
import com.aviostream.app.data.filterSavedItemsByFacets
import com.aviostream.app.data.parseReleaseDate
import com.aviostream.app.data.parseReleaseYear
import com.aviostream.app.data.sortSavedItems
import com.aviostream.app.ui.components.CustomPosterScreen
import com.aviostream.app.ui.components.PosterCard
import com.aviostream.app.ui.components.SafePosterImage
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/** Nuvio muted subtitle (#969CA3) — §2 presentation values. */
private val LibraryMuted = Color(0xFF969CA3)

/**
 * §2 section header (Nuvio settings header): 12sp Bold, letter-spacing
 * 0.8sp, muted, NO uppercase.
 */
@Composable
private fun LibrarySectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
        ),
        color = LibraryMuted,
        modifier = modifier
    )
}

/**
 * Step 47: Library Saved view (Nuvio library/LibraryScreen.kt +
 * LibraryDisplaySettings.kt, scoped port). One home for everything saved:
 * My Watchlist, custom lists, Trakt and Simkl watchlists — sortable 7
 * ways, rows-per-section or a single unified grid, with a watched filter
 * and a section picker. Preferences persist in SettingsStore.
 *
 * The sections (Up Next / Downloads / service rows) stay on the
 * "Sections" tab; this is the "Saved" tab.
 */
@Composable
fun LibrarySavedScreen(
    myWatchlist: List<WatchlistItem>,
    myLists: List<CustomList>,
    allListItems: List<CustomListItem>,
    traktWatchlist: List<MetaPreview>,
    simklWatchlist: List<MetaPreview>,
    watchedKeys: Set<String>,
    settings: SettingsStore,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    // Step 58: starred people (pinned first) + tap opens the actor page.
    favoritePeople: List<FavoritePerson> = emptyList(),
    onOpenPerson: (personId: Int, name: String) -> Unit = { _, _ -> },
    // Per-source load state from LibraryScreen (Nuvio per-source error
    // card + Retry + offline card).
    traktLoadError: Boolean = false,
    simklLoadError: Boolean = false,
    isOffline: Boolean = false,
    onRetry: (() -> Unit)? = null
) {
    val allItems = remember(myWatchlist, myLists, allListItems, traktWatchlist, simklWatchlist) {
        buildList {
            myWatchlist.forEach { item ->
                add(
                    SavedLibraryItem(
                        metaId = item.metaId, type = item.type, name = item.name,
                        poster = item.poster, addonId = item.addonId,
                        sectionKey = "watchlist", sectionName = "My Watchlist",
                        addedAt = item.addedAt, year = item.year, sourceOrder = 0
                    )
                )
            }
            myLists.forEachIndexed { index, list ->
                allListItems.filter { it.listId == list.id }.forEach { item ->
                    add(
                        SavedLibraryItem(
                            metaId = item.metaId, type = item.type, name = item.name,
                            poster = item.poster, addonId = item.addonId,
                            sectionKey = "list:${list.id}", sectionName = list.name,
                            addedAt = item.addedAt, year = item.year, sourceOrder = 1 + index
                        )
                    )
                }
            }
            traktWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "trakt", sectionName = "Trakt",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 100,
                        releaseDate = preview.releaseDate ?: parseReleaseDate(preview.releaseInfo),
                        genres = preview.genres
                    )
                )
            }
            simklWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "simkl", sectionName = "Simkl",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 101,
                        releaseDate = preview.releaseDate ?: parseReleaseDate(preview.releaseInfo),
                        genres = preview.genres
                    )
                )
            }
        }
    }
    // Sections present, in source order.
    val sections = remember(allItems) {
        allItems.distinctBy { it.sectionKey }.map { it.sectionKey to it.sectionName }
    }
    // Nuvio sort gating: the sort menu offers only what the current
    // library source offers (Local 4, single remote 5, merged all 7);
    // a stored sort the source does not offer falls back in-memory.
    val storedSort = LibrarySortOption.fromStored(settings.librarySort)
    val sort = effectiveLibrarySortOption(storedSort, settings.trackingLibrarySource)
    val sortOptions = availableLibrarySortOptions(settings.trackingLibrarySource)
    val layout = LibraryLayoutMode.fromStored(settings.libraryLayout)
    val watchedFilter = LibraryWatchedFilter.fromStored(settings.libraryWatchedFilter)
    val selectedSection = settings.librarySection.takeIf { key ->
        key.isNotEmpty() && sections.any { it.first == key }
    } ?: ""
    // Vertical-mode facets (Nuvio LibrarySavedControls): session-only
    // picks — SettingsStore holds no facet fields, so they do not persist.
    var facetType by rememberSaveable { mutableStateOf<String?>(null) }
    var facetGenre by rememberSaveable { mutableStateOf<String?>(null) }
    var facetYear by rememberSaveable { mutableStateOf<Int?>(null) }
    val hasActiveFacets = facetType != null || facetGenre != null || facetYear != null

    val visibleItems = remember(allItems, watchedKeys, watchedFilter) {
        filterSavedItems(allItems, watchedFilter, watchedKeys)
    }
    val facets = remember(visibleItems) { computeLibraryFacets(visibleItems) }
    val facetItems = remember(visibleItems, facetType, facetGenre, facetYear) {
        filterSavedItemsByFacets(visibleItems, facetType, facetGenre, facetYear)
    }
    val sectionItems = remember(facetItems, selectedSection) {
        if (selectedSection.isEmpty()) facetItems else facetItems.filter { it.sectionKey == selectedSection }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Step 58: favorite people rail (pinned first) above the
        // controls — tap opens the actor page.
        if (favoritePeople.isNotEmpty()) {
            LibrarySectionHeader(
                text = "Favorite people",
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(favoritePeople, key = { it.tmdbId }) { person ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(72.dp)
                            .clickable { onOpenPerson(person.tmdbId, person.name) }
                    ) {
                        SafePosterImage(
                            model = person.photo,
                            contentDescription = person.name,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = person.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        // Controls: sort menu + layout toggle, watched chips, section chips.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var sortMenuOpen by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { sortMenuOpen = true }) {
                    Text("Sort: ${sort.label}", color = TextPrimary, style = MaterialTheme.typography.labelLarge)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                }
                DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                settings.librarySort = option.stored
                                sortMenuOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            // 36dp icon tile (Nuvio settings row tile): accent @ 0.12, 12dp radius.
            val palette = LocalPalette.current
            Box(
                Modifier
                    .size(36.dp)
                    .background(palette.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .clickable {
                        settings.libraryLayout =
                            if (layout == LibraryLayoutMode.ROWS) LibraryLayoutMode.SINGLE.stored
                            else LibraryLayoutMode.ROWS.stored
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (layout == LibraryLayoutMode.ROWS) Icons.Filled.GridView else Icons.Filled.List,
                    contentDescription = if (layout == LibraryLayoutMode.ROWS) "Single section grid" else "Rows per section",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        // Vertical-mode facets (Nuvio): type / genre / year dropdowns
        // with counts. Rows mode keeps the section picker below instead.
        if (layout == LibraryLayoutMode.SINGLE) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var typeMenuOpen by remember { mutableStateOf(false) }
                var genreMenuOpen by remember { mutableStateOf(false) }
                var yearMenuOpen by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { typeMenuOpen = true }) {
                        Text(
                            "Type: " + (facetType?.let { if (it == "series") "Series" else "Movies" } ?: "All"),
                            color = TextPrimary, style = MaterialTheme.typography.labelLarge
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                    }
                    DropdownMenu(expanded = typeMenuOpen, onDismissRequest = { typeMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("All types (${facets.types.sumOf { it.count }})") },
                            onClick = { facetType = null; typeMenuOpen = false }
                        )
                        facets.types.forEach { facet ->
                            DropdownMenuItem(
                                text = { Text("${facet.label} (${facet.count})") },
                                onClick = { facetType = facet.key; typeMenuOpen = false }
                            )
                        }
                    }
                }
                if (facets.genres.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { genreMenuOpen = true }) {
                            Text(
                                "Genre: " + (facetGenre ?: "All"),
                                color = TextPrimary, style = MaterialTheme.typography.labelLarge
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                        }
                        DropdownMenu(expanded = genreMenuOpen, onDismissRequest = { genreMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("All genres") },
                                onClick = { facetGenre = null; genreMenuOpen = false }
                            )
                            facets.genres.forEach { facet ->
                                DropdownMenuItem(
                                    text = { Text("${facet.label} (${facet.count})") },
                                    onClick = { facetGenre = facet.key; genreMenuOpen = false }
                                )
                            }
                        }
                    }
                }
                if (facets.years.isNotEmpty()) {
                    Box {
                        TextButton(onClick = { yearMenuOpen = true }) {
                            Text(
                                "Year: " + (facetYear?.toString() ?: "All"),
                                color = TextPrimary, style = MaterialTheme.typography.labelLarge
                            )
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                        }
                        DropdownMenu(expanded = yearMenuOpen, onDismissRequest = { yearMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("All years") },
                                onClick = { facetYear = null; yearMenuOpen = false }
                            )
                            facets.years.forEach { facet ->
                                DropdownMenuItem(
                                    text = { Text("${facet.label} (${facet.count})") },
                                    onClick = { facetYear = facet.key.toIntOrNull(); yearMenuOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryWatchedFilter.entries.forEach { filter ->
                FilterChip(
                    selected = watchedFilter == filter,
                    onClick = { settings.libraryWatchedFilter = filter.stored },
                    label = { Text(filter.label) }
                )
            }
        }
        if (sections.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                item(key = "section_all") {
                    FilterChip(
                        selected = selectedSection.isEmpty(),
                        onClick = { settings.librarySection = "" },
                        label = { Text("All sections") }
                    )
                }
                items(sections, key = { it.first }) { (key, name) ->
                    FilterChip(
                        selected = selectedSection == key,
                        onClick = { settings.librarySection = key },
                        label = { Text(name) }
                    )
                }
            }
        }
        // 0.5dp divider under the controls (Nuvio §2 divider weight).
        HorizontalDivider(
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.10f),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
        )

        if (sectionItems.isEmpty()) {
            // Per-source empty / error / offline states (Nuvio
            // HomeEmptyStateCard + NuvioNetworkOfflineCard, scoped port).
            val failedSources = buildList {
                if (traktLoadError) add("Trakt")
                if (simklLoadError) add("Simkl")
            }
            val emptyTitle: String
            val emptyBody: String
            val showRetry: Boolean
            val showClearFilters: Boolean
            when {
                isOffline && allItems.isEmpty() && settings.trackingLibrarySource != "local" -> {
                    emptyTitle = "You're offline"
                    emptyBody = "Your Trakt and Simkl titles will load again when you're back online — your local watchlist is unaffected."
                    showRetry = true
                    showClearFilters = false
                }
                failedSources.isNotEmpty() && allItems.isEmpty() -> {
                    val names = failedSources.joinToString(" and ")
                    emptyTitle = "$names couldn't load"
                    emptyBody = "We couldn't reach $names just now — your saved titles are still there. Check your connection and try again."
                    showRetry = true
                    showClearFilters = false
                }
                allItems.isEmpty() -> {
                    emptyTitle = when (settings.trackingLibrarySource) {
                        "trakt" -> "Nothing saved on Trakt yet"
                        "simkl" -> "Nothing saved on Simkl yet"
                        "local" -> "Nothing saved locally yet"
                        else -> "Nothing saved yet"
                    }
                    emptyBody = when (settings.trackingLibrarySource) {
                        "trakt" -> "Your Trakt watchlist is empty — add a title on Trakt or in this app and it will show up here."
                        "simkl" -> "Your Simkl Plan to Watch list is empty — add a title on Simkl or in this app and it will show up here."
                        else -> "Add titles to your watchlist or a list."
                    }
                    showRetry = false
                    showClearFilters = false
                }
                else -> {
                    emptyTitle = "No saved titles match"
                    emptyBody = "Try a different filter — or clear the filters to see everything saved."
                    showRetry = false
                    showClearFilters = hasActiveFacets || watchedFilter != LibraryWatchedFilter.ALL ||
                        selectedSection.isNotEmpty()
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 36dp icon tile (Nuvio settings row tile): accent @ 0.12, 12dp radius.
                    val palette = LocalPalette.current
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(palette.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isOffline || failedSources.isNotEmpty()) "!" else "☆",
                            color = palette.accent,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        emptyTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        emptyBody,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LibraryMuted,
                        textAlign = TextAlign.Center
                    )
                    if (showRetry && onRetry != null) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = onRetry) {
                            Text("Retry", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (showClearFilters) {
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = {
                            facetType = null
                            facetGenre = null
                            facetYear = null
                            settings.libraryWatchedFilter = LibraryWatchedFilter.ALL.stored
                            settings.librarySection = ""
                        }) {
                            Text("Clear filters", color = palette.accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return@Column
        }

        when (layout) {
            LibraryLayoutMode.ROWS -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 110.dp)
                ) {
                    val grouped = sectionItems.groupBy { it.sectionKey }
                    sections.filter { (key, _) ->
                        selectedSection.isEmpty() || selectedSection == key
                    }.forEach { (key, name) ->
                        val items = sortSavedItems(
                            grouped[key].orEmpty().distinctBy { "${it.type}|${it.metaId}" },
                            sort
                        )
                        if (items.isEmpty()) return@forEach
                        item(key = "saved_row_$key") {
                            Column(Modifier.padding(top = 10.dp)) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    LibrarySectionHeader(
                                        text = name,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${items.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = LibraryMuted
                                    )
                                }
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    items(items, key = { "${it.type}_${it.metaId}" }) { item ->
                                        PosterCard(
                                            meta = MetaPreview(
                                                id = item.metaId, type = item.type,
                                                name = item.name, poster = item.poster,
                                                // Release info on the card
                                                // (Nuvio hydrated releaseInfo):
                                                // full date when known, else
                                                // the year. PosterCard shows
                                                // the year line from this.
                                                releaseInfo = item.releaseDate
                                                    ?: item.year.takeIf { it > 0 }?.toString()
                                            ),
                                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) },
                                            surface = CustomPosterScreen.LIBRARY
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            LibraryLayoutMode.SINGLE -> {
                val gridItems = sortSavedItems(dedupeSavedItems(sectionItems), sort)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 110.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(gridItems, key = { "${it.type}_${it.metaId}" }) { item ->
                        PosterCard(
                            meta = MetaPreview(
                                id = item.metaId, type = item.type,
                                name = item.name, poster = item.poster,
                                // Release info on the card (Nuvio hydrated
                                // releaseInfo): full date when known, else
                                // the year. PosterCard shows the year line.
                                releaseInfo = item.releaseDate
                                    ?: item.year.takeIf { it > 0 }?.toString()
                            ),
                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) },
                            surface = CustomPosterScreen.LIBRARY
                        )
                    }
                }
            }
        }
    }
}
