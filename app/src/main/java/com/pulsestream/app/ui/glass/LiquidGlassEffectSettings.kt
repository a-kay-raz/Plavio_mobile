package com.pulsestream.app.ui.glass

import kotlin.math.roundToInt

/*
 * Nuvio's glass numbers, ported from Nuvio v1.17
 * (core/ui/glass/LiquidGlassEffectSettings.kt).
 *
 * floatingBar = the dock / floating bar glass.
 * selectionLens = the selected-tab lens that slides under the active tab.
 * Values started as Nuvio's exact defaults; the floatingBar numbers
 * were strengthened for a more glassy look (more blur, more refraction,
 * thinner tint, chromatic + depth on).
 */

data class LiquidGlassEffectSettings(
    val cornerRadiusPercent: Float,
    val blurRadiusDp: Float,
    val refractionHeightDp: Float,
    val refractionAmountDp: Float,
    val chromaticAberration: Boolean,
    val depthEffect: Boolean,
    val tintOpacity: Float,
    val affectTabIcons: Boolean = false,
    val affectFloatingBar: Boolean = false,
) {
    fun sanitized(
        defaults: LiquidGlassEffectSettings,
        limits: LiquidGlassEffectLimits,
    ): LiquidGlassEffectSettings = copy(
        cornerRadiusPercent = safeRange(cornerRadiusPercent, 50f, defaults.cornerRadiusPercent),
        blurRadiusDp = safeRange(blurRadiusDp, limits.maxBlurRadiusDp, defaults.blurRadiusDp),
        refractionHeightDp = safeRange(
            refractionHeightDp,
            limits.maxRefractionHeightDp,
            defaults.refractionHeightDp,
        ),
        refractionAmountDp = safeRange(
            refractionAmountDp,
            limits.maxRefractionAmountDp,
            defaults.refractionAmountDp,
        ),
        tintOpacity = safeRange(tintOpacity, 1f, defaults.tintOpacity),
    )

    private fun safeRange(value: Float, maximum: Float, fallback: Float): Float =
        (value.takeIf { it.isFinite() } ?: fallback).coerceIn(0f, maximum)
}

data class LiquidGlassEffectLimits(
    val maxBlurRadiusDp: Float,
    val maxRefractionHeightDp: Float,
    val maxRefractionAmountDp: Float,
)

/**
 * These optional sources exist to add refraction targets to the selection lens, not to extend its
 * blur. If either refraction dimension is zero, Kyant's lens effect is a no-op and the sources
 * should not replace normal foreground content.
 */
internal fun LiquidGlassEffectSettings.hasActiveRefraction(): Boolean =
    refractionHeightDp > 0f && refractionAmountDp > 0f

/** Capture tab content only when requested and when refraction is active. */
internal fun LiquidGlassEffectSettings.shouldCaptureTabIcons(): Boolean =
    affectTabIcons && hasActiveRefraction()

/** Capture the floating-bar surface only when requested and when refraction is active. */
internal fun LiquidGlassEffectSettings.shouldCaptureFloatingBar(): Boolean =
    affectFloatingBar && hasActiveRefraction()

object LiquidGlassEffectDefaults {
    // Avio's "more glassy" values — stronger than Nuvio's exact
    // defaults (which were blur 10dp, refraction 20/20, tint 0.4,
    // chromatic + depth off). More blur, more lens bend, thinner tint,
    // chromatic fringing + depth on: the glass reads clearly.
    val floatingBar = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 28f,
        refractionHeightDp = 32f,
        refractionAmountDp = 32f,
        chromaticAberration = true,
        depthEffect = true,
        tintOpacity = 0.10f,
    )
    val selectionLens = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 0f,
        refractionHeightDp = 20f,
        refractionAmountDp = 28f,
        chromaticAberration = true,
        depthEffect = true,
        tintOpacity = 0f,
        affectTabIcons = true,
        affectFloatingBar = true,
    )

    val floatingBarLimits = LiquidGlassEffectLimits(
        maxBlurRadiusDp = 32f,
        maxRefractionHeightDp = 48f,
        maxRefractionAmountDp = 48f,
    )
    val selectionLensLimits = LiquidGlassEffectLimits(
        maxBlurRadiusDp = 24f,
        maxRefractionHeightDp = 32f,
        maxRefractionAmountDp = 32f,
    )
}

/**
 * Normalize glass corner percentages to the integer value used by RoundedCornerShape.
 * Clamp infinities to the supported range and map NaN to the fully rounded default.
 */
internal fun effectiveLiquidGlassCornerRadiusPercent(value: Float): Int =
    (value.takeIf { !it.isNaN() } ?: 50f).coerceIn(0f, 50f).roundToInt()
