package com.pulsestream.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "addons")
data class InstalledAddon(
    @PrimaryKey val id: String,
    val name: String,
    val version: String,
    val description: String,
    val baseUrl: String,
    val types: String,
    /** Comma-separated manifest resources, e.g. "catalog,meta,stream". Added in DB v2. */
    val resources: String = "catalog,meta,stream",
    /** True when this addon was installed automatically by the app (Cinemeta, OpenSubtitles). Added in DB v3. */
    val preinstalled: Boolean = false,
    /** User-facing on/off switch. Disabled addons are excluded from catalogs, metadata, streams and subtitle search. Added in DB v4. */
    val enabled: Boolean = true,
    /** Manifest behaviorHints.configurable — the addon has a web configure page. Added in DB v4. */
    val configurable: Boolean = false,
    /** Manifest behaviorHints.configurationRequired — must be configured before use. Added in DB v13. */
    val configurationRequired: Boolean = false,
    /** Whether this addon's catalogs appear on the Home screen. Added in DB v5. */
    val showOnHome: Boolean = true,
    /** Manifest logo URL for the addon icon. Added in DB v6. */
    val logoUrl: String = "",
    /** Manual ordering position (user-rearranged). Added in DB v7. */
    val sortOrder: Int = 0,
    /** Starred addons get priority for catalogs, fetching and playback. Added in DB v7. */
    val starred: Boolean = false,
    val addedAt: Long
) {
    val resourceSet: Set<String>
        get() = resources.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    val hasCatalog: Boolean get() = "catalog" in resourceSet
    val hasMeta: Boolean get() = "meta" in resourceSet
    val hasStream: Boolean get() = "stream" in resourceSet
    val hasSubtitles: Boolean get() = "subtitles" in resourceSet
}

@Dao
interface AddonDao {
    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, addedAt ASC")
    fun observeAll(): Flow<List<InstalledAddon>>

    @Query("SELECT * FROM addons ORDER BY sortOrder ASC, addedAt ASC")
    suspend fun getAll(): List<InstalledAddon>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(addon: InstalledAddon)

    @Query("UPDATE addons SET showOnHome = :show WHERE id = :id")
    suspend fun setShowOnHome(id: String, show: Boolean)

    @Query("DELETE FROM addons WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM addons")
    suspend fun clearAll()
}

@Entity(tableName = "playback")
data class PlaybackState(
    @PrimaryKey val key: String,
    val title: String,
    val poster: String?,
    val videoId: String,
    val streamUrl: String,
    val positionMs: Long,
    val durationMs: Long,
    /** IMDb id + season/episode so Continue Watching can offer skip-segments and subtitle search. Added in DB v3. */
    val imdbId: String? = null,
    val season: Int = 0,
    val episode: Int = 0,
    val updatedAt: Long
)

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM playback ORDER BY updatedAt DESC LIMIT 20")
    fun observeRecent(): Flow<List<PlaybackState>>

    @Query("SELECT * FROM playback WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): PlaybackState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: PlaybackState)

    @Query("DELETE FROM playback WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM playback")
    suspend fun getAll(): List<PlaybackState>

    @Query("DELETE FROM playback")
    suspend fun clearAll()
}

/** Cached catalog rows (per addon + type + catalog id) so Home renders instantly. Added in DB v3. */
@Entity(tableName = "catalog_cache")
data class CatalogCache(
    @PrimaryKey val key: String,
    val addonId: String,
    val addonName: String,
    val baseUrl: String,
    val type: String,
    val catalogId: String,
    val catalogName: String,
    val itemsJson: String,
    val fetchedAt: Long
)

@Dao
interface CatalogCacheDao {
    @Query("SELECT * FROM catalog_cache")
    suspend fun getAll(): List<CatalogCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<CatalogCache>)

    @Query("DELETE FROM catalog_cache WHERE addonId NOT IN (:ids)")
    suspend fun pruneExcept(ids: List<String>)

    @Query("DELETE FROM catalog_cache")
    suspend fun clearAll()

    @Query("DELETE FROM catalog_cache WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM catalog_cache WHERE addonId = :addonId")
    suspend fun deleteByAddonId(addonId: String)

    /**
     * Drops rows whose JSON exceeds SQLite's ~2MB CursorWindow limit —
     * reading one would crash with SQLiteBlobTooBigException.
     */
    @Query("DELETE FROM catalog_cache WHERE length(CAST(itemsJson AS BLOB)) > :maxBytes")
    suspend fun pruneOversized(maxBytes: Int): Int

    /**
     * Drops every cached M3U row of one playlist (exact stored baseUrl).
     * Callers pass both the raw and the normalized URL to cover playlists
     * stored by older builds.
     */
    @Query("DELETE FROM catalog_cache WHERE addonId = 'm3u' AND baseUrl = :baseUrl")
    suspend fun deleteM3uPlaylist(baseUrl: String)
}

/** User's watchlist — titles bookmarked for later. Added in DB v3. */
@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey val key: String,
    val addonId: String,
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val addedAt: Long,
    // Step 47: release year (0 = unknown) for the Library Saved sort.
    val year: Int = 0
)

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<WatchlistItem>>

    @Query("SELECT * FROM watchlist WHERE `key` = :key LIMIT 1")
    fun observeByKey(key: String): Flow<WatchlistItem?>

    @Query("SELECT * FROM watchlist WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): WatchlistItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: WatchlistItem)

    @Query("DELETE FROM watchlist WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM watchlist")
    suspend fun getAll(): List<WatchlistItem>

    @Query("DELETE FROM watchlist")
    suspend fun clearAll()
}

/** User-created custom lists — named collections beyond the flat watchlist. Added in DB v9. */
@Entity(tableName = "custom_lists")
data class CustomList(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    // Step 48: optional description + manual order (display = sortOrder ASC).
    val description: String = "",
    val sortOrder: Long = 0
)

/**
 * Items inside a custom list. itemKey mirrors the watchlist key format
 * ("addonId|type|metaId"). addonId is stored (not in the original spec) so
 * tapping an item can navigate back to its detail screen.
 * Added in DB v9.
 */
@Entity(
    tableName = "custom_list_items",
    primaryKeys = ["listId", "itemKey"]
)
data class CustomListItem(
    val listId: String,
    val itemKey: String,
    val addonId: String,
    val type: String,
    val metaId: String,
    val name: String,
    val poster: String?,
    val addedAt: Long,
    // Step 47: release year (0 = unknown) for the Library Saved sort.
    val year: Int = 0
)

@Dao
interface CustomListDao {
    // Step 48: display order is sortOrder ASC (backfilled from -createdAt,
    // so the previous newest-first order is preserved).
    @Query("SELECT * FROM custom_lists ORDER BY sortOrder ASC")
    fun observeLists(): Flow<List<CustomList>>

    // Step 47: all list items in one flow (the Saved view groups them).
    @Query("SELECT * FROM custom_list_items ORDER BY addedAt DESC")
    fun observeAllItems(): Flow<List<CustomListItem>>

    @Query("SELECT * FROM custom_lists ORDER BY sortOrder ASC")
    suspend fun getLists(): List<CustomList>

    @Query("SELECT * FROM custom_lists WHERE id = :id LIMIT 1")
    suspend fun getList(id: String): CustomList?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertList(list: CustomList)

    @Query("UPDATE custom_lists SET name = :name WHERE id = :id")
    suspend fun renameList(id: String, name: String)

    // Step 48: description edit + manual reorder.
    @Query("UPDATE custom_lists SET description = :description WHERE id = :id")
    suspend fun updateDescription(id: String, description: String)

    @Query("UPDATE custom_lists SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: String, sortOrder: Long)

    @Query("DELETE FROM custom_lists WHERE id = :id")
    suspend fun deleteList(id: String)

    @Query("DELETE FROM custom_lists")
    suspend fun clearLists()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLists(lists: List<CustomList>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<CustomListItem>)

    @Query("SELECT * FROM custom_list_items ORDER BY addedAt DESC")
    suspend fun getAllItems(): List<CustomListItem>

    @Query("DELETE FROM custom_list_items")
    suspend fun clearAllItems()

    @Query("SELECT * FROM custom_list_items WHERE listId = :listId ORDER BY addedAt DESC")
    fun observeItems(listId: String): Flow<List<CustomListItem>>

    @Query("SELECT listId FROM custom_list_items WHERE itemKey = :itemKey")
    fun observeListsContaining(itemKey: String): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addItem(item: CustomListItem)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId AND itemKey = :itemKey")
    suspend fun removeItem(listId: String, itemKey: String)

    @Query("DELETE FROM custom_list_items WHERE listId = :listId")
    suspend fun deleteItemsByList(listId: String)

    @Query("SELECT COUNT(*) FROM custom_list_items WHERE listId = :listId")
    fun observeItemCount(listId: String): Flow<Int>
}

/** Briefly-cached subtitle search results (per type + IMDb id). Added in DB v3. */
@Entity(tableName = "subtitle_cache")
data class SubtitleCache(
    @PrimaryKey val key: String,
    val itemsJson: String,
    val fetchedAt: Long
)

@Dao
interface SubtitleCacheDao {
    @Query("DELETE FROM subtitle_cache")
    suspend fun clearAll()
    @Query("SELECT * FROM subtitle_cache WHERE `key` = :key LIMIT 1")
    suspend fun getByKey(key: String): SubtitleCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SubtitleCache)
}

/** Offline download tracking. Added in DB v8. */
@Entity(tableName = "downloads")
data class DownloadItem(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val fileName: String,
    val filePath: String,
    val totalBytes: Long = -1L,
    val downloadedBytes: Long = 0L,
    /** QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED, CANCELLED */
    val status: String = "QUEUED",
    val error: String? = null,
    val headersJson: String = "{}",
    val createdAt: Long,
    val completedAt: Long? = null,
    // Step 36: per-show grouping — parent meta id for series episodes
    // ("" = movie / ungrouped single), display name for the group header,
    // and the episode position inside the show.
    val showKey: String = "",
    val seriesName: String = "",
    val season: Int = 0,
    val episode: Int = 0,
    // Step 46: subtitle URLs captured at enqueue time (org.json array of
    // {"url","lang"}) — fetched into sidecars after the download completes.
    val subtitlesJson: String = ""
)

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    suspend fun getAll(): List<DownloadItem>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadItem?

    @Query("SELECT * FROM downloads WHERE url = :url AND status != 'CANCELLED' LIMIT 1")
    suspend fun getByUrl(url: String): DownloadItem?

    // Step 46: offline playback looks a download up by its file path to
    // attach the subtitle sidecars + cached poster.
    @Query("SELECT * FROM downloads WHERE filePath = :filePath LIMIT 1")
    suspend fun getByFilePath(filePath: String): DownloadItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadItem)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, status = :status WHERE id = :id")
    suspend fun updateProgress(id: String, bytes: Long, status: String)

    @Query("UPDATE downloads SET status = :status, error = :error WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, error: String? = null)

    @Query("UPDATE downloads SET status = 'COMPLETED', downloadedBytes = totalBytes, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long)

    // Step 37: after a move into the custom (SAF) folder, the file lives at a content URI.
    @Query("UPDATE downloads SET filePath = :filePath WHERE id = :id")
    suspend fun updateFilePath(id: String, filePath: String)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAt ASC")
    suspend fun getActive(): List<DownloadItem>
}

/** One row per watched movie / episode (drives the WATCHED pill). Added in DB v12. */
@Entity(tableName = "watched")
data class WatchedRecord(
    @PrimaryKey val key: String,
    val watchedAt: Long
)

@Dao
interface WatchedDao {
    @Query("SELECT `key` FROM watched")
    fun observeKeys(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: WatchedRecord)

    @Query("SELECT * FROM watched ORDER BY watchedAt DESC")
    suspend fun getAll(): List<WatchedRecord>

    @Query("DELETE FROM watched WHERE `key` = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM watched WHERE `key` LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)

    @Query("DELETE FROM watched")
    suspend fun clearAll()
}

/**
 * Step 50: collections (Nuvio collection/CollectionModels.kt, scoped port).
 * A Collection holds folders; each folder holds sources that resolve to
 * titles — an addon catalog, a TMDB collection/list/discover, or a Trakt
 * list. Unlike custom lists (manual title buckets), a collection folder
 * is dynamic: its titles come from its sources.
 */
@Entity(tableName = "collections")
data class Collection(
    @PrimaryKey val id: String,
    val title: String,
    val backdropUrl: String? = null,
    val pinToTop: Boolean = false,
    /** TABBED_GRID | ROWS — how the folder renders (Step 51). */
    val viewMode: String = "TABBED_GRID",
    val showAllTab: Boolean = true,
    val sortOrder: Long = 0,
    val createdAt: Long = 0
)

@Entity(tableName = "collection_folders")
data class CollectionFolder(
    @PrimaryKey val id: String,
    val collectionId: String,
    val title: String,
    val coverImageUrl: String? = null,
    val coverEmoji: String? = null,
    /** poster | landscape | square. */
    val tileShape: String = "poster",
    val sortOrder: Long = 0
)

/** One source inside a folder. provider: "addon" | "tmdb" | "trakt". */
@Entity(tableName = "collection_sources")
data class CollectionSourceRow(
    @PrimaryKey val id: String,
    val folderId: String,
    val provider: String,
    val addonId: String? = null,
    val type: String? = null,
    val catalogId: String? = null,
    val genre: String? = null,
    /** TMDB: COLLECTION | LIST | DISCOVER. */
    val tmdbSourceType: String? = null,
    val tmdbId: Int? = null,
    /** movie | series (TMDB + Trakt). */
    val mediaType: String? = null,
    /** TMDB discover sort (popularity.desc …) or Trakt list sort (rank …). */
    val sortBy: String? = null,
    /** Trakt: asc | desc. */
    val sortHow: String? = null,
    val sortOrder: Long = 0
)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY sortOrder ASC")
    fun observeCollections(): Flow<List<Collection>>

    @Query("SELECT * FROM collections ORDER BY sortOrder ASC")
    suspend fun getCollections(): List<Collection>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getCollection(id: String): Collection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollection(collection: Collection)

    @Query("DELETE FROM collections WHERE id = :id")
    suspend fun deleteCollection(id: String)

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    fun observeFolders(collectionId: String): Flow<List<CollectionFolder>>

    @Query("SELECT * FROM collection_folders WHERE collectionId = :collectionId ORDER BY sortOrder ASC")
    suspend fun getFolders(collectionId: String): List<CollectionFolder>

    @Query("SELECT * FROM collection_folders WHERE id = :id LIMIT 1")
    suspend fun getFolder(id: String): CollectionFolder?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolder(folder: CollectionFolder)

    @Query("DELETE FROM collection_folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("DELETE FROM collection_folders WHERE collectionId = :collectionId")
    suspend fun deleteFoldersByCollection(collectionId: String)

    @Query("SELECT * FROM collection_sources WHERE folderId = :folderId ORDER BY sortOrder ASC")
    fun observeSources(folderId: String): Flow<List<CollectionSourceRow>>

    @Query("SELECT * FROM collection_sources WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getSources(folderId: String): List<CollectionSourceRow>

    @Query("SELECT * FROM collection_sources WHERE id = :id LIMIT 1")
    suspend fun getSource(id: String): CollectionSourceRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: CollectionSourceRow)

    @Query("DELETE FROM collection_sources WHERE id = :id")
    suspend fun deleteSource(id: String)

    @Query("DELETE FROM collection_sources WHERE folderId = :folderId")
    suspend fun deleteSourcesByFolder(folderId: String)
}
