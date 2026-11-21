package com.pulsestream.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Step 56: one database per profile — the profile's own library and
 * progress (watchlist, watched marks, Continue Watching playback
 * states, custom lists, collections). Device-level tables (addons,
 * catalog cache, downloads, subtitle cache, profiles) stay in the
 * global [AppDatabase]. Fresh file per profile, so version 1 needs no
 * migrations; a new profile starts empty, the first profile is seeded
 * once from the old global rows (ProfileStore.migrateLegacyData).
 */
@Database(
    entities = [
        PlaybackState::class,
        WatchlistItem::class,
        CustomList::class,
        CustomListItem::class,
        WatchedRecord::class,
        Collection::class,
        CollectionFolder::class,
        CollectionSourceRow::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun playbackDao(): PlaybackDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun customListDao(): CustomListDao
    abstract fun watchedDao(): WatchedDao
    abstract fun collectionDao(): CollectionDao
}
