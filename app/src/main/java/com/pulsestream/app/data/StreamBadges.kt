package com.pulsestream.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Step 61 — Custom stream badges (S4 + P34).
 *
 * Scoped port of Nuvio streams/StreamBadgeRules.kt,
 * streams/StreamBadgeSettingsRepository.kt, streams/BadgePattern.kt
 * (Android actual) and streams/StreamBadgePresentation.kt.
 *
 * A badge rule set is imported from a URL as JSON:
 *   { "filters": [{ "name", "pattern", "imageURL", "tagColor",
 *                   "tagStyle", "textColor", "borderColor",
 *                   "groupId", "isEnabled" }], "groups": [...] }
 * Each filter's regex is matched against the stream's filename, title,
 * name, description, release group and addon name; matches show as
 * chips on the stream row and (image badges only) in the player.
 *
 * Deviations from Nuvio:
 * - org.json instead of kotlinx-serialization (Plavio does not apply
 *   the serialization compiler plugin — same rule as everywhere else).
 * - No legacy debrid-badge storage migration (Plavio never had one).
 * - No per-group expand/collapse UI in the settings screen; groups
 *   only matter for the player two-phase match (a group already hit
 *   by the playback text is not re-matched from the meta text).
 * - Player badges have no MediaInfo refinement (no getMediaInfo
 *   equivalent in Plavio) — the playback candidate is the playing
 *   stream's filename/title.
 * - The file-size badge (Nuvio's StreamFileSizeBadge) is NOT ported:
 *   Plavio rows already show size via parseStreamInfo.
 */

const val STREAM_BADGE_IMPORT_LIMIT = 3

data class StreamBadge(
    val name: String,
    val imageURL: String = "",
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("imageURL", imageURL)
        put("tagColor", tagColor)
        put("tagStyle", tagStyle)
        put("textColor", textColor)
        put("borderColor", borderColor)
    }

    companion object {
        fun fromJson(obj: JSONObject): StreamBadge = StreamBadge(
            name = obj.optString("name", ""),
            imageURL = obj.optString("imageURL", ""),
            tagColor = obj.optString("tagColor", ""),
            tagStyle = obj.optString("tagStyle", ""),
            textColor = obj.optString("textColor", ""),
            borderColor = obj.optString("borderColor", ""),
        )
    }
}

data class StreamBadgeFilter(
    val id: String = "",
    val groupId: String = "",
    val name: String = "",
    val pattern: String = "",
    val imageURL: String = "",
    val isEnabled: Boolean = true,
    val tagColor: String = "",
    val tagStyle: String = "",
    val textColor: String = "",
    val borderColor: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("groupId", groupId)
        put("name", name)
        put("pattern", pattern)
        put("imageURL", imageURL)
        put("isEnabled", isEnabled)
        put("tagColor", tagColor)
        put("tagStyle", tagStyle)
        put("textColor", textColor)
        put("borderColor", borderColor)
    }

    companion object {
        fun fromJson(obj: JSONObject): StreamBadgeFilter = StreamBadgeFilter(
            id = obj.optString("id", ""),
            groupId = obj.optString("groupId", ""),
            name = obj.optString("name", ""),
            pattern = obj.optString("pattern", ""),
            imageURL = obj.optString("imageURL", ""),
            isEnabled = obj.optBoolean("isEnabled", true),
            tagColor = obj.optString("tagColor", ""),
            tagStyle = obj.optString("tagStyle", ""),
            textColor = obj.optString("textColor", ""),
            borderColor = obj.optString("borderColor", ""),
        )
    }
}

data class StreamBadgeGroup(
    val id: String = "",
    val name: String = "",
    val color: String = "",
    val isExpanded: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("color", color)
        put("isExpanded", isExpanded)
    }

    companion object {
        fun fromJson(obj: JSONObject): StreamBadgeGroup = StreamBadgeGroup(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            color = obj.optString("color", ""),
            isExpanded = obj.optBoolean("isExpanded", true),
        )
    }
}

data class StreamBadgeImport(
    val sourceUrl: String = "",
    val filters: List<StreamBadgeFilter> = emptyList(),
    val groups: List<StreamBadgeGroup> = emptyList(),
    val isActive: Boolean = true,
) {
    val enabledFilterCount: Int
        get() = filters.count { it.isEnabled }

    fun toJson(): JSONObject = JSONObject().apply {
        put("sourceUrl", sourceUrl)
        put("isActive", isActive)
        put("filters", JSONArray().apply { filters.forEach { put(it.toJson()) } })
        put("groups", JSONArray().apply { groups.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(obj: JSONObject): StreamBadgeImport {
            val filters = ArrayList<StreamBadgeFilter>()
            obj.optJSONArray("filters")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { filters += StreamBadgeFilter.fromJson(it) }
                }
            }
            val groups = ArrayList<StreamBadgeGroup>()
            obj.optJSONArray("groups")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { groups += StreamBadgeGroup.fromJson(it) }
                }
            }
            return StreamBadgeImport(
                sourceUrl = obj.optString("sourceUrl", ""),
                filters = filters,
                groups = groups,
                isActive = obj.optBoolean("isActive", true),
            )
        }
    }
}

data class StreamBadgeRules(
    val imports: List<StreamBadgeImport> = emptyList(),
) {
    val hasImport: Boolean
        get() = imports.isNotEmpty()

    val activeImport: StreamBadgeImport?
        get() = imports.firstOrNull { it.isActive } ?: imports.firstOrNull()

    val enabledFilterCount: Int
        get() = activeImport?.enabledFilterCount ?: 0

    fun normalized(): StreamBadgeRules {
        val normalizedImports = mutableListOf<StreamBadgeImport>()
        imports.forEach { import ->
            val normalizedUrl = import.sourceUrl.trim()
            if (normalizedUrl.isBlank() || import.filters.isEmpty()) return@forEach
            val normalizedImport = import.copy(sourceUrl = normalizedUrl)
            val existingIndex = normalizedImports.indexOfFirst { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }
            if (existingIndex >= 0) {
                normalizedImports[existingIndex] = normalizedImport
            } else if (normalizedImports.size < STREAM_BADGE_IMPORT_LIMIT) {
                normalizedImports += normalizedImport
            }
        }
        if (normalizedImports.isEmpty()) return copy(imports = emptyList())
        val activeIndex = normalizedImports.indexOfFirst { it.isActive }.takeIf { it >= 0 } ?: 0
        return copy(
            imports = normalizedImports.mapIndexed { index, import ->
                import.copy(isActive = index == activeIndex)
            },
        )
    }

    fun upsert(import: StreamBadgeImport, activate: Boolean = true): StreamBadgeRules {
        val normalizedUrl = import.sourceUrl.trim()
        if (normalizedUrl.isBlank()) return normalized()
        val normalizedImport = import.copy(sourceUrl = normalizedUrl, isActive = activate)
        val replaced = imports.map { existing ->
            if (existing.sourceUrl.equals(normalizedUrl, ignoreCase = true)) normalizedImport else existing
        }
        val nextImports = if (imports.any { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            replaced
        } else {
            imports + normalizedImport
        }
        val activeImports = if (activate) {
            nextImports.map { existing ->
                existing.copy(isActive = existing.sourceUrl.equals(normalizedUrl, ignoreCase = true))
            }
        } else {
            nextImports
        }
        return copy(imports = activeImports).normalized()
    }

    fun setActiveSource(sourceUrl: String): StreamBadgeRules {
        val normalizedUrl = sourceUrl.trim()
        if (normalizedUrl.isBlank() || imports.none { it.sourceUrl.equals(normalizedUrl, ignoreCase = true) }) {
            return normalized()
        }
        return copy(
            imports = imports.map { import ->
                import.copy(isActive = import.sourceUrl.equals(normalizedUrl, ignoreCase = true))
            },
        ).normalized()
    }

    fun forPlayer(sourceUrl: String): StreamBadgeRules =
        if (sourceUrl.isBlank()) normalized() else setActiveSource(sourceUrl)

    fun removeSource(sourceUrl: String): StreamBadgeRules =
        copy(imports = imports.filterNot { it.sourceUrl.equals(sourceUrl.trim(), ignoreCase = true) }).normalized()

    fun toJsonString(): String {
        val normalized = normalized()
        if (!normalized.hasImport) return ""
        return JSONObject().apply {
            put("imports", JSONArray().apply { normalized.imports.forEach { put(it.toJson()) } })
        }.toString()
    }

    companion object {
        /** Parses the stored format; also accepts Nuvio's legacy single-import shape. */
        fun parseStored(value: String?): StreamBadgeRules {
            if (value.isNullOrBlank()) return StreamBadgeRules()
            return try {
                val obj = JSONObject(value)
                val imports = ArrayList<StreamBadgeImport>()
                obj.optJSONArray("imports")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { imports += StreamBadgeImport.fromJson(it) }
                    }
                }
                if (imports.isEmpty() && obj.has("filters")) {
                    // Legacy: { "sourceUrl", "filters": [...], "groups": [...] }
                    imports += StreamBadgeImport.fromJson(obj)
                }
                StreamBadgeRules(imports).normalized()
            } catch (_: JSONException) {
                StreamBadgeRules()
            }
        }
    }
}

sealed interface StreamBadgeImportResult {
    data class Success(val rules: StreamBadgeRules) : StreamBadgeImportResult
    data class Error(val message: String) : StreamBadgeImportResult
}

internal object StreamBadgeRulesParser {
    fun parse(sourceUrl: String, payload: String): StreamBadgeImport {
        val decoded = try {
            JSONObject(payload)
        } catch (error: JSONException) {
            throw IllegalArgumentException("Invalid badge JSON: ${error.message.orEmpty()}")
        }

        val filters = ArrayList<StreamBadgeFilter>()
        decoded.optJSONArray("filters")?.let { arr ->
            for (i in 0 until arr.length()) {
                val filter = arr.optJSONObject(i) ?: continue
                val name = filter.optString("name", "").trim()
                val pattern = filter.optString("pattern", "").trim()
                if (name.isBlank() || pattern.isBlank()) continue
                filters += StreamBadgeFilter(
                    id = filter.optString("id", ""),
                    groupId = filter.optString("groupId", ""),
                    name = name,
                    pattern = pattern,
                    imageURL = filter.optString("imageURL", ""),
                    isEnabled = filter.optBoolean("isEnabled", true),
                    tagColor = filter.optString("tagColor", ""),
                    tagStyle = filter.optString("tagStyle", ""),
                    textColor = filter.optString("textColor", ""),
                    borderColor = filter.optString("borderColor", ""),
                )
            }
        }

        if (filters.isEmpty()) {
            throw IllegalArgumentException("Badge import did not contain any usable filters.")
        }

        val groups = ArrayList<StreamBadgeGroup>()
        decoded.optJSONArray("groups")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { groups += StreamBadgeGroup.fromJson(it) }
            }
        }

        return StreamBadgeImport(
            sourceUrl = sourceUrl.trim(),
            filters = filters,
            groups = groups,
        )
    }
}

/** Nuvio's Android actual, verbatim: a Regex wrapper that survives catastrophic backtracking. */
class BadgePattern(pattern: String) {
    private val regex = Regex(pattern)

    fun containsMatchIn(input: String): Boolean =
        try {
            regex.containsMatchIn(input)
        } catch (_: StackOverflowError) {
            false
        }
}

data class CompiledStreamBadgeFilter(
    val name: String,
    val badge: StreamBadge,
    val regex: BadgePattern,
    val groupId: String = "",
)

object StreamBadgeMatcher {
    private val WHITESPACE_PATTERN = Regex("\\s+")

    fun compile(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        if (!rules.hasImport) return emptyList()
        return rules.normalized().imports.filter { it.isActive }.flatMap { import ->
            import.filters.mapNotNull { filter ->
                if (!filter.isEnabled || filter.name.isBlank() || filter.pattern.isBlank()) {
                    return@mapNotNull null
                }
                val regex = runCatching { BadgePattern(filter.pattern) }.getOrNull() ?: return@mapNotNull null
                CompiledStreamBadgeFilter(
                    name = filter.name,
                    badge = StreamBadge(
                        name = filter.name,
                        imageURL = filter.imageURL,
                        tagColor = filter.tagColor,
                        tagStyle = filter.tagStyle,
                        textColor = filter.textColor,
                        borderColor = filter.borderColor,
                    ),
                    regex = regex,
                    groupId = filter.groupId,
                )
            }
        }
    }

    fun matchedBadges(stream: StreamItem, filters: List<CompiledStreamBadgeFilter>): List<StreamBadge> {
        if (filters.isEmpty()) return emptyList()
        return matchedBadges(badgeMatchCandidates(stream), filters)
    }

    fun matchedBadges(candidates: List<String>, filters: List<CompiledStreamBadgeFilter>): List<StreamBadge> {
        if (filters.isEmpty() || candidates.isEmpty()) return emptyList()

        val normalized = candidates.map { it.replace(WHITESPACE_PATTERN, " ") }
        val matched = linkedMapOf<String, StreamBadge>()
        filters.forEach { filter ->
            if (normalized.any { candidate -> filter.regex.containsMatchIn(candidate) }) {
                val key = filter.badge.dedupeKey()
                if (key !in matched) matched[key] = filter.badge
            }
        }
        return matched.values.toList()
    }

    /**
     * Two-phase badge matching for the player: the playback text wins;
     * a group already hit by playback is not re-matched from meta text.
     */
    fun matchedPlayerBadges(
        playbackCandidate: String?,
        metaCandidates: List<String>,
        filters: List<CompiledStreamBadgeFilter>
    ): List<StreamBadge> {
        if (filters.isEmpty()) return emptyList()
        val playback = playbackCandidate?.replace(WHITESPACE_PATTERN, " ")
        val meta = metaCandidates.map { it.replace(WHITESPACE_PATTERN, " ") }
        val playbackHits = filters.map { playback != null && it.regex.containsMatchIn(playback) }
        val coveredGroups = filters.filterIndexed { i, f -> playbackHits[i] && f.groupId.isNotBlank() }
            .mapTo(hashSetOf()) { it.groupId }
        val matched = linkedMapOf<String, StreamBadge>()
        filters.forEachIndexed { i, filter ->
            val hit = playbackHits[i] ||
                (filter.groupId.isBlank() || filter.groupId !in coveredGroups) &&
                meta.any { filter.regex.containsMatchIn(it) }
            if (hit) {
                val key = filter.badge.dedupeKey()
                if (key !in matched) matched[key] = filter.badge
            }
        }
        return matched.values.toList()
    }

    fun badgeMatchCandidates(stream: StreamItem): List<String> {
        val candidates = listOfNotNull(
            stream.filename,
            stream.filePath,
            stream.name,
            stream.title,
            stream.bingeGroup,
            stream.sourceName,
        )
            .map { it.replace(WHITESPACE_PATTERN, " ").trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return if (candidates.size <= 1) {
            candidates
        } else {
            candidates + candidates.joinToString(" ")
        }
    }

    private fun StreamBadge.dedupeKey(): String =
        (imageURL.takeIf { it.isNotBlank() } ?: name).lowercase()
}

/** Applies compiled badge rules to a stream list; regexes compile once per rules change. */
object StreamBadgePresentation {
    private var cachedRules: StreamBadgeRules? = null
    private var cachedFilters: List<CompiledStreamBadgeFilter> = emptyList()

    fun applyToStreams(streams: List<StreamItem>, rules: StreamBadgeRules): List<StreamItem> {
        if (streams.isEmpty()) return streams
        val filters = compiledFilters(rules)
        if (filters.isEmpty()) return streams
        return streams.map { stream ->
            val matchedBadges = StreamBadgeMatcher.matchedBadges(stream, filters)
            if (matchedBadges.isEmpty()) stream else stream.copy(badges = mergeBadges(stream.badges, matchedBadges))
        }
    }

    fun compiledFilters(rules: StreamBadgeRules): List<CompiledStreamBadgeFilter> {
        if (cachedRules != rules) {
            cachedFilters = StreamBadgeMatcher.compile(rules)
            cachedRules = rules
        }
        return cachedFilters
    }

    private fun mergeBadges(existing: List<StreamBadge>, matched: List<StreamBadge>): List<StreamBadge> {
        if (existing.isEmpty()) return matched
        val seenKeys = existing.mapTo(mutableSetOf()) { it.dedupeKey() }
        return existing + matched.filter { badge -> seenKeys.add(badge.dedupeKey()) }
    }

    private fun StreamBadge.dedupeKey(): String =
        (imageURL.takeIf { it.isNotBlank() } ?: name).lowercase()
}

/**
 * Settings-backed badge store. The rules JSON lives in the per-profile
 * SettingsStore (Step 56), so each profile has its own badge imports.
 * Replaces Nuvio's StreamBadgeSettingsRepository + Storage pair.
 */
class StreamBadgeSettings(private val settings: SettingsStore) {
    private val _rules = MutableStateFlow(StreamBadgeRules.parseStored(settings.streamBadgeRulesJson))
    val rules: StateFlow<StreamBadgeRules> = _rules.asStateFlow()

    private val _showInPlayer = MutableStateFlow(settings.showStreamBadgesInPlayer)
    val showInPlayer: StateFlow<Boolean> = _showInPlayer.asStateFlow()

    private val _playerBadgeSource = MutableStateFlow(settings.playerBadgeSource)
    val playerBadgeSource: StateFlow<String> = _playerBadgeSource.asStateFlow()

    fun snapshotRules(): StreamBadgeRules = _rules.value

    private fun persist(rules: StreamBadgeRules) {
        val normalized = rules.normalized()
        settings.streamBadgeRulesJson = normalized.toJsonString()
        _rules.value = normalized
    }

    suspend fun importFromUrl(url: String): StreamBadgeImportResult {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) {
            return StreamBadgeImportResult.Error("Enter a badge rules URL first.")
        }
        if (!normalizedUrl.startsWith("https://", ignoreCase = true) &&
            !normalizedUrl.startsWith("http://", ignoreCase = true)
        ) {
            return StreamBadgeImportResult.Error("Badge URL must start with http:// or https://.")
        }

        return try {
            val currentRules = _rules.value.normalized()
            val isExistingImport = currentRules.imports.any { import ->
                import.sourceUrl.equals(normalizedUrl, ignoreCase = true)
            }
            if (!isExistingImport && currentRules.imports.size >= STREAM_BADGE_IMPORT_LIMIT) {
                return StreamBadgeImportResult.Error("Badge import limit reached ($STREAM_BADGE_IMPORT_LIMIT sources). Remove one first.")
            }
            val payload = BoundedHttp.get(normalizedUrl)
            val parsedImport = StreamBadgeRulesParser.parse(
                sourceUrl = normalizedUrl,
                payload = payload,
            )
            val next = currentRules.upsert(parsedImport, activate = true)
            persist(next)
            StreamBadgeImportResult.Success(next)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            StreamBadgeImportResult.Error(error.message ?: "Badge import failed.")
        }
    }

    fun setActiveSource(sourceUrl: String) {
        val next = _rules.value.setActiveSource(sourceUrl)
        if (next == _rules.value) return
        persist(next)
    }

    fun deleteSource(sourceUrl: String) {
        val next = _rules.value.removeSource(sourceUrl)
        if (next == _rules.value) return
        persist(next)
    }

    fun setFilterEnabled(sourceUrl: String, filterId: String, enabled: Boolean) {
        val next = _rules.value.copy(
            imports = _rules.value.imports.map { import ->
                if (!import.sourceUrl.equals(sourceUrl, ignoreCase = true)) import
                else import.copy(
                    filters = import.filters.map { filter ->
                        if (filter.id == filterId || (filter.id.isBlank() && filter.name == filterId)) {
                            filter.copy(isEnabled = enabled)
                        } else filter
                    }
                )
            }
        ).normalized()
        if (next == _rules.value) return
        persist(next)
    }

    fun setShowInPlayer(enabled: Boolean) {
        if (_showInPlayer.value == enabled) return
        settings.showStreamBadgesInPlayer = enabled
        _showInPlayer.value = enabled
    }

    fun setPlayerBadgeSource(source: String) {
        if (_playerBadgeSource.value == source) return
        settings.playerBadgeSource = source
        _playerBadgeSource.value = source
    }
}
