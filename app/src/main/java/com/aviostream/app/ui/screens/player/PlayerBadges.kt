package com.aviostream.app.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aviostream.app.data.StreamBadge
import com.aviostream.app.ui.components.StreamBadgeChip
import com.aviostream.app.ui.components.StreamBadgeChipSize

/**
 * Step 61 — image badges for the currently playing stream, shown under
 * the player header title. Mirrors Nuvio's PlayerBadges layout (a
 * left-aligned row, 4dp gaps); like Nuvio, only badges with an imageURL
 * are shown here — text-only badges stay on the stream list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerBadgesRow(
    badges: List<StreamBadge>,
    modifier: Modifier = Modifier,
) {
    if (badges.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge ->
            StreamBadgeChip(badge = badge, size = StreamBadgeChipSize.PLAYER)
        }
    }
}
