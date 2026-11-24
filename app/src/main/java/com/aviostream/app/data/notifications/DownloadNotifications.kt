package com.aviostream.app.data.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aviostream.app.MainActivity
import com.aviostream.app.R
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.ui.nav.Routes

/**
 * "Download finished" notifications (v0.14.30 port). Shares the
 * existing "downloads" channel with the foreground service's
 * progress notifications (one channel in system settings).
 * Called from DownloadManager's completion points; the toggle is
 * SettingsStore.downloadFinishedNotifications — the progress
 * notification itself is unaffected by it.
 */
object DownloadNotifications {

    const val CHANNEL_ID = "downloads"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Download progress and finished downloads"
            }
        )
    }

    /** Post one "download finished" notification for [itemTitle]. */
    fun postDownloadFinished(context: Context, downloadId: String, itemTitle: String) {
        val appContext = context.applicationContext
        if (!SettingsStore(appContext).downloadFinishedNotifications) return
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(appContext)

        val intent = Intent(appContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(MainActivity.EXTRA_OPEN_ROUTE, Routes.DOWNLOADS)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            downloadId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = appContext.getString(R.string.notif_download_finished_text)
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_avio_logo)
            .setContentTitle(itemTitle)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(appContext)
                .notify("dl_done_$downloadId".hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission can be revoked between the check and notify().
        }
    }
}
