package com.pulsestream.app.ui.screens

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
import com.pulsestream.app.data.PlayerBottomButtonLayout
import com.pulsestream.app.data.PlayerButtonLayouts
import com.pulsestream.app.data.PlayerTopButtonLayout
import com.pulsestream.app.data.SettingsStore
import com.pulsestream.app.ui.components.GlassCard
import com.pulsestream.app.ui.components.GlowBackground
import com.pulsestream.app.ui.theme.TextPrimary
import com.pulsestream.app.ui.theme.TextSecondary

/**
 * Step 63: Player toolbar customization (P25).
 * Reorder/hide the bottom pill buttons and the top cluster buttons
 * of the player. Saves immediately, like the detail-sections screen.
 * Back stays always last in the top cluster and is not listed here.
 */
@Composable
fun PlayerToolbarScreen(
    settings: SettingsStore,
    onBack: () -> Unit
) {
    var bottom by remember { mutableStateOf(PlayerButtonLayouts.decodeBottom(settings.playerBottomLayout)) }
    var top by remember { mutableStateOf(PlayerButtonLayouts.decodeTop(settings.playerTopLayout)) }

    fun persistBottom(layout: PlayerBottomButtonLayout) {
        settings.playerBottomLayout = PlayerButtonLayouts.encodeBottom(layout)
    }
    fun persistTop(layout: PlayerTopButtonLayout) {
        settings.playerTopLayout = PlayerButtonLayouts.encodeTop(layout)
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
                    text = "Player buttons",
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
                        text = "Choose which player buttons show, and in what order. Hidden buttons stay hidden until you turn them back on.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                item {
                    Text(
                        text = "Bottom pill",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                itemsIndexed(bottom.entries, key = { _, entry -> entry.button.storageId }) { index, entry ->
                    ToolbarButtonRow(
                        label = entry.button.label,
                        visible = entry.visible,
                        canMoveUp = index > 0,
                        canMoveDown = index < bottom.entries.lastIndex,
                        onMoveUp = {
                            val next = bottom.moved(index, index - 1)
                            bottom = next
                            persistBottom(next)
                        },
                        onMoveDown = {
                            val next = bottom.moved(index, index + 1)
                            bottom = next
                            persistBottom(next)
                        },
                        onVisibilityChange = {
                            val next = bottom.withVisibility(entry.button, it)
                            bottom = next
                            persistBottom(next)
                        }
                    )
                }
                item {
                    Text(
                        text = "Top cluster",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Text(
                        text = "Back stays always last and is not listed here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                itemsIndexed(top.entries, key = { _, entry -> entry.button.storageId }) { index, entry ->
                    ToolbarButtonRow(
                        label = entry.button.label,
                        visible = entry.visible,
                        canMoveUp = index > 0,
                        canMoveDown = index < top.entries.lastIndex,
                        onMoveUp = {
                            val next = top.moved(index, index - 1)
                            top = next
                            persistTop(next)
                        },
                        onMoveDown = {
                            val next = top.moved(index, index + 1)
                            top = next
                            persistTop(next)
                        },
                        onVisibilityChange = {
                            val next = top.withVisibility(entry.button, it)
                            top = next
                            persistTop(next)
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun ToolbarButtonRow(
    label: String,
    visible: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = "Move up",
                        tint = if (canMoveUp) TextPrimary else TextSecondary
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.padding(0.dp)
                ) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Move down",
                        tint = if (canMoveDown) TextPrimary else TextSecondary
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            Switch(checked = visible, onCheckedChange = onVisibilityChange)
        }
    }
}
