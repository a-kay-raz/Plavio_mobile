package com.aviostream.app

import android.app.Application
import android.util.Log
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.aviostream.app.data.AppIcon
import com.aviostream.app.data.BehaviourReportSupport
import com.aviostream.app.data.ExitDeathReporter
import com.aviostream.app.data.ProfileStore
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StremioApi
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.db.AppDatabase
import com.aviostream.app.data.db.MIGRATION_18_19
import com.aviostream.app.data.db.MIGRATION_19_20
import com.aviostream.app.data.db.ProfileDatabase
import com.aviostream.app.data.db.MIGRATION_1_2
import com.aviostream.app.data.db.MIGRATION_2_3
import com.aviostream.app.data.db.MIGRATION_3_4
import com.aviostream.app.data.db.MIGRATION_4_5
import com.aviostream.app.data.db.MIGRATION_5_6
import com.aviostream.app.data.db.MIGRATION_6_7
import com.aviostream.app.data.db.MIGRATION_7_8
import com.aviostream.app.data.db.MIGRATION_8_9
import com.aviostream.app.data.db.MIGRATION_9_10
import com.aviostream.app.data.db.MIGRATION_10_11
import com.aviostream.app.data.db.MIGRATION_11_12
import com.aviostream.app.data.db.MIGRATION_12_13
import com.aviostream.app.data.db.MIGRATION_13_14
import com.aviostream.app.data.db.MIGRATION_14_15
import com.aviostream.app.data.db.MIGRATION_15_16
import com.aviostream.app.data.db.MIGRATION_16_17
import com.aviostream.app.data.db.MIGRATION_17_18
import com.aviostream.app.ui.tv.isTelevision
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AvioApp : Application(), ImageLoaderFactory {

    /**
     * TV-tuned image pipeline: on memory-constrained TVs (48MB heap class
     * seen in the field) Coil's default ~25% memory cache plus hundreds of
     * posters causes lag and OOMs. Cap it to 8% on TV; phones keep default.
     */
    override fun newImageLoader(): ImageLoader {
        val lowMem = isTelevision()
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lowMem) 0.08 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            // Downscale posters on TV: cards are ~128dp wide, no need for full-res.
            .respectCacheHeaders(false)
            .build()
    }

    lateinit var repository: StreamRepository
        private set

    lateinit var settings: SettingsStore
        private set

    lateinit var database: AppDatabase
        private set

    /** Step 56: the active profile's database (library/progress). */
    lateinit var profileDatabase: ProfileDatabase
        private set

    /**
     * A crashing background task must never kill the app (previously an
     * uncaught exception here meant an instant force-close loop that also
     * bricked the in-app updater). Failures are logged instead.
     */
    private val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e ->
            Log.e("AvioApp", "Background startup task failed", e)
        }
    )

    /**
     * P4: cold start no longer blocks the first frame on the
     * database open, the profile migration, the settings parses
     * or the launcher-icon re-apply — all of that runs on the IO
     * dispatcher in [initializeAsync]. MainActivity shows a plain
     * frame until [startupReady] flips, then the splash runs its
     * full 2.15 s floor (owner-locked, unchanged). The migration
     * still completes before anything uses profiles: the splash
     * waits for the feed, and the feed needs the repository,
     * which is only built after the migration.
     */
    private val startupReadyFlow = MutableStateFlow(false)
    val startupReady: StateFlow<Boolean> = startupReadyFlow.asStateFlow()

    override fun onCreate() {
        installCrashCatcher()
        super.onCreate()
        com.aviostream.app.data.MotionPreferences.initialize(this)
        appScope.launch { initializeAsync() }
    }

    /** Heavy startup, off the main thread (see [startupReady]). */
    private suspend fun initializeAsync() {
        val db = openDatabase()
        database = db
        // Step 56: the single identity becomes the "Main" profile on
        // first launch (one-time, guarded), then the active profile's
        // database + settings file open. The suspend call keeps the
        // ordering: nothing below may run before the migration has
        // seeded the profile database.
        val legacySettings = SettingsStore(this)
        // T7: a migration failure must never throw in onCreate on
        // every launch — log it and fall back to a default profile
        // (or the first existing one) instead of crashing.
        runCatching {
            ProfileStore.migrateLegacyData(this@AvioApp, db, legacySettings)
        }.onFailure {
            Log.e("AvioApp", "Profile migration failed; falling back to a default profile", it)
        }
        val activeProfileId = runCatching {
            ProfileStore.ensureActiveProfile(this@AvioApp, db, legacySettings)
        }.onFailure {
            Log.e("AvioApp", "Could not resolve an active profile", it)
        }.getOrNull()
            ?: error("Profiles migration did not set an active profile")
        profileDatabase = ProfileStore.openProfileDatabase(this, activeProfileId)
        settings = SettingsStore(this, ProfileStore.profilePrefsName(activeProfileId))
        // S4: one-time move of the shared secrets into every
        // existing profile's own secret file, so the per-profile
        // reads below never lose access. Guarded — a failure here
        // must never block startup (the lazy copy in SettingsStore
        // covers the active profile anyway).
        runCatching {
            SettingsStore.migrateSharedSecretsToProfiles(
                this, db.profileDao().getAll().map { it.id }
            )
        }
        // Step 70: re-apply the stored launcher icon on every cold start.
        // The picker only applies it on tap, so after a reinstall/restore
        // the launcher reverted to the default icon while the setting
        // still said black/white. Guarded — a PackageManager failure must
        // never stop the app from opening.
        runCatching { AppIcon.apply(this, settings.appIcon) }
        // Plugs (#22): a second plugin system (Nuvio-format JS
        // scraper repos on QuickJS + declarative JSON rule plugs),
        // racing the addons/CS3 fan-out per title. Wired to the
        // user's own Integrations TMDB key — the same credential
        // the enrichment uses — so JS plugs can resolve a title's
        // TMDB id from its IMDb id.
        val plugService = com.aviostream.app.data.plugs.PlugService(this)
        plugService.tmdbKeyProvider = { settings.tmdbApiKey }
        repository = StreamRepository(db, profileDatabase, StremioApi(), settings, plugs = plugService)
        // Sentry crash reports (his order 2026-10-23): ON by default with a
        // Settings opt-out. Initialized on a background thread, off the
        // launch path, and fully guarded — a Sentry failure can never stop
        // the app from opening (v0.12.23 lesson: the build where Sentry
        // first went live crashed on open before it could report anything).
        appScope.launch { com.aviostream.app.data.CrashReports.initIfEnabled(this@AvioApp, settings) }
        // v0.14.30 #48: behaviour tracker (TEST BUILDS ONLY — no-op
        // unless the build was made with -PDIAG_TRACKER=true). init
        // logs the app-start line itself. NOTE: CrashReports init
        // above is async, so on a tracker build the previous
        // session's unclean-exit upload at init time may be dropped
        // if Sentry has not finished initialising yet; freeze/slow
        // reports later in the session are unaffected.
        com.aviostream.app.data.DiagTracker.init(this)
        // Quietly install the pre-installed addons (Cinemeta + subtitles) on first launch.
        appScope.launch { repository.ensurePreinstalledAddons() }
        // CloudStream (CS3): cheap restore of the persisted registry
        // (repositories + installed plugins) so the stream leg and the
        // manager screen see it. Loading stays lazy — no plugin dex is
        // loaded until a stream, search or main-page call needs it.
        appScope.launch {
            com.aviostream.app.data.cloudstream.CloudStreamStorage.initialize(this@AvioApp)
            com.aviostream.app.data.cloudstream.CloudStreamRepository.initialize()
        }
        // Pre-added addons are removed (owner's order 2026-11-22): the
        // locked PlavioHD house addon and the bundled PenguPlay — their
        // pre-installed rows are deleted. The public PlavioHD row is
        // removed too (owner's order 2026-10-24). Then the beginner
        // bundle (all OFF by default).
        appScope.launch { repository.removeLockedPlavioHd() }
        appScope.launch { repository.removePreinstalledPublicHd() }
        appScope.launch { repository.ensureBundledAddons() }
        appScope.launch { repository.removeBundledPenguPlay() }
        // Trakt/Simkl library sync on launch, at most every 6 hours, only
        // when an account is linked (issue #3). Fire-and-forget.
        appScope.launch {
            val sync = com.aviostream.app.data.LibrarySyncManager(db, settings)
            if (sync.isLinked() &&
                System.currentTimeMillis() - settings.librarySyncLastAt > 6 * 3600_000L
            ) {
                runCatching { sync.sync() }
            }
        }
        // Keep the library in sync shortly after the user adds/removes a
        // title (debounced inside the repository hook by the sync's own
        // throttle: the manager no-ops when a sync ran in the last minute).
        repository.onWatchlistChanged = {
            appScope.launch {
                val sync = com.aviostream.app.data.LibrarySyncManager(db, settings)
                if (sync.isLinked() &&
                    System.currentTimeMillis() - settings.librarySyncLastAt > 60_000L
                ) {
                    runCatching { sync.sync() }
                }
            }
        }
        // P4: the episode/movie notification schedules moved here
        // from MainActivity.onCreate — each builds another
        // SettingsStore + WorkManager on the calling thread, and
        // the active profile is only known once the migration
        // above has run.
        runCatching {
            com.aviostream.app.data.notifications.EpisodeNotifications.ensureScheduled(this)
            com.aviostream.app.data.notifications.MovieNotifications.ensureScheduled(this)
        }
        startupReadyFlow.value = true
        // Fill in addon logos for installs from before logos were stored.
        appScope.launch { repository.backfillAddonLogos() }
        // Give pre-ordering addons distinct positions so rearrange works.
        appScope.launch { repository.normalizeAddonSortOrder() }
        // Drop catalog rows too big for SQLite's CursorWindow (reading one
        // would crash Home with SQLiteBlobTooBigException). Surgical — the
        // watchlist, addons and settings survive.
        appScope.launch { repository.pruneOversizedCache() }
    }

    /**
     * Opens the database, self-healing ONLY from genuine file corruption.
     *
     * A corrupt database file is renamed to a .corrupt.bak backup (never
     * silently deleted) before recreating empty, so user data can still be
     * recovered. Any other failure — failed migration, transient I/O error,
     * programming bug — is rethrown to crash loudly (and show up in the
     * local crash dialog) instead of silently wiping the user's add-ons, watchlist and history.
     * The in-app updater keeps working, so a fix can always reach the phone.
     */
    private fun openDatabase(): AppDatabase {
        return try {
            buildDatabase().also {
                // Force the open now — throws here if the file is corrupt.
                it.openHelper.writableDatabase
            }
        } catch (e: Exception) {
            if (!isCorruption(e)) {
                // Not corruption — do NOT delete user data. Crash loudly so
                // the real bug is visible in the crash dialog instead of
                // masking it as an empty database.
                throw e
            }
            Log.e("AvioApp", "Database file corrupt, backing up and recreating", e)
            // Rename the corrupt file as a backup instead of deleting it.
            runCatching {
                val dbFile = getDatabasePath(DB_NAME)
                if (dbFile.exists()) {
                    val bak = java.io.File(dbFile.parent, "$DB_NAME.corrupt.bak")
                    runCatching { bak.delete() }
                    dbFile.renameTo(bak)
                    // Also move the -wal / -shm sidecars so they don't confuse reopen.
                    for (suffix in listOf("-wal", "-shm")) {
                        runCatching {
                            java.io.File(dbFile.parent, "$DB_NAME$suffix").renameTo(
                                java.io.File(dbFile.parent, "$DB_NAME$suffix.corrupt.bak")
                            )
                        }
                    }
                } else {
                    deleteDatabase(DB_NAME)
                }
            }
            // The fresh DB is empty but the preinstall_done_* flags survived
            // in SharedPreferences — without clearing them the bundled
            // addons (Cinemeta/…) would never be reinstalled and
            // the app would look broken with no explanation.
            runCatching { SettingsStore(this).clearPreinstallFlags() }
            buildDatabase()
        }
    }

    /** True only for genuine SQLite file-corruption failures. */
    private fun isCorruption(e: Throwable): Boolean {
        var t: Throwable? = e
        while (t != null) {
            if (t is android.database.sqlite.SQLiteDatabaseCorruptException) return true
            val msg = (t.message ?: "").lowercase()
            // SQLite corruption signatures — deliberately narrow so a failed
            // migration or I/O error never counts as "corrupt".
            if ("database disk image is malformed" in msg ||
                "file is not a database" in msg ||
                "database is corrupt" in msg ||
                "notadb" in msg
            ) return true
            t = t.cause
        }
        return false
    }

    private fun buildDatabase(): AppDatabase =
        Room.databaseBuilder(this, AppDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
            .build()

    /**
     * Saves any uncaught crash to internal storage. On the next launch
     * MainActivity shows it with a Copy button, so the user can send it
     * for diagnosis.
     */
    private fun installCrashCatcher() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // v0.14.30 #48: snapshot the behaviour log's tail for the
            // next start's unclean-exit report. No-op unless the
            // tracker flag is on; never throws into the crash path.
            runCatching { com.aviostream.app.data.DiagTracker.snapshotTailForCrash() }
            try {
                openFileOutput("last_crash.txt", MODE_PRIVATE).bufferedWriter().use { out ->
                    out.write(Log.getStackTraceString(throwable))
                }
            } catch (_: Exception) {
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Crash log from the previous run, if the app died with an exception.
     * T6: a native death (libmpv SIGABRT, QuickJS abort) writes no
     * last_crash.txt, so the file can be an OLDER Java crash — showing
     * it as if it were the latest one misleads diagnosis. When the
     * most recent OS-recorded death is a native/ANR/low-memory death
     * newer than the file, the file is stale: drop it and show nothing
     * (the death itself is already reported to Sentry by
     * DiagTracker.reportPreviousDeaths). A Java crash (REASON_CRASH)
     * always matches its own file, so it still shows.
     */
    fun readCrashReport(): String? {
        val f = java.io.File(filesDir, "last_crash.txt")
        if (!f.exists()) return null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val latestDeath = runCatching {
                ExitDeathReporter.collect(this)
                    .firstOrNull { BehaviourReportSupport.isReportableDeath(it.reason) }
            }.getOrNull()
            if (latestDeath != null &&
                latestDeath.reason != BehaviourReportSupport.EXIT_REASON_CRASH &&
                latestDeath.timestampMs > f.lastModified() + STALE_CRASH_MARGIN_MS
            ) {
                // Stale Java crash: the latest death was native (or an
                // ANR / low-memory kill) and happened after it.
                runCatching { f.delete() }
                return null
            }
        }
        return f.readText().take(6000)
    }

    fun clearCrashReport() {
        try {
            java.io.File(filesDir, "last_crash.txt").delete()
        } catch (_: Exception) {
        }
    }

    companion object {
        private const val DB_NAME = "avio-stream.db"
        /** T6: a death must beat the crash file by this much to count as newer (clock granularity). */
        private const val STALE_CRASH_MARGIN_MS = 1_000L
    }
}
