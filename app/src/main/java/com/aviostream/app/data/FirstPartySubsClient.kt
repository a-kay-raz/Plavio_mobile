package com.aviostream.app.data

import kotlinx.coroutines.CancellationException

/**
 * First-party subtitles (v0.14.30 port): Avio's own standalone subtitle
 * addon at subs.plavio.site, treated by the player as a BUILT-IN
 * subtitle source — no Addons-list entry, no Integrations row, no
 * account and no key. It speaks the plain Stremio subtitles protocol
 * ({base}/subtitles/{type}/{imdbId}.json; episodes as
 * {imdbId}:{season}:{episode}), so this is just a protocol fetch
 * against a fixed first-party base URL through the shared
 * [StremioApi]. Returned entries carry real file URLs on our own
 * server, so the shared resolve/cache path ([resolveSubtitleFiles])
 * downloads and applies them exactly like addon-provided subtitles —
 * no synthetic scheme, no in-app third-party client, no credentials.
 *
 * The server fills its library from subtitle services on its side
 * (and caches every file it serves), so one fetch here is all the
 * player ever does. Every failure is silent: unreachable or empty
 * just means the panel has no first-party entries for this title.
 */
object FirstPartySubsClient {

    const val BASE_URL = "https://subs.plavio.site"
    const val HOST = "subs.plavio.site"

    /** All subtitles the first-party source has for a title; empty on any failure. */
    suspend fun fetch(
        api: StremioApi,
        type: String,
        imdbId: String,
        season: Int,
        episode: Int
    ): List<Subtitle> {
        val id = if (season > 0 && episode > 0) "$imdbId:$season:$episode" else imdbId
        return try {
            api.fetchSubtitles(BASE_URL, type, id)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }
}
