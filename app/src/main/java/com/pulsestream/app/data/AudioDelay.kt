@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.pulsestream.app.data

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.LoadingInfo
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SampleStream
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.source.WrappingMediaSource
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.extractor.text.SubtitleParser

/**
 * Step 65 — Audio delay, lip-sync. Ported verbatim from Nuvio's
 * `AudioDelayMediaSource.kt` (Infuse): there is no Media3 `setAudioDelay`
 * API, so the delay is created by shifting VIDEO sample timestamps.
 * Audio is the master clock in normal playback, so shifting video
 * timestamps in the opposite direction creates a real perceived audio
 * delay: a positive delay means the audio plays that much later than
 * the video (video samples are read earlier), a negative delay the
 * reverse. The delay value is read live from [audioDelayUsProvider]
 * on every sample read, so the slider applies without re-preparing.
 *
 * Range is +/-60s (Nuvio AUDIO_DELAY_MIN/MAX_MS); the provider is the
 * [java.util.concurrent.atomic.AtomicLong] owned by PlayerScreen, seeded
 * from SettingsStore.audioDelayMs and updated by the Audio modal.
 *
 * Deviation: Nuvio also keys the delay per output device
 * (rememberAudioDelayPerDevice) — Plavio keeps one value for the
 * device. No per-output memory.
 */
const val AUDIO_DELAY_MIN_MS = -60_000
const val AUDIO_DELAY_MAX_MS = 60_000
const val AUDIO_DELAY_STEP_MS = 100

/** Wraps every source [delegate] creates in an [AudioDelayMediaSource]. */
internal class AudioDelayMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val audioDelayUsProvider: () -> Long,
) : MediaSource.Factory {

    override fun setCmcdConfigurationFactory(
        cmcdConfigurationFactory: CmcdConfiguration.Factory,
    ): MediaSource.Factory = apply { delegate.setCmcdConfigurationFactory(cmcdConfigurationFactory) }

    override fun setDrmSessionManagerProvider(
        drmSessionManagerProvider: DrmSessionManagerProvider,
    ): MediaSource.Factory = apply { delegate.setDrmSessionManagerProvider(drmSessionManagerProvider) }

    override fun setLoadErrorHandlingPolicy(
        loadErrorHandlingPolicy: LoadErrorHandlingPolicy,
    ): MediaSource.Factory = apply { delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy) }

    override fun setSubtitleParserFactory(
        subtitleParserFactory: SubtitleParser.Factory,
    ): MediaSource.Factory = apply { delegate.setSubtitleParserFactory(subtitleParserFactory) }

    override fun getSupportedTypes(): IntArray = delegate.supportedTypes

    override fun createMediaSource(mediaItem: MediaItem): MediaSource =
        AudioDelayMediaSource(delegate.createMediaSource(mediaItem), audioDelayUsProvider)
}

internal class AudioDelayMediaSource(
    mediaSource: MediaSource,
    private val audioDelayUsProvider: () -> Long,
) : WrappingMediaSource(mediaSource) {

    override fun createPeriod(
        id: MediaSource.MediaPeriodId,
        allocator: Allocator,
        startPositionUs: Long,
    ): MediaPeriod {
        return AudioDelayMediaPeriod(
            mediaPeriod = mediaSource.createPeriod(id, allocator, startPositionUs),
            audioDelayUsProvider = audioDelayUsProvider,
        )
    }

    override fun releasePeriod(mediaPeriod: MediaPeriod) {
        val wrappedPeriod = (mediaPeriod as AudioDelayMediaPeriod).mediaPeriod
        mediaSource.releasePeriod(wrappedPeriod)
    }

    override fun onChildSourceInfoRefreshed(timeline: Timeline) {
        refreshSourceInfo(timeline)
    }
}

private class AudioDelayMediaPeriod(
    val mediaPeriod: MediaPeriod,
    private val audioDelayUsProvider: () -> Long,
) : MediaPeriod, MediaPeriod.Callback {

    private var callback: MediaPeriod.Callback? = null

    override fun prepare(callback: MediaPeriod.Callback, positionUs: Long) {
        this.callback = callback
        mediaPeriod.prepare(this, positionUs)
    }

    override fun maybeThrowPrepareError() {
        mediaPeriod.maybeThrowPrepareError()
    }

    override fun getTrackGroups(): TrackGroupArray = mediaPeriod.getTrackGroups()

    override fun getStreamKeys(trackSelections: List<ExoTrackSelection>): List<StreamKey> {
        return mediaPeriod.getStreamKeys(trackSelections)
    }

    override fun selectTracks(
        selections: Array<ExoTrackSelection?>,
        mayRetainStreamFlags: BooleanArray,
        streams: Array<SampleStream?>,
        streamResetFlags: BooleanArray,
        positionUs: Long,
    ): Long {
        val childStreams = arrayOfNulls<SampleStream>(streams.size)
        for (index in streams.indices) {
            childStreams[index] = (streams[index] as? AudioDelaySampleStream)?.childStream ?: streams[index]
        }

        val selectedPositionUs = mediaPeriod.selectTracks(
            selections,
            mayRetainStreamFlags,
            childStreams,
            streamResetFlags,
            positionUs,
        )

        for (index in streams.indices) {
            val childStream = childStreams[index]
            streams[index] = when {
                childStream == null -> null
                // Audio is the master clock in normal playback, so shifting audio timestamps
                // tends to pull video along with it. Shift video timestamps in the opposite
                // direction to create a real perceived audio delay.
                selections[index]
                    ?.selectedFormat
                    ?.sampleMimeType
                    ?.let(MimeTypes::getTrackType) == C.TRACK_TYPE_VIDEO -> {
                    val currentWrapper = streams[index] as? AudioDelaySampleStream
                    if (currentWrapper?.childStream === childStream) {
                        currentWrapper
                    } else {
                        AudioDelaySampleStream(childStream, audioDelayUsProvider)
                    }
                }
                else -> childStream
            }
        }

        return selectedPositionUs
    }

    override fun discardBuffer(positionUs: Long, toKeyframe: Boolean) {
        mediaPeriod.discardBuffer(positionUs, toKeyframe)
    }

    override fun readDiscontinuity(): Long = mediaPeriod.readDiscontinuity()

    override fun seekToUs(positionUs: Long): Long = mediaPeriod.seekToUs(positionUs)

    override fun getAdjustedSeekPositionUs(
        positionUs: Long,
        seekParameters: SeekParameters,
    ): Long = mediaPeriod.getAdjustedSeekPositionUs(positionUs, seekParameters)

    override fun getBufferedPositionUs(): Long = mediaPeriod.getBufferedPositionUs()

    override fun getNextLoadPositionUs(): Long = mediaPeriod.getNextLoadPositionUs()

    override fun continueLoading(loadingInfo: LoadingInfo): Boolean {
        return mediaPeriod.continueLoading(loadingInfo)
    }

    override fun isLoading(): Boolean = mediaPeriod.isLoading()

    override fun reevaluateBuffer(positionUs: Long) {
        mediaPeriod.reevaluateBuffer(positionUs)
    }

    override fun onPrepared(mediaPeriod: MediaPeriod) {
        callback?.onPrepared(this)
    }

    override fun onContinueLoadingRequested(mediaPeriod: MediaPeriod) {
        callback?.onContinueLoadingRequested(this)
    }
}

private class AudioDelaySampleStream(
    val childStream: SampleStream,
    private val audioDelayUsProvider: () -> Long,
) : SampleStream {

    override fun isReady(): Boolean = childStream.isReady()

    override fun maybeThrowError() {
        childStream.maybeThrowError()
    }

    override fun readData(
        formatHolder: FormatHolder,
        buffer: DecoderInputBuffer,
        readFlags: Int,
    ): Int {
        val result = childStream.readData(formatHolder, buffer, readFlags)
        if (result == C.RESULT_BUFFER_READ) {
            buffer.timeUs -= audioDelayUsProvider()
        }
        return result
    }

    override fun skipData(positionUs: Long): Int {
        return childStream.skipData((positionUs + audioDelayUsProvider()).coerceAtLeast(0L))
    }
}
