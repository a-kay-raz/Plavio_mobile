package com.aviostream.app.data.servers

import com.aviostream.app.BuildConfig
import com.aviostream.app.data.SettingsStore
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
 * Jellyfin + Emby client (Nuvio servers/mediabrowser/, scoped port).
 * Both speak the same MediaBrowser API: username/password auth, Views
 * as libraries, Items with ParentId, direct stream URLs, played
 * marking. Auth uses X-Emby-Authorization; reads use api_key.
 */
object MediaBrowserClient {

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class AuthResult(
        val userId: String,
        val username: String,
        val token: String,
        val serverName: String?
    )

    private fun authHeader(settings: SettingsStore, token: String? = null): String =
        buildString {
            append("MediaBrowser Client=\"Avio\", Device=\"Android\", ")
            append("DeviceId=\"${settings.mediaServerDeviceId}\", ")
            append("Version=\"${BuildConfig.VERSION_NAME}\"")
            if (token != null) append(", Token=\"$token\"")
        }

    private fun cleanBase(baseUrl: String): String = baseUrl.trim().removeSuffix("/")

    /** GET {base}/System/Info/Public — the server name, or null when unreachable. */
    suspend fun getPublicInfo(baseUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${cleanBase(baseUrl)}/System/Info/Public")
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                JSONObject(resp.body?.string().orEmpty())
                    .optString("ServerName").takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    /** POST /Users/AuthenticateByName — null when the login fails. */
    suspend fun authenticate(
        settings: SettingsStore,
        baseUrl: String,
        username: String,
        password: String
    ): AuthResult? = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString().toRequestBody(JSON)
            val req = Request.Builder()
                .url("${cleanBase(baseUrl)}/Users/AuthenticateByName")
                .header("X-Emby-Authorization", authHeader(settings))
                .header("Accept", "application/json")
                .post(body)
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val token = root.optString("AccessToken").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                val user = root.optJSONObject("User") ?: return@withContext null
                val userId = user.optString("Id").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                AuthResult(
                    userId = userId,
                    username = user.optString("Name").takeIf { it.isNotBlank() } ?: username,
                    token = token,
                    serverName = root.optString("ServerName").takeIf { it.isNotBlank() }
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /** GET /Users/{userId}/Views — libraries (movies + tvshows only). */
    suspend fun getLibraries(server: MediaServer): List<ServerLibrary> =
        withContext(Dispatchers.IO) {
            val base = cleanBase(server.baseUrl)
            val userId = server.userId ?: return@withContext emptyList()
            try {
                val req = Request.Builder()
                    .url("$base/Users/$userId/Views?api_key=<redacted>")
                    .header("Accept", "application/json")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val items = JSONObject(resp.body?.string().orEmpty())
                        .optJSONArray("Items") ?: JSONArray()
                    (0 until items.length()).mapNotNull { i ->
                        val row = items.optJSONObject(i) ?: return@mapNotNull null
                        val id = row.optString("Id").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val kind = when (row.optString("CollectionType")) {
                            "movies" -> ServerLibraryKind.MOVIE
                            "tvshows" -> ServerLibraryKind.SERIES
                            "mixed", "" -> ServerLibraryKind.MIXED
                            else -> return@mapNotNull null
                        }
                        ServerLibrary(
                            id = id,
                            name = row.optString("Name").takeIf { it.isNotBlank() } ?: id,
                            kind = kind
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * GET /Users/{userId}/Items — movies/series in a library, or episodes
     * of a series (parentId = series id, type = "episode").
     */
    suspend fun getItems(
        server: MediaServer,
        libraryId: String,
        type: String
    ): List<ServerItem> = withContext(Dispatchers.IO) {
        val base = cleanBase(server.baseUrl)
        val userId = server.userId ?: return@withContext emptyList()
        val includeTypes = when (type) {
            "movie" -> "Movie"
            "series" -> "Series"
            "episode" -> "Episode"
            else -> "Movie,Series"
        }
        val sortBy = if (type == "episode") "ParentIndexNumber,IndexNumber" else "SortName"
        try {
            val url = "$base/Users/$userId/Items" +
                "?ParentId=${encode(libraryId)}" +
                "&Recursive=true" +
                "&IncludeItemTypes=$includeTypes" +
                "&Fields=Overview,ProductionYear,Genres,RunTimeTicks,UserData,MediaSources,Path,PrimaryImageAspectRatio" +
                "&SortBy=$sortBy&SortOrder=Ascending" +
                "&api_key=<redacted>"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val items = JSONObject(resp.body?.string().orEmpty())
                    .optJSONArray("Items") ?: JSONArray()
                (0 until items.length()).mapNotNull { i ->
                    parseItem(server, items.optJSONObject(i) ?: return@mapNotNull null)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseItem(server: MediaServer, row: JSONObject): ServerItem? {
        val id = row.optString("Id").takeIf { it.isNotBlank() } ?: return null
        val type = when (row.optString("Type")) {
            "Movie" -> "movie"
            "Series" -> "series"
            "Episode" -> "episode"
            else -> return null
        }
        val userData = row.optJSONObject("UserData")
        val ticks = row.optLong("RunTimeTicks", 0L)
        val mediaSourceId = row.optJSONArray("MediaSources")
            ?.optJSONObject(0)?.optString("Id")?.takeIf { it.isNotBlank() }
        return ServerItem(
            contentId = id,
            type = type,
            title = row.optString("Name").takeIf { it.isNotBlank() } ?: return null,
            year = row.optInt("ProductionYear", 0).takeIf { it > 0 },
            posterUrl = imageUrl(server, id),
            backdropUrl = backdropUrl(server, id),
            overview = row.optString("Overview").takeIf { it.isNotBlank() },
            // Jellyfin/Emby report ProviderIds (Imdb/Tmdb/Tvdb) on Items
            // by default — the IMDb id lets Discover taps open the Cinemeta
            // detail page for a server title.
            imdbId = row.optJSONObject("ProviderIds")
                ?.optString("Imdb")?.takeIf { it.startsWith("tt", ignoreCase = true) },
            season = row.optInt("ParentIndexNumber", 0).takeIf { it > 0 },
            episode = row.optInt("IndexNumber", 0).takeIf { it > 0 },
            seriesId = row.optString("SeriesId").takeIf { it.isNotBlank() },
            seriesTitle = row.optString("SeriesName").takeIf { it.isNotBlank() },
            runtimeSecs = if (ticks > 0) ticks / 10_000_000L else null,
            played = userData?.optBoolean("Played", false) ?: false,
            positionSecs = userData?.optLong("PlaybackPositionTicks", 0L)
                ?.takeIf { it > 0L }?.let { it / 10_000_000.0 }
        ).let { it.copy(contentId = if (mediaSourceId != null) "$id|$mediaSourceId" else id) }
    }

    /** Direct stream URL (Static=true serves the file as-is). */
    fun streamUrl(server: MediaServer, item: ServerItem): String {
        val (itemId, mediaSourceId) = item.contentId.split("|").let {
            it[0] to it.getOrNull(1)
        }
        return buildString {
            append(cleanBase(server.baseUrl))
            append("/Videos/").append(itemId).append("/stream")
            append("?api_key=<redacted>")
            append("&Static=true")
            if (mediaSourceId != null) append("&MediaSourceId=").append(encode(mediaSourceId))
        }
    }

    fun imageUrl(server: MediaServer, itemId: String): String {
        val cleanId = itemId.substringBefore("|")
        return "${cleanBase(server.baseUrl)}/Items/$cleanId/Images/Primary?api_key=<redacted>&maxWidth=600&quality=80"
    }

    private fun backdropUrl(server: MediaServer, itemId: String): String {
        val cleanId = itemId.substringBefore("|")
        return "${cleanBase(server.baseUrl)}/Items/$cleanId/Images/Backdrop?api_key=<redacted>&maxWidth=1280&quality=80"
    }

    /** POST/DELETE /Users/{userId}/PlayedItems/{itemId}. */
    suspend fun markPlayed(
        server: MediaServer,
        itemId: String,
        played: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val base = cleanBase(server.baseUrl)
        val userId = server.userId ?: return@withContext false
        val cleanId = itemId.substringBefore("|")
        try {
            val req = Request.Builder()
                .url("$base/Users/$userId/PlayedItems/$cleanId?api_key=<redacted>")
                .header("Accept", "application/json")
                .let {
                    if (played) it.post(ByteArray(0).toRequestBody(null))
                    else it.delete()
                }
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")
}
