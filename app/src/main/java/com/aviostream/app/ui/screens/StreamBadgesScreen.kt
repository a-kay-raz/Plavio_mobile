package com.aviostream.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.StreamBadgeImportResult
import com.aviostream.app.data.StreamBadgeSettings
import com.aviostream.app.ui.components.FusionBadgeSource
import com.aviostream.app.ui.components.FusionBadgeSources
import com.aviostream.app.ui.components.FusionSourceKind
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.addFusionSourceFromText
import com.aviostream.app.ui.components.addFusionSourceFromUrl
import com.aviostream.app.ui.components.badgeKeyLabel
import com.aviostream.app.ui.components.deleteFusionSource
import com.aviostream.app.ui.components.fusionSourceDir
import com.aviostream.app.ui.components.loadFusionSources
import com.aviostream.app.ui.components.updateFusionSource
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Step 61: Custom stream badges (S4 + P34).
 * Import a badge rule set from a URL (JSON: { "filters": [{ "name",
 * "pattern", "imageURL", "tagColor", "tagStyle", "textColor",
 * "borderColor", "groupId", "isEnabled" }], "groups": [...] }).
 * Up to 3 sources; the active one matches stream rows, and the
 * player shows its image badges when "Show badges in player" is on.
 * Each filter can be toggled off individually.
 */
@Composable
fun StreamBadgesScreen(
    badgeSettings: StreamBadgeSettings,
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val rules by badgeSettings.rules.collectAsState()
    val showInPlayer by badgeSettings.showInPlayer.collectAsState()
    val playerBadgeSource by badgeSettings.playerBadgeSource.collectAsState()
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var expandedSource by remember { mutableStateOf<String?>(null) }
    var fusionOpen by remember { mutableStateOf(false) }
    var fusionSources by remember { mutableStateOf(loadFusionSources(settings)) }

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
                    text = "Stream badges",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = "Badge rules match stream names and filenames with regex patterns, and show as chips on stream rows. Import a rule set from a URL.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
                item {
                    GlassCard {
                        Column(Modifier.padding(16.dp)) {
                            OutlinedTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                label = { Text("Badge rules URL") },
                                placeholder = { Text("https://example.com/badges.json") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (importing) return@Button
                                        importing = true
                                        message = null
                                        scope.launch {
                                            val result = badgeSettings.importFromUrl(urlText)
                                            importing = false
                                            message = when (result) {
                                                is StreamBadgeImportResult.Success ->
                                                    "Imported ${result.rules.activeImport?.filters?.size ?: 0} badge filters."
                                                is StreamBadgeImportResult.Error -> result.message
                                            }
                                            if (result is StreamBadgeImportResult.Success) urlText = ""
                                        }
                                    },
                                    enabled = urlText.isNotBlank()
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null)
                                    Text("Import", modifier = Modifier.padding(start = 8.dp))
                                }
                                if (importing) CircularProgressIndicator()
                            }
                            message?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                    }
                }
                item {
                    GlassCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Show badges in player",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    "Matched image badges appear under the player title",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = showInPlayer,
                                onCheckedChange = { badgeSettings.setShowInPlayer(it) }
                            )
                        }
                    }
                }
                item {
                    GlassCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Fusion badge packs",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    if (fusionSources.isEmpty()) "Your own badge artwork for stream cards — up to 3 sources"
                                    else "${fusionSources.size} source${if (fusionSources.size == 1) "" else "s"} · ${fusionSources.sumOf { it.badgeCount }} badges",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Button(onClick = { fusionOpen = true }) {
                                Text("Manage")
                            }
                        }
                    }
                }
                if (rules.imports.isEmpty()) {
                    item {
                        Text(
                            "No badge sources imported yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                items(rules.imports, key = { it.sourceUrl }) { import ->
                    GlassCard {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = import.isActive,
                                    onClick = { badgeSettings.setActiveSource(import.sourceUrl) }
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        import.sourceUrl,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary,
                                        maxLines = 2
                                    )
                                    Text(
                                        "${import.filters.size} filters · ${import.enabledFilterCount} enabled",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(onClick = { badgeSettings.deleteSource(import.sourceUrl) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove source", tint = TextSecondary)
                                }
                            }
                            if (showInPlayer && rules.imports.size > 1) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = playerBadgeSource == import.sourceUrl,
                                        onClick = { badgeSettings.setPlayerBadgeSource(import.sourceUrl) }
                                    )
                                    Text(
                                        "Use in player",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary
                                    )
                                }
                            }
                            Text(
                                if (expandedSource == import.sourceUrl) "Hide filters" else "Show filters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable {
                                        expandedSource = if (expandedSource == import.sourceUrl) null else import.sourceUrl
                                    }
                            )
                            if (expandedSource == import.sourceUrl) {
                                import.filters.forEach { filter ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                filter.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = TextPrimary
                                            )
                                            Text(
                                                filter.pattern,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = TextSecondary
                                            )
                                        }
                                        Switch(
                                            checked = filter.isEnabled,
                                            onCheckedChange = {
                                                badgeSettings.setFilterEnabled(
                                                    import.sourceUrl,
                                                    filter.id.ifBlank { filter.name },
                                                    it
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (fusionOpen) {
                FusionBadgesDialog(
                    settings = settings,
                    onDismiss = { fusionOpen = false },
                    onSourcesChanged = { fusionSources = it }
                )
            }
        }
    }
}

/**
 * Fusion badge packs (v0.14.30 port, feature #15): the user's own
 * badge artwork for stream cards, from up to three link/paste
 * sources. Adapted from upstream's StreamsSettingsScreen dialog —
 * no legacy-pack migration (the fork never shipped the single
 * imported pack) and no Elite fallback mention (the fork has no
 * bundled Elite artwork; a fact no source carries simply shows no
 * Fusion chip).
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FusionBadgesDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSourcesChanged: (List<FusionBadgeSource>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sources by remember { mutableStateOf(loadFusionSources(settings)) }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var pasted by remember { mutableStateOf("") }
    var preview by remember { mutableStateOf<FusionBadgeSource?>(null) }

    fun applySources(next: List<FusionBadgeSource>) {
        sources = next
        onSourcesChanged(next)
    }

    if (preview != null) {
        val src = preview!!
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(src.label, color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "${src.badgeCount} badges · ${src.groupCount} groups",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        item {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                src.badgeKeys.forEach { key ->
                                    FusionPreviewBadge(src.id, key)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { preview = null }) {
                    Text("Close", color = LocalPalette.current.accent)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Fusion style badges", color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 540.dp)) {
                item(key = "intro") {
                    Text(
                        "Your own badge dressing for stream cards, from up to " +
                            "${FusionBadgeSources.MAX_SOURCES} sources. A badge from a " +
                            "rule set with the same name still wins; a fact no source " +
                            "carries simply shows no Fusion chip.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                itemsIndexed(sources, key = { _, s -> s.id }) { _, source ->
                    FusionSourceCard(
                        source = source,
                        working = working,
                        onPreview = { preview = source },
                        onUpdate = {
                            working = true
                            status = null
                            scope.launch {
                                updateFusionSource(context, settings, source.id)
                                    .onSuccess {
                                        applySources(loadFusionSources(settings))
                                        status = "Updated ${it.label} — ${it.badgeCount} badges"
                                    }
                                    .onFailure {
                                        status = it.message ?: "Couldn't update that source"
                                    }
                                working = false
                            }
                        },
                        onDelete = {
                            working = true
                            status = null
                            scope.launch {
                                val remaining = deleteFusionSource(context, settings, source.id)
                                applySources(remaining)
                                if (preview?.id == source.id) preview = null
                                status = "Removed ${source.label}"
                                working = false
                            }
                        }
                    )
                }
                if (sources.size >= FusionBadgeSources.MAX_SOURCES) {
                    item(key = "cap") {
                        Text(
                            "Three sources is the limit — delete one to add another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                } else {
                    item(key = "addLink") {
                        Column(Modifier.padding(top = 10.dp)) {
                            Text(
                                "Add from a link",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = link,
                                onValueChange = { link = it; status = null },
                                label = { Text("Badge pack link") },
                                singleLine = true,
                                enabled = !working,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                enabled = !working && link.isNotBlank(),
                                onClick = {
                                    working = true
                                    status = null
                                    scope.launch {
                                        addFusionSourceFromUrl(context, settings, link)
                                            .onSuccess {
                                                applySources(loadFusionSources(settings))
                                                link = ""
                                                status = "Added ${it.label} — ${it.badgeCount} badges"
                                            }
                                            .onFailure {
                                                status = it.message ?: "Couldn't add that link"
                                            }
                                        working = false
                                    }
                                }
                            ) {
                                Text("Add link", color = LocalPalette.current.accent)
                            }
                        }
                    }
                    item(key = "addPaste") {
                        Column(Modifier.padding(top = 6.dp)) {
                            Text(
                                "Add pasted JSON",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = pasted,
                                onValueChange = { pasted = it; status = null },
                                label = { Text("Fusion pack JSON") },
                                minLines = 3,
                                maxLines = 6,
                                enabled = !working,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(
                                enabled = !working && pasted.isNotBlank(),
                                onClick = {
                                    working = true
                                    status = null
                                    scope.launch {
                                        addFusionSourceFromText(context, settings, pasted)
                                            .onSuccess {
                                                applySources(loadFusionSources(settings))
                                                pasted = ""
                                                status = "Added ${it.label} — ${it.badgeCount} badges"
                                            }
                                            .onFailure {
                                                status = it.message ?: "That JSON couldn't be used"
                                            }
                                        working = false
                                    }
                                }
                            ) {
                                Text("Add pasted JSON", color = LocalPalette.current.accent)
                            }
                        }
                    }
                }
                item(key = "status") {
                    Column {
                        status?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        if (working) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = LocalPalette.current.accent,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Working…",
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
            TextButton(enabled = !working, onClick = onDismiss) {
                Text("Done", color = LocalPalette.current.accent)
            }
        }
    )
}

/** One Fusion source's card: label, kind, state, counts, and its actions. */
@Composable
private fun FusionSourceCard(
    source: FusionBadgeSource,
    working: Boolean,
    onPreview: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        source.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        when (source.kind) {
                            FusionSourceKind.URL -> "From a link"
                            FusionSourceKind.PASTE -> "Pasted JSON"
                            FusionSourceKind.LEGACY -> "Imported pack"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
                Text(
                    "Active",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalPalette.current.accent
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${source.badgeCount} badges · ${source.groupCount} groups",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Row {
                TextButton(enabled = !working, onClick = onPreview) {
                    Text("Preview", color = LocalPalette.current.accent)
                }
                if (source.kind == FusionSourceKind.URL) {
                    TextButton(enabled = !working, onClick = onUpdate) {
                        Text("Update", color = LocalPalette.current.accent)
                    }
                }
                TextButton(enabled = !working, onClick = onDelete) {
                    Text("Delete", color = TextSecondary)
                }
            }
        }
    }
}

/** One badge in a source preview: its artwork (when installed) over its plain name. */
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FusionPreviewBadge(sourceId: String, key: String) {
    val context = LocalContext.current
    val bitmap = remember(sourceId, key) {
        runCatching {
            val file = java.io.File(fusionSourceDir(context, sourceId), "$key.png")
            if (file.isFile) {
                BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            } else {
                null
            }
        }.getOrNull()
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = null,
                modifier = Modifier.height(22.dp)
            )
        } else {
            Box(Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(
            badgeKeyLabel(key),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }
}
