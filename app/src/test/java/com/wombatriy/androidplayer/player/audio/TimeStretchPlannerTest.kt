package com.wombatriy.androidplayer.player.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TimeStretchPlannerTest {
    @Test fun slowerPlaybackMeansLongerDuration() {
        val plan = TimeStretchPlanner.fromSpeed(0.8f)
        assertEquals(1.25, plan.outputToInputDurationRatio, 1e-6)
        assertEquals(1.0, plan.pitchScale, 0.0)
        assertEquals(QualityPriority.DANCE_CRITICAL, plan.quality)
    }

    @Test fun fasterPlaybackMeansShorterDuration() {
        val plan = TimeStretchPlanner.fromSpeed(1.1f)
        assertEquals(1.0 / 1.1, plan.outputToInputDurationRatio, 1e-6)
        assertEquals(QualityPriority.DANCE_CRITICAL, plan.quality)
    }

    @Test fun normalSpeedIsIdentity() {
        val plan = TimeStretchPlanner.fromSpeed(1.0f)
        assertEquals(1.0, plan.outputToInputDurationRatio, 0.0)
        assertEquals(1.0, plan.pitchScale, 0.0)
    }

    @Test fun outerRangeIsSecondary() {
        assertEquals(QualityPriority.SECONDARY,
            TimeStretchPlanner.fromSpeed(0.5f).quality)
        assertEquals(QualityPriority.SECONDARY,
            TimeStretchPlanner.fromSpeed(1.5f).quality)
    }

    @Test fun invalidSpeedsRejected() {
        for (speed in listOf(0f, 0.24f, 1.51f, Float.NaN, Float.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                TimeStretchPlanner.fromSpeed(speed)
            }
        }
    }
}
