package com.aviostream.app.data

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.aviostream.app.BuildConfig
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.LinkedBlockingQueue

/**
 * Behaviour tracker — TEST BUILDS ONLY (v0.14.67).
 *
 * Gated by BuildConfig.DIAG_TRACKER, which defaults to false in the
 * tree and is passed as -PDIAG_TRACKER=true for this one test build.
 * When the flag is off, every entry point here is a no-op and no
 * thread ever starts.
 *
 * When on, it appends timestamped event lines (app start, screen
 * opens, hero Watch Now taps, details opens, Watch taps, streams
 * race start / per-source finish, player open / first frame / close,
 * error screens, the .67 settings changes) to a private log file,
 * and a watchdog thread reports main-thread freezes over 5 s with
 * the main thread's stack trace. The owner reproduces the hang,
 * then shares the file from Settings → About ("Share behaviour
 * report").
 *
 * TEST v0.14.74 (item 7): the log now also leaves the device by
 * itself in exactly two ways, both through the existing Sentry
 * pipeline to the self-hosted GlitchTip and both carrying the
 * log tail INSIDE the event (never as an attachment): crash/ANR
 * events get the tail attached by CrashReports' beforeSend, and
 * a real foreground freeze self-reports once (rate-limited —
 * see [maybeSendFreezeReport]).
 *
 * TEST v0.14.84 (item 1 — owner's order: "it should send even
 * little thing happen on app, slow, lag, freeze, everything"):
 * the .82 session died at a player open and sent NOTHING — the
 * app was gone before any event flushed, and the dead session's
 * log never left the phone. Three more automatic paths:
 *  (a) UNCLEAN EXIT: a marker file lives while the app is in a
 *      foreground span (written at init and on every process
 *      foregrounding, deleted when the process fully backgrounds
 *      — the user leaving alive is a clean exit). If the marker
 *      is still there at the next init, the previous session
 *      died in use (crash, ANR kill, system kill) and its log
 *      tail uploads by itself — see [reportPreviousUncleanExit].
 *  (b) FREEZE events now FLUSH over the network the moment they
 *      are captured (see [maybeSendFreezeReport]), and measured
 *      slowness the tracker already logs (race settles, addon
 *      legs, plug unwinds) self-reports as it happens, throttled
 *      — see [maybeSendSlowReport].
 *  (c) The uncaught-exception handler snapshots the log tail to
 *      disk SYNCHRONOUSLY ([snapshotTailForCrash], called from
 *      AvioApp's crash catcher), so even a crash whose Sentry
 *      upload dies with the process leaves its exact final lines
 *      for the next start's unclean-exit report.
 *
 * Every write is asynchronous on one daemon writer thread fed by a
 * bounded queue: [log] never blocks and never touches disk, so the
 * tracker cannot become the hang it is hunting.
 */
object DiagTracker {

    val enabled: Boolean = BuildConfig.DIAG_TRACKER

    private const val MAX_BYTES = 2L * 1024 * 1024
    private const val KEEP_BYTES = 1L * 1024 * 1024
    private const val FREEZE_THRESHOLD_MS = 5_000L
    private const val WATCHDOG_TICK_MS = 2_000L

    private val queue = LinkedBlockingQueue<String>(512)
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile
    private var started = false

    @Volatile
    private var logFile: File? = null

    // ---- TEST v0.14.74 (item 7): automatic behaviour reports ----
    // The log tail rides inside Sentry events to GlitchTip (see
    // BehaviourReportSupport for why it is never an attachment).
    // CrashReports' beforeSend reads [tailText] for crash/ANR
    // events; the watchdog below self-reports real freezes.

    @Volatile
    private var appContext: Context? = null

    /** Activities currently started — the freeze report's foreground test. */
    private val startedActivities = java.util.concurrent.atomic.AtomicInteger(0)

    private val isForeground: Boolean
        get() = startedActivities.get() > 0

    // ---- TEST v0.14.75 (item 6): the freeze clock's lifecycle gate ----
    // The .74 phone session proved the false-freeze shape: the
    // owner backgrounded the app (Home / recents / an activity
    // restart), the OS parked the process, and on return the
    // watchdog read the whole parked span as one giant main-
    // thread silence — three FREEZE lines (up to 257 s) that
    // were all background suspensions, one of which self-
    // reported to GlitchTip. The watchdog clock now PAUSES
    // while the process is backgrounded: ProcessLifecycleOwner
    // ON_STOP/ON_START flip [processForeground]; while it is
    // false the watchdog resets its pong baseline every tick
    // and discards any open episode instead of timing it.
    @Volatile
    private var processForeground = true

    /** True once the ProcessLifecycleOwner observer is registered. */
    @Volatile
    private var processGateArmed = false

    /**
     * Whether the freeze clock runs this tick: the process
     * gate when it armed, the activity count as fallback
     * (registration is runCatching — a failure must not mute
     * the watchdog entirely).
     */
    private val freezeClockRunning: Boolean
        get() = if (processGateArmed) processForeground else isForeground

    /** At most one freeze self-report per app session. */
    @Volatile
    private var freezeReportSentThisSession = false

    // ---- TEST v0.14.84 (item 1): session marker + slow events ----

    /** The diag directory (log file, session marker, crash snapshot). */
    @Volatile
    private var diagDir: File? = null

    /** Present ⟺ the app is inside a foreground span (see header). */
    private const val SESSION_MARKER_FILE = "session-active.marker"

    /** The crash-time tail snapshot written by [snapshotTailForCrash]. */
    private const val SESSION_SNAPSHOT_FILE = "last-session-tail.txt"

    /** Slow self-reports already sent this session (item 1b cap). */
    private val slowReportsThisSession =
        java.util.concurrent.atomic.AtomicInteger(0)

    fun init(context: Context) {
        if (!enabled || started) return
        started = true
        appContext = context.applicationContext
        val dir = File(context.filesDir, "diag").apply { mkdirs() }
        diagDir = dir
        logFile = File(dir, "behaviour-log.txt")
        // Foreground tracking for the freeze self-report: a
        // "freeze" the OS reports while the app sits backgrounded
        // (its main thread parked, not stuck) must not send.
        (context.applicationContext as? Application)?.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) {
                    startedActivities.incrementAndGet()
                }

                override fun onActivityStopped(activity: Activity) {
                    startedActivities.decrementAndGet()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            }
        )
        // Item 6: the process lifecycle gate (see the state
        // block above). Registered from the application
        // context on the main thread (init runs in
        // Application.onCreate); any failure leaves the
        // activity-count fallback in charge.
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStart(owner: LifecycleOwner) {
                        processForeground = true
                        // Item 1a: a foreground span begins — this
                        // session is "in use" until it backgrounds.
                        markSessionActive()
                    }

                    override fun onStop(owner: LifecycleOwner) {
                        processForeground = false
                        // Item 1a: the process fully backgrounded
                        // alive — a clean exit. A later system kill
                        // in the background is not an in-use death
                        // and must not report.
                        markSessionClean()
                    }
                }
            )
            processGateArmed = true
        }
        // TEST v0.14.84 (item 1a): BEFORE this session writes a
        // single line, judge the previous one. The marker survived
        // → it died in use; upload its tail now (CrashReports is
        // initialised before this init — see AvioApp). The check
        // only runs when the lifecycle gate armed: without it the
        // marker is never cleared on backgrounding and every start
        // would false-report.
        if (processGateArmed) reportPreviousUncleanExit()
        // This session starts "in use" (see the marker rules in
        // the header): only a full backgrounding clears it.
        markSessionActive()
        startWriter()
        startWatchdog()
        log(
            "app start version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) " +
                "device=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                "android=${android.os.Build.VERSION.RELEASE}"
        )
    }

    /** Queue one event line. Drops silently when the queue is full. */
    fun log(event: String) {
        if (!enabled) return
        val stamp = synchronized(dateFormat) { dateFormat.format(Date()) }
        queue.offer("$stamp $event")
        // TEST v0.14.84 (item 1b): measured slowness self-reports
        // as it happens (throttled — see maybeSendSlowReport).
        maybeSendSlowReport(event)
    }

    /**
     * TEST v0.14.74 (item 7): the behaviour log's tail, shaped by
     * [BehaviourReportSupport] (last ~500 lines, ~120 KB cap,
     * newest lines always survive) — the text that rides inside
     * Sentry events. Reads only the file's last bytes (one seek +
     * one bounded read), so it is cheap enough for the crash
     * path; every failure is swallowed and reported as null.
     * Null whenever the tracker is off (stable builds) or no log
     * exists yet.
     */
    fun tailText(): String? {
        if (!enabled) return null
        return runCatching {
            val raw = rawLogText() ?: return null
            BehaviourReportSupport.tailFromText(raw).ifEmpty { null }
        }.getOrNull()
    }

    /** The log file's last [TAIL_READ_BYTES] as raw text (no trimming). */
    private fun rawLogText(): String? {
        val file = logFile ?: return null
        if (!file.exists() || file.length() == 0L) return null
        val readBytes = minOf(file.length(), TAIL_READ_BYTES).toInt()
        val buffer = ByteArray(readBytes)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(file.length() - readBytes)
            raf.readFully(buffer)
        }
        return String(buffer, Charsets.UTF_8)
    }

    /**
     * TEST v0.14.84 (item 1c): the crash-time tail snapshot —
     * called from AvioApp's uncaught-exception handler, on the
     * crashing thread, while the process is dying. Writes the
     * behaviour log's tail (the file's last bytes PLUS any lines
     * still sitting in the writer queue — a hard crash can outrun
     * the writer) to a snapshot file, synchronously, so the next
     * start's unclean-exit report carries the dead session's
     * exact final lines even when the crash's own Sentry upload
     * dies with the process. Every failure is swallowed: this
     * runs inside the crash path and must never recurse into it.
     * No-op when the tracker is off (stable builds).
     */
    fun snapshotTailForCrash() {
        if (!enabled) return
        runCatching {
            val dir = diagDir
                ?: appContext?.let { File(it.filesDir, "diag") }
                ?: return
            val fileText = runCatching { rawLogText() }.getOrNull()
            val queued = ArrayList(queue)
            val combined = buildString {
                if (!fileText.isNullOrEmpty()) {
                    append(fileText)
                    if (!fileText.endsWith("\n")) append('\n')
                }
                queued.forEach { append(it).append('\n') }
            }
            val tail = BehaviourReportSupport.tailFromText(combined)
            if (tail.isNotEmpty()) {
                File(dir, SESSION_SNAPSHOT_FILE).writeText(tail)
            }
        }
    }

    /** Bytes of log read for a tail — comfortably above the tail cap. */
    private const val TAIL_READ_BYTES = 256L * 1024

    /**
     * The freeze self-report (item 7): ONE info-level Sentry event
     * titled "Behaviour report (freeze)" with the log tail inside
     * it, tagged behaviour_report=freeze. Rate limits: at most 1
     * per app session and at most 3 per day (persisted counter).
     * Runs on the watchdog thread; every failure is swallowed —
     * a report must never disturb the app it describes. When the
     * user has crash reports off, the SDK is uninitialised and
     * captureEvent drops the event silently.
     */
    private fun maybeSendFreezeReport() {
        if (freezeReportSentThisSession) return
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(FREEZE_PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val next = BehaviourReportSupport.nextDailyCount(
            storedDay = prefs.getString(FREEZE_PREF_DAY, null),
            storedCount = prefs.getInt(FREEZE_PREF_COUNT, 0),
            today = today
        ) ?: return
        prefs.edit()
            .putString(FREEZE_PREF_DAY, today)
            .putInt(FREEZE_PREF_COUNT, next)
            .apply()
        freezeReportSentThisSession = true
        runCatching {
            val tail = tailText() ?: return
            val event = io.sentry.SentryEvent()
            event.message = io.sentry.protocol.Message().apply {
                message = "Behaviour report (freeze)"
            }
            event.level = io.sentry.SentryLevel.INFO
            event.setTag("behaviour_report", "freeze")
            event.contexts["behaviour_report"] = mapOf("log_tail" to tail)
            io.sentry.Sentry.captureEvent(event)
            // TEST v0.14.84 (item 1b): flush NOW, over the
            // network, instead of leaving the event in the SDK's
            // send queue to ride out whenever it next drains —
            // the .82 death proved a queued report can die with
            // the process. This runs on the watchdog thread; a
            // bounded stall there only delays the next freeze
            // tick, and reports are capped at one per session.
            runCatching { io.sentry.Sentry.flush(FREEZE_FLUSH_TIMEOUT_MS) }
            log("BEHAVIOUR REPORT SENT (freeze)")
        }
    }

    /** Bounded network flush for a just-captured freeze report. */
    private const val FREEZE_FLUSH_TIMEOUT_MS = 3_000L

    private const val FREEZE_PREFS = "diag_behaviour_reports"
    private const val FREEZE_PREF_DAY = "day"
    private const val FREEZE_PREF_COUNT = "count"

    // TEST v0.14.84 (item 1): the unclean-exit + slow-report
    // counters share the freeze reports' prefs file.
    private const val UNCLEAN_PREF_DAY = "unclean_day"
    private const val UNCLEAN_PREF_COUNT = "unclean_count"
    private const val SLOW_PREF_DAY = "slow_day"
    private const val SLOW_PREF_COUNT = "slow_count"

    /** Item 1a: this session is "in use" — (re)write the marker. */
    private fun markSessionActive() {
        if (!processGateArmed) return
        runCatching {
            val dir = diagDir ?: return
            File(dir, SESSION_MARKER_FILE).writeText(
                "started ${System.currentTimeMillis()}"
            )
        }
    }

    /** Item 1a: the app backgrounded alive — the exit was clean. */
    private fun markSessionClean() {
        runCatching {
            val dir = diagDir ?: return
            File(dir, SESSION_MARKER_FILE).delete()
        }
    }

    /**
     * TEST v0.14.84 (item 1a): the previous session's verdict,
     * judged at init before this session writes anything. The
     * marker survived → the previous session died while in use
     * (crash, ANR kill, system kill — the .82 shape, which sent
     * nothing at all): upload its log tail as one info-level
     * event titled "Behaviour report (unclean exit)", tagged
     * behaviour_report=unclean_exit, the tail INSIDE the event
     * (never an attachment — GlitchTip drops those). The tail is
     * the crash-time snapshot when one exists (item 1c), else the
     * log file's own tail. Rate limit: at most
     * [BehaviourReportSupport.MAX_UNCLEAN_REPORTS_PER_DAY] per
     * day (persisted counter), so a crash loop reports its first
     * deaths and then goes quiet instead of flooding. Marker and
     * snapshot are consumed either way — a death reports once.
     * Every failure is swallowed: reporting must never disturb
     * startup. When crash reports are off in Settings, the SDK
     * is uninitialised and captureEvent drops the event silently.
     */
    private fun reportPreviousUncleanExit() {
        val dir = diagDir ?: return
        val marker = File(dir, SESSION_MARKER_FILE)
        if (!marker.exists()) return
        runCatching { marker.delete() }
        val snapshotFile = File(dir, SESSION_SNAPSHOT_FILE)
        val snapshot = runCatching {
            if (snapshotFile.exists()) snapshotFile.readText() else null
        }.getOrNull()
        runCatching { snapshotFile.delete() }
        val tail = BehaviourReportSupport.preferSnapshotTail(
            snapshot, runCatching { tailText() }.getOrNull()
        ) ?: return
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(FREEZE_PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val next = BehaviourReportSupport.nextDailyCount(
            storedDay = prefs.getString(UNCLEAN_PREF_DAY, null),
            storedCount = prefs.getInt(UNCLEAN_PREF_COUNT, 0),
            today = today,
            maxPerDay = BehaviourReportSupport.MAX_UNCLEAN_REPORTS_PER_DAY
        ) ?: return
        prefs.edit()
            .putString(UNCLEAN_PREF_DAY, today)
            .putInt(UNCLEAN_PREF_COUNT, next)
            .apply()
        runCatching {
            val event = io.sentry.SentryEvent()
            event.message = io.sentry.protocol.Message().apply {
                message = "Behaviour report (unclean exit)"
            }
            event.level = io.sentry.SentryLevel.INFO
            event.setTag("behaviour_report", "unclean_exit")
            event.contexts["behaviour_report"] = mapOf("log_tail" to tail)
            io.sentry.Sentry.captureEvent(event)
        }
    }

    /**
     * TEST v0.14.84 (item 1b): a measured slowness self-report —
     * fired from [log] itself, so every tracker call site is
     * covered centrally: the line is judged by
     * [BehaviourReportSupport.slowEventKind] (race settles, addon
     * legs, plug unwinds) and, when it qualifies, ONE lightweight
     * info-level event goes out immediately — the measured line
     * only, no log tail (the crash/freeze/unclean events carry
     * tails; these are the "little things" and stay small).
     * Throttles: at most [BehaviourReportSupport
     * .MAX_SLOW_REPORTS_PER_SESSION] per session (checked first,
     * in memory) and [BehaviourReportSupport
     * .MAX_SLOW_REPORTS_PER_DAY] per day (persisted counter).
     * Runs on whatever thread logged the line; captureEvent only
     * hands the event to the SDK, and every failure is swallowed.
     */
    private fun maybeSendSlowReport(line: String) {
        if (!started) return
        val kind = BehaviourReportSupport.slowEventKind(line) ?: return
        // Session cap first: once spent, later slow lines cost
        // one atomic read and nothing else.
        while (true) {
            val current = slowReportsThisSession.get()
            if (current >= BehaviourReportSupport.MAX_SLOW_REPORTS_PER_SESSION) return
            if (slowReportsThisSession.compareAndSet(current, current + 1)) break
        }
        val context = appContext ?: return
        val prefs = context.getSharedPreferences(FREEZE_PREFS, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val next = BehaviourReportSupport.nextDailyCount(
            storedDay = prefs.getString(SLOW_PREF_DAY, null),
            storedCount = prefs.getInt(SLOW_PREF_COUNT, 0),
            today = today,
            maxPerDay = BehaviourReportSupport.MAX_SLOW_REPORTS_PER_DAY
        ) ?: return
        prefs.edit()
            .putString(SLOW_PREF_DAY, today)
            .putInt(SLOW_PREF_COUNT, next)
            .apply()
        runCatching {
            val event = io.sentry.SentryEvent()
            event.message = io.sentry.protocol.Message().apply {
                message = "Behaviour report (slow)"
            }
            event.level = io.sentry.SentryLevel.INFO
            event.setTag("behaviour_report", kind)
            event.contexts["behaviour_report"] =
                mapOf("kind" to kind, "line" to line)
            io.sentry.Sentry.captureEvent(event)
        }
    }

    private fun startWriter() {
        val thread = Thread({
            val batch = ArrayList<String>(64)
            var writesSinceRotateCheck = 0
            while (true) {
                try {
                    val first = queue.take()
                    batch.add(first)
                    queue.drainTo(batch, 63)
                    val file = logFile ?: continue
                    val text = buildString {
                        batch.forEach { append(it).append('\n') }
                    }
                    file.appendText(text)
                    batch.clear()
                    if (++writesSinceRotateCheck >= 50) {
                        writesSinceRotateCheck = 0
                        rotateIfNeeded(file)
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Exception) {
                    // A failed write must never kill the writer or the app.
                    batch.clear()
                }
            }
        }, "diag-writer")
        thread.isDaemon = true
        thread.start()
    }

    /** Cap the file at ~2 MB, keeping the newest ~1 MB on rotation. */
    private fun rotateIfNeeded(file: File) {
        runCatching {
            if (file.length() <= MAX_BYTES) return
            val bytes = file.readBytes()
            var start = (bytes.size - KEEP_BYTES).toInt().coerceAtLeast(0)
            // Align to the next line boundary so the kept log starts clean.
            while (start < bytes.size && bytes[start] != '\n'.code.toByte()) start++
            if (start < bytes.size) start++
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeBytes(bytes.copyOfRange(start, bytes.size))
            tmp.renameTo(file)
        }
    }

    /**
     * Main-thread watchdog: a daemon thread posts a ping to the main
     * handler every 2 s. If the main thread has not answered for over
     * 5 s, one FREEZE entry (with the main thread's current stack) is
     * logged per freeze episode, plus a FREEZE END when it answers
     * again.
     */
    private fun startWatchdog() {
        val mainHandler = Handler(Looper.getMainLooper())
        val thread = Thread({
            var lastPongMs = SystemClock.uptimeMillis()
            var freezeOpen = false
            var freezeStartMs = 0L
            var freezeReportPending = false
            while (true) {
                try {
                    mainHandler.post { lastPongMs = SystemClock.uptimeMillis() }
                    Thread.sleep(WATCHDOG_TICK_MS)
                    // TEST v0.14.74 (item 7): a freeze flagged on
                    // the previous tick is reported now — one tick
                    // later, so the writer has flushed the FREEZE
                    // entry itself into the log the report carries.
                    if (freezeReportPending) {
                        freezeReportPending = false
                        maybeSendFreezeReport()
                    }
                    // TEST v0.14.75 (item 6): while the app is
                    // backgrounded the freeze clock is PAUSED —
                    // the pong baseline resets every tick, and
                    // an episode that was open when the app
                    // went back is discarded with a note, never
                    // timed and never reported: the silence was
                    // the OS parking the process, not a hang.
                    if (!freezeClockRunning) {
                        if (freezeOpen) {
                            freezeOpen = false
                            log(
                                "FREEZE END app backgrounded; episode " +
                                    "discarded (background suspension, not a hang)"
                            )
                        }
                        lastPongMs = SystemClock.uptimeMillis()
                        continue
                    }
                    val silentFor = SystemClock.uptimeMillis() - lastPongMs
                    if (silentFor > FREEZE_THRESHOLD_MS) {
                        if (!freezeOpen) {
                            freezeOpen = true
                            freezeStartMs = lastPongMs
                            val frames = Looper.getMainLooper().thread.stackTrace
                                .map { it.toString() }
                            val stack = frames
                                .joinToString("\n") { "    at $it" }
                            log(
                                "FREEZE main thread unresponsive for ${silentFor}ms\n$stack"
                            )
                            // Item 7 + item 6: a REAL freeze while
                            // the app is foregrounded self-reports
                            // to GlitchTip — and only when the
                            // captured stack is not the idle shape
                            // (parked in the message queue with no
                            // app frame: the .74 false positives).
                            if (isForeground &&
                                !BehaviourReportSupport.mainThreadStackLooksIdle(frames)
                            ) {
                                freezeReportPending = true
                            }
                        }
                    } else if (freezeOpen) {
                        freezeOpen = false
                        log(
                            "FREEZE END main thread answered again; " +
                                "episode lasted ${SystemClock.uptimeMillis() - freezeStartMs}ms"
                        )
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Exception) {
                    // The watchdog must never die loudly or take the app with it.
                }
            }
        }, "diag-watchdog")
        thread.isDaemon = true
        thread.start()
    }

    /** Share the behaviour log through the Android share sheet. */
    fun share(context: Context) {
        if (!enabled) return
        runCatching {
            val source = logFile
                ?: File(File(context.filesDir, "diag"), "behaviour-log.txt")
            val dir = File(context.cacheDir, "debug").apply { mkdirs() }
            val out = File(dir, "avio-behaviour-report.txt")
            if (source.exists()) {
                source.copyTo(out, overwrite = true)
            } else {
                out.writeText("(no behaviour log captured yet)")
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", out
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Avio behaviour report")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share behaviour report"))
        }
    }
}
