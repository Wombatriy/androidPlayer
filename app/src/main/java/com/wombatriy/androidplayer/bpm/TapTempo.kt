package com.wombatriy.androidplayer.bpm

import kotlin.math.roundToInt

/**
 * Robust BPM from monotonic nanosecond tap intervals. Intervals are maintained
 * in ascending order; only the middle 50% contributes to the mean.
 * This rejects premature/double taps and missed beats at either extreme.
 * We need nine taps (eight intervals) before publishing an estimate.
 */
class TapTempo {
    data class Reading(val bpm: Double?, val intervals: Int, val restartedAfterPause: Boolean)

    private val sortedIntervals = mutableListOf<Long>()
    private var lastTapNanos: Long? = null

    val intervalCount: Int get() = sortedIntervals.size

    fun reset() {
        sortedIntervals.clear()
        lastTapNanos = null
    }

    fun clearIfTimedOut(nowNanos: Long): Boolean {
        val last = lastTapNanos ?: return false
        if (nowNanos - last < TIMEOUT_NS) return false
        reset()
        return true
    }

    fun tap(nowNanos: Long): Reading {
        val previous = lastTapNanos
        val restarted = previous != null &&
            (nowNanos <= previous || nowNanos - previous >= TIMEOUT_NS)
        if (restarted) reset()
        val last = lastTapNanos
        if (last != null) {
            val delta = nowNanos - last
            val position = sortedIntervals.binarySearch(delta)
            sortedIntervals.add(if (position >= 0) position else -position - 1, delta)
        }
        lastTapNanos = nowNanos
        if (sortedIntervals.size < REQUIRED_INTERVALS) {
            return Reading(null, sortedIntervals.size, restarted)
        }

        val trim = sortedIntervals.size / 4
        val middle = sortedIntervals.subList(trim, sortedIntervals.size - trim)
        val averageNanos = middle.sumOf { it.toDouble() } / middle.size
        val result = 60_000_000_000.0 / averageNanos
        val bpm = if (result in 20.0..800.0) {
            (result * 10).roundToInt() / 10.0
        } else null
        return Reading(bpm, sortedIntervals.size, restarted)
    }

    companion object {
        const val REQUIRED_INTERVALS = 8
        const val REQUIRED_TAPS = REQUIRED_INTERVALS + 1
        const val TIMEOUT_NS = 3_000_000_000L
    }
}
