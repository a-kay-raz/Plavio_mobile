package com.pulsestream.app.data.tracking

import com.pulsestream.app.data.SettingsStore

/**
 * Step 52: unified tracking layer (Nuvio tracking/TrackingSources.kt +
 * TrackingProvider.kt, scoped port). One picker per concern — library,
 * progress, ratings — over the linked services. "all" keeps the
 * pre-Step-52 behaviour (every linked service feeds the view); a single
 * source restricts to that service when it is linked, and falls back to
 * all linked services when it is not (Nuvio's effective*Source fallback).
 */
enum class TrackingLibrarySource(val stored: String, val label: String) {
    ALL("all", "All linked services"),
    LOCAL("local", "Local only"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "Simkl"),
    ANILIST("anilist", "AniList");

    companion object {
        fun fromStored(value: String?): TrackingLibrarySource =
            entries.firstOrNull { it.stored == value } ?: ALL
    }
}

enum class TrackingProgressSource(val stored: String, val label: String) {
    ALL("all", "All linked services"),
    LOCAL("local", "Local only"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "Simkl"),
    ANILIST("anilist", "AniList");

    companion object {
        fun fromStored(value: String?): TrackingProgressSource =
            entries.firstOrNull { it.stored == value } ?: ALL
    }
}

/** Simkl has no ratings API in Plavio, so the ratings picker is Trakt / AniList only. */
enum class TrackingRatingsSource(val stored: String, val label: String) {
    TRAKT("trakt", "Trakt"),
    ANILIST("anilist", "AniList");

    companion object {
        fun fromStored(value: String?): TrackingRatingsSource =
            entries.firstOrNull { it.stored == value } ?: TRAKT
    }
}

fun SettingsStore.isTraktLinked(): Boolean = !traktAccessToken.isNullOrBlank()

fun SettingsStore.isSimklLinked(): Boolean = !simklToken.isNullOrBlank()

fun SettingsStore.isAnilistLinked(): Boolean = !anilistAccessToken.isNullOrBlank()

/** Services feeding the Library for the current library-source setting. */
fun SettingsStore.libraryProviders(): Set<String> {
    val linked = linkedProviders()
    return when (val source = TrackingLibrarySource.fromStored(trackingLibrarySource)) {
        TrackingLibrarySource.ALL -> linked
        TrackingLibrarySource.LOCAL -> emptySet()
        TrackingLibrarySource.TRAKT -> if ("trakt" in linked) setOf("trakt") else linked
        TrackingLibrarySource.SIMKL -> if ("simkl" in linked) setOf("simkl") else linked
        TrackingLibrarySource.ANILIST -> if ("anilist" in linked) setOf("anilist") else linked
    }
}

/** Services feeding Up Next progress for the current progress-source setting. */
fun SettingsStore.progressProviders(): Set<String> {
    val linked = linkedProviders()
    return when (val source = TrackingProgressSource.fromStored(trackingProgressSource)) {
        TrackingProgressSource.ALL -> linked
        TrackingProgressSource.LOCAL -> emptySet()
        TrackingProgressSource.TRAKT -> if ("trakt" in linked) setOf("trakt") else linked
        TrackingProgressSource.SIMKL -> if ("simkl" in linked) setOf("simkl") else linked
        TrackingProgressSource.ANILIST -> if ("anilist" in linked) setOf("anilist") else linked
    }
}

/** Effective ratings source: AniList only when it is actually linked. */
fun SettingsStore.effectiveRatingsSource(): TrackingRatingsSource {
    val source = TrackingRatingsSource.fromStored(trackingRatingsSource)
    return if (source == TrackingRatingsSource.ANILIST && !isAnilistLinked()) {
        TrackingRatingsSource.TRAKT
    } else source
}

private fun SettingsStore.linkedProviders(): Set<String> = buildSet {
    if (isTraktLinked()) add("trakt")
    if (isSimklLinked()) add("simkl")
    if (isAnilistLinked()) add("anilist")
}
