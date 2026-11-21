package com.pulsestream.app.data

import android.content.Context

/**
 * Recent search queries, shown under the search field when it's empty.
 * Tiny SharedPreferences-backed store, most-recent-first, max 12 entries.
 */
object SearchHistory {
    private const val PREFS = "pulse_search"
    private const val KEY_HISTORY = "history"
    private const val KEY_ENABLED = "history_enabled"
    private const val MAX_ENTRIES = 12

    /** Search history on/off (Nuvio's enabled flag, default true). When off, nothing is recorded and the recents section hides — entries stay stored and reappear on re-enable. */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun get(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null) ?: return emptyList()
        return raw.split("\n").filter { it.isNotBlank() }.take(MAX_ENTRIES)
    }

    fun add(context: Context, query: String) {
        if (!isEnabled(context)) return
        val q = query.trim()
        if (q.length < 2) return
        val updated = (listOf(q) + get(context).filter { !it.equals(q, ignoreCase = true) })
            .take(MAX_ENTRIES)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, updated.joinToString("\n"))
            .apply()
    }

    /** Remove one entry (exact match, Nuvio removeSearch). */
    fun remove(context: Context, query: String) {
        val updated = get(context).filterNot { it == query }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, updated.joinToString("\n"))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_HISTORY)
            .apply()
    }
}
