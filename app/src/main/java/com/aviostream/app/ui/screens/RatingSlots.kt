package com.aviostream.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.R
import com.aviostream.app.data.OmdbRating
import com.aviostream.app.ui.theme.TextPrimary

/**
 * The detail page's rating sources (v0.14.30 port): each carries
 * the drawable of its brand mark — the OFFICIAL brand artwork
 * (Simple Icons paths), bundled in the APK, never hotlinked. The
 * icon IS the name — the row shows no written source names.
 * [aspect] is the mark's natural width ÷ height (from its own
 * bounding box), used by the size and fit rules below.
 *
 * Only the sources this fork actually has data for are here:
 * OMDb serves IMDb / Rotten Tomatoes / Metacritic, Trakt serves
 * its community rating. TMDB, RT Audience and Letterboxd have no
 * data source in this fork, and MDBList was removed by the owner —
 * their slots stay out.
 */
internal enum class RatingSource(val iconRes: Int, val aspect: Float) {
    IMDB(R.drawable.ic_rating_imdb, 1f),
    RT(R.drawable.ic_rating_rt, 1f),
    METACRITIC(R.drawable.ic_rating_metacritic, 1f),
    TRAKT(R.drawable.ic_rating_trakt, 1f);

    /** True for the marks whose natural shape is wider than square. */
    val wide: Boolean get() = aspect > 1.01f
}

/** Icon height of the ratings row (18dp — the locked upstream size). */
internal const val RATING_ICON_HEIGHT_DP = 18f

/** The widest any single rating icon may draw (the ~32dp wide slot). */
internal const val RATING_ICON_MAX_WIDTH_DP = 32f

/** Smallest clear gap kept between neighbouring icons when the row decides how many slots fit. */
internal const val RATING_ROW_MIN_GAP_DP = 16f

/**
 * The drawn size of one source's icon, in dp: height
 * [RATING_ICON_HEIGHT_DP] unless the mark's natural width at that
 * height would pass [RATING_ICON_MAX_WIDTH_DP] — then it shrinks
 * until the whole mark fits at its natural proportion. Nothing is
 * ever squeezed or clipped.
 */
internal fun RatingSource.iconSizeDp(): Pair<Float, Float> {
    val height = minOf(RATING_ICON_HEIGHT_DP, RATING_ICON_MAX_WIDTH_DP / aspect)
    return (height * aspect) to height
}

/**
 * The auto-fit rule: how many of [slots] (in slot order) fit WHOLE
 * in [availableWidthDp], each at its natural width with at least
 * [gapDp] of clear space between neighbours. If four don't fit,
 * three show; if three don't fit, two — dropped from the END of
 * the slot order. Returns 0 only when not even one icon fits.
 */
internal fun ratingFitCount(
    slots: List<RatingSlot>,
    availableWidthDp: Float,
    gapDp: Float = RATING_ROW_MIN_GAP_DP
): Int {
    var used = 0f
    var count = 0
    for (slot in slots) {
        val width = slot.source.iconSizeDp().first
        val needed = if (count == 0) width else width + gapDp
        if (used + needed > availableWidthDp) break
        used += needed
        count++
    }
    return count
}

/** One filled slot of the ratings row: the source + the number as shown. */
internal data class RatingSlot(val source: RatingSource, val value: String)

/**
 * The ratings row's slots, in the order the providers serve them
 * (OMDb first, Trakt appended): a rating the title doesn't have
 * simply has no slot — no placeholders. Values keep the formats
 * they are served in (7.8/10, 94%, 82/100).
 */
internal fun ratingSlots(ratings: List<OmdbRating>): List<RatingSlot> = buildList {
    for (rating in ratings) {
        val source = when (rating.source) {
            "Internet Movie Database" -> RatingSource.IMDB
            "Rotten Tomatoes" -> RatingSource.RT
            "Metacritic" -> RatingSource.METACRITIC
            "Trakt" -> RatingSource.TRAKT
            else -> null
        } ?: continue
        if (rating.value.isBlank()) continue
        add(RatingSlot(source, rating.value))
    }
}

/**
 * One inline ratings chip: the source's official brand icon at
 * ~14dp (the locked 18dp row size scaled to sit in the facts
 * line, natural proportion kept, wide marks capped the same way)
 * with its number BESIDE it. No written source name — the icon
 * is the name.
 */
@Composable
internal fun RatingChipInline(slot: RatingSlot) {
    val (fullW, fullH) = slot.source.iconSizeDp()
    val scale = 14f / RATING_ICON_HEIGHT_DP
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(slot.source.iconRes),
            contentDescription = null,
            modifier = Modifier.width((fullW * scale).dp).height((fullH * scale).dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            slot.value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            maxLines = 1,
            softWrap = false
        )
    }
}
