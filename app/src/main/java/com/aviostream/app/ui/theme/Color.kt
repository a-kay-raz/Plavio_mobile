package com.aviostream.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// Smoky slate, rather than near-black, is the default Liquid Glass canvas.
// AMOLED remains a separate opt-in palette in [paletteFor].
val BgDeep = Color(0xFF202630)
val BgPanel = Color(0xFF303A47)
// Raw values of the DARK design (the original constants). The public
// names below read the ACTIVE palette, so Normal (light) mode re-skins
// every screen that uses them; in Dark mode they resolve to exactly
// these values.
private val DarkTextPrimary = Color(0xFFF6F7FA)
private val DarkTextSecondary = Color(0xFFC5CDD7)
private val DarkGlassFill = Color.White.copy(alpha = 0.10f)
private val DarkGlassBorder = Color.White.copy(alpha = 0.18f)

val TextPrimary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textPrimary
val TextSecondary: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.textSecondary
val GlassWhite: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.glassFill
val GlassBorder: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.glassBorder

/** Opaque neutral fills used by the shared liquid-glass surface treatment. */
fun liquidGlassTopSurface(background: Color): Color = lerp(background, Color.White, 0.10f)
fun liquidGlassBottomSurface(background: Color): Color = lerp(background, Color.White, 0.06f)

/**
 * Accent color themes. GOLD is the default (the stored default in
 * SettingsStore). SIGNATURE teal #14B8A6 is v0.14.30's signature
 * accent (the mint family of the Live dot and the WATCHED pill).
 * WEAVE (owner's order 2026-10-26) carries the logo's own gradient
 * — violet #7C3AED → magenta #DB2777 → pink #F9738C — and replaced
 * the old accent in the picker; an install that still has the old
 * accent name stored lands on GOLD through [byName]'s fallback.
 * The others re-skin every accent in the app (buttons, toggles,
 * progress, glows, selected states) through [LocalPalette].
 */
/**
 * Step 70: parse a user-typed accent hex ("#RRGGBB" or "#AARRGGBB", "#" optional).
 * Returns null for anything that is not a valid 6- or 8-digit hex colour.
 */
fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    if (clean.length != 6 && clean.length != 8) return null
    val value = clean.toLongOrNull(16) ?: return null
    return if (clean.length == 6) Color(0xFF000000L or value) else Color(value)
}

/** Step 70: true when a colour is light enough that dark text reads on it. */
fun isLightColor(color: Color): Boolean {
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance > 0.6f
}

enum class AccentTheme(val label: String, val primary: Color, val secondary: Color) {
    SIGNATURE("Signature", Color(0xFF14B8A6), Color(0xFF0F766E)),
    WHITE("White", Color.White, Color(0xFFD9D9DE)),
    SLATE("Slate", Color(0xFF64748B), Color(0xFF475569)),
    WEAVE("Weave", Color(0xFF7C3AED), Color(0xFFDB2777)),
    CRIMSON("Crimson", Color(0xFFF43F5E), Color(0xFFFB923C)),
    OCEAN("Ocean", Color(0xFF38BDF8), Color(0xFF6366F1)),
    VIOLET("Violet", Color(0xFFA78BFA), Color(0xFFF0ABFC)),
    EMERALD("Emerald", Color(0xFF34D399), Color(0xFF22D3EE)),
    AMBER("Amber", Color(0xFFFBBF24), Color(0xFFF97316)),
    ROSE("Rose", Color(0xFFEC407A), Color(0xFFF48FB1)),
    /** Nuvio GOLD theme: primary is Nuvio's gold secondary 0xFFE8A91C,
     * secondary is its nativeAccentHex #FFD45C (light gold highlight).
     * Deeper gold than AMBER, and its secondary is pale gold, not
     * AMBER's orange — the two stay visually distinct in the picker. */
    GOLD("Gold", Color(0xFFE8A91C), Color(0xFFFFD45C));

    /** Picker swatch: the Weave wears the logo gradient; every other
     * accent is a solid disc (the picker's approved look). */
    val swatchBrush: Brush
        get() = when (this) {
            WEAVE -> Brush.linearGradient(
                listOf(primary, secondary, Color(0xFFF9738C))
            )
            else -> SolidColor(primary)
        }

    companion object {
        fun byName(name: String?): AccentTheme =
            values().firstOrNull { it.name == name } ?: GOLD
    }
}

/**
 * Resolved colors for the current theme. Provided at the root via [LocalPalette];
 * every screen reads its accent/background from here so a theme change
 * re-skins the whole app without touching individual components.
 */
data class AppPalette(
    val accent: Color,
    val accent2: Color,
    val background: Color,
    val panel: Color,
    val amoled: Boolean,
    /** True in Normal (light) mode; the dark design is the default. */
    val light: Boolean = false,
    val textPrimary: Color = DarkTextPrimary,
    val textSecondary: Color = DarkTextSecondary,
    val glassFill: Color = DarkGlassFill,
    val glassBorder: Color = DarkGlassBorder,
    /** The accent read as TEXT on surfaces; deepened in Normal mode. */
    val accentText: Color = accent,
    /** The six-theme identity this palette was built from. */
    val appTheme: AppTheme = AppTheme.ONYX,
    val accentTheme: AccentTheme = AccentTheme.GOLD,
    /**
     * Frosted glass on/off. Weak devices get the solid version of the
     * chosen theme instead (same colours, flat surfaces, no glows).
     */
    val frosted: Boolean = true,
    /** Multiplier on the ambient glow alphas in GlowBackground. */
    val glowAlpha: Float = 1f
) {
    val gradient: Brush
        get() = Brush.horizontalGradient(listOf(accent, accent2))

    /**
     * The ONE derivation for selected chip/card containers. The old
     * derivation — accent at ~22-25% alpha over the near-black ground
     * — desaturated every accent into mud (Amber read as dull
     * olive-brown). [accentTint] is a SOLID colour built from the
     * accent's own hue and saturation at a rich fixed lightness,
     * so a selected Amber reads gold, a selected Ocean reads
     * blue, and White stays a neutral grey.
     */
    val accentTint: Color
        get() = richAccentTint(accent, light)
}

/**
 * Builds a saturation-keeping tint of [accent]: same hue, same
 * saturation, lightness moved to a rich container level (0.32 on
 * dark palettes, 0.84 on light ones). Pure colour math.
 */
fun richAccentTint(accent: Color, light: Boolean): Color {
    val r = accent.red
    val g = accent.green
    val b = accent.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness0 = (max + min) / 2f
    val saturation = when {
        delta == 0f -> 0f
        lightness0 <= 0f || lightness0 >= 1f -> 0f
        else -> delta / (1f - kotlin.math.abs(2f * lightness0 - 1f))
    }
    val hue = when {
        delta == 0f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    val lightness = if (light) 0.84f else 0.32f
    val chroma = (1f - kotlin.math.abs(2f * lightness - 1f)) * saturation
    val x = chroma * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
    val m = lightness - chroma / 2f
    val (rr, gg, bb) = when {
        hue < 60f -> Triple(chroma, x, 0f)
        hue < 120f -> Triple(x, chroma, 0f)
        hue < 180f -> Triple(0f, chroma, x)
        hue < 240f -> Triple(0f, x, chroma)
        hue < 300f -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    return Color(
        red = (rr + m).coerceIn(0f, 1f),
        green = (gg + m).coerceIn(0f, 1f),
        blue = (bb + m).coerceIn(0f, 1f)
    )
}

val LocalPalette = compositionLocalOf {
    AppPalette(
        accent = AccentTheme.GOLD.primary,
        accent2 = AccentTheme.GOLD.secondary,
        background = BgDeep,
        panel = BgPanel,
        amoled = false
    )
}

/**
 * The six appearance themes (owner's pick 2026-10-25).
 * ONYX is the default for fresh installs — the owner's reference-app
 * look adopted app-wide 2026-10-25: pure black canvas, FLAT charcoal
 * cards (no glass, no glow), monochrome white accents; mint survives
 * only in the logo and splash. (Enum renamed from MIDNIGHT the same
 * day; stored "MIDNIGHT" strings map to ONYX in [AppTheme.byName].)
 * Existing installs migrate from the old dark/light + AMOLED settings
 * (see SettingsStore.appTheme).
 */
enum class AppTheme(val label: String, val blurb: String, val light: Boolean) {
    ONYX("Onyx", "Pure black mono — flat charcoal cards, white accents", false),
    WARM_IVORY("Warm Ivory", "Your current Normal, now frosted", true),
    AURORA_FROST("Aurora Frost", "The vivid one — strongest ambient colour", false),
    TRUE_BLACK("True Black", "AMOLED black — deepest blacks, best battery", false),
    TWILIGHT("Twilight", "Deep indigo night, violet-tinted glass", false),
    PEARL("Pearl", "Cool clean light — white glass over soft grey", true);

    /** Dark counterpart used to pin video surfaces (player, heroes). */
    fun darkVariant(): AppTheme = if (light) ONYX else this

    companion object {
        /**
         * Resolves a stored theme name; unknown names (incl. the old
         * "MIDNIGHT" string from before the 2026-10-25 rename) fall back
         * to ONYX, the default. Was a stub returning ONYX unconditionally,
         * which froze the theme at the migration default no matter what
         * SettingsStore.appTheme held — fixed 2026-11-23.
         */
        fun byName(name: String?): AppTheme =
            values().firstOrNull { it.name == name } ?: ONYX
    }
}

/** Builds the palette for one of the six themes. */
fun paletteForTheme(theme: AppTheme, accent: AccentTheme, frosted: Boolean = true): AppPalette {
    val base = when (theme) {
        AppTheme.ONYX -> AppPalette(
            // Onyx: the permanent theme. The chosen accent applies.
            // Two-black system — the page ground is PURE black and
            // every raised surface is the ONE panel black (#1C1C1F).
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFF000000),
            panel = Color(0xFF1C1C1F),
            amoled = false,
            textPrimary = Color.White,
            textSecondary = Color(0xFF9A9AA0),
            glassFill = Color.White.copy(alpha = 0.07f),
            glassBorder = Color.White.copy(alpha = 0.09f),
            accentText = if (accent.primary == Color.White) Color.Black else Color.White,
            glowAlpha = 0f
        )
        AppTheme.WARM_IVORY -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF4EFE6),
            panel = Color(0xFFFFFBF2),
            amoled = false,
            light = true,
            textPrimary = Color(0xFF171A20),
            textSecondary = Color(0xFF6F6A5E),
            glassFill = Color.Black.copy(alpha = 0.05f),
            glassBorder = Color(0xFF171A20).copy(alpha = 0.14f),
            accentText = lerp(accent.primary, Color.Black, 0.42f),
            glowAlpha = 1.1f
        )
        AppTheme.AURORA_FROST -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFF060B16),
            panel = Color(0xFF0E1626),
            amoled = false,
            textPrimary = Color(0xFFF2F6FC),
            textSecondary = Color(0xFFB9C8DE),
            glassFill = Color.White.copy(alpha = 0.14f),
            glassBorder = Color.White.copy(alpha = 0.24f),
            glowAlpha = 1.7f
        )
        AppTheme.TRUE_BLACK -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color.Black,
            // Two-black system: True Black shares the one panel
            // black with Onyx.
            panel = Color(0xFF1C1C1F),
            amoled = true,
            textPrimary = Color(0xFFF5F5F5),
            textSecondary = Color(0xFFB9B9C0),
            glowAlpha = 0.55f
        )
        AppTheme.TWILIGHT -> AppPalette(
            accent = Color(0xFF9D8BFF),
            accent2 = Color(0xFF38BDF8),
            background = Color(0xFF0C0A1D),
            panel = Color(0xFF171233),
            amoled = false,
            textPrimary = Color(0xFFEFEAFF),
            textSecondary = Color(0xFFC4BCE8),
            glassFill = Color.White.copy(alpha = 0.11f),
            glassBorder = Color.White.copy(alpha = 0.20f),
            accentText = Color(0xFF9D8BFF),
            glowAlpha = 1.2f
        )
        AppTheme.PEARL -> AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF2F4F7),
            panel = Color.White,
            amoled = false,
            light = true,
            textPrimary = Color(0xFF10131A),
            textSecondary = Color(0xFF4E586A),
            glassFill = Color.Black.copy(alpha = 0.04f),
            glassBorder = Color(0xFF10131A).copy(alpha = 0.12f),
            accentText = lerp(accent.primary, Color.Black, 0.42f),
            glowAlpha = 1.0f
        )
    }
    val themed0 = base.copy(appTheme = theme, accentTheme = accent)
    // Onyx is flat BY DESIGN (not a low-end fallback): no frost, no glow.
    val themed = if (theme == AppTheme.ONYX) {
        themed0.copy(frosted = false, glowAlpha = 0f)
    } else {
        themed0
    }
    // Solid fallback for low-end devices: same colours, flat surfaces.
    return if (frosted) themed else themed.copy(frosted = false, glowAlpha = 0f)
}

/** Builds the palette for an accent theme + AMOLED-black toggle. */
fun paletteFor(accent: AccentTheme, amoled: Boolean, light: Boolean = false): AppPalette =
    if (light) {
        // Normal mode — owner's pick 2026-10-24: Sample A "Warm Ivory".
        AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = Color(0xFFF4EFE6),
            panel = Color(0xFFFFFBF2),
            amoled = false,
            light = true,
            textPrimary = Color(0xFF171A20),
            textSecondary = Color(0xFF6F6A5E),
            glassFill = Color.Black.copy(alpha = 0.05f),
            glassBorder = Color(0xFF171A20).copy(alpha = 0.14f),
            accentText = lerp(accent.primary, Color.Black, 0.42f)
        )
    } else {
        AppPalette(
            accent = accent.primary,
            accent2 = accent.secondary,
            background = if (amoled) Color.Black else BgDeep,
            panel = if (amoled) Color(0xFF1C1C1F) else BgPanel,
            amoled = amoled
        )
    }

/**
 * The dark variant of this palette (same accent/AMOLED choices). Video
 * surfaces — the player, trailer players — are pinned to it in BOTH
 * modes: like the home/detail heroes, they stay cinematic.
 *
 * The video surfaces are ALSO pinned to the flat (non-frosted) glass:
 * on a non-default theme the player's liquid-glass controls sample
 * the backdrop — which re-records the whole screen, video TextureView
 * included, into a GraphicsLayer every frame — and the player never
 * opens (the app drops to the launcher; user 2026-11-24, phone-proven:
 * the default ONYX theme works, non-default themes break the player).
 * ONYX is the only theme with frosted=false / glowAlpha=0, so force
 * those here for every theme: the video surfaces then render exactly
 * as they do on ONYX, whatever the app theme is. Only DarkVideoSurface
 * (player + trailer players) uses asDark(), so no other screen — and
 * no theme's intended look elsewhere — is affected. The light branch
 * already lands on ONYX (darkVariant) whose paletteForTheme forces
 * frosted=false / glowAlpha=0; the dark branch is the one that used
 * to keep the theme's frosted glass.
 */
fun AppPalette.asDark(): AppPalette =
    if (!light) this.copy(frosted = false, glowAlpha = 0f)
    else paletteForTheme(appTheme.darkVariant(), accentTheme, frosted)
