package com.aviostream.app.data.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Downloads the update APK with DownloadManager and opens the system
 * installer once it finishes. The APK is stored in the app's private
 * external downloads dir and re-shared through FileProvider.
 *
 * v0.14.30 #49: the download now REPORTS progress and can be
 * CANCELLED. A short-lived poller thread reads DownloadManager's
 * byte counters (~every 500 ms, off the UI thread) and posts
 * [onProgress] callbacks to the main thread, so the update popup
 * shows a live bar/percentage and stays responsive. The returned
 * [DownloadHandle]'s cancel() actually aborts the transfer —
 * DownloadManager.remove() kills the download and deletes the
 * partial file, the completion receiver is unregistered, and the
 * poller stops — it never just hides the popup.
 *
 * [onDone] fires only on a verified-good download (installer launched).
 * [onError] fires with a human-readable message on any failure — the
 * caller keeps its dialog open and shows it instead of dismissing
 * silently (a failed ACTION_DOWNLOAD_COMPLETE used to launch the
 * installer on a garbage file → "problem parsing the package").
 */
object ApkInstaller {

    /** One in-flight update download; [cancel] aborts it for real. */
    class DownloadHandle internal constructor(
        private val cancelAction: () -> Unit
    ) {
        @Volatile
        var isCancelled: Boolean = false
            private set

        fun cancel() {
            if (isCancelled) return
            isCancelled = true
            cancelAction()
        }
    }

    fun download(
        context: Context,
        url: String,
        version: String,
        expectedSha256: String? = null,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
        onDone: () -> Unit,
        onError: (String) -> Unit = {}
    ): DownloadHandle {
        // S6: plain http is refused — the APK comes from GitHub
        // releases, always https; an http URL here means a tampered
        // manifest or a stale link. (The manifest parser already
        // requires https + github.com; this is the installer-side
        // backstop for any other caller.)
        val scheme = runCatching { Uri.parse(url).scheme }.getOrNull()?.lowercase()
        if (scheme != "https") {
            onError("That download link isn't a secure (https) update URL ($url).")
            return DownloadHandle {}
        }
        val fileName = "Avio-v$version.apk"
        context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
            File(dir, fileName).takeIf { it.exists() }?.delete()
        }
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("Avio v$version")
            setDescription("Downloading update…")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            setMimeType("application/vnd.android.package-archive")
        }
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        // enqueue() throws IllegalArgumentException on malformed URLs —
        // surface it as a readable error instead of force-closing.
        val downloadId = try {
            dm.enqueue(request)
        } catch (e: Exception) {
            onError("Couldn't start the download: ${e.message ?: "bad download link"}.")
            return DownloadHandle {}
        }

        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val finished = java.util.concurrent.atomic.AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                if (intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != downloadId) return
                finished.set(true)
                try {
                    context.unregisterReceiver(this)
                } catch (_: Exception) {
                }
                // A cancelled download's completion broadcast (it
                // can race the unregister) must not install or
                // report — cancel already tore everything down.
                if (cancelled.get()) return
                try {
                    verifyAndInstall(context, dm, downloadId, fileName, expectedSha256, onDone, onError)
                } catch (e: Exception) {
                    deletePartial(context, fileName)
                    onError("The update download failed: ${e.message ?: "unknown error"}.")
                }
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            ContextCompat.RECEIVER_EXPORTED
        )

        // Progress poller: DownloadManager has no progress listener,
        // so poll its counters on a background thread and post the
        // numbers to the main thread for the popup. Stops when the
        // download finishes (the receiver owns the outcome) or when
        // cancelled.
        val poller = Thread {
            while (!cancelled.get() && !finished.get()) {
                var downloaded = -1L
                var total = -1L
                // No row yet (the query can beat DownloadManager's
                // insert) counts as still running — only a row in
                // a terminal state ends the polling.
                var running = true
                runCatching {
                    dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(
                                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                            )
                            running = status == DownloadManager.STATUS_RUNNING ||
                                status == DownloadManager.STATUS_PENDING ||
                                status == DownloadManager.STATUS_PAUSED
                            downloaded = cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR
                                )
                            )
                            total = cursor.getLong(
                                cursor.getColumnIndexOrThrow(
                                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES
                                )
                            )
                        }
                    }
                }
                if (!running) break
                if (!cancelled.get() && !finished.get()) {
                    val d = downloaded
                    val t = total
                    mainHandler.post {
                        if (!cancelled.get() && !finished.get()) onProgress(d, t)
                    }
                }
                try {
                    Thread.sleep(500)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }
        poller.isDaemon = true
        poller.start()

        return DownloadHandle {
            // Abort for real: flag first (the poller and any racing
            // completion broadcast stand down), unregister the
            // receiver, then remove() — which stops the transfer
            // and deletes DownloadManager's partial file; the
            // deletePartial below covers the destination copy.
            cancelled.set(true)
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
            }
            runCatching { dm.remove(downloadId) }
            deletePartial(context, fileName)
        }
    }

    /**
     * ACTION_DOWNLOAD_COMPLETE fires for failures too — check the real
     * status (and a non-trivial file) before touching the installer.
     * When [expectedSha256] is set (manifest carries a hash), the
     * downloaded file must match it — a mismatch deletes the file
     * and never reaches the installer.
     */
    private fun verifyAndInstall(
        context: Context,
        dm: DownloadManager,
        downloadId: Long,
        fileName: String,
        expectedSha256: String?,
        onDone: () -> Unit,
        onError: (String) -> Unit
    ) {
        var failure: String? = null
        dm.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
            if (cursor.moveToFirst()) {
                val status = cursor.getInt(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                )
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    val reason = runCatching {
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    }.getOrNull()
                    failure = "Download failed (${reasonMessage(reason)})."
                }
            } else {
                failure = "Download failed (no download found)."
            }
        } ?: run { failure = "Download failed (couldn't check its status)." }
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            fileName
        )
        // A "successful" status with an empty/missing file is still a failure
        // (partial write, storage hiccup) — never launch the installer on it.
        if (failure == null && (!file.exists() || file.length() <= 0)) {
            failure = "Download failed (the file came back empty)."
        }
        // S6: when the manifest carries a SHA-256, verify the
        // download against it before the installer ever sees it.
        if (failure == null && expectedSha256 != null) {
            val actual = sha256Hex(file)
            if (actual == null || !actual.equals(expectedSha256, ignoreCase = true)) {
                failure = "Download failed (the file doesn't match the release checksum)."
            }
        }
        if (failure != null) {
            deletePartial(context, fileName)
            onError(failure!!)
            return
        }
        val apkUri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val install = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(install)
        onDone()
    }

    /** Lowercase hex SHA-256 of [file], or null when it cannot be read. */
    private fun sha256Hex(file: File): String? = runCatching {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun reasonMessage(reason: Int?): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage space"
        DownloadManager.ERROR_CANNOT_RESUME -> "couldn't resume the download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage not available"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "file already exists"
        DownloadManager.ERROR_FILE_ERROR -> "storage error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "network error"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server error"
        DownloadManager.ERROR_UNKNOWN -> "unknown error"
        else -> "unknown error"
    }

    private fun deletePartial(context: Context, fileName: String) {
        runCatching {
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { dir ->
                File(dir, fileName).takeIf { it.exists() }?.delete()
            }
        }
    }
}
