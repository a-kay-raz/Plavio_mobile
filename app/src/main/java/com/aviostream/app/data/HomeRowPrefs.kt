package com.aviostream.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Home catalog row preferences — Nuvio's model, ported in shape.
 *
 * One JSON blob (SettingsStore.homeRowPrefsJson), one record per catalog
 * row, keyed by the derived row key "<addonId>|<type>|<catalogId>".
 * Rows are derived from enabled addons; the user never creates one —
 * they only hide / reorder / rename / pin each derived row, and pick
 * which rows feed the hero. Nuvio stores the same blob per profile;
 * Avio is single-profile, so one blob is enough.
 *
 * Deliberately NOT ported from Nuvio: PINNED_POSITION (write-dead there),
 * collections / server rows (no such backends in Avio),
 * hero style + trailer playback (Avio's hero has one design, no
 * trailer), Concierge / Smart Shelves (Enhanced-only derived sections).
 * CloudStream rows ARE ported (Phase 2, 2026-11-22): addonId "cs3:<pluginId>".
 * Pin does not sync anywhere — there is nothing to sync to.
 */
data class StoredHomeRowPref(
    val key: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = true,
    val order: Int = 0,
    val rowPlacement: String = HomeRowPlacement.NORMAL.storage
)

data class StoredHomeRowPrefs(
    val heroEnabled: Boolean = true,
    val showCatalogType: Boolean = true,
    val hideUnreleasedContent: Boolean = false,
    val items: List<StoredHomeRowPref> = emptyList()
)

/** Max catalogs feeding the hero carousel (Nuvio's HERO_SOURCE_SELECTION_LIMIT). */
const val HOME_HERO_SOURCE_LIMIT = 2

/** Pin state of a Home row — Nuvio's HomeCatalogRowPlacement. */
enum class HomeRowPlacement(val storage: String) {
    NORMAL("normal"),
    PINNED_TOP("pinned_top"),
    PINNED_BOTTOM("pinned_bottom");

    companion object {
        fun fromStorage(raw: String?): HomeRowPlacement =
            entries.firstOrNull { it.storage == raw } ?: NORMAL
    }
}

/** Derived row identity — the same key Home and the catalog cache already use. */
fun homeRowKey(addonId: String, type: String, catalogId: String) = "$addonId|$type|$catalogId"

fun CatalogRow.rowKey() = homeRowKey(addonId, type, catalogId)

/**
 * Plain org.json codec. kotlinx-serialization-json is a dependency but its
 * compiler plugin is NOT applied to this module, so a generated
 * `.serializer()` does not exist (that was the dev-6 build failure) — and
 * this file follows the repo's existing pattern (AddonShare.kt) of explicit
 * codecs instead of adding a new plugin to the build. Unknown keys are
 * ignored, so an older blob still decodes.
 */
fun StoredHomeRowPrefs.encode(): String {
    val root = JSONObject()
    root.put("heroEnabled", heroEnabled)
    root.put("showCatalogType", showCatalogType)
    root.put("hideUnreleasedContent", hideUnreleasedContent)
    val arr = JSONArray()
    for (item in items) {
        val o = JSONObject()
        o.put("key", item.key)
        o.put("customTitle", item.customTitle)
        o.put("enabled", item.enabled)
        o.put("heroSourceEnabled", item.heroSourceEnabled)
        o.put("order", item.order)
        o.put("rowPlacement", item.rowPlacement)
        arr.put(o)
    }
    root.put("items", arr)
    return root.toString()
}

fun decodeHomeRowPrefs(raw: String?): StoredHomeRowPrefs = try {
    val root = raw?.let { JSONObject(it) } ?: return StoredHomeRowPrefs()
    val items = mutableListOf<StoredHomeRowPref>()
    val arr = root.optJSONArray("items")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val key = o.optString("key")
            if (key.isEmpty()) continue
            items += StoredHomeRowPref(
                key = key,
                customTitle = o.optString("customTitle", ""),
                enabled = o.optBoolean("enabled", true),
                heroSourceEnabled = o.optBoolean("heroSourceEnabled", true),
                order = o.optInt("order", 0),
                rowPlacement = o.optString("rowPlacement", HomeRowPlacement.NORMAL.storage)
            )
        }
    }
    StoredHomeRowPrefs(
        heroEnabled = root.optBoolean("heroEnabled", true),
        showCatalogType = root.optBoolean("showCatalogType", true),
        hideUnreleasedContent = root.optBoolean("hideUnreleasedContent", false),
        items = items
    )
} catch (_: Exception) {
    StoredHomeRowPrefs()
}

/** Display order: pinned-top first, then normal, then pinned-bottom; order inside each group. */
fun StoredHomeRowPrefs.displayOrder(): List<StoredHomeRowPref> =
    items.sortedWith(
        compareBy<StoredHomeRowPref> {
            when (HomeRowPlacement.fromStorage(it.rowPlacement)) {
                HomeRowPlacement.PINNED_TOP -> 0
                HomeRowPlacement.NORMAL -> 1
                HomeRowPlacement.PINNED_BOTTOM -> 2
            }
        }.thenBy { it.order }
    )

fun StoredHomeRowPrefs.visibleItems(): List<StoredHomeRowPref> =
    displayOrder().filter { it.enabled }

fun StoredHomeRowPrefs.hiddenItems(): List<StoredHomeRowPref> =
    displayOrder().filter { !it.enabled }

fun StoredHomeRowPrefs.heroSourceKeys(): Set<String> =
    items.filter { it.heroSourceEnabled }.map { it.key }.toSet()

fun StoredHomeRowPrefs.prefFor(row: CatalogRow): StoredHomeRowPref? =
    items.firstOrNull { it.key == row.rowKey() }

fun StoredHomeRowPrefs.displayTitle(row: CatalogRow): String =
    prefFor(row)?.customTitle?.takeIf { it.isNotBlank() } ?: row.catalogName

/** True when the item's release year is in the future. No parseable year = keep it. */
fun MetaPreview.isUnreleased(currentYear: Int): Boolean {
    val year = releaseInfo
        ?.let { Regex("""\b(19|20)\d{2}\b""").find(it)?.value?.toIntOrNull() }
        ?: return false
    return year > currentYear
}
