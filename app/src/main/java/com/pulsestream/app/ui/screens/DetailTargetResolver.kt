package com.pulsestream.app.ui.screens

import com.pulsestream.app.data.StreamRepository

data class DetailTarget(val addonId: String, val type: String, val metaId: String)
data class TmdbReference(val mediaType: String, val tmdbId: String)

/** Accepts both TMDB credit IDs and raw TMDB media IDs, normalizing their type for app routes. */
internal fun parseTmdbReference(metaId: String, type: String): TmdbReference? {
    val match = Regex("^tmdb:(movie|tv|series):([0-9]+)$", RegexOption.IGNORE_CASE).matchEntire(metaId.trim())
        ?: return null
    val mediaType = when (match.groupValues[1].lowercase()) {
        "movie" -> "movie"
        else -> "tv"
    }
    return TmdbReference(mediaType, match.groupValues[2])
}

internal fun normalizeDetailType(type: String): String =
    if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()

/**
 * TMDB-native IDs resolve to Cinemeta (the default meta source): the id
 * is converted to its IMDb id via the user's TMDB key inside
 * [StreamRepository.getMetaFromAny], and Cinemeta serves the meta from
 * there. Shared by search, actor filmography, related titles, and future
 * entry points.
 */
suspend fun resolveDetailTarget(
    repository: StreamRepository,
    addonId: String,
    type: String,
    metaId: String
): DetailTarget {
    val reference = parseTmdbReference(metaId, type)
        ?: return DetailTarget(addonId, normalizeDetailType(type), metaId)
    val resolvedType = if (reference.mediaType == "movie") "movie" else "series"
    return DetailTarget("com.linvo.cinemeta", resolvedType, metaId)
}
