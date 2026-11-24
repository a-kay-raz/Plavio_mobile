package com.aviostream.app.ui.components

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Shared badge-pack helpers (v0.14.30 port, feature #15 — Fusion
 * badge packs).
 *
 * Fork adaptation vs upstream 0.14.30: upstream's file also ships
 * the v0.14.21 single imported pack (importBadgePack +
 * badgeImportedDir) — the fork never shipped that, so only the
 * helpers the Fusion store needs are ported here: the canonical-key
 * normalisation, the pack-body collector and the byte fetcher. The
 * User-Agent identifies the app as Avio.
 */

private val importHttp: OkHttpClient by lazy {
    OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

private val IMAGE_EXT_RE = Regex("""\.(png|webp|jpg|jpeg)$""", RegexOption.IGNORE_CASE)
private val HREF_RE = Regex("""(?:href|src)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)

/** Bare/alternate badge names → the canonical keys streamBadgeKeys emits. */
private val BADGE_KEY_ALIASES: Map<String, String> = buildMap {
    put("2160p", "res-2160p"); put("4k", "res-2160p"); put("uhd", "res-2160p")
    put("1440p", "res-1440p"); put("1080p", "res-1080p"); put("720p", "res-720p")
    put("480p", "res-480p"); put("360p", "res-360p")
    put("bluray", "src-bluray"); put("blu-ray", "src-bluray"); put("bdrip", "src-bluray")
    put("webdl", "src-webdl"); put("web-dl", "src-webdl")
    put("webrip", "src-webrip"); put("web-rip", "src-webrip")
    put("hdrip", "src-hdrip"); put("dvdrip", "src-dvdrip"); put("hdtv", "src-hdtv")
    put("cam", "src-cam"); put("hdcam", "src-cam"); put("hdts", "src-cam")
    put("hevc", "codec-hevc"); put("x265", "codec-hevc"); put("h265", "codec-hevc")
    put("avc", "codec-avc"); put("x264", "codec-avc"); put("h264", "codec-avc")
    put("av1", "codec-av1"); put("10bit", "bit-10"); put("10-bit", "bit-10")
    put("dv", "hdr-dv"); put("dolbyvision", "hdr-dv"); put("dolby-vision", "hdr-dv")
    put("hdr10plus", "hdr-hdr10plus"); put("hdr10+", "hdr-hdr10plus")
    put("hdr10", "hdr-hdr10"); put("hdr", "hdr-hdr")
    put("truehd", "audio-truehd"); put("dtshd", "audio-dtshd"); put("dts-hd", "audio-dtshd")
    put("dts", "audio-dts"); put("ddp", "audio-ddp"); put("dd+", "audio-ddp")
    put("dd", "audio-dd"); put("ac3", "audio-dd"); put("aac", "audio-aac")
    put("flac", "audio-flac"); put("opus", "audio-opus")
    put("51", "ch-51"); put("5.1", "ch-51"); put("71", "ch-71"); put("7.1", "ch-71")
    put("61", "ch-61"); put("6.1", "ch-61"); put("20", "ch-20"); put("2.0", "ch-20")
    // Fusion filter names (v0.14.28, the Xperience pack shape):
    // full words, spaces and punctuation, normalised the same way
    // canonicalBadgeKey normalises (lowercase, spaces -> '-').
    put("4k", "res-2160p")
    put("dts-hd-ma", "audio-dtshd"); put("dts-hd", "audio-dtshd")
    put("dd+", "audio-ddp"); put("dd", "audio-dd")
    put("h265-hevc", "codec-hevc"); put("h264-avc", "codec-avc")
    put("hdr10+", "hdr-hdr10plus")
    // Full language names -> lang-<iso1>.
    put("english", "lang-en"); put("spanish", "lang-es"); put("french", "lang-fr")
    put("german", "lang-de"); put("italian", "lang-it"); put("portuguese", "lang-pt")
    put("japanese", "lang-ja"); put("korean", "lang-ko"); put("chinese", "lang-zh")
    put("hindi", "lang-hi"); put("telugu", "lang-te"); put("tamil", "lang-ta")
    put("malayalam", "lang-ml"); put("kannada", "lang-kn"); put("bengali", "lang-bn")
    put("punjabi", "lang-pa"); put("marathi", "lang-mr"); put("urdu", "lang-ur")
    put("arabic", "lang-ar"); put("russian", "lang-ru"); put("turkish", "lang-tr")
    put("polish", "lang-pl"); put("dutch", "lang-nl"); put("swedish", "lang-sv")
    put("thai", "lang-th"); put("vietnamese", "lang-vi"); put("indonesian", "lang-id")
    put("persian", "lang-fa"); put("ukrainian", "lang-uk")
}

/** A canonical key carries one of the known group prefixes (or is atmos/remux). */
private val CANONICAL_KEY_RE = Regex("""^(res|src|codec|hdr|audio|ch|lang|tag)-[a-z0-9+]+$""")

/**
 * Normalizes a pack entry name (a JSON key or a file name) to a
 * canonical badge key, or null when the name names nothing we render.
 */
internal fun canonicalBadgeKey(rawName: String): String? {
    var name = rawName.trim().substringAfterLast('/').lowercase()
    name = name.replace(IMAGE_EXT_RE, "")
    // Fusion filter names carry spaces ("DTS-HD MA", "H265 HEVC"):
    // fold them (and underscores) to '-' so the alias table matches.
    name = name.replace(Regex("[\\s_]+"), "-")
    if (name.isBlank()) return null
    if (CANONICAL_KEY_RE.matches(name) || name == "atmos" || name == "remux") return name
    BADGE_KEY_ALIASES[name]?.let { return it }
    // Bare two-letter language codes ride as lang-<code>.
    if (name.length == 2 && name.all { it.isLetter() }) return "lang-$name"
    return null
}

/** Internal so the Fusion source store (FusionBadgeStore.kt) rides the same transport. */
internal fun httpGetBytes(url: String): ByteArray? {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", "Avio")
        .build()
    return runCatching {
        importHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.bytes()
        }
    }.getOrNull()
}

/** Collects (rawName → imageUrl) pairs from a JSON object, one level into badge sub-objects. */
private fun collectFromJsonObject(obj: JSONObject, into: MutableMap<String, String>) {
    val keys = obj.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        when (val value = obj.opt(key)) {
            is String -> if (value.startsWith("http")) into[key] = value
            is JSONObject -> collectFromJsonObject(value, into)
        }
    }
}

internal fun collectPackEntries(body: String, baseUrl: String): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val trimmed = body.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val obj = JSONObject(trimmed)
            // Fusion shape (v0.14.28): {groups:[...], filters:[
            // {name, pattern, imageURL, groupId}, ...]} — the shape
            // the Xperience pack publishes. Each filter's name maps
            // through the same canonical-key normalisation as any
            // other entry; names with no canonical key (combo or
            // streaming-service badges Avio has no fact for) are
            // skipped by canonicalBadgeKey at download time.
            val filters = obj.optJSONArray("filters")
            if (filters != null) {
                for (i in 0 until filters.length()) {
                    val f = filters.optJSONObject(i) ?: continue
                    val name = f.optString("name")
                    val url = f.optString("imageURL")
                        .ifBlank { f.optString("imageUrl") }
                        .ifBlank { f.optString("url") }
                    if (name.isNotBlank() && url.startsWith("http")) out[name] = url
                }
            }
            // A single direct image link as the whole manifest.
            collectFromJsonObject(obj, out)
        }
    } else if (trimmed.startsWith("[")) {
        // GitHub-contents style listing: [{name, download_url}, …].
        runCatching {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val name = item.optString("name")
                val url = item.optString("download_url")
                    .ifBlank { item.optString("url") }
                if (name.isNotBlank() && url.startsWith("http")) out[name] = url
            }
        }
    }
    if (out.isEmpty()) {
        // HTML page (or a JSON shape we didn't match): the image
        // links on it are the pack.
        for (match in HREF_RE.findAll(body)) {
            val href = match.groupValues[1]
            if (!IMAGE_EXT_RE.containsMatchIn(href)) continue
            val absolute = runCatching {
                java.net.URL(java.net.URL(baseUrl), href).toString()
            }.getOrNull() ?: continue
            out[href.substringAfterLast('/')] = absolute
        }
    }
    return out
}
