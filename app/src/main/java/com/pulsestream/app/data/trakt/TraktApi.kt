package com.pulsestream.app.data.trakt

import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Trakt API (https://api.trakt.tv).
 * Uses OAuth 2.0 Authorization Code flow with PKCE (no client secret):
 * app builds an authorize URL -> user approves in the browser ->
 * our callback page shows the code -> user pastes it into the app ->
 * POST /oauth/token exchanges it for tokens.
 */
class TraktApi {

    companion object {
        const val CLIENT_ID = "2j7KBLNBFVZG5qs6afdPGxbWyb23_93tX0aopgiDGSY"
        const val REDIRECT_URI = "https://www.plavio.site/trakt-callback.html"
        private const val AUTHORIZE_URL = "https://trakt.tv/oauth/authorize"
        private const val TOKEN_URL = "https://api.trakt.tv/oauth/token"
        private const val API_BASE = "https://api.trakt.tv"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val userAgent = "Avio/${BuildConfig.VERSION_NAME}"

    data class Pkce(val verifier: String, val challenge: String, val state: String)

    data class Tokens(
        val accessToken: String,
        val refreshToken: String,
        val expiresInSec: Long
    )

    private val client = OkHttpClient.Builder()
        .callTimeout(25, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Generates a PKCE verifier/challenge pair plus a state token. */
    fun newPkce(): Pkce {
        val random = SecureRandom()
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val verifier = buildString {
            repeat(64) { append(chars[random.nextInt(chars.length)]) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        val challenge = base64Url(digest)
        val state = buildString {
            repeat(16) { append(chars[random.nextInt(62)]) }
        }
        return Pkce(verifier, challenge, state)
    }

    /** The URL the user opens in a browser to approve Avio. */
    fun authorizeUrl(pkce: Pkce): String {
        val params = listOf(
            "response_type" to "code",
            "client_id" to CLIENT_ID,
            "redirect_uri" to REDIRECT_URI,
            "state" to pkce.state,
            "code_challenge" to pkce.challenge,
            "code_challenge_method" to "S256"
        ).joinToString("&") { (k, v) -> "$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
        return "$AUTHORIZE_URL?$params"
    }

    /** Exchanges the pasted authorization code for tokens. */
    suspend fun exchangeCode(code: String, verifier: String): Tokens = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("code", code.trim())
            .put("client_id", CLIENT_ID)
            .put("redirect_uri", REDIRECT_URI)
            .put("grant_type", "authorization_code")
            .put("code_verifier", verifier)
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Trakt code exchange failed (HTTP ${response.code})")
            val json = JSONObject(text)
            Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresInSec = json.optLong("expires_in", 7_776_000L)
            )
        }
    }

    /** Refreshes an expired access token. */
    suspend fun refreshToken(refreshToken: String): Tokens = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("refresh_token", refreshToken)
            .put("client_id", CLIENT_ID)
            .put("redirect_uri", REDIRECT_URI)
            .put("grant_type", "refresh_token")
            .toString()
            .toRequestBody(JSON)
        val request = Request.Builder()
            .url(TOKEN_URL)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IllegalStateException("Trakt refresh failed (HTTP ${response.code})")
            val json = JSONObject(text)
            Tokens(
                accessToken = json.getString("access_token"),
                refreshToken = json.optString("refresh_token", refreshToken),
                expiresInSec = json.optLong("expires_in", 7_776_000L)
            )
        }
    }

    /** Returns the Trakt username for the linked account, or null. */
    suspend fun getUsername(accessToken: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$API_BASE/users/settings")
            .header("User-Agent", userAgent)
            .header("trakt-api-version", "2")
            .header("trakt-api-key", CLIENT_ID)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val text = response.body?.string().orEmpty()
            JSONObject(text).optJSONObject("user")?.optString("username")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * Sends a scrobble event (start/pause/stop).
     * Returns true on success; false on auth failure (caller should refresh/unlink).
     */
    suspend fun scrobble(accessToken: String, action: String, body: JSONObject): ScrobbleResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$API_BASE/scrobble/$action")
                .header("User-Agent", userAgent)
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .header("Authorization", "Bearer $accessToken")
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    when {
                        response.isSuccessful -> ScrobbleResult.Ok
                        response.code == 401 -> ScrobbleResult.Unauthorized
                        else -> ScrobbleResult.Failed
                    }
                }
            } catch (_: Exception) {
                ScrobbleResult.Failed
            }
        }

    enum class ScrobbleResult { Ok, Unauthorized, Failed }

    // ---- Watchlist (library sync, issue #3) ----

    /** One Trakt watchlist entry, IMDb-keyed. [type] is "movie" or "series". */
    data class WatchlistEntry(val type: String, val imdbId: String, val title: String)

    private fun authed(accessToken: String): Request.Builder = Request.Builder()
        .header("User-Agent", userAgent)
        .header("trakt-api-version", "2")
        .header("trakt-api-key", CLIENT_ID)
        .header("Authorization", "Bearer $accessToken")

    /** The user's whole Trakt watchlist (movies + shows). Throws on failure. */
    suspend fun getWatchlist(accessToken: String): List<WatchlistEntry> = withContext(Dispatchers.IO) {
        val out = mutableListOf<WatchlistEntry>()
        val sections = listOf(
            Triple("movies", "movie", "movie"),
            Triple("shows", "series", "show")
        )
        for ((path, type, nodeName) in sections) {
            val request = authed(accessToken).url("$API_BASE/users/me/watchlist/$path").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watchlist failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                for (i in 0 until arr.length()) {
                    val node = arr.optJSONObject(i)?.optJSONObject(nodeName) ?: continue
                    val imdb = node.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    out += WatchlistEntry(type, imdb, node.optString("title"))
                }
            }
        }
        out
    }

    /** Adds entries to the Trakt watchlist. Best-effort. */
    suspend fun addToWatchlist(accessToken: String, entries: List<WatchlistEntry>): Boolean =
        syncWatchlist(accessToken, "watchlist", entries)

    /** Removes entries from the Trakt watchlist. Best-effort. */
    suspend fun removeFromWatchlist(accessToken: String, entries: List<WatchlistEntry>): Boolean =
        syncWatchlist(accessToken, "watchlist/remove", entries)

    private suspend fun syncWatchlist(
        accessToken: String,
        path: String,
        entries: List<WatchlistEntry>
    ): Boolean = withContext(Dispatchers.IO) {
        if (entries.isEmpty()) return@withContext true
        val movies = org.json.JSONArray()
        val shows = org.json.JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().put("ids", JSONObject().put("imdb", e.imdbId))
            if (e.type == "movie") movies.put(obj) else shows.put(obj)
        }
        val body = JSONObject()
        if (movies.length() > 0) body.put("movies", movies)
        if (shows.length() > 0) body.put("shows", shows)
        val request = authed(accessToken).url("$API_BASE/sync/$path")
            .post(body.toString().toRequestBody(JSON)).build()
        try {
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /** One show the user is mid-way through, with the next episode to watch. */
    data class UpNextEntry(
        val imdbId: String,
        val title: String,
        val lastSeason: Int,
        val lastEpisode: Int,
        val nextSeason: Int,
        val nextEpisode: Int,
        val nextTitle: String?,
        val watchedCount: Int,
        val airedCount: Int,
        val lastWatchedAtMs: Long
    )

    private data class NextEp(val season: Int, val episode: Int, val title: String?, val airedTotal: Int)

    /**
     * "Up next" from Trakt watched progress: shows with at least one
     * watched episode and a later aired episode still unwatched.
     * sync/watched/shows gives the watched sets; each candidate show's
     * episode list is then checked for the first aired unwatched one.
     * Per-show failures are skipped — one bad show never sinks the list.
     */
    suspend fun getUpNext(accessToken: String, limit: Int = 12): List<UpNextEntry> =
        withContext(Dispatchers.IO) {
            val request = authed(accessToken).url("$API_BASE/sync/watched/shows").get().build()
            data class Candidate(
                val imdb: String, val title: String,
                val watched: Set<Pair<Int, Int>>, val lastMs: Long
            )
            val candidates = mutableListOf<Candidate>()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watched failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val show = item.optJSONObject("show") ?: continue
                    val imdb = show.optJSONObject("ids")?.optString("imdb")
                        ?.takeIf { it.startsWith("tt") } ?: continue
                    val lastMs = runCatching {
                        java.time.Instant.parse(item.optString("last_watched_at")).toEpochMilli()
                    }.getOrDefault(0L)
                    val watched = mutableSetOf<Pair<Int, Int>>()
                    val seasons = item.optJSONArray("seasons")
                    if (seasons != null) for (s in 0 until seasons.length()) {
                        val season = seasons.optJSONObject(s) ?: continue
                        val sNum = season.optInt("number")
                        if (sNum == 0) continue
                        val eps = season.optJSONArray("episodes")
                        if (eps != null) for (e in 0 until eps.length()) {
                            val ep = eps.optJSONObject(e) ?: continue
                            watched += sNum to ep.optInt("number")
                        }
                    }
                    if (watched.isNotEmpty()) {
                        candidates += Candidate(imdb, show.optString("title"), watched, lastMs)
                    }
                }
            }
            val out = mutableListOf<UpNextEntry>()
            for (c in candidates.sortedByDescending { it.lastMs }.take(limit)) {
                val next = runCatching { nextAiredEpisode(accessToken, c.imdb, c.watched) }
                    .getOrNull() ?: continue
                val last = c.watched.maxWith(compareBy({ it.first }, { it.second }))
                out += UpNextEntry(
                    imdbId = c.imdb, title = c.title,
                    lastSeason = last.first, lastEpisode = last.second,
                    nextSeason = next.season, nextEpisode = next.episode,
                    nextTitle = next.title,
                    watchedCount = c.watched.size, airedCount = next.airedTotal,
                    lastWatchedAtMs = c.lastMs
                )
            }
            out
        }

    /** First aired episode (season >= 1) not in [watched], plus the aired total. */
    private fun nextAiredEpisode(
        accessToken: String, imdb: String, watched: Set<Pair<Int, Int>>
    ): NextEp? {
        val request = authed(accessToken)
            .url("$API_BASE/shows/$imdb/seasons?extended=episodes").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            var aired = 0
            var found: NextEp? = null
            for (s in 0 until arr.length()) {
                val season = arr.optJSONObject(s) ?: continue
                val sNum = season.optInt("number")
                if (sNum == 0) continue
                val eps = season.optJSONArray("episodes") ?: continue
                for (e in 0 until eps.length()) {
                    val ep = eps.optJSONObject(e) ?: continue
                    if (ep.optString("first_aired").isBlank()) continue
                    aired++
                    val key = sNum to ep.optInt("number")
                    if (found == null && key !in watched) {
                        found = NextEp(
                            key.first, key.second,
                            ep.optString("title").takeIf { it.isNotBlank() }, 0
                        )
                    }
                }
            }
            val f = found ?: return null
            return NextEp(f.season, f.episode, f.title, aired)
        }
    }

    private fun base64Url(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    // ---- Watched history (Step 33, Nuvio TraktLibraryRepository/TraktProgressRepository) ----

    /** One watched movie from Trakt (sync/watched/movies). */
    data class WatchedMovie(
        val imdbId: String,
        val title: String,
        val lastWatchedAtMs: Long,
        val plays: Int
    )

    /** One watched show with its watched (season, episode) set. */
    data class WatchedShow(
        val imdbId: String,
        val title: String,
        val lastWatchedAtMs: Long,
        val episodes: Set<Pair<Int, Int>>
    )

    private fun parseWatchedAtMs(raw: String?): Long =
        runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrDefault(0L)

    /** All watched movies, IMDb-keyed. Throws on failure. */
    suspend fun getWatchedMovies(accessToken: String): List<WatchedMovie> =
        withContext(Dispatchers.IO) {
            val request = authed(accessToken).url("$API_BASE/sync/watched/movies").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watched movies failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val movie = item.optJSONObject("movie") ?: continue
                        val imdb = movie.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") } ?: continue
                        add(
                            WatchedMovie(
                                imdbId = imdb,
                                title = movie.optString("title"),
                                lastWatchedAtMs = parseWatchedAtMs(item.optString("last_watched_at")),
                                plays = item.optInt("plays", 1)
                            )
                        )
                    }
                }
            }
        }

    /** All watched shows with their watched episode sets, IMDb-keyed. Throws on failure. */
    suspend fun getWatchedShows(accessToken: String): List<WatchedShow> =
        withContext(Dispatchers.IO) {
            val request = authed(accessToken).url("$API_BASE/sync/watched/shows").get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Trakt watched shows failed (HTTP ${response.code})")
                }
                val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val show = item.optJSONObject("show") ?: continue
                        val imdb = show.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") } ?: continue
                        val watched = mutableSetOf<Pair<Int, Int>>()
                        val seasons = item.optJSONArray("seasons")
                        if (seasons != null) for (s in 0 until seasons.length()) {
                            val season = seasons.optJSONObject(s) ?: continue
                            val sNum = season.optInt("number")
                            if (sNum == 0) continue
                            val eps = season.optJSONArray("episodes")
                            if (eps != null) for (e in 0 until eps.length()) {
                                watched += sNum to eps.optJSONObject(e).optInt("number")
                            }
                        }
                        if (watched.isNotEmpty()) {
                            add(
                                WatchedShow(
                                    imdbId = imdb,
                                    title = show.optString("title"),
                                    lastWatchedAtMs = parseWatchedAtMs(item.optString("last_watched_at")),
                                    episodes = watched
                                )
                            )
                        }
                    }
                }
            }
        }

    // ---- Personal lists (Step 33, Nuvio TraktLibraryRepository) ----

    /** One of the user's own Trakt lists (type "personal" only). */
    data class TraktList(val id: String, val name: String, val description: String?)

    /** The user's personal lists. Throws on failure. */
    /** One in-progress playback row from /sync/playback (Step 62). */
    data class TraktPlaybackRow(
        val type: String, // "movie" | "episode"
        val imdbId: String?,
        val title: String,
        val season: Int,
        val episode: Int,
        val progressPercent: Double,
        val pausedAtMs: Long
    )

    /** In-progress playback from another device (Step 62). Throws on failure. */
    suspend fun getPlaybackProgress(accessToken: String): List<TraktPlaybackRow> = withContext(Dispatchers.IO) {
        val request = authed(accessToken).url("$API_BASE/sync/playback").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Trakt playback failed (HTTP ${response.code})")
            }
            val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            val out = mutableListOf<TraktPlaybackRow>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val type = item.optString("type")
                val progress = item.optDouble("progress", 0.0)
                if (progress <= 0.0) continue
                val pausedAtMs = parseWatchedAtMs(item.optString("paused_at").takeIf { it.isNotBlank() })
                when (type) {
                    "movie" -> {
                        val movie = item.optJSONObject("movie") ?: continue
                        val imdb = movie.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") }
                        out += TraktPlaybackRow(
                            type = "movie", imdbId = imdb, title = movie.optString("title"),
                            season = 0, episode = 0, progressPercent = progress, pausedAtMs = pausedAtMs
                        )
                    }
                    "episode" -> {
                        val show = item.optJSONObject("show") ?: continue
                        val ep = item.optJSONObject("episode") ?: continue
                        val imdb = show.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") }
                        out += TraktPlaybackRow(
                            type = "episode", imdbId = imdb, title = show.optString("title"),
                            season = ep.optInt("season", 0), episode = ep.optInt("number", 0),
                            progressPercent = progress, pausedAtMs = pausedAtMs
                        )
                    }
                }
            }
            out
        }
    }

    suspend fun getLists(accessToken: String): List<TraktList> = withContext(Dispatchers.IO) {
        val request = authed(accessToken).url("$API_BASE/users/me/lists").get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Trakt lists failed (HTTP ${response.code})")
            }
            val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    if (!item.optString("type").equals("personal", ignoreCase = true)) continue
                    val ids = item.optJSONObject("ids")
                    val id = ids?.optLong("trakt")?.takeIf { it > 0 }?.toString()
                        ?: ids?.optString("slug")?.takeIf { it.isNotBlank() } ?: continue
                    add(
                        TraktList(
                            id = id,
                            name = item.optString("name").takeIf { it.isNotBlank() } ?: "List $id",
                            description = item.optString("description").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
        }
    }

    /** One list's items (movies + shows), IMDb-keyed. Throws on failure. */
    suspend fun getListItems(accessToken: String, listId: String): List<WatchlistEntry> =
        withContext(Dispatchers.IO) {
            val out = mutableListOf<WatchlistEntry>()
            for ((path, type, nodeName) in listOf(
                Triple("movie", "movie", "movie"),
                Triple("show", "series", "show")
            )) {
                val request = authed(accessToken)
                    .url("$API_BASE/users/me/lists/$listId/items/$path").get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Trakt list items failed (HTTP ${response.code})")
                    }
                    val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                    for (i in 0 until arr.length()) {
                        val node = arr.optJSONObject(i)?.optJSONObject(nodeName) ?: continue
                        val imdb = node.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") } ?: continue
                        out += WatchlistEntry(type, imdb, node.optString("title"))
                    }
                }
            }
            out
        }

    // ---- Comments (Step 33, Nuvio TraktCommentsRepository) ----

    /** One Trakt comment, spoiler markup stripped like Nuvio's. */
    data class TraktComment(
        val id: Long,
        val author: String,
        val username: String?,
        val text: String,
        val spoiler: Boolean,
        val likes: Int,
        val rating: Int?,
        val createdAt: String?
    )

    data class TraktCommentsPage(
        val items: List<TraktComment>,
        val page: Int,
        val pageCount: Int,
        val itemCount: Int
    )

    private val inlineSpoilerRegex = Regex("(?is)\\[spoiler\\].*?\\[/spoiler\\]")
    private val inlineSpoilerTagRegex = Regex("\\[/?spoiler\\]", RegexOption.IGNORE_CASE)

    /**
     * Top comments for a title, most-liked first (100/page). Works with the
     * API key alone — [accessToken] is optional. Throws on failure; a 404
     * means "no comments" and returns an empty page.
     */
    suspend fun getComments(
        type: String,
        pathId: String,
        page: Int = 1,
        accessToken: String? = null
    ): TraktCommentsPage = withContext(Dispatchers.IO) {
        val endpoint = if (type == "movie") "movies" else "shows"
        val builder = Request.Builder()
            .header("User-Agent", userAgent)
            .header("trakt-api-version", "2")
            .header("trakt-api-key", CLIENT_ID)
        if (!accessToken.isNullOrBlank()) builder.header("Authorization", "Bearer $accessToken")
        val request = builder
            .url("$API_BASE/$endpoint/$pathId/comments/likes?page=$page&limit=100")
            .get().build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@withContext TraktCommentsPage(emptyList(), page, page, 0)
            if (!response.isSuccessful) {
                throw IllegalStateException("Trakt comments failed (HTTP ${response.code})")
            }
            val pageCount = response.header("X-Pagination-Page-Count")?.toIntOrNull() ?: page
            val itemCount = response.header("X-Pagination-Item-Count")?.toIntOrNull() ?: 0
            val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
            val items = buildList {
                for (i in 0 until arr.length()) {
                    val dto = arr.optJSONObject(i) ?: continue
                    val raw = dto.optString("comment").takeIf { it.isNotBlank() } ?: continue
                    val user = dto.optJSONObject("user")
                    val author = user?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: user?.optString("username")?.takeIf { it.isNotBlank() }
                        ?: "Trakt user"
                    val text = raw
                        .replace(inlineSpoilerTagRegex, "")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                    add(
                        TraktComment(
                            id = dto.optLong("id"),
                            author = author,
                            username = user?.optString("username")?.takeIf { it.isNotBlank() },
                            text = text,
                            spoiler = dto.optBoolean("spoiler") || inlineSpoilerRegex.containsMatchIn(raw),
                            likes = dto.optInt("likes"),
                            rating = dto.optJSONObject("user_stats")?.optInt("rating")
                                ?.takeIf { it > 0 },
                            createdAt = dto.optString("created_at").takeIf { it.isNotBlank() }
                        )
                    )
                }
            }
            TraktCommentsPage(items, page, pageCount, itemCount)
        }
    }

    // ---- Ratings (Step 33, Nuvio TraktRatingsProvider) ----

    /** A title's community rating on Trakt (public, API key only). */
    data class TraktTitleRatings(val rating: Double, val votes: Int)

    /** Community average for a title, or null when Trakt has none. */
    suspend fun getTitleRatings(imdbId: String, type: String): TraktTitleRatings? =
        withContext(Dispatchers.IO) {
            val endpoint = if (type == "movie") "movies" else "shows"
            val request = Request.Builder()
                .url("$API_BASE/$endpoint/$imdbId/ratings")
                .header("User-Agent", userAgent)
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .get().build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val root = JSONObject(response.body?.string().orEmpty())
                    val rating = root.optDouble("rating", 0.0)
                    val votes = root.optInt("votes", 0)
                    if (rating <= 0.0 || votes <= 0) null else TraktTitleRatings(rating, votes)
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * The user's own Trakt ratings (1–10) for movies or shows, IMDb-keyed.
     * Trakt defaults to 10/page, so this follows pagination (max 10 pages).
     */
    suspend fun getMyRatings(accessToken: String, type: String): Map<String, Int> =
        withContext(Dispatchers.IO) {
            val endpoint = if (type == "movie") "movies" else "shows"
            val out = mutableMapOf<String, Int>()
            var page = 1
            var pageCount = 1
            while (page <= pageCount && page <= 10) {
                val request = authed(accessToken)
                    .url("$API_BASE/sync/ratings/$endpoint?page=$page&limit=100").get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("Trakt ratings failed (HTTP ${response.code})")
                    }
                    pageCount = response.header("X-Pagination-Page-Count")?.toIntOrNull() ?: 1
                    val arr = org.json.JSONArray(response.body?.string().orEmpty().ifBlank { "[]" })
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i) ?: continue
                        val rating = item.optInt("rating").takeIf { it in 1..10 } ?: continue
                        val node = item.optJSONObject(if (type == "movie") "movie" else "show") ?: continue
                        val imdb = node.optJSONObject("ids")?.optString("imdb")
                            ?.takeIf { it.startsWith("tt") } ?: continue
                        out[imdb] = rating
                    }
                }
                page++
            }
            out
        }

    /** Rates a title on Trakt (1–10). Returns false when Trakt rejects it. */
    suspend fun setRating(
        accessToken: String,
        type: String,
        imdbId: String,
        title: String,
        rating: Int
    ): Boolean = postRating(accessToken, "ratings", type, imdbId, title, rating)

    /** Removes the user's Trakt rating for a title. */
    suspend fun removeRating(
        accessToken: String,
        type: String,
        imdbId: String,
        title: String
    ): Boolean = postRating(accessToken, "ratings/remove", type, imdbId, title, null)

    private suspend fun postRating(
        accessToken: String,
        path: String,
        type: String,
        imdbId: String,
        title: String,
        rating: Int?
    ): Boolean = withContext(Dispatchers.IO) {
        val group = if (type == "movie") "movies" else "shows"
        val item = JSONObject()
            .put("title", title)
            .put("ids", JSONObject().put("imdb", imdbId))
        if (rating != null) item.put("rating", rating)
        val body = JSONObject().put(group, org.json.JSONArray().put(item))
        val request = authed(accessToken).url("$API_BASE/sync/$path")
            .post(body.toString().toRequestBody(JSON)).build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext false
                val root = JSONObject(response.body?.string().orEmpty().ifBlank { "{}" })
                val added = root.optJSONObject("added")?.let {
                    it.optInt("movies") + it.optInt("shows")
                } ?: 0
                // Re-rating the same value comes back as "existing", not "added".
                val existing = root.optJSONObject("existing")?.let {
                    it.optInt("movies") + it.optInt("shows")
                } ?: 0
                val notFound = root.optJSONObject("not_found")?.let {
                    it.optInt("movies") + it.optInt("shows")
                } ?: 0
                // Remove returns no "added" — success is "not not-found".
                (if (rating == null) 1 else added + existing) > 0 && notFound == 0
            }
        } catch (_: Exception) {
            false
        }
    }
}
