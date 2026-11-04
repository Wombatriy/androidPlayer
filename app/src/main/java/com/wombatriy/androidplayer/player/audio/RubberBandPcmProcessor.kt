package com.wombatriy.androidplayer.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessorChain
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * A streaming PCM16 adapter. Only one short input block is processed at a
 * time; output remains inside Rubber Band until the AudioSink consumes it.
 * No Sonic processor is in this AudioProcessorChain.
 *
 * A renderer-thread-confined JNI handle is recreated on Media3 flush/seek,
 * so the previous track's native delay buffers never leak into a new track.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class RubberBandPcmProcessor : BaseAudioProcessor() {
    private var handle: Long = 0L
    private var speed: Float = 1f
    private var inputFrames: Long = 0L
    private var outputFrames: Long = 0L
    private var finalSubmitted = false

    fun setSpeed(value: Float) {
        require(value.isFinite() && value in 0.25f..1.50f)
        if (speed == value) return
        speed = value
        // Media3 applies speed changes when there is no pending input.
        // R3 permits real-time ratio updates without reconstructing the player.
        if (handle != 0L) {
            RubberBandNative.setTimeRatio(handle, 1.0 / speed.toDouble())
        }
    }

    val requestedSpeed: Float get() = speed

    fun mediaDuration(playoutDurationUs: Long): Long {
        // The ratio of consumed input frames to produced, delay-compensated
        // frames estimates actual speed rather than claiming ideal DSP timing.
        val applied = if (outputFrames >= 2048L && inputFrames > 0) {
            (inputFrames.toDouble() / outputFrames).coerceIn(0.20, 1.60)
        } else speed.toDouble()
        return (playoutDurationUs.toDouble() * applied).toLong()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat):
        AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT ||
            inputAudioFormat.channelCount !in 1..2 ||
            inputAudioFormat.sampleRate !in 8000..192000
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        // Always active, including 1.00x, so switching speed at runtime
        // needs neither codec reconfiguration nor a second speed processor.
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        disposeNative()
        inputFrames = 0
        outputFrames = 0
        finalSubmitted = false
        if (inputAudioFormat != AudioProcessor.AudioFormat.NOT_SET) {
            handle = RubberBandNative.create(
                inputAudioFormat.sampleRate,
                inputAudioFormat.channelCount,
                1.0 / speed.toDouble()
            )
            check(handle != 0L) { "Could not initialize Rubber Band R3" }
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (handle == 0L || hasPendingOutput()) return

        // Drain output generated from a previous native block before
        // submitting any more decoded input.
        if (RubberBandNative.available(handle) > 0) {
            emitOutput()
            if (hasPendingOutput()) return
        }
        val format = inputAudioFormat
        val frameBytes = format.bytesPerFrame
        val frames = min(inputBuffer.remaining() / frameBytes, MAX_INPUT_FRAMES)
        if (frames <= 0) {
            check(!inputBuffer.hasRemaining()) {
                "PCM input is not aligned to complete audio frames"
            }
            return
        }
        val bytes = frames * frameBytes
        val block = inputBuffer.duplicate().order(ByteOrder.nativeOrder())
        block.limit(inputBuffer.position() + bytes)
        block.position(inputBuffer.position())
        val directView = block.slice().order(ByteOrder.nativeOrder())
        check(directView.isDirect) { "Media3 must supply direct PCM" }

        RubberBandNative.process(handle, directView, frames, false)
        inputBuffer.position(inputBuffer.position() + bytes)
        inputFrames += frames
        emitOutput()
    }

    override fun getOutput(): ByteBuffer {
        var result = super.getOutput()
        if (!result.hasRemaining() && handle != 0L) {
            emitOutput()
            result = super.getOutput()
        }
        return result
    }

    private fun emitOutput() {
        if (handle == 0L || hasPendingOutput() ||
            RubberBandNative.available(handle) <= 0) return
        val buffer = replaceOutputBuffer(MAX_OUTPUT_FRAMES * inputAudioFormat.bytesPerFrame)
        val produced = RubberBandNative.retrieve(handle, buffer, MAX_OUTPUT_FRAMES)
        check(produced in 0..MAX_OUTPUT_FRAMES) { "Invalid native output frame count" }
        outputFrames += produced
        buffer.limit(produced * inputAudioFormat.bytesPerFrame)
        buffer.position(0)
    }

    override fun onQueueEndOfStream() {
        if (handle != 0L && !finalSubmitted) {
            RubberBandNative.process(handle, null, 0, true)
            finalSubmitted = true
        }
    }

    override fun isEnded(): Boolean =
        super.isEnded() &&
            (handle == 0L || RubberBandNative.available(handle) <= 0)

    override fun onReset() {
        disposeNative()
        inputFrames = 0
        outputFrames = 0
        finalSubmitted = false
        speed = 1f
    }

    private fun disposeNative() {
        if (handle != 0L) {
            RubberBandNative.close(handle)
            handle = 0L
        }
    }

    companion object {
        private const val MAX_INPUT_FRAMES = 1024
        private const val MAX_OUTPUT_FRAMES = 4096
    }
}

/** Replaces Media3's default Sonic stage; NEVER places Rubber Band before Sonic. */
@OptIn(markerClass = [UnstableApi::class])
internal class RubberBandAudioProcessorChain : AudioProcessorChain {
    private val processor = RubberBandPcmProcessor()

    override fun getAudioProcessors(): Array<AudioProcessor> = arrayOf(processor)

    override fun applyPlaybackParameters(params: PlaybackParameters): PlaybackParameters {
        processor.setSpeed(params.speed)
        // Preserve original pitch regardless of the requested value.
        return PlaybackParameters(params.speed, 1f)
    }

    override fun applySkipSilenceEnabled(enabled: Boolean): Boolean = false

    override fun getMediaDuration(playoutDuration: Long): Long =
        processor.mediaDuration(playoutDuration)

    override fun getSkippedOutputFrameCount(): Long = 0L
}
