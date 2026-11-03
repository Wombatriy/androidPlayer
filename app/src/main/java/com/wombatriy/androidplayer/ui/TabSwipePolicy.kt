package com.wombatriy.androidplayer.ui

import kotlin.math.abs

/**
 * Independent swipe decision for the four app tabs.
 * Horizontal swipes must clearly dominate vertical list scrolling.
 */
object TabSwipePolicy {
    fun direction(
        dx: Float,
        dy: Float,
        elapsedMs: Long,
        minDistancePx: Float
    ): Int {
        if (elapsedMs !in 0L..650L || minDistancePx <= 0f) return 0
        if (abs(dx) < minDistancePx || abs(dx) < abs(dy) * 1.6f) return 0
        return if (dx < 0f) 1 else -1
    }
}
