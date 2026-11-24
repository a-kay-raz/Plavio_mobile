package com.aviostream.app.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.ui.components.tmdbSizedImage
import com.aviostream.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

/**
 * The initial-load presentation (v0.14.33, owner's locked sample
 * "Poster Heartbeat + Status Line", replacing the 2026-10-24
 * "Backdrop + orbit" treatment on this path only): the title's
 * backdrop full-screen under a dark Onyx grade; bottom-left the
 * title and one quiet meta line; bottom-centre, lifted off the
 * edge, one useful line — the phase word (see
 * [PlayerLoadingPhase]) giving way to the picked source +
 * quality once a pick exists — above a frosted capsule.
 *
 * v0.14.41 (owner's order, 2026-11-13): the capsule shows
 * DETAILS ONLY — the "Starring …" cast line that used to ride
 * the quiet line above it moved INSIDE the capsule, and the
 * progress bar, the capsule's status word and the live elapsed
 * counter are gone entirely. The heartbeat, the centred name,
 * the quiet status line above and the details line under the
 * capsule are exactly as they were.
 *
 * v0.14.36 (item 4 + owner's amendment): the title shows the
 * same CLEAR way as Home and Details — the title LOGO as a plain
 * image with no wash, shade or panel behind it, the styled
 * title text as the fallback — and the lub-dub heartbeat rides
 * the NAME (logo or text), not the backdrop: the poster stays
 * static. Everything else in the locked design is unchanged.
 *
 * [active] is the initial-load gate (buffering/switching before
 * the first frame, no fatal error). When it drops, the overlay
 * fades out over the first frames instead of vanishing. The
 * rebuffer presentation is NOT here — it stays the plain orbit
 * over the video, untouched.
 */
@Composable
fun PlayerLoadingOverlay(
    active: Boolean,
    request: PlaybackRequest,
    phase: PlayerLoadingPhase,
    modifier: Modifier = Modifier
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "playerLoadingOverlayAlpha"
    )
    if (!active && overlayAlpha == 0f) return

    val onyx = Color(0xFF070707)

    // The heartbeat scale (shared — see rememberHeartbeatScale).
    val beatScale = rememberHeartbeatScale()

    // Live elapsed wait — restarts each time the load gate opens.
    var elapsedMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            val startMs = SystemClock.elapsedRealtime()
            while (true) {
                elapsedMs = SystemClock.elapsedRealtime() - startMs
                delay(50)
            }
        }
    }

    // The meta line fades in just after the title (the sample's
    // factsIn behaviour), then simply stays.
    val factsAlpha by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 700, delayMillis = 350),
        label = "playerLoadingFactsAlpha"
    )

    // The useful line above the capsule: the wait's real phase
    // word, giving way to the picked source + quality the moment
    // a pick truly exists. A pick counts only once the request
    // carries its stream — the source/quality are read off the
    // picked request itself, never guessed. (v0.14.41: the cast
    // line that shared this line since v0.14.34 moved INTO the
    // capsule — the status behaviour up here is unchanged.)
    val infoLine = playerLoadingPickedText(
        sourceName = request.sourceName.takeIf { request.streamUrl.isNotBlank() },
        qualityLabel = request.streamLabel.takeIf { request.streamUrl.isNotBlank() }
    ) ?: playerLoadingStatusText(phase)

    // The capsule's only content (v0.14.41 — owner's order): the
    // "Starring …" cast line, one member at a time off the same
    // elapsed clock. No cast on the request — no capsule at all.
    val castLine = playerLoadingCastLine(request.cast, elapsedMs)

    // The details line (year · runtime · genres · ★ rating).
    // v0.14.39 (owner correction 2026-11-13): it does NOT ride
    // with the name into the dead centre — mid-poster brightness
    // makes it unreadable there. It stays horizontally centred
    // but sits UNDER the loading status capsule with the bottom
    // group, its pre-.38 place near the bottom edge.
    val metaLine = playerLoadingMetaLine(
        request.releaseInfo, request.runtime, request.genres, request.imdbRating
    )

    Box(modifier.fillMaxSize().graphicsLayer { alpha = overlayAlpha }) {
        // TEST v0.14.77 (audit b): the loading backdrop is a
        // full-screen surface on the player's first frame — its
        // background is fetched sized (w1280, the hero
        // treatment), not the raw meta URL, which can be an
        // original-resolution image. The poster fallback keeps
        // its raw URL, as everywhere. (playerLoadingArt picks
        // background first, so sizing applies exactly when the
        // picked art IS the background.)
        val art = playerLoadingArt(request.background, request.poster)?.let { picked ->
            if (picked == request.background) tmdbSizedImage(picked, "w1280") else picked
        }
        if (art != null) {
            // v0.14.36: the backdrop is STATIC — the heartbeat
            // moved to the title name below. playerLoadingBackdropScale
            // pins the art's scale at 1f whatever the beat does.
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val still = playerLoadingBackdropScale(beatScale)
                        scaleX = still
                        scaleY = still
                    }
            )
        } else {
            // No art at all: plain Onyx, same texts (the fallback).
            Box(Modifier.fillMaxSize().background(onyx))
        }
        // Dark Onyx grading (the sample's two gradients).
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to onyx.copy(alpha = 0.42f),
                    0.55f to Color.Transparent
                )
            )
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to onyx.copy(alpha = 0.50f),
                    0.42f to onyx.copy(alpha = 0.14f),
                    1f to onyx.copy(alpha = 0.68f)
                )
            )
        )

        // The name block — the title logo/text ONLY. v0.14.37
        // (owner's small-fix batch item 1) moved it from
        // BOTTOM-LEFT to BOTTOM-CENTRE; v0.14.38 (owner: "i want
        // center from all four angles") moved it to the DEAD
        // CENTRE of the poster — aligned Center on both axes.
        // v0.14.39 (owner correction 2026-11-13): ONLY the
        // title/logo sits here; the details line that .38
        // dragged along returns to the bottom group (below).
        // The heartbeat and the bottom capsule are unchanged.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 24.dp, end = 24.dp)
                .widthIn(max = 560.dp)
        ) {
            LoadingTitleName(
                title = request.title,
                logo = request.logo,
                beatScale = beatScale
            )
        }

        // The bottom-centre group (owner's placement, v0.14.34 —
        // the capsule used to sit tucked under the title at the
        // bottom-left): the quiet useful line directly above the
        // capsule, both lifted off the edge by the one pinned
        // padding, the capsule its bottom item.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = LOADING_CAPSULE_BOTTOM_PADDING_DP.dp)
        ) {
            Crossfade(
                targetState = infoLine,
                animationSpec = tween(durationMillis = 350),
                label = "playerLoadingInfoLine"
            ) { line ->
                Text(
                    line,
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (castLine != null) {
                Spacer(Modifier.height(8.dp))
                // The capsule (v0.14.41 — owner's order: DETAILS
                // ONLY): the frosted pill now carries just the
                // "Starring …" cast line that used to sit on the
                // quiet line above it. The progress bar, the
                // capsule's status word and the elapsed counter
                // that filled it before are gone entirely — the
                // phase word lives on the quiet line above, the
                // heartbeat on the name, the details line below.
                // v0.14.42 (owner's order 2026-11-13): FIXED width —
                // the pill hugged its text, so its width jumped with
                // every cast name's length. It is now always
                // LOADING_CAPSULE_WIDTH_DP wide, the line centred;
                // an over-long name ellipsises inside it.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .width(LOADING_CAPSULE_WIDTH_DP.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Crossfade(
                        targetState = castLine,
                        animationSpec = tween(durationMillis = 350),
                        label = "playerLoadingCapsuleCast",
                        modifier = Modifier.fillMaxWidth()
                    ) { line ->
                        Text(
                            line,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
            // The details line (v0.14.39): horizontally centred,
            // directly UNDER the status capsule — the dark grade
            // at the poster's bottom edge keeps it readable,
            // unlike the bright middle .38 put it in.
            if (metaLine != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    metaLine,
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = factsAlpha }
                )
            }
        }
    }
}

/**
 * The loading overlay's title name (v0.14.36, item 4): the title
 * LOGO rendered BARE — a plain AsyncImage with no wash, shade or
 * panel behind it, the exact treatment of the Details and Home
 * heroes — taking the title's place in the dead-centre name
 * block (centred since v0.14.37) when the request carries a
 * logo. The styled title text is the
 * fallback, including when the logo's load fails. Whichever form
 * shows, the NAME breathes: the heartbeat scale rides this
 * element; the backdrop poster stays static. The block it sits
 * in is the poster's dead centre since v0.14.38.
 */
@Composable
/**
 * The loading heartbeat's scale (H1's lub-dub keyframes from the
 * locked sample, rises attacking expo-out, falls easing away) —
 * shared by the initial-load overlay and the mid-playback buffer
 * logo, so both breathe the same beat.
 */
@Composable
fun rememberHeartbeatScale(): Float {
    val beatTransition = rememberInfiniteTransition(label = "playerLoadingHeartbeat")
    val riseEasing = remember {
        CubicBezierEasing(
            HEARTBEAT_RISE_EASING[0], HEARTBEAT_RISE_EASING[1],
            HEARTBEAT_RISE_EASING[2], HEARTBEAT_RISE_EASING[3]
        )
    }
    val fallEasing = remember {
        CubicBezierEasing(
            HEARTBEAT_FALL_EASING[0], HEARTBEAT_FALL_EASING[1],
            HEARTBEAT_FALL_EASING[2], HEARTBEAT_FALL_EASING[3]
        )
    }
    val beatScale by beatTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = HEARTBEAT_CYCLE_MS
                HEARTBEAT_KEYFRAMES.forEachIndexed { index, (timeMs, scale) ->
                    if (index == HEARTBEAT_KEYFRAMES.lastIndex) {
                        scale at timeMs
                    } else if (heartbeatSegmentIsRise(index)) {
                        scale at timeMs with riseEasing
                    } else {
                        scale at timeMs with fallEasing
                    }
                }
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "playerLoadingHeartbeatScale"
    )
    return beatScale
}

/**
 * The title logo/name with the heartbeat, for mid-playback
 * buffers (seek rebuffer after a 10s skip, slow-network rebuffer,
 * source switch): the same breathing logo (or breathing title
 * text when no logo) as the loading screen, but with NO backdrop
 * — the video stays visible behind it. Replaces the circle
 * spinner the user asked to remove from the skip UI.
 */
@Composable
fun BufferingTitleLogo(title: String, logo: String?) {
    LoadingTitleName(title = title, logo = logo, beatScale = rememberHeartbeatScale())
}

private fun LoadingTitleName(title: String, logo: String?, beatScale: Float) {
    var logoFailed by remember(logo) { mutableStateOf(false) }
    val breathing = Modifier.graphicsLayer {
        val scale = playerLoadingNameScale(beatScale)
        scaleX = scale
        scaleY = scale
    }
    if (playerLoadingLogoShown(logo, logoFailed)) {
        AsyncImage(
            model = logo,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            onError = { logoFailed = true },
            modifier = breathing
                .height(LOADING_LOGO_HEIGHT_DP.dp)
                .widthIn(max = LOADING_LOGO_MAX_WIDTH_DP.dp)
        )
    } else {
        Text(
            title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 17.sp,
                shadow = androidx.compose.ui.graphics.Shadow(
                    Color.Black.copy(alpha = 0.65f),
                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                    blurRadius = 14f
                )
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = breathing
        )
    }
}
