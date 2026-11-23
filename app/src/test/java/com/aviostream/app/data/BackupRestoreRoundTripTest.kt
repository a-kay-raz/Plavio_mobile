package com.aviostream.app.data

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.aviostream.app.data.db.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BackupRestoreRoundTripTest {
    private fun fixture(test: suspend (AppDatabase, ProfileDatabase, SettingsStore, StreamRepository) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val profileDb = Room.inMemoryDatabaseBuilder(context, ProfileDatabase::class.java).allowMainThreadQueries().build()
        val settings = SettingsStore(context)
        val repo = StreamRepository(db, profileDb, StremioApi(), settings)
        try { test(db, profileDb, settings, repo) } finally { db.close(); profileDb.close() }
    }

    @Test fun portableBackupRestoresCustomListMembershipAndExplicitlyResetsEmptyProfile() = fixture { db, profileDb, settings, repo ->
        settings.autoplay = false
        profileDb.customListDao().upsertList(CustomList("list", "Keep this list", 1))
        profileDb.customListDao().addItem(CustomListItem("list", "entry", "addon", "movie", "tt123", "Title", null, 1))
        profileDb.watchlistDao().upsert(WatchlistItem("watch", "addon", "movie", "tt123", "Title", null, 1))
        val exported = repo.exportBackupJson().getOrThrow()
        repo.importBackupJson(repo.emptyBackupJson().getOrThrow()).getOrThrow()
        assertTrue(settings.autoplay)
        assertTrue(profileDb.customListDao().getLists().isEmpty())
        assertTrue(profileDb.watchlistDao().getAll().isEmpty())
        repo.importBackupJson(exported).getOrThrow()
        assertFalse(settings.autoplay)
        assertEquals("Keep this list", profileDb.customListDao().getLists().single().name)
        assertEquals("entry", profileDb.customListDao().getAllItems().single().itemKey)
        assertEquals("watch", profileDb.watchlistDao().getAll().single().key)
    }

    @Test fun malformedBackupDoesNotChangeExistingLibraryOrPreferences() = fixture { db, profileDb, settings, repo ->
        settings.autoplay = false
        profileDb.watchlistDao().upsert(WatchlistItem("keep", "addon", "movie", "tt123", "Keep", null, 1))
        val result = repo.importBackupJson("""{"format":"plavio-backup","version":2,"watchlist":"invalid","settings":{}}""")
        assertTrue(result.isFailure)
        assertEquals("keep", profileDb.watchlistDao().getAll().single().key)
        assertFalse(settings.autoplay)
    }

    @Test fun injectedDatabaseFailureRollsBackEarlierTableClears() = fixture { db, profileDb, settings, repo ->
        settings.autoplay = false
        profileDb.customListDao().upsertList(CustomList("old", "Existing", 1))
        profileDb.watchlistDao().upsert(WatchlistItem("keep", "addon", "movie", "tt123", "Keep", null, 1))
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_list_insert BEFORE INSERT ON custom_lists BEGIN SELECT RAISE(ABORT, 'injected failure'); END")
        val result = repo.importBackupJson("""{"format":"plavio-backup","version":2,"settings":{},"watchlist":[],"customLists":[{"id":"new","name":"New","createdAt":1}],"customListItems":[]}""")
        assertTrue(result.isFailure)
        assertEquals("keep", profileDb.watchlistDao().getAll().single().key)
        assertEquals("old", profileDb.customListDao().getLists().single().id)
        assertFalse(settings.autoplay)
    }
    @Test fun legacyCatalogCleanupMeasuresUtf8BytesRatherThanCharacterCount() = fixture { db, _, _, _ ->
        db.catalogCacheDao().upsertAll(listOf(CatalogCache("large", "addon", "Addon", "https://example.test",
            "movie", "catalog", "Catalog", "😀".repeat(400), 1)))
        assertEquals(1, db.catalogCacheDao().pruneOversized(1000))
        assertTrue(db.catalogCacheDao().getAll().isEmpty())
    }

}
