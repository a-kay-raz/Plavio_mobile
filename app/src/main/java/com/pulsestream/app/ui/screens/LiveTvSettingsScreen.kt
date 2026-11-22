package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.LiveTvEpgSource
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.fetchStalkerChannels
import com.pulsestream.app.data.fetchXtreamChannels
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Live TV providers (Nuvio `settings/LiveTvSettingsPage`): Xtream Codes
 * login, Stalker Portal login (MAC), and XMLTV guide sources. Channels
 * from a configured provider appear in the Live tab next to addons and
 * M3U playlists; the tab reloads on resume, so saving here takes effect
 * as soon as you go back.
 */
@Composable
fun LiveTvSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val scope = rememberCoroutineScope()
    var xtreamServer by remember { mutableStateOf(settings.liveTvXtreamServerUrl) }
    var xtreamUser by remember { mutableStateOf(settings.liveTvXtreamUsername) }
    var xtreamPass by remember { mutableStateOf(settings.liveTvXtreamPassword ?: "") }
    var xtreamEnabled by remember { mutableStateOf(settings.liveTvXtreamEnabled) }
    var xtreamTest by remember { mutableStateOf<String?>(null) }
    var xtreamTesting by remember { mutableStateOf(false) }
    var stalkerPortal by remember { mutableStateOf(settings.liveTvStalkerPortalUrl) }
    var stalkerMac by remember { mutableStateOf(settings.liveTvStalkerMac) }
    var stalkerUser by remember { mutableStateOf(settings.liveTvStalkerUsername) }
    var stalkerPass by remember { mutableStateOf(settings.liveTvStalkerPassword ?: "") }
    var stalkerEnabled by remember { mutableStateOf(settings.liveTvStalkerEnabled) }
    var stalkerTest by remember { mutableStateOf<String?>(null) }
    var stalkerTesting by remember { mutableStateOf(false) }
    var epgAutoDetect by remember { mutableStateOf(settings.liveTvEpgAutoDetect) }
    var epgSources by remember { mutableStateOf(settings.liveTvEpgSources) }
    var addEpgOpen by remember { mutableStateOf(false) }

    fun saveXtream() {
        settings.liveTvXtreamServerUrl = xtreamServer
        settings.liveTvXtreamUsername = xtreamUser
        settings.liveTvXtreamPassword = xtreamPass
        settings.liveTvXtreamEnabled = xtreamEnabled
    }

    fun saveStalker() {
        settings.liveTvStalkerPortalUrl = stalkerPortal
        settings.liveTvStalkerMac = stalkerMac
        settings.liveTvStalkerUsername = stalkerUser
        settings.liveTvStalkerPassword = stalkerPass
        settings.liveTvStalkerEnabled = stalkerEnabled
    }

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
                            text = "Live TV",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "IPTV providers and programme guides for the Live tab.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "xtream") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Xtream Codes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = xtreamServer,
                        onValueChange = { xtreamServer = it; settings.liveTvXtreamServerUrl = it },
                        label = "Server URL (http://panel.example:8080)"
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = xtreamUser,
                        onValueChange = { xtreamUser = it; settings.liveTvXtreamUsername = it },
                        label = "Username"
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = xtreamPass,
                        onValueChange = { xtreamPass = it; settings.liveTvXtreamPassword = it },
                        label = "Password",
                        password = true
                    )
                    Spacer(Modifier.height(8.dp))
                    V117ToggleRow(
                        icon = Icons.Filled.Tv,
                        title = "Xtream enabled",
                        subtitle = "Load channels from this provider",
                        checked = xtreamEnabled,
                        onCheckedChange = {
                            xtreamEnabled = it
                            settings.liveTvXtreamEnabled = it
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                saveXtream()
                                xtreamTesting = true
                                xtreamTest = null
                                scope.launch {
                                    val result = runCatching {
                                        fetchXtreamChannels(settings.liveTvXtreamSettings())
                                    }
                                    xtreamTest = result.fold(
                                        onSuccess = { "Found ${it.size} channels ✓" },
                                        onFailure = { "Failed: ${it.message ?: "couldn't connect"}" }
                                    )
                                    xtreamTesting = false
                                }
                            },
                            enabled = !xtreamTesting
                        ) {
                            Text(
                                if (xtreamTesting) "Testing…" else "Test login",
                                color = LocalPalette.current.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                settings.liveTvXtreamServerUrl = ""
                                settings.liveTvXtreamUsername = ""
                                settings.liveTvXtreamPassword = null
                                xtreamServer = ""
                                xtreamUser = ""
                                xtreamPass = ""
                                xtreamTest = null
                            }
                        ) {
                            Text("Remove", color = TextSecondary)
                        }
                        xtreamTest?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item(key = "stalker") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Stalker Portal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = stalkerPortal,
                        onValueChange = { stalkerPortal = it; settings.liveTvStalkerPortalUrl = it },
                        label = "Portal URL (…/portal.php or panel base URL)"
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = stalkerMac,
                        onValueChange = { stalkerMac = it; settings.liveTvStalkerMac = it },
                        label = "MAC address (00:1A:79:XX:XX:XX)"
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = stalkerUser,
                        onValueChange = { stalkerUser = it; settings.liveTvStalkerUsername = it },
                        label = "Username (optional)"
                    )
                    Spacer(Modifier.height(8.dp))
                    ProviderField(
                        value = stalkerPass,
                        onValueChange = { stalkerPass = it; settings.liveTvStalkerPassword = it },
                        label = "Password (optional)",
                        password = true
                    )
                    Spacer(Modifier.height(8.dp))
                    V117ToggleRow(
                        icon = Icons.Filled.Dns,
                        title = "Stalker enabled",
                        subtitle = "Load channels from this portal",
                        checked = stalkerEnabled,
                        onCheckedChange = {
                            stalkerEnabled = it
                            settings.liveTvStalkerEnabled = it
                        }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                saveStalker()
                                stalkerTesting = true
                                stalkerTest = null
                                scope.launch {
                                    val result = runCatching {
                                        fetchStalkerChannels(settings.liveTvStalkerSettings())
                                    }
                                    stalkerTest = result.fold(
                                        onSuccess = { "Found ${it.size} channels ✓" },
                                        onFailure = { "Failed: ${it.message ?: "couldn't connect"}" }
                                    )
                                    stalkerTesting = false
                                }
                            },
                            enabled = !stalkerTesting
                        ) {
                            Text(
                                if (stalkerTesting) "Testing…" else "Test login",
                                color = LocalPalette.current.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = {
                                settings.liveTvStalkerPortalUrl = ""
                                settings.liveTvStalkerMac = ""
                                settings.liveTvStalkerUsername = ""
                                settings.liveTvStalkerPassword = null
                                stalkerPortal = ""
                                stalkerMac = ""
                                stalkerUser = ""
                                stalkerPass = ""
                                stalkerTest = null
                            }
                        ) {
                            Text("Remove", color = TextSecondary)
                        }
                        stalkerTest?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item(key = "epg") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        "Programme guide (EPG)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(8.dp))
                    V117ToggleRow(
                        icon = Icons.Filled.Schedule,
                        title = "Auto-detect guides",
                        subtitle = "Use M3U x-tvg-url headers and the Xtream xmltv.php feed",
                        checked = epgAutoDetect,
                        onCheckedChange = {
                            epgAutoDetect = it
                            settings.liveTvEpgAutoDetect = it
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Guide sources",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { addEpgOpen = true }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Add guide source",
                                tint = LocalPalette.current.accent
                            )
                        }
                    }
                    if (epgSources.isEmpty()) {
                        Text(
                            "No manual sources — auto-detect covers M3U and Xtream guides.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    epgSources.forEach { source ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    source.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    source.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            IconButton(onClick = {
                                epgSources = epgSources - source
                                settings.liveTvEpgSources = epgSources
                            }) {
                                Icon(
                                    Icons.Filled.Delete, "Remove guide source",
                                    tint = TextSecondary, modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addEpgOpen) {
        AddEpgSourceDialog(
            onDismiss = { addEpgOpen = false },
            onAdd = { name, url ->
                val source = LiveTvEpgSource(
                    id = "epg-${System.currentTimeMillis()}",
                    name = name.ifBlank { url },
                    url = url
                )
                epgSources = epgSources + source
                settings.liveTvEpgSources = epgSources
                addEpgOpen = false
            }
        )
    }
}

@Composable
private fun ProviderField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false
) {
    var showPassword by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        singleLine = true,
        visualTransformation = if (password && !showPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (password) {
            {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showPassword) "Hide password" else "Show password",
                        tint = TextSecondary
                    )
                }
            }
        } else null,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = LocalPalette.current.accent,
            unfocusedBorderColor = GlassBorder,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = LocalPalette.current.accent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AddEpgSourceDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = LocalPalette.current.panel,
        title = { Text("Add guide source", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Paste an XMLTV guide URL (.xml or .xml.gz) — its programmes match channels by name or EPG id.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalPalette.current.accent,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LocalPalette.current.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; error = null },
                    label = { Text("Guide URL", color = TextSecondary) },
                    placeholder = { Text("https://…/guide.xml", color = TextSecondary) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = LocalPalette.current.accent,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = LocalPalette.current.accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = LocalPalette.current.accent, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val u = url.trim()
                if (!u.startsWith("http://") && !u.startsWith("https://")) {
                    error = "Paste a full http(s) guide URL."
                    return@TextButton
                }
                onAdd(name.trim(), u)
            }) {
                Text("Add", color = LocalPalette.current.accent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
