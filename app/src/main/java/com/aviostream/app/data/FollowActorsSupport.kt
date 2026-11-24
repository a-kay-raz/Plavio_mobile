package com.aviostream.app.data

/**
 * Follow actors (v0.14.39, owner approved 2026-11-12).
 *
 * The user follows people from the actor page; the Home screen then
 * carries a "New from your people" row with titles that arrived
 * AFTER the follow. "After" is decided against a BASELINE captured
 * at follow time: everything in the person's filmography released
 * before last year (or with an unparseable year) is baselined, so
 * day one shows only recent releases and anything newer joins the
 * row as it lands — an actor's whole back catalogue never floods it.
 *
 * Storage: one string in SettingsStore (followedActorsJson). The
 * encode/decode here is pure Kotlin (no Android, no org.json) so it
 * unit-tests directly. Line format per actor:
 * `id|name|photo|baselineId,baselineId,…` with %, | and newlines
 * percent-escaped inside name/photo.
 */
data class FollowedActor(
    val id: Int,
    val name: String,
    val photo: String?,
    val baselineIds: Set<String>
)

object FollowActorsSupport {

    fun encode(actors: List<FollowedActor>): String =
        actors.joinToString("\n") { a ->
            listOf(
                a.id.toString(),
                escape(a.name),
                escape(a.photo ?: ""),
                a.baselineIds.joinToString(",")
            ).joinToString("|")
        }

    fun decode(raw: String?): List<FollowedActor> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return raw.split('\n').mapNotNull { line ->
            val parts = line.split('|')
            if (parts.size < 4) return@mapNotNull null
            val id = parts[0].toIntOrNull() ?: return@mapNotNull null
            FollowedActor(
                id = id,
                name = unescape(parts[1]),
                photo = unescape(parts[2]).ifBlank { null },
                baselineIds = parts[3].split(',').filter { it.isNotBlank() }.toSet()
            )
        }
    }

    private fun escape(s: String): String =
        s.replace("%", "%25").replace("|", "%7C").replace("\n", "%0A")

    private fun unescape(s: String): String =
        s.replace("%0A", "\n").replace("%7C", "|").replace("%25", "%")

    fun isFollowed(actors: List<FollowedActor>, personId: Int): Boolean =
        actors.any { it.id == personId }

    /** Follow with the baseline computed from the CURRENT filmography. */
    fun follow(
        actors: List<FollowedActor>,
        person: PersonDetail,
        currentYear: Int
    ): List<FollowedActor> {
        if (isFollowed(actors, person.id)) return actors
        val filmography = person.movies + person.series
        val baseline = filmography
            .filter { meta -> isBaseline(meta, currentYear) }
            .map { it.id }
            .toSet()
        return actors + FollowedActor(person.id, person.name, person.photo, baseline)
    }

    fun unfollow(actors: List<FollowedActor>, personId: Int): List<FollowedActor> =
        actors.filterNot { it.id == personId }

    /** Baseline = released before last year, or no readable year (never "new"). */
    fun isBaseline(meta: MetaPreview, currentYear: Int): Boolean {
        val year = yearOf(meta) ?: return true
        return year < currentYear - 1
    }

    /**
     * Titles that count as NEW for the Home row: not in the baseline
     * and released last year or later. Unparseable years never count.
     */
    fun newTitles(person: PersonDetail, followed: FollowedActor, currentYear: Int): List<MetaPreview> =
        (person.movies + person.series).filter { meta ->
            meta.id !in followed.baselineIds && !isBaseline(meta, currentYear)
        }

    /** First 4-digit run of releaseInfo ("2023–2025" → 2023); null when absent. */
    fun yearOf(meta: MetaPreview): Int? {
        val info = meta.releaseInfo ?: return null
        return Regex("""\d{4}""").find(info)?.value?.toIntOrNull()
    }
}
