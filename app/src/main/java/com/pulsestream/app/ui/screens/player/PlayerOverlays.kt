package com.pulsestream.app.ui.screens.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Player overlays ported from Nuvio (P29/P30/P31): the device-status
 * strip (clock + battery + network), the pause metadata overlay, and
 * the staged loading text (PlayerScreen computes the step).
 */

enum class PlayerDeviceNetworkType {
    Wifi,
    Cellular,
    Offline,
    Unknown,
}

data class PlayerDeviceStatus(
    val timeLabel: String,
    val batteryPercent: Int?,
    val batteryCharging: Boolean,
    val networkType: PlayerDeviceNetworkType,
)

/** Nuvio's Android rememberPlayerDeviceStatus: 1s refresh + battery receiver. */
@Composable
fun rememberPlayerDeviceStatus(): PlayerDeviceStatus {
    val context = LocalContext.current.applicationContext
    var status by remember(context) {
        mutableStateOf(readPlayerDeviceStatus(context, readBatteryStatus(context)))
    }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val appCtx = context?.applicationContext ?: return
                status = readPlayerDeviceStatus(appCtx, intent)
            }
        }
        val stickyIntent = if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        if (stickyIntent != null) {
            status = readPlayerDeviceStatus(context, stickyIntent)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(context) {
        while (true) {
            status = readPlayerDeviceStatus(context, readBatteryStatus(context))
            delay(1_000L)
        }
    }

    return status
}

private fun readPlayerDeviceStatus(context: Context, batteryStatus: Intent?): PlayerDeviceStatus =
    PlayerDeviceStatus(
        timeLabel = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date()),
        batteryPercent = readBatteryPercent(context, batteryStatus),
        batteryCharging = isBatteryCharging(batteryStatus),
        networkType = readNetworkType(context),
    )

private fun readBatteryStatus(context: Context): Intent? =
    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

private fun readBatteryPercent(context: Context, batteryStatus: Intent?): Int? {
    val managerPercent = (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
        ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }
    if (managerPercent != null) return managerPercent
    if (batteryStatus == null) return null
    val level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    if (level < 0 || scale <= 0) return null
    return ((level * 100f) / scale).roundToInt().coerceIn(0, 100)
}

private fun isBatteryCharging(batteryStatus: Intent?): Boolean {
    if (batteryStatus == null) return false
    return when (batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
        BatteryManager.BATTERY_STATUS_CHARGING,
        BatteryManager.BATTERY_STATUS_FULL -> true
        else -> false
    }
}

private fun readNetworkType(context: Context): PlayerDeviceNetworkType {
    val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return PlayerDeviceNetworkType.Unknown
    val network = connectivity.activeNetwork ?: return PlayerDeviceNetworkType.Offline
    val capabilities = connectivity.getNetworkCapabilities(network) ?: return PlayerDeviceNetworkType.Unknown
    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> PlayerDeviceNetworkType.Wifi
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> PlayerDeviceNetworkType.Cellular
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> PlayerDeviceNetworkType.Unknown
        else -> PlayerDeviceNetworkType.Offline
    }
}

/** Top-center pill: network + clock + battery (Nuvio PlayerDeviceStatusOverlay, minus the finish-clock — Avio already has an "Ends at" row). */
@Composable
fun PlayerDeviceStatusStrip(
    status: PlayerDeviceStatus,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val (networkIcon, networkLabel) = when (status.networkType) {
                PlayerDeviceNetworkType.Wifi -> Icons.Filled.Wifi to "Wi-Fi"
                PlayerDeviceNetworkType.Cellular -> Icons.Filled.SignalCellularAlt to "LTE"
                PlayerDeviceNetworkType.Offline -> Icons.Filled.WifiOff to "Offline"
                PlayerDeviceNetworkType.Unknown -> Icons.Filled.Wifi to "Net"
            }
            Icon(
                networkIcon, contentDescription = networkLabel,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(14.dp)
            )
            Text(
                networkLabel,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                status.timeLabel,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
            val percent = status.batteryPercent
            if (percent != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (status.batteryCharging) Icons.Filled.BatteryChargingFull
                        else if (percent >= 50) Icons.Filled.BatteryFull else Icons.Filled.BatteryStd,
                        contentDescription = if (status.batteryCharging) "Charging" else "Battery",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "$percent%",
                        color = Color.White.copy(alpha = 0.92f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Pause metadata overlay (Nuvio PauseMetadataOverlay): a left-to-right
 * gradient with "You're watching" + big title + episode line + provider,
 * bottom-anchored above the bottom controls. Drawn under the transport
 * controls so they stay usable.
 */
@Composable
fun PauseMetadataOverlay(
    title: String,
    episodeLine: String?,
    providerName: String?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.85f),
                        Color.Black.copy(alpha = 0.45f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 150.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Text(
                "You're watching",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFB8B8B8),
            )
            Text(
                title,
                color = Color.White,
                fontSize = 28.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (episodeLine != null) {
                Text(
                    episodeLine,
                    color = Color.White.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (!providerName.isNullOrBlank()) {
                Text(
                    "via $providerName",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
