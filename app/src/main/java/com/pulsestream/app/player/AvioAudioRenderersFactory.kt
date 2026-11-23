package com.pulsestream.app.player

import android.content.Context
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil

/**
 * MediaCodec selector that excludes hardware decoders for audio codecs known to
 * lack hardware support on many devices (EAC3, AC3, DTS, TrueHD) — but only
 * while the FFmpeg extension is available.
 *
 * By returning an empty list for these codecs, ExoPlayer is forced to use the
 * FFmpeg extension renderer (software decoding) instead of failing on MediaCodec.
 * This avoids the "no audio" silent failure on devices like OnePlus 13R that
 * lack Dolby/DTS hardware decoders.
 *
 * The exclusion is conditional on FfmpegLibrary.isAvailable(): when the FFmpeg
 * native lib is missing or failed to load, excluding MediaCodec would
 * guarantee failure (the FFmpeg renderer also refuses the track), so
 * MediaCodec gets the track instead — a device that does have a software
 * decoder for the codec then plays, and a device that has none reports the
 * same unsupported result as before. When FFmpeg IS available, behaviour
 * is unchanged (the OnePlus 13R case still goes to FFmpeg).
 */
@UnstableApi
class NoHardwareDolbySelector : MediaCodecSelector {

    companion object {
        private val SOFTWARE_ONLY_CODECS = setOf(
            MimeTypes.AUDIO_E_AC3,      // Dolby Digital Plus
            MimeTypes.AUDIO_E_AC3_JOC,  // Dolby Digital Plus with JOC (Atmos)
            MimeTypes.AUDIO_AC3,        // Dolby Digital
            MimeTypes.AUDIO_DTS,        // DTS
            MimeTypes.AUDIO_DTS_HD,     // DTS-HD
            MimeTypes.AUDIO_TRUEHD      // Dolby TrueHD
        )
    }

    private val defaultSelector = MediaCodecSelector.DEFAULT

    override fun getDecoderInfos(
        mimeType: String,
        requiresSecureDecoder: Boolean,
        requiresTunnelingDecoder: Boolean
    ): List<MediaCodecInfo> {
        // For Dolby/DTS codecs, return empty list to force FFmpeg software decoding
        // — but only while FFmpeg is actually available. When it is not,
        // MediaCodec gets a chance instead of a certain failure (see KDoc).
        // This prevents MediaCodec from being selected (and failing silently)
        // on devices that lack Dolby/DTS hardware decoders but have FFmpeg.
        if (mimeType in SOFTWARE_ONLY_CODECS && FfmpegLibrary.isAvailable()) {
            return emptyList()
        }
        // For all other codecs, use default behavior.
        return defaultSelector.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
    }
}

/**
 * Custom renderers factory that forces FFmpeg software decoding for Dolby/DTS
 * audio codecs by excluding them from MediaCodec selection.
 *
 * Unlike the previous AvioRenderersFactory (which added a duplicate FFmpeg
 * renderer and broke audio on some devices), this approach uses a custom
 * MediaCodecSelector to prevent MediaCodec from claiming these formats.
 * The standard FFmpeg extension renderer (added via EXTENSION_RENDERER_MODE_PREFER)
 * then handles them via software decoding. The exclusion only applies while
 * FfmpegLibrary.isAvailable() — when FFmpeg is unavailable, MediaCodec gets
 * the track instead of a certain failure.
 *
 * Step 66: when a [DownmixConfig] is supplied, the audio sink carries a
 * [DownmixAudioProcessor] that mixes surround PCM down to stereo/mono.
 * The sink is otherwise the stock DefaultAudioSink (float output off —
 * float output disables audio processing, which would drop the downmix
 * processor from the sink chain).
 */
@UnstableApi
class AvioAudioRenderersFactory(
    context: Context,
    private val downmixConfig: DownmixConfig? = null,
) : DefaultRenderersFactory(context) {

    init {
        setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
        setMediaCodecSelector(NoHardwareDolbySelector())
    }

    // Media3 1.4.1 has no DefaultRenderersFactory.setAudioSink — the sink is
    // built by the protected buildAudioSink(), so the downmix processor is
    // attached there (same DefaultAudioSink settings as the stock build).
    // Float output is forced OFF (the passed-in flag is ignored): Media3
    // disables audio processing — the custom AudioProcessor chain — when
    // float output is on (DefaultAudioSink.configure takes the float branch
    // and never adds audioProcessorChain), so a float sink would silently
    // drop the DownmixAudioProcessor and a 5.1 track would reach the
    // AudioTrack as 6ch and fail (5001) — including on the downmix retry,
    // which would then fail identically. The factory default is already
    // false; this locks the invariant in.
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? {
        val builder = DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        if (downmixConfig != null) {
            builder.setAudioProcessors(arrayOf(DownmixAudioProcessor(downmixConfig)))
        }
        return builder.build()
    }
}
