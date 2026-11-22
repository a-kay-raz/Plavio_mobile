package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.imageLoader
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.screens.player.PlaybackCache
import com.pulsestream.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Nuvio v1.17 Advanced hub, grouped like the source page's
 * sections: General, Debugging (crash reports — Nuvio's Sentry
 * toggle lives in its Debugging section) and Cache (the two cache
 * clears). Nuvio's Startup/Network rows are not added: Avio has
 * no DNS setting and no CW/metadata cache clears to put there.
 * Behaviour unchanged — the toggle logic is the same as the old
 * root rows.
 */
@Composable
fun AdvancedSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var telemetryEnabled by remember { mutableStateOf(com.pulsestream.app.data.AppStats.enabled(context)) }
    var sentryEnabled by remember { mutableStateOf(repository.settings.sentryEnabled) }
    // Step 70: which cache was cleared most recently ("playback" / "images" / "").
    var clearedCache by remember { mutableStateOf("") }
    val cacheScope = rememberCoroutineScope()
    val reducedMotion by com.pulsestream.app.data.MotionPreferences.reduced.collectAsState()

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                    Text(
                        text = "Advanced",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                }
            }
            item(key = "general_section") {
                SettingsSectionHeader("General")
                SettingsGroup(
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Contrast,
                            title = "Reduced motion",
                            subtitle = "Pause automatic hero slides and decorative pulses; also respects Android animation settings.",
                            checked = reducedMotion,
                            onCheckedChange = { com.pulsestream.app.data.MotionPreferences.setReduced(context, it) }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Info,
                            title = "Anonymous usage counting",
                            subtitle = "On by default. Counts installs and active users on Avio's own server — a random ID only, no personal data, no Google.",
                            checked = telemetryEnabled,
                            onCheckedChange = {
                                com.pulsestream.app.data.AppStats.setEnabled(context, it)
                                telemetryEnabled = it
                            }
                        )
                    }
                )
            }
            item(key = "debugging_section") {
                SettingsSectionHeader("Debugging")
                SettingsGroup(
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.BugReport,
                            title = "Crash reports",
                            subtitle = "On by default. If the app crashes, an anonymous report is sent so the bug can be fixed — no personal data, no Google.",
                            checked = sentryEnabled,
                            onCheckedChange = {
                                settings.sentryEnabled = it
                                com.pulsestream.app.data.CrashReports.apply(context, it)
                                sentryEnabled = it
                            }
                        )
                    }
                )
            }
            item(key = "cache_section") {
                SettingsSectionHeader("Cache")
                SettingsGroup(
                    {
                        SettingsClearRow(
                            icon = Icons.Filled.Storage,
                            title = "Clear playback cache",
                            subtitle = "Deletes video segments held in the playback cache.",
                            cleared = clearedCache == "playback",
                            onClear = {
                                PlaybackCache.clear(context)
                                clearedCache = "playback"
                            }
                        )
                    },
                    {
                        SettingsClearRow(
                            icon = Icons.Filled.Image,
                            title = "Clear image cache",
                            subtitle = "Deletes cached posters and thumbnails; they reload on demand.",
                            cleared = clearedCache == "images",
                            onClear = {
                                cacheScope.launch {
                                    context.imageLoader.diskCache?.clear()
                                    context.imageLoader.memoryCache?.clear()
                                    clearedCache = "images"
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}
