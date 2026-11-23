package com.aviostream.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.DebugLog
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.tv.tvFocusRing

@Composable
fun AboutScreen(
    onBack: () -> Unit = {},
    onGoToLicenses: () -> Unit = {},
    onGoToWhatsNew: () -> Unit = {}
) {
    GlowBackground {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "About",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            item(key = "aboutCard") { AboutCard() }
            item(key = "updater") { AppUpdaterCard() }
            item(key = "whats_new") { WhatsNewRow(onGoToWhatsNew = onGoToWhatsNew) }
            item(key = "support") { SupportRows() }
            item(key = "legal") { LegalRows(onGoToLicenses = onGoToLicenses) }
        }
    }
}

/**
 * What's New row (Nuvio): opens the changelog screen.
 * Grouped v1.17 card (gap report §2): 24dp radius, 0.5dp dividers
 * inset 66dp between rows; rows are 16dp / 14dp with a 36dp icon
 * tile and a caller-supplied chevron.
 */
@Composable
private fun WhatsNewRow(onGoToWhatsNew: () -> Unit = {}) {
    SettingsGroup(
        {
            LegalRow(
                icon = Icons.Filled.NewReleases,
                title = "What's New",
                onClick = onGoToWhatsNew
            )
        }
    )
}

/**
 * Support rows: share a debug log (app/device facts, source diagnostics,
 * redacted logcat) so a user can send it when reporting a problem.
 */
@Composable
private fun SupportRows() {
    val context = LocalContext.current
    SettingsGroup(
        {
            LegalRow(
                icon = Icons.Filled.BugReport,
                title = "Share debug log",
                onClick = { DebugLog.share(context) }
            )
        }
    )
}

/**
 * Legal rows: open-source licenses.
 */
@Composable
private fun LegalRows(
    onGoToLicenses: () -> Unit = {}
) {
    SettingsGroup(
        {
            LegalRow(
                icon = Icons.Filled.Description,
                title = "Open source licenses",
                onClick = onGoToLicenses
            )
        }
    )
}

@Composable
private fun LegalRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .tvFocusRing(RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsIconTile(icon, title)
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        V117Chevron()
    }
}
