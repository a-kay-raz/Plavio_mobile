package com.aviostream.app.ui.screens

import com.aviostream.app.ui.theme.LocalPalette
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.plugs.PlugKind
import com.aviostream.app.data.plugs.RepoInstallResult
import com.aviostream.app.data.plugs.StoredPlug
import com.aviostream.app.data.plugs.StoredRepo
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/**
 * Plugs — Nuvio-style layout (owner order 2026-11-20 19:34,
 * v0.14.61; samples ~/workspace/.scratch-plugs-design3): a main
 * page with an OVERVIEW card (counts as pills, the "Enable plugs"
 * master switch, the counts line), an ADD REPOSITORY card (link
 * field + big Install Repository button) and INSTALLED
 * REPOSITORIES as their own cards — tapping one opens its
 * providers page: one big card per plug (repo line, name,
 * description, pills, switch), our per-plug status line (green
 * "Working · last title N streams" / amber reason — the data
 * PlugService records on every run), and a Test Provider button
 * that runs that plug alone against Inception.
 *
 * Plugs follow Nuvio's plugin design — a repository is one
 * manifest link listing plug files; JS plugs run unmodified in
 * that format, RULE plugs are Avio's declarative fast lane.
 * Everything runs on this device: repos and plug files are
 * stored locally, enabled plugs race the addons on every title,
 * and results are never cached — every open runs them fresh.
 * Every function from the previous page survives: master switch,
 * per-repo switches, per-plug switches, add / refresh / remove
 * repository, the seeded Avio Plugs repo, the quiet background
 * refresh on open.
 */

// Sample palette (design3): near-black glass cards, grey pills,
// the puzzle green, the remove pink, status green/amber.
private val PlugCardBorder = Color(0x0DFFFFFF)
private val PlugPillBg = Color(0xFF2B2B31)
private val PlugPillText = Color(0xFFBCBCC4)
private val PlugSectionText = Color(0xFF8E8E96)
private val PlugButtonBg = Color(0xFFF4F4F6)
private val PlugButtonText = Color(0xFF131315)
private val PlugPuzzleGreen = Color(0xFF63C768)
private val PlugRemovePink = Color(0xFFE5637E)
private val PlugStatusOk = Color(0xFF4CC96F)
private val PlugStatusErr = Color(0xFFE5A33C)

@Composable
fun PlugsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val service = repository.plugs
    // TEST v0.14.74 (item 8): switch flips tick.
    val hapticTick = com.aviostream.app.data.rememberHapticTick()
    val scope = rememberCoroutineScope()
    var repoUrl by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var selectedRepoUrl by remember { mutableStateOf<String?>(null) }
    var testingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        service?.store?.ensureLoaded()
        // Quiet background refresh: a repo whose manifest version
        // moved re-downloads its changed plugs (switches survive).
        // Every repo is a normal remote repo since v0.14.56 —
        // Avio Plugs included — so all of them refresh here.
        service?.store?.state?.value?.repos
            ?.forEach { repo -> service.store.refreshRepo(repo.url) }
    }
    val storeState by (service?.store?.state
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.aviostream.app.data.plugs.PlugStoreState()) })
        .collectAsState()
    // The last run's one-line reason for plugs that produced no
    // streams, keyed by StoredPlug.key — the amber line on a
    // plug's card; a plug with a recorded success shows the green
    // "Working" line instead. Test Provider runs write here too.
    val plugIssues by (service?.lastPlugIssues
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, String>()) })
        .collectAsState()
    val plugSuccess by (service?.lastPlugSuccess
        ?: remember { kotlinx.coroutines.flow.MutableStateFlow(emptyMap<String, Int>()) })
        .collectAsState()

    fun refreshRepo(repo: StoredRepo) {
        if (service == null || busy) return
        busy = true
        scope.launch {
            val result = service.store.refreshRepo(repo.url)
            busy = false
            result.fold(
                onSuccess = { install ->
                    messageIsError = false
                    message = repoResultMessage("Refreshed", install)
                },
                onFailure = { e ->
                    messageIsError = true
                    message = e.message ?: "Could not refresh that repository."
                }
            )
        }
    }

    val selectedRepo = storeState.repos.firstOrNull { it.url == selectedRepoUrl }
    BackHandler(enabled = selectedRepo != null) { selectedRepoUrl = null }

    GlowBackground {
        if (selectedRepo != null) {
            PlugProvidersPage(
                repo = selectedRepo,
                plugIssues = plugIssues,
                plugSuccess = plugSuccess,
                testingKeys = testingKeys,
                onBack = { selectedRepoUrl = null },
                onPlugToggle = { plug, enabled ->
                    if (service != null) {
                        scope.launch {
                            service.store.setEnabled(plug.repoUrl, plug.entry.id, enabled)
                        }
                        // TEST v0.14.73 (standard model): a user
                        // toggle resets the plug's circuit breaker.
                        service.resetBreaker(plug.key)
                    }
                },
                onSetAll = { enabled ->
                    if (service != null) {
                        scope.launch {
                            service.store.setAllPlugsEnabled(selectedRepo.url, enabled)
                        }
                        // TEST v0.14.73: bulk toggle resets the
                        // breakers of the repo's plugs too.
                        service.resetBreakersForRepo(selectedRepo.url)
                    }
                },
                onTest = { plug ->
                    if (service != null && plug.key !in testingKeys) {
                        testingKeys = testingKeys + plug.key
                        scope.launch {
                            try {
                                service.testPlug(plug)
                            } finally {
                                testingKeys = testingKeys - plug.key
                            }
                        }
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item(key = "header") {
                    PlugsHeader(onBack = onBack)
                }

                item(key = "overview_label") {
                    PlugSectionHeader("Overview")
                }
                item(key = "overview_card") {
                    PlugCard {
                        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                            PlugPill(
                                "${storeState.repos.size} " +
                                    if (storeState.repos.size == 1) "repo" else "repos"
                            )
                            PlugPill(
                                "${storeState.plugs.size} " +
                                    if (storeState.plugs.size == 1) "plug" else "plugs"
                            )
                            PlugPill(if (storeState.plugsEnabled) "Plugs on" else "Plugs off")
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Enable plugs",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Use plugs when looking for streams.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = storeState.plugsEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled != storeState.plugsEnabled) hapticTick()
                                    if (service != null) {
                                        scope.launch { service.store.setMasterEnabled(enabled) }
                                    }
                                },
                                colors = settingsSwitchColors()
                            )
                        }
                        HorizontalDivider(
                            color = Color(0x1AFFFFFF),
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                        val plugCount = storeState.plugs.size
                        val repoCount = storeState.repos.size
                        Text(
                            text = "$plugCount ${if (plugCount == 1) "plug" else "plugs"} from " +
                                "$repoCount ${if (repoCount == 1) "repository" else "repositories"}" +
                                " · running on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFC9C9D0)
                        )
                    }
                }

                item(key = "add_label") {
                    PlugSectionHeader("Add Repository")
                }
                item(key = "add_card") {
                    PlugCard {
                        OutlinedTextField(
                            value = repoUrl,
                            onValueChange = { repoUrl = it },
                            label = { Text("Repository link") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))
                        PlugBigButton(
                            text = if (busy) "Installing…" else "Install Repository",
                            enabled = repoUrl.isNotBlank() && !busy,
                            onClick = {
                                if (service == null || busy) return@PlugBigButton
                                busy = true
                                message = null
                                scope.launch {
                                    val result = service.store.addRepo(repoUrl)
                                    busy = false
                                    result.fold(
                                        onSuccess = { install ->
                                            messageIsError = false
                                            message = repoResultMessage("Added", install)
                                            repoUrl = ""
                                        },
                                        onFailure = { e ->
                                            messageIsError = true
                                            message = e.message ?: "Could not add that repository."
                                        }
                                    )
                                }
                            }
                        )
                    }
                }

                if (message != null) {
                    item(key = "message") {
                        Text(
                            text = message!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (messageIsError) Color(0xFFFF8A80) else Color(0xFF7CF2B2),
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                        )
                    }
                }

                item(key = "repos_label") {
                    PlugSectionHeader("Installed Repositories")
                }
                if (storeState.repos.isEmpty()) {
                    item(key = "repos_empty") {
                        Text(
                            text = "No repositories yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    storeState.repos.forEach { repo ->
                        item(key = "repo_card_${repo.url}") {
                            Box(Modifier.padding(bottom = 14.dp)) {
                                RepoCard(
                                    repo = repo,
                                    refreshEnabled = !busy,
                                    onRefresh = { refreshRepo(repo) },
                                    onRemove = {
                                        if (service != null) {
                                            scope.launch { service.store.removeRepo(repo.url) }
                                        }
                                    },
                                    onRepoToggle = { enabled ->
                                        if (service != null) {
                                            scope.launch {
                                                service.store.setRepoEnabled(repo.url, enabled)
                                            }
                                        }
                                    },
                                    onOpen = { selectedRepoUrl = repo.url }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The providers page: one big card per plug in [repo]. */
@Composable
private fun PlugProvidersPage(
    repo: StoredRepo,
    plugIssues: Map<String, String>,
    plugSuccess: Map<String, Int>,
    testingKeys: Set<String>,
    onBack: () -> Unit,
    onPlugToggle: (StoredPlug, Boolean) -> Unit,
    onSetAll: (Boolean) -> Unit,
    onTest: (StoredPlug) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "header") {
            PlugsHeader(onBack = onBack)
        }
        item(key = "providers_label") {
            PlugSectionHeader("Providers · ${repo.name}")
        }
        if (repo.plugs.isNotEmpty()) {
            // Item H (v0.14.63): bulk on/off for THIS repo's plugs —
            // one store write through the same persisted state the
            // per-plug switches use (PlugStore.setAllPlugsEnabled).
            item(key = "bulk_row") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
                ) {
                    PlugHalfButton(
                        text = "All on",
                        modifier = Modifier.weight(1f),
                        onClick = { onSetAll(true) }
                    )
                    PlugHalfButton(
                        text = "All off",
                        modifier = Modifier.weight(1f),
                        onClick = { onSetAll(false) }
                    )
                }
            }
        }
        repo.plugs.forEach { plug ->
            item(key = "plug_card_${plug.key}") {
                Box(Modifier.padding(bottom = 16.dp)) {
                    PlugProviderCard(
                        plug = plug,
                        issue = plugIssues[plug.key],
                        lastCount = plugSuccess[plug.key],
                        testing = plug.key in testingKeys,
                        onToggle = { enabled -> onPlugToggle(plug, enabled) },
                        onTest = { onTest(plug) }
                    )
                }
            }
        }
        if (repo.plugs.isEmpty()) {
            item(key = "plugs_empty") {
                Text(
                    text = "No plugs in this repository.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Back arrow + the big "Plugs" title, both pages. */
@Composable
private fun PlugsHeader(onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
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
            text = "Plugs",
            fontSize = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
    }
}

/** Uppercase section label (OVERVIEW / ADD REPOSITORY / …). */
@Composable
private fun PlugSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
        fontWeight = FontWeight.Bold,
        color = PlugSectionText,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 12.dp)
    )
}

/** The sample's glass card: near-black fill, hairline border, 26dp corners. */
@Composable
private fun PlugCard(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(LocalPalette.current.panel)
            .border(1.dp, PlugCardBorder, RoundedCornerShape(26.dp))
            .padding(20.dp),
        content = content
    )
}

/** The sample's grey pill. */
@Composable
private fun PlugPill(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(PlugPillBg)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = PlugPillText
        )
    }
}

/** The sample's big white button (Install Repository / Test Provider). */
@Composable
private fun PlugBigButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PlugButtonBg,
            contentColor = PlugButtonText,
            disabledContainerColor = Color(0xFF3A3A40),
            disabledContentColor = PlugSectionText
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
    ) {
        Text(text = text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Half-width sibling of [PlugBigButton] for the All on / All off row. */
@Composable
private fun PlugHalfButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PlugButtonBg,
            contentColor = PlugButtonText
        ),
        modifier = modifier.height(48.dp)
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * One installed-repository card (main page): big name with the
 * refresh / remove icon buttons, version line, truncated URL,
 * and the "N plugs" pill beside the repo's own switch. Tapping
 * the card opens the repo's providers page; the foot strip
 * (item G, design C) repeats the invitation under a hairline.
 */
@Composable
private fun RepoCard(
    repo: StoredRepo,
    refreshEnabled: Boolean,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onRepoToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    // TEST v0.14.74 (item 8): switch flips tick.
    val hapticTick = com.aviostream.app.data.rememberHapticTick()
    PlugCard {
        // Item G (v0.14.63, design C): the whole card body opens the
        // repo's plug list; the refresh / remove buttons and the
        // repo switch keep their own actions (they consume their
        // taps, so they never fall through to the open).
        Column(Modifier.clickable { onOpen() }) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = repo.name,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpen() }
            )
            IconButton(
                onClick = onRefresh,
                enabled = refreshEnabled,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh repository",
                    tint = TextPrimary
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove repository",
                    tint = PlugRemovePink
                )
            }
        }
        Column(Modifier.clickable { onOpen() }) {
            if (repo.version != null) {
                Text(
                    text = "Version ${repo.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                text = repo.url,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlugPill(
                "${repo.plugs.size} " + if (repo.plugs.size == 1) "plug" else "plugs"
            )
            Spacer(Modifier.weight(1f))
            Switch(
                checked = repo.enabled,
                onCheckedChange = { value ->
                    if (value != repo.enabled) hapticTick()
                    onRepoToggle(value)
                },
                colors = settingsSwitchColors(),
                modifier = Modifier.scale(0.85f)
            )
        }
        }
        // Item G foot strip (design C): hairline divider, then a
        // centred "View all N plugs ›" row that opens the same list.
        HorizontalDivider(
            color = Color(0x1AFFFFFF),
            modifier = Modifier.padding(top = 16.dp)
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onOpen() }
                .padding(top = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View all ${repo.plugs.size} " +
                    if (repo.plugs.size == 1) "plug" else "plugs",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * One plug's big card (providers page): small repo line, puzzle
 * icon, name + manifest description, switch; the pills row
 * (supported types, version, JS/RULE); our status line — the
 * amber reason when the last run produced nothing ([issue]), the
 * green "Working" line when it returned streams ([lastCount]);
 * and the Test Provider button.
 */
@Composable
private fun PlugProviderCard(
    plug: StoredPlug,
    issue: String? = null,
    lastCount: Int? = null,
    testing: Boolean = false,
    onToggle: (Boolean) -> Unit,
    onTest: () -> Unit
) {
    // TEST v0.14.74 (item 8): switch flips tick.
    val hapticTick = com.aviostream.app.data.rememberHapticTick()
    PlugCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Extension,
                contentDescription = null,
                tint = PlugPuzzleGreen,
                modifier = Modifier
                    .padding(top = 22.dp)
                    .size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = plug.repoName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFEDEDF0)
                )
                Text(
                    text = plug.entry.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                if (!plug.entry.description.isNullOrBlank()) {
                    Text(
                        text = plug.entry.description!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            Switch(
                checked = plug.enabled,
                onCheckedChange = { value ->
                    if (value != plug.enabled) hapticTick()
                    onToggle(value)
                },
                colors = settingsSwitchColors(),
                modifier = Modifier.scale(0.85f)
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            PlugPill(plug.entry.supportedTypes.joinToString(" | "))
            if (plug.entry.version != null) {
                PlugPill("v${plug.entry.version}")
            }
            PlugPill(if (plug.entry.kind == PlugKind.RULE) "RULE" else "JS")
        }
        when {
            !issue.isNullOrBlank() -> Text(
                text = issue,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PlugStatusErr,
                modifier = Modifier.padding(top = 12.dp)
            )
            lastCount != null && lastCount > 0 -> Text(
                text = "Working · last title $lastCount " +
                    if (lastCount == 1) "stream" else "streams",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = PlugStatusOk,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        PlugBigButton(
            text = if (testing) "Testing…" else "Test Provider",
            enabled = !testing,
            onClick = onTest
        )
    }
}

/**
 * The add/refresh result line (v0.14.56), in the owner's short
 * style: a clean count when everything landed, the count plus the
 * first failed name when some plugs were skipped.
 */
private fun repoResultMessage(verb: String, result: RepoInstallResult): String =
    if (result.failed.isEmpty()) {
        "$verb ${result.installedCount} plugs"
    } else {
        "$verb ${result.installedCount} of ${result.totalCount} plugs" +
            " · failed: ${result.failed.first().name}" +
            (if (result.failed.size > 1) " + ${result.failed.size - 1} more" else "") +
            " — tap the repo to retry"
    }
