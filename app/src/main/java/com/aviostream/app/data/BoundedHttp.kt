package com.aviostream.app.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.Source
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Shared cancellable HTTP transport for metadata/API requests. */
object BoundedHttp {
    class HttpFailure(val statusCode: Int) : IOException("Request failed (HTTP $statusCode)")
    const val DEFAULT_MAX_DECODED_BYTES: Long = 2L * 1024 * 1024
    private const val MAX_CONCURRENT_REQUESTS = 12
    private val permits = Semaphore(MAX_CONCURRENT_REQUESTS)
    // DNS choice (v0.14.30 port, #42): the client is cached per choice,
    // so flipping the setting takes effect on the next request without
    // a restart — and the shared pool survives while the choice is stable.
    private val clientLock = Any()
    private var clientChoice: DnsChoice? = null
    private var cachedClient: OkHttpClient? = null
    private val client: OkHttpClient
        get() = synchronized(clientLock) {
            if (cachedClient == null || clientChoice != AppDns.choice) {
                cachedClient = OkHttpClient.Builder()
                    .dns(AppDns.currentDns())
                    .callTimeout(25, TimeUnit.SECONDS)
                    .connectTimeout(12, TimeUnit.SECONDS)
                    .readTimeout(18, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()
                clientChoice = AppDns.choice
            }
            cachedClient!!
        }

    /** Reads decompressed bytes incrementally, rejecting one byte over the cap. */
    internal fun readBoundedUtf8(source: Source, maxBytes: Long): String {
        val buffer = Buffer()
        var count = 0L
        while (true) {
            val n = source.read(buffer, minOf(8192L, maxBytes - count + 1))
            if (n == -1L) break
            count += n
            if (count > maxBytes) throw IOException("Response exceeds the $maxBytes byte limit")
        }
        return buffer.readString(Charsets.UTF_8)
    }

    /**
     * Execute a GET with a process-wide concurrency bound. OkHttp transparently
     * decompresses gzip; the cap is enforced on bytes actually decoded/read.
     * Coroutine cancellation cancels the underlying socket call immediately.
     */
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        maxDecodedBytes: Long = DEFAULT_MAX_DECODED_BYTES
    ): String {
        require(maxDecodedBytes > 0) { "Response size limit must be positive" }
        return permits.withPermit {
            val builder = Request.Builder().url(url).get()
            headers.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) builder.header(key, value)
            }
            execute(builder.build(), maxDecodedBytes)
        }
    }

    private suspend fun execute(request: Request, maxBytes: Long): String =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val text = response.use { resp ->
                            if (!resp.isSuccessful) {
                                throw HttpFailure(resp.code)
                            }
                            val body = resp.body ?: throw IOException("Empty response")
                            val declared = body.contentLength()
                            if (declared > maxBytes) throw IOException("Response exceeds the $maxBytes byte limit")
                            readBoundedUtf8(body.source(), maxBytes)
                        }
                        if (continuation.isActive) continuation.resume(text)
                    } catch (t: Throwable) {
                        if (t is CancellationException) throw t
                        if (continuation.isActive) continuation.resumeWithException(t)
                    }
                }
            })
        }
}
