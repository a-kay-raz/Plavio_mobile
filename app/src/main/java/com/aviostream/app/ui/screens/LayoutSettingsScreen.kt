package com.aviostream.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.ui.components.CardDepthSurface
import com.aviostream.app.ui.components.CustomPosterScreen
import com.aviostream.app.ui.components.DefaultCardDepthEdgeCoverage
import com.aviostream.app.ui.components.DefaultCardDepthEdgeStrength
import com.aviostream.app.ui.components.DefaultCardDepthSheenStrength
import com.aviostream.app.ui.components.cardDepthVisual
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlin.math.roundToInt

/**
 * Nuvio v1.17 Layout page (official root: Appearance + Layout are
 * separate rows): Home catalogs, poster cards, stream badges,
 * detail sections, navigation dock and player buttons. Moved out of
 * AppearanceSettingsScreen so Appearance keeps only themes and the
 * app icon, like the official app.
 */
@Composable
fun LayoutSettingsScreen(
    repository: StreamRepository,
    onThemeChanged: () -> Unit = {},
    onGoToHomeCatalogs: () -> Unit = {},
    onGoToDetailSections: () -> Unit = {},
    onGoToStreamBadges: () -> Unit = {},
    onGoToPlayerToolbar: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val settings = repository.settings

    var cornerRadius by remember { mutableFloatStateOf(settings.posterCornerRadiusDp.toFloat()) }
    var posterWidth by remember { mutableStateOf(settings.posterCardWidthDp) }
    var posterHideLabels by remember { mutableStateOf(settings.posterHideLabels) }
    var posterLandscapeMode by remember { mutableStateOf(settings.posterCatalogLandscapeMode) }
    var posterLandscapeClearlogo by remember { mutableStateOf(settings.posterLandscapeClearlogo) }
    var customPosterPattern by remember { mutableStateOf(settings.customPosterPattern) }
    var customPosterScreens by remember { mutableStateOf(settings.customPosterScreens) }
    var cardDepthEnabled by remember { mutableStateOf(settings.cardDepthEnabled) }
    var cardDepthEdge by remember { mutableFloatStateOf(settings.cardDepthEdgeStrength.toFloat()) }
    var cardDepthSheen by remember { mutableFloatStateOf(settings.cardDepthSheenStrength.toFloat()) }
    var cardDepthCoverage by remember { mutableFloatStateOf(settings.cardDepthEdgeCoverage.toFloat()) }
    var cardDepthSurfaces by remember { mutableStateOf(settings.cardDepthSurfaces) }
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
                            text = "Layout",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Home, cards, details, streams, and navigation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "home_section") {
                SettingsSectionHeader("Home")
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.GridView,
                            title = "Home catalogs",
                            subtitle = "Choose and arrange catalogs on the Home screen",
                            onClick = onGoToHomeCatalogs
                        )
                    },
                )
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Posters",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            TextButton(
                                onClick = {
                                    cornerRadius = 12f
                                    posterWidth = 128
                                    posterHideLabels = false
                                    posterLandscapeMode = false
                                    posterLandscapeClearlogo = false
                                    settings.posterCornerRadiusDp = 12
                                    settings.posterCardWidthDp = 128
                                    settings.posterHideLabels = false
                                    settings.posterCatalogLandscapeMode = false
                                    settings.posterLandscapeClearlogo = false
                                    onThemeChanged()
                                }
                            ) { Text("Reset") }
                        }
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
                        // W10: catalog landscape mode (Nuvio
                        // PosterCustomizationSettingsPage) — 1.77 cards from
                        // the backdrop art, poster as fallback.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Catalog landscape mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f).padding(end = 16.dp)
                            )
                            Switch(
                                checked = posterLandscapeMode,
                                onCheckedChange = {
                                    posterLandscapeMode = it
                                    settings.posterCatalogLandscapeMode = it
                                    onThemeChanged()
                                }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Always show logo on landscape cards",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f).padding(end = 16.dp)
                            )
                            Switch(
                                checked = posterLandscapeClearlogo,
                                onCheckedChange = {
                                    posterLandscapeClearlogo = it
                                    settings.posterLandscapeClearlogo = it
                                    onThemeChanged()
                                }
                            )
                        }
                    }
                }
            }
            // W10: custom poster source (Nuvio CustomPosterUrlRepository) —
            // a URL pattern with {placeholders} replaces poster art on the
            // enabled surfaces; titles the pattern cannot resolve keep
            // their original art.
            item(key = "customPoster") {
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
                            text = "Custom poster source",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Use your own poster service, e.g. RatingPosterDB. {imdb_id}, {tmdb_id}, {type} and {shape} are replaced per title; titles the pattern can't resolve keep their original art.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        OutlinedTextField(
                            value = customPosterPattern,
                            onValueChange = { customPosterPattern = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Text(
                                    text = "https://example.com/posters/{imdb_id}.jpg",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            },
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            singleLine = false,
                            maxLines = 4
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = {
                                    settings.customPosterPattern = customPosterPattern.trim()
                                    onThemeChanged()
                                },
                                enabled = customPosterPattern.trim() != settings.customPosterPattern
                            ) { Text("Save") }
                            if (settings.customPosterPattern.isNotBlank()) {
                                TextButton(
                                    onClick = {
                                        customPosterPattern = ""
                                        settings.customPosterPattern = ""
                                        onThemeChanged()
                                    }
                                ) { Text("Clear") }
                            }
                        }
                        if (settings.customPosterPattern.isNotBlank()) {
                            Text(
                                text = "✓ Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalPalette.current.accent
                            )
                            Text(
                                text = "Apply to",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            listOf(
                                CustomPosterScreen.HOME to "Home",
                                CustomPosterScreen.CONTINUE_WATCHING to "Continue Watching",
                                CustomPosterScreen.COLLECTIONS to "Collections",
                                CustomPosterScreen.LIBRARY to "Library",
                                CustomPosterScreen.SEARCH to "Search",
                                CustomPosterScreen.DETAILS to "Details"
                            ).forEach { (screen, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = screen.key in customPosterScreens,
                                        onCheckedChange = { enabled ->
                                            val next = if (enabled) {
                                                customPosterScreens + screen.key
                                            } else {
                                                customPosterScreens - screen.key
                                            }
                                            customPosterScreens = next
                                            settings.customPosterScreens = next
                                            onThemeChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // W10: card depth effect (Nuvio CardDepthStyleRepository) —
            // a soft top-edge glow and sheen over poster and media cards.
            item(key = "cardDepth") {
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Card depth",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            TextButton(
                                onClick = {
                                    cardDepthEdge = DefaultCardDepthEdgeStrength.toFloat()
                                    cardDepthSheen = DefaultCardDepthSheenStrength.toFloat()
                                    cardDepthCoverage = DefaultCardDepthEdgeCoverage.toFloat()
                                    cardDepthSurfaces = emptySet()
                                    settings.cardDepthEdgeStrength = DefaultCardDepthEdgeStrength
                                    settings.cardDepthSheenStrength = DefaultCardDepthSheenStrength
                                    settings.cardDepthEdgeCoverage = DefaultCardDepthEdgeCoverage
                                    settings.cardDepthSurfaces = emptySet()
                                    onThemeChanged()
                                }
                            ) { Text("Reset") }
                        }
                        Text(
                            text = "A soft top-edge glow and sheen over poster and media cards.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Enable card depth",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Switch(
                                checked = cardDepthEnabled,
                                onCheckedChange = {
                                    cardDepthEnabled = it
                                    settings.cardDepthEnabled = it
                                    onThemeChanged()
                                }
                            )
                        }
                        if (cardDepthEnabled) {
                            Text(
                                text = "Edge glow",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "Subtle" to 28,
                                    "Balanced" to 42,
                                    "Bold" to 56
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = cardDepthEdge.roundToInt() == value,
                                        onClick = {
                                            cardDepthEdge = value.toFloat()
                                            settings.cardDepthEdgeStrength = value
                                            onThemeChanged()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Slider(
                                value = cardDepthEdge,
                                onValueChange = { cardDepthEdge = it },
                                valueRange = 0f..100f,
                                onValueChangeFinished = {
                                    settings.cardDepthEdgeStrength = cardDepthEdge.roundToInt()
                                    onThemeChanged()
                                }
                            )
                            Text(
                                text = "Sheen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "Off" to 0,
                                    "Soft" to 10,
                                    "Bright" to 16
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = cardDepthSheen.roundToInt() == value,
                                        onClick = {
                                            cardDepthSheen = value.toFloat()
                                            settings.cardDepthSheenStrength = value
                                            onThemeChanged()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Slider(
                                value = cardDepthSheen,
                                onValueChange = { cardDepthSheen = it },
                                valueRange = 0f..100f,
                                onValueChangeFinished = {
                                    settings.cardDepthSheenStrength = cardDepthSheen.roundToInt()
                                    onThemeChanged()
                                }
                            )
                            Text(
                                text = "Edge coverage",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(
                                    "Top" to 0,
                                    "Half" to 50,
                                    "Full" to 100
                                ).forEach { (label, value) ->
                                    FilterChip(
                                        selected = cardDepthCoverage.roundToInt() == value,
                                        onClick = {
                                            cardDepthCoverage = value.toFloat()
                                            settings.cardDepthEdgeCoverage = value
                                            onThemeChanged()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }
                            Slider(
                                value = cardDepthCoverage,
                                onValueChange = { cardDepthCoverage = it },
                                valueRange = 0f..100f,
                                onValueChangeFinished = {
                                    settings.cardDepthEdgeCoverage = cardDepthCoverage.roundToInt()
                                    onThemeChanged()
                                }
                            )
                            // Live preview driven by the draft slider values.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                Color(0xFF33415C),
                                                Color(0xFF232D42),
                                                Color(0xFF141A28)
                                            )
                                        )
                                    )
                                    .cardDepthVisual(
                                        shape = RoundedCornerShape(14.dp),
                                        edgeStrength = cardDepthEdge,
                                        sheenStrength = cardDepthSheen,
                                        edgeCoverage = cardDepthCoverage
                                    )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "MOVIE · 2026",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White.copy(alpha = 0.78f)
                                    )
                                    Text(
                                        text = "Preview title",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = "Apply to",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            listOf(
                                CardDepthSurface.Posters to "Posters",
                                CardDepthSurface.ContinueWatching to "Continue Watching",
                                CardDepthSurface.EpisodeCards to "Episode cards",
                                CardDepthSurface.Cast to "Cast",
                                CardDepthSurface.Trailers to "Trailers"
                            ).forEach { (surface, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = surface.key in cardDepthSurfaces,
                                        onCheckedChange = { enabled ->
                                            val next = if (enabled) {
                                                cardDepthSurfaces + surface.key
                                            } else {
                                                cardDepthSurfaces - surface.key
                                            }
                                            cardDepthSurfaces = next
                                            settings.cardDepthSurfaces = next
                                            onThemeChanged()
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item(key = "streams_section") {
                SettingsSectionHeader("Streams")
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Style,
                            title = "Stream badges",
                            subtitle = "Import badge rules shown on matching streams",
                            onClick = onGoToStreamBadges
                        )
                    },
                )
            }
            item(key = "detail_section") {
                SettingsSectionHeader("Detail page")
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Tune,
                            title = "Detail sections",
                            subtitle = "Reorder and hide the detail page's sections",
                            onClick = onGoToDetailSections
                        )
                    },
                )
            }
            item(key = "nav_header") {
                SettingsSectionHeader("Navigation bar style")
            }
            // Step 70: navigation dock style (Nuvio's NavBarStyle,
            // Avio scope: Adaptive = the current shrink-on-scroll dock,
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
            item(key = "player_buttons_section") {
                SettingsSectionHeader("Player")
                SettingsGroup(
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
