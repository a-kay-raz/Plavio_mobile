package com.aviostream.app.data.debrid

import com.aviostream.app.data.StreamItem

/**
 * Native debrid layer — pure seams (v0.14.30 port, feature #14).
 *
 * Everything here is side-effect-free:
 *  - which stream rows are debrid-pending and which hash they carry
 *    (infohash placeholders AND magnet URLs are first-class inputs),
 *  - how a resolved row is matched back into the accumulated list,
 *  - the display/pick rank and chip label for resolved rows.
 *
 * The hard rules of the layer live in [DebridService]: no torrent
 * engine, no P2P — unresolved rows stay unplayable and out of
 * autoplay, exactly as before debrid existed.
 *
 * Fork adaptation vs upstream 0.14.30: upstream's [isActive] /
 * account-state helpers derive from validate-on-save verdicts
 * ([KeyValidation]) and an AppConfig "debrid" switch — the fork has
 * neither, so those are NOT ported. The fork's activation is key
 * presence, expressed by [DebridService.isActive]
 * (providers.isNotEmpty()). The rank order below follows the fork's
 * canonical provider order (Step 43): TorBox → Premiumize →
 * Real-Debrid → AllDebrid → Debrid-Link → Offcloud.
 */
public object DebridSupport {

    // ---- Pending set & hash extraction ----

    /**
     * Extract the btih hash from a magnet URL's `xt=urn:btih:` param.
     * Accepts 40-char hex and 32-char base32, returns it lowercased;
     * null for anything else (no xt, wrong scheme, malformed hash).
     */
    public fun magnetHash(url: String): String? {
        if (!url.startsWith("magnet:", ignoreCase = true)) return null
        val query = url.substringAfter('?', "")
        if (query.isEmpty()) return null
        for (part in query.split('&')) {
            if (!part.substringBefore('=').equals("xt", ignoreCase = true)) continue
            val value = part.substringAfter('=', "")
            if (!value.startsWith("urn:btih:", ignoreCase = true)) continue
            val hash = value.substring("urn:btih:".length).trim().lowercase()
            return hash.takeIf { isValidHash(it) }
        }
        return null
    }

    private fun isValidHash(hash: String): Boolean =
        (hash.length == 40 && hash.all { it in '0'..'9' || it in 'a'..'f' }) ||
            (hash.length == 32 && hash.all { it in 'a'..'z' || it in '2'..'7' })

    /**
     * The hash a stream row should be resolved with, or null when the
     * row is not debrid-pending:
     *  - `infohash:` placeholder rows need their infoHash field (the
     *    pre-existing rule, unchanged);
     *  - `magnet:` rows use their infoHash field when present, else
     *    the hash parsed out of the magnet itself;
     *  - every other row (direct links) is never pending.
     */
    public fun hashFor(stream: StreamItem): String? = when {
        stream.url.startsWith("infohash:") ->
            stream.infoHash?.takeIf { it.isNotBlank() }
        stream.url.startsWith("magnet:", ignoreCase = true) ->
            stream.infoHash?.takeIf { it.isNotBlank() } ?: magnetHash(stream.url)
        else -> null
    }

    // ---- Resolved rows: priority + identity ----

    /**
     * True when the row is a debrid-RESOLVED link (was a pending
     * infohash/magnet row, now a direct HTTP link through a debrid
     * provider — i.e. cached on that provider and instant to play).
     */
    public fun isResolved(stream: StreamItem): Boolean =
        stream.resolvedInfoHash != null

    /**
     * Display/pick rank for a stream: debrid-resolved rows rank by
     * provider in the layer's resolution order (TorBox first), every
     * other row ranks last. Lower = earlier.
     */
    public fun debridRank(stream: StreamItem): Int {
        if (stream.resolvedInfoHash == null) return 7
        val name = stream.sourceName ?: return 6
        return when {
            name.startsWith(DebridService.TAG_TORBOX) -> 0
            name.startsWith(DebridService.TAG_PREMIUMIZE) -> 1
            name.startsWith(DebridService.TAG_REAL_DEBRID) -> 2
            name.startsWith(DebridService.TAG_ALLDEBRID) -> 3
            name.startsWith(DebridService.TAG_DEBRID_LINK) -> 4
            name.startsWith(DebridService.TAG_OFFCLOUD) -> 5
            else -> 6
        }
    }

    /**
     * The chip label for a debrid-resolved row: the resolving
     * provider's name + " Cached". Null for non-resolved rows.
     */
    public fun cachedLabel(stream: StreamItem): String? {
        if (stream.resolvedInfoHash == null) return null
        val name = stream.sourceName ?: return "Debrid Cached"
        return when {
            name.startsWith(DebridService.TAG_TORBOX) -> "TorBox Cached"
            name.startsWith(DebridService.TAG_PREMIUMIZE) -> "Premiumize Cached"
            name.startsWith(DebridService.TAG_REAL_DEBRID) -> "Real-Debrid Cached"
            name.startsWith(DebridService.TAG_ALLDEBRID) -> "AllDebrid Cached"
            name.startsWith(DebridService.TAG_DEBRID_LINK) -> "Debrid-Link Cached"
            name.startsWith(DebridService.TAG_OFFCLOUD) -> "Offcloud Cached"
            else -> "Debrid Cached"
        }
    }

    /** True while the list still holds rows the debrid lane can resolve. */
    public fun hasPending(streams: List<StreamItem>): Boolean =
        streams.any { hashFor(it) != null }

    /** The debrid-pending subset of a stream list, in list order. */
    public fun pendingStreams(streams: List<StreamItem>): List<StreamItem> =
        streams.filter { hashFor(it) != null }

    /**
     * Identity match used when a resolved link replaces its pending
     * row in place: same file hints, and the same pending identity —
     * (infoHash + placeholder url) for infohash rows, the exact magnet
     * url for magnet rows.
     */
    public fun isSamePendingRow(candidate: StreamItem, target: StreamItem): Boolean {
        if (candidate.fileIdx != target.fileIdx || candidate.filePath != target.filePath) return false
        return when {
            target.url.startsWith("infohash:") ->
                candidate.infoHash == target.infoHash && candidate.url.startsWith("infohash:")
            target.url.startsWith("magnet:", ignoreCase = true) ->
                candidate.url == target.url
            else -> false
        }
    }
}

/**
 * Conservative file selection shared by the newer providers,
 * mirroring [TorBoxFileSelection]'s contract exactly:
 * `fileIdx` is the zero-based position in the provider's own file
 * list; with no hints at all, ONLY a torrent with exactly one video
 * file resolves (picking the largest video out of a season pack can
 * play the wrong episode); any hint that doesn't match fails the
 * resolve instead of guessing.
 */
public object DebridFileSelection {
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v", "ts")

    /** One file in a provider's listing. [sourceIndex] is the
     *  zero-based position in the provider's original list order. */
    public data class FileEntry(val name: String, val size: Long, val sourceIndex: Int)

    public fun select(files: List<FileEntry>, fileIdx: Int?, filePath: String?): FileEntry? {
        val hasRequestedPath = !filePath.isNullOrBlank()
        if (fileIdx == null && !hasRequestedPath) {
            return files.filter { isVideo(it.name) }.singleOrNull()
        }
        val byIndex = fileIdx?.let { requested -> files.singleOrNull { it.sourceIndex == requested } }
        val normalizedPath = filePath?.takeIf { it.isNotBlank() }?.let(::normalizePath)
        if (fileIdx != null) {
            val indexed = byIndex ?: return null
            return indexed.takeIf { normalizedPath == null || normalizePath(it.name) == normalizedPath }
        }
        val byPath = normalizedPath?.let { requested ->
            files.filter { normalizePath(it.name) == requested }.singleOrNull()
        }
        if (hasRequestedPath && byPath == null) return null
        return byPath
    }

    public fun isVideo(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS

    private fun normalizePath(path: String): String =
        path.replace('\\', '/').trim('/').lowercase()
}
