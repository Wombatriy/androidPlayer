package com.wombatriy.androidplayer.waveform

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Monotonic visual media clock. ExoPlayer can report position at irregular
 * intervals on some devices; do not make the waveform jump to every sample.
 *
 * This clock only controls the picture, never actual audio playback.
 */
class WaveformFrameClock {
    private var initialized = false
    private var anchorPositionMs = 0.0
    private var anchorNanos = 0L
    private var lastCorrectionNanos = 0L
    private var speed = 1f
    private var running = false
    private var durationMs = 0L

    fun positionAt(nowNanos: Long): Long {
        if (!initialized) return 0L
        val elapsedMs = if (running) {
            (nowNanos - anchorNanos).coerceAtLeast(0L).toDouble() / 1_000_000.0
        } else 0.0
        return (anchorPositionMs + elapsedMs * speed).roundToLong()
            .coerceIn(0L, durationMs.coerceAtLeast(0L))
    }

    /**
     * Call when playback starts/stops/changes speed, on a seek, and at
     * periodic coarse Player UI ticks. Small position jitter is softly
     * corrected at most every 400ms, without snapping the waveform.
     *
     * Large errors (seek, loop restart, source change) snap immediately.
     */
    fun sync(
        observedMs: Long,
        timeNanos: Long,
        playbackSpeed: Float,
        isPlaying: Boolean,
        mediaDurationMs: Long,
        force: Boolean = false
    ) {
        val safeDuration = mediaDurationMs.coerceAtLeast(0L)
        val observed = observedMs.coerceIn(0L, safeDuration).toDouble()
        val validSpeed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
        val predicted = positionAt(timeNanos).toDouble()
        val error = observed - predicted
        val mustReset = force || !initialized || running != isPlaying ||
            speed != validSpeed || durationMs != safeDuration ||
            abs(error) >= HARD_DISCONTINUITY_MS

        durationMs = safeDuration
        if (mustReset) {
            anchorPositionMs = observed
            anchorNanos = timeNanos
            lastCorrectionNanos = timeNanos
        } else if (timeNanos - lastCorrectionNanos >= SYNC_INTERVAL_NS) {
            // Correct at most 7ms per sync: users never see a snap and the
            // display remains close to the hardware media clock.
            anchorPositionMs = (predicted + (error * 0.14)
                .coerceIn(-MAX_CORRECTION_MS, MAX_CORRECTION_MS))
                .coerceIn(0.0, safeDuration.toDouble())
            anchorNanos = timeNanos
            lastCorrectionNanos = timeNanos
        }
        initialized = true
        speed = validSpeed
        running = isPlaying
    }

    companion object {
        private const val HARD_DISCONTINUITY_MS = 260.0
        private const val MAX_CORRECTION_MS = 7.0
        private const val SYNC_INTERVAL_NS = 400_000_000L
    }
}
