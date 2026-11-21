package com.pulsestream.app

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.pulsestream.app.data.BackgroundAudioController
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Step 64 — foreground service carrying the now-playing notification
 * for background audio. Scoped port of Nuvio's
 * PlayerNowPlayingService.android.kt: the latest notification is kept
 * in process state, publish/hide run on a single-thread executor, and
 * the service only promotes itself to foreground when it has a
 * notification to show.
 *
 * The player itself lives in PlayerScreen; this service never owns
 * playback — it only keeps the process alive in the background and
 * surfaces the controls. When the activity is destroyed the player
 * is released and BackgroundAudioController.unregister() stops this
 * service again.
 */
class BackgroundAudioService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START_FOREGROUND) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val notification = BackgroundAudioServiceState.notification ?: run {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    BackgroundAudioController.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(BackgroundAudioController.NOTIFICATION_ID, notification)
            }
        }.onFailure { error ->
            Log.w(TAG, "Unable to promote playback service", error)
        }.isSuccess
        if (!started) stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        BackgroundAudioServiceState.startRequested.set(false)
        super.onDestroy()
    }

    companion object {
        const val TAG = "PlavioBackgroundAudio"
        const val ACTION_START_FOREGROUND = "com.pulsestream.app.bg_audio.START_FOREGROUND"

        internal fun publish(context: Context, notification: Notification) {
            BackgroundAudioServiceState.notification = notification
            BackgroundAudioServiceController.publish(context.applicationContext, notification)
        }

        internal fun hide(context: Context) {
            BackgroundAudioServiceState.notification = null
            BackgroundAudioServiceController.hide(context.applicationContext)
        }
    }
}

internal object BackgroundAudioServiceState {
    @Volatile
    var notification: Notification? = null
    val startRequested = AtomicBoolean(false)
}

private sealed interface BackgroundAudioServiceCommand {
    data class Publish(val context: Context, val notification: Notification) : BackgroundAudioServiceCommand
    data class Hide(val context: Context) : BackgroundAudioServiceCommand
}

private object BackgroundAudioServiceController {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "PlavioBackgroundAudio").apply { isDaemon = true }
    }

    fun publish(context: Context, notification: Notification) {
        executor.execute { execute(BackgroundAudioServiceCommand.Publish(context, notification)) }
    }

    fun hide(context: Context) {
        executor.execute { execute(BackgroundAudioServiceCommand.Hide(context)) }
    }

    private fun execute(command: BackgroundAudioServiceCommand) {
        when (command) {
            is BackgroundAudioServiceCommand.Publish -> {
                val context = command.context
                if (!BackgroundAudioServiceState.startRequested.getAndSet(true)) {
                    val intent = Intent(context, BackgroundAudioService::class.java)
                        .setAction(BackgroundAudioService.ACTION_START_FOREGROUND)
                    runCatching {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }.onFailure { error ->
                        Log.w(BackgroundAudioService.TAG, "Unable to start playback service", error)
                        BackgroundAudioServiceState.startRequested.set(false)
                    }
                } else {
                    context.getSystemService(NotificationManager::class.java)
                        ?.notify(
                            BackgroundAudioController.NOTIFICATION_ID,
                            command.notification
                        )
                }
            }
            is BackgroundAudioServiceCommand.Hide -> {
                BackgroundAudioServiceState.startRequested.set(false)
                command.context.getSystemService(NotificationManager::class.java)
                    ?.cancel(BackgroundAudioController.NOTIFICATION_ID)
                command.context.stopService(
                    Intent(command.context, BackgroundAudioService::class.java)
                )
            }
        }
    }
}

/** Notification / lock-screen action taps route back to the controller. */
class BackgroundAudioActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BackgroundAudioController.handleAction(intent.action)
    }
}
