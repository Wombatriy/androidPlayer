package com.wombatriy.androidplayer.bpm

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.sqrt

/**
 * Read only the first 70s of selected SAF audio as a 100 Hz RMS envelope.
 * Decoder and codec resources are always closed, including on cancellation.
 */
class AndroidBpmDecoder(private val context: Context) {
    fun analyze(uri: Uri, cancelled: () -> Boolean): BeatEstimator.Estimate? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        try {
            val file = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open the selected audio file")
            return file.use {
                extractor.setDataSource(it.fileDescriptor)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("No audio track in this file")
                val format = extractor.getTrackFormat(trackIndex)
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: error("Audio codec is not specified")
                extractor.selectTrack(trackIndex)
                val decoder = MediaCodec.createDecoderByType(mime)
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()
                started = true

                val info = MediaCodec.BufferInfo()
                var outputFormat = format
                var envelope: PcmEnergyEnvelope? = null
                var endInput = false
                var endOutput = false
                val startedAt = SystemClock.elapsedRealtime()

                while (!endOutput) {
                    if (cancelled()) throw CancellationException("BPM analysis cancelled")
                    if (SystemClock.elapsedRealtime() - startedAt > TIMEOUT_MS) {
                        error("Audio decoding exceeded the time limit")
                    }
                    if (!endInput) {
                        val index = decoder.dequeueInputBuffer(10_000L)
                        if (index >= 0) {
                            val buffer = decoder.getInputBuffer(index)
                                ?: error("Missing decoder input buffer")
                            buffer.clear()
                            val timestamp = extractor.sampleTime
                            val size = if (timestamp < 0 || timestamp >= MAX_AUDIO_US) {
                                -1
                            } else extractor.readSampleData(buffer, 0)
                            if (size < 0) {
                                decoder.queueInputBuffer(
                                    index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                endInput = true
                            } else {
                                decoder.queueInputBuffer(index, 0, size, timestamp, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val index = decoder.dequeueOutputBuffer(info, 10_000L)
                    when {
                        index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                            outputFormat = decoder.outputFormat
                        index >= 0 -> {
                            try {
                                if (info.size > 0) {
                                    val pcm = decoder.getOutputBuffer(index)
                                        ?: error("Missing decoder output buffer")
                                    pcm.position(info.offset)
                                    pcm.limit(info.offset + info.size)
                                    val collector = envelope ?: PcmEnergyEnvelope(
                                        outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                    ).also { envelope = it }
                                    val channels = outputFormat.getInteger(
                                        MediaFormat.KEY_CHANNEL_COUNT
                                    )
                                    val encoding = if (outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                                        outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                    } else AudioFormat.ENCODING_PCM_16BIT
                                    collector.consume(pcm, channels, encoding)
                                    if (collector.finished) endOutput = true
                                }
                                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                    endOutput = true
                                }
                            } finally {
                                decoder.releaseOutputBuffer(index, false)
                            }
                        }
                    }
                }
                if (cancelled()) throw CancellationException("BPM analysis cancelled")
                BeatEstimator.estimate(envelope?.finish() ?: DoubleArray(0))
            }
        } finally {
            if (started) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    private class PcmEnergyEnvelope(sampleRate: Int) {
        private val samplesPerBin = (sampleRate / FRAMES_PER_SECOND).coerceAtLeast(1)
        private val levels = ArrayList<Double>(MAX_SECONDS * FRAMES_PER_SECOND)
        private var powerSum = 0.0
        private var frameCount = 0

        val finished: Boolean get() = levels.size >= MAX_SECONDS * FRAMES_PER_SECOND

        fun consume(buffer: ByteBuffer, channels: Int, encoding: Int) {
            require(channels in 1..32) { "Unsupported channel count: $channels" }
            buffer.order(ByteOrder.nativeOrder())
            val floatPcm = encoding == AudioFormat.ENCODING_PCM_FLOAT
            require(floatPcm || encoding == AudioFormat.ENCODING_PCM_16BIT) {
                "Unsupported PCM encoding: $encoding"
            }
            val bytesPerFrame = (if (floatPcm) 4 else 2) * channels
            while (!finished && buffer.remaining() >= bytesPerFrame) {
                var power = 0.0
                repeat(channels) {
                    val sample = if (floatPcm) buffer.float.toDouble()
                        else buffer.short.toDouble() / 32768.0
                    power += sample * sample
                }
                powerSum += power / channels
                frameCount++
                if (frameCount >= samplesPerBin) {
                    levels.add(sqrt(powerSum / frameCount))
                    powerSum = 0.0
                    frameCount = 0
                }
            }
        }

        fun finish(): DoubleArray {
            if (frameCount > samplesPerBin / 2 && !finished) {
                levels.add(sqrt(powerSum / frameCount))
            }
            return levels.toDoubleArray()
        }
    }

    companion object {
        private const val FRAMES_PER_SECOND = 100
        private const val MAX_SECONDS = 70
        private const val MAX_AUDIO_US = MAX_SECONDS * 1_000_000L
        private const val TIMEOUT_MS = 35_000L
    }
}
