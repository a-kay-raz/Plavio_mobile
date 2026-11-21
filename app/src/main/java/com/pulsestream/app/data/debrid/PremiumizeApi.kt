package com.pulsestream.app.data.debrid

import android.util.Log
import com.pulsestream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Step 43 — Raw HTTP client for the Premiumize API
 * (https://www.premiumize.me/api), API-key auth like TorBox/Real-Debrid
 * (Nuvio uses a device-code OAuth flow for Premiumize; Plavio keeps the
 * paste-a-key pattern its other two providers use).
 *
 * Debrid-only usage: resolve a cached torrent's infoHash to a direct HTTP
 * download link. No torrent downloading happens in the app — Premiumize's
 * servers do the fetching, we just get the HTTP URL.
 *
 * Flow:
 *  1. checkCached(hash) → POST /api/cache/check (items[] = magnet link)
 *  2. directDownload(magnet) → POST /api/transfer/directdl (src = magnet)
 *     returns the cached file's direct link immediately, no transfer wait.
 *  3. PremiumizeFileSelection picks the addon's requested file (fileIdx /
 *     filePath) or, with no hint, the single video file — fail closed on a
 *     multi-video pack, same rule as TorBoxFileSelection.
 */
class PremiumizeApi(private val apiKey: String) {

    companion object {
        private const val TAG = "PremiumizeApi"
        private const val BASE = "https://www.premiumize.me"

        /** GET /api/account/info?apikey=KEY — true when the key is valid. */
        suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext false
            try {
                val client = OkHttpClient.Builder()
                    .callTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                val url = "$BASE/api/account/info?apikey=${apiKey.trim()}"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Plavio/${BuildConfig.VERSION_NAME}")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext false
                    val body = response.body?.string().orEmpty()
                    JSONObject(body).optString("status") == "success"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        }
    }

    private val userAgent = "Plavio/${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun postForm(path: String, vararg fields: Pair<String, String>): JSONObject {
        val body = FormBody.Builder().apply {
            add("apikey", apiKey)
            fields.forEach { (name, value) -> add(name, value) }
        }.build()
        val request = Request.Builder()
            .url("$BASE$path")
            .header("User-Agent", userAgent)
            .post(body)
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            if (!response.isSuccessful) {
                val code = response.code
                throw PremiumizeException(
                    "Premiumize $path failed (HTTP $code): ${json.optString("message", "unknown error")}",
                    isAuthError = code == 401 || code == 403,
                )
            }
            json
        }
    }

    /** Is this hash already cached on Premiumize's servers? */
    suspend fun checkCached(infoHash: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val magnet = "magnet:?xt=urn:btih:${infoHash.trim().lowercase()}"
            val json = postForm("/api/cache/check", "items[]" to magnet)
            if (json.optString("status") != "success") return@withContext false
            json.optJSONArray("response")?.optBoolean(0, false) == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Full resolve with detailed result: infoHash → DebridResolveResult.
     *
     * Only attempts torrents that are already cached (fast path). Picks the
     * addon's requested file, or the single video file when the torrent has
     * exactly one. A cached multi-file pack with no file hint fails closed
     * (no playable file picked) rather than playing the wrong episode.
     */
    suspend fun resolveCachedDetailed(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): DebridResolveResult = withContext(Dispatchers.IO) {
        try {
            if (!checkCached(infoHash)) {
                Log.d(TAG, "resolveCached: $infoHash not cached")
                return@withContext DebridResolveResult.NotCached
            }
            val magnet = "magnet:?xt=urn:btih:${infoHash.trim().lowercase()}"
            val json = postForm("/api/transfer/directdl", "src" to magnet)
            if (json.optString("status").equals("error", ignoreCase = true)) {
                val message = listOfNotNull(
                    json.optString("message", "").takeIf { it.isNotBlank() },
                    json.optString("code", "").takeIf { it.isNotBlank() },
                ).joinToString(" ").lowercase()
                if (message.contains("cache") || message.contains("not found")) {
                    return@withContext DebridResolveResult.NotCached
                }
                val isAuth = message.contains("invalid") || message.contains("apikey") ||
                    message.contains("api key") || message.contains("unauthor") ||
                    message.contains("forbidden")
                return@withContext DebridResolveResult.Error(
                    json.optString("message", "Premiumize error").takeIf { it.isNotBlank() }
                        ?: "Premiumize error",
                    isAuth,
                )
            }
            val files = json.optJSONArray("content")?.let { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val file = arr.optJSONObject(i) ?: return@mapNotNull null
                    val path = file.optString("path", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val link = file.optString("link", "").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PremiumizeFile(path = path, size = file.optLong("size", 0L), link = link, sourceIndex = i)
                }
            } ?: emptyList()
            val selected = PremiumizeFileSelection.select(files, fileIdx, filePath)
                ?: return@withContext DebridResolveResult.Error(
                    if (fileIdx != null || !filePath.isNullOrBlank()) "Requested torrent file was not found"
                    else "No playable files found in torrent"
                )
            Log.d(TAG, "resolveCached: resolved $infoHash -> [URL received, ${selected.link.length} chars]")
            DebridResolveResult.Resolved(selected.link)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PremiumizeException) {
            val msg = e.message ?: "Unknown Premiumize error"
            Log.e(TAG, "resolveCached failed for $infoHash: $msg")
            DebridResolveResult.Error(msg, e.isAuthError)
        } catch (e: Exception) {
            Log.e(TAG, "resolveCached failed for $infoHash", e)
            DebridResolveResult.Error("Network error: ${e.message}")
        }
    }

    /** Full resolve: infoHash → direct HTTP URL, null when not cached or on failure. */
    suspend fun resolveCachedToHttp(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): String? = withContext(Dispatchers.IO) {
        when (val result = resolveCachedDetailed(infoHash, fileIdx, filePath)) {
            is DebridResolveResult.Resolved -> result.url
            else -> null
        }
    }
}

/** Thrown for Premiumize API failures (so callers can distinguish from IO errors). */
class PremiumizeException(message: String, val isAuthError: Boolean = false) : Exception(message)

data class PremiumizeFile(
    val path: String,
    val size: Long,
    val link: String,
    val sourceIndex: Int,
)

/**
 * Conservative selection of a Premiumize direct-download file for a Stremio
 * stream — same rules as TorBoxFileSelection: `fileIdx` is the zero-based
 * position in the provider's original files array (Premiumize's content
 * list preserves the torrent's file order); with no hint only a single
 * video file is safe, a multi-video pack fails closed.
 */
internal object PremiumizeFileSelection {
    private val videoExtensions = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v", "ts")

    fun select(
        files: List<PremiumizeFile>,
        fileIdx: Int?,
        filePath: String?
    ): PremiumizeFile? {
        val hasRequestedPath = !filePath.isNullOrBlank()
        if (fileIdx == null && !hasRequestedPath) {
            // No episode/file hint is safe only if there is exactly one video.
            // Choosing the largest video in a season pack can play the wrong episode.
            return files.filter { isVideo(it.path) }.singleOrNull()
        }

        val byIndex = fileIdx?.let { requested ->
            files.singleOrNull { it.sourceIndex == requested }
        }
        val normalizedPath = filePath?.takeIf { it.isNotBlank() }?.let(::normalizePath)
        if (fileIdx != null) {
            val indexed = byIndex ?: return null
            return indexed.takeIf { normalizedPath == null || normalizePath(it.path) == normalizedPath }
        }
        val byPath = normalizedPath?.let { requested ->
            val matches = files.filter { normalizePath(it.path) == requested }
            matches.singleOrNull()
        }

        if (hasRequestedPath && byPath == null) return null
        return byPath
    }

    private fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in videoExtensions

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trim('/').lowercase()
}
