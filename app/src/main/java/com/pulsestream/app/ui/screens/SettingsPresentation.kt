package com.pulsestream.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.TextPrimary

/**
 * Nuvio-Enhanced v1.17 presentation kit (gap report §2 — measured
 * values from `SettingsComponents.kt` / `Tokens.kt` / `Components.kt`):
 * 36dp icon tile (accent @ 0.12, 12dp radius), gradient switch
 * 52x32dp / 24dp thumb / 180ms tween, 12sp Bold section labels
 * (ls 0.8sp, muted, no uppercase), 0.5dp dividers inset 66dp,
 * muted subtitle #969CA3, rows 16dp / 14dp, no built-in chevron.
 *
 * The shared rows in SettingsScreen.kt still render the old flat
 * style (bare 24dp icon, stock M3 Switch, built-in chevron); these
 * V117 rows render the v1.17 values directly so the remaining
 * settings screens don't wait on that file. When the shared rows are
 * updated, these can delegate to them.
 */

/** Nuvio `textMuted` — #969CA3 (Avio's TextSecondary is #B8BEC5). */
val V117Muted = Color(0xFF969CA3)

/**
 * Bare section label (Nuvio `NuvioSectionLabel`): labelMedium 12sp
 * Bold, ls 0.8sp, muted, no uppercase. No padding baked in — the
 * caller adds it (list-level headers use [SettingsSectionHeader]).
 */
@Composable
fun V117SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        ),
        color = V117Muted
    )
}

/** Caller-supplied trailing chevron (Nuvio nav rows have no built-in one). */
@Composable
fun V117Chevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = V117Muted,
        modifier = Modifier.size(20.dp)
    )
}

/** Standalone divider: 0.5dp, inset 66dp phone (Nuvio hairline). */
@Composable
fun V117Divider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 66.dp),
        thickness = 0.5.dp,
        color = GlassBorder
    )
}

@Composable
private fun V117RowContent(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    trailing: @Composable RowScope.() -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            SettingsIconTile(icon, title)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        trailing()
    }
}

/** Toggle row: 36dp icon tile + gradient switch, 16dp / 14dp. */
@Composable
fun V117ToggleRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Box(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }
    ) {
        V117RowContent(icon, title, subtitle) {
            SettingsGradientSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

/**
 * Navigation row: no built-in chevron — pass one via [trailing]
 * (e.g. [V117Chevron]) when the row opens another page/picker.
 */
@Composable
fun V117NavRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Box(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        V117RowContent(icon, title, subtitle) {
            trailing?.invoke(this)
        }
    }
}

/** Non-interactive / action row with a caller-supplied trailing slot. */
@Composable
fun V117StaticRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    dimmed: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.6f else 1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        V117RowContent(icon, title, subtitle) {
            trailing?.invoke(this)
        }
    }
}
