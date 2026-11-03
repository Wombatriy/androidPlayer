package com.wombatriy.androidplayer.waveform

import org.junit.Assert.*
import org.junit.Test

class WaveformAnimationPolicyTest {
    @Test fun onlyVisiblePlayingWaveformRunsAtVsync() {
        assertTrue(WaveformAnimationPolicy.shouldAnimate(true, true, true, false))
        assertFalse(WaveformAnimationPolicy.shouldAnimate(false, true, true, false))
        assertFalse(WaveformAnimationPolicy.shouldAnimate(true, false, true, false))
        assertFalse(WaveformAnimationPolicy.shouldAnimate(true, true, false, false))
        assertFalse(WaveformAnimationPolicy.shouldAnimate(true, true, true, true))
    }

    @Test fun coarseTickerCannotRewindSmoothMediaClock() {
        // This was the ~120ms visual jerk in the previous implementation.
        assertEquals(1125L, WaveformAnimationPolicy.choosePosition(
            tickerMs = 1000L, liveMediaClockMs = 1125L,
            playing = true, seekbarScrubbing = false
        ))
    }

    @Test fun pauseAndSeekbarDisplayTheRealUserSelectedPosition() {
        assertEquals(1000L, WaveformAnimationPolicy.choosePosition(
            tickerMs = 1000L, liveMediaClockMs = 1125L,
            playing = false, seekbarScrubbing = false
        ))
        assertEquals(5000L, WaveformAnimationPolicy.choosePosition(
            tickerMs = 5000L, liveMediaClockMs = 1125L,
            playing = true, seekbarScrubbing = true
        ))
    }

    @Test fun negativePositionIsClamped() {
        assertEquals(0L, WaveformAnimationPolicy.choosePosition(
            tickerMs = -1L, liveMediaClockMs = -1L,
            playing = true, seekbarScrubbing = false
        ))
    }
}
