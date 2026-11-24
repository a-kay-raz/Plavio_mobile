package com.aviostream.app.data.plugs

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.net.URLEncoder

/**
 * RULE plug engine (Plugs v1) — Avio's own declarative fast lane.
 * A rule plug is a JSON file (the plug's manifest entry carries
 * `"kind": "rule"`) describing an ordered list of steps; the engine
 * executes them natively over [RuleFetcher] (OkHttp in production).
 * No code from the rule ever runs — the worst a bad rule can do is
 * produce no streams.
 *
 * Step ops:
 *  - `fetch`    {id, url, method?, headers?, body?} — HTTP call;
 *               the response body is stored under `id`.
 *  - `jsonVar`  {id, from, path} — reads a scalar out of a JSON body
 *               ("moviesData.0.imdbId") into a template variable.
 *  - `unescape` {id, from} — stores a copy of a body with JSON-style
 *               escapes undone (for RSC/embedded-JSON pages).
 *  - `window`   {id, from, anchor, length} — stores the substring of
 *               a body starting at the anchor (scopes later regexes
 *               to one record in a page of many).
 *  - `streamsRegex` {from, pattern, urlGroup, title?, qualityGroup?,
 *               sizeGroup?} — every regex match becomes a stream.
 *  - `pixeldrain` {from} — finds Pixeldrain file ids in a body and
 *               resolves each through the official API
 *               (GET /api/file/{id}/info) into a stream whose URL is
 *               the API download URL, with the file's real name and
 *               size. Ids the API does not confirm are skipped — the
 *               op never invents a row.
 *
 * A rule may also carry a top-level `headers` object: those headers
 * attach to EVERY stream the rule returns (v0.14.56 — e.g.
 * MoviesBazar's BunnyCDN files refuse any request without the
 * site's Referer, so its rows were unplayable until the rule could
 * declare it). They ride the exact same PlugStream.headers →
 * StreamItem.headers → player mapping JS plugs already use.
 *
 * Templates ({...}) resolve from the title context ({imdbId},
 * {tmdbId}, {title}, {titleQuery}, {year}, {season}, {episode},
 * {type}, {query} = imdbId else the encoded title), from jsonVar
 * variables, and — inside streamsRegex titles — from match groups
 * ({g1}…). A `|numeric` suffix keeps digits only ("{siteImdb|numeric}").
 */
class RulePlugEngine(private val fetcher: RuleFetcher) {

    private class RuleAbort(message: String) : Exception(message)

    suspend fun run(ruleJson: String, ctx: PlugTitleContext): List<PlugStream> {
        val root = runCatching { Json.parseToJsonElement(ruleJson).jsonObject }.getOrNull()
            ?: return emptyList()
        val steps = (root["steps"] as? JsonArray) ?: return emptyList()
        val bodies = HashMap<String, String>()
        val vars = HashMap<String, String>()
        val items = ArrayList<PlugStream>()
        // Top-level rule headers: attached to every returned stream.
        val ruleHeaders = (root["headers"] as? JsonObject)?.entries?.associate { (k, v) ->
            k to template((v as? JsonPrimitive)?.content ?: "", ctx, vars)
        } ?: emptyMap()
        try {
            for (el in steps) {
                val step = el as? JsonObject ?: continue
                when (step.str("op")) {
                    "fetch" -> opFetch(step, ctx, vars, bodies)
                    "jsonVar" -> opJsonVar(step, bodies, vars)
                    "unescape" -> {
                        val from = bodies[step.str("from")] ?: throw RuleAbort("unescape: missing body")
                        bodies[step.str("id") ?: ""] = unescape(from)
                    }
                    "window" -> opWindow(step, ctx, vars, bodies)
                    "streamsRegex" -> opStreamsRegex(step, ctx, vars, bodies, items, ruleHeaders)
                    "pixeldrain" -> opPixeldrain(step, bodies, items, ruleHeaders)
                    else -> Unit // unknown ops are ignored (forward-compatible rules)
                }
            }
        } catch (e: RuleAbort) {
            // A broken chain yields whatever was honestly extracted
            // before the break (usually nothing) — never a fake row.
            return items
        }
        return items
    }

    private suspend fun opFetch(
        step: JsonObject,
        ctx: PlugTitleContext,
        vars: Map<String, String>,
        bodies: MutableMap<String, String>
    ) {
        val id = step.str("id") ?: throw RuleAbort("fetch: no id")
        val url = template(step.str("url") ?: throw RuleAbort("fetch: no url"), ctx, vars)
        val headers = (step["headers"] as? JsonObject)?.entries?.associate { (k, v) ->
            k to template((v as? JsonPrimitive)?.content ?: "", ctx, vars)
        } ?: emptyMap()
        val resp = try {
            fetcher.fetch(url, headers, step.str("method") ?: "GET", step.str("body"))
        } catch (e: Exception) {
            throw RuleAbort("fetch failed for $url: ${e.message}")
        }
        if (!resp.ok) throw RuleAbort("fetch HTTP ${resp.status} for $url")
        bodies[id] = resp.body
    }

    private fun opJsonVar(step: JsonObject, bodies: Map<String, String>, vars: MutableMap<String, String>) {
        val id = step.str("id") ?: return
        val body = bodies[step.str("from")] ?: return
        val path = step.str("path") ?: return
        val root = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return
        var cur: JsonElement = root
        for (part in path.split(".")) {
            cur = when (val c = cur) {
                is JsonObject -> c[part] ?: return
                is JsonArray -> c.getOrNull(part.toIntOrNull() ?: return) ?: return
                else -> return
            }
        }
        (cur as? JsonPrimitive)?.content?.let { vars[id] = it }
    }

    private fun opWindow(
        step: JsonObject,
        ctx: PlugTitleContext,
        vars: Map<String, String>,
        bodies: MutableMap<String, String>
    ) {
        val id = step.str("id") ?: return
        val from = bodies[step.str("from")] ?: throw RuleAbort("window: missing body")
        val anchor = template(step.str("anchor") ?: "", ctx, vars)
        val length = (step["length"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 12_000
        val idx = from.indexOf(anchor)
        bodies[id] = if (idx < 0) "" else from.substring(idx, minOf(idx + length, from.length))
    }

    private fun opStreamsRegex(
        step: JsonObject,
        ctx: PlugTitleContext,
        vars: Map<String, String>,
        bodies: Map<String, String>,
        items: MutableList<PlugStream>,
        ruleHeaders: Map<String, String>
    ) {
        val body = bodies[step.str("from")] ?: return
        val pattern = step.str("pattern") ?: return
        val regex = runCatching { Regex(pattern) }.getOrNull() ?: return
        val urlGroup = (step["urlGroup"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val qualityGroup = (step["qualityGroup"] as? JsonPrimitive)?.content?.toIntOrNull()
        val sizeGroup = (step["sizeGroup"] as? JsonPrimitive)?.content?.toIntOrNull()
        val titleTemplate = step.str("title")
        for (match in regex.findAll(body)) {
            val url = runCatching { match.groups[urlGroup] }.getOrNull()?.value?.trim() ?: continue
            if (!url.startsWith("http")) continue
            val groups = match.groupValues
            val title = titleTemplate?.let { template(it, ctx, vars, groups) }
            val quality = qualityGroup?.let { groups.getOrNull(it) }
            val size = sizeGroup?.let { groups.getOrNull(it) }
            val composed = listOfNotNull(
                title?.takeIf { it.isNotBlank() },
                quality?.takeIf { q -> title?.contains(q, ignoreCase = true) != true },
                size?.takeIf { s -> title?.contains(s, ignoreCase = true) != true }
            ).joinToString(" ").trim()
            items += PlugStream(
                url = url,
                title = composed.ifBlank { null },
                headers = ruleHeaders,
                filename = filenameFromUrl(url)
            )
        }
    }

    private suspend fun opPixeldrain(
        step: JsonObject,
        bodies: Map<String, String>,
        items: MutableList<PlugStream>,
        ruleHeaders: Map<String, String>
    ) {
        val raw = bodies[step.str("from")] ?: return
        val text = raw.replace("\\/", "/")
        val ids = PIXELDRAIN_ID.findAll(text).map { it.groupValues[1] }.distinct().take(6).toList()
        for (id in ids) {
            val info = try {
                fetcher.fetch("https://pixeldrain.com/api/file/$id/info", emptyMap())
            } catch (e: Exception) {
                null
            } ?: continue
            if (!info.ok) continue
            val obj = runCatching { Json.parseToJsonElement(info.body).jsonObject }.getOrNull() ?: continue
            val success = (obj["success"] as? JsonPrimitive)?.content == "true"
            if (!success) continue
            val name = (obj["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: continue
            val size = (obj["size"] as? JsonPrimitive)?.content?.toLongOrNull()
            items += PlugStream(
                url = "https://pixeldrain.com/api/file/$id",
                name = "Pixeldrain",
                title = name,
                headers = ruleHeaders,
                sizeBytes = size,
                filename = name
            )
        }
    }

    private fun unescape(text: String): String =
        text.replace("\\\"", "\"")
            .replace("\\/", "/")
            .replace("\\n", "\n")
            .replace("\\\\", "\\")

    private fun filenameFromUrl(url: String): String? {
        val tail = url.substringAfterLast('/').substringBefore('?').substringBefore('#')
        if (tail.isBlank() || !tail.contains('.')) return null
        return runCatching { java.net.URLDecoder.decode(tail, "UTF-8") }.getOrDefault(tail)
    }

    /**
     * Template expansion. [groups] carries streamsRegex match groups
     * ({g1}…); context and jsonVar values fill the rest.
     */
    internal fun template(
        text: String,
        ctx: PlugTitleContext,
        vars: Map<String, String>,
        groups: List<String> = emptyList()
    ): String {
        return TEMPLATE_VAR.replace(text) { m ->
            val expr = m.groupValues[1]
            val name = expr.substringBefore('|')
            val transform = expr.substringAfter('|', "")
            val raw: String = when {
                name.startsWith("g") && name.drop(1).toIntOrNull() != null ->
                    groups.getOrNull(name.drop(1).toInt()) ?: ""
                name == "imdbId" -> ctx.imdbId ?: ""
                name == "tmdbId" -> ctx.tmdbId?.toString() ?: ""
                name == "title" -> ctx.title ?: ""
                name == "titleQuery" -> URLEncoder.encode(ctx.title ?: "", "UTF-8")
                name == "query" -> ctx.imdbId ?: URLEncoder.encode(ctx.title ?: "", "UTF-8")
                name == "year" -> ctx.year?.toString() ?: ""
                name == "season" -> ctx.season?.toString() ?: ""
                name == "episode" -> ctx.episode?.toString() ?: ""
                name == "type" -> ctx.appType
                name == "nuvioType" -> ctx.nuvioType
                else -> vars[name] ?: ""
            }
            if (transform == "numeric") raw.filter { it.isDigit() } else raw
        }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }

    companion object {
        private val TEMPLATE_VAR = Regex("\\{([A-Za-z0-9_|]+)\\}")
        private val PIXELDRAIN_ID =
            Regex("(?:pixeldrain\\.com/(?:u|file)/|pixeldrain\\.com/api/file/)([A-Za-z0-9_-]{6,})")
    }
}
