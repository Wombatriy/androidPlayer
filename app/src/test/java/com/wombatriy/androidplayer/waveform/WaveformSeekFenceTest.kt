package com.wombatriy.androidplayer.waveform

import org.junit.Assert.*
import org.junit.Test

class WaveformSeekFenceTest {
    @Test fun ignoresOldPositionUntilSeekConfirmation() {
        val fence = WaveformSeekFence()
        fence.begin(50_000L, 1_000_000_000L)
        assertEquals(50_000L, fence.pendingTarget)
        assertTrue(fence.ignoreOldSample(1_100_000_000L))
        assertTrue(fence.ignoreOldSample(1_800_000_000L))
    }

    @Test fun ignoresStaleSamplesForShortGracePeriodAfterConfirmation() {
        val fence = WaveformSeekFence()
        fence.begin(24_000L, 0L)
        assertEquals(24_000L, fence.confirmed(100_000_000L))
        assertTrue(fence.ignoreOldSample(500_000_000L))
        assertFalse(fence.ignoreOldSample(700_000_000L))
        assertNull(fence.pendingTarget)
    }

    @Test fun pendingSeekTimesOutIfDeviceDoesNotReportDiscontinuity() {
        val fence = WaveformSeekFence()
        fence.begin(100L, 0L)
        assertTrue(fence.ignoreOldSample(1_199_000_000L))
        assertFalse(fence.ignoreOldSample(1_200_000_000L))
    }

    @Test fun repeatedScrubsReplacePendingTargetAndDeadline() {
        val fence = WaveformSeekFence()
        fence.begin(40_000L, 0L)
        fence.begin(42_000L, 1_000_000_000L)
        assertEquals(42_000L, fence.pendingTarget)
        assertTrue(fence.ignoreOldSample(1_800_000_000L))
        assertEquals(42_000L, fence.confirmed(1_900_000_000L))
    }

    @Test fun changingTracksClearsOldSeekState() {
        val fence = WaveformSeekFence()
        fence.begin(99_999L, 100L)
        fence.clear()
        assertFalse(fence.ignoreOldSample(500L))
        assertNull(fence.pendingTarget)
    }
}
