package com.aviostream.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        InstalledAddon::class,
        PlaybackState::class,
        CatalogCache::class,
        WatchlistItem::class,
        SubtitleCache::class,
        DownloadItem::class,
        CustomList::class,
        CustomListItem::class,
        WatchedRecord::class,
        TitleStatusItem::class,
        Collection::class,
        CollectionFolder::class,
        CollectionSourceRow::class,
        Profile::class
    ],
    version = 20,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun addonDao(): AddonDao
    abstract fun playbackDao(): PlaybackDao
    abstract fun catalogCacheDao(): CatalogCacheDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun subtitleCacheDao(): SubtitleCacheDao
    abstract fun downloadDao(): DownloadDao
    abstract fun customListDao(): CustomListDao
    abstract fun watchedDao(): WatchedDao
    abstract fun titleStatusDao(): TitleStatusDao
    abstract fun collectionDao(): CollectionDao
    abstract fun profileDao(): ProfileDao
}

/**
 * v1 → v2: add the `resources` column to the addons table so each addon
 * remembers which Stremio resources it provides (catalog / meta / stream).
 * v0.1.0 only accepted full addons, so the default is correct for existing rows.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE addons ADD COLUMN resources TEXT NOT NULL DEFAULT 'catalog,meta,stream'"
        )
    }
}

/**
 * v2 → v3: new tables for catalog caching, the watchlist, and subtitle
 * search caching; a `preinstalled` flag on addons; IMDb/season/episode
 * columns on playback rows so Continue Watching keeps full context.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS catalog_cache (
                `key` TEXT NOT NULL PRIMARY KEY,
                addonId TEXT NOT NULL,
                addonName TEXT NOT NULL,
                baseUrl TEXT NOT NULL,
                type TEXT NOT NULL,
                catalogId TEXT NOT NULL,
                catalogName TEXT NOT NULL,
                itemsJson TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS watchlist (
                `key` TEXT NOT NULL PRIMARY KEY,
                addonId TEXT NOT NULL,
                type TEXT NOT NULL,
                metaId TEXT NOT NULL,
                name TEXT NOT NULL,
                poster TEXT,
                addedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS subtitle_cache (
                `key` TEXT NOT NULL PRIMARY KEY,
                itemsJson TEXT NOT NULL,
                fetchedAt INTEGER NOT NULL
            )"""
        )
        db.execSQL("ALTER TABLE addons ADD COLUMN preinstalled INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playback ADD COLUMN imdbId TEXT")
        db.execSQL("ALTER TABLE playback ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE playback ADD COLUMN episode INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v3 → v4: per-addon `enabled` flag (user on/off switch) and `configurable`
 * flag (manifest behaviorHints — the addon ships a web configure page).
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN enabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE addons ADD COLUMN configurable INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v4 → v5: per-addon `showOnHome` flag — controls whether the addon's
 * catalogs appear on the Home screen (Settings > Home Catalogs).
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN showOnHome INTEGER NOT NULL DEFAULT 1")
    }
}

/**
 * v5 → v6: `logoUrl` on addons — the manifest logo, shown as the addon
 * icon in the redesigned Addons tab.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN logoUrl TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * v6 → v7: `sortOrder` (user-rearranged addon order) and `starred`
 * (priority addon flag) on addons.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE addons ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE addons ADD COLUMN starred INTEGER NOT NULL DEFAULT 0")
    }
}

/**
 * v7 → v8: new `downloads` table for the offline downloader.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS downloads (
                id TEXT NOT NULL PRIMARY KEY,
                url TEXT NOT NULL,
                title TEXT NOT NULL,
                fileName TEXT NOT NULL,
                filePath TEXT NOT NULL,
                totalBytes INTEGER NOT NULL DEFAULT -1,
                downloadedBytes INTEGER NOT NULL DEFAULT 0,
                status TEXT NOT NULL DEFAULT 'QUEUED',
                error TEXT,
                headersJson TEXT NOT NULL DEFAULT '{}',
                createdAt INTEGER NOT NULL,
                completedAt INTEGER
            )"""
        )
    }
}

/** Creates Room's exact custom-list schema and copies any recoverable legacy rows. */
private fun rebuildCustomListTables(db: SupportSQLiteDatabase) {
    fun columns(table: String): Set<String> {
        if (db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'").use { it.count == 0 }) return emptySet()
        return db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet { while (cursor.moveToNext()) add(cursor.getString(cursor.getColumnIndexOrThrow("name"))) }
        }
    }
    fun copyTable(table: String, required: List<String>, defaults: Map<String, String>) {
        val existing = columns(table)
        val backup = "${table}_migration_backup"
        db.execSQL("DROP TABLE IF EXISTS `$backup`")
        if (existing.isNotEmpty()) db.execSQL("ALTER TABLE `$table` RENAME TO `$backup`")
        when (table) {
            "custom_lists" -> db.execSQL("CREATE TABLE `custom_lists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
            "custom_list_items" -> db.execSQL("CREATE TABLE `custom_list_items` (`listId` TEXT NOT NULL, `itemKey` TEXT NOT NULL, `addonId` TEXT NOT NULL, `type` TEXT NOT NULL, `metaId` TEXT NOT NULL, `name` TEXT NOT NULL, `poster` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`listId`, `itemKey`))")
        }
        if (existing.isNotEmpty()) {
            val usable = required.filter { it in existing || it in defaults }
            if (required.take(2).all { it in usable }) {
                val expr = usable.joinToString(",") { col -> if (col in existing) "`$col`" else defaults.getValue(col) }
                val cols = usable.joinToString(",") { "`$it`" }
                db.execSQL("INSERT OR IGNORE INTO `$table` ($cols) SELECT $expr FROM `$backup`")
            }
            db.execSQL("DROP TABLE IF EXISTS `$backup`")
        }
    }
    copyTable("custom_lists", listOf("id", "name", "createdAt"), mapOf("name" to "''", "createdAt" to "0"))
    copyTable("custom_list_items", listOf("listId", "itemKey", "addonId", "type", "metaId", "name", "poster", "addedAt"), mapOf("addonId" to "''", "type" to "''", "metaId" to "''", "name" to "''", "poster" to "NULL", "addedAt" to "0"))
}

/** v8 → v9: introduce custom lists without discarding any pre-existing recoverable tables. */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) = rebuildCustomListTables(db)
}

/** v9 → v10: normalize custom-list schema while preserving every compatible row. */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) = rebuildCustomListTables(db)
}

/** v10 → v11: normalize Room's exact schema and retain user list contents. */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) = rebuildCustomListTables(db)
}

/** v11 → v12: watched-history table for the WATCHED pill (v0.12.26). */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS watched (`key` TEXT NOT NULL, watchedAt INTEGER NOT NULL, PRIMARY KEY(`key`))")
    }
}

/** v12 → v13: `configurationRequired` flag on addons (behaviorHints.configurationRequired) so the Configure action also shows for addons that must be configured. */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE addons ADD COLUMN configurationRequired INTEGER NOT NULL DEFAULT 0"
        )
    }
}

/** v13 → v14 (Step 36): per-show download grouping — parent meta id, series name, season/episode on downloads. Existing rows stay ungrouped (empty showKey). */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN showKey TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE downloads ADD COLUMN seriesName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE downloads ADD COLUMN season INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE downloads ADD COLUMN episode INTEGER NOT NULL DEFAULT 0")
    }
}

/** v14 → v15 (Step 46): subtitle URLs captured at enqueue time on downloads (fetched into sidecars after completion). */
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloads ADD COLUMN subtitlesJson TEXT NOT NULL DEFAULT ''")
    }
}

/** v15 → v16 (Step 47): release year on watchlist + custom list items (Library Saved sort). Existing rows stay unknown (0). */
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE watchlist ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE custom_list_items ADD COLUMN year INTEGER NOT NULL DEFAULT 0")
    }
}

/** v16 → v17 (Step 48): description + manual order on custom lists. sortOrder is backfilled from -createdAt so the previous newest-first order is preserved. */
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE custom_lists ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE custom_lists ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE custom_lists SET sortOrder = -createdAt")
    }
}

/** v17 → v18 (Step 50): collections — a collection holds folders, each folder holds sources (addon catalog / TMDB / Trakt) that resolve to titles. */
val MIGRATION_17_18 = object : Migration(17, 18) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS collections (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                backdropUrl TEXT,
                pinToTop INTEGER NOT NULL DEFAULT 0,
                viewMode TEXT NOT NULL DEFAULT 'TABBED_GRID',
                showAllTab INTEGER NOT NULL DEFAULT 1,
                sortOrder INTEGER NOT NULL DEFAULT 0,
                createdAt INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS collection_folders (
                id TEXT NOT NULL PRIMARY KEY,
                collectionId TEXT NOT NULL,
                title TEXT NOT NULL,
                coverImageUrl TEXT,
                coverEmoji TEXT,
                tileShape TEXT NOT NULL DEFAULT 'poster',
                sortOrder INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS collection_sources (
                id TEXT NOT NULL PRIMARY KEY,
                folderId TEXT NOT NULL,
                provider TEXT NOT NULL,
                addonId TEXT,
                type TEXT,
                catalogId TEXT,
                genre TEXT,
                tmdbSourceType TEXT,
                tmdbId INTEGER,
                mediaType TEXT,
                sortBy TEXT,
                sortHow TEXT,
                sortOrder INTEGER NOT NULL DEFAULT 0
            )"""
        )
    }
}

/** v18 → v19 (Step 56): profiles table in the global database. The
 * per-profile library tables are NOT moved here — each profile gets
 * its own database file (ProfileDatabase), seeded once from the old
 * global rows by ProfileStore.migrateLegacyData. */
val MIGRATION_18_19 = object : Migration(18, 19) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS profiles (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                avatarColorHex TEXT NOT NULL,
                avatar TEXT NOT NULL,
                pinHash TEXT,
                pinSalt TEXT,
                createdAt INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL
            )"""
        )
    }
}

/** v19 → v20 (v0.14.30 port, #43): the `title_statuses` table for
 * Avio-native library statuses (WATCHING / HOLD / COMPLETED /
 * DROPPED). NEXT_TO_WATCH has no row — it is backed by the watchlist
 * table. Starts empty; existing watchlist rows already read as
 * Next to Watch, so no data copy is needed. */
val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS title_statuses (
                `key` TEXT NOT NULL PRIMARY KEY,
                addonId TEXT NOT NULL,
                type TEXT NOT NULL,
                metaId TEXT NOT NULL,
                name TEXT NOT NULL,
                poster TEXT,
                status TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )"""
        )
    }
}
