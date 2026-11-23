package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Forward5
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material3.Icon
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
            .playerLiquidGlass(
                shape = RoundedCornerShape(12.dp),
                refractionHeight = 4.dp,
                refractionAmount = 6.dp,
            )
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
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 6.dp,
                refractionAmount = 8.dp,
            )
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .tvFocusRing(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

/** Nuvio legacy center transport button (skip back/forward): glass circle,
 * 10dp padding, 26dp white icon (46dp circle — Nuvio phone tier). */
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
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 8.dp,
                refractionAmount = 12.dp,
            )
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .tvFocusRing(CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
    }
}

/** Center transport button with custom content (the skip-step icons draw
 * the number for 15s, where no numbered material icon exists). */
@Composable
internal fun CenterControlButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(
        modifier
            .playerLiquidGlass(
                shape = CircleShape,
                refractionHeight = 8.dp,
                refractionAmount = 12.dp,
            )
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick)
            .tvFocusRing(CircleShape)
            .padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Skip-step icon (Nuvio PlayerControls.SkipStepIcon): numbered 5/10/30s
 * icons; any other value (15s) draws a mirrored Replay arrow with the
 * number overlaid. */
@Composable
internal fun SkipStepIcon(seconds: Int, forward: Boolean, size: Dp = 26.dp) {
    val numbered = when (seconds) {
        5 -> if (forward) Icons.Filled.Forward5 else Icons.Filled.Replay5
        10 -> if (forward) Icons.Filled.Forward10 else Icons.Filled.Replay10
        30 -> if (forward) Icons.Filled.Forward30 else Icons.Filled.Replay30
        else -> null
    }
    if (numbered != null) {
        Icon(numbered, contentDescription = null, tint = Color.White, modifier = Modifier.size(size))
        return
    }
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Replay,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { if (forward) scaleX = -1f },
        )
        val density = LocalDensity.current
        Text(
            text = seconds.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = with(density) { (size * 0.26f).toSp() },
                lineHeight = with(density) { (size * 0.26f).toSp() },
                fontWeight = FontWeight.Bold,
            ),
            modifier = Modifier.offset(y = size * 0.1f),
        )
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
        // Nuvio playhead: a white vertical line (not the default round
        // thumb) — the same look as Nuvio's seek bar.
        thumb = {
            Box(
                Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (enabled) Color.White else Color.White.copy(alpha = 0.6f))
            )
        },
        track = {
            // The whole track is drawn by hand (base + buffered + played)
            // so the buffered segment stays visible between the played head
            // and the buffered head — the same picture as Nuvio's
            // PlayerProgressTrack, without depending on the experimental
            // SliderDefaults.Track overload.
            Canvas(Modifier.fillMaxSize()) {
                val trackH = 6.dp.toPx()
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
                    // Nuvio PlayerProgressTrack: the played segment is an
                    // accent GRADIENT (not solid) — accent at the start,
                    // lightened toward the playhead. (playedColor is White
                    // when disabled, so the gradient stays white there.)
                    val playedBrush = Brush.horizontalGradient(
                        colors = listOf(playedColor, lerp(playedColor, Color.White, 0.35f)),
                        startX = 0f,
                        endX = size.width * frac
                    )
                    drawRoundRect(
                        brush = playedBrush,
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(size.width * frac, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )
                }
                // Chapter ticks (Nuvio chapterMarkFractions): short white
                // ticks straddling the track at each chapter start.
                if (chapterFractions.isNotEmpty()) {
                    val tickW = 2.dp.toPx()
                    val tickH = 14.dp.toPx()
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

/** Nuvio PlayerQualityPanel QualityOptionRow: a bordered option row.
 * Selected = White 0.15 bg + 1dp accent-0.75 border; unselected =
 * surfacePopover bg + 1dp borderSubtle border. 12dp radius, h14/v12
 * padding. Title bodyMedium (SemiBold if selected); subtitle
 * labelMedium muted; "Selected" label (accent, labelMedium SemiBold)
 * on the right when selected. */
@Composable
internal fun QualityOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val accent = LocalPalette.current.accent
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) Color.White.copy(alpha = 0.15f) else Color(0xFF222222)
            )
            .border(
                width = 1.dp,
                color = if (selected) accent.copy(alpha = 0.75f) else Color(0xFF252A2A),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = Color.White,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF969CA3),
                    maxLines = 1,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(10.dp))
            Text(
                "Selected",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = accent
            )
        }
    }
}

/** Nuvio PlayerDialogButton ("Close"): 12dp radius, surfaceCard bg,
 * h16/v10 padding, labelLarge textSecondary. */
@Composable
internal fun PlayerDialogCloseButton(onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF222222))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Close",
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFFB8BEC5)
        )
    }
}

/** Nuvio AudioTrackModal AudioTrackRow: 12dp radius, h12/v8 padding.
 * Selected = FULL accent fill with onAccent text + a 20dp checkmark;
 * unselected = transparent. Line 1 titleMedium (max 1); line 2 the
 * language name (bodySmall, onAccent 0.82 / White 0.72) shown only
 * when the language is non-blank and not "und". */
@Composable
internal fun AudioTrackRow(
    label: String,
    language: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalPalette.current.accent
    val onAccent = Color.Black
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) onAccent else Color.White,
                maxLines = 1
            )
            val langName = language?.let { displayLanguage(it) }
            if (!langName.isNullOrBlank() && language != "und" && langName != label) {
                Text(
                    langName,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) onAccent.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.72f),
                    maxLines = 1
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = onAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
