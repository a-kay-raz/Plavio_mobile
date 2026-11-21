package com.pulsestream.app.data.mdblist

import com.pulsestream.app.BuildConfig
import com.pulsestream.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the MDBList API (https://api.mdblist.com).
 *
 * Scoped port of Nuvio's features/mdblist (MdbListApiClient +
 * MdbListAuthRepository + MdbListSyncRemote + MdbListRatingsProvider,
 * read side only): device-code OAuth, the user's lists + watchlist,
 * watched history and the user's own ratings. Nuvio's client id is NOT
 * reused — the user creates their own app and pastes its client id
 * (same rule as WeTrakr, Step 53).
 *
 * Entry ids: MDBList returns imdb / tmdb / tvdb ids; Plavio uses the
 * IMDb id when present, else "tmdb:<id>" (Cinemeta resolves tmdb: ids).
 */
object MdbListApi {

    private const val BASE = "https://api.mdblist.com"

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

    data class MdbList(
        val id: Long,
        val name: String
    )

    /** One library entry: a movie or a show. */
    data class Entry(
        val contentId: String,
        val type: String, // "movie" | "series"
        val title: String?,
        val year: Int?
    )

    /** One watched episode. */
    data class WatchedEpisode(
        val showContentId: String,
        val showTitle: String?,
        val season: Int,
        val number: Int,
        val watchedAtMs: Long
    )

    // ------------------------------------------------------------------
    // Auth — device code grant
    // ------------------------------------------------------------------

    /** POST /oauth/device-authorization/ — returns null when it fails. */
    suspend fun requestDeviceCode(clientId: String): DeviceData? = withContext(Dispatchers.IO) {
        try {
            val form = FormBody.Builder()
                .add("scope", "write")
                .add("client_id", clientId)
                .build()
            val req = Request.Builder()
                .url("$BASE/oauth/device-authorization/")
                .header("User-Agent", userAgent)
                .post(form)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val deviceCode = root.optString("device_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val userCode = root.optString("user_code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val uri = root.optString("verification_uri").takeIf { it.isNotBlank() }
                    ?: root.optString("verification_url").takeIf { it.isNotBlank() }
                    ?: "https://mdblist.com"
                val complete = root.optString("verification_uri_complete")
                    .takeIf { it.isNotBlank() } ?: "$uri?user_code=$userCode"
                DeviceData(
                    deviceCode = deviceCode,
                    userCode = userCode,
                    verificationUrl = complete,
                    intervalSec = root.optLong("interval", 5L).coerceIn(1L, 3600L),
                    expiresInSec = root.optLong("expires_in", 600L).coerceIn(60L, 3600L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST /oauth/token/ (device_code grant) — poll until Approved. */
    suspend fun pollDeviceToken(clientId: String, deviceCode: String): PollResult =
        withContext(Dispatchers.IO) {
            try {
                val form = FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                    .add("device_code", deviceCode)
                    .add("scope", "write")
                    .add("client_id", clientId)
                    .build()
                val req = Request.Builder()
                    .url("$BASE/oauth/token/")
                    .header("User-Agent", userAgent)
                    .post(form)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful) {
                        val root = JSONObject(text)
                        val access = root.optString("access_token").takeIf { it.isNotBlank() }
                            ?: return@withContext PollResult.Failed
                        val refresh = root.optString("refresh_token").takeIf { it.isNotBlank() }
                            ?: return@withContext PollResult.Failed
                        val expiresAt = root.optLong("expires_in", 0L)
                            .takeIf { it > 0L }
                            ?.let { System.currentTimeMillis() + it * 1000L }
                            ?: 0L
                        return@withContext PollResult.Approved(access, refresh, expiresAt)
                    }
                    val code = runCatching { JSONObject(text).optString("error") }
                        .getOrNull().orEmpty()
                    return@withContext when {
                        code.contains("authorization_pending", ignoreCase = true) -> PollResult.Pending
                        code.contains("slow_down", ignoreCase = true) -> PollResult.SlowDown
                        code.contains("access_denied", ignoreCase = true) -> PollResult.Denied
                        code.contains("expired_token", ignoreCase = true) -> PollResult.Expired
                        resp.code in 500..599 -> PollResult.Pending
                        else -> PollResult.Failed
                    }
                }
            } catch (_: Exception) {
                PollResult.Failed
            }
        }

    /**
     * POST /oauth/token/ (refresh_token grant) — swaps the refresh token
     * for a new pair and persists them. Null on failure.
     */
    suspend fun refreshAccessToken(settings: SettingsStore): String? =
        withContext(Dispatchers.IO) {
            val refreshToken = settings.mdblistRefreshToken?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            val clientId = settings.mdblistClientId?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            try {
                val form = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .add("client_id", clientId)
                    .build()
                val req = Request.Builder()
                    .url("$BASE/oauth/token/")
                    .header("User-Agent", userAgent)
                    .post(form)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val access = root.optString("access_token").takeIf { it.isNotBlank() }
                        ?: return@withContext null
                    val refresh = root.optString("refresh_token")
                        .takeIf { it.isNotBlank() } ?: refreshToken
                    settings.mdblistAccessToken = access
                    settings.mdblistRefreshToken = refresh
                    settings.mdblistTokenExpiresAtMs = root.optLong("expires_in", 0L)
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
     * its expiry (with a 60s safety margin). Null when not linked or when
     * the refresh fails.
     */
    suspend fun ensureAccessToken(settings: SettingsStore): String? {
        val token = settings.mdblistAccessToken?.takeIf { it.isNotBlank() }
            ?: return null
        val expiresAt = settings.mdblistTokenExpiresAtMs
        if (expiresAt > 0L && System.currentTimeMillis() + 60_000L >= expiresAt) {
            return refreshAccessToken(settings)
        }
        return token
    }

    // ------------------------------------------------------------------
    // Reads (Bearer token)
    // ------------------------------------------------------------------

    /**
     * Personal lists: GET /lists/user?unified=false&sort=ranked —
     * a JSON array; only static (non-dynamic) lists are the user's own.
     */
    suspend fun getLists(settings: SettingsStore): List<MdbList> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val root = get(settings, "/lists/user", listOf("unified" to "false", "sort" to "ranked"), token)
            ?: return emptyList()
        val rows: JSONArray = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("lists") ?: JSONArray()
            else -> JSONArray()
        }
        return (0 until rows.length()).mapNotNull { i ->
            val row = rows.optJSONObject(i) ?: return@mapNotNull null
            if (row.optString("type") != "static") return@mapNotNull null
            if (row.optBoolean("dynamic", false)) return@mapNotNull null
            val id = row.optLong("id", 0L).takeIf { it > 0L } ?: return@mapNotNull null
            val name = row.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MdbList(id = id, name = name)
        }
    }

    /** GET /lists/{id}/items — movies + shows of one list. */
    suspend fun getListItems(settings: SettingsStore, listId: Long): List<Entry> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val root = get(settings, "/lists/$listId/items", emptyList(), token)
            ?: return emptyList()
        return parseLibraryPage(root)
    }

    /** GET /watchlist/items. */
    suspend fun getWatchlist(settings: SettingsStore): List<Entry> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val root = get(settings, "/watchlist/items", emptyList(), token)
            ?: return emptyList()
        return parseLibraryPage(root)
    }

    /**
     * Watched episodes: GET /sync/watched?append_to_response=poster —
     * {episodes[]} rows plus nested episodes inside {shows[]}.
     * Grouped by show to the latest watched episode.
     */
    suspend fun getWatchedEpisodes(settings: SettingsStore): List<WatchedEpisode> {
        val token = ensureAccessToken(settings) ?: return emptyList()
        val root = get(settings, "/sync/watched", listOf("append_to_response" to "poster"), token)
            as? JSONObject ?: return emptyList()
        val out = mutableListOf<WatchedEpisode>()
        val episodes = root.optJSONArray("episodes") ?: JSONArray()
        for (i in 0 until episodes.length()) {
            parseWatchedEpisodeRow(episodes.optJSONObject(i))?.let { out += it }
        }
        val shows = root.optJSONArray("shows") ?: JSONArray()
        for (i in 0 until shows.length()) {
            val row = shows.optJSONObject(i) ?: continue
            val showObj = row.optJSONObject("show") ?: row
            val showIds = showObj.optJSONObject("ids")
            val showContentId = contentId(showIds) ?: continue
            val showTitle = showObj.optString("title")
                .takeIf { it.isNotBlank() }
                ?: showObj.optString("name").takeIf { it.isNotBlank() }
            val nested = row.optJSONArray("episodes") ?: JSONArray()
            for (j in 0 until nested.length()) {
                val ep = nested.optJSONObject(j) ?: continue
                val season = ep.optInt("season", -1).takeIf { it >= 0 } ?: continue
                val number = ep.optInt("number", -1).takeIf { it >= 0 }
                    ?: ep.optInt("episode", -1).takeIf { it > 0 } ?: continue
                val watchedAt = row.optString("last_watched_at")
                    .takeIf { it.isNotBlank() }?.let { parseIsoMs(it) }
                    ?: ep.optString("last_watched_at").takeIf { it.isNotBlank() }
                        ?.let { parseIsoMs(it) } ?: 0L
                out += WatchedEpisode(showContentId, showTitle, season, number, watchedAt)
            }
        }
        return out.groupBy { Triple(it.showContentId, it.season, it.number) }
            .map { (_, group) -> group.maxBy { it.watchedAtMs } }
    }

    /**
     * The user's own ratings: GET /sync/ratings?offset=0&limit=5000 —
     * {movies[], shows[]} rows with a rating + nested ids. One page is
     * enough for a lookup (Nuvio pages further, but the sheet only needs
     * the title in front of the user).
     */
    suspend fun getRatings(settings: SettingsStore): Map<String, Int> {
        val token = ensureAccessToken(settings) ?: return emptyMap()
        val root = get(settings, "/sync/ratings", listOf("offset" to "0", "limit" to "5000"), token)
            as? JSONObject ?: return emptyMap()
        val out = mutableMapOf<String, Int>()
        for (key in listOf("movies", "shows")) {
            val rows = root.optJSONArray(key) ?: JSONArray()
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val rating = row.optInt("rating", 0).takeIf { it in 1..10 } ?: continue
                val media = row.optJSONObject(if (key == "movies") "movie" else "show")
                    ?: row
                contentId(media.optJSONObject("ids"))?.let { out[it] = rating }
            }
        }
        return out
    }

    // ------------------------------------------------------------------
    // Parsing (mirrors Nuvio's MdbListLibraryDecoder / WatchedDecoder)
    // ------------------------------------------------------------------

    private fun parseLibraryPage(root: Any): List<Entry> {
        val rows = mutableListOf<JSONObject>()
        when (root) {
            is JSONArray -> for (i in 0 until root.length()) {
                root.optJSONObject(i)?.let { rows += it }
            }
            is JSONObject -> {
                root.optJSONArray("items")?.let { items ->
                    for (i in 0 until items.length()) {
                        items.optJSONObject(i)?.let { rows += it }
                    }
                }
                for (key in listOf("movies", "shows")) {
                    root.optJSONArray(key)?.let { items ->
                        for (i in 0 until items.length()) {
                            items.optJSONObject(i)?.let { rows += it }
                        }
                    }
                }
            }
        }
        return rows.mapNotNull { parseLibraryItem(it) }
    }

    private fun parseLibraryItem(row: JSONObject): Entry? {
        val declared = row.optString("mediatype").takeIf { it.isNotBlank() }
            ?: row.optString("type").takeIf { it.isNotBlank() }
        if (declared == "season" || declared == "episode") return null
        val type = when (declared?.lowercase()) {
            "movie" -> "movie"
            "show", "series", "tv" -> "series"
            null -> null
            else -> return null
        } ?: return null
        val ids = JSONObject()
        row.optLong("id", 0L).takeIf { it > 0L }?.let { ids.put("tmdb", it) }
        row.optString("imdb_id").takeIf { it.startsWith("tt", ignoreCase = true) }
            ?.let { ids.put("imdb", it) }
        row.optLong("tvdb_id", 0L).takeIf { it > 0L }?.let { ids.put("tvdb", it) }
        row.optJSONObject("ids")?.let { nested ->
            nested.keys().forEach { key -> ids.put(key, nested.opt(key)) }
        }
        val contentId = contentId(ids) ?: return null
        return Entry(
            contentId = contentId,
            type = type,
            title = row.optString("title").takeIf { it.isNotBlank() }
                ?: row.optString("name").takeIf { it.isNotBlank() },
            year = row.optInt("release_year", 0).takeIf { it > 0 }
                ?: row.optInt("year", 0).takeIf { it > 0 }
                ?: row.optString("release_date").take(4).toIntOrNull()
        )
    }

    private fun parseWatchedEpisodeRow(row: JSONObject?): WatchedEpisode? {
        row ?: return null
        val episode = row.optJSONObject("episode") ?: row
        val showObj = row.optJSONObject("show")
        val showIds = showObj?.optJSONObject("ids")
        val showContentId = contentId(showIds) ?: return null
        val season = episode.optInt("season", -1).takeIf { it >= 0 } ?: return null
        val number = episode.optInt("number", -1).takeIf { it > 0 }
            ?: episode.optInt("episode", -1).takeIf { it > 0 } ?: return null
        val watchedAt = row.optString("last_watched_at").takeIf { it.isNotBlank() }
            ?.let { parseIsoMs(it) }
            ?: row.optString("watched_at").takeIf { it.isNotBlank() }
                ?.let { parseIsoMs(it) } ?: 0L
        return WatchedEpisode(
            showContentId = showContentId,
            showTitle = showObj?.optString("title")?.takeIf { it.isNotBlank() }
                ?: showObj?.optString("name")?.takeIf { it.isNotBlank() },
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
            ?: ids.optLong("tmdbid", 0L).takeIf { it > 0L }
        if (tmdb != null) return "tmdb:$tmdb"
        val tvdb = ids.optLong("tvdb", 0L).takeIf { it > 0L }
            ?: ids.optLong("tvdbid", 0L).takeIf { it > 0L }
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

    /** GET returning the parsed JSON root (object or array), or null. */
    private suspend fun get(
        settings: SettingsStore,
        path: String,
        query: List<Pair<String, String>>,
        token: String
    ): Any? = withContext(Dispatchers.IO) {
        try {
            val url = buildString {
                append(BASE).append(path)
                if (query.isNotEmpty()) {
                    append("?")
                    query.forEachIndexed { i, (k, v) ->
                        if (i > 0) append("&")
                        append(k).append("=")
                            .append(java.net.URLEncoder.encode(v, "UTF-8"))
                    }
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
                val text = resp.body?.string().orEmpty().trim()
                if (text.startsWith("[")) JSONArray(text) else JSONObject(text)
            }
        } catch (_: Exception) {
            null
        }
    }
}
