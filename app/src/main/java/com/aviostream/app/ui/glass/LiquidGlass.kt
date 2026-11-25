package com.aviostream.app.ui.glass

import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.opacity
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.aviostream.app.ui.theme.LocalPalette

/*
 * Nuvio-style liquid glass, ported from Nuvio v1.17
 * (core/ui/glass/LiquidGlass.kt + LiquidGlassEffectSettings.kt).
 *
 * Nuvio uses Kyant's Backdrop library (io.github.kyant0:backdrop).
 * This port uses backdrop 1.0.6 — Kotlin 2.3.10 matches this project
 * and its drawBackdrop / lens / blur / vibrancy API is the same as
 * Nuvio's 2.0.1 usage. 2.0.1 needs Compose 1.12 / Kotlin 2.4.10,
 * which this project (Compose BOM 2024.10.01, Kotlin 2.3.10) does not
 * have — do not upgrade the library without upgrading the BOM.
 *
 * Architecture (same as Nuvio):
 *  - One [AvioBackdrop] records the root content into a GraphicsLayer.
 *    MainActivity provides it via [LocalAvioGlassBackdrop] and attaches
 *    [avioBackdropSource] to the content box.
 *  - Glass surfaces ([liquidGlassSurface] / [ambientLiquidGlass] /
 *    [LiquidGlassFrostedBar]) sample that backdrop with Kyant's
 *    drawBackdrop: vibrancy + blur + lens refraction + highlight +
 *    shadow + tint.
 *  - Below Android 12 (API 31, no RenderEffect) or when the backdrop
 *    is not ready, surfaces fall back to a flat translucent fill —
 *    the same static glass the app already uses elsewhere.
 */

/** RenderEffect blur (the glass blur) needs Android 12+. */
fun isLiquidGlassAvailable(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * P1: minimum gap between backdrop re-records. The backdrop is a
 * blurred, low-detail layer — it does not need a fresh full-screen
 * record on every draw. A hero trailer redraws every video frame and
 * a scroll fling redraws constantly; without this the whole screen is
 * re-recorded at video frame rate. ~15fps is plenty for glass: the
 * glass keeps the last recorded frame between re-records, so the
 * look (blur / strength) is unchanged. Main thread only.
 */
private const val BACKDROP_RECORD_THROTTLE_MS = 66L

/** RuntimeShader refraction (the lens) needs Android 13+. The lens
 * effect itself is also a no-op below 33 inside the library. */
fun isLiquidRefractionSupported(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

fun isLiquidGlassSupported(): Boolean = isLiquidGlassAvailable()

interface LiquidGlassBackdropHost {
    val isReady: Boolean
    fun acquire() {}
    fun release() {}
}

val LocalAvioGlassBackdrop = staticCompositionLocalOf<Backdrop?> { null }

@Stable
class AvioBackdrop internal constructor(
    internal val graphicsLayer: GraphicsLayer,
) : Backdrop, LiquidGlassBackdropHost {

    /**
     * When true, the source node draws the content to screen but does not
     * re-record it into the backdrop layer. Set by the player while the
     * video is pinch-zoomed (videoZoom != 1f): recording the scaled 4K
     * TextureView is what force-stops the app past ~1.2x (native graphics
     * crash, no Java trace). The glass keeps showing the last recorded
     * frame until the zoom is back to 1x and recording resumes.
     * Main thread only.
     */
    var suspendRecording: Boolean = false

    /**
     * Last backdrop re-record, monotonic ms (P1 throttle). Only
     * updated when a record actually happens, so a record can never
     * be starved by draws arriving faster than the throttle — the
     * first draw after the gap always records. Main thread only.
     */
    internal var lastRecordMs: Long = 0L

    override val isCoordinatesDependent: Boolean = true

    internal var sourceCoordinates: LayoutCoordinates? by mutableStateOf(null)

    override val isReady: Boolean
        get() = sourceCoordinates != null

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val target = coordinates ?: return
        val source = sourceCoordinates ?: return
        if (!target.isAttached || !source.isAttached) return
        val offset = offsetBetween(source, target)
        translate(-offset.x, -offset.y) {
            drawLayer(graphicsLayer)
        }
    }

    private fun offsetBetween(source: LayoutCoordinates, target: LayoutCoordinates): Offset {
        if (source.findRootCoordinates() === target.findRootCoordinates()) {
            try {
                return source.localPositionOf(target, Offset.Zero)
            } catch (_: Exception) {
            }
        }
        val targetOnScreen = target.positionOnScreen()
        val sourceOnScreen = source.positionOnScreen()
        return if (targetOnScreen.isSpecified && sourceOnScreen.isSpecified) {
            targetOnScreen - sourceOnScreen
        } else {
            target.positionInWindow() - source.positionInWindow()
        }
    }
}

@Composable
fun rememberAvioBackdrop(): AvioBackdrop {
    val graphicsLayer = rememberGraphicsLayer()
    return remember(graphicsLayer) { AvioBackdrop(graphicsLayer) }
}

fun Modifier.avioBackdropSource(backdrop: AvioBackdrop): Modifier =
    this then AvioBackdropSourceElement(backdrop)

private class AvioBackdropSourceElement(
    val backdrop: AvioBackdrop,
) : ModifierNodeElement<AvioBackdropSourceNode>() {

    override fun create(): AvioBackdropSourceNode = AvioBackdropSourceNode(backdrop)

    override fun update(node: AvioBackdropSourceNode) {
        if (node.backdrop != backdrop) {
            node.backdrop.sourceCoordinates = null
            node.backdrop = backdrop
        }
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "avioBackdropSource"
        properties["backdrop"] = backdrop
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AvioBackdropSourceElement) return false
        return backdrop == other.backdrop
    }

    override fun hashCode(): Int = backdrop.hashCode()
}

private class AvioBackdropSourceNode(
    var backdrop: AvioBackdrop,
) : DrawModifierNode, GlobalPositionAwareModifierNode, Modifier.Node() {

    override fun ContentDrawScope.draw() {
        drawContent()
        // Frozen while the player video is zoomed (see suspendRecording):
        // drawing the scaled 4K TextureView into the recording layer
        // force-stops the app past ~1.2x. The glass keeps the last frame.
        if (backdrop.suspendRecording) return
        // P1: throttle the full-screen re-record to ~15fps. Every video
        // frame and every scroll step invalidates this draw; recording
        // the whole screen each time is the Home/Detail scroll jank and
        // battery cost. The glass keeps the last recorded frame between
        // re-records — blurred glass hides the staleness, look unchanged.
        val nowMs = SystemClock.uptimeMillis()
        if (nowMs - backdrop.lastRecordMs < BACKDROP_RECORD_THROTTLE_MS) return
        backdrop.lastRecordMs = nowMs
        val nodeDensity = requireDensity()
        val contentScope = this
        backdrop.graphicsLayer.record {
            val previousDensity = drawContext.density
            drawContext.density = nodeDensity
            try {
                contentScope.drawContent()
            } finally {
                drawContext.density = previousDensity
            }
        }
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        if (coordinates.isAttached) {
            backdrop.sourceCoordinates = coordinates
        }
    }

    override fun onDetach() {
        backdrop.sourceCoordinates = null
    }
}

enum class LiquidGlassMaterial(val key: String) {
    LIQUID("liquid"),
    FROSTED("frosted"),
    CLEAR("clear"),
    SOLID("solid"),
    ;

    companion object {
        fun fromKey(key: String?): LiquidGlassMaterial =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: LIQUID
    }
}

@Immutable
data class LiquidGlassStyle(
    val material: LiquidGlassMaterial = LiquidGlassMaterial.LIQUID,
    val vibrancy: Boolean = true,
    val edgeHighlight: Boolean = true,
    val shadow: Boolean = true,
    val selectionLens: Boolean = true,
) {
    val usesBackdrop: Boolean
        get() = material != LiquidGlassMaterial.SOLID

    val usesBlur: Boolean
        get() = material == LiquidGlassMaterial.LIQUID || material == LiquidGlassMaterial.FROSTED

    val usesLens: Boolean
        get() = material == LiquidGlassMaterial.LIQUID || material == LiquidGlassMaterial.CLEAR

    companion object {
        val Default = LiquidGlassStyle()
    }
}

internal val DefaultGlassHighlight: () -> Highlight? = { Highlight.Default }

internal val DefaultGlassShadow: () -> Shadow? = { Shadow.Default }

/**
 * The core glass surface. When there is no backdrop (or glass is not
 * supported on this device) it falls back to a flat [fallback] fill,
 * clipped to [shape] — the same static glass the app already uses.
 * Otherwise it draws the backdrop through Kyant's drawBackdrop with
 * vibrancy + blur + lens refraction + highlight + shadow, then the
 * [tint] on top.
 */
internal fun Modifier.liquidGlassSurface(
    backdrop: Backdrop?,
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp,
    refractionHeight: Dp = 0.dp,
    refractionAmount: Dp = refractionHeight,
    depthEffect: Boolean = true,
    chromaticAberration: Boolean = true,
    withVibrancy: Boolean = true,
    withHighlight: Boolean = true,
    withShadow: Boolean = false,
    fallbackBrush: Brush? = null,
    backdropOpacity: Float = 1f,
): Modifier =
    if (backdrop == null || !isLiquidGlassSupported()) {
        val staticGlass = clip(shape)
        if (fallbackBrush != null) staticGlass.background(fallbackBrush) else staticGlass.background(fallback)
    } else {
        drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (withVibrancy) vibrancy()
                if (blurRadius > 0.dp) blur(blurRadius.toPx())
                if (shape is CornerBasedShape && refractionHeight > 0.dp && refractionAmount > 0.dp) {
                    lens(
                        refractionHeight = refractionHeight.toPx().coerceAtMost(size.minDimension / 2f),
                        refractionAmount = refractionAmount.toPx(),
                        depthEffect = depthEffect,
                        chromaticAberration = chromaticAberration,
                    )
                }
                if (backdropOpacity < 1f) opacity(backdropOpacity.coerceIn(0f, 1f))
            },
            highlight = if (withHighlight) DefaultGlassHighlight else null,
            shadow = if (withShadow) DefaultGlassShadow else null,
            onDrawSurface = if (tint.alpha > 0f) {
                { drawRect(tint) }
            } else {
                null
            },
        )
    }

internal fun Modifier.liquidGlassBlur(
    backdrop: Backdrop?,
    radius: Dp,
    alphaProvider: () -> Float = { 1f },
): Modifier =
    if (backdrop == null || !isLiquidGlassAvailable()) {
        this
    } else {
        graphicsLayer { alpha = alphaProvider() }
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(radius.toPx()) },
            )
    }

@Composable
internal fun isAmbientLiquidGlassActive(): Boolean {
    val backdrop = LocalAvioGlassBackdrop.current ?: return false
    val host = backdrop as? LiquidGlassBackdropHost
    return isLiquidGlassSupported() && (host?.isReady ?: true)
}

/**
 * Glass that samples the ambient backdrop provided at the root.
 * Avio's "more glassy" defaults (strengthened from Nuvio's blur 16dp,
 * refraction 10dp / 16dp): blur 24dp, refraction 20dp / 28dp, with
 * depth + chromatic aberration on. When the backdrop is not ready (or
 * the theme has frosted glass off) it falls back to the flat [fallback]
 * fill.
 */
@Composable
fun Modifier.ambientLiquidGlass(
    shape: Shape,
    tint: Color,
    fallback: Color,
    blurRadius: Dp = 16.dp,
    refractionHeight: Dp = 10.dp,
    refractionAmount: Dp = refractionHeight * 1.6f,
    depthEffect: Boolean = true,
    chromaticAberration: Boolean = true,
    withVibrancy: Boolean = true,
    withHighlight: Boolean = true,
    withShadow: Boolean = false,
    fallbackBrush: Brush? = null,
    backdropOpacity: Float = 1f,
    staticFromBackdrop: Boolean = false,
): Modifier {
    val backdrop = LocalAvioGlassBackdrop.current
    val host = backdrop as? LiquidGlassBackdropHost
    if (host != null) {
        DisposableEffect(host) {
            host.acquire()
            onDispose { host.release() }
        }
    }
    val ready = backdrop != null && (host?.isReady ?: true)
    val frosted = LocalPalette.current.frosted
    return liquidGlassSurface(
        backdrop = if (ready && frosted) backdrop else null,
        shape = shape,
        tint = tint,
        fallback = fallback,
        blurRadius = blurRadius,
        refractionHeight = refractionHeight,
        refractionAmount = refractionAmount,
        depthEffect = depthEffect,
        chromaticAberration = chromaticAberration,
        withVibrancy = withVibrancy,
        withHighlight = withHighlight,
        withShadow = withShadow,
        fallbackBrush = fallbackBrush,
        backdropOpacity = backdropOpacity,
    )
}
