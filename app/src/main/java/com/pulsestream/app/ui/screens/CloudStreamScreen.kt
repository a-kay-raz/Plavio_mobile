package com.pulsestream.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pulsestream.app.data.cloudstream.AddCloudStreamRepositoryResult
import com.pulsestream.app.data.cloudstream.CloudStreamInstallResult
import com.pulsestream.app.data.cloudstream.CloudStreamRepository
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * CloudStream (CS3) manager — Phase 1 minimal: add a repository by URL,
 * install / enable / disable / remove its plugins. No prebuilt repos, no
 * seed URL; the user pastes a repo URL (repo.json or plugins.json).
 *
 * Plugins run in-process with no sandbox (same as upstream CloudStream):
 * installing one runs its code inside the app. The warning gate below is
 * shown once before the first repository can be added, and installs are
 * refused unless the repository lists a SHA-256 hash for the package.
 */
@Composable
fun CloudStreamScreen(
    onBack: () -> Unit = {}
) {
    val uiState by CloudStreamRepository.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var repoUrl by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    var installError by remember { mutableStateOf<String?>(null) }

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
                            text = "CloudStream",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Community plugins. Streams from enabled plugins appear in Choose Stream.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
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
                                "A CloudStream plugin is third-party code that runs inside Plavio " +
                                    "with the app's permissions. Only add repositories you trust, " +
                                    "and only install plugins from authors you trust.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
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
                            OutlinedTextField(
                                value = repoUrl,
                                onValueChange = { repoUrl = it; addError = null },
                                label = { Text("Repository URL") },
                                placeholder = { Text("https://…/repo.json or …/plugins.json") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Button(
                                enabled = !adding && repoUrl.isNotBlank(),
                                onClick = {
                                    adding = true
                                    addError = null
                                    scope.launch {
                                        when (val result = CloudStreamRepository.addRepository(repoUrl.trim())) {
                                            is AddCloudStreamRepositoryResult.Success -> {
                                                repoUrl = ""
                                            }
                                            is AddCloudStreamRepositoryResult.Error -> {
                                                addError = result.message
                                            }
                                        }
                                        adding = false
                                    }
                                }
                            ) {
                                Text(if (adding) "Adding…" else "Add repository")
                            }
                            addError?.let { error ->
                                Spacer(Modifier.height(8.dp))
                                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }

                items(uiState.repositories, key = { it.manifest.sourceUrl }) { repo ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    repo.manifest.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    repo.manifest.sourceUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                repo.errorMessage?.let { error ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Switch(
                                checked = repo.enabled,
                                onCheckedChange = {
                                    CloudStreamRepository.setRepositoryEnabled(repo.manifest.sourceUrl, it)
                                }
                            )
                            IconButton(onClick = { CloudStreamRepository.removeRepository(repo.manifest.sourceUrl) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove repository", tint = TextSecondary)
                            }
                        }
                    }
                }

                if (uiState.plugins.isNotEmpty()) {
                    item(key = "plugins_header") {
                        Text(
                            "Plugins",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                    }
                }

                items(uiState.plugins, key = { it.metadata.id.value }) { plugin ->
                    val pluginId = plugin.metadata.id.value
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    plugin.metadata.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    "${plugin.metadata.internalName} · v${plugin.metadata.version}" +
                                        if (plugin.isInstalled) " · installed" else " · not installed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                                plugin.errorMessage?.let { error ->
                                    Spacer(Modifier.height(4.dp))
                                    Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (!plugin.isInstalled || plugin.hasUpdate) {
                                Button(
                                    onClick = {
                                        installError = null
                                        scope.launch {
                                            val result = if (plugin.hasUpdate) {
                                                CloudStreamRepository.updatePlugin(pluginId)
                                            } else {
                                                CloudStreamRepository.installPlugin(pluginId)
                                            }
                                            if (result is CloudStreamInstallResult.Error) {
                                                installError = result.message
                                            }
                                        }
                                    }
                                ) {
                                    Text(if (plugin.hasUpdate) "Update" else "Install")
                                }
                            } else {
                                Switch(
                                    checked = plugin.enabled,
                                    onCheckedChange = {
                                        CloudStreamRepository.setPluginEnabled(pluginId, it)
                                    }
                                )
                            }
                            IconButton(onClick = { CloudStreamRepository.removePlugin(pluginId) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove plugin", tint = TextSecondary)
                            }
                        }
                    }
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
