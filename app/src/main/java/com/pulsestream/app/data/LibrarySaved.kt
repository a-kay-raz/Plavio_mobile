package com.pulsestream.app.data

/**
 * Step 47: Library Saved view — sorting, watched filter and layout
 * (Nuvio library/LibraryDisplaySettings.kt, scoped port). Pure logic, no
 * Compose: the UI lives in ui/screens/LibrarySavedScreen.kt.
 *
 * Sources are unified into one list of [SavedLibraryItem]s. A title can
 * live in several sources at once (watchlist + a custom list + Trakt);
 * rows mode shows it in every section it belongs to, single-section mode
 * dedupes by (type, metaId) keeping the first source in source order
 * (My Watchlist, then custom lists, then Trakt, then Simkl).
 */

/** First 4-digit 19xx/20xx year in a releaseInfo string ("2024", "2024-03-01"), else 0 = unknown. */
fun parseReleaseYear(releaseInfo: String?): Int {
    if (releaseInfo.isNullOrBlank()) return 0
    return Regex("(?:19|20)\\d{2}").find(releaseInfo)?.value?.toIntOrNull() ?: 0
}

/** Nuvio LibrarySortOption — the 7 sort ways, in Nuvio's order. */
enum class LibrarySortOption(val stored: String, val label: String) {
    DEFAULT("default", "Default"),
    ADDED_DESC("added_desc", "Added · newest"),
    ADDED_ASC("added_asc", "Added · oldest"),
    RELEASED_DESC("released_desc", "Released · newest"),
    RELEASED_ASC("released_asc", "Released · oldest"),
    TITLE_ASC("title_asc", "Title · A–Z"),
    TITLE_DESC("title_desc", "Title · Z–A");

    companion object {
        fun fromStored(stored: String): LibrarySortOption =
            entries.firstOrNull { it.stored == stored } ?: DEFAULT
    }
}

/** Nuvio LibraryWatchedFilter. */
enum class LibraryWatchedFilter(val stored: String, val label: String) {
    ALL("all", "All"),
    WATCHED("watched", "Watched"),
    UNWATCHED("unwatched", "Unwatched");

    companion object {
        fun fromStored(stored: String): LibraryWatchedFilter =
            entries.firstOrNull { it.stored == stored } ?: ALL
    }
}

/** Nuvio LibraryLayoutMode: HORIZONTAL rows per section, VERTICAL single section. */
enum class LibraryLayoutMode(val stored: String) {
    ROWS("rows"),
    SINGLE("single");

    companion object {
        fun fromStored(stored: String): LibraryLayoutMode =
            entries.firstOrNull { it.stored == stored } ?: ROWS
    }
}

data class SavedLibraryItem(
    val metaId: String,
    val type: String,
    val name: String,
    val poster: String?,
    val addonId: String,
    /** "watchlist" | "list:<id>" | "trakt" | "simkl". */
    val sectionKey: String,
    val sectionName: String,
    /** 0 for remote (Trakt/Simkl) items, which carry no added date. */
    val addedAt: Long,
    /** 0 = unknown year. */
    val year: Int,
    /** Source order: watchlist first, then lists, then Trakt, then Simkl. */
    val sourceOrder: Int,
)

fun sortSavedItems(items: List<SavedLibraryItem>, sort: LibrarySortOption): List<SavedLibraryItem> {
    val bySource = compareBy<SavedLibraryItem> { it.sourceOrder }.thenByDescending { it.addedAt }
    return when (sort) {
        LibrarySortOption.DEFAULT -> items.sortedWith(bySource)
        // Dated items first (in date order), undated after in source order.
        LibrarySortOption.ADDED_DESC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.addedAt > 0 }
                .thenByDescending { it.addedAt }.thenBy { it.sourceOrder }
        )
        LibrarySortOption.ADDED_ASC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.addedAt > 0 }
                .thenBy { it.addedAt }.thenBy { it.sourceOrder }
        )
        // Known years first (in year order), unknown years last.
        LibrarySortOption.RELEASED_DESC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.year > 0 }
                .thenByDescending { it.year }.thenBy { it.sourceOrder }
        )
        LibrarySortOption.RELEASED_ASC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.year > 0 }
                .thenBy { it.year }.thenBy { it.sourceOrder }
        )
        LibrarySortOption.TITLE_ASC -> items.sortedWith(
            compareBy<SavedLibraryItem> { it.name.lowercase() }.thenBy { it.sourceOrder }
        )
        LibrarySortOption.TITLE_DESC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.name.lowercase() }.thenBy { it.sourceOrder }
        )
    }
}

/**
 * Watched for the filter: a movie is watched when its movie mark exists;
 * a series counts as watched when any of its episodes is marked watched
 * (Plavio stores no per-series completion, so "any episode" is the proxy —
 * partial watches show under Watched, not Unwatched).
 */
fun isSavedItemWatched(item: SavedLibraryItem, watchedKeys: Set<String>): Boolean {
    if (item.type == "movie") return WatchedMarks.movieKey(item.metaId) in watchedKeys
    val prefix = "e|${item.metaId}|"
    return watchedKeys.any { it.startsWith(prefix) }
}

fun filterSavedItems(
    items: List<SavedLibraryItem>,
    filter: LibraryWatchedFilter,
    watchedKeys: Set<String>,
): List<SavedLibraryItem> = when (filter) {
    LibraryWatchedFilter.ALL -> items
    LibraryWatchedFilter.WATCHED -> items.filter { isSavedItemWatched(it, watchedKeys) }
    LibraryWatchedFilter.UNWATCHED -> items.filter { !isSavedItemWatched(it, watchedKeys) }
}

/** First occurrence (in source order) wins per (type, metaId). */
fun dedupeSavedItems(items: List<SavedLibraryItem>): List<SavedLibraryItem> =
    items.distinctBy { "${it.type}|${it.metaId}" }
