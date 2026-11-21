package com.pulsestream.app.data.wetrakr

import com.pulsestream.app.BuildConfig
import com.pulsestream.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the WeTrakr API (https://api.wetrakr.com).
 *
 * Scoped port of Nuvio's features/wetrakr (WeTrakrApiClient +
 * WeTrakrSyncRepository, read side only): device-code OAuth, the
 * planning watchlist, watched episodes and the ratings lists. Nuvio's
 * client id is NOT reused — the user creates their own app at
 * wetrakr.com and pastes its client id (same rule as AniList, Step 52).
 *
 * Entry ids: WeTrakr returns imdb / tmdb / tvdb ids; Plavio uses the
 * IMDb id when present, else "tmdb:<id>" (Cinemeta resolves tmdb: ids).
 */
object WeTrakrApi {

    private const val BASE = "https://api.wetrakr.com"
    private const val DEFAULT_VERIFICATION_URL = "https://wetrakr.com/activate"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val userAgent = "PulseStream/${BuildConfig.VERSION_NAME}"

    data class DeviceData(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSec: Long,
        val expiresInSec: Long
    )

    sealed interface PollResult {
        data class Approved(
            val accessToken: String,
            val refreshToken: String,
            val expiresAtMs: Long
        ) : PollResult

        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Expired : PollResult
        data object Denied : PollResult
        data object Failed : PollResult
    }

    /** One watchlist entry: planning movies/shows. */
    data class Entry(
        val contentId: String,
        val type: String, // "movie" | "series"
        val title: String?,
        val year: Int?
    )

    /** One watched episode, grouped by show. */
    data class WatchedEpisode(
        val showContentId: String,
        val showTitle: String?,
        val season: Int,
        val number: Int,
        val watchedAtMs: Long
    )

    // ------------------------------------------------------------------
    // Auth — device code grant (RFC 8628 shape, WeTrakr flavour)
    // ------------------------------------------------------------------

    /** POST /oauth/device/code — returns null when the request fails. */
    suspend fun requestDeviceCode(clientId: String): DeviceData? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("client_id", clientId).toString()
                .toRequestBody(JSON)
            val req = Request.Builder()
                .url("$BASE/oauth/device/code")
                .header("User-Agent", userAgent)
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val deviceCode = root.optString("device_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val userCode = root.optString("user_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                DeviceData(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUrl = root.optString("verification_url")
                        .takeIf { it.isNotBlank() }
                        ?: root.optString("verification_uri")
                            .takeIf { it.isNotBlank() }
                        ?: DEFAULT_VERIFICATION_URL,
                    intervalSec = root.optLong("interval", 5L).coerceIn(1L, 60L),
                    expiresInSec = root.optLong("expires_in", 600L).coerceIn(60L, 3600L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST /oauth/device/token — poll until Approved / Expired / Denied. */
    suspend fun pollDeviceToken(clientId: String, deviceCode: String): PollResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject()
                    .put("code", deviceCode)
                    .put("device_code", deviceCode)
                    .put("client_id", clientId)
                    .toString().toRequestBody(JSON)
                val req = Request.Builder()
                    .url("$BASE/oauth/device/token")
                    .header("User-Agent", userAgent)
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val root = JSONObject(text)
                        val access = root.optString("access_token")
                            .takeIf { it.isNotBlank() } ?: return@withContext PollResult.Failed
                        val refresh = root.optString("refresh_token")
                            .takeIf { it.isNotBlank() }
                            ?: root.optString("new_refresh_token")
                                .takeIf { it.isNotBlank() }
                            ?: return@withContext PollResult.Failed
                        val expiresAt = root.optLong("expires_in", 0L)
                            .takeIf { it > 0L }
                            ?.let { System.currentTimeMillis() + it * 1000L }
                            ?: 0L
                        return@withContext PollResult.Approved(access, refresh, expiresAt)
                    }
                    val code = runCatching { JSONObject(text).optString("error") }
                        .getOrNull().orEmpty()
                    return@withContext when (resp.code) {
                        400 -> when {
                            code.contains("pending", ignoreCase = true) -> PollResult.Pending
                            code.contains("slow", ignoreCase = true) -> PollResult.SlowDown
                            code.contains("expired", ignoreCase = true) -> PollResult.Expired
                            code.contains("denied", ignoreCase = true) -> PollResult.Denied
                            else -> PollResult.Failed
                        }
                        429 -> PollResult.SlowDown
                        410 -> PollResult.Expired
                        418 -> PollResult.Denied
                        in 500..599 -> PollResult.Pending
                        else -> PollResult.Failed
                    }
                }
            } catch (_: Exception) {
                PollResult.Failed
            }
        }

    /**
     * POST /oauth/token/refresh — swaps a refresh token for a new pair and
     * persists them. Returns the new access token, or null on failure.
     */
    suspend fun refreshAccessToken(settings: SettingsStore): String? =
        withContext(Dispatchers.IO) {
            val refreshToken = settings.wetrakrRefreshToken?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            try {
                val body = JSONObject().put("refresh_token", refreshToken).toString()
                    .toRequestBody(JSON)
                val req = Request.Builder()
                    .url("$BASE/oauth/token/refresh")
                    .header("User-Agent", userAgent)
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val access = root.optString("access_token").takeIf { it.isNotBlank() }
                        ?: return@withContext null
                    val refresh = root.optString("refresh_token")
                        .takeIf { it.isNotBlank() }
                        ?: root.optString("new_refresh_token")
                            .takeIf { it.isNotBlank() }
                        ?: refreshToken
                    settings.wetrakrAccessToken = access
                    settings.wetrakrRefreshToken = refresh
                    settings.wetrakrTokenExpiresAtMs = root.optLong("expires_in", 0L)
                        .takeIf { it > 0L }
                        ?.let { System.currentTimeMillis() + it * 1000L }
                        ?: 0L
                    access
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * A usable access token: refreshes first when the stored one is past
     * its expiry (with a 60s safety margin). Returns null when not linked
     * or when the refresh fails.
     */
    suspend fun ensureAccessToken(settings: SettingsStore): String? {
        val token = settings.wetrakrAccessToken?.takeIf { it.isNotBlank() }
            ?: return null
        val expiresAt = settings.wetrakrTokenExpiresAtMs
        if (expiresAt > 0L && System.currentTimeMillis() + 60_000L >= expiresAt) {
            return refreshAccessToken(settings)
        }
        return token
    }

    /** GET /account/settings — the linked username, or null. */
    suspend fun fetchUsername(settings: SettingsStore): String? {
        val token = ensureAccessToken(settings) ?: return null
        return get(settings, "/account/settings", token)?.let { root ->
            val info = root.optJSONObject("info")
            info?.optString("username")?.takeIf { it.isNotBlank() }
                ?: root.optString("username").takeIf { it.isNotBlank() }
                ?: root.optJSONObject("user")?.optString("username")
                    ?.takeIf { it.isNotBlank() }
        }
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /**
     * Planning watchlist: GET /sync/tracking/planning/movies|shows
     * (extended=movie_level_1 / show_level_1, paged via
     * x-pagination-page-count). Movies and shows merged, cap 100.
     */
    suspend fun getWatchlist(settings: SettingsStore, limit: Int = 100): List<Entry> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val out = mutableListOf<Entry>()
        for ((path, type) in listOf(
            "/sync/tracking/planning/movies" to "movie",
            "/sync/tracking/planning/shows" to "series"
        )) {
            var page = 1
            while (out.size < limit && page <= 10) {
                val query = listOf(
                    "extended" to if (type == "movie") "movie_level_1" else "show_level_1",
                    "page" to page.toString(),
                    "limit" to "100"
                )
                val (items, pageCount) = getPaged(settings, path, query, token)
                    ?: break
                for (row in items) parseEntry(row, type)?.let { out += it }
                if (pageCount != null && page >= pageCount) break
                if (items.isEmpty()) break
                page++
            }
        }
        return out.take(limit)
    }

    /**
     * Watched episodes: GET /sync/tracking/watched/episodes?compact=true
     * (cursor via x-pagination-next), grouped by show to the latest
     * watched episode — the caller derives next = number + 1.
     */
    suspend fun getWatchedEpisodes(settings: SettingsStore): List<WatchedEpisode> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val rows = mutableListOf<JSONObject>()
        var after: String? = null
        var guard = 0
        while (guard < 10) {
            guard++
            val query = buildList {
                add("compact" to "true")
                add("limit" to "100")
                after?.let { add("after" to it) }
            }
            val (items, next) = getCompact(settings, "/sync/tracking/watched/episodes", query, token)
                ?: break
            rows += items
            if (items.isEmpty() || next == null || next == after) break
            after = next
        }
        return rows.mapNotNull(::parseWatchedEpisode)
            .groupBy { Triple(it.showContentId, it.season, it.number) }
            .map { (_, group) -> group.maxBy { it.watchedAtMs } }
    }

    /**
     * Ratings: GET /sync/ratings/movies|shows (compact) — 1..10, keyed by
     * the same content id as the watchlist entries.
     */
    suspend fun getRatings(settings: SettingsStore): Map<String, Int> {
        val token = ensureAccessToken(settings) ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        for (path in listOf("/sync/ratings/movies", "/sync/ratings/shows")) {
            val (items, _) = getCompact(settings, path, listOf("compact" to "true", "limit" to "100"), token)
                ?: continue
            for (row in items) {
                if (row.optString("status").equals("removed", ignoreCase = true)) continue
                val value = row.optDouble("rating", Double.NaN)
                    .takeIf { !it.isNaN() }?.toInt()
                    ?: row.optJSONObject("interactions")?.optJSONObject("user")
                        ?.optDouble("rating", Double.NaN)?.takeIf { !it.isNaN() }?.toInt()
                    ?: continue
                val ids = row.optJSONObject("ids") ?: continue
                contentId(ids)?.let { out[it] = value.coerceIn(1, 10) }
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Parsing (mirrors Nuvio's parseEntry / parseWatchedEpisode)
    // ------------------------------------------------------------------

    private fun parseEntry(row: JSONObject, type: String): Entry? {
        val media = parseMedia(row) ?: parseMedia(row.optJSONObject(type))
            ?: return null
        val contentId = media.contentId ?: return null
        return Entry(
            contentId = contentId,
            type = type,
            title = media.title,
            year = media.year
        )
    }

    private data class Media(
        val contentId: String?,
        val title: String?,
        val year: Int?
    )

    private fun parseMedia(obj: JSONObject?): Media? {
        obj ?: return null
        obj.optLong("id", 0L).takeIf { it > 0L } ?: return null
        val ids = obj.optJSONObject("ids")
        val contentId = contentId(ids)
        val year = obj.optInt("year", 0).takeIf { it > 0 }
            ?: listOf("release_date", "first_air_date", "air_date", "released")
                .firstNotNullOfOrNull { key ->
                    obj.optString(key).take(4).toIntOrNull()
                }
        return Media(
            contentId = contentId,
            title = obj.optString("title").takeIf { it.isNotBlank() },
            year = year
        )
    }

    private fun parseWatchedEpisode(row: JSONObject): WatchedEpisode? {
        val show = row.optJSONObject("show")
        val season = row.optInt("season_number", -1).takeIf { it >= 0 }
            ?: row.optInt("season", -1).takeIf { it >= 0 } ?: return null
        val number = row.optInt("number", -1).takeIf { it >= 0 }
            ?: row.optInt("episode_number", -1).takeIf { it >= 0 } ?: return null
        val showIds = row.optJSONObject("show_ids")
            ?: show?.optJSONObject("ids")
        val showContentId = contentId(showIds) ?: return null
        val watchedAt = row.optString("watched_at").takeIf { it.isNotBlank() }
            ?.let { parseIsoMs(it) }
            ?: row.optString("last_watched_at").takeIf { it.isNotBlank() }
                ?.let { parseIsoMs(it) }
            ?: row.optString("tracked_at").takeIf { it.isNotBlank() }
                ?.let { parseIsoMs(it) } ?: 0L
        return WatchedEpisode(
            showContentId = showContentId,
            showTitle = show?.optString("title")?.takeIf { it.isNotBlank() },
            season = season,
            number = number,
            watchedAtMs = watchedAt
        )
    }

    /** IMDb id when present, else "tmdb:<id>" (Cinemeta resolves tmdb: ids). */
    private fun contentId(ids: JSONObject?): String? {
        ids ?: return null
        val imdb = ids.optString("imdb").takeIf { it.startsWith("tt", ignoreCase = true) }
        if (imdb != null) return imdb
        val tmdb = ids.optLong("tmdb", 0L).takeIf { it > 0L }
        if (tmdb != null) return "tmdb:$tmdb"
        val tvdb = ids.optLong("tvdb", 0L).takeIf { it > 0L }
        if (tvdb != null) return "tvdb:$tvdb"
        return null
    }

    private fun parseIsoMs(value: String): Long = runCatching {
        java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
    }.getOrElse {
        value.toLongOrNull()?.let { if (it < 10_000_000_000L) it * 1000L else it } ?: 0L
    }

    // ------------------------------------------------------------------
    // HTTP plumbing
    // ------------------------------------------------------------------

    private suspend fun get(
        settings: SettingsStore,
        path: String,
        token: String
    ): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$BASE$path")
                .header("Authorization", "Bearer $token")
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
            }
        } catch (_: Exception) {
            null
        }
    }

    /** One GET returning (items, x-pagination-page-count). */
    private suspend fun getPaged(
        settings: SettingsStore,
        path: String,
        query: List<Pair<String, String>>,
        token: String
    ): Pair<List<JSONObject>, Int?>? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(BASE).append(path).append("?")
                query.forEachIndexed { i, (k, v) ->
                    if (i > 0) append("&")
                    append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
                }
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: JSONArray()
                val list = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                val pageCount = resp.header("x-pagination-page-count")?.toIntOrNull()
                list to pageCount
            }
        } catch (_: Exception) {
            null
        }
    }

    /** One GET returning (items, x-pagination-next cursor). */
    private suspend fun getCompact(
        settings: SettingsStore,
        path: String,
        query: List<Pair<String, String>>,
        token: String
    ): Pair<List<JSONObject>, String?>? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(BASE).append(path).append("?")
                query.forEachIndexed { i, (k, v) ->
                    if (i > 0) append("&")
                    append(k).append("=").append(java.net.URLEncoder.encode(v, "UTF-8"))
                }
            }
            val req = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", userAgent)
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                val root = JSONObject(body)
                val items = root.optJSONArray("items")
                    ?: root.optJSONArray("data")
                    ?: JSONArray()
                val list = (0 until items.length()).mapNotNull { items.optJSONObject(it) }
                list to resp.header("x-pagination-next")
            }
        } catch (_: Exception) {
            null
        }
    }
}
