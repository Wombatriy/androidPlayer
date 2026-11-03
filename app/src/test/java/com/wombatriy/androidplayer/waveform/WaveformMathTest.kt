package com.wombatriy.androidplayer.waveform

import org.junit.Assert.*
import org.junit.Test

class WaveformMathTest {
    @Test fun timeMappingBoundsAndEndpoints() {
        assertEquals(0L, WaveformMath.millisAtFraction(-1f, 10_000))
        assertEquals(5000L, WaveformMath.millisAtFraction(0.5f, 10_000))
        assertEquals(10_000L, WaveformMath.millisAtFraction(1.5f, 10_000))
        assertEquals(0.25f, WaveformMath.fractionAtMillis(2500L, 10_000), 0.0001f)
        assertEquals(1f, WaveformMath.fractionAtMillis(20_000L, 10_000), 0.0001f)
    }

    @Test fun timestampsMapToBinsWithoutOverflow() {
        assertEquals(0, WaveformMath.binForTimestamp(0L, 1_000_000L))
        assertEquals(256, WaveformMath.binForTimestamp(500_000L, 1_000_000L))
        assertEquals(511, WaveformMath.binForTimestamp(1_000_000L, 1_000_000L))
        assertEquals(0, WaveformMath.binForTimestamp(-100L, 1_000_000L))
    }

    @Test fun normalizedEnvelopeKeepsSilenceAtZero() {
        val values = WaveformMath.normalizedPeaks(DoubleArray(512))
        assertEquals(512, values.size)
        assertTrue(values.all { it.toInt() == 0 })
    }

    @Test fun normalizedEnvelopeHandlesOutliersAndInvalids() {
        val peaks = DoubleArray(512) { if (it < 256) 0.1 else 0.5 }
        peaks[0] = Double.NaN
        peaks[1] = Double.POSITIVE_INFINITY
        peaks[2] = -1.0
        peaks[511] = 10.0
        val result = WaveformMath.normalizedPeaks(peaks)
        assertEquals(0, result[0].toInt())
        assertEquals(0, result[1].toInt())
        assertEquals(0, result[2].toInt())
        assertTrue((result[300].toInt() and 255) > (result[100].toInt() and 255))
        assertEquals(255, result[511].toInt() and 255)
    }

    @Test fun missingDurationIsSafe() {
        assertEquals(0L, WaveformMath.millisAtFraction(0.3f, 0L))
        assertEquals(0f, WaveformMath.fractionAtMillis(100L, 0L), 0f)
    }
}
