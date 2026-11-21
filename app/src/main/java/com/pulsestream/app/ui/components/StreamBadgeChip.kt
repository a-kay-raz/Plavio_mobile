package com.pulsestream.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pulsestream.app.data.StreamBadge

/**
 * Step 61 — badge chips on stream rows and in the player.
 * Scoped port of Nuvio streams/StreamBadgeChip.kt (sizes verbatim).
 * Image badges render the image; badges without an image render
 * their name as a text chip (Nuvio renders only images — the text
 * fallback is Plavio-only, since many rule sets ship name + colors
 * with no imageURL).
 */
enum class StreamBadgeChipSize(
    val containerHeight: Dp,
    val imageHeight: Dp,
    val minImageWidth: Dp,
    val maxImageWidth: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
) {
    STREAM(
        containerHeight = 20.dp,
        imageHeight = 16.dp,
        minImageWidth = 34.dp,
        maxImageWidth = 92.dp,
        horizontalPadding = 3.dp,
        verticalPadding = 2.dp,
    ),
    PLAYER(
        containerHeight = 17.dp,
        imageHeight = 13.dp,
        minImageWidth = 28.dp,
        maxImageWidth = 78.dp,
        horizontalPadding = 3.dp,
        verticalPadding = 2.dp,
    ),
}

@Composable
fun StreamBadgeChip(
    badge: StreamBadge,
    size: StreamBadgeChipSize = StreamBadgeChipSize.STREAM,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(6.dp)
    val backgroundColor = if (badge.tagStyle.equals("filled", ignoreCase = true)) {
        badge.tagColor.toBadgeColorOrNull()
    } else {
        null
    }
    val outlineColor = badge.borderColor.toBadgeColorOrNull()
    var chipModifier = modifier.height(size.containerHeight)
    if (backgroundColor != null) {
        chipModifier = chipModifier.background(backgroundColor, shape)
    }
    if (outlineColor != null) {
        chipModifier = chipModifier.border(1.dp, outlineColor, shape)
    }

    Box(
        modifier = chipModifier
            .padding(horizontal = size.horizontalPadding, vertical = size.verticalPadding),
        contentAlignment = Alignment.Center,
    ) {
        if (badge.imageURL.isNotBlank()) {
            AsyncImage(
                model = badge.imageURL,
                contentDescription = badge.name,
                modifier = Modifier
                    .height(size.imageHeight)
                    .widthIn(min = size.minImageWidth, max = size.maxImageWidth)
                    .clip(shape),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = badge.name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = badge.textColor.toBadgeColorOrNull() ?: Color.White,
                maxLines = 1,
            )
        }
    }
}

private fun String.toBadgeColorOrNull(): Color? {
    val hex = trim().removePrefix("#")
    val argb = when (hex.length) {
        6 -> "FF$hex"
        8 -> hex
        else -> return null
    }
    val value = argb.toLongOrNull(16) ?: return null
    return Color(value)
}
