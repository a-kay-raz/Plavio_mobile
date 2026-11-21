package com.pulsestream.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Step 57: Detail section customization (Nuvio
 * details/MetaScreenSettingsRepository.kt, scoped). Nuvio's ten
 * section keys, in Nuvio's default order; each has an enabled flag
 * and the list order persists. `tabLayout` shows a sticky tab row
 * that scrolls to a section instead of one long scroll. Nuvio also
 * stores background mode / hero trailer / episode card style here —
 * Plavio already has those as their own settings (Steps 24, 29), so
 * this file owns only the section order, visibility and tab layout.
 */
enum class DetailSectionKey(val title: String, val canBeTabbed: Boolean) {
    ACTIONS("Actions", false),
    OVERVIEW("Overview", false),
    PRODUCTION("Production", true),
    CAST("Cast", true),
    COMMENTS("Comments", true),
    TRAILERS("Trailers", true),
    EPISODES("Episodes", true),
    DETAILS("Details", true),
    COLLECTION("Collection", true),
    MORE_LIKE_THIS("More like this", true)
}

data class DetailSectionPref(val key: DetailSectionKey, val enabled: Boolean)

data class DetailSectionSettings(
    val order: List<DetailSectionPref>,
    val tabLayout: Boolean
) {
    fun isEnabled(key: DetailSectionKey): Boolean =
        order.firstOrNull { it.key == key }?.enabled ?: true
}

/** What the current title actually has — one source of truth for both rendering and scroll indices. */
data class DetailSectionAvailability(
    val hasOverview: Boolean,
    val hasTrailers: Boolean,
    val hasCast: Boolean,
    val hasProduction: Boolean,
    val hasCollection: Boolean,
    val hasComments: Boolean,
    val isSeries: Boolean,
    val hasDetails: Boolean,
    val relatedCount: Int
)

fun DetailSectionKey.isAvailable(a: DetailSectionAvailability): Boolean = when (this) {
    DetailSectionKey.ACTIONS -> true
    DetailSectionKey.OVERVIEW -> a.hasOverview
    DetailSectionKey.PRODUCTION -> a.hasProduction
    DetailSectionKey.CAST -> a.hasCast
    DetailSectionKey.COMMENTS -> a.hasComments
    DetailSectionKey.TRAILERS -> a.hasTrailers
    DetailSectionKey.EPISODES -> a.isSeries
    DetailSectionKey.DETAILS -> a.hasDetails
    DetailSectionKey.COLLECTION -> a.hasCollection
    DetailSectionKey.MORE_LIKE_THIS -> a.relatedCount > 0
}

/** LazyColumn items a section emits (episodes = seasons + header + search + rail; related = one per row). */
fun DetailSectionKey.itemCount(a: DetailSectionAvailability): Int = when (this) {
    DetailSectionKey.EPISODES -> if (a.isSeries) 4 else 0
    DetailSectionKey.MORE_LIKE_THIS -> a.relatedCount
    else -> if (isAvailable(a)) 1 else 0
}

fun DetailSectionSettings.visibleOrdered(a: DetailSectionAvailability): List<DetailSectionKey> =
    order.filter { it.enabled && it.key.isAvailable(a) }.map { it.key }

/**
 * Index of the section's first item in the Detail LazyColumn
 * (hero = 0, sticky tab row = 1 when tab layout is on). Null when
 * the section is hidden or has nothing to show.
 */
fun DetailSectionSettings.scrollIndexFor(
    key: DetailSectionKey,
    a: DetailSectionAvailability
): Int? {
    val visible = visibleOrdered(a)
    if (key !in visible) return null
    var index = 1 // hero
    if (tabLayout) index += 1 // sticky tab row
    for (k in visible) {
        if (k == key) return index
        index += k.itemCount(a)
    }
    return null
}

fun defaultDetailSections(): DetailSectionSettings =
    DetailSectionSettings(
        order = DetailSectionKey.entries.map { DetailSectionPref(it, true) },
        tabLayout = false
    )

fun loadDetailSections(raw: String?): DetailSectionSettings {
    val fallback = defaultDetailSections()
    if (raw.isNullOrBlank()) return fallback
    return runCatching {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("order")
        val seen = mutableSetOf<DetailSectionKey>()
        val order = mutableListOf<DetailSectionPref>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val key = runCatching {
                    DetailSectionKey.valueOf(obj.optString("key"))
                }.getOrNull() ?: continue
                if (!seen.add(key)) continue
                order += DetailSectionPref(key, obj.optBoolean("enabled", true))
            }
        }
        // Keys missing from storage (new sections) append enabled, in default order.
        DetailSectionKey.entries.forEach { key ->
            if (key !in seen) order += DetailSectionPref(key, true)
        }
        DetailSectionSettings(order, root.optBoolean("tabLayout", false))
    }.getOrDefault(fallback)
}

fun saveDetailSections(settings: DetailSectionSettings): String {
    val arr = JSONArray()
    settings.order.forEach { pref ->
        arr.put(JSONObject().put("key", pref.key.name).put("enabled", pref.enabled))
    }
    return JSONObject().put("order", arr).put("tabLayout", settings.tabLayout).toString()
}
