package com.wombatriy.androidplayer.waveform

import org.junit.Assert.*
import org.junit.Test

class WaveformFrameClockTest {
    @Test fun interpolatesSmoothlyBetweenIrregularMediaSamples() {
        val clock = WaveformFrameClock()
        clock.sync(10_000, 1_000_000_000L, 1f, true, 240_000)
        assertEquals(10_016L, clock.positionAt(1_016_000_000L))
        assertEquals(10_100L, clock.positionAt(1_100_000_000L))
        // Device reported a slightly stale position. Don't jump backward.
        clock.sync(10_070, 1_120_000_000L, 1f, true, 240_000)
        assertEquals(10_136L, clock.positionAt(1_136_000_000L))
    }

    @Test fun slowerPlaybackAdvancesAtItsRealRate() {
        val clock = WaveformFrameClock()
        clock.sync(5_000, 0L, 0.7f, true, 100_000)
        assertEquals(5_350L, clock.positionAt(500_000_000L))
    }

    @Test fun pauseIsStableAndResumeBeginsFromActualPosition() {
        val clock = WaveformFrameClock()
        clock.sync(2_000, 0L, 1f, true, 60_000)
        clock.sync(2_200, 200_000_000L, 1f, false, 60_000)
        assertEquals(2_200L, clock.positionAt(2_000_000_000L))
        clock.sync(2_200, 2_000_000_000L, 1f, true, 60_000)
        assertEquals(2_250L, clock.positionAt(2_050_000_000L))
    }

    @Test fun largeSeekAndLoopResetSnapImmediately() {
        val clock = WaveformFrameClock()
        clock.sync(10_000, 0L, 1f, true, 60_000)
        clock.sync(4_000, 100_000_000L, 1f, true, 60_000)
        assertEquals(4_000L, clock.positionAt(100_000_000L))
        clock.sync(30_000, 200_000_000L, 1f, true, 60_000)
        assertEquals(30_000L, clock.positionAt(200_000_000L))
    }

    @Test fun smallCorrectionDoesNotProduceVisibleBackwardJump() {
        val clock = WaveformFrameClock()
        clock.sync(10_000, 0L, 1f, true, 60_000)
        clock.sync(10_350, 400_000_000L, 1f, true, 60_000)
        assertTrue(clock.positionAt(400_000_000L) in 10_392L..10_400L)
        assertTrue(clock.positionAt(416_000_000L) > clock.positionAt(400_000_000L))
    }

    @Test fun stopsAtDurationAndHandlesInvalidSpeed() {
        val clock = WaveformFrameClock()
        clock.sync(990, 0L, Float.NaN, true, 1_000)
        assertEquals(1_000L, clock.positionAt(1_000_000_000L))
    }
}
