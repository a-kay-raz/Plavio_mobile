package com.aviostream.app.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/** Resume-from-saved-position prompt. Callbacks cover dismiss/confirm/start-over. */
@Composable
internal fun ResumePromptDialog(
    savedMs: Long,
    title: String,
    onResume: () -> Unit,
    onStartOver: () -> Unit
) {
    // Nuvio-style centered glass dialog (PlayerCenteredDialog) — not a
    // flat Material3 AlertDialog.
    PlayerCenteredDialog(onDismiss = onResume) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                "Resume playback",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                "Resume \"$title\" from ${formatMs(savedMs)}?",
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onStartOver) {
                    Text("Start over", color = TextSecondary)
                }
                TextButton(onClick = onResume) {
                    Text("Resume", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Speed pill in the bottom toolbar — opens the centered speed
 * dialog (same pattern as Quality). It used to be a Popup above the
 * button, but the popup is ~270dp tall and covered the seekbar and
 * the time pills; a centered dialog cannot overlap them. */
@Composable
internal fun SpeedMenuButton(
    speed: Float,
    onClick: () -> Unit
) {
    PillBarButton(
        icon = Icons.Filled.Speed,
        label = speedLabel(speed),
        onClick = onClick
    )
}

/** Sleep pill in the bottom toolbar — opens the centered sleep-timer
 * dialog (same pattern as Speed). The label shows the time left while
 * a timer runs, "Ep. end" for end-of-episode, else "Sleep". */
@Composable
internal fun SleepTimerButton(
    label: String,
    onClick: () -> Unit
) {
    PillBarButton(
        icon = Icons.Filled.Bedtime,
        label = label,
        onClick = onClick
    )
}
