package com.aviostream.app.data

import android.content.Context

/**
 * In-app per-title star ratings, stored locally (Nuvio
 * `ratings/UserRatingsRepository`, local-only: Nuvio syncs ratings through
 * tracking providers, Avio has no rating-write path there, so the stars
 * live in their own prefs file, keyed by the show/movie meta id).
 */
class UserRatings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("avio_user_ratings", Context.MODE_PRIVATE)

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

    // ---- End-of-play prompt dismissals (v0.14.30 port) ----
    // The rating prompt lives at the end of playback in the player.
    // Its keys are per title (movies) or per EPISODE (series:
    // "<metaId>:S<s>E<e>"): after two skips for the same key the
    // prompt stays quiet for that title/episode. Stored under a
    // prefix inside the same prefs file, so a prompt key can never
    // collide with a rating key.

    /** How many times the end-of-play prompt was skipped for [promptKey]. */
    fun promptSkipCount(promptKey: String): Int =
        prefs.getInt(SKIP_PREFIX + promptKey, 0).coerceAtLeast(0)

    /** Record one skip of the end-of-play prompt for [promptKey]. */
    fun notePromptSkip(promptKey: String) {
        prefs.edit()
            .putInt(SKIP_PREFIX + promptKey, promptSkipCount(promptKey) + 1)
            .apply()
    }

    private fun key(metaId: String) = "rating|$metaId"

    private companion object {
        const val SKIP_PREFIX = "prompt_skips:"
    }
}
