package com.pulsestream.app.data.publicmetadb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for PublicMetaDB (https://publicmetadb.com).
 *
 * Scoped port of Nuvio's features/publicmetadb (PublicMetaDbApiClient +
 * PublicMetaDbSyncRepository, read side only): API-key auth, the user's
 * lists + list items, watched entries and resume points. Everything is
 * keyed by TMDB id — Plavio resolves those to IMDb ids through its own
 * TmdbClient (Step 6 key) and falls back to "tmdb:movie:<id>" ids,
 * which Cinemeta resolves.
 */
object PublicMetaDbApi {

    private const val BASE = "https://publicmetadb.com"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class PmdbList(
        val id: String,
        val name: String
    )

    data class PmdbItem(
        val tmdbId: Long,
        val mediaType: String, // "movie" | "tv"
        val title: String?,
        val year: Int?,
        val posterPath: String?
    )

    data class PmdbWatched(
        val tmdbId: Long,
        val mediaType: String, // "movie" | "tv"
        val season: Int?,
        val episode: Int?
    )

    data class PmdbResume(
        val tmdbId: Long,
        val mediaType: String, // "movie" | "tv"
        val season: Int?,
        val episode: Int?,
        val progress: Float?, // 0..1 when known
        val updatedAtMs: Long
    )

    /** GET /api/external/lists?page=1&perPage=1 — 200 means the key works. */
    suspend fun verify(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE/api/external/lists?page=1&perPage=1")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** GET /api/external/lists (paged). */
    suspend fun getLists(apiKey: String): List<PmdbList> {
        val out = mutableListOf<PmdbList>()
        var page = 1
        while (page <= 10) {
            val items = getPage(apiKey, "/api/external/lists", page, 100) ?: break
            for (row in items) {
                val id = row.firstString("id") ?: continue
                val name = row.firstString("name", "title") ?: "List"
                out += PmdbList(id = id, name = name)
            }
            if (items.size < 100) break
            page++
        }
        return out.distinctBy { it.id }
    }

    /** GET /api/external/lists/{id}/items (paged). */
    suspend fun getListItems(apiKey: String, listId: String): List<PmdbItem> {
        val out = mutableListOf<PmdbItem>()
        var page = 1
        while (page <= 10) {
            val segment = java.net.URLEncoder.encode(listId, "UTF-8")
            val items = getPage(apiKey, "/api/external/lists/$segment/items", page, 100)
                ?: break
            for (row in items) {
                val mediaType = normalizeMediaType(
                    row.firstString("media_type", "mediaType")
                        ?: row.optJSONObject("media")?.firstString("media_type", "type")
                )
                val tmdbId = tmdbId(row, mediaType) ?: continue
                val media = row.optJSONObject("media")
                out += PmdbItem(
                    tmdbId = tmdbId,
                    mediaType = mediaType,
                    title = row.firstString("title", "name")
                        ?: media?.firstString("title", "name"),
                    year = row.optInt("year", 0).takeIf { it > 0 }
                        ?: row.firstString("release_date", "first_air_date")
                            ?.take(4)?.toIntOrNull(),
                    posterPath = row.firstString("poster", "poster_path")
                        ?: media?.firstString("poster", "poster_path")
                )
            }
            if (items.size < 100) break
            page++
        }
        return out.distinctBy { "${it.mediaType}:${it.tmdbId}" }
    }

    /** GET /api/external/watched (paged). */
    suspend fun getWatched(apiKey: String): List<PmdbWatched> {
        val out = mutableListOf<PmdbWatched>()
        var page = 1
        while (page <= 10) {
            val items = getPage(apiKey, "/api/external/watched", page, 100) ?: break
            for (row in items) {
                val season = row.firstInt("season", "season_number")
                val episode = row.firstInt("episode", "episode_number")
                val mediaType = when {
                    row.firstString("media_type", "mediaType") != null ->
                        normalizeMediaType(row.firstString("media_type", "mediaType"))
                    episode != null -> "tv"
                    else -> "movie"
                }
                val tmdbId = tmdbId(row, mediaType) ?: continue
                if (mediaType == "tv" && (season == null || episode == null || episode <= 0)) continue
                out += PmdbWatched(tmdbId, mediaType, season, episode)
            }
            if (items.size < 100) break
            page++
        }
        return out
    }

    /** GET /api/external/resume (paged). */
    suspend fun getResume(apiKey: String): List<PmdbResume> {
        val out = mutableListOf<PmdbResume>()
        var page = 1
        while (page <= 10) {
            val items = getPage(apiKey, "/api/external/resume", page, 100) ?: break
            for (row in items) {
                val season = row.firstInt("season", "season_number")
                val episode = row.firstInt("episode", "episode_number")
                val mediaType = when {
                    row.firstString("media_type", "mediaType") != null ->
                        normalizeMediaType(row.firstString("media_type", "mediaType"))
                    episode != null && episode > 0 -> "tv"
                    else -> "movie"
                }
                val tmdbId = tmdbId(row, mediaType) ?: continue
                if (mediaType == "tv" && (season == null || episode == null || episode <= 0)) continue
                val position = row.optLong("position_ms", 0L).takeIf { it > 0 }
                    ?: row.optLong("progress_ms", 0L).takeIf { it > 0 }
                val runtime = row.optLong("runtime_ms", 0L).takeIf { it > 0 }
                    ?: row.optLong("duration_ms", 0L).takeIf { it > 0 }
                val progress = when {
                    position != null && runtime != null && runtime > 0 ->
                        (position.toFloat() / runtime.toFloat()).coerceIn(0f, 1f)
                    else -> row.optDouble("progress", Double.NaN)
                        .takeIf { !it.isNaN() }?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
                }
                val updatedAt = row.firstString("updated_at", "updated", "created_at")
                    ?.let { parseIsoMs(it) } ?: 0L
                out += PmdbResume(tmdbId, mediaType, season, episode, progress, updatedAt)
            }
            if (items.size < 100) break
            page++
        }
        return out
    }

    // ------------------------------------------------------------------
    // Parsing helpers (mirrors Nuvio's publicMetaDb* helpers)
    // ------------------------------------------------------------------

    private fun normalizeMediaType(raw: String?): String = when (raw?.lowercase()) {
        "tv", "show", "series" -> "tv"
        else -> "movie"
    }

    private fun tmdbId(row: JSONObject, mediaType: String): Long? {
        fun coerce(element: Any?): Long? = when (element) {
            is Number -> element.toLong().takeIf { it > 0L }
            is String -> element.trim().toLongOrNull()?.takeIf { it > 0L }
                ?: element.trim().toDoubleOrNull()?.toLong()?.takeIf { it > 0L }
            is JSONObject -> {
                val keys = when (mediaType) {
                    "tv" -> listOf("tv", "show", "series")
                    else -> listOf("movie", "movies")
                } + listOf("tv", "show", "series", "movie", "movies")
                keys.firstNotNullOfOrNull { coerce(element.opt(it)) }
            }
            else -> null
        }
        return coerce(row.opt("tmdb_id")) ?: coerce(row.opt("tmdbId"))
            ?: coerce(row.opt("tmdb"))
            ?: row.optJSONObject("ids")?.let { coerce(it.opt("tmdb")) }
            ?: row.optJSONObject("media")?.let {
                coerce(it.opt("tmdb_id")) ?: coerce(it.opt("tmdb"))
            }
    }

    private fun parseIsoMs(value: String): Long = runCatching {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrElse {
        value.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it } ?: 0L
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private fun JSONObject.firstString(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            optString(key).takeIf { it.isNotBlank() }
        }

    private fun JSONObject.firstInt(vararg keys: String): Int? =
        keys.firstNotNullOfOrNull { key ->
            if (has(key) && !isNull(key)) optInt(key, 0).takeIf { it != 0 } else null
        }

    private suspend fun getPage(
        apiKey: String,
        path: String,
        page: Int,
        perPage: Int
    ): List<JSONObject>? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE$path?page=$page&perPage=$perPage")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val items: JSONArray = root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("results")
                    ?: JSONArray()
                (0 until items.length()).mapNotNull { items.optJSONObject(it) }
            }
        } catch (_: Exception) {
            null
        }
    }
}
