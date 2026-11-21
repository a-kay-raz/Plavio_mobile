package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.CustomList
import com.pulsestream.app.data.CustomListItem
import com.pulsestream.app.data.FavoritePerson
import com.pulsestream.app.data.LibraryLayoutMode
import com.pulsestream.app.data.LibrarySortOption
import com.pulsestream.app.data.LibraryWatchedFilter
import com.pulsestream.app.data.MetaPreview
import com.pulsestream.app.data.SavedLibraryItem
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.WatchlistItem
import com.pulsestream.app.data.dedupeSavedItems
import com.pulsestream.app.data.filterSavedItems
import com.pulsestream.app.data.parseReleaseYear
import com.pulsestream.app.data.sortSavedItems
import com.pulsestream.app.ui.components.PosterCard
import com.pulsestream.app.ui.components.SafePosterImage
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

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
    anilistWatchlist: List<MetaPreview> = emptyList(),
    wetrakrWatchlist: List<MetaPreview> = emptyList(),
    pmdbWatchlist: List<MetaPreview> = emptyList(),
    mdblistWatchlist: List<MetaPreview> = emptyList(),
    watchedKeys: Set<String>,
    settings: SettingsStore,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit,
    // Step 58: starred people (pinned first) + tap opens the actor page.
    favoritePeople: List<FavoritePerson> = emptyList(),
    onOpenPerson: (personId: Int, name: String) -> Unit = { _, _ -> }
) {
    val allItems = remember(myWatchlist, myLists, allListItems, traktWatchlist, simklWatchlist, anilistWatchlist, wetrakrWatchlist, pmdbWatchlist, mdblistWatchlist) {
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
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 100
                    )
                )
            }
            simklWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "simkl", sectionName = "Simkl",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 101
                    )
                )
            }
            anilistWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "anilist", sectionName = "AniList",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 102
                    )
                )
            }
            wetrakrWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "wetrakr", sectionName = "WeTrakr",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 103
                    )
                )
            }
            pmdbWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "publicmetadb", sectionName = "PublicMetaDB",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 104
                    )
                )
            }
            mdblistWatchlist.forEach { preview ->
                add(
                    SavedLibraryItem(
                        metaId = preview.id, type = preview.type, name = preview.name,
                        poster = preview.poster, addonId = "",
                        sectionKey = "mdblist", sectionName = "MDBList",
                        addedAt = 0L, year = parseReleaseYear(preview.releaseInfo), sourceOrder = 105
                    )
                )
            }
        }
    }
    // Sections present, in source order.
    val sections = remember(allItems) {
        allItems.distinctBy { it.sectionKey }.map { it.sectionKey to it.sectionName }
    }
    val sort = LibrarySortOption.fromStored(settings.librarySort)
    val layout = LibraryLayoutMode.fromStored(settings.libraryLayout)
    val watchedFilter = LibraryWatchedFilter.fromStored(settings.libraryWatchedFilter)
    val selectedSection = settings.librarySection.takeIf { key ->
        key.isNotEmpty() && sections.any { it.first == key }
    } ?: ""

    val visibleItems = remember(allItems, watchedKeys, watchedFilter) {
        filterSavedItems(allItems, watchedFilter, watchedKeys)
    }
    val sectionItems = remember(visibleItems, selectedSection) {
        if (selectedSection.isEmpty()) visibleItems else visibleItems.filter { it.sectionKey == selectedSection }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Step 58: favorite people rail (pinned first) above the
        // controls — tap opens the actor page.
        if (favoritePeople.isNotEmpty()) {
            Text(
                text = "Favorite people",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
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
                    LibrarySortOption.entries.forEach { option ->
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
            IconButton(onClick = {
                settings.libraryLayout =
                    if (layout == LibraryLayoutMode.ROWS) LibraryLayoutMode.SINGLE.stored
                    else LibraryLayoutMode.ROWS.stored
            }) {
                Icon(
                    if (layout == LibraryLayoutMode.ROWS) Icons.Filled.GridView else Icons.Filled.List,
                    contentDescription = if (layout == LibraryLayoutMode.ROWS) "Single section grid" else "Rows per section",
                    tint = TextSecondary
                )
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

        if (sectionItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (allItems.isEmpty()) "Nothing saved yet — add titles to your watchlist or a list."
                    else "No saved titles match this filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
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
                                    Text(
                                        name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "${items.size}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
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
                                                name = item.name, poster = item.poster
                                            ),
                                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) }
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
                                name = item.name, poster = item.poster
                            ),
                            onClick = { onOpenDetail(item.addonId, item.type, item.metaId) }
                        )
                    }
                }
            }
        }
    }
}
