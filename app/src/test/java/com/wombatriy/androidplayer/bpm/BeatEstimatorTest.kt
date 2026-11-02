package com.wombatriy.androidplayer.bpm

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class BeatEstimatorTest {
    private fun beatEnvelope(bpm: Int, seconds: Int = 36): DoubleArray {
        val samplesPerSecond = 100
        val result = DoubleArray(samplesPerSecond * seconds)
        val step = samplesPerSecond * 60.0 / bpm
        var k = 0
        while (true) {
            val index = (200 + k * step).roundToInt()
            if (index + 1 >= result.size) break
            result[index] = 1.0
            result[index + 1] = 0.4
            k++
        }
        return result
    }

    @Test fun clearPeriodicBeatsAreDetected() {
        for (expected in listOf(60, 75, 90, 100, 120, 128, 140)) {
            val estimate = BeatEstimator.estimate(beatEnvelope(expected))
            assertNotNull("No tempo for $expected BPM", estimate)
            assertEquals("Wrong BPM for $expected", expected.toDouble(), estimate!!.bpm, 2.0)
            assertTrue("Confidence $expected", estimate.confidence > 0.1)
        }
    }

    @Test fun doubleTimeCanAppearAsAlternative() {
        val result = BeatEstimator.estimate(beatEnvelope(160))
        assertNotNull(result)
        val values = listOfNotNull(result!!.bpm, result.alternativeBpm)
        assertTrue(values.any { abs(it - 160.0) <= 2.0 })
    }

    @Test fun silentOrVeryShortInputHasNoEstimate() {
        assertNull(BeatEstimator.estimate(DoubleArray(2000)))
        assertNull(BeatEstimator.estimate(beatEnvelope(120).copyOfRange(0, 300)))
    }
}

class TapTempoTest {
    private val beatNanos = 500_000_000L // 120 BPM

    private fun measurement(intervals: List<Long>): TapTempo.Reading {
        val tap = TapTempo()
        var time = 1_000_000_000L
        tap.tap(time)
        var result: TapTempo.Reading? = null
        for (interval in intervals) {
            time += interval
            result = tap.tap(time)
        }
        return result!!
    }

    @Test fun nineTapsRequiredBeforeAnyEstimate() {
        val tap = TapTempo()
        var time = 1_000_000_000L
        assertNull(tap.tap(time).bpm)
        repeat(7) {
            time += beatNanos
            assertNull(tap.tap(time).bpm)
        }
        time += beatNanos
        val estimate = tap.tap(time)
        assertEquals(8, estimate.intervals)
        assertEquals(120.0, estimate.bpm!!, 0.01)
    }

    @Test fun averageOfSortedMiddleRejectsEarlyAndMissedTaps() {
        val intervals = listOf(
            500_000_000L, 500_000_000L, 100_000_000L, // accidental double-tap
            500_000_000L, 500_000_000L, 1_000_000_000L, // missed beat
            500_000_000L, 500_000_000L, 510_000_000L, 490_000_000L
        )
        val sample = measurement(intervals)
        assertEquals(120.0, sample.bpm!!, 0.1)
    }

    @Test fun trimmedWindowKeepsMedianTimingDespiteUnevenHands() {
        val intervals = listOf(
            470_000_000L, 530_000_000L, 490_000_000L,
            510_000_000L, 500_000_000L, 495_000_000L,
            505_000_000L, 700_000_000L
        )
        assertEquals(120.0, measurement(intervals).bpm!!, 2.0)
    }

    @Test fun silenceForThreeSecondsResetsWindow() {
        val tap = TapTempo()
        var time = 1_000_000_000L
        repeat(9) {
            tap.tap(time)
            time += beatNanos
        }
        val latest = time - beatNanos
        assertFalse(tap.clearIfTimedOut(latest + TapTempo.TIMEOUT_NS - 1))
        assertTrue(tap.clearIfTimedOut(latest + TapTempo.TIMEOUT_NS))
        assertEquals(0, tap.intervalCount)
        assertNull(tap.tap(latest + TapTempo.TIMEOUT_NS + 1).bpm)
    }

    @Test fun lateTapRestartsImmediately() {
        val tap = TapTempo()
        tap.tap(1_000_000_000L)
        tap.tap(1_500_000_000L)
        val sample = tap.tap(5_000_000_000L)
        assertTrue(sample.restartedAfterPause)
        assertEquals(0, sample.intervals)
        assertNull(sample.bpm)
    }

    @Test fun explicitResetClearsAllSortedIntervals() {
        val tap = TapTempo()
        tap.tap(1_000_000_000L)
        tap.tap(1_400_000_000L)
        tap.reset()
        assertEquals(0, tap.intervalCount)
        assertNull(tap.tap(2_000_000_000L).bpm)
    }
}
