package com.aviostream.app.data.debrid

import com.aviostream.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Raw HTTP client for the AllDebrid API (https://api.alldebrid.com/v4).
 *
 * Debrid-only usage. AllDebrid documents NO cache-check endpoint, so
 * the cache verdict rides on the upload itself (official docs,
 * docs.alldebrid.com):
 *  1. POST /magnet/upload — the response entry carries `ready`:
 *     "whether the magnet is already available". That IS the cache
 *     check; a magnet that isn't ready starts downloading on the
 *     account, which the cache-first rule only tolerates inside the
 *     resolve window:
 *  2. Poll POST /v4.1/magnet/status until statusCode 4 (Ready).
 *  3. If it never becomes ready inside the window — it was not
 *     cached in any useful sense — POST /magnet/delete removes it
 *     again (the account is not littered) and the resolve reports
 *     NotCached.
 *  4. Ready → POST /magnet/files gives the file tree ({n, s, l}
 *     leaves, {n, e[]} folders); the chosen file's `l` is the
 *     download link.
 *
 * Envelope: `{status: "success", data: …}` or `{status: "error",
 * error: {code, message}}`; AUTH_* codes are key problems.
 */
class AllDebridApi(private val apiKey: String) {

    companion object {
        private const val BASE_V4 = "https://api.alldebrid.com/v4"
        private const val BASE_V41 = "https://api.alldebrid.com/v4.1"

        /** How many times to poll magnet status before giving up. */
        private const val MAX_POLLS = 10
        /** Delay between status polls. */
        private const val POLL_DELAY_MS = 3000L

        /** statusCode meaning "Ready" in /magnet/status. */
        internal const val STATUS_READY = 4
        /** statusCodes >= this are terminal errors. */
        internal const val STATUS_ERROR_MIN = 5

        internal fun isAuthErrorCode(code: String): Boolean = code.startsWith("AUTH_")

        /** Parse the /magnet/upload response's first entry. Entry-level
         *  errors (invalid magnet, must-be-premium…) throw. */
        internal fun parseUploadEntry(json: JSONObject): UploadEntry {
            val data = requireSuccessData(json)
            val magnets = data.optJSONArray("magnets")
                ?: throw AllDebridException("AllDebrid upload: missing magnets array")
            val entry = magnets.optJSONObject(0)
                ?: throw AllDebridException("AllDebrid upload: empty magnets array")
            entry.optJSONObject("error")?.let { error ->
                val code = error.optString("code")
                throw AllDebridException(
                    error.optString("message").ifBlank { "AllDebrid upload failed ($code)" },
                    isAuthError = isAuthErrorCode(code)
                )
            }
            val id = entry.optLong("id", -1L)
            if (id < 0) throw AllDebridException("AllDebrid upload: missing magnet id")
            return UploadEntry(id = id, ready = entry.optBoolean("ready", false))
        }

        /** Parse the /magnet/status response for one magnet id. */
        internal fun parseStatusCode(json: JSONObject, magnetId: Long): Int {
            val data = requireSuccessData(json)
            val magnets = data.optJSONArray("magnets")
                ?: throw AllDebridException("AllDebrid status: missing magnets array")
            for (i in 0 until magnets.length()) {
                val entry = magnets.optJSONObject(i) ?: continue
                if (entry.optLong("id", -2L) == magnetId) return entry.optInt("statusCode", -1)
            }
            throw AllDebridException("AllDebrid status: magnet $magnetId not reported")
        }

        /** Parse the /magnet/files response for one magnet id into a
         *  flat file list (tree order preserved as sourceIndex). */
        internal fun parseFiles(json: JSONObject, magnetId: Long): List<AdFile> {
            val data = requireSuccessData(json)
            val magnets = data.optJSONArray("magnets")
                ?: throw AllDebridException("AllDebrid files: missing magnets array")
            for (i in 0 until magnets.length()) {
                val entry = magnets.optJSONObject(i) ?: continue
                // The files endpoint reports ids as strings.
                if (entry.optString("id") != magnetId.toString()) continue
                entry.optJSONObject("error")?.let { error ->
                    throw AllDebridException(
                        error.optString("message").ifBlank { "AllDebrid files failed" },
                        isAuthError = isAuthErrorCode(error.optString("code"))
                    )
                }
                val files = entry.optJSONArray("files")
                    ?: throw AllDebridException("AllDebrid files: missing files array")
                val out = mutableListOf<AdFile>()
                flatten(files, prefix = "", out)
                return out
            }
            throw AllDebridException("AllDebrid files: magnet $magnetId not reported")
        }

        /** Depth-first flatten of the {n, e[]} / {n, s, l} tree. */
        private fun flatten(nodes: JSONArray, prefix: String, out: MutableList<AdFile>) {
            for (i in 0 until nodes.length()) {
                val node = nodes.optJSONObject(i) ?: continue
                val name = node.optString("n")
                val children = node.optJSONArray("e")
                if (children != null) {
                    flatten(children, prefix + name + "/", out)
                } else {
                    val link = node.optString("l").takeIf { it.isNotBlank() } ?: continue
                    out += AdFile(
                        name = prefix + name,
                        size = node.optLong("s", 0L),
                        link = link,
                        sourceIndex = out.size
                    )
                }
            }
        }

        private fun requireSuccessData(json: JSONObject): JSONObject {
            if (json.optString("status") == "success") {
                return json.optJSONObject("data")
                    ?: throw AllDebridException("AllDebrid response: missing data")
            }
            val error = json.optJSONObject("error")
            val code = error?.optString("code").orEmpty()
            throw AllDebridException(
                error?.optString("message")?.ifBlank { null } ?: "AllDebrid request failed",
                isAuthError = isAuthErrorCode(code)
            )
        }
    }

    /** The upload verdict: the magnet's id, and whether it was
     *  already available (AllDebrid's cache signal). */
    data class UploadEntry(val id: Long, val ready: Boolean)

    /** One file of a magnet: [name] is its full path in the tree. */
    data class AdFile(
        val name: String,
        val size: Long,
        val link: String,
        /** Zero-based position in the flattened tree order. */
        val sourceIndex: Int
    )

    private val userAgent = "Avio/${BuildConfig.VERSION_NAME}"

    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun postForm(base: String, path: String, form: FormBody): JSONObject {
        val request = Request.Builder()
            .url("$base$path")
            .header("Authorization", "Bearer $apiKey")
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        client.newCall(request).execute().use { response ->
            val bodyStr = response.body?.string().orEmpty()
            // AllDebrid answers errors in its envelope even on 4xx;
            // prefer the envelope whenever the body parses.
            val parsed = try {
                JSONObject(bodyStr)
            } catch (e: Exception) {
                null
            }
            if (parsed != null && parsed.has("status")) return parsed
            throw AllDebridException(
                "AllDebrid request failed (HTTP ${response.code})",
                isAuthError = response.code == 401 || response.code == 403
            )
        }
    }

    suspend fun uploadMagnet(magnet: String): UploadEntry = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("magnets[]", magnet).build()
        parseUploadEntry(postForm(BASE_V4, "/magnet/upload", form))
    }

    suspend fun magnetStatusCode(magnetId: Long): Int = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("id", magnetId.toString()).build()
        parseStatusCode(postForm(BASE_V41, "/magnet/status", form), magnetId)
    }

    suspend fun magnetFiles(magnetId: Long): List<AdFile> = withContext(Dispatchers.IO) {
        val form = FormBody.Builder().add("id[]", magnetId.toString()).build()
        parseFiles(postForm(BASE_V4, "/magnet/files", form), magnetId)
    }

    /** Best-effort removal of a magnet we added (cleanup after a
     *  not-ready timeout or a terminal error). Never throws. */
    suspend fun deleteMagnet(magnetId: Long) {
        withContext(Dispatchers.IO) {
            runCatching {
                val form = FormBody.Builder().add("id", magnetId.toString()).build()
                postForm(BASE_V4, "/magnet/delete", form)
            }
        }
    }

    /**
     * Full resolve with detailed result: infoHash → DebridResolveResult.
     * See the class docs for the cache-first flow and the cleanup rule.
     */
    suspend fun resolveCachedDetailed(
        infoHash: String,
        fileIdx: Int? = null,
        filePath: String? = null
    ): DebridResolveResult = withContext(Dispatchers.IO) {
        try {
            val magnet = "magnet:?xt=urn:btih:$infoHash"
            val upload = uploadMagnet(magnet)
            if (!upload.ready) {
                var ready = false
                var failure: String? = null
                var polls = 0
                while (polls < MAX_POLLS) {
                    delay(POLL_DELAY_MS)
                    polls++
                    val code = magnetStatusCode(upload.id)
                    when {
                        code == STATUS_READY -> {
                            ready = true
                            break
                        }
                        code >= STATUS_ERROR_MIN -> {
                            failure = "AllDebrid magnet failed (statusCode $code)"
                            break
                        }
                    }
                }
                if (!ready) {
                    // Not cached within the window: remove what we
                    // added so the account isn't littered, and report
                    // the honest cache-first verdict.
                    deleteMagnet(upload.id)
                    return@withContext if (failure != null) {
                        DebridResolveResult.Error(failure)
                    } else {
                        DebridResolveResult.NotCached
                    }
                }
            }
            val files = magnetFiles(upload.id)
            val entry = DebridFileSelection.select(
                files.map {
                    DebridFileSelection.FileEntry(name = it.name, size = it.size, sourceIndex = it.sourceIndex)
                },
                fileIdx, filePath
            ) ?: return@withContext DebridResolveResult.Error(noFileMessage(fileIdx, filePath))
            val chosen = files.first { it.sourceIndex == entry.sourceIndex }
            DebridResolveResult.Resolved(chosen.link)
        } catch (e: CancellationException) {
            throw e
        } catch (e: AllDebridException) {
            DebridResolveResult.Error(e.message ?: "Unknown AllDebrid error", e.isAuthError)
        } catch (e: Exception) {
            DebridResolveResult.Error("Network error: ${e.message}")
        }
    }

    private fun noFileMessage(fileIdx: Int?, filePath: String?): String =
        if (fileIdx != null || !filePath.isNullOrBlank()) "Requested torrent file was not found"
        else "No playable files found in torrent"
}

/** AllDebrid API failure; [isAuthError] marks AUTH_* refusals. */
class AllDebridException(message: String, val isAuthError: Boolean = false) : Exception(message)
