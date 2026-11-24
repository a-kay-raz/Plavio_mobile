package com.aviostream.app.data.plugs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap

/**
 * IMDb → TMDB id resolution via TMDB's /find endpoint, using the
 * user's own TMDB key when one is configured (the Integrations
 * enrichment key). Returns null without a key or on any failure —
 * plugs that need a TMDB id then simply don't run for that title.
 * (Moved verbatim from PlugService.kt in v0.14.57 so the plug leg's
 * resolver below — and the JVM harness — can use it without the
 * Android-bound service class.)
 */
object TmdbFind {
    suspend fun resolve(apiKey: String, imdbId: String, appType: String): Int? {
        val bearer = apiKey.startsWith("eyJ") || apiKey.length > 40
        val url = StringBuilder("https://api.themoviedb.org/3/find/$imdbId?external_source=imdb_id")
        val headers = HashMap<String, String>()
        if (bearer) {
            headers["Authorization"] = "Bearer $apiKey"
            headers["Accept"] = "application/json"
        } else {
            url.append("&api_key=").append(apiKey)
        }
        val resp = runCatching { PlugHttp.fetch(url.toString(), headers = headers) }.getOrNull() ?: return null
        if (!resp.ok) return null
        val root = runCatching { Json.parseToJsonElement(resp.body).jsonObject }.getOrNull() ?: return null
        val key = if (appType == "series") "tv_results" else "movie_results"
        val first = (root[key] as? JsonArray)?.firstOrNull() as? JsonObject ?: return null
        return (first["id"] as? JsonPrimitive)?.content?.toIntOrNull()
    }
}

/**
 * Per-title TMDB-id resolution for JS plugs (v0.14.57).
 *
 * Root cause this fixes: JS plugs take a TMDB id as their input,
 * but titles opened from addon catalogs usually carry only an IMDb
 * id, so the plug leg reached [JsPlugHost] with a null TMDB id and
 * every JS plug was skipped before it started ("no TMDB id for
 * this title — JS plugs cannot run", the owner's .56 phone
 * verdict). The repository's context builder already tries a find
 * as a last resort; this resolver is the plug layer's own pass,
 * run before any JS plug starts, so the guarantee holds for every
 * caller of [PlugService.getStreams].
 *
 * The mapping is cached per title for the session in this class —
 * alongside the service's existing per-title result cache, not a
 * new store: repeat opens of a title never re-ask TMDB, and a
 * title TMDB cannot resolve is not re-asked either. The one case
 * NOT cached is "no key configured" — that is a settings state,
 * not a per-title answer, and a key added mid-session must work
 * on the next open.
 *
 * v0.14.58: the key find is now the LAST resort, not the only
 * path. The owner's .57 phone verdict was that every JS plug sat
 * out with "Could not resolve TMDB id for this title" on a phone
 * with no Integrations TMDB key pasted — most users never set
 * one, so plugs must not depend on it. The title's own metadata
 * usually carries the id already (the TMDB source link in the
 * meta's links, distilled by the meta parser into
 * MetaDetail.tmdbId); the repository feeds it into the plug
 * context directly and seeds this cache via [remember], and
 * [resolveTmdbId] takes it as its meta step before the find.
 */
class TmdbIdResolver(
    private val keyProvider: () -> String?,
    private val find: suspend (apiKey: String, imdbId: String, appType: String) -> Int? =
        { apiKey, imdbId, appType -> TmdbFind.resolve(apiKey, imdbId, appType) }
) {
    private val hits = ConcurrentHashMap<String, Int>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    /**
     * Seeds the session cache with a mapping learned elsewhere —
     * v0.14.58: the id the title's own metadata carried (see
     * [resolveTmdbId]'s meta step). A seeded hit behaves exactly
     * like a find hit: later resolves for the title serve from the
     * cache and never re-ask. Also clears any recorded miss — the
     * meta just proved the title resolvable.
     */
    fun remember(imdbId: String, appType: String, tmdbId: Int) {
        val cacheKey = "$appType|$imdbId"
        hits[cacheKey] = tmdbId
        misses.remove(cacheKey)
    }

    /**
     * The title's TMDB id, in the v0.14.58 resolution order:
     * (a) the one the title already carries; (b) the one its own
     * metadata carried ([metaTmdbId] — the TMDB source link rich
     * meta addons serve, distilled by the meta parser; needs NO
     * key, which is the point: plugs must not depend on the user
     * having pasted one); (c) the key-based TMDB find (v0.14.57).
     * Else null (no IMDb id, no meta id, no key, or TMDB has no
     * match). Never throws.
     */
    suspend fun resolveTmdbId(ctx: PlugTitleContext, metaTmdbId: Int? = null): Int? {
        if (ctx.tmdbId != null) return ctx.tmdbId
        val imdbId = ctx.imdbId ?: return null
        val cacheKey = "${ctx.appType}|$imdbId"
        hits[cacheKey]?.let { return it }
        if (metaTmdbId != null && metaTmdbId > 0) {
            remember(imdbId, ctx.appType, metaTmdbId)
            return metaTmdbId
        }
        if (cacheKey in misses) return null
        val apiKey = keyProvider()?.takeIf { it.isNotBlank() } ?: return null
        val resolved = runCatching { find(apiKey, imdbId, ctx.appType) }.getOrNull()
        if (resolved != null) {
            hits[cacheKey] = resolved
        } else {
            misses += cacheKey
        }
        return resolved
    }

    /** [ctx] with its TMDB id filled in, or null when unresolvable. */
    suspend fun withResolvedTmdbId(
        ctx: PlugTitleContext,
        metaTmdbId: Int? = null
    ): PlugTitleContext? {
        val id = resolveTmdbId(ctx, metaTmdbId) ?: return null
        return if (ctx.tmdbId == id) ctx else ctx.copy(tmdbId = id)
    }
}
