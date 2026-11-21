package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.tv.tvFocusRing

@Composable
internal fun TrackRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusRing(RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = LocalPalette.current.accent)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Small time pill shown under the seekbar — Nuvio legacy TimePill: liquid
 * glass, white text. The total pill is clickable (toggles elapsed/remaining). */
@Composable
internal fun TimeChip(text: String, onClick: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .liquidGlass(RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick) else Modifier)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White
        )
    }
}

/** One icon+label button in the bottom action pill — Nuvio legacy
 * PlayerActionPillButton: horizontal icon + label, 18dp white icon,
 * 12dp/12dp padding, white labelSm text. */
@Composable
internal fun PillBarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .tvFocusRing(RoundedCornerShape(22.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Nuvio legacy header button: 36dp liquid-glass circle, 20dp white icon. */
@Composable
internal fun PlayerHeaderButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(36.dp)
            .liquidGlass(CircleShape, frost = 0.12f, darken = 0.03f)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .tvFocusRing(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Nuvio legacy center transport button (skip back/forward): glass circle,
 * 10dp padding, 34dp white icon. */
@Composable
internal fun CenterControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier
            .liquidGlass(CircleShape, frost = 0.12f, darken = 0.03f)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .tvFocusRing(CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp))
    }
}

/** Nuvio legacy seek bar: Material3 Slider + thumb, 22dp touch height at
 * 0.82 scaleY, base track white 0.20, buffered white 0.40, played accent.
 * Drag scrubs (seek commits on release); tap seeks. TV: D-pad Left/Right
 * seeks ±10s while focused. Disabled (locked overlay): white thumb/track,
 * inactive white 0.28, no input. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegacySeekBar(
    positionMs: Long,
    durationMs: Long,
    bufferedMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isTv: Boolean = false,
    chapterFractions: List<Float> = emptyList(),
    onSeekDelta: ((Long) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    // Step 59: scrub position while dragging (null on release) — the
    // seek-preview bubble follows it.
    onScrub: ((Long?) -> Unit)? = null
) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = (dragFrac ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f)
        .coerceIn(0f, 1f)
    val bufferedFrac =
        if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val accent = LocalPalette.current.accent
    val tvModifier = if (isTv) {
        Modifier
            .focusable()
            .onFocusChanged { onFocusChanged?.invoke(it.isFocused) }
            .tvFocusRing(RoundedCornerShape(8.dp))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> { onSeekDelta?.invoke(-10_000); true }
                    Key.DirectionRight -> { onSeekDelta?.invoke(10_000); true }
                    else -> false
                }
            }
    } else Modifier
    Slider(
        value = frac,
        onValueChange = {
            if (enabled) {
                dragFrac = it
                onScrub?.invoke((it * durationMs).toLong())
            }
        },
        onValueChangeFinished = {
            dragFrac?.let { onSeek((it * durationMs).toLong()) }
            dragFrac = null
            onScrub?.invoke(null)
        },
        valueRange = 0f..1f,
        enabled = enabled && durationMs > 0,
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .graphicsLayer(scaleY = 0.82f)
            .then(tvModifier),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = if (enabled) accent else Color.White,
            inactiveTrackColor = Color.Transparent,
            disabledThumbColor = Color.White,
            disabledActiveTrackColor = Color.White,
            disabledInactiveTrackColor = Color.Transparent
        ),
        track = {
            // The whole track is drawn by hand (base + buffered + played)
            // so the buffered segment stays visible between the played head
            // and the buffered head — the same picture as Nuvio's
            // PlayerProgressTrack, without depending on the experimental
            // SliderDefaults.Track overload.
            Canvas(Modifier.fillMaxSize()) {
                val trackH = 4.dp.toPx()
                val cy = size.height / 2f
                val baseColor = if (enabled) Color.White.copy(alpha = 0.20f)
                else Color.White.copy(alpha = 0.28f)
                val playedColor = if (enabled) accent else Color.White
                drawRoundRect(
                    color = baseColor,
                    topLeft = Offset(0f, cy - trackH / 2f),
                    size = Size(size.width, trackH),
                    cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                )
                if (enabled && bufferedFrac > frac) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.40f),
                        topLeft = Offset(size.width * frac, cy - trackH / 2f),
                        size = Size(size.width * (bufferedFrac - frac), trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                }
                if (frac > 0f) {
                    drawRoundRect(
                        color = playedColor,
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(size.width * frac, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                }
                // Chapter ticks (Nuvio chapterMarkFractions): short white
                // ticks straddling the track at each chapter start.
                if (chapterFractions.isNotEmpty()) {
                    val tickW = 2.dp.toPx()
                    val tickH = 10.dp.toPx()
                    chapterFractions.forEach { fraction ->
                        val x = (size.width * fraction).coerceIn(0f, size.width - tickW)
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.85f),
                            topLeft = Offset(x, cy - tickH / 2f),
                            size = Size(tickW, tickH),
                            cornerRadius = CornerRadius(tickW / 2f, tickW / 2f)
                        )
                    }
                }
            }
        }
    )
}
