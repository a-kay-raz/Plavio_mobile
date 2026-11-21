package com.pulsestream.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
}
