package com.aviostream.app.data

/**
 * Pure rules for the automatic behaviour reports (TEST v0.14.74,
 * item 7): the owner's behaviour log rides INSIDE Sentry events
 * to the self-hosted GlitchTip — never as an attachment (the
 * server accepts envelopes but drops file attachments, verified
 * live 2026-11-22) — so the tail has to fit a hard text budget,
 * and the freeze self-report has to be rate-limited so a bad day
 * cannot flood the project.
 *
 * Everything here is a pure function over plain values so the
 * trimming and the counter can be unit-tested without Android.
 */
object BehaviourReportSupport {

    /** How many of the log's most recent lines a report carries. */
    const val MAX_TAIL_LINES: Int = 500

    /** Hard byte cap for the carried tail (~120 KB of text). */
    const val MAX_TAIL_BYTES: Int = 120 * 1024

    /** Freeze self-reports allowed per day (plus at most 1 per session). */
    const val MAX_FREEZE_REPORTS_PER_DAY: Int = 3

    /**
     * TEST v0.14.84 (item 1a): unclean-exit reports allowed per
     * day. The .82 phone session died at the player open and sent
     * NOTHING — the dead session's log only left the phone because
     * the owner shared it by hand. From .84 the next start uploads
     * the dead session's tail by itself when the previous session
     * never reached a clean backgrounding; the cap keeps a crash
     * loop from flooding the project with one report per relaunch.
     */
    const val MAX_UNCLEAN_REPORTS_PER_DAY: Int = 3

    /**
     * TEST v0.14.84 (item 1b): slow-event reports — at most this
     * many per app session AND per day (the daily count rides the
     * same [nextDailyCount] decision as the freeze reports).
     * Slow events are lightweight (the measured line only, no log
     * tail), but a pathological session must still not flood.
     */
    const val MAX_SLOW_REPORTS_PER_SESSION: Int = 5
    const val MAX_SLOW_REPORTS_PER_DAY: Int = 10

    /** A race whose settle took at least this long is a slow event. */
    const val SLOW_RACE_SETTLE_MS: Long = 10_000L

    /**
     * A single ADDON leg answering at least this slowly is a slow
     * event. Plug legs are deliberately NOT judged by this rule:
     * their deadline machinery already reports itself through the
     * PLUG UNWIND line (see [slowEventKind]).
     */
    const val SLOW_SOURCE_DONE_MS: Long = 20_000L

    private val MS_VALUE = Regex("""ms=(\d+)""")

    /**
     * TEST v0.14.84 (item 1b): the slow-event kind for one raw
     * tracker line (the text DiagTracker.log was called with,
     * before its timestamp is prepended), or null when the line
     * is not a reportable slowness. The rules read the tracker's
     * own measured lines:
     *  - "PLUG UNWIND …" — the QuickJS deadline machinery fired:
     *    a plug overran its budget. Always reportable.
     *  - "RACE SETTLED … ms=N" with N ≥ [SLOW_RACE_SETTLE_MS] —
     *    a title's whole stream race took that long to settle.
     *  - "RACE SOURCE DONE … ms=N" with N ≥ [SLOW_SOURCE_DONE_MS]
     *    — one addon leg answered that slowly. Lines naming a
     *    plug (" plug=") are skipped: the plug's own UNWIND line
     *    is its report, one event per leg is enough.
     */
    fun slowEventKind(line: String): String? {
        if (line.startsWith("PLUG UNWIND")) return "plug_unwind"
        val ms = MS_VALUE.find(line)?.groupValues?.get(1)?.toLongOrNull()
            ?: return null
        if (line.startsWith("RACE SETTLED")) {
            return if (ms >= SLOW_RACE_SETTLE_MS) "slow_race" else null
        }
        if (line.startsWith("RACE SOURCE DONE")) {
            if (line.contains(" plug=")) return null
            return if (ms >= SLOW_SOURCE_DONE_MS) "slow_source" else null
        }
        return null
    }

    /**
     * TEST v0.14.84 (item 1a/1c): which tail an unclean-exit
     * report carries. The crash-time snapshot (written
     * synchronously by the uncaught-exception handler, so it
     * holds the dead session's final lines — including any still
     * sitting in the writer queue when the process died) wins
     * whenever it has content; otherwise the log file's own tail
     * (the kill-without-exception shape: the file is all that
     * survives). Null when neither has anything.
     */
    fun preferSnapshotTail(snapshot: String?, fileTail: String?): String? =
        snapshot?.takeIf { it.isNotEmpty() } ?: fileTail?.takeIf { it.isNotEmpty() }

    /**
     * The report tail of [text]: the last [maxLines] lines, then
     * trimmed from the FRONT (whole lines first) until the UTF-8
     * size fits [maxBytes] — the most recent lines always survive.
     * A single line longer than the cap keeps its own tail end.
     */
    fun tailFromText(
        text: String,
        maxLines: Int = MAX_TAIL_LINES,
        maxBytes: Int = MAX_TAIL_BYTES
    ): String {
        if (text.isEmpty()) return ""
        val all = text.split('\n').dropLastWhile { it.isEmpty() }
        if (all.isEmpty()) return ""
        var kept: List<String> =
            if (all.size > maxLines) all.subList(all.size - maxLines, all.size) else all
        var joined = kept.joinToString("\n")
        while (joined.toByteArray(Charsets.UTF_8).size > maxBytes && kept.size > 1) {
            kept = kept.subList(1, kept.size)
            joined = kept.joinToString("\n")
        }
        val bytes = joined.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxBytes) {
            joined = String(bytes, bytes.size - maxBytes, maxBytes, Charsets.UTF_8)
        }
        return joined
    }

    /**
     * The daily freeze-report counter decision: given the stored
     * (day, count) and today's day key, returns the count to
     * persist after one more send — or null when the day's limit
     * is already reached and this send must not happen. A stored
     * day different from [today] starts the count fresh.
     */
    fun nextDailyCount(
        storedDay: String?,
        storedCount: Int,
        today: String,
        maxPerDay: Int = MAX_FREEZE_REPORTS_PER_DAY
    ): Int? {
        val countToday = if (storedDay == today) storedCount else 0
        return if (countToday >= maxPerDay) null else countToday + 1
    }

    // ---- TEST v0.14.85: process-death records (Items 33/34) ----
    //
    // The .84 machinery's proven hole: a NATIVE death (the
    // 2026-11-23 Newest/Oldest death, the 2026-11-24 QuickJS
    // deaths) kills the process before any Java reporter runs,
    // and the next start's unclean-exit upload could silently
    // drop it — the daily cap, or a null log tail, ended the
    // path with nothing sent, and the phone's own record of the
    // death (reason, signal) was never read at all. From .85 the
    // next start ALSO reads the Android historical exit records
    // (ApplicationExitInfo, API 30+) and sends every death the
    // system recorded as its own event. The pure rules live here;
    // the Android glue is ExitDeathReporter + DiagTracker's
    // reportPreviousDeaths. Death reports are NEVER capped and
    // NEVER depend on a log tail existing.

    /**
     * ApplicationExitInfo reason codes, mirrored as plain ints so
     * this file stays Android-free (the values are the platform's
     * stable constants): LOW_MEMORY=3, CRASH=4, CRASH_NATIVE=5,
     * ANR=6. Only these four are deaths a report must carry —
     * user-requested stops, dependency deaths, package updates
     * and clean self-exits are not deaths.
     */
    const val EXIT_REASON_LOW_MEMORY: Int = 3
    const val EXIT_REASON_CRASH: Int = 4
    const val EXIT_REASON_CRASH_NATIVE: Int = 5
    const val EXIT_REASON_ANR: Int = 6

    /** One historical process death, flattened from the OS record. */
    data class ExitDeath(
        val timestampMs: Long,
        val pid: Int,
        val reason: Int,
        /** Signal number for signalled/native deaths, else 0. */
        val status: Int,
        val description: String?,
        val importance: Int,
        val pssKb: Long,
        val rssKb: Long,
        /** The record's trace text when it provides one (ANR). */
        val trace: String? = null
    )

    /** True when [reason] is a death the server must hear about. */
    fun isReportableDeath(reason: Int): Boolean = reason == EXIT_REASON_LOW_MEMORY ||
        reason == EXIT_REASON_CRASH ||
        reason == EXIT_REASON_CRASH_NATIVE ||
        reason == EXIT_REASON_ANR

    /** The platform name of a reason code, for tags and text. */
    fun exitReasonName(reason: Int): String = when (reason) {
        0 -> "REASON_UNKNOWN"
        1 -> "REASON_EXIT_SELF"
        2 -> "REASON_SIGNALED"
        EXIT_REASON_LOW_MEMORY -> "REASON_LOW_MEMORY"
        EXIT_REASON_CRASH -> "REASON_CRASH"
        EXIT_REASON_CRASH_NATIVE -> "REASON_CRASH_NATIVE"
        EXIT_REASON_ANR -> "REASON_ANR"
        7 -> "REASON_INITIALIZATION_FAILURE"
        8 -> "REASON_PERMISSION_CHANGE"
        9 -> "REASON_EXCESSIVE_RESOURCE_USAGE"
        10 -> "REASON_USER_REQUESTED"
        11 -> "REASON_USER_STOPPED"
        12 -> "REASON_DEPENDENCY_DIED"
        13 -> "REASON_OTHER"
        14 -> "REASON_FREEZER"
        15 -> "REASON_PACKAGE_STATE_CHANGE"
        16 -> "REASON_PACKAGE_UPDATED"
        else -> "REASON_$reason"
    }

    /**
     * The dedupe key of one death record. Timestamp + pid +
     * reason identifies a death: a process dies once, and the
     * system never reuses a pid within the same millisecond for
     * this package's records in practice. Persisted across starts
     * (see [mergeReportedDeathKeys]) so a death reports exactly
     * once, however often the app restarts afterwards.
     */
    fun deathKey(death: ExitDeath): String =
        "${death.timestampMs}:${death.pid}:${death.reason}"

    /**
     * The deaths from [deaths] that still owe the server a
     * report: reportable reasons whose key is not in
     * [reportedKeys], OLDEST FIRST — events leave in the order
     * the deaths happened, so a morning of crashes reads in
     * sequence at the server.
     */
    fun unreportedDeaths(
        deaths: List<ExitDeath>,
        reportedKeys: Set<String>
    ): List<ExitDeath> = deaths
        .filter { isReportableDeath(it.reason) && deathKey(it) !in reportedKeys }
        .sortedBy { it.timestampMs }

    /** How many reported-death keys are kept (the system's own
     *  record list is short; 64 keys cover it many times over). */
    const val MAX_REPORTED_DEATH_KEYS: Int = 64

    /**
     * Merges freshly-reported keys into the persisted list:
     * order preserved, duplicates dropped, only the newest
     * [MAX_REPORTED_DEATH_KEYS] survive.
     */
    fun mergeReportedDeathKeys(
        existing: List<String>,
        newKeys: List<String>
    ): List<String> = (existing + newKeys).distinct().takeLast(MAX_REPORTED_DEATH_KEYS)

    /** Hard cap for a death record's carried trace text. */
    const val MAX_DEATH_TRACE_CHARS: Int = 24_000

    /**
     * Bounds a record's trace text to [MAX_DEATH_TRACE_CHARS],
     * keeping the HEAD: an ANR trace opens with the main thread —
     * the part that names the hang — while its tail is thread
     * dumps of idle workers. Null/blank stays null.
     */
    fun boundDeathTrace(trace: String?): String? {
        val text = trace?.takeIf { it.isNotBlank() } ?: return null
        return if (text.length <= MAX_DEATH_TRACE_CHARS) text
        else text.substring(0, MAX_DEATH_TRACE_CHARS)
    }

    /**
     * The plain-text summary of one death — the body its server
     * event carries (inside the event, like every behaviour
     * payload; never an attachment). Every field the OS record
     * holds, one per line, so the server copy answers "what
     * killed it" without the phone: reason (name + code), the
     * signal/status, the system's own description, importance,
     * memory at death, and when it happened.
     */
    fun deathReportText(death: ExitDeath): String = buildString {
        append("reason=").append(exitReasonName(death.reason))
        append(" (").append(death.reason).append(")\n")
        append("status=").append(death.status).append('\n')
        append("timestampMs=").append(death.timestampMs).append('\n')
        append("pid=").append(death.pid).append('\n')
        append("importance=").append(death.importance).append('\n')
        append("pssKb=").append(death.pssKb).append('\n')
        append("rssKb=").append(death.rssKb).append('\n')
        append("description=").append(death.description ?: "(none)")
    }

    /**
     * TEST v0.14.75 (item 6): does this main-thread stack look
     * IDLE rather than stuck? The .74 phone session proved the
     * false-freeze shape: every false episode's stack was the
     * main thread parked in the message queue
     * (nativePollOnce / Looper.loop) or running foreground-
     * return machinery — never inside app code. A stack counts
     * as idle only when BOTH hold: no frame from the app's own
     * package anywhere in it, and the queue-park frames are
     * present. Anything else — app frames present, or no park
     * frames at all (a library busy-loop, say) — is NOT idle
     * and a freeze on it may still self-report. Pure string
     * work over StackTraceElement.toString() lines, so the
     * decision is unit-testable.
     */
    fun mainThreadStackLooksIdle(frames: List<String>): Boolean {
        if (frames.isEmpty()) return false
        if (frames.any { it.contains("com.aviostream.app") }) return false
        return frames.any {
            it.contains("nativePollOnce") || it.contains("Looper.loop")
        }
    }
}
