package com.aviostream.app.ui.components

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.aviostream.app.data.VoiceSearchSupport
import com.aviostream.app.ui.theme.TextSecondary

/**
 * Voice search mic for the Search bar.
 *
 * ISOLATION CONTRACT — this composable can only ever do two things:
 * render a mic button, and (on a successful recognition) hand ONE
 * string to [onResult]. Every failure mode — no recognition service
 * on the device, recognizer cancelled, errored, or returning
 * nothing — renders nothing or does nothing. It holds no state that
 * the search flow reads, writes no state the search flow reads, and
 * the result travels through the exact same query state typed text
 * uses.
 *
 * The mic renders ONLY when a speech-recognition activity resolves
 * on this device; otherwise this composable emits nothing and the
 * search bar is unchanged.
 *
 * NO RECORD_AUDIO permission and no runtime request. Recognition is
 * 100% the system's (RecognizerIntent) — the recognizer app does the
 * listening under its own UI with its own mic permission; this app
 * never records audio, so it needs no permission of its own.
 * Tapping the mic opens the system speech UI directly.
 */
@Composable
fun VoiceSearchButton(onResult: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Does any speech-recognition activity exist on this device?
    val recognitionResolves = remember(context) {
        try {
            context.packageManager
                .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
                .isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = VoiceSearchSupport.firstResult(
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS),
            )
            if (text != null) onResult(text)
        }
        // Cancel / error / no-match: nothing happens — the query and
        // the search stay exactly as they were.
    }

    if (!recognitionResolves) return

    IconButton(
        onClick = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            // The service resolved a moment ago; if it has vanished
            // since, swallowing here keeps the tap a no-op instead
            // of a crash.
            try {
                speechLauncher.launch(intent)
            } catch (e: Exception) {
                // no-op by design
            }
        },
        modifier = modifier,
    ) {
        Icon(Icons.Filled.Mic, contentDescription = "Voice search", tint = TextSecondary)
    }
}
