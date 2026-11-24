@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.aviostream.app.ui.screens

import com.aviostream.app.ui.components.pickAutoplayStream
import com.aviostream.app.ui.screens.player.*

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.StayCurrentLandscape
import androidx.compose.material.icons.filled.StayCurrentPortrait
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.ExitToApp
import com.aviostream.app.ui.components.OrbitDots
import com.aviostream.app.ui.components.CustomPosterScreen
import com.aviostream.app.ui.components.PosterCard
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.aviostream.app.ui.widget.CwWidgetUpdater
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.aviostream.app.MainActivity
import com.aviostream.app.R
import com.aviostream.app.ui.tv.rememberIsTv
import com.aviostream.app.ui.tv.tvSheetFocus
import com.aviostream.app.ui.tv.tvFocusRing
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.Tracks
import androidx.media3.common.PlaybackException
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.VideoSize
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import com.aviostream.app.player.CENTER_MIX_LEVEL_MAX_DB
import com.aviostream.app.player.CENTER_MIX_LEVEL_MIN_DB
import com.aviostream.app.player.DownmixConfig
import com.aviostream.app.player.AvioAudioRenderersFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aviostream.app.media.AvioExtractorsFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.aviostream.app.data.AUDIO_DELAY_MAX_MS
import com.aviostream.app.data.AUDIO_DELAY_MIN_MS
import com.aviostream.app.data.AUDIO_DELAY_STEP_MS
import com.aviostream.app.data.AudioDelayMediaSourceFactory
import com.aviostream.app.data.ParentalGuideRepository
import com.aviostream.app.data.ParentalWarning
import com.aviostream.app.data.buildParentalWarnings
import com.aviostream.app.data.extractParentalGuideImdbId
import com.aviostream.app.data.rememberHapticTick
import com.aviostream.app.data.BingeGroupCache
import com.aviostream.app.data.CachedStreamLink
import com.aviostream.app.data.FilenameEpisodeMatcher
import com.aviostream.app.data.HlsQualityResolver
import com.aviostream.app.data.HlsQualityState
import com.aviostream.app.data.HlsQualityVariant
import com.aviostream.app.data.StreamLinkCache
import com.aviostream.app.data.streamLinkContentKey
import com.aviostream.app.data.MetaPreview
import com.aviostream.app.data.PlaybackHeaders
import com.aviostream.app.data.PlaybackRequest
import com.aviostream.app.util.ExternalPlayer
import com.aviostream.app.data.TvChannelRef
import com.aviostream.app.data.AvioSidecarSubtitles
import com.aviostream.app.data.SubtitleStyleOverride
import com.aviostream.app.data.SubtitleStyleOverrides
import com.aviostream.app.data.SubtitleStyleValues
import com.aviostream.app.data.ResolvedSubtitle
import java.util.concurrent.TimeUnit
import com.aviostream.app.data.SkipSegment
import com.aviostream.app.data.StreamItem
import com.aviostream.app.data.StreamRepository
import com.aviostream.app.data.FirstPartySubsClient
import com.aviostream.app.data.Subtitle
import com.aviostream.app.data.UserRatings
import com.aviostream.app.data.trakt.TraktApi
import com.aviostream.app.data.resolveSubtitleFiles
import com.aviostream.app.data.shiftSubtitles
import com.aviostream.app.data.formatOffsetMs
import com.aviostream.app.data.Video
import com.aviostream.app.data.ASPECT_MODES
import com.aviostream.app.data.SkipSegmentRepository
import com.aviostream.app.data.SdhFilteringTextOutput
import com.aviostream.app.data.SubtitleSdhFilter
import com.aviostream.app.data.detectTrackLanguageVariant
import com.aviostream.app.data.autoPickSubtitleIndex
import com.aviostream.app.data.subtitleLangMatches
import com.aviostream.app.data.subtitleTypeface
import com.aviostream.app.data.simkl.SimklManager
import com.aviostream.app.data.scrobble.ScrobbleManager
import com.aviostream.app.data.servers.PlexTimelineReporter
import com.aviostream.app.data.trakt.TraktManager
import com.aviostream.app.data.buttonLabel
import com.aviostream.app.data.buttonStartMs
import com.aviostream.app.data.autoSkipStoredValue
import com.aviostream.app.data.streamQualityLabel
import com.aviostream.app.ui.components.AddonTabs
import com.aviostream.app.ui.components.ErrorState
import com.aviostream.app.ui.components.GlowBackground
import com.aviostream.app.ui.components.DotsLoadingIndicator
import com.aviostream.app.ui.components.SafePosterImage
import com.aviostream.app.ui.glass.AvioBackdrop
import com.aviostream.app.ui.glass.LocalAvioGlassBackdrop
import com.aviostream.app.ui.glass.ambientLiquidGlass
import com.aviostream.app.ui.screens.player.playerLiquidGlass
import com.aviostream.app.ui.components.StreamCard
import com.aviostream.app.ui.components.StreamList
import com.aviostream.app.ui.components.rememberStreamBrowserState
import com.aviostream.app.ui.theme.LocalPalette
import com.aviostream.app.ui.theme.GlassBorder
import com.aviostream.app.ui.theme.TextPrimary
import com.aviostream.app.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Any of the eight player sheets open. Kept out of [PlayerScreen] on purpose:
 * the 10-term `||` chains that used to inline these flags made D8 emit an
 * invalid register move in the giant PlayerScreen method (ART VerifyError
 * `copy-cat1 v2<-v417 MutableState` at dex 0x36AD, dev-37) — a small helper
 * method compiles cleanly where the inlined chain did not.
 */
private fun anySheetOpen(
    tracksOpen: Boolean,
    subSearchOpen: Boolean,
    sourcesOpen: Boolean,
    episodesOpen: Boolean,
    qualityOpen: Boolean,
    infoOpen: Boolean,
    chaptersOpen: Boolean,
    speedMenuOpen: Boolean
): Boolean = tracksOpen || subSearchOpen || sourcesOpen || episodesOpen ||
    qualityOpen || infoOpen || chaptersOpen || speedMenuOpen

/**
 * Full dialog-open check (sheets + resume offer + player error). The whole
 * `||` lives here so PlayerScreen itself has no boolean merge left for it —
 * dev-38 proved even the 3-term remainder (`anySheetOpen(...) ||
 * resumeOfferMs != null || playerError != null`) still trips D8's register
 * allocator in the giant PlayerScreen method (VerifyError 0x36B9 v409).
 */
private fun isDialogOpen(
    tracksOpen: Boolean,
    subSearchOpen: Boolean,
    sourcesOpen: Boolean,
    episodesOpen: Boolean,
    qualityOpen: Boolean,
    infoOpen: Boolean,
    chaptersOpen: Boolean,
    speedMenuOpen: Boolean,
    resumeOfferMs: Long?,
    playerError: String?
): Boolean = anySheetOpen(tracksOpen, subSearchOpen, sourcesOpen, episodesOpen, qualityOpen, infoOpen, chaptersOpen, speedMenuOpen) ||
    resumeOfferMs != null || playerError != null

/** Full sheet-up check (sheets + resume offer + pending download). Same reason as [isDialogOpen]. */
private fun isSheetUp(
    tracksOpen: Boolean,
    subSearchOpen: Boolean,
    sourcesOpen: Boolean,
    episodesOpen: Boolean,
    qualityOpen: Boolean,
    infoOpen: Boolean,
    chaptersOpen: Boolean,
    speedMenuOpen: Boolean,
    resumeOfferMs: Long?,
    downloadStream: StreamItem?
): Boolean = anySheetOpen(tracksOpen, subSearchOpen, sourcesOpen, episodesOpen, qualityOpen, infoOpen, chaptersOpen, speedMenuOpen) ||
    resumeOfferMs != null || downloadStream != null

/**
 * Reference-style player: title block top-left, icon cluster top-right,
 * big center transport, slim seekbar + time chips, bottom pill toolbar
 * (Aspect / Speed / Subs / Audio / Sources / Episodes), edge volume pill,
 * and a controls lock — with all of Avio's gesture strengths kept.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    repository: StreamRepository,
    onBack: () -> Unit,
    onOpenDetail: (addonId: String, type: String, metaId: String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val initialRequest = repository.pendingPlayback
    Log.d("AvioPlay", "PlayerScreen entered, initialRequest null=" + (initialRequest == null))
    val settings = repository.settings
    val userRatings = remember(context) { UserRatings(context) }
    // Haptics (v0.14.30 port): one light tick shared by the player funnels below.
    val hapticTick = rememberHapticTick()

    // Step 63: player toolbar layouts (order + visibility), decoded from
    // the stored "id:v,id:h" strings; blank = all visible, default order.
    val bottomButtonLayout = remember(settings.playerBottomLayout) {
        com.aviostream.app.data.PlayerButtonLayouts.decodeBottom(settings.playerBottomLayout)
    }
    val topButtonLayout = remember(settings.playerTopLayout) {
        com.aviostream.app.data.PlayerButtonLayouts.decodeTop(settings.playerTopLayout)
    }

    // Reuse last link (Nuvio StreamLinkCacheRepository): the last played link
    // per title, so a replay inside the TTL skips the full re-resolve.
    val linkCache = remember(context) { StreamLinkCache(context) }

    /**
     * Saves the currently playing link for reuse. Live, trailers and local
     * files never cache; only real http(s) links do (guarded in save()).
     */
    fun saveLinkForReuse(req: PlaybackRequest) {
        if (!settings.reuseLastLinkEnabled || req.isLive || req.isTrailer) return
        // Torrent gate: the loopback URL is per-session (a fresh port
        // on every prepare) — caching it would poison later replays
        // with a dead URL once the engine has stopped.
        if (req.streamUrl.startsWith("http://127.0.0.1", ignoreCase = true)) return
        linkCache.save(
            streamLinkContentKey(
                type = req.type,
                videoId = req.videoId,
                parentMetaId = req.metaId.takeIf { it.isNotBlank() },
                season = req.season.takeIf { it > 0 },
                episode = req.episode.takeIf { it > 0 },
            ),
            CachedStreamLink(
                url = req.streamUrl,
                streamName = req.streamLabel ?: req.title,
                addonName = req.sourceName ?: "",
                addonId = "",
                cachedAtMs = System.currentTimeMillis(),
                requestHeaders = req.headers,
                mimeType = req.mimeType,
            )
        )
    }

    // PiP (v0.9.0+): track whether the video floats so overlay UI can hide,
    // and mark the player active so MainActivity.onUserLeaveHint auto-enters
    // PiP when Home/recents is pressed mid-playback.
    val inPip by MainActivity.pipMode.collectAsState()
    // PiP dismissed via its X button: MainActivity bumps the counter (the
    // screen never leaves composition on its own in that path) — exit
    // through the normal back path so onDispose stops and releases.
    val pipDismissCount by MainActivity.pipDismissedCount.collectAsState()
    val pipDismissBaseline = remember { pipDismissCount }
    LaunchedEffect(pipDismissCount) {
        if (pipDismissCount != pipDismissBaseline) onBack()
    }
    DisposableEffect(Unit) {
        MainActivity.isPlayerActive = true
        // Resume prompt (Nuvio ResumePromptRepository): entering the player
        // arms the prompt with this title; leaving normally (back / PiP
        // dismiss) clears it. A set flag at launch therefore means the
        // last session ended inside the player — the only case that
        // prompts. A killed process runs no dispose, so the flag survives
        // exactly that case. Only armed when there is a real request.
        val promptVideoId = initialRequest?.videoId
        if (promptVideoId != null) {
            settings.resumePromptWasInPlayer = true
            settings.resumePromptLastVideoId = promptVideoId
        }
        onDispose {
            MainActivity.isPlayerActive = false
            settings.resumePromptWasInPlayer = false
            settings.resumePromptLastVideoId = ""
        }
    }

    // (Back handling is registered below, after the UI state it depends on.)

    if (initialRequest == null) {
        GlowBackground { ErrorState("Nothing to play.", onRetry = onBack) }
        return
    }

    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val ioScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    val isTv = rememberIsTv()
    /**
     * Set in onDispose. Background work (zap, episode load, source switch)
     * checks this before building a new ExoPlayer so no player is ever
     * created on disposed state (#9).
     */
    val disposed = remember { AtomicBoolean(false) }
    /** Monotonic generation: a stale subtitle transform may never overwrite a newer selection/offset. */
    val subtitleShiftGeneration = remember { AtomicInteger(0) }
    // All mutable UI/playback state lives in PlayerScreenState: a single
    // `remember` instead of 130 `by remember` delegates, so D8's register
    // allocator stops producing invalid dex for this giant composable.
    val state = remember { PlayerScreenState(settings, initialRequest, isTv) }
    // Second playback engine (MPV port, v0.14.30 #21): the engine
    // state lives here, before the error listener, so the listener
    // can fail over through the hook (composable locals are
    // positional — nothing below may be referenced above it).
    // The engine resolved for this play: Auto routes HDR / Dolby
    // Vision to Exo and anime to MPV; a manual pick pins one engine.
    val startEngine = remember {
        com.aviostream.app.player.PlaybackEngineResolver.resolve(
            settings.playbackEngine, initialRequest
        )
    }
    var activeEngine by remember { mutableStateOf(startEngine) }
    val mpvEngineState = remember { mutableStateOf<com.aviostream.app.player.MpvPlayerEngine?>(null) }
    var mpvEngine by mpvEngineState
    /** The MPV track list mirrored into UI state for the track panels. */
    val mpvTracksUiState = remember {
        mutableStateOf<List<com.aviostream.app.player.MpvPlayerEngine.Track>>(emptyList())
    }
    var mpvTracksUi by mpvTracksUiState
    /** Identity of the resolved subtitles already added to the MPV engine. */
    var mpvSubsKey by remember { mutableStateOf<String?>(null) }
    /** Ladder guard: the one silent engine failover for this play. */
    var engineFailoverUsed by remember { mutableStateOf(false) }
    /** A manual in-player engine switch disarms the auto failover. */
    var engineFailoverArmed by remember { mutableStateOf(true) }
    /** Assigned once the engine block exists; the Exo error listener fails over through it. */
    var engineFailoverHook: (() -> Unit)? by remember { mutableStateOf(null) }
    /** Assigned once the engine block exists; the MPV engine reports fatal errors through it. */
    var mpvFatalErrorHook: ((String) -> Unit)? by remember { mutableStateOf(null) }

    /** True when the MPV engine owns playback right now. */
    fun mpvActive(): Boolean =
        activeEngine == com.aviostream.app.player.PlaybackEngine.MPV && mpvEngine != null
    // Sleep timer label (v0.14.30 port): time left while a timed deadline
    // runs, "Ep. end" for end-of-episode, else "Sleep".
    val sleepTimerLabel = when {
        state.sleepDeadlineMs > 0L -> "%d:%02d".format(state.sleepRemainingSec / 60, state.sleepRemainingSec % 60)
        state.sleepAtEpisodeEnd -> "Ep. end"
        else -> "Sleep"
    }
    /** Invalidates callbacks from listeners attached to released/replaced players. */
    val activePlayerListenerGeneration = remember { AtomicInteger(0) }
    /**
     * Registered on every player instance we build (#4). ExoPlayer invokes
     * listeners on the player's application thread (main here), so touching
     * Compose state directly is safe.
     */
    val errorListener = remember {
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                if (disposed.get()) return
                state.lastPlayerErrorCode = error.errorCode
                val cause = generateSequence<Throwable>(error) { it.cause }.firstOrNull {
                    it::class.java.simpleName == "UnrecognizedInputFormatException"
                }
                if (cause != null && !state.mimeHealAttempted) {
                    state.mimeHealAttempted = true
                    state.healOnFormatError?.invoke(error)
                    return
                }
                // Audio self-heal: a surround track fails on a stereo-only
                // phone when the AudioTrack cannot take multichannel PCM
                // (FfmpegAudioRenderer / MediaCodecAudioRenderer, e.g. 6ch
                // 48kHz raw -> 5001 AudioTrack init failed). If downmix is
                // off, turn it on and rebuild the SAME source once. Downmix
                // defaults OFF (SettingsStore), so this is the safety net
                // for every 5.1 source. (state.downmixOn mirrors
                // downmixConfig.enabled; the config itself is declared after
                // this listener, so it cannot be referenced here.)
                //
                // Also covers an AUDIO decoder init failure (4001) and a
                // format that exceeds the device's audio capabilities / is
                // reported unsupported (4004/4005) when the message names an
                // audio renderer (FfmpegAudioRenderer /
                // MediaCodecAudioRenderer): a multichannel format can surface
                // as any of these, and the message is what separates it from
                // a VIDEO decoder failure (4K the phone cannot decode) —
                // downmix must not fire for video.
                val isAudioOutputError = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
                val isAudioDecoderError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED &&
                    (error.message?.contains("AudioRenderer") == true)
                val isAudioFormatError =
                    (error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                        error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED) &&
                    (error.message?.contains("AudioRenderer") == true)
                if ((isAudioOutputError || isAudioDecoderError || isAudioFormatError) && !state.downmixOn && !state.downmixFailoverAttempted) {
                    state.downmixFailoverAttempted = true
                    state.retryWithDownmix?.invoke()
                    return
                }
                // Auto-failover: generic playback tries 3 sources; mobile Live
                // uses its separate bounded forward-only policy.
                // Also covers decoder errors (e.g. 4K on phones that can't decode it).
                val isSourceError = error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                    error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                    error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED
                val mobileLiveFallback = com.aviostream.app.data.LivePlaybackPolicy.shouldRetry(
                    isLive = initialRequest.isLive,
                    isTv = isTv,
                    errorCode = error.errorCode,
                    attemptsSoFar = state.autoFailoverCount
                )
                if ((isSourceError && state.autoFailoverCount < settings.fallbackMaxSources) || mobileLiveFallback) {
                    state.autoFailoverCount++
                    if (initialRequest.isLive && !isTv) {
                        com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_attempt", 0)
                    }
                    state.failoverToNextSource?.invoke(state.autoFailoverCount)
                    return
                }
                if (initialRequest.isLive && !isTv &&
                    com.aviostream.app.data.LivePlaybackPolicy.isRecoverableSourceError(error.errorCode)) {
                    com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
                }
                // Engine failover (MPV port): the Exo rungs above all
                // declined — try the other engine once at the same
                // position instead of showing the error. Live and
                // trailers stay on Exo; a manual switch disarms it.
                if (settings.autoEngineFailover && engineFailoverArmed && !engineFailoverUsed &&
                    !initialRequest.isLive && !initialRequest.isTrailer
                ) {
                    engineFailoverUsed = true
                    engineFailoverHook?.invoke()
                    return
                }
                state.autoFailoverCount = 0
                state.playerError = com.aviostream.app.data.PlaybackErrorText.explanation(error)
            }

            override fun onIsPlayingChanged(playing: Boolean) {
                // Keeps the PiP window's play/pause RemoteAction icon in sync.
                (activity as? MainActivity)?.updatePipActions(playing)
                // Reset auto-failover counter on successful playback
                if (playing) {
                    state.autoFailoverCount = 0
                    // Step 40: a source that plays again earns a fresh
                    // stall-resume attempt (Nuvio PlaybackReopenSession.onReady).
                    state.vodStallResumeAttemptedFor = null
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                // Keeps the PiP aspect ratio current (portrait videos float
                // portrait like YouTube) for Home-press auto-PiP.
                if (videoSize.width > 0 && videoSize.height > 0) {
                    MainActivity.pipVideoW = videoSize.width
                    MainActivity.pipVideoH = videoSize.height
                }
            }
        }
    }

    /** Each player gets its own listener wrapper; stale instances become inert immediately. */
    fun guardedPlayerListener(): Player.Listener {
        val generation = activePlayerListenerGeneration.incrementAndGet()
        return object : Player.Listener {
            private fun isCurrent() = !disposed.get() && activePlayerListenerGeneration.get() == generation
            override fun onPlayerError(error: PlaybackException) {
                if (isCurrent()) errorListener.onPlayerError(error)
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                if (isCurrent()) errorListener.onIsPlayingChanged(playing)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (isCurrent()) errorListener.onVideoSizeChanged(videoSize)
            }
        }
    }

    /**
     * The index of the subtitle [subtitleConfigs] flags as default —
     * and the sidecar engine (#26) auto-activates: the explicitly
     * requested URL first, else the first track in a preferred
     * language, else the first subtitle when no preference is set,
     * or -1 when a preference is set but nothing matches. One source
     * of truth for both paths, so the media-item path and the sidecar
     * can never pick different defaults. (The "Subtitles by default"
     * mode is deliberately NOT consulted here: the media path never
     * consulted it for addon subtitles, and the sidecar mirrors the
     * media path exactly.)
     */
    fun subtitleDefaultIndex(
        resolved: List<ResolvedSubtitle>,
        defaultUrl: String? = null
    ): Int {
        if (resolved.isEmpty()) return -1
        val prefs = settings.subtitleLanguages
        return when {
            defaultUrl != null ->
                resolved.indexOfFirst { it.sub.url == defaultUrl }.takeIf { it >= 0 } ?: -1
            prefs.isEmpty() -> 0
            else -> resolved.indexOfFirst { r ->
                prefs.any { pref -> subtitleLangMatches(r.sub.lang, pref) }
            }
        }
    }

    /**
     * Builds subtitle configurations from already-resolved (downloaded +
     * format-sniffed) subtitle files, with the same default-selection rules
     * as [attachResolvedSubtitles].
     */
    fun subtitleConfigs(
        resolved: List<ResolvedSubtitle>,
        defaultUrl: String? = null
    ): List<MediaItem.SubtitleConfiguration> {
        if (resolved.isEmpty()) return emptyList()
        val defaultIndex = subtitleDefaultIndex(resolved, defaultUrl)
        return resolved.mapIndexed { index, r ->
            MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(r.file))
                .setMimeType(r.mimeType)
                .setLanguage(r.sub.lang)
                .setId(r.sub.url)
                .apply { if (index == defaultIndex) setSelectionFlags(C.SELECTION_FLAG_DEFAULT) }
                .build()
        }
    }

    /**
     * Builds the player for a request. Already-resolved side-loaded subtitles
     * can be passed in so the player carries them from its first prepare —
     * attaching them later would force a setMediaItem+prepare() and a visible
     * rebuffer (#29). Their URLs rarely carry a usable file extension, so
     * still-unresolved subtitle lists are resolved in the background and
     * attached via [attachResolvedSubtitles].
     */
    /** Builds the playback HTTP data source using platform TLS validation. */
    fun buildPlaybackDataSourceFactory(
        headers: Map<String, String>,
        originUrl: String,
        forceSeek: Boolean = false
    ): androidx.media3.datasource.DataSource.Factory {
        val httpFactory = com.aviostream.app.data.PlaybackHttp.factory(originUrl, headers, forceSeek)
        // DefaultDataSource delegates per URI scheme: FileDataSource for
        // file:// (downloads), HttpDataSource for http(s):// (streams).
        return DefaultDataSource.Factory(context, httpFactory)
    }

    /**
     * MediaSource.Factory for a request (headers + optional disk cache).
     * Shared by [buildPlaybackMediaSource] and the player's own factory so
     * later setMediaItem calls use the same networking.
     */
    // Step 65: audio delay (lip-sync). The value lives in an AtomicLong
    // so the AudioDelayMediaSourceFactory's provider reads it live from
    // the renderer thread — the slider applies without re-preparing —
    // and in state + SettingsStore so the modal shows it and it
    // persists. Seeded from settings so a set delay applies on the
    // next title too. Declared here (before buildPlaybackMediaSource)
    // because that factory builder reads the ref.
    val audioDelayUsRef = remember { AtomicLong(settings.audioDelayMs * 1000L) }
    fun setAudioDelayMs(ms: Int) {
        val clamped = ms.coerceIn(AUDIO_DELAY_MIN_MS, AUDIO_DELAY_MAX_MS)
        state.audioDelayMs = clamped
        audioDelayUsRef.set(clamped * 1000L)
        settings.audioDelayMs = clamped
        // Second engine: libmpv takes the delay directly (the Exo
        // path shifts it at the renderer instead).
        if (mpvActive()) mpvEngine?.setAudioDelay(clamped.toLong())
    }
    fun formatAudioDelay(ms: Int): String {
        val sign = when {
            ms > 0 -> "+"
            ms < 0 -> "-"
            else -> ""
        }
        val absoluteMs = kotlin.math.abs(ms)
        return "$sign${absoluteMs / 1000}.${(absoluteMs % 1000).toString().padStart(3, '0')}s"
    }

    // Step 66: downmix config shared with the audio processor. The
    // processor snapshots enabled/target at configure() (next video),
    // centre level + keep-original are read live per buffer.
    val downmixConfig = remember {
        DownmixConfig().apply {
            enabled = settings.downmixEnabled
            targetChannels = settings.downmixTargetChannels
            state.centerMixDb = settings.centerMixLevelDb
            maintainOriginal = settings.maintainOriginalAudioOnDownmix
        }
    }
    fun setDownmixEnabled(on: Boolean) {
        state.downmixOn = on
        settings.downmixEnabled = on
        downmixConfig.enabled = on
    }
    fun setDownmixTarget(channels: Int) {
        state.downmixTarget = channels.coerceIn(1, 2)
        settings.downmixTargetChannels = state.downmixTarget
        downmixConfig.targetChannels = state.downmixTarget
    }
    fun setCenterMixDb(db: Int) {
        state.centerMixDb = db.coerceIn(CENTER_MIX_LEVEL_MIN_DB, CENTER_MIX_LEVEL_MAX_DB)
        settings.centerMixLevelDb = state.centerMixDb
        downmixConfig.centerMixDb = state.centerMixDb
    }
    fun setKeepOriginalLevels(keep: Boolean) {
        state.keepOriginalLevels = keep
        settings.maintainOriginalAudioOnDownmix = keep
        downmixConfig.maintainOriginal = keep
    }


    fun playbackMediaSourceFactory(req: PlaybackRequest): MediaSource.Factory {
        val playbackHeaders = PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv)
        // HLS variant temp playlists are file:// URLs — the HTTP origin is
        // the master playlist, so segment requests keep the stream's headers.
        // Force seek is a VOD file-playback tool — Live takes the plain path.
        val dataSourceFactory = buildPlaybackDataSourceFactory(playbackHeaders, req.hlsMasterUrl ?: req.streamUrl, forceSeek = settings.forceSeekAnyStream && !req.isLive)
        val cacheMb = settings.diskCacheMb
        return if (cacheMb > 0) {
            // Step 39: auto-size uses 1/5 of free space (256 MB – 10 GB)
            // instead of the fixed size.
            val maxBytes = if (settings.diskCacheAutoSize) PlaybackCache.autoSizeBytes(context)
                else cacheMb * 1024L * 1024L
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(PlaybackCache.get(context, maxBytes))
                .setUpstreamDataSourceFactory(dataSourceFactory)
            // MKV tail-Cues seeking (v0.14.30 port, feature #20):
            // Matroska sources are parsed by the tail-cues fork, so
            // muxes whose head SeekHead omits the Cues index stay
            // seekable. Every other format keeps its stock extractor.
            DefaultMediaSourceFactory(cacheDataSourceFactory, AvioExtractorsFactory())
        } else {
            DefaultMediaSourceFactory(dataSourceFactory, AvioExtractorsFactory())
        }
    }

    /**
     * Builds the MediaSource for a request. YouTube DASH qualities are
     * video-only — when [PlaybackRequest.mergeAudioUrl] is set, the video is
     * merged with that audio stream so every quality plays with sound.
     */
    fun buildPlaybackMediaSource(
        req: PlaybackRequest,
        subs: List<ResolvedSubtitle> = emptyList(),
        forcedMime: String? = null
    ): MediaSource {
        // Step 65: wrap the factory so video sample timestamps shift
        // by the audio delay (audio is the master clock) — the delay
        // provider reads the AtomicLong live, no re-prepare needed.
        // This is the single choke point: buildExo and the quality
        // switch (replaceStream) both build sources here.
        val mediaSourceFactory = AudioDelayMediaSourceFactory(
            playbackMediaSourceFactory(req),
            audioDelayUsRef::get
        )
        // Nuvio parity: infer HLS/DASH from the FULL URL (path + query) so
        // proxied URLs like /api/manifest?url=<encoded ...m3u8> play as HLS
        // instead of being misidentified as progressive.
        val inferredMime = forcedMime
            ?: com.aviostream.app.data.PlaybackContentProbe.recognizedMime(req.mimeType)
            ?: PlaybackHeaders.inferMimeType(req.streamUrl)
        val videoItem = MediaItem.Builder()
            .setUri(req.streamUrl)
            .apply { inferredMime?.let { setMimeType(it) } }
            .setSubtitleConfigurations(subtitleConfigs(subs))
            .build()
        val mergeAudio = req.mergeAudioUrl
        return if (mergeAudio != null) {
            MergingMediaSource(
                mediaSourceFactory.createMediaSource(videoItem),
                mediaSourceFactory.createMediaSource(MediaItem.fromUri(mergeAudio))
            )
        } else {
            mediaSourceFactory.createMediaSource(videoItem)
        }
    }

    fun buildExo(
        req: PlaybackRequest,
        subs: List<ResolvedSubtitle> = emptyList(),
        forcedMime: String? = null
    ): ExoPlayer {
        Log.d("AvioPlay", "buildExo called")
        val prefs = settings.subtitleLanguages
        val audioPref = settings.preferredAudioLanguage
        // Memory-conscious buffering: on low-RAM devices (e.g. 48MB-heap TVs)
        // the default ~50s HLS buffer exhausts the heap mid-playback and OOMs.
        // The user's buffer settings (Settings > Playback > Buffer & Cache)
        // apply here, capped to the safe low-memory window on such devices.
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val lowMemory = am.isLowRamDevice || am.memoryClass <= 64
        val minBufferMs = if (lowMemory) minOf(settings.bufferMinMs, 15_000) else settings.bufferMinMs
        val maxBufferMs = if (lowMemory) minOf(settings.bufferMaxMs, 30_000) else settings.bufferMaxMs
        val forPlaybackMs = if (lowMemory) minOf(settings.bufferForPlaybackMs, 2_500) else settings.bufferForPlaybackMs
        // Step 39: custom mode uses the fine rebuffer + back-buffer values;
        // otherwise rebuffer is derived (2x start) and no back buffer is kept.
        val rebufferMs = if (settings.customPlaybackBuffersEnabled) settings.bufferRebufferMs
            else (forPlaybackMs * 2).coerceAtMost(30_000)
        val backBufferMs = if (settings.customPlaybackBuffersEnabled) settings.bufferBackBufferMs else 0
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minBufferMs,
                /* maxBufferMs = */ maxBufferMs,
                /* bufferForPlaybackMs = */ forPlaybackMs,
                /* bufferForPlaybackAfterRebufferMs = */ rebufferMs
            )
            .setBackBuffer(backBufferMs, false)
            .build()
        val mediaSourceFactory = playbackMediaSourceFactory(req)
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(AvioAudioRenderersFactory(context, downmixConfig))
            .setMediaSourceFactory(mediaSourceFactory)
            // Belt and suspenders: also tell the track selector the preferred
            // subtitle languages, so his languages win even if the default
            // flag is ever ignored.
            .setTrackSelector(
                DefaultTrackSelector(context).apply {
                    val params = buildUponParameters()
                    // Subtitles by default (v0.14.30 port): OFF means no preferred text languages, so subtitles stay off until the user picks one. Otherwise the preferred languages are set as before (default: on in my languages).
                    if (prefs.isNotEmpty() && settings.subtitleDefaultMode != com.aviostream.app.data.SUB_DEFAULT_OFF) {
                        params.setPreferredTextLanguages(*prefs.toTypedArray())
                    }
                    // Preferred audio language (Settings > Playback): when the
                    // title's audio tracks include it, ExoPlayer picks it
                    // automatically at playback start. Step 38: the secondary
                    // language is passed too, as the fallback order.
                    val audioPrefs = listOfNotNull(
                        audioPref?.takeIf { it.isNotBlank() },
                        settings.secondaryPreferredAudioLanguage?.takeIf { it.isNotBlank() },
                    ).distinct()
                    if (audioPrefs.isNotEmpty()) {
                        params.setPreferredAudioLanguages(*audioPrefs.toTypedArray())
                    }
                    setParameters(params)
                }
            )
            .build()
            .apply {
                // YouTube DASH (video-only + merged audio) goes through
                // setMediaSource; everything else keeps the setMediaItem path.
                setMediaSource(buildPlaybackMediaSource(req, subs, forcedMime))
            }
    }


    // ---- Per-title subtitle style overrides (#27) ----
    // Settings → Subtitles is the ONLY home of the global subtitle
    // style defaults. The in-player Style rail used to write those
    // same globals, so a tweak inside one title silently re-styled
    // every other title and fought the Settings page. The rail now
    // writes a PER-TITLE override (persisted by
    // SubtitleStyleOverrides, keyed by video id); both engines
    // render the RESOLVED style — override field, else the global
    // default, per field.

    /** The style the renderers must use right now: this title's override over the Settings defaults. */
    fun effectiveSubtitleStyle(): SubtitleStyleValues =
        state.subtitleOverrideState.resolve(
            SubtitleStyleValues.fromGlobals(settings)
        )

    /** The Style rail's only write path: fold a change into THIS title's override and persist it. */
    fun persistSubtitleStyle(
        transform: (SubtitleStyleOverride) -> SubtitleStyleOverride
    ) {
        val next = transform(state.subtitleOverrideState)
        state.subtitleOverrideState = next
        SubtitleStyleOverrides.save(context, state.subtitleOverrideKey, next)
    }

    /** Rail Reset: drop THIS title's override (the rail re-seeds its mirrors from the globals). */
    fun clearSubtitleStyleOverride() {
        state.subtitleOverrideState = SubtitleStyleOverride.EMPTY
        SubtitleStyleOverrides.clear(context, state.subtitleOverrideKey)
    }

    /** Re-seed the Style rail's mirrors from the effective style (override over globals). */
    fun reseedStyleRailMirrors() {
        val style = effectiveSubtitleStyle()
        state.subFontSpUi = style.fontSizeSp
        state.subColorUi = style.textColor
        state.subBoldUi = style.bold
        state.subEdgeUi = style.edgeType
        state.subEdgeColorUi = style.edgeColor
        state.subBottomUi = style.bottomOffset
    }

    /**
     * Applies the user's subtitle styling (Settings > Playback) to the
     * PlayerView's caption view: text color, size, background/shade and
     * edge style. Called when the view is created and on every update.
     *
     * The style is the RESOLVED one (#27): this title's override over
     * the Settings defaults — never the globals alone.
     *
     * SYSTEM-CAPTIONS HOOK (fix #20): when the user picked "System
     * default", skip setStyle() entirely so Android's
     * Settings > Accessibility > Captions preferences apply.
     */
    fun applySubtitleStyle(pv: PlayerView) {
        if (settings.useSystemCaptions) return
        val sv = pv.subtitleView ?: return
        val style = effectiveSubtitleStyle()
        val edge = when (style.edgeType) {
            0 -> CaptionStyleCompat.EDGE_TYPE_NONE
            1 -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            3 -> CaptionStyleCompat.EDGE_TYPE_RAISED
            4 -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
            else -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        }
        // Style rail application (v0.14.5): embedded styling/font sizes
        // are overridden so the user's style always wins, the size is a
        // fixed sp (fractional in PiP, where the window is small and
        // captions must scale with it), and the bottom offset follows
        // the rail's 0–200 range.
        sv.setApplyEmbeddedStyles(false)
        sv.setApplyEmbeddedFontSizes(false)
        sv.setStyle(
            CaptionStyleCompat(
                style.textColor,
                style.backgroundColor,
                android.graphics.Color.TRANSPARENT,
                edge,
                style.edgeColor,
                // Step 38: the user's picked subtitle font (built-in face or
                // an imported ttf/otf); bold is synthesized onto it.
                run {
                    val base = subtitleTypeface(pv.context, style.fontFamily)
                        ?: android.graphics.Typeface.DEFAULT
                    if (style.bold) {
                        android.graphics.Typeface.create(base, android.graphics.Typeface.BOLD)
                    } else {
                        base
                    }
                }
            )
        )
        val fontSp = style.fontSizeSp
        if (inPip) {
            sv.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * (fontSp / 18f)
            )
        } else {
            sv.setFixedTextSize(
                android.util.TypedValue.COMPLEX_UNIT_SP, fontSp.toFloat()
            )
        }
        // Bottom offset: base fraction plus offset/1000 (the offset term
        // clamped to 0..0.2), the total clamped to 0..0.4.
        val offsetFraction = (style.bottomOffset / 1000f).coerceIn(0f, 0.2f)
        sv.setBottomPaddingFraction(
            (SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION * (2f / 3f) + offsetFraction)
                .coerceIn(0f, 0.4f)
        )
    }


    LaunchedEffect(state.currentRequest.videoId, state.currentRequest.liveChannel) {
        state.liveRebufferRecoveries = 0
    }
    var player by remember {
        mutableStateOf(buildExo(initialRequest).also { it.addListener(guardedPlayerListener()) })
    }
    val latestPlayer = rememberUpdatedState(player)
    val latestRequest = rememberUpdatedState(state.currentRequest)

    // ---- Sidecar subtitle engine (#26) ----
    // Addon subtitles do NOT ride the media item here: the picked
    // subtitle is parsed off-player by AvioSidecarSubtitles and the
    // 100 ms render loop below pushes the active cues straight into
    // the PlayerView's SubtitleView. The media period is never
    // touched — no re-prepare, no rebuffer — and the subtitle delay
    // is a render offset (the loop reads effectiveSubOffsetMs() on
    // every pass), never a file rewrite. A subtitle that parses to
    // nothing deactivates the sidecar quietly; it never falls back
    // to a media reload. MPV keeps its own sub-add path, and a
    // player built carrying subtitles in its media item keeps the
    // media path (see attachResolvedSubtitles) — the sidecar stands
    // down there so the same cues are never painted twice.

    /** The sidecar subtitle URL that is actually rendering right now (null while MPV owns playback). */
    fun activeSidecarUrl(): String? = if (mpvActive()) null else state.sidecarSubUrl

    /**
     * Renders [resolved] through the sidecar: parse off-player, then
     * the render loop pushes the active cues into the SubtitleView.
     * The player's own text renderer is disabled while the sidecar
     * shows, so the same track cannot double up.
     */
    fun activateSidecarSubtitle(resolved: ResolvedSubtitle) {
        val generation = state.sidecarGeneration.incrementAndGet()
        state.sidecarSubUrl = resolved.sub.url
        state.sidecarCues = emptyList()
        ioScope.launch {
            val parsed = withContext(Dispatchers.IO) {
                AvioSidecarSubtitles.loadCues(resolved)
            }
            withContext(Dispatchers.Main) {
                if (disposed.get() || state.sidecarGeneration.get() != generation) {
                    return@withContext
                }
                if (parsed.cues.isEmpty()) {
                    // Downloaded but unusable (no cues parsed): go dark
                    // quietly — never a media reload as a "fallback".
                    state.sidecarSubUrl = null
                    state.sidecarCues = emptyList()
                    state.playerView?.subtitleView?.setCues(emptyList())
                } else {
                    state.sidecarCues = parsed.cues
                }
            }
        }
        // The player's own text renderer must not double up with the
        // sidecar — text tracks are disabled while the sidecar shows.
        runCatching {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    /** Stops sidecar rendering and clears the caption view. */
    fun deactivateSidecarSubtitle() {
        state.sidecarGeneration.incrementAndGet()
        state.sidecarSubUrl = null
        state.sidecarCues = emptyList()
        state.playerView?.subtitleView?.setCues(emptyList())
    }

    // --- Second engine (MPV) helpers ---------------------------------
    // The UI never talks to a parked engine: every position /
    // duration / transport read goes through these, so the shared
    // control shell works on either engine.

    /** Position of the engine that owns playback right now. */
    fun activePositionMs(): Long =
        if (mpvActive()) {
            mpvEngine?.snapshot?.value?.positionMs ?: 0L
        } else {
            runCatching { player.currentPosition }.getOrDefault(0L)
        }

    /** Duration of the engine that owns playback right now. */
    fun activeDurationMs(): Long =
        if (mpvActive()) {
            mpvEngine?.snapshot?.value?.durationMs ?: 0L
        } else {
            runCatching { player.duration }.getOrDefault(0L).takeIf { it > 0 } ?: 0L
        }

    /** Buffered position (MPV reports none — position stands in). */
    fun activeBufferedMs(): Long =
        if (mpvActive()) activePositionMs()
        else runCatching { player.bufferedPosition }.getOrDefault(0L).takeIf { it > 0 } ?: 0L

    fun activeIsPlaying(): Boolean =
        if (mpvActive()) {
            mpvEngine?.snapshot?.value?.let { !it.paused } ?: false
        } else {
            runCatching { player.isPlaying }.getOrDefault(false)
        }

    /** True when the active engine has reached the end of the file. */
    fun activeEnded(): Boolean =
        if (mpvActive()) {
            mpvEngine?.snapshot?.value?.eofReached ?: false
        } else {
            runCatching { player.playbackState }.getOrDefault(Player.STATE_IDLE) == Player.STATE_ENDED
        }

    /** Seeks the engine that owns playback right now. */
    fun activeSeekTo(positionMs: Long) {
        if (mpvActive()) mpvEngine?.seekTo(positionMs.coerceAtLeast(0L))
        else runCatching { player.seekTo(positionMs) }
    }

    /** Sets the playback speed on the engine that owns playback. */
    fun activeSetSpeed(speed: Float) {
        if (mpvActive()) mpvEngine?.setSpeed(speed)
        else runCatching { player.setPlaybackSpeed(speed) }
    }

    /** Pauses the engine that owns playback right now. */
    fun activePause() {
        if (mpvActive()) mpvEngine?.setPaused(true)
        else runCatching { player.pause() }
    }

    /** The fork's aspect mode (AspectRatioFrameLayout constants) as MPV 0 = Fit / 1 = Fill / 2 = Zoom. */
    fun mpvAspectMode(): Int = when (state.aspectMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> 1
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> 2
        else -> 0
    }

    /** Applies the RESOLVED subtitle style (this title's override over the Settings defaults) to the MPV engine. */
    fun applyMpvSubtitleStyle() {
        val style = effectiveSubtitleStyle()
        mpvEngine?.applySubtitleStyle(
            textColor = style.textColor,
            backgroundColor = style.backgroundColor,
            edgeColor = style.edgeColor,
            edgeType = style.edgeType,
            fontSizeSp = style.fontSizeSp,
            bottomOffset = style.bottomOffset,
            bold = style.bold,
            // MPV takes an Int family (0 = default, 1 = sans, 2 = serif,
            // 3 = mono); a custom font file has no MPV mapping and keeps
            // the default.
            fontFamily = when (style.fontFamily) {
                "sans" -> 1
                "serif" -> 2
                "mono" -> 3
                else -> 0
            }
        )
    }

    /** Re-apply the resolved subtitle style to whichever engine owns playback right now. */
    fun reapplySubtitleStyle() {
        state.playerView?.let { applySubtitleStyle(it) }
        applyMpvSubtitleStyle()
    }

    /**
     * Starts (or restarts) the MPV engine on [req]. The previous
     * engine instance is released first — one engine per play, and
     * its release runs on its own thread (detach → destroy). The
     * parked ExoPlayer is only paused, never released: the screen
     * keeps holding it, so only a fresh ExoPlayer — built by the
     * switch back — ever replaces it.
     */
    fun activateMpv(req: PlaybackRequest, startMs: Long, autoplay: Boolean) {
        // Stop the parked ExoPlayer's audio before MPV starts.
        runCatching { player.pause() }
        val engine = com.aviostream.app.player.MpvPlayerEngine(
            context,
            hardwareDecoding = settings.mpvHardwareDecoding,
            yuv420pFilter = settings.mpvYuv420pFilter
        )
        engine.onFatalError = { message ->
            // libmpv reports on its own event thread — hop to main
            // before touching player state or rebuilding anything.
            ioScope.launch {
                withContext(Dispatchers.Main) {
                    if (!disposed.get()) mpvFatalErrorHook?.invoke(message)
                }
            }
        }
        engine.onFileLoaded = { applyMpvSubtitleStyle() }
        mpvEngine?.release()
        mpvEngine = engine
        mpvTracksUi = emptyList()
        mpvSubsKey = null
        activeEngine = com.aviostream.app.player.PlaybackEngine.MPV
        engine.initialize()
        engine.setPaused(!autoplay)
        engine.setSpeed(state.speed)
        engine.setResizeMode(mpvAspectMode())
        engine.setAudioDelay(settings.audioDelayMs.toLong())
        engine.load(
            req.streamUrl,
            com.aviostream.app.data.PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv),
            startMs,
            req.mergeAudioUrl
        )
        applyMpvSubtitleStyle()
    }

    /** Releases the MPV engine (idempotent) and returns to Exo. */
    fun releaseMpv() {
        mpvEngine?.release()
        mpvEngine = null
        mpvTracksUi = emptyList()
        mpvSubsKey = null
        if (activeEngine == com.aviostream.app.player.PlaybackEngine.MPV) {
            activeEngine = com.aviostream.app.player.PlaybackEngine.EXO
        }
    }

    // Mirror the live MPV track list into UI state for the panels.
    LaunchedEffect(mpvEngine) {
        val engine = mpvEngine ?: return@LaunchedEffect
        engine.tracks.collect { mpvTracksUi = it }
    }

    // Second-engine surface callback: the SurfaceView hands its
    // surface to the engine on create and takes it back on destroy.
    val mpvSurfaceCallback = remember {
        object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                mpvEngine?.attachSurface(holder.surface)
            }

            override fun surfaceChanged(
                holder: android.view.SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
            }

            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                mpvEngine?.detachSurface()
            }
        }
    }

    /**
     * MPV tracks as panel options: the panels only know
     * [TrackOption], so each MPV track rides a synthetic one-track
     * group (id "mpv:<id>"); selection maps back by language, then
     * by title. The Exo path never sees these.
     */
    fun mpvTrackOptions(type: String): List<TrackOption> {
        return mpvTracksUi.filter { it.type == type }.mapIndexed { index, track ->
            val format = Format.Builder()
                .setId("mpv:${track.id}")
                .setLanguage(track.lang)
                .setLabel(track.title ?: displayLanguage(track.lang))
                .build()
            TrackOption(
                group = TrackGroup(format),
                trackIndex = 0,
                label = trackLabel(format, index),
                selected = track.selected,
                language = track.lang
            )
        }
    }

    fun attachSdhCueFilter(targetPlayer: ExoPlayer, sv: SubtitleView) {
        if (state.cueFilterPlayer === targetPlayer && state.cueFilterListener != null) return
        state.cueFilterListener?.let { state.cueFilterPlayer?.removeListener(it) }
        val output = SdhFilteringTextOutput(sv) { settings.subtitleSdhFilter }
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) { output.onCues(cueGroup) }
        }
        targetPlayer.addListener(listener)
        state.cueFilterListener = listener
        state.cueFilterPlayer = targetPlayer
    }

    // Step 59: seek-preview thumbnails — a second player that grabs
    // frames while the main player keeps playing. Keyed by stream URL
    // so a source switch rebuilds it; the grabber sends the same
    // scoped headers as playback.
    val seekPreviewHeaders = remember(state.currentRequest.streamUrl) {
        PlaybackHeaders.buildPlaybackHeaders(state.currentRequest.headers, isTv)
    }
    val seekPreviewController = rememberSeekPreviewController(
        activity = activity,
        url = state.currentRequest.streamUrl,
        headers = seekPreviewHeaders,
        mimeType = PlaybackHeaders.inferMimeType(state.currentRequest.streamUrl)
    )

    // Step 61: player badges — matched image badges for the playing
    // stream, from the user's imported badge rules (Nuvio's
    // two-phase match: playback text first, meta text second).
    val badgeSettings = remember { com.aviostream.app.data.StreamBadgeSettings(repository.settings) }
    val badgeRules by badgeSettings.rules.collectAsState()
    val showPlayerBadges by badgeSettings.showInPlayer.collectAsState()
    val playerBadgeSource by badgeSettings.playerBadgeSource.collectAsState()
    val playerBadges = remember(state.currentRequest.streamUrl, badgeRules, showPlayerBadges, playerBadgeSource) {
        if (!showPlayerBadges) emptyList()
        else {
            val stream = state.currentRequest.streams.firstOrNull { it.url == state.currentRequest.streamUrl }
            val rules = badgeRules.forPlayer(playerBadgeSource)
            val filters = com.aviostream.app.data.StreamBadgeMatcher.compile(rules)
            val playbackCandidate = stream?.filename?.takeIf { it.isNotBlank() } ?: stream?.title
            val metaCandidates = listOfNotNull(stream?.filename, stream?.title, stream?.name)
            com.aviostream.app.data.StreamBadgeMatcher
                .matchedPlayerBadges(playbackCandidate, metaCandidates, filters)
                .filter { it.imageURL.isNotBlank() }
        }
    }

    // Step 60: binge groups — the release group of the stream now
    // playing is saved under the series id, so the next episode's
    // autoplay prefers the same group instead of re-ranking.
    val bingeGroupCache = remember { BingeGroupCache(repository.settings) }
    LaunchedEffect(state.currentRequest.metaId, state.currentRequest.videoId, state.currentRequest.streamUrl) {
        val stream = state.currentRequest.streams.firstOrNull { it.url == state.currentRequest.streamUrl }
        val group = stream?.bingeGroup
        if (group != null && state.currentRequest.metaId.isNotBlank()) {
            bingeGroupCache.save(state.currentRequest.metaId, group, stream.filename, stream.infoHash)
        }
    }

    // PiP RemoteAction: the floating window's play/pause button toggles the
    // player here. The player listener's onIsPlayingChanged then refreshes
    // the RemoteAction icon via MainActivity.updatePipActions.
    // (Placed after `player` is declared — composable locals are positional.)
    LaunchedEffect(Unit) {
        MainActivity.pipToggleRequest.collect {
            if (mpvActive()) {
                val engine = mpvEngine
                if (engine != null) engine.setPaused(!engine.snapshot.value.paused)
            } else {
                player.playWhenReady = !player.playWhenReady
            }
        }
    }
    /**
     * Effective subtitle timing offset for the current title: global default
     * plus the per-video delay, clamped to ±60s.
     */
    fun effectiveSubOffsetMs(): Long =
        (settings.subtitleOffsetMs + state.subOffsetOverrideMs).coerceIn(-60_000L, 60_000L)
    /**
     * Re-applies the effective timing offset to the raw subtitles and
     * publishes the shifted list (which re-attaches to the player via the
     * existing resolvedSubs effect). Runs on IO; no-op when there is nothing
     * to shift.
     */
    fun applySubtitleTiming() {
        val raw = state.rawResolvedSubs
        if (raw.isEmpty()) return
        val offset = effectiveSubOffsetMs()
        val generation = subtitleShiftGeneration.incrementAndGet()
        ioScope.launch {
            val shifted = withContext(Dispatchers.IO) { shiftSubtitles(raw, offset, context.cacheDir) }
            if (!disposed.get() && subtitleShiftGeneration.get() == generation) state.resolvedSubs = shifted
        }
    }
    // Self-heal handler: probe Content-Type, rebuild once with probed MIME.
    state.healOnFormatError = { error ->
        val req = latestRequest.value
        val old = latestPlayer.value
        ioScope.launch {
            val probed = probePlaybackContentType(
                req.streamUrl,
                PlaybackHeaders.buildPlaybackHeaders(req.headers, isTv)
            )
            // A source switch/zap can finish while the probe is in flight.
            // Never apply the old source's MIME (or release its replacement).
            withContext(Dispatchers.Main) {
                if (disposed.get() || latestRequest.value != req || latestPlayer.value !== old) return@withContext
                if (probed != null) {
                    val playWhenReady = old.playWhenReady
                    val position = old.currentPosition
                    val playbackSpeed = old.playbackParameters.speed
                    val fresh = buildExo(req, state.resolvedSubs, forcedMime = probed)
                        .also { it.addListener(guardedPlayerListener()) }
                    state.playerView?.player = null
                    old.release()
                    player = fresh
                    state.playerError = null
                    fresh.setPlaybackSpeed(playbackSpeed)
                    fresh.seekTo(position)
                    fresh.prepare()
                    fresh.playWhenReady = playWhenReady
                } else if (com.aviostream.app.data.LivePlaybackPolicy.shouldRetry(
                        isLive = req.isLive,
                        isTv = isTv,
                        errorCode = error.errorCode,
                        attemptsSoFar = state.autoFailoverCount
                    )) {
                    state.autoFailoverCount++
                    com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_attempt", 0)
                    state.playerError = null
                    state.failoverToNextSource?.invoke(state.autoFailoverCount)
                } else {
                    if (req.isLive && !isTv &&
                        com.aviostream.app.data.LivePlaybackPolicy.isRecoverableSourceError(error.errorCode)) {
                        com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
                    }
                    state.playerError = com.aviostream.app.data.PlaybackErrorText.explanation(error)
                }
            }
        }
    }
    // Audio self-heal: a surround (multichannel) track can fail on a
    // stereo-only phone (MediaCodecAudioRenderer / AudioTrack init, e.g.
    // 6ch 48kHz raw PCM). Rebuild the SAME source once with downmix on.
    // The new player reads downmixConfig.enabled when its
    // DownmixAudioProcessor configures, so the 6ch mix becomes stereo
    // before it reaches the AudioTrack. (setDownmixEnabled is called here,
    // not in onPlayerError, because it is declared after that listener.)
    state.retryWithDownmix = {
        setDownmixEnabled(true)
        val req = latestRequest.value
        val old = latestPlayer.value
        ioScope.launch {
            withContext(Dispatchers.Main) {
                if (disposed.get() || latestRequest.value != req || latestPlayer.value !== old) return@withContext
                val playWhenReady = old.playWhenReady
                val position = old.currentPosition
                val playbackSpeed = old.playbackParameters.speed
                val fresh = buildExo(req, state.resolvedSubs)
                    .also { it.addListener(guardedPlayerListener()) }
                state.playerView?.player = null
                old.release()
                player = fresh
                state.playerError = null
                fresh.setPlaybackSpeed(playbackSpeed)
                fresh.seekTo(position)
                fresh.prepare()
                fresh.playWhenReady = playWhenReady
            }
        }
    }

    // Unified scrobbling: one pipeline fans playback events out to every
    // linked backend (Trakt, Simkl, …). Each service is enabled/disabled
    // independently via its own login state; auth flows are untouched.
    val simkl = remember { SimklManager(settings) }
    val trakt = remember { TraktManager(settings) }
    val scrobbler = remember { ScrobbleManager(listOf(simkl, trakt), ioScope) }

    // Plex live timeline (Nuvio PlexProvider.report + ServerPlayback):
    // /:/timeline progress while a Plex title plays, so Plex shows
    // now-playing and keeps the resume point live. Plex-only gating
    // lives inside the reporter (videoId "server:{plex}:{ratingKey}").
    val plexTimeline = remember { PlexTimelineReporter(settings::getMediaServers) }

    // PiP: drop the transport UI the moment the window floats (the system
    // draws its own PiP controls); restore the default on return.
    LaunchedEffect(inPip) {
        if (inPip) state.showControls = false
        // Caption sizing switches between fixed sp and fractional in
        // PiP — re-apply so the change lands without a player rebuild.
        state.playerView?.let { applySubtitleStyle(it) }
    }
    val boostHolder = remember {
        object {
            var enhancer: LoudnessEnhancer? = null
            var sessionId: Int = -1
        }
    }
    fun applyVolumeBoost() {
        val sid = runCatching { player.audioSessionId }.getOrDefault(-1)
        if (sid <= 0) return
        if (boostHolder.enhancer == null || boostHolder.sessionId != sid) {
            runCatching { boostHolder.enhancer?.release() }
            boostHolder.enhancer = runCatching { LoudnessEnhancer(sid) }.getOrNull()
            boostHolder.sessionId = sid
        }
        runCatching {
            boostHolder.enhancer?.apply {
                setTargetGain((state.volumeBoost * 1000).roundToInt())
                enabled = state.volumeBoost > 0f
            }
        }
    }
    fun displayVolumePercent(): Int {
        val sys = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val sysPct = sys * 100 / maxVolume.coerceAtLeast(1)
        return if (sys >= maxVolume) sysPct + (state.volumeBoost * 100).roundToInt() else sysPct
    }

    /** Audio modal stepper (Nuvio setAudioAmplification): set the boost
     *  in whole dB, apply it to the enhancer now and persist it — a set
     *  boost applies on the next title. The volume swipe past 100%
     *  still moves volumeBoost for this session only. */
    fun setAudioBoostDb(db: Int) {
        val clamped = db.coerceIn(0, 10)
        state.volumeBoost = clamped / 10f
        applyVolumeBoost()
        settings.audioBoostDb = clamped
    }
    val skipProgress = remember { Animatable(0f) }
    /** Main-thread scope for the delayed single-tap toggle in videoGestures. */
    val gestureUiScope = rememberCoroutineScope()

    val aspectLabel = ASPECT_MODES.firstOrNull { it.first == state.aspectMode }?.second ?: "Fit"

    /** True once the current request has actually played — gates the
     * startup presentation (backdrop + orbit) against a plain rebuffer
     * orbit. Resets whenever the request changes, so episode and
     * source switches start a fresh startup. */
    var hasStartedPlayback by remember(state.currentRequest) { mutableStateOf(false) }


    /**
     * Attaches resolved (downloaded + format-sniffed) subtitles to the current
     * player, preserving position. Auto-picks the default: the explicitly
     * requested URL first, else the first track matching the preferred
     * languages, else the first subtitle (or none when a preference is set
     * but nothing matches).
     *
     * Exo side-loaded subtitles render through the sidecar engine (#26) —
     * parsed off-player and pushed to the caption view — so attaching them
     * never calls setMediaItem+prepare() again and never visibly rebuffers
     * the video (#29). A fresh player built carrying the same subtitles
     * (source switch) keeps the media path: the sidecar stands down there.
     *
     * Skips silently when this exact set is already attached to this player
     * instance — re-attaching would call setMediaItem+prepare() again and
     * visibly rebuffer the video (#29). A fresh player built carrying the
     * same subtitles (source switch) never reaches the sidecar path below.
     */
    fun attachResolvedSubtitles(resolved: List<ResolvedSubtitle>, defaultUrl: String? = null) {
        if (resolved.isEmpty()) return
        // Second engine: MPV takes subtitles as sub-add calls, not a
        // MediaItem rebuild. The default is the requested URL, else
        // the first track in a preferred language, else nothing is
        // forced (libmpv auto-selects).
        if (mpvActive()) {
            val engine = mpvEngine ?: return
            val key = subtitleIdentity(resolved)
            if (mpvSubsKey == key) return
            val prefs = settings.subtitleLanguages
            val defaultSub = resolved.firstOrNull { it.sub.url == defaultUrl }
                ?: resolved.firstOrNull { r -> prefs.any { pref -> subtitleLangMatches(r.sub.lang, pref) } }
            resolved.forEach { r ->
                engine.addExternalSubtitle(r.file.absolutePath, select = r.sub.url == defaultSub?.sub.url)
            }
            mpvSubsKey = key
            state.trackTick++
            return
        }
        val key = subtitleIdentity(resolved)
        val attached = state.subsAttached
        if (attached != null && attached.first === player && attached.second == key) {
            // This player was already built carrying this exact set:
            // its own text renderer owns the subtitles — the sidecar
            // stands down so the same cues are never painted twice.
            deactivateSidecarSubtitle()
            return
        }
        // Sidecar subtitle engine (#26): side-loaded subtitles render
        // off-player instead of riding a setMediaItem+prepare() (which
        // visibly rebuffers). The default pick mirrors
        // subtitleConfigs' rules exactly.
        if (state.sidecarSubUrl != null) {
            // The sidecar owns caption rendering: a rebuilt player
            // that carries the same subtitles in its media item must
            // not paint them a second time through its own text
            // renderer.
            runCatching {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
        }
        val defaultIndex = subtitleDefaultIndex(resolved, defaultUrl)
        if (defaultIndex < 0) {
            // No default (a preference is set but nothing matches):
            // mirror the media path showing nothing — unless this is
            // a manual pick that simply has not resolved yet.
            if (defaultUrl == null && !state.sidecarTouched) deactivateSidecarSubtitle()
            return
        }
        val defaultSub = resolved[defaultIndex]
        if (defaultSub.sub.url == state.sidecarSubUrl) return
        if (defaultUrl == null && state.sidecarTouched) return
        if (defaultUrl != null) state.sidecarTouched = true
        // The sidecar parses the RAW (unshifted) file: the render
        // loop applies the delay as a live offset, so parsing a
        // shifted file would apply the delay twice.
        val raw = state.rawResolvedSubs.firstOrNull { it.sub.url == defaultSub.sub.url } ?: defaultSub
        if (!AvioSidecarSubtitles.canAttachViaSidecar(raw.sub, keepAssOnMediaPath = false)) {
            if (defaultUrl == null && !state.sidecarTouched) deactivateSidecarSubtitle()
            return
        }
        activateSidecarSubtitle(raw)
    }

    val shuffleWatchedKeys by com.aviostream.app.data.WatchedMarks.keys.collectAsState()
    val playerShufflePicker = remember(state.currentRequest.metaId) {
        com.aviostream.app.data.RandomEpisodePicker(emptyList(), emptySet())
    }
    LaunchedEffect(state.currentRequest.episodes, shuffleWatchedKeys) {
        playerShufflePicker.update(
            state.currentRequest.episodes,
            com.aviostream.app.data.watchedEpisodeKeys(state.currentRequest.metaId, shuffleWatchedKeys),
        )
    }

    // Fetch the chosen episode's streams for its streams sheet.
    LaunchedEffect(state.episodeStreamsVideo, state.episodeStreamsTick) {
        val video = state.episodeStreamsVideo ?: return@LaunchedEffect
        state.episodeStreamsLoading = true
        state.episodeStreams = emptyList()
        state.episodeStreamsSubtitles = emptyList()
        val result = withTimeoutOrNull(20_000) {
            repository.streamResultsFlow(state.currentRequest.type, video.id, includePlugs = false)
                .firstOrNull { it.streams.any { stream -> com.aviostream.app.data.isPlayableLiveStreamUrl(stream.url) } }
        }
        state.episodeStreams = result?.streams ?: emptyList()
        state.episodeStreamsSubtitles = result?.subtitles ?: emptyList()
        state.episodeStreamsLoading = false
    }
    var qualitySelection by remember(state.currentRequest.videoId) { mutableStateOf<String?>(null) }
    // HLS variant rows (Nuvio PlayerQualitySelector): the master playlist's
    // actual variants for the playing stream, resolved lazily when the
    // Quality picker opens. Resets per video.
    var hlsQualityState by remember(state.currentRequest.videoId) { mutableStateOf<HlsQualityState?>(null) }
    var hlsVariantSelection by remember(state.currentRequest.videoId) { mutableStateOf<String?>(null) }
    var hlsVariantsLoading by remember(state.currentRequest.videoId) { mutableStateOf(false) }
    // Resolve HLS variants lazily when the Quality picker opens (one extra
    // master-playlist GET per HLS title, only for users who open the picker).
    // Temp-playlist URLs are skipped: they ARE a variant already.
    LaunchedEffect(state.qualityOpen) {
        if (!state.qualityOpen) return@LaunchedEffect
        val url = state.currentRequest.streamUrl
        if (state.currentRequest.hlsMasterUrl != null) return@LaunchedEffect
        if (hlsQualityState?.sourceUrl == url) return@LaunchedEffect
        if (!HlsQualityResolver.looksLikeHlsPlaylist(url)) return@LaunchedEffect
        hlsVariantsLoading = true
        hlsQualityState = HlsQualityResolver.resolve(
            context.applicationContext, url, state.currentRequest.headers
        )
        hlsVariantsLoading = false
    }
    // Keep for next videos (Nuvio keepAudioAmplification): when OFF, a set
    // boost resets to 0 dB when a new title starts. Keyed on videoId, so
    // episode switches reset it but source switches of the same title keep
    // it. Default ON preserves today's always-persist behaviour.
    LaunchedEffect(state.currentRequest.videoId) {
        if (!settings.keepAudioBoost) {
            state.volumeBoost = 0f
            settings.audioBoostDb = 0
            applyVolumeBoost()
        }
    }
    /** Distinct qualities across the loaded streams, best first — the Quality picker options. */
    val qualityOptions = remember(state.availableStreams) {
        val order = listOf("4K", "2160P", "1440P", "1080P", "720P", "480P", "360P")
        state.availableStreams
            .mapNotNull { stream -> streamQualityLabel(stream)?.let { it to stream } }
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedBy { (label, _) -> order.indexOf(label).takeIf { it >= 0 } ?: Int.MAX_VALUE }
            .map { (label, streams) -> label to streams.first() }
    }
    /** A bounded late-source wait is invalidated by manual navigation and disposal. */
    val liveFallbackGeneration = remember { AtomicInteger(0) }

    fun preemptPendingLiveFallback() {
        liveFallbackGeneration.incrementAndGet()
        state.pendingLiveFallbackJob?.cancel()
        state.pendingLiveFallbackJob = null
    }


    val skipSegments = mergeSkipIntervals(state.apiSkipSegments, state.chapterSkipSegments, isMovie = state.currentRequest.isMovie)




    // A new episode/movie clears the end-of-playback cards.
    LaunchedEffect(state.currentRequest.videoId) {
        state.nextEpisodeCard = null
        state.nextEpisodeCountdown = null
        state.movieRecs = emptyList()
        state.movieRecsLoadedFor = null
        state.movieRecsAddonId = null
        state.movieRecsDismissed = false
        state.movieRecsAutoHidden = false
        state.autoSkippedSegments = emptySet()
    }


    // Back hierarchy, in one handler so the order is explicit: sheets first,
    // then hide visible controls, and only then leave the player (#27).
    // (ModalBottomSheets register their own later-composed back handling, so
    // they still dismiss themselves before this runs.)
    BackHandler {
        when {
            state.tracksOpen || state.subSearchOpen || state.sourcesOpen || state.episodesOpen || state.qualityOpen || state.infoOpen || state.chaptersOpen -> {
                state.tracksOpen = false
                state.subSearchOpen = false
                state.sourcesOpen = false
                state.episodesOpen = false
                state.qualityOpen = false
                state.infoOpen = false
                state.chaptersOpen = false
            }
            state.showControls && !state.locked -> state.showControls = false
            else -> onBack()
        }
    }

    val audioOpts = remember(state.trackTick, player, mpvTracksUi) {
        if (mpvActive()) mpvTrackOptions("audio") else trackOptions(player, C.TRACK_TYPE_AUDIO)
    }
    val textOpts = remember(state.trackTick, player, mpvTracksUi) {
        if (mpvActive()) mpvTrackOptions("sub") else trackOptions(player, C.TRACK_TYPE_TEXT)
    }
    // When preferred subtitle languages are set, the Subs picker only offers
    // tracks in those languages (plus Off and Search). Preferences are read
    // fresh every time the dialog opens. Falls back to all tracks if none
    // match, so the menu is never empty.
    val subOpts = remember(state.trackTick, player, state.tracksOpen) {
        val prefs = settings.subtitleLanguages
        if (prefs.isEmpty()) textOpts
        else textOpts.filter { opt ->
            prefs.any { pref ->
                subtitleLangMatches(opt.group.getFormat(opt.trackIndex).language, pref)
            }
        }.ifEmpty { textOpts }
    }
    val subtitlesOff = remember(state.trackTick, player, mpvTracksUi, state.sidecarSubUrl) {
        if (mpvActive()) mpvTracksUi.none { it.type == "sub" && it.selected }
        else state.sidecarSubUrl == null && !hasSelectedTrack(player, C.TRACK_TYPE_TEXT)
    }

    fun pokeIndicator(i: PlayerIndicator) {
        state.indicator = i
        state.indicatorTick++
    }

    /**
     * Switches engine mid-play at [resumeAtMs]. Exo → MPV parks the
     * ExoPlayer (paused, not released — the screen still holds it);
     * MPV → Exo releases the MPV engine and builds a fresh ExoPlayer.
     */
    fun switchPlaybackEngine(
        to: com.aviostream.app.player.PlaybackEngine,
        resumeAtMs: Long
    ) {
        val req = state.currentRequest
        state.playerError = null
        if (to == com.aviostream.app.player.PlaybackEngine.MPV) {
            state.playerView?.player = null
            activateMpv(req, resumeAtMs, autoplay = true)
            if (state.resolvedSubs.isNotEmpty()) attachResolvedSubtitles(state.resolvedSubs)
        } else {
            releaseMpv()
            val old = player
            val fresh = buildExo(req, emptyList())
                .also { it.addListener(guardedPlayerListener()) }
            fresh.setPlaybackSpeed(state.speed)
            state.playerView?.player = null
            player = fresh
            runCatching { old.release() }
            state.subsAttached = null
            fresh.seekTo(resumeAtMs)
            fresh.prepare()
            fresh.playWhenReady = true
        }
        pokeIndicator(
            PlayerIndicator.Message(
                if (to == com.aviostream.app.player.PlaybackEngine.MPV) {
                    "Engine: MPV"
                } else {
                    "Engine: ExoPlayer"
                }
            )
        )
    }

    /**
     * MPV fatal errors, classified like the Exo ladder: an engine
     * fault (init failure, format / codec / decode wording) fails
     * over to the other engine once; anything else is a source
     * fault and walks the next-source ladder; live and trailers
     * never switch engines.
     */
    fun handleMpvFatalError(message: String) {
        if (disposed.get()) return
        val req = state.currentRequest
        val playerFault = message == "Second engine failed to start" ||
            message.contains("format", ignoreCase = true) ||
            message.contains("codec", ignoreCase = true) ||
            message.contains("decode", ignoreCase = true)
        if (playerFault && settings.autoEngineFailover && engineFailoverArmed &&
            !engineFailoverUsed && !req.isLive && !req.isTrailer
        ) {
            engineFailoverUsed = true
            switchPlaybackEngine(
                com.aviostream.app.player.PlaybackEngineResolver.other(activeEngine),
                activePositionMs()
            )
            return
        }
        if (!req.isLive && !req.isTrailer &&
            state.autoFailoverCount < settings.fallbackMaxSources
        ) {
            state.autoFailoverCount++
            state.failoverToNextSource?.invoke(state.autoFailoverCount)
            return
        }
        state.playerError = message
    }

    // Wire the engine hooks now that the engine block exists.
    engineFailoverHook = {
        if (activeEngine == com.aviostream.app.player.PlaybackEngine.EXO) {
            switchPlaybackEngine(
                com.aviostream.app.player.PlaybackEngineResolver.other(activeEngine),
                activePositionMs()
            )
        }
    }
    mpvFatalErrorHook = { message -> handleMpvFatalError(message) }

    fun seekBy(deltaMs: Long) {
        val dur = activeDurationMs().takeIf { it > 0 } ?: Long.MAX_VALUE
        activeSeekTo((activePositionMs() + deltaMs).coerceIn(0L, dur))
    }

    fun seekByWithFeedback(deltaMs: Long) {
        hapticTick()
        seekBy(deltaMs)
        val seconds = (kotlin.math.abs(deltaMs) / 1000L)
        pokeIndicator(PlayerIndicator.Seek((if (deltaMs >= 0) "+" else "−") + "${seconds}s"))
    }

    /**
     * Double-tap seek (Nuvio handleDoubleTapSeek): direction -1 = back,
     * +1 = forward; the step is the Skip seconds setting. Repeated taps
     * in the same direction accumulate from one baseline (so three quick
     * taps jump 3x the step); the accumulated state resets 800ms after
     * the last tap.
     */
    fun doubleTapSeek(direction: Int) {
        val stepMs = (if (direction < 0) settings.skipBackwardSeconds else settings.skipForwardSeconds) * 1000L
        val pos = activePositionMs().coerceAtLeast(0L)
        if (state.accumulatedSeekDirection == direction) {
            state.accumulatedSeekAmountMs += stepMs
        } else {
            state.accumulatedSeekDirection = direction
            state.accumulatedSeekBaselineMs = pos
            state.accumulatedSeekAmountMs = stepMs
        }
        val dur = activeDurationMs().takeIf { it > 0 } ?: Long.MAX_VALUE
        val target = if (direction < 0) {
            (state.accumulatedSeekBaselineMs - state.accumulatedSeekAmountMs).coerceAtLeast(0L)
        } else {
            (state.accumulatedSeekBaselineMs + state.accumulatedSeekAmountMs).coerceIn(0L, dur)
        }
        hapticTick()
        activeSeekTo(target)
        val seconds = state.accumulatedSeekAmountMs / 1000L
        pokeIndicator(PlayerIndicator.Seek((if (direction >= 0) "+" else "−") + "${seconds}s"))
        state.accumulatedSeekResetJob?.cancel()
        state.accumulatedSeekResetJob = gestureUiScope.launch {
            delay(800)
            state.accumulatedSeekDirection = null
            state.accumulatedSeekAmountMs = 0L
        }
    }

    fun toggleControls() {
        if (state.locked) return
        state.showControls = !state.showControls
        if (state.showControls) state.controlsTick++
    }

    fun togglePlay() {
        hapticTick()
        if (mpvActive()) {
            val engine = mpvEngine
            if (engine != null) engine.setPaused(!engine.snapshot.value.paused)
        } else {
            val c = player
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    /** Shared by the header and pill PiP buttons. */
    fun enterPip() {
        if (!settings.pipEnabled) return
        val act = activity as? MainActivity ?: return
        if (mpvActive()) {
            val snap = mpvEngine?.snapshot?.value
            if (snap != null && snap.videoWidth > 0 && snap.videoHeight > 0) {
                MainActivity.pipVideoW = snap.videoWidth
                MainActivity.pipVideoH = snap.videoHeight
            }
        } else {
            val vs = player.videoSize
            if (vs.width > 0 && vs.height > 0) {
                MainActivity.pipVideoW = vs.width
                MainActivity.pipVideoH = vs.height
            }
        }
        act.enterPictureInPictureMode(
            MainActivity.buildPipParams(act, activeIsPlaying())
        )
    }

    // TV remote fallback: MainActivity.onKeyDown forwards Left/Right/FF/RW
    // here ONLY when Compose didn't consume the key (e.g. focus was lost).
    LaunchedEffect(Unit) {
        MainActivity.playerSeekRequest.collect { seekByWithFeedback(it) }
    }

    fun cycleAspect() {
        state.aspectMode = when (state.aspectMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        settings.resizeMode = state.aspectMode
        // Nuvio cycleResizeMode: a resize reset clears the pinch zoom.
        state.videoZoom = 1f
        state.rawVideoZoom = 1f
        state.playerView?.resizeMode = state.aspectMode
        // Second engine: the same cycle drives libmpv's panscan.
        if (mpvActive()) mpvEngine?.setResizeMode(mpvAspectMode())
        pokeIndicator(PlayerIndicator.Message("Aspect: $aspectLabel"))
    }

    fun startSeekPreview() {
        // Step 59: warms the seek-preview cache around the current
        // position so the first scrub shows a frame at once.
        seekPreviewController?.warmUp(activePositionMs(), activeDurationMs())
    }

    LaunchedEffect(hasStartedPlayback) {
        if (hasStartedPlayback) startSeekPreview()
    }

    fun applyAudioSelection(opt: TrackOption?) {
        // A manual pick (including "Auto", which hands selection back) is
        // the user's word for this session — auto-select steps aside.
        state.userPickedAudio = true
        // Second engine: MPV tracks select by language, then title.
        if (mpvActive()) {
            if (opt == null) mpvEngine?.selectAudio(null)
            else {
                val track = mpvTracksUi.firstOrNull { it.type == "audio" && it.lang == opt.language }
                    ?: mpvTracksUi.firstOrNull { it.type == "audio" && it.title == opt.label }
                mpvEngine?.selectAudio(track?.id)
            }
            state.trackTick++
            pokeIndicator(PlayerIndicator.Message(if (opt == null) "Audio: Auto" else "Audio: ${opt.label}"))
            return
        }
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) params.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
        else params.setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
        player.trackSelectionParameters = params.build()
        state.trackTick++
        pokeIndicator(PlayerIndicator.Message(if (opt == null) "Audio: Auto" else "Audio: ${opt.label}"))
    }

    /**
     * Tightened preferred-audio enforcement (v0.12.0): buildExo only passes
     * the Settings > Playback language as a track-selector *hint*, which
     * the selector could outvote (channel count / role flags), so the
     * chosen language sometimes didn't play even when present. Now the
     * preference is applied as a hard TrackSelectionOverride for the first
     * audio track whose language matches, re-applied whenever the track
     * set changes (playback start, source switch, late track arrivals).
     * No matching track → parameters stay untouched (graceful fallback to
     * the default selection).
     */
    fun applyPreferredAudioLanguage() {
        // Second engine: libmpv picks its own audio track.
        if (mpvActive()) return
        if (state.userPickedAudio) return
        // Step 38: ordered targets — primary, then secondary (Nuvio
        // resolvePreferredAudioLanguageTargets, Avio-native). The first
        // target that has a matching track wins.
        val targets = listOfNotNull(
            settings.preferredAudioLanguage?.takeIf { it.isNotBlank() },
            settings.secondaryPreferredAudioLanguage?.takeIf { it.isNotBlank() },
        ).distinct()
        if (targets.isEmpty()) return
        val p = player
        // Variant-aware match: a track's es-419/es or pt-br/pt variant comes
        // from its language tag + label (e.g. "Español (Latinoamérica)").
        fun trackLanguage(format: Format): String =
            detectTrackLanguageVariant(format.language, format.label, null)
        // Already playing a preferred language — nothing to tighten.
        val alreadySelected = p.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            .any { g ->
                (0 until g.length).any { i ->
                    g.isTrackSelected(i) && targets.any { t ->
                        subtitleLangMatches(trackLanguage(g.getTrackFormat(i)), t)
                    }
                }
            }
        if (alreadySelected) return
        val match = targets.firstNotNullOfOrNull { target ->
            p.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO }
                .firstNotNullOfOrNull { g ->
                    (0 until g.length).firstOrNull { i ->
                        subtitleLangMatches(trackLanguage(g.getTrackFormat(i)), target)
                    }?.let { idx -> g to idx }
                }
        } ?: return
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setPreferredAudioLanguages(*targets.toTypedArray())
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
            .setOverrideForType(TrackSelectionOverride(match.first.mediaTrackGroup, listOf(match.second)))
            .build()
        state.trackTick++
    }

    // Apply the preferred audio language on player (re)creation and on
    // every track-set change for that player.
    LaunchedEffect(player) {
        state.userPickedAudio = false
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                applyPreferredAudioLanguage()
            }
        }
        player.addListener(listener)
        applyPreferredAudioLanguage()
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            player.removeListener(listener)
        }
    }

    fun applySubtitleSelection(opt: TrackOption?) {
        // Picking an embedded track (or "None") hands rendering back
        // from the sidecar engine to the player's own text renderer.
        // (MPV picks never touch the sidecar: its URL is preserved so
        // switching back to Exo resumes the same subtitle.)
        if (!mpvActive() && state.sidecarSubUrl != null) deactivateSidecarSubtitle()
        // Second engine: MPV tracks select by language, then title.
        if (mpvActive()) {
            if (opt == null) mpvEngine?.selectSubtitle(null)
            else {
                val track = mpvTracksUi.firstOrNull { it.type == "sub" && it.lang == opt.language }
                    ?: mpvTracksUi.firstOrNull { it.type == "sub" && it.title == opt.label }
                mpvEngine?.selectSubtitle(track?.id)
            }
            state.trackTick++
            pokeIndicator(
                PlayerIndicator.Message(if (opt == null) "Subtitles: Off" else "Subtitles: ${opt.label}")
            )
            return
        }
        state.sidecarTouched = true
        val params = player.trackSelectionParameters.buildUpon()
        if (opt == null) {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            params.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            params.setOverrideForType(TrackSelectionOverride(opt.group, listOf(opt.trackIndex)))
        }
        player.trackSelectionParameters = params.build()
        state.trackTick++
        pokeIndicator(
            PlayerIndicator.Message(if (opt == null) "Subtitles: Off" else "Subtitles: ${opt.label}")
        )
    }

    fun openSubtitleSearch() {
        // Panel-integrated (v0.14.2): results merge into the subtitle
        // panel's list instead of opening a separate sheet.

        state.subSearchLoading = true
        state.subSearchError = null
        state.subSearchResults = emptyList()
        // Capture request data on the main thread before hopping to IO.
        val imdb = state.currentRequest.imdbId
        val isMovie = state.currentRequest.isMovie
        val existingUrls = (state.currentRequest.subtitles + state.addedSubs).map { it.url }.toSet()
        ioScope.launch {
            try {
                if (imdb.isNullOrBlank()) {
                    withContext(Dispatchers.Main) {
                        state.subSearchError = "No title info available for subtitle search."
                    }
                } else {
                    val type = if (isMovie) "movie" else "series"
                    val results = repository.searchSubtitles(type, imdb)
                        .filter { it.url !in existingUrls }
                    withContext(Dispatchers.Main) {
                        state.subSearchResults = results
                        if (results.isEmpty()) state.subSearchError = "No subtitles found for this title."
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    state.subSearchError = e.message ?: "Subtitle search failed."
                }
            } finally {
                withContext(Dispatchers.Main + NonCancellable) { state.subSearchLoading = false }
            }
        }
    }


    fun addExternalSubtitle(sub: Subtitle) {
        state.addedSubs = state.addedSubs + sub
        state.subSearchOpen = false
        // The resolver effect below picks this up, downloads it and makes it
        // the active subtitle.
        state.pendingDefaultSubUrl = sub.url
        pokeIndicator(
            PlayerIndicator.Message("Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}")
        )
    }

    // ---- Subtitle panel plumbing (v0.14.2, owner's reference design) ----

    // First-party subtitles (v0.14.30 port): Avio's own subtitle source —
    // every language it has for this title (listed in the subtitle panel,
    // labelled "Avio Subs") and the single auto-pick winner that is
    // downloaded + applied without a tap.
    var firstPartySubs by remember(state.currentRequest) { mutableStateOf(emptyList<Subtitle>()) }
    var firstPartyAutoSub by remember(state.currentRequest) { mutableStateOf<Subtitle?>(null) }

    /** Every subtitle the panel can offer, instantly: the request's own
     *  (straight from the addon response — no waiting), the first-party
     *  entries, the user's additions and online-search results, deduped
     *  by URL. */
    fun panelSubs(): List<Subtitle> =
        (state.currentRequest.subtitles + firstPartySubs + state.addedSubs + state.subSearchResults).distinctBy { it.url }

    fun panelTrackId(opt: TrackOption): String? =
        runCatching { opt.group.getFormat(opt.trackIndex).id }.getOrNull()

    // Step 67: external-player handoff with subtitles + skip segments.
    // Preparation (subtitle ordering + IntroDB lookup) lives in
    // ExternalPlayer.prepareLaunch; the intent fires on Main.
    fun launchExternalPlayer(ctx: Context, req: PlaybackRequest) {
        val posAtClick = state.positionMs
        ioScope.launch {
            val selectedUrl = activeSidecarUrl()
                ?: textOpts.firstOrNull { it.selected }?.let { panelTrackId(it) }
            val extras = ExternalPlayer.prepareLaunch(
                ctx, req, settings,
                alreadyResolved = state.resolvedSubs,
                selectedSubtitleUrl = selectedUrl,
                isTv = isTv
            )
            withContext(Dispatchers.Main) {
                ExternalPlayer.play(
                    ctx, req,
                    subtitles = extras.subtitles,
                    skipSegmentsJson = extras.skipSegmentsJson,
                    resumePositionMs = posAtClick
                )
            }
        }
    }

    fun panelLangName(code: String?): String =
        displayLanguage(code) ?: code?.uppercase() ?: "Unknown"

    /** Player text tracks that did NOT come from the panel's subtitle
     *  list — i.e. tracks embedded in the video file itself. */
    fun panelEmbeddedOpts(): List<TrackOption> {
        val subs = panelSubs()
        return textOpts.filter { o -> subs.none { it.url == panelTrackId(o) } }
    }

    /** (language name, track count) pairs in first-seen order. */
    fun panelLanguages(): List<Pair<String, Int>> {
        val counts = linkedMapOf<String, Int>()
        panelSubs().forEach {
            val n = panelLangName(it.lang)
            counts[n] = (counts[n] ?: 0) + 1
        }
        panelEmbeddedOpts().forEach { o ->
            val n = panelLangName(
                runCatching { o.group.getFormat(o.trackIndex).language }.getOrNull()
            )
            counts[n] = (counts[n] ?: 0) + 1
        }
        return counts.entries.map { it.key to it.value }
    }

    /** Tap-to-apply for a panel subtitle: if its track is already in the
     *  player, select it; if it is still resolving, make it the default
     *  the attach effect picks; otherwise add it (download + apply). */
    fun activatePanelSubtitle(sub: Subtitle) {
        // Second engine: external subtitles sub-add straight into MPV,
        // preferring the already-downloaded file over the remote URL.
        if (mpvActive()) {
            val local = state.resolvedSubs.firstOrNull { it.sub.url == sub.url }?.file?.absolutePath
            mpvEngine?.addExternalSubtitle(local ?: sub.url, select = true)
            pokeIndicator(
                PlayerIndicator.Message("Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}")
            )
            return
        }
        val opt = textOpts.firstOrNull { panelTrackId(it) == sub.url }
        when {
            opt != null -> applySubtitleSelection(opt)
            state.rawResolvedSubs.any { it.sub.url == sub.url } -> {
                // Already downloaded: render it through the sidecar at
                // once — no re-prepare, no waiting on a republish.
                val raw = state.rawResolvedSubs.first { it.sub.url == sub.url }
                state.sidecarTouched = true
                state.pendingDefaultSubUrl = null
                activateSidecarSubtitle(raw)
                pokeIndicator(
                    PlayerIndicator.Message(
                        "Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}"
                    )
                )
            }
            state.addedSubs.any { it.url == sub.url } ||
                state.currentRequest.subtitles.any { it.url == sub.url } -> {
                state.pendingDefaultSubUrl = sub.url
                pokeIndicator(
                    PlayerIndicator.Message(
                        "Subtitles: ${displayLanguage(sub.lang) ?: sub.lang.uppercase()}"
                    )
                )
            }
            else -> addExternalSubtitle(sub)
        }
    }

    fun panelHostLabel(url: String): String {
        val host = runCatching { android.net.Uri.parse(url).host }.getOrNull()
            ?.removePrefix("www.")?.takeIf { it.isNotBlank() }
        return if (host == FirstPartySubsClient.HOST) "Avio Subs" else host ?: "Addon"
    }

    fun panelFileLabel(sub: Subtitle): String =
        sub.url.substringAfterLast('/').substringBefore('?')
            .ifBlank { displayLanguage(sub.lang) ?: sub.lang.uppercase() }

    fun panelFormatLabel(mimeType: String): String? = when (mimeType) {
        "application/x-subrip" -> "SRT"
        "text/vtt" -> "VTT"
        "application/ttml+xml" -> "TTML"
        else -> null
    }

    /** Panel delay readout in the reference format: "+0.500s". */
    fun panelDelayLabel(ms: Long): String =
        String.format(java.util.Locale.US, "%+.3fs", ms / 1000.0)

    /** Parses (startMs, text) cues out of a downloaded SRT/VTT file. */
    fun parseSubtitleCues(file: java.io.File): List<Pair<Long, String>> {
        val out = mutableListOf<Pair<Long, String>>()
        val timing = Regex("(\\d{1,2}):(\\d{2}):(\\d{2})[.,](\\d{3})\\s*-->")
        val lines = runCatching { file.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
        var i = 0
        while (i < lines.size && out.size < 4000) {
            val m = timing.find(lines[i])
            if (m == null) { i++; continue }
            val (h, min, sec, ms) = m.destructured
            val start = ((h.toLong() * 60 + min.toLong()) * 60 + sec.toLong()) * 1000 + ms.toLong()
            val text = StringBuilder()
            i++
            while (i < lines.size && lines[i].isNotBlank() && !timing.containsMatchIn(lines[i])) {
                val t = lines[i].replace(Regex("<[^>]+>"), "").trim()
                if (t.isNotEmpty() && !t.all { ch -> ch.isDigit() }) {
                    if (text.isNotEmpty()) text.append(' ')
                    text.append(t)
                }
                i++
            }
            if (text.isNotEmpty()) out.add(start to text.toString())
        }
        return out
    }

    /**
     * Style rail delay control: set the per-video delay (the stepper's
     * ±100ms, Reset's 0, or an Auto Sync cue tap), persist it for this
     * video, cancel any in-progress auto-sync cue pick (a manual change
     * always wins), and re-shift the downloaded subtitles.
     */
    fun setSubtitleDelay(delayMs: Long) {
        val clamped = delayMs.coerceIn(-60_000L, 60_000L)
        state.subOffsetOverrideMs = clamped
        settings.setSubtitleDelayMsFor(state.currentRequest.videoId, clamped)
        state.captureCues = null
        applySubtitleTiming()
    }


    /** Both taps captured: shift the delay by (heard − saw) — heard
     *  before seen means the subtitles are late, so the delay drops —
     *  persist it per video via setSubtitleDelay, then clear the
     *  captures so the user can measure again. */
    fun applySyncByEarIfReady() {
        val heard = state.syncByEarHeardMs ?: return
        val saw = state.syncByEarSawMs ?: return
        state.syncByEarHeardMs = null
        state.syncByEarSawMs = null
        setSubtitleDelay(state.subOffsetOverrideMs + heard - saw)
        pokeIndicator(PlayerIndicator.Message("Subtitles synced: ${panelDelayLabel(effectiveSubOffsetMs())}"))
    }

    fun captureSyncByEarHeard() {
        state.syncByEarHeardMs = activePositionMs().coerceAtLeast(0L)
        applySyncByEarIfReady()
    }

    fun captureSyncByEarSaw() {
        state.syncByEarSawMs = activePositionMs().coerceAtLeast(0L)
        applySyncByEarIfReady()
    }

    /** Auto Sync > Reload: drop the cached downloads + shifted copies
     *  and re-resolve every subtitle fresh from its source. */
    fun reloadPanelSubtitles() {
        state.rawResolvedSubs.forEach { raw ->
            runCatching { raw.file.delete() }
            val key = raw.file.absolutePath.hashCode().toUInt().toString(16)
            context.cacheDir.listFiles()?.forEach { f ->
                if (f.name.startsWith("shift_") && f.name.contains(key)) {
                    runCatching { f.delete() }
                }
            }
        }
        state.subReloadTick++
        pokeIndicator(PlayerIndicator.Message("Subtitles reloading…"))
    }

    /** Auto Sync > Capture: collect the cues of the active downloaded
     *  subtitle around the current position; the user then taps the
     *  line they are hearing and the delay is set from it. */
    fun startCaptureSync() {
        val selectedUrl = activeSidecarUrl()
            ?: textOpts.firstOrNull { it.selected }?.let { panelTrackId(it) }
        val raw = state.rawResolvedSubs.firstOrNull { it.sub.url == selectedUrl }
            ?: state.rawResolvedSubs.firstOrNull()
        if (raw == null) {
            pokeIndicator(PlayerIndicator.Message("Auto sync needs a downloaded subtitle track"))
            return
        }
        // The position is captured NOW (when the line is heard); a cue
        // tap later computes the delay from this stored position.
        state.capturePositionMs = activePositionMs()
        state.captureLoading = true
        ioScope.launch {
            val cues = parseSubtitleCues(raw.file)
            val center = (state.capturePositionMs - effectiveSubOffsetMs()).coerceAtLeast(0L)
            // The five cues nearest the captured position, in time order.
            val near = cues.sortedBy { kotlin.math.abs(it.first - center) }
                .take(5).sortedBy { it.first }
            withContext(Dispatchers.Main) {
                state.captureLoading = false
                state.captureCues = near
                if (near.isEmpty()) {
                    pokeIndicator(PlayerIndicator.Message("No subtitle lines found to sync from"))
                }
            }
        }
    }

    // URL of a just-added subtitle that should become active once resolved.

    // Resolve side-loaded subtitles in the background once per request (or
    // when the user adds one via search): download, sniff the real format,
    // then attach. Files are cached so this is instant on repeat visits. The
    // resolved list goes into state; a separate effect attaches it, so a
    // source switch can carry the subtitles into the fresh player instead of
    // re-preparing the video (#29).

    // The first-party auto-pick winner: the best match under the user's
    // subtitle rules (languages first; Always-on falls back to the first
    // available; Off picks none — the entries still list in the panel).
    // The first-party auto-pick winner, via the shared ranked auto-pick
    // (v0.14.30 #47): language priority first, full tracks above forced
    // ones in the same language. No SDH-preference setting exists in Avio
    // yet (preferSdh off) and forced tracks stay eligible. Behaviour
    // change vs the old inline pick: with NO preferred languages set,
    // the best available track now turns on (the shared ranker's rule,
    // matching the resolved-subtitles path) instead of none.
    fun pickFirstPartyAutoSub(sorted: List<Subtitle>): Subtitle? {
        val idx = autoPickSubtitleIndex(
            sorted,
            settings.subtitleLanguages,
            settings.subtitleDefaultMode,
            preferSdh = false,
            forcedAutoShow = true
        )
        return if (idx >= 0) sorted[idx] else null
    }

    // First-party subtitles (v0.14.30 port): Avio's own subtitle addon is
    // a BUILT-IN source in the player — on play, fetch its subtitles for
    // this title, no manual step. The panel lists every language returned
    // with the user's own languages first; the best match under the user's
    // subtitle rules (languages / default mode) becomes [firstPartyAutoSub]
    // and the resolver below downloads + applies it. Any other entry
    // downloads only if the user taps it in the panel. Fully off the
    // playback path: the site being unreachable or returning nothing
    // changes nothing.
    LaunchedEffect(state.currentRequest) {
        val req = state.currentRequest
        if (req.isLive || req.isTrailer || req.streamUrl.startsWith("file:", true) ||
            req.imdbId.isNullOrBlank()
        ) {
            return@LaunchedEffect
        }
        val found = FirstPartySubsClient.fetch(
            repository.api, req.type, req.imdbId!!, req.season, req.episode
        )
        if (found.isEmpty() || state.currentRequest != req || disposed.get()) {
            return@LaunchedEffect
        }
        // Entries the playing addon already carries for this title
        // (same file URL) are not duplicated.
        val fresh = found.filter { candidate ->
            req.subtitles.none { it.url == candidate.url }
        }
        // The user's own subtitle languages first (settings order,
        // stable); everything else keeps the server's order.
        val prefs = settings.subtitleLanguages
        val sorted = if (prefs.isEmpty()) {
            fresh
        } else {
            fresh.sortedBy { sub ->
                val idx = prefs.indexOfFirst { pref -> subtitleLangMatches(sub.lang, pref) }
                if (idx >= 0) idx else Int.MAX_VALUE
            }
        }
        firstPartySubs = sorted
        firstPartyAutoSub = pickFirstPartyAutoSub(sorted)
    }

    LaunchedEffect(state.currentRequest, state.addedSubs, state.subReloadTick, firstPartyAutoSub) {
        val reqAtStart = state.currentRequest
        // The first-party auto-pick winner rides along FIRST, so the
        // shared auto-pick's stable ranking prefers it on ties inside
        // the user's top language; a remembered manual pick still
        // overrides it in subtitleConfigs.
        val all = listOfNotNull(firstPartyAutoSub) + reqAtStart.subtitles + state.addedSubs
        state.subLoadError = false
        state.resolvedSubs = emptyList()
        state.rawResolvedSubs = emptyList()
        if (all.isEmpty() || disposed.get()) return@LaunchedEffect
        val resolved = resolveSubtitleFiles(
            all, context.cacheDir,
            requestHeaders = reqAtStart.headers,
            streamOriginUrl = reqAtStart.streamUrl,
            isTv = isTv
        )
        // Superseded while resolving (source/episode/zap switch) — the newer
        // effect run owns the result.
        if (state.currentRequest != reqAtStart || disposed.get()) return@LaunchedEffect
        state.subLoadError = resolved.isEmpty()
        // Store the raw files, then publish the timing-shifted list. The
        // separate attach effect picks up resolvedSubs.
        state.rawResolvedSubs = resolved
        val generation = subtitleShiftGeneration.incrementAndGet()
        val shifted = withContext(Dispatchers.IO) {
            shiftSubtitles(resolved, effectiveSubOffsetMs(), context.cacheDir)
        }
        if (state.currentRequest == reqAtStart && subtitleShiftGeneration.get() == generation && !disposed.get()) {
            state.resolvedSubs = shifted
        }
    }

    // Load the persisted per-video delay only when the actual
    // title/episode changes; adding a subtitle or switching source does
    // not discard the user's delay.
    LaunchedEffect(state.currentRequest.metaId, state.currentRequest.videoId, state.currentRequest.season, state.currentRequest.episode) {
        // A new title starts the sidecar clean: the old title's cues
        // must never paint over the new one, and its manual choice
        // must not veto the new title's default.
        deactivateSidecarSubtitle()
        state.sidecarTouched = false
        // Per-title subtitle style (#27): the override follows the title.
        if (state.subtitleOverrideKey != state.currentRequest.videoId) {
            state.subtitleOverrideKey = state.currentRequest.videoId
            state.subtitleOverrideState =
                SubtitleStyleOverrides.load(context, state.currentRequest.videoId)
        }
        reseedStyleRailMirrors()
        state.subOffsetOverrideMs = settings.subtitleDelayMsFor(state.currentRequest.videoId)
        // A new title invalidates any half-finished sync-by-ear capture.
        state.syncByEarHeardMs = null
        state.syncByEarSawMs = null
        if (state.rawResolvedSubs.isNotEmpty()) applySubtitleTiming()
    }

    // Attach resolved subtitles to the player. Skips silently when the fresh
    // player was already built carrying them (source switch) — no re-prepare.
    LaunchedEffect(player, state.resolvedSubs) {
        if (state.resolvedSubs.isEmpty() || disposed.get()) return@LaunchedEffect
        val wantDefault = state.pendingDefaultSubUrl
        state.pendingDefaultSubUrl = null
        attachResolvedSubtitles(state.resolvedSubs, wantDefault)
    }

    // Sidecar render loop (#26): every 100 ms, push the cues active at
    // (position − subtitle delay) into the PlayerView's SubtitleView.
    // The delay is a render offset here — changing it never re-shifts a
    // file and never touches the media item. Unchanged cue sets are
    // never re-pushed (signature check). MPV owns playback while it is
    // active, so the loop idles there; the sidecar URL is preserved
    // and resumes when Exo takes over again.
    LaunchedEffect(player, state.sidecarSubUrl) {
        if (state.sidecarSubUrl == null) return@LaunchedEffect
        var lastSignature = Long.MIN_VALUE
        while (!disposed.get()) {
            if (!mpvActive()) {
                val pv = state.playerView
                val cues = state.sidecarCues
                if (pv != null) {
                    if (cues.isEmpty()) {
                        if (lastSignature != 0L) {
                            lastSignature = 0L
                            pv.subtitleView?.setCues(emptyList())
                        }
                    } else {
                        // The lookup position carries the per-title
                        // offset — render-time only, the stored cues
                        // stay pristine. No drift correction: the fork
                        // has no per-title speed store, so 1000 (1x).
                        val positionUs =
                            AvioSidecarSubtitles.scaledLookupPositionUs(
                                runCatching { player.currentPosition }.getOrDefault(0L),
                                effectiveSubOffsetMs(),
                                1000
                            )
                        val active = AvioSidecarSubtitles.collectActiveCues(cues, positionUs)
                        val merged = AvioSidecarSubtitles.mergeOverlappingCues(active)
                        // Step 38 SDH filtering applies to sidecar cues
                        // too — the Exo onCues path never fires while
                        // the text renderer is disabled.
                        val shown = if (settings.subtitleSdhFilter) {
                            merged.mapNotNull { cue ->
                                val text = cue.text?.toString() ?: return@mapNotNull cue
                                val clean = SubtitleSdhFilter.filter(text)
                                    ?: return@mapNotNull null
                                if (clean == text) cue
                                else cue.buildUpon().setText(clean).build()
                            }
                        } else {
                            merged
                        }
                        val signature = AvioSidecarSubtitles.activeCueSignature(shown)
                        if (signature != lastSignature) {
                            lastSignature = signature
                            pv.subtitleView?.setCues(shown)
                        }
                    }
                }
            }
            delay(AvioSidecarSubtitles.RENDER_INTERVAL_MS)
        }
    }

    // Step 68: parental guide — fetch once playback starts; the overlay
    // shows itself for ~5s and fades out. A new title clears the old
    // warnings first so they never linger on the wrong video.
    LaunchedEffect(state.currentRequest.videoId, hasStartedPlayback) {
        state.parentalGuideVisible = false
        state.parentalWarnings = emptyList()
        if (!settings.showParentalGuide || !hasStartedPlayback) return@LaunchedEffect
        val imdb = extractParentalGuideImdbId(
            state.currentRequest.imdbId ?: state.currentRequest.metaId
        ) ?: return@LaunchedEffect
        val guide = ParentalGuideRepository.getParentalGuide(imdb) ?: return@LaunchedEffect
        val warnings = buildParentalWarnings(guide)
        if (warnings.isEmpty() || disposed.get()) return@LaunchedEffect
        state.parentalWarnings = warnings
        state.parentalGuideVisible = true
    }

    // PrePlay (v0.14.30 port, feature #45): while an episode plays,
    // prefetch the NEXT episode's streams into the session stream
    // cache so the play press is instant. Gated on the switch
    // (default OFF); failures silent; never re-prefetch a fresh
    // pocket (see repository.prePlayPrefetch). Navigating away
    // cancels this effect — the collect inside the prefetch is
    // cancelled with it.
    LaunchedEffect(state.currentRequest.videoId, state.currentRequest.episodes.size) {
        val req = state.currentRequest
        if (!settings.prePlayEnabled || req.isMovie || req.isLive || req.isTrailer ||
            req.episodes.isEmpty()
        ) return@LaunchedEffect
        val idx = req.episodes.indexOfFirst { it.id == req.videoId }
            .takeIf { it >= 0 }
            ?: req.episodes.indexOfFirst {
                it.season == req.season && it.episode == req.episode
            }
        val next = if (idx >= 0) req.episodes.getOrNull(idx + 1) else null
        if (next == null) return@LaunchedEffect
        runCatching {
            repository.prePlayPrefetch(req.type, next.id)
        }
    }

    /**
     * Serializes player rebuilds (#5): rapid source switches/zaps used to
     * orphan the first fresh player (still playWhenReady → double audio) and
     * could complete out of order. Only the latest request wins; a superseded
     * request releases nothing — the winner owns the old player.
     */
    val replaceMutex = remember { Mutex() }
    val replaceGen = remember { AtomicInteger(0) }

    /** Rebuilds the ExoPlayer around a new stream, preserving position. */
    fun replaceStream(newReq: PlaybackRequest, startAtMs: Long, autoplay: Boolean, preserveAudioHeal: Boolean = false) {
        if (disposed.get()) return
        preemptPendingLiveFallback()
        val gen = replaceGen.incrementAndGet()
        val old = player
        val oldReq = state.currentRequest
        // Read the old player's position on THIS thread (the player's own
        // thread, normally main). Touching it from a background thread crashes.
        val posMs = activePositionMs()
        val durMs = activeDurationMs()
        ioScope.launch {
            replaceMutex.withLock {
                if (gen != replaceGen.get() || disposed.get()) return@withLock
                // #8: live TV never enters Continue Watching.
                if (!oldReq.isLive) {
                    repository.savePlayback(
                        title = oldReq.title,
                        poster = oldReq.poster,
                        videoId = oldReq.videoId,
                        streamUrl = oldReq.streamUrl,
                        positionMs = posMs,                        durationMs = durMs,                        imdbId = oldReq.imdbId,
                        season = oldReq.season,
                        episode = oldReq.episode
                    )
                }
                // #17: positions are keyed by URL — the old source's entry is
                // kept, not deleted, so its progress survives a switch-back.
                // Everything below touches the player or Compose state, so it must
                // run on the main thread no matter which thread called us.
                withContext(Dispatchers.Main) {
                    if (gen != replaceGen.get() || disposed.get()) return@withContext
                    state.playerError = null
                    state.mimeHealAttempted = false
                    // Audio setting reset (user: reset on source failure):
                    // if the previous source's audio failure auto-enabled
                    // downmix (self-heal), turn it back OFF for the new
                    // source — every source is tried with normal settings
                    // first. A downmix the user turned on by hand is
                    // untouched (the flag is only true after a self-heal).
                    // A same-source Retry keeps the heal (preserveAudioHeal):
                    // the source just proved it needs downmix, so retrying
                    // without it would fail the same way again.
                    if (!preserveAudioHeal) {
                        if (state.downmixFailoverAttempted) {
                            setDownmixEnabled(false)
                        }
                        state.downmixFailoverAttempted = false
                    }
                    if (oldReq.type != newReq.type || oldReq.videoId != newReq.videoId || oldReq.liveChannel != newReq.liveChannel) {
                        state.streamsJob?.cancel()
                        state.availableStreams = com.aviostream.app.ui.screens.player.mergePlayerSourceSet(
                            emptyList(), newReq.streams, includeHeadersInIdentity = newReq.isLive && !isTv
                        )
                        state.attemptedLiveSourceIds = emptySet()
                    }
                    state.currentRequest = newReq
                    repository.pendingPlayback = newReq
                    saveLinkForReuse(newReq)
                    state.resumeOfferMs = null
                    // #29: carry already-resolved subtitles (same title) into
                    // the fresh player so the attach effect doesn't re-prepare
                    // the video just to add them back.
                    val carrySubs =
                        newReq.subtitles == oldReq.subtitles && state.resolvedSubs.isNotEmpty()
                    val carried = if (carrySubs) state.resolvedSubs else emptyList()
                    val targetEngine = com.aviostream.app.player.PlaybackEngineResolver.resolve(
                        settings.playbackEngine, newReq
                    )
                    if (targetEngine == com.aviostream.app.player.PlaybackEngine.MPV) {
                        // Second engine: the parked ExoPlayer stays
                        // paused; MPV owns playback from here.
                        state.playerView?.player = null
                        runCatching { old.pause() }
                        activateMpv(newReq, startAtMs, autoplay)
                        if (state.resolvedSubs.isNotEmpty()) attachResolvedSubtitles(state.resolvedSubs)
                    } else {
                        if (activeEngine == com.aviostream.app.player.PlaybackEngine.MPV) releaseMpv()
                        val fresh = buildExo(newReq, carried).also { it.addListener(guardedPlayerListener()) }
                        // #16: a fresh player defaults to 1x — re-apply the pill.
                        fresh.setPlaybackSpeed(state.speed)
                        state.subsAttached =
                            if (carried.isNotEmpty()) fresh to subtitleIdentity(carried) else null
                        // Detach the surface from the old player before releasing it.
                        state.playerView?.player = null
                        player = fresh
                        old.release()
                        seekToPlaybackStart(
                            isMobileLive = newReq.isLive && !isTv,
                            startAtMs = startAtMs,
                            seekToPosition = { fresh.seekTo(it) },
                            seekToDefaultPosition = { fresh.seekToDefaultPosition() }
                        )
                        fresh.prepare()
                        fresh.playWhenReady = autoplay
                    }
                    state.showControls = true
                    state.controlsTick++
                }
            }
        }
    }

    fun switchSource(stream: StreamItem) {
        if (!com.aviostream.app.data.isPlayableLiveStreamUrl(stream.url)) return
        preemptPendingLiveFallback()
        val mobileLive = state.currentRequest.isLive && !isTv
        val sameCandidate = if (mobileLive) {
            com.aviostream.app.data.LivePlaybackPolicy.isSameMobileLiveCandidate(
                state.currentRequest.streamUrl, state.currentRequest.headers, stream.url, stream.headers
            )
        } else stream.url == state.currentRequest.streamUrl
        if (sameCandidate) return
        state.sourcesOpen = false
        // Picking a stream directly is an explicit quality choice.
        qualitySelection = streamQualityLabel(stream)
        // A new stream is not a variant of the old master: clear the HLS
        // variant state so the picker re-resolves for this stream.
        hlsVariantSelection = null
        hlsQualityState = null
        pokeIndicator(
            PlayerIndicator.Message("Source: ${streamQualityLabel(stream) ?: stream.sourceName ?: "switched"}")
        )
        replaceStream(
            state.currentRequest.copy(
                streamUrl = stream.url,
                headers = stream.headers,
                mimeType = stream.mimeType,
                streamLabel = streamQualityLabel(stream),
                sourceName = stream.sourceName,
                hlsMasterUrl = null
            ),
            startAtMs = activePositionMs(),
            autoplay = true
        )
    }

    /**
     * Quality picker selection: null = Auto (best available quality),
     * otherwise the first stream carrying that quality label. Reuses
     * [switchSource] so position and headers carry over.
     */
    fun switchQuality(label: String?) {
        val target = if (label == null) qualityOptions.firstOrNull()?.second
        else qualityOptions.firstOrNull { it.first == label }?.second
        state.qualityOpen = false
        if (target != null) switchSource(target)
        qualitySelection = label
    }

    /**
     * HLS variant selection (Nuvio PlayerQualitySelector): null = Auto
     * (the original master playlist), otherwise the chosen variant's
     * sanitized temp playlist. Reuses [replaceStream] directly — the temp
     * file:// URL is not a playable URL for [switchSource]'s guard.
     * Position carries over; the master URL is kept on the request so
     * segment requests keep the stream's headers.
     */
    fun switchHlsVariant(variant: HlsQualityVariant?) {
        state.qualityOpen = false
        if (variant == null) {
            val master = hlsQualityState?.sourceUrl ?: return
            hlsVariantSelection = null
            qualitySelection = null
            replaceStream(
                state.currentRequest.copy(
                    streamUrl = master,
                    hlsMasterUrl = null,
                    streamLabel = qualityOptions.firstOrNull()?.first
                ),
                startAtMs = activePositionMs(),
                autoplay = true
            )
        } else {
            hlsVariantSelection = variant.id
            qualitySelection = variant.qualityName
            pokeIndicator(
                PlayerIndicator.Message("Quality: ${variant.displayLabel}")
            )
            replaceStream(
                state.currentRequest.copy(
                    streamUrl = variant.playbackUrl,
                    hlsMasterUrl = hlsQualityState?.sourceUrl,
                    streamLabel = variant.qualityName,
                    // Let the .m3u8 URL infer HLS instead of keeping the
                    // master stream's MIME hint.
                    mimeType = null
                ),
                startAtMs = activePositionMs(),
                autoplay = true
            )
        }
    }

    /**
     * Live TV channel zapping. The Live tab hands the visible channel list
     * over via [StreamRepository.setTvZapList]; zap targets wrap around.
     * M3U channels play directly (the URL is the stream); addon channels
     * re-resolve their stream first.
     */
    val zapChannels: List<TvChannelRef> = repository.pendingTvChannels

    fun canZap(): Boolean =
        state.currentRequest.isLive && zapChannels.size > 1

    fun zapTo(index: Int) {
        // #9: never start zap work on disposed state (ghost audio).
        if (disposed.get()) return
        val list = zapChannels
        if (list.size < 2) return
        val target = list[index.mod(list.size)]
        // Same channel tapped twice — nothing to do. M3U ids are URLs and
        // videoId holds the URL too, so this covers addon AND M3U channels:
        // double-tap CH+ must not rebuild the player (#19).
        if (target.metaId == state.currentRequest.videoId &&
            (state.currentRequest.liveChannel == null || state.currentRequest.liveChannel == target)) return
        preemptPendingLiveFallback()
        state.zapping = true
        ioScope.launch {
            try {
                val result = if (target.addonId == "m3u") {
                    com.aviostream.app.data.StreamResult(
                        listOf(com.aviostream.app.data.StreamItem(url = target.metaId)), emptyList())
                } else {
                    withTimeoutOrNull(20_000) {
                        repository.liveStreamResultsFlow(target)
                            .firstOrNull { it.streams.any { s -> com.aviostream.app.data.isPlayableLiveStreamUrl(s.url) } }
                    }
                }
                val playable = result?.streams.orEmpty().filter { com.aviostream.app.data.isPlayableLiveStreamUrl(it.url) }
                val stream = playable.firstOrNull()
                if (stream == null) {
                    withContext(Dispatchers.Main) {
                        pokeIndicator(PlayerIndicator.Message("No stream for ${target.name}"))
                    }
                    return@launch
                }
                repository.pendingTvIndex = list.indexOf(target)
                withContext(Dispatchers.Main) {
                    pokeIndicator(PlayerIndicator.Message(target.name))
                    replaceStream(
                        state.currentRequest.copy(
                            streamUrl = stream.url,
                            headers = stream.headers,
                            mimeType = stream.mimeType,
                            title = target.name,
                            poster = target.poster,
                            videoId = target.metaId,
                            type = target.type,
                            liveChannel = target,
                            streamLabel = "LIVE",
                            sourceName = stream.sourceName,
                            streams = playable,
                            subtitles = result?.subtitles.orEmpty()
                        ),
                        startAtMs = 0L,
                        autoplay = true
                    )
                }
            } finally {
                withContext(Dispatchers.Main) { state.zapping = false }
            }
        }
    }

    fun zapDelta(delta: Int) {
        val list = zapChannels
        if (list.size < 2) return
        zapTo(repository.pendingTvIndex + delta)
    }

    /** Quietly (re)fills the Sources list in the background, progressively. */
    fun refreshStreamsBackground(type: String, videoId: String) {
        state.streamsJob?.cancel()
        val requestAtStart = state.currentRequest
        val liveChannel = requestAtStart.liveChannel
        state.streamsJob = ioScope.launch {
            state.streamsLoading = true
            try {
                val results = if (liveChannel != null) repository.liveStreamResultsFlow(liveChannel)
                    else repository.streamResultsFlow(
                        type, videoId,
                        // CS3 title fallback needs the title: without it a
                        // non-IMDb id finds nothing here, while the detail
                        // page (which passes title+year) does — Sources
                        // would stay empty even though playback works.
                        title = requestAtStart.seriesName?.takeIf { it.isNotBlank() }
                            ?: requestAtStart.title.takeIf { it.isNotBlank() },
                        // Plugs (#22): a background re-fill, not a fresh
                        // open — it must never start fresh QuickJS runs.
                        includePlugs = false
                    )
                results.collect { result ->
                    if (result.streams.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (disposed.get() || state.currentRequest.type != type || state.currentRequest.videoId != videoId ||
                                state.currentRequest.liveChannel != liveChannel) return@withContext
                            val incoming = if (requestAtStart.isLive) result.streams.filter {
                                com.aviostream.app.data.isPlayableLiveStreamUrl(it.url)
                            } else result.streams
                            val complete = com.aviostream.app.ui.screens.player.mergePlayerSourceSet(
                                state.availableStreams, incoming,
                                includeHeadersInIdentity = requestAtStart.isLive && !isTv
                            )
                            // Preserve the original handoff while slow addons and debrid finish.
                            if (complete != state.availableStreams) {
                                state.availableStreams = complete
                                state.hiddenIncompatibleCount = 0
                            }
                        }
                    }
                }
            } finally {
                // NonCancellable: a replaced job must still clear the spinner.
                withContext(Dispatchers.Main + NonCancellable) { state.streamsLoading = false }
            }
        }
    }

    /** Awaits late Live candidates instead of abandoning fallback after an empty first emission. */
    fun awaitMobileLiveFallbackCandidate() {
        if (state.pendingLiveFallbackJob?.isActive == true) return
        val requestAtStart = state.currentRequest
        val playerAtStart = player
        val generation = liveFallbackGeneration.incrementAndGet()
        val liveChannel = requestAtStart.liveChannel
        val current = StreamItem(url = requestAtStart.streamUrl, headers = requestAtStart.headers)
        val job = ioScope.launch {
            var selected: StreamItem? = null
            try {
                withTimeoutOrNull(20_000L) {
                    val results = if (liveChannel != null) repository.liveStreamResultsFlow(liveChannel)
                        else repository.streamResultsFlow(requestAtStart.type, requestAtStart.videoId, includePlugs = false)
                    results.firstOrNull { result ->
                        selected = withContext(Dispatchers.Main) {
                            if (disposed.get() || liveFallbackGeneration.get() != generation ||
                                state.currentRequest != requestAtStart || player !== playerAtStart) return@withContext null
                            val incoming = result.streams.filter {
                                com.aviostream.app.data.isPlayableLiveStreamUrl(it.url)
                            }
                            if (incoming.isEmpty()) return@withContext null
                            val merged = com.aviostream.app.ui.screens.player.mergePlayerSourceSet(
                                state.availableStreams, incoming, includeHeadersInIdentity = true
                            )
                            if (merged != state.availableStreams) state.availableStreams = merged
                            com.aviostream.app.data.LivePlaybackPolicy.nextUntriedCandidate(
                                merged, current, state.attemptedLiveSourceIds
                            )?.also { candidate ->
                                state.attemptedLiveSourceIds = state.attemptedLiveSourceIds +
                                    com.aviostream.app.data.LivePlaybackPolicy.candidateIdentity(candidate)
                            }
                        }
                        selected != null
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A failed or completed source flow is equivalent to the bounded wait expiring.
            }
            withContext(Dispatchers.Main) {
                if (disposed.get() || liveFallbackGeneration.get() != generation ||
                    state.currentRequest != requestAtStart || player !== playerAtStart) return@withContext
                if (selected != null) {
                    switchSource(selected!!)
                } else {
                    state.playerError = "No other sources available"
                    pokeIndicator(PlayerIndicator.Message("No other sources available"))
                }
                state.pendingLiveFallbackJob = null
            }
        }
        state.pendingLiveFallbackJob = job
    }

    /** Re-prepares the current stream after a fatal error (#4). */
    fun retryCurrentStream() {
        if (disposed.get()) return
        state.playerError = null
        replaceStream(
            state.currentRequest,
            startAtMs = activePositionMs().coerceAtLeast(0L),
            autoplay = true,
            // Same source, not a source change: keep a self-heal downmix
            // that is already on, or the retry fails the same way again.
            preserveAudioHeal = true
        )
    }

    /**
     * Advances to the next available stream for this title (#4). When there is
     * nothing to advance to, says so and quietly refreshes the Sources list.
     */
    fun nextSource(automaticLiveFallback: Boolean = false) {
        val streams = state.availableStreams.filter { com.aviostream.app.data.isPlayableLiveStreamUrl(it.url) }
        val mobileLive = state.currentRequest.isLive && !isTv
        val next = if (automaticLiveFallback && mobileLive) {
            val current = StreamItem(url = state.currentRequest.streamUrl, headers = state.currentRequest.headers)
            state.attemptedLiveSourceIds = state.attemptedLiveSourceIds +
                com.aviostream.app.data.LivePlaybackPolicy.candidateIdentity(current)
            com.aviostream.app.data.LivePlaybackPolicy.nextUntriedCandidate(
                streams, current, state.attemptedLiveSourceIds
            )?.also { candidate ->
                state.attemptedLiveSourceIds = state.attemptedLiveSourceIds +
                    com.aviostream.app.data.LivePlaybackPolicy.candidateIdentity(candidate)
            }
        } else {
            val cur = streams.indexOfFirst {
                it.url == state.currentRequest.streamUrl && it.headers == state.currentRequest.headers
            }
            if (streams.size < 2) null else streams[(cur + 1).mod(streams.size)]
        }
        if (next == null) {
            if (automaticLiveFallback && mobileLive) {
                awaitMobileLiveFallbackCandidate()
                return
            }
            pokeIndicator(PlayerIndicator.Message("No other sources available"))
            refreshStreamsBackground(state.currentRequest.type, state.currentRequest.videoId)
            return
        }
        switchSource(next)
    }

    // Wire the auto-failover path now that the helpers exist.
    state.failoverToNextSource = { attempt ->
        val maxAttempts = if (state.currentRequest.isLive && !isTv)
            com.aviostream.app.data.LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS else settings.fallbackMaxSources
        pokeIndicator(PlayerIndicator.Message("Trying next source… ($attempt/$maxAttempts)"))
        nextSource(automaticLiveFallback = state.currentRequest.isLive && !isTv)
    }

    // A mobile Live source can stall in BUFFERING forever without producing a
    // Media3 error. Bound only first-start recovery: a source that has played
    // once, VOD, TV, or an intentionally paused player is never interrupted.
    LaunchedEffect(player, activeEngine, state.currentRequest, isTv) {
        if (!state.currentRequest.isLive || isTv) return@LaunchedEffect
        // Second engine: the Exo watchdogs never run on MPV playback.
        if (mpvActive()) return@LaunchedEffect
        val watchedPlayer = player
        var waitingSinceMs: Long? = null
        val attemptsAtStart = state.autoFailoverCount
        val replacementAtStart = replaceGen.get()
        var hasEverPlayed = watchedPlayer.isPlaying
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value || state.currentRequest != latestRequest.value) {
                return@LaunchedEffect
            }
            if (state.autoFailoverCount != attemptsAtStart || replaceGen.get() != replacementAtStart) {
                return@LaunchedEffect
            }
            hasEverPlayed = hasEverPlayed || watchedPlayer.isPlaying
            if (hasEverPlayed) return@LaunchedEffect
            val playbackState = watchedPlayer.playbackState
            val buffering = playbackState == Player.STATE_BUFFERING || playbackState == Player.STATE_IDLE
            val nowMs = System.nanoTime() / 1_000_000L
            waitingSinceMs = liveStartupWaitingSince(
                waitingSinceMs, nowMs, watchedPlayer.playWhenReady, buffering
            )
            val elapsedMs = waitingSinceMs?.let { nowMs - it } ?: 0L
            if (shouldFailoverStalledLiveStartup(
                    isLive = true,
                    isTv = false,
                    playWhenReady = watchedPlayer.playWhenReady,
                    hasEverPlayed = hasEverPlayed,
                    isBuffering = buffering,                    elapsedMs = elapsedMs,
                    // Step 40: configurable startup timeout (default 15s).
                    timeoutMs = settings.fallbackStartupTimeoutSeconds * 1000L
                )) {
                val maxAttempts = com.aviostream.app.data.LivePlaybackPolicy.MAX_MOBILE_LIVE_FAILOVERS
                if (state.autoFailoverCount < maxAttempts) {
                    state.autoFailoverCount++
                    com.aviostream.app.data.SourceDiagnostics.record("Live playback", "startup_timeout", elapsedMs)
                    state.failoverToNextSource?.invoke(state.autoFailoverCount)
                } else {
                    com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", 0)
                    state.playerError = "Live source did not start"
                }
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    // Recovery after first successful playback: the startup watchdog above deliberately
    // stops once a stream has played. A separate bounded watchdog handles streams that
    // subsequently stall without a Media3 error. It never acts while paused or seeking.
    LaunchedEffect(player, activeEngine, state.currentRequest, isTv) {
        if (!state.currentRequest.isLive || isTv) return@LaunchedEffect
        // Second engine: the Exo watchdogs never run on MPV playback.
        if (mpvActive()) return@LaunchedEffect
        val watchedPlayer = player
        var hasPlayed = watchedPlayer.isPlaying
        var rebufferSinceMs: Long? = null
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value || state.currentRequest != latestRequest.value) {
                return@LaunchedEffect
            }
            hasPlayed = hasPlayed || watchedPlayer.isPlaying
            if (!hasPlayed || !watchedPlayer.playWhenReady || watchedPlayer.playbackState != Player.STATE_BUFFERING) {
                rebufferSinceMs = null
            } else {
                val nowMs = System.nanoTime() / 1_000_000L
                rebufferSinceMs = rebufferSinceMs ?: nowMs
                if (com.aviostream.app.ui.screens.player.shouldFailoverStalledLiveRebuffer(
                        isLive = state.currentRequest.isLive,
                        isTv = isTv,
                        playWhenReady = watchedPlayer.playWhenReady,
                        hasEverPlayed = hasPlayed,
                        isBuffering = watchedPlayer.playbackState == Player.STATE_BUFFERING,                        elapsedMs = nowMs - rebufferSinceMs!!
                    )) {
                    val maxRecoveries = 2
                    if (state.liveRebufferRecoveries < maxRecoveries) {
                        state.liveRebufferRecoveries++
                        com.aviostream.app.data.SourceDiagnostics.record("Live playback", "rebuffer_timeout", nowMs - rebufferSinceMs!!)
                        pokeIndicator(PlayerIndicator.Message("Live stream stalled — trying another source…"))
                        nextSource(automaticLiveFallback = true)
                    } else {
                        com.aviostream.app.data.SourceDiagnostics.record("Live playback", "fallback_exhausted", nowMs - rebufferSinceMs!!)
                        state.playerError = "Live stream stopped responding. Try Retry or choose another source."
                    }
                    return@LaunchedEffect
                }
            }
            delay(500)
        }
    }

    // VOD stall watchdog (owner's player round, 2026-10-23): a movie/episode
    // source that never starts, or freezes mid-play with no position
    // progress — hosts that ignore byte-range requests behave exactly like
    // this — fails over to the next source instead of spinning forever.
    // Bounded by the generic failover cap (Settings > Playback > Source
    // failover); a mid-play stall first retries the same source once
    // (Step 40, Nuvio PlaybackStallResume).
    LaunchedEffect(player, activeEngine, state.currentRequest, isTv) {
        if (state.currentRequest.isLive) return@LaunchedEffect
        // Second engine: the Exo watchdogs never run on MPV playback.
        if (mpvActive()) return@LaunchedEffect
        val watchedPlayer = player
        val replacementAtStart = replaceGen.get()
        var hasPlayed = watchedPlayer.isPlaying
        var stallSinceMs: Long? = null
        var lastPositionMs = -1L
        var lastProgressAtMs = 0L
        while (true) {
            if (disposed.get() || watchedPlayer !== latestPlayer.value ||
                state.currentRequest != latestRequest.value || replaceGen.get() != replacementAtStart
            ) {
                return@LaunchedEffect
            }
            hasPlayed = hasPlayed || watchedPlayer.isPlaying
            val nowMs = System.nanoTime() / 1_000_000L
            val posMs = runCatching { watchedPlayer.currentPosition }.getOrDefault(0L)
            if (lastPositionMs < 0L || posMs > lastPositionMs + 1_000L) {
                lastPositionMs = posMs
                lastProgressAtMs = nowMs
            }
            val buffering = watchedPlayer.playbackState == Player.STATE_BUFFERING ||
                watchedPlayer.playbackState == Player.STATE_IDLE
            val progressed = nowMs - lastProgressAtMs < 3_000L
            stallSinceMs = if (watchedPlayer.playWhenReady && buffering && !progressed) {
                stallSinceMs ?: nowMs
            } else null
            val elapsedMs = stallSinceMs?.let { nowMs - it } ?: 0L
            if (com.aviostream.app.ui.screens.player.shouldFailoverStalledVod(
                    isLive = false,
                    playWhenReady = watchedPlayer.playWhenReady,
                    hasEverPlayed = hasPlayed,
                    isBuffering = buffering,                    positionProgressed = progressed,
                    elapsedMs = elapsedMs,
                    // Step 40: configurable startup timeout (default 15s);
                    // the mid-play rebuffer timeout stays 30s.
                    startupTimeoutMs = settings.fallbackStartupTimeoutSeconds * 1000L
                )) {
                if (hasPlayed && state.vodStallResumeAttemptedFor != state.currentRequest.streamUrl) {
                    // Step 40 (Nuvio PlaybackStallResume): a source that
                    // played and then stalled gets one reopen at its current
                    // position before we switch to the next source.
                    state.vodStallResumeAttemptedFor = state.currentRequest.streamUrl
                    com.aviostream.app.data.SourceDiagnostics.record("VOD playback", "stall_resume", elapsedMs)
                    pokeIndicator(PlayerIndicator.Message("Stream stalled — retrying this source…"))
                    retryCurrentStream()
                } else if (state.autoFailoverCount < settings.fallbackMaxSources) {
                    state.autoFailoverCount++
                    com.aviostream.app.data.SourceDiagnostics.record("VOD playback", "stall_timeout", elapsedMs)
                    state.failoverToNextSource?.invoke(state.autoFailoverCount)
                } else {
                    com.aviostream.app.data.SourceDiagnostics.record("VOD playback", "fallback_exhausted", elapsedMs)
                    state.playerError = "Stream stalled. Try Retry or choose another source."
                }
                return@LaunchedEffect
            }
            delay(500)
        }
    }

    /** Play [video] with a caller-picked [stream] (per-episode stream choice, Nuvio PlayerEpisodesPanel). */
    fun playEpisodeWithStream(
        video: Video,
        stream: com.aviostream.app.data.StreamItem,
        playableStreams: List<com.aviostream.app.data.StreamItem>,
        subtitles: List<Subtitle>
    ) {
        // #9: never start episode work on disposed state (ghost audio).
        if (disposed.get()) return
        state.episodesOpen = false
        state.episodeStreamsVideo = null
        state.switchingMedia = true
        val showName = state.currentRequest.seriesName ?: state.currentRequest.title
        state.availableStreams = playableStreams
        state.hiddenIncompatibleCount = 0
        replaceStream(
            state.currentRequest.copy(
                streamUrl = stream.url,
                headers = stream.headers,
                mimeType = stream.mimeType,
                title = "$showName S${video.season}E${video.episode}",
                poster = video.thumbnail ?: state.currentRequest.poster,
                videoId = video.id,
                subtitles = subtitles,
                season = video.season,
                episode = video.episode,
                episodeTitle = video.title,
                streamLabel = streamQualityLabel(stream),
                sourceName = stream.sourceName,
                streams = playableStreams
            ),
            startAtMs = 0L,
            autoplay = true
        )
        state.switchingMedia = false
    }

    fun playEpisode(video: Video) {
        // #9: never start episode work on disposed state (ghost audio).
        if (disposed.get()) return
        state.episodesOpen = false
        state.switchingMedia = true
        // Pre-resolved while the countdown card showed: skip the fetch.
        val prefetched = state.prefetchedNextStreams?.takeIf { it.first == video.id }?.second
        state.prefetchedNextStreams = null
        ioScope.launch {
            val type = state.currentRequest.type
            val result = prefetched ?: withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(type, video.id, includePlugs = false).firstOrNull { it.streams.any { stream -> com.aviostream.app.data.isPlayableLiveStreamUrl(stream.url) } }
            }
            // Back to the main thread: everything below touches Compose state.
            withContext(Dispatchers.Main) {
                val (playableStreams, hidden) = result?.streams?.let { ( it to 0 ) } ?: (emptyList<com.aviostream.app.data.StreamItem>() to 0)
                // Step 60: next-episode autoplay stays on the same release
                // group — the group saved when the current episode's
                // stream started playing, plus the expected next
                // filename built from the saved filename. Falls back to
                // the first playable stream when nothing matches.
                val preferredGroup = if (settings.autoplayPreferBingeGroup) {
                    bingeGroupCache.get(state.currentRequest.metaId)
                } else null
                val preferredFilename = preferredGroup?.let {
                    bingeGroupCache.getFilename(state.currentRequest.metaId)?.let { saved ->
                        FilenameEpisodeMatcher.buildNextFilename(
                            saved,
                            state.currentRequest.season.takeIf { it > 0 },
                            state.currentRequest.episode,
                            video.season.takeIf { it > 0 },
                            video.episode
                        )
                    }
                }
                val stream = pickAutoplayStream(
                    playableStreams.filter { com.aviostream.app.data.isPlayableLiveStreamUrl(it.url) },
                    settings, isEpisode = true,
                    preferredBingeGroup = preferredGroup,
                    preferredFilename = preferredFilename
                ) ?: playableStreams.firstOrNull { com.aviostream.app.data.isPlayableLiveStreamUrl(it.url) }
                if (stream == null) {
                    pokeIndicator(PlayerIndicator.Message("No sources found for this episode"))
                } else {
                    playEpisodeWithStream(video, stream, playableStreams, result?.subtitles ?: emptyList())
                }
            }
        }
    }

    // Auto-select a freshly added subtitle track once ExoPlayer exposes it.
    LaunchedEffect(state.addedSubs.size) {
        // Second engine: the attach effect sub-adds into MPV instead.
        if (mpvActive()) return@LaunchedEffect
        val sub = state.addedSubs.lastOrNull() ?: return@LaunchedEffect
        repeat(15) {
            val group = player.currentTracks.groups.firstOrNull { g ->
                g.type == C.TRACK_TYPE_TEXT &&
                    (0 until g.length).any { i -> g.getTrackFormat(i).id == sub.url }
            }
            if (group != null) {
                val idx = (0 until group.length).first { i -> group.getTrackFormat(i).id == sub.url }
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, listOf(idx)))
                    .build()
                state.trackTick++
                return@LaunchedEffect
            }
            delay(300)
        }
    }

    /**
     * Full gesture set for the video surface in ONE pointerInput handler:
     * tap (toggle controls), double-tap (skip by the Skip seconds
     * setting), directional drags (seek / volume / brightness) and
     * hold-for-speed with slide steps. Touch gestures off = tap-only:
     * drags swallow (mode 3) and the double-tap seek is disabled;
     * swipe-to-seek off = horizontal drags swallow, volume/brightness
     * drags still work.
     *
     * v0.12.1 regression fix: v0.12.0 stacked THREE separate detectors
     * (tap + a second detectTapGestures for the hold + drag) on this same
     * node. Concurrent detectors share PointerInputChange consumption, so
     * the tap detector's wait-for-up was cancelled by its siblings on every
     * tap — controls never appeared and every control (incl. Back) was
     * unreachable; only the hold still fired. A single unified state
     * machine removes that whole class of interference: nothing else on
     * this node consumes pointer changes.
     *
     * Hold-for-speed (GitHub issue #2 + owner's slide extension): a press
     * held ~350ms without dragging engages 2x; while still holding, each
     * ~60dp of rightward slide steps up (2x → 3x → 4x → max 5x), sliding
     * left steps back down (floor 2x). Release restores the exact speed
     * that was playing before the hold. Disabled while locked (tap only
     * shows the locked hint, as before).
     */
    fun Modifier.videoGestures(): Modifier = this.pointerInput(player, state.locked) {
        if (state.locked) {
            // Nuvio legacy: a tap while locked reveals the locked overlay
            // (which auto-hides again after 3.5s).
            detectTapGestures(onTap = { state.lockedOverlayVisible = true })
            return@pointerInput
        }
        var lastTapUpMs = 0L
        var pendingToggle: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown()
            val downPos = down.position
            val downTime = down.uptimeMillis
            val slop = viewConfiguration.touchSlop
            val stepPx = 60.dp.toPx()
            val holdThresholdMs = 350L
            var lastEventMs = downTime
            var lastPos = downPos
            var accX = 0f
            var accY = 0f
            // Drag state (same semantics as the old drag detector):
            // -1 undecided, 0 = seek, 1 = volume, 2 = brightness.
            var dragMode = -1
            // Pinch zoom interrupts any drag/hold: the gesture ends
            // without settling (Nuvio interruptedByPinch).
            var pinchInterrupted = false
            var seekBase = 0L
            var seekTarget = -1L
            var volBase = 0f
            var brightBase = 0.5f
            // Hold state.
            var holdEngaged = false
            var prevSpeed = 1f
            var slideBaseX = 0f
            var upChange: androidx.compose.ui.input.pointer.PointerInputChange? = null

            while (upChange == null) {
                val event = if (!holdEngaged && dragMode == -1 && settings.touchGesturesEnabled && !pinchInterrupted) {
                    // Still a tap/hold candidate: wake up at the hold
                    // threshold even if no further pointer event arrives.
                    // (Gestures off: block on the next event instead — the
                    // threshold must not spin the loop.)
                    //
                    // Once a pinch has started, take the blocking await
                    // instead: with two fingers the hold never engages,
                    // so a reached threshold would return null WITHOUT
                    // suspending and the loop would spin on the Main
                    // thread forever (UI freeze / ANR — the 2-finger-hold
                    // crash, user 2026-11-23; the pinch crash was this
                    // too, not the zoom: every pinch holds two fingers
                    // down past the threshold).
                    val remaining = (downTime + holdThresholdMs - lastEventMs).coerceAtLeast(0L)
                    if (remaining > 0L) {
                        withTimeoutOrNull(remaining) { awaitPointerEvent() }
                    } else {
                        // Threshold already reached (the last event arrived
                        // at/past it): treat the timeout as fired. Never
                        // withTimeoutOrNull(0) — a 0ms timeout races the
                        // event dispatch and double-resumes the
                        // continuation (IllegalStateException: Already
                        // resumed — crash, PlayerScreen.kt:2595).
                        null
                    }
                } else {
                    awaitPointerEvent()
                }
                if (event == null) {
                    // Threshold reached with the finger still down and no
                    // drag in progress: engage the speed hold at the Hold
                    // speed setting (default 2x) — unless this gesture
                    // already became a pinch (two fingers down). A slow
                    // pinch held still used to hit this timeout mid-pinch
                    // and flip to 2x, and the speed then stuck there.
                    if (!pinchInterrupted) {
                        holdEngaged = true
                        prevSpeed = if (mpvActive()) mpvEngine?.snapshot?.value?.speed ?: state.speed else player.playbackParameters.speed
                        slideBaseX = accX
                        state.holdSpeedStep = settings.holdSpeedValue
                        runCatching { activeSetSpeed(settings.holdSpeedValue) }
                        state.holdSpeedActive = true
                    }
                    continue
                }
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                lastEventMs = change.uptimeMillis
                // Pinch zoom (Nuvio playerSurfacePinchGestures, folded
                // into this one state machine so consumption stays
                // single-owner): a second finger abandons any drag/hold
                // and zooms the video 0.8–2.0x, snapped to 1.0x within
                // 0.04. The gesture ends without settling when a finger
                // lifts.
                if (event.changes.count { it.pressed } > 1) {
                    if (holdEngaged) {
                        holdEngaged = false
                        state.holdSpeedActive = false
                        runCatching { activeSetSpeed(prevSpeed) }
                    }
                    pinchInterrupted = true
                    val zoom = event.calculateZoom()
                    if (zoom != 1f && zoom > 0f) {
                        state.rawVideoZoom = (state.rawVideoZoom * zoom).coerceIn(0.8f, 2.0f)
                        val next = if (abs(state.rawVideoZoom - 1f) < 0.04f) 1f else state.rawVideoZoom
                        if (next != state.videoZoom) {
                            state.videoZoom = next
                            pokeIndicator(PlayerIndicator.Message("${(next * 100f).roundToInt()}%"))
                        }
                    }
                    event.changes.forEach { it.consume() }
                    continue
                }
                if (pinchInterrupted) {
                    // A finger lifted after pinching: end without settling.
                    upChange = change
                    break
                }
                if (!change.pressed) {
                    // Pointer went up (Compose 1.7 no longer distinguishes
                    // a system cancel here — it also arrives as !pressed).
                    upChange = change
                    break
                }
                val delta = change.position - lastPos
                lastPos = change.position
                accX += delta.x
                accY += delta.y
                if (holdEngaged) {
                    // Slide right = step up 1x per ~60dp from the hold
                    // base (floor = base, max 5x); left steps back down.
                    val step = (settings.holdSpeedValue +
                        ((accX - slideBaseX) / stepPx).toInt().toFloat())
                        .coerceIn(settings.holdSpeedValue, 5f)
                    if (step != state.holdSpeedStep) {
                        state.holdSpeedStep = step
                        runCatching { activeSetSpeed(step) }
                    }
                } else {
                    if (dragMode == -1 && (abs(accX) > slop || abs(accY) > slop)) {
                        // Gestures off (or swipe-to-seek off for a horizontal
                        // drag): swallow the drag — mode 3 settles as a
                        // no-op, never a seek/volume/brightness change and
                        // never a tap.
                        // Nuvio zones: left <40% brightness, right >60%
                        // volume, centre seek-only; the top/bottom 25%
                        // bars vertical gestures, and the 24dp side edges
                        // bar horizontal seek (system back zone).
                        val edgePx = 24.dp.toPx()
                        val inTopBottomEdge = downPos.y <= size.height * 0.25f ||
                            downPos.y >= size.height * 0.75f
                        val inSideEdge = downPos.x <= edgePx ||
                            downPos.x >= size.width - edgePx
                        dragMode = if (!settings.touchGesturesEnabled) 3
                        else if (abs(accX) > abs(accY)) {
                            if (settings.swipeToSeekEnabled && !inSideEdge) 0 else 3
                        } else if (inTopBottomEdge) 3
                        else if (downPos.x < size.width * 0.4f) 2
                        else if (downPos.x > size.width * 0.6f) 1
                        else 3
                        seekBase = activePositionMs()
                        seekTarget = -1L
                        volBase = displayVolumePercent().toFloat()
                        brightBase = activity?.window?.attributes
                            ?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                    }
                    if (dragMode != -1) {
                        change.consume()
                        when (dragMode) {
                            0 -> { // horizontal swipe: tiered seek (Nuvio:
                                // ≥60min → 120s, ≥30min → 90s, else 60s
                                // per full-width swipe).
                                val dur = activeDurationMs()
                                if (dur > 0) {
                                    val sensitivitySeconds = when {
                                        dur >= 3_600_000L -> 120f
                                        dur >= 1_800_000L -> 90f
                                        else -> 60f
                                    }
                                    seekTarget = (seekBase + accX / size.width * sensitivitySeconds * 1000f)
                                        .toLong().coerceIn(0L, dur)
                                    val deltaMs = seekTarget - activePositionMs()
                                    pokeIndicator(
                                        PlayerIndicator.Seek(
                                            (if (deltaMs >= 0) "+" else "−") + formatMs(abs(deltaMs))
                                        )
                                    )
                                }
                            }
                            1 -> { // vertical swipe, right half: volume (+ boost past max)
                                // Total range 0..200%: 0..100 drives the system
                                // volume; past 100 the system stays at max and
                                // the boost takes over. Swiping back down drains
                                // the boost first, then the system volume.
                                val total = (volBase - accY / size.height * 200f)
                                    .coerceIn(0f, 200f)
                                if (total >= 100f) {
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, maxVolume, 0
                                    )
                                    state.volumeBoost = (total - 100f) / 100f
                                } else {
                                    state.volumeBoost = 0f
                                    val v = (total / 100f * maxVolume)
                                        .roundToInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC, v, 0
                                    )
                                }
                                applyVolumeBoost()
                                state.volumePercent = displayVolumePercent()
                                // Second engine: the gesture also drives
                                // libmpv's own fine volume (0..160%).
                                if (mpvActive()) mpvEngine?.setVolumeFine(state.volumePercent.toFloat())
                                pokeIndicator(PlayerIndicator.Volume(state.volumePercent))
                            }
                            2 -> { // vertical swipe, left half: brightness
                                val b = (brightBase - accY / size.height).coerceIn(0.01f, 1f)
                                activity?.window?.let { w ->
                                    val lp = w.attributes
                                    lp.screenBrightness = b
                                    w.attributes = lp
                                }
                                pokeIndicator(PlayerIndicator.Brightness((b * 100).toInt()))
                            }
                        }
                    }
                }
            }

            // Safety net: a hold that engaged before this gesture became a
            // pinch is already restored by the pinch branch — but if one is
            // still engaged when a pinched gesture ends, restore the speed
            // here instead of leaving 2x stuck (the pinch path settles
            // nothing).
            if (pinchInterrupted && holdEngaged) {
                state.holdSpeedActive = false
                runCatching { activeSetSpeed(prevSpeed) }
            }
            // Gesture finished — settle the outcome. A pinch-interrupted
            // gesture settles nothing (Nuvio interruptedByPinch).
            if (!pinchInterrupted) when {
                holdEngaged -> {
                    state.holdSpeedActive = false
                    runCatching { activeSetSpeed(prevSpeed) }
                }
                dragMode == 0 -> {
                    if (seekTarget >= 0) {
                        hapticTick()
                        activeSeekTo(seekTarget)
                    }
                }
                dragMode == -1 -> {
                    // A tap: a second tap inside the double-tap window
                    // seeks by the Skip seconds setting (accumulating);
                    // centre double-tap toggles the controls. A lone tap
                    // toggles the controls once the window expires (same
                    // timing as the old detectTapGestures behaviour).
                    // Gestures off: no double-tap seek, tap only.
                    val now = upChange?.uptimeMillis ?: downTime
                    val window = viewConfiguration.doubleTapTimeoutMillis
                    if (lastTapUpMs != 0L && now - lastTapUpMs <= window && settings.touchGesturesEnabled) {
                        pendingToggle?.cancel()
                        pendingToggle = null
                        lastTapUpMs = 0L
                        // Nuvio zones: <40% back, >60% forward, centre toggles.
                        when {
                            downPos.x < size.width * 0.4f -> doubleTapSeek(-1)
                            downPos.x > size.width * 0.6f -> doubleTapSeek(1)
                            else -> toggleControls()
                        }
                    } else {
                        lastTapUpMs = now
                        pendingToggle = gestureUiScope.launch {
                            delay(window.toLong())
                            toggleControls()
                        }
                    }
                }
            }
        }
    }

    /** Tap-only gestures for the controls overlay (slider drags stay clean). */
    fun Modifier.tapGestures(): Modifier = this.pointerInput(player, state.locked) {
        detectTapGestures(
            onTap = {
                if (state.locked) pokeIndicator(PlayerIndicator.Message("Locked — tap the lock icon to unlock"))
                else toggleControls()
            },
            onDoubleTap = { offset ->
                if (!state.locked && settings.touchGesturesEnabled) {
                    // Nuvio zones: <40% back, >60% forward, centre toggles.
                    when {
                        offset.x < size.width * 0.4f -> doubleTapSeek(-1)
                        offset.x > size.width * 0.6f -> doubleTapSeek(1)
                        else -> toggleControls()
                    }
                }
            }
        )
    }

    fun savePosition() {
        val req = latestRequest.value
        // #8: live TV never enters Continue Watching.
        if (req.isLive) return
        // Trailers never enter Continue Watching either.
        if (req.isTrailer) return
        val pos = activePositionMs()
        val dur = activeDurationMs().takeIf { it > 0 } ?: C.TIME_UNSET
        // NonCancellable: onDispose cancels ioScope's children (#9) and must
        // not kill this final save.
        ioScope.launch(NonCancellable) {
            repository.savePlayback(
                title = req.title,
                poster = req.poster,
                videoId = req.videoId,
                streamUrl = req.streamUrl,
                positionMs = pos,                durationMs = if (dur == C.TIME_UNSET) 0L else dur,                imdbId = req.imdbId,
                season = req.season,
                episode = req.episode
            )
            // CW widget (v0.14.30 port, feature #19): the row just changed.
            CwWidgetUpdater.refresh(context)
        }
    }


    // Countdown driver: 10…1, one tick a second, then play the next
    // episode. Cancels when the card closes (dismiss, seek-back, new
    // episode). The advance marks the episode handled so the silent
    // end-of-playback block never fires for it too.
    LaunchedEffect(state.nextEpisodeCard) {
        val next = state.nextEpisodeCard ?: return@LaunchedEffect
        for (sec in NEXT_EPISODE_COUNTDOWN_SECONDS downTo 1) {
            state.nextEpisodeCountdown = sec
            delay(1000)
        }
        state.nextEpisodeCountdown = null
        state.nextEpisodeCard = null
        state.endedHandledFor = state.currentRequest.videoId
        if (state.sleepAtEpisodeEnd) {
            // Sleep timer armed while the card was showing: don't advance —
            // the episode plays to its natural end, where the sleep stop fires.
            return@LaunchedEffect
        }
        playEpisode(next)
    }

    // Sleep timer ticker (v0.14.30 port): counts the deadline down for the
    // chip; at zero it pauses and clears itself.
    LaunchedEffect(state.sleepDeadlineMs) {
        if (state.sleepDeadlineMs <= 0L) {
            state.sleepRemainingSec = 0
            return@LaunchedEffect
        }
        while (true) {
            val leftMs = state.sleepDeadlineMs - System.currentTimeMillis()
            if (leftMs <= 0L) {
                state.sleepDeadlineMs = 0L
                state.sleepRemainingSec = 0
                state.sleepChosenMinutes = 0
                activePause()
                // Release the screen hold with the pause — the PlayerView's
                // keepScreenOn is set once at creation and nothing else ever
                // clears it, so a timer pause used to leave the screen lit.
                state.sleepScreenReleased = true
                state.playerView?.keepScreenOn = false
                pokeIndicator(PlayerIndicator.Message("Sleep timer — playback paused"))
                break
            }
            state.sleepRemainingSec = ((leftMs + 999L) / 1000L).toInt()
            delay(500)
        }
    }

    // When playback resumes after a sleep-timer pause, restore the
    // Keep-screen-on toggle's hold exactly as the factory set it — the
    // release above belongs to the sleep stop only.
    LaunchedEffect(state.isPlaying) {
        if (state.isPlaying && state.sleepScreenReleased) {
            state.sleepScreenReleased = false
            state.playerView?.keepScreenOn = settings.keepScreenOn
        }
    }

    // Movie recommendations auto-hide 10s after appearing while the
    // controls are hidden; showing the controls brings them back.
    LaunchedEffect(state.movieRecs, state.showControls) {
        if (state.movieRecs.isNotEmpty() && !state.showControls) {
            delay(10_000)
            state.movieRecsAutoHidden = true
        } else {
            state.movieRecsAutoHidden = false
        }
    }

    // Pinch zoom resets whenever a new title/episode starts.
    LaunchedEffect(state.currentRequest.videoId) {
        state.videoZoom = 1f
        state.rawVideoZoom = 1f
    }

    // Skip-button auto-hide (Nuvio SkipIntroButton): the 10s countdown
    // runs only while the controls are hidden; showing them pauses it
    // and brings the button back. Dismissal and the countdown reset
    // whenever the active segment changes.
    LaunchedEffect(state.activeSegment?.type, state.activeSegment?.startMs, state.activeSegment?.endMs) {
        state.skipDismissed = false
        state.skipAutoHidden = false
        skipProgress.snapTo(0f)
    }
    LaunchedEffect(state.activeSegment?.type, state.activeSegment?.startMs, state.activeSegment?.endMs, state.showControls, state.skipDismissed) {
        val seg = state.activeSegment
        if (seg != null && !state.skipDismissed && !state.showControls && !state.skipAutoHidden) {
            skipProgress.animateTo(
                1f,
                tween(
                    durationMillis = ((1f - skipProgress.value) * 10_000).toInt().coerceAtLeast(1),
                    easing = LinearEasing,
                ),
            )
            state.skipAutoHidden = true
        }
    }

    // End-of-play rating prompt (v0.14.30 port): offer the rating card
    // once per video when it ends — unless already rated locally or
    // skipped twice for this title/episode. Ratings live in the fork's
    // UserRatings (1–5 stars, keyed by meta id); the prompt key is per
    // title (movies) or per episode (series).
    fun maybeOfferRatingPrompt(req: PlaybackRequest) {
        if (req.isTrailer || req.isLive) return
        val metaId = req.metaId.takeIf { it.isNotBlank() } ?: return
        if (userRatings.getRating(metaId) != null) return
        val promptKey = if (req.isMovie) metaId
            else "$metaId:S${req.season}E${req.episode}"
        if (userRatings.promptSkipCount(promptKey) >= 2) return
        state.ratingPrompt = EndRatingPrompt(
            metaId = metaId,
            promptKey = promptKey,
            type = req.type,
            imdbId = req.imdbId?.takeIf { it.isNotBlank() },
            title = req.title,
            forVideoId = req.videoId
        )
    }

    // Rates from the end-of-play prompt: local 1–5 stars first (always
    // sticks), then the Trakt push (1–10 scale, stars × 2) when Trakt is
    // linked and the title has an IMDb id. Returns the line the card shows.
    suspend fun rateFromPrompt(prompt: EndRatingPrompt, stars: Int): String {
        userRatings.setRating(prompt.metaId, stars)
        val imdbId = prompt.imdbId
            ?: return "Saved on this device · Trakt needs an IMDb id."
        val token = runCatching { TraktManager(settings).validAccessToken() }.getOrNull()
            ?: return "Saved on this device · Trakt isn't linked."
        val ok = runCatching {
            TraktApi().setRating(token, prompt.type, imdbId, prompt.title, (stars * 2).coerceIn(1, 10))
        }.getOrDefault(false)
        return if (ok) "Rating saved · Synced to Trakt"
        else "Saved on this device · Couldn't reach Trakt. Try again."
    }

    // The rating prompt belongs to the video whose end raised it — when
    // playback moves on (e.g. auto-advance), retire it.
    LaunchedEffect(state.currentRequest) {
        if (state.ratingPrompt?.forVideoId != state.currentRequest.videoId) {
            state.ratingPrompt = null
        }
    }

    LaunchedEffect(player) {
        // Previous tick's position, for manual-seek detection (a jump
        // over a segment marks it auto-skipped, Nuvio
        // intervalsAtSeekPositions).
        var lastPollPositionMs = -1L
        while (true) {
            val c = player
            val snap = if (mpvActive()) mpvEngine?.snapshot?.value else null
            val ended = activeEnded()
            state.positionMs = snap?.positionMs ?: c.currentPosition
            state.durationMs = snap?.durationMs ?: (c.duration.takeIf { it > 0 } ?: 0L)
            state.bufferedMs = activeBufferedMs()
            state.isPlaying = snap?.let { !it.paused } ?: c.isPlaying
            if (state.isPlaying) hasStartedPlayback = true
            state.isBuffering = snap?.buffering ?: (c.playbackState == Player.STATE_BUFFERING ||
                c.playbackState == Player.STATE_IDLE)
            // Step 59: the seek-preview prefetch pauses while buffering.
            seekPreviewController?.playbackBuffering = state.isBuffering
            state.loadingStep = when {
                state.switchingMedia -> "Opening source…"
                snap != null -> if (snap.fileLoaded) "Buffering…" else "Opening source…"
                c.playbackState == Player.STATE_IDLE -> "Opening source…"
                c.currentTracks.groups.isEmpty() -> "Reading tracks…"
                else -> "Buffering…"
            }
            state.volumePercent = displayVolumePercent()
            // A rebuilt player (source/episode switch) has a new audio
            // session — re-attach the boost enhancer to it.
            if (state.volumeBoost > 0f) applyVolumeBoost()
            // A manual seek over a segment marks it auto-skipped, so
            // seeking back into it doesn't instantly re-skip.
            if (lastPollPositionMs >= 0 && kotlin.math.abs(state.positionMs - lastPollPositionMs) > 3000) {
                val from = minOf(lastPollPositionMs, state.positionMs)
                val to = maxOf(lastPollPositionMs, state.positionMs)
                val crossed = skipSegments.filter { seg -> seg.startMs < to && seg.endMs > from }
                if (crossed.isNotEmpty()) state.autoSkippedSegments = state.autoSkippedSegments + crossed
            }
            lastPollPositionMs = state.positionMs
            state.activeSegment = if (settings.showSkipButton) {
                val offset = settings.skipIntroOffsetSeconds
                skipSegments.firstOrNull {
                    state.positionMs >= it.buttonStartMs(offset) && state.positionMs <= it.endMs
                }
            } else null
            // Auto-skip (Nuvio PlayerScreenRuntimeEffects): when the
            // playhead enters an auto-skip segment, jump to its end.
            // Fires once per segment per title.
            if (state.isPlaying && settings.showSkipButton) {
                val autoTypes = settings.autoSkipSegmentTypes
                if (autoTypes.isNotEmpty()) {
                    val offset = settings.skipIntroOffsetSeconds
                    val target = skipSegments.firstOrNull { seg ->
                        seg.type.autoSkipStoredValue() in autoTypes &&
                            seg !in state.autoSkippedSegments &&
                            state.positionMs >= seg.buttonStartMs(offset) && state.positionMs < seg.endMs
                    }
                    if (target != null) {
                        state.autoSkippedSegments = state.autoSkippedSegments + target
                        val seekTo = if (state.durationMs > 0) {
                            (target.endMs + 400).coerceAtMost(state.durationMs - 1)
                        } else {
                            target.endMs + 400
                        }
                        if (seekTo > state.positionMs) {
                            activeSeekTo(seekTo)
                            val label = target.type.buttonLabel()
                                .removePrefix("Skip ").replaceFirstChar { it.lowercase() }
                            pokeIndicator(PlayerIndicator.Message("Skipped $label"))
                        }
                    }
                }
            }
            // Next-episode countdown card (Nuvio NextEpisodeCard): when
            // the threshold is crossed, the card appears and counts down
            // instead of the silent immediate advance. Seeking back below
            // the threshold hides it again.
            val nextEp = if (
                settings.autoPlayNext &&
                !state.currentRequest.isMovie &&
                !state.currentRequest.isTrailer &&
                state.currentRequest.episodes.isNotEmpty()
            ) {
                resolveNextEpisode(
                    state.currentRequest.episodes,
                    state.currentRequest.season,
                    state.currentRequest.episode,
                    state.currentRequest.videoId,
                    settings.shuffleEpisodes
                )
            } else null
            if (nextEp != null && state.nextEpisodeCard == null &&
                !state.sleepAtEpisodeEnd &&
                state.nextEpisodeDismissedFor != state.currentRequest.videoId &&
                shouldShowNextEpisodeCard(
                    state.positionMs, state.durationMs, skipSegments,
                    settings.nextEpisodeThresholdMode,
                    settings.nextEpisodeThresholdPercent,
                    settings.nextEpisodeThresholdMinutes
                )
            ) {
                state.nextEpisodeCard = nextEp
                // Pre-resolve the next episode's sources while the 3s
                // countdown runs (Nuvio preloadNextEpisodeSources), so
                // Play now / auto-advance plays instantly instead of
                // fetching after the countdown ends.
                if (state.prefetchingNextId != nextEp.id && state.prefetchedNextStreams?.first != nextEp.id) {
                    state.prefetchingNextId = nextEp.id
                    val nextType = state.currentRequest.type
                    ioScope.launch {
                        val res = withTimeoutOrNull(20_000) {
                            repository.streamResultsFlow(nextType, nextEp.id, includePlugs = false)
                                .firstOrNull { it.streams.any { s -> com.aviostream.app.data.isPlayableLiveStreamUrl(s.url) } }
                        }
                        withContext(Dispatchers.Main) {
                            state.prefetchingNextId = null
                            if (res != null) state.prefetchedNextStreams = nextEp.id to res
                        }
                    }
                }
            } else if (state.nextEpisodeCard != null && !shouldShowNextEpisodeCard(
                    state.positionMs, state.durationMs, skipSegments,
                    settings.nextEpisodeThresholdMode,
                    settings.nextEpisodeThresholdPercent,
                    settings.nextEpisodeThresholdMinutes
                )
            ) {
                // Sought back below the threshold: hide the card; the
                // countdown effect cancels with it.
                state.nextEpisodeCard = null
                state.nextEpisodeCountdown = null
                state.prefetchedNextStreams = null
            }
            // Sleep timer (v0.14.30 port), "End of episode": the episode
            // finished — stop here instead of advancing, and consume the
            // timer. The player is already stopped at STATE_ENDED; the
            // screen hold is released so the screen can sleep.
            if (ended &&
                state.sleepAtEpisodeEnd &&
                !state.currentRequest.isMovie &&
                !state.currentRequest.isTrailer &&
                state.currentRequest.episodes.isNotEmpty()
            ) {
                state.sleepAtEpisodeEnd = false
                state.sleepChosenMinutes = 0
                activePause()
                state.sleepScreenReleased = true
                state.playerView?.keepScreenOn = false
                pokeIndicator(PlayerIndicator.Message("Sleep timer — playback paused"))
            }
            // Auto-play next episode: when a series episode ends and the
            // setting is on, advance automatically — unless the countdown
            // card is showing (it advances itself) or was cancelled, or a
            // sleep timer is armed for end of episode.
            if (ended &&
                settings.autoPlayNext &&
                !state.sleepAtEpisodeEnd &&
                !state.currentRequest.isMovie &&
                !state.currentRequest.isTrailer &&
                state.currentRequest.episodes.isNotEmpty() &&
                state.nextEpisodeCard == null &&
                state.endedHandledFor != state.currentRequest.videoId
            ) {
                state.endedHandledFor = state.currentRequest.videoId
                if (nextEp != null) {
                    pokeIndicator(PlayerIndicator.Message("Playing next episode…"))
                    playEpisode(nextEp)
                }
            }
            // Auto-download next episode (v0.14.30 port, feature #16):
            // the episode just ended and the switch is on — queue the
            // NEXT episode in Downloads under the user's own rules (the
            // same queue path "Download season" uses, so the quality/size
            // pick and Wi-Fi-only/storage rules all apply). Shuffle plays
            // a random episode next, so it never triggers a sequential
            // download. Fire-and-forget: any failure simply queues
            // nothing. Fires once per video end, whether or not
            // auto-play-next is on.
            if (ended &&
                settings.autoDownloadNextEpisode &&
                !settings.shuffleEpisodes &&
                !state.currentRequest.isMovie &&
                !state.currentRequest.isTrailer &&
                state.currentRequest.episodes.isNotEmpty() &&
                state.autoDownloadHandledFor != state.currentRequest.videoId
            ) {
                state.autoDownloadHandledFor = state.currentRequest.videoId
                val next = resolveNextEpisode(
                    state.currentRequest.episodes,
                    state.currentRequest.season,
                    state.currentRequest.episode,
                    state.currentRequest.videoId,
                    false
                )
                val seriesKey = (state.currentRequest.imdbId
                    ?: state.currentRequest.videoId.substringBefore(':'))
                    .takeIf { it.startsWith("tt") }
                if (next != null && seriesKey != null) {
                    ioScope.launch {
                        AutoDownloadNext.queue(
                            repository, context, seriesKey, next.season, next.episode
                        )
                    }
                }
            }
            // End-of-play rating prompt (v0.14.30 port): once per video,
            // when it ends, offer the rating card.
            if (ended &&
                state.ratingPromptHandledFor != state.currentRequest.videoId
            ) {
                state.ratingPromptHandledFor = state.currentRequest.videoId
                maybeOfferRatingPrompt(state.currentRequest)
            }
            // Movie recommendations (Nuvio MovieRecommendationCard):
            // once per movie, when it ends, load "More like this".
            if (ended &&
                state.currentRequest.isMovie &&
                !state.currentRequest.isTrailer &&
                state.movieRecsLoadedFor != state.currentRequest.videoId
            ) {
                state.movieRecsLoadedFor = state.currentRequest.videoId
                val req = state.currentRequest
                ioScope.launch {
                    val sections = runCatching {
                        repository.getRelated(req.type, req.metaId.substringBefore(":"))
                    }.getOrDefault(emptyList())
                    val recs = sections.flatMap { it.metas }.distinctBy { it.id }.take(10)
                    val addonId = runCatching {
                        repository.metaAddonsFor(req.type).firstOrNull()?.id
                    }.getOrNull()
                    withContext(Dispatchers.Main) {
                        if (state.movieRecsLoadedFor == req.videoId) {
                            state.movieRecs = recs
                            state.movieRecsAddonId = addonId
                        }
                    }
                }
            }
            if (!state.currentRequest.isTrailer) {
                scrobbler.tick(
                    state.currentRequest,
                    state.positionMs,
                    state.durationMs,
                    state.isPlaying,
                    ended
                )
                // Plex live timeline: same tick, Plex-gated inside
                // (non-Plex videoIds are ignored).
                plexTimeline.tick(
                    state.currentRequest,
                    state.positionMs,
                    state.durationMs,
                    state.isPlaying,
                    ended
                )
            }
            delay(if (isTv) 1000 else 500) // low-end TVs: half the recompositions
        }
    }

    // Auto-hide controls and indicators.
    LaunchedEffect(state.showControls, state.controlsTick) {
        if (state.showControls && !state.locked) {
            delay(3500)
            state.showControls = false
        }
    }

    // Pause screen (v0.14.30 port, feature #45). The fork has no
    // explicit "user pressed pause" flag (upstream sets one in
    // togglePlay); the approximation used here is a READY pause —
    // not playing, playback has started, not buffering — which is
    // the same condition the pause screen's own visibility effect
    // requires, so a buffering/loading transition hides it in the
    // same recomposition. A new request starts clean.
    val userPaused = !state.isPlaying && hasStartedPlayback && !state.isBuffering
    var pauseScreenVisible by remember(state.currentRequest) { mutableStateOf(false) }
    LaunchedEffect(state.currentRequest) {
        pauseScreenVisible = false
    }
    // Visibility (upstream's hard condition): the switch is on, the
    // pause was deliberate, the player is READY-paused (not playing
    // and NOT buffering), no error, no panel, not locked, VOD only
    // (never live, never trailers), not in PiP, and the controls are
    // gone. It appears a short beat after the controls fade, never
    // over them. The fork already auto-hides the controls 3.5 s
    // after they show, paused or playing, so no auto-hide change
    // was needed for the pause branch.
    val pauseAnyPanelOpen = state.tracksOpen || state.sourcesOpen || state.episodesOpen ||
        state.subSearchOpen || state.qualityOpen || state.infoOpen || state.chaptersOpen
    LaunchedEffect(
        userPaused, state.isPlaying, state.isBuffering, state.showControls,
        state.playerError, pauseAnyPanelOpen, state.locked,
        state.currentRequest, settings.pauseScreenEnabled, inPip
    ) {
        val eligible = settings.pauseScreenEnabled && userPaused &&
            !state.isPlaying && !state.isBuffering && !state.showControls &&
            state.playerError == null && !pauseAnyPanelOpen && !state.locked && !inPip &&
            !state.currentRequest.isLive && !state.currentRequest.isTrailer
        if (eligible) {
            delay(300)
            pauseScreenVisible = true
        } else {
            pauseScreenVisible = false
        }
    }

    // Nuvio legacy locked overlay auto-hides after 3.5s (reveal again by
    // tapping while locked).
    LaunchedEffect(state.locked, state.lockedOverlayVisible) {
        if (state.locked && state.lockedOverlayVisible) {
            delay(3500)
            state.lockedOverlayVisible = false
        }
    }
    // (The indicator's hide lives with its show animation below —
    // one unified effect, see "One show / one hide".)

    // One-time init: begin buffering immediately, then apply saved-position / autoplay policy.
    LaunchedEffect(Unit) {
        // Second engine: resolved once for this play; MPV skips every
        // Exo prepare/seek below and activates its own engine instead.
        val startOnMpv = startEngine == com.aviostream.app.player.PlaybackEngine.MPV
        val startupPlayer = player
        val isLocalFile = initialRequest.streamUrl.startsWith("file:", true) || initialRequest.streamUrl.startsWith("content:", true)
        val prepareEarly = initialRequest.streams.isNotEmpty() || initialRequest.isLive || isLocalFile
        val saved = preparePlayerBeforeSavedPositionLookup(
            preparePlayer = {
                // Prepare with playback held so a resume prompt never leaks audio/video
                // while progress storage (or a fresh-URL lookup) is still pending.
                // Second engine: MPV owns playback, so the parked ExoPlayer never prepares.
                if (!startOnMpv) {
                    startupPlayer.playWhenReady = false
                    startupPlayer.prepare()
                }
            },
            loadSavedPosition = {
                // Play from start: ignore the saved position entirely —
                // saved = 0 means no resume offer and seekToPlaybackStart
                // starts at 0:00. The saved position is kept, not erased.
                if (initialRequest.playFromStart) 0L
                else if (initialRequest.isLive && !isTv) 0L
                else repository.getSavedPosition(initialRequest.streamUrl)
            },
            prepareEarly = prepareEarly
        )
        var req: PlaybackRequest = initialRequest
        var rebuiltForFreshResumeUrl = false
        var offerResume = saved > 0
        // Local files (downloads): never re-resolve sources — play the file directly.
        if (saved > 0 && req.streams.isEmpty() && !req.isLive && !isLocalFile && !disposed.get()) {
            // #15 + Nuvio reuse-last-link: within the TTL, replay the last
            // played link directly instead of re-resolving every addon (and
            // debrid) first. Expired/signed URLs fall through to the fresh
            // resolve below; a dead reused link is dropped on error.
            val reuseKey = streamLinkContentKey(
                type = req.type,
                videoId = req.videoId,
                parentMetaId = req.metaId.takeIf { it.isNotBlank() },
                season = req.season.takeIf { it > 0 },
                episode = req.episode.takeIf { it > 0 },
            )
            val cachedLink = if (settings.reuseLastLinkEnabled) {
                linkCache.getValid(reuseKey, settings.reuseLastLinkCacheHours * 3_600_000L)
            } else null
            if (cachedLink != null) {
                req = req.copy(
                    streamUrl = cachedLink.url,
                    headers = cachedLink.requestHeaders,
                    mimeType = cachedLink.mimeType ?: req.mimeType,
                    streamLabel = cachedLink.streamName,
                    sourceName = cachedLink.addonName.takeIf { it.isNotBlank() },
                    streams = emptyList() // filled in the background by refreshStreamsBackground
                )
                repository.pendingPlayback = req
                // Fresh URL: go straight in at the saved position, no prompt.
                offerResume = false
            } else {
            // #15: Continue Watching resume — re-resolve the title's streams
            // first so signed/expiring URLs are fresh, and resume at the saved
            // position on the fresh URL. Falls back to the stored URL when
            // re-resolution fails.
            val fresh = withTimeoutOrNull(20_000) {
                repository.streamResultsFlow(
                    req.type, req.videoId,
                    // Same CS3 title fallback as the background refresh:
                    // a non-IMDb id resolves by title, not by id alone.
                    title = req.seriesName?.takeIf { it.isNotBlank() }
                        ?: req.title.takeIf { it.isNotBlank() },
                    // Plugs (#22): a resume re-resolution, not a fresh
                    // open — it must never start fresh QuickJS runs.
                    includePlugs = false
                )
                    .firstOrNull { it.streams.any { stream -> com.aviostream.app.data.isPlayableLiveStreamUrl(stream.url) } }
            }
            val (playableFresh, hiddenFresh) = fresh?.streams?.let { ( it to 0 ) } ?: (emptyList<com.aviostream.app.data.StreamItem>() to 0)
            val stream = playableFresh.firstOrNull { com.aviostream.app.data.isPlayableLiveStreamUrl(it.url) }
            if (fresh != null && stream != null && !disposed.get()) {
                state.availableStreams = playableFresh
                state.hiddenIncompatibleCount = hiddenFresh
                req = req.copy(
                    streamUrl = stream.url,
                    headers = stream.headers,
                    mimeType = stream.mimeType,
                    streamLabel = streamQualityLabel(stream),
                    sourceName = stream.sourceName,
                    subtitles = fresh.subtitles,
                    streams = fresh.streams
                )
                repository.pendingPlayback = req
                // Fresh URL: go straight in at the saved position, no prompt.
                offerResume = false
            } else {
                // Stored URL stays; quietly fill Sources for a manual re-pick.
                refreshStreamsBackground(req.type, req.videoId)
            }
            }
        }
        if (disposed.get()) return@LaunchedEffect
        if (req !== initialRequest && !startOnMpv) {
            // Rebuild the player around the refreshed request so the first
            // prepare already uses the fresh URL (#15; guarded by #9 above).
            val oldPlayer = player
            state.playerView?.player = null
            oldPlayer.release()
            val rebuilt = buildExo(req).also { it.addListener(guardedPlayerListener()) }
            rebuilt.setPlaybackSpeed(state.speed)
            player = rebuilt
            state.mimeHealAttempted = false
            if (state.downmixFailoverAttempted) {
                setDownmixEnabled(false)
            }
            state.downmixFailoverAttempted = false
            state.currentRequest = req
            rebuiltForFreshResumeUrl = true
        }
        if (startOnMpv) {
            if (req !== initialRequest) {
                state.currentRequest = req
                repository.pendingPlayback = req
            }
            // The parked ExoPlayer never prepared; MPV owns playback now.
            // A resume offer starts paused at the saved position — the
            // prompt's Resume / Start over buttons drive MPV from there.
            activateMpv(req, saved, autoplay = !offerResume && settings.autoplay)
            if (offerResume) {
                state.resumeOfferMs = saved
                state.showControls = true
            }
        } else {
            val p = player
            seekToPlaybackStart(
                isMobileLive = req.isLive && !isTv,
                startAtMs = saved,
                seekToPosition = { p.seekTo(it) },
                seekToDefaultPosition = { p.seekToDefaultPosition() }
            )
            // The original instance is already prepared above. A refreshed URL has
            // a newly-built player and still needs its first prepare after seekTo.
            if (rebuiltForFreshResumeUrl || !prepareEarly) p.prepare()
            if (offerResume) {
                p.playWhenReady = false
                state.resumeOfferMs = saved
                state.showControls = true
            } else if (settings.autoplay) {
                p.playWhenReady = true
            } else {
                p.playWhenReady = false
                state.showControls = true
            }
        }
        // The settled link is the last played link for this title.
        saveLinkForReuse(req)
    }

    // Step 62: cross-device resume — when the resume source is Trakt or
    // Simkl and this device has no local progress for the title, jump to
    // the pulled position once the player reports a real duration.
    // Trakt/Simkl return a percent, not milliseconds, so the seek waits
    // for duration (max ~30s) and only fires while playback is still
    // near the start. Local progress always wins; Play from start and
    // live never take the remote position.
    val watchProgressPull = remember { com.aviostream.app.data.WatchProgressPull(settings) }
    LaunchedEffect(initialRequest.videoId, initialRequest.season, initialRequest.episode) {
        if (initialRequest.playFromStart || initialRequest.isLive) return@LaunchedEffect
        if (settings.watchProgressSource == "local") return@LaunchedEffect
        val identity = watchProgressPull.identityFor(
            initialRequest.imdbId, initialRequest.metaId,
            initialRequest.season, initialRequest.episode
        )
        val pct = watchProgressPull.resumePercentFor(identity) ?: return@LaunchedEffect
        if (pct < 1.0 || pct >= 95.0) return@LaunchedEffect
        val localMs = runCatching { repository.getSavedPosition(initialRequest.streamUrl) }.getOrDefault(0L)
        if (localMs > 0) return@LaunchedEffect
        var waited = 0
        while (waited < 30_000) {
            if (disposed.get()) return@LaunchedEffect
            val duration = activeDurationMs().takeIf { it > 0 } ?: -1L
            if (duration > 0) {
                val target = (duration * pct / 100.0).toLong()
                val pos = activePositionMs()
                if (pos < 10_000 && target > 10_000) {
                    activeSeekTo(target)
                }
                return@LaunchedEffect
            }
            delay(500)
            waited += 500
        }
    }

    // An early Play action may hand off only the first addon's results. Always finish
    // the same title's complete addon flow; a source switch alone does not restart it.
    LaunchedEffect(state.currentRequest.type, state.currentRequest.videoId, state.currentRequest.liveChannel) {
        val req = state.currentRequest
        val local = req.streamUrl.startsWith("file:", true) || req.streamUrl.startsWith("content:", true)
        val playlistChannel = req.isLive && req.liveChannel == null &&
            com.aviostream.app.data.isPlayableLiveStreamUrl(req.videoId)
        if (!local && !playlistChannel) refreshStreamsBackground(req.type, req.videoId)
    }

    // Skip segments follow the title, not the source — fetch once per episode.
    // Merged repository (v0.14.30 port, #33): IntroDB by IMDb id plus, for
    // anime, Anime-Skip and AniSkip by MAL id (resolved from a mal:/anilist:
    // video id), merged IntroDB > Anime-Skip > AniSkip.
    LaunchedEffect(initialRequest.imdbId, initialRequest.season, initialRequest.episode) {
        val ids = listOf(initialRequest.metaId, initialRequest.videoId)
        val hasMalId = ids.any {
            val raw = it.trim().lowercase()
            raw.startsWith("mal:") || raw.startsWith("anilist:")
        }
        state.apiSkipSegments = if (!initialRequest.imdbId.isNullOrBlank() || hasMalId) {
            runCatching {
                SkipSegmentRepository().fetchMerged(
                    imdbId = initialRequest.imdbId,
                    candidateIds = ids,
                    season = initialRequest.season,
                    episode = initialRequest.episode,
                    isMovie = initialRequest.isMovie,
                    episodeLengthSec = (state.durationMs / 1000L).coerceAtLeast(0L)
                )
            }.getOrDefault(emptyList())
        } else emptyList()
    }

    // Chapters follow the source, not the title — read once per stream URL
    // from the file itself (ExoPlayer's Matroska extractor skips them).
    // Local files and HLS/DASH playlists are skipped by the loader/gate.
    LaunchedEffect(state.currentRequest.streamUrl) {
        state.chapters = emptyList()
        state.chapterSkipSegments = emptyList()
        val req = state.currentRequest
        if (!shouldReadChapters(req.mimeType, req.streamUrl)) return@LaunchedEffect
        state.chapters = MatroskaChapterLoader.load(req.streamUrl, req.headers)
    }

    // Chapter-derived skip intervals: computed once the duration is known.
    LaunchedEffect(state.chapters, state.durationMs) {
        state.chapterSkipSegments = ChapterSkipIntervals.fromChapters(state.chapters, state.durationMs)
    }

    // Save on background so progress is never lost.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                savePosition()
                // PiP: the activity pauses when the video floats, but playback
                // must continue in the PiP window — only pause when the app is
                // truly backgrounded (not floating).
                if (activity?.isInPictureInPictureMode == true) return@LifecycleEventObserver
                // Second engine: the background-audio service mirrors the
                // ExoPlayer only — MPV always pauses on background.
                if (mpvActive()) {
                    activePause()
                    return@LifecycleEventObserver
                }
                // Step 64: background audio (opt-in, VOD only) keeps
                // playing — the foreground service holds the notification
                // + session. Otherwise #10: pause on background —
                // release stays in onDispose.
                if (settings.backgroundAudioEnabled && !state.currentRequest.isLive) return@LifecycleEventObserver
                latestPlayer.value.pause()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    // Landscape + immersive; restore on exit; save + release on dispose.
    // YouTube opens portrait-first (Rotate pill switches to landscape).
    DisposableEffect(Unit) {
        // PiP dismissal rework (v0.14.4): MainActivity stops playback
        // through this hook when a PiP window is X-closed — on the
        // owner's Samsung the stopped activity never recomposes, so
        // the old counter-only path left audio playing until the app
        // was killed from recents. stop()+release() here kills the
        // audio at the source; the dispose path below stays as-is.
        MainActivity.pipEmergencyStop = {
            runCatching { savePosition() }
            runCatching { latestPlayer.value.stop() }
            runCatching { latestPlayer.value.release() }
            runCatching { mpvEngine?.release() }
        }
        activity?.requestedOrientation = if (initialRequest.sourceName == "YouTube")
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        else
            // SENSOR_LANDSCAPE (not LANDSCAPE): both landscape directions
            // follow the sensor, so the video flips when the phone is
            // turned the other way. LANDSCAPE locks to one side only.
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        // Hide status bar: the pill (clock, battery, signal) the owner
        // reported still showing. The player always hid the bars on
        // entry, so a visible bar means the system showed it again
        // afterwards (shade pull-down, PiP return, controls tap) — the
        // resume observer + the showControls effect below re-hide it.
        // Default on; the Playback setting turns it off.
        val hideStatusBar = settings.hideStatusBarInPlayer
        val controller = window?.let { WindowInsetsControllerCompat(it, it.decorView) }
        if (hideStatusBar) {
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            controller?.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val resumeObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && hideStatusBar) {
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        lifecycle.addObserver(resumeObserver)

        onDispose {
            MainActivity.pipEmergencyStop = null
            // #9: from here on, no late work may build a player (ghost audio).
            disposed.set(true)
            preemptPendingLiveFallback()
            savePosition()
            scrobbler.shutdown()
            plexTimeline.shutdown()
            // #9: kill late zap/episode/source-switch work. The scope itself
            // stays valid (the scrobblers above hold it); only its children
            // are cancelled. savePosition()'s save runs NonCancellable so it
            // survives this; the scrobblers' final stop-events are best-effort.
            ioScope.coroutineContext.cancelChildren()
            latestPlayer.value.release()
            mpvEngine?.release()
            // Step 64: stop the background-audio service + session with the player.
            com.aviostream.app.data.BackgroundAudioController.unregister()
            runCatching { boostHolder.enhancer?.release() }
            // #18: swipe-brightness must not leak into the rest of the app.
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = -1f
                w.attributes = lp
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            lifecycle.removeObserver(resumeObserver)
            if (hideStatusBar) controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Re-hide the status bar when the controls toggle — on some devices
    // showing the controls (or a swipe) brings the bar back, and nothing
    // re-hid it until now. Reads the setting live, so flipping it in
    // Playback applies on the next controls tap.
    LaunchedEffect(state.showControls) {
        if (settings.hideStatusBarInPlayer) {
            activity?.window?.let { WindowInsetsControllerCompat(it, it.decorView) }
                ?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    val req = state.currentRequest
    val headerTitle = req.seriesName ?: req.title
    // Nuvio legacy episode format: "S1 E2 · Title".
    val episodeLine = if (!req.isMovie) {
        "S${req.season} E${req.episode}" + (req.episodeTitle?.let { " · $it" } ?: "")
    } else null
    // Nuvio legacy meta row: stream title + provider name (italic) as two
    // separate texts, not one joined line.
    val streamLabelText = req.streamLabel
    val sourceNameText = req.sourceName
    // Nuvio legacy "Ends at HH:MM" label, speed-adjusted.
    val endsAtText = remember(state.positionMs, state.durationMs, state.speed) {
        endsAtLabel(state.positionMs, state.durationMs, state.speed)
    }

    // Step 64: background audio — hand the player to the now-playing
    // controller while the setting is on (VOD only). Metadata follows
    // the current request; turning the setting off unregisters.
    LaunchedEffect(player, state.currentRequest.videoId, state.currentRequest.title, settings.backgroundAudioEnabled) {
        if (!settings.backgroundAudioEnabled || state.currentRequest.isLive) {
            com.aviostream.app.data.BackgroundAudioController.unregister()
            return@LaunchedEffect
        }
        com.aviostream.app.data.BackgroundAudioController.register(
            context = context,
            player = player,
            metadata = com.aviostream.app.data.BackgroundAudioMetadata(
                title = headerTitle,
                subtitle = episodeLine ?: state.currentRequest.sourceName,
                artworkUrl = state.currentRequest.poster
            )
        )
    }

    // ---- Android TV D-pad: works when the touch gestures can't. ----
    val playPauseFocus = remember { FocusRequester() }
    // TV: the player container itself is focusable and holds D-pad focus
    // whenever the transport controls are hidden, so the remote never goes
    // dead (NuvioTV parity: containerFocusRequester pattern).
    val containerFocus = remember { FocusRequester() }
    // TV: land D-pad focus on the error overlay's Retry button when it shows.
    val errorRetryFocus = remember { FocusRequester() }
    LaunchedEffect(state.playerError, isTv) {
        // A dead HTTP status on the playing link means the cached URL is
        // gone (signed link expired): drop it so the next replay re-resolves
        // instead of retrying the same dead link.
        if (state.playerError != null && settings.reuseLastLinkEnabled &&
            (state.lastPlayerErrorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                state.lastPlayerErrorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        ) {
            linkCache.remove(
                streamLinkContentKey(
                    type = state.currentRequest.type,
                    videoId = state.currentRequest.videoId,
                    parentMetaId = state.currentRequest.metaId.takeIf { it.isNotBlank() },
                    season = state.currentRequest.season.takeIf { it > 0 },
                    episode = state.currentRequest.episode.takeIf { it > 0 },
                )
            )
        }
        if (isTv && state.playerError != null) {
            delay(300)
            runCatching { errorRetryFocus.requestFocus() }
        }
    }
    LaunchedEffect(state.showControls, isTv, state.locked) {
        if (!isTv) return@LaunchedEffect
        if (state.showControls && !state.locked) {
            delay(350) // let the controls fade in first
            runCatching { playPauseFocus.requestFocus() }
        } else if (!state.showControls && state.playerError == null) {
            // Controls hidden: park focus on the container so D-pad events
            // always have a target (container's onPreviewKeyEvent toggles
            // controls back on). Without this the remote goes dead.
            runCatching { containerFocus.requestFocus() }
        }
    }
    // TV: initial focus on the container so the remote works immediately,
    // before the user ever opens the controls.
    LaunchedEffect(isTv) {
        if (isTv && state.playerError == null) {
            delay(300)
            runCatching { containerFocus.requestFocus() }
        }
    }
    // Focus bookkeeping: overlay flag resets when controls are hidden, and the
    // currently focused transport button registers its action so OK always
    // fires it (even if the button's own click handling misses the key).
    LaunchedEffect(state.showControls) { if (!state.showControls) state.transportFocused = false }
    fun act(key: String, action: () -> Unit): Modifier =
        Modifier.onFocusChanged {
            if (it.isFocused || it.hasFocus) { state.focusedKey = key; state.focusedAction = action }
            else if (state.focusedKey == key) { state.focusedKey = null; state.focusedAction = null }
        }
    val tvKeys = if (isTv) {
        // A settings sheet/dialog is up: the D-pad must drive the dialog's
        // own focus ( Up/Down through rows, OK to pick ), not the player.
        // Media keys and the channel rocker keep working regardless.
        val dialogOpen = isDialogOpen(state.tracksOpen, state.subSearchOpen, state.sourcesOpen, state.episodesOpen, state.qualityOpen, state.infoOpen, state.chaptersOpen, state.speedMenuOpen, state.resumeOfferMs, state.playerError)
        Modifier.onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            // Any remote activity keeps the controls on screen (they used to
            // auto-hide after 3.5s MID-navigation, so focus "vanished").
            if (state.showControls) state.controlsTick++
            // Remote channel rocker zaps while watching live TV.
            when (event.nativeKeyEvent.keyCode) {
                android.view.KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (canZap()) { zapDelta(1); true } else false
                }
                android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (canZap()) { zapDelta(-1); true } else false
                }
                else -> when (event.key) {
                // Media keys always act directly.
                Key.MediaPlayPause -> { togglePlay(); true }
                Key.MediaPlay -> { if (mpvActive()) mpvEngine?.setPaused(false) else player.play(); true }
                Key.MediaPause -> { if (mpvActive()) mpvEngine?.setPaused(true) else player.pause(); true }
                Key.MediaFastForward -> { seekByWithFeedback(settings.skipForwardSeconds * 1000L); true }
                Key.MediaRewind -> { seekByWithFeedback(-settings.skipBackwardSeconds * 1000L); true }
                // D-pad Left/Right: with a dialog open, hand off to the
                // dialog. With the seekbar focused, its own handler seeks.
                // With controls visible, let 2D focus search move between
                // buttons (this is what makes every control reachable).
                // With controls hidden, arrows seek directly.
                Key.DirectionLeft -> when {
                    dialogOpen || state.seekbarFocused || state.showControls -> false
                    else -> { seekByWithFeedback(-settings.skipBackwardSeconds * 1000L); true }
                }
                Key.DirectionRight -> when {
                    dialogOpen || state.seekbarFocused || state.showControls -> false
                    else -> { seekByWithFeedback(settings.skipForwardSeconds * 1000L); true }
                }
                Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                    if (dialogOpen) false
                    else if (state.showControls && state.focusedAction != null) {
                        if (event.nativeKeyEvent.repeatCount == 0) state.focusedAction?.invoke()
                        true
                    }
                    else if (state.showControls && !state.transportFocused) {
                        runCatching { playPauseFocus.requestFocus() }; true
                    } else if (state.showControls) false else { toggleControls(); true }
                // D-pad Up/Down: dialogs handle their own; visible controls
                // get 2D focus navigation between rows; hidden controls are
                // summoned (Down brings them up per the TV spec).
                Key.DirectionUp, Key.DirectionDown ->
                    if (dialogOpen) false
                    else if (state.showControls && !state.transportFocused) {
                        runCatching { playPauseFocus.requestFocus() }; true
                    } else if (state.showControls) false else { toggleControls(); true }
                else -> false
                }
            }
        }
    } else Modifier

    // TV: the root container is focusable and holds the containerFocus
    // requester, so D-pad key events always reach onPreviewKeyEvent even
    // when no transport button has focus (NuvioTV parity).
    val tvContainerModifier = if (isTv) {
        Modifier
            .focusRequester(containerFocus)
            .focusable()
    } else Modifier
    val sheetUp = isSheetUp(state.tracksOpen, state.subSearchOpen, state.sourcesOpen, state.episodesOpen, state.qualityOpen, state.infoOpen, state.chaptersOpen, state.speedMenuOpen, state.resumeOfferMs, state.downloadStream)
    LaunchedEffect(state.rootHasFocus, isTv, sheetUp, state.showControls, state.playerError) {
        if (isTv && !state.rootHasFocus && !sheetUp && state.playerError == null) {
            delay(400)
            runCatching { if (state.showControls && !state.locked) playPauseFocus.requestFocus() else containerFocus.requestFocus() }
                .onFailure { runCatching { containerFocus.requestFocus() } }
        }
    }
    // Pinch zoom (Nuvio setVideoZoom, ExoPlayer branch): scale the
    // PlayerView's inner content frame (exo_content_frame, else the video
    // surface itself) with a plain View scale. The old code scaled the
    // whole AndroidView with a Compose graphicsLayer, which forced the
    // TextureView into an offscreen hardware layer every frame — the app
    // died mid-pinch, normal playback included, with no Java trace
    // (native graphics crash). Nuvio scales the content frame and it
    // does not crash; the subtitles stay unscaled too.
    //
    // The View scale alone did NOT fix the crash (still force-stops past
    // ~1.2x): the remaining divergence from Nuvio is the SURFACE.
    // Nuvio's PlayerView is a SurfaceView (separate surface, never drawn
    // into the backdrop recording); Avio's is a TextureView, and the
    // backdrop source re-records the whole screen — including the 4K
    // video — into a GraphicsLayer every frame. Drawing the SCALED
    // TextureView into that layer is what kills the app (the scaled 4K
    // texture crosses the GPU's ~4096 render-target limit: 3840 x ~1.07
    // = 4096, and the % pill lags the true scale by an event or two, so
    // the user sees the last value ~115-120%). While zoomed, the
    // recording is therefore frozen (AvioBackdrop.suspendRecording) and
    // the glass keeps the last recorded frame; recording resumes when
    // the zoom is back to 1x.
    val glassBackdrop = LocalAvioGlassBackdrop.current as? AvioBackdrop
    fun applyVideoZoom() {
        val pv = state.playerView ?: return
        val target = pv.findViewById<View>(androidx.media3.ui.R.id.exo_content_frame)
            ?: pv.videoSurfaceView
            ?: return
        target.scaleX = state.videoZoom
        target.scaleY = state.videoZoom
        glassBackdrop?.suspendRecording = state.videoZoom != 1f
    }
    LaunchedEffect(state.videoZoom) { applyVideoZoom() }
    // The backdrop is app-wide: never leave the recording frozen after
    // the player goes away (the Home/Detail glass would show a stale
    // frame forever).
    DisposableEffect(Unit) {
        onDispose { glassBackdrop?.suspendRecording = false }
    }
    Box(tvKeys.fillMaxSize().background(Color.Black)
        .then(if (isTv) Modifier.onFocusChanged { state.rootHasFocus = it.hasFocus } else Modifier)
        .then(tvContainerModifier)) {
        // Video surface with the full gesture set (gestures off while floating —
        // taps on the PiP window belong to the system's PiP controls).
        Box(Modifier.fillMaxSize().then(if (inPip) Modifier else Modifier.videoGestures())) {
            if (mpvActive()) {
                // Second engine (MPV port): MPV renders into a plain
                // SurfaceView; the control shell around it is shared
                // and unchanged. The engine owns the attach/detach
                // ordering on its own thread.
                AndroidView(
                    factory = { ctx ->
                        android.view.SurfaceView(ctx).also { sv ->
                            sv.layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            sv.holder.addCallback(mpvSurfaceCallback)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            AndroidView(
                factory = { ctx ->
                    // TextureView surface (R.layout.player_view): the video
                    // renders into the view hierarchy, so the glass backdrop
                    // recording captures its frames and the video shows
                    // blurred behind the player glass. A SurfaceView (the
                    // PlayerView(ctx) default) is a separate surface the
                    // backdrop cannot see.
                    (LayoutInflater.from(ctx).inflate(R.layout.player_view, null, false) as PlayerView).also { pv ->
                        pv.player = player
                        // Step 38: route cues through the SDH filter when the
                        // toggle is on — the delegate is the PlayerView's own
                        // SubtitleView, so rendering is otherwise unchanged.
                        pv.subtitleView?.let { sv ->
                            attachSdhCueFilter(player, sv)
                        }
                        pv.useController = false
                        pv.isFocusable = false
                        pv.isFocusableInTouchMode = false
                        pv.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                        pv.keepScreenOn = settings.keepScreenOn
                        pv.resizeMode = state.aspectMode
                        pv.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        applySubtitleStyle(pv)
                        state.playerView = pv
                    }
                },
                update = { pv ->
                    pv.player = player
                    pv.subtitleView?.let { sv ->
                        attachSdhCueFilter(player, sv)
                    }
                    applySubtitleStyle(pv)
                    applyVideoZoom()
                },
                // Pinch zoom is applied by applyVideoZoom() (View scale on
                // the content frame) — not a graphicsLayer on this view.
                modifier = Modifier.fillMaxSize()
            )
            }
        }

        // PiP (v0.9.0+): while the video floats, skip every overlay layer —
        // the system draws its own PiP controls. Only the PlayerView surface
        // stays composed, so playback continues uninterrupted.
        if (inPip) return@Box

        // Step 68: parental guide overlay (top-left, below the header).
        // Shows itself for ~5s once playback starts, then fades out.
        if (state.parentalGuideVisible && state.parentalWarnings.isNotEmpty()) {
            ParentalGuideOverlay(
                warnings = state.parentalWarnings,
                isVisible = state.parentalGuideVisible,
                onAnimationComplete = { state.parentalGuideVisible = false }
            )
        }

        // Sleep timer chip (v0.14.30 port): while a sleep timer runs, a
        // small pill at the top-centre shows what's left — tapping it
        // cancels the timer (change it from the Sleep menu in the pill).
        // It fades with the controls — it renders only while they show.
        if ((state.sleepDeadlineMs > 0L || state.sleepAtEpisodeEnd) && state.showControls && !state.locked) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 84.dp)
                    .clickable {
                        state.sleepDeadlineMs = 0L
                        state.sleepAtEpisodeEnd = false
                        state.sleepChosenMinutes = 0
                        pokeIndicator(PlayerIndicator.Message("Sleep timer off"))
                    }
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Bedtime,
                        contentDescription = null,
                        tint = TextPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        if (state.sleepDeadlineMs > 0L) {
                            "Sleep in $sleepTimerLabel"
                        } else {
                            "Sleep at episode end"
                        },
                        color = TextPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Startup presentation (owner's pick 2026-10-24 — "Backdrop
        // + orbit"): while the current request has not played yet, the
        // connecting state is the title's art full-screen, dimmed, the
        // title, the orbit loader and the staged step text (Nuvio
        // PlayerLoadingStatus — "Opening source… / Reading tracks… /
        // Buffering…" instead of a bare spinner). Once playback has begun,
        // a rebuffer shows just the orbit + "Buffering…" over the video.
        // Suppressed on a fatal error — the error overlay takes its place (#4).
        if ((state.isBuffering || state.switchingMedia) && state.playerError == null) {
            // v0.14.30 #44 (Poster Heartbeat): WHICH buffering moments
            // wear the loading screen is the upstream scope pin
            // (playerBufferVisual) — the initial load before this
            // title has ever played only. Every other mid-playback
            // buffer (seek, slow-network rebuffer, source switch)
            // keeps the plain orbit over the video, exactly as
            // before. Resume-after-pause buffering is NOT wired
            // (resumeBuffering stays false) — that would need
            // togglePlay edits, out of scope for this port.
            val bufferVisual = playerBufferVisual(
                isBuffering = state.isBuffering || state.switchingMedia,
                hasError = state.playerError != null,
                hasStartedOnce = hasStartedPlayback,
                resumeBuffering = false
            )
            if (bufferVisual == PlayerBufferVisual.STARTUP_OVERLAY) {
                PlayerLoadingOverlay(
                    active = true,
                    request = state.currentRequest,
                    phase = initialLoadingPhase(
                        state.currentRequest.streamUrl.isBlank(),
                        state.currentRequest.isLive,
                        state.currentRequest.isTrailer
                    )
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OrbitDots(Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Buffering…",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Fatal stream error (#4): Retry re-prepares this stream, Next source
        // advances to the next available stream, and the floating Sources
        // switcher stays openable underneath via "Browse all sources".
        // End-of-play rating prompt (v0.14.30 port): the card shows at
        // the bottom when the finished video raised it.
        state.ratingPrompt?.let { prompt ->
            PlayerRatingPrompt(
                prompt = prompt,
                nextEpisodeCardVisible = state.nextEpisodeCard != null,
                onRate = { stars -> rateFromPrompt(prompt, stars) },
                onSkip = {
                    userRatings.notePromptSkip(prompt.promptKey)
                    state.ratingPrompt = null
                },
                onDone = { state.ratingPrompt = null }
            )
        }

        state.playerError?.let { err ->
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.78f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        com.aviostream.app.data.PlaybackErrorText.TITLE,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        err,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { retryCurrentStream() },
                            modifier = Modifier
                                .focusRequester(errorRetryFocus)
                                .tvFocusRing(RoundedCornerShape(20.dp))
                        ) {
                            Text("Retry")
                        }
                        Button(
                            onClick = { nextSource() },
                            modifier = Modifier.tvFocusRing(RoundedCornerShape(20.dp))
                        ) {
                            Text("Next source")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        // Fresh fetch when opening Sources — ensures full list
                        refreshStreamsBackground(state.currentRequest.type, state.currentRequest.videoId)
                        state.sourcesOpen = true
                    }) {
                        Text(
                            "Browse all sources",
                            color = LocalPalette.current.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Transient gesture feedback (never consumes touches).
        // v0.14.3 (owner's order): pop-in spring + fade; volume/
        // brightness get an animated level bar; the volume indicator
        // changes colour at the extremes — soft red at mute (0),
        // amber in the boost zone (past 100).
        // Show instantly, hide with a fade (owner's report 2026-11-23):
        // the old pop-in (alpha 0->1 + scale 0.82->1) made the glass
        // box visible BEFORE the volume / brightness word + level was
        // readable, so the pill looked empty and then filled in. Now
        // the pill snaps to full opacity + scale the moment it appears,
        // so the box and its text arrive together. A poke that lands
        // mid-fade snaps it straight back to full opacity. The hide is
        // a single debounced fade that starts 1s after the LAST poke.
        val indScale = remember { Animatable(1f) }
        val indAlpha = remember { Animatable(0f) }
        LaunchedEffect(state.indicatorTick) {
            if (state.indicator == null) return@LaunchedEffect
            indScale.snapTo(1f)
            indAlpha.snapTo(1f)
            delay(1000)
            indAlpha.animateTo(0f, tween(200))
            state.indicator = null
        }
        state.indicator?.let { ind ->
            // Nuvio legacy GestureFeedbackPill: top-center, 24dp radius
            // liquid glass, 28dp icon circle + message. No level bar (the
            // side bars are the new layout's feedback, not legacy's).
            val isBoostedVolume = ind is PlayerIndicator.Volume && ind.percent > 100
            val isMuted = ind is PlayerIndicator.Volume && ind.percent <= 0
            val iconBoxColor = when {
                isMuted -> Color(0xFFFF8A80).copy(alpha = 0.22f)
                isBoostedVolume -> Color(0xFFFF5252).copy(alpha = 0.22f)
                else -> Color.White.copy(alpha = 0.15f)
            }
            Box(
                Modifier.fillMaxSize().padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier
                        .playerLiquidGlass(
                            shape = RoundedCornerShape(24.dp),
                        )
                        .graphicsLayer {
                            scaleX = indScale.value
                            scaleY = indScale.value
                            alpha = indAlpha.value
                        }
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val indIcon: ImageVector? = when (ind) {
                            is PlayerIndicator.Volume ->
                                if (ind.percent <= 0) Icons.Filled.VolumeOff
                                else Icons.Filled.VolumeUp
                            is PlayerIndicator.Brightness -> Icons.Filled.SettingsBrightness
                            is PlayerIndicator.Seek ->
                                if (ind.text.startsWith("+")) Icons.Filled.FastForward
                                else Icons.Filled.FastRewind
                            is PlayerIndicator.Speed -> Icons.Filled.Speed
                            is PlayerIndicator.Message -> null
                        }
                        if (indIcon != null) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(iconBoxColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    indIcon, null,
                                    tint = if (isMuted) Color(0xFFFFC1C1) else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            when (ind) {
                                is PlayerIndicator.Volume -> "Volume ${ind.percent}%"
                                is PlayerIndicator.Brightness -> "Brightness ${ind.percent}%"
                                is PlayerIndicator.Seek -> ind.text
                                is PlayerIndicator.Speed -> "Speed ${ind.label}"
                                is PlayerIndicator.Message -> ind.text
                            },
                            // Nuvio legacy: boosted volume reads red + Bold.
                            color = if (isBoostedVolume) Color(0xFFFF5252) else Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isBoostedVolume) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }
            // Side vertical bars (Nuvio GestureFeedbackOverlay): volume
            // on the right, brightness on the left; the top-centre pill
            // above carries the exact percentage.
            val sideBarFraction = when (ind) {
                is PlayerIndicator.Volume -> (ind.percent / 200f).coerceIn(0f, 1f)
                is PlayerIndicator.Brightness -> (ind.percent / 100f).coerceIn(0f, 1f)
                else -> null
            }
            if (sideBarFraction != null) {
                val isBoostedBar = ind is PlayerIndicator.Volume && ind.percent > 100
                val barColor = if (isBoostedBar) Color(0xFFFF5252) else Color.White
                val barAlignment = if (ind is PlayerIndicator.Volume) Alignment.CenterEnd else Alignment.CenterStart
                Box(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp),
                    contentAlignment = barAlignment
                ) {
                    Box(
                        Modifier
                            .width(6.dp)
                            .height(104.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .graphicsLayer { alpha = indAlpha.value },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(sideBarFraction)
                                .background(barColor)
                        )
                    }
                }
            }
        }

        // Hold-for-speed pill: pinned top-center only while the hold boost
        // is active, showing the current step (2x..5x); never consumes touches.
        // Nuvio legacy feedback sits top-center at 40dp in a glass pill.
        if (state.holdSpeedActive) {
            Box(
                Modifier.fillMaxSize().padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    Modifier.playerLiquidGlass(
                        shape = RoundedCornerShape(24.dp),
                    )
                ) {
                    Row(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.FastForward,
                            contentDescription = null,
                            tint = LocalPalette.current.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            speedLabel(state.holdSpeedStep),
                            color = TextPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // An end card (next-episode countdown / movie recommendations)
        // owns the bottom-end slot while it shows — the skip button
        // hides under it (Play now does the same job).
        val endCardVisible = state.nextEpisodeCard != null ||
            (state.movieRecs.isNotEmpty() && !state.movieRecsDismissed && !state.movieRecsAutoHidden)
        // Skip intro / recap / outro button (Nuvio SkipIntroButton):
        // glass 16dp, SkipNext 20dp + label, 3dp progress bar for the
        // 10s auto-hide, dismissible with the X. Hidden while an end
        // card owns the slot (Play now does the same job) and while a
        // player error is showing — the button is meaningless when
        // playback failed, and at BottomEnd + 170dp up it lands on top
        // of the centered error text and the Retry / Next source
        // buttons (reported 2026-11-23: Skip intro pill overlapping
        // the error screen).
        if (!endCardVisible && state.playerError == null) {
            state.activeSegment?.let { seg ->
                if (!state.skipDismissed && (!state.skipAutoHidden || state.showControls)) {
                    Box(
                        Modifier.fillMaxSize().padding(end = 24.dp, bottom = 170.dp).zIndex(1f),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 320.dp)
                                // Size to the Row's content width: without
                                // this, the 3dp progress bar's fillMaxWidth()
                                // stretches the whole pill to the 320dp cap,
                                // leaving a wide empty glass slab with the
                                // content huddled on the left (owner's
                                // on-device report: Skip button mis-padded).
                                .width(IntrinsicSize.Max)
                                .clip(RoundedCornerShape(16.dp))
                                .playerLiquidGlass(
                                    shape = RoundedCornerShape(16.dp),
                                )
                                .clickable {
                                    state.autoSkippedSegments = state.autoSkippedSegments + seg
                                    activeSeekTo(seg.endMs + 400)
                                    state.activeSegment = null
                                }
                        ) {
                            Row(
                                Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    seg.type.buttonLabel(),
                                    color = TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                IconButton(
                                    onClick = { state.skipDismissed = true },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "Dismiss", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (!state.showControls) {
                                Box(
                                    Modifier.fillMaxWidth().height(3.dp)
                                        .background(Color.White.copy(alpha = 0.15f))
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth(skipProgress.value).height(3.dp)
                                            .background(Color.White.copy(alpha = 0.85f))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Next-episode countdown card (Nuvio NextEpisodeCard): bottom-end,
        // above the controls. Counts 10…1, then plays the next episode;
        // Cancel stops the auto-advance for this episode.
        state.nextEpisodeCard?.let { next ->
            Box(
                Modifier.fillMaxSize().padding(end = 24.dp, bottom = 170.dp).zIndex(1f),
                contentAlignment = Alignment.BottomEnd
            ) {
                Column(
                    Modifier
                        .widthIn(max = 292.dp)
                        .clip(RoundedCornerShape(16.dp))
                        // Nuvio NextEpisodeCard glass: tint #191919 0.64,
                        // fallback #191919 0.89, blur 20dp, refraction
                        // 10/14dp, 1dp White 0.12 border — not flat dark.
                        .ambientLiquidGlass(
                            shape = RoundedCornerShape(16.dp),
                            tint = Color(0xFF191919).copy(alpha = 0.64f),
                            fallback = Color(0xFF191919).copy(alpha = 0.89f),
                            blurRadius = 20.dp,
                            refractionHeight = 10.dp,
                            refractionAmount = 14.dp,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Up next",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = {
                                state.nextEpisodeDismissedFor = state.currentRequest.videoId
                                state.endedHandledFor = state.currentRequest.videoId
                                state.nextEpisodeCard = null
                                state.nextEpisodeCountdown = null
                                state.prefetchedNextStreams = null
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Cancel", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(
                        "S${next.season}E${next.episode} · ${next.title}",
                        color = TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = {
                                state.endedHandledFor = state.currentRequest.videoId
                                state.nextEpisodeCard = null
                                state.nextEpisodeCountdown = null
                                playEpisode(next)
                            }
                        ) {
                            Text("Play now")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Playing in ${state.nextEpisodeCountdown ?: NEXT_EPISODE_COUNTDOWN_SECONDS}s",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // Movie recommendations card (Nuvio MovieRecommendationCard):
        // "More like this" posters after a movie ends. Auto-hides 10s
        // after appearing while the controls are hidden; showing the
        // controls brings it back.
        if (state.movieRecs.isNotEmpty() && !state.movieRecsDismissed && !state.movieRecsAutoHidden) {
            Box(
                Modifier.fillMaxSize().padding(bottom = 170.dp).zIndex(1f),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        // Nuvio MovieRecommendationCard glass: same recipe
                        // as the next-episode card (tint #191919 0.64,
                        // fallback 0.89, blur 20dp, refraction 10/14dp,
                        // 1dp White 0.12 border) — not flat dark.
                        .ambientLiquidGlass(
                            shape = RoundedCornerShape(16.dp),
                            tint = Color(0xFF191919).copy(alpha = 0.64f),
                            fallback = Color(0xFF191919).copy(alpha = 0.89f),
                            blurRadius = 20.dp,
                            refractionHeight = 10.dp,
                            refractionAmount = 14.dp,
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "More like this",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        IconButton(
                            onClick = { state.movieRecsDismissed = true },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Dismiss", tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.movieRecs, key = { it.id }) { meta ->
                            PosterCard(
                                meta = meta,
                                onClick = {
                                    val addonId = state.movieRecsAddonId ?: return@PosterCard
                                    onOpenDetail(addonId, meta.type, meta.id)
                                },
                                surface = CustomPosterScreen.DETAILS
                            )
                        }
                    }
                }
            }
        }

        // Locked overlay — Nuvio legacy: full-screen, bottom scrim 220dp,
        // center glass lock circle (72dp, icon 34dp) + "Tap to unlock",
        // disabled slider + time pills below. Tap the circle to unlock.
        if (state.locked && state.lockedOverlayVisible) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))
                            )
                        )
                )
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .clickable {
                            state.locked = false
                            state.showControls = true
                            state.controlsTick++
                            pokeIndicator(PlayerIndicator.Message("Controls unlocked"))
                        }
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(72.dp)
                            .playerLiquidGlass(
                                shape = CircleShape,
                                refractionHeight = 6.dp,
                                refractionAmount = 8.dp,
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            "Unlock controls",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tap to unlock",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.95f)
                    )
                }
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 16.dp)
                ) {
                    LegacySeekBar(
                        positionMs = state.positionMs,                        durationMs = state.durationMs,                        bufferedMs = state.bufferedMs,                        chapterFractions = state.chapters.chapterMarkFractions(state.durationMs),
                        onSeek = {},
                        enabled = false,
                        isTv = isTv
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TimeChip(formatMs(state.positionMs))
                        TimeChip(formatMs(state.durationMs))
                    }
                }
            }
        }

        // Pause metadata overlay (Nuvio PauseMetadataOverlay): while
        // paused, the title/episode/provider/description show big over a
        // gradient, under the transport controls so they stay usable.
        // Hidden until playback has actually started, on error, and in PiP.
        // Description is the current episode's overview (series only —
        // movies carry no overview in the request). Suppressed while
        // the v0.14.30 pause screen (#45) is switched on — the two
        // would otherwise stack a dimmed title block over a dimmed
        // title block; the switch defaults OFF, so this is unchanged
        // unless the user opts into the new screen.
        if (!settings.pauseScreenEnabled && !state.isPlaying && hasStartedPlayback && state.playerError == null && !inPip) {
            val pauseDescription = if (!req.isMovie) {
                req.episodes.firstOrNull { it.season == req.season && it.episode == req.episode }?.overview
            } else null
            PauseMetadataOverlay(
                title = headerTitle,
                episodeCode = if (!req.isMovie) "S${req.season} E${req.episode}" else null,
                episodeTitle = req.episodeTitle,
                providerName = sourceNameText,
                description = pauseDescription
            )
        }

        // Pause screen (v0.14.30 port, feature #45): sits UNDER the
        // controls overlay in z-order and carries no gesture handlers
        // of its own — a tap falls through to the surface's tap
        // layer, wakes the controls, and this hides in the same
        // recomposition.
        AnimatedVisibility(
            visible = pauseScreenVisible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(150))
        ) {
            PauseScreenOverlay(req = state.currentRequest)
        }

        // Controls overlay.
        AnimatedVisibility(
            visible = state.showControls && !state.locked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(Modifier.fillMaxSize().tapGestures()
                .then(if (isTv) Modifier.onFocusChanged { state.transportFocused = it.hasFocus } else Modifier)) {
                // Scrims (Nuvio legacy: top 160dp, bottom 220dp, black 0.35
                // with glass controls) — behind the controls, fading with them.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent)
                            )
                        )
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))
                            )
                        )
                )
                // ---- Top: title block + icon cluster (Nuvio legacy
                // PlayerHeader — no container; title sits on the scrim) ----
                Row(
                    Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(top = 8.dp, start = 20.dp, end = 20.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(Modifier.weight(1f).padding(end = if (settings.playerStatusOverlayEnabled) 36.dp else 12.dp)) {
                        Text(
                            headerTitle,
                            color = Color.White,
                            fontSize = 18.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (episodeLine != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                episodeLine,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (streamLabelText != null || sourceNameText != null) {
                            Spacer(Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                streamLabelText?.let {
                                    Text(
                                        it,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                sourceNameText?.let {
                                    Text(
                                        it,
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        fontStyle = FontStyle.Italic,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        // Step 61: matched image badges for the playing stream.
                        if (playerBadges.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            PlayerBadgesRow(badges = playerBadges)
                        }
                    }
                    // Top-right cluster, Nuvio legacy order (only the actions
                    // Avio has — no cast/party/engine-switch/rate;
                    // video settings is iOS-only in Nuvio). Step 63: the
                    // order/visibility follow the user's toolbar layout.
                    // Back is last, always.
                    Row(
                        modifier = Modifier.padding(start = if (settings.playerStatusOverlayEnabled) 36.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        topButtonLayout.visibleButtons.forEach { button ->
                            when (button) {
                                com.aviostream.app.data.PlayerTopButton.PLAYBACK_INFO -> {
                                    PlayerHeaderButton(
                                        icon = Icons.Filled.Info,
                                        label = "Playback info",
                                        onClick = { state.infoOpen = true }
                                    )
                                }
                                com.aviostream.app.data.PlayerTopButton.EXTERNAL_PLAYER -> {
                                    PlayerHeaderButton(
                                        icon = Icons.Filled.ExitToApp,
                                        label = "Open in external player",
                                        onClick = {
                                            val ctx = activity ?: return@PlayerHeaderButton
                                            // Protected links warn once (owner's order
                                            // 2026-10-26) instead of the old hard block;
                                            // normal links open at once.
                                            if (ExternalPlayer.needsWarning(ctx, req)) {
                                                state.externalWarningHide = false
                                                state.externalWarningReq = req
                                            } else {
                                                launchExternalPlayer(ctx, req)
                                            }
                                        }
                                    )
                                }
                                com.aviostream.app.data.PlayerTopButton.ROTATE -> {
                                    PlayerHeaderButton(
                                        icon = if (state.isPortrait) Icons.Filled.StayCurrentLandscape
                                        else Icons.Filled.StayCurrentPortrait,
                                        label = "Rotate",
                                        onClick = {
                                            state.isPortrait = !state.isPortrait
                                            activity?.requestedOrientation = if (state.isPortrait)
                                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            else
                                                // SENSOR_LANDSCAPE: both landscape
                                                // directions follow the sensor.
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        }
                                    )
                                }
                                com.aviostream.app.data.PlayerTopButton.LOCK -> {
                                    PlayerHeaderButton(
                                        icon = Icons.Filled.LockOpen,
                                        label = "Lock controls",
                                        onClick = {
                                            state.locked = true
                                            state.showControls = false
                                            state.lockedOverlayVisible = true
                                            pokeIndicator(PlayerIndicator.Message("Controls locked"))
                                        }
                                    )
                                }
                            }
                        }
                        PlayerHeaderButton(
                            icon = Icons.Filled.ArrowBack,
                            label = "Back",
                            modifier = Modifier.then(act("back") { onBack() }),
                            onClick = { onBack() }
                        )
                    }
                }

                // Device-status strip (Nuvio PlayerDeviceStatusOverlay):
                // top-centre pill, only when the status overlay is on
                // (default off in Nuvio). The header leaves 36dp clear
                // of it on both sides (see the title/icon paddings above).
                // Remember the device status unconditionally (as in build
                // 34): making this remember call conditional on the
                // overlay setting produced a VerifyError in build 35 —
                // the giant PlayerScreen method failed dex verification
                // on the phone (copy of a MutableState into the wrong
                // register). The strip itself still renders only when
                // the overlay setting is on.
                val deviceStatus = rememberPlayerDeviceStatus()
                if (settings.playerStatusOverlayEnabled) {
                    PlayerDeviceStatusStrip(
                        status = deviceStatus,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 4.dp)
                    )
                }

                // ---- Center transport (Nuvio legacy: 56dp gap, 10dp lift,
                // 34dp play icon in a glass circle, 34dp skip icons) ----
                Row(
                    Modifier.align(Alignment.Center).padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Live channel zap — no Nuvio legacy equivalent (its
                    // channels live in a panel); kept so live keeps working.
                    if (canZap()) {
                        CenterControlButton(
                            Icons.Filled.SkipPrevious, "Previous channel",
                            onClick = { zapDelta(-1) }, enabled = !state.zapping
                        )
                    }
                    if (!state.currentRequest.isLive) {
                        CenterControlButton(
                            label = "Back ${settings.skipBackwardSeconds} seconds",
                            onClick = { seekByWithFeedback(-settings.skipBackwardSeconds * 1000L) },
                            modifier = Modifier.then(act("rew") { seekByWithFeedback(-settings.skipBackwardSeconds * 1000L) })
                        ) {
                            SkipStepIcon(settings.skipBackwardSeconds, forward = false)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .playerLiquidGlass(
                                shape = CircleShape,
                                refractionHeight = 10.dp,
                                refractionAmount = 14.dp,
                            )
                            .clip(CircleShape)
                            .clickable(onClick = { togglePlay() })
                            .focusRequester(playPauseFocus)
                            .then(act("play") { togglePlay() })
                            .tvFocusRing(CircleShape)
                            .padding(13.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Nuvio legacy: a loading spinner replaces the icon
                        // while buffering.
                        if (state.isBuffering) {
                            OrbitDots(Modifier.size(34.dp))
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                if (state.isPlaying) "Pause" else "Play",
                                tint = Color.White, modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                    if (!state.currentRequest.isLive) {
                        CenterControlButton(
                            label = "Forward ${settings.skipForwardSeconds} seconds",
                            onClick = { seekByWithFeedback(settings.skipForwardSeconds * 1000L) },
                            modifier = Modifier.then(act("fwd") { seekByWithFeedback(settings.skipForwardSeconds * 1000L) })
                        ) {
                            SkipStepIcon(settings.skipForwardSeconds, forward = true)
                        }
                    }
                    if (canZap()) {
                        CenterControlButton(
                            Icons.Filled.SkipNext, "Next channel",
                            onClick = { zapDelta(1) }, enabled = !state.zapping
                        )
                    }
                }

                // The action pill is shared by the live and on-demand
                // bottom blocks — Nuvio legacy order: PiP, Quality, Subs,
                // Audio, Resize, Speed, Sources, Chapters, Next episode,
                // Episodes, Live channels.
                // Nuvio legacy pill: a Next-episode button when a next
                // episode exists (independent of autoplay-next — same as
                // Nuvio's nextEpisodeButtonEnabled gate with it on).
                val pillNextEpisode = if (
                    !req.isMovie && !state.currentRequest.isTrailer &&
                    state.currentRequest.episodes.isNotEmpty()
                ) {
                    resolveNextEpisode(
                        state.currentRequest.episodes,
                        state.currentRequest.season,
                        state.currentRequest.episode,
                        state.currentRequest.videoId,
                        settings.shuffleEpisodes
                    )
                } else null
                // Bottom action pill: when extra options appear the icons
                // must NOT shrink — instead the row slides (scrolls)
                // horizontally. BoxWithConstraints caps the pill at the
                // available width so the inner Row can scroll; with few
                // buttons the pill still wraps its content and stays
                // centered, like Nuvio's.
                val actionPill: @Composable () -> Unit = {
                    val palette = LocalPalette.current
                    BoxWithConstraints(
                        Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .widthIn(max = maxWidth)
                                .playerLiquidGlass(
                                    shape = RoundedCornerShape(24.dp),
                                    refractionHeight = 10.dp,
                                    refractionAmount = 14.dp,
                                )
                        ) {
                            Row(
                                Modifier
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            // Step 63: pill buttons follow the user's toolbar
                            // layout (order + visibility) — each button keeps
                            // its own availability gate.
                            bottomButtonLayout.visibleButtons.forEach { button ->
                                when (button) {
                                    com.aviostream.app.data.PlayerBottomButton.PIP -> {
                                        if (settings.pipEnabled) {
                                            PillBarButton(
                                                icon = Icons.Filled.PictureInPicture,
                                                label = "PiP",
                                                onClick = { enterPip() }
                                            )
                                        }
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.QUALITY -> {
                                        PillBarButton(
                                            icon = Icons.Filled.HighQuality,
                                            label = streamLabelText ?: "Quality",
                                            onClick = {
                                                // Fresh fetch when opening Quality — ensures full list, not stale partial
                                                refreshStreamsBackground(state.currentRequest.type, state.currentRequest.videoId)
                                                state.qualityOpen = true
                                            }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.SUBTITLES -> {
                                        PillBarButton(
                                            icon = Icons.Filled.ClosedCaption,
                                            label = "Subs",
                                            onClick = {
                                                state.trackTick++
                                                state.tracksMode = "subs"
                                                state.tracksOpen = true
                                            }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.AUDIO -> {
                                        PillBarButton(
                                            icon = Icons.Filled.Audiotrack,
                                            label = "Audio",
                                            onClick = { state.trackTick++; state.tracksMode = "audio"; state.tracksOpen = true }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.CHAPTERS -> {
                                        if (state.chapters.isNotEmpty()) {
                                            PillBarButton(
                                                icon = Icons.Filled.List,
                                                label = "Chapters",
                                                onClick = { state.chaptersOpen = true }
                                            )
                                        }
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.NEXT_EPISODE -> {
                                        val next = pillNextEpisode
                                        if (next != null) {
                                            PillBarButton(
                                                icon = Icons.Filled.SkipNext,
                                                label = "Next episode",
                                                onClick = {
                                                    // Cancel the auto-advance countdown so it
                                                    // doesn't re-play the same episode after
                                                    // the manual tap.
                                                    state.nextEpisodeCard = null
                                                    state.nextEpisodeCountdown = null
                                                    playEpisode(next)
                                                }
                                            )
                                        }
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.RESIZE -> {
                                        PillBarButton(
                                            icon = Icons.Filled.AspectRatio,
                                            label = aspectLabel,
                                            onClick = { cycleAspect() }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.SPEED -> {
                                        SpeedMenuButton(
                                            speed = state.speed,
                                            onClick = { state.speedMenuOpen = true }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.SLEEP -> {
                                        SleepTimerButton(
                                            label = sleepTimerLabel,
                                            onClick = { state.sleepMenuOpen = true }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.SOURCES -> {
                                        PillBarButton(
                                            icon = Icons.Filled.CloudDownload,
                                            label = "Sources",
                                            onClick = {
                                                // Fresh fetch when opening Sources — ensures full list, not stale partial
                                                refreshStreamsBackground(state.currentRequest.type, state.currentRequest.videoId)
                                                state.sourcesOpen = true
                                            }
                                        )
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.EPISODES -> {
                                        if (!req.isMovie && req.episodes.isNotEmpty()) {
                                            PillBarButton(
                                                icon = Icons.Filled.VideoLibrary,
                                                label = "Episodes",
                                                onClick = { state.episodesOpen = true }
                                            )
                                        }
                                    }
                                    com.aviostream.app.data.PlayerBottomButton.LIVE_CHANNELS -> {
                                        if (state.currentRequest.isLive && canZap()) {
                                            PillBarButton(
                                                icon = Icons.Filled.Tv,
                                                label = "Channels",
                                                onClick = { state.channelsOpen = true }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                }

                // ---- Bottom: end-time row, seek bar, time pills + action
                // pill (Nuvio legacy ProgressControls — no container) ----
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 13.dp)
                ) {
                    if (state.currentRequest.isLive) {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = Color(0xFFE5484D),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "LIVE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Text(
                                state.currentRequest.title,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                when { state.switchingMedia -> "Switching…"; state.isBuffering -> "Buffering…"; else -> "On air" },
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            actionPill()
                        }
                    } else {
                        // Chapter + end-time row: the current chapter title
                        // on the left (tap opens the Chapters panel), the
                        // "Ends at" time on the right.
                        Row(
                            Modifier.fillMaxWidth().offset(y = (-19).dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            val chapterIndex = state.chapters.currentChapterIndex(state.positionMs)
                            val chapterTitle = state.chapters.getOrNull(chapterIndex)?.title?.takeIf { it.isNotBlank() }
                                ?: if (chapterIndex >= 0) "Chapter ${chapterIndex + 1}" else null
                            if (chapterTitle != null) {
                                Text(
                                    chapterTitle,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .padding(horizontal = 2.dp, vertical = 2.dp)
                                        .clickable { state.chaptersOpen = true }
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            endsAtText?.let {
                                Text(
                                    it,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                                )
                            }
                        }
                        // Step 59: the seek-preview bubble floats above
                        // the bar while scrubbing. Bar sits at the Box top
                        // with a 12dp gap to the time row — measured
                        // against the official screenshot (track centre
                        // ~102dp, timer ~69dp, pill ~36dp from the bottom):
                        // the old -13dp offset + 12dp padding had lifted
                        // the bar ~21dp too high, and the 16dp spacer
                        // below the timer held the whole seekbar+timer
                        // unit ~19dp too high.
                        Box(Modifier.fillMaxWidth()) {
                            SeekPreviewOverlay(
                                controller = seekPreviewController,
                                active = state.scrubPositionMs != null,
                                positionMs = state.scrubPositionMs ?: state.positionMs,                                durationMs = state.durationMs,                                modifier = Modifier.fillMaxWidth().height(22.dp)
                            )
                            LegacySeekBar(
                                positionMs = state.positionMs,                                durationMs = state.durationMs,                                bufferedMs = state.bufferedMs,                                chapterFractions = state.chapters.chapterMarkFractions(state.durationMs),
                                onSeek = { activeSeekTo(it) },
                                modifier = Modifier.padding(bottom = 12.dp),
                                isTv = isTv,
                                onSeekDelta = { seekByWithFeedback(it) },
                                onFocusChanged = { state.seekbarFocused = it },
                                onScrub = { state.scrubPositionMs = it }
                            )
                        }
                        // Time pills on their own row (left = current,
                        // right = total/remaining), action pill on its own
                        // row below — stacked like Nuvio, so a wide action
                        // pill (many buttons) can never overlap the time
                        // pills. They used to share one Box at the same
                        // offset, which is why the timing layout overlapped
                        // once the pill grew wide.
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TimeChip(formatMs(state.positionMs))
                                TimeChip(
                                    if (state.showRemainingTime) formatRemainingMs(state.positionMs, state.durationMs)
                                    else formatMs(state.durationMs),
                                    onClick = { state.showRemainingTime = !state.showRemainingTime }
                                )
                            }
                            // No spacer between the time row and the pill:
                            // Nuvio's timer sits right on the pill's top
                            // edge (its bottom edge even overlaps the pill
                            // by ~2dp — different x, so no collision).
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                actionPill()
                            }
                        }
                    }
                }
            }
        }

        // External-player warning for protected links (owner's order
        // 2026-10-26 — replaces the old hard block): the link leaves
        // the app without its protected headers, so the outside player
        // may not be able to play it. "Don't show again" persists.
        state.externalWarningReq?.let { warnReq ->
            val warnCtx = activity
            // Nuvio-style centered glass dialog (PlayerCenteredDialog) —
            // not a flat Material3 AlertDialog.
            com.aviostream.app.ui.screens.player.PlayerCenteredDialog(
                onDismiss = { state.externalWarningReq = null }
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Protected link",
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "This source uses protected headers, which stay in Avio — " +
                            "the other app may not be able to play this link.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { state.externalWarningHide = !state.externalWarningHide }
                    ) {
                        Checkbox(
                            checked = state.externalWarningHide,
                            onCheckedChange = { state.externalWarningHide = it }
                        )
                        Text(
                            "Don't show again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { state.externalWarningReq = null }) {
                            Text("Cancel", color = TextSecondary)
                        }
                        TextButton(onClick = {
                            if (state.externalWarningHide && warnCtx != null) {
                                ExternalPlayer.setWarningSuppressed(warnCtx, true)
                            }
                            state.externalWarningReq = null
                            if (warnCtx != null) launchExternalPlayer(warnCtx, warnReq)
                        }) {
                            Text("Open anyway", color = LocalPalette.current.accent)
                        }
                    }
                }
            }
        }

        // Resume prompt.
        state.resumeOfferMs?.let { saved ->
            ResumePromptDialog(
                savedMs = saved,
                title = headerTitle,
                onResume = {
                    // Tapping outside keeps their place and resumes.
                    state.resumeOfferMs = null
                    if (mpvActive()) mpvEngine?.setPaused(false) else player.playWhenReady = true
                },
                onStartOver = {
                    state.resumeOfferMs = null
                    ioScope.launch { repository.deleteSavedPosition(state.currentRequest.streamUrl) }
                    if (mpvActive()) {
                        activeSeekTo(0)
                        mpvEngine?.setPaused(false)
                    } else {
                        player.seekTo(0)
                        player.playWhenReady = true
                    }
                }
            )
        }

        // Chapters panel (Nuvio PlayerChaptersPanel): a RIGHT-side panel
        // (420dp, full height), not a bottom card. Header 'Chapters'
        // (headlineSmall) + Close button; rows spaced 6dp, current row =
        // White 0.15 bg + 1dp accent-0.75 border, others = 0xFF222222 bg
        // + 1dp borderSubtle border; time is muted 0xFF969CA3 when not
        // current. Tap seeks to the chapter and closes.
        if (state.chaptersOpen) {
            com.aviostream.app.ui.screens.player.PlayerSidePanel(
                onDismiss = { state.chaptersOpen = false },
                widthDp = 420.dp
            ) {
                val currentIndex = state.chapters.currentChapterIndex(state.positionMs)
                val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (currentIndex - 1).coerceAtLeast(0)
                )
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Chapters",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        com.aviostream.app.ui.screens.player.PlayerDialogCloseButton(
                            onClick = { state.chaptersOpen = false }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(state.chapters, key = { index, chapter -> "chap_${index}_${chapter.startMs}" }) { index, chapter ->
                            val isCurrent = index == currentIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isCurrent) Color.White.copy(alpha = 0.15f) else Color(0xFF222222))
                                    .border(
                                        width = 1.dp,
                                        color = if (isCurrent) LocalPalette.current.accent.copy(alpha = 0.75f) else Color(0xFF252A2A),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        state.chaptersOpen = false
                                        activeSeekTo(chapter.startMs)
                                        pokeIndicator(PlayerIndicator.Message(chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}"))
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    formatMs(chapter.startMs),
                                    color = if (isCurrent) LocalPalette.current.accent else Color(0xFF969CA3),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${index + 1}",
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isCurrent) {
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Playing",
                                        color = LocalPalette.current.accent,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Track switching: the Subs button opens the subtitle overlay
        // (v0.14.5, Nuvio-style three rails over the playing video); the
        // Audio button keeps its bottom sheet. Neither is an
        // AlertDialog, so the root media/channel-key handler keeps
        // working while they're open (#28).
        if (state.tracksOpen) {
            val isAudio = state.tracksMode == "audio"
            // TV: land D-pad focus on the first row when the picker opens.
            val firstRowFocus = remember { FocusRequester() }
            LaunchedEffect(isTv, state.tracksMode) {
                if (state.tracksMode == "subs") {
                    // Seed the Style rail's visibility from the track
                    // that is already playing when the overlay opens.
                    state.panelPickedUrl = activeSidecarUrl()
                        ?: textOpts.firstOrNull { it.selected }
                            ?.let { panelTrackId(it) }
                }
                if (isTv) {
                    delay(300)
                    runCatching { firstRowFocus.requestFocus() }
                }
            }
            if (isAudio) {
                // Nuvio AudioTrackModal layout: full-screen gradient scrim,
                // NO card — the rail sits directly on the video, bottom-left,
                // max 444dp wide (content padding 44/44/28/64). Wide
                // (>=760dp): tracks left + adjustments right, side by side.
                com.aviostream.app.ui.screens.player.PlayerOverlayScaffold(
                    onDismiss = { state.tracksOpen = false },
                    contentPadding = PaddingValues(start = 44.dp, end = 44.dp, top = 28.dp, bottom = 64.dp)
                ) {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val railWidth = minOf(maxWidth, 444.dp)
                        val railMaxHeight = (maxHeight - 64.dp).coerceAtLeast(120.dp).coerceAtMost(620.dp)
                        val panelMaxHeight = maxHeight
                        // Adjustments: delay + amplification (+ keep chip) +
                        // downmix (Avio's 5.1 mechanism). Each section gets
                        // 12dp above it, like Nuvio's sections.
                        val adjustments: @Composable () -> Unit = {
                            // Audio delay (Nuvio AudioDelaySection): a stepper,
                            // 25ms steps, +/-60s. A positive delay means the
                            // audio plays later than the video.
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Audio delay",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    StyleStepper(
                                        value = formatAudioDelay(state.audioDelayMs),
                                        onMinus = { setAudioDelayMs(state.audioDelayMs - AUDIO_DELAY_STEP_MS) },
                                        onPlus = { setAudioDelayMs(state.audioDelayMs + AUDIO_DELAY_STEP_MS) }
                                    )
                                    AudioResetChip(
                                        enabled = state.audioDelayMs != 0,
                                        onClick = { setAudioDelayMs(0) }
                                    )
                                }
                            }
                            // Amplification (Nuvio AudioAmplificationSection):
                            // a dB stepper on the existing LoudnessEnhancer —
                            // the value persists and applies on the next title.
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Amplification",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val boostDb = (state.volumeBoost * 10).roundToInt().coerceIn(0, 10)
                                    StyleStepper(
                                        value = if (boostDb > 0) "+$boostDb dB" else "0 dB",
                                        onMinus = { setAudioBoostDb(boostDb - 1) },
                                        onPlus = { setAudioBoostDb(boostDb + 1) }
                                    )
                                    com.aviostream.app.ui.screens.player.AudioToggleChip(
                                        label = "Keep for next videos",
                                        checked = settings.keepAudioBoost,
                                        onCheckedChange = { settings.keepAudioBoost = it }
                                    )
                                }
                            }
                            // Step 66 (Nuvio downmix + centre-mix): mix
                            // surround sound down to stereo/mono. The toggle
                            // + target apply on the next video (the channel
                            // count is negotiated when the player is built);
                            // centre level + keep-original apply live.
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    "Downmix",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Mix surround down",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Switch(
                                        checked = state.downmixOn,
                                        onCheckedChange = { setDownmixEnabled(it) }
                                    )
                                }
                                if (state.downmixOn) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)
                                    ) {
                                        FilterChip(
                                            selected = state.downmixTarget == 2,
                                            onClick = { setDownmixTarget(2) },
                                            label = { Text("Stereo") }
                                        )
                                        FilterChip(
                                            selected = state.downmixTarget == 1,
                                            onClick = { setDownmixTarget(1) },
                                            label = { Text("Mono") }
                                        )
                                    }
                                    Text(
                                        "Centre mix",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        StyleStepper(
                                            value = if (state.centerMixDb > 0) "+${state.centerMixDb} dB" else "${state.centerMixDb} dB",
                                            onMinus = { setCenterMixDb(state.centerMixDb - 1) },
                                            onPlus = { setCenterMixDb(state.centerMixDb + 1) }
                                        )
                                        AudioResetChip(
                                            enabled = state.centerMixDb != 0,
                                            onClick = { setCenterMixDb(0) }
                                        )
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            "Keep original levels",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Switch(
                                            checked = state.keepOriginalLevels,
                                            onCheckedChange = { setKeepOriginalLevels(it) }
                                        )
                                    }
                                }
                                Text(
                                    "Mixes 5.1/7.1 audio so dialogue stays clear on stereo output. Toggle + target apply on the next video; centre level applies live.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 12.dp)
                                )
                            }
                        }
                        // Tracks column (Nuvio tracksColumn): header + the
                        // track list, bottom-anchored; the list scrolls on
                        // its own when tall.
                        val tracksColumn: @Composable (Modifier) -> Unit = { columnModifier ->
                            Column(
                                modifier = columnModifier,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Text(
                                    "Audio Tracks",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f, fill = false)
                                        .heightIn(max = railMaxHeight),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 8.dp)
                                ) {
                                    // Tracks FIRST (Nuvio AudioTrackModal order) —
                                    // full accent fill + checkmark, not a RadioButton.
                                    item {
                                        com.aviostream.app.ui.screens.player.AudioTrackRow(
                                            label = "Auto",
                                            language = null,
                                            selected = audioOpts.none { it.selected },
                                            onClick = { applyAudioSelection(null) },
                                            modifier = Modifier.focusRequester(firstRowFocus)
                                        )
                                    }
                                    items(audioOpts) { opt ->
                                        com.aviostream.app.ui.screens.player.AudioTrackRow(
                                            label = opt.label,
                                            language = opt.language,
                                            selected = opt.selected,
                                            onClick = { applyAudioSelection(opt) }
                                        )
                                    }
                                }
                            }
                        }
                        if (maxWidth >= 760.dp) {
                            // Wide enough: tracks on the left and adjustments
                            // on the right, so neither squeezes the other.
                            val columnWidth = minOf((maxWidth - 32.dp) / 2, 444.dp)
                            Row(
                                modifier = Modifier.align(Alignment.BottomStart),
                                horizontalArrangement = Arrangement.spacedBy(32.dp),
                                verticalAlignment = Alignment.Bottom
                            ) {
                                tracksColumn(
                                    Modifier
                                        .width(columnWidth)
                                        .heightIn(max = panelMaxHeight)
                                )
                                Column(
                                    modifier = Modifier
                                        .width(columnWidth)
                                        .heightIn(max = panelMaxHeight)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    adjustments()
                                }
                            }
                        } else {
                            // One rail that scrolls as a whole, so the tracks
                            // keep their size next to the adjustments.
                            Column(
                                modifier = Modifier
                                    .width(railWidth)
                                    .heightIn(max = panelMaxHeight)
                                    .align(Alignment.BottomStart)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    "Audio Tracks",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = Color.White,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    com.aviostream.app.ui.screens.player.AudioTrackRow(
                                        label = "Auto",
                                        language = null,
                                        selected = audioOpts.none { it.selected },
                                        onClick = { applyAudioSelection(null) },
                                        modifier = Modifier.focusRequester(firstRowFocus)
                                    )
                                    audioOpts.forEach { opt ->
                                        com.aviostream.app.ui.screens.player.AudioTrackRow(
                                            label = opt.label,
                                            language = opt.language,
                                            selected = opt.selected,
                                            onClick = { applyAudioSelection(opt) }
                                        )
                                    }
                                }
                                adjustments()
                            }
                        }
                    }
                }

            } else {
                // ===== Subtitle overlay (v0.14.5, owner's approved
                // design): a translucent full-player overlay — the video
                // keeps playing behind it and every style change lands
                // on the real captions live. Three side-by-side rails in
                // a horizontally scrollable row: Languages | Subtitles |
                // Style (the Style rail fades in only once a subtitle
                // is selected). =====
                val langs = panelLanguages()
                // Preferred languages first (Settings > Subtitles),
                // stable for the rest.
                val prefLangNames = settings.subtitleLanguages.map { panelLangName(it) }
                val sortedLangs = langs.sortedBy { (name, _) ->
                    prefLangNames.indexOf(name).let { if (it < 0) Int.MAX_VALUE else it }
                }
                val activeLang = state.panelLang?.takeIf { ln -> sortedLangs.any { it.first == ln } }
                    ?: activeSidecarUrl()?.let { url ->
                        panelSubs().firstOrNull { it.url == url }?.let { panelLangName(it.lang) }
                    }
                    ?: textOpts.firstOrNull { it.selected }?.let {
                        panelLangName(
                            runCatching { it.group.getFormat(it.trackIndex).language }
                                .getOrNull()
                        )
                    }
                    ?: sortedLangs.firstOrNull()?.first
                val langSubs = panelSubs().filter { panelLangName(it.lang) == activeLang }
                val langEmbedded = panelEmbeddedOpts().filter {
                    panelLangName(
                        runCatching { it.group.getFormat(it.trackIndex).language }
                            .getOrNull()
                    ) == activeLang
                }
                val selectedSubUrl = activeSidecarUrl()
                    ?: textOpts.firstOrNull { it.selected }
                        ?.let { panelTrackId(it) }
                val selectedIsAddon = selectedSubUrl != null &&
                    panelSubs().any { it.url == selectedSubUrl }
                val styleVisible = !subtitlesOff || state.panelPickedUrl != null
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val railHeight = if (maxHeight < 400.dp) 236.dp else 300.dp
                    // Scrim: dims the video; a tap outside the rails
                    // dismisses the overlay.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { state.tracksOpen = false }
                    )
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
                    ) {
                        Text(
                            "Subtitles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // ---- Languages rail (200dp) ----
                            // Nuvio's rails are bare columns — no card
                            // background, radius or border; the rows sit
                            // on the overlay scrim.
                            Column(Modifier.width(200.dp)) {
                                Text(
                                    "Languages",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF969CA3),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(railHeight)
                                        .padding(10.dp)
                                ) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        item {
                                            RailLanguageRow(
                                                name = "None",
                                                count = null,
                                                selected = subtitlesOff && state.panelPickedUrl == null,
                                                modifier = Modifier.focusRequester(firstRowFocus),
                                                onClick = {
                                                    state.panelPickedUrl = null
                                                    applySubtitleSelection(null)
                                                }
                                            )
                                        }
                                        items(sortedLangs) { (name, count) ->
                                            RailLanguageRow(
                                                name = name,
                                                count = count,
                                                selected = name == activeLang,
                                                onClick = { state.panelLang = name }
                                            )
                                        }
                                    }
                                }
                            }
                            // ---- Subtitles rail (300dp) ----
                            Column(Modifier.width(300.dp)) {
                                Text(
                                    "Subtitles",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color(0xFF969CA3),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(railHeight)
                                        .padding(10.dp)
                                ) {
                                    LazyColumn(
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                    if (activeLang == null && !state.subSearchLoading) {
                                        item {
                                            Text(
                                                "No subtitles for this title.",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                    items(langSubs) { sub ->
                                        val resolvedSub =
                                            state.rawResolvedSubs.firstOrNull { it.sub.url == sub.url }
                                        PanelSubCard(
                                            chip = panelHostLabel(sub.url),
                                            title = panelFileLabel(sub),
                                            detail = listOfNotNull(
                                                panelLangName(sub.lang),
                                                resolvedSub?.let { panelFormatLabel(it.mimeType) },
                                                if (sub.url.contains("forced", ignoreCase = true)) {
                                                    "Forced"
                                                } else {
                                                    null
                                                }
                                            ).joinToString(" \u00b7 "),
                                            selected = selectedSubUrl == sub.url,
                                            onClick = {
                                                state.panelPickedUrl = sub.url
                                                activatePanelSubtitle(sub)
                                            }
                                        )
                                    }
                                    items(langEmbedded) { opt ->
                                        PanelSubCard(
                                            chip = "Built-in",
                                            title = opt.label,
                                            detail = listOfNotNull(
                                                activeLang,
                                                if (opt.label.contains("forced", ignoreCase = true)) {
                                                    "Forced"
                                                } else {
                                                    null
                                                }
                                            ).joinToString(" \u00b7 "),
                                            selected = opt.selected,
                                            onClick = {
                                                state.panelPickedUrl = "built-in"
                                                applySubtitleSelection(opt)
                                            }
                                        )
                                    }
                                    if (state.subSearchLoading) {
                                        item {
                                            Text(
                                                "Searching online\u2026",
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 6.dp)
                                            )
                                        }
                                    }
                                    if (langSubs.isEmpty() && langEmbedded.isEmpty() &&
                                        !state.subSearchLoading
                                    ) {
                                        item {
                                            Row(
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(Color.White.copy(alpha = 0.06f))
                                                    .clickable { openSubtitleSearch() }
                                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.CloudDownload,
                                                    contentDescription = null,
                                                    tint = LocalPalette.current.accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "Tap to fetch subtitles",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary
                                                )
                                            }
                                        }
                                    }
                                    if (state.subLoadError && panelSubs().isEmpty()) {
                                        item {
                                            Text(
                                                "Couldn't load subtitles \u2014 check your connection and try again.",
                                                color = Color(0xFFEF4444),
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(vertical = 8.dp)
                                            )
                                        }
                                    }
                                    state.subSearchError?.let { err ->
                                        item {
                                            Text(
                                                err,
                                                color = TextSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(start = 4.dp)
                                            )
                                        }
                                    }
                                    if (langSubs.isNotEmpty() || langEmbedded.isNotEmpty()) {
                                        item {
                                            TextButton(onClick = { openSubtitleSearch() }) {
                                                Text(
                                                    "Fetch subtitles online",
                                                    color = LocalPalette.current.accent,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            }
                            // ---- Style rail (280dp) ----
                            AnimatedVisibility(
                                visible = styleVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Column(Modifier.width(280.dp)) {
                                    Text(
                                        "Style",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF969CA3),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(railHeight)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    // 1. Subtitle Delay
                                    StyleSectionLabel("Subtitle Delay")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = panelDelayLabel(effectiveSubOffsetMs()),
                                        onMinus = { setSubtitleDelay(state.subOffsetOverrideMs - 100L) },
                                        onPlus = { setSubtitleDelay(state.subOffsetOverrideMs + 100L) }
                                    )
                                    TextButton(
                                        onClick = { setSubtitleDelay(0L) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "Reset",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    // 1b. Sync by ear (Nuvio SubtitleSyncByEarCard):
                                    // tap when you hear a line, tap when its
                                    // subtitle shows — the offset is computed
                                    // and applied as the per-video delay.
                                    StyleSectionLabel("Sync by ear")
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Tap when you hear a line and when its subtitle shows, in either order.",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SyncByEarButton(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Filled.Hearing,
                                            label = "Heard",
                                            captured = state.syncByEarHeardMs != null,
                                            onClick = { captureSyncByEarHeard() }
                                        )
                                        SyncByEarButton(
                                            modifier = Modifier.weight(1f),
                                            icon = Icons.Filled.Visibility,
                                            label = "Saw",
                                            captured = state.syncByEarSawMs != null,
                                            onClick = { captureSyncByEarSaw() }
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = when {
                                            state.syncByEarHeardMs != null -> "Heard captured. Tap Saw when its subtitle shows."
                                            state.syncByEarSawMs != null -> "Saw captured. Tap Heard when you hear the line."
                                            state.subOffsetOverrideMs == 0L -> "Now: In sync"
                                            else -> "Now: ${panelDelayLabel(effectiveSubOffsetMs())}"
                                        },
                                        color = if (state.syncByEarHeardMs != null || state.syncByEarSawMs != null) LocalPalette.current.accent else TextSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                    Spacer(Modifier.height(10.dp))
                                    // 2. Font Size
                                    StyleSectionLabel("Font Size")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = "${state.subFontSpUi}sp",
                                        onMinus = {
                                            val v = (state.subFontSpUi - 2).coerceIn(12, 40)
                                            state.subFontSpUi = v
                                            persistSubtitleStyle { it.copy(fontSizeSp = v) }
                                            reapplySubtitleStyle()
                                        },
                                        onPlus = {
                                            val v = (state.subFontSpUi + 2).coerceIn(12, 40)
                                            state.subFontSpUi = v
                                            persistSubtitleStyle { it.copy(fontSizeSp = v) }
                                            reapplySubtitleStyle()
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 2b. Font (per-title override; the global
                                    // font stays in Settings > Subtitles).
                                    // A custom global font shows no chip
                                    // selected; picking a built-in here
                                    // overrides it for this title only.
                                    StyleSectionLabel("Font")
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.horizontalScroll(rememberScrollState())
                                    ) {
                                        listOf(
                                            "default" to "Default",
                                            "sans" to "Sans",
                                            "serif" to "Serif",
                                            "mono" to "Mono"
                                        ).forEach { (id, label) ->
                                            StyleChip(
                                                label = label,
                                                on = effectiveSubtitleStyle().fontFamily == id,
                                                onClick = {
                                                    persistSubtitleStyle { it.copy(fontFamily = id) }
                                                    reapplySubtitleStyle()
                                                }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 3. Bold
                                    StyleSectionLabel("Bold")
                                    Spacer(Modifier.height(6.dp))
                                    StyleChip(
                                        label = if (state.subBoldUi) "On" else "Off",
                                        on = state.subBoldUi,
                                        onClick = {
                                            val on = !state.subBoldUi
                                            state.subBoldUi = on
                                            persistSubtitleStyle { it.copy(bold = on) }
                                            reapplySubtitleStyle()
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 4. Color (keeps the current opacity)
                                    StyleSectionLabel("Color")
                                    Spacer(Modifier.height(6.dp))
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        items(
                                            listOf(
                                                0xFFFFFFFF, 0xFFFFD700, 0xFF00E5FF, 0xFFFF5C5C,
                                                0xFF00FF88, 0xFF9B59B6, 0xFFF97316, 0xFF22C55E,
                                                0xFF3B82F6, 0xFF000000
                                            )
                                        ) { c ->
                                            val rgb = c.toInt()
                                            StyleSwatch(
                                                color = Color(rgb),
                                                selected = (state.subColorUi and 0x00FFFFFF) ==
                                                    (rgb and 0x00FFFFFF),
                                                onClick = {
                                                    val merged =
                                                        (state.subColorUi and 0xFF000000.toInt()) or
                                                            (rgb and 0x00FFFFFF)
                                                    state.subColorUi = merged
                                                    persistSubtitleStyle { it.copy(textColor = merged) }
                                                    reapplySubtitleStyle()
                                                }
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 5. Text Opacity
                                    StyleSectionLabel("Text Opacity")
                                    Spacer(Modifier.height(6.dp))
                                    val opacityPct =
                                        (android.graphics.Color.alpha(state.subColorUi) * 100 + 127) / 255
                                    StyleStepper(
                                        value = "$opacityPct%",
                                        onMinus = {
                                            val p = (opacityPct - 10).coerceIn(0, 100)
                                            val a = (p * 255 + 50) / 100
                                            val merged = (a shl 24) or (state.subColorUi and 0x00FFFFFF)
                                            state.subColorUi = merged
                                            persistSubtitleStyle { it.copy(textColor = merged) }
                                            reapplySubtitleStyle()
                                        },
                                        onPlus = {
                                            val p = (opacityPct + 10).coerceIn(0, 100)
                                            val a = (p * 255 + 50) / 100
                                            val merged = (a shl 24) or (state.subColorUi and 0x00FFFFFF)
                                            state.subColorUi = merged
                                            persistSubtitleStyle { it.copy(textColor = merged) }
                                            reapplySubtitleStyle()
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 6. Outline + Outline Color
                                    StyleSectionLabel("Outline")
                                    Spacer(Modifier.height(6.dp))
                                    StyleChip(
                                        label = if (state.subEdgeUi != 0) "On" else "Off",
                                        on = state.subEdgeUi != 0,
                                        onClick = {
                                            val t = if (state.subEdgeUi == 0) 1 else 0
                                            state.subEdgeUi = t
                                            persistSubtitleStyle { it.copy(edgeType = t) }
                                            reapplySubtitleStyle()
                                        }
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    StyleSectionLabel("Outline Color")
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.alpha(if (state.subEdgeUi == 0) 0.42f else 1f)
                                    ) {
                                        listOf(0xFF000000, 0xFFFFFFFF, 0xFF00E5FF, 0xFFFF5C5C)
                                            .forEach { c ->
                                                val rgb = c.toInt()
                                                StyleSwatch(
                                                    color = Color(rgb),
                                                    selected = (state.subEdgeColorUi and 0x00FFFFFF) ==
                                                        (rgb and 0x00FFFFFF),
                                                    onClick = {
                                                        state.subEdgeColorUi = rgb
                                                        val nextEdge = if (state.subEdgeUi == 0) 1 else state.subEdgeUi
                                                        if (state.subEdgeUi == 0) state.subEdgeUi = 1
                                                        persistSubtitleStyle {
                                                            it.copy(edgeColor = rgb, edgeType = nextEdge)
                                                        }
                                                        reapplySubtitleStyle()
                                                    }
                                                )
                                            }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 7. Bottom Offset
                                    StyleSectionLabel("Bottom Offset")
                                    Spacer(Modifier.height(6.dp))
                                    StyleStepper(
                                        value = "${state.subBottomUi}",
                                        onMinus = {
                                            val v = (state.subBottomUi - 5).coerceIn(0, 200)
                                            state.subBottomUi = v
                                            persistSubtitleStyle { it.copy(bottomOffset = v) }
                                            reapplySubtitleStyle()
                                        },
                                        onPlus = {
                                            val v = (state.subBottomUi + 5).coerceIn(0, 200)
                                            state.subBottomUi = v
                                            persistSubtitleStyle { it.copy(bottomOffset = v) }
                                            reapplySubtitleStyle()
                                        }
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    // 8. Auto Sync (addon subtitles only)
                                    StyleSectionLabel("Auto Sync")
                                    Spacer(Modifier.height(6.dp))
                                    if (!selectedIsAddon) {
                                        Text(
                                            "Select an addon subtitle first",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    } else {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { reloadPanelSubtitles() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    "Reload",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = { startCaptureSync() },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    if (state.captureLoading) "Loading\u2026" else "Capture",
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                        state.captureCues?.let { cues ->
                                            Spacer(Modifier.height(10.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "Tap the line you hear now",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = TextPrimary,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                TextButton(onClick = { state.captureCues = null }) {
                                                    Text(
                                                        "Cancel",
                                                        color = TextSecondary,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            }
                                            cues.forEach { (startMs, cueText) ->
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color.White.copy(alpha = 0.06f))
                                                        .clickable {
                                                            // Delay from the CAPTURED
                                                            // position minus the cue's
                                                            // start, with the 300ms
                                                            // reaction-time compensation.
                                                            setSubtitleDelay(
                                                                state.capturePositionMs - startMs - 300L -
                                                                    settings.subtitleOffsetMs
                                                            )
                                                            pokeIndicator(
                                                                PlayerIndicator.Message(
                                                                    "Subtitles synced"
                                                                )
                                                            )
                                                        }
                                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        String.format(
                                                            java.util.Locale.US,
                                                            "%d:%02d",
                                                            startMs / 60000,
                                                            (startMs / 1000) % 60
                                                        ),
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = LocalPalette.current.accent
                                                    )
                                                    Spacer(Modifier.width(10.dp))
                                                    Text(
                                                        cueText,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = TextPrimary,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Spacer(Modifier.height(6.dp))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    // 9. Reset Defaults — clears only THIS
                                    // title's override; the Settings
                                    // defaults are untouched.
                                    TextButton(
                                        onClick = {
                                            setSubtitleDelay(0L)
                                            clearSubtitleStyleOverride()
                                            reseedStyleRailMirrors()
                                            reapplySubtitleStyle()
                                        }
                                    ) {
                                        Text(
                                            "Reset defaults",
                                            color = TextSecondary,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-player subtitle search results.
        // Bottom sheet (not AlertDialog) so the root media/channel-key handler
        // keeps working while it's open (#28).
        if (state.subSearchOpen) {
            // TV: land D-pad focus on the first result row when results land.
            val firstResultFocus = remember { FocusRequester() }
            LaunchedEffect(isTv, state.subSearchResults) {
                if (isTv && state.subSearchResults.isNotEmpty()) {
                    delay(300)
                    runCatching { firstResultFocus.requestFocus() }
                }
            }
            com.aviostream.app.ui.screens.player.PlayerOverlayPanel(
                onDismiss = { state.subSearchOpen = false }
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp)
                ) {
                    item {
                        Text(
                            "Find subtitles",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                    when {
                        state.subSearchLoading -> item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OrbitDots(modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Searching subtitle addons…", color = TextSecondary)
                            }
                        }
                        state.subSearchError != null -> item {
                            Text(
                                state.subSearchError!!,
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        else -> itemsIndexed(state.subSearchResults) { index, sub ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .tvFocusRing(RoundedCornerShape(10.dp))
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstResultFocus)
                                        else Modifier
                                    )
                                    .clickable { addExternalSubtitle(sub) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        displayLanguage(sub.lang) ?: sub.lang.uppercase(),
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        sub.url.substringAfter("://").take(52),
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-player Sources (Nuvio PlayerSourcesPanel): a RIGHT-side
        // glass panel (520dp, left corners 16dp, tint 0.72, scrim 0.34)
        // — not a bottom card. Header + provider filter + stream rows
        // live in PlayerSourcesContent.
        if (state.sourcesOpen) {
            com.aviostream.app.ui.screens.player.PlayerSidePanel(
                onDismiss = { state.sourcesOpen = false },
                widthDp = 520.dp
            ) {
                com.aviostream.app.ui.screens.player.PlayerSourcesContent(
                    title = state.currentRequest.title,
                    streams = state.availableStreams,
                    selectedUrl = state.currentRequest.streamUrl,
                    loading = state.streamsLoading,
                    onPick = { switchSource(it) },
                    onDownload = { state.downloadStream = it },
                    onRetry = { refreshStreamsBackground(state.currentRequest.type, state.currentRequest.videoId) },
                    onClose = { state.sourcesOpen = false },
                    modifier = Modifier.fillMaxWidth(),
                    settings = repository.settings,
                    isEpisode = state.currentRequest.type != "movie"
                )
            }
        }

        // In-player Quality picker (Nuvio PlayerQualityPanel): a CENTERED
        // glass dialog (not a bottom card) with a Close button, bordered
        // option rows ("Selected" on the right, "Currently playing" /
        // Playback-speed dialog (centered, same pattern as Quality): the
        // old speed Popup sat above the pill button and covered the seekbar
        // and the time pills, because it is ~270dp tall and the gap above
        // the pill is only ~60dp. A centered dialog cannot overlap them.
        if (state.speedMenuOpen) {
            com.aviostream.app.ui.screens.player.PlayerCenteredDialog(
                onDismiss = { state.speedMenuOpen = false }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Playback speed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        com.aviostream.app.ui.screens.player.PlayerDialogCloseButton(
                            onClick = { state.speedMenuOpen = false }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 2.dp)
                    ) {
                        items(SPEEDS.sortedDescending()) { s ->
                            com.aviostream.app.ui.screens.player.QualityOptionRow(
                                title = speedLabel(s),
                                subtitle = if (s == 1f) "Normal" else null,
                                selected = s == state.speed,
                                onClick = {
                                    state.speed = s
                                    activeSetSpeed(s)
                                    state.speedMenuOpen = false
                                    pokeIndicator(PlayerIndicator.Speed(speedLabel(s)))
                                }
                            )
                        }
                    }
                }
            }
        }

        // Sleep timer dialog (v0.14.30 port, centered like Speed): Off,
        // 15/30/45/60 minutes, or End of episode.
        if (state.sleepMenuOpen) {
            com.aviostream.app.ui.screens.player.PlayerCenteredDialog(
                onDismiss = { state.sleepMenuOpen = false }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Sleep timer",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        com.aviostream.app.ui.screens.player.PlayerDialogCloseButton(
                            onClick = { state.sleepMenuOpen = false }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = 2.dp)
                    ) {
                        item {
                            com.aviostream.app.ui.screens.player.QualityOptionRow(
                                title = "Off",
                                subtitle = null,
                                selected = state.sleepDeadlineMs <= 0L && !state.sleepAtEpisodeEnd,
                                onClick = {
                                    state.sleepDeadlineMs = 0L
                                    state.sleepAtEpisodeEnd = false
                                    state.sleepChosenMinutes = 0
                                    state.sleepMenuOpen = false
                                }
                            )
                        }
                        items(listOf(15, 30, 45, 60)) { minutes ->
                            com.aviostream.app.ui.screens.player.QualityOptionRow(
                                title = "$minutes minutes",
                                subtitle = null,
                                selected = state.sleepDeadlineMs > 0L && state.sleepChosenMinutes == minutes,
                                onClick = {
                                    state.sleepAtEpisodeEnd = false
                                    state.sleepChosenMinutes = minutes
                                    state.sleepDeadlineMs = System.currentTimeMillis() + minutes * 60_000L
                                    state.sleepMenuOpen = false
                                    pokeIndicator(PlayerIndicator.Message("Sleep timer — $minutes minutes"))
                                }
                            )
                        }
                        item {
                            com.aviostream.app.ui.screens.player.QualityOptionRow(
                                title = "End of episode",
                                subtitle = null,
                                selected = state.sleepAtEpisodeEnd,
                                onClick = {
                                    state.sleepDeadlineMs = 0L
                                    state.sleepChosenMinutes = 0
                                    state.sleepAtEpisodeEnd = true
                                    state.sleepMenuOpen = false
                                    pokeIndicator(PlayerIndicator.Message("Sleep timer — end of episode"))
                                }
                            )
                        }
                    }
                }
            }
        }

        // "Recommended compatible quality" subtitles). Auto first, then
        // one row per distinct quality, best first. Picking one switches
        // to a stream of that quality at the current position.
        if (state.qualityOpen) {
            com.aviostream.app.ui.screens.player.PlayerCenteredDialog(
                onDismiss = { state.qualityOpen = false }
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Quality",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        com.aviostream.app.ui.screens.player.PlayerDialogCloseButton(
                            onClick = { state.qualityOpen = false }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    val hlsState = hlsQualityState
                    if (hlsVariantsLoading) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = LocalPalette.current.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else if (hlsState != null && hlsState.hasSelectableQualities) {
                        // HLS variants of the playing stream replace the
                        // whole-stream buckets: Quality is the quality of
                        // this stream; switching streams stays in Sources.
                        val recommended = hlsState.selectedVariantFor(null)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 2.dp)
                        ) {
                            item {
                                com.aviostream.app.ui.screens.player.QualityOptionRow(
                                    title = "Auto (${recommended?.qualityName ?: hlsState.variants.first().qualityName})",
                                    subtitle = "Recommended compatible quality",
                                    selected = hlsVariantSelection == null,
                                    onClick = { switchHlsVariant(null) }
                                )
                            }
                            items(hlsState.variants, key = { it.id }) { variant ->
                                com.aviostream.app.ui.screens.player.QualityOptionRow(
                                    title = variant.displayLabel,
                                    subtitle = null,
                                    selected = hlsVariantSelection == variant.id,
                                    onClick = { switchHlsVariant(variant) }
                                )
                            }
                        }
                    } else if (state.streamsLoading && qualityOptions.isEmpty()) {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = LocalPalette.current.accent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else if (qualityOptions.isEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            com.aviostream.app.ui.screens.player.QualityOptionRow(
                                title = streamLabelText ?: "Current quality",
                                subtitle = "Currently playing",
                                selected = true,
                                enabled = false,
                                onClick = {}
                            )
                            Text(
                                "This stream does not expose selectable quality variants.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF969CA3)
                            )
                        }
                    } else {
                        val bestLabel = qualityOptions.first().first
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            contentPadding = PaddingValues(bottom = 2.dp)
                        ) {
                            item {
                                com.aviostream.app.ui.screens.player.QualityOptionRow(
                                    title = "Auto ($bestLabel)",
                                    subtitle = "Recommended compatible quality",
                                    selected = qualitySelection == null,
                                    onClick = { switchQuality(null) }
                                )
                            }
                            items(qualityOptions, key = { it.first }) { (label, _) ->
                                com.aviostream.app.ui.screens.player.QualityOptionRow(
                                    title = label,
                                    subtitle = null,
                                    selected = qualitySelection == label,
                                    onClick = { switchQuality(label) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Playback info (Nuvio PlaybackInfoModal content in Avio's
        // bottom-sheet panel shape): codec, resolution, fps, bitrate and
        // live network speed. Formats re-read every second while open, so
        // an adaptive switch updates the rows live.
        if (state.infoOpen) {
            PlaybackInfoSheet(
                player = player,
                selectedQualityLabel = qualitySelection ?: qualityOptions.firstOrNull()?.first,
                isAuto = qualitySelection == null,
                onDismiss = { state.infoOpen = false }
            )
        }

        // Download confirmation dialog
        state.downloadStream?.let { stream ->
            com.aviostream.app.ui.components.DownloadConfirmDialog(
                stream = stream,
                title = state.currentRequest.title,
                onDismiss = { state.downloadStream = null },
                // Step 36: series episodes group under the show; movies stay flat.
                metaId = if (!state.currentRequest.isMovie) state.currentRequest.metaId else "",
                seriesName = state.currentRequest.seriesName,
                season = state.currentRequest.season,
                episode = state.currentRequest.episode,
                // Step 46: subtitles + offline poster.
                subtitles = state.currentRequest.subtitles,
                posterUrl = state.currentRequest.poster
            )
        }

        // TEST: YouTube quality picker dialog.
        // Step 63: in-player live-channels panel (search + catalog groups).
        if (state.channelsOpen) {
            LiveChannelsPanel(
                channels = zapChannels,
                currentVideoId = state.currentRequest.videoId,
                onChannelSelected = { index ->
                    state.channelsOpen = false
                    zapTo(index)
                },
                onDismiss = { state.channelsOpen = false }
            )
        }
        // Episodes picker for series (Nuvio PlayerEpisodesPanel): a
        // RIGHT-side panel (520dp, full height), not a bottom card.
        // Header 'Episodes' (headlineSmall) + Close button. Season
        // chips: 24dp radius, selected = 0xFFF5F5F5 fill + Black text,
        // labels 'Season 1' / 'Specials'. Episode rows: 16dp radius,
        // 130x90 thumbnail with an 'S1 E2' code chip, title titleMedium
        // (max 1), overview max 2, current = 2dp focusRing border.
        if (state.episodesOpen) {
            val episodes = state.currentRequest.episodes
            val seasons = remember(episodes) { episodes.map { it.season }.distinct().sorted() }
            var selSeason by remember(episodes) { mutableStateOf(state.currentRequest.season) }
            val panelWatchedKeys by com.aviostream.app.data.WatchedMarks.keys.collectAsState()
            com.aviostream.app.ui.screens.player.PlayerSidePanel(
                onDismiss = { state.episodesOpen = false },
                widthDp = 520.dp
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Episodes",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        // Step 41: shuffle sheet for this series.
                        IconButton(
                            onClick = {
                                state.episodesOpen = false
                                state.shuffleOpen = true
                            },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Shuffle episodes",
                                tint = if (settings.shuffleEpisodes) LocalPalette.current.accent else TextSecondary,
                            )
                        }
                        com.aviostream.app.ui.screens.player.PlayerDialogCloseButton(
                            onClick = { state.episodesOpen = false }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        items(seasons, key = { it }) { season ->
                            val chipSelected = selSeason == season
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(if (chipSelected) Color(0xFFF5F5F5) else Color(0xFF222222))
                                    .border(
                                        width = 1.dp,
                                        color = if (chipSelected) Color.Transparent else Color(0xFF252A2A),
                                        shape = RoundedCornerShape(24.dp)
                                    )
                                    .clickable { selSeason = season }
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (season == 0) "Specials" else "Season $season",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (chipSelected) Color.Black else TextSecondary
                                )
                            }
                        }
                    }
                val eps = remember(episodes, selSeason) {
                    episodes.filter { it.season == selSeason }
                        .sortedWith(compareBy({ it.episode }, { it.title }))
                }
                val currentEpIndex = eps.indexOfFirst { it.id == state.currentRequest.videoId }.coerceAtLeast(0)
                val epListState = androidx.compose.foundation.lazy.rememberLazyListState(
                    initialFirstVisibleItemIndex = (currentEpIndex - 1).coerceAtLeast(0)
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    state = epListState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp)
                ) {
                    items(eps, key = { it.id }) { video ->
                        val selected = video.id == state.currentRequest.videoId
                        val isWatched = com.aviostream.app.data.WatchedMarks.episodeKey(state.currentRequest.metaId, video.season, video.episode) in panelWatchedKeys
                        // Nuvio EpisodeRow: Top-aligned, 16dp radius,
                        // surfaceCard bg, current = 2dp accent border.
                        // Tap plays; LONG-PRESS opens this episode's
                        // streams link (same panel as the ⋮ button).
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFF222222))
                                .then(
                                    if (selected) Modifier.border(
                                        width = 2.dp,
                                        color = LocalPalette.current.accent,
                                        shape = RoundedCornerShape(16.dp)
                                    ) else Modifier
                                )
                                .combinedClickable(
                                    onClick = { playEpisode(video) },
                                    onLongClick = { state.episodeStreamsVideo = video }
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                Modifier.size(130.dp, 90.dp).clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF222222)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (video.thumbnail != null) {
                                    SafePosterImage(
                                        video.thumbnail, null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        "E${video.episode}",
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                // Episode-code chip, bottom-start (Nuvio:
                                // 8dp outer padding, 6dp radius, Black 0.75).
                                Box(
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.75f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "S${video.season} E${video.episode}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (isWatched) {
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(22.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(LocalPalette.current.accent),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = "Watched",
                                            tint = Color.Black,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    video.title,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val airDate = video.released?.takeIf { it.isNotBlank() }?.let { formatEpisodeAirDate(it) }
                                if (airDate != null) {
                                    Text(
                                        airDate,
                                        color = Color(0xFF969CA3),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                if (!video.overview.isNullOrBlank()) {
                                    Text(
                                        video.overview!!,
                                        color = TextSecondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (selected) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Filled.PlayArrow, null, tint = LocalPalette.current.accent)
                            }
                            // Per-episode stream choice (Nuvio
                            // PlayerEpisodesPanel): opens this episode's
                            // streams; picking one plays it.
                            IconButton(
                                onClick = { state.episodeStreamsVideo = video },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "Streams for this episode",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
                }
            }
        }

        // Per-episode stream choice (Nuvio PlayerEpisodesPanel): the ⋮
        // button on an episode row opens this episode's streams; picking
        // one plays that episode with that stream. Same right-side panel
        // as Sources (Nuvio's Episodes "Streams" sub-view).
        state.episodeStreamsVideo?.let { video ->
            com.aviostream.app.ui.screens.player.PlayerSidePanel(
                onDismiss = { state.episodeStreamsVideo = null },
                widthDp = 520.dp
            ) {
                com.aviostream.app.ui.screens.player.PlayerSourcesContent(
                    title = "S${video.season}E${video.episode} · ${video.title}",
                    streams = state.episodeStreams,
                    selectedUrl = "",
                    loading = state.episodeStreamsLoading,
                    onPick = { stream ->
                        playEpisodeWithStream(video, stream, state.episodeStreams, state.episodeStreamsSubtitles)
                    },
                    onDownload = { state.downloadStream = it },
                    onRetry = { state.episodeStreamsTick++ },
                    onClose = { state.episodeStreamsVideo = null },
                    modifier = Modifier.fillMaxWidth(),
                    settings = repository.settings,
                    isEpisode = true
                )
            }
        }

        // Shuffle sheet (Step 41): random episode for this series.
        if (state.shuffleOpen && !state.currentRequest.isMovie) {
            EpisodeShuffleSheet(
                showName = state.currentRequest.seriesName ?: state.currentRequest.title,
                picker = playerShufflePicker,
                includeWatched = settings.shuffleIncludeWatched,
                onIncludeWatchedChange = { settings.shuffleIncludeWatched = it },
                shuffleFollowing = settings.shuffleEpisodes,
                onShuffleFollowingChange = { settings.shuffleEpisodes = it },
                onPlay = { video ->
                    state.shuffleOpen = false
                    playEpisode(video)
                },
                onDismiss = { state.shuffleOpen = false },
            )
        }
    }
}


/** "2011-09-22" -> "Sep 22, 2011" for episode air dates; falls back to the raw value. */
private fun formatEpisodeAirDate(iso: String): String {
    val parsed = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(iso)
    }.getOrNull() ?: return iso
    return java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(parsed)
}


/**
 * One row of the subtitle overlay's Languages rail (v0.14.5): the
 * selected language is a full WHITE pill with dark text and the track
 * count sits in a circle (the v0.14.3 reference look).
 */
@Composable
private fun RailLanguageRow(
    name: String,
    count: Int?,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Nuvio SubtitleLanguageRow: selected = ACCENT fill + onAccent text
    // (black on light accents like GOLD, white on dark ones); the count
    // badge is a pill — selected White 0.18 bg, unselected accent 0.85 bg.
    val accent = LocalPalette.current.accent
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) onAccent else TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (count != null) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.18f)
                        else accent.copy(alpha = 0.85f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = onAccent
                )
            }
        }
    }
}

/** Small white section label above a Style-rail control (v0.14.5). */
@Composable
private fun StyleSectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = TextPrimary
    )
}

/** Sync-by-ear tap button (Nuvio SyncByEarButton): accent-filled while
 *  its tap is captured, white 10% background otherwise. */
@Composable
private fun SyncByEarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    captured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    // Text on the accent fill: black on light accents (White, Ocean,
    // Amber…), white on dark ones (Weave) — computed, not assumed.
    val accent = LocalPalette.current.accent
    val contentColor = if (captured) {
        if (accent.luminance() > 0.5f) Color.Black else Color.White
    } else TextPrimary
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (captured) LocalPalette.current.accent else Color.White.copy(alpha = 0.1f))
            .border(
                width = 1.dp,
                color = if (captured) Color.Transparent else Color.White.copy(alpha = 0.08f),
                shape = shape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** One 40dp rounded square stepper button (white 8% background). */@Composable
private fun StyleStepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * The Style rail's only numeric control (v0.14.5): [−] value [+] —
 * 40dp rounded buttons flanking a value box (white 6% background,
 * min-width 84dp). No sliders anywhere in the rail.
 */
@Composable
private fun StyleStepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StyleStepperButton(Icons.Filled.Remove, "Decrease", onMinus)
        Box(
            Modifier
                .widthIn(min = 84.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.06f))
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
        StyleStepperButton(Icons.Filled.Add, "Increase", onPlus)
    }
}

/**
 * Nuvio SubtitleTextAction: the small Reset chip used by the Audio delay /
 * centre-mix sections — White 0.08 bg, 12dp radius, h12/v10.
 */
@Composable
private fun AudioResetChip(
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Reset",
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.38f)
        )
    }
}

/** On/Off chip for the Style rail: accent background when On. */
@Composable
private fun StyleChip(
    label: String,
    on: Boolean,
    onClick: () -> Unit
) {
    val palette = LocalPalette.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) palette.accent else Color.White.copy(alpha = 0.08f))
            .clickable { onClick() }
            .tvFocusRing(RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (on) palette.accentText else TextPrimary
        )
    }
}

/** 32dp circular color swatch; the selected one gets a 2dp white border. */
@Composable
private fun StyleSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Color.White else Color.White.copy(alpha = 0.25f),
                CircleShape
            )
            .clickable { onClick() }
            .tvFocusRing(CircleShape)
    )
}

/**
 * One subtitle track card in the player's subtitle panel (v0.14.2):
 * source chip, track name, language/format line, accent check when
 * selected — flat Onyx card like the rest of the app.
 */
@Composable
private fun PanelSubCard(
    chip: String,
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    // Nuvio SubtitleOptionRow: selected = ACCENT fill + onAccent text,
    // no border; unselected = transparent. Source chip is a pill
    // (999dp): selected onAccent 0.14 bg + 1dp onAccent 0.22 border,
    // unselected White 0.08 bg. Title bodyLarge max 2, metadata
    // bodySmall max 2, 20dp onAccent checkmark.
    val palette = LocalPalette.current
    val accent = palette.accent
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accent else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) onAccent.copy(alpha = 0.14f)
                        else Color.White.copy(alpha = 0.08f)
                    )
                    .then(
                        if (selected) Modifier.border(
                            1.dp, onAccent.copy(alpha = 0.22f),
                            RoundedCornerShape(50)
                        ) else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    chip,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) onAccent else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(5.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) onAccent else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) onAccent.copy(alpha = 0.72f) else Color(0xFF969CA3),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Selected",
                tint = onAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
