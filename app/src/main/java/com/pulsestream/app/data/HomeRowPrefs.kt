package com.pulsestream.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Home catalog row preferences — Nuvio's model, ported in shape.
 *
 * One JSON blob (SettingsStore.homeRowPrefsJson), one record per catalog
 * row, keyed by the derived row key "<addonId>|<type>|<catalogId>".
 * Rows are derived from enabled addons; the user never creates one —
 * they only hide / reorder / rename / pin each derived row, and pick
 * which rows feed the hero. Nuvio stores the same blob per profile;
 * Plavio is single-profile, so one blob is enough.
 *
 * Deliberately NOT ported from Nuvio: PINNED_POSITION (write-dead there),
 * collections / server / CloudStream rows (no such backends in Plavio),
 * hero style + trailer playback (Plavio's hero has one design, no
 * trailer), Concierge / Smart Shelves (Enhanced-only derived sections).
 * Pin does not sync anywhere — there is nothing to sync to.
 */
@Serializable
data class StoredHomeRowPref(
    val key: String,
    val customTitle: String = "",
    val enabled: Boolean = true,
    val heroSourceEnabled: Boolean = true,
    val order: Int = 0,
    val rowPlacement: String = HomeRowPlacement.NORMAL.storage
)

@Serializable
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

private val homeRowPrefsJson = Json { ignoreUnknownKeys = true }

fun StoredHomeRowPrefs.encode(): String =
    homeRowPrefsJson.encodeToString(StoredHomeRowPrefs.serializer(), this)

fun decodeHomeRowPrefs(raw: String?): StoredHomeRowPrefs = try {
    raw?.let { homeRowPrefsJson.decodeFromString(StoredHomeRowPrefs.serializer(), it) }
        ?: StoredHomeRowPrefs()
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
