package com.pulsestream.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.R
import com.pulsestream.app.data.AppIcon
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.AccentTheme
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import com.pulsestream.app.ui.theme.parseHexColor
import com.pulsestream.app.ui.tv.tvFocusRing
import kotlin.math.roundToInt

@Composable
fun AppearanceSettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onGoToHomeCatalogs: () -> Unit = {},
    onGoToDetailSections: () -> Unit = {},
    onGoToStreamBadges: () -> Unit = {},
    onGoToPlayerToolbar: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var accentName by remember { mutableStateOf(settings.accentName) }
    // Step 70 state.
    var customHex by remember { mutableStateOf(settings.customAccentHex) }
    var hexError by remember { mutableStateOf(false) }
    var appIcon by remember { mutableStateOf(settings.appIcon) }
    var cornerRadius by remember { mutableFloatStateOf(settings.posterCornerRadiusDp.toFloat()) }
    var posterWidth by remember { mutableStateOf(settings.posterCardWidthDp) }
    var posterHideLabels by remember { mutableStateOf(settings.posterHideLabels) }
    var dockStyle by remember { mutableStateOf(settings.dockStyle) }

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
                            text = "Appearance",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Pick your accent colour.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "accent") {
                // Accent circles (owner's order 2026-10-25, from his
                // reference): one card, solid colour circles in rows of
                // three, the selected one ringed with a check — the ONLY
                // appearance choice now that Onyx is permanent.
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        AccentTheme.values().toList().chunked(3).forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowThemes.forEach { theme ->
                                    val selected = theme.name == accentName
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(theme.swatchBrush)
                                                .border(
                                                    if (selected) 2.5.dp else 1.dp,
                                                    if (selected) theme.primary else GlassBorder,
                                                    RoundedCornerShape(50)
                                                )
                                                .clickable {
                                                    accentName = theme.name
                                                    settings.accentName = theme.name
                                                    // A preset accent clears any custom
                                                    // hex, so the tap visibly takes effect.
                                                    settings.customAccentHex = ""
                                                    customHex = ""
                                                    hexError = false
                                                    onThemeChanged()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "${theme.label} selected",
                                                    tint = if (theme == AccentTheme.WHITE ||
                                                        theme == AccentTheme.AMBER ||
                                                        theme == AccentTheme.EMERALD
                                                    ) Color.Black else Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = theme.label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (selected) TextPrimary else TextSecondary
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Box(
                                            Modifier
                                                .width(26.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(theme.primary)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // Step 70: custom accent — any hex colour, overriding the preset
            // above (Nuvio's CustomThemeEditor, single-colour scope: no
            // gradient toggle; the preset's secondary is not customisable).
            item(key = "custom-accent") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Custom accent",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Any hex colour, e.g. #7C3AED. It overrides the preset accent above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = customHex,
                                onValueChange = { customHex = it; hexError = false },
                                placeholder = { Text("#RRGGBB") },
                                singleLine = true,
                                isError = hexError,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(
                                        parseHexColor(customHex) ?: Color.Transparent
                                    )
                                    .border(1.dp, GlassBorder, RoundedCornerShape(50))
                            )
                        }
                        if (hexError) {
                            Text(
                                text = "Not a valid hex colour — use #RRGGBB.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFF43F5E)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                val parsed = parseHexColor(customHex)
                                if (parsed == null) {
                                    hexError = true
                                } else {
                                    settings.customAccentHex = customHex.trim()
                                    hexError = false
                                    onThemeChanged()
                                }
                            }) { Text("Apply") }
                            if (settings.customAccentHex.isNotEmpty()) {
                                TextButton(onClick = {
                                    settings.customAccentHex = ""
                                    customHex = ""
                                    hexError = false
                                    onThemeChanged()
                                }) { Text("Use preset") }
                            }
                        }
                    }
                }
            }
            // Step 70: app-icon picker (AppIcon.kt) — the same foreground
            // on three backgrounds, via activity aliases.
            item(key = "app-icon") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "App icon",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "The launcher icon. It may take a moment to appear on the home screen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AppIcon.VARIANTS.forEach { variant ->
                                val selected = variant == appIcon
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(58.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                when (variant) {
                                                    AppIcon.BLACK -> Color.Black
                                                    AppIcon.WHITE -> Color.White
                                                    else -> Color(0xFF1A1A1A)
                                                }
                                            )
                                            .border(
                                                if (selected) 2.5.dp else 1.dp,
                                                if (selected) LocalPalette.current.accent else GlassBorder,
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                appIcon = variant
                                                settings.appIcon = variant
                                                AppIcon.apply(context, variant)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Image(
                                            painter = painterResource(R.mipmap.ic_launcher_foreground),
                                            contentDescription = "${AppIcon.label(variant)} icon",
                                            modifier = Modifier.size(44.dp)
                                        )
                                        if (selected) {
                                            Icon(
                                                Icons.Filled.Check,
                                                contentDescription = "${AppIcon.label(variant)} selected",
                                                tint = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = AppIcon.label(variant),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selected) TextPrimary else TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // Step 70: poster customization (PosterStyle.kt) — corner
            // radius, card width, hide labels.
            item(key = "posters") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Posters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Corner radius",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "${cornerRadius.roundToInt()}dp",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        Slider(
                            value = cornerRadius,
                            onValueChange = { cornerRadius = it },
                            valueRange = 0f..24f,
                            steps = 23,
                            onValueChangeFinished = {
                                settings.posterCornerRadiusDp = cornerRadius.roundToInt()
                                onThemeChanged()
                            }
                        )
                        Text(
                            text = "Card width",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(112, 128, 144).forEach { width ->
                                FilterChip(
                                    selected = posterWidth == width,
                                    onClick = {
                                        posterWidth = width
                                        settings.posterCardWidthDp = width
                                        onThemeChanged()
                                    },
                                    label = { Text("${width}dp") }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hide titles under posters",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Switch(
                                checked = posterHideLabels,
                                onCheckedChange = {
                                    posterHideLabels = it
                                    settings.posterHideLabels = it
                                    onThemeChanged()
                                }
                            )
                        }
                    }
                }
            }
            // Step 70: navigation dock style (Nuvio's NavBarStyle,
            // Plavio scope: Adaptive = the current shrink-on-scroll dock,
            // Compact = icons only, always shrunk. Position stays bottom —
            // the dock's blur anchoring and the tabs' bottom padding are
            // built around the bottom seat.)
            item(key = "dock") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Navigation dock",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Compact keeps the dock shrunk — icons only, no labels.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = dockStyle == "adaptive",
                                onClick = {
                                    dockStyle = "adaptive"
                                    settings.dockStyle = "adaptive"
                                    onThemeChanged()
                                },
                                label = { Text("Adaptive") }
                            )
                            FilterChip(
                                selected = dockStyle == "compact",
                                onClick = {
                                    dockStyle = "compact"
                                    settings.dockStyle = "compact"
                                    onThemeChanged()
                                },
                                label = { Text("Compact") }
                            )
                        }
                    }
                }
            }
            // Nuvio v1.17 Appearance page: nav rows to the layout
            // sub-pages (Home catalogs, Detail sections, Stream badges,
            // Player buttons) live here, not at root.
            item(key = "layout-pages") {
                SettingsSectionHeader("Layout")
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.GridView,
                            title = "Home catalogs",
                            subtitle = "Choose and arrange catalogs on the Home screen",
                            onClick = onGoToHomeCatalogs
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Tune,
                            title = "Detail sections",
                            subtitle = "Reorder and hide the detail page's sections",
                            onClick = onGoToDetailSections
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Style,
                            title = "Stream badges",
                            subtitle = "Import badge rules shown on matching streams",
                            onClick = onGoToStreamBadges
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Dashboard,
                            title = "Player buttons",
                            subtitle = "Reorder and hide the player's pill and top buttons",
                            onClick = onGoToPlayerToolbar
                        )
                    }
                )
            }
        }
    }
}

