package com.aviostream.app.ui.screens.player

import kotlin.math.exp

/**
 * The player's initial-load presentation (v0.14.33, owner's locked
 * sample "Poster Heartbeat + Status Line"): pure decision core —
 * the phases the wait really passes through, the words each phase
 * shows, the progress bar's honest easing and the heartbeat's
 * keyframes. The composable (PlayerLoadingOverlay) only renders
 * what this file decides, so every value is unit-pinned here.
 * v0.14.36 (item 4): the heartbeat's TARGET changed on the
 * owner's word — it animates the title name, not the backdrop
 * (see the name-treatment section at the bottom of this file);
 * the curve itself is untouched.
 */

/** The real phases of the initial wait, in the order they occur. */
enum class PlayerLoadingPhase { FINDING_STREAMS, PICKING_QUALITY, LOADING }

/** Which loading visual a buffering moment gets. */
enum class PlayerBufferVisual { NONE, STARTUP_OVERLAY, PLAIN_SPINNER }

/**
 * THE GATE (owner's scope pin, v0.14.34 — hard-closed after the
 * 0.14.33 device leak): the heartbeat overlay shows ONLY for
 * (1) the initial load, before this title has ever played, and
 * (2) resume buffering — the user pressed play after a pause and
 * the player is buffering back up. EVERY other mid-playback
 * buffer (seek, slow-network rebuffer, source switch) keeps the
 * plain spinner, exactly as before. A fatal error shows neither
 * (the error overlay takes the screen).
 *
 * [hasStartedOnce] must be latched per TITLE (never reset by a
 * request copy — see PlayerScreen); [resumeBuffering] is set by
 * the play press that ends a pause and cleared when playing
 * resumes.
 */
fun playerBufferVisual(
    isBuffering: Boolean,
    hasError: Boolean,
    hasStartedOnce: Boolean,
    resumeBuffering: Boolean
): PlayerBufferVisual = when {
    !isBuffering || hasError -> PlayerBufferVisual.NONE
    !hasStartedOnce || resumeBuffering -> PlayerBufferVisual.STARTUP_OVERLAY
    else -> PlayerBufferVisual.PLAIN_SPINNER
}

/** The capsule's status word for a phase — the sample's exact lines. */
fun playerLoadingStatusText(phase: PlayerLoadingPhase): String = when (phase) {
    PlayerLoadingPhase.FINDING_STREAMS -> "Finding streams…"
    PlayerLoadingPhase.PICKING_QUALITY -> "Picking best quality…"
    PlayerLoadingPhase.LOADING -> "Loading…"
}

/**
 * The phase a fresh request starts in: a request with no stream
 * yet (a Watch Now pending pick) is still FINDING its streams;
 * anything already carrying a URL — a direct pick, a resume, a
 * live channel, a trailer, a local file — goes straight to
 * LOADING (its find + pick already happened upstream).
 */
fun initialLoadingPhase(
    streamUrlBlank: Boolean,
    isLive: Boolean,
    isTrailer: Boolean
): PlayerLoadingPhase =
    if (streamUrlBlank && !isLive && !isTrailer) {
        PlayerLoadingPhase.FINDING_STREAMS
    } else {
        PlayerLoadingPhase.LOADING
    }

/**
 * The bar's fill fraction: `0.90 · (1 − e^(−t/2.4))`, t in
 * seconds — it eases toward 90% and NEVER reaches a fake 100%
 * (the sample's formula, verbatim).
 */
fun playerLoadingBarFraction(elapsedSeconds: Double): Float {
    val t = elapsedSeconds.coerceAtLeast(0.0)
    return (0.90 * (1.0 - exp(-t / 2.4))).toFloat()
}

/** "4.2s" — tenths, floored, no locale separators. */
fun formatLoadingElapsed(elapsedMs: Long): String {
    val tenths = (elapsedMs.coerceAtLeast(0L) / 100L).toInt()
    return "${tenths / 10}.${tenths % 10}s"
}

/** The wide art the loading state shows: backdrop first, poster as fallback. */
fun playerLoadingArt(background: String?, poster: String?): String? =
    background?.takeIf { it.isNotBlank() } ?: poster?.takeIf { it.isNotBlank() }

/**
 * Formats a meta runtime for the quiet line: addon runtimes
 * arrive as "148 min" (or occasionally already shaped "2h 28m");
 * minutes over an hour render as "2h 28m", shorter ones stay
 * "45 min", anything unrecognised passes through untouched.
 */
fun formatLoadingRuntime(runtime: String?): String? {
    val raw = runtime?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val minutes = Regex("""^(\d+)\s*min""").find(raw)?.groupValues?.get(1)?.toIntOrNull()
        ?: return raw
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m > 0) "${h}h ${m}m" else "${h}h"
    } else {
        "$minutes min"
    }
}

/**
 * The quiet meta line under the loading title: year · runtime ·
 * top genres · ★ rating, built ONLY from fields the serving meta
 * actually carried — a missing field simply doesn't render, and
 * with nothing at all the line itself is absent (null).
 */
fun playerLoadingMetaLine(
    releaseInfo: String?,
    runtime: String?,
    genres: List<String>,
    imdbRating: String?
): String? {
    val parts = mutableListOf<String>()
    releaseInfo?.let { info ->
        Regex("""\d{4}""").find(info)?.value?.let { parts += it }
    }
    formatLoadingRuntime(runtime)?.let { parts += it }
    genres.map { it.trim() }.filter { it.isNotBlank() }.take(2).forEach { parts += it }
    imdbRating?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "★ $it" }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * The status capsule's lift off the player's bottom edge: the
 * capsule anchors bottom-centre on the loading screen, clear of
 * the edges — the owner's placement (v0.14.34), pinned here so
 * the overlay and the tests share one number.
 */
const val LOADING_CAPSULE_BOTTOM_PADDING_DP = 56

/**
 * The loading details capsule's FIXED width (v0.14.42, owner's
 * order 2026-11-13): the pill used to hug its "Starring …" line,
 * so its width changed with every cast name — the capsule is now
 * always this wide and the line centres/ellipsises inside it.
 */
const val LOADING_CAPSULE_WIDTH_DP = 280

/** How long each cast name rests on the loading screen before the next. */
const val LOADING_CAST_ROTATE_MS = 2200L

/** The cast rotation never runs past the top-billed few. */
const val LOADING_CAST_MAX = 6

/**
 * The cast the loading line may show: the names the request
 * actually carried, trimmed, blanks dropped, top-billed first,
 * capped at [LOADING_CAST_MAX]. Empty in → empty out (the line
 * then never appears — nothing is ever padded or invented).
 */
fun playerLoadingCastNames(cast: List<String>): List<String> =
    cast.map { it.trim() }.filter { it.isNotBlank() }.take(LOADING_CAST_MAX)

/**
 * Which cast member is up at [elapsedMs]: a fresh name every
 * [LOADING_CAST_ROTATE_MS], cycling back to the first after the
 * last. Only meaningful for a non-empty cast (guarded by
 * [playerLoadingCastLine]); a non-positive count yields 0.
 */
fun playerLoadingCastIndex(elapsedMs: Long, castCount: Int): Int {
    if (castCount <= 0) return 0
    val step = (elapsedMs.coerceAtLeast(0L) / LOADING_CAST_ROTATE_MS).toInt()
    return step % castCount
}

/** The cast line's wording for one member. */
fun playerLoadingCastText(name: String): String = "Starring $name"

/**
 * The quiet cast line at [elapsedMs] — "Starring <name>", one
 * member at a time — or null when the title carried no cast, in
 * which case the line simply never appears.
 */
fun playerLoadingCastLine(cast: List<String>, elapsedMs: Long): String? {
    val names = playerLoadingCastNames(cast)
    if (names.isEmpty()) return null
    return playerLoadingCastText(names[playerLoadingCastIndex(elapsedMs, names.size)])
}

/**
 * The picked-stream line — "<source> · <quality>" from the REAL
 * chosen stream (the request's source display name + parsed
 * quality label, exactly as the pick recorded them). Whichever
 * part the pick truly carries renders; with neither, null — a
 * pick is never guessed or placeholdered.
 */
fun playerLoadingPickedText(sourceName: String?, qualityLabel: String?): String? {
    val source = sourceName?.trim()?.takeIf { it.isNotBlank() }
    val quality = qualityLabel?.trim()?.takeIf { it.isNotBlank() }
    return when {
        source != null && quality != null -> "$source · $quality"
        source != null -> source
        quality != null -> quality
        else -> null
    }
}

/**
 * The loading screen's one useful line (owner's completed
 * design, v0.14.34): with cast, the cast one by one; without
 * cast, the wait's real phase word — until a stream is truly
 * picked, when the picked source + quality takes the line over.
 * [pickedSource]/[pickedQuality] must be null until a pick
 * exists; if loading ends first, the line simply stays on the
 * phase words.
 */
fun playerLoadingInfoLine(
    cast: List<String>,
    phase: PlayerLoadingPhase,
    elapsedMs: Long,
    pickedSource: String?,
    pickedQuality: String?
): String {
    playerLoadingCastLine(cast, elapsedMs)?.let { return it }
    return playerLoadingPickedText(pickedSource, pickedQuality)
        ?: playerLoadingStatusText(phase)
}

/**
 * The heartbeat's full cycle length: H1's 2 s lub-dub from the
 * locked sample, stretched ×1.6 to a calm 3.2 s on the owner's
 * device verdict ("breath is so fast", v0.14.34).
 */
const val HEARTBEAT_CYCLE_MS = 3200

// ---------------------------------------------------------------------------
// The name treatment (v0.14.36, item 4 + owner's amendment): the
// loading overlay shows the title the same CLEAR way Home and
// Details do — the title LOGO as a plain image with no wash,
// shade or panel behind it, the styled title text as the
// fallback (including on logo load failure) — and the BREATHING
// moved off the backdrop onto the name: the logo (or the text)
// pulses with the heartbeat; the backdrop poster stays STATIC.
// This supersedes the poster-heartbeat half of the 2026-11-09
// lock on the owner's direct word; every other part of the
// locked loading design is unchanged.
// ---------------------------------------------------------------------------

/**
 * Whether the loading overlay shows the title-logo artwork
 * instead of the styled title text: artwork only when a logo
 * URL exists AND its load has not failed — the same rule the
 * Home hero applies (heroLogoShown).
 */
fun playerLoadingLogoShown(logo: String?, loadFailed: Boolean): Boolean =
    !logo.isNullOrBlank() && !loadFailed

/** The loading logo's height — the heroes' language (Home/Details, 76dp). */
const val LOADING_LOGO_HEIGHT_DP = 76

/** The loading logo's widest reach — the heroes' language (300dp). */
const val LOADING_LOGO_MAX_WIDTH_DP = 300

/**
 * The heartbeat scale as applied to the BACKDROP ART: always
 * 1f, whatever the beat is doing — the poster does not breathe
 * (v0.14.36 amendment: "name should breath, no poster").
 */
fun playerLoadingBackdropScale(beatScale: Float): Float = 1f

/**
 * The heartbeat scale as applied to the title NAME (the logo,
 * or the styled title text when no logo shows): the beat
 * itself, unmodified — same lub-dub curve, new target.
 */
fun playerLoadingNameScale(beatScale: Float): Float = beatScale

/**
 * Heartbeat keyframes as (timeMs in cycle, scale): the sample's
 * lub-dub shape with every time stretched ×1.6 — the lub swell
 * at 224 ms, the dip at 416, the dub peak at 704 (softened a
 * touch, 1.045 → 1.040, with the slower pace), settled by 1408,
 * then a long stillness to the cycle's end.
 */
val HEARTBEAT_KEYFRAMES: List<Pair<Int, Float>> = listOf(
    0 to 1.000f,
    224 to 1.028f,
    416 to 1.010f,
    704 to 1.040f,
    1088 to 1.006f,
    1408 to 1.000f,
    HEARTBEAT_CYCLE_MS to 1.000f
)

/** Cubic-bezier control points of a RISE segment (expo-out): fast attack, soft landing. */
val HEARTBEAT_RISE_EASING = floatArrayOf(0.16f, 1f, 0.3f, 1f)

/** Cubic-bezier control points of a FALL segment (standard): eases away gently. */
val HEARTBEAT_FALL_EASING = floatArrayOf(0.4f, 0f, 0.2f, 1f)

/**
 * True when the segment STARTING at [keyframeIndex] is a rise
 * (scale increases into the next keyframe) — the sample attacks
 * rises expo-out and lets falls ease away standard.
 */
fun heartbeatSegmentIsRise(keyframeIndex: Int): Boolean {
    val frames = HEARTBEAT_KEYFRAMES
    if (keyframeIndex < 0 || keyframeIndex >= frames.lastIndex) return false
    return frames[keyframeIndex + 1].second > frames[keyframeIndex].second
}
