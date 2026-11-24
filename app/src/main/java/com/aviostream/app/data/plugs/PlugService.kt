package com.aviostream.app.data.plugs

import android.content.Context
import android.util.Log
import com.aviostream.app.data.StreamItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Plug orchestration (Plugs v1): owns the store, the JS host and
 * the rule engine, and runs the per-title race.
 *
 * Race shape (owner's progressive doctrine, same as the addon
 * fan-out): every enabled plug that serves the title's type runs
 * IN PARALLEL on plug-dedicated threads, and each plug's rows are
 * handed to the caller THE MOMENT THAT PLUG FINISHES (v0.14.61 —
 * plugs never wait for each other; the .60 race collected until
 * all plugs answered or the budget expired, so the sheet only
 * ever saw plug rows in one late lump after the slowest plug).
 * A plug that throws, times out or returns garbage fails THAT
 * title only: there is no session disable (v0.14.60 removed it —
 * in .59 one timeout benched the plug for the whole session,
 * which is what left later titles with zero plug rows even after
 * the plugs would have run fine).
 *
 * No result caching (owner ruling 2026-11-20: "we should never
 * cache in app"): every title open runs the plugs fresh, exactly
 * like the reference hosts. The per-title results cache that
 * painted the first successful answer forever — new plugs
 * installed later never joined a title it had already answered —
 * is gone, along with its on-disk files' read/write path.
 */
class PlugService(context: Context) {
    private val appContext = context.applicationContext
    val store = PlugStore(appContext)

    /** Prelude (v0.14.60): the QuickJS host's only JS asset shim —
     *  the Babel/regenerator companions of the Rhino stack are
     *  gone with the engine swap; QuickJS parses plug code as
     *  written and has native generators. */
    private val prelude: String by lazy {
        runCatching {
            appContext.assets.open("plugs/prelude.js").bufferedReader().use { it.readText() }
        }.getOrDefault("")
    }
    private val cheerioSource: String? by lazy {
        runCatching {
            appContext.assets.open("plugs/cheerio.js").bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    private val cryptoJsSource: String? by lazy {
        runCatching {
            appContext.assets.open("plugs/crypto-js.js").bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    /** Item F (v0.14.65): the native-bridge shims. Production runs
     *  are bridge runs — the cheerio/crypto-js sources above are no
     *  longer read on the plug path at all. */
    private val bridgeScript: String? by lazy {
        runCatching {
            appContext.assets.open("plugs/bridge.js").bufferedReader().use { it.readText() }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }
    private val jsHost: JsPlugHost by lazy {
        JsPlugHost(
            prelude,
            { line -> Log.d(TAG, line) },
            bridgeScript = bridgeScript
        )
    }

    // ---- per-plug issues (item 6, runtime half, v0.14.56) ---------
    private val _lastPlugIssues = MutableStateFlow<Map<String, String>>(emptyMap())
    /**
     * Last known problem per plug, keyed by [StoredPlug.key]: a
     * shortened failure reason, or why a successful run still gave
     * zero streams. Cleared for a plug as soon as it returns streams.
     * The plugs UI reads this; nothing here persists across restarts.
     */
    val lastPlugIssues: StateFlow<Map<String, String>> = _lastPlugIssues

    private val _lastPlugSuccess = MutableStateFlow<Map<String, Int>>(emptyMap())
    /**
     * Last stream count per plug, keyed by [StoredPlug.key], from
     * its most recent run that returned streams — the plugs page's
     * green "Working · last title N streams" line (design B).
     * In-memory only, like [lastPlugIssues].
     */
    val lastPlugSuccess: StateFlow<Map<String, Int>> = _lastPlugSuccess

    private fun recordIssue(key: String, reason: String) {
        _lastPlugIssues.value = _lastPlugIssues.value + (key to shortenReason(reason))
    }

    private fun clearIssue(key: String) {
        if (key in _lastPlugIssues.value) {
            _lastPlugIssues.value = _lastPlugIssues.value - key
        }
    }

    private fun recordSuccess(key: String, count: Int) {
        _lastPlugSuccess.value = _lastPlugSuccess.value + (key to count)
        clearIssue(key)
    }

    /**
     * One short human line (≤ 90 chars, single line). The common
     * HTTP case is humanized: a long exception mentioning a status
     * becomes "Site answered 403".
     */
    internal fun shortenReason(raw: String): String {
        val oneLine = raw.lineSequence().firstOrNull()?.trim().orEmpty()
            .ifBlank { "Unknown error" }
        val http = Regex("""HTTP\s+(\d{3})""").find(oneLine)
        val shortened = if (http != null && oneLine.length > 40) {
            "Site answered ${http.groupValues[1]}"
        } else {
            oneLine
        }
        return shortened.take(90)
    }
    private val ruleEngine = RulePlugEngine(PlugHttpRuleFetcher)

    /**
     * TMDB-id resolution for JS plugs (v0.14.57): wired by PulseApp
     * to the user's own Integrations TMDB key — the same credential
     * [TmdbFind] always used. Null provider (tests, previews) means
     * resolution simply cannot fire and JS plugs keep the honest
     * per-plug reason instead of running id-less.
     */
    @Volatile
    var tmdbKeyProvider: (() -> String?)? = null

    private val tmdbResolver = TmdbIdResolver(keyProvider = { tmdbKeyProvider?.invoke() })

    /**
     * v0.14.58: records a TMDB id the title's own metadata carried
     * (the repository's context builder distils it from the meta's
     * TMDB source link) in the resolver's session cache, so repeat
     * opens — and any caller that reaches the plug leg without a
     * meta walk — resolve from the cache instead of re-asking.
     * The key-based find stays as the fallback for metas that
     * carry no TMDB link.
     */
    fun cacheMetaTmdbId(imdbId: String?, appType: String, tmdbId: Int) {
        if (imdbId.isNullOrBlank() || tmdbId <= 0) return
        tmdbResolver.remember(imdbId, appType, tmdbId)
    }

    private val executor = Executors.newCachedThreadPool { r ->
        Thread({
            // TEST v0.14.71 (item 1b): plug threads run at
            // BACKGROUND priority. Since the settle fix (item 1a)
            // nothing the user waits on needs a plug result, so a
            // plug run — above all an abandoned race's run, which
            // cannot be interrupted and drains for up to its full
            // deadline — must lose every CPU contest against the
            // UI and render threads (root-cause study 2026-11-21,
            // Q5 item 3). Guarded: on a JVM without the Android
            // runtime (unit tests) the call simply does not land.
            runCatching {
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )
            }
            r.run()
        }, "plugs").apply { isDaemon = true }
    }
    private val plugDispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()

    /**
     * Execution gating — the hang saga in three shapes:
     *
     * v0.14.63 (item B — owner ruling: full parallel, match the
     * reference) removed the concurrency gate entirely; the owner's
     * phone then froze during searches with ~104 plugs installed
     * (hang diagnosis 2026-11-21: 100+ live QuickJS runtimes and
     * 200+ threads starved the UI thread on a 2-big-core device).
     *
     * v0.14.64 (hang fix 1): LAUNCHES stayed unbounded —
     * raceStreams launches one job per plug the moment its leg
     * starts, and every plug shows as searching from t=0 — but
     * live EXECUTIONS queued on one service-wide gate at 8. It
     * still hung (diagnosis round 2): the gate bounded threads,
     * not total CPU work, and 8 concurrent runtimes saturate a
     * 2-big-core phone for the race's whole life.
     *
     * v0.14.65 (hang fix round 2, items A1 + A5): two changes.
     * (A1) The gate WIDTH adapts to the device — [gateWidth] is
     * [WEAK_GATE_WIDTH] (3, the .62-proven width) on parts with
     * ≤2 performance cores and [MAX_CONCURRENT_PLUG_RUNS] (8)
     * only on stronger hardware (see [detectPlugGateWidth]).
     * (A5) The gate is created PER RACE (and per test run),
     * not service-wide: a run in flight cannot be interrupted
     * (the host blocks in runBlocking), so under a shared gate a
     * cancelled race's stragglers held permits the NEXT title's
     * race queued behind for up to a full plug deadline. A dead
     * race's runs now hold only that race's own permits; a fresh
     * race starts with a full gate. Queued runs still hold no
     * runtime and no heap, and a cancelled race cancels its
     * queued runs before they ever execute (item J3 semantics —
     * Semaphore acquisition is cancellable). The isolation is
     * unchanged: each executing run runs on this service's OWN
     * cached thread pool (never a shared app pool), builds its
     * OWN QuickJS runtime with the 64 MiB memory limit (item
     * J2), and is bounded by its own deadline + HTTP watchdog.
     * Timed-out runtimes are retired to the reaper, never
     * closed mid-run (item A close semantics).
     *
     * TEST v0.14.71 (item 1c): A5's per-race gate is REVERSED —
     * [executionGate] is ONE service-wide semaphore of the same
     * device width, shared by every race. The root-cause study
     * (2026-11-21, Q3/Q5 item 3) showed the per-race shape
     * multiplying the device's plug budget by the number of live
     * races: fast browsing stacked races, each with a full gate,
     * and their uninterruptible runs saturated the SoC. A5's
     * reason for per-race gates — a dead race's stragglers
     * stranding the NEXT race's permits — only mattered while a
     * user-visible wait could queue behind a plug permit; since
     * item 1a the settle is addons-only, so nothing the user
     * waits on needs a permit at all. Stragglers now hold shared
     * permits only until their runs end, at background thread
     * priority (item 1b); launches stay per-race and unbounded,
     * so every plug of a fresh race still shows as searching
     * from t=0 — its executions simply share the one budget.
     */
    private val gateWidth: Int by lazy { detectPlugGateWidth() }

    /** The ONE app-wide plug execution gate (v0.14.71, item 1c). */
    private val executionGate: Semaphore by lazy { Semaphore(gateWidth) }

    fun hasEnabledPlugs(appType: String): Boolean = enabledPlugs(appType).isNotEmpty()

    /**
     * The plugs that may run for a title: the master switch on,
     * the plug's repo switch on, the plug's own switch on, and the
     * plug serving the title's type. Nothing else gates a run —
     * in particular no failure history: a plug that failed the
     * last title runs again on this one.
     */
    fun enabledPlugs(appType: String): List<StoredPlug> {
        val state = store.state.value
        if (!state.plugsEnabled) return emptyList()
        val repoEnabled = state.repos.associate { it.url to it.enabled }
        return state.plugs.filter {
            it.enabled && repoEnabled[it.repoUrl] == true &&
                it.entry.supportsAppType(appType)
        }
    }

    /**
     * v0.14.58, item 2 — the dead-stream prune. Probes [rows]
     * (plug rows only — the repository passes exactly the plug
     * leg's fresh rows) in the background-shaped pass the owner's
     * delay design approves: the rows have ALREADY painted by the
     * time this runs, the pass is bounded in time and parallelism,
     * and it returns the rows that may stay. A row is dropped only
     * on proof of death (see [PlugProbeClassifier]); slow, fenced
     * or troubled rows all stay.
     *
     * v0.14.60: verdicts are NO LONGER recorded anywhere — with
     * the results cache removed there is no entry to carry them,
     * so the hiding applies to the current showing only and rows
     * are re-probed on the next open (the accepted knock-on of
     * the owner's no-cache ruling). Never throws.
     */
    suspend fun pruneDeadPlugRows(
        ctx: PlugTitleContext,
        rows: List<StreamItem>
    ): List<StreamItem> {
        val targets = rows.filter {
            it.sourceId?.startsWith(PLUG_SOURCE_PREFIX) == true
        }
        if (targets.isEmpty()) return rows
        val dead = ConcurrentHashMap.newKeySet<String>()
        runCatching {
            withTimeoutOrNull(PRUNE_BUDGET_MS) {
                coroutineScope {
                    val gate = Semaphore(PROBE_PARALLELISM)
                    val jobs = targets.map { row ->
                        launch(plugDispatcher) {
                            gate.withPermit {
                                if (PlugStreamProber.verdict(row.url, row.headers) ==
                                    PlugProbeVerdict.DEAD
                                ) {
                                    dead += row.url
                                }
                            }
                        }
                    }
                    jobs.joinAll()
                }
            }
        }
        if (dead.isEmpty()) return rows
        Log.i(TAG, "plug probe pruned ${dead.size} dead row(s) for ${ctx.cacheKey}")
        return rows.filter { it.url !in dead }
    }

    /**
     * The whole-race convenience over [raceStreams]: collects
     * every plug's batch and returns the full set. Kept for
     * callers that genuinely want the finished race; the streams
     * sheet does NOT use it anymore (it merges per-plug, below).
     * Never throws. Runs fresh on every call — no cached rows are
     * read or written.
     */
    suspend fun getStreams(ctx: PlugTitleContext): List<StreamItem> {
        val results = ConcurrentLinkedQueue<StreamItem>()
        // Named argument: raceStreams gained a trailing
        // onPlugSettled parameter (item E) — a trailing lambda
        // would bind to THAT instead of onBatch.
        raceStreams(ctx, onBatch = { batch -> results += batch })
        return results.toList()
    }

    // ---------------------------------------------------------------
    // TEST v0.14.73 — the STANDARD provider-race model for the plug
    // leg (owner override 2026-11-22: "follow the official way, a
    // standard way" — the old locks, a 120 s per-plug deadline and
    // no benching ever, are lifted for plugs):
    //  * PER-PLUG RACE TIMEOUT 15 s — a plug that has not produced
    //    its result within 15 s of its run starting is out of the
    //    race; its late result is dropped, its execution-gate slot
    //    is freed at the timeout (the run returns at its deadline
    //    via the host watchdog / the rule engine's withTimeout), and
    //    a still-running QuickJS eval is left to the bounded host
    //    mechanics — it holds no gate slot and emits nothing.
    //  * CIRCUIT BREAKER — 3 CONSECUTIVE timeout/error strikes sit
    //    the plug out for a 30-minute cooldown, then it is tried
    //    again automatically. A clean finish with zero rows is NOT
    //    a strike (a plug may simply not have a title). In-memory
    //    only: never persisted, never touches the user's saved
    //    on/off choices, resets on app restart and when the user
    //    toggles the plug. State is per plug key.
    private class BreakerState {
        val strikes = java.util.concurrent.atomic.AtomicInteger(0)
        @Volatile var openUntilMs: Long = 0L

        /**
         * TEST v0.14.74 (item 6, tracker hygiene): the cooldown
         * window (identified by its openUntilMs) for which a
         * PLUG BREAKER COOLDOWN line has already been logged.
         * The line used to repeat once per benched plug per race
         * (~12× per race in the owner's report); it now logs at
         * most once per plug per cooldown window. Log volume
         * only — breaker behaviour is untouched.
         */
        @Volatile var cooldownLoggedUntilMs: Long = 0L
    }

    private val plugBreakers =
        java.util.concurrent.ConcurrentHashMap<String, BreakerState>()

    private fun breakerOpen(plugKey: String, nowMs: Long): Boolean =
        plugBreakers[plugKey]?.let { it.openUntilMs > nowMs } == true

    private fun recordPlugStrike(plug: StoredPlug, reason: String) {
        val state = plugBreakers.computeIfAbsent(plug.key) { BreakerState() }
        val count = state.strikes.incrementAndGet()
        if (count >= BREAKER_STRIKES) {
            state.openUntilMs = System.currentTimeMillis() + BREAKER_COOLDOWN_MS
            state.strikes.set(0)
            Log.i(TAG,
                "PLUG BREAKER OPEN plug=${plug.key} reason=$reason " +
                    "strikes=$count cooldownMin=${BREAKER_COOLDOWN_MS / 60_000}"
            )
        }
    }

    private fun recordPlugClean(plug: StoredPlug) {
        plugBreakers[plug.key]?.strikes?.set(0)
    }

    /** A user toggle forgives the plug: its breaker state resets. */
    fun resetBreaker(plugKey: String) {
        plugBreakers.remove(plugKey)
    }

    /** Bulk toggle (a whole repo on/off) resets that repo's plugs. */
    fun resetBreakersForRepo(repoUrl: String) {
        val prefix = "$repoUrl|"
        plugBreakers.keys.filter { it.startsWith(prefix) }
            .forEach { plugBreakers.remove(it) }
    }

    /**
     * v0.14.61 — the progressive race the sheet runs. [onBatch]
     * fires the moment EACH plug finishes, carrying exactly that
     * plug's rows (an empty list when the plug answered with
     * nothing or failed — an answer either way, so the caller can
     * release its settle on the first one). Returns when every
     * runnable plug has answered or the race budget expires; a
     * plug still running at that point is ABANDONED, not awaited:
     * the plug jobs live in a race-owned scope, not in the
     * caller's, because a plug parked inside a blocking host run
     * must never hold the caller (the repository leg — and with
     * it the sheet's settle and the 60 s cap) hostage. Stragglers
     * finish on their own daemon threads, bounded by the host's
     * deadline watchdog; their late batches land in a caller that
     * has already closed its leg and are dropped there. Never
     * throws.
     */
    suspend fun raceStreams(
        ctx: PlugTitleContext,
        onBatch: suspend (List<StreamItem>) -> Unit,
        // v0.14.63 (item E): per-plug settle signal for the streams
        // sheet's provider row — fired exactly once per plug that was
        // part of this race (including plugs that sit the title out
        // and plugs whose run failed), with that plug's row count,
        // AFTER that plug's batch has been handed to onBatch (so a
        // consumer reading the merged list sees the rows counted).
        onPlugSettled: suspend (plug: StoredPlug, streamCount: Int) -> Unit = { _, _ -> }
    ) {
        store.ensureLoaded()
        val plugs = enabledPlugs(ctx.appType)
        if (plugs.isEmpty()) return
        // v0.14.57: JS plugs take a TMDB id as their input; a title
        // opened from an addon catalog usually carries only an IMDb
        // id, so resolve it here (cached per title) before any JS
        // plug starts. When the id cannot be resolved, the JS plugs
        // sit this title out with a truthful per-plug reason —
        // never a silent skip, and never a session disable (the
        // miss belongs to the title, not the plug). RULE plugs do
        // not need a TMDB id and always run.
        var raceCtx = ctx
        var runnable = plugs
        val jsPlugs = plugs.filter { it.entry.kind == PlugKind.JS }
        if (jsPlugs.isNotEmpty() && ctx.tmdbId == null) {
            val resolvedCtx = if (ctx.imdbId != null) {
                runCatching {
                    withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { tmdbResolver.withResolvedTmdbId(ctx) }
                }.getOrNull()
            } else {
                null
            }
            if (resolvedCtx != null) {
                raceCtx = resolvedCtx
            } else {
                jsPlugs.forEach {
                    recordIssue(it.key, "Could not resolve TMDB id for this title")
                    // Item E: a plug that sits this title out has
                    // still SETTLED (with zero rows) for the row.
                    runCatching { onPlugSettled(it, 0) }
                }
                runnable = plugs.filter { it.entry.kind == PlugKind.RULE }
                if (runnable.isEmpty()) return
            }
        }
        // TEST v0.14.73 (standard model): plugs inside a breaker
        // cooldown sit this race out — settled immediately with
        // zero rows, exactly like the TMDB sit-out above, so the
        // provider row still completes truthfully.
        val benchNow = System.currentTimeMillis()
        val (benched, activePlugs) = runnable.partition { breakerOpen(it.key, benchNow) }
        if (benched.isNotEmpty()) {
            benched.forEach {
                // TEST v0.14.74 (item 6): one COOLDOWN line per
                // plug per cooldown window — the first race that
                // finds the breaker already open logs it; later
                // races in the same window stay silent.
                val state = plugBreakers[it.key]
                if (state != null && state.cooldownLoggedUntilMs != state.openUntilMs) {
                    state.cooldownLoggedUntilMs = state.openUntilMs
                    Log.i(TAG, "PLUG BREAKER COOLDOWN plug=${it.key}")
                }
                runCatching { onPlugSettled(it, 0) }
            }
            runnable = activePlugs
            if (runnable.isEmpty()) return
        }
        // TEST v0.14.71 (item 1c): executions draw permits from
        // the ONE service-wide gate (see the gate note on
        // [gateWidth]); launches stay per-race and unbounded.
        val raceGate = executionGate
        val remaining = AtomicInteger(runnable.size)
        val allDone = CompletableDeferred<Unit>()
        // v0.14.62 (item J3): the race scope is a CHILD of the
        // collecting flow's job — when the collector is cancelled
        // (sheet closed, leg deadline, screen gone) the race is
        // cancelled with it: runs not yet started never start,
        // in-flight runs are abandoned (their batches drop into
        // the runCatching below), and no new work is launched.
        // In-flight JS runs themselves stay bounded by their own
        // deadline + watchdog, and their runtime closes stay with
        // item A's reaper — cancellation never closes a runtime
        // whose queue may still execute.
        val raceScope = CoroutineScope(
            SupervisorJob(currentCoroutineContext()[kotlinx.coroutines.Job]) + plugDispatcher
        )
        try {
            runnable.forEach { plug ->
                raceScope.launch {
                    try {
                        // TEST v0.14.73 (standard model): the run's
                        // outcome feeds the circuit breaker — Rows
                        // (even empty) is a clean finish and clears
                        // the strikes; TimedOut / Failed add one.
                        val outcome = runCatching { runPlug(plug, raceCtx, raceGate) }
                            .getOrNull()
                        when (outcome) {
                            is PlugRaceOutcome.Rows -> {
                                recordPlugClean(plug)
                                // A closed caller (leg deadline, sheet
                                // gone) turns this into a no-op drop,
                                // never a crash.
                                runCatching { onBatch(outcome.rows) }
                                // Item E: settle this plug for the
                                // provider row with its own count —
                                // even when the caller's leg has
                                // already closed (the row callback is
                                // the repository's, and dropping it
                                // there is the repository's business).
                                runCatching { onPlugSettled(plug, outcome.rows.size) }
                            }
                            PlugRaceOutcome.TimedOut -> {
                                recordPlugStrike(plug, "timeout")
                                runCatching { onBatch(emptyList()) }
                                runCatching { onPlugSettled(plug, 0) }
                            }
                            else -> {
                                recordPlugStrike(plug, "error")
                                runCatching { onBatch(emptyList()) }
                                runCatching { onPlugSettled(plug, 0) }
                            }
                        }
                    } finally {
                        if (remaining.decrementAndGet() == 0) {
                            allDone.complete(Unit)
                        }
                    }
                }
            }
            withTimeoutOrNull(RACE_BUDGET_MS) { allDone.await() }
        } finally {
            raceScope.cancel()
        }
    }

    /**
     * The providers page's Test Provider button (v0.14.61): runs
     * ONE plug alone against a fixed benchmark title — Inception,
     * the title the owner checks plugs against — through the exact
     * [runPlug] path a title race uses, so the outcome lands in
     * the same per-plug status lines ([lastPlugIssues] /
     * [lastPlugSuccess]) the page already renders: the stream
     * count on success, the real error on failure. The plug's own
     * switches do not gate a test — a test is a question about the
     * plug itself. Runs on the plug dispatcher (a JS run blocks
     * its thread for up to the plug budget). Never throws.
     */
    suspend fun testPlug(plug: StoredPlug) {
        store.ensureLoaded()
        withContext(plugDispatcher) {
            // A single test run needs no gate (one execution,
            // nothing to queue behind); it takes the exact
            // runPlugGated path a race uses, with the test-only
            // long budget (TEST v0.14.73: a deliberate one-plug
            // benchmark is not a race — the 15 s race timeout and
            // the circuit breaker do not apply to it).
            runCatching { runPlugGated(plug, TEST_TITLE_CONTEXT, TEST_PLUG_TIMEOUT_MS) }
        }
    }

    /**
     * TEST v0.14.73 (standard model): a race run's outcome, kept
     * distinct because the circuit breaker treats them
     * differently — [Rows] (even an empty list) is a clean finish;
     * [TimedOut] and [Failed] are strikes.
     */
    private sealed interface PlugRaceOutcome {
        data class Rows(val rows: List<StreamItem>) : PlugRaceOutcome
        data object TimedOut : PlugRaceOutcome
        data object Failed : PlugRaceOutcome
    }

    /** Runs one plug under [gate] with the 15 s race timeout. */
    private suspend fun runPlug(
        plug: StoredPlug,
        ctx: PlugTitleContext,
        gate: Semaphore
    ): PlugRaceOutcome =
        // Executions queue on the shared execution gate —
        // launches are NOT gated (see the gate note on
        // [gateWidth]). withPermit releases the slot on every
        // exit path, including cancellation while queued. The
        // run itself returns at the race deadline (the JS host's
        // watchdog / the rule engine's withTimeout), so the slot
        // frees at 15 s even when a QuickJS eval cannot be
        // interrupted — the eval is left to the host's bounded
        // mechanics and its late result is dropped by the caller.
        gate.withPermit { runPlugGated(plug, ctx, PLUG_RACE_TIMEOUT_MS) }

    private suspend fun runPlugGated(
        plug: StoredPlug,
        ctx: PlugTitleContext,
        timeoutMs: Long
    ): PlugRaceOutcome {
        val body = store.readBody(plug) ?: run {
            recordIssue(plug.key, "Plug file missing on this device")
            return PlugRaceOutcome.Failed
        }
        val started = System.currentTimeMillis()
        var jsLastError: String? = null
        val streams: List<PlugStream> = when (plug.entry.kind) {
            PlugKind.JS -> {
                val outcome = jsHost.run(body, plug.entry.name, ctx, timeoutMs)
                jsLastError = outcome.lastErrorLog
                if (outcome.timedOut) {
                    Log.i(TAG, "plug ${plug.entry.id} timed out at $timeoutMs ms")
                    recordIssue(plug.key, "Timed out")
                    return PlugRaceOutcome.TimedOut
                }
                if (outcome.failure != null) {
                    Log.i(TAG, "plug ${plug.entry.id} failed: ${outcome.failure}")
                    recordIssue(plug.key, outcome.failure)
                    return PlugRaceOutcome.Failed
                }
                outcome.streams
            }
            PlugKind.RULE -> {
                val out = withTimeoutOrNull(timeoutMs) {
                    ruleEngine.run(body, ctx)
                }
                if (out == null) {
                    Log.i(TAG, "rule plug ${plug.entry.id} timed out")
                    recordIssue(plug.key, "Timed out")
                    return PlugRaceOutcome.TimedOut
                }
                out
            }
        }
        Log.i(
            TAG,
            "plug ${plug.entry.id} → ${streams.size} streams in ${System.currentTimeMillis() - started} ms"
        )
        if (streams.isEmpty()) {
            recordIssue(plug.key, jsLastError ?: "Returned no streams for this title")
        } else {
            recordSuccess(plug.key, streams.size)
        }
        return PlugRaceOutcome.Rows(streams.map { it.toStreamItem(plug) })
    }

    private fun PlugStream.toStreamItem(plug: StoredPlug): StreamItem = StreamItem(
        url = url,
        name = name ?: plug.entry.name,
        title = title,
        sourceName = plug.entry.name,
        sourceId = PLUG_SOURCE_PREFIX + plug.entry.id,
        headers = headers,
        videoSize = sizeBytes,
        filename = filename
    )

    companion object {
        private const val TAG = "Plugs"
        /** StreamItem.sourceId prefix marking plug rows (PLUG chip). */
        const val PLUG_SOURCE_PREFIX = "plug:"
        /**
         * Gate width on STRONG devices (v0.14.64's width, kept):
         * at most this many plug executions are LIVE at once in
         * one race, however many plugs are installed.
         */
        const val MAX_CONCURRENT_PLUG_RUNS = 8
        /**
         * Gate width on WEAK devices (hang fix round 2, item A1):
         * the .62-proven width — on a ≤2-performance-core phone
         * three live runtimes keep instantaneous CPU demand near
         * what the cores can feed, so the UI thread keeps winning
         * a core often enough to stay responsive (diagnosis
         * round 2: at width ≥4 the saturation window is the whole
         * leg; at 3 the same total work ran with no hang).
         */
        const val WEAK_GATE_WIDTH = 3
        /**
         * TEST v0.14.73 — the STANDARD provider-race model
         * (owner override 2026-11-22, replacing the 120 s
         * per-plug deadline of v0.14.65 and the no-benching
         * lock): a plug's race run is out at [PLUG_RACE_TIMEOUT_MS];
         * [BREAKER_STRIKES] consecutive timeout/error strikes open
         * the breaker for [BREAKER_COOLDOWN_MS], after which the
         * plug is tried again automatically.
         */
        const val PLUG_RACE_TIMEOUT_MS = 15_000L
        const val BREAKER_STRIKES = 3
        const val BREAKER_COOLDOWN_MS = 30 * 60_000L
        /**
         * The Test Provider button's single-run budget: a
         * deliberate one-plug benchmark is not a race, so it
         * keeps the long budget the PlugX keepers were measured
         * against (~78–106 s from a fast seat).
         */
        const val TEST_PLUG_TIMEOUT_MS = 120_000L
        /** The whole race: one plug budget plus merge slack. */
        const val RACE_BUDGET_MS = 125_000L
        /** Bound on the v0.14.57 TMDB find so it can never eat the race. */
        const val RESOLVE_TIMEOUT_MS = 5_000L
        /** v0.14.58 item 2: at most this many plug-row probes at once. */
        const val PROBE_PARALLELISM = 4
        /** v0.14.58 item 2: the whole prune pass is capped at this. */
        const val PRUNE_BUDGET_MS = 10_000L
        /** v0.14.61: the Test Provider button's fixed title. */
        val TEST_TITLE_CONTEXT = PlugTitleContext(
            appType = "movie",
            imdbId = "tt1375666",
            tmdbId = 27205,
            title = "Inception",
            year = 2010
        )
    }
}

/**
 * Hang fix round 2 (v0.14.65, item A1): the plug gate's width for
 * this device — [PlugService.WEAK_GATE_WIDTH] when the part has
 * ≤2 performance cores, [PlugService.MAX_CONCURRENT_PLUG_RUNS]
 * otherwise.
 *
 * The signal that matters is PERFORMANCE cores, not the logical
 * count: the owner's Galaxy A16 (Helio G99) reports 8 processors
 * but only 2 of them are big Cortex-A76 cores — a plain
 * availableProcessors() check would class it "strong" and hand
 * it the width that hung it. So the performance cores are
 * counted from the cpufreq topology: a core counts as a
 * performance core when its max frequency is at least 80% of
 * the fastest core's (the G99's 2.2 GHz A76s vs its 2.0 GHz
 * A55s separate cleanly; homogeneous parts count all their
 * cores). When the topology is unreadable (some OEM kernels
 * restrict /sys), the fallback is the plain processor count:
 * ≤4 → weak. Known limit, stated plainly: a homogeneous
 * all-little octa-core part reads as "strong" — no such part is
 * in the owner's test pool, and the fallback keeps the common
 * heterogeneous shapes exact.
 */
internal fun detectPlugGateWidth(): Int {
    val perfCores = countPerformanceCores()
    return when {
        perfCores != null ->
            if (perfCores <= 2) PlugService.WEAK_GATE_WIDTH
            else PlugService.MAX_CONCURRENT_PLUG_RUNS
        Runtime.getRuntime().availableProcessors() <= 4 -> PlugService.WEAK_GATE_WIDTH
        else -> PlugService.MAX_CONCURRENT_PLUG_RUNS
    }
}

/** Performance-core count from /sys cpufreq, or null when the
 *  topology can't be read on this device. */
private fun countPerformanceCores(): Int? {
    return try {
        val cpuRoot = java.io.File("/sys/devices/system/cpu")
        val freqs = cpuRoot.listFiles { f -> f.name.matches(Regex("cpu\\d+")) }
            ?.mapNotNull { cpu ->
                runCatching {
                    java.io.File(cpu, "cpufreq/cpuinfo_max_freq")
                        .readText().trim().toLong()
                }.getOrNull()
            }
            .orEmpty()
        if (freqs.size < 2) return null
        val max = freqs.max()
        if (max <= 0L) return null
        // ≥80% of the fastest core, in integer arithmetic.
        freqs.count { it * 5 >= max * 4 }
    } catch (e: Exception) {
        null
    }
}
