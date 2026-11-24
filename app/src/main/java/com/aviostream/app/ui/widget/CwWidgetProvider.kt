package com.aviostream.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.aviostream.app.MainActivity
import com.aviostream.app.R
import kotlinx.coroutines.launch

/**
 * Continue Watching home-screen widget (v0.14.30 port, feature #19):
 * a poster grid of in-progress titles fed by [CwWidgetService]. A
 * card tap opens that title's detail page through the same
 * avio://title/<type>/<id> link MainActivity routes (see
 * handleTitleLink); the header (and empty area) opens the app.
 * Refreshed by [CwWidgetUpdater] whenever playback saves progress,
 * plus the platform's half-hourly periodic update.
 */
class CwWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, buildViews(context, id))
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun buildViews(context: Context, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_cw)

        val serviceIntent = Intent(context, CwWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            // Distinct data per widget instance so the system keeps
            // one factory per widget.
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_grid, serviceIntent)
        views.setEmptyView(R.id.widget_grid, R.id.widget_empty)

        // Card taps: the template launches MainActivity; each item's
        // fill-in intent adds its own avio://title/... data, which
        // MainActivity routes to the detail page.
        val template = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val templatePending = PendingIntent.getActivity(
            context, widgetId, template,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_grid, templatePending)

        // Header tap opens the app home.
        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_title,
            PendingIntent.getActivity(
                context, widgetId + 10_000, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        return views
    }
}

/** Refresh hook: call whenever Continue Watching data changes. */
object CwWidgetUpdater {
    // getAppWidgetIds is a Binder call and refresh() is invoked from
    // MainActivity.onStart (main thread) and from the player — the
    // work now always runs on a background dispatcher; callers keep
    // the same one-line call and identical behaviour.
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    fun refresh(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                val manager = AppWidgetManager.getInstance(appContext)
                val ids = manager.getAppWidgetIds(
                    ComponentName(appContext, CwWidgetProvider::class.java)
                )
                if (ids.isNotEmpty()) {
                    manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_grid)
                }
            }
        }
    }
}
