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
    /** Poster height keeps Plavio's 2:3 card ratio. */
    val cardHeight: Dp get() = cardWidth * 180f / 128f
}

val LocalPosterStyle = staticCompositionLocalOf { PosterStyle() }

fun posterStyleFrom(settings: SettingsStore): PosterStyle = PosterStyle(
    cardWidth = settings.posterCardWidthDp.dp,
    cornerRadius = settings.posterCornerRadiusDp.dp,
    hideLabels = settings.posterHideLabels,
)
