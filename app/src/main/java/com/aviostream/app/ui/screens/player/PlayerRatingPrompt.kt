package com.aviostream.app.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The end-of-play rating prompt (v0.14.30 port): raised when a video
 * ends. [metaId] is the local-rating key (the fork's UserRatings);
 * [promptKey] is the skip-count key — per title for movies, per
 * EPISODE for series; [imdbId] + [title] feed the Trakt push;
 * [forVideoId] ties the prompt to the video whose end raised it.
 */
internal data class EndRatingPrompt(
    val metaId: String,
    val promptKey: String,
    val type: String,
    val imdbId: String?,
    val title: String,
    val forVideoId: String
)

/**
 * The end-of-play rating card (v0.14.30 port — rating lives at the
 * end of playback, never on Details): five stars (the fork's local
 * scale), one tap rates. The tap writes the on-device store and
 * pushes to Trakt via [onRate], whose returned line ("Synced to
 * Trakt" only when true, else saved-on-device with the short reason)
 * shows briefly before the card leaves. ✕ skips — [onSkip] records
 * it; two skips for the same title/episode keep the prompt quiet.
 */
@Composable
internal fun EndRatingPromptCard(
    prompt: EndRatingPrompt,
    onRate: suspend (Int) -> String,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val palette = LocalPalette.current
    var picked by remember(prompt) { mutableIntStateOf(0) }
    var resultLine by remember(prompt) { mutableStateOf<String?>(null) }
    LaunchedEffect(resultLine) {
        if (resultLine != null) {
            delay(2_500)
            onDone()
        }
    }
    Surface(
        color = Color.Black.copy(alpha = 0.78f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        if (resultLine != null) {
            Text(
                resultLine!!,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
            )
        } else {
            Row(
                Modifier.padding(start = 18.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    "Rate this",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(Modifier.width(8.dp))
                for (star in 1..5) {
                    Icon(
                        if (star <= picked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Rate $star",
                        tint = if (star <= picked) palette.accent
                            else TextSecondary.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable {
                                if (picked == 0) {
                                    picked = star
                                    scope.launch {
                                        resultLine = onRate(star)
                                    }
                                }
                            }
                    )
                }
                Text(
                    "✕",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onSkip() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Renders the end-of-play rating card at the bottom centre, lifted
 * above the next-episode card while that is showing.
 */
@Composable
internal fun BoxScope.PlayerRatingPrompt(
    prompt: EndRatingPrompt,
    nextEpisodeCardVisible: Boolean,
    onRate: suspend (Int) -> String,
    onSkip: () -> Unit,
    onDone: () -> Unit
) {
    EndRatingPromptCard(
        prompt = prompt,
        onRate = onRate,
        onSkip = onSkip,
        onDone = onDone,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = if (nextEpisodeCardVisible) 178.dp else 120.dp
            )
    )
}
