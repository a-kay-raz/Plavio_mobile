package com.aviostream.app.data

import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.buffer
import java.io.EOFException
import java.io.IOException

/**
 * VLC-style forced seek for hosts that ignore HTTP byte ranges
 * (owner order 2026-10-30, Settings > Playback > "Force seek on any
 * stream").
 *
 * Some hosts answer a ranged request either with a plain 200 (the whole
 * file from byte 0) or with a 206 whose Content-Range still starts at 0 —
 * either way the player is handed bytes from the start of the file when
 * it asked for a later position, so seeking restarts playback instead of
 * jumping. mpv/VLC survive this by reading-and-discarding up to the
 * target; this [Call.Factory] wrapper does the same at the HTTP layer:
 * when a request asks for byte P > 0 and the response's body actually
 * starts earlier than P, the wrapper discards the difference from the
 * body and rewrites the response into an honest 206 (Content-Range +
 * adjusted Content-Length) before the player's data source sees it.
 * From the data source's point of view the host honoured the range.
 *
 * Position-0 opens and honest ranged responses pass through untouched,
 * byte-for-byte the old behaviour. Detection keys off the request's
 * Range header and the actual response — never a hardcoded host list.
 * Compressed bodies are left alone (byte offsets are meaningless inside
 * a gzip stream); a failure mid-discard surfaces as an ordinary
 * IOException, i.e. a normal playback error the failover can handle.
 */
object ForceSeekHttp {

    /** Wraps [delegate] so every ranged call it creates is force-seek aware. */
    fun wrap(delegate: Call.Factory): Call.Factory = Call.Factory { request ->
        val wanted = parseRangeStart(request.header("Range"))
        val call = delegate.newCall(request)
        if (wanted == null || wanted <= 0L) call else ForceSeekCall(call, wanted)
    }

    /** Start offset of a single-range `bytes=P-…` header; null otherwise. */
    private fun parseRangeStart(range: String?): Long? {
        if (range == null || ',' in range) return null
        val match = RANGE_START_REGEX.find(range.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    /** (start, total) parsed from a `bytes S-E/T` Content-Range header. */
    private fun parseContentRange(value: String?): Pair<Long, Long?>? {
        if (value == null) return null
        val match = CONTENT_RANGE_REGEX.find(value.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val total = match.groupValues[2].toLongOrNull()
        return start to total
    }

    /**
     * Rewrites [response] so its body begins at [wantedStart]; returns the
     * response unchanged when it already starts there (or when nothing
     * safe can be done with it).
     */
    internal fun forceSeekResponse(response: Response, wantedStart: Long): Response {
        val bodyStart: Long
        val total: Long?
        when (response.code) {
            200 -> {
                // A 200 to a ranged request serves the file from byte 0;
                // the body's length IS the document size when known.
                bodyStart = 0L
                total = response.body?.contentLength()?.takeIf { it >= 0 }
            }
            206 -> {
                val (start, rangeTotal) =
                    parseContentRange(response.header("Content-Range")) ?: return response
                bodyStart = start
                val partial = response.body?.contentLength()?.takeIf { it >= 0 }
                total = rangeTotal ?: partial?.let { start + it }
            }
            else -> return response
        }
        if (bodyStart >= wantedStart) return response
        // Byte positions are meaningless inside a compressed body.
        val encoding = response.header("Content-Encoding")?.trim()?.lowercase()
        if (encoding != null && encoding != "identity") return response
        val body = response.body ?: return response

        val skip = wantedStart - bodyStart
        val adjustedLength = total?.let { it - wantedStart } ?: -1L
        val newBody = DiscardingResponseBody(body, skip, adjustedLength)
        val builder = response.newBuilder().code(206).body(newBody)
        if (total != null && total > 0) {
            builder.header("Content-Range", "bytes $wantedStart-${total - 1}/$total")
            builder.header("Content-Length", adjustedLength.toString())
        } else {
            builder.removeHeader("Content-Range")
            builder.removeHeader("Content-Length")
        }
        return builder.build()
    }

    private val RANGE_START_REGEX = Regex("""^bytes=(\d+)-""")
    private val CONTENT_RANGE_REGEX = Regex("""^bytes\s+(\d+)-\d+/(\d+|\*)$""")

    /** A [Call] that runs [forceSeekResponse] over the delegate's response. */
    private class ForceSeekCall(
        private val delegate: Call,
        private val wantedStart: Long
    ) : Call by delegate {

        override fun execute(): Response = forceSeekResponse(delegate.execute(), wantedStart)

        override fun enqueue(responseCallback: Callback) {
            delegate.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) =
                    responseCallback.onFailure(this@ForceSeekCall, e)

                override fun onResponse(call: Call, response: Response) =
                    responseCallback.onResponse(this@ForceSeekCall, forceSeekResponse(response, wantedStart))
            })
        }

        override fun clone(): Call = ForceSeekCall(delegate.clone(), wantedStart)
    }

    /** Response body that discards [skipBytes] before its first real byte. */
    private class DiscardingResponseBody(
        private val delegate: ResponseBody,
        private val skipBytes: Long,
        private val adjustedLength: Long
    ) : ResponseBody() {

        override fun contentType(): MediaType? = delegate.contentType()

        override fun contentLength(): Long = adjustedLength

        override fun source(): BufferedSource {
            val upstream = delegate.source()
            val discarding = object : ForwardingSource(upstream) {
                private var remaining = skipBytes
                private val scratch = Buffer()

                override fun read(sink: Buffer, byteCount: Long): Long {
                    while (remaining > 0) {
                        val read = super.read(scratch, minOf(remaining, SCRATCH_BYTES))
                        if (read == -1L) {
                            throw EOFException(
                                "Stream ended $remaining bytes before the seek point"
                            )
                        }
                        scratch.skip(scratch.size)
                        remaining -= read
                    }
                    return super.read(sink, byteCount)
                }
            }
            return discarding.buffer()
        }

        private companion object {
            const val SCRATCH_BYTES = 64 * 1024L
        }
    }
}
