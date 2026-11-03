package com.wombatriy.androidplayer.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.SystemClock
import com.wombatriy.androidplayer.audio.AudioDecoderSlots
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CancellationException
import kotlin.math.abs

/**
 * One streaming decode of an entire song into 50 ms peak buckets.
 * Never called on the UI thread. No PCM is retained after processing.
 *
 * AudioDecoderSlots limits foreground+batch BPM+waveform to two codecs total.
 * WaveformCoordinator owns just one worker, so waveform itself is sequential.
 */
class WaveformDecoder(private val context: Context) {
    fun analyze(
        uri: Uri,
        cancelled: () -> Boolean,
        onProgress: (Int) -> Unit = {}
    ): WaveformData =
        AudioDecoderSlots.withPermit(cancelled) {
            decode(uri, cancelled, onProgress)
        }

    private fun decode(
        uri: Uri,
        cancelled: () -> Boolean,
        onProgress: (Int) -> Unit
    ): WaveformData {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        var started = false
        try {
            val file = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Cannot open audio file")
            return file.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                    extractor.getTrackFormat(index)
                        .getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: error("No audio stream")
                val format = extractor.getTrackFormat(trackIndex)
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else 0L
                require(durationUs > 0L) { "Audio duration unavailable" }
                require(durationUs <= MAX_DURATION_US) {
                    "Waveform preview unavailable for tracks longer than 25 minutes"
                }
                val mime = format.getString(MediaFormat.KEY_MIME)
                    ?: error("Missing audio format")
                extractor.selectTrack(trackIndex)
                val decoder = MediaCodec.createDecoderByType(mime)
                codec = decoder
                decoder.configure(format, null, null, 0)
                decoder.start()
                started = true

                val durationMs = durationUs / 1_000L
                val peaks = DoubleArray(WaveformMath.binsForDuration(durationMs))
                val info = MediaCodec.BufferInfo()
                var outputFormat = format
                var inputEnded = false
                var outputEnded = false
                var sampleCount = 0L
                val startedAt = SystemClock.elapsedRealtime()
                var lastReportAt = startedAt
                var lastProgress = -1
                var decodedBuffers = 0

                while (!outputEnded) {
                    if (cancelled() || Thread.currentThread().isInterrupted) {
                        throw CancellationException("Waveform was cancelled")
                    }
                    if (SystemClock.elapsedRealtime() - startedAt > WALL_TIMEOUT_MS) {
                        error("Waveform decode exceeded time budget")
                    }
                    if (!inputEnded) {
                        val inputIndex = decoder.dequeueInputBuffer(10_000L)
                        if (inputIndex >= 0) {
                            val input = decoder.getInputBuffer(inputIndex)
                                ?: error("Audio input buffer is unavailable")
                            input.clear()
                            val timestampUs = extractor.sampleTime
                            val bytes = if (timestampUs < 0L) -1
                                else extractor.readSampleData(input, 0)
                            if (bytes < 0) {
                                decoder.queueInputBuffer(
                                    inputIndex, 0, 0, 0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                inputEnded = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, bytes, timestampUs, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputIndex = decoder.dequeueOutputBuffer(info, 10_000L)
                    when {
                        outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED ->
                            outputFormat = decoder.outputFormat
                        outputIndex >= 0 -> {
                            try {
                                if (info.size > 0) {
                                    val pcm = decoder.getOutputBuffer(outputIndex)
                                        ?: error("Audio output buffer is unavailable")
                                    pcm.position(info.offset)
                                    pcm.limit(info.offset + info.size)
                                    sampleCount += collectPeaks(
                                        pcm, info.presentationTimeUs,
                                        outputFormat, peaks
                                    )
                                }
                                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                    outputEnded = true
                                }
                            } finally {
                                decoder.releaseOutputBuffer(outputIndex, false)
                            }
                            // The full-track codec competes with ExoPlayer
                            // for CPU, memory bandwidth, and possibly hardware
                            // decoder resources. Yield regularly; never spin
                            // at maximum speed just to generate a preview.
                            decodedBuffers++
                            if (decodedBuffers % 12 == 0) SystemClock.sleep(2L)
                            val now = SystemClock.elapsedRealtime()
                            if (now - lastReportAt >= 750L && durationUs > 0L) {
                                val progress = (info.presentationTimeUs
                                    .coerceAtLeast(0L).toDouble() / durationUs * 100.0)
                                    .toInt().coerceIn(0, 99)
                                if (progress > lastProgress) {
                                    lastProgress = progress
                                    onProgress(progress)
                                }
                                lastReportAt = now
                            }
                        }
                    }
                }
                if (cancelled()) throw CancellationException("Waveform was cancelled")
                require(sampleCount > 0L) { "No PCM samples decoded" }
                WaveformData(durationMs, WaveformMath.normalizedPeaks(peaks))
            }
        } finally {
            if (started) runCatching { codec?.stop() }
            runCatching { codec?.release() }
            extractor.release()
        }
    }

    /**
     * Samples a small subset of PCM frames in each output buffer.
     * Buckets are placed by decoded presentation timestamps, not file offsets.
     */
    private fun collectPeaks(
        pcm: ByteBuffer,
        presentationUs: Long,
        format: MediaFormat,
        peaks: DoubleArray
    ): Long {
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        require(channels in 1..32) { "Unsupported channel count" }
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        require(sampleRate > 0) { "Invalid sample rate" }
        val encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            format.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else AudioFormat.ENCODING_PCM_16BIT
        val isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT
        require(isFloat || encoding == AudioFormat.ENCODING_PCM_16BIT) {
            "Unsupported PCM encoding"
        }
        pcm.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = if (isFloat) 4 else 2
        val bytesPerFrame = bytesPerSample * channels
        val frames = pcm.remaining() / bytesPerFrame
        val origin = pcm.position()
        val stride = (sampleRate / 2000).coerceAtLeast(1)
        var accepted = 0L
        for (frame in 0 until frames step stride) {
            val frameOffset = origin + frame * bytesPerFrame
            var peak = 0.0
            for (channel in 0 until channels) {
                val offset = frameOffset + channel * bytesPerSample
                val sample = if (isFloat) pcm.getFloat(offset).toDouble()
                    else pcm.getShort(offset).toDouble() / 32768.0
                if (sample.isFinite()) peak = maxOf(peak, abs(sample))
            }
            val timeUs = presentationUs.coerceAtLeast(0L) +
                frame.toLong() * 1_000_000L / sampleRate
            val bucket = WaveformMath.binForTimestamp(timeUs, peaks.size)
            peaks[bucket] = maxOf(peaks[bucket], peak)
            accepted++
        }
        return accepted
    }

    companion object {
        private const val MAX_DURATION_US = 25L * 60L * 1_000_000L
        private const val WALL_TIMEOUT_MS = 120_000L
    }
}
