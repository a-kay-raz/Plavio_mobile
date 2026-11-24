package com.aviostream.app.data.plugs

import com.dokar.quickjs.QuickJs
import android.util.Log
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.define
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * JS plug host (Plugs v1) — runs Nuvio-format plug files on
 * QuickJS (io.github.dokar3:quickjs-kt, the same binding the
 * reference hosts use; v0.14.60 replaced the Rhino+Babel stack
 * after the 2026-11-20 diagnosis showed the transpile queue, the
 * 20 s budget arithmetic and the global-scope collisions — not
 * the plugs — were what produced zero rows):
 *
 *  - A plug is one JS file ending in `module.exports = { getStreams }`.
 *  - `getStreams(tmdbId, mediaType, season, episode)` is called with
 *    mediaType "movie" | "tv"; it returns a Promise of an array of
 *    stream objects {name, title, url, quality, headers?, size?, …}
 *    (a plain array is accepted too).
 *  - Globals provided: fetch (native, over [PlugHttp]), console,
 *    setTimeout/clearTimeout, Headers, URL/URLSearchParams,
 *    AbortController, btoa/atob, TextEncoder/TextDecoder and
 *    require() for the host modules (cheerio, crypto-js, axios) —
 *    see assets/plugs/prelude.js, which is also what the VM proof
 *    harness loads, byte for byte.
 *
 * Isolation: every run gets a FRESH QuickJS runtime, and the plug
 * code is evaluated inside a function wrapper (the reference
 * hosts' wrappedCode shape), so the plug's top-level declarations
 * are function locals that shadow the host globals — a plug that
 * declares its own `const atob` can never collide with the
 * prelude's. The per-run deadline is real: a watchdog cancels the
 * run's in-flight HTTP calls the moment it passes, and the result
 * wait is bounded by the same deadline. Any failure is returned
 * as data — a plug can never throw into the app.
 *
 * Host libraries (v0.14.65, hang fix round 2 item A2): cheerio
 * (1.3 MB of source) and crypto-js used to be PARSED FROM SOURCE
 * on every single run — the diagnosis measured ~104 plugs × a
 * full parse each as the race's dominant fixed cost. The dokar
 * binding exposes QuickJS bytecode (compile once, evaluate the
 * bytes on any runtime of the same build), so each library is
 * now compiled ONCE per process into a wrapper that stores the
 * evaluated module in `globalThis.__plugModules`, and a run
 * whose plug mentions the library evaluates the BYTECODE —
 * deserialisation + execution, no parse — before the plug code
 * runs; the prelude's require() checks that registry before the
 * source path. Any failure in compile or bytecode evaluation
 * marks the library broken for the process and the run falls
 * back to the exact pre-.65 source path, so a bytecode problem
 * can never break a plug.
 */
class JsPlugHost(
    private val prelude: String,
    private val log: (String) -> Unit = {},
    private val cheerioSource: String? = null,
    private val cryptoJsSource: String? = null,
    /**
     * Item F (v0.14.65): the native-bridge script (assets/plugs/
     * bridge.js). When present, runs are BRIDGE runs: the native
     * DOM/crypto bindings are installed and the bridge script's
     * shims are what require('cheerio') / require('crypto-js')
     * return — the library sources are never touched. When null,
     * runs take the LEGACY path (library sources / A2 bytecode),
     * which production no longer uses; it remains as the A/B
     * comparison leg for the bridge compatibility gate.
     */
    private val bridgeScript: String? = null
) {
    data class Outcome(
        val streams: List<PlugStream> = emptyList(),
        /** Non-null when the plug failed (syntax, throw, timeout…). */
        val failure: String? = null,
        val timedOut: Boolean = false,
        /** Last console.error line the plug emitted this run. */
        val lastErrorLog: String? = null
    )

    /** Per-run mutable state shared with the host functions. */
    private class RunState {
        var lastErrorLog: String? = null

        /** True once the run's deadline has passed (watchdog-set). */
        @Volatile var expired: Boolean = false

        /** Wall-clock deadline of this run (set in [run]); delay jobs clamp to it. */
        @Volatile var deadlineMs: Long = 0L

        /** HTTP calls this run currently has in flight. */
        val inFlight: MutableSet<Call> = ConcurrentHashMap.newKeySet()

        /** Item F: this run's native DOM registry (bridge runs only). */
        var domBridge: PlugDomBridge? = null

        /**
         * TEST v0.14.74 (item 1d): true once [execute] has unwound
         * (its finally ran) — the point from which closing the
         * runtime can no longer race a live evaluate of this run.
         */
        @Volatile var unwound: Boolean = false

        fun cancelInFlight() {
            inFlight.forEach { runCatching { it.cancel() } }
        }
    }

    /** What the call script hands back through the capture binding. */
    private data class Captured(
        val json: String? = null,
        val error: String? = null,
        val timedOut: Boolean = false
    )

    fun run(source: String, plugLabel: String, ctx: PlugTitleContext, timeoutMs: Long): Outcome {
        if (ctx.tmdbId == null) {
            return Outcome(failure = "no TMDB id for this title — JS plugs cannot run")
        }
        val state = RunState()
        state.deadlineMs = System.currentTimeMillis() + timeoutMs
        // The deadline, made real: when it passes, every HTTP call
        // this run still has in flight is cancelled, so a plug
        // parked inside a fetch wakes up with a network error
        // instead of overrunning its budget in silence (the .59
        // host measured "20 s" runs lasting 126 s).
        val watchdog = WATCHDOG.schedule(
            {
                state.expired = true
                state.cancelInFlight()
            },
            timeoutMs, TimeUnit.MILLISECONDS
        )
        try {
            val captured = runBlocking {
                withTimeoutOrNull(timeoutMs + CLOSE_GRACE_MS) {
                    execute(source, plugLabel, ctx, state)
                }
            }
            return when {
                captured == null || captured.timedOut -> Outcome(
                    failure = "plug timed out", timedOut = true,
                    lastErrorLog = state.lastErrorLog
                )
                captured.error != null -> Outcome(
                    failure = captured.error,
                    lastErrorLog = state.lastErrorLog
                )
                else -> Outcome(
                    streams = mapStreams(captured.json),
                    lastErrorLog = state.lastErrorLog
                )
            }
        } catch (e: Exception) {
            // TEST v0.14.79 (item 6): a throw that surfaces at or
            // past the run's deadline — above all the native
            // evaluation-timeout interrupt — IS this run's
            // timeout: return the same outcome the withTimeout
            // path returns, so the caller (breaker strike, gate
            // release) cannot tell the two apart. No new error
            // surface.
            if (state.expired ||
                System.currentTimeMillis() >= state.deadlineMs
            ) {
                return Outcome(
                    failure = "plug timed out", timedOut = true,
                    lastErrorLog = state.lastErrorLog
                )
            }
            return Outcome(
                failure = e.message ?: e.javaClass.simpleName,
                lastErrorLog = state.lastErrorLog
            )
        } finally {
            watchdog.cancel(false)
        }
    }

    /**
     * One full run on a fresh QuickJS runtime: bindings, prelude,
     * the wrapped plug, then the call script, whose async wrapper
     * awaits getStreams and hands the JSON result back through the
     * capture binding (the shape the reference hosts use — the
     * engine's own job queue drives the promise chain while this
     * coroutine waits on the deferred).
     */
    private suspend fun execute(
        source: String,
        plugLabel: String,
        ctx: PlugTitleContext,
        state: RunState
    ): Captured {
        val deferred = CompletableDeferred<Captured>()
        val qjs = QuickJs.create(jobDispatcher = JS_DISPATCHER)
        // v0.14.62 (item J2): cap the runtime's heap — a plug that
        // allocates without bound (a decompression bomb, an endless
        // string builder) now fails its own run with an OOM error
        // instead of eating the phone's memory. dokar quickjs-kt
        // 1.0.5 exposes this as QuickJs.memoryLimit (bytes), wired
        // to JS_SetMemoryLimit.
        runCatching { qjs.memoryLimit = JS_MEMORY_LIMIT_BYTES }
        // TEST v0.14.79 (item 6): bound every native evaluation
        // by the run's REMAINING deadline. quickjs-kt 1.0.18's
        // evaluationTimeoutMillis arms the QuickJS interrupt
        // handler, so a plug parked in an endless JS loop is
        // interrupted at the deadline and its evaluate THROWS
        // here instead of holding this thread (and the caller's
        // gate permit) forever — the .61–.78 native-loop hang.
        // The throw unwinds through the finally below like any
        // other failed run, and run() maps a post-deadline throw
        // onto the existing plug-timeout outcome. Re-armed
        // before each evaluate below with the time then left.
        armEvaluationTimeout(qjs, state)
        // TEST v0.14.74 (item 1d): arm the runtime's retirement
        // NOW, at creation — before this run can park inside an
        // uninterruptible native evaluate. Before .74 the reaper
        // was armed only from the finally below, so a run whose
        // thread never unwound had no close scheduled at all:
        // its runtime (up to 64 MiB of native heap) and its
        // dispatcher thread were retained for the process life
        // (the OOM diagnosis's zombie runs, 2026-11-22 pass 2).
        // The armed check is anchored to the run's deadline and
        // closes ONLY once the run has unwound — closing under a
        // live evaluate is the .61 use-after-close crash — and
        // while the run is still stuck it re-arms and checks
        // again, so the close lands promptly whenever the run
        // does return. (dokar quickjs-kt 1.0.5 exposes no
        // interrupt handler, so a permanently stuck evaluate
        // still cannot be force-closed safely; what changed is
        // that its close is now always scheduled and fires at
        // the first post-unwind check instead of never being
        // armed.) Exactly-once across the inline close and the
        // armed check is guarded by the retirement's claim.
        val retirement = armRuntimeRetirement(qjs, state)
        // v0.14.62 (item A): the runtime is closed inline ONLY when
        // the run completed before its deadline — at that point the
        // capture has fired and no job of this run can still be
        // parked past the budget (delays clamp to the deadline, see
        // __plugDelay). On the timeout/cancel path the job queue may
        // still execute on the dispatcher threads, and closing the
        // runtime under it is a native use-after-close — the .61
        // phone crash (whole process gone, no FATAL EXCEPTION).
        // Timed-out runtimes are retired to the reaper instead,
        // which closes them once their jobs have drained.
        var completedNormally = false
        if (bridgeScript != null) state.domBridge = PlugDomBridge()
        try {
            installBindings(qjs, plugLabel, state, deferred)
            qjs.evaluate<Any?>(prelude, "plugs/prelude.js", false)
            armEvaluationTimeout(qjs, state)
            if (bridgeScript != null) {
                // Item F: the bridge shims register into
                // __plugModules; require() serves them and the
                // library sources are never parsed on this path.
                qjs.evaluate<Any?>(bridgeScript, "plugs/bridge.js", false)
            } else {
                installPrecompiledLibraries(qjs, source, state)
            }
            armEvaluationTimeout(qjs, state)
            qjs.evaluate<Any?>(wrapPlugCode(source), plugLabel, false)
            armEvaluationTimeout(qjs, state)
            qjs.evaluate<Any?>(callCode(ctx), "plugs/call.js", false)
            val captured = deferred.await()
            completedNormally = !state.expired
            return captured
        } finally {
            // Item F: the run's DOM registry dies with the run
            // (the reference host clears its DomBridge in a finally
            // exactly the same way).
            state.domBridge?.clear()
            // TEST v0.14.74 (item 1d): from here on the armed
            // retirement check may close the runtime — this run
            // can no longer be inside an evaluate.
            state.unwound = true
            // TEST v0.14.78 (item 6): deadline→unwind telemetry.
            // A run that passed its deadline reports how long its
            // execution actually kept going past it (the .77 log's
            // 56 s / 136 s runs against a 15 s deadline). This is
            // the same unwind moment the reaper waits for, so the
            // number is the true overrun. Measurement only — the
            // watchdog and retirement behaviour are unchanged.
            // Log-only telemetry.
            if (state.expired) {
                Log.i("Plugs",
                    "PLUG UNWIND plug=$plugLabel overrunMs=" +
                        (System.currentTimeMillis() - state.deadlineMs)
                )
            }
            if (completedNormally) {
                if (retirement == null || retirement.claim()) {
                    retirement?.cancel()
                    runCatching { qjs.close() }
                }
            } else if (retirement == null) {
                // Arming itself failed at creation: fall back to
                // the pre-.74 behaviour (reaper armed at unwind).
                retireRuntime(qjs)
            }
            // Otherwise the armed retirement owns the close: it
            // fires at its deadline-anchored check, now that the
            // run has unwound.
        }
    }

    /**
     * TEST v0.14.74 (item 1d): one runtime's retirement — the
     * exactly-once claim on its close, plus the scheduled check.
     */
    private class RuntimeRetirement(val qjs: QuickJs, val state: RunState) {
        private val claimed = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile var future: java.util.concurrent.ScheduledFuture<*>? = null
        fun claim(): Boolean = claimed.compareAndSet(false, true)
        fun cancel() {
            future?.cancel(false)
        }
    }

    /**
     * TEST v0.14.79 (item 6): sets the runtime's native
     * evaluation timeout to this run's remaining budget
     * (floored at 1 ms so an already-expired run interrupts
     * immediately). Best-effort like the memory limit: a
     * failure to arm must not fail the run.
     */
    private fun armEvaluationTimeout(qjs: QuickJs, state: RunState) {
        val remainingMs = state.deadlineMs - System.currentTimeMillis()
        runCatching {
            qjs.evaluationTimeoutMillis = remainingMs.coerceAtLeast(1L)
        }
    }

    /**
     * Schedules the runtime's retirement check for
     * deadline + close-grace + drain (the same total budget the
     * pre-.74 reaper allowed after unwind, now anchored to the
     * deadline so a late-returning run is closed at the first
     * check after it unwinds rather than a fresh drain window
     * later). Returns null when the schedule itself failed —
     * the caller then falls back to [retireRuntime] at unwind.
     */
    private fun armRuntimeRetirement(qjs: QuickJs, state: RunState): RuntimeRetirement? {
        val retirement = RuntimeRetirement(qjs, state)
        val firstDelayMs = maxOf(
            0L,
            state.deadlineMs + CLOSE_GRACE_MS + REAPER_DRAIN_MS -
                System.currentTimeMillis()
        )
        return if (scheduleRetirementCheck(retirement, firstDelayMs)) retirement else null
    }

    private fun scheduleRetirementCheck(
        retirement: RuntimeRetirement,
        delayMs: Long
    ): Boolean = runCatching {
        retirement.future = WATCHDOG.schedule(
            {
                if (retirement.state.unwound) {
                    if (retirement.claim()) {
                        runCatching { retirement.qjs.close() }
                    }
                } else {
                    // Still stuck inside an uninterruptible
                    // evaluate: closing now would be the .61
                    // use-after-close. Check again after another
                    // drain window.
                    scheduleRetirementCheck(retirement, REAPER_DRAIN_MS)
                }
            },
            delayMs, TimeUnit.MILLISECONDS
        )
        true
    }.getOrDefault(false)

    /**
     * Hands a timed-out run's runtime to the reaper: the close is
     * scheduled [REAPER_DRAIN_MS] out on the watchdog thread. By
     * then the run's deadline is long past, its delay jobs (clamped
     * to the remaining budget + grace) have all fired, its in-flight
     * fetches were cancelled by the watchdog, and the promise
     * continuations behind them have run out — the queue is drained
     * and the close is safe. If the schedule itself ever failed, the
     * runtime simply leaks (GC'd with its dispatcher jobs) rather
     * than risking a native crash.
     */
    private fun retireRuntime(qjs: QuickJs) {
        runCatching {
            WATCHDOG.schedule(
                { runCatching { qjs.close() } },
                REAPER_DRAIN_MS, TimeUnit.MILLISECONDS
            )
        }
    }

    // ---- v0.14.65 (item A2): precompiled host libraries --------
    //
    // The bytecode is produced by a throwaway runtime and cached
    // on the host (PlugService's host is a process-lifetime lazy
    // singleton, so the cache is per process). The compiled unit
    // is a small wrapper script: it evaluates the library's own
    // expression-IIFE source — the exact shape the prelude's
    // source path consumes via (0, eval)(src) — and stores the
    // resulting module object in the __plugModules registry,
    // where the prelude's require() finds it without parsing.
    // The trailing "0;" matters: it makes the script's completion
    // value a primitive. Without it the completion value IS the
    // module object, and dokar's result conversion chokes on the
    // module graph's cycles ("TypeError: circular reference" —
    // crypto-js hit exactly this; its bytecode then never landed).

    private val libraryBytecode = ConcurrentHashMap<String, ByteArray>()
    private val libraryBytecodeBroken = ConcurrentHashMap.newKeySet<String>()
    private val compileLock = Any()

    private fun librarySource(name: String): String? = when (name) {
        "cheerio" -> cheerioSource
        "crypto-js" -> cryptoJsSource
        else -> null
    }

    /**
     * The compiled bytecode for [name], compiling it on first
     * use; null when the library is absent or compilation has
     * failed on this process (the caller then keeps the source
     * path). Compilation happens under a lock so a burst of
     * first runs pays it once, not once per run.
     */
    private fun compiledLibrary(name: String): ByteArray? {
        libraryBytecode[name]?.let { return it }
        if (name in libraryBytecodeBroken) return null
        val source = librarySource(name) ?: return null
        synchronized(compileLock) {
            libraryBytecode[name]?.let { return it }
            if (name in libraryBytecodeBroken) return null
            val compiled = try {
                val scratch = QuickJs.create(jobDispatcher = JS_DISPATCHER)
                try {
                    scratch.compile(
                        "globalThis.__plugModules = globalThis.__plugModules || {};\n" +
                            "globalThis.__plugModules['$name'] =\n" + source + "\n;\n0;",
                        "plugs/$name.js",
                        false
                    )
                } finally {
                    runCatching { scratch.close() }
                }
            } catch (e: Exception) {
                libraryBytecodeBroken += name
                log("plug library $name: bytecode compile failed (${e.message}); source path kept")
                null
            }
            if (compiled != null) libraryBytecode[name] = compiled
            return compiled
        }
    }

    /**
     * Evaluates the precompiled bytecode of each host library
     * the plug's own source mentions (require('cheerio') /
     * require('crypto-js') appear literally in the overwhelming
     * majority of plugs), so the modules are sitting in the
     * __plugModules registry before the plug code runs. A plug
     * that builds its require dynamically misses the fast path
     * and the prelude falls back to evaluating the source —
     * correct, just slower, exactly as before .65. A library
     * whose bytecode fails to evaluate is marked broken for the
     * process and this run falls back the same way.
     */
    private suspend fun installPrecompiledLibraries(
        qjs: QuickJs,
        plugSource: String,
        state: RunState
    ) {
        for (name in listOf("cheerio", "crypto-js")) {
            if (!plugSource.contains(name)) continue
            val bytecode = compiledLibrary(name) ?: continue
            armEvaluationTimeout(qjs, state)
            val ok = runCatching { qjs.evaluate<Any?>(bytecode) }.isSuccess
            if (!ok) {
                libraryBytecodeBroken += name
                libraryBytecode.remove(name)
                log("plug library $name: bytecode evaluate failed; source path kept")
            }
        }
    }

    /** Test/diagnostic surface for item A2: the cached bytecode
     *  size for a library, or null when it is not compiled
     *  (not yet, absent, or marked broken). */
    internal fun compiledLibraryBytes(name: String): Int? = libraryBytecode[name]?.size

    private fun installBindings(
        qjs: QuickJs,
        plugLabel: String,
        state: RunState,
        deferred: CompletableDeferred<Captured>
    ) {
        // console.log/info/warn/error → the host log sink. error
        // lines are also kept on the run state (the app surfaces
        // the plug's own last error when it returns zero).
        qjs.define("console") {
            for (level in listOf("log", "info", "warn", "error", "debug")) {
                function(level) { args: Array<Any?> ->
                    val line = args.joinToString(" ") { a ->
                        when (a) {
                            null -> "null"
                            is Map<*, *>, is List<*> -> runCatching {
                                Json.encodeToString(
                                    kotlinx.serialization.json.JsonElement.serializer(),
                                    toJsonElement(a)
                                )
                            }.getOrElse { a.toString() }
                            else -> a.toString()
                        }
                    }
                    if (level == "error") state.lastErrorLog = line
                    log("[$plugLabel] $line")
                    null
                }
            }
        }

        // __plugFetch(url, method, headersJson, body, redirectMode)
        // → JSON string. Synchronous on the JS thread over the
        // plugs' own OkHttp (the reference hosts' bridge shape).
        // It NEVER throws across the boundary: a network failure
        // comes back as an {"error": …} envelope and the prelude
        // turns it into a normal JS fetch rejection — a host
        // exception thrown into the engine's promise machinery gets
        // mangled there. Every call is registered on the run state
        // BEFORE it starts, so the deadline watchdog can cancel it
        // mid-flight.
        // redirectMode (v0.14.58): the prelude passes "manual" when
        // the plug fetched with redirect:"manual" — the bridge then
        // does NOT follow, so the plug sees the raw 3xx status and
        // Location header its later resolver stages are written
        // against. Anything else follows.
        qjs.function("__plugFetch") { args: Array<Any?> ->
            try {
                val url = args.getOrNull(0)?.toString()
                    ?: return@function buildJsonObject { put("error", "fetch: no url") }.toString()
                val method = args.getOrNull(1)?.toString() ?: "GET"
                val headers = parseHeadersJson(args.getOrNull(2)?.toString())
                val body = args.getOrNull(3)?.toString()
                val followRedirects = args.getOrNull(4)?.toString() != "manual"
                var call: Call? = null
                try {
                    val resp = PlugHttp.fetchBlocking(
                        url, method, headers, body, followRedirects
                    ) { created ->
                        call = created
                        state.inFlight += created
                    }
                    // v0.14.61: serialise from the UN-JOINED header
                    // values — a header sent more than once (above
                    // all Set-Cookie) crosses as a JSON array of its
                    // separate values; the prelude's Headers facade
                    // hands them back faithfully (getSetCookie()).
                    // The comma-joined map survives only as the
                    // fallback for responses built without values.
                    val headersJson = buildJsonObject {
                        if (resp.headerValues.isNotEmpty()) {
                            resp.headerValues.forEach { (k, values) ->
                                if (values.size == 1) {
                                    put(k, values[0])
                                } else {
                                    put(k, JsonArray(values.map { JsonPrimitive(it) }))
                                }
                            }
                        } else {
                            resp.headers.forEach { (k, v) -> put(k, v) }
                        }
                    }
                    buildJsonObject {
                        put("status", resp.status)
                        put("statusText", resp.statusText)
                        put("url", resp.finalUrl)
                        put("headers", headersJson)
                        put("body", resp.body)
                    }.toString()
                } finally {
                    call?.let { state.inFlight -= it }
                }
            } catch (e: Exception) {
                buildJsonObject {
                    put("error", e.message ?: e.javaClass.simpleName)
                }.toString()
            }
        }

        qjs.function("__plugB64e") { args: Array<Any?> ->
            Base64.getEncoder().encodeToString(
                (args.getOrNull(0)?.toString() ?: "").toByteArray(Charsets.UTF_8)
            )
        }
        qjs.function("__plugB64d") { args: Array<Any?> ->
            String(
                Base64.getDecoder().decode(args.getOrNull(0)?.toString() ?: ""),
                Charsets.UTF_8
            )
        }
        // __plugRandomBytes(n) → JS array of n unsigned byte values
        // from the platform SecureRandom. Backs the prelude's
        // WebCrypto-lite `crypto.getRandomValues`, which crypto-js
        // (4.x browser build) hard-requires for WordArray.random —
        // it has no insecure fallback.
        qjs.function("__plugRandomBytes") { args: Array<Any?> ->
            val n = (args.getOrNull(0) as? Number)?.toInt()?.coerceIn(0, 65_536) ?: 0
            val bytes = ByteArray(n)
            SECURE_RANDOM.nextBytes(bytes)
            List(n) { bytes[it].toInt() and 0xFF }
        }
        // __plugDelay(ms) → a promise resolving after the delay;
        // the prelude's setTimeout rides on it.
        // v0.14.62 (item A): the delay clamps to the run's REMAINING
        // budget (+ close grace), never the old flat 300 s — a plug
        // parked in a long setTimeout past its deadline kept the
        // runtime's job queue alive for minutes after the run was
        // abandoned, which is what made the old timeout-path close
        // a use-after-close. Now every delay job fires by the
        // deadline and the queue drains right behind it.
        qjs.asyncFunction("__plugDelay") { args: Array<Any?> ->
            val requested = (args.getOrNull(0) as? Number)?.toLong()?.coerceIn(0L, 300_000L) ?: 0L
            val remaining = (state.deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
            val ms = minOf(requested, remaining + CLOSE_GRACE_MS)
            if (ms > 0) delay(ms)
            null
        }
        // __plugModuleSource('cheerio'|'crypto-js') → the host
        // asset's source text (or null); the prelude's require()
        // evaluates it once per run and caches the module.
        // LEGACY runs only: bridge runs never touch the sources.
        if (bridgeScript == null) {
            qjs.function("__plugModuleSource") { args: Array<Any?> ->
                when (args.getOrNull(0)?.toString()) {
                    "cheerio" -> cheerioSource
                    "crypto-js" -> cryptoJsSource
                    else -> null
                }
            }
        }
        // Item F: the native bridges (bridge runs only) — the
        // Ksoup DOM registry for this run + the platform crypto.
        state.domBridge?.let { dom ->
            dom.install(qjs)
            PlugCryptoBridge.install(qjs)
        }
        // Result capture for the call script. A capture landing
        // after the deadline (the plug only got here because the
        // watchdog cancelled its fetch) is a timeout, not a result.
        qjs.function("__plugCapture") { args: Array<Any?> ->
            if (state.expired) {
                deferred.complete(Captured(timedOut = true))
            } else {
                deferred.complete(Captured(json = args.getOrNull(0)?.toString()))
            }
            null
        }
        qjs.function("__plugCaptureError") { args: Array<Any?> ->
            if (state.expired) {
                deferred.complete(Captured(timedOut = true))
            } else {
                deferred.complete(
                    Captured(error = args.getOrNull(0)?.toString() ?: "plug failed")
                )
            }
            null
        }
    }

    /**
     * The plug file, wrapped so its top-level declarations live in
     * a function scope (the reference hosts' wrappedCode shape):
     * `module`/`exports` are prepared outside, the plug code runs
     * inside the IIFE and assigns module.exports there. A plug
     * declaring `const atob` (4KHDHub) simply shadows the global —
     * the .59 host evaluated plug code in the shared global scope
     * and Rhino threw "redeclaration of var atob" before the plug's
     * first line could run.
     */
    private fun wrapPlugCode(source: String): String =
        "var module = { exports: {} };\n" +
            "var exports = module.exports;\n" +
            "(function () {\n" + source + "\n})();\n"

    /**
     * The call script: resolves getStreams (module.exports first,
     * a global fallback second — the reference hosts accept both),
     * awaits it, and hands the JSON-serialised result back through
     * the capture bindings. Absent season/episode ride as
     * undefined, so a plug's own defaults (usually null) apply —
     * the same call shape the reference hosts make for movies.
     * Only numeric/enum literals are interpolated, all from the
     * typed context — no string ever reaches this code.
     */
    private fun callCode(ctx: PlugTitleContext): String {
        val season = ctx.season?.toString() ?: "undefined"
        val episode = ctx.episode?.toString() ?: "undefined"
        val type = if (ctx.nuvioType == "tv") "\"tv\"" else "\"movie\""
        return """
            (async function () {
              try {
                var gs = (typeof module !== 'undefined' && module.exports && module.exports.getStreams) || globalThis.getStreams;
                if (typeof gs !== 'function') {
                  __plugCaptureError('plug exports no getStreams function');
                  return;
                }
                var result = await gs(${ctx.tmdbId}, $type, $season, $episode);
                __plugCapture(JSON.stringify(result || []));
              } catch (e) {
                __plugCaptureError(String((e && e.message) || e));
              }
            })();
            """.trimIndent()
    }

    /** Maps the captured JSON array to [PlugStream]s (Nuvio stream shape). */
    private fun mapStreams(json: String?): List<PlugStream> {
        if (json.isNullOrBlank()) return emptyList()
        val array = runCatching { Json.parseToJsonElement(json) as? JsonArray }.getOrNull()
            ?: return emptyList()
        val out = ArrayList<PlugStream>(array.size)
        for (el in array) {
            val o = el as? JsonObject ?: continue
            val url = str(o, "url")?.takeIf { it.startsWith("http") } ?: continue
            val name = str(o, "name")
            val rawTitle = str(o, "title")
            val quality = str(o, "quality")
            val title = when {
                rawTitle.isNullOrBlank() -> quality ?: name
                quality != null && !rawTitle.contains(quality, ignoreCase = true) -> "$quality $rawTitle"
                else -> rawTitle
            }
            val headers = LinkedHashMap<String, String>()
            (o["headers"] as? JsonObject)?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { headers[k] = it }
            }
            // Nuvio TV reads request headers from
            // behaviorHints.proxyHeaders.request — accept that too.
            val hints = o["behaviorHints"] as? JsonObject
            val proxy = hints?.get("proxyHeaders") as? JsonObject
            (proxy?.get("request") as? JsonObject)?.forEach { (k, v) ->
                (v as? JsonPrimitive)?.contentOrNull?.let { headers[k] = it }
            }
            val sizeBytes = when (val s = o["size"]) {
                is JsonPrimitive -> if (s.isString) {
                    s.content.filter { it.isDigit() }.toLongOrNull()
                } else {
                    s.content.toLongOrNull()
                }
                else -> null
            }
            out += PlugStream(
                url = url,
                name = name,
                title = title,
                headers = headers,
                sizeBytes = sizeBytes
            )
        }
        return out
    }

    private fun str(obj: JsonObject, key: String): String? =
        (obj[key] as? JsonPrimitive)?.contentOrNull

    private fun toJsonElement(value: Any?): kotlinx.serialization.json.JsonElement = when (value) {
        null -> kotlinx.serialization.json.JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Map<*, *> -> kotlinx.serialization.json.JsonObject(
            value.entries.filter { it.key is String }
                .associate { (it.key as String) to toJsonElement(it.value) }
        )
        is List<*> -> kotlinx.serialization.json.JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun parseHeadersJson(text: String?): Map<String, String> {
        if (text.isNullOrBlank()) return emptyMap()
        val obj = runCatching { Json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return emptyMap()
        val out = LinkedHashMap<String, String>()
        for ((k, v) in obj) {
            (v as? JsonPrimitive)?.content?.let { out[k] = it }
        }
        return out
    }

    companion object {
        private val SECURE_RANDOM = java.security.SecureRandom()

        /**
         * The QuickJS job dispatcher: a cached pool, so concurrent
         * plug runs (and their blocking fetch bridges) never starve
         * each other. Daemon threads — a wedged plug can never keep
         * the process alive.
         * TEST v0.14.71 (item 1b): the pool's threads run at
         * BACKGROUND priority, like PlugService's executor — a
         * QuickJS run blocks its pool thread for its whole life,
         * and an abandoned run must lose every CPU contest against
         * the UI/render threads while it drains. Guarded for JVM
         * unit tests, where the Android priority call cannot land.
         */
        private val JS_DISPATCHER: CoroutineDispatcher =
            Executors.newCachedThreadPool { r ->
                Thread({
                    runCatching {
                        android.os.Process.setThreadPriority(
                            android.os.Process.THREAD_PRIORITY_BACKGROUND
                        )
                    }
                    r.run()
                }, "plugs-js").apply { isDaemon = true }
            }.asCoroutineDispatcher()

        /** Deadline watchdogs (in-flight call cancellation). */
        private val WATCHDOG: ScheduledExecutorService =
            Executors.newSingleThreadScheduledExecutor { r ->
                Thread(r, "plugs-watchdog").apply { isDaemon = true }
            }

        /** Slack for the runtime to close after a timed-out run. */
        private const val CLOSE_GRACE_MS = 2_000L

        /** Item J2 (v0.14.62): per-runtime QuickJS heap cap, 64 MiB. */
        private const val JS_MEMORY_LIMIT_BYTES = 64L * 1024 * 1024

        /**
         * How long a retired (timed-out) runtime waits for its job
         * queue to drain before the reaper closes it (item A). The
         * run's deadline has passed and its delays are clamped to
         * the budget, so 30 s is far beyond the last job's life.
         */
        private const val REAPER_DRAIN_MS = 30_000L
    }
}
