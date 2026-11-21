package com.pulsestream.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * TMDB API client for enriching cast data with photos and IDs.
 * Used when user has provided a TMDB API key in Integrations.
 */
object TmdbClient {
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG_BASE = "https://image.tmdb.org/t/p/w185"
    
    private val json = Json { ignoreUnknownKeys = true }

    internal fun isFullyEnrichedCast(cast: List<CastMember>): Boolean =
        cast.isNotEmpty() && cast.all { it.photo != null && it.id > 0 }

    /**
     * Validate a candidate TMDB key without saving it: a /configuration
     * call answers 200 for a good key and 401 for a bad one. Any failure
     * (bad key, offline) is false — the dialog says "couldn't verify".
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false
        try {
            BoundedHttp.get("$BASE/configuration?api_key=$apiKey", headers = mapOf("Accept" to "application/json"))
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    /**
     * Enrich cast members with TMDB photos and IDs.
     * Takes a Stremio/IMDb ID (resolved via /find) or a TMDB-native "tmdb:" id (used directly).
     * Only enriches members that lack photo or have id=0.
     * The key comes from SettingsStore (Settings > Integrations) — callers never pass it.
     */
    suspend fun enrichCast(
        settings: SettingsStore,
        stremioId: String,
        mediaType: String, // "movie" or "series"
        cast: List<CastMember>
    ): List<CastMember> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext cast
        // Only enrich if needed
        if (isFullyEnrichedCast(cast)) return@withContext cast
        
        try {
            val tmdbType = if (mediaType == "series") "tv" else "movie"
            // TMDB-native ids ("tmdb:693134", "tmdb:movie:693134") carry the
            // TMDB id directly; the /find lookup only resolves IMDb ids and
            // returns nothing for them, which silently skipped enrichment.
            val tmdbId: Int? = if (stremioId.startsWith("tmdb:")) {
                stremioId.removePrefix("tmdb:").substringAfterLast(":").toIntOrNull()
            } else {
                // Resolve Stremio/IMDb ID to TMDB ID via find API
                val findUrl = "$BASE/find/$stremioId?api_key=$apiKey&external_source=imdb_id"
                val findResponse = BoundedHttp.get(findUrl, headers = mapOf("Accept" to "application/json"))
                val findJson = json.parseToJsonElement(findResponse).jsonObject
                val results = findJson["${tmdbType}_results"]?.jsonArray
                results?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
            }
            val resolvedTmdbId = tmdbId ?: return@withContext cast
            
            val url = "$BASE/$tmdbType/$resolvedTmdbId/credits?api_key=$apiKey"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val tmdbCast = root["cast"]?.jsonArray ?: return@withContext cast
            
            val photoMap = mutableMapOf<String, Pair<Int, String?>>()
            val tmdbFullCast = mutableListOf<CastMember>()
            for (el in tmdbCast) {
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val profile = obj["profile_path"]?.jsonPrimitive?.content
                val photoUrl = if (profile != null) "$IMG_BASE$profile" else null
                photoMap[name.lowercase()] = Pair(id, photoUrl)
                // Build full cast list (up to 15) from TMDB
                if (tmdbFullCast.size < 15) {
                    tmdbFullCast.add(CastMember(id = id, name = name, photo = photoUrl))
                }
            }
            
            // If addon provided few actors (< 5), use TMDB's full cast list instead
            if (cast.size < 5 && tmdbFullCast.isNotEmpty()) {
                return@withContext tmdbFullCast
            }
            
            cast.map { member ->
                val match = photoMap[member.name.lowercase()]
                if (match != null) {
                    member.copy(
                        id = if (member.id == 0) match.first else member.id,
                        photo = member.photo ?: match.second
                    )
                } else member
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cast // Return original on failure
        }
    }

    /**
     * Fetch actor details + filmography directly from TMDB.
     * Returns PersonDetail with movies/series lists.
     */
    suspend fun getPerson(
        settings: SettingsStore,
        personId: Int
    ): PersonDetail? = withContext(Dispatchers.IO) {
        if (personId <= 0) return@withContext null
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            // Person details
            val personUrl = "$BASE/person/$personId?api_key=$apiKey"
            val personResponse = BoundedHttp.get(personUrl, headers = mapOf("Accept" to "application/json"))
            val personJson = json.parseToJsonElement(personResponse).jsonObject
            val name = personJson["name"]?.jsonPrimitive?.content ?: return@withContext null
            val profile = personJson["profile_path"]?.jsonPrimitive?.content
            val photo = if (profile != null) "https://image.tmdb.org/t/p/w185$profile" else null
            // Step 58: the Nuvio person fields — biography, birthday,
            // deathday, place of birth, known-for department.
            val biography = personJson["biography"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val birthday = personJson["birthday"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val deathday = personJson["deathday"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val placeOfBirth = personJson["place_of_birth"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
            val knownFor = personJson["known_for_department"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }

            // Combined credits (movies + TV)
            val creditsUrl = "$BASE/person/$personId/combined_credits?api_key=$apiKey"
            val creditsResponse = BoundedHttp.get(creditsUrl, headers = mapOf("Accept" to "application/json"))
            val creditsJson = json.parseToJsonElement(creditsResponse).jsonObject
            val castArray = creditsJson["cast"]?.jsonArray ?: return@withContext PersonDetail(
                id = personId, name = name, photo = photo,
                movies = emptyList(), series = emptyList(),
                biography = biography, birthday = birthday, deathday = deathday,
                placeOfBirth = placeOfBirth, knownFor = knownFor
            )

            val movies = mutableListOf<MetaPreview>()
            val series = mutableListOf<MetaPreview>()
            for (el in castArray) {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
                // TMDB IDs need to be mapped back; use a tmdb: prefix so the app can resolve
                val metaId = "tmdb:$mediaType:$id"
                // Step 58: release date + popularity feed the person
                // page's Popular/Latest/Upcoming rails.
                val releaseDate = (obj["release_date"]?.jsonPrimitive?.contentOrNull
                    ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull)
                    ?.takeIf { it.isNotBlank() }
                val item = MetaPreview(
                    id = metaId, type = mediaType, name = title, poster = posterUrl,
                    releaseInfo = releaseDate?.take(4),
                    releaseDate = releaseDate,
                    popularity = obj["popularity"]?.jsonPrimitive?.doubleOrNull
                )
                if (mediaType == "movie") movies.add(item) else series.add(item)
            }

            PersonDetail(
                id = personId,
                name = name,
                photo = photo,
                movies = movies.take(20),
                series = series.take(20),
                biography = biography,
                birthday = birthday,
                deathday = deathday,
                placeOfBirth = placeOfBirth,
                knownFor = knownFor
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** One TMDB search hit carrying the ranking signals MetaPreview lacks. */
    data class TmdbSearchHit(
        val title: String,
        /** "movie" or "series" (mapped from TMDB's movie/tv). */
        val type: String,
        val year: Int?,
        val popularity: Double,
        val voteAverage: Double,
        val voteCount: Int
    )

    /**
     * Search TMDB and return popularity / vote data for ranking (v0.12.1).
     * Used by StreamRepository to order merged addon search results by
     * real popularity instead of relying on IMDb rating alone. Returns an
     * empty list on any failure — callers treat that as "no enrichment".
     */
    suspend fun searchWithPopularity(settings: SettingsStore, query: String): List<TmdbSearchHit> =
        withContext(Dispatchers.IO) {
            if (query.trim().length < 2) return@withContext emptyList()
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            try {
                val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val url = "$BASE/search/multi?api_key=$apiKey&query=$encoded&page=1&include_adult=false"
                val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                val root = json.parseToJsonElement(response).jsonObject
                val results = root["results"]?.jsonArray ?: return@withContext emptyList()
                val out = mutableListOf<TmdbSearchHit>()
                for (el in results.take(20)) {
                    val obj = el.jsonObject
                    val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                    if (mediaType != "movie" && mediaType != "tv") continue
                    val title = obj["title"]?.jsonPrimitive?.content
                        ?: obj["name"]?.jsonPrimitive?.content ?: continue
                    val dateStr = obj["release_date"]?.jsonPrimitive?.content
                        ?: obj["first_air_date"]?.jsonPrimitive?.content
                    val year = dateStr?.take(4)?.toIntOrNull()
                    out.add(
                        TmdbSearchHit(
                            title = title,
                            type = if (mediaType == "movie") "movie" else "series",
                            year = year,
                            popularity = obj["popularity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                            voteAverage = obj["vote_average"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                            voteCount = obj["vote_count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                        )
                    )
                }
                out
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { emptyList() }
        }

    /**
     * This week's trending movies + series (v0.12.4) — backs the Search
     * tab's "Popular right now" list. Ids use the same
     * "tmdb:<mediaType>:<id>" shape [search] produces, which the
     * detail-target resolver already understands. Empty on any failure.
     */
    suspend fun trending(settings: SettingsStore, limit: Int = 10): List<MetaPreview> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        try {
            val url = "$BASE/trending/all/week?api_key=$apiKey&language=en-US&page=1"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            val out = mutableListOf<MetaPreview>()
            for (el in results) {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                if (mediaType != "movie" && mediaType != "tv") continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
                val backdrop = obj["backdrop_path"]?.jsonPrimitive?.content
                val backdropUrl = if (backdrop != null) "https://image.tmdb.org/t/p/w780$backdrop" else null
                val dateStr = obj["release_date"]?.jsonPrimitive?.content
                    ?: obj["first_air_date"]?.jsonPrimitive?.content
                val year = dateStr?.take(4)?.toIntOrNull()?.toString()
                val type = if (mediaType == "movie") "movie" else "series"
                out.add(
                    MetaPreview(
                        id = "tmdb:$mediaType:$id", type = type, name = title,
                        poster = posterUrl, background = backdropUrl, releaseInfo = year
                    )
                )
                if (out.size >= limit) break
            }
            out
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    suspend fun search(settings: SettingsStore, query: String): List<MetaPreview> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE/search/multi?api_key=$apiKey&query=$encoded&page=1&include_adult=false"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            val out = mutableListOf<MetaPreview>()
            for (el in results.take(20)) {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.content ?: continue
                if (mediaType != "movie" && mediaType != "tv") continue
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                val title = obj["title"]?.jsonPrimitive?.content
                    ?: obj["name"]?.jsonPrimitive?.content ?: continue
                val poster = obj["poster_path"]?.jsonPrimitive?.content
                val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
                val type = if (mediaType == "movie") "movie" else "series"
                out.add(MetaPreview(id = "tmdb:$mediaType:$id", type = type, name = title, poster = posterUrl))
            }
            out
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    /**
     * Step 50: shared parser for TMDB result-shaped arrays (search /
     * trending shape). mediaTypeOverride fills in the type when the
     * endpoint implies it (collection parts are movies; discover is
     * per-endpoint). Ids are "tmdb:movie:123" like the other TMDB paths.
     */
    private fun parseTmdbItems(
        items: kotlinx.serialization.json.JsonArray,
        mediaTypeOverride: String?,
        limit: Int,
    ): List<MetaPreview> {
        val out = mutableListOf<MetaPreview>()
        for (el in items) {
            val obj = el.jsonObject
            val mediaType = mediaTypeOverride
                ?: obj["media_type"]?.jsonPrimitive?.content ?: continue
            if (mediaType != "movie" && mediaType != "tv") continue
            val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val title = obj["title"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content ?: continue
            val poster = obj["poster_path"]?.jsonPrimitive?.content
            val posterUrl = if (poster != null) "https://image.tmdb.org/t/p/w342$poster" else null
            val backdrop = obj["backdrop_path"]?.jsonPrimitive?.content
            val backdropUrl = if (backdrop != null) "https://image.tmdb.org/t/p/w780$backdrop" else null
            val dateStr = obj["release_date"]?.jsonPrimitive?.content
                ?: obj["first_air_date"]?.jsonPrimitive?.content
            val year = dateStr?.take(4)?.toIntOrNull()?.toString()
            val type = if (mediaType == "movie") "movie" else "series"
            out.add(
                MetaPreview(
                    id = "tmdb:$mediaType:$id", type = type, name = title,
                    poster = posterUrl, background = backdropUrl, releaseInfo = year
                )
            )
            if (out.size >= limit) break
        }
        return out
    }

    /** Step 50: GET /collection/{id} — the movies in a TMDB collection. */
    suspend fun getCollection(settings: SettingsStore, collectionId: Int, limit: Int = 100): List<MetaPreview> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        try {
            val url = "$BASE/collection/$collectionId?api_key=$apiKey&language=en-US"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val parts = root["parts"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(parts, mediaTypeOverride = "movie", limit = limit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    /** Step 50: GET /list/{id} — the items of a TMDB list (v3 lists). */
    suspend fun getTmdbList(settings: SettingsStore, listId: Int, limit: Int = 100): List<MetaPreview> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        try {
            val url = "$BASE/list/$listId?api_key=$apiKey&language=en-US"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val items = root["items"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(items, mediaTypeOverride = null, limit = limit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    /** Step 50: GET /discover/{movie|tv} — sorted TMDB browse (sortBy like "popularity.desc"). */
    suspend fun discover(
        settings: SettingsStore,
        mediaType: String,
        sortBy: String,
        limit: Int = 100,
    ): List<MetaPreview> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val endpoint = if (mediaType == "tv") "tv" else "movie"
        try {
            val encodedSort = java.net.URLEncoder.encode(sortBy, "UTF-8")
            val url = "$BASE/discover/$endpoint?api_key=$apiKey&sort_by=$encodedSort&language=en-US&page=1&include_adult=false"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(results, mediaTypeOverride = endpoint, limit = limit)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    /**
     * Step 51: the TMDB collection a movie belongs to (Nuvio TMDB meta
     * belongsToCollection). Resolves the TMDB movie id from a "tmdb:movie:"
     * id directly, or from an IMDb "tt" id via /find. Returns the
     * collection ref + its titles, or null when there is none.
     */
    data class TmdbCollectionRef(
        val id: Int,
        val name: String,
        val posterPath: String?,
        val backdropPath: String?,
    )

    suspend fun getMovieCollection(
        settings: SettingsStore,
        metaId: String,
    ): Pair<TmdbCollectionRef, List<MetaPreview>>? = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val tmdbMovieId: Int = when {
                metaId.startsWith("tmdb:movie:") ->
                    metaId.substringAfterLast(":").toIntOrNull() ?: return@withContext null
                metaId.startsWith("tt") -> {
                    val findUrl = "$BASE/find/$metaId?api_key=$apiKey&external_source=imdb_id&language=en-US"
                    val findRoot = json.parseToJsonElement(
                        BoundedHttp.get(findUrl, headers = mapOf("Accept" to "application/json"))
                    ).jsonObject
                    findRoot["movie_results"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: return@withContext null
                }
                else -> return@withContext null
            }
            val url = "$BASE/movie/$tmdbMovieId?api_key=$apiKey&language=en-US"
            val root = json.parseToJsonElement(
                BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            ).jsonObject
            val collectionObj = root["belongs_to_collection"]?.jsonObject ?: return@withContext null
            val ref = TmdbCollectionRef(
                id = collectionObj["id"]?.jsonPrimitive?.content?.toIntOrNull()
                    ?: return@withContext null,
                name = collectionObj["name"]?.jsonPrimitive?.content
                    ?: return@withContext null,
                posterPath = collectionObj["poster_path"]?.jsonPrimitive?.contentOrNull,
                backdropPath = collectionObj["backdrop_path"]?.jsonPrimitive?.contentOrNull
            )
            val titles = getCollection(settings, ref.id)
            if (titles.isEmpty()) return@withContext null
            ref to titles
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    /**
     * Step 53: TMDB id -> IMDb id (GET /movie|tv/{id} -> imdb_id). Used to
     * resolve PublicMetaDB's TMDB-keyed entries to Plavio meta ids. Null
     * when there is no TMDB key or the title has no IMDb id.
     */
    suspend fun getImdbId(
        settings: SettingsStore,
        mediaType: String,
        tmdbId: Long
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val segment = if (mediaType == "tv") "tv" else "movie"
            val url = "$BASE/$segment/$tmdbId?api_key=<redacted>&language=en-US"
            val root = json.parseToJsonElement(
                BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            ).jsonObject
            root["imdb_id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("tt") }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }
}
