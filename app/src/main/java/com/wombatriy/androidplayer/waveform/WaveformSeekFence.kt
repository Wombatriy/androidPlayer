package com.wombatriy.androidplayer.waveform

/**
 * Protects the visual clock from Media3 position samples that still belong
 * to the OLD location immediately after a user seek.
 *
 * Requests arrive before Player.seekTo(); completion can arrive later via
 * Player.Listener.onPositionDiscontinuity. Keep a short grace period even
 * after confirmation while the playback pipeline settles.
 */
class WaveformSeekFence {
    var pendingTarget: Long? = null
        private set

    private var requestedAtNs = 0L
    private var ignoreUntilNs = 0L

    fun begin(targetMs: Long, nowNs: Long) {
        pendingTarget = targetMs.coerceAtLeast(0L)
        requestedAtNs = nowNs
        ignoreUntilNs = nowNs + STABILIZE_NS
    }

    /** Returns the original requested target for comparing actual seek result. */
    fun confirmed(nowNs: Long): Long? {
        val target = pendingTarget
        pendingTarget = null
        ignoreUntilNs = nowNs + STABILIZE_NS
        return target
    }

    fun ignoreOldSample(nowNs: Long): Boolean {
        if (pendingTarget != null && nowNs - requestedAtNs >= CONFIRM_TIMEOUT_NS) {
            pendingTarget = null
        }
        return pendingTarget != null || nowNs < ignoreUntilNs
    }

    fun clear() {
        pendingTarget = null
        requestedAtNs = 0L
        ignoreUntilNs = 0L
    }

    companion object {
        const val CONFIRM_TIMEOUT_NS = 1_200_000_000L
        const val STABILIZE_NS = 600_000_000L
    }
}
