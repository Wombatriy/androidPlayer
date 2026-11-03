package com.wombatriy.androidplayer.waveform

/**
 * Pure lifecycle and clock-selection rules for the Player waveform.
 * Only the visible, actually playing waveform schedules display frames.
 */
object WaveformAnimationPolicy {
    fun shouldAnimate(
        playing: Boolean,
        visible: Boolean,
        hasWaveform: Boolean,
        interactionBlocked: Boolean
    ): Boolean = playing && visible && hasWaveform && !interactionBlocked

    /**
     * Never feed a 120ms-old UI-ticker sample back into a smoothly updated
     * waveform while ExoPlayer is playing. Explicit SeekBar scrubbing wins.
     */
    fun choosePosition(
        tickerMs: Long,
        liveMediaClockMs: Long,
        playing: Boolean,
        seekbarScrubbing: Boolean
    ): Long = (if (playing && !seekbarScrubbing) liveMediaClockMs else tickerMs)
        .coerceAtLeast(0L)
}
