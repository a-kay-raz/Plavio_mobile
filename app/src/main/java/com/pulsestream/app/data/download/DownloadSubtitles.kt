package com.pulsestream.app.data.download

import android.content.Context
import android.util.Log
import com.pulsestream.app.data.Subtitle
import com.pulsestream.app.data.db.DownloadItem
import java.io.File
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Step 46: bundled subtitles (Nuvio downloads/DownloadSubtitles.kt, scoped
 * port). After a download completes, the subtitle URLs captured at enqueue
 * time are fetched into sidecar files under
 * filesDir/download-subs/<itemId>/ — "0.srt", "1.vtt", … plus a
 * manifest.json listing them — and offered in offline playback via
 * [localSubtitles].
 *
 * Sidecars live under filesDir keyed by DownloadItem id (NOT next to the
 * video file): Step 37's move into a custom SAF folder deletes the private
 * copy and its sidecars, so sibling sidecars would not survive the move.
 * Keying by id works for both private-dir and SAF-stored videos.
 */
object DownloadSubtitles {
    private const val TAG = "DownloadSubtitles"
    private const val MAX_TRACKS = 8
    private const val FETCH_TIMEOUT_MS = 15_000L
    private val SUPPORTED_EXTENSIONS = setOf("srt", "vtt", "ass", "sub", "smi")

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun manifestDir(context: Context, itemId: String): File =
        File(context.filesDir, "download-subs/$itemId")

    /** org.json array of {"url","lang"} — stored on DownloadItem.subtitlesJson. */
    fun encodeSubtitles(subtitles: List<Subtitle>): String {
        val arr = JSONArray()
        subtitles.distinctBy { it.url }.take(MAX_TRACKS).forEach { sub ->
            arr.put(JSONObject().put("url", sub.url).put("lang", sub.lang))
        }
        return arr.toString()
    }

    fun decodeSubtitles(json: String): List<Subtitle> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val url = obj.optString("url").takeIf { it.isNotBlank() } ?: continue
                    add(Subtitle(url = url, lang = obj.optString("lang").takeIf { it.isNotBlank() } ?: "und"))
                }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Fetch the item's captured subtitles into sidecars. Best-effort: any
     * failure is logged and never fails the download. Already-saved tracks
     * (by source URL) are skipped, so a re-run resumes instead of refetching.
     */
    suspend fun fetchSidecars(context: Context, item: DownloadItem) {
        val pending = decodeSubtitles(item.subtitlesJson)
            .filter { it.url.startsWith("http://", ignoreCase = true) || it.url.startsWith("https://", ignoreCase = true) }
            .distinctBy { it.url }
        if (pending.isEmpty()) return
        val dir = manifestDir(context, item.id)
        val saved = readManifest(dir).toMutableList()
        var nextIndex = (saved.maxOfOrNull { it.fileName.substringBefore('.').toIntOrNull() ?: -1 } ?: -1) + 1
        for (subtitle in pending) {
            if (saved.any { it.sourceUrl == subtitle.url }) continue
            val body = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                runCatching {
                    val request = Request.Builder().url(subtitle.url).get().build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@runCatching null
                        response.body?.string()?.takeIf { it.isNotBlank() }
                    }
                }.getOrNull()
            } ?: continue
            val extension = subtitle.url.substringAfterLast('.', "").lowercase()
                .takeIf { it in SUPPORTED_EXTENSIONS } ?: "srt"
            val fileName = "$nextIndex.$extension"
            nextIndex++
            runCatching {
                dir.mkdirs()
                File(dir, fileName).writeText(body)
                saved += ManifestTrack(subtitle.url, fileName, subtitle.lang)
                writeManifest(dir, saved)
            }.onFailure { Log.w(TAG, "Could not save subtitle ${subtitle.url}: ${it.message}") }
        }
        runCatching { writeManifest(dir, saved, complete = true) }
        if (saved.isNotEmpty()) Log.i(TAG, "Saved ${saved.size} subtitle sidecar(s) for ${item.id}")
    }

    /** Subtitles available for offline playback of this download (file:// URIs). */
    fun localSubtitles(context: Context, itemId: String): List<Subtitle> {
        val dir = manifestDir(context, itemId)
        if (!dir.isDirectory) return emptyList()
        return readManifest(dir).mapNotNull { track ->
            val file = File(dir, track.fileName)
            if (!file.exists()) return@mapNotNull null
            Subtitle(url = "file://${file.absolutePath}", lang = track.language)
        }
    }

    fun deleteSidecars(context: Context, itemId: String) {
        runCatching { manifestDir(context, itemId).deleteRecursively() }
    }

    private data class ManifestTrack(val sourceUrl: String, val fileName: String, val language: String)

    private fun readManifest(dir: File): List<ManifestTrack> {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) return emptyList()
        return runCatching {
            val json = JSONObject(manifestFile.readText())
            val tracks = json.optJSONArray("tracks") ?: return emptyList()
            buildList {
                for (i in 0 until tracks.length()) {
                    val obj = tracks.optJSONObject(i) ?: continue
                    val sourceUrl = obj.optString("sourceUrl").takeIf { it.isNotBlank() } ?: continue
                    val fileName = obj.optString("fileName").takeIf { it.isNotBlank() } ?: continue
                    add(ManifestTrack(sourceUrl, fileName, obj.optString("language").takeIf { it.isNotBlank() } ?: "und"))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeManifest(dir: File, tracks: List<ManifestTrack>, complete: Boolean = false) {
        dir.mkdirs()
        val json = JSONObject()
        json.put("complete", complete)
        val arr = JSONArray()
        tracks.forEach { track ->
            arr.put(
                JSONObject()
                    .put("sourceUrl", track.sourceUrl)
                    .put("fileName", track.fileName)
                    .put("language", track.language)
            )
        }
        json.put("tracks", arr)
        File(dir, "manifest.json").writeText(json.toString())
    }
}
