package com.pulsestream.app.data

import org.json.JSONObject

/**
 * Step 60: binge groups (Nuvio streams/BingeGroupCacheRepository.kt +
 * streams/FilenameEpisodeMatcher.kt, scoped port). When an episode
 * plays, its stream's release group is saved under the series id; the
 * next episode's autoplay then prefers streams from the same group
 * instead of re-ranking from scratch.
 *
 * Nuvio takes the group only from the addon's behaviorHints.bingeGroup.
 * Plavio does the same first — and falls back to the release group
 * derived from the filename, because the debrid addons Plavio plays
 * from essentially never send bingeGroup, so without the fallback the
 * feature would almost never fire.
 */
fun extractReleaseGroup(filename: String?): String? {
    val base = filename?.trim()?.takeIf { it.isNotBlank() } ?: return null
    // Strip the extension, then take the last dash-separated segment.
    val noExt = base.substringBeforeLast('.', base).trimEnd('.', ' ', '-')
    val segment = noExt.substringAfterLast('-', "").trim()
    if (segment.length !in 2..16) return null
    if (!segment.all { it.isLetterOrDigit() || it == '_' }) return null
    if (segment.all { it.isDigit() }) return null
    // A quality/container tag in the same slot is not a group.
    if (segment.lowercase() in KNOWN_NON_GROUP_TAGS) return null
    return segment
}

private val KNOWN_NON_GROUP_TAGS = setOf(
    "1080p", "720p", "2160p", "4k", "web-dl", "webdl", "web", "dl", "webrip",
    "bluray", "blu-ray", "brrip", "dvdrip", "hdtv", "hdts", "cam", "x264",
    "x265", "hevc", "hdr", "dv", "aac", "dts", "10bit", "8bit", "proper",
    "repack", "h264", "h265", "avc", "hevc10", "remux"
)

data class BingeGroupEntry(
    val group: String,
    val filename: String? = null,
    val infoHash: String? = null
)

/**
 * Per-series binge-group store. One org.json blob in SettingsStore
 * (per-profile since Step 56), mirroring Nuvio's per-contentId keys.
 */
class BingeGroupCache(private val settings: SettingsStore) {
    private var loaded = false
    private val entries = mutableMapOf<String, BingeGroupEntry>()

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = settings.bingeGroupCacheJson.trim()
        if (raw.isEmpty()) return
        runCatching {
            val root = JSONObject(raw)
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                val group = obj.optString("group", "").trim()
                if (group.isEmpty()) continue
                entries[key] = BingeGroupEntry(
                    group = group,
                    filename = obj.optString("filename", null)?.takeIf { it.isNotBlank() },
                    infoHash = obj.optString("infoHash", null)?.takeIf { it.isNotBlank() }
                )
            }
        }.onFailure { entries.clear() }
    }

    fun save(contentId: String, bingeGroup: String, filename: String? = null, infoHash: String? = null) {
        if (contentId.isBlank() || bingeGroup.isBlank()) return
        ensureLoaded()
        entries[contentId] = BingeGroupEntry(
            group = bingeGroup,
            filename = filename?.takeIf { it.isNotBlank() },
            infoHash = infoHash?.takeIf { it.isNotBlank() }
        )
        persist()
    }

    fun get(contentId: String): String? {
        ensureLoaded()
        return entries[contentId]?.group
    }

    fun getFilename(contentId: String): String? {
        ensureLoaded()
        return entries[contentId]?.filename
    }

    fun getInfoHash(contentId: String): String? {
        ensureLoaded()
        return entries[contentId]?.infoHash
    }

    fun remove(contentId: String) {
        ensureLoaded()
        if (entries.remove(contentId) == null) return
        persist()
    }

    private fun persist() {
        val root = JSONObject()
        entries.forEach { (contentId, entry) ->
            root.put(
                contentId,
                JSONObject()
                    .put("group", entry.group)
                    .put("filename", entry.filename)
                    .put("infoHash", entry.infoHash)
            )
        }
        settings.bingeGroupCacheJson = root.toString()
    }
}

/**
 * Builds a target filename string by replacing episode/season numbers in the
 * current filename with the next episode's numbers. Used for precise stream
 * matching when binge group alone is ambiguous. Verbatim port of Nuvio's
 * FilenameEpisodeMatcher (pure logic, no platform deps).
 */
object FilenameEpisodeMatcher {

    fun buildNextFilename(
        filename: String,
        currentSeason: Int?,
        currentEpisode: Int,
        nextSeason: Int?,
        nextEpisode: Int
    ): String? {
        if (filename.isBlank()) return null
        for (pattern in patterns) {
            val result = pattern.replace(filename, currentSeason, currentEpisode, nextSeason, nextEpisode)
            if (result != null) return result
        }
        return null
    }

    fun matches(candidateFilename: String?, expectedFilename: String?): Boolean {
        if (candidateFilename.isNullOrBlank() || expectedFilename.isNullOrBlank()) return false
        return candidateFilename.equals(expectedFilename, ignoreCase = true)
    }

    fun extractSeasonEpisode(filename: String): Pair<Int?, Int>? {
        if (filename.isBlank()) return null
        Regex("""(?i)S\s*(\d{1,3})\s*E\s*(\d{1,4})""").find(filename)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull() ?: return null
            return season to episode
        }
        Regex("""(?<!\d)(\d{1,2})[xX](\d{1,4})""").find(filename)?.let { match ->
            val season = match.groupValues[1].toIntOrNull()
            val episode = match.groupValues[2].toIntOrNull() ?: return null
            return season to episode
        }
        Regex("""(?i)\bE[Pp]?[.\s]?(\d{1,4})\b""").find(filename)?.let { match ->
            val episode = match.groupValues[1].toIntOrNull() ?: return null
            return null to episode
        }
        return null
    }

    private interface EpisodePattern {
        fun replace(filename: String, currentSeason: Int?, currentEpisode: Int, nextSeason: Int?, nextEpisode: Int): String?
    }

    private val sXeY = object : EpisodePattern {
        private val regex = Regex("""(?i)(S)\s*(\d{1,3})\s*(E)\s*(\d{1,4})""")
        override fun replace(filename: String, currentSeason: Int?, currentEpisode: Int, nextSeason: Int?, nextEpisode: Int): String? {
            if (currentSeason == null) return null
            val match = regex.find(filename) ?: return null
            if (match.groupValues[2].toIntOrNull() != currentSeason || match.groupValues[4].toIntOrNull() != currentEpisode) return null
            val seasonWidth = match.groupValues[2].length
            val episodeWidth = match.groupValues[4].length
            val replacement = "${match.groupValues[1]}${(nextSeason ?: currentSeason).toString().padStart(seasonWidth, '0')}${match.groupValues[3]}${nextEpisode.toString().padStart(episodeWidth, '0')}"
            return filename.replaceRange(match.range, replacement)
        }
    }

    // 01x03, 1x03 (negative lookbehind for digit avoids matching 1920x1080)
    private val seasonXepisode = object : EpisodePattern {
        private val regex = Regex("""(?<!\d)(\d{1,2})[xX](\d{1,4})""")
        override fun replace(filename: String, currentSeason: Int?, currentEpisode: Int, nextSeason: Int?, nextEpisode: Int): String? {
            if (currentSeason == null) return null
            val match = regex.find(filename) ?: return null
            if (match.groupValues[1].toIntOrNull() != currentSeason || match.groupValues[2].toIntOrNull() != currentEpisode) return null
            val seasonWidth = match.groupValues[1].length
            val episodeWidth = match.groupValues[2].length
            val sep = filename[match.range.first + match.groupValues[1].length]
            val replacement = "${(nextSeason ?: currentSeason).toString().padStart(seasonWidth, '0')}${sep}${nextEpisode.toString().padStart(episodeWidth, '0')}"
            return filename.replaceRange(match.range, replacement)
        }
    }

    private val episodeOnly = object : EpisodePattern {
        private val regex = Regex("""(?i)\b(E[Pp]?)[.\s]?(\d{1,4})\b""")
        override fun replace(filename: String, currentSeason: Int?, currentEpisode: Int, nextSeason: Int?, nextEpisode: Int): String? {
            val match = regex.find(filename) ?: return null
            if (match.groupValues[2].toIntOrNull() != currentEpisode) return null
            val episodeWidth = match.groupValues[2].length
            val prefix = match.groupValues[1]
            val separator = filename.substring(
                match.range.first + prefix.length,
                match.range.first + prefix.length + (match.value.length - prefix.length - match.groupValues[2].length)
            )
            val replacement = "$prefix$separator${nextEpisode.toString().padStart(episodeWidth, '0')}"
            return filename.replaceRange(match.range, replacement)
        }
    }

    private val patterns = listOf(sXeY, seasonXepisode, episodeOnly)
}
