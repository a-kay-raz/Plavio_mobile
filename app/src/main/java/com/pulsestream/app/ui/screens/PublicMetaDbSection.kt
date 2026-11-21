package com.pulsestream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.publicmetadb.PublicMetaDbApi
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 53: PublicMetaDB link card (mirrors AniListSection's shape).
 * API-key auth: the user pastes their key from publicmetadb.com, it is
 * verified against GET /api/external/lists before saving (encrypted
 * secret, same as the other tracker tokens).
 */
@Composable
fun PublicMetaDbSection(settings: SettingsStore) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val linked = remember(tick) { !settings.publicMetaDbApiKey.isNullOrBlank() }
    var showDialog by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }

    SettingsGroup(
        {
            SettingsStaticRow(
                icon = Icons.Filled.Storage,
                title = "PublicMetaDB",
                subtitle = if (linked) {
                    "Linked — your lists, watched and resume sync here"
                } else {
                    "Link your PublicMetaDB lists"
                },
                trailing = {
                    if (linked) {
                        Text(
                            "Linked",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = LocalPalette.current.accent
                        )
                    } else {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = "Link PublicMetaDB",
                            tint = TextSecondary
                        )
                    }
                },
                onClick = { if (linked) showUnlink = true else showDialog = true }
            )
        }
    )

    if (showDialog) {
        var apiKey by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) showDialog = false },
            title = { Text("Link PublicMetaDB") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Paste your API key from publicmetadb.com. It is checked against " +
                            "your lists before saving, and stored encrypted on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it.trim() },
                        label = { Text("API key") },
                        singleLine = true,
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
                    enabled = apiKey.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                PublicMetaDbApi.verify(apiKey)
                            }
                            busy = false
                            if (ok) {
                                settings.publicMetaDbApiKey = apiKey
                                tick++
                                showDialog = false
                            } else {
                                error = "That key didn't work. Check it and try again."
                            }
                        }
                    }
                ) { Text(if (busy) "Checking…" else "Save") }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { showDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    if (showUnlink) {
        AlertDialog(
            onDismissRequest = { showUnlink = false },
            title = { Text("Unlink PublicMetaDB?") },
            text = { Text("Your PublicMetaDB lists will stop feeding the Library until you link again.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.publicMetaDbApiKey = null
                    tick++
                    showUnlink = false
                }) { Text("Unlink") }
            },
            dismissButton = { TextButton(onClick = { showUnlink = false }) { Text("Cancel") } }
        )
    }
}
