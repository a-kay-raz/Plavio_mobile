package com.pulsestream.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pulsestream.app.data.CatalogRow
import com.pulsestream.app.data.HOME_HERO_SOURCE_LIMIT
import com.pulsestream.app.data.HomeRowPlacement
import com.pulsestream.app.data.StoredHomeRowPref
import com.pulsestream.app.data.StoredHomeRowPrefs
import com.pulsestream.app.data.StreamRepository
import com.pulsestream.app.data.displayTitle
import com.pulsestream.app.data.hiddenItems
import com.pulsestream.app.data.rowKey
import com.pulsestream.app.data.visibleItems
import com.pulsestream.app.ui.components.EmptyState
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.components.LoadingState
import com.pulsestream.app.ui.theme.GlassBorder
import com.pulsestream.app.ui.theme.GlassWhite
import com.pulsestream.app.ui.theme.LocalPalette
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

class HomeCatalogsViewModel(private val repo: StreamRepository) : ViewModel() {
    var prefs by mutableStateOf(StoredHomeRowPrefs())
        private set
    var rows by mutableStateOf<List<CatalogRow>>(emptyList())
        private set
    var loading by mutableStateOf(true)
        private set

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            var all = repo.allHomeCatalogRows()
            if (all.isEmpty()) {
                runCatching { repo.refreshCatalogRows() }
                all = repo.allHomeCatalogRows()
            }
            rows = all
            prefs = repo.homeRowPrefs()
            loading = false
        }
    }

    private fun update(action: suspend () -> Unit) {
        viewModelScope.launch {
            action()
            prefs = repo.homeRowPrefs()
            rows = repo.allHomeCatalogRows()
        }
    }

    fun setEnabled(key: String, enabled: Boolean) = update { repo.setHomeRowEnabled(key, enabled) }
    fun setTitle(key: String, title: String) = update { repo.setHomeRowTitle(key, title) }
    fun setPlacement(key: String, placement: HomeRowPlacement) =
        update { repo.setHomeRowPlacement(key, placement) }
    fun setHeroSource(key: String, enabled: Boolean) = update { repo.setHomeRowHeroSource(key, enabled) }
    fun move(key: String, up: Boolean, visible: Boolean) = update { repo.moveHomeRow(key, up, visible) }
    fun setHeroEnabled(enabled: Boolean) = update { repo.setHomeHeroEnabled(enabled) }
    fun setShowCatalogType(show: Boolean) = update { repo.setHomeShowCatalogType(show) }
    fun setHideUnreleased(hide: Boolean) = update { repo.setHomeHideUnreleased(hide) }
    fun reset() = update { repo.resetHomeRowPrefs() }
}

/**
 * Dedicated screen for everything about Home catalogs — Nuvio's
 * Homescreen settings, ported in function: per-row hide/show, reorder,
 * rename and pin top/bottom, hero on/off + hero sources (max 2), show
 * catalog type, hide unreleased, and a Reset. Rows come from enabled
 * addons; a row is hidden here, never deleted.
 * Opened from Settings via the single "Home catalogs" row.
 */
@Composable
fun HomeCatalogsScreen(
    repository: StreamRepository,
    onBack: () -> Unit = {}
) {
    val vm: HomeCatalogsViewModel = viewModel(factory = vmFactory { HomeCatalogsViewModel(repository) })
    val prefs = vm.prefs
    val rows = vm.rows
    val rowByKey = remember(rows) { rows.associateBy { it.rowKey() } }
    val visiblePrefs = prefs.visibleItems()
    val hiddenPrefs = prefs.hiddenItems()
    val heroSelected = visiblePrefs.count { it.heroSourceEnabled }
    val heroAtCap = heroSelected >= HOME_HERO_SOURCE_LIMIT
    var expandedKey by rememberSaveable { mutableStateOf<String?>(null) }
    var showHidden by rememberSaveable { mutableStateOf(false) }

    GlowBackground {
        if (vm.loading) {
            LoadingState("Loading catalogs…")
            return@GlowBackground
        }
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
                            text = "Home catalogs",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose which catalog rows show on Home, arrange their " +
                            "order, and pick what feeds the hero.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(start = 56.dp, end = 8.dp)
                    )
                }
            }

            item(key = "summary") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "${visiblePrefs.size} of ${prefs.items.size} catalogs visible" +
                            " • $heroSelected hero sources selected",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            item(key = "layout_header") { HomeSectionHeader("HOME LAYOUT") }
            item(key = "hero_toggle") {
                HomeToggleRow(
                    title = "Show Hero Section",
                    subtitle = "The featured carousel at the top of Home",
                    checked = prefs.heroEnabled,
                    onCheckedChange = { vm.setHeroEnabled(it) }
                )
            }
            item(key = "type_toggle") {
                HomeToggleRow(
                    title = "Show catalog type",
                    subtitle = "Show the addon name under each row title",
                    checked = prefs.showCatalogType,
                    onCheckedChange = { vm.setShowCatalogType(it) }
                )
            }
            item(key = "unreleased_toggle") {
                HomeToggleRow(
                    title = "Hide unreleased content",
                    subtitle = "Hide titles whose release year is in the future",
                    checked = prefs.hideUnreleasedContent,
                    onCheckedChange = { vm.setHideUnreleased(it) }
                )
            }

            if (prefs.heroEnabled) {
                item(key = "hero_header") { HomeSectionHeader("HERO SOURCES") }
                // Keys are prefixed per section: the hero list and the
                // catalog list render the SAME prefs, and LazyColumn keys
                // must be unique across the whole list — unprefixed keys
                // crashed on scroll (duplicate key, dev-7).
                items(visiblePrefs, key = { "hero:${it.key}" }) { pref ->
                    val row = rowByKey[pref.key] ?: return@items
                    HeroSourceRow(
                        title = prefs.displayTitle(row),
                        subtitle = row.addonName,
                        checked = pref.heroSourceEnabled,
                        switchEnabled = pref.heroSourceEnabled || !heroAtCap,
                        onToggle = { vm.setHeroSource(pref.key, it) }
                    )
                }
                if (heroAtCap) {
                    item(key = "hero_cap") {
                        Text(
                            "Limit reached — turn one off to pick another.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            item(key = "catalogs_header") {
                HomeSectionHeader("CATALOGS") {
                    TextButton(onClick = { vm.reset() }) { Text("Reset") }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        title = "No catalog addons",
                        message = "Install an addon with catalogs first, then arrange them here.",
                    )
                }
            } else {
                items(visiblePrefs, key = { "row:${it.key}" }) { pref ->
                    val row = rowByKey[pref.key] ?: return@items
                    val idx = visiblePrefs.indexOfFirst { it.key == pref.key }
                    HomeCatalogRow(
                        row = row,
                        pref = pref,
                        title = prefs.displayTitle(row),
                        expanded = expandedKey == pref.key,
                        onToggleExpand = {
                            expandedKey = if (expandedKey == pref.key) null else pref.key
                        },
                        onToggle = { vm.setEnabled(pref.key, it) },
                        onUp = { vm.move(pref.key, up = true, visible = true) },
                        onDown = { vm.move(pref.key, up = false, visible = true) },
                        canUp = idx > 0 &&
                            visiblePrefs[idx - 1].rowPlacement == pref.rowPlacement,
                        canDown = idx < visiblePrefs.size - 1 &&
                            visiblePrefs[idx + 1].rowPlacement == pref.rowPlacement,
                        onTitle = { vm.setTitle(pref.key, it) },
                        onPlacement = { vm.setPlacement(pref.key, it) }
                    )
                }
            }

            if (hiddenPrefs.isNotEmpty()) {
                item(key = "hidden_header") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "HIDDEN ROWS (${hiddenPrefs.size})",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                        TextButton(onClick = { showHidden = !showHidden }) {
                            Text(if (showHidden) "Hide" else "Show")
                        }
                    }
                }
                if (showHidden) {
                    items(hiddenPrefs, key = { "hidden:${it.key}" }) { pref ->
                        val row = rowByKey[pref.key] ?: return@items
                        val idx = hiddenPrefs.indexOfFirst { it.key == pref.key }
                        HomeCatalogRow(
                            row = row,
                            pref = pref,
                            title = prefs.displayTitle(row),
                            expanded = expandedKey == pref.key,
                            onToggleExpand = {
                                expandedKey = if (expandedKey == pref.key) null else pref.key
                            },
                            onToggle = { vm.setEnabled(pref.key, it) },
                            onUp = { vm.move(pref.key, up = true, visible = false) },
                            onDown = { vm.move(pref.key, up = false, visible = false) },
                            canUp = idx > 0 &&
                                hiddenPrefs[idx - 1].rowPlacement == pref.rowPlacement,
                            canDown = idx < hiddenPrefs.size - 1 &&
                                hiddenPrefs[idx + 1].rowPlacement == pref.rowPlacement,
                            onTitle = { vm.setTitle(pref.key, it) },
                            onPlacement = { vm.setPlacement(pref.key, it) }
                        )
                    }
                }
            }

            item(key = "note") {
                Text(
                    "Rows come from your enabled addons. Disable an addon to " +
                        "remove its rows, or hide them here — hidden rows can " +
                        "always be brought back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TextSecondary
        )
        action?.invoke()
    }
}

@Composable
private fun HomeToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val palette = LocalPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = { onCheckedChange(!checked) }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.accent,
                    checkedTrackColor = palette.accent.copy(alpha = 0.35f)
                )
            )
        }
    }
}

@Composable
private fun HeroSourceRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchEnabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val palette = LocalPalette.current
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                enabled = switchEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.accent,
                    checkedTrackColor = palette.accent.copy(alpha = 0.35f)
                )
            )
        }
    }
}

@Composable
private fun HomeCatalogRow(
    row: CatalogRow,
    pref: StoredHomeRowPref,
    title: String,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    canUp: Boolean,
    canDown: Boolean,
    onTitle: (String) -> Unit,
    onPlacement: (HomeRowPlacement) -> Unit
) {
    val palette = LocalPalette.current
    val placement = HomeRowPlacement.fromStorage(pref.rowPlacement)
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        onClick = onToggleExpand
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(1.dp))
                    Text(
                        (if (pref.enabled) "Showing on Home" else "Hidden from Home") +
                            " • " + row.addonName,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    if (placement != HomeRowPlacement.NORMAL) {
                        Text(
                            if (placement == HomeRowPlacement.PINNED_TOP) "Pinned to top"
                            else "Pinned to bottom",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.accentText
                        )
                    }
                }
                IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onUp, enabled = canUp, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canUp) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = onDown, enabled = canDown, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canDown) TextPrimary else TextSecondary.copy(alpha = 0.35f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Switch(
                    checked = pref.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = palette.accent,
                        checkedTrackColor = palette.accent.copy(alpha = 0.35f)
                    )
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                var draft by remember(pref.key, pref.customTitle) {
                    mutableStateOf(pref.customTitle)
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Display name") },
                    placeholder = { Text(row.catalogName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onTitle(draft) }),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onTitle(draft) }) { Text("Save name") }
                }
                Text(
                    "Home placement",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PlacementButton(
                        label = "Default",
                        selected = placement == HomeRowPlacement.NORMAL,
                        onClick = { onPlacement(HomeRowPlacement.NORMAL) }
                    )
                    PlacementButton(
                        label = "Pin top",
                        selected = placement == HomeRowPlacement.PINNED_TOP,
                        onClick = { onPlacement(HomeRowPlacement.PINNED_TOP) }
                    )
                    PlacementButton(
                        label = "Pin bottom",
                        selected = placement == HomeRowPlacement.PINNED_BOTTOM,
                        onClick = { onPlacement(HomeRowPlacement.PINNED_BOTTOM) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlacementButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .background(
                if (selected) palette.accent else GlassWhite,
                RoundedCornerShape(50)
            )
            .border(1.dp, GlassBorder, RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color(0xFF04120B) else TextPrimary
        )
    }
}
