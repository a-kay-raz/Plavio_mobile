package com.pulsestream.app.data

import kotlin.random.Random

/**
 * Step 41 — random episode picker (Nuvio shuffle/RandomEpisodePicker.kt,
 * ported to Plavio's Video + WatchedMarks keys). No-repeat within a
 * session: picks avoid already-shown ids until the pool is exhausted,
 * then the shown set resets (excluding the last pick).
 */
class RandomEpisodePicker(
    videos: List<Video>,
    watched: Set<Pair<Int, Int>>,
    private val random: Random = Random.Default,
) {
    private var episodes: List<Video> = emptyList()
    private var watchedKeys: Set<Pair<Int, Int>> = emptySet()
    private val shownIds = mutableSetOf<String>()
    private var lastPickedId: String? = null

    init {
        update(videos, watched)
    }

    /** Refresh the episode list / watched set; the no-repeat history survives for ids still present. */
    fun update(videos: List<Video>, watched: Set<Pair<Int, Int>>) {
        episodes = videos
            .filter { it.season > 0 && it.episode > 0 && it.id.isNotBlank() }
            .filter { isReleased(it) }
            .distinctBy { it.season to it.episode }
            .distinctBy { it.id }
        watchedKeys = watched
        val ids = episodes.map { it.id }.toSet()
        shownIds.retainAll(ids)
        if (lastPickedId !in ids) lastPickedId = null
    }

    fun count(includeWatched: Boolean): Int = candidates(includeWatched).size
    fun unwatchedCount(): Int = episodes.count { !isWatched(it) }
    fun totalCount(): Int = episodes.size
    fun isWatched(video: Video): Boolean = (video.season to video.episode) in watchedKeys

    fun pick(
        includeWatched: Boolean,
        current: Pair<Int, Int>? = null,
        consumeSelection: Boolean = true,
    ): Video? {
        val candidates = candidates(includeWatched)
            .filterNot { it.season to it.episode == current }
        if (candidates.isEmpty()) return null
        val unseen = candidates.filterNot { it.id in shownIds }
        val pool = unseen.ifEmpty {
            if (consumeSelection) shownIds.removeAll(candidates.map { it.id }.toSet())
            else shownIds.clear()
            candidates.filterNot { it.id == lastPickedId }.ifEmpty { candidates }
        }
        return pool.random(random).also {
            if (consumeSelection) shownIds.add(it.id)
            lastPickedId = it.id
        }
    }

    fun recordPlayed(current: Pair<Int, Int>) {
        episodes.firstOrNull { it.season to it.episode == current }?.let { shownIds.add(it.id) }
    }

    private fun candidates(includeWatched: Boolean): List<Video> =
        if (includeWatched) episodes else episodes.filterNot { isWatched(it) }

    /** Unreleased (future-dated) episodes never shuffle in; undated ones do. */
    private fun isReleased(video: Video): Boolean {
        val date = video.released?.take(10).orEmpty()
        if (date.length < 10) return true
        return runCatching { date <= java.time.LocalDate.now().toString() }.getOrDefault(true)
    }
}

/** Watched (season, episode) pairs for a series from the WatchedMarks key set ("e|<seriesId>|<season>|<episode>"). */
fun watchedEpisodeKeys(seriesId: String, keys: Set<String>): Set<Pair<Int, Int>> =
    keys.mapNotNull { key ->
        val parts = key.split("|")
        if (parts.size == 4 && parts[0] == "e" && parts[1] == seriesId) {
            val season = parts[2].toIntOrNull() ?: return@mapNotNull null
            val episode = parts[3].toIntOrNull() ?: return@mapNotNull null
            season to episode
        } else {
            null
        }
    }.toSet()
