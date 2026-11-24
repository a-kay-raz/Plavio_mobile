package com.aviostream.app.data

import com.aviostream.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * TEST v0.14.73 (item 12, OC-4): skip segments merged from three
 * read-only providers in strict priority order —
 * IntroDB > Anime-Skip > AniSkip. All three are lookups only;
 * nothing is ever submitted or uploaded. Every provider failure
 * degrades to "no segments from that provider", and the merge
 * itself is pure and unit-tested.
 */
object SkipSegmentMerger {

    /** Lower number = higher priority. */
    fun priorityOf(provider: String): Int = when (provider) {
        "introdb" -> 0
        "anime-skip" -> 1
        "aniskip" -> 2
        else -> 3
    }

    /**
     * Merge per segment type: for each type, walk providers in
     * priority order and keep a segment only when it does not
     * overlap a segment already kept for that type. Result is
     * sorted by start time.
     */
    fun merge(byProvider: Map<String, List<SkipSegment>>): List<SkipSegment> {
        val ordered = byProvider.entries.sortedBy { priorityOf(it.key) }
        val kept = mutableListOf<SkipSegment>()
        for (type in SegmentType.values()) {
            val keptOfType = mutableListOf<SkipSegment>()
            for ((_, segments) in ordered) {
                for (segment in segments) {
                    if (segment.type != type) continue
                    if (segment.endMs <= segment.startMs) continue
                    val overlaps = keptOfType.any {
                        segment.startMs < it.endMs && it.startMs < segment.endMs
                    }
                    if (!overlaps) keptOfType.add(segment)
                }
            }
            kept.addAll(keptOfType)
        }
        return kept.sortedBy { it.startMs }
    }
}

/** AniSkip (api.aniskip.com) — keyed by MyAnimeList id. No auth. */
class AniSkipApi {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetchSegments(
        malId: Int,
        episode: Int,
        episodeLengthSec: Long
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        val types = listOf("op", "ed", "mixed-op", "mixed-ed", "recap")
            .joinToString("&") { "types[]=$it" }
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode" +
            "?$types&episodeLength=$episodeLengthSec"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Avio")
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parse(response.body?.string() ?: return@withContext emptyList())
            }
        }.getOrDefault(emptyList())
    }

    internal fun parse(text: String): List<SkipSegment> =
        runCatching {
            val obj = json.parseToJsonElement(text).jsonObject
            if (obj["found"]?.jsonPrimitive?.content != "true") return emptyList()
            val results = obj["results"]?.jsonArray ?: return emptyList()
            results.mapNotNull { el ->
                val item = el.jsonObject
                val type = when (item["skipType"]?.jsonPrimitive?.content) {
                    "op", "mixed-op" -> SegmentType.INTRO
                    "ed", "mixed-ed" -> SegmentType.OUTRO
                    "recap" -> SegmentType.RECAP
                    else -> return@mapNotNull null
                }
                val interval = item["interval"]?.jsonObject
                    ?: return@mapNotNull null
                val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull
                    ?: return@mapNotNull null
                val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull
                    ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                SkipSegment(type, (start * 1000).toLong(), (end * 1000).toLong())
            }
        }.getOrDefault(emptyList())
}

/**
 * Anime-Skip (api.anime-skip.com) — GraphQL, keyed by
 * MyAnimeList id. The API requires a per-app registered client
 * id (X-Client-ID). Avio has none registered, so the provider
 * ships INACTIVE: it contributes segments only when a build
 * carries ANIME_SKIP_CLIENT_ID (passed like the other -P keys,
 * empty by default). No credential is fabricated.
 */
class AnimeSkipApi(
    private val clientId: String = BuildConfig.ANIME_SKIP_CLIENT_ID
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    val isEnabled: Boolean get() = clientId.isNotBlank()

    suspend fun fetchSegments(
        malId: Int,
        episode: Int
    ): List<SkipSegment> = withContext(Dispatchers.IO) {
        if (!isEnabled) return@withContext emptyList()
        val query = """
            query (${'$'}animeId: UUID!, ${'$'}episode: Int!) {
              findEpisodeByExternalId(
                service: MYANIMELIST, serviceId: ${'$'}animeId, episode: ${'$'}episode
              ) { timestamps { type { name } at length } }
            }
        """.trimIndent()
        val body = buildJsonObject {
            put("query", query)
            put(
                "variables",
                buildJsonObject {
                    put("animeId", malId.toString())
                    put("episode", episode)
                }
            )
        }
        val request = Request.Builder()
            .url("https://api.anime-skip.com/graphql")
            .header("X-Client-ID", clientId)
            .header("User-Agent", "Avio")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                parse(response.body?.string() ?: return@withContext emptyList())
            }
        }.getOrDefault(emptyList())
    }

    internal fun parse(text: String): List<SkipSegment> =
        runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val episode = (root["data"] as? JsonObject)
                ?.get("findEpisodeByExternalId") as? JsonObject
                ?: return emptyList()
            val timestamps = episode["timestamps"]?.jsonArray ?: return emptyList()
            // Anime-Skip timestamps are POINTS (a type starts at
            // `at` seconds and runs to the next point); a segment
            // needs its end, so sort points and pair each mapped
            // type with the following point (or the episode
            // length carried on the last point).
            data class Point(val atSec: Double, val type: SegmentType?, val lengthSec: Double?)
            val points = timestamps.mapNotNull { el ->
                val item = el.jsonObject
                val at = item["at"]?.jsonPrimitive?.doubleOrNull
                    ?: return@mapNotNull null
                val name = (item["type"] as? JsonObject)
                    ?.get("name")?.jsonPrimitive?.content
                val type = when (name?.lowercase()) {
                    "intro", "op", "opening" -> SegmentType.INTRO
                    "recap", "preview" -> SegmentType.RECAP
                    "outro", "ed", "ending", "credits" -> SegmentType.OUTRO
                    else -> null
                }
                val length = item["length"]?.jsonPrimitive?.doubleOrNull
                Point(at, type, length)
            }.sortedBy { it.atSec }
            val episodeLength = points.mapNotNull { it.lengthSec }.maxOrNull()
            points.mapIndexedNotNull { index, point ->
                val type = point.type ?: return@mapIndexedNotNull null
                val end = points.getOrNull(index + 1)?.atSec
                    ?: episodeLength
                    ?: return@mapIndexedNotNull null
                if (end <= point.atSec) return@mapIndexedNotNull null
                SkipSegment(
                    type,
                    (point.atSec * 1000).toLong(),
                    (end * 1000).toLong()
                )
            }
        }.getOrDefault(emptyList())
}

/**
 * Resolves a MyAnimeList id for the anime providers from the
 * identifiers the app already carries: a "mal:" id directly, or
 * an "anilist:" id via one AniList GraphQL lookup. Anything else
 * (IMDb ids included) resolves to null — the anime providers
 * simply contribute nothing for non-anime titles.
 */
object MalIdResolver {
    private val client = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun fromPrefixedId(id: String?): Int? {
        val raw = id?.trim()?.lowercase() ?: return null
        return when {
            raw.startsWith("mal:") ->
                raw.removePrefix("mal:").toIntOrNull()
            else -> null
        }
    }

    suspend fun resolve(ids: List<String?>): Int? {
        for (id in ids) {
            fromPrefixedId(id)?.let { return it }
        }
        val anilistId = ids.firstNotNullOfOrNull { id ->
            val raw = id?.trim()?.lowercase() ?: return@firstNotNullOfOrNull null
            if (raw.startsWith("anilist:")) {
                raw.removePrefix("anilist:").toIntOrNull()
            } else {
                null
            }
        } ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put(
                        "query",
                        "query (\$id: Int) { Media(id: \$id, type: ANIME) { idMal } }"
                    )
                    put("variables", buildJsonObject { put("id", anilistId) })
                }
                val request = Request.Builder()
                    .url("https://graphql.anilist.co")
                    .header("User-Agent", "Avio")
                    .post(
                        body.toString()
                            .toRequestBody("application/json".toMediaType())
                    )
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val text = response.body?.string() ?: return@use null
                    val root = json.parseToJsonElement(text).jsonObject
                    ((root["data"] as? JsonObject)?.get("Media") as? JsonObject)
                        ?.get("idMal")?.jsonPrimitive?.intOrNull
                }
            }.getOrNull()
        }
    }
}

/**
 * The merged lookup the player calls: IntroDB by IMDb id plus —
 * for anime, when a MAL id resolves — Anime-Skip and AniSkip,
 * fetched in parallel and merged by [SkipSegmentMerger].
 */
class SkipSegmentRepository(
    private val introDb: IntroDbApi = IntroDbApi(),
    private val aniSkip: AniSkipApi = AniSkipApi(),
    private val animeSkip: AnimeSkipApi = AnimeSkipApi()
) {
    suspend fun fetchMerged(
        imdbId: String?,
        candidateIds: List<String?>,
        season: Int,
        episode: Int,
        isMovie: Boolean,
        episodeLengthSec: Long
    ): List<SkipSegment> = coroutineScope {
        val introDeferred = async {
            if (imdbId.isNullOrBlank()) {
                emptyList()
            } else {
                runCatching {
                    introDb.fetchSegments(imdbId, season, episode, isMovie)
                }.getOrDefault(emptyList())
            }
        }
        val malId = if (isMovie) {
            null
        } else {
            runCatching { MalIdResolver.resolve(candidateIds) }.getOrNull()
        }
        val aniDeferred = async {
            if (malId == null) {
                emptyList()
            } else {
                runCatching {
                    aniSkip.fetchSegments(malId, episode, episodeLengthSec)
                }.getOrDefault(emptyList())
            }
        }
        val animeDeferred = async {
            if (malId == null) {
                emptyList()
            } else {
                runCatching {
                    animeSkip.fetchSegments(malId, episode)
                }.getOrDefault(emptyList())
            }
        }
        SkipSegmentMerger.merge(
            mapOf(
                "introdb" to introDeferred.await(),
                "anime-skip" to animeDeferred.await(),
                "aniskip" to aniDeferred.await()
            )
        )
    }
}
