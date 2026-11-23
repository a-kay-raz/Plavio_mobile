package com.aviostream.app.ui.screens.player

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.TextureView
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aviostream.app.data.PlaybackHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs

/**
 * Step 59: seek-preview thumbnails (Nuvio player/seekpreview, scoped
 * port). While the user drags the seek bar, a bubble above it shows the
 * frame at the scrub position; the main player never stalls because
 * the frames come from a second ExoPlayer instance.
 *
 * Frame-extraction path, verified first (plan P23):
 * - MediaMetadataRetriever: rejected — a fresh connection per frame,
 *   unreliable on HLS across OEMs, and it bypasses Avio's scoped
 *   header policy (PlaybackHttp).
 * - Thumbnail-track feed (Nuvio's SeekPreviewFeed): rejected — debrid
 *   HLS manifests essentially never carry EXT-X-IMAGE-STREAM-INF /
 *   DASH image tracks, and the feed needs native code Avio has none of.
 * - Nuvio's GL readback (custom shader + renderer): rejected for the
 *   port — the same frame comes out of a TextureView with only stable
 *   public APIs, so ~450 lines of UnstableApi GL were not carried over.
 * - Chosen: a second ExoPlayer rendering to a hidden 320x180
 *   TextureView (off-screen in the decor view), frames via
 *   TextureView.getBitmap(). It reuses PlaybackHttp's scoped client,
 *   so the grabber sends the same headers as playback.
 *
 * Controller shape mirrors Nuvio's: bucketed cache (1–5s buckets, max
 * 100 frames), warm-up 1.2s after playback starts, idle release after
 * 30s, 3 initial failures disable previews for the stream. Deviations:
 * no thumbnail-track feed (above); the prefetch loop is capped at 12
 * targets (Nuvio: 48) to bound the second decoder's work.
 */
private const val FrameMaxWidthPx = 320
private const val MaxCachedFrames = 100
private const val MinBucketMs = 1_000L
private const val MaxBucketMs = 5_000L
private const val BucketsPerTitle = 1_000L
private const val MaxInitialFailures = 3
private const val WarmUpDelayMs = 1_200L
private const val IdleReleaseMs = 30_000L
private const val FrameTimeoutSeconds = 20L
private const val PrefetchCount = 12L
private const val PrefetchMinStepMs = 30_000L
private const val PrefetchDelayMs = 3_000L
private const val PrefetchGapMs = 250L
private const val PrefetchStepTimeoutMs = 20_000L
private const val PrefetchBufferingPollMs = 500L
private const val SeekToleranceUs = 2_000_000L

internal data class SeekPreviewFrame(val positionMs: Long, val bitmap: Bitmap)

internal interface SeekPreviewFrameSource {
    fun frameAt(positionMs: Long, maxWidthPx: Int): Bitmap?
    fun cancel() = Unit
    fun close()
}

internal fun seekPreviewBucketFor(positionMs: Long, durationMs: Long): Long {
    val bucketMs = (durationMs / BucketsPerTitle).coerceIn(MinBucketMs, MaxBucketMs)
    return ((positionMs + bucketMs / 2) / bucketMs * bucketMs).coerceAtMost(durationMs)
}

internal class SeekPreviewController(
    private val url: String,
    private val headers: Map<String, String>,
    private val activity: Activity,
    private val mimeType: String?,
) {
    private val decodeDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val requests = MutableStateFlow<Long?>(null)

    private val cache = LinkedHashMap<Long, Bitmap>()
    private var worker: Job? = null
    private var warmJob: Job? = null
    private var releaseJob: Job? = null
    private var prefetchJob: Job? = null
    private var scrubbing = false
    private var consecutiveFailures = 0
    private var hasSucceeded = false

    @Volatile
    private var source: SeekPreviewFrameSource? = null
    private var sourceOpened = false
    private var connectionGeneration = 0
    private var prefetchDurationMs = 0L
    private var prefetchAnchorMs = 0L
    private var disposed = false

    @Volatile
    var playbackBuffering = false

    var frame by mutableStateOf<SeekPreviewFrame?>(null)
        private set

    var isUnavailable by mutableStateOf(false)
        private set

    fun request(positionMs: Long, durationMs: Long) {
        if (isUnavailable || durationMs <= 0L) return
        scrubbing = true
        prefetchDurationMs = durationMs
        prefetchAnchorMs = positionMs.coerceIn(0L, durationMs)
        warmJob?.cancel()
        releaseJob?.cancel()
        prefetchJob?.cancel()
        val bucket = seekPreviewBucketFor(positionMs.coerceIn(0L, durationMs), durationMs)
        val cached = cache.remove(bucket)?.also { cache[bucket] = it }
        if (cached != null) {
            frame = SeekPreviewFrame(bucket, cached)
        } else {
            nearestCached(bucket)?.let { frame = it }
        }
        requests.value = bucket
        if (worker == null) worker = scope.launch { decodeRequests() }
    }

    fun warmUp(positionMs: Long, durationMs: Long) {
        if (isUnavailable || durationMs <= 0L || scrubbing) return
        prefetchDurationMs = durationMs
        prefetchAnchorMs = positionMs.coerceIn(0L, durationMs)
        releaseJob?.cancel()
        warmJob?.cancel()
        warmJob = scope.launch {
            delay(WarmUpDelayMs)
            if (scrubbing || isUnavailable) return@launch
            val bucket = seekPreviewBucketFor(positionMs.coerceIn(0L, durationMs), durationMs)
            cache[bucket]?.let { frame = SeekPreviewFrame(bucket, it) }
            requests.value = bucket
            if (worker == null) worker = scope.launch { decodeRequests() }
            scheduleRelease()
        }
    }

    fun endScrub() {
        scrubbing = false
        requests.value = null
        if (hasSucceeded) startPrefetch() else scheduleRelease()
    }

    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(IdleReleaseMs)
            if (scrubbing) return@launch
            requests.value = null
            releaseConnection()
        }
    }

    private fun releaseConnection() {
        connectionGeneration++
        source?.cancel()
        CoroutineScope(decodeDispatcher).launch {
            val activeSource = source ?: return@launch
            source = null
            sourceOpened = false
            runCatching { activeSource.close() }
        }
    }

    fun dispose() {
        disposed = true
        scope.cancel()
        source?.cancel()
        CoroutineScope(decodeDispatcher).launch {
            runCatching { source?.close() }
            source = null
        }
    }

    private suspend fun decodeRequests() {
        requests.filterNotNull().collect { bucket ->
            if (cache.containsKey(bucket) && source != null) return@collect
            val generation = connectionGeneration
            val bitmap = try {
                withContext(decodeDispatcher) { decodeFrame(bucket) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                if (generation != connectionGeneration) return@collect
                onFailure(error)
                return@collect
            }
            if (bitmap == null) {
                onFailure(null)
                return@collect
            }
            hasSucceeded = true
            consecutiveFailures = 0
            cache[bucket] = bitmap
            while (cache.size > MaxCachedFrames) {
                cache.remove(cache.keys.first())
            }
            val latest = requests.value
            val shown = frame
            if (latest == bucket || shown == null || shown.positionMs != latest) {
                frame = if (latest == bucket) {
                    SeekPreviewFrame(bucket, bitmap)
                } else {
                    latest?.let(::nearestCached) ?: SeekPreviewFrame(bucket, bitmap)
                }
            }
        }
    }

    private fun decodeFrame(positionMs: Long): Bitmap? {
        if (!sourceOpened) {
            sourceOpened = true
            source = openSeekPreviewFrameSource(activity, url, headers, mimeType)
        }
        val activeSource = source ?: throw UnsupportedSeekPreviewSource
        return activeSource.frameAt(positionMs, FrameMaxWidthPx)
    }

    private fun onFailure(error: Throwable?) {
        consecutiveFailures++
        if (error === UnsupportedSeekPreviewSource) {
            isUnavailable = true
        } else if (!hasSucceeded && consecutiveFailures >= MaxInitialFailures) {
            isUnavailable = true
        }
        if (isUnavailable) {
            frame = null
            worker?.cancel()
            worker = null
            releaseConnection()
        }
    }

    private fun nearestCached(bucket: Long): SeekPreviewFrame? {
        var bestKey: Long? = null
        var bestDistance = Long.MAX_VALUE
        for (key in cache.keys) {
            val distance = abs(key - bucket)
            if (distance < bestDistance) {
                bestDistance = distance
                bestKey = key
            }
        }
        val key = bestKey ?: return null
        val bitmap = cache[key] ?: return null
        return SeekPreviewFrame(key, bitmap)
    }

    private fun startPrefetch() {
        if (disposed || scrubbing || prefetchDurationMs <= 0L) return
        if (prefetchJob?.isActive == true) return
        val durationMs = prefetchDurationMs
        val anchorMs = prefetchAnchorMs.coerceIn(0L, durationMs)
        val stepMs = (durationMs / PrefetchCount).coerceAtLeast(PrefetchMinStepMs)
        prefetchJob = scope.launch {
            delay(PrefetchDelayMs)
            for (target in prefetchTargets(anchorMs, durationMs, stepMs)) {
                while (playbackBuffering && !scrubbing) delay(PrefetchBufferingPollMs)
                if (scrubbing || disposed) return@launch
                val near = nearestCached(target)
                if (near != null && abs(near.positionMs - target) < stepMs / 2) continue
                requests.value = target
                if (worker == null) worker = scope.launch { decodeRequests() }
                // Wait for the decode worker to fill the bucket.
                withTimeoutOrNull(PrefetchStepTimeoutMs) {
                    while (!cache.containsKey(target) && !disposed && !scrubbing) delay(100)
                }
                delay(PrefetchGapMs)
            }
        }
    }

    private fun prefetchTargets(anchorMs: Long, durationMs: Long, stepMs: Long): Sequence<Long> = sequence {
        yield(anchorMs)
        var offset = stepMs
        while (anchorMs + offset <= durationMs || anchorMs - offset >= 0L) {
            if (anchorMs + offset <= durationMs) yield(anchorMs + offset)
            if (anchorMs - offset >= 0L) yield(anchorMs - offset)
            offset += stepMs
        }
    }

    private object UnsupportedSeekPreviewSource : RuntimeException("no frame source for this stream")
}

@Composable
internal fun rememberSeekPreviewController(
    activity: Activity?,
    url: String?,
    headers: Map<String, String>,
    mimeType: String?,
): SeekPreviewController? {
    val controller = remember(url, headers) {
        if (url != null && activity != null) SeekPreviewController(url, headers, activity, mimeType) else null
    }
    DisposableEffect(controller) {
        onDispose { controller?.dispose() }
    }
    return controller
}

/** Only http(s)/file/content streams can be frame-grabbed. */
internal fun openSeekPreviewFrameSource(
    activity: Activity,
    url: String,
    headers: Map<String, String>,
    mimeType: String?,
): SeekPreviewFrameSource? {
    val scheme = Uri.parse(url).scheme?.lowercase()
    if (scheme !in setOf("http", "https", "file", "content", null)) return null
    return TextureViewFrameGrabber(activity, url, headers, mimeType)
}

/**
 * A second ExoPlayer that renders into a hidden 320x180 TextureView
 * parked off-screen in the decor view; frames come from
 * TextureView.getBitmap(). The player is created on the main thread
 * (ExoPlayer is single-threaded), seeks are posted there, and each
 * frameAt waits for the next SurfaceTexture update (20s cap) before
 * reading the bitmap — so a seek that lands on the same frame still
 * returns the (correct) last frame instead of hanging.
 */
@OptIn(UnstableApi::class)
internal class TextureViewFrameGrabber(
    private val activity: Activity,
    private val url: String,
    private val headers: Map<String, String>,
    private val firstMimeType: String?,
) : SeekPreviewFrameSource {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var textureView: TextureView? = null
    @Volatile
    private var player: ExoPlayer? = null
    private var playerError = AtomicReference<Throwable?>(null)
    private var pendingLatch = AtomicReference<CountDownLatch?>(null)
    private var pendingBitmap = AtomicReference<Bitmap?>(null)
    @Volatile
    private var lastBitmap: Bitmap? = null
    @Volatile
    private var closed = false

    private fun <T> onMainThread(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val result = AtomicReference<Result<T>>()
        val done = CountDownLatch(1)
        mainHandler.post {
            result.set(runCatching(block))
            done.countDown()
        }
        check(done.await(FrameTimeoutSeconds, TimeUnit.SECONDS)) { "main thread busy" }
        return result.get().getOrThrow()
    }

    private fun ensurePlayer(startPositionMs: Long) {
        if (player != null) return
        onMainThread {
            val view = TextureView(activity).apply {
                layoutParams = ViewGroup.LayoutParams(FrameMaxWidthPx, FrameMaxWidthPx * 9 / 16)
                // Off-screen but VISIBLE: the SurfaceTexture only
                // produces frames while the view is attached + visible.
                translationX = -10000f
                translationY = -10000f
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = Unit
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                        // Only capture while a frame is awaited —
                        // grabbing every rendered frame while idle
                        // would burn CPU for nothing.
                        val latch = pendingLatch.get() ?: return
                        pendingBitmap.set(runCatching { getBitmap() }.getOrNull())
                        latch.countDown()
                    }
                }
            }
            (activity.window.decorView as ViewGroup).addView(view)
            textureView = view
            val dataSourceFactory = DefaultDataSource.Factory(
                activity,
                PlaybackHttp.factory(url, headers)
            )
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            val exo = ExoPlayer.Builder(activity, mediaSourceFactory)
                .setLoadControl(
                    DefaultLoadControl.Builder()
                        .setBufferDurationsMs(1_000, 2_000, 0, 0)
                        .build()
                )
                .setSeekParameters(SeekParameters(SeekToleranceUs, SeekToleranceUs))
                .build()
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playerError.set(error)
                    pendingLatch.getAndSet(null)?.countDown()
                }
            })
            exo.setVideoTextureView(view)
            exo.playWhenReady = false
            exo.setMediaItem(
                MediaItem.Builder().setUri(url).apply {
                    firstMimeType?.let { setMimeType(it) }
                }.build(),
                startPositionMs
            )
            exo.prepare()
            player = exo
        }
    }

    override fun frameAt(positionMs: Long, maxWidthPx: Int): Bitmap? {
        val targetMs = positionMs.coerceAtLeast(0L)
        ensurePlayer(targetMs)
        playerError.get()?.let { throw it }
        val latch = CountDownLatch(1)
        pendingLatch.set(latch)
        pendingBitmap.set(null)
        onMainThread { player?.seekTo(targetMs) }
        latch.await(FrameTimeoutSeconds, TimeUnit.SECONDS)
        pendingLatch.compareAndSet(latch, null)
        playerError.get()?.let { throw it }
        val bitmap = pendingBitmap.getAndSet(null)?.also { lastBitmap = it } ?: lastBitmap
            ?: onMainThread { runCatching { textureView?.bitmap }.getOrNull() }
            ?: return null
        if (bitmap.width <= maxWidthPx) return bitmap
        val height = (bitmap.height * maxWidthPx / bitmap.width).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, maxWidthPx, height, true)
    }

    override fun cancel() {
        pendingLatch.getAndSet(null)?.countDown()
    }

    override fun close() {
        if (closed) return
        closed = true
        cancel()
        onMainThread {
            runCatching { player?.release() }
            player = null
            textureView?.let { (activity.window.decorView as? ViewGroup)?.removeView(it) }
            textureView = null
        }
    }
}

private val PreviewWidth = 160.dp
private val PreviewShape = RoundedCornerShape(10.dp)
private const val DefaultAspectRatio = 16f / 9f

/**
 * The bubble above the seek bar (Nuvio SeekPreviewOverlay): 160dp
 * wide, positioned by the scrub fraction, clamped to the bar's width,
 * with the scrub time under it. Drives the controller: request() while
 * scrubbing, endScrub() on release, warmUp() once per controller.
 */
@Composable
internal fun SeekPreviewOverlay(
    controller: SeekPreviewController?,
    active: Boolean,
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier,
    gap: Dp = 8.dp,
) {
    if (controller == null || durationMs <= 0L) return
    val visible = active && !controller.isUnavailable

    androidx.compose.runtime.LaunchedEffect(controller, visible, positionMs, durationMs) {
        if (visible) controller.request(positionMs, durationMs)
    }
    androidx.compose.runtime.LaunchedEffect(controller, active) {
        if (!active) controller.endScrub()
    }
    androidx.compose.runtime.LaunchedEffect(controller) {
        controller.warmUp(positionMs, durationMs)
    }

    Layout(
        modifier = modifier,
        content = {
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(tween(120)) + scaleIn(
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(120),
                ),
                exit = fadeOut(tween(160)) + scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    animationSpec = tween(160),
                ),
            ) {
                SeekPreviewBubble(controller = controller, positionMs = positionMs)
            }
        },
    ) { measurables, constraints ->
        val placeable = measurables.firstOrNull()?.measure(Constraints())
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            if (placeable == null) return@layout
            val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val x = (fraction * width - placeable.width / 2f).toInt()
                .coerceIn(0, (width - placeable.width).coerceAtLeast(0))
            placeable.place(x, -placeable.height - gap.roundToPx())
        }
    }
}

@Composable
private fun SeekPreviewBubble(controller: SeekPreviewController, positionMs: Long) {
    val frame = controller.frame
    val aspectRatio = frame?.bitmap
        ?.takeIf { it.width > 0 && it.height > 0 }
        ?.let { it.width.toFloat() / it.height.toFloat() }
        ?.coerceIn(1f, 2.4f)
        ?: DefaultAspectRatio

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = PreviewWidth, height = PreviewWidth / aspectRatio)
                .clip(PreviewShape)
                .background(androidx.compose.ui.graphics.Color.Black)
                .border(1.5.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.9f), PreviewShape),
            contentAlignment = Alignment.Center,
        ) {
            if (frame != null) {
                Image(
                    bitmap = frame.bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = formatMs(positionMs),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .background(
                    androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(6.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
