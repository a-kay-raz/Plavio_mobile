package com.pulsestream.app.data

import android.content.Context

/**
 * In-app per-title star ratings, stored locally (Nuvio
 * `ratings/UserRatingsRepository`, local-only: Nuvio syncs ratings through
 * tracking providers, Plavio has no rating-write path there, so the stars
 * live in their own prefs file, keyed by the show/movie meta id).
 */
class UserRatings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("pulse_user_ratings", Context.MODE_PRIVATE)

    /** 1..5 stars for a title, or null when unrated. */
    fun getRating(metaId: String): Int? {
        val value = prefs.getInt(key(metaId), 0)
        return value.takeIf { it in 1..5 }
    }

    fun setRating(metaId: String, stars: Int) {
        prefs.edit().putInt(key(metaId), stars.coerceIn(1, 5)).apply()
    }

    fun clearRating(metaId: String) {
        prefs.edit().remove(key(metaId)).apply()
    }

    private fun key(metaId: String) = "rating|$metaId"
}
