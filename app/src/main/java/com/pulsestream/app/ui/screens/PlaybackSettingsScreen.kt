package com.pulsestream.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.AUDIO_LANGUAGE_OPTIONS
import com.pulsestream.app.data.BUFFER_BACK_OPTIONS_S
import com.pulsestream.app.data.BUFFER_MAX_OPTIONS_S
import com.pulsestream.app.data.BUFFER_MIN_OPTIONS_S
import com.pulsestream.app.data.BUFFER_PRESETS
import com.pulsestream.app.data.BUFFER_REBUFFER_OPTIONS_MS
import com.pulsestream.app.data.BUFFER_START_OPTIONS_MS
import com.pulsestream.app.data.DISK_CACHE_OPTIONS
import com.pulsestream.app.data.PlayerChoice
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.audioLanguageName
import com.pulsestream.app.data.bufferPresetName
import com.pulsestream.app.data.diskCacheName
import com.pulsestream.app.data.SUBTITLE_BG_OPTIONS
import com.pulsestream.app.data.SUBTITLE_COLOR_OPTIONS
import com.pulsestream.app.data.SUBTITLE_EDGE_OPTIONS
import com.pulsestream.app.data.SUBTITLE_LANGUAGE_OPTIONS
import com.pulsestream.app.data.SUBTITLE_SIZE_OPTIONS
import com.pulsestream.app.data.subtitleBgName
import com.pulsestream.app.data.subtitleColorName
import com.pulsestream.app.data.subtitleEdgeName
import com.pulsestream.app.data.subtitleSizeName
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.SKIP_SECONDS_OPTIONS
import com.pulsestream.app.data.WATCHED_THRESHOLD_OPTIONS
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.tv.tvFocusRing
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

@Composable
fun PlaybackSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var autoplay by remember { mutableStateOf(settings.autoplay) }
    var autoPlayNext by remember { mutableStateOf(settings.autoPlayNext) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var showSkip by remember { mutableStateOf(settings.showSkipButton) }
    var pipEnabled by remember { mutableStateOf(settings.pipEnabled) }
    var audioLang by remember { mutableStateOf(settings.preferredAudioLanguage) }
    var audioLangPickerOpen by remember { mutableStateOf(false) }
    // Step 38: secondary preferred audio language (fallback when the
    // primary has no track).
    var secondaryAudioLang by remember { mutableStateOf(settings.secondaryPreferredAudioLanguage) }
    var secondaryAudioLangPickerOpen by remember { mutableStateOf(false) }
    var bufferPresetOpen by remember { mutableStateOf(false) }
    var diskCacheOpen by remember { mutableStateOf(false) }
    // Step 39: custom per-second buffer values + auto-size disk cache.
    var customBuffers by remember { mutableStateOf(settings.customPlaybackBuffersEnabled) }
    var bufferFineOpen by remember { mutableStateOf(false) }
    var diskCacheAutoSize by remember { mutableStateOf(settings.diskCacheAutoSize) }
    var watchedThresholdOpen by remember { mutableStateOf(false) }
    var skipBackOpen by remember { mutableStateOf(false) }
    var skipForwardOpen by remember { mutableStateOf(false) }
    var nextEpisodeOpen by remember { mutableStateOf(false) }
    var autoSkipOpen by remember { mutableStateOf(false) }
    var skipOffsetOpen by remember { mutableStateOf(false) }
    var reuseLastLink by remember { mutableStateOf(settings.reuseLastLinkEnabled) }
    var reuseLinkHoursOpen by remember { mutableStateOf(false) }
    var heroTrailerPlayback by remember { mutableStateOf(settings.heroTrailerPlayback) }
    var heroTrailerDelayOpen by remember { mutableStateOf(false) }
    var heroTrailerUnmuted by remember { mutableStateOf(settings.heroTrailerStartUnmuted) }
    var gesturesOn by remember { mutableStateOf(settings.touchGesturesEnabled) }
    var swipeSeekOn by remember { mutableStateOf(settings.swipeToSeekEnabled) }
    var playerPickerOpen by remember { mutableStateOf(false) }
    // Refresh key so the preferred-player subtitle updates after the dialog closes.
    var playerUiTick by remember { mutableStateOf(0) }
    // Refresh key so the buffer/disk-cache subtitles update after the dialogs close.
    var bufferUiTick by remember { mutableStateOf(0) }
    // Refresh key so the watched-threshold subtitle updates after the dialog closes.
    var watchedUiTick by remember { mutableStateOf(0) }
    // Refresh key so the skip-seconds subtitles update after the dialogs close.
    var skipUiTick by remember { mutableStateOf(0) }
    // Refresh key so the next-episode subtitle updates after the dialog closes.
    var nextEpisodeUiTick by remember { mutableStateOf(0) }
    // Refresh key so the auto-skip / skip-offset subtitles update after the dialogs close.
    var skipSettingsUiTick by remember { mutableStateOf(0) }
    // Refresh key so the last-link cache subtitle updates after the dialog closes.
    var reuseLinkUiTick by remember { mutableStateOf(0) }
    // Refresh key so the hero-trailer delay subtitle updates after the dialog closes.
    var heroTrailerUiTick by remember { mutableStateOf(0) }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Playback",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How videos start, behave and sound while playing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "player_group") {
                // Read inside the item so recomposition picks up dialog changes.
                playerUiTick
                bufferUiTick
                watchedUiTick
                skipUiTick
                nextEpisodeUiTick
                skipSettingsUiTick
                reuseLinkUiTick
                heroTrailerUiTick
                SettingsGroup(
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.PlayArrow,
                            title = "Autoplay",
                            subtitle = "Play the first source automatically",
                            checked = autoplay,
                            onCheckedChange = {
                                autoplay = it
                                settings.autoplay = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.SkipNext,
                            title = "Auto-play next episode",
                            subtitle = "Start the next episode when one finishes",
                            checked = autoPlayNext,
                            onCheckedChange = {
                                autoPlayNext = it
                                settings.autoPlayNext = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.BrightnessHigh,
                            title = "Keep screen on",
                            subtitle = "Prevent the screen from sleeping while playing",
                            checked = keepScreenOn,
                            onCheckedChange = {
                                keepScreenOn = it
                                settings.keepScreenOn = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.FastForward,
                            title = "Skip intro button",
                            subtitle = "Show a skip button for intros, recaps and outros",
                            checked = showSkip,
                            onCheckedChange = {
                                showSkip = it
                                settings.showSkipButton = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.SkipNext,
                            title = "Auto-skip",
                            subtitle = autoSkipLabel(settings),
                            onClick = { autoSkipOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Schedule,
                            title = "Skip offset",
                            subtitle = skipOffsetLabel(settings),
                            onClick = { skipOffsetOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.PictureInPicture,
                            title = "Picture-in-Picture",
                            subtitle = "Float the video when you leave the player",
                            checked = pipEnabled,
                            onCheckedChange = {
                                pipEnabled = it
                                settings.pipEnabled = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.OpenInNew,
                            title = "Preferred player",
                            subtitle = "Currently: ${PlayerChoice.fromName(settings.preferredPlayer).label}",
                            onClick = { playerPickerOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Audiotrack,
                            title = "Preferred audio language",
                            subtitle = if (audioLang.isNullOrBlank()) {
                                "Not set \u2014 title's default audio is used"
                            } else {
                                "Auto-plays ${audioLanguageName(audioLang!!)} when available"
                            },
                            onClick = { audioLangPickerOpen = true }
                        )
                    },
                    {
                        // Step 38: tried when no track matches the primary.
                        SettingsNavRow(
                            icon = Icons.Filled.Audiotrack,
                            title = "Secondary audio language",
                            subtitle = if (secondaryAudioLang.isNullOrBlank()) {
                                "Not set"
                            } else {
                                "Auto-plays ${audioLanguageName(secondaryAudioLang!!)} when the primary language has no track"
                            },
                            onClick = { secondaryAudioLangPickerOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Speed,
                            title = "Buffer preset",
                            subtitle = "Currently: ${bufferPresetName(settings.bufferMinMs, settings.bufferMaxMs, settings.bufferForPlaybackMs)}",
                            onClick = { bufferPresetOpen = true }
                        )
                    },
                    {
                        // Step 39: per-second buffer tuning instead of presets only.
                        SettingsToggleRow(
                            icon = Icons.Filled.Tune,
                            title = "Custom buffer values",
                            subtitle = "Fine-tune min / max / start / rebuffer / back buffer per second",
                            checked = customBuffers,
                            onCheckedChange = {
                                customBuffers = it
                                settings.customPlaybackBuffersEnabled = it
                            }
                        )
                    },
                    {
                        if (customBuffers) {
                            SettingsNavRow(
                                icon = Icons.Filled.Tune,
                                title = "Fine-tune buffers",
                                subtitle = "${settings.bufferMinMs / 1000}s min · ${settings.bufferMaxMs / 1000}s max · starts after ${settings.bufferForPlaybackMs / 1000.0}s · rebuffer ${settings.bufferRebufferMs / 1000}s · back ${settings.bufferBackBufferMs / 1000}s",
                                onClick = { bufferFineOpen = true }
                            )
                        }
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Storage,
                            title = "Disk cache",
                            subtitle = when {
                                settings.diskCacheMb <= 0 -> "Cache played media on device: Off"
                                diskCacheAutoSize -> "Cache played media on device: Auto-size (1/5 of free space)"
                                else -> "Cache played media on device: ${diskCacheName(settings.diskCacheMb)}"
                            },
                            onClick = { diskCacheOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.CheckCircle,
                            title = "Watched threshold",
                            subtitle = "A title counts as watched at ${settings.watchedThresholdPercent}% played",
                            onClick = { watchedThresholdOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.FastRewind,
                            title = "Skip backward",
                            subtitle = "Double-tap left skips back ${settings.skipBackwardSeconds}s",
                            onClick = { skipBackOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.FastForward,
                            title = "Skip forward",
                            subtitle = "Double-tap right skips forward ${settings.skipForwardSeconds}s",
                            onClick = { skipForwardOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.TouchApp,
                            title = "Touch gestures",
                            subtitle = "Double-tap skip, swipe seek/volume/brightness and hold-for-speed",
                            checked = gesturesOn,
                            onCheckedChange = {
                                gesturesOn = it
                                settings.touchGesturesEnabled = it
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Swipe,
                            title = "Swipe to seek",
                            subtitle = if (gesturesOn) {
                                "Horizontal swipe seeks; turn off to keep only volume/brightness swipes"
                            } else {
                                "Turn on Touch gestures to use swipe seek"
                            },
                            checked = swipeSeekOn && gesturesOn,
                            onCheckedChange = {
                                if (gesturesOn) {
                                    swipeSeekOn = it
                                    settings.swipeToSeekEnabled = it
                                }
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.SkipNext,
                            title = "Next episode",
                            subtitle = nextEpisodeThresholdLabel(settings),
                            onClick = { nextEpisodeOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Link,
                            title = "Reuse last link",
                            subtitle = "Replay a title with its last played link instead of re-resolving sources",
                            checked = reuseLastLink,
                            onCheckedChange = {
                                reuseLastLink = it
                                settings.reuseLastLinkEnabled = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.History,
                            title = "Last-link cache",
                            subtitle = "Links stay reusable for ${settings.reuseLastLinkCacheHours}h",
                            onClick = { reuseLinkHoursOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Movie,
                            title = "Hero trailer autoplay",
                            subtitle = "Play the first trailer muted in the detail hero",
                            checked = heroTrailerPlayback,
                            onCheckedChange = {
                                heroTrailerPlayback = it
                                settings.heroTrailerPlayback = it
                            }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Schedule,
                            title = "Trailer start delay",
                            subtitle = heroTrailerDelayLabel(settings),
                            onClick = { heroTrailerDelayOpen = true }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.VolumeUp,
                            title = "Start trailer with sound",
                            subtitle = "Hero trailers start unmuted instead of muted",
                            checked = heroTrailerUnmuted,
                            onCheckedChange = {
                                heroTrailerUnmuted = it
                                settings.heroTrailerStartUnmuted = it
                            }
                        )
                    }
                )
            }
        }
    }

    if (audioLangPickerOpen) {
        AudioLanguagePickerDialog(
            initial = audioLang,
            onDismiss = { audioLangPickerOpen = false },
            onDone = { lang ->
                settings.preferredAudioLanguage = lang
                audioLang = lang
                audioLangPickerOpen = false
            }
        )
    }

    if (secondaryAudioLangPickerOpen) {
        AudioLanguagePickerDialog(
            initial = secondaryAudioLang,
            onDismiss = { secondaryAudioLangPickerOpen = false },
            onDone = { lang ->
                settings.secondaryPreferredAudioLanguage = lang
                secondaryAudioLang = lang
                secondaryAudioLangPickerOpen = false
            }
        )
    }

    if (bufferPresetOpen) {
        BufferPresetDialog(
            settings = settings,
            onDismiss = { bufferPresetOpen = false },
            onDone = { bufferPresetOpen = false; bufferUiTick++ }
        )
    }

    if (bufferFineOpen) {
        BufferFineDialog(
            settings = settings,
            onDismiss = { bufferFineOpen = false },
            onDone = { bufferFineOpen = false; bufferUiTick++ }
        )
    }

    if (diskCacheOpen) {
        DiskCacheDialog(
            settings = settings,
            onDismiss = { diskCacheOpen = false },
            onDone = {
                diskCacheOpen = false
                diskCacheAutoSize = settings.diskCacheAutoSize
                bufferUiTick++
            }
        )
    }

    if (playerPickerOpen) {
        PlayerChoiceDialog(
            settings = settings,
            onDismiss = { playerPickerOpen = false },
            onDone = { playerPickerOpen = false; playerUiTick++ }
        )
    }

    if (watchedThresholdOpen) {
        WatchedThresholdDialog(
            settings = settings,
            onDismiss = { watchedThresholdOpen = false },
            onDone = { watchedThresholdOpen = false; watchedUiTick++ }
        )
    }

    if (skipBackOpen) {
        SkipSecondsDialog(
            settings = settings,
            forward = false,
            onDismiss = { skipBackOpen = false },
            onDone = { skipBackOpen = false; skipUiTick++ }
        )
    }

    if (skipForwardOpen) {
        SkipSecondsDialog(
            settings = settings,
            forward = true,
            onDismiss = { skipForwardOpen = false },
            onDone = { skipForwardOpen = false; skipUiTick++ }
        )
    }

    if (nextEpisodeOpen) {
        NextEpisodeThresholdDialog(
            settings = settings,
            onDismiss = { nextEpisodeOpen = false },
            onDone = { nextEpisodeOpen = false; nextEpisodeUiTick++ }
        )
    }

    if (autoSkipOpen) {
        AutoSkipDialog(
            settings = settings,
            onDismiss = { autoSkipOpen = false },
            onDone = { autoSkipOpen = false; skipSettingsUiTick++ }
        )
    }

    if (skipOffsetOpen) {
        SkipOffsetDialog(
            settings = settings,
            onDismiss = { skipOffsetOpen = false },
            onDone = { skipOffsetOpen = false; skipSettingsUiTick++ }
        )
    }

    if (reuseLinkHoursOpen) {
        ReuseLinkHoursDialog(
            settings = settings,
            onDismiss = { reuseLinkHoursOpen = false },
            onDone = { reuseLinkHoursOpen = false; reuseLinkUiTick++ }
        )
    }

    if (heroTrailerDelayOpen) {
        HeroTrailerDelayDialog(
            settings = settings,
            onDismiss = { heroTrailerDelayOpen = false },
            onDone = { heroTrailerDelayOpen = false; heroTrailerUiTick++ }
        )
    }
}

/**
 * Skip-seconds picker (Settings > Playback, Nuvio skipBackward/skipForward
 * seconds): how far a double-tap jumps. Options 5/10/15/30, default 10.
 */
@Composable
fun SkipSecondsDialog(
    settings: SettingsStore,
    forward: Boolean,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(if (forward) settings.skipForwardSeconds else settings.skipBackwardSeconds)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forward) "Skip forward" else "Skip backward", color = TextPrimary) },
        text = {
            Column {
                Text(
                    if (forward) "How far a double-tap on the right jumps forward."
                    else "How far a double-tap on the left jumps back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(SKIP_SECONDS_OPTIONS, key = { it }) { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = seconds }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == seconds,
                                onClick = { selected = seconds },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("${seconds}s", color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (forward) settings.skipForwardSeconds = selected
                else settings.skipBackwardSeconds = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Last-link cache window (Nuvio streamReuseLastLinkCacheHours): how long a played link stays reusable. */
private val REUSE_LINK_CACHE_HOURS_OPTIONS = listOf(6, 12, 24, 48)

/** Cache-hours picker for Reuse last link. */
@Composable
fun ReuseLinkHoursDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.reuseLastLinkCacheHours) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Last-link cache", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "How long a played link stays reusable before the title re-resolves its sources.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(REUSE_LINK_CACHE_HOURS_OPTIONS, key = { it }) { hours ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = hours }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == hours,
                                onClick = { selected = hours },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("${hours}h", color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.reuseLastLinkCacheHours = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Hero trailer start delay, seconds (Nuvio heroTrailerStartDelaySeconds): 0–10. */
private val HERO_TRAILER_DELAY_OPTIONS = listOf(0, 1, 2, 3, 5, 10)

private fun heroTrailerDelayLabel(settings: SettingsStore): String {
    val seconds = settings.heroTrailerStartDelaySeconds
    return if (seconds == 0) "Starts immediately" else "Starts after ${seconds}s"
}

/**
 * Hero-trailer delay picker (Settings > Playback, Nuvio
 * heroTrailerStartDelaySeconds): how long after the detail opens the hero
 * trailer fades in.
 */
@Composable
fun HeroTrailerDelayDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.heroTrailerStartDelaySeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Trailer start delay", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "How long after the detail opens the hero trailer starts playing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(HERO_TRAILER_DELAY_OPTIONS, key = { it }) { seconds ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = seconds }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == seconds,
                                onClick = { selected = seconds },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                if (seconds == 0) "Immediately" else "${seconds}s",
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.heroTrailerStartDelaySeconds = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** One next-episode threshold choice: when the countdown card appears. */
private data class NextEpisodeThresholdOption(
    val label: String,
    val mode: String,
    val percent: Int,
    val minutes: Float
)

private val NEXT_EPISODE_THRESHOLD_OPTIONS = listOf(
    NextEpisodeThresholdOption("At 97% played", "percentage", 97, 2f),
    NextEpisodeThresholdOption("At 98% played", "percentage", 98, 2f),
    NextEpisodeThresholdOption("At 99% played", "percentage", 99, 2f),
    NextEpisodeThresholdOption("At the end (100%)", "percentage", 100, 2f),
    NextEpisodeThresholdOption("30 sec before the end", "minutes", 99, 0.5f),
    NextEpisodeThresholdOption("1 min before the end", "minutes", 99, 1f),
    NextEpisodeThresholdOption("2 min before the end", "minutes", 99, 2f),
    NextEpisodeThresholdOption("3 min before the end", "minutes", 99, 3f)
)

/** Subtitle for the Next episode row: the current threshold in words. */
private fun nextEpisodeThresholdLabel(settings: SettingsStore): String {
    return if (settings.nextEpisodeThresholdMode == "minutes") {
        val minutes = settings.nextEpisodeThresholdMinutes
        if (minutes < 1f) "Countdown card 30 sec before the end"
        else "Countdown card ${minutes.toInt()} min before the end"
    } else if (settings.nextEpisodeThresholdPercent >= 100) {
        "Countdown card at the end"
    } else {
        "Countdown card at ${settings.nextEpisodeThresholdPercent}% played"
    }
}

/**
 * Next-episode threshold picker (Settings > Playback, Nuvio
 * nextEpisodeThresholdMode/Percent/MinutesBeforeEnd): when the "Up next"
 * countdown card appears. One list covers both modes; the stored mode
 * and value follow the picked row.
 */
@Composable
fun NextEpisodeThresholdDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(
            NEXT_EPISODE_THRESHOLD_OPTIONS.firstOrNull {
                it.mode == settings.nextEpisodeThresholdMode &&
                    (if (it.mode == "minutes") it.minutes == settings.nextEpisodeThresholdMinutes
                    else it.percent == settings.nextEpisodeThresholdPercent)
            } ?: NEXT_EPISODE_THRESHOLD_OPTIONS[2]
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Next episode", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "When the \"Up next\" countdown card appears near the end of an episode. Cancel stops the auto-advance for that episode.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(NEXT_EPISODE_THRESHOLD_OPTIONS, key = { it.label }) { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = option }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == option,
                                onClick = { selected = option },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(option.label, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.nextEpisodeThresholdMode = selected.mode
                settings.nextEpisodeThresholdPercent = selected.percent
                settings.nextEpisodeThresholdMinutes = selected.minutes
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Subtitle for the Auto-skip row: which segment types skip themselves. */
private fun autoSkipLabel(settings: SettingsStore): String {
    val types = settings.autoSkipSegmentTypes
    if (types.isEmpty()) return "Off — show the skip button only"
    val names = listOf(
        "intro" to "Intros",
        "recap" to "Recaps",
        "outro" to "Outros",
        "movie-credits" to "Movie credits"
    ).filter { (value, _) -> value in types }.map { (_, name) -> name }
    return "Auto-skips: ${names.joinToString(", ")}"
}

/** Subtitle for the Skip offset row. */
private fun skipOffsetLabel(settings: SettingsStore): String {
    val offset = settings.skipIntroOffsetSeconds
    return when {
        offset == 0 -> "Button appears when the intro starts"
        offset > 0 -> "Button appears ${offset}s into the intro"
        else -> "Button appears ${-offset}s before the intro"
    }
}

private val AUTO_SKIP_OPTIONS = listOf(
    "intro" to "Intros",
    "recap" to "Recaps",
    "outro" to "Outros",
    "movie-credits" to "Movie credits"
)

/**
 * Auto-skip picker (Settings > Playback, Nuvio autoSkipSegmentTypes):
 * which segment types jump to their end on their own, once per title.
 * Nothing checked = the skip button only, never automatic.
 */
@Composable
fun AutoSkipDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.autoSkipSegmentTypes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-skip", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Checked types skip to their end as soon as the playhead enters them, once per title. Unchecked types only show the skip button.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                AUTO_SKIP_OPTIONS.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (value in selected) selected - value else selected + value
                            }
                            .tvFocusRing(RoundedCornerShape(10.dp))
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = value in selected,
                            onCheckedChange = {
                                selected = if (value in selected) selected - value else selected + value
                            },
                            colors = CheckboxDefaults.colors(
                                checkedColor = LocalPalette.current.accent
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.autoSkipSegmentTypes = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private val SKIP_OFFSET_OPTIONS = listOf(
    -10 to "-10s (early)",
    -5 to "-5s",
    0 to "On time",
    5 to "+5s",
    10 to "+10s (late)"
)

/**
 * Skip-offset picker (Settings > Playback, Nuvio skipIntroOffsetSeconds):
 * shifts when the intro skip button appears. Positive = later into the
 * intro; negative = before it starts.
 */
@Composable
fun SkipOffsetDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.skipIntroOffsetSeconds) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Skip offset", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "When the intro skip button appears. Only intros shift; recaps, outros and credits always start on time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(SKIP_OFFSET_OPTIONS, key = { it.first }) { (seconds, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = seconds }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == seconds,
                                onClick = { selected = seconds },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.skipIntroOffsetSeconds = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Watched threshold picker (Settings > Playback, Nuvio
 * WatchedThresholdSection): how much of a title must be played before it
 * counts as watched. Options 80/85/90/95/99, default 90.
 */
@Composable
fun WatchedThresholdDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.watchedThresholdPercent) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Watched threshold", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "How much of a title you must play before it counts as watched — in Library, Continue Watching and episode lists.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(WATCHED_THRESHOLD_OPTIONS, key = { it }) { percent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = percent }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == percent,
                                onClick = { selected = percent },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("$percent%", color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.watchedThresholdPercent = selected
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Buffer preset picker (Settings > Playback > Buffer & Cache): Low /
 * Balanced / Aggressive presets mapping to concrete ExoPlayer buffer
 * durations. Applies on the next player build (next title played).
 */
@Composable
fun BufferPresetDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(
            BUFFER_PRESETS.indexOfFirst {
                it.second == Triple(settings.bufferMinMs, settings.bufferMaxMs, settings.bufferForPlaybackMs)
            }.takeIf { it >= 0 }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Buffer preset", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "How much video is buffered ahead. Higher = fewer stalls on slow networks, more RAM used. Applies to the next title you play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(BUFFER_PRESETS.size) { index ->
                        val (label, values) = BUFFER_PRESETS[index]
                        val (minMs, maxMs, forPlaybackMs) = values
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = index }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == index,
                                onClick = { selected = index },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(label, color = TextPrimary)
                                Text(
                                    "${minMs / 1000}s min · ${maxMs / 1000}s max · starts after ${forPlaybackMs / 1000.0} s",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val idx = selected
                if (idx != null && idx >= 0) {
                    val (_, values) = BUFFER_PRESETS[idx]
                    settings.bufferMinMs = values.first
                    settings.bufferMaxMs = values.second
                    settings.bufferForPlaybackMs = values.third
                }
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Disk cache picker (Settings > Playback > Buffer & Cache): caches played
 * media on device so re-watches don't re-download. Applies on the next
 * player build.
 */
@Composable
fun DiskCacheDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember { mutableStateOf(settings.diskCacheMb) }
    // Step 39: auto-size from free space (1/5 of free, 256 MB – 10 GB).
    var autoSize by remember { mutableStateOf(settings.diskCacheAutoSize) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Disk cache", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Keep played video on your device so re-watching doesn't download it again. Applies to the next title you play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-size from free space", color = TextPrimary)
                        Text(
                            "Use 1/5 of free space (256 MB – 10 GB) instead of a fixed size",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                    Switch(
                        checked = autoSize,
                        onCheckedChange = { autoSize = it },
                        enabled = selected > 0,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(DISK_CACHE_OPTIONS, key = { it.first }) { (mb, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = mb }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == mb,
                                onClick = { selected = mb },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(label, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.diskCacheMb = selected
                settings.diskCacheAutoSize = autoSize
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Preferred-player picker (Settings > Playback): built-in Plavio player,
 * an external app via the system chooser, or ask every time.
 */
@Composable
fun PlayerChoiceDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var selected by remember {
        mutableStateOf(PlayerChoice.fromName(settings.preferredPlayer))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred player", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Which app plays your videos. External players open through Android's app chooser; some addons' streams may need their original headers, which only the built-in player guarantees.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(PlayerChoice.values(), key = { it.name }) { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = choice }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == choice,
                                onClick = { selected = choice },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(choice.label, color = TextPrimary)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    choice.blurb,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                settings.preferredPlayer = selected.name
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Multi-select dialog for the preferred subtitle languages (max 2). */
@Composable
fun SubtitleLanguagePickerDialog(
    initial: List<String>,
    onDismiss: () -> Unit,
    onDone: (List<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initial.toSet()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred subtitle languages", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "Pick up to 2. Matching subtitles turn on automatically in the player.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(SUBTITLE_LANGUAGE_OPTIONS, key = { it.first }) { (code, name) ->
                        val isSelected = code in selected
                        val enabled = isSelected || selected.size < 2
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    selected =
                                        if (isSelected) selected - code else selected + code
                                }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                enabled = enabled,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                name,
                                color = if (enabled) TextPrimary
                                else TextSecondary.copy(alpha = 0.45f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDone(
                        SUBTITLE_LANGUAGE_OPTIONS
                            .filter { it.first in selected }
                            .map { it.first }
                    )
                }
            ) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Single-select dialog for the preferred audio language. */
@Composable
fun AudioLanguagePickerDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onDone: (String?) -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Preferred audio language", color = TextPrimary) },
        text = {
            Column {
                Text(
                    "If a title's audio tracks include this language, it plays in it automatically. Otherwise the title's default is used.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    item(key = "none") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = null }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == null,
                                onClick = { selected = null },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text("Not set", color = TextPrimary)
                        }
                    }
                    items(AUDIO_LANGUAGE_OPTIONS, key = { it.first }) { (code, name) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selected = code }
                                .tvFocusRing(RoundedCornerShape(10.dp))
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = selected == code,
                                onClick = { selected = code },
                                colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                    selectedColor = LocalPalette.current.accent
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(name, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(selected) }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/**
 * Subtitle styling dialog (Settings > Playback): text color, size,
 * background/shade and edge style — with a live preview. Transactional:
 * every change edits a DRAFT; "Done" persists it, "Cancel" (or dismissing)
 * throws it away. "System default" skips the app's styling entirely so
 * Android's Settings > Accessibility > Captions preferences apply.
 */
@Composable
fun SubtitleStyleDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit
) {
    var useSystem by remember { mutableStateOf(settings.useSystemCaptions) }
    var textColor by remember { mutableStateOf(settings.subtitleTextColor) }
    var bgColor by remember { mutableStateOf(settings.subtitleBackgroundColor) }
    var sizeScale by remember { mutableStateOf(settings.subtitleTextSizeScale) }
    var edgeType by remember { mutableStateOf(settings.subtitleEdgeType) }

    fun confirm() {
        settings.useSystemCaptions = useSystem
        settings.subtitleTextColor = textColor
        settings.subtitleBackgroundColor = bgColor
        settings.subtitleTextSizeScale = sizeScale
        settings.subtitleEdgeType = edgeType
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle appearance", color = TextPrimary) },
        text = {
            Column {
                // ---- System default ----
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { useSystem = !useSystem }
                        .tvFocusRing(RoundedCornerShape(10.dp))
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = useSystem,
                        onCheckedChange = null,
                        colors = CheckboxDefaults.colors(
                            checkedColor = LocalPalette.current.accent
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("System default", color = TextPrimary)
                        Text(
                            "Use Android's Settings > Accessibility > Captions",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                // The custom styling controls edit the draft only while
                // "System default" is off (dimmed + guarded when it is on).
                Column(
                    Modifier
                        .fillMaxWidth()
                        .alpha(if (useSystem) 0.4f else 1f)
                ) {
                    // ---- Live preview ----
                    SubtitlePreview(
                        textColor = textColor,
                        bgColor = bgColor,
                        sizeScale = sizeScale,
                        edgeType = edgeType
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Text color", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SwatchRow(
                        options = SUBTITLE_COLOR_OPTIONS,
                        selected = textColor,
                        onSelect = { if (!useSystem) textColor = it }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Background", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SwatchRow(
                        options = SUBTITLE_BG_OPTIONS,
                        selected = bgColor,
                        onSelect = { if (!useSystem) bgColor = it }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Size", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    OptionChips(
                        options = SUBTITLE_SIZE_OPTIONS.map { it.second },
                        selected = subtitleSizeName(sizeScale),
                        onSelect = { name ->
                            if (!useSystem) {
                                SUBTITLE_SIZE_OPTIONS.find { it.second == name }?.let {
                                    sizeScale = it.first
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(12.dp))

                    Text("Edge style", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    OptionChips(
                        options = SUBTITLE_EDGE_OPTIONS.map { it.second },
                        selected = subtitleEdgeName(edgeType),
                        onSelect = { name ->
                            if (!useSystem) {
                                SUBTITLE_EDGE_OPTIONS.find { it.second == name }?.let {
                                    edgeType = it.first
                                }
                            }
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { confirm() }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

/** Row of color swatches for the subtitle styling dialog. */
@Composable
fun SwatchRow(
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.lazy.LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        items(options.size) { i ->
            val (color, name) = options[i]
            val isSelected = color == selected
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(androidx.compose.ui.graphics.Color(color))
                    .clickable { onSelect(color) }
                    .tvFocusRing(androidx.compose.foundation.shape.CircleShape)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) LocalPalette.current.accent
                        else androidx.compose.ui.graphics.Color.White.copy(alpha = 0.2f),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (name == "None") {
                    Text(
                        "✕",
                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

/** Wrapping row of text chips for size / edge options. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OptionChips(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { name ->
            val isSelected = name == selected
            androidx.compose.material3.FilterChip(
                selected = isSelected,
                onClick = { onSelect(name) },
                label = { Text(name) },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = LocalPalette.current.accent.copy(alpha = 0.25f),
                    selectedLabelColor = TextPrimary,
                    containerColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f),
                    labelColor = TextSecondary
                )
            )
        }
    }
}

/** Live subtitle preview that mirrors the chosen styling. */
@Composable
fun SubtitlePreview(
    textColor: Int,
    bgColor: Int,
    sizeScale: Float,
    edgeType: Int
) {
    val textCol = androidx.compose.ui.graphics.Color(textColor)
    val bgCol = androidx.compose.ui.graphics.Color(bgColor)
    val shadow = when (edgeType) {
        1 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black,
            offset = androidx.compose.ui.geometry.Offset(2f, 2f),
            blurRadius = 0f
        )
        2 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.9f),
            offset = androidx.compose.ui.geometry.Offset(3f, 3f),
            blurRadius = 4f
        )
        3 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f),
            offset = androidx.compose.ui.geometry.Offset(-2f, -2f),
            blurRadius = 1f
        )
        4 -> androidx.compose.ui.graphics.Shadow(
            color = androidx.compose.ui.graphics.Color.Black,
            offset = androidx.compose.ui.geometry.Offset(0f, 3f),
            blurRadius = 2f
        )
        else -> null
    }
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.Black)
            .border(
                1.dp,
                androidx.compose.ui.graphics.Color.White.copy(alpha = 0.12f),
                androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "This is how subtitles will look",
            color = textCol,
            fontSize = (16 * sizeScale).sp,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge.copy(shadow = shadow),
            modifier = Modifier
                .background(bgCol, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Step 39 — per-second buffer fine-tuning (Nuvio's custom buffer seconds).
 * Min / max / start / rebuffer / back-buffer, each as a chip row. On Done
 * the values are clamped so ExoPlayer's LoadControl rules hold
 * (min <= max, start/rebuffer <= min); applies to the next title played.
 */
@Composable
fun BufferFineDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onDone: () -> Unit
) {
    var minS by remember { mutableStateOf(settings.bufferMinMs / 1000) }
    var maxS by remember { mutableStateOf(settings.bufferMaxMs / 1000) }
    var startMs by remember { mutableStateOf(settings.bufferForPlaybackMs) }
    var rebufferMs by remember { mutableStateOf(settings.bufferRebufferMs) }
    var backS by remember { mutableStateOf(settings.bufferBackBufferMs / 1000) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fine-tune buffers", color = TextPrimary) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Per-second buffer values. Higher = fewer stalls on slow networks, more RAM used. Applies to the next title you play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Text("Minimum buffer", color = TextPrimary, fontWeight = FontWeight.Bold)
                OptionChips(
                    options = BUFFER_MIN_OPTIONS_S.map { "${it}s" },
                    selected = "${minS}s",
                    onSelect = { minS = it.removeSuffix("s").toInt() },
                )
                Text("Maximum buffer", color = TextPrimary, fontWeight = FontWeight.Bold)
                OptionChips(
                    options = BUFFER_MAX_OPTIONS_S.map { "${it}s" },
                    selected = "${maxS}s",
                    onSelect = { maxS = it.removeSuffix("s").toInt() },
                )
                Text("Start playback after", color = TextPrimary, fontWeight = FontWeight.Bold)
                OptionChips(
                    options = BUFFER_START_OPTIONS_MS.map { if (it % 1000 == 0) "${it / 1000}s" else "${it / 1000.0}s" },
                    selected = if (startMs % 1000 == 0) "${startMs / 1000}s" else "${startMs / 1000.0}s",
                    onSelect = { label ->
                        startMs = BUFFER_START_OPTIONS_MS.first {
                            (if (it % 1000 == 0) "${it / 1000}s" else "${it / 1000.0}s") == label
                        }
                    },
                )
                Text("Resume after stall", color = TextPrimary, fontWeight = FontWeight.Bold)
                OptionChips(
                    options = BUFFER_REBUFFER_OPTIONS_MS.map { if (it % 1000 == 0) "${it / 1000}s" else "${it / 1000.0}s" },
                    selected = if (rebufferMs % 1000 == 0) "${rebufferMs / 1000}s" else "${rebufferMs / 1000.0}s",
                    onSelect = { label ->
                        rebufferMs = BUFFER_REBUFFER_OPTIONS_MS.first {
                            (if (it % 1000 == 0) "${it / 1000}s" else "${it / 1000.0}s") == label
                        }
                    },
                )
                Text("Keep back buffer", color = TextPrimary, fontWeight = FontWeight.Bold)
                OptionChips(
                    options = BUFFER_BACK_OPTIONS_S.map { if (it == 0) "Off" else "${it}s" },
                    selected = if (backS == 0) "Off" else "${backS}s",
                    onSelect = { backS = if (it == "Off") 0 else it.removeSuffix("s").toInt() },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val minMs = (minS * 1000).coerceAtMost(maxS * 1000)
                val maxMs = (maxS * 1000).coerceAtLeast(minMs)
                settings.bufferMinMs = minMs
                settings.bufferMaxMs = maxMs
                settings.bufferForPlaybackMs = startMs.coerceAtMost(minMs)
                settings.bufferRebufferMs = rebufferMs.coerceAtMost(minMs)
                settings.bufferBackBufferMs = backS * 1000
                onDone()
            }) {
                Text("Done", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
