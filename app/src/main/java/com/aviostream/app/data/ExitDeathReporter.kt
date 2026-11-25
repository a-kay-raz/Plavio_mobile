package com.aviostream.app.data

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.InputStreamReader

/**
 * TEST v0.14.85 (Items 33/34): the Android glue for the
 * process-death reports — reads the phone's own historical exit
 * records ([ApplicationExitInfo], API 30+) and flattens them
 * into [BehaviourReportSupport.ExitDeath] values for
 * DiagTracker's reportPreviousDeaths.
 *
 * Why this exists: a native death (the 2026-11-23 Newest/Oldest
 * SIGABRT, the 2026-11-24 QuickJS SIGABRTs) kills the process
 * before any Java reporter can run, so the crash itself never
 * reaches the server — only the OS remembers it, in these
 * records, with its real reason and signal. Reading them at the
 * next start makes the death itself reportable.
 *
 * Isolated in its own class behind the API-30 guard at the call
 * site: nothing here may be touched on older Android (minSdk 26).
 * Every failure is swallowed to an empty list — reporting must
 * never disturb startup.
 */
@RequiresApi(Build.VERSION_CODES.R)
object ExitDeathReporter {

    /**
     * All exit records the system still holds for this package
     * (newest first, the platform's order), flattened. The list
     * is short (the system keeps a bounded history).
     */
    fun collect(context: Context): List<BehaviourReportSupport.ExitDeath> =
        runCatching {
            val manager = context.getSystemService(ActivityManager::class.java)
                ?: return emptyList()
            val records = manager.getHistoricalProcessExitReasons(
                context.packageName, 0, 0
            ) ?: return emptyList()
            records.map { info ->
                BehaviourReportSupport.ExitDeath(
                    timestampMs = info.timestamp,
                    pid = info.pid,
                    reason = info.reason,
                    status = info.status,
                    description = info.description,
                    importance = info.importance,
                    pssKb = info.pss,
                    rssKb = info.rss,
                    trace = readTrace(info)
                )
            }
        }.getOrDefault(emptyList())

    /**
     * The record's trace text when it provides one (ANR records
     * carry the full thread dump; native-crash records usually
     * provide none — their tombstone is not exposed through this
     * API). Read is bounded at the char cap so a giant dump can
     * never balloon the read; [BehaviourReportSupport
     * .boundDeathTrace] applies the same bound again at the pure
     * seam.
     */
    private fun readTrace(info: ApplicationExitInfo): String? = runCatching {
        val stream = info.traceInputStream ?: return null
        InputStreamReader(stream, Charsets.UTF_8).use { reader ->
            val buffer = CharArray(BehaviourReportSupport.MAX_DEATH_TRACE_CHARS)
            var filled = 0
            while (filled < buffer.size) {
                val read = reader.read(buffer, filled, buffer.size - filled)
                if (read < 0) break
                filled += read
            }
            if (filled == 0) null else String(buffer, 0, filled)
        }
    }.getOrNull()
}
