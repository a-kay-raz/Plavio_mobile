package com.pulsestream.app.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

/** Centre-mix level range in dB (Nuvio CENTER_MIX_LEVEL_MIN/MAX_DB). */
const val CENTER_MIX_LEVEL_MIN_DB = -10
const val CENTER_MIX_LEVEL_MAX_DB = 30

/**
 * Step 66 — Downmix / centre-mix / output channels.
 *
 * Nuvio does all three inside its own ffmpeg audio renderer
 * (`FfmpegAudioDownmix` + `FfmpegAudioRenderer`), which Plavio does not
 * have (no ffmpeg extension on the classpath). The portable equivalent
 * is a stock-Media3 [AudioProcessor] in the audio-sink chain: it mixes
 * multi-channel PCM down to stereo or mono with BS.775-style
 * coefficients, so dialogue (centre) keeps its level against music
 * and effects on stereo output.
 *
 * - Downmix on/off + target channels (stereo/mono) are snapshotted in
 *   [AudioProcessor.configure] — the output channel count is negotiated
 *   there, so toggling them applies on the NEXT video (new player).
 * - Centre-mix level ([DownmixConfig.centerMixDb], Nuvio range
 *   -10..+30 dB) and [DownmixConfig.maintainOriginal] are read live
 *   per buffer — they only change coefficients, so they apply at once.
 *
 * Channel layouts assumed for PCM input (the standard Media3 order):
 * 3ch FL FR FC · 5ch FL FR FC BL BR · 6ch FL FR FC LFE BL BR ·
 * 7ch FL FR FC LFE BL BR BC · 8ch FL FR FC LFE BL BR SL SR.
 * 4ch is treated as quad FL FR BL BR. LFE is dropped in the mix
 * (standard downmix behaviour). Only PCM 16-bit and PCM float are
 * mixed; anything else passes through untouched.
 */
@UnstableApi
class DownmixConfig {
    @Volatile var enabled: Boolean = false
    @Volatile var targetChannels: Int = 2
    @Volatile var centerMixDb: Int = 0
    @Volatile var maintainOriginal: Boolean = true
}

@UnstableApi
class DownmixAudioProcessor(
    private val config: DownmixConfig,
) : AudioProcessor {

    private var inputAudioFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputFormat: AudioFormat = AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded: Boolean = false

    // Snapshotted at configure() — the mix decision must not change
    // mid-stream, or the sink's negotiated channel count would no
    // longer match the bytes we emit.
    private var mixActive: Boolean = false
    private var inChannels: Int = 0
    private var outChannels: Int = 0
    private var bytesPerSample: Int = 0
    private var pending: ByteArray = ByteArray(0)

    override fun isActive(): Boolean = outputFormat != AudioFormat.NOT_SET

    override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

    override fun getOutput(): ByteBuffer {
        val buffer = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return buffer
    }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        this.inputAudioFormat = inputAudioFormat
        val channels = inputAudioFormat.channelCount
        val encoding = inputAudioFormat.encoding
        val supported = encoding == C.ENCODING_PCM_16BIT || encoding == C.ENCODING_PCM_FLOAT
        mixActive = config.enabled &&
            supported &&
            channels >= 3 &&
            config.targetChannels in 1..2 &&
            config.targetChannels < channels
        inChannels = channels
        outChannels = if (mixActive) config.targetChannels else channels
        bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        pending = ByteArray(0)
        outputFormat = if (mixActive) {
            AudioFormat(encoding, outChannels, inputAudioFormat.sampleRate)
        } else {
            inputAudioFormat
        }
        return outputFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        // Combine any partial-frame tail with the new bytes, then
        // process whole frames only; the tail (< 1 frame) waits.
        val inFrameBytes = inChannels * bytesPerSample
        val total = pending.size + inputBuffer.remaining()
        val frames = total / inFrameBytes
        if (frames == 0) {
            val tail = ByteArray(total)
            System.arraycopy(pending, 0, tail, 0, pending.size)
            inputBuffer.get(tail, pending.size, inputBuffer.remaining())
            pending = tail
            return
        }
        val bytesToProcess = frames * inFrameBytes
        val work = ByteArray(bytesToProcess)
        System.arraycopy(pending, 0, work, 0, pending.size)
        inputBuffer.get(work, pending.size, bytesToProcess - pending.size)
        pending = ByteArray(total - bytesToProcess).also { tail ->
            if (tail.isNotEmpty()) inputBuffer.get(tail)
        }

        val outFrameBytes = outChannels * bytesPerSample
        val out = ByteBuffer.allocate(frames * outFrameBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
        val input = ByteBuffer.wrap(work).order(ByteOrder.LITTLE_ENDIAN)
        if (!mixActive) {
            out.put(work)
        } else {
            mixFrames(input, out, frames)
        }
        out.flip()
        outputBuffer = out
    }

    private fun mixFrames(input: ByteBuffer, out: ByteBuffer, frames: Int) {
        // Centre coefficient: 0.707 at 0 dB, scaled by the live level.
        // Surrounds at 0.707 (BS.775). LFE is dropped.
        val centreGain = 0.707f * 10f.pow(config.centerMixDb / 20f)
        val surroundGain = 0.707f
        val normalizeScale = if (config.maintainOriginal) {
            1f
        } else {
            // Worst-case sum of the mixed terms, so a full-scale
            // surround mix can never clip.
            1f / (1f + centreGain + 2f * surroundGain)
        }
        val isFloat = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        repeat(frames) {
            val frameStart = input.position()
            val samples = FloatArray(inChannels) { ch ->
                if (isFloat) {
                    input.getFloat(frameStart + ch * 4)
                } else {
                    input.getShort(frameStart + ch * 2) / 32768f
                }
            }
            // Advance past this input frame.
            input.position(frameStart + inChannels * bytesPerSample)
            val fl = samples.getOrElse(0) { 0f }
            val fr = samples.getOrElse(1) { 0f }
            val fc = if (inChannels >= 3) samples[2] else 0f
            // Layout: 5ch FL FR FC BL BR · 6ch +LFE · 7ch +BC · 8ch +SL SR.
            // 4ch is quad (no centre/LFE): samples 2,3 are BL,BR.
            val bl: Float
            val br: Float
            val sl: Float
            val sr: Float
            when (inChannels) {
                4 -> {
                    bl = samples[2]; br = samples[3]; sl = 0f; sr = 0f
                }
                5 -> {
                    bl = samples[3]; br = samples[4]; sl = 0f; sr = 0f
                }
                6 -> {
                    bl = samples[4]; br = samples[5]; sl = 0f; sr = 0f
                }
                7 -> {
                    bl = samples[4]; br = samples[5]; sl = 0f; sr = 0f
                }
                else -> { // 8
                    bl = samples[4]; br = samples[5]; sl = samples[6]; sr = samples[7]
                }
            }
            if (outChannels == 1) {
                val mono = (0.5f * fl + 0.5f * fr + centreGain * fc +
                    0.5f * surroundGain * (bl + br + sl + sr)) * normalizeScale
                writeSample(out, mono, isFloat)
            } else {
                val left = (fl + centreGain * fc + surroundGain * (bl + sl)) * normalizeScale
                val right = (fr + centreGain * fc + surroundGain * (br + sr)) * normalizeScale
                writeSample(out, left, isFloat)
                writeSample(out, right, isFloat)
            }
        }
    }

    private fun writeSample(out: ByteBuffer, value: Float, isFloat: Boolean) {
        val clamped = value.coerceIn(-1f, 1f)
        if (isFloat) {
            out.putFloat(clamped)
        } else {
            out.putShort((clamped * 32767f).toInt().toShort())
        }
    }

    override fun queueEndOfStream() {
        // A partial frame tail (< 1 frame) is inaudible — drop it.
        pending = ByteArray(0)
        inputEnded = true
    }

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        pending = ByteArray(0)
        inputEnded = false
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputFormat = AudioFormat.NOT_SET
        mixActive = false
        inChannels = 0
        outChannels = 0
        bytesPerSample = 0
    }
}
