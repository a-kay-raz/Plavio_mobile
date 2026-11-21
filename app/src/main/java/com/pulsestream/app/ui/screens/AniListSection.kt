package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.anilist.AniListApi
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 52: AniList link card (mirrors TraktSimklSection's shape).
 * AniList's implicit flow returns the token in the authorize URL
 * fragment, which Android strips from deep-link intents, so the user
 * copies the token from the browser URL and pastes it here. The
 * authorize link uses the user's own AniList OAuth client id
 * (anilist.co/settings/developer) — Nuvio's client id is not reused.
 */
@Composable
fun AniListSection(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val linked = remember(tick) { !settings.anilistAccessToken.isNullOrBlank() }
    val viewerName = remember(tick) { settings.anilistViewerName }
    var showDialog by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }

    SettingsGroup(
        {
            SettingsStaticRow(
                icon = Icons.Filled.Tv,
                title = "AniList",
                subtitle = if (linked) {
                    "Linked as ${viewerName ?: "AniList user"}"
                } else {
                    "Link your AniList anime list"
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
                            contentDescription = "Link AniList",
                            tint = TextSecondary
                        )
                    }
                },
                onClick = { if (linked) showUnlink = true else showDialog = true }
            )
        }
    )

    if (showDialog) {
        var clientId by remember { mutableStateOf(settings.anilistClientId ?: "") }
        var token by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) showDialog = false },
            title = { Text("Link AniList") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "1. Create an API client at anilist.co/settings/developer and paste its client ID below.\n" +
                            "2. Tap \"Open AniList to authorize\" and log in — the token appears in the browser URL after access_token=.\n" +
                            "3. Paste that token here and save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it.trim() },
                        label = { Text("AniList client ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        enabled = clientId.isNotBlank(),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse(AniListApi.authorizeUrl(clientId))
                                )
                            )
                        }
                    ) { Text("Open AniList to authorize") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text("Access token") },
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
                    enabled = token.isNotBlank() && !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            val viewer = withContext(Dispatchers.IO) {
                                AniListApi.validateToken(token)
                            }
                            busy = false
                            if (viewer != null) {
                                if (clientId.isNotBlank()) settings.anilistClientId = clientId
                                settings.anilistAccessToken = token
                                settings.anilistViewerName = viewer.name
                                tick++
                                showDialog = false
                            } else {
                                error = "That token didn't work. Check it and try again."
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
            title = { Text("Unlink AniList?") },
            text = { Text("Your AniList list will stop feeding the Library until you link again.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.anilistAccessToken = null
                    settings.anilistViewerName = null
                    tick++
                    showUnlink = false
                }) { Text("Unlink") }
            },
            dismissButton = { TextButton(onClick = { showUnlink = false }) { Text("Cancel") } }
        )
    }
}
