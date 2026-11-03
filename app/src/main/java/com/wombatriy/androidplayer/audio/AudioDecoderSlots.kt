package com.wombatriy.androidplayer.audio

import java.util.concurrent.CancellationException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Shared codec budget for foreground BPM, batch BPM and waveform decoding.
 * Never block Android's main thread; callers use background executors.
 */
object AudioDecoderSlots {
    private val slots = Semaphore(2, true)

    fun <T> withPermit(cancelled: () -> Boolean, block: () -> T): T {
        while (true) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                throw CancellationException("Audio decoding was cancelled")
            }
            if (slots.tryAcquire(120L, TimeUnit.MILLISECONDS)) break
        }
        try {
            if (cancelled()) throw CancellationException("Audio decoding was cancelled")
            return block()
        } finally {
            slots.release()
        }
    }
}
