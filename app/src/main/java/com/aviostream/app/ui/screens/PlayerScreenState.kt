package com.aviostream.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.ui.PlayerView
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.ParentalWarning
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.data.ResolvedSubtitle
import com.aviostream.app.data.SettingsStore
import com.aviostream.app.data.SkipSegment
import com.aviostream.app.data.StreamItem
import com.aviostream.app.data.StreamResult
import com.aviostream.app.data.Subtitle
import com.aviostream.app.data.SubtitleStyleOverride
import com.aviostream.app.data.Video
import com.aviostream.app.ui.screens.player.*
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Job

/**
 * All mutable UI/playback state for [PlayerScreen], hoisted into one holder.
 *
 * PlayerScreen used to declare 130 `by remember` delegates inline; the
 * resulting method (429 registers) made D8's register allocator emit
 * invalid dex (VerifyError, moving offset on every patch). Holding the
 * state in one object leaves the composable with a single `state` local,
 * so the allocator stops producing invalid merges. Behaviour is
 * identical: same types, same initial values, same reset semantics for
 * the six keyed vars that stay local in PlayerScreen (they reset when
 * their key changes) and for `player` (its initial value needs the local
 * buildExo/guardedPlayerListener helpers).
 *
 * Constructor params are used only for initial values and are not stored.
 */
internal class PlayerScreenState(
    settings: SettingsStore,
    initialRequest: PlaybackRequest,
    isTv: Boolean,
) {

    /** Non-null while the current stream is in a failed state (#4). */
    var playerError by mutableStateOf<String?>(null)
    /** Last ExoPlayer error code, so effects can tell a dead link from a stall. */
    var lastPlayerErrorCode by mutableStateOf(0)
    /** Auto-failover count; generic playback caps at the fallback setting, mobile Live uses its policy cap. */
    var autoFailoverCount by mutableStateOf(0)
    /**
     * Stream URL the VOD stall watchdog already resumed once (Step 40,
     * Nuvio PlaybackReopenSession: one reopen per source). Reset when
     * playback resumes playing.
     */
    var vodStallResumeAttemptedFor by mutableStateOf<String?>(null)
    /** Bounded recovery budget for streams that play and later stall, per channel session. */
    var liveRebufferRecoveries by mutableIntStateOf(0)
    /** Nuvio parity: self-heal — probe Content-Type and retry once on format errors. */
    var mimeHealAttempted by mutableStateOf(false)
    /**
     * Set after player state exists; invoked by errorListener for the
     * self-heal path. Indirection avoids forward-reference issues.
     */
    var healOnFormatError: ((PlaybackException) -> Unit)? by mutableStateOf(null)
    /** Audio self-heal: retry once with downmix on after an audio-output error. */
    var downmixFailoverAttempted by mutableStateOf(false)
    /**
     * Set after the local helpers below are declared; invoked by errorListener
     * for the audio-downmix retry. Indirection avoids forward-reference issues
     * (same pattern as healOnFormatError).
     */
    var retryWithDownmix: (() -> Unit)? by mutableStateOf(null)
    /**
     * Set after the local helpers below are declared; invoked by errorListener
     * for the auto-failover path. Indirection avoids forward-reference issues
     * (same pattern as healOnFormatError).
     */
    var failoverToNextSource: ((attempt: Int) -> Unit)? by mutableStateOf(null)
    var audioDelayMs by mutableIntStateOf(settings.audioDelayMs)
    var downmixOn by mutableStateOf(settings.downmixEnabled)
    var downmixTarget by mutableIntStateOf(settings.downmixTargetChannels)
    var centerMixDb by mutableIntStateOf(settings.centerMixLevelDb)
    var keepOriginalLevels by mutableStateOf(settings.maintainOriginalAudioOnDownmix)
    // Step 68: parental guide warnings for the current title.
    var parentalWarnings by mutableStateOf(emptyList<ParentalWarning>())
    var parentalGuideVisible by mutableStateOf(false)
    var currentRequest by mutableStateOf(initialRequest)
    // Step 38: SDH cue filtering — Media3 1.4.1 has no ExoPlayer.setTextOutput,
    // so cues are intercepted via Player.Listener.onCues. The listener is
    // (re)attached after PlayerView registers its own, so the filtered cues
    // are applied last to the same SubtitleView.
    var cueFilterListener by mutableStateOf<Player.Listener?>(null)
    var cueFilterPlayer by mutableStateOf<ExoPlayer?>(null)
    var scrubPositionMs by mutableStateOf<Long?>(null)
    var playerView by mutableStateOf<PlayerView?>(null)
    /**
     * Side-loaded subtitles, resolved (downloaded + format-sniffed) in the
     * background for the current request. Kept in state so a rebuilt player
     * (source switch) can carry them from its first prepare instead of
     * re-preparing the video to attach them (#29).
     */
    var resolvedSubs by mutableStateOf(emptyList<ResolvedSubtitle>())
    /**
     * Raw (unshifted) downloaded subtitles — the source of truth for timing
     * shifts. The in-player +/- timing buttons re-shift from these files,
     * never from already-shifted output (which would compound the offset).
     */
    var rawResolvedSubs by mutableStateOf(emptyList<ResolvedSubtitle>())
    /**
     * Per-video subtitle delay (ms), added to the global
     * [SettingsStore.subtitleOffsetMs]. Set from the Style rail's delay
     * stepper / Auto Sync and persisted per video id
     * ([SettingsStore.subtitleDelayMsFor]); reloaded whenever the title
     * changes (see the load effect below the resolver).
     */
    var subOffsetOverrideMs by mutableLongStateOf(0L)
    /**
     * Which subtitle set is already attached to which player instance.
     * Attaching the same set twice would re-prepare the video for nothing.
     */
    var subsAttached by mutableStateOf<Pair<ExoPlayer, String>?>(null)
    /**
     * Sidecar subtitle engine (#26): the URL of the subtitle currently
     * rendered off-player by AvioSidecarSubtitles, or null when the
     * sidecar is idle (the media path, MPV, or an embedded track owns
     * the captions instead).
     */
    var sidecarSubUrl by mutableStateOf<String?>(null)
    /** Parsed cues for the active sidecar subtitle, ordered by start time. */
    var sidecarCues by mutableStateOf(emptyList<CuesWithTiming>())
    /**
     * True once the user made an explicit subtitle choice for this
     * title (a manual pick — including "None" — or an embedded pick):
     * the automatic default must not override it on a later republish.
     * Reset when the title changes.
     */
    var sidecarTouched by mutableStateOf(false)
    /** Monotonic generation: a stale sidecar parse may never overwrite a newer activation. */
    val sidecarGeneration = AtomicInteger(0)
    var showControls by mutableStateOf(true)
    var controlsTick by mutableIntStateOf(0)
    var locked by mutableStateOf(false)
    // Nuvio legacy locked overlay: shown on lock, auto-hides after 3.5s,
    // revealed again by tapping while locked.
    var lockedOverlayVisible by mutableStateOf(true)
    var isPlaying by mutableStateOf(true)
    var positionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    // Nuvio legacy: the total time pill toggles elapsed / remaining on tap.
    var showRemainingTime by mutableStateOf(false)
    var volumePercent by mutableIntStateOf(0)
    // Volume boost (past 100%): a LoudnessEnhancer on the player's audio
    // session adds up to +10 dB on top of the system max. volumeBoost is
    // 0f..1f; the shown percent is 100 + boost*100 while system is at max.
    // It starts at the persisted Audio-modal value (Step 13: Nuvio
    // audioAmplificationDb) so a set boost applies on the next title.
    var volumeBoost by mutableFloatStateOf(settings.audioBoostDb / 10f)
    var speed by mutableFloatStateOf(1f)
    var speedMenuOpen by mutableStateOf(false)
    /** Playback engine picker (centered dialog) open — manual mid-play switch. */
    var engineMenuOpen by mutableStateOf(false)
    /** Sleep timer (v0.14.30 port): wall-clock deadline for the timed options, 0 = none. */
    var sleepDeadlineMs by mutableLongStateOf(0L)
    /** Sleep timer: true while "End of episode" is armed. */
    var sleepAtEpisodeEnd by mutableStateOf(false)
    /** Sleep timer: seconds left on the timed deadline, for the chip + pill label. */
    var sleepRemainingSec by mutableIntStateOf(0)
    /** Sleep timer: true once a sleep pause released the screen hold (restored on resume). */
    var sleepScreenReleased by mutableStateOf(false)
    /** Sleep timer menu (centered dialog) open. */
    var sleepMenuOpen by mutableStateOf(false)
    /** Sleep timer: the minutes option chosen for the timed deadline, 0 = none. */
    var sleepChosenMinutes by mutableIntStateOf(0)
    /** True while the hold-for-speed gesture is boosting playback (v0.12.0). */
    var holdSpeedActive by mutableStateOf(false)
    /** Current hold-boost speed shown in the hold pill: the Hold speed
     * setting at engage, sliding up to 5x while held (v0.12.1). */
    var holdSpeedStep by mutableFloatStateOf(2f)
    /** Accumulated double-tap seek (Nuvio handleDoubleTapSeek): repeated
     * taps in the same direction add up from one baseline; the 800ms
     * reset job clears it. */
    var accumulatedSeekDirection by mutableStateOf<Int?>(null)
    var accumulatedSeekBaselineMs by mutableLongStateOf(0L)
    var accumulatedSeekAmountMs by mutableLongStateOf(0L)
    var accumulatedSeekResetJob by mutableStateOf<Job?>(null)
    /** Pinch zoom factor on the video surface (Nuvio pinchVideoZoom):
     * 0.8–2.0x, snapped to 1.0x within 0.04. */
    var videoZoom by mutableFloatStateOf(1f)
    var rawVideoZoom by mutableFloatStateOf(1f)
    /** Pre-resolved next-episode streams (Nuvio preloadNextEpisodeSources):
     * fetched while the countdown card shows, consumed by playEpisode. */
    var prefetchedNextStreams by mutableStateOf<Pair<String, com.aviostream.app.data.StreamResult>?>(null)
    var prefetchingNextId by mutableStateOf<String?>(null)
    /** Skip-button auto-hide (Nuvio SkipIntroButton): manually dismissed
     * for this segment, auto-hidden after 10s with controls down, and
     * the 0..1 progress of that countdown. */
    var skipDismissed by mutableStateOf(false)
    var skipAutoHidden by mutableStateOf(false)
    var indicator by mutableStateOf<PlayerIndicator?>(null)
    var indicatorTick by mutableIntStateOf(0)
    // YouTube opens portrait-first (user rotates via the Rotate pill);
    // everything else opens landscape as before.
    var isPortrait by mutableStateOf(initialRequest.sourceName == "YouTube")
    // Aspect ratio (in-player only — cycles Fit → Fill → Zoom)
    var aspectMode by mutableIntStateOf(settings.resizeMode)
    var isBuffering by mutableStateOf(true)
    // Staged startup text (Nuvio PlayerLoadingStatus): what the player is
    // doing right now, updated by the poll below — "Opening source…" while
    // the media is being (re)built or the player is idle, "Reading
    // tracks…" once preparing but before any track is known, "Buffering…"
    // once tracks are in and data is awaited.
    var loadingStep by mutableStateOf("Opening source…")
    var switchingMedia by mutableStateOf(false)
    // Track switching
    var tracksOpen by mutableStateOf(false)
    // External-player protected-link warning (owner's order 2026-10-26):
    // non-null while the warning popup is up for that request.
    var externalWarningReq by mutableStateOf<PlaybackRequest?>(null)
    var externalWarningHide by mutableStateOf(false)
    var tracksMode by mutableStateOf("subs")  // "subs" or "audio"
    var trackTick by mutableIntStateOf(0)
    /** Set when the user hand-picks an audio track — the preferred-language
     * auto-select then leaves their pick alone for this player session. */
    var userPickedAudio by mutableStateOf(false)
    // Sources + episodes sheets
    var sourcesOpen by mutableStateOf(false)
    var episodesOpen by mutableStateOf(false)
    // Step 41: shuffle sheet state + one no-repeat picker per request.
    var shuffleOpen by mutableStateOf(false)
    // Per-episode stream choice (Nuvio PlayerEpisodesPanel): the episode
    // whose streams are being browsed in its own sheet.
    var episodeStreamsVideo by mutableStateOf<Video?>(null)
    var episodeStreams by mutableStateOf<List<com.aviostream.app.data.StreamItem>>(emptyList())
    var episodeStreamsSubtitles by mutableStateOf<List<Subtitle>>(emptyList())
    var episodeStreamsLoading by mutableStateOf(false)
    var episodeStreamsTick by mutableStateOf(0)
    // Quality picker: null = Auto (best available quality), otherwise
    // the picked quality label. Resets per video.
    var qualityOpen by mutableStateOf(false)
    var infoOpen by mutableStateOf(false)
    var downloadStream by mutableStateOf<com.aviostream.app.data.StreamItem?>(null)
    var zapping by mutableStateOf(false)
    var availableStreams by 
        mutableStateOf(com.aviostream.app.ui.screens.player.mergePlayerSourceSet(
            emptyList(), initialRequest.streams, includeHeadersInIdentity = initialRequest.isLive && !isTv
        ))
    /** Live fallback never retries the same URL/header tuple during this channel session. */
    var attemptedLiveSourceIds by mutableStateOf(emptySet<String>())
    /** Count of streams hidden because the device can't decode them (e.g. 4K on budget phones). */
    var hiddenIncompatibleCount by mutableStateOf(0)
    var streamsLoading by mutableStateOf(false)
    var streamsJob by mutableStateOf<Job?>(null)
    var pendingLiveFallbackJob by mutableStateOf<Job?>(null)
    // Resume prompt
    var resumeOfferMs by mutableStateOf<Long?>(null)
    // Skip segments: IntroDB intervals (per title) + chapter-derived
    // intervals (per source) merge with chapters winning per type.
    var apiSkipSegments by mutableStateOf(emptyList<SkipSegment>())
    var chapterSkipSegments by mutableStateOf(emptyList<SkipSegment>())
    var activeSegment by mutableStateOf<SkipSegment?>(null)
    // Segments already auto-skipped for this title (Nuvio
    // autoSkippedIntervals): once per segment — a manual seek back
    // into one doesn't instantly re-skip it.
    var autoSkippedSegments by mutableStateOf<Set<SkipSegment>>(emptySet())
    // Chapters (Nuvio PlayerChapters): read once per source from the
    // file itself (ExoPlayer's Matroska extractor skips them).
    var chapters by mutableStateOf<List<PlayerChapter>>(emptyList())
    var chaptersOpen by mutableStateOf(false)
    // Step 63: in-player live-channels panel.
    var channelsOpen by mutableStateOf(false)
    // Next-episode countdown card (Nuvio NextEpisodeCard): the resolved
    // next episode + the live countdown; Cancel marks the episode handled
    // so the silent end-of-playback advance never fires for it.
    var nextEpisodeCard by mutableStateOf<Video?>(null)
    var nextEpisodeCountdown by mutableStateOf<Int?>(null)
    /** End-of-play rating prompt (v0.14.30 port): the prompt the finished video raised, or null. */
    var ratingPrompt by mutableStateOf<EndRatingPrompt?>(null)
    /** End-of-play rating prompt: video id the prompt was already evaluated for (once per video). */
    var ratingPromptHandledFor by mutableStateOf<String?>(null)
    var nextEpisodeDismissedFor by mutableStateOf<String?>(null)
    // Movie recommendations (Nuvio MovieRecommendationCard): "More like
    // this" after a movie ends, loaded once per title.
    var movieRecs by mutableStateOf<List<MetaPreview>>(emptyList())
    var movieRecsLoadedFor by mutableStateOf<String?>(null)
    var movieRecsAddonId by mutableStateOf<String?>(null)
    var movieRecsDismissed by mutableStateOf(false)
    var movieRecsAutoHidden by mutableStateOf(false)
    // Subtitle search
    var subSearchOpen by mutableStateOf(false)
    var subSearchLoading by mutableStateOf(false)
    var subSearchResults by mutableStateOf(emptyList<Subtitle>())
    var subSearchError by mutableStateOf<String?>(null)
    var addedSubs by mutableStateOf(emptyList<Subtitle>())
    // Subtitle overlay (v0.14.5): chosen language rail + the URL of the
    // subtitle the user last picked in the panel (drives the Style
    // rail's appearance before the player finishes attaching it).
    var panelLang by mutableStateOf<String?>(null)
    var panelPickedUrl by mutableStateOf<String?>(null)
    /**
     * Per-title subtitle style override (#27): the video id the
     * loaded override belongs to. Reloaded (with the override) when
     * the title changes; the in-player Style rail writes here, never
     * to the global Settings keys.
     */
    var subtitleOverrideKey by mutableStateOf(initialRequest.videoId)
    /** This title's style override (EMPTY = pure Settings defaults). */
    var subtitleOverrideState by mutableStateOf(SubtitleStyleOverride.EMPTY)
    // Style-rail mirrors of the persisted style: font size (sp), text
    // colour (ARGB — its alpha is the opacity control), bold / outline /
    // outline colour / bottom offset, plus auto-sync capture state and
    // the reload tick that re-runs the subtitle resolver effect.
    var subFontSpUi by mutableIntStateOf(settings.subtitleFontSizeSp)
    var subColorUi by mutableIntStateOf(settings.subtitleTextColor)
    var subBoldUi by mutableStateOf(settings.subtitleBold)
    var subEdgeUi by mutableIntStateOf(settings.subtitleEdgeType)
    var subEdgeColorUi by mutableIntStateOf(settings.subtitleEdgeColor)
    var subBottomUi by mutableIntStateOf(settings.subtitleBottomOffset)
    var subReloadTick by mutableIntStateOf(0)
    var captureCues by mutableStateOf<List<Pair<Long, String>>?>(null)
    var captureLoading by mutableStateOf(false)
    /** Playback position stored when Auto Sync > Capture is pressed. */
    var capturePositionMs by mutableLongStateOf(0L)
    var pendingDefaultSubUrl by mutableStateOf<String?>(null)
    // True when side-loaded subtitles existed but none could be downloaded.
    var subLoadError by mutableStateOf(false)
    /** Sync by ear (Nuvio SubtitleSyncByEarCard): the playback position
     *  captured when the user hears a line / sees its subtitle. */
    var syncByEarHeardMs by mutableStateOf<Long?>(null)
    var syncByEarSawMs by mutableStateOf<Long?>(null)
    // Poll position / duration / playing / buffering / volume for the UI,
    // and track the active skip-segment window.
    var endedHandledFor by mutableStateOf<String?>(null)
    /** Auto-download next episode (v0.14.30 port): video id the
     *  auto-download trigger already fired for (once per video). */
    var autoDownloadHandledFor by mutableStateOf<String?>(null)
    // Tracks whether any transport button currently holds focus. The D-pad
    // key handler uses this: if controls are visible but nothing grabbed
    // focus (e.g. requestFocus failed), Center/Up/Down re-request focus on
    // the play/pause button instead of being swallowed with no effect.
    var transportFocused by mutableStateOf(false)
    /** True while the TV seekbar holds D-pad focus (arrows then seek). */
    var seekbarFocused by mutableStateOf(false)
    var focusedKey by mutableStateOf<String?>(null)
    var focusedAction by mutableStateOf<(() -> Unit)?>(null)
    // TV watchdog: if D-pad focus is lost for any reason (a focused button was
    // removed, a sheet closed, an overlay faded out) park it back on the player
    // container, so the remote can never stay dead. Skipped while a sheet or
    // dialog (separate window) legitimately owns focus.
    var rootHasFocus by mutableStateOf(true)
}
