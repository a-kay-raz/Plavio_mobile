package com.aviostream.app.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aviostream.app.R

/** Nuvio's typeface: JetBrains Sans (SIL OFL), same three weights
 * Nuvio ships — Regular / SemiBold / Bold. */
private val JetBrainsSans = FontFamily(
    Font(R.font.jetbrains_sans_regular, FontWeight.Normal),
    Font(R.font.jetbrains_sans_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_sans_bold, FontWeight.Bold),
)

/** Nuvio's type scale (NuvioTokens.Type): body 14/15/16sp, titles
 * 18/22sp, headline 26sp, labels 11/12sp semibold. */
private val AvioTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 38.sp, lineHeight = 42.sp,
        fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 26.sp, lineHeight = 30.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 18.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 16.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 15.sp, lineHeight = 22.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 14.sp, lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 14.sp, lineHeight = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 12.sp, lineHeight = 15.sp,
        fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsSans, fontSize = 11.sp, lineHeight = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
)

/**
 * App theme. The palette (accent theme + AMOLED black) is provided through
 * [LocalPalette] so changing it in Settings re-skins the whole app.
 * Supports dark (default) and light modes via [isLight].
 */
@Composable
fun AvioTheme(
    palette: AppPalette = LocalPalette.current,
    isLight: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme = if (isLight) {
        // Normal mode — Sample A "Warm Ivory" (owner's pick 2026-10-24).
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF04120B),
            secondary = palette.accent2,
            onSecondary = Color(0xFF04120B),
            tertiary = palette.accent2,
            background = Color(0xFFF4EFE6),
            onBackground = Color(0xFF171A20),
            surface = Color(0xFFFFFBF2),
            onSurface = Color(0xFF171A20),
            surfaceVariant = Color(0xFFEAE3D3),
            onSurfaceVariant = Color(0xFF6F6A5E),
            outline = Color(0xFFD8D0BE),
            error = Color(0xFFB3261E)
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF062025),
            secondary = palette.accent2,
            onSecondary = Color(0xFF2A0A22),
            tertiary = palette.accent2,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.panel,
            onSurface = palette.textPrimary,
            surfaceVariant = Color(0xFF222222),
            onSurfaceVariant = palette.textSecondary,
            outline = palette.glassBorder,
            error = Color(0xFFE36A8A)
        )
    }
    // Provide the content colour at the theme root. Material3's
    // LocalContentColor defaults to BLACK and MaterialTheme does not
    // override it, so any bare Text without an explicit colour
    // rendered black — invisible on the dark canvas. Every
    // component that sets its own content colour (buttons, surfaces,
    // bars, dialogs) still wins locally; bare text now falls back
    // to the scheme's onBackground in both palettes.
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalContentColor provides scheme.onBackground
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AvioTypography,
            content = content
        )
    }
}
