package com.aviostream.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Step 58: favorite people (Nuvio details/FavoritePeopleRepository.kt,
 * scoped port). Nuvio's repo is per-profile disk storage with a pinned
 * list; Avio stores one org.json blob in SettingsStore — which is
 * already per-profile since Step 56, so favorites follow the active
 * profile with no extra plumbing. No serialization plugin is applied
 * in this app, so the blob is parsed with org.json like
 * StreamLinkCache.
 */
data class FavoritePerson(
    val tmdbId: Int,
    val name: String,
    val photo: String? = null,
    val knownFor: String? = null,
    val addedAtEpochMs: Long
)

data class FavoritePeopleUiState(
    val people: List<FavoritePerson> = emptyList(),
    val pinnedPersonIds: List<Int> = emptyList(),
    val isLoaded: Boolean = false
) {
    /** Pinned people first, then the rest newest-first. */
    val displayPeople: List<FavoritePerson>
        get() {
            if (people.isEmpty() || pinnedPersonIds.isEmpty()) return people
            val peopleById = people.associateBy(FavoritePerson::tmdbId)
            val pinned = pinnedPersonIds.mapNotNull(peopleById::get)
            val pinnedIds = pinned.mapTo(mutableSetOf(), FavoritePerson::tmdbId)
            return pinned + people.filterNot { it.tmdbId in pinnedIds }
        }
}

class FavoritePeopleRepository(private val settings: SettingsStore) {
    private val _uiState = MutableStateFlow(FavoritePeopleUiState())
    val uiState: StateFlow<FavoritePeopleUiState> = _uiState.asStateFlow()

    private var hasLoaded = false
    private var peopleById = linkedMapOf<Int, FavoritePerson>()
    private var pinnedPersonIds = emptyList<Int>()

    fun ensureLoaded() {
        if (hasLoaded) return
        hasLoaded = true
        val raw = settings.favoritePeopleJson.trim()
        if (raw.isEmpty()) {
            publish()
            return
        }
        runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("people") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optInt("tmdbId", 0)
                val name = obj.optString("name", "").trim()
                if (id <= 0 || name.isBlank()) continue
                peopleById[id] = FavoritePerson(
                    tmdbId = id,
                    name = name,
                    photo = obj.optString("photo", null)?.takeIf { it.isNotBlank() },
                    knownFor = obj.optString("knownFor", null)?.takeIf { it.isNotBlank() },
                    addedAtEpochMs = obj.optLong("addedAtEpochMs", 0L)
                )
            }
            val pins = root.optJSONArray("pinnedPersonIds") ?: JSONArray()
            pinnedPersonIds = (0 until pins.length())
                .mapNotNull { pins.optInt(it, 0).takeIf { id -> id > 0 } }
                .distinct()
                .filter(peopleById::containsKey)
        }.onFailure {
            peopleById.clear()
            pinnedPersonIds = emptyList()
        }
        publish()
    }

    fun isFavorite(tmdbId: Int): Boolean {
        ensureLoaded()
        return peopleById.containsKey(tmdbId)
    }

    fun isPinned(tmdbId: Int): Boolean {
        ensureLoaded()
        return tmdbId in pinnedPersonIds
    }

    fun toggle(person: PersonDetail) {
        ensureLoaded()
        if (peopleById.remove(person.id) == null) {
            peopleById[person.id] = FavoritePerson(
                tmdbId = person.id,
                name = person.name.trim(),
                photo = person.photo?.trim()?.takeIf { it.isNotBlank() },
                knownFor = person.knownFor?.trim()?.takeIf { it.isNotBlank() },
                addedAtEpochMs = System.currentTimeMillis()
            )
        } else {
            pinnedPersonIds = pinnedPersonIds.filterNot { it == person.id }
        }
        publish()
        persist()
    }

    fun remove(tmdbId: Int) {
        ensureLoaded()
        if (peopleById.remove(tmdbId) == null) return
        pinnedPersonIds = pinnedPersonIds.filterNot { it == tmdbId }
        publish()
        persist()
    }

    fun togglePinned(tmdbId: Int) {
        ensureLoaded()
        if (!peopleById.containsKey(tmdbId)) return
        pinnedPersonIds = if (tmdbId in pinnedPersonIds) {
            pinnedPersonIds.filterNot { it == tmdbId }
        } else {
            listOf(tmdbId) + pinnedPersonIds
        }.distinct().filter(peopleById::containsKey)
        publish()
        persist()
    }

    private fun publish() {
        val sorted = peopleById.values.sortedByDescending(FavoritePerson::addedAtEpochMs)
        pinnedPersonIds = pinnedPersonIds.distinct().filter(peopleById::containsKey)
        _uiState.value = FavoritePeopleUiState(
            people = sorted,
            pinnedPersonIds = pinnedPersonIds,
            isLoaded = hasLoaded
        )
    }

    private fun persist() {
        val root = JSONObject()
        val arr = JSONArray()
        peopleById.values.sortedByDescending(FavoritePerson::addedAtEpochMs).forEach { p ->
            arr.put(
                JSONObject()
                    .put("tmdbId", p.tmdbId)
                    .put("name", p.name)
                    .put("photo", p.photo)
                    .put("knownFor", p.knownFor)
                    .put("addedAtEpochMs", p.addedAtEpochMs)
            )
        }
        root.put("people", arr)
        val pins = JSONArray()
        pinnedPersonIds.filter(peopleById::containsKey).forEach { pins.put(it) }
        root.put("pinnedPersonIds", pins)
        settings.favoritePeopleJson = root.toString()
    }
}
