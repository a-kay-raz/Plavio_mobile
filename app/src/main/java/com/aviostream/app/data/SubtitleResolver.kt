package com.aviostream.app.data

import androidx.media3.common.MimeTypes
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** A subtitle downloaded to the local cache and positively identified by content. */
data class ResolvedSubtitle(
    val sub: Subtitle,
    val file: File,
    val mimeType: String
)

private const val SUB_CACHE_DIR = "avio_subs"
/** Bump when the download logic changes so stale cached files are ignored. */
private const val SUB_CACHE_VERSION = "v3"
private const val SNIFF_BYTES = 4096
private const val MAX_SUB_BYTES = 4 * 1024 * 1024

// ---------------------------------------------------------------------
// Automatic-selection helpers (v0.14.30 #47). Addon subtitle data is just
// {url, lang} — there is no SDH or forced flag to read — so both are
// NAME-based best-effort: tokens are matched in the subtitle's URL
// (file name / path), the only descriptive text available.
// ---------------------------------------------------------------------

private val SDH_TOKEN_REGEX = Regex(
    "(?i)(?<![a-z0-9])(sdh|cc)(?![a-z0-9])|hearing[\\s._-]*impaired|(?<=[._\\-])hi(?=[._\\-])"
)

/** Best-effort: does this subtitle look like an SDH / hearing-impaired track? */
fun subtitleLooksSdh(sub: Subtitle): Boolean = SDH_TOKEN_REGEX.containsMatchIn(sub.url)

/** Best-effort: does this subtitle look like a forced track? */
fun subtitleLooksForced(sub: Subtitle): Boolean =
    sub.url.contains("forced", ignoreCase = true)

/**
 * Ranks auto-selection candidates best-first. [priorityOf] gives the
 * language priority (lower = better; Int.MAX_VALUE = not a preferred
 * language). Within one priority: a forced track ranks below a full
 * track (it only covers foreign-language dialogue); when [preferSdh]
 * is set an SDH-looking track ranks above a plain one. Stable — the
 * original order breaks remaining ties.
 */
fun rankAutoPickCandidates(
    subs: List<Subtitle>,
    priorityOf: (Subtitle) -> Int,
    preferSdh: Boolean,
    forcedAutoShow: Boolean
): List<Subtitle> =
    subs.filter { forcedAutoShow || !subtitleLooksForced(it) }
        .sortedWith(
            compareBy(
                { priorityOf(it) },
                { if (subtitleLooksForced(it)) 1 else 0 },
                { if (preferSdh && !subtitleLooksSdh(it)) 1 else 0 }
            )
        )

/**
 * The shared "which subtitle turns on automatically" decision, used by
 * the player for side-loaded tracks. Returns an index into [subs] or
 * -1 for "none".
 *
 * - [SUB_DEFAULT_OFF]: never auto-selects.
 * - [SUB_DEFAULT_ON_IN_MY_LANGUAGES]: with preferences set, the best
 *   track in a preferred language (priority order wins); with no
 *   preferences, the best available track.
 * - [SUB_DEFAULT_ALWAYS_ON]: like the above, but when preferences are
 *   set and nothing matches them, the best available track still turns
 *   on instead of none.
 */
fun autoPickSubtitleIndex(
    subs: List<Subtitle>,
    languages: List<String>,
    defaultMode: Int,
    preferSdh: Boolean,
    forcedAutoShow: Boolean
): Int {
    if (subs.isEmpty() || defaultMode == SUB_DEFAULT_OFF) return -1
    fun priorityOf(sub: Subtitle): Int {
        if (languages.isEmpty()) return 0
        val idx = languages.indexOfFirst { pref -> subtitleLangMatches(sub.lang, pref) }
        return if (idx >= 0) idx else Int.MAX_VALUE
    }
    val ranked = rankAutoPickCandidates(subs, ::priorityOf, preferSdh, forcedAutoShow)
    val best = ranked.firstOrNull() ?: return -1
    if (languages.isNotEmpty() && priorityOf(best) == Int.MAX_VALUE &&
        defaultMode != SUB_DEFAULT_ALWAYS_ON
    ) {
        return -1
    }
    return subs.indexOf(best)
}

/**
 * Downloads side-loaded subtitles concurrently. Request headers are accepted only
 * after normal playback-header validation; PlaybackHttp strips credentials on
 * cross-origin requests/redirects, matching the player’s security boundary.
 * Invalid HTML/JSON/error bodies are rejected rather than mislabelled as SRT.
 */
suspend fun resolveSubtitleFiles(
    subs: List<Subtitle>,
    cacheDir: File,
    requestHeaders: Map<String, String> = emptyMap(),
    streamOriginUrl: String? = null,
    isTv: Boolean = false
): List<ResolvedSubtitle> = withContext(Dispatchers.IO) {
    val dir = File(cacheDir, SUB_CACHE_DIR).apply { mkdirs() }
    dir.listFiles()?.forEach { f ->
        if (f.isFile && !f.name.startsWith("${SUB_CACHE_VERSION}_")) f.delete()
    }
    val safeHeaders = PlaybackHeaders.buildPlaybackHeaders(requestHeaders, isTv)
    val client = if (!streamOriginUrl.isNullOrBlank()) {
        PlaybackHttp.client(streamOriginUrl)
    } else {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followSslRedirects(false)
            .build()
    }
    subs.map { sub ->
        async {
            try {
                // Include a digest of the header set so a cached subtitle from a
                // previous account/session cannot be reused for the same URL.
                val identity = sub.url + "\n" + safeHeaders.toSortedMap().entries.joinToString("\n") { "${it.key}:${it.value}" }
                val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
                    .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val file = File(dir, "${SUB_CACHE_VERSION}_sub_$digest")
                if (!file.exists() || file.length() == 0L) downloadTo(sub.url, file, client, safeHeaders)
                if (!file.exists() || file.length() == 0L) return@async null
                val head = file.inputStream().use { input ->
                    val buf = ByteArray(SNIFF_BYTES)
                    val n = input.read(buf)
                    if (n <= 0) return@async null
                    String(buf, 0, n, Charsets.UTF_8).removePrefix("\uFEFF")
                }
                val mime = detectSubtitleMimeType(head) ?: run { file.delete(); return@async null }
                ResolvedSubtitle(sub, file, mime)
            } catch (_: Exception) {
                null
            }
        }
    }.awaitAll().filterNotNull()
}

/** Recognize actual subtitle syntax, not merely “anything that isn't WEBVTT”. */
internal fun detectSubtitleMimeType(content: String): String? {
    val text = content.removePrefix("\uFEFF").trimStart()
    if (text.startsWith("WEBVTT", ignoreCase = true) &&
        (text.length == 6 || text[6].isWhitespace() || text[6] == '\n' || text[6] == '\r')) {
        return MimeTypes.TEXT_VTT
    }
    val srtTimestamp = Regex("(?m)^\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}")
    val numberedSrt = Regex("(?m)^\\s*\\d+\\s*\\r?\\n\\s*\\d{2}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->")
    if (srtTimestamp.containsMatchIn(text) || numberedSrt.containsMatchIn(text)) {
        return MimeTypes.APPLICATION_SUBRIP
    }
    // Looser SRT shapes the strict pair above rejects wholesale — one-digit
    // hours, 1–2 digit millisecond fields, a missing numeric cue index — are
    // still real SubRip files. Dropping them at resolve time silently removed
    // files the panel listed from the resolved set, so a tap on them could
    // never display anything. Accept a file as SubRip when its head carries
    // at least two plausible cue timing lines in the lenient shape; HTML/JSON
    // error bodies still fail (they contain no "-->" timing lines at all).
    val lenientTiming = Regex(
        "(?m)^\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}\\s+-->\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{1,3}"
    )
    return if (lenientTiming.findAll(text.take(8192)).take(2).count() >= 2) {
        MimeTypes.APPLICATION_SUBRIP
    } else {
        null
    }
}

private fun downloadTo(
    url: String,
    dest: File,
    client: OkHttpClient,
    headers: Map<String, String>
) {
    val request = Request.Builder().url(url).apply {
        headers.forEach { (name, value) -> header(name, value) }
    }.get().build()
    try {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Subtitle HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty subtitle response")
            if (body.contentLength() > MAX_SUB_BYTES) throw IOException("Subtitle exceeds size limit")
            body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_SUB_BYTES) throw IOException("Subtitle exceeds size limit")
                        output.write(buf, 0, n)
                    }
                }
            }
        }
    } catch (e: Exception) {
        dest.delete()
        throw e
    }
}
