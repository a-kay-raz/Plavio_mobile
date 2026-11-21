package com.pulsestream.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.data.wetrakr.WeTrakrApi
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 53: WeTrakr link card (mirrors TraktSimklSection's Simkl shape).
 * Device-code OAuth: the user creates their own app at wetrakr.com,
 * pastes its client id, approves the code at wetrakr.com/activate, and
 * the poll loop saves the access + refresh tokens. Nuvio's client id
 * is not reused.
 */
@Composable
fun WeTrakrSection(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val linked = remember(tick) { !settings.wetrakrAccessToken.isNullOrBlank() }
    val username = remember(tick) { settings.wetrakrUsername }
    var showDialog by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }
    var deviceData by remember { mutableStateOf<WeTrakrApi.DeviceData?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    SettingsGroup(
        {
            SettingsStaticRow(
                icon = Icons.Filled.Sync,
                title = "WeTrakr",
                subtitle = if (linked) {
                    "Linked as ${username ?: "WeTrakr user"}"
                } else {
                    "Link your WeTrakr watchlist"
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
                            contentDescription = "Link WeTrakr",
                            tint = TextSecondary
                        )
                    }
                },
                onClick = { if (linked) showUnlink = true else showDialog = true }
            )
        }
    )

    if (showDialog && deviceData != null) {
        LaunchedEffect(deviceData) {
            val device = deviceData ?: return@LaunchedEffect
            var intervalMs = device.intervalSec * 1000L
            val deadline = SystemClock.elapsedRealtime() + device.expiresInSec * 1000L
            while (SystemClock.elapsedRealtime() < deadline) {
                delay(intervalMs)
                val clientId = settings.wetrakrClientId ?: break
                when (val result = withContext(Dispatchers.IO) {
                    WeTrakrApi.pollDeviceToken(clientId, device.deviceCode)
                }) {
                    is WeTrakrApi.PollResult.Approved -> {
                        settings.wetrakrAccessToken = result.accessToken
                        settings.wetrakrRefreshToken = result.refreshToken
                        settings.wetrakrTokenExpiresAtMs = result.expiresAtMs
                        settings.wetrakrUsername =
                            withContext(Dispatchers.IO) { WeTrakrApi.fetchUsername(settings) }
                        tick++
                        showDialog = false
                        deviceData = null
                        break
                    }
                    WeTrakrApi.PollResult.SlowDown -> intervalMs += 5000L
                    WeTrakrApi.PollResult.Expired -> {
                        pinError = "That code expired. Please try again."
                        deviceData = null
                        break
                    }
                    WeTrakrApi.PollResult.Denied -> {
                        pinError = "Access was denied on WeTrakr."
                        deviceData = null
                        break
                    }
                    WeTrakrApi.PollResult.Pending, WeTrakrApi.PollResult.Failed -> {
                    }
                }
            }
        }
    }

    if (showDialog) {
        var clientId by remember { mutableStateOf(settings.wetrakrClientId ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) { showDialog = false; deviceData = null; pinError = null } },
            title = { Text("Link WeTrakr", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        deviceData == null -> {
                            Text(
                                "Create an app at wetrakr.com (developer settings) and paste its client ID below. " +
                                    "Then approve the code at wetrakr.com/activate — this window closes automatically once you do.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it.trim() },
                                label = { Text("WeTrakr client ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (error != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    error!!,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        else -> {
                            Text(
                                "Enter this code at wetrakr.com/activate — or tap Open WeTrakr to go there",
                                color = TextSecondary,
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
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            if (pinError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    pinError!!,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (deviceData == null) {
                    TextButton(
                        enabled = clientId.isNotBlank() && !busy,
                        onClick = {
                            busy = true
                            error = null
                            pinError = null
                            scope.launch {
                                val device = withContext(Dispatchers.IO) {
                                    WeTrakrApi.requestDeviceCode(clientId)
                                }
                                busy = false
                                if (device == null) {
                                    error = "Couldn't reach WeTrakr. Check your connection and try again."
                                } else {
                                    settings.wetrakrClientId = clientId
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
                    }) { Text("Open WeTrakr", color = LocalPalette.current.accent) }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !busy,
                    onClick = { showDialog = false; deviceData = null; pinError = null }
                ) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = androidx.compose.ui.graphics.Color(0xFF14141F)
        )
    }

    if (showUnlink) {
        AlertDialog(
            onDismissRequest = { showUnlink = false },
            title = { Text("Unlink WeTrakr?") },
            text = { Text("Your WeTrakr list will stop feeding the Library until you link again.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.wetrakrAccessToken = null
                    settings.wetrakrRefreshToken = null
                    settings.wetrakrTokenExpiresAtMs = 0L
                    settings.wetrakrUsername = null
                    tick++
                    showUnlink = false
                }) { Text("Unlink") }
            },
            dismissButton = { TextButton(onClick = { showUnlink = false }) { Text("Cancel") } }
        )
    }
}
