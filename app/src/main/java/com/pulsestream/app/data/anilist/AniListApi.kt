package com.pulsestream.app.data.anilist

import kotlinx.coroutines.CancellationException
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
 * Step 52: AniList client (Nuvio anilist/AniListApiClient.kt + AniListIdResolver.kt,
 * scoped port). GraphQL over OkHttp + org.json — the kotlinx-serialization
 * compiler plugin is not applied in Plavio, so no @Serializable DTOs.
 *
 * Auth is a pasted OAuth access token (Settings > Integrations > AniList):
 * AniList's implicit flow returns the token in the authorize URL fragment,
 * which Android strips from deep-link intents, so the user copies it from
 * the browser URL. The authorize link uses the user's own AniList OAuth
 * client id (created at anilist.co/settings/developer) — Nuvio's client
 * id is not reused.
 *
 * AniList entries are anime keyed by AniList/MAL ids; the public ARM map
 * (arm.haglund.dev, no key) translates them to IMDb ids so they resolve to
 * Plavio titles, and back for ratings.
 */
data class AniListViewer(val id: Long, val name: String)

data class AniListListEntry(
    val mediaId: Long,
    val malId: Long?,
    val title: String,
    val poster: String?,
    val progress: Int,
    val episodes: Int?,
    val score: Int,
    val status: String,
    /** Last list update, epoch millis (for Up Next ordering). */
    val updatedAtMs: Long,
)

object AniListApi {
    private const val GRAPHQL_URL = "https://graphql.anilist.co"
    private const val ARM_BASE_URL = "https://arm.haglund.dev/api/v2"
    private const val AUTHORIZE_URL = "https://anilist.co/api/v2/oauth/authorize"

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val armClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    /** Authorize URL for the user's own client id (implicit flow). */
    fun authorizeUrl(clientId: String): String =
        "$AUTHORIZE_URL?client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}&response_type=token"

    /** Validates a token and returns the viewer, or null when it is bad. */
    suspend fun validateToken(token: String): AniListViewer? = withContext(Dispatchers.IO) {
        try {
            val data = graphql(
                token,
                """{ Viewer { id name } }"""
            ) ?: return@withContext null
            val viewer = data.optJSONObject("Viewer") ?: return@withContext null
            AniListViewer(
                id = viewer.optLong("id"),
                name = viewer.optString("name").takeIf { it.isNotBlank() } ?: "AniList user"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The viewer's anime list (all statuses), newest first. Entries carry
     * the episode count so Up Next can show the next episode number.
     */
    suspend fun getWatchlist(token: String, limit: Int = 100): List<AniListListEntry> =
        withContext(Dispatchers.IO) {
            try {
                val viewer = validateToken(token) ?: return@withContext emptyList()
                val data = graphql(
                    token,
                    """{ MediaListCollection(userId: ${viewer.id}, type: ANIME) { lists { entries { status progress score updatedAt media { id idMal title { romaji english } coverImage { large } episodes averageScore } } } } }"""
                ) ?: return@withContext emptyList()
                val out = mutableListOf<AniListListEntry>()
                val lists = data.optJSONObject("MediaListCollection")
                    ?.optJSONArray("lists") ?: return@withContext emptyList()
                for (i in 0 until lists.length()) {
                    val entries = lists.optJSONObject(i)?.optJSONArray("entries") ?: continue
                    for (j in 0 until entries.length()) {
                        val entry = entries.optJSONObject(j) ?: continue
                        val media = entry.optJSONObject("media") ?: continue
                        val titleObj = media.optJSONObject("title")
                        val title = titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                            ?: titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
                            ?: continue
                        out += AniListListEntry(
                            mediaId = media.optLong("id"),
                            malId = if (media.has("idMal") && !media.isNull("idMal")) media.optLong("idMal") else null,
                            title = title,
                            poster = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.isNotBlank() },
                            progress = entry.optInt("progress"),
                            episodes = if (media.has("episodes") && !media.isNull("episodes")) media.optInt("episodes") else null,
                            score = entry.optInt("score"),
                            status = entry.optString("status"),
                            updatedAtMs = entry.optLong("updatedAt") * 1000L
                        )
                        if (out.size >= limit) return@withContext out
                    }
                }
                out
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }

    /** Mean score (0–100) for an AniList media id, or null. Public query — token optional. */
    suspend fun getMediaAverageScore(token: String?, anilistId: Long): Int? =
        withContext(Dispatchers.IO) {
            try {
                val data = graphql(
                    token,
                    """{ Media(id: $anilistId) { averageScore } }"""
                ) ?: return@withContext null
                val score = data.optJSONObject("Media")?.optInt("averageScore")
                score?.takeIf { it > 0 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }

    /** ARM: IMDb id ("tt…") for an AniList media id, or null. */
    suspend fun imdbForAnilist(mediaId: Long): String? = withContext(Dispatchers.IO) {
        try {
            val root = getJson("$ARM_BASE_URL/ids?source=anilist&id=$mediaId&include=imdb") as? JSONObject
                ?: return@withContext null
            root.optString("imdb").takeIf { it.startsWith("tt", ignoreCase = true) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** ARM: first AniList media id for an IMDb id, or null. */
    suspend fun anilistForImdb(imdbId: String): Long? = withContext(Dispatchers.IO) {
        try {
            val root = getJson("$ARM_BASE_URL/imdb?id=$imdbId&include=anilist")
                ?: return@withContext null
            when (root) {
                is JSONObject -> root.optLong("anilist").takeIf { it > 0 }
                is JSONArray -> (0 until root.length())
                    .mapNotNull { root.optJSONObject(it)?.optLong("anilist") }
                    .firstOrNull { it > 0 }
                else -> null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Runs a GraphQL query and returns the `data` object, or null on error. */
    private suspend fun graphql(token: String?, query: String): JSONObject? {
        val body = JSONObject().put("query", query).toString()
        val requestBuilder = Request.Builder()
            .url(GRAPHQL_URL)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(JSON))
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            val root = JSONObject(text)
            if (root.has("errors")) return null
            return root.optJSONObject("data")
        }
    }

    private suspend fun getJson(url: String): Any? {
        val request = Request.Builder().url(url)
            .header("Accept", "application/json").get().build()
        armClient.newCall(request).execute().use { response ->
            val text = response.body?.string() ?: return null
            if (!response.isSuccessful) return null
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                try { JSONArray(text) } catch (e2: Exception) { null }
            }
        }
    }
}
