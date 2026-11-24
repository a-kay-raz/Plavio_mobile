package com.aviostream.app.player

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.Surface
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import `is`.xyz.mpv.Utils
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The second playback engine (libmpv) — dual-engine overhaul,
 * 2026-11-06. Wraps one libmpv instance with the lifecycle
 * discipline the overhaul is built on:
 *
 *  - EVERY libmpv call runs on one dedicated single thread
 *    ("AvioMpv"); nothing touches the engine from the UI thread.
 *  - Surface teardown order is vo=null → force-window=no →
 *    detachSurface, always on the engine thread.
 *  - Release is idempotent (atomic flag): detach → removeObserver →
 *    destroy → executor shutdown. No `stop` command.
 *  - Observers (this class) check the released flag in every
 *    callback, so a dying instance can never write into a new play.
 *
 * State is published as StateFlows: a position/duration/paused
 * snapshot (fed by observed properties) and the track list.
 */
class MpvPlayerEngine(
    context: Context,
    private val hardwareDecoding: Boolean,
    private val yuv420pFilter: Boolean,
) : MPV.EventObserver {

    /** One row of libmpv's track-list. */
    data class Track(
        val id: Int,
        val type: String, // "video" | "audio" | "sub"
        val title: String?,
        val lang: String?,
        val selected: Boolean,
        val external: Boolean,
    )

    data class Snapshot(
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val paused: Boolean = true,
        val buffering: Boolean = false,
        val eofReached: Boolean = false,
        val seeking: Boolean = false,
        val speed: Float = 1f,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val fileLoaded: Boolean = false,
    )

    private val appContext = context.applicationContext
    private val mpv = MPV()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AvioMpv").apply { isDaemon = true }
    }
    private val released = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var attachedSurface: Surface? = null

    @Volatile
    private var pendingStartMs: Long = 0L

    @Volatile
    private var pendingAudioUrl: String? = null

    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val _tracks = MutableStateFlow<List<Track>>(emptyList())
    val tracks: StateFlow<List<Track>> = _tracks.asStateFlow()

    /** Fatal playback error (END_FILE reason=error). Set before use. */
    @Volatile
    var onFatalError: ((String) -> Unit)? = null

    /** Fired once per load when the file is loaded and tracks exist. */
    @Volatile
    var onFileLoaded: (() -> Unit)? = null

    // -----------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------

    /** Creates + initialises the native engine on the engine thread. */
    fun initialize() {
        executor.execute {
            if (released.get() || !initialized.compareAndSet(false, true)) return@execute
            try {
                Utils.copyAssets(appContext)
                mpv.create(appContext)
                val filesDir = appContext.filesDir
                val cacheDir = appContext.cacheDir
                mpv.setOptionString("config", "yes")
                mpv.setOptionString("config-dir", filesDir.absolutePath)
                mpv.setOptionString("gpu-shader-cache-dir", cacheDir.absolutePath)
                mpv.setOptionString("icc-cache-dir", cacheDir.absolutePath)
                mpv.setOptionString("vo", "gpu-next")
                mpv.setOptionString("profile", "fast")
                mpv.setOptionString("hwdec", if (hardwareDecoding) "auto" else "no")
                if (yuv420pFilter) mpv.setOptionString("vf", "format=yuv420p")
                mpv.setOptionString("msg-level", "all=warn")
                mpv.setOptionString("tls-verify", "yes")
                mpv.setOptionString("tls-ca-file", File(filesDir, "cacert.pem").absolutePath)
                val cacheBytes = (if (Build.VERSION.SDK_INT >= 27) 64 else 32) shl 20
                mpv.setOptionString("demuxer-max-bytes", cacheBytes.toString())
                mpv.setOptionString("demuxer-max-back-bytes", cacheBytes.toString())
                mpv.setOptionString("volume-max", "400")
                mpv.setOptionString("vd-lavc-film-grain", "cpu")
                mpv.setOptionString("keep-open", "true")
                mpv.setOptionString("input-default-bindings", "true")
                mpv.setOptionString("audio-fallback-to-null", "true")
                mpv.init()
                mpv.setPropertyString("force-window", "no")
                mpv.setPropertyString("idle", "once")
                mpv.addObserver(this)
                observe("pause", FORMAT_FLAG)
                observe("paused-for-cache", FORMAT_FLAG)
                observe("core-idle", FORMAT_FLAG)
                observe("eof-reached", FORMAT_FLAG)
                observe("seeking", FORMAT_FLAG)
                observe("duration", FORMAT_DOUBLE)
                observe("time-pos", FORMAT_DOUBLE)
                observe("speed", FORMAT_DOUBLE)
                observe("width", FORMAT_INT64)
                observe("height", FORMAT_INT64)
                observe("track-list", FORMAT_NODE)
                attachedSurface?.let { mpv.attachSurface(it) }
            } catch (e: Exception) {
                Log.e(TAG, "mpv init failed", e)
                if (!released.get()) onFatalError?.invoke("Second engine failed to start")
            }
        }
    }

    /** Surface became available (UI thread): hand it to the engine thread. */
    fun attachSurface(surface: Surface) {
        attachedSurface = surface
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.attachSurface(surface)
            } catch (e: Exception) {
                Log.d(TAG, "attachSurface failed: ${e.message}")
            }
        }
    }

    /**
     * Surface going away: the detach sequence runs on the engine
     * thread (vo=null → force-window=no → detachSurface) so the UI
     * thread never blocks on the native engine.
     */
    fun detachSurface() {
        attachedSurface = null
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyString("vo", "null")
                mpv.setPropertyString("force-window", "no")
                mpv.detachSurface()
                mpv.setPropertyString("vo", "gpu-next")
            } catch (e: Exception) {
                Log.d(TAG, "detachSurface failed: ${e.message}")
            }
        }
    }

    /** Idempotent full release: detach → destroy → thread shutdown. */
    fun release() {
        if (!released.compareAndSet(false, true)) return
        executor.execute {
            try {
                if (initialized.get()) {
                    try {
                        mpv.setPropertyString("vo", "null")
                        mpv.setPropertyString("force-window", "no")
                        mpv.detachSurface()
                    } catch (_: Exception) {
                    }
                    mpv.removeObserver(this)
                    mpv.destroy()
                }
            } catch (e: Exception) {
                Log.d(TAG, "mpv destroy failed: ${e.message}")
            } finally {
                executor.shutdown()
            }
        }
    }

    // -----------------------------------------------------------------
    // Loading + transport
    // -----------------------------------------------------------------

    fun load(url: String, headers: Map<String, String>, startMs: Long, mergeAudioUrl: String?) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                _snapshot.update { it.copy(fileLoaded = false, eofReached = false) }
                pendingStartMs = startMs
                pendingAudioUrl = mergeAudioUrl
                // Request headers: User-Agent gets its own property;
                // the rest ride http-header-fields (commas in values
                // escaped, matching libmpv's option parsing).
                var userAgent: String? = null
                val fields = ArrayList<String>()
                for ((name, value) in headers) {
                    if (name.equals("User-Agent", ignoreCase = true)) {
                        userAgent = value
                    } else if (value.isNotBlank()) {
                        fields += "$name: ${value.replace(",", "\\,")}"
                    }
                }
                if (userAgent != null) mpv.setPropertyString("user-agent", userAgent)
                mpv.setPropertyString(
                    "http-header-fields",
                    if (fields.isEmpty()) "" else fields.joinToString(","),
                )
                mpv.setPropertyString("aid", "auto")
                if (startMs > 0L) {
                    mpv.command("loadfile", url, "replace", "-1", "start=${startMs / 1000.0}")
                } else {
                    mpv.command("loadfile", url, "replace")
                }
            } catch (e: Exception) {
                Log.e(TAG, "mpv load failed", e)
                if (!released.get()) onFatalError?.invoke("Unable to play stream")
            }
        }
    }

    fun setPaused(paused: Boolean) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyBoolean("pause", paused)
            } catch (_: Exception) {
            }
        }
    }

    fun seekTo(positionMs: Long) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.command("seek", (positionMs / 1000.0).toString(), "absolute+exact")
            } catch (_: Exception) {
            }
        }
    }

    fun seekBy(deltaMs: Long) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.command("seek", (deltaMs / 1000.0).toString(), "relative+exact")
            } catch (_: Exception) {
            }
        }
    }

    fun setSpeed(speed: Float) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyDouble("speed", speed.toDouble())
            } catch (_: Exception) {
            }
        }
    }

    /** Resize modes mirror the player's: 0 = Fit, 1 = Fill, 2 = Zoom. */
    fun setResizeMode(mode: Int) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                when (mode) {
                    1 -> {
                        mpv.setPropertyDouble("panscan", 1.0)
                        mpv.setPropertyString("video-aspect-override", "no")
                    }

                    2 -> {
                        mpv.setPropertyDouble("panscan", 0.5)
                        mpv.setPropertyString("video-aspect-override", "no")
                    }

                    else -> {
                        mpv.setPropertyDouble("panscan", 0.0)
                        mpv.setPropertyString("video-aspect-override", "no")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    // -----------------------------------------------------------------
    // Tracks + subtitles
    // -----------------------------------------------------------------

    fun selectAudio(id: Int?) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                if (id == null) mpv.setPropertyString("aid", "no")
                else mpv.setPropertyInt("aid", id)
            } catch (_: Exception) {
            }
        }
    }

    fun selectSubtitle(id: Int?) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                if (id == null) mpv.setPropertyString("sid", "no")
                else mpv.setPropertyInt("sid", id)
            } catch (_: Exception) {
            }
        }
    }

    /** Adds an external subtitle file/URL; [select] makes it current. */
    fun addExternalSubtitle(url: String, select: Boolean) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.command("sub-add", url, if (select) "select" else "cached")
            } catch (_: Exception) {
            }
        }
    }

    fun setSubtitleDelay(delayMs: Long) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyDouble("sub-delay", delayMs / 1000.0)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Subtitle drift correction: MPV's native "sub-speed"
     * multiplier. 1.0 = no correction.
     */
    fun setSubtitleSpeed(speed: Double) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyDouble("sub-speed", speed.coerceIn(0.9, 1.1))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Audio delay: libmpv exposes a real `audio-delay` property, so
     * MPV plays CAN shift audio — unlike ExoPlayer, which has no
     * audio-delay API (the fork shifts it at the renderer instead).
     */
    fun setAudioDelay(delayMs: Long) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyDouble("audio-delay", delayMs / 1000.0)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Applies the shared subtitle style store to libmpv's sub-*
     * properties. Colours are Android ARGB ints; libmpv wants
     * "#AARRGGBB" strings.
     */
    fun applySubtitleStyle(
        textColor: Int,
        backgroundColor: Int,
        edgeColor: Int,
        edgeType: Int,
        fontSizeSp: Int,
        bottomOffset: Int,
        bold: Boolean,
        fontFamily: Int = 0,
    ) {
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                fun hex(color: Int): String = String.format("#%08X", color)
                mpv.setPropertyString("sub-color", hex(textColor))
                mpv.setPropertyString("sub-back-color", hex(backgroundColor))
                mpv.setPropertyString("sub-outline-color", hex(edgeColor))
                val bgAlpha = backgroundColor ushr 24
                mpv.setPropertyString(
                    "sub-border-style",
                    if (bgAlpha == 0) "outline-and-shadow" else "background-box",
                )
                mpv.setPropertyDouble("sub-outline-size", if (edgeType == 1) 2.0 else 0.0)
                mpv.setPropertyDouble(
                    "sub-font-size",
                    (fontSizeSp * 55.0 / 18.0).coerceIn(36.0, 122.0),
                )
                // The user's offset is stored, and sub-pos is pushed
                // through pushSubPos() so the controls-lift (below)
                // composes with it instead of overwriting it.
                subBottomOffsetUnits = (bottomOffset / 10).coerceIn(0, 20)
                pushSubPos()
                mpv.setPropertyString("sub-bold", if (bold) "yes" else "no")
                // Font family: the fork passes 0 (libmpv default) — its
                // subtitle font picks are font files, not fontconfig
                // family names, so there is nothing to map here.
                mpvSubFontName(fontFamily)?.let {
                    mpv.setPropertyString("sub-font", it)
                }
            } catch (_: Exception) {
            }
        }
    }

    // Subtitle lift state — the user's own bottom offset (in
    // sub-pos units, 1 unit = 1% of the frame height) plus the
    // live lift the player adds while its controls are visible,
    // so the captions ride above the control stack and settle
    // back when it fades.
    @Volatile
    private var subBottomOffsetUnits = 0

    @Volatile
    private var subLiftUnits = 0

    /** Pushes the composed sub-pos. Call on the executor. */
    private fun pushSubPos() {
        try {
            mpv.setPropertyInt(
                "sub-pos",
                (100 - subBottomOffsetUnits - subLiftUnits).coerceIn(0, 100)
            )
        } catch (_: Exception) {
        }
    }

    /**
     * Sets the live subtitle lift in sub-pos units (0 = rest
     * position). Deduped — the player feeds this from its lift
     * animation's frame flow.
     */
    fun setSubtitleLiftUnits(units: Int) {
        val u = units.coerceIn(0, 60)
        if (u == subLiftUnits) return
        subLiftUnits = u
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            pushSubPos()
        }
    }

    /**
     * The fine-grained volume the player's volume gesture drives —
     * libmpv's own volume property (0..160 on the gesture's scale;
     * volume-max is 400 from init), so the drag moves in 1-point
     * steps instead of the system volume's coarse steps.
     */
    fun setVolumeFine(totalPercent: Float) {
        val v = totalPercent.coerceIn(0f, 160f).toDouble()
        executor.execute {
            if (released.get() || !initialized.get()) return@execute
            try {
                mpv.setPropertyDouble("volume", v)
            } catch (_: Exception) {
            }
        }
    }

    // -----------------------------------------------------------------
    // MPV.EventObserver — every callback guards on `released`.
    // -----------------------------------------------------------------

    override fun eventProperty(property: String) {
        if (released.get()) return
    }

    override fun eventProperty(property: String, value: Long) {
        if (released.get()) return
        _snapshot.update {
            when (property) {
                "width" -> it.copy(videoWidth = value.toInt())
                "height" -> it.copy(videoHeight = value.toInt())
                else -> it
            }
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (released.get()) return
        _snapshot.update {
            when (property) {
                "pause" -> it.copy(paused = value)
                "paused-for-cache" -> it.copy(buffering = value)
                "eof-reached" -> it.copy(eofReached = value)
                "seeking" -> it.copy(seeking = value)
                else -> it
            }
        }
    }

    override fun eventProperty(property: String, value: String) {
        if (released.get()) return
    }

    override fun eventProperty(property: String, value: Double) {
        if (released.get()) return
        _snapshot.update {
            when (property) {
                "time-pos" -> it.copy(positionMs = (value * 1000).toLong())
                "duration" -> it.copy(durationMs = (value * 1000).toLong())
                "speed" -> it.copy(speed = value.toFloat())
                else -> it
            }
        }
    }

    override fun eventProperty(property: String, value: MPVNode) {
        if (released.get()) return
        if (property == "track-list") {
            _tracks.value = parseTracks(value)
        }
    }

    override fun event(eventId: Int, data: MPVNode) {
        if (released.get()) return
        when (eventId) {
            EVENT_FILE_LOADED -> {
                _snapshot.update { it.copy(fileLoaded = true) }
                // Corrective seek: if the loadfile start option was
                // not honoured (older libmpv), land on the position.
                val start = pendingStartMs
                if (start > 0L && _snapshot.value.positionMs < start - 1_500L) {
                    seekTo(start)
                }
                pendingAudioUrl?.let { audioUrl ->
                    executor.execute {
                        if (released.get() || !initialized.get()) return@execute
                        try {
                            mpv.command("audio-add", audioUrl, "auto")
                        } catch (_: Exception) {
                        }
                    }
                }
                onFileLoaded?.invoke()
            }

            EVENT_END_FILE -> {
                val map = try {
                    data.asMap()
                } catch (_: Exception) {
                    null
                }
                val reason = map?.get("reason")?.asString()
                if (reason == "error") {
                    val message = map["error"]?.asString()?.takeIf { it.isNotBlank() }
                        ?: "Unable to play stream"
                    onFatalError?.invoke(message)
                }
            }
        }
    }

    private fun parseTracks(node: MPVNode): List<Track> {
        val array = try {
            node.asArray()
        } catch (_: Exception) {
            null
        } ?: return emptyList()
        val out = ArrayList<Track>(array.size)
        for (entry in array) {
            val map = try {
                entry.asMap()
            } catch (_: Exception) {
                null
            } ?: continue
            val id = map["id"]?.asInt()?.toInt() ?: continue
            val type = map["type"]?.asString() ?: continue
            if (map["albumart"]?.asBoolean() == true) continue
            out += Track(
                id = id,
                type = type,
                title = map["title"]?.asString(),
                lang = map["lang"]?.asString(),
                selected = map["selected"]?.asBoolean() ?: false,
                external = map["external"]?.asBoolean() ?: false,
            )
        }
        return out
    }

    private fun observe(name: String, format: Int) {
        try {
            mpv.observeProperty(name, format)
        } catch (_: Exception) {
        }
    }

    private companion object {
        const val TAG = "MpvPlayerEngine"
        // libmpv mpv_format values.
        const val FORMAT_FLAG = 3
        const val FORMAT_INT64 = 4
        const val FORMAT_DOUBLE = 5
        const val FORMAT_NODE = 6
        // libmpv event ids.
        const val EVENT_END_FILE = 7
        const val EVENT_FILE_LOADED = 8
    }
}

/**
 * The libmpv `sub-font` name for a font-family index, or null to
 * leave libmpv's default. The names are the fontconfig generics.
 * The fork always passes 0 (default) — its subtitle font picks
 * are font files, not family names.
 */
internal fun mpvSubFontName(family: Int): String? = when (family) {
    1 -> "sans-serif"
    2 -> "serif"
    3 -> "monospace"
    else -> null
}
