package com.pulsestream.app.data.servers

import com.pulsestream.app.BuildConfig
import com.pulsestream.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.w3c.dom.Element
import java.io.StringReader
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

/**
 * Plex client (Nuvio servers/plex/, scoped port): PIN sign-in via
 * plex.tv, resource discovery, library sections + items, direct-play
 * stream URLs and scrobble. Transcoding is NOT ported — direct play
 * only; a file the device cannot play directly will fail in the
 * player instead of transcoding.
 */
object PlexClient {

    private const val PLEX_TV = "https://plex.tv"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    data class PinData(
        val pinId: Long,
        val code: String
    )

    /** A reachable Plex server with its best connection URI. */
    data class PlexResource(
        val name: String,
        val machineId: String,
        val baseUrl: String,
        val token: String
    )

    private fun plexHeaders(settings: SettingsStore): Map<String, String> = mapOf(
        "X-Plex-Product" to "Plavio",
        "X-Plex-Version" to BuildConfig.VERSION_NAME,
        "X-Plex-Client-Identifier" to settings.mediaServerDeviceId,
        "X-Plex-Device" to "Android",
        "X-Plex-Platform" to "Android",
        "Accept" to "application/json"
    )

    /** POST /api/v2/pins?strong=true — the code the user approves at plex.tv/link. */
    suspend fun requestPin(settings: SettingsStore): PinData? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$PLEX_TV/api/v2/pins?strong=true")
                .apply { plexHeaders(settings).forEach { (k, v) -> header(k, v) } }
                .post(ByteArray(0).toRequestBody(JSON))
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val root = JSONObject(resp.body?.string().orEmpty())
                val id = root.optLong("id", 0L).takeIf { it > 0L } ?: return@withContext null
                val code = root.optString("code").takeIf { it.isNotBlank() }
                    ?: return@withContext null
                PinData(pinId = id, code = code)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** GET /api/v2/pins/{id} — the auth token once the user approved, else null. */
    suspend fun pollPin(settings: SettingsStore, pinId: Long): String? =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$PLEX_TV/api/v2/pins/$pinId")
                    .apply { plexHeaders(settings).forEach { (k, v) -> header(k, v) } }
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext null
                    JSONObject(resp.body?.string().orEmpty())
                        .optString("authToken").takeIf { it.isNotBlank() }
                }
            } catch (_: Exception) {
                null
            }
        }

    /**
     * GET /api/v2/resources — servers the account can reach, each with
     * its best connection (local first, then remote, relay last).
     */
    suspend fun getResources(settings: SettingsStore, authToken: String): List<PlexResource> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$PLEX_TV/api/v2/resources?includeHttps=1&includeRelay=1")
                    .header("X-Plex-Token", authToken)
                    .apply { plexHeaders(settings).forEach { (k, v) -> header(k, v) } }
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val doc = parseXml(resp.body?.string().orEmpty()) ?: return@withContext emptyList()
                    val devices = doc.getElementsByTagName("Device")
                    (0 until devices.length).mapNotNull { i ->
                        val device = devices.item(i) as? Element ?: return@mapNotNull null
                        if (device.getAttribute("provides")?.contains("server") != true) {
                            return@mapNotNull null
                        }
                        val token = device.getAttribute("accessToken")
                            .takeIf { it.isNotBlank() } ?: authToken
                        val name = device.getAttribute("name")
                            .takeIf { it.isNotBlank() } ?: "Plex server"
                        val machineId = device.getAttribute("clientIdentifier")
                        val connections = device.getElementsByTagName("Connection")
                        var best: String? = null
                        var bestScore = Int.MAX_VALUE
                        for (j in 0 until connections.length) {
                            val conn = connections.item(j) as? Element ?: continue
                            val uri = conn.getAttribute("uri").takeIf { it.isNotBlank() } ?: continue
                            val relay = conn.getAttribute("relay") == "1"
                            val local = conn.getAttribute("local") == "1"
                            val score = when {
                                local && !relay -> 0
                                !relay -> 1
                                else -> 2
                            }
                            if (score < bestScore) {
                                bestScore = score
                                best = uri
                            }
                        }
                        best?.let { PlexResource(name, machineId, it.removeSuffix("/"), token) }
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /** GET {base}/library/sections — movie + show libraries. */
    suspend fun getSections(server: MediaServer): List<ServerLibrary> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${server.baseUrl}/library/sections")
                    .header("X-Plex-Token", server.accessToken)
                    .header("Accept", "application/xml")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val doc = parseXml(resp.body?.string().orEmpty()) ?: return@withContext emptyList()
                    val dirs = doc.getElementsByTagName("Directory")
                    (0 until dirs.length).mapNotNull { i ->
                        val dir = dirs.item(i) as? Element ?: return@mapNotNull null
                        val key = dir.getAttribute("key").takeIf { it.isNotBlank() }
                            ?: return@mapNotNull null
                        val kind = when (dir.getAttribute("type")) {
                            "movie" -> ServerLibraryKind.MOVIE
                            "show" -> ServerLibraryKind.SERIES
                            else -> return@mapNotNull null
                        }
                        ServerLibrary(
                            id = key,
                            name = dir.getAttribute("title").takeIf { it.isNotBlank() } ?: key,
                            kind = kind
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    /**
     * GET {base}/library/sections/{key}/all?type=1|2 — movies or shows.
     * Episodes of a show: getChildren(ratingKey) twice (seasons, then
     * a season's episodes).
     */
    suspend fun getSectionItems(
        server: MediaServer,
        libraryId: String,
        type: String
    ): List<ServerItem> = withContext(Dispatchers.IO) {
        val plexType = if (type == "series") "2" else "1"
        try {
            val req = Request.Builder()
                .url("${server.baseUrl}/library/sections/$libraryId/all?type=$plexType&includeGuids=1")
                .header("X-Plex-Token", server.accessToken)
                .header("Accept", "application/xml")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val doc = parseXml(resp.body?.string().orEmpty()) ?: return@withContext emptyList()
                val metas = doc.getElementsByTagName("Video")
                (0 until metas.length).mapNotNull { i ->
                    parseVideo(server, metas.item(i) as? Element ?: return@mapNotNull null)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** GET {base}/library/metadata/{ratingKey}/children — seasons, or a season's episodes. */
    suspend fun getChildren(server: MediaServer, ratingKey: String): List<ServerItem> =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("${server.baseUrl}/library/metadata/$ratingKey/children")
                    .header("X-Plex-Token", server.accessToken)
                    .header("Accept", "application/xml")
                    .get()
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@withContext emptyList()
                    val doc = parseXml(resp.body?.string().orEmpty()) ?: return@withContext emptyList()
                    val out = mutableListOf<ServerItem>()
                    val dirs = doc.getElementsByTagName("Directory")
                    for (i in 0 until dirs.length) {
                        val dir = dirs.item(i) as? Element ?: continue
                        // Seasons: a Directory with a ratingKey and an index.
                        val key = dir.getAttribute("ratingKey").takeIf { it.isNotBlank() } ?: continue
                        val index = dir.getAttribute("index").toIntOrNull()
                        out += ServerItem(
                            contentId = key,
                            type = "series",
                            title = dir.getAttribute("title").takeIf { it.isNotBlank() } ?: "Season $index",
                            year = dir.getAttribute("year").toIntOrNull(),
                            posterUrl = imageUrl(server, dir.getAttribute("thumb")),
                            backdropUrl = null,
                            overview = dir.getAttribute("summary").takeIf { it.isNotBlank() },
                            imdbId = null,
                            season = index,
                            episode = null,
                            seriesId = ratingKey,
                            seriesTitle = null,
                            runtimeSecs = null,
                            played = false,
                            positionSecs = null
                        )
                    }
                    val videos = doc.getElementsByTagName("Video")
                    for (i in 0 until videos.length) {
                        parseVideo(server, videos.item(i) as? Element ?: continue)?.let { out += it }
                    }
                    out
                }
            } catch (_: Exception) {
                emptyList()
            }
        }

    private fun parseVideo(server: MediaServer, el: Element): ServerItem? {
        val ratingKey = el.getAttribute("ratingKey").takeIf { it.isNotBlank() } ?: return null
        val type = when (el.getAttribute("type")) {
            "movie" -> "movie"
            "episode" -> "episode"
            "show" -> "series"
            else -> return null
        }
        val title = el.getAttribute("title").takeIf { it.isNotBlank() } ?: return null
        val partKey = el.getElementsByTagName("Part").let { parts ->
            (0 until parts.length).mapNotNull { (parts.item(it) as? Element)?.getAttribute("key") }
                .firstOrNull { it.isNotBlank() }
        }
        val viewOffset = el.getAttribute("viewOffset").toLongOrNull()
        val duration = el.getAttribute("duration").toLongOrNull()
        return ServerItem(
            contentId = if (partKey != null) "$ratingKey|$partKey" else ratingKey,
            type = type,
            title = title,
            year = el.getAttribute("year").toIntOrNull(),
            posterUrl = imageUrl(server, el.getAttribute("thumb")),
            backdropUrl = null,
            overview = el.getAttribute("summary").takeIf { it.isNotBlank() },
            imdbId = imdbFromGuids(el),
            season = el.getAttribute("parentIndex").toIntOrNull(),
            episode = el.getAttribute("index").toIntOrNull(),
            seriesId = el.getAttribute("grandparentRatingKey").takeIf { it.isNotBlank() },
            seriesTitle = el.getAttribute("grandparentTitle").takeIf { it.isNotBlank() },
            runtimeSecs = duration?.let { it / 1000L },
            played = el.getAttribute("viewCount").toIntOrNull()?.let { it > 0 } ?: false,
            positionSecs = viewOffset?.let { it / 1000.0 }
        )
    }

    private fun imdbFromGuids(el: Element): String? {
        val guids = el.getElementsByTagName("Guid")
        for (i in 0 until guids.length) {
            val id = (guids.item(i) as? Element)?.getAttribute("id") ?: continue
            // com.plexapp.agents.imdb://tt1234567?lang=en
            val imdb = id.substringAfter("imdb://", "").substringBefore("?")
            if (imdb.startsWith("tt")) return imdb
            val tmdb = id.substringAfter("tmdb://", "").substringBefore("?")
            if (tmdb.toLongOrNull() != null) return "tmdb:$tmdb"
        }
        return null
    }

    /** Direct-play URL for a part: {base}{partKey}?X-Plex-Token=... */
    fun streamUrl(server: MediaServer, item: ServerItem): String? {
        val partKey = item.contentId.substringAfter("|", "")
        if (partKey.isBlank()) return null
        return "${server.baseUrl}$partKey?X-Plex-Token=<redacted>"
    }

    fun imageUrl(server: MediaServer, thumb: String?): String? {
        if (thumb.isNullOrBlank()) return null
        val encoded = java.net.URLEncoder.encode(
            if (thumb.startsWith("http")) thumb else "${server.baseUrl}$thumb", "UTF-8"
        )
        return "${server.baseUrl}/photo/:/transcode?url=$encoded&width=600&height=900&minSize=1&X-Plex-Token=<redacted>"
    }

    /** GET /:/scrobble (played) or /:/unscrobble. */
    suspend fun markPlayed(
        server: MediaServer,
        item: ServerItem,
        played: Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        val ratingKey = item.contentId.substringBefore("|")
        try {
            val action = if (played) "scrobble" else "unscrobble"
            val req = Request.Builder()
                .url("${server.baseUrl}/:/$action?key=$ratingKey&identifier=com.plexapp.plugins.library&X-Plex-Token=<redacted>")
                .header("Accept", "application/xml")
                .get()
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    private fun parseXml(text: String): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.newDocumentBuilder().parse(InputSource(StringReader(text)))
    }.getOrNull()
}
