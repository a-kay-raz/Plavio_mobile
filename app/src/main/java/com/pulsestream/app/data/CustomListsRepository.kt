package com.pulsestream.app.data

import com.pulsestream.app.data.db.ProfileDatabase
import com.pulsestream.app.data.db.CustomList
import com.pulsestream.app.data.db.CustomListItem
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * User-created named lists, separate from the flat watchlist.
 *
 * Takes the [ProfileDatabase] directly (PulseApp exposes it as
 * `profileDatabase`) so this can live outside StreamRepository. Write
 * paths that read-then-write are serialized with a mutex, mirroring
 * StreamRepository's watchlist toggle.
 */
class CustomListsRepository(private val db: ProfileDatabase) {

    private val dao get() = db.customListDao()
    private val writeMutex = Mutex()

    /** All lists, newest first. */
    val lists: Flow<List<CustomList>> = dao.observeLists()

    // Step 47: all list items in one flow (the Saved view groups them).
    val allItems: Flow<List<CustomListItem>> = dao.observeAllItems()

    fun observeItems(listId: String): Flow<List<CustomListItem>> =
        dao.observeItems(listId)

    fun observeItemCount(listId: String): Flow<Int> =
        dao.observeItemCount(listId)

    /** Ids of the lists that currently contain [itemKey]. */
    fun observeListsContaining(itemKey: String): Flow<List<String>> =
        dao.observeListsContaining(itemKey)

    suspend fun getList(id: String): CustomList? = withContext(Dispatchers.IO) {
        dao.getList(id)
    }

    /** Creates a list and returns its id. New lists go first. */
    suspend fun createList(name: String, description: String = ""): String = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        require(trimmed.isNotEmpty()) { "List name must not be empty" }
        val id = UUID.randomUUID().toString()
        writeMutex.withLock {
            val minOrder = dao.getLists().minOfOrNull { it.sortOrder } ?: 0L
            dao.upsertList(
                CustomList(
                    id = id, name = trimmed, createdAt = System.currentTimeMillis(),
                    description = description.trim().take(200), sortOrder = minOrder - 1000
                )
            )
        }
        id
    }

    suspend fun renameList(id: String, name: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        require(trimmed.isNotEmpty()) { "List name must not be empty" }
        dao.renameList(id, trimmed)
    }

    /** Step 48: name + description edited together (the list editor dialog). */
    suspend fun updateList(id: String, name: String, description: String) = withContext(Dispatchers.IO) {
        val trimmed = name.trim().take(60)
        require(trimmed.isNotEmpty()) { "List name must not be empty" }
        writeMutex.withLock {
            dao.renameList(id, trimmed)
            dao.updateDescription(id, description.trim().take(200))
        }
    }

    suspend fun updateDescription(id: String, description: String) = withContext(Dispatchers.IO) {
        dao.updateDescription(id, description.trim().take(200))
    }

    /**
     * Step 48: move a list to a display position (drag-reorder). The list
     * is removed from the current sortOrder sequence and re-inserted at
     * [toIndex]; every list then gets sortOrder = index * 1000 so later
     * moves have room without renumbering the whole table each time.
     */
    suspend fun moveList(id: String, toIndex: Int) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val ordered = dao.getLists().sortedBy { it.sortOrder }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            if (from < 0) return@withLock
            val item = ordered.removeAt(from)
            ordered.add(toIndex.coerceIn(0, ordered.size), item)
            ordered.forEachIndexed { index, list ->
                dao.updateSortOrder(list.id, index * 1000L)
            }
        }
    }

    /** Deletes the list and all of its items. */
    suspend fun deleteList(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.deleteItemsByList(id)
            dao.deleteList(id)
        }
    }

    suspend fun addToList(
        listId: String,
        addonId: String,
        type: String,
        metaId: String,
        name: String,
        poster: String?,
        // Step 47: release year (0 = unknown) for the Library Saved sort.
        year: Int = 0
    ) = withContext(Dispatchers.IO) {
        dao.addItem(
            CustomListItem(
                listId = listId,
                itemKey = "$addonId|$type|$metaId",
                addonId = addonId,
                type = type,
                metaId = metaId,
                name = name,
                poster = poster,
                addedAt = System.currentTimeMillis(),
                year = year
            )
        )
    }

    suspend fun removeFromList(listId: String, itemKey: String) =
        withContext(Dispatchers.IO) {
            dao.removeItem(listId, itemKey)
        }

    /** Shared key format with the watchlist: "addonId|type|metaId". */
    fun itemKey(addonId: String, type: String, metaId: String): String =
        "$addonId|$type|$metaId"
}
