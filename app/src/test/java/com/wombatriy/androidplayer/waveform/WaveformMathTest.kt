package com.wombatriy.androidplayer.waveform

import org.junit.Assert.*
import org.junit.Test

class WaveformMathTest {
    @Test fun fiftyMillisecondBinsCoverTheSong() {
        assertEquals(20, WaveformMath.binsForDuration(1_000L))
        assertEquals(401, WaveformMath.binsForDuration(20_001L))
        assertEquals(30_000, WaveformMath.binsForDuration(25L * 60L * 1_000L))
        assertEquals(0, WaveformMath.binForTimestamp(0L, 20))
        assertEquals(10, WaveformMath.binForTimestamp(500_000L, 20))
        assertEquals(19, WaveformMath.binForTimestamp(1_000_000L, 20))
        assertEquals(0, WaveformMath.binForTimestamp(-100L, 20))
    }

    @Test fun windowFollowsPlayheadButClampsAtTrackEdges() {
        assertEquals(WaveformMath.Window(0L, 20_000L),
            WaveformMath.windowAround(3_000L, 240_000L, 10_000L))
        assertEquals(WaveformMath.Window(90_000L, 110_000L),
            WaveformMath.windowAround(100_000L, 240_000L, 10_000L))
        assertEquals(WaveformMath.Window(220_000L, 240_000L),
            WaveformMath.windowAround(239_000L, 240_000L, 10_000L))
        assertEquals(WaveformMath.Window(0L, 8_000L),
            WaveformMath.windowAround(4_000L, 8_000L, 10_000L))
    }

    @Test fun positionMapsPreciselyWithinLocalWindow() {
        val window = WaveformMath.Window(90_000L, 110_000L)
        assertEquals(90_000L, WaveformMath.timeAtFraction(0f, window))
        assertEquals(100_000L, WaveformMath.timeAtFraction(0.5f, window))
        assertEquals(110_000L, WaveformMath.timeAtFraction(1f, window))
        assertEquals(0.5f, WaveformMath.fractionAtTime(100_000L, window), 0.0001f)
    }

    @Test fun swipeLeftAdvancesAndRightRewinds() {
        assertEquals(70_000L, WaveformMath.swipedPosition(
            60_000L, -100f, 200f, 20_000L, 240_000L
        ))
        assertEquals(50_000L, WaveformMath.swipedPosition(
            60_000L, 100f, 200f, 20_000L, 240_000L
        ))
    }

    @Test fun swipeCannotGoBeyondTrackBoundaries() {
        assertEquals(0L, WaveformMath.swipedPosition(
            3_000L, 200f, 200f, 20_000L, 240_000L
        ))
        assertEquals(240_000L, WaveformMath.swipedPosition(
            239_000L, -200f, 200f, 20_000L, 240_000L
        ))
        assertEquals(0L, WaveformMath.swipedPosition(
            120L, 20f, 0f, 20_000L, 0L
        ))
    }

    @Test fun silenceIsZeroAndLoudSectionsStandOut() {
        val silence = WaveformMath.normalizedPeaks(DoubleArray(400))
        assertTrue(silence.all { it.toInt() == 0 })
        val peaks = DoubleArray(400) { if (it < 200) 0.1 else 0.5 }
        peaks[0] = Double.NaN
        peaks[1] = Double.POSITIVE_INFINITY
        peaks[2] = -1.0
        peaks[399] = 10.0
        val result = WaveformMath.normalizedPeaks(peaks)
        assertEquals(0, result[0].toInt())
        assertEquals(0, result[1].toInt())
        assertEquals(0, result[2].toInt())
        assertTrue((result[300].toInt() and 255) > (result[100].toInt() and 255))
        assertEquals(255, result[399].toInt() and 255)
    }

    @Test fun peakGroupsStayFixedWhileViewportSlides() {
        // Same 20s zoom, with the viewport moving by only 25 milliseconds.
        val before = WaveformMath.Window(100_000L, 120_000L)
        val after = WaveformMath.Window(100_025L, 120_025L)
        val groupSize = WaveformMath.drawGroupSize(before, 300)
        assertEquals(2, groupSize)
        assertEquals(groupSize, WaveformMath.drawGroupSize(after, 300))
        val first = WaveformMath.visibleGroupStarts(before, 3_000, groupSize).toList()
        val shifted = WaveformMath.visibleGroupStarts(after, 3_000, groupSize).toList()
        // Unchanged groups represent the same absolute audio time and use
        // the same cached level. The previous screen-column algorithm didn't.
        assertEquals(first.take(12), shifted.take(12))
        assertTrue(first.toSet().intersect(shifted.toSet()).size >= first.size - 1)
        val sampleTime = 105_000L
        val expectedPixelShift =
            (WaveformMath.fractionAtTime(sampleTime, after) -
                WaveformMath.fractionAtTime(sampleTime, before)) * 300f
        assertEquals(-0.375f, expectedPixelShift, 0.002f)
    }

    @Test fun movingAcrossGroupBoundaryDoesNotRegroupInterior() {
        val before = WaveformMath.Window(100_075L, 120_075L)
        val after = WaveformMath.Window(100_125L, 120_125L)
        val size = WaveformMath.drawGroupSize(before, 300)
        val a = WaveformMath.visibleGroupStarts(before, 3_000, size).toSet()
        val b = WaveformMath.visibleGroupStarts(after, 3_000, size).toSet()
        assertTrue(a.intersect(b).size >= a.size - 1)
    }

    @Test fun groupsAreAnchoredToZeroAndHandleInvalidInputs() {
        val window = WaveformMath.Window(150L, 750L)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14),
            WaveformMath.visibleGroupStarts(window, 20, 2).toList())
        assertTrue(WaveformMath.visibleGroupStarts(window, 0, 2).none())
        assertTrue(WaveformMath.visibleGroupStarts(
            WaveformMath.Window(0L, 0L), 10, 1
        ).none())
    }

    @Test fun missingDurationIsHandled() {
        assertEquals(WaveformMath.Window(0L, 0L),
            WaveformMath.windowAround(1_000L, 0L, 10_000L))
        assertEquals(0L, WaveformMath.timeAtFraction(0.3f, WaveformMath.Window(0L, 0L)))
        assertEquals(0f,
            WaveformMath.fractionAtTime(100L, WaveformMath.Window(0L, 0L)), 0f)
    }
}
