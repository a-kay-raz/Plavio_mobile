package com.aviostream.app.ui.screens

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.R
import com.aviostream.app.data.AppIcon
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.AccentTheme
import com.aviostream.app.ui.theme.AppTheme
import com.aviostream.app.ui.theme.GlassBorder
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import com.aviostream.app.ui.theme.paletteForTheme
import com.aviostream.app.ui.theme.parseHexColor

@Composable
fun AppearanceSettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var accentName by remember { mutableStateOf(settings.accentName) }
    // Step 70 state.
    var customHex by remember { mutableStateOf(settings.customAccentHex) }
    var hexError by remember { mutableStateOf(false) }
    var appIcon by remember { mutableStateOf(settings.appIcon) }
    // Six-theme choice (SettingsStore.appTheme).
    var appTheme by remember { mutableStateOf(settings.appTheme) }
    // W10: landscape mode, custom poster source, card depth.

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
                        "Pick your accent colour and theme.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "accent") {
                // Accent circles (owner's order 2026-10-25, from his
                // reference): one card, solid colour circles in rows of
                // three, the selected one ringed with a check.
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
                                                        theme == AccentTheme.GOLD ||
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
            // Six-theme picker (SettingsStore.appTheme) — same rows-of-
            // three style as the accent picker above; each swatch shows
            // the theme's own background. The tap writes the stored value
            // and onThemeChanged() makes MainActivity re-read it via
            // AppTheme.byName, so the whole app re-skins at once.
            item(key = "app-theme") {
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = "Theme",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Six looks — the whole app re-skins when you pick one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                        AppTheme.values().toList().chunked(3).forEach { rowThemes ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                rowThemes.forEach { theme ->
                                    val selected = theme.name == appTheme
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(58.dp)
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    paletteForTheme(
                                                        theme,
                                                        AccentTheme.byName(accentName)
                                                    ).background
                                                )
                                                .border(
                                                    if (selected) 2.5.dp else 1.dp,
                                                    if (selected) LocalPalette.current.accent else GlassBorder,
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    appTheme = theme.name
                                                    settings.appTheme = theme.name
                                                    onThemeChanged()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (selected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "${theme.label} selected",
                                                    tint = if (theme.light) Color.Black else Color.White,
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
                                    }
                                }
                            }
                        }
                        AppTheme.values().firstOrNull { it.name == appTheme }?.let { current ->
                            Text(
                                text = current.blurb,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
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
        }
    }
}

