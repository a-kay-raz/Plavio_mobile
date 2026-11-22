package com.pulsestream.app.data.simkl

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Simkl API (https://api.simkl.com).
 * Uses the OAuth 2.0 Device Authorization Grant (RFC 8628):
 * POST /oauth2/device -> user approves at simkl.com/pin -> POST /oauth2/token.
 */
class SimklApi {

    companion object {
        const val CLIENT_ID = "c1cbe532ad5f4224a0ffe1da154d321c7656ab23bd7f8bf45d77844fca42ad38"
        private const val BASE = "https://api.simkl.com"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val userAgent = "PulseStream/${BuildConfig.VERSION_NAME}"

    data class DeviceData(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val verificationUriComplete: String,
        val intervalSec: Int,
        val expiresInSec: Int
    )

    sealed interface PollResult {
        data class Approved(val accessToken: String) : PollResult
        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Expired : PollResult
        data object Failed : PollResult
    }

    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Step 1 of device auth: get a user code to show in the app. */
    suspend fun requestDeviceCode(): DeviceData = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("client_id", CLIENT_ID).build()
        val request = Request.Builder()
            .url("$BASE/oauth2/device")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Simkl device request failed (HTTP ${response.code})")
            val bodyStr = response.body?.string() ?: throw IllegalStateException("Empty response")
            val json = JSONObject(bodyStr)
            DeviceData(
                deviceCode = json.getString("device_code"),
                userCode = json.getString("user_code"),
                verificationUri = json.optString("verification_uri", "https://simkl.com/pin"),
                verificationUriComplete = json.optString(
                    "verification_uri_complete",
                    "https://simkl.com/pin"
                ),
                intervalSec = json.optInt("interval", 5).coerceAtLeast(3),
                expiresInSec = json.optInt("expires_in", 900)
            )
        }
    }

    /**
     * Step 2 of device auth: poll until the user approves the code.
     */
    suspend fun pollDeviceToken(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        val form = FormBody.Builder()
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .add("client_id", CLIENT_ID)
            .add("device_code", deviceCode)
            .build()
        val request = Request.Builder()
            .url("$BASE/oauth2/token")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string() ?: return@withContext PollResult.Failed
            val json = JSONObject(bodyStr)
            if (response.isSuccessful) {
                val token = json.optString("access_token", "")
                return@withContext if (token.isNotEmpty()) PollResult.Approved(token)
                else PollResult.Failed
            }
            when (json.optString("error")) {
                "authorization_pending" -> PollResult.Pending
                "slow_down" -> PollResult.SlowDown
                "expired_token", "access_denied" -> PollResult.Expired
                else -> PollResult.Failed
            }
        }
    }

    /** Fetch the linked account's display name. */
    suspend fun getUsername(token: String): String? = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/users/settings").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val bodyStr = response.body?.string() ?: return@withContext null
            val json = JSONObject(bodyStr)
            json.optJSONObject("user")?.optString("name", null)
        }
    }

    /**
     * Send a scrobble event. [action] is one of "start", "pause", "stop".
     * @return true if the server accepted it.
     */
    suspend fun scrobble(token: String, action: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val request = authedRequest(token, "$BASE/scrobble/$action")
                .post(body.toString().toRequestBody(JSON))
                .build()
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }

    // ---- Library lists (library sync, issue #3) ----

    /** One Simkl library entry, IMDb-keyed. [type] is "movie" or "series". */
    data class ListEntry(val type: String, val imdbId: String, val title: String)

    /**
     * The user's Simkl library — Plan to Watch + Watching only (Completed /
     * On Hold / Dropped are not "library" in Avio's sense). Throws on
     * failure so the sync can tell "empty" from "unreachable".
     */
    suspend fun getAllItems(token: String): List<ListEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<ListEntry>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val status = item.optString("status")
                    if (status != "plantowatch" && status != "watching") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += ListEntry(type, imdb, node.optString("title"))
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** One show the user is currently watching on Simkl. */
    data class WatchingEntry(
        val imdbId: String,
        val title: String,
        val watchedEpisodes: Int?,
        val totalEpisodes: Int?,
        val lastWatchedAt: String?
    )

    /** Shows with Simkl status "watching" (the Library screen's Up Next input). */
    suspend fun getWatching(token: String): List<WatchingEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<WatchingEntry>()
            fun collect(section: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("status") != "watching") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += WatchingEntry(
                        imdbId = imdb,
                        title = node.optString("title"),
                        watchedEpisodes = item.optInt("watched_episodes_count", -1).takeIf { it >= 0 },
                        totalEpisodes = item.optInt("total_episodes_count", -1).takeIf { it > 0 },
                        lastWatchedAt = item.optString("last_watched_at").takeIf { it.isNotBlank() }
                    )
                }
            }
            collect("shows", "show")
            collect("anime", "anime")
            out
        }
    }

    /** Plan-to-Watch items only (the Library screen's Simkl watchlist row). */
    suspend fun getPlanToWatch(token: String): List<ListEntry> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl library failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<ListEntry>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (item.optString("status") != "plantowatch") continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += ListEntry(type, imdb, node.optString("title"))
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** Adds entries to the Simkl library as Plan to Watch (= tracked on Simkl). Best-effort. */
    suspend fun addToList(token: String, entries: List<ListEntry>): Boolean =
        postList(token, "$BASE/sync/add-to-list", entries, withStatus = true)

    /** Removes entries from the Simkl library. Best-effort. */
    suspend fun removeFromList(token: String, entries: List<ListEntry>): Boolean =
        postList(token, "$BASE/sync/remove-from-list", entries, withStatus = false)

    private suspend fun postList(
        token: String,
        url: String,
        entries: List<ListEntry>,
        withStatus: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext true
        val movies = org.json.JSONArray()
        val shows = org.json.JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().put("ids", JSONObject().put("imdb", e.imdbId))
            if (withStatus) obj.put("to", "plantowatch")
            if (e.type == "movie") movies.put(obj) else shows.put(obj)
        }
        val body = JSONObject()
        if (movies.length() > 0) body.put("movies", movies)
        if (shows.length() > 0) body.put("shows", shows)
        val request = authedRequest(token, url)
            .post(body.toString().toRequestBody(JSON))
            .build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun authedRequest(token: String, url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .header("simkl-api-key", CLIENT_ID)

    // ---- Sync engine (Step 34, Nuvio SimklSyncRemote + SimklMutationService) ----

    /** One domain's activity timestamps from /sync/activities. */
    data class SimklActivityDomain(
        val all: String?,
        val ratedAt: String?,
        val playback: String?,
        val plantowatch: String?,
        val watching: String?,
        val completed: String?,
        val hold: String?,
        val dropped: String?,
        val removedFromList: String?
    )

    data class SimklActivities(
        val all: String?,
        val movies: SimklActivityDomain?,
        val shows: SimklActivityDomain?,
        val anime: SimklActivityDomain?
    )

    /** Activity watermarks — the engine skips the fetch when `all` is unchanged. */
    suspend fun getActivities(token: String): SimklActivities = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/activities").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl activities failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            fun domain(node: JSONObject?): SimklActivityDomain? {
                if (node == null) return null
                return SimklActivityDomain(
                    all = node.optString("all").takeIf { it.isNotBlank() },
                    ratedAt = node.optString("rated_at").takeIf { it.isNotBlank() },
                    playback = node.optString("playback").takeIf { it.isNotBlank() },
                    plantowatch = node.optString("plantowatch").takeIf { it.isNotBlank() },
                    watching = node.optString("watching").takeIf { it.isNotBlank() },
                    completed = node.optString("completed").takeIf { it.isNotBlank() },
                    hold = node.optString("hold").takeIf { it.isNotBlank() },
                    dropped = node.optString("dropped").takeIf { it.isNotBlank() },
                    removedFromList = node.optString("removed_from_list").takeIf { it.isNotBlank() }
                )
            }
            SimklActivities(
                all = root.optString("all").takeIf { it.isNotBlank() },
                movies = domain(root.optJSONObject("movies")),
                shows = domain(root.optJSONObject("shows")),
                anime = domain(root.optJSONObject("anime"))
            )
        }
    }

    /** One library entry with its watched episode set (from /sync/all-items full). */
    data class SimklSyncEntry(
        val type: String, // "movie" | "series"
        val imdbId: String,
        val title: String,
        val status: String?,
        val lastWatchedAtMs: Long,
        val watchedEpisodes: Set<Pair<Int, Int>> = emptySet(),
        val watchedEpisodeAtMs: Map<Pair<Int, Int>, Long> = emptyMap()
    )

    /**
     * Full library (all statuses) with per-episode watched info. With
     * [dateFrom] (an activities watermark) Simkl returns only items changed
     * since then — the engine's delta fetch. Throws on failure.
     */
    suspend fun getAllItemsFull(token: String, dateFrom: String? = null): List<SimklSyncEntry> =
        withContext(Dispatchers.IO) {
            val query = buildString {
                append("extended=full_anime_seasons&episode_watched_at=yes")
                append("&episode_tvdb_id=yes&include_all_episodes=yes&language=en")
                if (dateFrom != null) append("&date_from=${java.net.URLEncoder.encode(dateFrom, "UTF-8")}")
            }
            val request = authedRequest(token, "$BASE/sync/all-items?$query").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Simkl all-items failed (HTTP ${response.code})")
                }
                val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                val out = mutableListOf<SimklSyncEntry>()
                fun collect(section: String, type: String, nodeName: String) {
                    val arr = root.optJSONArray(section) ?: return
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val node = item.optJSONObject(nodeName) ?: continue
                        val imdb = node.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") } ?: continue
                        val watched = mutableSetOf<Pair<Int, Int>>()
                        val watchedAt = mutableMapOf<Pair<Int, Int>, Long>()
                        val seasons = item.optJSONArray("seasons")
                        if (seasons != null) for (s in 0 until seasons.length()) {
                            val season = seasons.optJSONObject(s) ?: continue
                            val sNum = season.optInt("number")
                            if (sNum == 0) continue
                            val eps = season.optJSONArray("episodes")
                            if (eps != null) for (e in 0 until eps.length()) {
                                val ep = eps.optJSONObject(e) ?: continue
                                val at = ep.optString("watched_at")
                                if (at.isBlank()) continue
                                val key = sNum to ep.optInt("number")
                                watched += key
                                watchedAt[key] = runCatching {
                                    java.time.Instant.parse(at).toEpochMilli()
                                }.getOrDefault(0L)
                            }
                        }
                        out += SimklSyncEntry(
                            type = type,
                            imdbId = imdb,
                            title = node.optString("title"),
                            status = item.optString("status").takeIf { it.isNotBlank() },
                            lastWatchedAtMs = runCatching {
                                java.time.Instant.parse(item.optString("last_watched_at")).toEpochMilli()
                            }.getOrDefault(0L),
                            watchedEpisodes = watched,
                            watchedEpisodeAtMs = watchedAt
                        )
                    }
                }
                collect("movies", "movie", "movie")
                collect("shows", "series", "show")
                collect("anime", "series", "anime")
                out
            }
        }

    /** Current Simkl ids only ("type|imdb") — removal reconciliation. */
    suspend fun getAllItemIds(token: String): Set<String> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/all-items?extended=simkl_ids_only").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl all-items (ids) failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableSetOf<String>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val node = arr.optJSONObject(i)?.optJSONObject(nodeName) ?: continue
                    node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") }?.let { out += "$type|$it" }
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** One in-progress playback row from /sync/playback. */
    data class SimklPlaybackRow(
        val type: String,
        val imdbId: String?,
        val title: String,
        val season: Int,
        val episode: Int,
        val progressPercent: Double,
        val playedAtMs: Long
    )

    suspend fun getPlayback(token: String): List<SimklPlaybackRow> = withContext(Dispatchers.IO) {
        val request = authedRequest(token, "$BASE/sync/playback").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Simkl playback failed (HTTP ${response.code})")
            }
            val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
            val out = mutableListOf<SimklPlaybackRow>()
            fun collect(section: String, type: String, nodeName: String) {
                val arr = root.optJSONArray(section) ?: return
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val node = item.optJSONObject(nodeName) ?: continue
                    val ep = item.optJSONObject("episode")
                    val playedAt = item.optString("paused_at").takeIf { it.isNotBlank() }
                        ?: item.optString("watched_at").takeIf { it.isNotBlank() }
                    out += SimklPlaybackRow(
                        type = type,
                        imdbId = node.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") },
                        title = node.optString("title"),
                        season = ep?.optInt("season", 0) ?: 0,
                        episode = ep?.optInt("number", 0) ?: 0,
                        progressPercent = item.optDouble("progress", 0.0),
                        playedAtMs = runCatching {
                            java.time.Instant.parse(playedAt).toEpochMilli()
                        }.getOrDefault(0L)
                    )
                }
            }
            collect("movies", "movie", "movie")
            collect("shows", "series", "show")
            collect("anime", "series", "anime")
            out
        }
    }

    /** One watched/unwatched title for /sync/history(+/remove). */
    data class HistoryItem(
        val type: String,
        val imdbId: String,
        val title: String,
        val season: Int = 0,
        val episode: Int = 0,
        val watchedAtMs: Long = 0L
    )

    private fun historyBody(items: List<HistoryItem>): JSONObject {
        val movies = org.json.JSONArray()
        val shows = org.json.JSONArray()
        fun watchedAt(ms: Long): String? =
            if (ms > 0) java.time.Instant.ofEpochMilli(ms).toString() else null
        items.forEach { item ->
            val ids = JSONObject().put("imdb", item.imdbId)
            if (item.type == "movie") {
                val obj = JSONObject().put("ids", ids)
                if (item.title.isNotBlank()) obj.put("title", item.title)
                watchedAt(item.watchedAtMs)?.let { obj.put("watched_at", it) }
                movies.put(obj)
            } else {
                val ep = JSONObject().put("number", item.episode)
                watchedAt(item.watchedAtMs)?.let { ep.put("watched_at", it) }
                val season = JSONObject().put("number", item.season)
                    .put("episodes", org.json.JSONArray().put(ep))
                val obj = JSONObject().put("ids", ids)
                    .put("seasons", org.json.JSONArray().put(season))
                if (item.title.isNotBlank()) obj.put("title", item.title)
                shows.put(obj)
            }
        }
        return JSONObject().apply {
            if (movies.length() > 0) put("movies", movies)
            if (shows.length() > 0) put("shows", shows)
        }
    }

    /** Marks titles watched on Simkl (/sync/history). Best-effort. */
    suspend fun addToHistory(token: String, items: List<HistoryItem>): Boolean =
        postHistory(token, "$BASE/sync/history", items)

    /** Marks titles unwatched on Simkl (/sync/history/remove). Best-effort. */
    suspend fun removeFromHistory(token: String, items: List<HistoryItem>): Boolean =
        postHistory(token, "$BASE/sync/history/remove", items)

    private suspend fun postHistory(token: String, url: String, items: List<HistoryItem>): Boolean =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext true
            val request = authedRequest(token, url)
                .post(historyBody(items).toString().toRequestBody(JSON)).build()
            try {
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }

    /** Moves titles to a Simkl list status (/sync/add-to-list with "to"). Best-effort. */
    suspend fun changeStatus(token: String, entries: List<ListEntry>, to: String): Boolean =
        withContext(Dispatchers.IO) {
            if (entries.isEmpty()) return@withContext true
            val movies = org.json.JSONArray()
            val shows = org.json.JSONArray()
            entries.forEach { e ->
                val obj = JSONObject().put("ids", JSONObject().put("imdb", e.imdbId)).put("to", to)
                if (e.type == "movie") movies.put(obj) else shows.put(obj)
            }
            val body = JSONObject()
            if (movies.length() > 0) body.put("movies", movies)
            if (shows.length() > 0) body.put("shows", shows)
            val request = authedRequest(token, "$BASE/sync/add-to-list")
                .post(body.toString().toRequestBody(JSON)).build()
            try {
                client.newCall(request).execute().use { it.isSuccessful }
            } catch (_: Exception) {
                false
            }
        }
}
