package com.pulsestream.app.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.AUTOPLAY_QUALITY_OPTIONS
import com.pulsestream.app.data.AUTOPLAY_SIZE_OPTIONS
import com.pulsestream.app.data.QUALITY_ORDER_OPTIONS
import com.pulsestream.app.data.SmartStreamQuality
import com.pulsestream.app.data.StreamItem
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.formatDebridStreamText
import com.pulsestream.app.data.autoplaySizeName
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.tv.tvFocusRing

/**
 * Settings > Streams (v0.8.31+): how the stream picker lists sources and
 * how the Play button auto-picks a stream.
 */
@Composable
fun StreamsSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    var perSource by remember { mutableStateOf(settings.streamsPerSource) }
    var qualityOrder by remember { mutableStateOf(settings.qualityOrder) }
    var autoplay by remember { mutableStateOf(settings.streamAutoplay) }
    var autoplayQuality by remember { mutableStateOf(settings.autoplayQuality) }
    var autoplayMaxSize by remember { mutableStateOf(settings.autoplayMaxSizeGb) }
    var qualityPickerOpen by remember { mutableStateOf(false) }
    var sizePickerOpen by remember { mutableStateOf(false) }
    // Step 44 — Debrid stream preferences.
    var debridMinQuality by remember { mutableStateOf(settings.debridMinQuality) }
    var debridDvFilter by remember { mutableStateOf(settings.debridDvFilter) }
    var debridHdrFilter by remember { mutableStateOf(settings.debridHdrFilter) }
    var debridCodecFilter by remember { mutableStateOf(settings.debridCodecFilter) }
    var debridPreferredTags by remember { mutableStateOf(settings.debridPreferredTags.joinToString(", ")) }
    var debridRequiredTags by remember { mutableStateOf(settings.debridRequiredTags.joinToString(", ")) }
    var debridExcludedTags by remember { mutableStateOf(settings.debridExcludedTags.joinToString(", ")) }
    var debridNameTemplate by remember { mutableStateOf(settings.debridNameTemplate) }
    var debridDescriptionTemplate by remember { mutableStateOf(settings.debridDescriptionTemplate) }
    var minQualityPickerOpen by remember { mutableStateOf(false) }
    var dvPickerOpen by remember { mutableStateOf(false) }
    var hdrPickerOpen by remember { mutableStateOf(false) }
    var codecPickerOpen by remember { mutableStateOf(false) }

    if (qualityPickerOpen) {
        SingleSelectDialog(
            title = "Preferred quality",
            options = AUTOPLAY_QUALITY_OPTIONS,
            selected = autoplayQuality,
            onDismiss = { qualityPickerOpen = false },
            onSelect = {
                autoplayQuality = it
                settings.autoplayQuality = it
                qualityPickerOpen = false
            }
        )
    }
    if (sizePickerOpen) {
        SingleSelectDialog(
            title = "Max file size",
            options = AUTOPLAY_SIZE_OPTIONS.map { it.second },
            selected = autoplaySizeName(autoplayMaxSize),
            onDismiss = { sizePickerOpen = false },
            onSelect = { label ->
                val gb = AUTOPLAY_SIZE_OPTIONS.find { it.second == label }?.first ?: 0
                autoplayMaxSize = gb
                settings.autoplayMaxSizeGb = gb
                sizePickerOpen = false
            }
        )
    }

    if (minQualityPickerOpen) {
        SingleSelectDialog(
            title = "Minimum quality",
            options = listOf("Any", "720p", "1080p", "4K"),
            selected = when (debridMinQuality) {
                "720" -> "720p"; "1080" -> "1080p"; "2160" -> "4K"; else -> "Any"
            },
            onDismiss = { minQualityPickerOpen = false },
            onSelect = { label ->
                debridMinQuality = when (label) {
                    "720p" -> "720"; "1080p" -> "1080"; "4K" -> "2160"; else -> "any"
                }
                settings.debridMinQuality = debridMinQuality
                minQualityPickerOpen = false
            }
        )
    }
    if (dvPickerOpen) {
        SingleSelectDialog(
            title = "Dolby Vision",
            options = listOf("Any", "Only DV", "Exclude DV"),
            selected = when (debridDvFilter) {
                "only" -> "Only DV"; "exclude" -> "Exclude DV"; else -> "Any"
            },
            onDismiss = { dvPickerOpen = false },
            onSelect = { label ->
                debridDvFilter = when (label) {
                    "Only DV" -> "only"; "Exclude DV" -> "exclude"; else -> "any"
                }
                settings.debridDvFilter = debridDvFilter
                dvPickerOpen = false
            }
        )
    }
    if (hdrPickerOpen) {
        SingleSelectDialog(
            title = "HDR",
            options = listOf("Any", "Only HDR", "Exclude HDR"),
            selected = when (debridHdrFilter) {
                "only" -> "Only HDR"; "exclude" -> "Exclude HDR"; else -> "Any"
            },
            onDismiss = { hdrPickerOpen = false },
            onSelect = { label ->
                debridHdrFilter = when (label) {
                    "Only HDR" -> "only"; "Exclude HDR" -> "exclude"; else -> "any"
                }
                settings.debridHdrFilter = debridHdrFilter
                hdrPickerOpen = false
            }
        )
    }
    if (codecPickerOpen) {
        SingleSelectDialog(
            title = "Codec",
            options = listOf("Any", "H.264", "HEVC", "AV1"),
            selected = when (debridCodecFilter) {
                "h264" -> "H.264"; "hevc" -> "HEVC"; "av1" -> "AV1"; else -> "Any"
            },
            onDismiss = { codecPickerOpen = false },
            onSelect = { label ->
                debridCodecFilter = when (label) {
                    "H.264" -> "h264"; "HEVC" -> "hevc"; "AV1" -> "av1"; else -> "any"
                }
                settings.debridCodecFilter = debridCodecFilter
                codecPickerOpen = false
            }
        )
    }

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
                            text = "Streams",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "How sources are listed, ordered and auto-picked.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            item(key = "perSource") {
                // Flat v1.17 row (gap report §2): 16dp / 14dp, 36dp icon
                // tile, bodyLarge Medium title + bodyMedium muted subtitle.
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SettingsIconTile(Icons.Filled.FilterList, "Streams per source")
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Streams per source",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Max streams shown from one addon",
                            style = MaterialTheme.typography.bodyMedium,
                            color = V117Muted
                        )
                    }
                    IconButton(
                        onClick = {
                            perSource = (perSource - 1).coerceIn(1, 10)
                            settings.streamsPerSource = perSource
                        },
                        enabled = perSource > 1
                    ) {
                        Icon(Icons.Filled.Remove, "Fewer", tint = TextPrimary)
                    }
                    Text(
                        text = perSource.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LocalPalette.current.accent,
                        modifier = Modifier.width(28.dp),
                    )
                    IconButton(
                        onClick = {
                            perSource = (perSource + 1).coerceIn(1, 10)
                            settings.streamsPerSource = perSource
                        },
                        enabled = perSource < 10
                    ) {
                        Icon(Icons.Filled.Add, "More", tint = TextPrimary)
                    }
                }
            }

            item(key = "qualityOrderHeader") {
                SettingsSectionHeader("Quality order")
            }
            itemsIndexed(qualityOrder, key = { _, q -> q }) { index, quality ->
                // Flat v1.17 rows (gap report §2): 16dp / 14dp, hairline
                // 0.5dp divider inset 66dp between rows, muted rank number.
                Column(Modifier.fillMaxWidth()) {
                    if (index > 0) V117Divider()
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = V117Muted,
                            modifier = Modifier.width(28.dp)
                        )
                        Icon(
                            Icons.Filled.HighQuality,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = quality,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                if (index > 0) {
                                    qualityOrder = qualityOrder.toMutableList().also {
                                        val t = it[index - 1]; it[index - 1] = it[index]; it[index] = t
                                    }
                                    settings.qualityOrder = qualityOrder
                                }
                            },
                            enabled = index > 0
                        ) {
                            Icon(Icons.Filled.ArrowUpward, "Move up", tint = TextPrimary)
                        }
                        IconButton(
                            onClick = {
                                if (index < qualityOrder.size - 1) {
                                    qualityOrder = qualityOrder.toMutableList().also {
                                        val t = it[index + 1]; it[index + 1] = it[index]; it[index] = t
                                    }
                                    settings.qualityOrder = qualityOrder
                                }
                            },
                            enabled = index < qualityOrder.size - 1
                        ) {
                            Icon(Icons.Filled.ArrowDownward, "Move down", tint = TextPrimary)
                        }
                    }
                }
            }
            item(key = "qualityOrderNote") {
                Text(
                    "Top = highest priority in the stream list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            item(key = "autoplayHeader") {
                SettingsSectionHeader("Autoplay")
            }
            item(key = "autoplay") {
                V117ToggleRow(
                    icon = Icons.Filled.PlayArrow,
                    title = "Autoplay by rule",
                    subtitle = "Play picks the stream matching your quality and size rule",
                    checked = autoplay,
                    onCheckedChange = {
                        autoplay = it
                        settings.streamAutoplay = it
                    }
                )
            }
            item(key = "autoplayQuality") {
                val enabled = autoplay
                Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
                    V117NavRow(
                        icon = Icons.Filled.Sort,
                        title = "Preferred quality",
                        subtitle = if (autoplayQuality == "Auto") "Follow the quality order above"
                        else "Prefer $autoplayQuality streams",
                        onClick = { if (enabled) qualityPickerOpen = true },
                        trailing = {
                            Text(
                                autoplayQuality,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                            Spacer(Modifier.width(8.dp))
                            V117Chevron()
                        }
                    )
                }
            }
            item(key = "autoplaySize") {
                val enabled = autoplay
                Box(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.45f)) {
                    V117NavRow(
                        icon = Icons.Filled.Storage,
                        title = "Max file size",
                        subtitle = "Skip streams bigger than this when auto-playing",
                        onClick = { if (enabled) sizePickerOpen = true },
                        trailing = {
                            Text(
                                autoplaySizeName(autoplayMaxSize),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = LocalPalette.current.accent
                            )
                            Spacer(Modifier.width(8.dp))
                            V117Chevron()
                        }
                    )
                }
            }
            item(key = "autoplayNote") {
                Text(
                    "The source list button next to Play always opens the full stream list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            // Step 44 — Debrid stream preferences: filters that narrow the
            // list, tag lists, and name/description templates for
            // debrid-resolved rows.
            item(key = "debridPrefsHeader") {
                SettingsSectionHeader("Debrid stream preferences")
            }
            item(key = "debridMinQuality") {
                DebridPrefSelectRow(
                    title = "Minimum quality",
                    subtitle = "Hide streams below this resolution",
                    valueLabel = when (debridMinQuality) {
                        "720" -> "720p"; "1080" -> "1080p"; "2160" -> "4K"; else -> "Any"
                    },
                    onClick = { minQualityPickerOpen = true }
                )
            }
            item(key = "debridDv") {
                DebridPrefSelectRow(
                    title = "Dolby Vision",
                    subtitle = "Only show, or hide, Dolby Vision streams",
                    valueLabel = when (debridDvFilter) {
                        "only" -> "Only DV"; "exclude" -> "Exclude DV"; else -> "Any"
                    },
                    onClick = { dvPickerOpen = true }
                )
            }
            item(key = "debridHdr") {
                DebridPrefSelectRow(
                    title = "HDR",
                    subtitle = "Only show, or hide, HDR streams",
                    valueLabel = when (debridHdrFilter) {
                        "only" -> "Only HDR"; "exclude" -> "Exclude HDR"; else -> "Any"
                    },
                    onClick = { hdrPickerOpen = true }
                )
            }
            item(key = "debridCodec") {
                DebridPrefSelectRow(
                    title = "Codec",
                    subtitle = "Only show streams in this codec",
                    valueLabel = when (debridCodecFilter) {
                        "h264" -> "H.264"; "hevc" -> "HEVC"; "av1" -> "AV1"; else -> "Any"
                    },
                    onClick = { codecPickerOpen = true }
                )
            }
            item(key = "debridPreferredTags") {
                DebridPrefTextRow(
                    title = "Preferred tags",
                    subtitle = "Comma-separated — matching streams sort first",
                    value = debridPreferredTags,
                    placeholder = "e.g. remux, atmos",
                    onChange = {
                        debridPreferredTags = it
                        settings.debridPreferredTags = parseDebridTags(it)
                    }
                )
            }
            item(key = "debridRequiredTags") {
                DebridPrefTextRow(
                    title = "Required tags",
                    subtitle = "Comma-separated — a stream must contain all of them",
                    value = debridRequiredTags,
                    placeholder = "e.g. bluray, dts",
                    onChange = {
                        debridRequiredTags = it
                        settings.debridRequiredTags = parseDebridTags(it)
                    }
                )
            }
            item(key = "debridExcludedTags") {
                DebridPrefTextRow(
                    title = "Excluded tags",
                    subtitle = "Comma-separated — matching streams are hidden",
                    value = debridExcludedTags,
                    placeholder = "e.g. cam, hdcam",
                    onChange = {
                        debridExcludedTags = it
                        settings.debridExcludedTags = parseDebridTags(it)
                    }
                )
            }
            item(key = "debridNameTemplate") {
                Column {
                    DebridPrefTextRow(
                        title = "Debrid row name template",
                        subtitle = "Renames debrid-resolved rows. Keys: {resolution} {codec} {hdr} {audio} {source} {size} {service} {title} {name}. Blank = keep the addon name.",
                        value = debridNameTemplate,
                        placeholder = "e.g. {resolution} {service} Instant",
                        onChange = {
                            debridNameTemplate = it
                            settings.debridNameTemplate = it
                        }
                    )
                    val preview = remember(debridNameTemplate) {
                        debridTemplatePreview(debridNameTemplate)
                    }
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
            item(key = "debridDescriptionTemplate") {
                Column {
                    DebridPrefTextRow(
                        title = "Debrid row description template",
                        subtitle = "Adds a line under debrid-resolved rows. Same keys as the name template. Blank = no extra line.",
                        value = debridDescriptionTemplate,
                        placeholder = "e.g. {codec} · {audio} · {size}",
                        onChange = {
                            debridDescriptionTemplate = it
                            settings.debridDescriptionTemplate = it
                        }
                    )
                    val preview = remember(debridDescriptionTemplate) {
                        debridTemplatePreview(debridDescriptionTemplate)
                    }
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
            item(key = "debridPrefsReset") {
                // Flat v1.17 action row: 16dp / 14dp, accent label, no card.
                Row(
                    Modifier.fillMaxWidth().clickable {
                        debridMinQuality = "any"; settings.debridMinQuality = "any"
                        debridDvFilter = "any"; settings.debridDvFilter = "any"
                        debridHdrFilter = "any"; settings.debridHdrFilter = "any"
                        debridCodecFilter = "any"; settings.debridCodecFilter = "any"
                        debridPreferredTags = ""; settings.debridPreferredTags = emptySet()
                        debridRequiredTags = ""; settings.debridRequiredTags = emptySet()
                        debridExcludedTags = ""; settings.debridExcludedTags = emptySet()
                        debridNameTemplate = ""; settings.debridNameTemplate = ""
                        debridDescriptionTemplate = ""; settings.debridDescriptionTemplate = ""
                    }.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Reset debrid stream preferences",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = LocalPalette.current.accent,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** Single-select AlertDialog used by the Streams settings pickers. */
@Composable
private fun SingleSelectDialog(
    title: String,
    options: List<String>,
    selected: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = TextPrimary) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                itemsIndexed(options) { _, option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) }
                            .tvFocusRing(RoundedCornerShape(10.dp))
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        RadioButton(
                            selected = option == selected,
                            onClick = { onSelect(option) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = LocalPalette.current.accent
                            )
                        )
                        Text(option, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = LocalPalette.current.accent)
            }
        }
    )
}

/** Select row for the Step 44 debrid stream prefs (flat v1.17 row: 16dp / 14dp, no icon, value + chevron trailing). */
@Composable
private fun DebridPrefSelectRow(
    title: String,
    subtitle: String,
    valueLabel: String,
    onClick: () -> Unit,
) {
    V117StaticRow(
        icon = null,
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        trailing = {
            Text(
                valueLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = LocalPalette.current.accent
            )
            Spacer(Modifier.width(8.dp))
            V117Chevron()
        }
    )
}

/** Free-text row for the Step 44 tag lists and templates; saves on every change. */
@Composable
private fun DebridPrefTextRow(
    title: String,
    subtitle: String,
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = V117Muted
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            placeholder = { Text(placeholder, color = V117Muted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun parseDebridTags(text: String): Set<String> =
    text.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

/** Live preview of a template against a sample debrid-resolved stream. */
private fun debridTemplatePreview(template: String): String {
    if (template.isBlank()) return "Preview: default naming (addon name)."
    val sample = StreamItem(
        url = "https://example.com/file.mkv",
        title = "Movie 2024 1080p BluRay x265 DTS",
        name = "Movie 2024 1080p BluRay",
        sourceName = "⚡ TorBox · Example",
    )
    val rendered = formatDebridStreamText(template, sample, SmartStreamQuality.cached(sample))
    return if (rendered.isBlank()) "Preview: (empty — template produced nothing)"
    else "Preview: $rendered"
}
