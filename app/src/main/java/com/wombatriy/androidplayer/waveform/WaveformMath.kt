package com.wombatriy.androidplayer.waveform

import kotlin.math.roundToInt
import kotlin.math.sqrt

/** JVM-testable time, viewport and amplitude calculations. */
object WaveformMath {
    /**
     * One peak per 50 ms across the song: a 20-second window has up to
     * 400 meaningful samples rather than a few dozen full-track bins.
     * 25-minute tracks occupy only ~30 KB in the on-disk cache.
     */
    const val BIN_DURATION_MS = 50L

    fun binsForDuration(durationMs: Long): Int {
        require(durationMs > 0L) { "Waveform duration must be positive" }
        val bins = (durationMs - 1L) / BIN_DURATION_MS + 1L
        require(bins <= 30_000L) { "Waveform exceeds 25-minute limit" }
        return bins.toInt()
    }

    fun binForTimestamp(timestampUs: Long, bins: Int): Int {
        if (bins <= 0) return 0
        return (timestampUs.coerceAtLeast(0L) / (BIN_DURATION_MS * 1_000L))
            .coerceIn(0L, bins.toLong() - 1L).toInt()
    }

    data class Window(val startMs: Long, val endMs: Long) {
        val lengthMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    }

    /** A fixed-width time window, clamped to the start/end of a track. */
    fun windowAround(positionMs: Long, durationMs: Long, radiusMs: Long): Window {
        if (durationMs <= 0L || radiusMs <= 0L) return Window(0L, 0L)
        val windowLength = minOf(durationMs, radiusMs.coerceAtMost(Long.MAX_VALUE / 2L) * 2L)
        val lastStart = durationMs - windowLength
        val start = (positionMs.coerceIn(0L, durationMs) - windowLength / 2L)
            .coerceIn(0L, lastStart)
        return Window(start, start + windowLength)
    }

    fun timeAtFraction(fraction: Float, window: Window): Long {
        if (window.lengthMs <= 0L || !fraction.isFinite()) return window.startMs
        return window.startMs +
            (fraction.coerceIn(0f, 1f).toDouble() * window.lengthMs).toLong()
    }

    fun fractionAtTime(positionMs: Long, window: Window): Float =
        if (window.lengthMs <= 0L) 0f
        else ((positionMs - window.startMs).toDouble() / window.lengthMs)
            .toFloat().coerceIn(0f, 1f)

    /**
     * Swipe left moves the audio forward; swipe right moves backward.
     * The original window width stays fixed throughout each gesture.
     */
    fun swipedPosition(
        originMs: Long,
        deltaX: Float,
        contentWidthPx: Float,
        windowLengthMs: Long,
        durationMs: Long
    ): Long {
        if (durationMs <= 0L || contentWidthPx <= 0f ||
            !deltaX.isFinite() || windowLengthMs <= 0L) {
            return originMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        }
        val shift = deltaX.toDouble() / contentWidthPx * windowLengthMs
        return (originMs.toDouble() - shift).toLong().coerceIn(0L, durationMs)
    }

    /**
     * Choose a stable downsampling factor for the selected zoom level.
     * The factor depends on zoom and screen width, NEVER on the viewport
     * position, so peaks don't change shape when the player advances.
     */
    fun drawGroupSize(window: Window, widthPixels: Int): Int {
        if (window.lengthMs <= 0L || widthPixels <= 0) return 1
        val bins = (window.lengthMs - 1L) / BIN_DURATION_MS + 1L
        return ((bins - 1L) / widthPixels + 1L).toInt().coerceAtLeast(1)
    }

    /**
     * Group indices are aligned to absolute track time (t=0), not the
     * moving window edges. The same audio peak keeps the same group and
     * height while the viewport moves; only its X coordinate changes.
     */
    fun visibleGroupStarts(
        window: Window, totalBins: Int, groupSize: Int
    ): IntProgression {
        if (window.lengthMs <= 0L || totalBins <= 0 || groupSize <= 0) {
            return 1..0
        }
        val periodMs = BIN_DURATION_MS * groupSize
        val first = (window.startMs.coerceAtLeast(0L) / periodMs) * groupSize
        val last = ((window.endMs - 1L).coerceAtLeast(0L) / periodMs) * groupSize
        return first.toInt().coerceIn(0, totalBins - 1)..
            last.toInt().coerceIn(0, totalBins - 1) step groupSize
    }

    /**
     * Bound the pre-rendered bitmap to at most twice the visible width.
     * The 20-second default stores a 40-second tile; tight zoom stays safe.
     */
    fun bufferedTileWindow(viewport: Window, durationMs: Long): Window {
        if (viewport.lengthMs <= 0L || durationMs <= 0L) return Window(0L, 0L)
        val pad = minOf(10_000L, viewport.lengthMs / 2L)
        return Window(
            (viewport.startMs - pad).coerceAtLeast(0L),
            (viewport.endMs + pad).coerceAtMost(durationMs)
        )
    }

    /** A tile is usable only while it contains the ENTIRE visible timeline. */
    fun tileCovers(viewport: Window, tile: Window): Boolean =
        viewport.lengthMs > 0L && tile.lengthMs > 0L &&
            viewport.startMs >= tile.startMs && viewport.endMs <= tile.endMs

    /**
     * A moving waveform must reuse its already-rasterized bitmap for many
     * consecutive frames. Request a fresh tile only near the
     * buffered edges or its source/scale/dimensions changed.
     */
    fun peakPathNeedsRebuild(
        viewport: Window,
        cached: Window,
        trackDurationMs: Long,
        preloadMs: Long,
        configurationChanged: Boolean
    ): Boolean {
        if (configurationChanged || viewport.lengthMs <= 0L) return true
        if (viewport.startMs < cached.startMs || viewport.endMs > cached.endMs) return true
        val cushion = preloadMs.coerceAtLeast(0L) / 6L
        return (cached.startMs > 0L && viewport.startMs < cached.startMs + cushion) ||
            (cached.endMs < trackDurationMs && viewport.endMs > cached.endMs - cushion)
    }

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
        require(levels.size == WaveformMath.binsForDuration(durationMs))
    }
}
