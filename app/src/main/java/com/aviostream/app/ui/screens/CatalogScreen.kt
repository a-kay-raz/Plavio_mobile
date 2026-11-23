package com.aviostream.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aviostream.app.data.CatalogRow
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.isUnreleased
import com.aviostream.app.ui.components.EmptyState
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.OrbitDots
import com.aviostream.app.ui.components.PosterCard
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow

/**
 * "View all" grid for a Home catalog row — now paged (Nuvio
 * CatalogRepository). The row's items are handed over in-memory via
 * [repository.pendingCatalog] as page 1, and scrolling near the end
 * fetches further pages from the addon with Stremio skip=
 * (.../catalog/{type}/{id}/skip={n}.json, 100 per page). The grid state
 * is saveable, so rotate/back keeps the scroll spot; unreleased titles
 * hide here exactly like on Home (the global Home catalogs setting).
 */
@Composable
fun CatalogScreen(
    repository: StreamRepository,
    onBack: () -> Unit,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit
) {
    val catalog = repository.pendingCatalog
    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = TextPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = catalog?.catalogName ?: "Catalog",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (catalog != null) {
                        Text(
                            text = catalog.addonName,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
            if (catalog == null || catalog.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        title = "Nothing here",
                        message = "This catalog came back empty.",
                        actionLabel = "Go back",
                        onAction = onBack
                    )
                }
            } else {
                val vm: CatalogViewModel = viewModel(factory = vmFactory { CatalogViewModel(repository, catalog) })
                // Saveable grid state (Nuvio CatalogScreen): rotate and
                // back-from-Detail both restore the exact spot.
                val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
                // Load more when the last visible item is within 6 of the end.
                LaunchedEffect(gridState) {
                    snapshotFlow { gridState.layoutInfo }
                        .map { info ->
                            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                            last >= info.totalItemsCount - 6
                        }
                        .distinctUntilChanged()
                        .filter { nearEnd -> nearEnd && vm.hasMore && !vm.isLoadingMore }
                        .collect { vm.loadMore() }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(140.dp),
                    state = gridState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        vm.items,
                        key = { index, meta -> "cat_${meta.id}_$index" }
                    ) { _, meta ->
                        PosterCard(
                            meta = meta,
                            onClick = {
                                onOpenDetail(catalog.addonId, meta.type, meta.id)
                            }
                        )
                    }
                    if (vm.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                OrbitDots(modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    if (vm.loadError != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth().padding(16.dp)
                            ) {
                                Text(
                                    vm.loadError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                TextButton(onClick = { vm.loadMore() }) { Text("Retry") }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Page size for catalog paging (Nuvio CATALOG_PAGE_SIZE). */
private const val CATALOG_PAGE_SIZE = 100

/**
 * Paged catalog state (Nuvio CatalogRepository, simplified for the
 * Stremio skip= protocol): the handed-over row seeds page 1, then
 * pages load with skip = raw items fetched so far. skip= counts RAW
 * addon items — including ones the unreleased filter drops — so the
 * offset advances by the raw page size, never the displayed count.
 */
class CatalogViewModel(
    private val repo: StreamRepository,
    private val catalog: CatalogRow
) : ViewModel() {
    var items by mutableStateOf<List<MetaPreview>>(catalog.items)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set

    private var nextSkip: Int? = null
    private var hideUnreleased = false
    private val currentYear = java.time.LocalDate.now().year

    init {
        viewModelScope.launch {
            hideUnreleased = runCatching { repo.homeRowPrefs().hideUnreleasedContent }.getOrDefault(false)
            if (hideUnreleased) {
                items = items.filter { !it.isUnreleased(currentYear) }
            }
            refreshFirstPage()
        }
    }

    /**
     * Re-fetch page 1: the handed-over seed is the filtered display
     * list, so its size is not the raw page size — the fresh fetch
     * establishes how many raw items page 1 holds, which is what
     * skip= counts. Merged over the seed (dedupe by id) so the screen
     * never flashes empty.
     */
    private suspend fun refreshFirstPage() {
        val page = runCatching {
            repo.fetchCatalogPage(catalog.baseUrl, catalog.type, catalog.catalogId, skip = 0)
        }.getOrNull() ?: return
        items = mergeItems(page.filter { !hideUnreleased || !it.isUnreleased(currentYear) }, items)
        nextSkip = if (page.size >= CATALOG_PAGE_SIZE) page.size else null
        hasMore = nextSkip != null
    }

    fun loadMore() {
        val skip = nextSkip ?: return
        if (isLoadingMore) return
        isLoadingMore = true
        loadError = null
        viewModelScope.launch {
            val page = runCatching {
                repo.fetchCatalogPage(catalog.baseUrl, catalog.type, catalog.catalogId, skip = skip)
            }.getOrNull()
            if (page == null) {
                loadError = "Couldn't load more — check your connection."
                isLoadingMore = false
                return@launch
            }
            val before = items.size
            items = mergeItems(
                items,
                page.filter { !hideUnreleased || !it.isUnreleased(currentYear) }
            )
            val added = items.size - before
            nextSkip = when {
                page.size < CATALOG_PAGE_SIZE -> null // last page
                added == 0 -> null // addon ignores skip and repeats page 1
                else -> skip + page.size
            }
            hasMore = nextSkip != null
            isLoadingMore = false
        }
    }

    private fun mergeItems(
        existing: List<MetaPreview>,
        incoming: List<MetaPreview>
    ): List<MetaPreview> {
        if (incoming.isEmpty()) return existing
        val seen = existing.mapTo(mutableSetOf()) { it.id }
        return buildList(existing.size + incoming.size) {
            addAll(existing)
            incoming.forEach { if (seen.add(it.id)) add(it) }
        }
    }
}
