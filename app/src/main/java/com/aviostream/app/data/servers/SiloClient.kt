package com.aviostream.app.data.servers

import com.aviostream.app.BuildConfig
import com.aviostream.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Silo client (Nuvio servers/silo/, scoped port): device-code
 * sign-in, libraries, catalog items and watched marking. Silo's
 * negotiated playback session (installation handshake, sequenced
 * progress, HLS/direct delivery) is NOT ported — Silo titles browse
 * in the Library/Home rows, but playback needs that protocol and is
 * deferred. Profile PINs are not supported either; the account token
 * is used directly.
 */
object SiloClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val userAgent = "Avio/${BuildConfig.VERSION_NAME}"

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
            val expiresAtMs: Long,
            val username: String?
        ) : PollResult

        data object Pending : PollResult
        data object SlowDown : PollResult
        data object Expired : PollResult
        data object Denied : PollResult
        data object Failed : PollResult
    }

    private fun cleanBase(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    private fun bearer(token: String) = "Bearer $token"

    /** GET {base}/api/v2/system/info — the server name, or null when unreachable. */
    suspend fun getSystemInfo(baseUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${cleanBase(baseUrl)}/api/v2/system/info")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                root.optString("name").takeIf { it.isNotBlank() }
                    ?: root.optString("server_name").takeIf { it.isNotBlank() }
                    ?: "Silo"
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST /api/v2/auth/device/start — null when the request fails. */
    suspend fun requestDeviceCode(baseUrl: String): DeviceData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${cleanBase(baseUrl)}/api/v2/auth/device/start")
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .post(JSONObject().toString().toRequestBody(JSON))
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
                    verificationUrl = root.optString("verification_uri_complete")
                        .takeIf { it.isNotBlank() }
                        ?: root.optString("verification_uri").takeIf { it.isNotBlank() }
                        ?: cleanBase(baseUrl),
                    intervalSec = root.optLong("interval", 3L).coerceIn(1L, 60L),
                    expiresInSec = root.optLong("expires_in", 900L).coerceIn(60L, 3600L)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST /api/v2/auth/device/poll {"device_code"} — poll until Approved. */
    suspend fun pollDeviceToken(baseUrl: String, deviceCode: String): PollResult =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("device_code", deviceCode).toString()
                    .toRequestBody(JSON)
                val req = Request.Builder()
                    .url("${cleanBase(baseUrl)}/api/v2/auth/device/poll")
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) return@withContext PollResult.Failed
                    val root = JSONObject(text)
                    return@withContext when (root.optString("status")) {
                        "approved" -> {
                            val tokens = root.optJSONObject("tokens")
                                ?: return@withContext PollResult.Failed
                            val access = tokens.optString("access_token")
                                .takeIf { it.isNotBlank() } ?: return@withContext PollResult.Failed
                            val refresh = tokens.optString("refresh_token")
                                .takeIf { it.isNotBlank() } ?: return@withContext PollResult.Failed
                            val expiresAt = tokens.optLong("expires_in", 0L)
                                .takeIf { it > 0L }
                                ?.let { System.currentTimeMillis() + it * 1000L }
                                ?: 0L
                            val user = tokens.optJSONObject("user")
                            PollResult.Approved(
                                accessToken = access,
                                refreshToken = refresh,
                                expiresAtMs = expiresAt,
                                username = user?.optString("username")?.takeIf { it.isNotBlank() }
                            )
                        }
                        "pending" -> PollResult.Pending
                        "denied" -> PollResult.Denied
                        "expired", "used" -> PollResult.Expired
                        else -> PollResult.Pending
                    }
                }
            } catch (_: Exception) {
                PollResult.Failed
            }
        }

    /** A refreshed token pair: the new access token, a rotated refresh
     * token when the server issued one, and the new access-token expiry. */
    data class RefreshedTokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMs: Long
    )

    /** Refresh at most this long before the stored expiry, so a request
     * that starts now never runs on a token that dies mid-request. */
    private const val REFRESH_MARGIN_MS = 60_000L

    /** Refreshed servers held in memory so a screen still holding the
     * pre-refresh copy keeps working; the persisted copy is the source
     * of truth after a cold start. Keyed by server id. */
    private val freshServers = mutableMapOf<String, MediaServer>()

    /** One refresh at a time: a rotated refresh token must not be spent twice. */
    private val refreshMutex = Mutex()

    /** POST /api/v2/auth/refresh {"refresh_token"} — null on failure. */
    suspend fun refreshAccessToken(server: MediaServer): RefreshedTokens? =
        withContext(Dispatchers.IO) {
            val refreshToken = server.refreshToken?.takeIf { it.isNotBlank() }
                ?: return@withContext null
            try {
                val body = JSONObject().put("refresh_token", refreshToken).toString()
                    .toRequestBody(JSON)
                val req = Request.Builder()
                    .url("${cleanBase(server.baseUrl)}/api/v2/auth/refresh")
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .post(body)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    val root = JSONObject(resp.body?.string().orEmpty())
                    val tokens = root.optJSONObject("tokens")
                    val access = root.optString("access_token").takeIf { it.isNotBlank() }
                        ?: tokens?.optString("access_token")?.takeIf { it.isNotBlank() }
                        ?: return@withContext null
                    val rotated = root.optString("refresh_token").takeIf { it.isNotBlank() }
                        ?: tokens?.optString("refresh_token")?.takeIf { it.isNotBlank() }
                    val expiresIn = root.optLong("expires_in", 0L).takeIf { it > 0L }
                        ?: tokens?.optLong("expires_in", 0L)?.takeIf { it > 0L }
                    RefreshedTokens(
                        accessToken = access,
                        refreshToken = rotated,
                        expiresAtMs = expiresIn
                            ?.let { System.currentTimeMillis() + it * 1000L } ?: 0L
                    )
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * The server copy to use for a request: the in-memory refreshed copy
     * when it is still fresh, otherwise a refresh first when the stored
     * token is expired or about to expire. Falls back to [server] when
     * there is nothing to refresh with (the 401 retry below is the
     * backstop for an unknown expiry — [MediaServer.tokenExpiresAtMs]
     * is 0 when sign-in never reported one).
     */
    private suspend fun resolveServer(
        settings: SettingsStore?,
        server: MediaServer
    ): MediaServer {
        freshServers[server.id]?.let { cached ->
            if (cached.tokenExpiresAtMs <= 0L ||
                System.currentTimeMillis() < cached.tokenExpiresAtMs - REFRESH_MARGIN_MS
            ) return cached
        }
        if (server.tokenExpiresAtMs > 0L &&
            System.currentTimeMillis() >= server.tokenExpiresAtMs - REFRESH_MARGIN_MS
        ) {
            refreshAndStore(settings, server)?.let { return it }
        }
        return freshServers[server.id] ?: server
    }

    /**
     * Spend the refresh token once, cache the new pair and persist it
     * when [settings] is supplied. Null when there is no refresh token
     * or the server refused it — then the sign-in is dead and the user
     * has to sign in again.
     */
    private suspend fun refreshAndStore(
        settings: SettingsStore?,
        server: MediaServer
    ): MediaServer? = refreshMutex.withLock {
        val base = freshServers[server.id] ?: server
        val refreshed = refreshAccessToken(base) ?: return@withLock null
        val updated = base.copy(
            accessToken = refreshed.accessToken,
            refreshToken = refreshed.refreshToken ?: base.refreshToken,
            tokenExpiresAtMs = refreshed.expiresAtMs.takeIf { it > 0L }
                ?: base.tokenExpiresAtMs
        )
        freshServers[server.id] = updated
        if (settings != null) {
            runCatching {
                settings.saveMediaServers(
                    settings.getMediaServers().map { if (it.id == server.id) updated else it }
                )
            }
        }
        updated
    }

    /**
     * Run one authenticated request: refresh first when the token is
     * expired (or about to be), and retry once on 401 with a fresh
     * token. Returns (HTTP code, body) or null when the request itself
     * failed (no network, timeout) — callers treat that as before.
     */
    private suspend fun executeAuthed(
        settings: SettingsStore?,
        server: MediaServer,
        build: (token: String) -> Request
    ): Pair<Int, String>? {
        var current = resolveServer(settings, server)
        repeat(2) { attempt ->
            val result = try {
                client.newCall(build(current.accessToken)).execute().use { resp ->
                    resp.code to (resp.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                return null
            }
            if (result.first != 401 || attempt == 1) return result
            current = refreshAndStore(settings, current) ?: return result
        }
        return null
    }

    /** GET /api/v2/user/libraries — {items: [{id, name, type, sort_order}]}. */
    suspend fun getLibraries(
        server: MediaServer,
        settings: SettingsStore? = null
    ): List<ServerLibrary> =
        withContext(Dispatchers.IO) {
            try {
                val (code, body) = executeAuthed(settings, server) { token ->
                    Request.Builder()
                        .url("${cleanBase(server.baseUrl)}/api/v2/user/libraries")
                        .header("Authorization", bearer(token))
                        .header("User-Agent", userAgent)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                } ?: return@withContext emptyList()
                if (code !in 200..299) return@withContext emptyList()
                val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
                (0 until items.length()).mapNotNull { i ->
                    val row = items.optJSONObject(i) ?: return@mapNotNull null
                    val id = row.optString("id").takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val kind = when (row.optString("type").lowercase()) {
                        "movie", "movies" -> ServerLibraryKind.MOVIE
                        "show", "shows", "series", "tv" -> ServerLibraryKind.SERIES
                        else -> ServerLibraryKind.MIXED
                    }
                    ServerLibrary(
                        id = id,
                        name = row.optString("name").takeIf { it.isNotBlank() } ?: id,
                        kind = kind
                    )
                } // Response order is the server's order.
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * GET /api/v2/catalog?source=query&library_id={id}&type={movie|series}
     * &sort=-added_at&image_size=medium&limit=100.
     */
    suspend fun getCatalogItems(
        server: MediaServer,
        library: ServerLibrary,
        settings: SettingsStore? = null
    ): List<ServerItem> = withContext(Dispatchers.IO) {
        val type = when (library.kind) {
            ServerLibraryKind.MOVIE -> "movie"
            ServerLibraryKind.SERIES -> "series"
            ServerLibraryKind.MIXED -> null
        }
        try {
            val url = buildString {
                append(cleanBase(server.baseUrl))
                append("/api/v2/catalog?source=query&library_id=")
                append(java.net.URLEncoder.encode(library.id, "UTF-8"))
                if (type != null) append("&type=").append(type)
                append("&sort=-added_at&image_size=medium&limit=100")
            }
            val (code, body) = executeAuthed(settings, server) { token ->
                Request.Builder()
                    .url(url)
                    .header("Authorization", bearer(token))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            } ?: return@withContext emptyList()
            if (code !in 200..299) return@withContext emptyList()
            val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
            (0 until items.length()).mapNotNull { i ->
                parseCatalogItem(items.optJSONObject(i) ?: return@mapNotNull null)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * GET /api/v2/catalog/series/{id} — episodes of a series
     * ({episodes: [CatalogItemWire]} when present).
     */
    suspend fun getSeriesEpisodes(
        server: MediaServer,
        seriesId: String,
        settings: SettingsStore? = null
    ): List<ServerItem> = withContext(Dispatchers.IO) {
        try {
            val (code, body) = executeAuthed(settings, server) { token ->
                Request.Builder()
                    .url("${cleanBase(server.baseUrl)}/api/v2/catalog/series/${encode(seriesId)}")
                    .header("Authorization", bearer(token))
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            } ?: return@withContext emptyList()
            if (code !in 200..299) return@withContext emptyList()
            val root = JSONObject(body)
            val episodes = root.optJSONArray("episodes") ?: JSONArray()
            (0 until episodes.length()).mapNotNull { i ->
                parseCatalogItem(episodes.optJSONObject(i) ?: return@mapNotNull null)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** POST/DELETE /api/v2/watched/{contentId} (no body). */
    suspend fun setPlayed(
        server: MediaServer,
        contentId: String,
        played: Boolean,
        settings: SettingsStore? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, _) = executeAuthed(settings, server) { token ->
                Request.Builder()
                    .url("${cleanBase(server.baseUrl)}/api/v2/watched/${encode(contentId)}")
                    .header("Authorization", bearer(token))
                    .header("User-Agent", userAgent)
                    .let {
                        if (played) it.post(ByteArray(0).toRequestBody(null))
                        else it.delete()
                    }
                    .build()
            } ?: return@withContext false
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

    private fun parseCatalogItem(row: JSONObject): ServerItem? {
        val contentId = row.optString("content_id").takeIf { it.isNotBlank() } ?: return null
        val type = when (row.optString("type").lowercase()) {
            "movie" -> "movie"
            "series", "show" -> "series"
            "episode" -> "episode"
            else -> return null
        }
        val title = row.optString("title").takeIf { it.isNotBlank() } ?: return null
        val userState = row.optJSONObject("user_state")
        val userData = row.optJSONObject("user_data")
        return ServerItem(
            contentId = contentId,
            type = type,
            title = title,
            year = row.optInt("year", 0).takeIf { it > 0 }
                ?: row.optString("release_date").take(4).toIntOrNull()
                ?: row.optString("first_air_date").take(4).toIntOrNull(),
            posterUrl = row.optString("poster_url").takeIf { it.isNotBlank() }
                ?: row.optString("series_poster_url").takeIf { it.isNotBlank() },
            backdropUrl = row.optString("backdrop_url").takeIf { it.isNotBlank() }
                ?: row.optString("series_backdrop_url").takeIf { it.isNotBlank() },
            overview = row.optString("overview").takeIf { it.isNotBlank() },
            imdbId = row.optString("imdb_id").takeIf { it.startsWith("tt", ignoreCase = true) },
            season = row.optInt("season_number", 0).takeIf { it > 0 },
            episode = row.optInt("episode_number", 0).takeIf { it > 0 },
            seriesId = row.optString("series_id").takeIf { it.isNotBlank() },
            seriesTitle = row.optString("series_title").takeIf { it.isNotBlank() },
            runtimeSecs = row.optDouble("duration_seconds", Double.NaN)
                .takeIf { !it.isNaN() }?.toLong()
                ?: row.optInt("runtime", 0).takeIf { it > 0 }?.let { it * 60L },
            played = userState?.optBoolean("played", false)
                ?: userData?.optBoolean("played", false) ?: false,
            positionSecs = userData?.optDouble("position_seconds", Double.NaN)
                ?.takeIf { !it.isNaN() }
                ?: row.optDouble("position_seconds", Double.NaN).takeIf { !it.isNaN() }
        )
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
