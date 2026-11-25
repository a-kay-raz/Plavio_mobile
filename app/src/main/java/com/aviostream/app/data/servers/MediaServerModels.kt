package com.aviostream.app.data.servers

import org.json.JSONArray
import org.json.JSONObject

/**
 * Step 55: media servers (Nuvio servers/, scoped port). One stored
 * connection per server; tokens live in the same encrypted blob as the
 * rest of the connection (SettingsStore secret "media_servers"), never
 * in plain prefs.
 */
enum class ServerKind(val stored: String, val label: String) {
    JELLYFIN("jellyfin", "Jellyfin"),
    EMBY("emby", "Emby"),
    PLEX("plex", "Plex"),
    SILO("silo", "Silo");

    companion object {
        fun fromStored(value: String?): ServerKind? =
            entries.firstOrNull { it.stored == value }
    }
}

enum class ServerLibraryKind(val stored: String) {
    MOVIE("movie"),
    SERIES("series"),
    MIXED("mixed");

    companion object {
        fun fromStored(value: String?): ServerLibraryKind =
            entries.firstOrNull { it.stored == value } ?: MIXED
    }
}

data class MediaServer(
    val id: String,
    val kind: ServerKind,
    val name: String,
    /** Jellyfin/Emby/Silo: server address. Plex: the chosen connection URI. */
    val baseUrl: String,
    val username: String?,
    val userId: String?,
    val accessToken: String,
    val refreshToken: String?,
    val tokenExpiresAtMs: Long,
    /**
     * S1: the user confirmed the public-plain-http warning for this
     * server once. LAN http stays silent, so this is only ever true
     * for a server the user knowingly added over public http.
     */
    val publicHttpAcknowledged: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("kind", kind.stored)
        put("name", name)
        put("baseUrl", baseUrl)
        put("username", username)
        put("userId", userId)
        put("accessToken", accessToken)
        put("refreshToken", refreshToken)
        put("tokenExpiresAtMs", tokenExpiresAtMs)
        put("publicHttpAcknowledged", publicHttpAcknowledged)
    }

    companion object {
        fun fromJson(obj: JSONObject): MediaServer? {
            val kind = ServerKind.fromStored(obj.optString("kind")) ?: return null
            val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return null
            val baseUrl = obj.optString("baseUrl").takeIf { it.isNotBlank() } ?: return null
            val token = obj.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
            return MediaServer(
                id = id,
                kind = kind,
                name = obj.optString("name").takeIf { it.isNotBlank() } ?: kind.label,
                baseUrl = baseUrl,
                username = obj.optString("username").takeIf { it.isNotBlank() },
                userId = obj.optString("userId").takeIf { it.isNotBlank() },
                accessToken = token,
                refreshToken = obj.optString("refreshToken").takeIf { it.isNotBlank() },
                tokenExpiresAtMs = obj.optLong("tokenExpiresAtMs", 0L),
                publicHttpAcknowledged = obj.optBoolean("publicHttpAcknowledged", false)
            )
        }
    }
}

data class ServerLibrary(
    val id: String,
    val name: String,
    val kind: ServerLibraryKind
)

/**
 * One browsable title on a server. contentId is the server's own id;
 * imdbId (when the server reports one) lets the player reuse
 * skip-segments and subtitle search.
 */
data class ServerItem(
    val contentId: String,
    val type: String, // "movie" | "series" | "episode"
    val title: String,
    val year: Int?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val overview: String?,
    val imdbId: String?,
    val season: Int?,
    val episode: Int?,
    val seriesId: String?,
    val seriesTitle: String?,
    val runtimeSecs: Long?,
    val played: Boolean,
    val positionSecs: Double?
)

/** Serialise / parse the stored server list (org.json, no plugin needed). */
internal fun encodeServers(servers: List<MediaServer>): String {
    val arr = JSONArray()
    servers.forEach { arr.put(it.toJson()) }
    return arr.toString()
}

internal fun decodeServers(raw: String?): List<MediaServer> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(MediaServer::fromJson) }
    }.getOrDefault(emptyList())
}

/**
 * S1: true when [rawUrl] is plain http to a host that is NOT the local
 * network — a token or password sent to it travels the public internet
 * unencrypted. Private LAN addresses (10/8, 172.16/12, 192.168/16,
 * loopback, localhost) and any https URL return false, so the user's
 * home-network media servers stay silent. A host that is not a
 * private IP — a public IP, a public hostname, or an unparseable host
 * on an http URL — is treated as public: warn first.
 */
fun isPublicHttpUrl(rawUrl: String): Boolean {
    val uri = runCatching { java.net.URI(rawUrl.trim()) }.getOrNull() ?: return false
    if (!uri.scheme.equals("http", ignoreCase = true)) return false
    val host = uri.host ?: return true
    return !isPrivateOrLocalHost(host)
}

fun isPrivateOrLocalHost(host: String): Boolean {
    val h = host.trim().trim('[', ']').lowercase()
    if (h == "localhost" || h == "127.0.0.1" || h == "::1") return true
    val parts = h.split(".")
    if (parts.size != 4) return false
    val octets = IntArray(4)
    for (i in 0..3) {
        val n = parts[i].toIntOrNull() ?: return false
        if (n !in 0..255) return false
        octets[i] = n
    }
    if (octets[0] == 10) return true
    if (octets[0] == 172 && octets[1] in 16..31) return true
    if (octets[0] == 192 && octets[1] == 168) return true
    if (octets[0] == 127) return true
    return false
}
