package com.aviostream.app.data.debrid

import com.aviostream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Debrid-Link API (https://debrid-link.com/api/v2).
 *
 * Debrid-only usage, cache-first with an honest degradation:
 *  1. POST /seedbox/cached used to answer "is this hash in the
 *     cache" without adding anything — but Debrid-Link REMOVED that
 *     endpoint (noted in StremThru's client, where it is marked
 *     deprecated for exactly that reason). So: when it answers, its
 *     verdict rules (not listed = not cached, and we add nothing).
 *     When it doesn't answer, the fallback below decides.
 *  2. POST /seedbox/add (field `rl`) adds the magnet; a cached
 *     torrent flips to `downloaded` almost immediately. Poll
 *     GET /seedbox/list inside the window; if it never completes,
 *     the torrent is removed again and the resolve reports
 *     NotCached — a fresh download is never left running.
 *  3. A downloaded torrent's files carry their own `downloadUrl`.
 *
 * Envelope: `{success: bool, value: …, error, error_description}`.
 * Auth: Bearer token; the auth error codes are badToken /
 * hidedToken / expired_token / unauthorized_client / invalid_client.
 */
class DebridLinkApi(private val apiKey: String) {

    companion object {
        private const val BASE = "https://debrid-link.com/api/v2"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** How many times to poll the seedbox list before giving up. */
        private const val MAX_POLLS = 12
        /** Delay between list polls. */
        private const val POLL_DELAY_MS = 2500L

        internal val AUTH_ERRORS = setOf(
            "badToken", "hidedToken", "expired_token",
            "unauthorized_client", "invalid_client", "access_denied"
        )

        internal fun isAuthError(code: String): Boolean = code in AUTH_ERRORS

        /** The envelope's `value`, or a thrown classified error. */
        internal fun requireValue(json: JSONObject): Any {
            if (json.optBoolean("success", false)) {
                return json.opt("value")
                    ?: throw DebridLinkException("Debrid-Link response: missing value")
            }
            val code = json.optString("error")
            throw DebridLinkException(
                json.optString("error_description").ifBlank { "Debrid-Link request failed ($code)" },
                isAuthError = isAuthError(code)
            )
        }

        /** Parse /seedbox/cached's value (a map keyed by the asked
         *  url/hash): cached iff an entry for us exists AND lists
         *  files. */
        internal fun parseCached(json: JSONObject, magnet: String, hash: String): Boolean {
            val value = requireValue(json) as? JSONObject
                ?: throw DebridLinkException("Debrid-Link cached: unexpected value shape")
            val entry = value.optJSONObject(magnet)
                ?: value.optJSONObject(hash.lowercase())
                ?: value.optJSONObject(hash.uppercase())
                ?: singleEntryOf(value)
                ?: return false
            val files = entry.optJSONArray("files")
            return files != null && files.length() > 0
        }

        private fun singleEntryOf(value: JSONObject): JSONObject? {
            if (value.length() != 1) return null
            val key = value.keys().next()
            return value.optJSONObject(key)
        }

        /** Parse one seedbox torrent object. */
        internal fun parseTorrent(json: JSONObject): SeedboxTorrent {
            val filesJson = json.optJSONArray("files") ?: JSONArray()
            val files = (0 until filesJson.length()).mapNotNull { i ->
                val f = filesJson.optJSONObject(i) ?: return@mapNotNull null
                DlFile(
                    id = f.optString("id"),
                    name = f.optString("name"),
                    size = f.optLong("size", 0L),
                    downloadUrl = f.optString("downloadUrl").takeIf { it.isNotBlank() },
                    sourceIndex = i
                )
            }
            return SeedboxTorrent(
                id = json.optString("id"),
                error = json.optInt("error", 0),
                errorString = json.optString("errorString"),
                downloaded = json.optBoolean("downloaded", false),
                files = files
            )
        }

        /** Parse /seedbox/add's response (value = the torrent). */
        internal fun parseAddedTorrent(json: JSONObject): SeedboxTorrent {
            val value = requireValue(json) as? JSONObject
                ?: throw DebridLinkException("Debrid-Link add: unexpected value shape")
            return parseTorrent(value)
        }

        /** Parse /seedbox/list's response (value = torrent array). */
        internal fun parseTorrentList(json: JSONObject): List<SeedboxTorrent> {
            val value = requireValue(json) as? JSONArray
                ?: throw DebridLinkException("Debrid-Link list: unexpected value shape")
            return (0 until value.length()).mapNotNull { i ->
                value.optJSONObject(i)?.let { parseTorrent(it) }
            }
        }
    }

    /** One file of a seedbox torrent; [downloadUrl] is present once
     *  the file is downloaded. */
    data class DlFile(
        val id: String,
        val name: String,
        val size: Long,
        val downloadUrl: String?,
        /** Zero-based position in the torrent's files array. */
        val sourceIndex: Int
    )

    data class SeedboxTorrent(
        val id: String,
        val error: Int,
        val errorString: String,
        val downloaded: Boolean,
        val files: List<DlFile>
    )

    private val userAgent = "Avio/${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun request(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            try {
                return JSONObject(bodyStr)
            } catch (e: Exception) {
                throw DebridLinkException(
                    "Debrid-Link request failed (HTTP ${response.code})",
                    isAuthError = response.code == 401 || response.code == 403
                )
            }
        }
    }

    private fun authed(builder: Request.Builder): Request.Builder =
        builder.header("Authorization", "Bearer $apiKey").header("User-Agent", userAgent)

    private fun postJson(path: String, body: JSONObject): JSONObject =
        request(
            authed(Request.Builder().url("$BASE$path"))
                .post(body.toString().toRequestBody(JSON))
                .build()
        )

    /** The cache verdict — see the class docs for its degraded mode. */
    suspend fun checkCached(magnet: String, hash: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("urls", JSONArray().put(magnet))
        parseCached(postJson("/seedbox/cached", body), magnet, hash)
    }

    suspend fun addTorrent(magnet: String): SeedboxTorrent = withContext(Dispatchers.IO) {
        val body = JSONObject().put("rl", magnet).put("async", true)
        parseAddedTorrent(postJson("/seedbox/add", body))
    }

    suspend fun listTorrent(torrentId: String): SeedboxTorrent? = withContext(Dispatchers.IO) {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("debrid-link.com")
            .addPathSegments("api/v2/seedbox/list")
            .addQueryParameter("ids[]", torrentId)
            .build()
        val json = request(authed(Request.Builder().url(url)).get().build())
        parseTorrentList(json).firstOrNull { it.id == torrentId }
    }

    /** Best-effort removal of a torrent we added. Never throws; the
     *  v2 delete route is exercised on a swallow-everything basis. */
    suspend fun removeTorrent(torrentId: String) {
        withContext(Dispatchers.IO) {
            runCatching {
                val url = "$BASE/seedbox/$torrentId"
                request(authed(Request.Builder().url(url)).delete().build())
            }
        }
    }

    /**
     * Full resolve with detailed result: infoHash → DebridResolveResult.
     * Cache-first per the class docs: a proven cache miss adds
     * nothing; an unproven one is decided by add+poll with cleanup.
     */
    suspend fun resolveCachedDetailed(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): DebridResolveResult = withContext(Dispatchers.IO) {
        try {
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            val provenNotCached = try {
                !checkCached(magnet, infoHash)
            } catch (e: DebridLinkException) {
                if (e.isAuthError) throw e
                // Endpoint gone or unreachable: proves nothing either
                // way — the add+poll below becomes the verdict.
                false
            }
            if (provenNotCached) return@withContext DebridResolveResult.NotCached

            var torrent = addTorrent(magnet)
            if (torrent.error != 0) {
                return@withContext DebridResolveResult.Error(
                    torrent.errorString.ifBlank { "Debrid-Link torrent error ${torrent.error}" }
                )
            }
            var polls = 0
            while (!torrent.downloaded && polls < MAX_POLLS) {
                delay(POLL_DELAY_MS)
                polls++
                torrent = listTorrent(torrent.id) ?: break
                if (torrent.error != 0) {
                    removeTorrent(torrent.id)
                    return@withContext DebridResolveResult.Error(
                        torrent.errorString.ifBlank { "Debrid-Link torrent error ${torrent.error}" }
                    )
                }
            }
            if (!torrent.downloaded) {
                removeTorrent(torrent.id)
                return@withContext DebridResolveResult.NotCached
            }
            val entry = DebridFileSelection.select(
                torrent.files.map {
                    DebridFileSelection.FileEntry(name = it.name, size = it.size, sourceIndex = it.sourceIndex)
                },
                fileIdx, filePath
            ) ?: return@withContext DebridResolveResult.Error(noFileMessage(fileIdx, filePath))
            val chosen = torrent.files.first { it.sourceIndex == entry.sourceIndex }
            val url = chosen.downloadUrl
                ?: return@withContext DebridResolveResult.Error("Debrid-Link file has no download URL yet")
            DebridResolveResult.Resolved(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DebridLinkException) {
            DebridResolveResult.Error(e.message ?: "Unknown Debrid-Link error", e.isAuthError)
        } catch (e: Exception) {
            DebridResolveResult.Error("Network error: ${e.message}")
        }
    }

    private fun noFileMessage(fileIdx: Int?, filePath: String?): String =
        if (fileIdx != null || !filePath.isNullOrBlank()) "Requested torrent file was not found"
        else "No playable files found in torrent"
}

/** Debrid-Link API failure; [isAuthError] marks token refusals. */
class DebridLinkException(message: String, val isAuthError: Boolean = false) : Exception(message)
