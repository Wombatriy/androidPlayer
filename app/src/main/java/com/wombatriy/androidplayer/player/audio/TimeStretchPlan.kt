package com.wombatriy.androidplayer.player.audio

/**
 * A media *speed* of 0.80x means that playback lasts 1/0.80 = 1.25 times as
 * long. Native stretchers usually accept duration ratio, not tempo speed.
 *
 * This plan is used to define and test the future DSP contract, not to
 * switch the currently active Sonic processor behind the user's back.
 */
data class TimeStretchPlan(
    val playbackSpeed: Float,
    val outputToInputDurationRatio: Double,
    val pitchScale: Double,
    val quality: QualityPriority
)

enum class QualityPriority {
    DANCE_CRITICAL,
    SECONDARY
}

object TimeStretchPlanner {
    const val MIN_SPEED = 0.25f
    const val MAX_SPEED = 1.50f
    const val PRIORITY_MIN_SPEED = 0.80f
    const val PRIORITY_MAX_SPEED = 1.10f

    fun fromSpeed(speed: Float): TimeStretchPlan {
        require(speed.isFinite() && speed in MIN_SPEED..MAX_SPEED) {
            "Speed must be finite and between 0.25x and 1.50x"
        }
        return TimeStretchPlan(
            playbackSpeed = speed,
            outputToInputDurationRatio = 1.0 / speed.toDouble(),
            pitchScale = 1.0, // tempo change must preserve musical key
            quality = if (speed >= PRIORITY_MIN_SPEED && speed <= PRIORITY_MAX_SPEED) {
                QualityPriority.DANCE_CRITICAL
            } else {
                QualityPriority.SECONDARY
            }
        )
    }
}
