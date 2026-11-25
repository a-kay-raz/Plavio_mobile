package com.aviostream.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Last played stream link, cached per title (Nuvio
 * `streams/StreamLinkCacheRepository`, simplified for Avio: plain
 * org.json, no serialization compiler plugin).
 *
 * Replaying a title within the TTL reuses the last played link instead of
 * re-resolving every addon (and debrid) — the #15 Continue Watching resume
 * path in PlayerScreen. Signed/expiring URLs are handled two ways: the
 * entry dies after [maxAgeMs], and a dead HTTP status on a reused link
 * removes the entry at once (see PlayerScreen's error listener).
 */
data class CachedStreamLink(
    val url: String,
    val streamName: String,
    val addonName: String,
    val addonId: String,
    val cachedAtMs: Long,
    val requestHeaders: Map<String, String> = emptyMap(),
    val filename: String? = null,
    val videoHash: String? = null,
    val videoSize: Long? = null,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val mimeType: String? = null,
)

/** Content key (Nuvio `contentKey`): episode-scoped for series, plain for movies. */
fun streamLinkContentKey(
    type: String,
    videoId: String,
    parentMetaId: String? = null,
    season: Int? = null,
    episode: Int? = null,
): String {
    val normalizedType = type.lowercase()
    return if (!parentMetaId.isNullOrBlank() && season != null && episode != null) {
        "$normalizedType|${parentMetaId.trim()}|s$season|e$episode|$videoId"
    } else {
        "$normalizedType|$videoId"
    }
}

/** Persistent per-title link cache, one entry per hashed content key. */
class StreamLinkCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("avio_stream_link_cache", Context.MODE_PRIVATE)

    /**
     * Stores [link] under [contentKey]. Only real direct links are cached —
     * unresolved torrents (`infohash:`/`magnet:`) and local files never are.
     *
     * S7: a link that only plays with secret material is never written to
     * disk — this cache is plain SharedPreferences, so a token-bearing URL
     * or an Authorization/Cookie header stored here would be a replayable
     * credential for any local reader of the app's private files. Such
     * titles simply re-resolve on next play (the resume path already
     * re-resolves when there is no cached link); token-free entries cache
     * exactly as before, and TTL/eviction behaviour is unchanged.
     */
    fun save(contentKey: String, link: CachedStreamLink) {
        val url = link.url
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) return
        if (urlHasTokenParam(url)) return
        if (link.requestHeaders.keys.any { isSensitiveHeader(it) }) return
        val payload = JSONObject().apply {
            put("url", link.url)
            put("streamName", link.streamName)
            put("addonName", link.addonName)
            put("addonId", link.addonId)
            put("cachedAtMs", link.cachedAtMs)
            put("headers", JSONObject(link.requestHeaders))
            link.filename?.let { put("filename", it) }
            link.videoHash?.let { put("videoHash", it) }
            link.videoSize?.let { put("videoSize", it) }
            link.infoHash?.let { put("infoHash", it) }
            link.fileIdx?.let { put("fileIdx", it) }
            link.mimeType?.let { put("mimeType", it) }
        }.toString()
        prefs.edit().putString(hashedKey(contentKey), payload).apply()
    }

    /** Returns the entry when it exists, is fresh and has a usable link; otherwise null. */
    fun getValid(contentKey: String, maxAgeMs: Long): CachedStreamLink? {
        if (maxAgeMs <= 0L) return null
        val hashed = hashedKey(contentKey)
        val raw = prefs.getString(hashed, null) ?: return null
        val entry = runCatching { decode(raw) }.getOrNull() ?: run {
            prefs.edit().remove(hashed).apply()
            return null
        }
        val age = System.currentTimeMillis() - entry.cachedAtMs
        if (entry.cachedAtMs <= 0L || age > maxAgeMs) {
            prefs.edit().remove(hashed).apply()
            return null
        }
        if (entry.url.isBlank() && entry.infoHash.isNullOrBlank()) {
            prefs.edit().remove(hashed).apply()
            return null
        }
        // S7: purge entries cached before the save() guard (or written by
        // an older build) that carry secret material — they are never
        // handed back for playback.
        if (urlHasTokenParam(entry.url) ||
            entry.requestHeaders.keys.any { isSensitiveHeader(it) }
        ) {
            prefs.edit().remove(hashed).apply()
            return null
        }
        return entry
    }

    fun remove(contentKey: String) {
        prefs.edit().remove(hashedKey(contentKey)).apply()
    }

    private fun decode(raw: String): CachedStreamLink {
        val json = JSONObject(raw)
        val headersJson = json.optJSONObject("headers")
        val headers = mutableMapOf<String, String>()
        headersJson?.keys()?.forEach { key ->
            headers[key] = headersJson.optString(key)
        }
        return CachedStreamLink(
            url = json.getString("url"),
            streamName = json.optString("streamName"),
            addonName = json.optString("addonName"),
            addonId = json.optString("addonId"),
            cachedAtMs = json.optLong("cachedAtMs"),
            requestHeaders = headers,
            filename = json.optString("filename").takeIf { it.isNotEmpty() },
            videoHash = json.optString("videoHash").takeIf { it.isNotEmpty() },
            videoSize = json.optLong("videoSize").takeIf { it > 0L },
            infoHash = json.optString("infoHash").takeIf { it.isNotEmpty() },
            fileIdx = json.optInt("fileIdx").takeIf { json.has("fileIdx") },
            mimeType = json.optString("mimeType").takeIf { it.isNotEmpty() },
        )
    }

    /**
     * S7: true when a header carries a credential rather than a transport
     * hint — Authorization/Cookie and anything token/secret/key/auth-named.
     * Mirrors PlaybackHeaders.isSensitiveHeader (same set, plus the
     * underscore spelling); Referer/Origin are not credentials and are
     * left alone.
     */
    private fun isSensitiveHeader(name: String): Boolean {
        val normalized = name.lowercase().replace("_", "-")
        return normalized in setOf(
            "authorization", "proxy-authorization", "cookie", "set-cookie"
        ) || listOf("token", "secret", "api-key", "apikey", "auth")
            .any { it in normalized }
    }

    /**
     * S7: true when the URL's query carries a token the server needs for
     * playback — Plex's X-Plex-Token, Jellyfin/Emby's api_key, and any
     * token=/key=/access_token=/auth= param (including *_token suffixes).
     * The token is required to play, so the entry cannot be stored with
     * the token stripped — it is not cached at all instead.
     */
    private fun urlHasTokenParam(url: String): Boolean {
        val query = url.substringAfter("?", "").substringBefore("#")
        if (query.isEmpty()) return false
        return query.split("&").any { part ->
            val name = part.substringBefore("=").lowercase()
            name in setOf("x-plex-token", "api_key", "token", "key", "access_token", "auth") ||
                name.endsWith("_token") || name.endsWith("-token") ||
                "apikey" in name
        }
    }

    /** Nuvio's fold hash: the raw key never hits disk, only `stream_link_<hash>`. */
    private fun hashedKey(contentKey: String): String {
        val hash = contentKey.fold(0L) { acc, c -> acc * 31 + c.code }.toULong()
        return "stream_link_$hash"
    }
}
