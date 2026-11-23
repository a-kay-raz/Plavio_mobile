package com.aviostream.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.StreamBadgeImportResult
import com.aviostream.app.data.StreamBadgeSettings
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
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
        }
    }
}
