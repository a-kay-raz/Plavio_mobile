package com.aviostream.app.ui.widget

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.aviostream.app.AvioApp
import com.aviostream.app.R
import com.aviostream.app.data.db.PlaybackState
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Backing service for the Continue Watching widget grid. Reads the
 * ACTIVE profile's playback rows (the profile database holds the
 * current profile's rows), keeps unfinished titles (started, under
 * 98%), newest first, capped at 12. Poster bitmaps are fetched here
 * (the factory's data-set change runs off the main thread) and
 * downscaled so the RemoteViews payload stays small.
 *
 * Fork adaptations vs upstream 0.14.30: playback lives in the
 * per-profile database here (AvioApp.profileDatabase), not the
 * global one; PlaybackState has no seriesName/metaId/episodeTitle,
 * so a series is recognised by season/episode + imdbId and its
 * card key is the imdbId (mirrors StreamRepository's series
 * identity); the deep link is avio://title/<type>/<id>.
 */
class CwWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CwWidgetFactory(applicationContext)
}

private class CwWidgetFactory(
    private val context: Context
) : RemoteViewsService.RemoteViewsFactory {

    private data class CwRow(
        val title: String,
        val subtitle: String,
        val posterUrl: String?,
        val link: String
    )

    @Volatile private var rows: List<CwRow> = emptyList()
    // ConcurrentHashMap: getViewAt reads posters on the host's binder
    // thread while the background load below writes them. Null bitmaps
    // are never stored (ConcurrentHashMap forbids null values) — a
    // failed fetch simply leaves the poster absent, as before.
    private val posters = ConcurrentHashMap<String, android.graphics.Bitmap>()
    // T12: the data load runs on this scope, not on the host's binder
    // thread — onDataSetChanged used to runBlocking the DB read (plus
    // the poster fetches) here, stalling every widget refresh.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var loadJob: Job? = null

    override fun onCreate() {}

    override fun onDataSetChanged() {
        // Return at once with the previously-loaded rows (empty on the
        // very first load); the background load below re-binds the grid
        // when the new rows land. A load already in flight is not
        // restarted — its completion still refreshes.
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            val loaded = runCatching { loadRows() }.getOrDefault(emptyList())
            val changed = loaded != rows
            rows = loaded
            // Pre-fetch posters for the visible set, before the refresh,
            // so the re-bind finds them ready.
            for (row in loaded) {
                val url = row.posterUrl ?: continue
                if (url !in posters) fetchPoster(url)?.let { posters[url] = it }
            }
            // Only re-bind when the rows actually changed: the refresh
            // makes the host call onDataSetChanged again, and that second
            // load finds identical rows and stops here — no refresh loop.
            if (changed) CwWidgetUpdater.refresh(context)
        }
    }

    private suspend fun loadRows(): List<CwRow> {
        val app = context.applicationContext as AvioApp
        // Same one-card-per-series collapse as the in-app
        // Continue Watching list — newest first, so the first
        // row per series is its last opened episode.
        val seenSeries = HashSet<String>()
        return app.profileDatabase.playbackDao().getAll()
            .filter { state ->
                val duration = state.durationMs
                state.positionMs > 0L &&
                    (duration <= 0L || state.positionMs * 100L < duration * 98L)
            }
            .sortedByDescending { it.updatedAt }
            .filter { state ->
                val seriesKey = seriesKeyForPlayback(state)
                    ?: return@filter true
                seenSeries.add(seriesKey)
            }
            .take(12)
            .map { it.toRow() }
    }

    /** One card per series (its imdbId) or per movie (its videoId). */
    private fun seriesKeyForPlayback(state: PlaybackState): String? =
        if ((state.season > 0 || state.episode > 0) && !state.imdbId.isNullOrBlank()) {
            state.imdbId
        } else {
            null
        }

    private fun PlaybackState.toRow(): CwRow {
        val isSeries = season > 0 || episode > 0
        val type = if (isSeries) "series" else "movie"
        val id = imdbId ?: videoId
        val subtitle = if (isSeries && season > 0 && episode > 0) {
            "S$season · E$episode"
        } else ""
        return CwRow(
            title = title,
            subtitle = subtitle,
            posterUrl = poster,
            link = "avio://title/$type/$id"
        )
    }

    private fun fetchPoster(url: String): android.graphics.Bitmap? = runCatching {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = true
        connection.inputStream.use { input ->
            val bytes = input.readBytes()
            val options = BitmapFactory.Options().apply { inSampleSize = 2 }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }
    }.getOrNull()

    override fun getCount(): Int = rows.size

    override fun getViewAt(position: Int): RemoteViews {
        val row = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_cw_item)
        val views = RemoteViews(context.packageName, R.layout.widget_cw_item)
        views.setTextViewText(R.id.widget_item_title, row.title)
        views.setTextViewText(R.id.widget_item_sub, row.subtitle)
        row.posterUrl?.let { url -> posters[url]?.let { views.setImageViewBitmap(R.id.widget_item_poster, it) } }
        val fillIn = Intent().apply { data = Uri.parse(row.link) }
        views.setOnClickFillInIntent(R.id.widget_item_poster, fillIn)
        // The whole card isn't a single view in this layout; the
        // poster carries the tap (it fills most of the card).
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = false
    override fun onDestroy() {
        scope.cancel()
        posters.clear()
    }
}
