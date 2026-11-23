package com.aviostream.app.data

import com.aviostream.app.data.db.ProfileDatabase
import com.aviostream.app.data.db.Collection
import com.aviostream.app.data.db.CollectionFolder
import com.aviostream.app.data.db.CollectionSourceRow
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Step 50: collections store (Nuvio collection/CollectionRepository.kt,
 * scoped port onto Room). A Collection holds folders; each folder holds
 * sources that resolve to titles. Writes that read-then-write are
 * serialized with a mutex, mirroring CustomListsRepository.
 */
class CollectionRepository(private val db: ProfileDatabase) {

    private val dao get() = db.collectionDao()
    private val writeMutex = Mutex()

    val collections: Flow<List<Collection>> = dao.observeCollections()

    fun observeFolders(collectionId: String): Flow<List<CollectionFolder>> =
        dao.observeFolders(collectionId)

    fun observeSources(folderId: String): Flow<List<CollectionSourceRow>> =
        dao.observeSources(folderId)

    suspend fun getCollection(id: String): Collection? = withContext(Dispatchers.IO) {
        dao.getCollection(id)
    }

    suspend fun getFolder(id: String): CollectionFolder? = withContext(Dispatchers.IO) {
        dao.getFolder(id)
    }

    suspend fun getFolders(collectionId: String): List<CollectionFolder> =
        withContext(Dispatchers.IO) { dao.getFolders(collectionId) }

    suspend fun getSources(folderId: String): List<CollectionSourceRow> =
        withContext(Dispatchers.IO) { dao.getSources(folderId) }

    /** Creates a collection (last in order) and returns its id. */
    suspend fun createCollection(title: String): String = withContext(Dispatchers.IO) {
        val trimmed = title.trim().take(80)
        require(trimmed.isNotEmpty()) { "Collection title must not be empty" }
        val id = UUID.randomUUID().toString()
        writeMutex.withLock {
            val maxOrder = dao.getCollections().maxOfOrNull { it.sortOrder } ?: 0L
            dao.upsertCollection(
                Collection(
                    id = id, title = trimmed,
                    sortOrder = maxOrder + 1000, createdAt = System.currentTimeMillis()
                )
            )
        }
        id
    }

    suspend fun updateCollection(collection: Collection) = withContext(Dispatchers.IO) {
        dao.upsertCollection(collection)
    }

    /** Deletes the collection and all of its folders and sources. */
    suspend fun deleteCollection(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val folders = dao.getFolders(id)
            folders.forEach { folder -> dao.deleteSourcesByFolder(folder.id) }
            dao.deleteFoldersByCollection(id)
            dao.deleteCollection(id)
        }
    }

    suspend fun moveCollection(id: String, toIndex: Int) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val ordered = dao.getCollections().sortedBy { it.sortOrder }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            if (from < 0) return@withLock
            val item = ordered.removeAt(from)
            ordered.add(toIndex.coerceIn(0, ordered.size), item)
            ordered.forEachIndexed { index, collection ->
                dao.upsertCollection(collection.copy(sortOrder = index * 1000L))
            }
        }
    }

    suspend fun createFolder(collectionId: String, title: String): String = withContext(Dispatchers.IO) {
        val trimmed = title.trim().take(80)
        require(trimmed.isNotEmpty()) { "Folder title must not be empty" }
        val id = UUID.randomUUID().toString()
        writeMutex.withLock {
            val maxOrder = dao.getFolders(collectionId).maxOfOrNull { it.sortOrder } ?: 0L
            dao.upsertFolder(
                CollectionFolder(
                    id = id, collectionId = collectionId, title = trimmed,
                    sortOrder = maxOrder + 1000
                )
            )
        }
        id
    }

    suspend fun updateFolder(folder: CollectionFolder) = withContext(Dispatchers.IO) {
        dao.upsertFolder(folder)
    }

    /** Deletes the folder and all of its sources. */
    suspend fun deleteFolder(id: String) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            dao.deleteSourcesByFolder(id)
            dao.deleteFolder(id)
        }
    }

    suspend fun moveFolder(id: String, toIndex: Int) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val folder = dao.getFolder(id) ?: return@withLock
            val ordered = dao.getFolders(folder.collectionId).sortedBy { it.sortOrder }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            if (from < 0) return@withLock
            val item = ordered.removeAt(from)
            ordered.add(toIndex.coerceIn(0, ordered.size), item)
            ordered.forEachIndexed { index, entry ->
                dao.upsertFolder(entry.copy(sortOrder = index * 1000L))
            }
        }
    }

    /** Adds a source to a folder (last in order) and returns its id. */
    suspend fun addSource(
        folderId: String,
        provider: String,
        addonId: String? = null,
        type: String? = null,
        catalogId: String? = null,
        genre: String? = null,
        tmdbSourceType: String? = null,
        tmdbId: Int? = null,
        mediaType: String? = null,
        sortBy: String? = null,
        sortHow: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        writeMutex.withLock {
            val maxOrder = dao.getSources(folderId).maxOfOrNull { it.sortOrder } ?: 0L
            dao.upsertSource(
                CollectionSourceRow(
                    id = id, folderId = folderId, provider = provider,
                    addonId = addonId, type = type, catalogId = catalogId, genre = genre,
                    tmdbSourceType = tmdbSourceType, tmdbId = tmdbId, mediaType = mediaType,
                    sortBy = sortBy, sortHow = sortHow, sortOrder = maxOrder + 1000
                )
            )
        }
        id
    }

    suspend fun deleteSource(id: String) = withContext(Dispatchers.IO) {
        dao.deleteSource(id)
    }

    suspend fun moveSource(id: String, toIndex: Int) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            val row = dao.getSource(id) ?: return@withLock
            val ordered = dao.getSources(row.folderId).sortedBy { it.sortOrder }.toMutableList()
            val from = ordered.indexOfFirst { it.id == id }
            if (from < 0) return@withLock
            val item = ordered.removeAt(from)
            ordered.add(toIndex.coerceIn(0, ordered.size), item)
            ordered.forEachIndexed { index, entry ->
                dao.upsertSource(entry.copy(sortOrder = index * 1000L))
            }
        }
    }
}
