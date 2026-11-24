package com.aviostream.app.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Appearance extras (v0.14.30 port, feature #17), provided once at
 * the app root from SettingsStore and read by the shared components
 * they govern. Every default reproduces the shipped behaviour, so
 * the app looks identical until the user changes an option in
 * Settings → Appearance.
 *
 * Fork adaptations vs upstream 0.14.30:
 *  - NO poster locals here: the fork already governs poster size
 *    and corner radius through PosterStyle (LocalPosterStyle,
 *    Settings → Layout — width in dp + corner radius in dp,
 *    finer-grained than upstream's Small/Medium/Large scale), so
 *    LocalPosterScale / LocalPosterCorner would duplicate it.
 *  - NO text size: upstream's rides LocalDensity's fontScale; the
 *    shortlist does not include it.
 *  - Reduced motion rides the fork's MotionPreferences (the single
 *    reduced-motion store — Advanced settings already toggles it),
 *    not a second SettingsStore.animations key, so the two can
 *    never disagree.
 *  - Glass-card Rounded is 24.dp (the fork's shipped Nuvio card
 *    radius), not upstream's 28.dp.
 */

/** Glass-card corner radius: 24.dp Rounded / 8.dp Sharp. */
val LocalCardCorner = staticCompositionLocalOf { 24.dp }

/** Row/list padding scale: 0.78 Compact / 1 Comfortable. */
val LocalSpacingScale = staticCompositionLocalOf { 1f }

/** Reduced animations: true damps crossfades/motion. */
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Glass-card corner radius for a SettingsStore.cardCorners value. */
fun cardCornerFor(setting: String): Dp =
    if (setting == "Sharp") 8.dp else 24.dp
