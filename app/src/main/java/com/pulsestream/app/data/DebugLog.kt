package com.pulsestream.app.data

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.system.Os
import android.system.OsConstants
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import com.pulsestream.app.BuildConfig
import com.pulsestream.app.player.DownmixAudioProcessor
import java.io.File

/**
 * "Share debug log" (Settings > About): builds a small text report the
 * user can send to support — app/device facts, the privacy-safe source
 * diagnostics, and the app's own recent logcat with URLs and key/token
 * values redacted — then shares it as a .txt file. Nothing is sent
 * anywhere automatically; the user picks the destination.
 */
object DebugLog {

    private val urlPattern = Regex("""https?://\S+""")
    private val secretPattern = Regex("""(?i)(key|token|secret|password|auth)=([^\s&]+)""")

    fun build(): String = buildString {
        appendLine("Avio debug log")
        appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine()
        appendLine(audioCodecDiagnostics())
        appendLine()
        appendLine(SourceDiagnostics.export())
        appendLine()
        appendLine("--- Logcat (this app, latest 300 lines, redacted) ---")
        appendLine(readOwnLogcat())
    }

    /**
     * 5.1 / FFmpeg audio facts (2026-11-23, corrected): 5.1 titles fail with
     * "FfmpegAudioRenderer error, ..., format_supported=NO_UNSUPPORTED_TYPE".
     * That format is the DECODER OUTPUT format (audio/raw 6ch/48kHz), not
     * the track format, and NO_UNSUPPORTED_TYPE is Media3's string for
     * FORMAT_UNSUPPORTED_SUBTYPE — a label recomputed for the error
     * message, not the cause. The lib is NOT the problem: Diagnostics
     * proves FfmpegLibrary.isAvailable() true (Lavc60.3.100) and the .so
     * loads, and the user's phone is Android 14 (4 KiB pages), so the
     * 16 KiB re-layout was never the fix here either. The failure is in
     * the audio SINK after a successful decode: the AudioTrack cannot be
     * created for 6ch PCM on this phone (5001). The getMinBufferSize
     * line does NOT answer that — sizing only validates parameters
     * (46128 bytes here) while creation goes through AudioFlinger/HAL.
     * The probe line below is the check that predicts it: it tries to
     * create a 6ch AudioTrack for real, which is what the downmix
     * processor now does before passing multichannel PCM to the sink.
     * The load-error line only runs when the lib is unavailable, and
     * captures the linker's reason directly.
     */
    @OptIn(UnstableApi::class)
    private fun audioCodecDiagnostics(): String = buildString {
        appendLine("--- Audio codecs (5.1 / FFmpeg) ---")
        val available = runCatching { FfmpegLibrary.isAvailable() }.getOrElse { false }
        appendLine("FFmpeg native lib available: $available")
        appendLine("FFmpeg version: ${runCatching { FfmpegLibrary.getVersion() }.getOrNull() ?: "(none)"}")
        appendLine("Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        if (!available) {
            // The LibraryLoader swallows the load failure, so retry the load
            // directly to capture the linker's reason (missing .so, missing
            // symbol, ...). A second load after a success is a harmless no-op.
            val loadError = runCatching { System.loadLibrary("ffmpegJNI") }.exceptionOrNull()
            appendLine("FFmpeg load error: ${loadError?.message ?: "(none — load succeeded on retry)"}")
        }
        val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrNull()
        appendLine("Device page size: ${pageSize?.let { "$it bytes" } ?: "(unknown)"}")
        val minBuffer51 = runCatching {
            AudioTrack.getMinBufferSize(
                48_000,
                AudioFormat.CHANNEL_OUT_5POINT1,
                AudioFormat.ENCODING_PCM_16BIT
            )
        }.getOrNull()
        appendLine(
            "AudioTrack 5.1/48kHz min buffer: " +
                (minBuffer51?.let { if (it < 0) "unsupported (error $it)" else "$it bytes" } ?: "(unknown)")
        )
        val probe51 = runCatching {
            DownmixAudioProcessor.deviceSupportsMultichannelPcm(6, 48_000, C.ENCODING_PCM_16BIT)
        }.getOrNull()
        appendLine(
            "AudioTrack 5.1/48kHz probe (real creation): " +
                (probe51?.let { if (it) "can create" else "CANNOT create — downmix engages" } ?: "(unknown)")
        )
    }

    private fun readOwnLogcat(): String = runCatching {
        val proc = Runtime.getRuntime().exec(
            arrayOf("logcat", "-d", "-t", "300", "--pid", Process.myPid().toString())
        )
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        redact(text).ifBlank { "(no log lines captured)" }
    }.getOrElse { "(logcat unavailable: ${it.javaClass.simpleName})" }

    private fun redact(text: String): String =
        secretPattern.replace(urlPattern.replace(text, "<url>")) { "${it.groupValues[1]}=<hidden>" }

    /** Writes the log to the cache dir and opens the system share sheet. */
    fun share(context: Context) {
        runCatching {
            val dir = File(context.cacheDir, "debug").apply { mkdirs() }
            val file = File(dir, "avio-debug-log.txt")
            file.writeText(build())
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Avio debug log")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share debug log"))
        }
    }
}
