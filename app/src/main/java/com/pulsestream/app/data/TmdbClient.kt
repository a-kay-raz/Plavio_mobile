package com.pulsestream.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * TMDB API client for enriching cast data with photos and IDs.
 * Used when user has provided a TMDB API key in Integrations.
 *
 * Enrichment gates (Nuvio TmdbSettings, see SettingsStore): the
 * master switch [SettingsStore.tmdbEnrichmentEnabled] plus one flag
 * per module. Every public function below checks the master switch
 * first, then its module — a module OFF means that call never fires
 * and the app falls back to what the meta addons already provide.
 * [getImdbId] is the one exception: it converts tmdb: ids to IMDb ids
 * so Cinemeta can serve meta, which is resolution infrastructure,
 * not enrichment, so it stays ungated.
 */
object TmdbClient {
    private const val BASE = "https://api.themoviedb.org/3"
    private const val IMG_BASE = "https://image.tmdb.org/t/p/w185"

    private val json = Json { ignoreUnknownKeys = true }

    private fun enrichmentOn(settings: SettingsStore): Boolean =
        settings.tmdbEnrichmentEnabled

    /** `language=xx` for TMDB calls, from the user's language setting (default en-US). */
    private fun langParam(settings: SettingsStore): String =
        "language=" + java.net.URLEncoder.encode(settings.tmdbLanguage, "UTF-8")

    /**
     * Title for TMDB-supplied items: the original title/name when the
     * user asked for original-language titles, else the localized one.
     */
    private fun localizedTitle(obj: JsonObject, settings: SettingsStore): String? =
        if (settings.tmdbOriginalTitleLanguage) {
            obj["original_title"]?.jsonPrimitive?.contentOrNull
                ?: obj["original_name"]?.jsonPrimitive?.contentOrNull
                ?: obj["title"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
        } else {
            obj["title"]?.jsonPrimitive?.content
                ?: obj["name"]?.jsonPrimitive?.content
        }

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
        // Credits module: cast enrichment is a credits fetch.
        if (!enrichmentOn(settings) || !settings.tmdbUseCredits) return@withContext cast
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
                val findUrl = "$BASE/find/$stremioId?api_key=$apiKey&external_source=imdb_id&${langParam(settings)}"
                val findResponse = BoundedHttp.get(findUrl, headers = mapOf("Accept" to "application/json"))
                val findJson = json.parseToJsonElement(findResponse).jsonObject
                val results = findJson["${tmdbType}_results"]?.jsonArray
                results?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
            }
            val resolvedTmdbId = tmdbId ?: return@withContext cast
            
            val url = "$BASE/$tmdbType/$resolvedTmdbId/credits?api_key=$apiKey&${langParam(settings)}"
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
        // Credits module: the person page is a person + combined-credits fetch.
        if (!enrichmentOn(settings) || !settings.tmdbUseCredits) return@withContext null
        try {
            // Person details
            val personUrl = "$BASE/person/$personId?api_key=$apiKey&${langParam(settings)}"
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
            val creditsUrl = "$BASE/person/$personId/combined_credits?api_key=$apiKey&${langParam(settings)}"
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
                val title = localizedTitle(obj, settings) ?: continue
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
            // Basic Info module: search/popularity is a basic title lookup.
            if (!enrichmentOn(settings) || !settings.tmdbUseBasicInfo) return@withContext emptyList()
            try {
                val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
                val url = "$BASE/search/multi?api_key=$apiKey&query=$encoded&page=1&include_adult=false&${langParam(settings)}"
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
        // Basic Info module: the trending chart is a basic title lookup.
        if (!enrichmentOn(settings) || !settings.tmdbUseBasicInfo) return@withContext emptyList()
        try {
            val url = "$BASE/trending/all/week?api_key=$apiKey&${langParam(settings)}&page=1"
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
        // Basic Info module: search is a basic title lookup.
        if (!enrichmentOn(settings) || !settings.tmdbUseBasicInfo) return@withContext emptyList()
        try {
            val encoded = java.net.URLEncoder.encode(query.trim(), "UTF-8")
            val url = "$BASE/search/multi?api_key=$apiKey&query=$encoded&page=1&include_adult=false&${langParam(settings)}"
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
        settings: SettingsStore,
    ): List<MetaPreview> {
        val out = mutableListOf<MetaPreview>()
        for (el in items) {
            val obj = el.jsonObject
            val mediaType = mediaTypeOverride
                ?: obj["media_type"]?.jsonPrimitive?.content ?: continue
            if (mediaType != "movie" && mediaType != "tv") continue
            val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
            val title = localizedTitle(obj, settings) ?: continue
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
        // Collections module.
        if (!enrichmentOn(settings) || !settings.tmdbUseCollections) return@withContext emptyList()
        try {
            val url = "$BASE/collection/$collectionId?api_key=$apiKey&${langParam(settings)}"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val parts = root["parts"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(parts, mediaTypeOverride = "movie", limit = limit, settings = settings)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { emptyList() }
    }

    /** Step 50: GET /list/{id} — the items of a TMDB list (v3 lists). */
    suspend fun getTmdbList(settings: SettingsStore, listId: Int, limit: Int = 100): List<MetaPreview> = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        // Collections module.
        if (!enrichmentOn(settings) || !settings.tmdbUseCollections) return@withContext emptyList()
        try {
            val url = "$BASE/list/$listId?api_key=$apiKey&${langParam(settings)}"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val items = root["items"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(items, mediaTypeOverride = null, limit = limit, settings = settings)
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
        // Collections module: discover backs TMDB collection folders.
        if (!enrichmentOn(settings) || !settings.tmdbUseCollections) return@withContext emptyList()
        val endpoint = if (mediaType == "tv") "tv" else "movie"
        try {
            val encodedSort = java.net.URLEncoder.encode(sortBy, "UTF-8")
            val url = "$BASE/discover/$endpoint?api_key=$apiKey&sort_by=$encodedSort&${langParam(settings)}&page=1&include_adult=false"
            val response = BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            val root = json.parseToJsonElement(response).jsonObject
            val results = root["results"]?.jsonArray ?: return@withContext emptyList()
            parseTmdbItems(results, mediaTypeOverride = endpoint, limit = limit, settings = settings)
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
        // Details module: the belongs_to_collection ref comes from the
        // /movie/{id} details response. (The parts fetch inside goes
        // through getCollection, which checks the Collections module.)
        if (!enrichmentOn(settings) || !settings.tmdbUseDetails) return@withContext null
        try {
            val tmdbMovieId: Int = when {
                metaId.startsWith("tmdb:movie:") ->
                    metaId.substringAfterLast(":").toIntOrNull() ?: return@withContext null
                metaId.startsWith("tt") -> {
                    val findUrl = "$BASE/find/$metaId?api_key=$apiKey&external_source=imdb_id&${langParam(settings)}"
                    val findRoot = json.parseToJsonElement(
                        BoundedHttp.get(findUrl, headers = mapOf("Accept" to "application/json"))
                    ).jsonObject
                    findRoot["movie_results"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
                        ?: return@withContext null
                }
                else -> return@withContext null
            }
            val url = "$BASE/movie/$tmdbMovieId?api_key=$apiKey&${langParam(settings)}"
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
     * TMDB id -> IMDb id (GET /movie|tv/{id} -> imdb_id). Null when there
     * is no TMDB key or the title has no IMDb id.
     *
     * Deliberately NOT gated by the enrichment switch or any module:
     * this converts tmdb: ids so Cinemeta can serve meta for them —
     * resolution infrastructure, not enrichment. Gating it would break
     * every tmdb: title's Details page for users who turn enrichment off.
     */
    suspend fun getImdbId(
        settings: SettingsStore,
        mediaType: String,
        tmdbId: Long
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
        try {
            val segment = if (mediaType == "tv") "tv" else "movie"
            val url = "$BASE/$segment/$tmdbId?api_key=<redacted>&${langParam(settings)}"
            val root = json.parseToJsonElement(
                BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
            ).jsonObject
            root["imdb_id"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.startsWith("tt") }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { null }
    }

    // ---- Enrichment fallbacks (Nuvio TmdbSettings modules) ----
    // Detail uses these only when the meta addon serves no equivalent
    // of its own (no trailers, no backdrop/poster, no production rows,
    // no network, no related rows, no season posters, no episode
    // overviews). Every function checks the master switch + its module.

    /**
     * Resolve a meta id to a TMDB numeric id: "tmdb:movie:123" carries
     * it directly, an IMDb "tt" id resolves via /find. Null when there
     * is no key, enrichment is off, or Basic Info is off — id
     * resolution IS the Basic Info module, and the enrichment
     * fallbacks below all funnel through here. (The reverse direction,
     * [getImdbId], is NOT gated: it is meta-resolution infrastructure.)
     */
    suspend fun resolveTmdbId(settings: SettingsStore, metaId: String, mediaType: String): Int? =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
            if (!enrichmentOn(settings) || !settings.tmdbUseBasicInfo) return@withContext null
            try {
                if (metaId.startsWith("tmdb:")) {
                    return@withContext metaId.removePrefix("tmdb:")
                        .substringAfterLast(":").toIntOrNull()
                }
                if (!metaId.startsWith("tt")) return@withContext null
                val tmdbType = if (mediaType == "series") "tv" else "movie"
                val findUrl = "$BASE/find/$metaId?api_key=$apiKey&external_source=imdb_id&${langParam(settings)}"
                val findJson = json.parseToJsonElement(
                    BoundedHttp.get(findUrl, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                findJson["${tmdbType}_results"]?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }

    /**
     * YouTube trailers for a title (Trailers module): /{type}/{id}/videos,
     * YouTube only, Trailer/Teaser first. Detail uses these only when
     * the meta addon serves no trailerStreams of its own.
     */
    suspend fun getTrailers(settings: SettingsStore, mediaType: String, tmdbId: Int): List<TrailerStream> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            if (!enrichmentOn(settings) || !settings.tmdbUseTrailers) return@withContext emptyList()
            try {
                val segment = if (mediaType == "tv") "tv" else "movie"
                val url = "$BASE/$segment/$tmdbId/videos?api_key=$apiKey&${langParam(settings)}"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                val results = root["results"]?.jsonArray ?: return@withContext emptyList()
                val sorted = results.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                    .filter {
                        it["site"]?.jsonPrimitive?.contentOrNull == "YouTube" &&
                            !it["key"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
                    }
                    .sortedByDescending {
                        when (it["type"]?.jsonPrimitive?.contentOrNull) {
                            "Trailer" -> 2
                            "Teaser" -> 1
                            else -> 0
                        }
                    }
                val out = mutableListOf<TrailerStream>()
                for (obj in sorted) {
                    val key = obj["key"]?.jsonPrimitive?.contentOrNull ?: continue
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: "Trailer"
                    out += TrailerStream(title = name, ytId = key)
                    if (out.size >= 10) break
                }
                out
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }

    /**
     * Best backdrop + poster for a title (Artwork module):
     * /{type}/{id}/images, highest-voted first. Returns
     * (posterUrl, backdropUrl); either side null when TMDB has none.
     * Detail uses these only when the meta addon serves no art.
     */
    suspend fun getArtwork(settings: SettingsStore, mediaType: String, tmdbId: Int): Pair<String?, String?>? =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext null
            if (!enrichmentOn(settings) || !settings.tmdbUseArtwork) return@withContext null
            try {
                val segment = if (mediaType == "tv") "tv" else "movie"
                val url = "$BASE/$segment/$tmdbId/images?api_key=$apiKey"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                fun best(kind: String, size: String): String? {
                    val arr = root[kind]?.jsonArray ?: return null
                    val top = arr.mapNotNull { runCatching { it.jsonObject }.getOrNull() }
                        .sortedByDescending {
                            it["vote_average"]?.jsonPrimitive?.doubleOrNull ?: 0.0
                        }.firstOrNull() ?: return null
                    val path = top["file_path"]?.jsonPrimitive?.contentOrNull ?: return null
                    return "https://image.tmdb.org/t/p/$size$path"
                }
                best("posters", "w342") to best("backdrops", "w780")
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }

    /**
     * Production-company names for a title (Productions module): the
     * /{type}/{id} details response. Detail shows them only when the
     * meta addon serves no director/writer/network of its own.
     */
    suspend fun getProductionCompanies(settings: SettingsStore, mediaType: String, tmdbId: Int): List<String> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            if (!enrichmentOn(settings) || !settings.tmdbUseProductions) return@withContext emptyList()
            try {
                val segment = if (mediaType == "tv") "tv" else "movie"
                val url = "$BASE/$segment/$tmdbId?api_key=$apiKey&${langParam(settings)}"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                root["production_companies"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject }.getOrNull()
                        ?.get("name")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { name -> name.isNotBlank() }
                } ?: emptyList()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }

    /**
     * Network names for a series (Networks module): /tv/{id}. Movies
     * have no networks, so this always returns empty for them. Detail
     * shows the first one only when the meta addon serves no network.
     */
    suspend fun getNetworks(settings: SettingsStore, tmdbId: Int): List<String> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            if (!enrichmentOn(settings) || !settings.tmdbUseNetworks) return@withContext emptyList()
            try {
                val url = "$BASE/tv/$tmdbId?api_key=$apiKey&${langParam(settings)}"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                root["networks"]?.jsonArray?.mapNotNull {
                    runCatching { it.jsonObject }.getOrNull()
                        ?.get("name")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { name -> name.isNotBlank() }
                } ?: emptyList()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }

    /**
     * TMDB recommendations (More Like This module):
     * /{type}/{id}/recommendations. Detail shows these only when the
     * meta addons return no related rows at all.
     */
    suspend fun getRecommendations(settings: SettingsStore, mediaType: String, tmdbId: Int, limit: Int = 20): List<MetaPreview> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            if (!enrichmentOn(settings) || !settings.tmdbUseMoreLikeThis) return@withContext emptyList()
            try {
                val segment = if (mediaType == "tv") "tv" else "movie"
                val url = "$BASE/$segment/$tmdbId/recommendations?api_key=$apiKey&${langParam(settings)}&page=1"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                val results = root["results"]?.jsonArray ?: return@withContext emptyList()
                parseTmdbItems(results, mediaTypeOverride = segment, limit = limit, settings = settings)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }

    /**
     * Season posters for a series (Season Posters module): /tv/{id}
     * seasons array, season number -> poster URL (w342). Detail uses
     * these in the season poster row when a season has no art yet.
     */
    suspend fun getSeasonPosters(settings: SettingsStore, tmdbId: Int): Map<Int, String> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyMap()
            if (!enrichmentOn(settings) || !settings.tmdbUseSeasonPosters) return@withContext emptyMap()
            try {
                val url = "$BASE/tv/$tmdbId?api_key=$apiKey&${langParam(settings)}"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                val out = mutableMapOf<Int, String>()
                root["seasons"]?.jsonArray?.forEach { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@forEach
                    val season = obj["season_number"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: return@forEach
                    val path = obj["poster_path"]?.jsonPrimitive?.contentOrNull
                        ?: return@forEach
                    out[season] = "https://image.tmdb.org/t/p/w342$path"
                }
                out
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyMap() }
        }

    /** One TMDB episode: overview/runtime/still/rating for the Episodes + Episode Ratings modules. */
    data class TmdbEpisode(
        val episode: Int,
        val name: String?,
        val overview: String?,
        val runtimeMinutes: Int?,
        val stillUrl: String?,
        val voteAverage: Double?,
    )

    /**
     * Episodes of one season (Episodes + Episode Ratings modules):
     * /tv/{id}/season/{season}. The function itself only checks the
     * master switch and that at least one of the two modules is on —
     * Detail applies the fields per-module (overview/runtime/still
     * only when Episodes is on, voteAverage only when Episode Ratings
     * is on).
     */
    suspend fun getSeasonEpisodes(settings: SettingsStore, tmdbId: Int, season: Int): List<TmdbEpisode> =
        withContext(Dispatchers.IO) {
            val apiKey = settings.tmdbApiKey?.takeIf { it.isNotBlank() } ?: return@withContext emptyList()
            if (!enrichmentOn(settings) ||
                (!settings.tmdbUseEpisodes && !settings.tmdbUseEpisodeRatings)
            ) return@withContext emptyList()
            try {
                val url = "$BASE/tv/$tmdbId/season/$season?api_key=$apiKey&${langParam(settings)}"
                val root = json.parseToJsonElement(
                    BoundedHttp.get(url, headers = mapOf("Accept" to "application/json"))
                ).jsonObject
                val episodes = root["episodes"]?.jsonArray ?: return@withContext emptyList()
                episodes.mapNotNull { el ->
                    val obj = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
                    val ep = obj["episode_number"]?.jsonPrimitive?.content?.toIntOrNull()
                        ?: return@mapNotNull null
                    val still = obj["still_path"]?.jsonPrimitive?.contentOrNull
                    TmdbEpisode(
                        episode = ep,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull,
                        overview = obj["overview"]?.jsonPrimitive?.contentOrNull
                            ?.takeIf { it.isNotBlank() },
                        runtimeMinutes = obj["runtime"]?.jsonPrimitive?.content?.toIntOrNull(),
                        stillUrl = if (still != null) "https://image.tmdb.org/t/p/w300$still" else null,
                        voteAverage = obj["vote_average"]?.jsonPrimitive?.doubleOrNull
                            ?.takeIf { it > 0.0 },
                    )
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }
}
