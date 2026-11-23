package com.aviostream.app.data.servers

import com.aviostream.app.BuildConfig
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.Subtitle
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
 * sign-in, libraries, catalog items, watched marking and playback.
 * Playback is the smallest working port of Nuvio's
 * SiloProvider.preparePlayback: installation handshake, watch state
 * for the file id, playback/start for the plan's stream URL — the URL
 * + auth headers go straight into the existing player path. No replan
 * for copy-remux routes and no sequenced progress/stop reporting yet
 * (watched marking still works through markPlayed). Profile PINs are
 * not supported; the primary profile is used directly.
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

    // ---- Playback (smallest port of Nuvio SiloProvider.preparePlayback) ----

    /** A started Silo playback: the plan's stream URL plus the headers
     * the player must send with it (the token, so the URL works), the
     * server's playback session id, the delivery kind, and any sidecar
     * subtitles from the plan. */
    data class SiloPlaybackSession(
        val url: String,
        val headers: Map<String, String>,
        val sessionId: String?,
        val delivery: String,
        val subtitles: List<Subtitle>
    )

    /** The server's playback installation, which every playback call
     * carries. Cached 10 minutes (Nuvio's TTL). Null when playback is
     * switched off or the server is too old for protocol 3. */
    private val installations = mutableMapOf<String, Pair<Long, String>>()

    /** The profile playback belongs to (primary first); "" when the
     * server does not list one. Browsing never needed it. Cached per
     * server: the profile list does not change mid-session. */
    private val profileIds = mutableMapOf<String, String>()

    /** The device this playback comes from, as Silo's own apps name it. */
    private fun deviceHeaders(settings: SettingsStore?): Map<String, String> {
        val deviceId = settings?.mediaServerDeviceId
            ?: java.util.UUID.randomUUID().toString()
        return mapOf(
            "X-Device-ID" to deviceId,
            "X-Silo-Device-Id" to deviceId,
            "X-Client-Name" to "Avio",
            "X-Client-Version" to BuildConfig.VERSION_NAME
        )
    }

    private fun playbackHeaders(
        token: String,
        profileId: String,
        device: Map<String, String>
    ): Map<String, String> = buildMap {
        put("Authorization", bearer(token))
        if (profileId.isNotBlank()) put("X-Profile-Id", profileId)
        putAll(device)
    }

    /** A link from a playback plan: root-relative to the address Silo is used at. */
    private fun resolveStreamUrl(server: MediaServer, url: String): String? {
        val value = url.trim().takeIf { it.isNotEmpty() } ?: return null
        return when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("/") -> cleanBase(server.baseUrl) + value
            else -> null
        }
    }

    private fun randomHexId(): String =
        java.util.UUID.randomUUID().toString().replace("-", "")

    private fun jsonStrings(values: List<String>): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }

    private fun jsonInts(values: List<Int>): JSONArray =
        JSONArray().apply { values.forEach { put(it) } }

    private suspend fun installationId(
        settings: SettingsStore?,
        server: MediaServer
    ): String? {
        val now = System.currentTimeMillis()
        installations[server.id]?.let { (at, id) ->
            if (now - at < 10 * 60_000L) return id
        }
        val (code, body) = executeAuthed(settings, server) { token ->
            Request.Builder()
                .url("${cleanBase(server.baseUrl)}/api/v2/playback/capabilities")
                .header("Authorization", bearer(token))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        } ?: return null
        if (code !in 200..299) return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val protocols = root.optJSONArray("protocol_versions")
        val hasProtocol3 = protocols != null &&
            (0 until protocols.length()).any { protocols.optInt(it) == 3 }
        val features = root.optJSONArray("features")
        val hasSequencedProgress = features != null &&
            (0 until features.length()).any { features.optString(it) == "sequenced_progress_v1" }
        val usable = root.optString("state") == "available" &&
            root.optBoolean("allowed", false) && hasProtocol3 && hasSequencedProgress
        val id = root.optString("installation_id").takeIf { usable && it.isNotBlank() }
            ?: return null
        installations[server.id] = now to id
        return id
    }

    private suspend fun profileId(
        settings: SettingsStore?,
        server: MediaServer
    ): String {
        profileIds[server.id]?.let { return it }
        val (code, body) = executeAuthed(settings, server) { token ->
            Request.Builder()
                .url("${cleanBase(server.baseUrl)}/api/v2/profiles")
                .header("Authorization", bearer(token))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        } ?: return ""
        if (code !in 200..299) return ""
        val items = runCatching {
            JSONObject(body).optJSONArray("items") ?: JSONArray()
        }.getOrDefault(JSONArray())
        var first = ""
        var primary = ""
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            val id = row.optString("id").takeIf { it.isNotBlank() } ?: continue
            if (first.isEmpty()) first = id
            if (row.optBoolean("is_primary", false)) primary = id
        }
        val resolved = primary.ifBlank { first }
        if (resolved.isNotBlank()) profileIds[server.id] = resolved
        return resolved
    }

    /** The file to play: the last-played file when it is still a version
     * of this title, else the first version. Null when the title has no
     * playable file. */
    private suspend fun watchFileId(
        settings: SettingsStore?,
        server: MediaServer,
        contentId: String
    ): String? {
        val (code, body) = executeAuthed(settings, server) { token ->
            Request.Builder()
                .url("${cleanBase(server.baseUrl)}/api/v2/watch/${encode(contentId)}")
                .header("Authorization", bearer(token))
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .get()
                .build()
        } ?: return null
        if (code !in 200..299) return null
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val versions = root.optJSONArray("versions") ?: JSONArray()
        val fileIds = (0 until versions.length()).mapNotNull {
            versions.optJSONObject(it)?.optString("file_id")?.takeIf { s -> s.isNotBlank() }
        }
        if (fileIds.isEmpty()) return null
        val last = root.optJSONObject("user_data")?.optString("last_file_id")
            ?.takeIf { it.isNotBlank() }
        return if (last != null && fileIds.contains(last)) last else fileIds.first()
    }

    private fun deliveryJson(
        containers: List<String>,
        video: List<String>,
        audio: List<String>,
        direct: Boolean
    ): JSONObject = JSONObject().apply {
        put("enabled", true)
        put("supported_on_device", true)
        put("containers", jsonStrings(containers))
        put("video_codecs", jsonStrings(video))
        put("audio_decode_codecs", jsonStrings(audio))
        put("audio_passthrough_codecs", jsonStrings(emptyList()))
        put("subtitles", JSONObject().apply {
            put("embedded_text", direct)
            put("sidecar_text", true)
            put("ass_styling", direct)
            put("embedded_bitmap", direct)
            put("sidecar_bitmap", false)
            put("font_attachments", false)
        })
        put("features", jsonStrings(if (direct) emptyList() else listOf("hls")))
        put("auth_header_refresh", false)
        put(
            "validated_claims",
            jsonStrings(
                if (direct) listOf(
                    "client_managed_dynamic_range_v1",
                    "client_selected_audio_track_v1"
                ) else emptyList()
            )
        )
        put("transformations", JSONArray())
    }

    /** What the player asks Silo for (Nuvio siloStartBody, ExoPlayer
     * support: direct-play the original file when the phone can, else
     * a plain HLS conversion). Servers from before mid October 2026
     * need the legacy shape ("original" + the `metered` flag). */
    private fun startPlaybackBody(
        installationId: String,
        fileId: String,
        profileId: String,
        attemptId: String,
        legacyQuality: Boolean
    ): JSONObject {
        val videoCodecs = listOf("h264", "hevc", "vp8", "vp9", "av1")
        val audioCodecs = listOf("aac", "mp3", "ac3", "eac3", "flac", "opus", "vorbis")
        val containers = listOf("mp4", "m4v", "mkv", "matroska", "webm", "mov")
        val dvProfiles = listOf(8)
        val hdr = JSONObject().apply {
            put("hdr10", true)
            put("hdr10_plus", true)
            put("hlg", true)
            put("dolby_vision_profiles", jsonInts(dvProfiles))
        }
        return JSONObject().apply {
            put("installation_id", installationId)
            put("protocol_version", 3)
            put(
                "client_features",
                jsonStrings(listOf("playback_plan_v3", "direct_stream_resume_v1"))
            )
            put("file_id", fileId)
            if (profileId.isNotBlank()) put("profile_id", profileId)
            put("playback_attempt_id", attemptId)
            put("quality_preference", if (legacyQuality) "original" else "auto")
            put("subtitle_fidelity_preference", "preserve")
            if (legacyQuality) put("metered", false)
            put("start_position", 0)
            put("client_capabilities", JSONObject().apply {
                put("video_evidence", "exact")
                put("audio_evidence", "exact")
                put("codecs_video", jsonStrings(videoCodecs + "dolby_vision"))
                put("codecs_video_hardware", jsonStrings(videoCodecs + "dolby_vision"))
                put("codecs_audio", jsonStrings(audioCodecs))
                put("containers", jsonStrings(containers))
                put("max_resolution", "2160p")
                put("hdr", true)
                put("hdr_details", hdr)
                put("video_decode", JSONArray().apply {
                    videoCodecs.forEach { codec ->
                        put(JSONObject().apply {
                            put("codec", codec)
                            put("hardware", true)
                            put("bit_depths", jsonInts(listOf(8, 10, 12)))
                        })
                    }
                })
            })
            put("client_playback_context", JSONObject().apply {
                put("protocol_version", 3)
                put("form_factor", "mobile")
                put("app_version", BuildConfig.VERSION_NAME.take(64).ifBlank { "1" })
                put("device", JSONObject().apply { put("platform", "android") })
                put("output", JSONObject().apply {
                    put("hdr_details", hdr)
                    put("output_context_id", "1")
                })
                put("deliveries", JSONObject().apply {
                    put("original_http", deliveryJson(containers, videoCodecs, audioCodecs, direct = true))
                    put(
                        "hls",
                        deliveryJson(
                            listOf("m3u8", "hls", "ts", "mp4"),
                            listOf("h264", "hevc"),
                            listOf("aac", "ac3", "eac3"),
                            direct = false
                        )
                    )
                })
            })
        }
    }

    /** One playback decision from POST /api/v2/playback/start, or null
     * when the server refused it. A 422 / validation_failed answer is
     * retried once with the legacy quality shape. */
    private suspend fun startPlayback(
        settings: SettingsStore?,
        server: MediaServer,
        installationId: String,
        profileId: String,
        fileId: String
    ): JSONObject? {
        val attemptId = randomHexId()
        val device = deviceHeaders(settings)
        suspend fun start(legacy: Boolean): Pair<Int, String>? {
            val payload = startPlaybackBody(
                installationId, fileId, profileId, attemptId, legacy
            ).toString()
            return executeAuthed(settings, server) { token ->
                Request.Builder()
                    .url("${cleanBase(server.baseUrl)}/api/v2/playback/start")
                    .apply {
                        playbackHeaders(token, profileId, device).forEach { (k, v) -> header(k, v) }
                    }
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .post(payload.toRequestBody(JSON))
                    .build()
            }
        }
        val (code, body) = start(legacy = false) ?: return null
        val decision = runCatching { JSONObject(body) }.getOrNull()
        val validationFailed = code == 422 ||
            decision?.optString("code") == "validation_failed"
        if (validationFailed) {
            val (retryCode, retryBody) = start(legacy = true) ?: return null
            if (retryCode !in 200..299) return null
            return runCatching { JSONObject(retryBody) }.getOrNull()
        }
        if (code !in 200..299 || decision == null) return null
        return decision
    }

    /**
     * Start a Silo title: installation handshake → watch state for the
     * file id → playback/start for the plan's stream URL. Null when any
     * step fails (playback off, no file, server refused) — the caller
     * shows that instead of playing. The returned URL + headers go
     * straight into the existing player path (PlaybackRequest), like a
     * Plex direct-play URL.
     */
    suspend fun preparePlayback(
        settings: SettingsStore,
        server: MediaServer,
        contentId: String
    ): SiloPlaybackSession? = withContext(Dispatchers.IO) {
        try {
            val fresh = resolveServer(settings, server)
            val installation = installationId(settings, fresh) ?: return@withContext null
            val profile = profileId(settings, fresh)
            val fileId = watchFileId(settings, fresh, contentId) ?: return@withContext null
            val decision = startPlayback(settings, fresh, installation, profile, fileId)
                ?: return@withContext null
            if (decision.optString("outcome") != "playable") return@withContext null
            val plan = decision.optJSONObject("playback_plan") ?: return@withContext null
            val rawUrl = plan.optJSONObject("stream")?.optString("url")
                ?.takeIf { it.isNotBlank() } ?: return@withContext null
            val url = resolveStreamUrl(server, rawUrl) ?: return@withContext null
            val sessionId = decision.optString("session_id").takeIf { it.isNotBlank() }
            val delivery = plan.optString("delivery")
            val headers = buildMap<String, String> {
                put("Authorization", bearer(fresh.accessToken))
                if (profile.isNotBlank()) put("X-Profile-Id", profile)
            }
            val inventory = plan.optJSONObject("subtitle")?.optJSONArray("inventory")
            val subtitles = if (inventory == null) emptyList() else {
                (0 until inventory.length()).mapNotNull { i ->
                    val entry = inventory.optJSONObject(i) ?: return@mapNotNull null
                    if (entry.optString("delivery") != "sidecar") return@mapNotNull null
                    val subUrl = entry.optString("url").takeIf { it.isNotBlank() }
                        ?.let { resolveStreamUrl(server, it) } ?: return@mapNotNull null
                    Subtitle(
                        url = subUrl,
                        lang = entry.optString("language").takeIf { it.isNotBlank() } ?: "und"
                    )
                }
            }
            SiloPlaybackSession(
                url = url,
                headers = headers,
                sessionId = sessionId,
                delivery = delivery,
                subtitles = subtitles
            )
        } catch (_: Exception) {
            null
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
