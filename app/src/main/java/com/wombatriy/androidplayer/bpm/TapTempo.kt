package com.wombatriy.androidplayer.bpm

import java.util.ArrayDeque
import kotlin.math.roundToInt

/**
 * Estimates BPM from the last few taps, robust against a single mistimed tap
 * by taking the median of intervals. Uses monotonic timestamps supplied by UI.
 */
class TapTempo {
    private val taps = ArrayDeque<Long>()

    fun reset() = taps.clear()

    fun tap(atMs: Long): Double? {
        val previous = taps.peekLast()
        if (previous != null && (atMs <= previous || atMs - previous > 2_500)) {
            taps.clear()
        }
        taps.addLast(atMs)
        while (taps.size > 7) taps.removeFirst()
        if (taps.size < 3) return null

        val times = taps.toList()
        val intervals = times.zipWithNext { a, b -> b - a }.sorted()
        val interval = if (intervals.size % 2 == 1) intervals[intervals.size / 2].toDouble()
            else (intervals[intervals.size / 2 - 1] + intervals[intervals.size / 2]) / 2.0
        if (interval <= 0) return null
        val bpm = 60_000.0 / interval
        return if (bpm in 35.0..300.0) (bpm * 10).roundToInt() / 10.0 else null
    }
}
