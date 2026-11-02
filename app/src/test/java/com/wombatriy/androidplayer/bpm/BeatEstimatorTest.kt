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
    @Test fun threeTapsGive120Bpm() {
        val tap = TapTempo()
        assertNull(tap.tap(1000))
        assertNull(tap.tap(1500))
        assertEquals(120.0, tap.tap(2000)!!, 0.01)
    }

    @Test fun longPauseResetsTapSequence() {
        val tap = TapTempo()
        tap.tap(1000); tap.tap(1500); tap.tap(2000)
        assertNull(tap.tap(8000))
        assertNull(tap.tap(8600))
        assertEquals(100.0, tap.tap(9200)!!, 0.01)
    }

    @Test fun medianReducesMistimedTapImpact() {
        val tap = TapTempo()
        listOf(0L, 500L, 1000L, 1520L, 2020L).forEach { tap.tap(it) }
        assertEquals(120.0, tap.tap(2520)!!, 3.0)
    }
}
