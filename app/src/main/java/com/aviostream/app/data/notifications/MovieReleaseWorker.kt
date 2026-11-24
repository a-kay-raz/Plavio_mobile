package com.aviostream.app.data.notifications

import android.content.Context
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aviostream.app.data.ProfileStore
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StremioApi
import com.aviostream.app.data.db.AppDatabase
import com.aviostream.app.data.db.MIGRATION_1_2
import com.aviostream.app.data.db.MIGRATION_2_3
import com.aviostream.app.data.db.MIGRATION_3_4
import com.aviostream.app.data.db.MIGRATION_4_5
import com.aviostream.app.data.db.MIGRATION_5_6
import com.aviostream.app.data.db.MIGRATION_6_7
import com.aviostream.app.data.db.MIGRATION_7_8
import com.aviostream.app.data.db.MIGRATION_19_20
import java.time.LocalDate

/**
 * Periodic background check for newly released movies (v0.14.30
 * port), modelled on [EpisodeCheckWorker]: every
 * [MovieNotifications.CHECK_INTERVAL_HOURS] hours, for each movie
 * in the active profile's watchlist, resolve the meta through the
 * addon's own server and read its release date.
 *
 * Baseline-diff, exactly like episodes: the first sight of a movie
 * only records its release date (and whether that date was still in
 * the future). A notification fires only when a movie baselined as
 * FUTURE has since arrived — never for movies first seen already
 * released — so enabling the feature, or adding a back-catalogue
 * movie, can't flood. Movies whose meta carries no full release
 * date (year only) are skipped: there is no honest day to notify on.
 *
 * Scope note: unlike upstream (watchlist + Trakt + Simkl union),
 * this follows the fork's episode worker and reads the local
 * watchlist only.
 */
class MovieReleaseWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val settings = SettingsStore.forActiveProfile(ctx)
        if (!settings.movieNotifications) {
            MovieNotifications.cancel(ctx)
            return Result.success()
        }

        val dbFile = ctx.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return Result.success()

        val db = runCatching {
            Room.databaseBuilder(ctx, AppDatabase::class.java, DB_NAME)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_19_20
                )
                .build()
        }.getOrNull() ?: return Result.success()

        // The watchlist lives in the active profile's database now;
        // addons stay in the global database (same as EpisodeCheckWorker).
        val activeProfileId = ProfileStore.activeProfileId(ctx)
        val profileDb = activeProfileId?.let {
            runCatching { ProfileStore.openProfileDatabase(ctx, it) }.getOrNull()
        }
        if (profileDb == null) {
            runCatching { db.close() }
            return Result.success()
        }

        try {
            val tracker = MovieReleaseTracker(ctx)
            val api = StremioApi()
            val today = runCatching { LocalDate.now() }.getOrNull()
                ?: return Result.success()

            val addons = runCatching { db.addonDao().getAll() }.getOrNull().orEmpty()
            val metaAddons = addons
                .filter { it.enabled && "meta" in it.resourceSet }
                .associateBy { it.id }

            val movies = runCatching { profileDb.watchlistDao().getAll() }
                .getOrNull().orEmpty()
                .filter { it.type == "movie" }
            if (movies.isEmpty()) {
                tracker.prune(emptySet())
                return Result.success()
            }

            val keep = mutableSetOf<String>()
            for (item in movies) {
                keep.add(item.metaId)
                val addon = metaAddons[item.addonId] ?: continue
                runCatching {
                    val meta = api.fetchMeta(addon.baseUrl, "movie", item.metaId)
                        ?: return@runCatching
                    val release = releaseDateOf(meta) ?: return@runCatching

                    if (tracker.baseline(item.metaId) == null) {
                        // First sight: baseline only, never notify.
                        val future = release.isAfter(today)
                        tracker.recordBaseline(item.metaId, release.toString(), future)
                        if (!future) {
                            // Already out when first seen: silent forever.
                            tracker.markNotified(item.metaId)
                        }
                    } else if (!tracker.wasNotified(item.metaId) &&
                        tracker.wasFutureAtBaseline(item.metaId) &&
                        !release.isAfter(today)
                    ) {
                        MovieNotifications.postMovieReleased(
                            ctx, item.addonId, item.metaId,
                            item.name.ifBlank { meta.name }
                        )
                        tracker.markNotified(item.metaId)
                    }
                }
            }
            tracker.prune(keep)
        } finally {
            runCatching { profileDb.close() }
            runCatching { db.close() }
        }
        return Result.success()
    }

    /**
     * Full release date for a movie meta: releaseInfo first (PMeta /
     * Cinemeta carry "yyyy-MM-dd" there), else the single video's
     * released field. Year-only values return null.
     */
    private fun releaseDateOf(meta: com.aviostream.app.data.MetaDetail): LocalDate? {
        parseDate(meta.releaseInfo)?.let { return it }
        return parseDate(meta.videos.firstOrNull()?.released)
    }

    private fun parseDate(raw: String?): LocalDate? {
        val s = raw?.trim()?.takeIf { it.length >= 10 } ?: return null
        return runCatching { LocalDate.parse(s.substring(0, 10)) }.getOrNull()
    }

    companion object {
        private const val DB_NAME = "avio-stream.db"
    }
}
