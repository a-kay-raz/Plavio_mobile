package com.aviostream.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.ui.theme.TextPrimary

private val PAUSE_TEXT_SHADOW = Shadow(
    color = Color.Black.copy(alpha = 0.85f),
    offset = Offset(0f, 2f),
    blurRadius = 12f
)

/**
 * The pause screen (v0.14.30 port, feature #45): the dimmed frame
 * with the title's logo (or title text), meta line (year · runtime ·
 * ★ rating), genre chips, overview (max 2 lines) and a cast line
 * (first 3 names), plus the PAUSED pill top-right. Pure
 * presentation — every fact comes from the PlaybackRequest, and a
 * missing fact simply doesn't render. The visibility gates
 * (deliberate pause only, ready state only, never over the
 * controls, never while buffering) live at the call site in
 * PlayerScreen.
 */
@Composable
fun PauseScreenOverlay(req: PlaybackRequest) {
    Box(Modifier.fillMaxSize()) {
        // Dimmed frame: a flat shade plus a deeper bottom gradient,
        // so the centred block reads over any frame.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.55f)
                    )
                )
        )
        // PAUSED pill, top-right — two drawn bars, not a glyph, so
        // it renders identically in every font.
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 24.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(999.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(width = 3.dp, height = 10.dp)
                    .background(Color.White, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "PAUSED",
                color = TextPrimary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (req.logo != null) {
                AsyncImage(
                    model = req.logo,
                    contentDescription = req.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .heightIn(max = 84.dp)
                )
            } else {
                Text(
                    req.seriesName ?: req.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = PAUSE_TEXT_SHADOW
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            // Meta line: year · runtime · ★ rating — built only
            // from what the request carries; for series the episode
            // tag leads, as the top block's line does.
            val metaParts = mutableListOf<String>()
            if (!req.isMovie && req.season > 0) {
                metaParts += "S${req.season} · E${req.episode}"
            }
            req.releaseInfo?.let { info ->
                Regex("""\d{4}""").find(info)?.value?.let { metaParts += it }
            }
            formatLoadingRuntime(req.runtime)?.let { metaParts += it }
            req.imdbRating?.trim()?.takeIf { it.isNotBlank() }
                ?.let { metaParts += "★ $it" }
            if (metaParts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    metaParts.joinToString(" · "),
                    color = TextPrimary.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = PAUSE_TEXT_SHADOW
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (req.genres.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    req.genres.map { it.trim() }.filter { it.isNotBlank() }
                        .take(4).forEach { genre -> PauseScreenBadge(genre) }
                }
            }
            if (req.overview.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    req.overview,
                    color = TextPrimary.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = PAUSE_TEXT_SHADOW
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            if (req.cast.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    req.cast.take(3).joinToString(" · "),
                    color = TextPrimary.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodySmall.copy(
                        shadow = PAUSE_TEXT_SHADOW
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PauseScreenBadge(text: String) {
    Box(
        Modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
            .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            color = TextPrimary,
            maxLines = 1
        )
    }
}
