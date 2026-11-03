package com.wombatriy.androidplayer.waveform

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.wombatriy.androidplayer.Track
import java.util.concurrent.CancellationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Asynchronously loads, generates and caches one waveform at a time.
 * Switching songs cancels stale work; never sends data from an old URI
 * to the current PlayerScreen.
 */
class WaveformCoordinator(
    context: Context,
    private val onChanged: (WaveformData?, String) -> Unit
) {
    private val application = context.applicationContext
    private val cache = WaveformCache(application)
    private val decoder = WaveformDecoder(application)
    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "waveform-decode").apply { priority = Thread.MIN_PRIORITY }
    }
    private val main = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private var selectedUri: Uri? = null
    private var closed = false

    fun select(track: Track?, durationHintMs: Long) {
        val uri = track?.uri
        if (uri == selectedUri) return
        selectedUri = uri
        val token = generation.incrementAndGet()
        if (uri == null) {
            onChanged(null, "")
            return
        }
        onChanged(null, "Generating waveform…")
        executor.execute {
            val result = runCatching {
                cache.read(uri, durationHintMs) ?: decoder.analyze(uri) {
                    closed || generation.get() != token ||
                        Thread.currentThread().isInterrupted
                }.also { waveform ->
                    // Cache errors should not discard an otherwise usable waveform.
                    runCatching { cache.write(uri, waveform) }
                }
            }
            main.post {
                if (closed || generation.get() != token || selectedUri != uri) {
                    return@post
                }
                result.onSuccess { value ->
                    onChanged(value, "")
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        onChanged(null, error.message ?: "Waveform unavailable")
                    }
                }
            }
        }
    }

    fun close() {
        closed = true
        generation.incrementAndGet()
        executor.shutdownNow()
    }
}
