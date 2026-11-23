package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.glass.ambientLiquidGlass

// Player liquid glass — Nuvio's player recipe (PlayerLiquidGlass.kt):
// clear glass with a light blur over the live video. The backdrop
// opacity is 0.55 so the live video shows through and the glass never
// turns dark even if a captured frame comes back black. The fallback
// is a translucent white (not the dark panel), and a hairline rim
// sells the glass edge. This is what the player CONTROLS use (pills,
// buttons, time chip); the panels keep their own stronger backing.

/** Light blur so the video stays readable through the glass. */
internal val PlayerGlassBlur: Dp = 6.dp

/** Near-clear: a faint white sheen instead of a dark fill. */
internal val PlayerGlassSurfaceTint = Color.White.copy(alpha = 0.05f)

/**
 * How strongly the blurred video copy is drawn inside the glass. Below 1
 * the live video shows through, so the glass stays translucent (and never
 * turns dark if a captured frame comes back black).
 */
internal const val PlayerGlassBackdropOpacity = 0.55f

/** Used when there is no live backdrop (Android < 12, protected/DRM video, glass turned off). */
internal val PlayerGlassFallback = Color.White.copy(alpha = 0.08f)

/** Hairline rim that sells the glass edge on top of Kyant's highlight. */
internal val PlayerGlassRim = Color.White.copy(alpha = 0.22f)

/**
 * The one glass recipe every player control uses: Kyant backdrop with
 * vibrancy, a light blur, lens refraction and edge highlight. Falls back
 * to a translucent fill when the backdrop isn't available.
 */
@Composable
internal fun Modifier.playerLiquidGlass(
    shape: Shape,
    blurRadius: Dp = PlayerGlassBlur,
    refractionHeight: Dp = 8.dp,
    refractionAmount: Dp = 12.dp,
    tint: Color = PlayerGlassSurfaceTint,
    fallback: Color = PlayerGlassFallback,
    backdropOpacity: Float = PlayerGlassBackdropOpacity,
    rim: Boolean = true,
): Modifier {
    val glass = clip(shape).ambientLiquidGlass(
        shape = shape,
        tint = tint,
        fallback = fallback,
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        withVibrancy = true,
        withHighlight = true,
        withShadow = false,
        backdropOpacity = backdropOpacity,
    )
    return if (rim) glass.border(0.75.dp, PlayerGlassRim, shape) else glass
}
