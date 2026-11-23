package com.aviostream.app.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.CircularProgressIndicator
import com.aviostream.app.ui.components.OrbitDots
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StreamItem
import com.aviostream.app.ui.components.AddonTabs
import com.aviostream.app.ui.components.QualityPills
import com.aviostream.app.ui.components.SmartStreamSheet
import com.aviostream.app.ui.components.pinnedStreamSourceKey
import com.aviostream.app.ui.components.rememberStreamBrowserState
import com.aviostream.app.ui.components.streamListItems
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/**
 * Design I (user's approved design): full-width banner (artwork + title +
 * source count) at the top, then addon tabs, quality pills and stream cards
 * sharing one scroll area.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerSourcesContent(
    title: String,
    streams: List<StreamItem>,
    selectedUrl: String,
    loading: Boolean,
    onPick: (StreamItem) -> Unit,
    onDownload: (StreamItem) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    settings: SettingsStore? = null,
    isEpisode: Boolean = false
) {
    val browser = rememberStreamBrowserState(streams, settings, limitPerSource = false, isEpisode = isEpisode)
    // Pinned sources (Nuvio StreamSourcePinSheet): one row per distinct
    // source in this list; pin toggles persist in SettingsStore and the
    // pinned-first sort in applyStreamPreferences re-applies everywhere
    // (player Sources, per-episode streams, Detail browser, autoplay).
    var pinSheetOpen by remember { mutableStateOf(false) }
    var pinsVersion by remember { mutableStateOf(0) }
    // Step 42 — Smart Streams sheet: same tick pattern as pins; every
    // filter/sort change bumps smartVersion so the list re-sorts live.
    var smartSheetOpen by remember { mutableStateOf(false) }
    var smartVersion by remember { mutableStateOf(0) }
    val store = settings
    val pinnedKeys = store?.pinnedStreamSources ?: emptySet()
    val smartActiveCount = if (smartVersion >= 0) store?.smartStreamSettings()?.filters?.activeCount ?: 0 else 0
    val pinSources = remember(streams, pinsVersion) {
        streams.groupBy { pinnedStreamSourceKey(it) }
            .map { (key, list) ->
                Triple(
                    key,
                    list.first().sourceName?.takeIf { it.isNotBlank() } ?: pinnedStreamSourceKey(list.first()),
                    list.size
                )
            }
            .sortedWith(
                compareByDescending<Triple<String, String, Int>> { it.first in pinnedKeys }
                    .thenByDescending { it.third }
            )
    }
    // Nuvio PlayerSourcesPanel layout: fixed header + content label +
    // provider filter on top, the stream list scrolls below (weight 1f).
    // No banner — Nuvio's Sources panel has no artwork header.
    // Header kept compact so the links list gets maximum room in
    // landscape: 16dp horizontal / 12dp vertical outer padding, 8dp
    // header gap, 6dp gaps between filter rows, compact chips.
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sources",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            PlayerDialogCloseButton(onClick = onClose)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        if (streams.isNotEmpty()) {
            // Provider tabs ALWAYS show (compact = true): without it,
            // AddonTabs hides itself when there are <= 2 tabs — with a
            // single addon that means no tab at all, so the user can't
            // see which addon the links are from. Compact = Nuvio
            // AddonFilterChip style.
            AddonTabs(browser, compact = true)
            Spacer(Modifier.height(6.dp))
            // Compact pills (Detail browser style): smaller chips; the
            // first pill reads "Any" like it does on Detail.
            QualityPills(browser, compact = true)
            // Plain clickable texts, not TextButtons: a TextButton enforces
            // a 40dp min height, these rows are ~28dp so the list gains room.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${browser.filtered.size} sources", style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary)
                Spacer(Modifier.weight(1f))
                if (store != null) {
                    Text(
                        text = if (smartActiveCount > 0) "Smart ($smartActiveCount)" else "Smart",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPalette.current.accent,
                        modifier = Modifier.clickable(role = Role.Button) { smartSheetOpen = true }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
                Row(
                    modifier = Modifier.clickable(role = Role.Button) { pinSheetOpen = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = LocalPalette.current.accent
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Pin sources",
                        style = MaterialTheme.typography.labelLarge,
                        color = LocalPalette.current.accent
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("player-source-list"),
                contentPadding = PaddingValues(bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (browser.filtered.isEmpty()) item {
                    Text("No sources match these filters.", color = TextSecondary)
                }
                streamListItems(streams = browser.filtered, onPick = onPick, selectedUrl = selectedUrl,
                    // Step 35: hide the download button when the setting is off.
                    onDownload = if (settings?.showDownloadButton != false) onDownload else null, compact = true,
                    // Step 44: debrid name/description templates rename debrid rows.
                    settings = settings)
            }
        } else {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (loading) {
                    OrbitDots(modifier = Modifier.size(26.dp))
                } else {
                    Text("No sources loaded for this title.", color = TextSecondary)
                }
            }
            if (!loading) TextButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 8.dp)) { Text("Load sources") }
        }
    }
    if (smartSheetOpen && store != null) {
        SmartStreamSheet(
            streams = streams,
            settings = store,
            isEpisode = isEpisode,
            onChanged = { smartVersion++ },
            onDismiss = { smartSheetOpen = false },
        )
    }
    if (pinSheetOpen && store != null) {
        PlayerOverlayPanel(
            onDismiss = { pinSheetOpen = false }
        ) {
            Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Pin sources",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            Text(
                text = "Pinned sources list first on every title until unpinned.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(pinSources, key = { it.first }) { (key, label, count) ->
                    val isPinned = key in pinnedKeys
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold, color = TextPrimary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$count streams", style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary)
                        }
                        IconButton(onClick = {
                            store.pinnedStreamSources =
                                if (isPinned) pinnedKeys - key else pinnedKeys + key
                            pinsVersion++
                        }) {
                            Icon(
                                if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin $label" else "Pin $label",
                                tint = if (isPinned) Color(0xFFFFC107) else TextSecondary
                            )
                        }
                    }
                }
            }
            }
        }
    }
}
