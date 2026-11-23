package com.aviostream.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aviostream.app.data.cloudstream.AddCloudStreamRepositoryResult
import com.aviostream.app.data.cloudstream.CloudStreamBulkInstallResult
import com.aviostream.app.data.cloudstream.CloudStreamInstallResult
import com.aviostream.app.data.cloudstream.CloudStreamPluginItem
import com.aviostream.app.data.cloudstream.CloudStreamRepository
import com.aviostream.app.data.cloudstream.CloudStreamRepositoryItem
import com.aviostream.app.data.cloudstream.CloudStreamRepositoryManifest
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * CloudStream (CS3) manager — full manager on the Phase 1 screen (Settings → CloudStream):
 * security-warning gate, repo add / edit / remove, Discover (type "MegaRepo" to list the
 * community repositories from the recloudstream cs-repos index), plugin install / update /
 * enable / disable / remove with visible per-plugin state, bulk install-and-enable,
 * language / type filters, and pin sources (pinned providers' streams sort first in
 * Choose Stream, like starred Stremio addons).
 *
 * No prebuilt repos, no seed URL; the user pastes a repo URL (repo.json or plugins.json).
 * Rows are flat Infuse-style settings rows (16dp horizontal / 14dp vertical, plain 24dp
 * accent icon, bodyLarge Medium title + bodyMedium muted subtitle) — no GlassCards.
 *
 * Plugins run in-process with no sandbox (same as upstream CloudStream): installing one
 * runs its code inside the app. The warning gate below is shown once before the first
 * repository can be added, and installs are refused unless the repository lists a SHA-256
 * hash for the package.
 */
@Composable
fun CloudStreamScreen(
    onBack: () -> Unit = {}
) {
    val uiState by CloudStreamRepository.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var repoUrl by remember { mutableStateOf("") }
    var editingRepositoryUrl by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf<String?>(null) }
    var selectedType by remember { mutableStateOf<String?>(null) }
    var bulkInstalling by remember { mutableStateOf(false) }
    var bulkMessage by remember { mutableStateOf<String?>(null) }
    var installingIds by remember { mutableStateOf(setOf<String>()) }

    val languages = remember(uiState.plugins) {
        uiState.plugins.mapNotNull { it.metadata.language?.trim()?.takeIf { language -> language.isNotEmpty() } }
            .distinct().sorted()
    }
    val types = remember(uiState.plugins) {
        uiState.plugins.flatMap { it.metadata.rawTvTypes }.map { it.trim() }
            .filter { it.isNotEmpty() }.distinct().sorted()
    }
    val visiblePlugins = remember(uiState.plugins, query, selectedLanguage, selectedType) {
        val q = query.trim().lowercase()
        uiState.plugins.filter { plugin ->
            (selectedLanguage == null || plugin.metadata.language?.trim() == selectedLanguage) &&
                (selectedType == null || plugin.metadata.rawTvTypes.contains(selectedType)) &&
                (q.isEmpty() ||
                    plugin.metadata.name.lowercase().contains(q) ||
                    plugin.metadata.internalName.lowercase().contains(q) ||
                    plugin.metadata.authors.any { it.lowercase().contains(q) } ||
                    (plugin.metadata.description?.lowercase()?.contains(q) == true))
        }.sortedWith(
            compareByDescending<CloudStreamPluginItem> { it.pinned }
                .thenBy { it.metadata.name.lowercase() }
        )
    }
    val bulkEligible = remember(uiState.plugins) {
        uiState.plugins.filter {
            it.metadata.status.canInstall && it.compatibility.isRunnable &&
                (!it.isInstalled || it.hasUpdate)
        }
    }

    // Add / edit / discover submit: the add-repo form's single entry point. A matching
    // URL in edit mode refreshes instead of re-adding; a MegaRepo keyword discovers
    // the community index instead of adding one repository.
    fun submitRepository() {
        val requested = repoUrl.trim()
        if (requested.isBlank()) return
        adding = true
        addError = null
        message = null
        scope.launch {
            val previous = editingRepositoryUrl
            when {
                previous != null && requested == previous -> {
                    CloudStreamRepository.refreshRepository(previous)
                    message = "Refreshing repository."
                    editingRepositoryUrl = null
                    repoUrl = ""
                }
                previous == null && CloudStreamRepository.isDiscoveryInput(requested) -> {
                    discovering = true
                    val result = CloudStreamRepository.discoverRepositories(requested)
                    discovering = false
                    result.fold(
                        onSuccess = { count -> message = "Found $count repositories." },
                        onFailure = { error -> addError = error.message ?: "Could not discover repositories" }
                    )
                    repoUrl = ""
                }
                else -> {
                    when (val result = CloudStreamRepository.addRepository(requested)) {
                        is AddCloudStreamRepositoryResult.Success -> {
                            if (previous != null) {
                                CloudStreamRepository.removeRepository(previous)
                                message = "Repository updated."
                            } else {
                                message = "Repository added."
                            }
                            editingRepositoryUrl = null
                            repoUrl = ""
                        }
                        is AddCloudStreamRepositoryResult.Error -> {
                            addError = result.message
                        }
                    }
                }
            }
            adding = false
        }
    }

    GlowBackground {
        LazyColumn(
            state = listState,
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
                            text = "CloudStream",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Community plugins. Streams from enabled plugins appear in Choose Stream.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            if (!uiState.securityWarningAccepted) {
                item(key = "warning") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                "Plugins run inside the app",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "A CloudStream plugin is third-party code that runs inside Avio " +
                                    "with the app's permissions. Only add repositories you trust, " +
                                    "and only install plugins from authors you trust.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = V117Muted
                            )
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { CloudStreamRepository.acceptSecurityWarning() }) {
                                Text("I understand — continue")
                            }
                        }
                    }
                }
            } else {
                item(key = "add_repo") {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (editingRepositoryUrl != null) "Edit repository link" else "Add repository",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it; addError = null },
                                label = { Text("Repository URL") },
                                placeholder = { Text("https://…/repo.json or …/plugins.json") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Paste a repo URL, or type MegaRepo to discover community repositories.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = V117Muted
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    enabled = !adding && !discovering && repoUrl.isNotBlank(),
                                    onClick = { submitRepository() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        when {
                                            adding -> "Adding…"
                                            discovering -> "Discovering…"
                                            editingRepositoryUrl != null -> "Apply changes"
                                            else -> "Add repository"
                                        }
                                    )
                                }
                                if (editingRepositoryUrl != null) {
                                    OutlinedButton(
                                        onClick = { editingRepositoryUrl = null; repoUrl = ""; addError = null }
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                            addError?.let { error ->
                                Spacer(Modifier.height(8.dp))
                                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                message?.let { info ->
                    item(key = "message") {
                        Text(
                            info,
                            style = MaterialTheme.typography.bodyMedium,
                            color = V117Muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (uiState.repositories.isNotEmpty()) {
                    item(key = "repos_header") {
                        CsSectionHeader("Repositories")
                    }
                }

                items(uiState.repositories, key = { it.manifest.sourceUrl }) { repo ->
                    CsRepoRow(
                        repo = repo,
                        onEdit = {
                            repoUrl = repo.manifest.sourceUrl
                            editingRepositoryUrl = repo.manifest.sourceUrl
                            addError = null
                            message = null
                            scope.launch { listState.scrollToItem(0) }
                        }
                    )
                }

                if (uiState.discoveredRepositories.isNotEmpty()) {
                    item(key = "discovered_header") {
                        CsSectionHeader("Discovered repositories")
                    }
                }

                items(uiState.discoveredRepositories, key = { it.sourceUrl }) { discovered ->
                    CsDiscoveredRow(
                        manifest = discovered,
                        onAdd = {
                            addError = null
                            message = null
                            scope.launch {
                                when (val result = CloudStreamRepository.addRepository(discovered.sourceUrl)) {
                                    is AddCloudStreamRepositoryResult.Success -> {
                                        message = "Repository added."
                                    }
                                    is AddCloudStreamRepositoryResult.Error -> {
                                        addError = result.message
                                    }
                                }
                            }
                        }
                    )
                }

                if (uiState.plugins.isNotEmpty()) {
                    item(key = "plugins_header") {
                        CsSectionHeader("Plugins")
                    }

                    item(key = "plugins_tools") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("Search plugins") },
                                placeholder = { Text("Name, author, or description") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Search, contentDescription = null, tint = V117Muted)
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (languages.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                CsFilterRow(
                                    label = "Language",
                                    options = languages,
                                    selected = selectedLanguage,
                                    display = { it.uppercase() },
                                    onSelect = { selectedLanguage = it }
                                )
                            }
                            if (types.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                CsFilterRow(
                                    label = "Type",
                                    options = types,
                                    selected = selectedType,
                                    onSelect = { selectedType = it }
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                enabled = !bulkInstalling && bulkEligible.isNotEmpty(),
                                onClick = {
                                    bulkInstalling = true
                                    bulkMessage = null
                                    installError = null
                                    scope.launch {
                                        val result = CloudStreamRepository.installAndEnablePlugins(
                                            bulkEligible.map { it.metadata.id.value }
                                        )
                                        bulkMessage = describeBulkResult(result)
                                        bulkInstalling = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    Icons.Filled.CloudDownload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    when {
                                        bulkInstalling -> "Installing…"
                                        bulkEligible.isEmpty() -> "Everything is installed and active"
                                        else -> "Install and enable all (${bulkEligible.size})"
                                    }
                                )
                            }
                            bulkMessage?.let { info ->
                                Spacer(Modifier.height(8.dp))
                                Text(info, style = MaterialTheme.typography.bodyMedium, color = V117Muted)
                            }
                        }
                    }
                } else if (uiState.repositories.isNotEmpty()) {
                    item(key = "plugins_empty") {
                        Text(
                            "No plugins found in these repositories.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = V117Muted,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                items(visiblePlugins, key = { it.metadata.id.value }) { plugin ->
                    val pluginId = plugin.metadata.id.value
                    CsPluginRow(
                        plugin = plugin,
                        isInstalling = pluginId in installingIds,
                        onInstall = {
                            installError = null
                            installingIds = installingIds + pluginId
                            scope.launch {
                                val result = if (plugin.hasUpdate) {
                                    CloudStreamRepository.updatePlugin(pluginId)
                                } else {
                                    CloudStreamRepository.installPlugin(pluginId)
                                }
                                if (result is CloudStreamInstallResult.Error) {
                                    installError = result.message
                                }
                                installingIds = installingIds - pluginId
                            }
                        },
                        onRemove = { CloudStreamRepository.removePlugin(pluginId) }
                    )
                }

                installError?.let { error ->
                    item(key = "install_error") {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                if (uiState.repositories.isNotEmpty()) {
                    item(key = "refresh") {
                        OutlinedButton(
                            onClick = { CloudStreamRepository.refreshAll() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Refresh all repositories")
                        }
                    }
                }
            }
        }
    }
}

private fun describeBulkResult(result: CloudStreamBulkInstallResult): String {
    val parts = mutableListOf("${result.enabledCount} enabled")
    if (result.installedCount > 0) parts += "${result.installedCount} installed/updated"
    if (result.skippedCount > 0) parts += "${result.skippedCount} skipped"
    val firstFailure = result.failures.firstOrNull()
    return if (firstFailure != null) {
        parts.joinToString(", ") + ". First failure: ${firstFailure.pluginName} — ${firstFailure.message}"
    } else {
        parts.joinToString(", ") + "."
    }
}

@Composable
private fun CsSectionHeader(text: String) {
    // Nuvio v1.17 section label (gap report §2): 12sp Bold, ls 0.8sp,
    // muted #969CA3, no uppercase; 24dp horizontal, 10dp gap below.
    V117SectionLabel(
        text,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 10.dp)
    )
}

/**
 * Flat v1.17 repo row (gap report §2): 16dp horizontal / 14dp vertical,
 * 36dp icon tile, bodyLarge Medium title + bodyMedium muted subtitle,
 * gradient switch, 0.5dp divider inset 66dp below.
 */
@Composable
private fun CsRepoRow(
    repo: CloudStreamRepositoryItem,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsIconTile(Icons.Filled.Extension, "Repository")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        repo.manifest.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!repo.enabled) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Disabled",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    repo.manifest.sourceUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                repo.manifest.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodySmall,
                        color = V117Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                repo.errorMessage?.let { error ->
                    Spacer(Modifier.height(2.dp))
                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            SettingsGradientSwitch(
                checked = repo.enabled,
                onCheckedChange = {
                    CloudStreamRepository.setRepositoryEnabled(repo.manifest.sourceUrl, it)
                }
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit repository", tint = V117Muted)
            }
            IconButton(onClick = { CloudStreamRepository.refreshRepository(repo.manifest.sourceUrl) }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh repository", tint = V117Muted)
            }
            IconButton(onClick = { CloudStreamRepository.removeRepository(repo.manifest.sourceUrl) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove repository", tint = V117Muted)
            }
        }
        V117Divider()
    }
}

@Composable
private fun CsDiscoveredRow(
    manifest: CloudStreamRepositoryManifest,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (!manifest.iconUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = manifest.iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Filled.Extension,
                        contentDescription = null,
                        tint = LocalPalette.current.accent,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    manifest.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    manifest.sourceUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(onClick = onAdd) {
                Text("Add")
            }
        }
        V117Divider()
    }
}

/**
 * Flat plugin row with visible state: icon, name + authors, description, status badges,
 * compatibility reason when it cannot run, error text, and the action row (Install /
 * Update + Remove, or the enable Switch when installed). The pin button sorts the
 * plugin's streams first in Choose Stream.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsPluginRow(
    plugin: CloudStreamPluginItem,
    isInstalling: Boolean,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val pluginId = plugin.metadata.id.value
    val canToggle = plugin.isInstalled && plugin.verified && plugin.compatibility.isRunnable &&
        plugin.metadata.status.canInstall
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (!plugin.metadata.iconUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = plugin.metadata.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.Extension,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            plugin.metadata.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (plugin.pinned) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.PushPin,
                                contentDescription = "Pinned source",
                                tint = LocalPalette.current.accent,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        plugin.metadata.authors.joinToString(", ")
                            .ifBlank { plugin.metadata.internalName },
                        style = MaterialTheme.typography.bodyMedium,
                        color = V117Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { CloudStreamRepository.setProviderPinned(pluginId, !plugin.pinned) }
                ) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = if (plugin.pinned) "Unpin source" else "Pin source",
                        tint = if (plugin.pinned) LocalPalette.current.accent else V117Muted
                    )
                }
                if (plugin.isInstalled && !plugin.hasUpdate) {
                    SettingsGradientSwitch(
                        checked = plugin.enabled,
                        enabled = canToggle,
                        onCheckedChange = { CloudStreamRepository.setPluginEnabled(pluginId, it) }
                    )
                }
            }
            plugin.metadata.description?.takeIf { it.isNotBlank() }?.let { description ->
                Spacer(Modifier.height(8.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = V117Muted,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CsBadge(if (plugin.isInstalled) "Installed v${plugin.installedVersion}" else "Not installed")
                if (plugin.hasUpdate) CsBadge("Update v${plugin.metadata.version}")
                plugin.metadata.language?.trim()?.takeIf { it.isNotEmpty() }?.let { CsBadge(it.uppercase()) }
                plugin.metadata.rawTvTypes.forEach { CsBadge(it) }
                CsBadge(if (plugin.verified) "Hash verified" else "Hash unverified")
            }
            if (!plugin.compatibility.isRunnable) {
                Spacer(Modifier.height(4.dp))
                Text(
                    plugin.compatibility.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            plugin.errorMessage?.let { error ->
                Spacer(Modifier.height(4.dp))
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!plugin.isInstalled || plugin.hasUpdate) {
                    Button(
                        onClick = onInstall,
                        enabled = !isInstalling,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            if (isInstalling) "Installing…"
                            else if (plugin.hasUpdate) "Update"
                            else "Install"
                        )
                    }
                }
                if (plugin.isInstalled) {
                    OutlinedButton(onClick = onRemove, modifier = Modifier.weight(1f)) {
                        Text("Remove")
                    }
                }
            }
        }
        V117Divider()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsFilterRow(
    label: String,
    options: List<String>,
    selected: String?,
    display: (String) -> String = { it },
    onSelect: (String?) -> Unit,
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = V117Muted
        )
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CsFilterChip(label = "All", selected = selected == null, onClick = { onSelect(null) })
            options.forEach { option ->
                CsFilterChip(
                    label = display(option),
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun CsFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalPalette.current.accent
    Surface(
        onClick = onClick,
        color = if (selected) accent.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(1.dp, if (selected) accent else V117Muted.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) accent else V117Muted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun CsBadge(text: String) {
    Surface(
        color = LocalPalette.current.accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = V117Muted,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
