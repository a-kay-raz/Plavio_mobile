package com.pulsestream.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.BuiltInSubtitleFonts
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.SubtitleFont
import com.pulsestream.app.data.SubtitleFonts
import com.pulsestream.app.data.formatOffsetMs
import com.pulsestream.app.data.subtitleColorName
import com.pulsestream.app.data.subtitleEdgeName
import com.pulsestream.app.data.subtitleFontLabel
import com.pulsestream.app.data.subtitleLanguageName
import com.pulsestream.app.data.subtitleSizeName
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Settings > Subtitles: preferred subtitle languages (auto-selected in the
 * player when available) and subtitle appearance (color, size, background,
 * edge style, or the Android system caption settings).
 */
@Composable
fun SubtitleSettingsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings
    val context = LocalContext.current
    var subLangs by remember { mutableStateOf(settings.subtitleLanguages) }
    var subLangPickerOpen by remember { mutableStateOf(false) }
    var subStyleOpen by remember { mutableStateOf(false) }
    var subStyleVersion by remember { mutableIntStateOf(0) }
    // Step 38: subtitle font + SDH filter.
    var subFontId by remember { mutableStateOf(settings.subtitleFontId) }
    var subFontPickerOpen by remember { mutableStateOf(false) }
    var sdhFilter by remember { mutableStateOf(settings.subtitleSdhFilter) }
    var installedFonts by remember {
        mutableStateOf(SubtitleFonts.installed(context))
    }
    // Global subtitle timing offset (ms). Applied to every title on load;
    // fine-tune per title in the player.
    var subOffsetMs by remember { mutableLongStateOf(settings.subtitleOffsetMs) }

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
                            text = "Subtitles",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Which subtitles turn on automatically, and how they look.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }
            item(key = "sub_group") {
                val styleSummary = remember(subStyleVersion) {
                    if (settings.useSystemCaptions) {
                        "System default (Android caption settings)"
                    } else {
                        "${subtitleColorName(settings.subtitleTextColor)} • " +
                            "${subtitleSizeName(settings.subtitleTextSizeScale)} • " +
                            subtitleEdgeName(settings.subtitleEdgeType)
                    }
                }
                SettingsGroup(
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.Translate,
                            title = "Preferred subtitle languages",
                            subtitle = if (subLangs.isEmpty()) {
                                "Not set — first available subtitle is used"
                            } else {
                                subLangs.joinToString(", ") { subtitleLanguageName(it) }
                            },
                            onClick = { subLangPickerOpen = true }
                        )
                    },
                    {
                        SettingsNavRow(
                            icon = Icons.Filled.ClosedCaption,
                            title = "Subtitle appearance",
                            subtitle = styleSummary,
                            onClick = { subStyleOpen = true }
                        )
                    },
                    {
                        // Step 38: custom subtitle font (built-ins + imported ttf/otf).
                        SettingsNavRow(
                            icon = Icons.Filled.TextFields,
                            title = "Subtitle font",
                            subtitle = subtitleFontLabel(subFontId, installedFonts),
                            onClick = { subFontPickerOpen = true }
                        )
                    },
                    {
                        // Step 38: strip "[sound]" / "(sound)" / "NAME:" SDH cues.
                        SettingsToggleRow(
                            icon = Icons.Filled.VisibilityOff,
                            title = "Filter SDH captions",
                            subtitle = "Hide [sounds], (sounds) and speaker labels in subtitles",
                            checked = sdhFilter,
                            onCheckedChange = {
                                settings.subtitleSdhFilter = it
                                sdhFilter = it
                            }
                        )
                    }
                )
            }
            // Global subtitle timing offset (v0.9.0+). Positive = subtitles
            // appear later, negative = earlier. Fine-tune per title in the
            // player's Subtitles sheet.
            item(key = "subTiming") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Schedule,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                "Subtitle timing",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                            Text(
                                "Shift all subtitles earlier or later",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(start = 36.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val next = (subOffsetMs - 500L).coerceIn(-10_000L, 10_000L)
                                settings.subtitleOffsetMs = next
                                subOffsetMs = next
                            }
                        ) {
                            Text("−500 ms")
                        }
                        Text(
                            formatOffsetMs(subOffsetMs),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        OutlinedButton(
                            onClick = {
                                val next = (subOffsetMs + 500L).coerceIn(-10_000L, 10_000L)
                                settings.subtitleOffsetMs = next
                                subOffsetMs = next
                            }
                        ) {
                            Text("+500 ms")
                        }
                    }
                    if (subOffsetMs != 0L) {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                settings.subtitleOffsetMs = 0L
                                subOffsetMs = 0L
                            },
                            modifier = Modifier.padding(start = 28.dp)
                        ) {
                            Text(
                                "Reset timing",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    if (subLangPickerOpen) {
        SubtitleLanguagePickerDialog(
            initial = subLangs,
            onDismiss = { subLangPickerOpen = false },
            onDone = { langs ->
                settings.subtitleLanguages = langs
                subLangs = langs
                subLangPickerOpen = false
            }
        )
    }

    if (subStyleOpen) {
        SubtitleStyleDialog(
            settings = settings,
            onDismiss = {
                subStyleOpen = false
                subStyleVersion++
            }
        )
    }

    if (subFontPickerOpen) {
        SubtitleFontPickerDialog(
            selectedId = subFontId,
            fonts = installedFonts,
            onSelect = { id ->
                settings.subtitleFontId = id
                subFontId = id
                subFontPickerOpen = false
            },
            onDismiss = { subFontPickerOpen = false },
            onFontsChanged = { installedFonts = SubtitleFonts.installed(context) },
            onImported = { font ->
                settings.subtitleFontId = font.id
                subFontId = font.id
                installedFonts = SubtitleFonts.installed(context)
                subFontPickerOpen = false
            },
        )
    }
}

/**
 * Step 38 — subtitle font picker (Nuvio SubtitleFontPicker, Plavio-native):
 * built-in faces with an "Aa" preview, imported ttf/otf fonts with delete,
 * and a file import (SAF picker, validated + deduped by SubtitleFonts).
 * Selecting a row applies it at once; importing applies the new font.
 */
@Composable
private fun SubtitleFontPickerDialog(
    selectedId: String,
    fonts: List<SubtitleFont>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    onFontsChanged: () -> Unit,
    onImported: (SubtitleFont) -> Unit,
) {
    val context = LocalContext.current
    var importFailed by remember { mutableStateOf(false) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        if (bytes == null) {
            importFailed = true
            return@rememberLauncherForActivityResult
        }
        val name = uri.lastPathSegment?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() } ?: "Custom font"
        runCatching { SubtitleFonts.import(context, name, bytes) }
            .onSuccess { onImported(it) }
            .onFailure { importFailed = true }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle font") },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BuiltInSubtitleFonts.forEach { id ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selectedId == id, onClick = { onSelect(id) })
                        Text(
                            "Aa",
                            fontFamily = when (id) {
                                "serif" -> FontFamily.Serif
                                "mono" -> FontFamily.Monospace
                                "sans" -> FontFamily.SansSerif
                                else -> FontFamily.Default
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(subtitleFontLabel(id, fonts))
                    }
                }
                fonts.forEach { font ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(font.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = selectedId == font.id,
                            onClick = { onSelect(font.id) },
                        )
                        Text(
                            "Aa",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(
                            font.name,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            SubtitleFonts.remove(context, font.id)
                            onFontsChanged()
                            if (selectedId == font.id) onSelect("default")
                        }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${font.name}")
                        }
                    }
                }
                if (importFailed) {
                    Text(
                        "That file is not a supported font (ttf/otf, up to 10 MB).",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        dismissButton = {
            TextButton(onClick = { importFailed = false; importLauncher.launch("*/*") }) {
                Text("Import font")
            }
        },
    )
}
