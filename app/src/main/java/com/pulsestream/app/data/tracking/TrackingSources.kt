package com.pulsestream.app.data.tracking

import com.pulsestream.app.data.SettingsStore

/**
 * Step 52: unified tracking layer (Nuvio tracking/TrackingSources.kt +
 * TrackingProvider.kt, scoped port). One picker per concern — library and
 * progress — over the linked services. "all" keeps the pre-Step-52
 * behaviour (every linked service feeds the view); a single source
 * restricts to that service when it is linked, and falls back to all
 * linked services when it is not (Nuvio's effective*Source fallback).
 *
 * 2026-11-22: the ported providers (AniList / WeTrakr / PublicMetaDB /
 * MDBList) were removed on the user's instruction — only the pre-existing
 * Trakt + Simkl services remain. The ratings picker went with them:
 * Detail always shows the Trakt community rating now.
 */
enum class TrackingLibrarySource(val stored: String, val label: String) {
    ALL("all", "All linked services"),
    LOCAL("local", "Local only"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "Simkl");

    companion object {
        fun fromStored(value: String?): TrackingLibrarySource =
            entries.firstOrNull { it.stored == value } ?: ALL
    }
}

enum class TrackingProgressSource(val stored: String, val label: String) {
    ALL("all", "All linked services"),
    LOCAL("local", "Local only"),
    TRAKT("trakt", "Trakt"),
    SIMKL("simkl", "Simkl");

    companion object {
        fun fromStored(value: String?): TrackingProgressSource =
            entries.firstOrNull { it.stored == value } ?: ALL
    }
}

fun SettingsStore.isTraktLinked(): Boolean = !traktAccessToken.isNullOrBlank()

fun SettingsStore.isSimklLinked(): Boolean = !simklToken.isNullOrBlank()

/** Services feeding the Library for the current library-source setting. */
fun SettingsStore.libraryProviders(): Set<String> {
    val linked = linkedProviders()
    return when (val source = TrackingLibrarySource.fromStored(trackingLibrarySource)) {
        TrackingLibrarySource.ALL -> linked
        TrackingLibrarySource.LOCAL -> emptySet()
        TrackingLibrarySource.TRAKT -> if ("trakt" in linked) setOf("trakt") else linked
        TrackingLibrarySource.SIMKL -> if ("simkl" in linked) setOf("simkl") else linked
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
    }
}

private fun SettingsStore.linkedProviders(): Set<String> = buildSet {
    if (isTraktLinked()) add("trakt")
    if (isSimklLinked()) add("simkl")
}
