package com.pulsestream.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One provider score from OMDb, e.g. ("Rotten Tomatoes", "91%"). */
data class OmdbRating(val source: String, val value: String)

/**
 * OMDb API client — seed for Step 28 (ratings). Validates a candidate key
 * today; ratings lookups land with Step 28.
 */
object OmdbClient {
    private const val BASE = "https://www.omdbapi.com/"
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Validate a candidate OMDb key without saving it: a lookup for a
     * known title answers Response=True for a good key and
     * Response=False ("Invalid API key!") for a bad one. Any failure
     * (bad key, offline) is false — the dialog says "couldn't verify".
     */
    suspend fun validateApiKey(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext false
        try {
            val response = BoundedHttp.get(
                "$BASE?apikey=$apiKey&i=tt0111161",
                headers = mapOf("Accept" to "application/json")
            )
            val root = json.parseToJsonElement(response).jsonObject
            root["Response"]?.jsonPrimitive?.contentOrNull == "True"
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }
    }

    /**
     * Provider scores for a title by IMDb id (Step 28, Nuvio D5): the
     * OMDb "Ratings" array — Internet Movie Database, Rotten Tomatoes,
     * Metacritic. Empty on any failure (bad key, offline, unknown id) —
     * the row then falls back to the meta addon's IMDb value.
     */
    suspend fun fetchRatings(apiKey: String, imdbId: String): List<OmdbRating> =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || imdbId.isBlank()) return@withContext emptyList()
            try {
                val response = BoundedHttp.get(
                    "$BASE?apikey=$apiKey&i=$imdbId",
                    headers = mapOf("Accept" to "application/json")
                )
                val root = json.parseToJsonElement(response).jsonObject
                if (root["Response"]?.jsonPrimitive?.contentOrNull != "True") {
                    return@withContext emptyList()
                }
                root["Ratings"]?.jsonArray?.mapNotNull { element ->
                    val obj = element.jsonObject
                    val source = obj["Source"]?.jsonPrimitive?.contentOrNull
                    val value = obj["Value"]?.jsonPrimitive?.contentOrNull
                    if (source.isNullOrBlank() || value.isNullOrBlank()) null
                    else OmdbRating(source, value)
                } ?: emptyList()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { emptyList() }
        }
}
