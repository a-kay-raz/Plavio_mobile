package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.tracking.TrackingLibrarySource
import com.pulsestream.app.data.tracking.TrackingProgressSource
import com.pulsestream.app.data.tracking.TrackingRatingsSource
import com.pulsestream.app.data.tracking.effectiveRatingsSource
import com.pulsestream.app.data.tracking.isAnilistLinked
import com.pulsestream.app.data.tracking.isMdblistLinked
import com.pulsestream.app.data.tracking.isPublicMetaDbLinked
import com.pulsestream.app.data.tracking.isSimklLinked
import com.pulsestream.app.data.tracking.isWetrakrLinked
import com.pulsestream.app.data.tracking.isTraktLinked
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Step 52: tracking settings page (Nuvio settings/TrackingSettingsPage.kt,
 * scoped port). Picks which linked service feeds the Library, Up Next
 * progress and the ratings sheet. A single source that isn't linked falls
 * back to all linked services (library/progress) or Trakt (ratings) —
 * Nuvio's effective-source fallback.
 */
@Composable
fun TrackingSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit,
) {
    val settings = repository.settings
    // Bumped on every save so the effective-source notes recompose.
    var tick by remember { mutableIntStateOf(0) }

    var libraryPickerOpen by remember { mutableStateOf(false) }
    var progressPickerOpen by remember { mutableStateOf(false) }
    var ratingsPickerOpen by remember { mutableStateOf(false) }

    val traktLinked = remember(tick) { settings.isTraktLinked() }
    val simklLinked = remember(tick) { settings.isSimklLinked() }
    val anilistLinked = remember(tick) { settings.isAnilistLinked() }
    val wetrakrLinked = remember(tick) { settings.isWetrakrLinked() }
    val publicMetaDbLinked = remember(tick) { settings.isPublicMetaDbLinked() }
    val mdblistLinked = remember(tick) { settings.isMdblistLinked() }
    val librarySource = remember(tick) {
        TrackingLibrarySource.fromStored(settings.trackingLibrarySource)
    }
    val progressSource = remember(tick) {
        TrackingProgressSource.fromStored(settings.trackingProgressSource)
    }
    val ratingsSource = remember(tick) {
        TrackingRatingsSource.fromStored(settings.trackingRatingsSource)
    }
    val effectiveRatings = remember(tick) { settings.effectiveRatingsSource() }

    fun linkedNote(source: String, linked: Boolean, fallback: String): String? =
        if (source != "all" && source != "local" && !linked) {
            "Not linked — showing $fallback instead."
        } else null

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
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
                    text = "Tracking",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                item {
                    Text(
                        text = "Linked services",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                    Text(
                        text = "Trakt — ${if (traktLinked) "linked" else "not linked"}\n" +
                            "Simkl — ${if (simklLinked) "linked" else "not linked"}\n" +
                            "AniList — ${if (anilistLinked) "linked" else "not linked"}\n" +
                            "WeTrakr — ${if (wetrakrLinked) "linked" else "not linked"}\n" +
                            "PublicMetaDB — ${if (publicMetaDbLinked) "linked" else "not linked"}\n" +
                            "MDBList — ${if (mdblistLinked) "linked" else "not linked"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "Link services in Settings > Integrations.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
                item {
                    TrackingPickerRow(
                        title = "Library source",
                        subtitle = "Which service's watchlist and lists show in the Library",
                        valueLabel = librarySource.label,
                        note = when (librarySource) {
                            TrackingLibrarySource.TRAKT -> linkedNote("trakt", traktLinked, "all linked services")
                            TrackingLibrarySource.SIMKL -> linkedNote("simkl", simklLinked, "all linked services")
                            TrackingLibrarySource.ANILIST -> linkedNote("anilist", anilistLinked, "all linked services")
                            TrackingLibrarySource.WETRAKR -> linkedNote("wetrakr", wetrakrLinked, "all linked services")
                            TrackingLibrarySource.PUBLICMETADB -> linkedNote("publicmetadb", publicMetaDbLinked, "all linked services")
                            TrackingLibrarySource.MDBLIST -> linkedNote("mdblist", mdblistLinked, "all linked services")
                            else -> null
                        },
                        onClick = { libraryPickerOpen = true }
                    )
                }
                item {
                    TrackingPickerRow(
                        title = "Up Next source",
                        subtitle = "Which service's watch progress feeds Up Next",
                        valueLabel = progressSource.label,
                        note = when (progressSource) {
                            TrackingProgressSource.TRAKT -> linkedNote("trakt", traktLinked, "all linked services")
                            TrackingProgressSource.SIMKL -> linkedNote("simkl", simklLinked, "all linked services")
                            TrackingProgressSource.ANILIST -> linkedNote("anilist", anilistLinked, "all linked services")
                            TrackingProgressSource.WETRAKR -> linkedNote("wetrakr", wetrakrLinked, "all linked services")
                            TrackingProgressSource.PUBLICMETADB -> linkedNote("publicmetadb", publicMetaDbLinked, "all linked services")
                            TrackingProgressSource.MDBLIST -> linkedNote("mdblist", mdblistLinked, "all linked services")
                            else -> null
                        },
                        onClick = { progressPickerOpen = true }
                    )
                }
                item {
                    TrackingPickerRow(
                        title = "Ratings source",
                        subtitle = "Which service's community score shows in the ratings sheet",
                        valueLabel = ratingsSource.label,
                        note = when (ratingsSource) {
                            TrackingRatingsSource.ANILIST ->
                                if (!anilistLinked) "AniList isn't linked — showing Trakt ratings instead." else null
                            TrackingRatingsSource.WETRAKR ->
                                if (!wetrakrLinked) "WeTrakr isn't linked — showing Trakt ratings instead." else null
                            TrackingRatingsSource.MDBLIST ->
                                if (!mdblistLinked) "MDBList isn't linked — showing Trakt ratings instead." else null
                            else -> null
                        },
                        onClick = { ratingsPickerOpen = true }
                    )
                    Spacer(Modifier.height(8.dp))
                    if (effectiveRatings != ratingsSource) {
                        Text(
                            text = "Showing ${effectiveRatings.label} ratings (your pick isn't linked).",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }

    if (libraryPickerOpen) {
        TrackingSingleSelectDialog(
            title = "Library source",
            options = TrackingLibrarySource.entries.map { it.stored to it.label },
            selected = librarySource.stored,
            onSelect = {
                settings.trackingLibrarySource = it
                tick++
                libraryPickerOpen = false
            },
            onDismiss = { libraryPickerOpen = false }
        )
    }
    if (progressPickerOpen) {
        TrackingSingleSelectDialog(
            title = "Up Next source",
            options = TrackingProgressSource.entries.map { it.stored to it.label },
            selected = progressSource.stored,
            onSelect = {
                settings.trackingProgressSource = it
                tick++
                progressPickerOpen = false
            },
            onDismiss = { progressPickerOpen = false }
        )
    }
    if (ratingsPickerOpen) {
        TrackingSingleSelectDialog(
            title = "Ratings source",
            options = TrackingRatingsSource.entries.map { it.stored to it.label },
            selected = ratingsSource.stored,
            onSelect = {
                settings.trackingRatingsSource = it
                tick++
                ratingsPickerOpen = false
            },
            onDismiss = { ratingsPickerOpen = false }
        )
    }
}

@Composable
private fun TrackingPickerRow(
    title: String,
    subtitle: String,
    valueLabel: String,
    note: String?,
    onClick: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun TrackingSingleSelectDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                itemsIndexed(options, key = { index, _ -> index }) { _, option ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(option.first) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option.first == selected,
                            onClick = { onSelect(option.first) }
                        )
                        Text(option.second, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
