package com.wombatriy.androidplayer.player

/** End-exclusive A–B loop; seeking outside the selected interval restarts at A. */
data class LoopRange(val startMs: Long, val endMs: Long) {
    init {
        require(startMs >= 0) { "A must be non-negative" }
        require(endMs > startMs) { "B must be after A" }
    }

    fun constrainSeek(requestedMs: Long): Long =
        if (requestedMs < startMs || requestedMs >= endMs) startMs else requestedMs
}
