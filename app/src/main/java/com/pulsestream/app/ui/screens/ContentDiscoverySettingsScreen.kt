package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.R
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Nuvio v1.17 Content & Discovery hub: the discovery destinations
 * (Continue Watching, Live TV, Addons, Streams, Subtitles, Downloads,
 * Collections, Media Servers, CloudStream) live here instead of at
 * root. Destinations and behaviour unchanged.
 */
@Composable
fun ContentDiscoverySettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {},
    onGoToContinueWatching: () -> Unit = {},
    onGoToLiveTv: () -> Unit = {},
    onGoToAddons: () -> Unit = {},
    onGoToStreams: () -> Unit = {},
    onGoToSubtitles: () -> Unit = {},
    onGoToDownloads: () -> Unit = {},
    onGoToCollections: () -> Unit = {},
    onGoToMediaServers: () -> Unit = {},
    onGoToCloudStream: () -> Unit = {}
) {
    val installedAddons by repository.addons.collectAsState(initial = emptyList())
    val enabledAddons = remember(installedAddons) { installedAddons.count { it.enabled } }

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
                        text = "Content & Discovery",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                        color = TextPrimary
                    )
                }
            }
            item(key = "group") {
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.History,
                            title = "Continue Watching",
                            subtitle = "Sort, card style, tap action and resume prompt",
                            onClick = onGoToContinueWatching
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Tv,
                            title = "Live TV",
                            subtitle = "Xtream / Stalker providers and programme guides",
                            onClick = onGoToLiveTv
                        )
                    },
                    {
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
                            onClick = onGoToAddons
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.VideoLibrary,
                            title = stringResource(R.string.settings_streams_title),
                            subtitle = stringResource(R.string.settings_streams_sub),
                            onClick = onGoToStreams
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.ClosedCaption,
                            title = stringResource(R.string.settings_subtitles_title),
                            subtitle = stringResource(R.string.settings_subtitles_sub),
                            onClick = onGoToSubtitles
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Download,
                            title = "Downloads",
                            subtitle = "Your downloaded movies and shows",
                            onClick = onGoToDownloads
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Folder,
                            title = "Collections",
                            subtitle = "Folders of catalogs, TMDB lists and Trakt lists",
                            onClick = onGoToCollections
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Dns,
                            title = "Media Servers",
                            subtitle = "Your Jellyfin, Emby, Plex or Silo libraries",
                            onClick = onGoToMediaServers
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Extension,
                            title = "CloudStream",
                            subtitle = "Community plugins and repositories",
                            onClick = onGoToCloudStream
                        )
                    }
                )
            }
        }
    }
}
