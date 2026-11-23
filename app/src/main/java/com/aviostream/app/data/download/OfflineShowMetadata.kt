package com.aviostream.app.data.download

import android.content.Context
import android.util.Log
import com.aviostream.app.data.Video
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Step 46: offline show metadata (Nuvio downloads/OfflineShowMetadata.kt,
 * scoped port). For every title with downloads, the show/movie art and meta
 * captured at download time are kept under filesDir/offline-meta/ so the
 * Downloads and Library screens can still browse it with no connection:
 * "<key>.json" (title, poster/background URLs, description, genres,
 * episode list) plus "<key>-poster.jpg" (the poster image bytes, so the
 * artwork itself works offline too).
 *
 * Key: the series showKey for episodes, the DownloadItem id for movies.
 * Only titles that have downloads are stored; entries are removed when
 * their last download is deleted (see DownloadManager.deleteDownload).
 */
object OfflineShowMetadata {
    private const val TAG = "OfflineShowMetadata"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class OfflineVideoMeta(
        val id: String,
        val title: String,
        val released: String? = null,
        val thumbnail: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val overview: String? = null,
    )

    data class OfflineMeta(
        val title: String,
        val type: String,
        val poster: String? = null,
        val background: String? = null,
        val description: String? = null,
        val genres: List<String> = emptyList(),
        val videos: List<OfflineVideoMeta> = emptyList(),
    )

    private fun sanitizedKey(key: String): String =
        key.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "unknown" }

    private fun metaDir(context: Context): File = File(context.filesDir, "offline-meta")

    private fun metaFile(context: Context, key: String): File =
        File(metaDir(context), "${sanitizedKey(key)}.json")

    private fun posterFileInternal(context: Context, key: String): File =
        File(metaDir(context), "${sanitizedKey(key)}-poster.jpg")

    /** The cached poster image for this title, when it was downloaded. */
    fun posterFile(context: Context, key: String): File? =
        posterFileInternal(context, key).takeIf { it.exists() }

    fun save(
        context: Context,
        key: String,
        title: String,
        type: String,
        posterUrl: String? = null,
        backgroundUrl: String? = null,
        description: String? = null,
        genres: List<String> = emptyList(),
        videos: List<Video> = emptyList(),
    ) {
        runCatching {
            val dir = metaDir(context)
            dir.mkdirs()
            val json = JSONObject()
            json.put("title", title)
            json.put("type", type)
            posterUrl?.takeIf { it.isNotBlank() }?.let { json.put("poster", it) }
            backgroundUrl?.takeIf { it.isNotBlank() }?.let { json.put("background", it) }
            description?.takeIf { it.isNotBlank() }?.let { json.put("description", it) }
            json.put("genres", JSONArray(genres))
            val videoArr = JSONArray()
            videos.take(2000).forEach { video ->
                val obj = JSONObject()
                obj.put("id", video.id)
                obj.put("title", video.title ?: "")
                video.released?.let { obj.put("released", it) }
                video.thumbnail?.let { obj.put("thumbnail", it) }
                if (video.season > 0) obj.put("season", video.season)
                if (video.episode > 0) obj.put("episode", video.episode)
                video.overview?.let { obj.put("overview", it) }
                videoArr.put(obj)
            }
            json.put("videos", videoArr)
            metaFile(context, key).writeText(json.toString())
            // Poster bytes: only when not already cached.
            val posterFile = posterFileInternal(context, key)
            if (posterUrl?.startsWith("http") == true && !posterFile.exists()) {
                runCatching {
                    val request = Request.Builder().url(posterUrl).get().build()
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            response.body?.bytes()?.takeIf { it.isNotEmpty() }?.let { bytes ->
                                posterFile.writeBytes(bytes)
                            }
                        }
                    }
                }.onFailure { Log.w(TAG, "Poster cache failed for $key: ${it.message}") }
            }
        }.onFailure { Log.w(TAG, "Offline meta save failed for $key: ${it.message}") }
    }

    fun load(context: Context, key: String): OfflineMeta? {
        val file = metaFile(context, key)
        if (!file.exists()) return null
        return runCatching {
            val json = JSONObject(file.readText())
            val videoArr = json.optJSONArray("videos")
            val videos = buildList {
                if (videoArr != null) {
                    for (i in 0 until videoArr.length()) {
                        val obj = videoArr.optJSONObject(i) ?: continue
                        add(
                            OfflineVideoMeta(
                                id = obj.optString("id"),
                                title = obj.optString("title"),
                                released = obj.optString("released").takeIf { it.isNotBlank() },
                                thumbnail = obj.optString("thumbnail").takeIf { it.isNotBlank() },
                                season = obj.optInt("season").takeIf { it > 0 },
                                episode = obj.optInt("episode").takeIf { it > 0 },
                                overview = obj.optString("overview").takeIf { it.isNotBlank() },
                            )
                        )
                    }
                }
            }
            val genreArr = json.optJSONArray("genres")
            val genres = buildList {
                if (genreArr != null) {
                    for (i in 0 until genreArr.length()) {
                        genreArr.optString(i).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
            }
            OfflineMeta(
                title = json.optString("title"),
                type = json.optString("type").takeIf { it.isNotBlank() } ?: "series",
                poster = json.optString("poster").takeIf { it.isNotBlank() },
                background = json.optString("background").takeIf { it.isNotBlank() },
                description = json.optString("description").takeIf { it.isNotBlank() },
                genres = genres,
                videos = videos,
            )
        }.getOrNull()
    }

    fun delete(context: Context, key: String) {
        runCatching {
            metaFile(context, key).delete()
            posterFileInternal(context, key).delete()
        }
    }
}
