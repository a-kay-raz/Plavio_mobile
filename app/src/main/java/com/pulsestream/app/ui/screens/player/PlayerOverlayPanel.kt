package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.pulsestream.app.ui.glass.ambientLiquidGlass
import com.pulsestream.app.ui.theme.LocalPalette

/*
 * Full-player overlay panel — the same presentation as the subtitle
 * overlay (PlayerScreen, tracksMode == "subs"): a scrim dims the
 * video, the video keeps playing behind it, and the content sits in a
 * bottom-anchored glass panel. A tap on the scrim dismisses it.
 * This replaces ModalBottomSheet for the player panels (Chapters,
 * Audio, Sources, Quality, Episodes, Channels, Playback info, Pin
 * sources, subtitle search, per-episode streams) so they all open
 * the way the subtitle box does.
 *
 * The panel itself carries the glass (ambientLiquidGlass) — content
 * placed inside must NOT add its own glass, or it double-glazes.
 * The panel caps at 75% of the screen height; scrolling content
 * (LazyColumn) sizes to its content up to that cap.
 */
@Composable
fun PlayerOverlayPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Scrim: dims the video; a tap outside the panel dismisses.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
        )
        // Bottom-anchored glass panel, like the subtitle overlay's
        // bottom placement (24dp sides, 20dp bottom).
        Box(
            modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                .heightIn(max = maxHeight * 0.75f)
                .ambientLiquidGlass(
                    shape = RoundedCornerShape(28.dp),
                    tint = LocalPalette.current.panel.copy(alpha = 0.25f),
                    fallback = Color(0xFF14141F),
                )
        ) {
            content()
        }
    }
}
