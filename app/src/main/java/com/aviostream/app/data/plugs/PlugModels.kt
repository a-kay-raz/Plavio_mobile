package com.aviostream.app.data.plugs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Plugs v1 (v0.14.55-test) — data model.
 *
 * The plug REPO format is Nuvio's, adopted as the primary shape by the
 * owner's order 2026-11-20 ("Plugs follow Nuvio's design"): a repo is
 * one JSON manifest URL; the manifest names the repo and lists its
 * scrapers, each pointing at one JavaScript file whose
 * `getStreams(tmdbId, mediaType, season, episode)` returns stream
 * objects (see [JsPlugHost]). Avio adds ONE extension field —
 * `"kind": "rule"` marks a declarative RULE plug (see
 * [RulePlugEngine]); entries without it are JS plugs, exactly as
 * Nuvio treats them, so an unmodified Nuvio repo parses cleanly here.
 */

enum class PlugKind { JS, RULE }

/** One plug as listed in a repo manifest. */
data class PlugRepoEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val version: String? = null,
    val author: String? = null,
    val kind: PlugKind = PlugKind.JS,
    /** Entry path as listed in the manifest (relative to it, or absolute). */
    val filename: String,
    /** Nuvio vocabulary: "movie" and/or "tv" (tv = series). */
    val supportedTypes: List<String> = listOf("movie", "tv"),
    val logo: String? = null,
    val enabledByDefault: Boolean = true
) {
    /** True when this plug serves the given APP type ("movie"/"series"). */
    fun supportsAppType(appType: String): Boolean {
        val nuvioType = if (appType == "series") "tv" else appType
        return supportedTypes.any { it.equals(nuvioType, ignoreCase = true) } ||
            (appType == "movie" && supportedTypes.any { it.equals("movie", true) })
    }
}

/** A parsed repo manifest. */
data class PlugRepoManifest(
    val name: String,
    val version: String? = null,
    val plugs: List<PlugRepoEntry>
)

object PlugManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Parses a Nuvio-format manifest. The scraper list is read from
     * the `"scrapers"` key (what the real repos publish); `"providers"`
     * is accepted as an alias. Throws on a document that carries
     * neither — a wrong URL should fail loudly in the Add flow, never
     * install an empty repo silently.
     */
    fun parse(text: String): PlugRepoManifest {
        val root = json.parseToJsonElement(text).jsonObject
        val name = root.str("name") ?: "Unnamed repo"
        val version = root.str("version")
        val list = (root["scrapers"] ?: root["providers"])?.let {
            runCatching { it.jsonArray }.getOrNull()
        } ?: throw IllegalArgumentException("That link lists no plugs — is it a plug repo link?")
        val plugs = list.mapNotNull { el ->
            val o = runCatching { el.jsonObject }.getOrNull() ?: return@mapNotNull null
            val id = o.str("id") ?: return@mapNotNull null
            val filename = o.str("filename") ?: o.str("url") ?: return@mapNotNull null
            PlugRepoEntry(
                id = id,
                name = o.str("name") ?: id,
                description = o.str("description"),
                version = o.str("version"),
                author = o.str("author"),
                kind = if (o.str("kind").equals("rule", ignoreCase = true)) PlugKind.RULE else PlugKind.JS,
                filename = filename,
                supportedTypes = o.strList("supportedTypes").ifEmpty { listOf("movie", "tv") },
                logo = o.str("logo"),
                enabledByDefault = o.bool("enabled") ?: true
            )
        }
        if (plugs.isEmpty()) {
            throw IllegalArgumentException("That manifest lists no usable plugs.")
        }
        return PlugRepoManifest(name = name, version = version, plugs = plugs)
    }

    /** Resolves a manifest entry filename against the manifest URL. */
    fun resolveEntryUrl(manifestUrl: String, filename: String): String {
        if (filename.startsWith("http://") || filename.startsWith("https://")) return filename
        val base = manifestUrl.toHttpUrlOrNull() ?: return filename
        return runCatching { base.resolve(filename)?.toString() }.getOrNull() ?: filename
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.let {
            when (it.lowercase()) {
                "true" -> true
                "false" -> false
                else -> null
            }
        }

    private fun JsonObject.strList(key: String): List<String> =
        (this[key] as? JsonArray)?.mapNotNull {
            (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull
        } ?: emptyList()
}

/**
 * Everything a plug is told about the title it is resolving. JS plugs
 * receive (tmdbId, mediaType, season, episode) per the Nuvio
 * contract; RULE plugs template from the whole context.
 */
data class PlugTitleContext(
    /** App vocabulary: "movie" or "series". */
    val appType: String,
    val imdbId: String? = null,
    val tmdbId: Int? = null,
    val title: String? = null,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null
) {
    /** Nuvio mediaType for the JS contract: "movie" | "tv". */
    val nuvioType: String get() = if (appType == "series") "tv" else "movie"

    /** Stable identity for the on-device result cache. */
    val cacheKey: String
        get() = listOf(
            appType,
            imdbId ?: tmdbId?.let { "tmdb:$it" } ?: title.orEmpty(),
            season?.toString().orEmpty(),
            episode?.toString().orEmpty()
        ).joinToString("|")
}

/** Stream-id parsing shared by the plug leg (Stremio id shapes). */
object PlugIds {
    data class Parsed(val baseId: String, val season: Int?, val episode: Int?)

    /**
     * Splits a stream video id into its base id and, for episodes,
     * the trailing ":season:episode". Base shapes seen in the wild:
     * "tt14849194", "tmdb:840430", "tmdb:movie:840430",
     * "tmdb:tv:1399", plus opaque addon ids (returned whole).
     */
    fun parseVideoId(videoId: String): Parsed {
        val parts = videoId.split(":")
        if (parts.size >= 3) {
            val season = parts[parts.size - 2].toIntOrNull()
            val episode = parts[parts.size - 1].toIntOrNull()
            if (season != null && episode != null) {
                return Parsed(parts.dropLast(2).joinToString(":"), season, episode)
            }
        }
        return Parsed(videoId, null, null)
    }

    /** TMDB numeric id out of a "tmdb:…" base id, else null. */
    fun tmdbFromBaseId(baseId: String?): Int? {
        if (baseId == null || !baseId.startsWith("tmdb:")) return null
        return baseId.split(":").lastOrNull()?.toIntOrNull()
    }

    /** IMDb tt id out of a base id, else null. */
    fun imdbFromBaseId(baseId: String?): String? {
        if (baseId == null) return null
        return baseId.takeIf { it.matches(Regex("tt\\d+")) }
    }
}

/** One stream as a plug produced it, before mapping to StreamItem. */
data class PlugStream(
    val url: String,
    val name: String? = null,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val sizeBytes: Long? = null,
    val filename: String? = null
)
