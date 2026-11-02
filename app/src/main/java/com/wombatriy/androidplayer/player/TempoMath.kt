package com.wombatriy.androidplayer.player

import kotlin.math.roundToInt

/** Canonical integer percent representation for the player's 0.25x..1.50x range. */
object TempoMath {
    const val MIN_PERCENT = 25
    const val MAX_PERCENT = 150
    const val DEFAULT_PERCENT = 100

    fun parsePercent(text: String): Int? =
        text.trim().toIntOrNull()?.takeIf { it in MIN_PERCENT..MAX_PERCENT }

    fun toSpeed(percent: Int): Float {
        require(percent in MIN_PERCENT..MAX_PERCENT)
        return percent / 100f
    }

    fun toPercent(speed: Float): Int =
        (speed * 100).roundToInt().coerceIn(MIN_PERCENT, MAX_PERCENT)

    /**
     * Filename BPM is the original tempo. Return tempo after speed adjustment.
     * Round to a tenth deterministically, using integer arithmetic.
     */
    fun effectiveBpm(originalBpm: Int, speedPercent: Int): String {
        require(originalBpm > 0)
        require(speedPercent in MIN_PERCENT..MAX_PERCENT)
        val tenths = (originalBpm * speedPercent + 5) / 10
        return if (tenths % 10 == 0) "${tenths / 10}"
        else "${tenths / 10}.${tenths % 10}"
    }
}
