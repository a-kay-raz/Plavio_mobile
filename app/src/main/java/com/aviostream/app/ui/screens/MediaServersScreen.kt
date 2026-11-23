package com.aviostream.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.servers.MediaBrowserClient
import com.aviostream.app.data.servers.MediaServer
import com.aviostream.app.data.servers.MediaServerOps
import com.aviostream.app.data.servers.PlexClient
import com.aviostream.app.data.servers.ServerKind
import com.aviostream.app.data.servers.ServerLibrary
import com.aviostream.app.data.servers.SiloClient
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Step 55: Media Servers settings page (Nuvio
 * settings/MediaServersSettingsPage.kt, scoped port). Sign in to
 * Jellyfin / Emby (URL + username + password), Plex (PIN at
 * plex.tv/link) or Silo (device code). Servers are stored encrypted
 * in SettingsStore; their libraries render as Home rows and open in
 * the server library screen.
 */
@Composable
fun MediaServersScreen(
    settings: SettingsStore,
    onBack: () -> Unit,
    onOpenLibrary: (serverId: String, libraryId: String) -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }
    val servers = remember(tick) { settings.getMediaServers() }
    var showAdd by remember { mutableStateOf(false) }
    var addKind by remember { mutableStateOf<ServerKind?>(null) }
    var removeTarget by remember { mutableStateOf<MediaServer?>(null) }
    var expandedServer by remember { mutableStateOf<String?>(null) }

    fun save(server: MediaServer) {
        val current = settings.getMediaServers().filter { it.id != server.id } + server
        settings.saveMediaServers(current)
        tick++
    }

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
                    text = "Media Servers",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Your own Jellyfin, Emby, Plex or Silo libraries show up as rows on Home, and play in the built-in player.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(servers, key = { it.id }) { server ->
                    ServerCard(
                        server = server,
                        expanded = expandedServer == server.id,
                        onToggleExpand = {
                            expandedServer = if (expandedServer == server.id) null else server.id
                        },
                        onOpenLibrary = { library -> onOpenLibrary(server.id, library.id) },
                        onRemove = { removeTarget = server }
                    )
                }
                item {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showAdd = true },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SettingsIconTile(Icons.Filled.Dns, "Add a server")
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = "Add a server",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            V117Chevron()
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showAdd && addKind == null) {
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Add a server", color = TextPrimary) },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    ServerKind.entries.forEach { kind ->
                        TextButton(
                            onClick = { addKind = kind },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                kind.label,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text("Cancel", color = V117Muted) }
            },
            containerColor = LocalPalette.current.background
        )
    }

    when (addKind) {
        ServerKind.JELLYFIN, ServerKind.EMBY -> MediaBrowserSignInDialog(
            kind = addKind!!,
            settings = settings,
            onDismiss = { addKind = null; showAdd = false },
            onSignedIn = { server ->
                save(server)
                addKind = null
                showAdd = false
            }
        )
        ServerKind.PLEX -> PlexSignInDialog(
            settings = settings,
            onDismiss = { addKind = null; showAdd = false },
            onSignedIn = { server ->
                save(server)
                addKind = null
                showAdd = false
            }
        )
        ServerKind.SILO -> SiloSignInDialog(
            settings = settings,
            onDismiss = { addKind = null; showAdd = false },
            onSignedIn = { server ->
                save(server)
                addKind = null
                showAdd = false
            }
        )
        null -> Unit
    }

    if (removeTarget != null) {
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            containerColor = LocalPalette.current.background,
            title = { Text("Remove ${removeTarget!!.name}?") },
            text = { Text("Its libraries will stop showing on Home until you add it again.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.saveMediaServers(settings.getMediaServers().filter { it.id != removeTarget!!.id })
                    removeTarget = null
                    tick++
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ServerCard(
    server: MediaServer,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenLibrary: (ServerLibrary) -> Unit,
    onRemove: () -> Unit
) {
    var libraries by remember { mutableStateOf<List<ServerLibrary>?>(null) }
    var loadingLibraries by remember { mutableStateOf(false) }

    if (expanded && libraries == null && !loadingLibraries) {
        LaunchedEffect(server.id) {
            loadingLibraries = true
            libraries = withContext(Dispatchers.IO) {
                runCatching { MediaServerOps.getLibraries(server) }.getOrNull()
            } ?: emptyList()
            loadingLibraries = false
        }
    }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsIconTile(Icons.Filled.Storage, server.name)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "${server.kind.label} · ${server.username ?: server.baseUrl}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted
                    )
                }
                V117Chevron()
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                when {
                    loadingLibraries -> Text("Loading libraries…", color = V117Muted, style = MaterialTheme.typography.bodySmall)
                    libraries.isNullOrEmpty() -> Text(
                        "No libraries found — check the server is reachable.",
                        color = V117Muted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> libraries!!.forEach { library ->
                        TextButton(
                            onClick = { onOpenLibrary(library) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                library.name,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Start,
                                color = TextPrimary
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRemove) {
                        Text("Remove server", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaBrowserSignInDialog(
    kind: ServerKind,
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSignedIn: (MediaServer) -> Unit
) {
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Sign in to ${kind.label}", color = TextPrimary) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it.trim() },
                    label = { Text("Server URL (http://192.168.1.10:8096)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = baseUrl.isNotBlank() && username.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        val auth = withContext(Dispatchers.IO) {
                            MediaBrowserClient.authenticate(settings, baseUrl, username, password)
                        }
                        busy = false
                        if (auth == null) {
                            error = "Couldn't sign in. Check the URL, username and password."
                        } else {
                            val serverName = withContext(Dispatchers.IO) {
                                MediaBrowserClient.getPublicInfo(baseUrl)
                            }
                            onSignedIn(
                                MediaServer(
                                    id = UUID.randomUUID().toString(),
                                    kind = kind,
                                    name = serverName ?: auth.username,
                                    baseUrl = baseUrl.trim().removeSuffix("/"),
                                    username = auth.username,
                                    userId = auth.userId,
                                    accessToken = auth.token,
                                    refreshToken = null,
                                    tokenExpiresAtMs = 0L
                                )
                            )
                        }
                    }
                }
            ) { Text(if (busy) "Signing in…" else "Sign in") }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = V117Muted) }
        },
        containerColor = LocalPalette.current.background
    )
}

@Composable
private fun PlexSignInDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSignedIn: (MediaServer) -> Unit
) {
    val context = LocalContext.current
    var pin by remember { mutableStateOf<PlexClient.PinData?>(null) }
    var resources by remember { mutableStateOf<List<PlexClient.PlexResource>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        busy = true
        pin = withContext(Dispatchers.IO) { PlexClient.requestPin(settings) }
        busy = false
        if (pin == null) error = "Couldn't reach plex.tv. Check your connection and try again."
    }

    if (pin != null && resources == null) {
        val currentPin = pin!!
        LaunchedEffect(currentPin) {
            val deadline = SystemClock.elapsedRealtime() + 15 * 60 * 1000L
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(2000L)
                val token = withContext(Dispatchers.IO) {
                    PlexClient.pollPin(settings, currentPin.pinId)
                }
                if (token != null) {
                    val found = withContext(Dispatchers.IO) {
                        PlexClient.getResources(settings, token)
                    }
                    if (found.isEmpty()) {
                        error = "Signed in, but no reachable Plex server was found on this account."
                    } else {
                        resources = found
                    }
                    break
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Sign in to Plex", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when {
                    resources != null -> {
                        Text(
                            "Choose the server to use:",
                            color = V117Muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        resources!!.forEach { resource ->
                            TextButton(
                                onClick = {
                                    onSignedIn(
                                        MediaServer(
                                            id = UUID.randomUUID().toString(),
                                            kind = ServerKind.PLEX,
                                            name = resource.name,
                                            baseUrl = resource.baseUrl,
                                            username = null,
                                            userId = resource.machineId,
                                            accessToken = resource.token,
                                            refreshToken = null,
                                            tokenExpiresAtMs = 0L
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(resource.name, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                    pin != null -> {
                        Text(
                            "Enter this code at plex.tv/link — or tap Open Plex to go there",
                            color = V117Muted,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            pin!!.code,
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
                            color = V117Muted,
                            textAlign = TextAlign.Center
                        )
                    }
                    busy -> Text("Talking to plex.tv…", color = V117Muted, textAlign = TextAlign.Center)
                    else -> Text(
                        error ?: "Couldn't reach plex.tv.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                if (error != null && resources == null && pin != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
            }
        },
        confirmButton = {
            if (pin != null && resources == null) {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://plex.tv/link"))
                    )
                }) { Text("Open Plex", color = LocalPalette.current.accent) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = V117Muted) }
        },
        containerColor = LocalPalette.current.background
    )
}

@Composable
private fun SiloSignInDialog(
    settings: SettingsStore,
    onDismiss: () -> Unit,
    onSignedIn: (MediaServer) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var baseUrl by remember { mutableStateOf("") }
    var deviceData by remember { mutableStateOf<SiloClient.DeviceData?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    if (deviceData != null) {
        val device = deviceData!!
        LaunchedEffect(device) {
            var intervalMs = device.intervalSec * 1000L
            val deadline = SystemClock.elapsedRealtime() + device.expiresInSec * 1000L
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(intervalMs)
                when (val result = withContext(Dispatchers.IO) {
                    SiloClient.pollDeviceToken(baseUrl, device.deviceCode)
                }) {
                    is SiloClient.PollResult.Approved -> {
                        val name = withContext(Dispatchers.IO) {
                            SiloClient.getSystemInfo(baseUrl)
                        }
                        onSignedIn(
                            MediaServer(
                                id = UUID.randomUUID().toString(),
                                kind = ServerKind.SILO,
                                name = name ?: "Silo",
                                baseUrl = baseUrl.trim().removeSuffix("/"),
                                username = result.username,
                                userId = null,
                                accessToken = result.accessToken,
                                refreshToken = result.refreshToken,
                                tokenExpiresAtMs = result.expiresAtMs
                            )
                        )
                        break
                    }
                    SiloClient.PollResult.SlowDown -> intervalMs += 5000L
                    SiloClient.PollResult.Expired -> {
                        error = "That code expired. Please try again."
                        deviceData = null
                        break
                    }
                    SiloClient.PollResult.Denied -> {
                        error = "Access was denied on Silo."
                        deviceData = null
                        break
                    }
                    SiloClient.PollResult.Pending, SiloClient.PollResult.Failed -> {
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Sign in to Silo", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (deviceData == null) {
                    Text(
                        "Enter your Silo server address, then approve the code in your browser.",
                        style = MaterialTheme.typography.bodySmall,
                        color = V117Muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it.trim() },
                        label = { Text("Server URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                } else {
                    Text(
                        "Enter this code on your Silo server — or tap Open Silo to go there",
                        color = V117Muted,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deviceData!!.userCode,
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
                        color = V117Muted,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            if (deviceData == null) {
                TextButton(
                    enabled = baseUrl.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val device = withContext(Dispatchers.IO) {
                                SiloClient.requestDeviceCode(baseUrl)
                            }
                            busy = false
                            if (device == null) {
                                error = "Couldn't reach that Silo server. Check the URL."
                            } else {
                                deviceData = device
                            }
                        }
                    }
                ) { Text(if (busy) "Talking…" else "Get code") }
            } else {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(deviceData!!.verificationUrl))
                    )
                }) { Text("Open Silo", color = LocalPalette.current.accent) }
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel", color = V117Muted) }
        },
        containerColor = LocalPalette.current.background
    )
}
