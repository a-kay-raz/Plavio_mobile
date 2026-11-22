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

/** First ISO date (yyyy-MM-dd) in a releaseInfo string, else null. */
fun parseReleaseDate(releaseInfo: String?): String? {
    if (releaseInfo.isNullOrBlank()) return null
    return Regex("(?:19|20)\\d{2}-\\d{2}-\\d{2}").find(releaseInfo)?.value
}

/** Epoch day for a full date ("2024-03-01"); a bare year becomes Jan 1 of that year; else null. */
fun releaseEpochDay(releaseDate: String?, year: Int): Long? {
    releaseDate?.let {
        runCatching { java.time.LocalDate.parse(it).toEpochDay() }.getOrNull()?.let { return it }
    }
    if (year > 0) {
        runCatching { java.time.LocalDate.of(year, 1, 1).toEpochDay() }.getOrNull()?.let { return it }
    }
    return null
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
    /** Full ISO release date ("2024-03-01") when the source sends one; null = year only / unknown. */
    val releaseDate: String? = null,
    /** Genres from the meta fetch (remote items only; local items carry none). */
    val genres: List<String> = emptyList(),
) {
    /** Epoch day for the Released sort: full date when known, else Jan 1 of the year, else null. */
    val releaseEpochDay: Long? get() = releaseEpochDay(releaseDate, year)
}

/**
 * Nuvio sort gating (LibraryDisplaySettings.availableLibrarySortOptions):
 * MDBList offers all 7 (not a Plavio source); a single remote source
 * (Trakt/Simkl) offers 5 (no Released sorts — remote lists carry no
 * release dates); Local offers 4 (no Default, no Released sorts). The
 * merged "all" source offers all 7 — local items carry added dates and
 * remote items carry release dates, so every sort works.
 */
fun availableLibrarySortOptions(librarySource: String): List<LibrarySortOption> = when (librarySource) {
    "local" -> LibrarySortOption.entries.filterNot {
        it == LibrarySortOption.DEFAULT ||
            it == LibrarySortOption.RELEASED_DESC || it == LibrarySortOption.RELEASED_ASC
    }
    "trakt", "simkl" -> LibrarySortOption.entries.filterNot {
        it == LibrarySortOption.RELEASED_DESC || it == LibrarySortOption.RELEASED_ASC
    }
    else -> LibrarySortOption.entries
}

/** A stored sort the current source does not offer falls back like Nuvio: remote -> Default, else Added-newest. */
fun effectiveLibrarySortOption(selected: LibrarySortOption, librarySource: String): LibrarySortOption {
    if (selected in availableLibrarySortOptions(librarySource)) return selected
    return if (librarySource == "trakt" || librarySource == "simkl") LibrarySortOption.DEFAULT
    else LibrarySortOption.ADDED_DESC
}

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
        // Released sorts use the FULL date (epoch day) when known — a
        // bare year falls back to Jan 1 of that year. Undated items sort
        // last, in source order (Nuvio: nulls last on RELEASED_ASC).
        LibrarySortOption.RELEASED_DESC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.releaseEpochDay != null }
                .thenByDescending { it.releaseEpochDay }.thenBy { it.sourceOrder }
        )
        LibrarySortOption.RELEASED_ASC -> items.sortedWith(
            compareByDescending<SavedLibraryItem> { it.releaseEpochDay != null }
                .thenBy { it.releaseEpochDay }.thenBy { it.sourceOrder }
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

/** One facet option with its count, rendered as "Label (count)" (Nuvio libraryFacetOptions). */
data class LibraryFacetOption(val key: String, val label: String, val count: Int)

/** Vertical-mode facets (Nuvio LibraryVerticalProjection): type / genre / year with counts. */
data class LibraryFacets(
    val types: List<LibraryFacetOption>,
    val genres: List<LibraryFacetOption>,
    val years: List<LibraryFacetOption>,
)

/** Counts are computed from the watched-filtered items, so they always match what is on screen. */
fun computeLibraryFacets(items: List<SavedLibraryItem>): LibraryFacets {
    val types = items.groupingBy { it.type }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { (type, count) ->
            LibraryFacetOption(type, if (type == "series") "Series" else "Movies", count)
        }
    val genres = items.flatMap { it.genres }.groupingBy { it }.eachCount()
        .entries.sortedByDescending { it.value }
        .map { (genre, count) -> LibraryFacetOption(genre, genre, count) }
    val years = items.filter { it.year > 0 }.groupingBy { it.year }.eachCount()
        .entries.sortedByDescending { it.key }
        .map { (year, count) -> LibraryFacetOption(year.toString(), year.toString(), count) }
    return LibraryFacets(types, genres, years)
}

/** Facet filtering (vertical mode only): type, genre and year combine (AND). Null = all. */
fun filterSavedItemsByFacets(
    items: List<SavedLibraryItem>,
    type: String?,
    genre: String?,
    year: Int?,
): List<SavedLibraryItem> = items.filter { item ->
    (type == null || item.type == type) &&
        (genre == null || genre in item.genres) &&
        (year == null || item.year == year)
}
