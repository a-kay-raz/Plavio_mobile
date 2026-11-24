package com.aviostream.app.data

import android.content.Context
import android.util.Log
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.locks.ReentrantLock

/**
 * In-app torrent engine (ported from upstream v0.14.30). Plays
 * torrent/magnet stream rows in the app's own player: a
 * jlibtorrent (libtorrent 1.2 via frostwire's binding) session
 * downloads the chosen file with streaming piece deadlines, and
 * a loopback NanoHTTPD server hands the bytes to ExoPlayer/MPV
 * over plain HTTP with Range support, so the player treats a
 * torrent exactly like any other stream.
 *
 * Lifecycle: the session starts on the first torrent play
 * ([prepare]) and stops when playback exits ([stopCurrent],
 * called by the player route's gate and on dispose). One torrent
 * at a time; seeding happens only while the session lives, i.e.
 * while playing.
 *
 * Cache policy: downloads live in `cacheDir/torrents/<infoHash>/`.
 * Nothing is wiped at app start (re-tapping the same title is
 * instant); instead every [prepare] sweeps the cache back under
 * [MAX_CACHE_BYTES], deleting the least-recently-modified
 * torrent folders first. The active torrent's folder is never
 * swept.
 */
object TorrentEngine {
    private const val TAG = "TorrentEngine"

    /** Cache cap for downloaded torrent data: 2 GiB. */
    private const val MAX_CACHE_BYTES = 2L * 1024 * 1024 * 1024

    /** How long [prepare] waits for swarm metadata before failing. */
    private const val METADATA_TIMEOUT_MS = 120_000L

    /** How long a single HTTP read waits for its piece to land. */
    private const val PIECE_WAIT_TIMEOUT_MS = 90_000L

    /** Pieces kept at deadline priority ahead of the playhead. */
    private const val WINDOW_PIECES = 32

    private val lock = ReentrantLock()

    @Volatile private var stopRequested = false
    @Volatile private var active = false

    private var session: SessionManager? = null
    private var handle: TorrentHandle? = null
    private var server: TorrentHttpServer? = null
    private var currentHash: String? = null
    private var currentUrl: String? = null

    // Geometry of the file being served (set once metadata lands).
    @Volatile private var streamFile: File? = null
    @Volatile private var streamFileSize: Long = 0
    @Volatile private var streamFileTorrentOffset: Long = 0
    @Volatile private var streamPieceLength: Int = 0
    @Volatile private var streamLastPiece: Int = 0

    /**
     * Resolves a torrent row to a local HTTP URL the player can
     * play, or null when the torrent cannot be started (bad hash,
     * no metadata inside the budget, engine failure). Blocking
     * swarm work runs on IO under the engine lock; a concurrent
     * [stopCurrent] aborts the wait instead of deadlocking it.
     */
    suspend fun prepare(
        context: Context,
        magnetUri: String?,
        infoHash: String?,
        fileIdx: Int?
    ): String? {
        val hash = (infoHash ?: torrentHashFromMagnet(magnetUri))
            ?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{40}")) }
            ?: return null
        return withContext(Dispatchers.IO) {
            lock.lock()
            try {
                stopRequested = false
                prepareLocked(context.applicationContext, magnetUri, hash, fileIdx)
            } catch (e: Exception) {
                Log.w(TAG, "prepare failed for $hash", e)
                stopLocked()
                null
            } finally {
                lock.unlock()
            }
        }
    }

    /** Stops the session and the HTTP server. Never blocks long:
     *  an in-flight [prepare] is flagged out and cleans itself up. */
    fun stopCurrent() {
        stopRequested = true
        active = false
        runCatching { server?.stop() }
        server = null
        if (lock.tryLock()) {
            try {
                stopLocked()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun prepareLocked(
        context: Context,
        magnetUri: String?,
        hash: String,
        fileIdx: Int?
    ): String? {
        // Same torrent already live: hand back the running URL.
        if (currentHash == hash && active && server != null &&
            handle?.isValid == true
        ) {
            return currentUrl
        }
        stopLocked()
        stopRequested = false

        val root = File(context.cacheDir, "torrents")
        sweepCacheLocked(root, keepHash = hash)
        val dir = File(root, hash)
        dir.mkdirs()

        val sm = SessionManager()
        sm.start()
        session = sm
        val magnet = magnetUri?.takeIf { it.startsWith("magnet:", true) }
            ?: magnetForHash(hash)
        sm.download(magnet, dir)

        // The handle appears once the session registers the add.
        val sha = Sha1Hash(hash)
        var found: TorrentHandle? = null
        val findDeadline = System.currentTimeMillis() + 30_000L
        while (found == null && System.currentTimeMillis() < findDeadline) {
            if (stopRequested) {
                stopLocked(); return null
            }
            found = runCatching { sm.find(sha) }.getOrNull()?.takeIf { it.isValid }
            if (found == null) Thread.sleep(250)
        }
        val h = found ?: run {
            Log.w(TAG, "torrent handle never appeared for $hash")
            stopLocked(); return null
        }
        handle = h

        // Metadata comes from the swarm (trackers in the magnet,
        // DHT otherwise) — poll for it inside the budget.
        var info: TorrentInfo? = null
        val metaDeadline = System.currentTimeMillis() + METADATA_TIMEOUT_MS
        while (System.currentTimeMillis() < metaDeadline) {
            if (stopRequested) {
                stopLocked(); return null
            }
            info = runCatching { h.torrentFile() }.getOrNull()
            if (info != null) break
            Thread.sleep(500)
        }
        val torrentInfo = info ?: run {
            Log.w(TAG, "no metadata within budget for $hash")
            stopLocked(); return null
        }

        // Choose the file: the row's own index when it names a real
        // file, else the largest file in the torrent.
        val files = torrentInfo.files()
        val numFiles = files.numFiles()
        if (numFiles <= 0) {
            stopLocked(); return null
        }
        var chosen = fileIdx?.takeIf { it in 0 until numFiles && files.fileSize(it) > 0 }
        if (chosen == null) {
            var bestSize = -1L
            for (i in 0 until numFiles) {
                val size = files.fileSize(i)
                if (size > bestSize) {
                    bestSize = size
                    chosen = i
                }
            }
        }
        val fileIndex = chosen ?: run {
            stopLocked(); return null
        }

        // Only the chosen file downloads; everything else is off.
        runCatching {
            h.prioritizeFiles(
                Array(numFiles) { i ->
                    if (i == fileIndex) Priority.SEVEN else Priority.IGNORE
                }
            )
        }

        var torrentOffset = 0L
        for (i in 0 until fileIndex) torrentOffset += files.fileSize(i)
        streamFile = File(dir, files.filePath(fileIndex))
        streamFileSize = files.fileSize(fileIndex)
        streamFileTorrentOffset = torrentOffset
        streamPieceLength = torrentInfo.pieceLength()
        streamLastPiece = torrentInfo.numPieces() - 1

        // Streaming start: deadline the head of the file (players
        // open at byte 0) and its tail (container indexes — e.g.
        // the mp4 moov atom — often live at the end).
        val firstPiece = pieceOf(0L)
        demandWindow(firstPiece)
        val tailPiece = pieceOf(streamFileSize - 1)
        demandWindow(tailPiece - 4)

        val srv = TorrentHttpServer(
            fileProvider = { streamFile },
            fileSizeProvider = { streamFileSize },
            pieceLengthProvider = { streamPieceLength },
            torrentOffsetProvider = { streamFileTorrentOffset },
            mime = mimeForName(files.filePath(fileIndex)),
            awaitBytes = { offset -> awaitBytes(offset) },
            demand = { offset -> demandWindow(pieceOf(offset)) },
            isActive = { active }
        )
        srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        server = srv
        currentHash = hash
        currentUrl = "http://127.0.0.1:${srv.listeningPort}/video"
        active = true
        Log.i(TAG, "torrent $hash file $fileIndex live at $currentUrl")
        return currentUrl
    }

    /** Piece index (torrent-wide) holding the given in-file offset. */
    private fun pieceOf(fileOffset: Long): Int {
        if (streamPieceLength <= 0) return 0
        return ((streamFileTorrentOffset + fileOffset) / streamPieceLength)
            .toInt()
            .coerceIn(0, streamLastPiece)
    }

    /** Puts [WINDOW_PIECES] pieces from [firstPiece] on deadline,
     *  soonest first — libtorrent then fetches them ahead of the
     *  rest, which is what makes the file play as a stream. */
    private fun demandWindow(firstPiece: Int) {
        val h = handle ?: return
        for (i in 0 until WINDOW_PIECES) {
            val piece = firstPiece + i
            if (piece > streamLastPiece) break
            runCatching { h.setPieceDeadline(piece, i * 100) }
        }
    }

    /**
     * Blocks until the piece holding [fileOffset] has downloaded
     * (true) or the wait budget expires / the engine stops (false).
     * Called from HTTP server threads.
     */
    private fun awaitBytes(fileOffset: Long): Boolean {
        val h = handle ?: return false
        val piece = pieceOf(fileOffset)
        demandWindow(piece)
        val deadline = System.currentTimeMillis() + PIECE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!active || stopRequested) return false
            val bits = runCatching {
                h.status(TorrentHandle.QUERY_PIECES).pieces()
            }.getOrNull()
            if (bits != null && bits.getBit(piece)) return true
            Thread.sleep(200)
        }
        return false
    }

    private fun stopLocked() {
        active = false
        runCatching { server?.stop() }
        server = null
        runCatching { session?.stop() }
        session = null
        handle = null
        currentHash = null
        currentUrl = null
        streamFile = null
        streamFileSize = 0
        streamFileTorrentOffset = 0
        streamPieceLength = 0
        streamLastPiece = 0
    }

    /** Deletes least-recently-modified torrent folders until the
     *  cache is under the cap. The folder named [keepHash] (the
     *  torrent being prepared) is never deleted. */
    private fun sweepCacheLocked(root: File, keepHash: String) {
        val dirs = root.listFiles()?.filter { it.isDirectory } ?: return
        fun sizeOf(f: File): Long =
            if (f.isFile) f.length() else f.listFiles()?.sumOf { sizeOf(it) } ?: 0L
        var total = dirs.sumOf { sizeOf(it) }
        if (total <= MAX_CACHE_BYTES) return
        dirs.filter { it.name != keepHash }
            .sortedBy { it.lastModified() }
            .forEach { dir ->
                if (total <= MAX_CACHE_BYTES) return@forEach
                total -= sizeOf(dir)
                dir.deleteRecursively()
            }
    }

    /** InfoHash-only rows carry no tracker list; give the swarm
     *  the well-known public trackers plus DHT (session default). */
    private fun magnetForHash(hash: String): String {
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.tracker.cl:1337/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://opentracker.i2p.rocks:6969/announce",
            "udp://tracker.dler.org:6969/announce"
        )
        val encoded = trackers.joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") }
        return "magnet:?xt=urn:btih:$hash$encoded"
    }

    private fun mimeForName(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "ts", "m2ts" -> "video/mp2t"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            "flv" -> "video/x-flv"
            else -> "video/mp4"
        }
}

/**
 * Loopback HTTP server for one torrent file: GET with Range
 * support; reads block until the pieces behind them have
 * downloaded (that wait IS the streaming), and abort when a piece
 * does not land inside the engine's wait budget or playback has
 * exited — the player's own error ladder then retries like it
 * would for any flaky HTTP source.
 */
private class TorrentHttpServer(
    private val fileProvider: () -> File?,
    private val fileSizeProvider: () -> Long,
    private val pieceLengthProvider: () -> Int,
    private val torrentOffsetProvider: () -> Long,
    private val mime: String,
    private val awaitBytes: (Long) -> Boolean,
    private val demand: (Long) -> Unit,
    private val isActive: () -> Boolean
) : NanoHTTPD("127.0.0.1", 0) {

    override fun serve(session: IHTTPSession): Response {
        if (!isActive()) {
            return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, "text/plain", "stopped"
            )
        }
        val file = fileProvider() ?: return newFixedLengthResponse(
            Response.Status.SERVICE_UNAVAILABLE, "text/plain", "no file"
        )
        val total = fileSizeProvider()
        if (total <= 0) {
            return newFixedLengthResponse(
                Response.Status.SERVICE_UNAVAILABLE, "text/plain", "no data"
            )
        }
        var start = 0L
        var end = total - 1
        var partial = false
        val range = session.headers["range"]
        if (range != null && range.startsWith("bytes=")) {
            val parts = range.removePrefix("bytes=").split("-")
            val s = parts.getOrNull(0)?.toLongOrNull()
            val e = parts.getOrNull(1)?.toLongOrNull()
            if (s != null) {
                start = s
                if (e != null) end = minOf(e, total - 1)
                partial = true
            } else if (e != null) {
                // Suffix range: the last N bytes.
                start = maxOf(0L, total - e)
                partial = true
            }
        }
        if (start < 0 || start >= total || end < start) {
            val resp = newFixedLengthResponse(
                Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "bad range"
            )
            resp.addHeader("Content-Range", "bytes */$total")
            return resp
        }
        val length = end - start + 1
        demand(start)
        val stream = TorrentFileInputStream(
            file, start, end + 1,
            pieceLengthProvider(), torrentOffsetProvider(), awaitBytes
        )
        val status = if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK
        val resp = newFixedLengthResponse(status, mime, stream, length)
        resp.addHeader("Accept-Ranges", "bytes")
        if (partial) {
            resp.addHeader("Content-Range", "bytes $start-$end/$total")
        }
        return resp
    }
}

/**
 * A file stream that never reads past a downloaded piece: each
 * read first waits for the piece under the read position, then
 * reads at most to that piece's end (the engine's await/demand
 * callbacks hold the torrent geometry). A wait that expires
 * reads as EOF, which aborts the HTTP response.
 */
private class TorrentFileInputStream(
    private val file: File,
    start: Long,
    private val endExclusive: Long,
    private val pieceLength: Int,
    private val torrentOffset: Long,
    private val awaitBytes: (Long) -> Boolean
) : InputStream() {
    private var pos = start
    private var raf: RandomAccessFile? = null

    private fun ensureOpen(): Boolean {
        if (raf != null) return true
        // libtorrent creates/allocates the file as pieces land;
        // the first read may race that, so wait briefly for it.
        val deadline = System.currentTimeMillis() + 30_000L
        while (System.currentTimeMillis() < deadline) {
            if (file.exists()) {
                raf = runCatching { RandomAccessFile(file, "r") }.getOrNull()
                if (raf != null) return true
            }
            Thread.sleep(250)
        }
        return false
    }

    override fun read(): Int {
        val one = ByteArray(1)
        val n = read(one, 0, 1)
        return if (n <= 0) n else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (pos >= endExclusive) return -1
        if (!awaitBytes(pos)) return -1
        val reader = raf ?: run {
            if (!ensureOpen()) return -1
            raf!!
        }
        // Cap the read at the current piece's end: awaitBytes
        // vouches only for the piece under pos, so a single read
        // must never cross into the next (possibly undownloaded)
        // piece — its bytes on disk are still zeros.
        val pieceEndInFile = if (pieceLength > 0) {
            val torrentPos = torrentOffset + pos
            (torrentPos / pieceLength + 1) * pieceLength - torrentOffset
        } else {
            endExclusive
        }
        val toRead = minOf(len.toLong(), endExclusive - pos, pieceEndInFile - pos)
            .toInt()
            .coerceAtLeast(1)
        reader.seek(pos)
        val n = reader.read(b, off, toRead)
        if (n > 0) pos += n
        return n
    }

    override fun close() {
        runCatching { raf?.close() }
        raf = null
    }
}
