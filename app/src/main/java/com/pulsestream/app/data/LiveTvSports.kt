package com.pulsestream.app.data

import android.util.Log
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request

/**
 * Live TV sports scores (Nuvio `livetv/LiveTvSports.kt`, LV3).
 *
 * ESPN public, keyless scoreboard endpoints — no account needed. One feed
 * per league (13 leagues), refreshed every minute while something is live,
 * otherwise every five minutes. Events are matched to the user's own
 * channels (team names first, then broadcaster names) so a score card can
 * jump straight to the channel carrying the match.
 *
 * Android notes vs Nuvio (KMP): kotlinx.datetime is replaced with
 * java.time (minSdk 26), `httpGetTextWithHeaders` with the app's OkHttp
 * client, InAppLogger with android.util.Log.
 */

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

/** One ESPN scoreboard feed. Public, keyless endpoints. */
data class LiveTvSportsLeague(
    val id: String,
    val label: String,
    val path: String,
)

internal val liveTvSportsLeagues = listOf(
    LiveTvSportsLeague("eng.1", "Premier League", "soccer/eng.1"),
    LiveTvSportsLeague("esp.1", "LaLiga", "soccer/esp.1"),
    LiveTvSportsLeague("ita.1", "Serie A", "soccer/ita.1"),
    LiveTvSportsLeague("ger.1", "Bundesliga", "soccer/ger.1"),
    LiveTvSportsLeague("fra.1", "Ligue 1", "soccer/fra.1"),
    LiveTvSportsLeague("uefa.champions", "Champions League", "soccer/uefa.champions"),
    LiveTvSportsLeague("uefa.europa", "Europa League", "soccer/uefa.europa"),
    LiveTvSportsLeague("idn.1", "Liga 1 Indonesia", "soccer/idn.1"),
    LiveTvSportsLeague("nba", "NBA", "basketball/nba"),
    LiveTvSportsLeague("nfl", "NFL", "football/nfl"),
    LiveTvSportsLeague("nhl", "NHL", "hockey/nhl"),
    LiveTvSportsLeague("mlb", "MLB", "baseball/mlb"),
    LiveTvSportsLeague("ufc", "UFC", "mma/ufc"),
)

enum class LiveTvSportsState { Upcoming, Live, Finished }

data class LiveTvSportsCompetitor(
    val name: String,
    val shortName: String,
    val logoUrl: String?,
    val score: String?,
    val isHome: Boolean,
    val isWinner: Boolean,
)

data class LiveTvSportsEvent(
    val id: String,
    val league: LiveTvSportsLeague,
    val name: String,
    val startMillis: Long,
    val state: LiveTvSportsState,
    val statusDetail: String,
    val competitors: List<LiveTvSportsCompetitor>,
    val broadcasts: List<String>,
)

data class LiveTvSportsUiState(
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
    val errorMessage: String? = null,
    val events: List<LiveTvSportsEvent> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

// ---------------------------------------------------------------------------------------------
// Repository
// ---------------------------------------------------------------------------------------------

object LiveTvSportsRepository {
    private const val TAG = "LiveTvSports"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val headers = mapOf(
        "Accept" to "application/json",
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36",
    )
    private val _uiState = MutableStateFlow(LiveTvSportsUiState())
    val uiState: StateFlow<LiveTvSportsUiState> = _uiState.asStateFlow()
    private var job: Job? = null

    /** Refreshes when stale: every minute while something is live, otherwise every five minutes. */
    fun refreshIfStale() {
        val state = _uiState.value
        if (state.isLoading) return
        val now = System.currentTimeMillis()
        val maxAge = if (state.events.any { it.state == LiveTvSportsState.Live }) 60_000L else 5 * 60_000L
        if (state.hasLoaded && now - state.updatedAtMillis < maxAge) return
        refresh()
    }

    fun refresh() {
        job?.cancel()
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        job = scope.launch {
            val now = System.currentTimeMillis()
            val range = espnDates(now)
            val results = withContext(Dispatchers.Default) {
                liveTvSportsLeagues.map { league ->
                    async {
                        try {
                            Result.success(fetchLeague(league, range))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Throwable) {
                            Log.w(TAG, "ESPN ${league.id} failed: ${error.message}")
                            Result.failure(error)
                        }
                    }
                }.awaitAll()
            }
            val events = results
                .flatMap { it.getOrNull().orEmpty() }
                .filter { event ->
                    when (event.state) {
                        LiveTvSportsState.Live -> true
                        LiveTvSportsState.Upcoming -> event.startMillis - now <= 48 * SPORTS_HOUR_MILLIS
                        LiveTvSportsState.Finished -> now - event.startMillis <= 18 * SPORTS_HOUR_MILLIS
                    }
                }
                .distinctBy { "${it.league.id}:${it.id}" }
                .sortedWith(liveTvSportsEventOrder)
            val allFailed = results.isNotEmpty() && results.all { it.isFailure }
            val failures = results.mapIndexedNotNull { index, result ->
                result.exceptionOrNull()?.let { "${liveTvSportsLeagues[index].label}: ${it.message ?: it::class.simpleName}" }
            }
            Log.i(TAG, "ESPN refresh: ${events.size} shown, ${failures.size} leagues failed, range=${range.range}")
            val emptyReason = when {
                events.isNotEmpty() -> null
                failures.isNotEmpty() -> failures.take(3).joinToString("\n")
                else -> null
            }
            _uiState.value = LiveTvSportsUiState(
                isLoading = false,
                hasLoaded = true,
                errorMessage = emptyReason ?: if (allFailed) results.first().exceptionOrNull()?.message ?: "Network error" else null,
                events = if (allFailed) _uiState.value.events else events,
                updatedAtMillis = now,
            )
        }
    }

    private suspend fun fetchLeague(league: LiveTvSportsLeague, dates: LiveTvSportsDates): List<LiveTvSportsEvent> {
        val base = "https://site.api.espn.com/apis/site/v2/sports/${league.path}/scoreboard"
        var lastError: Throwable? = null
        suspend fun attempt(url: String): List<LiveTvSportsEvent>? = try {
            fetchScoreboard(league, url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            lastError = error
            Log.w(TAG, "ESPN ${league.id} ${error.message ?: error::class.simpleName} for $url")
            null
        }

        attempt("$base?dates=${dates.range}&limit=1000")?.takeIf { it.isNotEmpty() }?.let { return it }

        val perDay = mutableListOf<LiveTvSportsEvent>()
        var anyDaySucceeded = false
        for (day in dates.days) {
            attempt("$base?dates=$day")?.let { events ->
                anyDaySucceeded = true
                perDay += events
            }
        }
        if (perDay.isNotEmpty()) return perDay

        attempt(base)?.let { return it }
        if (anyDaySucceeded) return emptyList()
        throw lastError ?: IllegalStateException("No scoreboard response")
    }

    private suspend fun fetchScoreboard(league: LiveTvSportsLeague, url: String): List<LiveTvSportsEvent> {
        val text = sportsGetText(url)
        val root = json.parseToJsonElement(text) as? JsonObject ?: return emptyList()
        val raw = root.espnArr("events")
        var firstFailure: String? = null
        val parsed = raw.mapNotNull { element ->
            val event = element as? JsonObject ?: return@mapNotNull null
            runCatching { parseEvent(event, league) }
                .onFailure { if (firstFailure == null) firstFailure = it.message ?: it::class.simpleName }
                .getOrNull()
        }
        if (raw.isNotEmpty() && parsed.isEmpty()) {
            val reason = firstFailure?.let { failure -> " ($failure)" }.orEmpty()
            error("0 of " + raw.size + " events readable" + reason)
        }
        return parsed
    }

    private fun parseEvent(event: JsonObject, league: LiveTvSportsLeague): LiveTvSportsEvent? {
        val id = event.espnStr("id") ?: return null
        val rawDate = event.espnStr("date")
        val startMillis = rawDate?.let(::parseEspnDate) ?: error("unreadable date: " + rawDate)
        val statusType = (event.espnObj("status") ?: event.espnArr("competitions").firstEspnObj()?.espnObj("status"))?.espnObj("type")
        val state = when (statusType?.espnStr("state")) {
            "in" -> LiveTvSportsState.Live
            "post" -> LiveTvSportsState.Finished
            else -> LiveTvSportsState.Upcoming
        }
        val competitions = event.espnArr("competitions").mapNotNull { it as? JsonObject }
        val competition = if (league.id == "ufc") competitions.lastOrNull() else competitions.firstOrNull()
        val competitors = competition?.espnArr("competitors").orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val team = item.espnObj("team")
            val athlete = item.espnObj("athlete")
            val name = team?.espnStr("displayName") ?: athlete?.espnStr("displayName") ?: return@mapNotNull null
            LiveTvSportsCompetitor(
                name = name,
                shortName = team?.espnStr("shortDisplayName") ?: athlete?.espnStr("shortName") ?: name,
                logoUrl = team?.espnStr("logo") ?: athlete?.espnObj("flag")?.espnStr("href"),
                score = item.espnStr("score"),
                isHome = item.espnStr("homeAway") == "home",
                isWinner = (item["winner"] as? JsonPrimitive)?.contentOrNull == "true",
            )
        }.sortedBy { if (it.isHome) 1 else 0 }
        val broadcasts = buildList {
            competitions.forEach { comp ->
                comp.espnArr("broadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.espnArr("names")?.forEach { name ->
                        (name as? JsonPrimitive)?.contentOrNull?.let(::add)
                    }
                }
                comp.espnArr("geoBroadcasts").forEach { broadcast ->
                    (broadcast as? JsonObject)?.espnObj("media")?.espnStr("shortName")?.let(::add)
                }
            }
        }.map(String::trim).filter(String::isNotBlank).distinctBy(String::lowercase)
        return LiveTvSportsEvent(
            id = id,
            league = league,
            name = event.espnStr("name") ?: event.espnStr("shortName") ?: competitors.joinToString(" vs ") { it.name },
            startMillis = startMillis,
            state = state,
            statusDetail = statusType?.espnStr("shortDetail") ?: statusType?.espnStr("detail").orEmpty(),
            competitors = competitors,
            broadcasts = broadcasts,
        )
    }

    private suspend fun sportsGetText(url: String): String =
        withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (name, value) -> requestBuilder.header(name, value) }
            PlaybackHttp.client(url).newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) throw IllegalStateException("HTTP ${response.code} for $url")
                response.body?.string() ?: throw IllegalStateException("Empty response for $url")
            }
        }
}

private const val SPORTS_HOUR_MILLIS = 60L * 60L * 1000L

private val liveTvSportsEventOrder = Comparator<LiveTvSportsEvent> { a, b ->
    val rank = { event: LiveTvSportsEvent ->
        when (event.state) {
            LiveTvSportsState.Live -> 0
            LiveTvSportsState.Upcoming -> 1
            LiveTvSportsState.Finished -> 2
        }
    }
    val byRank = rank(a).compareTo(rank(b))
    when {
        byRank != 0 -> byRank
        a.state == LiveTvSportsState.Finished -> b.startMillis.compareTo(a.startMillis)
        else -> a.startMillis.compareTo(b.startMillis)
    }
}

internal class LiveTvSportsDates(val range: String, val days: List<String>)

/**
 * ESPN scoreboards are keyed by US Eastern calendar days. Covers yesterday through the day after
 * tomorrow, in Eastern time (fixed UTC-5; the one-hour DST error only shifts the window edges).
 */
private fun espnDates(nowMillis: Long): LiveTvSportsDates {
    val eastern = ZoneOffset.ofHours(-5)
    fun ymd(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atOffset(eastern).toLocalDate()
        return "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)
    }
    val days = (-1..2).map { offset -> ymd(nowMillis + offset * 24 * SPORTS_HOUR_MILLIS) }
    return LiveTvSportsDates(range = "${days.first()}-${days.last()}", days = days)
}

private val espnDateRegex = Regex("""(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?""")

/** ESPN ISO date → epoch millis (civil-days algorithm, Nuvio verbatim). */
private fun parseEspnDate(value: String): Long? {
    val match = espnDateRegex.find(value) ?: return null
    val g = match.groupValues
    val year = g[1].toInt()
    val month = g[2].toInt()
    val day = g[3].toInt()
    val hour = g[4].toInt()
    val minute = g[5].toInt()
    val second = g.getOrNull(6)?.toIntOrNull() ?: 0
    val y = if (month <= 2) year - 1 else year
    val era = (if (y >= 0) y else y - 399) / 400
    val yoe = y - era * 400
    val mp = if (month > 2) month - 3 else month + 9
    val doy = (153 * mp + 2) / 5 + day - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    val days = era * 146097L + doe - 719468L
    return ((days * 24 + hour) * 60 + minute) * 60_000L + second * 1000L
}

/** Start time label: "19:30" today, "21/11 19:30" otherwise (device timezone). */
fun formatSportsStart(startMillis: Long, nowMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDateTime()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val time = "%02d:%02d".format(start.hour, start.minute)
    return if (start.toLocalDate() == today) {
        time
    } else {
        "%02d/%02d $time".format(start.dayOfMonth, start.monthValue)
    }
}

private fun JsonObject.espnStr(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotBlank)
private fun JsonObject.espnObj(key: String) = this[key] as? JsonObject
private fun JsonObject.espnArr(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList().orEmpty()
private fun List<JsonElement>.firstEspnObj() = firstOrNull() as? JsonObject

// ---------------------------------------------------------------------------------------------
// Channel matching
// ---------------------------------------------------------------------------------------------

private val channelNoiseTokens = setOf(
    "hd", "fhd", "uhd", "sd", "4k", "8k", "hevc", "h265", "h264", "1080p", "720p", "50fps", "60fps",
    "raw", "vip", "backup", "live", "tv",
)

internal fun normalizeSportsName(value: String): String =
    value.lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in channelNoiseTokens }
        .joinToString(" ")

/**
 * Finds playlist channels that probably carry [event]: channels named after both teams (event
 * channels many providers create per match) rank first, then channels whose name matches one of
 * ESPN's broadcaster names as a whole-word sequence.
 *
 * Plavio note: only provider + M3U channels are LiveTvChannel here — addon
 * channels live in CatalogRows and are not matched.
 */
internal fun matchSportsChannels(
    event: LiveTvSportsEvent,
    channels: List<Pair<LiveTvChannel, String>>,
    limit: Int = 8,
): List<LiveTvChannel> {
    val teamKeys = event.competitors
        .map { normalizeSportsName(it.shortName).ifBlank { normalizeSportsName(it.name) } }
        .filter { it.length >= 3 }
    val broadcastKeys = event.broadcasts.map(::normalizeSportsName).filter { it.length >= 3 }
    if (teamKeys.size < 2 && broadcastKeys.isEmpty()) return emptyList()
    return channels.asSequence()
        .mapNotNull { (channel, normalized) ->
            val padded = " $normalized "
            val score = when {
                teamKeys.size >= 2 && teamKeys.all { padded.contains(" $it ") } -> 3
                broadcastKeys.any { it == normalized } -> 2
                broadcastKeys.any { padded.contains(" $it ") } -> 1
                else -> 0
            }
            if (score > 0) channel to score else null
        }
        .sortedWith(compareByDescending<Pair<LiveTvChannel, Int>> { it.second }.thenBy { it.first.name.length })
        .take(limit)
        .map { it.first }
        .toList()
}
