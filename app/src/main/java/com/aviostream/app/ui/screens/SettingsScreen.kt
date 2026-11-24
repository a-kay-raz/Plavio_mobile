package com.aviostream.app.ui.screens

import androidx.compose.material3.OutlinedButton
import com.aviostream.app.ui.components.OrbitDots
import com.aviostream.app.ui.components.ProfileAvatarImage
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationImportant
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviostream.app.BuildConfig
import com.aviostream.app.R
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.rememberHapticTick
import com.aviostream.app.data.cloud.GoogleDriveSyncManager
import com.aviostream.app.data.cloud.AvioAccountManager
import com.aviostream.app.data.simkl.SimklApi
import com.aviostream.app.data.update.UpdateChecker
import com.aviostream.app.data.update.UpdateInfo
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.liquidGlass
import com.aviostream.app.ui.components.UpdatePrompt
import coil.imageLoader
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.screens.player.PlaybackCache
import com.aviostream.app.ui.theme.AppTheme
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.AccentTheme
import com.aviostream.app.ui.theme.GlassBorder
import com.aviostream.app.ui.theme.GlassWhite
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import com.aviostream.app.ui.tv.tvFocusRing
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onGoToAddons: () -> Unit = {},
    onGoToHomeCatalogs: () -> Unit = {},
    onGoToContinueWatching: () -> Unit = {},
    onGoToLiveTv: () -> Unit = {},
    onGoToPlayback: () -> Unit = {},
    onGoToSubtitles: () -> Unit = {},
    onGoToAppearance: () -> Unit = {},
    onGoToLayout: () -> Unit = {},
    onGoToStreams: () -> Unit = {},
    onGoToAbout: () -> Unit = {},
    onGoToAccount: () -> Unit = {},
    onGoToIntegrations: () -> Unit = {},
    onGoToCloudStream: () -> Unit = {},
    onGoToDownloads: () -> Unit = {},
    onGoToCollections: () -> Unit = {},
    onGoToTracking: () -> Unit = {},
    onGoToMediaServers: () -> Unit = {},
    onGoToProfiles: () -> Unit = {},
    onGoToDetailSections: () -> Unit = {},
    onGoToStreamBadges: () -> Unit = {},
    onGoToPlayerToolbar: () -> Unit = {},
    onGoToContentDiscovery: () -> Unit = {},
    onGoToNotifications: () -> Unit = {},
    onGoToAdvanced: () -> Unit = {},
    onGoToWhatsNew: () -> Unit = {},
    onGoToLicenses: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current

    var sentryEnabled by remember { mutableStateOf(repository.settings.sentryEnabled) }
    // Step 70: which cache was cleared most recently ("playback" / "images" / "").
    var clearedCache by remember { mutableStateOf("") }
    val cacheScope = rememberCoroutineScope()
    var episodeNotifs by remember { mutableStateOf(settings.episodeNotifications) }
    var testNotifSent by remember { mutableStateOf(false) }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // ---- Sample F (v0.12.4): searchable compact root screen ----
    // The account card reads the same sync manager the Account screen
    // uses; this screen is recreated on every visit, so the values are
    // fresh each time it opens.
    var settingsQuery by rememberSaveable { mutableStateOf("") }
    val syncManager = remember { GoogleDriveSyncManager(context, repository.settings, repository) }
    val signedIn = remember { syncManager.isSignedIn() }
    val accountEmail = remember { syncManager.getAccountEmail() }
    val lastBackup = remember { syncManager.getLastBackupTime() }
    // Avio account (v0.14.0): the card must reflect THIS sign-in too —
    // it previously read only the Google state and showed "Not signed
    // in" for Avio-account users (owner's report 2026-10-25).
    val avioManager = remember { AvioAccountManager(context, repository) }
    val avioEmail = remember {
        if (AvioAccountManager.isConfigured() && avioManager.isSignedIn()) {
            avioManager.accountEmail()
        } else {
            null
        }
    }
    val avioLastSync = remember(avioEmail) {
        if (avioEmail != null) avioManager.lastSyncTime() else null
    }
    val anySignedIn = signedIn || avioEmail != null
    // The user's own chosen username (set in Account) wins over the
    // email-derived name (owner's order 2026-10-22).
    val profileUserName = remember { settings.profileUserName }
    val accountName = remember(accountEmail, profileUserName) {
        profileUserName.takeIf { it.isNotBlank() } ?: accountEmail?.let { deriveDisplayName(it) }
    }
    val displayName = accountName ?: avioEmail?.let { deriveDisplayName(it) }
    val installedAddons by repository.addons.collectAsState(initial = emptyList())
    val enabledAddons = remember(installedAddons) { installedAddons.count { it.enabled } }
    val reducedMotion by com.aviostream.app.data.MotionPreferences.reduced.collectAsState()

    // Nuvio v1.17 root sections (SettingsRootPage: Account / General /
    // About / Advanced): the account card stays on top, then the
    // entries grouped by section in Nuvio's order. Sub-pages live
    // behind hubs (Content & Discovery, Notifications, Advanced) and
    // Appearance — only the hub rows show at root.
    val sectionAccount = stringResource(R.string.settings_section_account)
    val sectionGeneral = stringResource(R.string.settings_section_general)
    val sectionAbout = stringResource(R.string.settings_section_about)
    val sectionAdvanced = stringResource(R.string.settings_section_advanced)

    // Every root entry as data, so the search field filters the very
    // rows the sections render. Hub children (hub != null) render
    // inside their hub screen, but stay in the search index so a
    // search still finds them — tapping one goes straight to its
    // destination. Destinations and behaviour are exactly the
    // pre-restructure ones — presentation only. The old Account nav
    // row is gone: the account card above the list IS the account entry.
    val entries = listOf(
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_appearance_title),
            "appearance accent color amoled black theme dark light") {
            SettingsNavRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.settings_appearance_title),
                subtitle = "Themes, app icon, and language",
                onClick = onGoToAppearance,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Layout",
            "layout home cards details streams navigation poster customize") {
            SettingsNavRow(
                icon = Icons.Filled.Dashboard,
                title = "Layout",
                subtitle = "Home, cards, details, streams, and navigation.",
                onClick = onGoToLayout,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_home_catalogs_title),
            "home catalogs rows arrange visibility home screen order", hub = "layout") {
            SettingsNavRow(
                icon = Icons.Filled.GridView,
                title = stringResource(R.string.settings_home_catalogs_title),
                subtitle = stringResource(R.string.settings_home_catalogs_sub),
                onClick = onGoToHomeCatalogs,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Continue Watching",
            "continue watching resume sort style tap action prompt", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.History,
                title = "Continue Watching",
                subtitle = "Sort, card style, tap action and resume prompt",
                onClick = onGoToContinueWatching,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Live TV",
            "live tv xtream stalker iptv providers epg guide", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.Tv,
                title = "Live TV",
                subtitle = "Xtream / Stalker providers and programme guides",
                onClick = onGoToLiveTv,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_addons_title),
            "addons manage extensions install sources manifest", hub = "content") {
            SettingsStaticRow(
                icon = Icons.Filled.Extension,
                title = stringResource(R.string.settings_addons_title),
                subtitle = stringResource(
                    R.string.settings_addons_sub,
                    installedAddons.size,
                    enabledAddons
                ),
                trailing = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = "Open addons",
                        tint = TextSecondary
                    )
                },
                onClick = onGoToAddons,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Content & Discovery",
            "content discovery addons media servers plugins cloudstream search history") {
            SettingsNavRow(
                icon = Icons.Filled.Extension,
                title = "Content & Discovery",
                subtitle = "Manage addons and discovery sources.",
                onClick = onGoToContentDiscovery,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_playback_title),
            "playback autoplay next episode screen skip intro audio resume buffer") {
            SettingsNavRow(
                icon = Icons.Filled.PlayArrow,
                title = stringResource(R.string.settings_playback_title),
                subtitle = "Player, subtitles, and auto-play",
                onClick = onGoToPlayback,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Downloads",
            "downloads offline files storage downloaded movies shows") {
            SettingsNavRow(
                icon = Icons.Filled.Download,
                title = "Downloads",
                subtitle = "Manage your downloaded movies and episodes.",
                onClick = onGoToDownloads,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_streams_title),
            "streams sources source limits quality order autoplay rule providers", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.VideoLibrary,
                title = stringResource(R.string.settings_streams_title),
                subtitle = stringResource(R.string.settings_streams_sub),
                onClick = onGoToStreams,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, stringResource(R.string.settings_subtitles_title),
            "subtitles captions preferred languages appearance font size", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.ClosedCaption,
                title = stringResource(R.string.settings_subtitles_title),
                subtitle = stringResource(R.string.settings_subtitles_sub),
                onClick = onGoToSubtitles,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Downloads",
            "downloads offline files storage downloaded movies shows", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.Download,
                title = "Downloads",
                subtitle = "Your downloaded movies and shows",
                onClick = onGoToDownloads,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Collections",
            "collections folders tmdb trakt catalog groups", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.Folder,
                title = "Collections",
                subtitle = "Folders of catalogs, TMDB lists and Trakt lists",
                onClick = onGoToCollections,
                compact = true
            )
        },
        // Step 55: media servers (Jellyfin/Emby/Plex/Silo) — sign in
        // once, libraries show as rows on Home.
        SettingsEntry(sectionGeneral, "Media Servers",
            "media servers jellyfin emby plex silo self hosted libraries", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.Dns,
                title = "Media Servers",
                subtitle = "Your Jellyfin, Emby, Plex or Silo libraries",
                onClick = onGoToMediaServers,
                compact = true
            )
        },
        // Step 56: profiles — each keeps its own library, progress and settings.
        SettingsEntry(sectionAccount, "Profiles",
            "profiles switch manage pin lock multi profile") {
            SettingsNavRow(
                icon = Icons.Filled.Person,
                title = "Profiles",
                subtitle = "Switch profiles, each with its own library",
                onClick = onGoToProfiles,
                compact = true
            )
        },
        // Step 57: detail sections — reorder/hide the detail page's sections.
        SettingsEntry(sectionGeneral, "Detail sections",
            "detail sections reorder hide tabs episodes cast production", hub = "layout") {
            SettingsNavRow(
                icon = Icons.Filled.Tune,
                title = "Detail sections",
                subtitle = "Reorder and hide the detail page's sections",
                onClick = onGoToDetailSections,
                compact = true
            )
        },
        // Step 61: custom stream badges — import rule sets, show chips on streams.
        SettingsEntry(sectionGeneral, "Stream badges",
            "stream badges rules import chips labels quality codec group", hub = "layout") {
            SettingsNavRow(
                icon = Icons.Filled.Style,
                title = "Stream badges",
                subtitle = "Import badge rules shown on matching streams",
                onClick = onGoToStreamBadges,
                compact = true
            )
        },
        // Step 63: player toolbar — reorder/hide the pill and top buttons.
        SettingsEntry(sectionGeneral, "Player buttons",
            "player buttons toolbar pill reorder hide chapters sources speed", hub = "layout") {
            SettingsNavRow(
                icon = Icons.Filled.Dashboard,
                title = "Player buttons",
                subtitle = "Reorder and hide the player's pill and top buttons",
                onClick = onGoToPlayerToolbar,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Integrations",
            "integrations torbox debrid real-debrid alldebrid premiumize link accounts api keys") {
            SettingsNavRow(
                icon = Icons.Filled.Link,
                title = "Integrations",
                subtitle = "Manage available integrations",
                onClick = onGoToIntegrations,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Tracking",
            "tracking trakt simkl library progress ratings source") {
            SettingsNavRow(
                icon = Icons.Filled.Sync,
                title = "Tracking",
                subtitle = "Connect tracking providers and choose which one powers your Library and Continue Watching.",
                onClick = onGoToTracking,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Notifications",
            "notifications episode alerts test notification releases") {
            SettingsNavRow(
                icon = Icons.Filled.Notifications,
                title = "Notifications",
                subtitle = "Manage episode release alerts and send a test notification.",
                onClick = onGoToNotifications,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "CloudStream",
            "cloudstream cs3 plugins providers repositories", hub = "content") {
            SettingsNavRow(
                icon = Icons.Filled.Extension,
                title = "CloudStream",
                subtitle = "Community plugins and repositories",
                onClick = onGoToCloudStream,
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "New episode alerts",
            "notifications new episode alerts series watchlist releases", hub = "notifications") {
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
                        if (android.os.Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                context, android.Manifest.permission.POST_NOTIFICATIONS
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            notifPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                },
                compact = true
            )
        },
        SettingsEntry(sectionGeneral, "Send test notification",
            "notifications test notification episode alerts", hub = "notifications") {
            // Re-read on every recomposition so a grant made elsewhere is picked up.
            val notificationsOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() &&
                (android.os.Build.VERSION.SDK_INT < 33 ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
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
                },
                compact = true
            )
        },
        SettingsEntry(sectionAbout, "What's New",
            "whats new changelog version updates features changes") {
            SettingsNavRow(
                icon = Icons.Filled.NewReleases,
                title = "What's New",
                subtitle = "What changed in this version.",
                onClick = onGoToWhatsNew,
                compact = true
            )
        },
        SettingsEntry(sectionAbout, stringResource(R.string.settings_about_title),
            "about version website updates app info check") {
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = stringResource(R.string.settings_about_title),
                subtitle = stringResource(R.string.settings_about_sub),
                onClick = onGoToAbout,
                compact = true
            )
        },
        SettingsEntry(sectionAbout, "Licenses",
            "licenses attributions open source data sources tmdb fanart") {
            SettingsNavRow(
                icon = Icons.Filled.Info,
                title = "Licenses",
                subtitle = "Data sources, acknowledgements, and platform licenses",
                onClick = onGoToLicenses,
                compact = true
            )
        },
        SettingsEntry(sectionAdvanced, "Advanced",
            "advanced motion crash reports cache clear") {
            SettingsNavRow(
                icon = Icons.Filled.Tune,
                title = "Advanced",
                subtitle = "Reduced motion, crash reports and cache controls.",
                onClick = onGoToAdvanced,
                compact = true
            )
        },
        SettingsEntry(sectionAdvanced, "Reduced motion",
            "reduced motion animations accessibility pause hero slides shimmer", hub = "advanced") {
            SettingsToggleRow(
                icon = Icons.Filled.Contrast,
                title = "Reduced motion",
                subtitle = "Pause automatic hero slides and decorative shimmer; also respects Android animation settings.",
                checked = reducedMotion,
                onCheckedChange = { com.aviostream.app.data.MotionPreferences.setReduced(context, it) },
                compact = true
            )
        },
        SettingsEntry(sectionAdvanced, "Crash reports",
            "crash reports sentry bugs errors anonymous fix", hub = "advanced") {
            SettingsToggleRow(
                icon = Icons.Filled.BugReport,
                title = "Crash reports",
                subtitle = "On by default. If the app crashes, an anonymous report is sent so the bug can be fixed — no personal data, no Google.",
                checked = sentryEnabled,
                onCheckedChange = {
                    settings.sentryEnabled = it
                    com.aviostream.app.data.CrashReports.apply(context, it)
                    sentryEnabled = it
                },
                compact = true
            )
        },
        SettingsEntry(sectionAdvanced, "Clear playback cache",
            "clear cache playback video buffer storage", hub = "advanced") {
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
        SettingsEntry(sectionAdvanced, "Clear image cache",
            "clear cache images posters thumbnails", hub = "advanced") {
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
        },

    )
    val normalizedQuery = settingsQuery.trim().lowercase()
    // Downloads is both a root row and a Content & Discovery hub child,
    // so the index holds it twice — dedupe by title so search shows it
    // once (two "entry_Downloads" keys crashed the LazyColumn).
    val visibleEntries = if (normalizedQuery.isEmpty()) entries
        else entries.filter { entry ->
            entry.title.lowercase().contains(normalizedQuery) ||
                entry.searchText.contains(normalizedQuery)
        }.distinctBy { it.title }

    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // Bottom padding ensures the floating nav capsule never covers the last card,
            // including on smaller phones (capsule ~80dp + system nav ~32dp + margin)
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }


            // ---- Settings search (Sample F) ----
            item(key = "settings_search") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .liquidGlass(RoundedCornerShape(50))
                ) {
                    OutlinedTextField(
                        value = settingsQuery,
                        onValueChange = { settingsQuery = it },
                        placeholder = { Text("Search settings", color = TextSecondary) },
                        leadingIcon = { Icon(Icons.Filled.Search, null, tint = TextSecondary) },
                        trailingIcon = if (settingsQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { settingsQuery = "" }) {
                                    Icon(Icons.Filled.Close, "Clear search", tint = TextSecondary)
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(50),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = LocalPalette.current.accent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- Account card (Sample F): signed-in Google account,
            // name + email only, plus sync status. This card IS the
            // account/backup entry — it opens the same Account screen
            // the old nav row did, with every backup/restore action
            // intact there. No product name for the sync feature is
            // shown anywhere in Settings (owner's order, v0.12.4).
            item(key = "account_card") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    onClick = onGoToAccount
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ProfileAvatarImage(
                            avatarSetting = remember { settings.profileAvatar },
                            photoFile = remember { settings.profilePhotoFile() },
                            modifier = Modifier.size(44.dp),
                            placeholderLetter = if (anySignedIn) {
                                displayName?.firstOrNull()?.uppercase() ?: "P"
                            } else null
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (anySignedIn) (displayName ?: "Signed in") else "Not signed in",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    avioEmail != null -> avioEmail
                                    signedIn && accountEmail != null -> accountEmail
                                    signedIn -> "Google account"
                                    else -> "Tap to sign in and enable backup & sync"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                when {
                                    avioEmail != null -> avioLastSync?.let { "Synced · $it" }
                                        ?: "Avio account · backup & sync on"
                                    signedIn -> lastBackup?.let { "Last backup: $it" }
                                        ?: "Signed in · backup & sync on"
                                    else -> "Backup & sync off"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondary
                        )
                    }
                }
            }

            // ---- Entries: sticky section headers when browsing; a
            // flat filtered list while searching (Sample F) ----
            if (normalizedQuery.isEmpty()) {
                // Sections render in Nuvio v1.17's fixed order
                // (Account / General / About / Advanced), not in
                // first-entry order. Only root entries render here —
                // hub children live in their hub screens.
                val rootEntries = entries.filter { it.hub == null }
                val groupedEntries = rootEntries.groupBy { it.section }
                val orderedSections = listOf(sectionAccount, sectionGeneral, sectionAbout, sectionAdvanced)
                    .mapNotNull { section -> groupedEntries[section]?.let { section to it } }
                orderedSections.forEach { (section, sectionEntries) ->
                    stickyHeader(key = "section_$section") {
                        SettingsSectionHeader(section)
                    }
                    item(key = "group_$section") {
                        SettingsGroup(
                            *sectionEntries.map { entry ->
                                val rowContent: @Composable () -> Unit = { entry.content() }
                                rowContent
                            }.toTypedArray()
                        )
                    }
                }
            } else {
                items(visibleEntries, key = { "entry_${it.title}_${it.hub ?: "root"}" }) { entry ->
                    entry.content()
                }
                if (visibleEntries.isEmpty()) {
                    item(key = "no_settings_match") {
                        Text(
                            "No settings match \"${settingsQuery.trim()}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 24.dp)
                        )
                    }
                }
            }

            item(key = "footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Avio",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = LocalPalette.current.accent,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Device-code dialog for linking a Simkl account. */
@Composable
fun SimklPinDialog(
    deviceData: SimklApi.DeviceData?,
    error: String?,
    onOpenSite: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link Simkl", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    error != null -> {
                        Text(error, color = TextSecondary, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "You can retry from the Simkl row.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    deviceData == null -> {
                        OrbitDots(modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Talking to Simkl…",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            "Enter this code at simkl.com/pin — or tap Open Simkl to pre-fill it",
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            deviceData.userCode,
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 6.sp
                            ),
                            color = LocalPalette.current.accent,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This window closes automatically once you approve the code.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSite, enabled = deviceData != null) {
                Text("Open Simkl", color = LocalPalette.current.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = LocalPalette.current.background
    )
}

/** One root Settings entry for the Sample F searchable list (v0.12.4). */
private data class SettingsEntry(
    val section: String,
    val title: String,
    /** Extra lowercase keywords the settings search matches against. */
    val searchText: String,
    /** Hub this entry lives in ("content" / "notifications" / "advanced" /
     * "appearance") — null means it renders at root. Hub children stay
     * in the search index either way. */
    val hub: String? = null,
    val content: @Composable () -> Unit
)

/**
 * Display name for the Settings account card, derived from the account
 * email's local part ("syed.najam" → "Syed Najam") — the sync layer
 * exposes only the email address, so the name is derived at display
 * time and never stored.
 */
private fun deriveDisplayName(email: String): String =
    email.substringBefore('@')
        .split('.', '_', '-', '+')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { email }

/**
 * True while rendering inside a [SettingsGroup]: the shared settings
 * rows drop their own card and sit flush in the group card — the Onyx
 * settings structure (one card per section, hairline dividers between
 * rows), matching the owner's reference screenshots (2026-10-25).
 */
val LocalSettingsGrouped = compositionLocalOf { false }

/**
 * One grouped settings card: rows stacked in a single flat-ish glass
 * card with hairline dividers between them (inset to the text column).
 */
@Composable
fun SettingsGroup(vararg rows: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            .liquidGlass(RoundedCornerShape(24.dp))
    ) {
        Column(Modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalSettingsGrouped provides true) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 66.dp, end = 16.dp)
                                .height(0.5.dp)
                                .background(TextSecondary.copy(alpha = 0.16f))
                        )
                    }
                    row()
                }
            }
        }
    }
}

/** Switch colours: Onyx gets the reference look (white track, black thumb). */
@Composable
fun settingsSwitchColors(): SwitchColors =
    if (LocalPalette.current.appTheme == AppTheme.ONYX) {
        // The accent drives the small things (owner's order 2026-10-25):
        // the ON switch wears the chosen accent, thumb contrasts it.
        val onyxAccent = LocalPalette.current.accent
        SwitchDefaults.colors(
            checkedThumbColor = if (onyxAccent == Color.White) Color.Black else Color.White,
            checkedTrackColor = onyxAccent,
            uncheckedThumbColor = Color(0xFF8E8E93),
            uncheckedTrackColor = Color(0xFF2E2E30),
            checkedBorderColor = Color.Transparent,
            uncheckedBorderColor = Color.Transparent
        )
    } else {
        SwitchDefaults.colors(
            checkedThumbColor = LocalPalette.current.accent,
            checkedTrackColor = LocalPalette.current.accent.copy(alpha = 0.35f)
        )
    }

/** Nuvio's muted subtitle colour (#969CA3) for settings rows. */
private val SettingsMuted = Color(0xFF969CA3)

/**
 * Custom gradient switch (Nuvio's SettingsGradientSwitch, Avio
 * accent): 52x32dp track, 24dp thumb, 180ms tween. The track wears the
 * current accent's gradient brush while ON; the thumb is white ON,
 * muted OFF. Visual only in rows — the row itself handles the tap;
 * standalone (AppUpdaterCard) it is clickable.
 */
@Composable
fun SettingsGradientSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(durationMillis = 180),
        label = "settings_switch_thumb"
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(999.dp))
            .then(
                if (checked && enabled) Modifier.background(LocalPalette.current.accentTheme.swatchBrush)
                else Modifier.background(
                    if (checked) LocalPalette.current.accent
                    else TextSecondary.copy(alpha = 0.35f)
                )
            )
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(4.dp)
            .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (checked) Color.White else SettingsMuted)
        )
    }
}

/** Small-caps section header, Avio style. */
@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        color = SettingsMuted,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 10.dp)
    )
}

/** Leading icon in a rounded accent tile (Nuvio: 36dp, accent @ 0.12, 12dp radius). */
@Composable
fun SettingsIconTile(icon: ImageVector, contentDescription: String?) {
    if (LocalPalette.current.appTheme == AppTheme.ONYX) {
        // Onyx: flat charcoal tile, white glyph (owner's reference).
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(Color(0xFF2E2E30), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = Color.White, modifier = Modifier.size(24.dp))
        }
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    LocalPalette.current.accent.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = LocalPalette.current.accent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean = false
) {
    // Haptics (v0.14.30 port): every Settings switch flip ticks — this row is the one funnel nearly every settings switch goes through, so the wrap lives here, once. The tick fires only on an actual flip and only while haptics are ON.
    val hapticTick = rememberHapticTick()
    val wrappedOnCheckedChange: (Boolean) -> Unit = { value ->
        if (value != checked) hapticTick()
        onCheckedChange(value)
    }
    if (LocalSettingsGrouped.current) {
        SettingsToggleRowGrouped(icon, title, subtitle, checked, wrappedOnCheckedChange, compact)
        return
    }
    // Nuvio-style flat row: 16dp horizontal / 14dp vertical, 36dp
    // accent icon tile, bodyLarge title + bodyMedium muted subtitle,
    // custom gradient switch.
    Box(
        Modifier.fillMaxWidth().clickable { wrappedOnCheckedChange(!checked) }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted
                )
            }
            SettingsGradientSwitch(
                checked = checked,
                onCheckedChange = wrappedOnCheckedChange,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

/** Step 70: action row for one-shot actions (cache clears): icon + title /
 * subtitle + a Clear button that turns into "Cleared" after it runs. */
@Composable
fun SettingsClearRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    cleared: Boolean,
    onClear: () -> Unit
) {
    if (LocalSettingsGrouped.current) {
        // Flat inside the group card, like the other rows.
        Box(Modifier.fillMaxWidth().clickable { if (!cleared) onClear() }) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconTile(icon, title)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettingsMuted
                    )
                }
                if (cleared) {
                    Text(
                        "Cleared",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalPalette.current.accent
                    )
                } else {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
            }
        }
        return
    }
    GlassCard(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp),
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = SettingsMuted
                )
            }
            Spacer(Modifier.width(12.dp))
            if (cleared) {
                Text(
                    "Cleared",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalPalette.current.accent
                )
            } else {
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

/** Navigation row (Nuvio-style flat): icon tile + title/subtitle, opens a
 * sub-screen. No built-in chevron — the caller supplies [trailing] when
 * one is needed. */
@Composable
fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    compact: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    if (LocalSettingsGrouped.current) {
        SettingsNavRowGrouped(icon, title, subtitle, onClick, compact, trailing)
        return
    }
    // Nuvio-style flat row: 16dp horizontal / 14dp vertical, 36dp
    // accent icon tile, bodyLarge title + bodyMedium muted subtitle.
    Box(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted
                )
            }
            trailing?.invoke()
        }
    }
}

/** Non-interactive row (used for "coming soon" placeholders). */
@Composable
fun SettingsStaticRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
    compact: Boolean = false
) {
    if (LocalSettingsGrouped.current) {
        SettingsStaticRowGrouped(icon, title, subtitle, trailing, dimmed, onClick, compact)
        return
    }
    // Nuvio-style flat row: 16dp horizontal / 14dp vertical, 36dp
    // accent icon tile, bodyLarge title + bodyMedium muted subtitle.
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = if (compact) 9.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SettingsMuted
                )
            }
            trailing()
        }
    }
}

@Composable
private fun SettingsToggleRowGrouped(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    compact: Boolean
) {
    Box(Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
            }
            SettingsGradientSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

@Composable
private fun SettingsNavRowGrouped(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    compact: Boolean,
    trailing: (@Composable () -> Unit)? = null
) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
            }
            trailing?.invoke()
        }
    }
}

@Composable
private fun SettingsStaticRowGrouped(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
    dimmed: Boolean,
    onClick: (() -> Unit)?,
    compact: Boolean
) {
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = SettingsMuted)
            }
            trailing()
        }
    }
}

/** Script font used for the "Najam Syyd" signature in About. */

@Composable
fun AboutCard() {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.mipmap.app_logo),
                contentDescription = "Avio logo",
                modifier = Modifier.size(88.dp).clip(RoundedCornerShape(26.dp))
            )
            // Name + version text REMOVED (owner's order 2026-10-22):
            // the logo already says Avio, and the updater card below
            // already shows the version — the pair only duplicated both.
            Spacer(Modifier.height(14.dp))
            Text(
                text = "A beautiful open-source streaming app with a built-in " +
                    "player. Add your favorite addons and enjoy your movies " +
                    "and shows in one glowing place.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Filled.Link,
                    contentDescription = null,
                    tint = LocalPalette.current.accent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Data: TMDB • Fanart.tv • Cinemeta • OpenSubtitles • IntroDB",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Settings > About > App Updater: auto-check toggle + manual update check. */
@Composable
fun AppUpdaterCard() {
    val appContext = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var autoCheck by remember { mutableStateOf(UpdateChecker.isAutoCheckEnabled(appContext)) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsIconTile(Icons.Filled.SystemUpdate, "App updater")
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "App Updater",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "You're on v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Check automatically",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Text(
                        "Pop up when a new version is available",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Spacer(Modifier.width(12.dp))
                SettingsGradientSwitch(
                    checked = autoCheck,
                    onCheckedChange = {
                        autoCheck = it
                        UpdateChecker.setAutoCheckEnabled(appContext, it)
                    }
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    checking = true
                    status = null
                    scope.launch {
                        // Classified outcome: tell offline apart from
                        // up-to-date instead of "latest version" for all of it.
                        when (val result = UpdateChecker.checkNowDetailed(appContext)) {
                            is UpdateChecker.UpdateCheckResult.Update -> {
                                updateInfo = result.info
                            }
                            UpdateChecker.UpdateCheckResult.UpToDate -> {
                                status = "You're on the latest version."
                            }
                            UpdateChecker.UpdateCheckResult.Offline -> {
                                status = "Couldn't check — you're offline."
                            }
                            is UpdateChecker.UpdateCheckResult.Error -> {
                                status = "Couldn't check for updates: ${result.message}"
                            }
                        }
                        checking = false
                    }
                },
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPalette.current.accent.copy(alpha = 0.18f),
                    contentColor = LocalPalette.current.accent,
                    disabledContainerColor = LocalPalette.current.accent.copy(alpha = 0.08f)
                )
            ) {
                if (checking) {
                    OrbitDots(modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Checking…")
                } else {
                    Text("Check for update now")
                }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    UpdatePrompt(
        info = updateInfo,
        onDismiss = { updateInfo = null }
    )
}
