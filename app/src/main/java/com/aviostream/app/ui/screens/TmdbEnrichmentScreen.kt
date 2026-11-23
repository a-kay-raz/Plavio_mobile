package com.aviostream.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.TmdbClient
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * TMDB Enrichment (Nuvio TmdbSettingsPage, v1.17): the master switch,
 * personal API key, language, original-language titles, and the 12
 * module toggles. Each module gates its own TmdbClient call site —
 * a module OFF means that TMDB call never fires; the app falls back
 * to what the meta addons already serve (Cinemeta stays the default
 * meta source, TMDB only ever fills gaps).
 *
 * No key, no enrichment: every call below is key-gated inside
 * TmdbClient, so these settings do nothing until a key is saved.
 */
@Composable
fun TmdbEnrichmentScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val settings = repository.settings

    // SettingsStore is plain SharedPreferences, not observable — the
    // tick pattern (same as IntegrationsScreen) recomposes the rows
    // after a save.
    var tick by remember { mutableIntStateOf(0) }
    val enrichmentEnabled = remember(tick) { settings.tmdbEnrichmentEnabled }

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
                            text = "TMDB Enrichment",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "TMDB fills in what your addons don't serve — trailers, artwork, credits and more.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            item(key = "enrichment_group") {
                SettingsGroup(
                    {
                        V117ToggleRow(
                            icon = Icons.Filled.Movie,
                            title = "Enable TMDB enrichment",
                            subtitle = "Use TMDB to fill in missing trailers, artwork, credits and more",
                            checked = enrichmentEnabled,
                            onCheckedChange = {
                                settings.tmdbEnrichmentEnabled = it
                                tick++
                            }
                        )
                    },
                    {
                        TmdbApiKeyField(
                            currentKey = settings.tmdbApiKey,
                            onSave = { settings.tmdbApiKey = it; tick++ }
                        )
                    }
                )
            }

            item(key = "localization_header") {
                Text(
                    "Localization",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            item(key = "localization_group") {
                SettingsGroup(
                    {
                        TmdbLanguageField(
                            currentLanguage = remember(tick) { settings.tmdbLanguage },
                            onSave = { settings.tmdbLanguage = it; tick++ }
                        )
                    },
                    {
                        V117ToggleRow(
                            icon = Icons.Filled.Title,
                            title = "Original title language",
                            subtitle = "Show TMDB titles in their original language instead of the language above",
                            checked = remember(tick) { settings.tmdbOriginalTitleLanguage },
                            enabled = enrichmentEnabled,
                            onCheckedChange = {
                                settings.tmdbOriginalTitleLanguage = it
                                tick++
                            }
                        )
                    }
                )
            }

            item(key = "modules_header") {
                Text(
                    "Modules",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                )
            }

            item(key = "modules_group") {
                val modules = listOf(
                    TmdbModuleRow(
                        "Trailers",
                        "YouTube trailers from TMDB when the addon serves none",
                        { settings.tmdbUseTrailers },
                        { settings.tmdbUseTrailers = it }
                    ),
                    TmdbModuleRow(
                        "Artwork",
                        "Backdrop and poster from TMDB when the addon serves no art",
                        { settings.tmdbUseArtwork },
                        { settings.tmdbUseArtwork = it }
                    ),
                    TmdbModuleRow(
                        "Basic info",
                        "TMDB title lookup — resolves IMDb ids to TMDB ids for the modules below",
                        { settings.tmdbUseBasicInfo },
                        { settings.tmdbUseBasicInfo = it }
                    ),
                    TmdbModuleRow(
                        "Details",
                        "Full title details from TMDB — powers the collection lookup on Detail",
                        { settings.tmdbUseDetails },
                        { settings.tmdbUseDetails = it }
                    ),
                    TmdbModuleRow(
                        "Credits",
                        "Cast photos and person pages from TMDB credits",
                        { settings.tmdbUseCredits },
                        { settings.tmdbUseCredits = it }
                    ),
                    TmdbModuleRow(
                        "Production companies",
                        "Studio names from TMDB when the addon serves no production info",
                        { settings.tmdbUseProductions },
                        { settings.tmdbUseProductions = it }
                    ),
                    TmdbModuleRow(
                        "Networks",
                        "TV network names from TMDB when the addon serves none",
                        { settings.tmdbUseNetworks },
                        { settings.tmdbUseNetworks = it }
                    ),
                    TmdbModuleRow(
                        "Episodes",
                        "Episode overviews, runtimes and stills from TMDB when the addon serves none",
                        { settings.tmdbUseEpisodes },
                        { settings.tmdbUseEpisodes = it }
                    ),
                    TmdbModuleRow(
                        "Episode ratings",
                        "Per-episode TMDB ratings on the episode cards",
                        { settings.tmdbUseEpisodeRatings },
                        { settings.tmdbUseEpisodeRatings = it }
                    ),
                    TmdbModuleRow(
                        "Season posters",
                        "Per-season posters from TMDB in the season picker",
                        { settings.tmdbUseSeasonPosters },
                        { settings.tmdbUseSeasonPosters = it }
                    ),
                    TmdbModuleRow(
                        "More like this",
                        "TMDB recommendations when the addons return no related titles",
                        { settings.tmdbUseMoreLikeThis },
                        { settings.tmdbUseMoreLikeThis = it }
                    ),
                    TmdbModuleRow(
                        "Collections",
                        "TMDB collections and lists in Collection folders",
                        { settings.tmdbUseCollections },
                        { settings.tmdbUseCollections = it }
                    ),
                )
                val moduleRows: List<@Composable () -> Unit> = modules.map { module ->
                    {
                        V117ToggleRow(
                            icon = null,
                            title = module.title,
                            subtitle = module.subtitle,
                            checked = remember(tick) { module.get() },
                            enabled = enrichmentEnabled,
                            onCheckedChange = {
                                module.set(it)
                                tick++
                            }
                        )
                    }
                }
                SettingsGroup(*moduleRows.toTypedArray())
            }

            item(key = "note") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Note",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "TMDB never replaces your addons — details, posters and " +
                                "streams still come from them (Cinemeta by default). " +
                                "Turning a module off stops its TMDB calls entirely. " +
                                "Without an API key these settings do nothing; " +
                                "tmdb: titles still resolve through the key-gated " +
                                "IMDb lookup even with enrichment off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

private data class TmdbModuleRow(
    val title: String,
    val subtitle: String,
    val get: () -> Boolean,
    val set: (Boolean) -> Unit,
)

/**
 * Personal TMDB API key (Nuvio TmdbApiKeyRow): inline field + Save,
 * verified against /configuration before saving — an invalid key
 * won't save. Blank clears the key without verifying.
 */
@Composable
private fun TmdbApiKeyField(
    currentKey: String?,
    onSave: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var draft by remember(currentKey) { mutableStateOf(currentKey ?: "") }
    var showKey by remember { mutableStateOf(false) }
    var validating by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var savedFlash by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            "Personal API key",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            "Your own TMDB key, used instead of any shared key. Get one at themoviedb.org/settings/api.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; savedFlash = false; validationError = null },
            label = { Text("TMDB API key") },
            singleLine = true,
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { showKey = !showKey }) {
                    Icon(
                        if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (showKey) "Hide key" else "Show key",
                        tint = TextSecondary
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    val trimmed = draft.trim()
                    if (trimmed.isBlank()) {
                        onSave("")
                        savedFlash = true
                        return@Button
                    }
                    scope.launch {
                        validating = true
                        validationError = null
                        val ok = runCatching { TmdbClient.validateApiKey(trimmed) }.getOrDefault(false)
                        validating = false
                        if (ok) {
                            onSave(trimmed)
                            savedFlash = true
                        } else {
                            validationError = "Couldn't verify that key — check it and try again."
                        }
                    }
                },
                enabled = !validating && draft.trim() != (currentKey ?: ""),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LocalPalette.current.accent
                )
            ) {
                Text(if (validating) "Checking…" else if (savedFlash) "Saved ✓" else "Save")
            }
            if (validationError != null) {
                Spacer(Modifier.width(12.dp))
                Text(
                    validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/** Preferred TMDB language code (Nuvio TmdbLanguageRow): "en-US", "hi-IN", "fr-FR" … */
@Composable
private fun TmdbLanguageField(
    currentLanguage: String,
    onSave: (String) -> Unit,
) {
    var draft by remember(currentLanguage) { mutableStateOf(currentLanguage) }
    var savedFlash by remember { mutableStateOf(false) }
    val normalized = draft.trim().ifBlank { "en-US" }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Language,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Text(
                "Preferred language",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            "Language for TMDB text (titles, overviews, episode details).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it; savedFlash = false },
            label = { Text("Language code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                draft = normalized
                onSave(normalized)
                savedFlash = true
            },
            enabled = normalized != currentLanguage,
            colors = ButtonDefaults.buttonColors(
                containerColor = LocalPalette.current.accent
            )
        ) {
            Text(if (savedFlash) "Saved ✓" else "Save")
        }
    }
}
