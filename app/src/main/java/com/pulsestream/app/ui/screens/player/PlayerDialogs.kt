package com.pulsestream.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import com.pulsestream.app.ui.components.liquidGlass
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/** Resume-from-saved-position prompt. Callbacks cover dismiss/confirm/start-over. */
@Composable
internal fun ResumePromptDialog(
    savedMs: Long,
    title: String,
    onResume: () -> Unit,
    onStartOver: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            // Tapping outside keeps their place and resumes.
            onResume()
        },
        containerColor = Color(0xFF14141F),
        title = {
            Text(
                "Resume playback",
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                "Resume \"${title}\" from ${formatMs(savedMs)}?",
                color = TextSecondary
            )
        },
        confirmButton = {
            TextButton(onClick = onResume) {
                Text("Resume", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onStartOver) {
                Text("Start over", color = TextSecondary)
            }
        }
    )
}

/** Speed pill + popup menu for the bottom toolbar — Nuvio legacy
 * PlayerSpeedPopup: a Popup anchored ABOVE the speed button (gap 8dp),
 * 76dp wide, options 2 → 0.25, selected row Bold + accent on white 0.14. */
@Composable
internal fun SpeedMenuButton(
    speed: Float,
    menuOpen: Boolean,
    onOpenMenu: () -> Unit,
    onDismissMenu: () -> Unit,
    onSpeedSelected: (Float) -> Unit
) {
    Box {
        PillBarButton(
            icon = Icons.Filled.Speed,
            label = speedLabel(speed),
            onClick = onOpenMenu
        )
        if (menuOpen) {
            val gapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, -gapPx),
                onDismissRequest = onDismissMenu
            ) {
                Column(
                    Modifier
                        .width(76.dp)
                        .liquidGlass(RoundedCornerShape(14.dp), frost = 0.12f, darken = 0.03f)
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Nuvio's option set, top → bottom (0.25x included,
                    // same as the SPEEDS list).
                    SPEEDS.sortedDescending().forEach { s ->
                        val selected = s == speed
                        Box(
                            Modifier
                                .width(64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (selected) Color.White.copy(alpha = 0.14f)
                                    else Color.Transparent
                                )
                                .clickable { onSpeedSelected(s) }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                speedLabel(s),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) LocalPalette.current.accent else TextPrimary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
