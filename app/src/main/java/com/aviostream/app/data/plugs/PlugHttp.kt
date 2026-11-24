package com.aviostream.app.data.plugs

import com.aviostream.app.data.AppDns
import com.aviostream.app.data.DnsChoice
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One HTTP answer as plug machinery sees it (body already decoded). */
data class PlugHttpResponse(
    val status: Int,
    val statusText: String,
    val finalUrl: String,
    val headers: Map<String, String>,
    val body: String,
    /**
     * Every header's values, un-joined, in wire order (v0.14.61).
     * [headers] above comma-joins repeats — fine for single-value
     * headers, but it MANGLES `Set-Cookie`: cookie values contain
     * commas themselves (Expires=Wed, 21 Oct …), so a plug reading
     * the joined string can never split the cookies back apart.
     * The JS bridge serialises from THIS map instead, so repeated
     * headers reach JS as separate values (fetch `getSetCookie()`
     * shape). Keyed by the first-seen header name, original case.
     */
    val headerValues: Map<String, List<String>> = emptyMap()
) {
    val ok: Boolean get() = status in 200..299
}

/**
 * The plugs' own HTTP transport (Plugs v1). Deliberately a SEPARATE
 * OkHttp client from the addon transport: plug traffic is untrusted
 * third-party code's traffic — it never carries the app's gate key or
 * any account header (this client simply never adds them), and its
 * timeouts are tuned for scraping (short, fail-fast) rather than for
 * the first-party addon's long cold fills.
 */
object PlugHttp {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Bodies larger than this are treated as a failed fetch. */
    const val MAX_BODY_BYTES: Long = 6L * 1024 * 1024

    // Timeout arithmetic (v0.14.60, per the 2026-11-20 diagnosis):
    // NO component may exceed the per-plug budget (60 s), and there
    // is deliberately NO callTimeout — the plug deadline is the one
    // ceiling, enforced by the host, which cancels the in-flight
    // call the moment the deadline passes. The .59 stack had a
    // 25 s callTimeout sitting inside a 20 s plug budget, so the
    // arithmetic could never work. The reference hosts run their
    // plug client at 60 s components with no call cap; same here.
    // DNS choice (v0.14.30 port, #42): the three clients are cached
    // per DNS choice, so flipping the setting rebuilds them once on
    // the next access — and the shared pools survive while the choice
    // is stable. (Before #42 the fork had no DNS-choice setting, so
    // these were built once, lazily, on the system DNS.)
    private val clientCacheLock = Any()
    private var clientCacheChoice: DnsChoice? = null
    private var cachedClient: OkHttpClient? = null
    private var cachedNoRedirect: OkHttpClient? = null
    private var cachedProbe: OkHttpClient? = null
    private fun cachedClients(): Triple<OkHttpClient, OkHttpClient, OkHttpClient> =
        synchronized(clientCacheLock) {
            if (cachedClient == null || clientCacheChoice != AppDns.choice) {
                val base = OkHttpClient.Builder()
                    .dns(AppDns.currentDns())
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .build()
                cachedClient = base
                cachedNoRedirect = base.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                cachedProbe = base.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .callTimeout(8, TimeUnit.SECONDS)
                    .build()
                clientCacheChoice = AppDns.choice
            }
            Triple(cachedClient!!, cachedNoRedirect!!, cachedProbe!!)
        }

    val client: OkHttpClient get() = cachedClients().first

    /**
     * No-follow twin of [client] (v0.14.58, item 3): used ONLY when
     * a JS plug fetches with `redirect: "manual"` — the plug then
     * sees the raw 3xx response (status + Location header) instead
     * of the final page, which some plugs' resolver stages are
     * written against. Every default fetch keeps [client].
     */
    val clientNoRedirect: OkHttpClient get() = cachedClients().second

    /**
     * Short-timeout client for the dead-stream probe (v0.14.58,
     * item 2): a probe must never hold a streams list up, so its
     * whole call is capped well under the scraping client's.
     * Redirects ARE followed — the final status is the verdict
     * input, exactly what a player would land on.
     */
    val probeClient: OkHttpClient get() = cachedClients().third

    fun newCall(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        followRedirects: Boolean = true
    ): Call {
        val builder = Request.Builder().url(url)
        var hasUa = false
        headers.forEach { (k, v) ->
            if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
            builder.header(k, v)
        }
        if (!hasUa) builder.header("User-Agent", USER_AGENT)
        when (method.uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            // A null body posts as a truly empty body with no content
            // type — some APIs (e.g. MoviesBazar's search) key on
            // exactly the bare POST their own web client sends.
            "POST" -> builder.post(
                body?.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                    ?: ByteArray(0).toRequestBody(null)
            )
            else -> builder.method(method.uppercase(), body?.toRequestBody(null))
        }
        return (if (followRedirects) client else clientNoRedirect).newCall(builder.build())
    }

    /** Suspending fetch for the rule engine and repo downloads. */
    suspend fun fetch(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): PlugHttpResponse = suspendCancellableCoroutine { cont ->
        val call = newCall(url, method, headers, body)
        cont.invokeOnCancellation { runCatching { call.cancel() } }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val parsed = parse(response)
                    if (cont.isActive) cont.resume(parsed)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        })
    }

    /**
     * Blocking fetch for the JS host bridge (runs on a plug thread).
     * [onCallCreated] fires with the live [Call] before it executes,
     * so the JS host can register it against the run's deadline —
     * when the deadline passes, the host cancels the call and this
     * fetch fails immediately instead of overrunning the budget.
     */
    fun fetchBlocking(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        followRedirects: Boolean = true,
        onCallCreated: ((Call) -> Unit)? = null
    ): PlugHttpResponse {
        val call = newCall(url, method, headers, body, followRedirects)
        onCallCreated?.invoke(call)
        return call.execute().use { parse(it) }
    }

    /**
     * Status-only probe (v0.14.58, item 2 — dead-stream hiding):
     * one small ranged GET on [probeClient]; the body is closed
     * unread the moment the status lands, so a probe costs a
     * playlist's headers, never a file download. Returns the FINAL
     * status (redirects followed). Throws the [IOException] on any
     * transport failure — the caller classifies (see
     * PlugProbeClassifier): a timeout is not a death, a refused or
     * unresolvable connection is.
     */
    suspend fun probe(url: String, headers: Map<String, String> = emptyMap()): Int =
        suspendCancellableCoroutine { cont ->
            val builder = Request.Builder().url(url)
            var hasUa = false
            var hasRange = false
            headers.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                if (k.equals("Range", ignoreCase = true)) hasRange = true
                builder.header(k, v)
            }
            if (!hasUa) builder.header("User-Agent", USER_AGENT)
            if (!hasRange) builder.header("Range", "bytes=0-1023")
            val call = probeClient.newCall(builder.get().build())
            cont.invokeOnCancellation { runCatching { call.cancel() } }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) cont.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val code = response.code
                    runCatching { response.close() }
                    if (cont.isActive) cont.resume(code)
                }
            })
        }

    private fun parse(response: Response): PlugHttpResponse {
        val raw = response.body?.bytes() ?: ByteArray(0)
        // fetch() semantics: the body a plug sees is ALWAYS
        // decompressed. OkHttp only transparently gunzips when the
        // request did not set Accept-Encoding itself — plugs (and
        // the sites they scrape) set it freely, so decode here.
        // (Found by the v0.14.55 harness: vixsrc.to answered gzip
        // and the raw bytes reached JSON.parse as \u001f garbage.)
        val bytes = decode(raw, response.header("Content-Encoding"))
        if (bytes.size > MAX_BODY_BYTES) {
            throw IOException("Response exceeds the $MAX_BODY_BYTES byte plug limit")
        }
        val headers = LinkedHashMap<String, String>()
        val headerValues = LinkedHashMap<String, List<String>>()
        for (name in response.headers.names()) {
            val values = response.headers.values(name)
            headerValues[name] = values
            headers[name] = values.joinToString(", ")
        }
        return PlugHttpResponse(
            status = response.code,
            statusText = response.message,
            finalUrl = response.request.url.toString(),
            headers = headers,
            body = bytes.toString(Charsets.UTF_8),
            headerValues = headerValues
        )
    }

    /** Bounded decompression (the size cap applies AFTER decoding). */
    private fun decode(bytes: ByteArray, encoding: String?): ByteArray {
        val stream = when (encoding?.trim()?.lowercase()) {
            "gzip" -> java.util.zip.GZIPInputStream(java.io.ByteArrayInputStream(bytes))
            "deflate" -> java.util.zip.InflaterInputStream(java.io.ByteArrayInputStream(bytes))
            else -> return bytes
        }
        return stream.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > MAX_BODY_BYTES) {
                    throw IOException("Response exceeds the $MAX_BODY_BYTES byte plug limit")
                }
            }
            out.toByteArray()
        }
    }
}

/** Fetcher seam for the rule engine (unit tests inject fixtures). */
interface RuleFetcher {
    suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        method: String = "GET",
        body: String? = null
    ): PlugHttpResponse
}

/** Production fetcher over [PlugHttp]. */
object PlugHttpRuleFetcher : RuleFetcher {
    override suspend fun fetch(
        url: String,
        headers: Map<String, String>,
        method: String,
        body: String?
    ): PlugHttpResponse = PlugHttp.fetch(url, method, headers, body)
}
