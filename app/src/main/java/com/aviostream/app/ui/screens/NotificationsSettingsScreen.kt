package com.aviostream.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.TextPrimary

/**
 * Nuvio v1.17 Notifications hub, grouped like the source page:
 * Alerts (episode release alerts) then Test (test notification
 * card). Behaviour unchanged — the toggle logic is the same as
 * the old root rows.
 */
@Composable
fun NotificationsSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var episodeNotifs by remember { mutableStateOf(settings.episodeNotifications) }
    var downloadDone by remember { mutableStateOf(settings.downloadFinishedNotifications) }
    var testNotifSent by remember { mutableStateOf(false) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
                        text = "Notifications",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                }
            }
            item(key = "alerts_section") {
                SettingsSectionHeader("Alerts")
                SettingsGroup(
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Notifications,
                            title = "New episode alerts",
                            subtitle = "On by default. A few times a day Avio quietly checks the series in your watchlist and notifies you when a new episode is out.",
                            checked = episodeNotifs,
                            onCheckedChange = { enabled ->
                                settings.episodeNotifications = enabled
                                episodeNotifs = enabled
                                if (enabled) {
                                    com.aviostream.app.data.notifications.EpisodeNotifications.ensureScheduled(context)
                                    if (Build.VERSION.SDK_INT >= 33 &&
                                        ContextCompat.checkSelfPermission(
                                            context, Manifest.permission.POST_NOTIFICATIONS
                                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                    ) {
                                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    },
                    {
                        SettingsToggleRow(
                            icon = Icons.Filled.Download,
                            title = "Download finished",
                            subtitle = "Notify when a download completes",
                            checked = downloadDone,
                            onCheckedChange = { enabled ->
                                settings.downloadFinishedNotifications = enabled
                                downloadDone = enabled
                                if (enabled && Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.POST_NOTIFICATIONS
                                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            }
                        )
                    }
                )
            }
            item(key = "test_section") {
                SettingsSectionHeader("Test")
                SettingsGroup(
                    {
                        // Re-read on every recomposition so a grant made elsewhere is picked up.
                        val notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                            (Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(
                                    context, Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                        SettingsNavRow(
                            icon = Icons.Filled.NotificationImportant,
                            title = "Send test notification",
                            subtitle = when {
                                testNotifSent -> "Test notification sent — check your notification shade."
                                !notificationsOn -> "Notifications are off — enable them in system settings first."
                                else -> "Post a test alert to check episode notifications work."
                            },
                            onClick = {
                                if (notificationsOn) {
                                    com.aviostream.app.data.notifications.EpisodeNotifications.postTest(context)
                                    testNotifSent = true
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}
