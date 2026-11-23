package com.aviostream.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.DetailSectionPref
import com.aviostream.app.data.DetailSectionSettings
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.loadDetailSections
import com.aviostream.app.data.saveDetailSections
import com.aviostream.app.ui.components.GlassCard
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary

/**
 * Step 57: Detail section customization (Nuvio MetaScreenSettings).
 * Reorder the detail page's sections with the arrows, hide any
 * section with its switch, and turn on tab layout for a sticky tab
 * row that jumps to each section. Saves immediately, like the other
 * settings toggles.
 */
@Composable
fun DetailSectionsScreen(
    settings: SettingsStore,
    onBack: () -> Unit
) {
    val initial = remember { loadDetailSections(settings.detailSectionsJson) }
    var order by remember { mutableStateOf(initial.order) }
    var tabLayout by remember { mutableStateOf(initial.tabLayout) }

    fun persist(newOrder: List<DetailSectionPref> = order, newTabLayout: Boolean = tabLayout) {
        settings.detailSectionsJson = saveDetailSections(
            DetailSectionSettings(newOrder, newTabLayout)
        )
    }

    GlowBackground {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }
                Text(
                    text = "Detail sections",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Black),
                    color = TextPrimary
                )
            }
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = "Choose which sections the detail page shows, and in what order. Hidden sections stay hidden until you turn them back on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "Tab layout",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "A sticky tab row above the sections — tap a tab to jump to it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = tabLayout,
                                onCheckedChange = {
                                    tabLayout = it
                                    persist(newTabLayout = it)
                                }
                            )
                        }
                    }
                }
                itemsIndexed(order, key = { _, pref -> pref.key.name }) { index, pref ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val next = order.toMutableList().apply {
                                                val item = removeAt(index)
                                                add(index - 1, item)
                                            }
                                            order = next
                                            persist(newOrder = next)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowUp,
                                        contentDescription = "Move up",
                                        tint = if (index > 0) TextPrimary else TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (index < order.lastIndex) {
                                            val next = order.toMutableList().apply {
                                                val item = removeAt(index)
                                                add(index + 1, item)
                                            }
                                            order = next
                                            persist(newOrder = next)
                                        }
                                    },
                                    enabled = index < order.lastIndex,
                                    modifier = Modifier.padding(0.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        contentDescription = "Move down",
                                        tint = if (index < order.lastIndex) TextPrimary else TextSecondary
                                    )
                                }
                            }
                            Text(
                                text = pref.key.title,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary,
                                modifier = Modifier.padding(start = 4.dp).weight(1f)
                            )
                            Switch(
                                checked = pref.enabled,
                                onCheckedChange = { enabled ->
                                    val next = order.toMutableList().apply {
                                        set(index, pref.copy(enabled = enabled))
                                    }
                                    order = next
                                    persist(newOrder = next)
                                }
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
