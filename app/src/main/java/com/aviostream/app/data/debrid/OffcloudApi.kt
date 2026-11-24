package com.aviostream.app.data.debrid

import com.aviostream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the Offcloud API (https://offcloud.com/api).
 *
 * Debrid-only usage, strictly cache-first (TorBox's rule):
 *  1. POST /cache answers whether the magnet is in Offcloud's cache.
 *     A miss is final — nothing is ever added for an uncached
 *     torrent.
 *  2. On a hit, POST /cloud creates the download; cached content
 *     reaches status "downloaded" effectively immediately. Poll
 *     POST /cloud/status inside the window; anything else (still
 *     downloading when the window closes, error, canceled) reports
 *     NotCached/Error — a slow fresh download is never awaited.
 *  3. POST /cloud/explore lists the download's files with their
 *     download URLs.
 *
 * Auth: the account API key as the `apikey` query parameter on every
 * call (Offcloud's documented scheme). Failures arrive as a flat
 * `{"error": "…"}` object — "NOAUTH" is the bad-key verdict.
 * Offcloud documents no endpoint to delete a cloud download, so a
 * timed-out add cannot be cleaned up through the API (see the
 * provider matrix in the report); the resolve still reports the
 * honest NotCached.
 */
class OffcloudApi(private val apiKey: String) {

    companion object {
        private const val BASE = "https://offcloud.com/api"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /** How many times to poll cloud status before giving up. */
        private const val MAX_POLLS = 10
        /** Delay between status polls. */
        private const val POLL_DELAY_MS = 3000L

        internal const val STATUS_DOWNLOADED = "downloaded"

        internal fun isAuthError(error: String): Boolean =
            error.equals("NOAUTH", ignoreCase = true)

        /** Throw when a response object carries an `error` field. */
        private fun requireNoError(json: JSONObject) {
            val error = json.optString("error")
            if (error.isNotBlank()) {
                throw OffcloudException(
                    "Offcloud error: $error",
                    isAuthError = isAuthError(error)
                )
            }
        }

        /** Parse the /cache response body. The endpoint's payload is
         *  seen in the wild both as a top-level array of per-item
         *  objects and as an object keyed by the requested URL — both
         *  shapes are read, anything else fails loudly. */
        internal fun parseCacheBody(body: String, magnet: String): Boolean {
            val trimmed = body.trim()
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                if (array.length() == 0) return false
                val first = array.optJSONObject(0)
                    ?: throw OffcloudException("Offcloud cache: unexpected item shape")
                requireNoError(first)
                return first.optBoolean("cached", false)
            }
            val json = JSONObject(trimmed)
            requireNoError(json)
            if (json.has("cached")) return json.optBoolean("cached", false)
            val entry = json.optJSONObject(magnet)
                ?: throw OffcloudException("Offcloud cache: unexpected response shape")
            requireNoError(entry)
            return entry.optBoolean("cached", false)
        }

        /** Parse a /cloud (add) response: the new requestId. */
        internal fun parseCloudRequestId(json: JSONObject): String {
            requireNoError(json)
            return json.optString("requestId").takeIf { it.isNotBlank() }
                ?: throw OffcloudException("Offcloud cloud: missing requestId")
        }

        /** Parse a /cloud/status response: the status string. */
        internal fun parseCloudStatus(json: JSONObject): String {
            requireNoError(json)
            return json.optString("status")
        }

        /** Parse a /cloud/explore response into its file list. */
        internal fun parseExploreFiles(json: JSONObject): List<OcFile> {
            requireNoError(json)
            val files = json.optJSONArray("files")
                ?: throw OffcloudException("Offcloud explore: missing files array")
            return (0 until files.length()).mapNotNull { i ->
                val f = files.optJSONObject(i) ?: return@mapNotNull null
                OcFile(
                    name = f.optString("path").ifBlank { f.optString("name") },
                    size = f.optLong("size", 0L),
                    url = f.optString("url").takeIf { it.isNotBlank() },
                    sourceIndex = i
                )
            }
        }
    }

    /** One file of a cloud download; [url] is its download URL. */
    data class OcFile(
        val name: String,
        val size: Long,
        val url: String?,
        /** Zero-based position in the explore response's files array. */
        val sourceIndex: Int
    )

    private val userAgent = "Avio/${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun post(path: String, body: RequestBody): String {
        val url = okhttp3.HttpUrl.Builder()
            .scheme("https")
            .host("offcloud.com")
            .addPathSegments("api$path")
            .addQueryParameter("apikey", apiKey)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            if (!response.isSuccessful && bodyStr.isBlank()) {
                throw OffcloudException(
                    "Offcloud request failed (HTTP ${response.code})",
                    isAuthError = response.code == 401 || response.code == 403
                )
            }
            return bodyStr
        }
    }

    private fun postForm(path: String, form: FormBody): JSONObject {
        val bodyStr = post(path, form)
        return try {
            JSONObject(bodyStr)
        } catch (e: Exception) {
            throw OffcloudException("Offcloud returned an unreadable response")
        }
    }

    /** Is this magnet already in Offcloud's cache? */
    suspend fun checkCached(magnet: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject().put("urls", JSONArray().put(magnet)).toString()
        parseCacheBody(post("/cache", body.toRequestBody(JSON)), magnet)
    }

    /** Create a cloud download for a (cached) magnet; returns its requestId. */
    suspend fun addCloudDownload(magnet: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("url", magnet).build()
        parseCloudRequestId(postForm("/cloud", form))
    }

    suspend fun cloudStatus(requestId: String): String = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("requestId", requestId).build()
        parseCloudStatus(postForm("/cloud/status", form))
    }

    suspend fun exploreFiles(requestId: String): List<OcFile> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("requestId", requestId).build()
        parseExploreFiles(postForm("/cloud/explore", form))
    }

    /**
     * Full resolve with detailed result: infoHash → DebridResolveResult.
     * Cache-only per the class docs.
     */
    suspend fun resolveCachedDetailed(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): DebridResolveResult = withContext(Dispatchers.IO) {
        try {
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            if (!checkCached(magnet)) return@withContext DebridResolveResult.NotCached
            val requestId = addCloudDownload(magnet)
            var status = cloudStatus(requestId)
            var polls = 0
            while (status != STATUS_DOWNLOADED && polls < MAX_POLLS) {
                if (status == "error" || status == "canceled") {
                    return@withContext DebridResolveResult.Error("Offcloud download $status")
                }
                delay(POLL_DELAY_MS)
                polls++
                status = cloudStatus(requestId)
            }
            if (status != STATUS_DOWNLOADED) return@withContext DebridResolveResult.NotCached
            val files = exploreFiles(requestId)
            val entry = DebridFileSelection.select(
                files.map {
                    DebridFileSelection.FileEntry(name = it.name, size = it.size, sourceIndex = it.sourceIndex)
                },
                fileIdx, filePath
            ) ?: return@withContext DebridResolveResult.Error(noFileMessage(fileIdx, filePath))
            val chosen = files.first { it.sourceIndex == entry.sourceIndex }
            val url = chosen.url
                ?: return@withContext DebridResolveResult.Error("Offcloud file has no download URL")
            DebridResolveResult.Resolved(url)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OffcloudException) {
            DebridResolveResult.Error(e.message ?: "Unknown Offcloud error", e.isAuthError)
        } catch (e: Exception) {
            DebridResolveResult.Error("Network error: ${e.message}")
        }
    }

    private fun noFileMessage(fileIdx: Int?, filePath: String?): String =
        if (fileIdx != null || !filePath.isNullOrBlank()) "Requested torrent file was not found"
        else "No playable files found in torrent"
}

/** Offcloud API failure; [isAuthError] marks the NOAUTH verdict. */
class OffcloudException(message: String, val isAuthError: Boolean = false) : Exception(message)
