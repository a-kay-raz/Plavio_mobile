@file:OptIn(ExperimentalLayoutApi::class)
package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.CollectionRepository
import com.pulsestream.app.data.CollectionSourceResolver
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.db.Collection
import com.pulsestream.app.data.db.CollectionFolder
import com.pulsestream.app.data.db.CollectionSourceRow
import com.pulsestream.app.data.db.InstalledAddon
import com.pulsestream.app.data.trakt.TraktApi
import com.pulsestream.app.data.trakt.TraktManager
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.LocalPalette
import kotlinx.coroutines.launch

/**
 * Step 50: collection editor (Nuvio collection/CollectionEditorScreen.kt,
 * scoped port). Edits the collection's title/backdrop/pin/view mode and
 * its folders (title, tile shape, cover emoji) and each folder's sources.
 * Sources resolve live — the editor shows each source's title count.
 * No Supabase sync, no focus GIF / hero video, no JSON import/export.
 */
@Composable
fun CollectionEditorScreen(
    collectionId: String,
    collectionRepository: CollectionRepository,
    repository: StreamRepository,
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var collection by remember { mutableStateOf<Collection?>(null) }
    val folders by collectionRepository.observeFolders(collectionId)
        .collectAsState(initial = emptyList())
    val addons by repository.addons.collectAsState(initial = emptyList())
    var showAddFolder by remember { mutableStateOf(false) }
    var viewModePickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(collectionId) {
        collection = collectionRepository.getCollection(collectionId)
    }

    fun save(updated: Collection) {
        collection = updated
        scope.launch { collectionRepository.updateCollection(updated) }
    }

    GlowBackground {
        val current = collection
        if (current == null) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Text("Loading…", color = V117Muted)
            }
            return@GlowBackground
        }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Edit collection",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { save(current.copy(title = it.take(80))) },
                    label = { Text("Collection title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = current.backdropUrl ?: "",
                    onValueChange = { save(current.copy(backdropUrl = it.take(500).ifBlank { null })) },
                    label = { Text("Backdrop image URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                // Flat v1.17 toggle rows (gap report §2): 16dp / 14dp,
                // gradient switch 52x32dp / 24dp thumb / 180ms tween.
                V117ToggleRow(
                    icon = null,
                    title = "Pin to top",
                    subtitle = "Pinned collections show first on Home",
                    checked = current.pinToTop,
                    onCheckedChange = { save(current.copy(pinToTop = it)) }
                )
                V117ToggleRow(
                    icon = null,
                    title = "Show \"All\" tab",
                    subtitle = "An All tab merges every folder's titles",
                    checked = current.showAllTab,
                    onCheckedChange = { save(current.copy(showAllTab = it)) }
                )
                V117NavRow(
                    icon = null,
                    title = "View mode",
                    subtitle = if (current.viewMode == "ROWS") "Rows" else "Tabbed grid",
                    onClick = { viewModePickerOpen = true },
                    trailing = {
                        Text("Change", color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        V117Chevron()
                    }
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    V117SectionLabel("Folders", modifier = Modifier.weight(1f))
                    TextButton(onClick = { showAddFolder = true }) { Text("+ Add folder") }
                }
                if (folders.isEmpty()) {
                    Text(
                        text = "No folders yet — add one, then give it sources (an addon catalog, a TMDB collection/list/discover, or a Trakt list).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            itemsIndexed(folders, key = { _, folder -> folder.id }) { index, folder ->
                FolderEditorCard(
                    folder = folder,
                    addons = addons,
                    isFirst = index == 0,
                    isLast = index == folders.lastIndex,
                    repository = repository,
                    collectionRepository = collectionRepository,
                    onOpenFolder = { onOpenFolder(folder.id) }
                )
                Spacer(Modifier.height(10.dp))
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (viewModePickerOpen) {
        val current = collection
        PickerDialog(
            title = "View mode",
            options = listOf("TABBED_GRID" to "Tabbed grid", "ROWS" to "Rows"),
            selected = current?.viewMode,
            onSelect = { mode ->
                viewModePickerOpen = false
                current?.let { save(it.copy(viewMode = mode)) }
            },
            onDismiss = { viewModePickerOpen = false }
        )
    }

    if (showAddFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddFolder = false },
            containerColor = LocalPalette.current.background,
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val title = name.trim()
                        showAddFolder = false
                        scope.launch { collectionRepository.createFolder(collectionId, title) }
                    }
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showAddFolder = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun FolderEditorCard(
    folder: CollectionFolder,
    addons: List<InstalledAddon>,
    isFirst: Boolean,
    isLast: Boolean,
    repository: StreamRepository,
    collectionRepository: CollectionRepository,
    onOpenFolder: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val sources by collectionRepository.observeSources(folder.id)
        .collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var showAddSource by remember { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { onOpenFolder() }) {
                    Text(
                        text = folder.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${sources.size} ${if (sources.size == 1) "source" else "sources"} — tap to view titles",
                        style = MaterialTheme.typography.bodySmall,
                        color = V117Muted
                    )
                }
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Folder options", tint = V117Muted)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; renameOpen = true }
                    )
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null) },
                        enabled = !isFirst,
                        onClick = {
                            menuOpen = false
                            scope.launch { collectionRepository.moveFolder(folder.id, folderIndex(collectionRepository, folder) - 1) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null) },
                        enabled = !isLast,
                        onClick = {
                            menuOpen = false
                            scope.launch { collectionRepository.moveFolder(folder.id, folderIndex(collectionRepository, folder) + 1) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; deleteOpen = true }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tile:", color = V117Muted, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.padding(horizontal = 4.dp))
                listOf("poster" to "Poster", "landscape" to "Landscape", "square" to "Square").forEach { (value, label) ->
                    FilterChip(
                        selected = folder.tileShape == value,
                        onClick = {
                            scope.launch { collectionRepository.updateFolder(folder.copy(tileShape = value)) }
                        },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp)
                    )
                }
            }
            OutlinedTextField(
                value = folder.coverEmoji ?: "",
                onValueChange = {
                    scope.launch {
                        collectionRepository.updateFolder(folder.copy(coverEmoji = it.take(2).ifBlank { null }))
                    }
                },
                label = { Text("Cover emoji (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
            Spacer(Modifier.height(8.dp))
            sources.forEach { source ->
                SourceRow(
                    source = source,
                    addons = addons,
                    repository = repository,
                    onDelete = { scope.launch { collectionRepository.deleteSource(source.id) } }
                )
            }
            TextButton(onClick = { showAddSource = true }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add source")
            }
        }
    }

    if (renameOpen) {
        var name by remember { mutableStateOf(folder.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Rename folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        renameOpen = false
                        scope.launch {
                            collectionRepository.updateFolder(folder.copy(title = name.trim().take(80)))
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renameOpen = false }) { Text("Cancel") } }
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            containerColor = LocalPalette.current.background,
            title = { Text("Delete folder?") },
            text = { Text("\"${folder.title}\" and all of its sources will be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteOpen = false
                    scope.launch { collectionRepository.deleteFolder(folder.id) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteOpen = false }) { Text("Cancel") } }
        )
    }

    if (showAddSource) {
        AddSourceDialog(
            folderId = folder.id,
            addons = addons,
            repository = repository,
            collectionRepository = collectionRepository,
            onDismiss = { showAddSource = false }
        )
    }
}

/** Folder index for the move buttons — read fresh so the index is never stale. */
private suspend fun folderIndex(
    collectionRepository: CollectionRepository,
    folder: CollectionFolder,
): Int = collectionRepository.getFolders(folder.collectionId)
    .indexOfFirst { it.id == folder.id }

@Composable
private fun SourceRow(
    source: CollectionSourceRow,
    addons: List<InstalledAddon>,
    repository: StreamRepository,
    onDelete: () -> Unit,
) {
    var count by remember(source.id) { mutableStateOf<Int?>(null) }
    LaunchedEffect(source) {
        count = CollectionSourceResolver.resolveSource(repository, source, 100).size
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = sourceLabel(source, addons),
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = count?.let { "$it titles" } ?: "Resolving…",
                color = V117Muted,
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove source", tint = V117Muted)
        }
    }
}

private fun sourceLabel(source: CollectionSourceRow, addons: List<InstalledAddon>): String =
    when (source.provider) {
        "addon" -> {
            val addonName = addons.firstOrNull { it.id == source.addonId }?.name ?: source.addonId
            buildString {
                append(addonName ?: "Addon")
                append(" · ")
                append(source.type ?: "movie")
                append(" · ")
                append(source.catalogId ?: "?")
                source.genre?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            }
        }
        "tmdb" -> when (source.tmdbSourceType?.uppercase()) {
            "COLLECTION" -> "TMDB collection ${source.tmdbId ?: "?"}"
            "LIST" -> "TMDB list ${source.tmdbId ?: "?"}"
            else -> "TMDB discover ${source.mediaType ?: "movie"} · ${source.sortBy ?: "popularity.desc"}"
        }
        "trakt" -> "Trakt list ${source.catalogId ?: "?"} (${source.mediaType ?: "all"})"
        else -> source.provider
    }

/** Generic single-select radio dialog, reused for pickers in this file. */
@Composable
private fun PickerDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.background,
        title = { Text(title) },
        text = {
            LazyColumn {
                itemsIndexed(options, key = { index, _ -> index }) { _, option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option.first) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.first == selected,
                            onClick = { onSelect(option.first) }
                        )
                        Text(option.second, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSourceDialog(
    folderId: String,
    addons: List<InstalledAddon>,
    repository: StreamRepository,
    collectionRepository: CollectionRepository,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf("addon") }

    fun add(block: suspend () -> Unit) {
        scope.launch { block(); onDismiss() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.background,
        title = { Text("Add source") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("addon" to "Addon catalog", "tmdb" to "TMDB", "trakt" to "Trakt list").forEach { (value, label) ->
                        FilterChip(
                            selected = provider == value,
                            onClick = { provider = value },
                            label = { Text(label) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (provider) {
                    "addon" -> AddonSourceForm(
                        addons = addons,
                        repository = repository,
                        onAdd = { addonId, type, catalogId, genre ->
                            add {
                                collectionRepository.addSource(
                                    folderId = folderId, provider = "addon",
                                    addonId = addonId, type = type,
                                    catalogId = catalogId, genre = genre
                                )
                            }
                        }
                    )
                    "tmdb" -> TmdbSourceForm(
                        onAdd = { kind, tmdbId, mediaType, sortBy ->
                            add {
                                collectionRepository.addSource(
                                    folderId = folderId, provider = "tmdb",
                                    tmdbSourceType = kind, tmdbId = tmdbId,
                                    mediaType = mediaType, sortBy = sortBy
                                )
                            }
                        }
                    )
                    "trakt" -> TraktSourceForm(
                        repository = repository,
                        onAdd = { listId, mediaType, sortBy, sortHow ->
                            add {
                                collectionRepository.addSource(
                                    folderId = folderId, provider = "trakt",
                                    catalogId = listId, mediaType = mediaType,
                                    sortBy = sortBy, sortHow = sortHow
                                )
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddonSourceForm(
    addons: List<InstalledAddon>,
    repository: StreamRepository,
    onAdd: (addonId: String, type: String, catalogId: String, genre: String?) -> Unit,
) {
    val enabled = addons.filter { it.enabled }
    var addonId by remember { mutableStateOf(enabled.firstOrNull()?.id) }
    var catalogList by remember(addonId) { mutableStateOf<List<com.pulsestream.app.data.CatalogRef>>(emptyList()) }
    var catalogId by remember(addonId) { mutableStateOf<String?>(null) }
    var genre by remember(catalogId) { mutableStateOf<String?>(null) }
    var addonPickerOpen by remember { mutableStateOf(false) }
    var catalogPickerOpen by remember { mutableStateOf(false) }
    var genrePickerOpen by remember { mutableStateOf(false) }

    val addon = enabled.firstOrNull { it.id == addonId }
    LaunchedEffect(addonId) {
        val baseUrl = addon?.baseUrl ?: return@LaunchedEffect
        catalogList = runCatching { repository.api.fetchManifest(baseUrl) }.getOrNull()?.catalogs ?: emptyList()
    }
    val catalog = catalogList.firstOrNull { it.id == catalogId }

    if (enabled.isEmpty()) {
        Text("No addons installed — install one first.", color = V117Muted)
        return
    }
    Row(Modifier.fillMaxWidth().clickable { addonPickerOpen = true }.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Addon", color = V117Muted, style = MaterialTheme.typography.bodySmall)
            Text(addon?.name ?: "Select an addon", color = TextPrimary)
        }
    }
    Row(Modifier.fillMaxWidth().clickable { if (catalogList.isNotEmpty()) catalogPickerOpen = true }.padding(vertical = 8.dp)) {
        Column(Modifier.weight(1f)) {
            Text("Catalog", color = V117Muted, style = MaterialTheme.typography.bodySmall)
            Text(
                catalog?.let { "${it.name} (${it.type})" } ?: if (catalogList.isEmpty()) "Loading catalogs…" else "Select a catalog",
                color = TextPrimary
            )
        }
    }
    if (catalog?.supportsGenre == true && catalog.genreOptions.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().clickable { genrePickerOpen = true }.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Genre", color = V117Muted, style = MaterialTheme.typography.bodySmall)
                Text(genre ?: "All genres", color = TextPrimary)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(
        enabled = addonId != null && catalogId != null,
        onClick = {
            val id = addonId ?: return@TextButton
            val cat = catalog ?: return@TextButton
            onAdd(id, cat.type, cat.id, genre)
        }
    ) { Text("Add source") }

    if (addonPickerOpen) {
        PickerDialog(
            title = "Addon",
            options = enabled.map { it.id to it.name },
            selected = addonId,
            onSelect = { addonPickerOpen = false; addonId = it; catalogId = null },
            onDismiss = { addonPickerOpen = false }
        )
    }
    if (catalogPickerOpen) {
        PickerDialog(
            title = "Catalog",
            options = catalogList.map { it.id to "${it.name} (${it.type})" },
            selected = catalogId,
            onSelect = { catalogPickerOpen = false; catalogId = it },
            onDismiss = { catalogPickerOpen = false }
        )
    }
    if (genrePickerOpen && catalog != null) {
        PickerDialog(
            title = "Genre",
            options = listOf("" to "All genres") + catalog.genreOptions.map { it to it },
            selected = genre ?: "",
            onSelect = { genrePickerOpen = false; genre = it.ifBlank { null } },
            onDismiss = { genrePickerOpen = false }
        )
    }
}

@Composable
private fun TmdbSourceForm(
    onAdd: (kind: String, tmdbId: Int?, mediaType: String?, sortBy: String?) -> Unit,
) {
    var kind by remember { mutableStateOf("COLLECTION") }
    var idText by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf("movie") }
    var sortBy by remember { mutableStateOf("popularity.desc") }
    var sortPickerOpen by remember { mutableStateOf(false) }

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("COLLECTION" to "Collection", "LIST" to "List", "DISCOVER" to "Discover").forEach { (value, label) ->
            FilterChip(selected = kind == value, onClick = { kind = value }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(8.dp))
    if (kind != "DISCOVER") {
        OutlinedTextField(
            value = idText,
            onValueChange = { idText = it.filter { ch -> ch.isDigit() }.take(10) },
            label = { Text(if (kind == "COLLECTION") "TMDB collection id" else "TMDB list id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("movie" to "Movies", "series" to "TV shows").forEach { (value, label) ->
            FilterChip(selected = mediaType == value, onClick = { mediaType = value }, label = { Text(label) })
        }
    }
    if (kind == "DISCOVER") {
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clickable { sortPickerOpen = true }.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Sort by", color = V117Muted, style = MaterialTheme.typography.bodySmall)
                Text(sortBy, color = TextPrimary)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    val id = idText.toIntOrNull()
    TextButton(
        enabled = kind == "DISCOVER" || id != null,
        onClick = { onAdd(kind, id, mediaType, if (kind == "DISCOVER") sortBy else null) }
    ) { Text("Add source") }

    if (sortPickerOpen) {
        PickerDialog(
            title = "Sort by",
            options = listOf(
                "popularity.desc" to "Most popular",
                "vote_average.desc" to "Highest rated",
                "vote_count.desc" to "Most voted",
                "primary_release_date.desc" to "Newest (movies)",
                "first_air_date.desc" to "Newest (TV)"
            ),
            selected = sortBy,
            onSelect = { sortPickerOpen = false; sortBy = it },
            onDismiss = { sortPickerOpen = false }
        )
    }
}

@Composable
private fun TraktSourceForm(
    repository: StreamRepository,
    onAdd: (listId: String, mediaType: String?, sortBy: String?, sortHow: String?) -> Unit,
) {
    var listId by remember { mutableStateOf("") }
    var mediaType by remember { mutableStateOf<String?>(null) }
    var sortBy by remember { mutableStateOf("rank") }
    var sortHow by remember { mutableStateOf("asc") }
    var lists by remember { mutableStateOf<List<TraktApi.TraktList>?>(null) }
    var listPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val token = runCatching { TraktManager(repository.settings).validAccessToken() }.getOrNull()
        lists = token?.let { runCatching { TraktApi().getLists(it) }.getOrNull() }
    }

    if (lists != null && lists!!.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().clickable { listPickerOpen = true }.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Trakt list", color = V117Muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    lists!!.firstOrNull { it.id == listId }?.name ?: "Select a list",
                    color = TextPrimary
                )
            }
        }
    } else {
        OutlinedTextField(
            value = listId,
            onValueChange = { listId = it.filter { ch -> ch.isDigit() }.take(12) },
            label = { Text("Trakt list id") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (lists != null) {
            Text(
                "Link Trakt in Integrations to pick from your lists.",
                color = V117Muted, style = MaterialTheme.typography.bodySmall
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(null to "All", "movie" to "Movies", "series" to "TV shows").forEach { (value, label) ->
            FilterChip(selected = mediaType == value, onClick = { mediaType = value }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("rank" to "Rank", "title" to "Title", "released" to "Released").forEach { (value, label) ->
            FilterChip(selected = sortBy == value, onClick = { sortBy = value }, label = { Text(label) })
        }
        listOf("asc" to "Ascending", "desc" to "Descending").forEach { (value, label) ->
            FilterChip(selected = sortHow == value, onClick = { sortHow = value }, label = { Text(label) })
        }
    }
    Spacer(Modifier.height(8.dp))
    TextButton(
        enabled = listId.isNotBlank(),
        onClick = { onAdd(listId, mediaType, sortBy, sortHow) }
    ) { Text("Add source") }

    if (listPickerOpen && lists != null) {
        PickerDialog(
            title = "Trakt list",
            options = lists!!.map { it.id to it.name },
            selected = listId.ifBlank { null },
            onSelect = { listPickerOpen = false; listId = it },
            onDismiss = { listPickerOpen = false }
        )
    }
}
