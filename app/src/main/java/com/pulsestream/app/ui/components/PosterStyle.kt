package com.pulsestream.app.ui.components

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore

/**
 * Step 70 — poster customization (scoped port of Nuvio's
 * PosterCustomizationSettingsPage: width / corner radius / hide-labels only;
 * landscape mode and clearlogo need catalog art Plavio does not fetch).
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
) {
    /** Poster height keeps Nuvio's poster ratio (0.675 width/height; 126x189 at the default width). */
    val cardHeight: Dp get() = cardWidth / 0.675f
}

val LocalPosterStyle = staticCompositionLocalOf { PosterStyle() }

fun posterStyleFrom(settings: SettingsStore): PosterStyle = PosterStyle(
    cardWidth = settings.posterCardWidthDp.dp,
    cornerRadius = settings.posterCornerRadiusDp.dp,
    hideLabels = settings.posterHideLabels,
)
