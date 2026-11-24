package com.aviostream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.AvioStatuses
import com.aviostream.app.data.CustomListsRepository
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.db.CollectionFolder
import com.aviostream.app.data.db.TitleStatusItem
import com.aviostream.app.data.db.WatchlistItem
import com.aviostream.app.data.db.CustomList
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Bottom sheet for adding the current title to custom lists.
 * Each row toggles membership; a "New list" row creates one inline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(
    listsRepository: CustomListsRepository,
    // v0.14.30 port (#43): Avio-native library statuses. Null (previews
    // and callers without a repository) hides the status picker.
    repository: StreamRepository? = null,
    addonId: String,
    type: String,
    metaId: String,
    name: String,
    poster: String?,
    // Step 47: release year (0 = unknown) for the Library Saved sort.
    year: Int = 0,
    // Step 51: folders containing this title (resolved by the caller) —
    // tap opens the folder's titles. Empty when the title is in no
    // folder, in which case this section stays hidden.
    containingFolders: List<CollectionFolder> = emptyList(),
    onOpenCollectionFolder: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val lists by listsRepository.lists.collectAsState(initial = emptyList())
    val itemKey = listsRepository.itemKey(addonId, type, metaId)
    val containing by listsRepository.observeListsContaining(itemKey)
        .collectAsState(initial = emptyList())
    // Avio statuses (v0.14.30 port, #43): explicit rows from the
    // repository; the resolved status folds in watchlist membership
    // as Next to Watch (see AvioStatuses).
    val palette = LocalPalette.current
    val watchlistItems by (repository?.watchlist?.collectAsState(initial = emptyList())
        ?: remember { mutableStateOf<List<WatchlistItem>>(emptyList()) })
    val libraryItem = watchlistItems.firstOrNull {
        it.type == type && it.metaId == metaId
    }
    val statusItems by (repository?.titleStatuses?.collectAsState(initial = emptyList<TitleStatusItem>())
        ?: remember { mutableStateOf<List<TitleStatusItem>>(emptyList()) })
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.background,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Text(
                text = "Add to list",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(12.dp))

            // ---- Avio status: five chips, single-select, tapping the
            // active chip clears it (v0.14.30 port, #43). Mirrors the
            // zip's status picker; Next to Watch is backed by the
            // watchlist, so picking it adds the title there.
            if (repository != null) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                val explicitStatus = statusItems.firstOrNull {
                    it.type == type && it.metaId == metaId
                }?.status
                val resolvedStatus = AvioStatuses.resolved(explicitStatus, libraryItem != null)
                FlowRow(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                ) {
                    AvioStatuses.ALL.forEach { status ->
                        val selected = resolvedStatus == status
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) palette.accent
                                    else Color.White.copy(alpha = 0.07f)
                                )
                                .border(
                                    1.dp,
                                    if (selected) palette.accent else Color.White.copy(alpha = 0.14f),
                                    RoundedCornerShape(50)
                                )
                                .clickable {
                                    scope.launch {
                                        repository.setAvioStatus(
                                            addonId, type, metaId, name, poster,
                                            if (selected) null else status,
                                            year
                                        )
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = AvioStatuses.label(status),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.accentText else TextPrimary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (lists.isEmpty() && !creating) {
                Text(
                    text = "You don't have any lists yet — create your first one below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }

            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(lists, key = { it.id }) { list ->
                    ListToggleRow(
                        list = list,
                        listsRepository = listsRepository,
                        checked = list.id in containing,
                        onToggle = { isNowIn ->
                            scope.launch {
                                if (isNowIn) {
                                    listsRepository.addToList(
                                        list.id, addonId, type, metaId, name, poster, year
                                    )
                                } else {
                                    listsRepository.removeFromList(list.id, itemKey)
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // Step 51: collections — the folders this title already
            // belongs to. Tap one to open its titles as a full grid
            // (same destination Home rows open).
            if (containingFolders.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Collections",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                containingFolders.forEach { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCollectionFolder(folder.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = folder.title,
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "Open folder",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            if (creating) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { if (it.length <= 60) newName = it },
                        placeholder = { Text("List name", color = TextSecondary) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            scope.launch {
                                val id = listsRepository.createList(newName)
                                listsRepository.addToList(
                                    id, addonId, type, metaId, name, poster, year
                                )
                                newName = ""
                                creating = false
                            }
                        },
                        enabled = newName.trim().isNotEmpty()
                    ) { Text("Create") }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { creating = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "New list",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = LocalPalette.current.accent
                    )
                }
            }
        }
    }
}

@Composable
private fun ListToggleRow(
    list: CustomList,
    listsRepository: CustomListsRepository,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val count by listsRepository.observeItemCount(list.id).collectAsState(initial = 0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(14.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.FormatListBulleted,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = list.name,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = "$count ${if (count == 1) "title" else "titles"}",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "In list",
                tint = LocalPalette.current.accent,
                modifier = Modifier.size(22.dp)
            )
        } else {
            // Keep alignment consistent with the checked state.
            Spacer(Modifier.size(22.dp))
        }
    }
}
