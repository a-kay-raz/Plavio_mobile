package com.pulsestream.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Step 68 — Parental guide overlay. Port of Nuvio's
 * ParentalGuideRepository.kt: the guide comes from the public,
 * no-key Tiffara API (`https://api.tiffara.com/titles/{imdbId}/
 * parentsGuide`), one vote-counted severity per category. The
 * severity shown is the most-voted non-"none" level, and only when
 * it beats the "none" votes — a title nobody flagged stays silent.
 *
 * Parsed with org.json (the kotlinx-serialization plugin is not
 * applied in Avio). Results are cached in memory for the session.
 */
data class ParentalGuideResult(
    val nudity: String? = null,
    val violence: String? = null,
    val profanity: String? = null,
    val alcohol: String? = null,
    val frightening: String? = null,
)

data class ParentalWarning(
    val label: String,
    val severity: String,
)

object ParentalGuideRepository {
    private const val BASE_URL = "https://api.tiffara.com"
    private const val MAX_RESPONSE_BYTES = 256L * 1024

    private val cache = mutableMapOf<String, ParentalGuideResult?>()
    private val cacheMutex = Mutex()

    suspend fun getParentalGuide(imdbId: String): ParentalGuideResult? {
        val normalized = extractParentalGuideImdbId(imdbId) ?: return null
        cacheMutex.withLock {
            if (cache.containsKey(normalized)) return cache[normalized]
        }
        val result = runCatching {
            val body = BoundedHttp.get(
                url = "$BASE_URL/titles/$normalized/parentsGuide",
                headers = mapOf("Accept" to "application/json"),
                maxDecodedBytes = MAX_RESPONSE_BYTES
            )
            val guide = JSONObject(body).optJSONArray("parentsGuide") ?: return@runCatching null
            val categories = (0 until guide.length()).mapNotNull { i ->
                guide.optJSONObject(i)
            }
            if (categories.isEmpty()) null else mapCategories(categories)
        }.getOrNull()
        cacheMutex.withLock { cache[normalized] = result }
        return result
    }

    private fun mapCategories(categories: List<JSONObject>): ParentalGuideResult {
        val byCategory = categories.associateBy {
            it.optString("category").uppercase()
        }
        return ParentalGuideResult(
            nudity = resolveSeverity(byCategory["SEXUAL_CONTENT"]),
            violence = resolveSeverity(byCategory["VIOLENCE"]),
            profanity = resolveSeverity(byCategory["PROFANITY"]),
            alcohol = resolveSeverity(byCategory["ALCOHOL_DRUGS"]),
            frightening = resolveSeverity(byCategory["FRIGHTENING_INTENSE_SCENES"]),
        )
    }

    private fun resolveSeverity(category: JSONObject?): String? {
        val breakdowns = category?.optJSONArray("severityBreakdowns") ?: return null
        var dominantLevel: String? = null
        var dominantVotes = 0
        var noneVotes = 0
        for (i in 0 until breakdowns.length()) {
            val item = breakdowns.optJSONObject(i) ?: continue
            val level = item.optString("severityLevel").lowercase()
            val votes = item.optInt("voteCount")
            if (level == "none") {
                noneVotes = votes
            } else if (votes > dominantVotes) {
                dominantVotes = votes
                dominantLevel = level
            }
        }
        if (dominantLevel == null || dominantVotes <= noneVotes) return null
        return dominantLevel
    }
}

/** Builds the display warnings, severest first (max 5). */
fun buildParentalWarnings(guide: ParentalGuideResult): List<ParentalWarning> {
    val severityOrder = mapOf("severe" to 0, "moderate" to 1, "mild" to 2)
    return listOfNotNull(
        guide.nudity?.let { "nudity" to it },
        guide.violence?.let { "violence" to it },
        guide.profanity?.let { "profanity" to it },
        guide.alcohol?.let { "alcohol" to it },
        guide.frightening?.let { "frightening" to it },
    )
        .sortedBy { severityOrder[it.second.lowercase()] ?: 3 }
        .map { (category, severity) ->
            ParentalWarning(
                label = when (category) {
                    "nudity" -> "Sex & Nudity"
                    "violence" -> "Violence & Gore"
                    "profanity" -> "Profanity"
                    "alcohol" -> "Alcohol & Drugs"
                    "frightening" -> "Frightening Scenes"
                    else -> category
                },
                severity = when (severity.lowercase()) {
                    "severe" -> "Severe"
                    "moderate" -> "Moderate"
                    "mild" -> "Mild"
                    else -> severity.replaceFirstChar { it.uppercase() }
                },
            )
        }
        .take(5)
}

private val imdbIdPattern = Regex("tt\\d+")

fun extractParentalGuideImdbId(value: String?): String? =
    value?.let { imdbIdPattern.find(it)?.value }?.takeIf { it.startsWith("tt") }
