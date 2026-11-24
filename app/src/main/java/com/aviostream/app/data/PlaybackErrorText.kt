package com.aviostream.app.data

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource

/**
 * Plain-language player errors (v0.14.65, item E — owner's order
 * 2026-11-21 after seeing the reference app's error screen): the
 * player's error screen explains the ACTUAL cause in plain words
 * instead of the same generic line (or a raw Media3 message /
 * "Playback failed (code N)") for everything.
 *
 * ONE mapping, used by the error UI for VOD and Live alike. It
 * changes only what the screen SAYS: the failover ladders, the
 * Live patience retries and the auto-fallback are untouched —
 * they decide WHEN the screen appears, this decides its words.
 * The wording is the owner's table, verbatim.
 */
object PlaybackErrorText {

    /** The error screen's title (the reference's wording). */
    const val TITLE = "Playback error"

    /** The HTTP status carried anywhere in [error]'s cause
     *  chain (an HttpDataSource.InvalidResponseCodeException
     *  carries it), or null when the failure has none. Mirrors
     *  the chain walk the feedback classifier already does. */
    fun httpStatusOf(error: Throwable): Int? {
        generateSequence<Throwable>(error) { it.cause }.take(12).forEach { t ->
            if (t is HttpDataSource.InvalidResponseCodeException) return t.responseCode
        }
        return null
    }

    /** The explanation for a Media3 failure: its error code
     *  plus the HTTP status from its cause chain. */
    fun explanation(error: PlaybackException): String =
        explanation(error.errorCode, httpStatusOf(error))

    /**
     * The mapping itself (pure — unit-tested without a player):
     * an HTTP status, when the failure carries one, is the most
     * precise fact available and wins over the code family.
     */
    fun explanation(errorCode: Int, httpStatus: Int?): String {
        if (httpStatus != null) {
            return when (httpStatus) {
                403 -> "This link has expired, or the server refused it. " +
                    "Try a different source."
                404, 410 -> "This link is dead — the file is no longer there. " +
                    "Try a different source."
                else -> "The server answered with an error (HTTP $httpStatus). " +
                    "Try a different source."
            }
        }
        return when (errorCode) {
            // The server sent something that is not playable
            // video — an error page, a broken manifest, a format
            // the parser does not know.
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
                "The stream source returned invalid or unplayable content. " +
                    "The link may have expired, or the server returned an " +
                    "error page instead of video."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "The source took too long to answer. " +
                    "Check your connection, or try a different source."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Couldn't reach the source. Check your internet and try again."
            // v0.14.29: the three codes the .65 map missed — the
            // hole sat exactly where Live failures live (owner's
            // SportsBite screenshot, 2026-11-21). A bad-HTTP-status
            // failure whose status did not survive the cause chain
            // lands here (a non-null status already returned above):
            // the refusal is real, only its number is lost, so it
            // gets the same refused/expired words as a 403.
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "This link has expired, or the server refused it. " +
                    "Try a different source."
            // The HLS playlist tracker gave up mid-stream (a reset
            // or stuck playlist) — the stream stopped, not the link.
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                "The stream was interrupted. Try again, or try a different source."
            // The playhead fell out of the live window (short
            // playlist, slow start) — a Live-only shape.
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
                "Playback fell behind the live stream. " +
                    "Try again, or try a different source."
            // The device side of the chain: decoder init/query,
            // decoding, audio-track init, and the two
            // format-capability codes beside them.
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ->
                "Your device can't play this format. " +
                    "Try a different source or quality."
            // Anything unmapped keeps a plain generic line —
            // never a raw exception or a code dump.
            else -> "Couldn't play this source. Try a different source."
        }
    }
}
