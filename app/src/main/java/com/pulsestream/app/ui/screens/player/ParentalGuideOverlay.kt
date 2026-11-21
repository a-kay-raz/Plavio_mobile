package com.pulsestream.app.ui.screens.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.ParentalWarning
import com.pulsestream.app.ui.theme.LocalPalette

private val ParentalGuideRowHeight = 18.dp
private val ParentalGuideRowGap = 2.dp

/**
 * Step 68 — Parental guide overlay. Port of Nuvio's
 * ParentalGuideOverlay.kt: an accent bar grows beside the warning
 * rows, rows fade in one by one, the whole thing holds ~5s, then
 * fades out staggered and reports completion. Shown once when
 * playback starts, for titles that have a guide.
 */
@Composable
fun ParentalGuideOverlay(
    warnings: List<ParentalWarning>,
    isVisible: Boolean,
    onAnimationComplete: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(start = 16.dp, top = 76.dp),
) {
    if (warnings.isEmpty()) return

    val count = warnings.size
    val totalLineHeight = (ParentalGuideRowHeight.value * count) +
        (ParentalGuideRowGap.value * (count - 1))
    val accent = LocalPalette.current.accent

    val containerAlpha = remember { Animatable(0f) }
    val lineHeightFraction = remember { Animatable(0f) }
    val itemAlphas = remember(count) { List(count) { Animatable(0f) } }
    var animating by remember { mutableStateOf(false) }

    LaunchedEffect(isVisible) {
        if (isVisible && !animating) {
            animating = true

            containerAlpha.animateTo(1f, tween(300))
            lineHeightFraction.animateTo(1f, tween(400, easing = FastOutSlowInEasing))

            for (i in 0 until count) {
                kotlinx.coroutines.delay(80)
                itemAlphas[i].animateTo(1f, tween(200))
            }

            kotlinx.coroutines.delay(5000)

            for (i in (count - 1) downTo 0) {
                kotlinx.coroutines.delay(60)
                itemAlphas[i].animateTo(0f, tween(150))
            }

            kotlinx.coroutines.delay(100)
            lineHeightFraction.animateTo(0f, tween(300, easing = FastOutSlowInEasing))

            kotlinx.coroutines.delay(200)
            containerAlpha.animateTo(0f, tween(200))

            animating = false
            onAnimationComplete()
        } else if (!isVisible && animating) {
            for (i in (count - 1) downTo 0) {
                itemAlphas[i].snapTo(0f)
            }
            lineHeightFraction.snapTo(0f)
            containerAlpha.snapTo(0f)
            animating = false
            onAnimationComplete()
        }
    }

    if (containerAlpha.value <= 0f) return

    Row(
        modifier = modifier
            .alpha(containerAlpha.value)
            .padding(contentPadding),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height((totalLineHeight * lineHeightFraction.value).dp)
                .clip(RoundedCornerShape(1.dp))
                .background(accent),
        )

        Column(
            modifier = Modifier.padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(ParentalGuideRowGap),
        ) {
            warnings.forEachIndexed { index, warning ->
                Row(
                    modifier = Modifier
                        .height(ParentalGuideRowHeight)
                        .alpha(itemAlphas.getOrNull(index)?.value ?: 0f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = warning.label,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = " · ",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                    )
                    Text(
                        text = warning.severity,
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.5f),
                    )
                }
            }
        }
    }
}
