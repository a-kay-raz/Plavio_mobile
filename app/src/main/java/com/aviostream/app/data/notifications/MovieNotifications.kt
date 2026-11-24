package com.aviostream.app.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.aviostream.app.MainActivity
import com.aviostream.app.R
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.ui.nav.Routes
import java.util.concurrent.TimeUnit

/**
 * New-movie-release notifications (v0.14.30 port): channel setup,
 * WorkManager scheduling and posting for movies in the user's
 * watchlist whose release date has arrived. Modelled directly on
 * [EpisodeNotifications]; the baseline-diff tracker lives in
 * [MovieReleaseTracker].
 */
object MovieNotifications {

    const val CHANNEL_ID = "movie_releases"
    const val WORK_NAME = "movie_release_check"
    const val CHECK_INTERVAL_HOURS = 12L

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_channel_movies),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_movies_desc)
            }
        )
    }

    /** Schedule the periodic movie check. Idempotent (KEEP). */
    fun ensureScheduled(context: Context) {
        val appContext = context.applicationContext
        if (!SettingsStore.forActiveProfile(appContext).movieNotifications) return
        ensureChannel(appContext)
        val request = PeriodicWorkRequestBuilder<MovieReleaseWorker>(
            CHECK_INTERVAL_HOURS, TimeUnit.HOURS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .addTag(WORK_NAME)
            .build()
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /** Stop the periodic check (called when the user disables the setting). */
    fun cancel(context: Context) {
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
    }

    /** Post a "movie released" notification. Silently skipped without permission. */
    fun postMovieReleased(
        context: Context,
        addonId: String,
        metaId: String,
        movieName: String
    ) {
        val appContext = context.applicationContext
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(appContext)

        val route = Routes.detail(addonId, "movie", metaId)
        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, route)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = appContext.getString(R.string.notif_new_movie_text)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avio_logo)
            .setContentTitle(movieName)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(appContext).notify(route.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
        }
    }
}

/**
 * Baseline store for the movie-release checker, SharedPreferences
 * (same pattern as EpisodeTracker): per movie (keyed by metaId) the
 * release date recorded when the movie was FIRST seen, plus a
 * notified flag. A movie only ever notifies when it was first seen
 * with a FUTURE release date that has since arrived — a movie first
 * seen already-released is baselined silently, so turning the
 * feature on (or adding an old movie) never floods.
 */
class MovieReleaseTracker(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The release date (yyyy-MM-dd) recorded at first sight, or null. */
    fun baseline(metaId: String): String? = prefs.getString("date_$metaId", null)

    /** True when the baselined release date was still future at first sight. */
    fun wasFutureAtBaseline(metaId: String): Boolean =
        prefs.getBoolean("future_$metaId", false)

    fun recordBaseline(metaId: String, releaseDate: String, futureAtBaseline: Boolean) {
        prefs.edit()
            .putString("date_$metaId", releaseDate)
            .putBoolean("future_$metaId", futureAtBaseline)
            .apply()
    }

    fun wasNotified(metaId: String): Boolean = prefs.getBoolean("notified_$metaId", false)

    fun markNotified(metaId: String) {
        prefs.edit().putBoolean("notified_$metaId", true).apply()
    }

    /** Drop entries for movies no longer followed. */
    fun prune(keepMetaIds: Set<String>) {
        val editor = prefs.edit()
        var changed = false
        for (key in prefs.all.keys) {
            if (!key.startsWith("date_") && !key.startsWith("notified_") &&
                !key.startsWith("future_")
            ) continue
            val metaId = key.substringAfter('_')
            if (metaId !in keepMetaIds) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }

    private companion object {
        const val PREFS = "movie_release_tracker"
    }
}
