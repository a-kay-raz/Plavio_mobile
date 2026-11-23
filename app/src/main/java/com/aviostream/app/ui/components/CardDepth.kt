package com.aviostream.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp

/**
 * W10 poster batch — scoped port of Nuvio's CardDepthEffect +
 * CardDepthStyleRepository (v1.17). A soft top-edge glow plus a top sheen
 * over poster and media cards, fully user-controlled: master switch, edge
 * strength, sheen strength, edge coverage, and per-surface apply-to
 * toggles. Pure visual effect — no backend, no new network call.
 *
 * Nuvio defaults: edge 28 (Subtle), sheen 10 (Soft), coverage 0 (Top),
 * master switch OFF, all five surfaces ON.
 */
const val DefaultCardDepthEdgeStrength = 28
const val DefaultCardDepthSheenStrength = 10
const val DefaultCardDepthEdgeCoverage = 0

enum class CardDepthSurface(val key: String) {
    Posters("posters"),
    ContinueWatching("continue_watching"),
    EpisodeCards("episodes"),
    Cast("cast"),
    Trailers("trailers");

    companion object {
        /** All surfaces enabled — the default state. */
        val ALL: Set<CardDepthSurface> = entries.toSet()

        /** Deserialize from stored key strings. Returns [ALL] if null/empty. */
        fun fromKeys(keys: Set<String>?): Set<CardDepthSurface> {
            if (keys.isNullOrEmpty()) return ALL
            return keys.mapNotNull { key -> entries.find { it.key == key } }.toSet()
        }

        /** Serialize to storable key strings. */
        fun toKeys(surfaces: Set<CardDepthSurface>): Set<String> =
            surfaces.map { it.key }.toSet()
    }
}

/**
 * Applies the card-depth effect for [surface] when the user enabled it
 * (master switch + this surface's toggle in Appearance → Card depth).
 * Reads [LocalPosterStyle], so a settings change takes effect on the next
 * theme tick with no call-site state.
 */
@Composable
fun Modifier.cardDepth(shape: Shape, surface: CardDepthSurface): Modifier {
    val style = LocalPosterStyle.current
    if (!style.isDepthEnabledFor(surface)) return this
    return cardDepthVisual(
        shape = shape,
        edgeStrength = style.cardDepthEdge.toFloat(),
        sheenStrength = style.cardDepthSheen.toFloat(),
        edgeCoverage = style.cardDepthCoverage.toFloat(),
    )
}

/**
 * The effect itself, verbatim from Nuvio's CardDepthEffect.cardDepthVisual:
 * a 1dp vertical-gradient border (bright at the top, fading down by the
 * coverage) plus a white sheen over the top 22% of the card. Values are
 * 0..100 percentages; the preview in Appearance settings drives this
 * directly with draft slider values.
 */
fun Modifier.cardDepthVisual(
    shape: Shape,
    edgeStrength: Float,
    sheenStrength: Float,
    edgeCoverage: Float = DefaultCardDepthEdgeCoverage.toFloat(),
): Modifier {
    val edgeTop = edgeStrength.coerceIn(0f, 100f) / 100f
    val sheen = sheenStrength.coerceIn(0f, 100f) / 100f
    val coverage = edgeCoverage.coerceIn(0f, 100f) / 100f

    val withEdge = if (edgeTop > 0f) {
        border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeTop),
                    Color.White.copy(alpha = edgeTop * (0.33f + 0.67f * coverage)),
                    Color.White.copy(alpha = edgeTop * coverage),
                ),
            ),
            shape = shape,
        )
    } else {
        this
    }

    return if (sheen > 0f) {
        withEdge.drawWithCache {
            val sheenHeight = size.height * 0.22f
            if (sheenHeight <= 0f) {
                return@drawWithCache onDrawWithContent { drawContent() }
            }
            val shapePath = shape.createOutline(size, layoutDirection, this).toPath()
            val sheenBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = sheen),
                    Color.Transparent,
                ),
                startY = 0f,
                endY = sheenHeight,
            )
            val sheenSize = Size(size.width, sheenHeight)
            onDrawWithContent {
                drawContent()
                clipPath(shapePath) {
                    drawRect(brush = sheenBrush, size = sheenSize)
                }
            }
        }
    } else {
        withEdge
    }
}

private fun Outline.toPath(): Path = when (this) {
    is Outline.Rectangle -> Path().apply { addRect(rect) }
    is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
    is Outline.Generic -> path
}
