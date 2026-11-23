package com.aviostream.app.data

import java.time.LocalDate

/**
 * Step 58: the person-page facts from Nuvio's PersonDetailScreen
 * (buildCreditSummary / personBirthLine / calculateAge /
 * formatDateForDisplay, verbatim logic — except the age uses the
 * real today via java.time; Nuvio's KMP build hardcodes 2026-04-03).
 */
private val SHORT_MONTHS = arrayOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** "1990-01-15" -> "Jan 15, 1990"; null when unparseable. */
fun formatPersonDate(date: String): String? {
    val parts = date.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 3) return null
    val (year, month, day) = parts
    if (month !in 1..12) return null
    return "${SHORT_MONTHS[month - 1]} $day, $year"
}

/** Age in years at [deathday] (or today); null when unparseable. */
fun calculatePersonAge(birthday: String, deathday: String?): Int? {
    val birthParts = birthday.split("-").mapNotNull { it.toIntOrNull() }
    if (birthParts.size < 3) return null
    val (birthYear, birthMonth, birthDay) = birthParts
    val endParts = deathday?.split("-")?.mapNotNull { it.toIntOrNull() }
    val (endYear, endMonth, endDay) = if (endParts != null && endParts.size >= 3) {
        Triple(endParts[0], endParts[1], endParts[2])
    } else {
        val today = LocalDate.now()
        Triple(today.year, today.monthValue, today.dayOfMonth)
    }
    var age = endYear - birthYear
    if (endMonth < birthMonth || (endMonth == birthMonth && endDay < birthDay)) age--
    return age.takeIf { it >= 0 }
}

/** "Jan 15, 1990 · 36 years" (or "· died Jan 1, 2020 · 29 years"). */
fun personBirthLine(birthday: String, deathday: String?): String {
    val birthdayDisplay = formatPersonDate(birthday) ?: birthday
    val deathDisplay = deathday?.let { formatPersonDate(it) ?: it }
    val age = calculatePersonAge(birthday, deathday)
    return buildString {
        append(birthdayDisplay)
        if (deathDisplay != null) {
            append(" · died ")
            append(deathDisplay)
        }
        if (age != null) {
            append(" · ")
            append(age)
            append(" years")
        }
    }
}

/** "42 titles · since 1998" — the credits summary under the name. */
fun buildCreditSummary(credits: List<MetaPreview>): String {
    if (credits.isEmpty()) return ""
    val firstYear = credits
        .mapNotNull { it.releaseDate?.take(4)?.toIntOrNull()
            ?: it.releaseInfo?.take(4)?.toIntOrNull() }
        .minOrNull()
    return buildString {
        append(credits.size)
        append(if (credits.size == 1) " title" else " titles")
        if (firstYear != null) {
            append(" · since ")
            append(firstYear)
        }
    }
}

/** First letters of the first two name words ("SR" for "Shah Rukh"). */
fun personInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return parts.take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { name.firstOrNull()?.uppercase() ?: "?" }
}

/** All of a person's credits, de-duplicated by id. */
fun allPersonCredits(person: PersonDetail): List<MetaPreview> =
    (person.movies + person.series).distinctBy { it.id }

/** Most popular first (no popularity sorts last) — Nuvio's Popular rail. */
fun popularCredits(credits: List<MetaPreview>): List<MetaPreview> =
    credits.sortedByDescending { it.popularity ?: 0.0 }

/** Released, newest first — Nuvio's Latest rail. */
fun latestCredits(credits: List<MetaPreview>, today: String = LocalDate.now().toString()): List<MetaPreview> =
    credits.filter { it.releaseDate != null && it.releaseDate <= today }
        .sortedByDescending { it.releaseDate ?: "" }

/** Not yet released, soonest first — Nuvio's Upcoming rail. */
fun upcomingCredits(credits: List<MetaPreview>, today: String = LocalDate.now().toString()): List<MetaPreview> =
    credits.filter { it.releaseDate != null && it.releaseDate > today }
        .sortedBy { it.releaseDate ?: "" }
