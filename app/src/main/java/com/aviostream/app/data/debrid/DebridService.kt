package com.aviostream.app.data.debrid

import com.aviostream.app.data.SourceDiagnostics
import com.aviostream.app.data.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The native debrid layer's single entry point (v0.14.30 port,
 * feature #14 — AllDebrid, Debrid-Link and Offcloud join TorBox,
 * Real-Debrid and Premiumize).
 *
 * What this class owns:
 *  - the provider set and its order — TorBox → Premiumize →
 *    Real-Debrid → AllDebrid → Debrid-Link → Offcloud — first success
 *    wins, exactly the semantics the old inline repository block had;
 *    the fork's preferred resolver (Settings > Integrations, Step 43)
 *    is tried first when set, the rest follow in canonical order;
 *  - the resolve orchestration: at most [MAX_PARALLEL_RESOLVES]
 *    streams at once, a [PER_STREAM_TIMEOUT_MS] cap per stream, and
 *    the [SourceDiagnostics] outcome for every provider attempt
 *    (succeeded / no_results / authentication_needed / failed);
 *  - the cache-first rule, enforced inside each provider client:
 *    a provider only ever resolves torrents already in its cache —
 *    NotCached means skip, never fetch-on-demand.
 *
 * What it does NOT own: the accumulated stream list. The repository
 * passes the pending rows in and applies each resolved link through
 * its callback, so the in-place swap, de-duplication and progressive
 * emissions stay exactly where they always were.
 *
 * Hard boundaries (owner's standing orders):
 *  - NO torrent engine, NO direct P2P playback, ever. Unresolved
 *    infohash/magnet rows stay unplayable and out of autoplay.
 *  - Providers are the six named services only.
 *
 * Fork adaptation vs upstream 0.14.30: upstream gates the whole
 * layer on an AppConfig "debrid" switch — the fork has no AppConfig,
 * so there is no [enabled] flag here; the layer is active exactly
 * when at least one API key is stored. Upstream's fixed order puts
 * Real-Debrid before Premiumize; the fork's canonical order (Step 43)
 * keeps Premiumize before Real-Debrid. The Premiumize tag is
 * "⚡ Premiumize" (upstream's), not the old inline block's "⚡ PM",
 * so [DebridSupport.debridRank]/[DebridSupport.cachedLabel] recognise
 * it.
 */
public class DebridService internal constructor(
    private val providers: List<Provider>,
) {

    /** One debrid provider: its id (for the preferred-resolver
     *  setting), its stream tag, its diagnostics label, and a resolve
     *  that answers [DebridResolveResult]. */
    public class Provider(
        public val id: String,
        public val tag: String,
        public val diagnosticsName: String,
        public val resolve: suspend (hash: String, fileIdx: Int?, filePath: String?) -> DebridResolveResult,
    )

    public val isActive: Boolean get() = providers.isNotEmpty()

    /** Provider tags in try-order (evidence for tests/UI). */
    public val providerTags: List<String> get() = providers.map { it.tag }

    /**
     * Resolve every pending row in parallel (bounded), invoking
     * [applyResolved] for each success with the row, its hash, the
     * winning provider's tag, and the direct HTTP link. A link only
     * counts as a success when [isPlayable] accepts its shape — the
     * same gate the inline block applied.
     */
    public suspend fun resolveAll(
        pending: List<StreamItem>,
        isPlayable: (String) -> Boolean,
        applyResolved: suspend (original: StreamItem, hash: String, tag: String, httpUrl: String) -> Unit,
    ) {
        if (!isActive || pending.isEmpty()) return
        val resolveGate = Semaphore(MAX_PARALLEL_RESOLVES)
        coroutineScope {
            pending.forEach { stream ->
                launch {
                    val hash = DebridSupport.hashFor(stream) ?: return@launch
                    var resolved: Pair<String, String>? = null
                    resolveGate.acquire()
                    try {
                        withTimeoutOrNull(PER_STREAM_TIMEOUT_MS) {
                            for (provider in providers) {
                                val httpUrl = resolveWithDiagnostics(provider, hash, stream)
                                if (httpUrl != null && isPlayable(httpUrl)) {
                                    resolved = provider.tag to httpUrl
                                    break
                                }
                            }
                        }
                    } finally {
                        resolveGate.release()
                    }
                    val (tag, httpUrl) = resolved ?: return@launch
                    applyResolved(stream, hash, tag, httpUrl)
                }
            }
        }
    }

    /** One provider attempt with its diagnostics outcome recorded —
     *  the exact mapping the old inline TorBox lambda used, now
     *  applied uniformly to every provider. */
    private suspend fun resolveWithDiagnostics(
        provider: Provider,
        hash: String,
        stream: StreamItem,
    ): String? {
        val started = System.nanoTime()
        fun record(outcome: String) =
            SourceDiagnostics.record(provider.diagnosticsName, outcome, (System.nanoTime() - started) / 1_000_000)
        return try {
            when (val result = provider.resolve(hash, stream.fileIdx, stream.filePath)) {
                is DebridResolveResult.Resolved -> {
                    record("succeeded")
                    result.url
                }
                is DebridResolveResult.NotCached -> {
                    record("no_results")
                    null
                }
                is DebridResolveResult.Error -> {
                    record(if (result.isAuthError) "authentication_needed" else "failed")
                    null
                }
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            record("failed")
            null
        }
    }

    public companion object {
        public const val PER_STREAM_TIMEOUT_MS: Long = 45_000
        public const val MAX_PARALLEL_RESOLVES: Int = 3

        public const val TAG_TORBOX: String = "⚡ TorBox"
        public const val TAG_REAL_DEBRID: String = "⚡ RD"
        public const val TAG_PREMIUMIZE: String = "⚡ Premiumize"
        public const val TAG_ALLDEBRID: String = "⚡ AllDebrid"
        public const val TAG_DEBRID_LINK: String = "⚡ Debrid-Link"
        public const val TAG_OFFCLOUD: String = "⚡ Offcloud"

        /**
         * Build the provider set from the stored keys, in the fork's
         * canonical order (TorBox → Premiumize → Real-Debrid →
         * AllDebrid → Debrid-Link → Offcloud), with [preferredId]
         * (the Settings > Integrations preferred resolver, ""/null =
         * automatic) moved first when it matches a configured
         * provider. No keys at all yields an inactive service.
         */
        public fun create(
            torboxApiKey: String?,
            realDebridApiKey: String?,
            premiumizeApiKey: String?,
            allDebridApiKey: String?,
            debridLinkApiKey: String?,
            offcloudApiKey: String?,
            preferredId: String? = null,
        ): DebridService {
            val providers = buildList {
                if (!torboxApiKey.isNullOrBlank()) {
                    val torbox = TorBoxApi(torboxApiKey)
                    add(Provider("torbox", TAG_TORBOX, "TorBox") { hash, fileIdx, filePath ->
                        torbox.resolveCachedDetailed(hash, fileIdx, filePath)
                    })
                }
                if (!premiumizeApiKey.isNullOrBlank()) {
                    val premiumize = PremiumizeApi(premiumizeApiKey)
                    add(Provider("premiumize", TAG_PREMIUMIZE, "Premiumize") { hash, fileIdx, filePath ->
                        premiumize.resolveCachedDetailed(hash, fileIdx, filePath)
                    })
                }
                if (!realDebridApiKey.isNullOrBlank()) {
                    val rd = RealDebridApi(realDebridApiKey)
                    add(Provider("realdebrid", TAG_REAL_DEBRID, "RealDebrid") { hash, fileIdx, filePath ->
                        // RealDebridApi collapses "not cached" and
                        // "failed" into null by design (see its docs);
                        // the honest mapping available here is
                        // no_results.
                        rd.resolveCachedToHttp(hash, fileIdx, filePath)
                            ?.let { DebridResolveResult.Resolved(it) }
                            ?: DebridResolveResult.NotCached
                    })
                }
                if (!allDebridApiKey.isNullOrBlank()) {
                    val allDebrid = AllDebridApi(allDebridApiKey)
                    add(Provider("alldebrid", TAG_ALLDEBRID, "AllDebrid") { hash, fileIdx, filePath ->
                        allDebrid.resolveCachedDetailed(hash, fileIdx, filePath)
                    })
                }
                if (!debridLinkApiKey.isNullOrBlank()) {
                    val debridLink = DebridLinkApi(debridLinkApiKey)
                    add(Provider("debridlink", TAG_DEBRID_LINK, "DebridLink") { hash, fileIdx, filePath ->
                        debridLink.resolveCachedDetailed(hash, fileIdx, filePath)
                    })
                }
                if (!offcloudApiKey.isNullOrBlank()) {
                    val offcloud = OffcloudApi(offcloudApiKey)
                    add(Provider("offcloud", TAG_OFFCLOUD, "Offcloud") { hash, fileIdx, filePath ->
                        offcloud.resolveCachedDetailed(hash, fileIdx, filePath)
                    })
                }
            }.let { built ->
                val preferred = preferredId?.trim()?.lowercase()
                if (preferred.isNullOrBlank()) built
                else built.sortedWith(compareByDescending<Provider> { it.id == preferred })
            }
            return DebridService(providers)
        }
    }
}
