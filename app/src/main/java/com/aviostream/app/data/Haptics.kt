package com.aviostream.app.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-wide haptics flag + the one tick every haptic site shares.
 *
 * Scope (the standard streaming-app set, tasteful by design):
 * player control taps, double-tap seek, seekbar grab/scrub,
 * Settings switch flips, and long-press actions. Ordinary
 * navigation and list taps NEVER tick.
 *
 * [enabled] mirrors SettingsStore's `haptics_enabled` pref
 * (default ON): the store seeds it in its init and re-syncs it
 * on every write, so any composable can read the flag through
 * [rememberHapticTick] without holding a store reference.
 */
object Haptics {
    @Volatile
    var enabled: Boolean = true
        private set

    fun sync(value: Boolean) {
        enabled = value
    }
}

/**
 * The one light tick: [HapticFeedbackType.TextHandleMove], the
 * lightest standard Compose feedback. The returned lambda is
 * stable to call from any click/long-press handler — it checks
 * the flag itself and does nothing while haptics are OFF.
 */
@Composable
fun rememberHapticTick(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return {
        if (Haptics.enabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
