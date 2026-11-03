package com.wombatriy.androidplayer.waveform

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Pure, JVM-testable sampling and display conversions. */
object WaveformMath {
    const val BINS = 512

    fun binForTimestamp(timestampUs: Long, durationUs: Long, bins: Int = BINS): Int {
        if (durationUs <= 0L || bins <= 0) return 0
        // Double avoids Long overflow on very long tracks.
        return (timestampUs.coerceAtLeast(0L).toDouble() / durationUs * bins)
            .toInt().coerceIn(0, bins - 1)
    }

    fun millisAtFraction(fraction: Float, durationMs: Long): Long {
        if (durationMs <= 0L || !fraction.isFinite()) return 0L
        return (fraction.coerceIn(0f, 1f).toDouble() * durationMs)
            .toLong().coerceIn(0L, durationMs)
    }

    fun fractionAtMillis(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f
        else (positionMs.toDouble() / durationMs).toFloat().coerceIn(0f, 1f)

    /**
     * Normalize using a robust 95th percentile instead of the loudest transient.
     * The square root boosts quiet sections without losing contrasts.
     */
    fun normalizedPeaks(peaks: DoubleArray): ByteArray {
        if (peaks.isEmpty()) return ByteArray(0)
        val sorted = peaks.filter { it.isFinite() && it >= 0.0 }.sorted()
        val ceiling = sorted.getOrNull(
            ((sorted.size - 1) * 0.95).toInt().coerceAtLeast(0)
        )?.coerceAtLeast(0.00001) ?: 0.00001
        return ByteArray(peaks.size) { index ->
            val peak = peaks[index].takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            (sqrt((peak / ceiling).coerceIn(0.0, 1.0)) * 255.0)
                .roundToInt().coerceIn(0, 255).toByte()
        }
    }
}

data class WaveformData(val durationMs: Long, val levels: ByteArray) {
    init {
        require(durationMs > 0L)
        require(levels.size == WaveformMath.BINS)
    }
}
