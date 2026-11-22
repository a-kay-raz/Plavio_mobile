package com.pulsestream.app.ui.glass

import kotlin.math.roundToInt

/*
 * Nuvio's glass numbers, ported from Nuvio v1.17
 * (core/ui/glass/LiquidGlassEffectSettings.kt).
 *
 * floatingBar = the dock / floating bar glass.
 * selectionLens = the selected-tab lens that slides under the active tab.
 * These are Nuvio's exact defaults — blur, refraction, tint.
 */

internal data class LiquidGlassEffectSettings(
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

internal data class LiquidGlassEffectLimits(
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

internal object LiquidGlassEffectDefaults {
    val floatingBar = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 10f,
        refractionHeightDp = 20f,
        refractionAmountDp = 20f,
        chromaticAberration = false,
        depthEffect = false,
        tintOpacity = 0.4f,
    )
    val selectionLens = LiquidGlassEffectSettings(
        cornerRadiusPercent = 50f,
        blurRadiusDp = 0f,
        refractionHeightDp = 12f,
        refractionAmountDp = 20f,
        chromaticAberration = false,
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
