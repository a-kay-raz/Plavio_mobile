package com.aviostream.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.SettingsStore

/**
 * Step 70 — poster customization (port of Nuvio's
 * PosterCustomizationSettingsPage: width / corner radius / hide-labels).
 *
 * W10 extends it with the rest of Nuvio's page: catalog landscape mode
 * (MetaPreview DOES carry backdrop art, so landscape cards render from
 * `background` with a poster fallback), the custom poster URL pattern with
 * per-surface apply-to, and the card depth effect (edge glow / sheen /
 * coverage + per-surface apply-to). All of it lives in this one style
 * object so every poster card in the app picks it up with no call-site
 * state.
 *
 * The style is provided once at the app root (MainActivity) from the current
 * profile's SettingsStore, so every poster card in the app — PosterCard,
 * NextUpCard, ReleaseRadarCard — picks it up with no call-site changes.
 * The provider value is rebuilt whenever the appearance settings change
 * (MainActivity's themeTick), so a change in the Appearance screen takes
 * effect immediately, without a restart.
 */
data class PosterStyle(
    val cardWidth: Dp = 128.dp,
    val cornerRadius: Dp = 12.dp,
    val hideLabels: Boolean = false,
    /** W10: catalog landscape mode — catalog rows render 1.77 landscape cards. */
    val landscapeMode: Boolean = false,
    /** W10: always show the title logo on landscape cards instead of a custom landscape poster. */
    val landscapeClearlogo: Boolean = false,
    /** W10: custom poster URL pattern with {placeholders}; blank = off. */
    val customPosterPattern: String = "",
    /** W10: surfaces the custom poster pattern applies to. */
    val customPosterScreens: Set<CustomPosterScreen> = CustomPosterScreen.ALL,
    /** W10: card depth effect master switch. */
    val cardDepthEnabled: Boolean = false,
    /** W10: card depth edge-glow strength, 0..100 (Nuvio default 28). */
    val cardDepthEdge: Int = DefaultCardDepthEdgeStrength,
    /** W10: card depth sheen strength, 0..100 (Nuvio default 10). */
    val cardDepthSheen: Int = DefaultCardDepthSheenStrength,
    /** W10: card depth edge coverage, 0..100 (Nuvio default 0). */
    val cardDepthCoverage: Int = DefaultCardDepthEdgeCoverage,
    /** W10: surfaces the card depth effect applies to. */
    val cardDepthSurfaces: Set<CardDepthSurface> = CardDepthSurface.ALL,
) {
    /** Poster height keeps Nuvio's poster ratio (0.675 width/height; 126x189 at the default width). */
    val cardHeight: Dp get() = cardWidth / 0.675f

    /**
     * Landscape card width: Nuvio's PosterLandscapeWidthScale (180/110).
     * Parent constraints still clamp it (grid cells, 160dp saved-card cap),
     * so landscape cards degrade gracefully where the full width does not fit.
     */
    val landscapeWidth: Dp get() = cardWidth * (180f / 110f)

    /** Landscape card height keeps Nuvio's 1.77 landscape aspect. */
    val landscapeHeight: Dp get() = landscapeWidth / 1.77f

    /** The depth effect is on for [surface] only with the master switch AND its own toggle. */
    fun isDepthEnabledFor(surface: CardDepthSurface): Boolean =
        cardDepthEnabled && surface in cardDepthSurfaces

    /** The custom-poster pattern for [surface], or "" when blank or the surface is disabled. */
    fun customPosterPatternFor(surface: CustomPosterScreen): String =
        if (customPosterPattern.isNotBlank() && surface in customPosterScreens) customPosterPattern else ""
}

val LocalPosterStyle = staticCompositionLocalOf { PosterStyle() }

fun posterStyleFrom(settings: SettingsStore): PosterStyle = PosterStyle(
    cardWidth = settings.posterCardWidthDp.dp,
    cornerRadius = settings.posterCornerRadiusDp.dp,
    hideLabels = settings.posterHideLabels,
    landscapeMode = settings.posterCatalogLandscapeMode,
    landscapeClearlogo = settings.posterLandscapeClearlogo,
    customPosterPattern = settings.customPosterPattern,
    customPosterScreens = CustomPosterScreen.fromKeys(settings.customPosterScreens),
    cardDepthEnabled = settings.cardDepthEnabled,
    cardDepthEdge = settings.cardDepthEdgeStrength,
    cardDepthSheen = settings.cardDepthSheenStrength,
    cardDepthCoverage = settings.cardDepthEdgeCoverage,
    cardDepthSurfaces = CardDepthSurface.fromKeys(settings.cardDepthSurfaces),
)
