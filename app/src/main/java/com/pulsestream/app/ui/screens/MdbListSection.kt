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
import androidx.compose.material.icons.filled.List
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
import com.pulsestream.app.data.mdblist.MdbListApi
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Step 54: MDBList link card (mirrors WeTrakrSection's device-code
 * shape). The user creates their own app at mdblist.com, pastes its
 * client id, approves the code, and the poll loop saves the access +
 * refresh tokens. Nuvio's client id is not reused.
 */
@Composable
fun MdbListSection(settings: SettingsStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val linked = remember(tick) { !settings.mdblistAccessToken.isNullOrBlank() }
    var showDialog by remember { mutableStateOf(false) }
    var showUnlink by remember { mutableStateOf(false) }
    var deviceData by remember { mutableStateOf<MdbListApi.DeviceData?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    SettingsGroup(
        {
            SettingsStaticRow(
                icon = Icons.Filled.List,
                title = "MDBList",
                subtitle = if (linked) {
                    "Linked — your MDBList lists and history"
                } else {
                    "Link your MDBList lists and ratings"
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
                            contentDescription = "Link MDBList",
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
                val clientId = settings.mdblistClientId ?: break
                when (val result = withContext(Dispatchers.IO) {
                    MdbListApi.pollDeviceToken(clientId, device.deviceCode)
                }) {
                    is MdbListApi.PollResult.Approved -> {
                        settings.mdblistAccessToken = result.accessToken
                        settings.mdblistRefreshToken = result.refreshToken
                        settings.mdblistTokenExpiresAtMs = result.expiresAtMs
                        tick++
                        showDialog = false
                        deviceData = null
                        break
                    }
                    MdbListApi.PollResult.SlowDown -> intervalMs += 5000L
                    MdbListApi.PollResult.Expired -> {
                        pinError = "That code expired. Please try again."
                        deviceData = null
                        break
                    }
                    MdbListApi.PollResult.Denied -> {
                        pinError = "Access was denied on MDBList."
                        deviceData = null
                        break
                    }
                    MdbListApi.PollResult.Pending, MdbListApi.PollResult.Failed -> {
                    }
                }
            }
        }
    }

    if (showDialog) {
        var clientId by remember { mutableStateOf(settings.mdblistClientId ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { if (!busy) { showDialog = false; deviceData = null; pinError = null } },
            title = { Text("Link MDBList", color = TextPrimary) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        deviceData == null -> {
                            Text(
                                "Create an app at mdblist.com (developer settings) and paste its client ID below. " +
                                    "Then approve the code — this window closes automatically once you do.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = clientId,
                                onValueChange = { clientId = it.trim() },
                                label = { Text("MDBList client ID") },
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
                                "Enter this code on MDBList — or tap Open MDBList to go there",
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
                                    MdbListApi.requestDeviceCode(clientId)
                                }
                                busy = false
                                if (device == null) {
                                    error = "Couldn't reach MDBList. Check your connection and try again."
                                } else {
                                    settings.mdblistClientId = clientId
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
                    }) { Text("Open MDBList", color = LocalPalette.current.accent) }
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
            title = { Text("Unlink MDBList?") },
            text = { Text("Your MDBList lists will stop feeding the Library until you link again.") },
            confirmButton = {
                TextButton(onClick = {
                    settings.mdblistAccessToken = null
                    settings.mdblistRefreshToken = null
                    settings.mdblistTokenExpiresAtMs = 0L
                    tick++
                    showUnlink = false
                }) { Text("Unlink") }
            },
            dismissButton = { TextButton(onClick = { showUnlink = false }) { Text("Cancel") } }
        )
    }
}
