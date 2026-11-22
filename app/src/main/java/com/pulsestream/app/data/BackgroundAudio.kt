package com.pulsestream.app.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import androidx.media3.common.Player
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.pulsestream.app.BackgroundAudioActionReceiver
import com.pulsestream.app.BackgroundAudioService
import com.pulsestream.app.MainActivity
import com.pulsestream.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Step 64 — Background audio-only playback + MediaSession (P21).
 * Scoped port of Nuvio's Android now-playing stack
 * (PlayerNowPlayingController.android.kt + PlayerNowPlayingService.android.kt):
 * a platform MediaSession + MediaStyle notification driven by a small
 * foreground service, so audio keeps playing when the app goes to the
 * background and the lock screen / notification can pause, resume and
 * seek. Opt-in via Settings > Playback > "Background audio" (default
 * off); Avio's deliberate pause-on-background (#10) stays the
 * behaviour when it is off.
 *
 * The player itself stays in the activity (PlayerScreen owns it) — the
 * service only carries the notification + session. Consequences, by
 * design: swiping the app away from recents destroys the activity,
 * releases the player and stops the audio; and live streams are
 * excluded (they keep pausing on background — the live failover
 * machinery assumes a foreground player).
 */
data class BackgroundAudioMetadata(
    val title: String,
    val subtitle: String?,
    val artworkUrl: String?,
)

object BackgroundAudioController {
    const val CHANNEL_ID = "avio_playback"
    const val NOTIFICATION_ID = 0x9A10
    const val ACTION_PLAY = "com.pulsestream.app.bg_audio.PLAY"
    const val ACTION_PAUSE = "com.pulsestream.app.bg_audio.PAUSE"
    const val ACTION_REWIND = "com.pulsestream.app.bg_audio.REWIND"
    const val ACTION_FAST_FORWARD = "com.pulsestream.app.bg_audio.FAST_FORWARD"
    const val ACTION_STOP = "com.pulsestream.app.bg_audio.STOP"
    private const val SEEK_INTERVAL_MS = 10_000L

    private var appContext: Context? = null
    private var playerRef: WeakReference<Player>? = null
    private var mediaSession: MediaSession? = null
    private var metadata: BackgroundAudioMetadata? = null
    private var artwork: Bitmap? = null
    private var artworkUrlLoaded: String? = null
    private var registered = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var ticker: Job? = null
    private var playerListener: Player.Listener? = null

    /** PlayerScreen calls this when playback starts (only when the setting is on). */
    fun register(context: Context, player: Player, metadata: BackgroundAudioMetadata) {
        val app = context.applicationContext
        if (registered && playerRef?.get() === player) {
            updateMetadata(metadata)
            return
        }
        unregister()
        appContext = app
        playerRef = WeakReference(player)
        this.metadata = metadata
        registered = true

        mediaSession = MediaSession(app, "AvioNowPlaying").apply {
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() = handleAction(ACTION_PLAY)
                override fun onPause() = handleAction(ACTION_PAUSE)
                override fun onStop() = handleAction(ACTION_STOP)
                override fun onSeekTo(pos: Long) {
                    playerRef?.get()?.seekTo(pos.coerceAtLeast(0L))
                }
                override fun onFastForward() = handleAction(ACTION_FAST_FORWARD)
                override fun onRewind() = handleAction(ACTION_REWIND)
            })
            isActive = true
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
            override fun onPlaybackStateChanged(state: Int) = publish()
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) = publish()
        }
        playerListener = listener
        player.addListener(listener)
        loadArtwork(metadata.artworkUrl)
        publish()
        startTicker()
    }

    fun updateMetadata(metadata: BackgroundAudioMetadata) {
        if (!registered) return
        val artworkChanged = metadata.artworkUrl != this.metadata?.artworkUrl
        this.metadata = metadata
        if (artworkChanged) loadArtwork(metadata.artworkUrl)
        publish()
    }

    /** PlayerScreen calls this from its dispose path; stops the service and session. */
    fun unregister() {
        if (!registered && mediaSession == null) return
        registered = false
        ticker?.cancel()
        ticker = null
        playerRef?.get()?.let { player ->
            playerListener?.let { runCatching { player.removeListener(it) } }
        }
        playerRef = null
        playerListener = null
        mediaSession?.let { runCatching { it.release() } }
        mediaSession = null
        metadata = null
        artwork = null
        artworkUrlLoaded = null
        appContext?.let { BackgroundAudioService.hide(it) }
    }

    /** Called by BackgroundAudioActionReceiver for notification/lock-screen taps. */
    fun handleAction(action: String?) {
        val player = playerRef?.get() ?: return
        when (action) {
            ACTION_PLAY -> runCatching { player.play() }
            ACTION_PAUSE -> runCatching { player.pause() }
            ACTION_STOP -> runCatching { player.pause() }
            ACTION_REWIND -> runCatching {
                player.seekTo((player.currentPosition - SEEK_INTERVAL_MS).coerceAtLeast(0L))
            }
            ACTION_FAST_FORWARD -> runCatching {
                val duration = player.duration
                val target = player.currentPosition + SEEK_INTERVAL_MS
                player.seekTo(if (duration > 0) target.coerceAtMost(duration) else target)
            }
        }
        publish()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(1000)
                if (registered) publishPlaybackStateOnly()
            }
        }
    }

    private fun publish() {
        val context = appContext ?: return
        val meta = metadata ?: return
        val player = playerRef?.get()
        val playing = player?.isPlaying == true
        ensureChannel(context)
        publishPlaybackStateOnly()
        val notification = buildNotification(context, meta, playing)
        BackgroundAudioService.publish(context, notification)
    }

    /** Refreshes the MediaSession state (position/speed) without rebuilding the notification. */
    private fun publishPlaybackStateOnly() {
        val session = mediaSession ?: return
        val player = playerRef?.get() ?: return
        val meta = metadata ?: return
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, meta.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, meta.subtitle)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, artwork)
                .build()
        )
        val position = player.currentPosition.coerceAtLeast(0L)
        val state = if (player.isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
        session.setPlaybackState(
            PlaybackState.Builder()
                .setState(state, position, player.playbackParameters.speed)
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                        PlaybackState.ACTION_STOP or PlaybackState.ACTION_SEEK_TO or
                        PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND
                )
                .build()
        )
    }

    private fun buildNotification(
        context: Context,
        meta: BackgroundAudioMetadata,
        playing: Boolean
    ): Notification {
        val session = mediaSession
        val playPauseAction = if (playing) {
            Notification.Action.Builder(
                android.R.drawable.ic_media_pause,
                "Pause",
                actionIntent(context, ACTION_PAUSE, 2),
            ).build()
        } else {
            Notification.Action.Builder(
                android.R.drawable.ic_media_play,
                "Play",
                actionIntent(context, ACTION_PLAY, 2),
            ).build()
        }
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_pulse_logo)
            .setContentTitle(meta.title)
            .setContentText(meta.subtitle)
            .setLargeIcon(artwork)
            .setContentIntent(contentIntent(context))
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_rew,
                    "Rewind 10 seconds",
                    actionIntent(context, ACTION_REWIND, 1),
                ).build(),
            )
            .addAction(playPauseAction)
            .addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_media_ff,
                    "Forward 10 seconds",
                    actionIntent(context, ACTION_FAST_FORWARD, 3),
                ).build(),
            )
        if (session != null) {
            builder.setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2),
            )
        }
        return builder.build()
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, BackgroundAudioActionReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun contentIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    private fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Now-playing controls for background audio" }
        )
    }

    private fun loadArtwork(urlString: String?) {
        if (urlString.isNullOrBlank()) {
            artwork = null
            artworkUrlLoaded = null
            return
        }
        if (urlString == artworkUrlLoaded) return
        val context = appContext ?: return
        scope.launch(Dispatchers.IO) {
            val bitmap = runCatching {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(urlString)
                    .size(512)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                } else null
            }.getOrNull()
            withContext(Dispatchers.Main) {
                if (metadata?.artworkUrl != urlString) return@withContext
                artwork = bitmap
                artworkUrlLoaded = urlString
                publish()
            }
        }
    }
}
