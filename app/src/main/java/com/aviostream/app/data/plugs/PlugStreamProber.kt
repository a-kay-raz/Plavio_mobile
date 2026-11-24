package com.aviostream.app.data.plugs

import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Dead-stream verdicts for PLUG rows (v0.14.58, item 2).
 *
 * Owner's order: "any stream which can't play should not display"
 * — scoped to plugs only (addon rows are the addons' own
 * responsibility and are never probed). The probe runs from the
 * device itself, with the row's own headers, AFTER the rows have
 * painted: the list never waits for it and play never waits for
 * it. Only a row PROVEN dead is removed; anything short of proof
 * keeps the row — never hide on a guess (the same doctrine as the
 * owner's player rules and the Pixeldrain deep-403 lesson: a
 * refusal window or a slow answer is not a death).
 */
enum class PlugProbeVerdict { DEAD, KEEP }

/**
 * The pure verdict rule (unit-tested directly):
 *  - an HTTP answer convicts ONLY on 404, 405 or 410 — the
 *    file is gone. 405 convicts on evidence: the probe uses
 *    GET, the only method the player ever makes, with the
 *    row's own headers, so a method-level refusal of this
 *    URL cannot play either — and the 2026-11-20 MoviesBazar
 *    diagnosis found the dead asrmo playlists answer exactly
 *    that (their nginx prints status 405 carrying a "404 Not
 *    Found" body). Every other status (200/206 alive, 403
 *    fences and quota windows, 5xx server trouble, 3xx
 *    oddities) KEEPS.
 *  - a transport failure convicts only when it is a CONNECTION
 *    failure (refused, unresolvable, reset, TLS) — the device
 *    cannot reach the stream at all. A TIMEOUT never convicts:
 *    slow is not dead. A cancelled probe (the screen went away)
 *    never convicts either.
 */
object PlugProbeClassifier {
    fun classify(status: Int?, failure: Throwable?): PlugProbeVerdict = when {
        status != null -> when (status) {
            404, 405, 410 -> PlugProbeVerdict.DEAD
            else -> PlugProbeVerdict.KEEP
        }
        failure != null -> when {
            failure is InterruptedIOException -> PlugProbeVerdict.KEEP
            failure is CancellationException -> PlugProbeVerdict.KEEP
            // OkHttp reports a cancelled call as IOException("Canceled").
            failure is IOException && failure.message == "Canceled" -> PlugProbeVerdict.KEEP
            failure is IOException -> PlugProbeVerdict.DEAD
            else -> PlugProbeVerdict.KEEP
        }
        else -> PlugProbeVerdict.KEEP
    }
}

/** Probes one plug row from this device, with the row's own headers. */
object PlugStreamProber {
    suspend fun verdict(url: String, headers: Map<String, String>): PlugProbeVerdict =
        try {
            PlugProbeClassifier.classify(PlugHttp.probe(url, headers), null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            PlugProbeClassifier.classify(null, e)
        }
}
