package com.aviostream.app.data

/**
 * Avio's own library statuses (v0.14.30 port): five statuses, exactly
 * one per title, mirroring Simkl's five — with Avio's watch-later
 * status named "Next to Watch" in Simkl's Plan to Watch slot.
 *
 * Storage model: WATCHING / HOLD / COMPLETED / DROPPED live as a
 * row in the title_statuses table. NEXT_TO_WATCH has no row — it is
 * backed by the watchlist table itself, which is also the seed:
 * every existing watchlist entry simply reads as Next to Watch.
 * Display state resolves as: an explicit title_statuses row wins;
 * otherwise watchlist membership means Next to Watch; otherwise
 * the title has no Avio status.
 */
object AvioStatuses {
    const val WATCHING = "watching"
    const val NEXT_TO_WATCH = "nexttowatch"
    const val HOLD = "hold"
    const val COMPLETED = "completed"
    const val DROPPED = "dropped"
    val ALL = listOf(WATCHING, NEXT_TO_WATCH, HOLD, COMPLETED, DROPPED)

    fun label(status: String): String = when (status) {
        WATCHING -> "Watching"
        NEXT_TO_WATCH -> "Next to Watch"
        HOLD -> "On Hold"
        COMPLETED -> "Completed"
        DROPPED -> "Dropped"
        else -> status
    }

    /** The one status a title displays with, or null for none. */
    fun resolved(explicit: String?, inWatchlist: Boolean): String? =
        explicit ?: if (inWatchlist) NEXT_TO_WATCH else null
}
