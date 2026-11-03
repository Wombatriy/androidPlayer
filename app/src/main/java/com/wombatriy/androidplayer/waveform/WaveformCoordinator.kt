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
    private val onWaveform: (WaveformData?) -> Unit,
    private val onStatus: (String) -> Unit
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
        // Clear the old track exactly once. Decode progress must never clear
        // an already selected waveform or cancel pending preview tiles.
        onWaveform(null)
        if (uri == null) {
            onStatus("")
            return
        }
        onStatus("Generating waveform…")
        executor.execute {
            // Java Thread.MIN_PRIORITY alone does not always lower Android's
            // Linux scheduler priority for MediaCodec work. Be explicit.
            runCatching {
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )
            }
            val result = runCatching {
                cache.read(uri, durationHintMs) ?: decoder.analyze(
                    uri = uri,
                    cancelled = {
                        closed || generation.get() != token ||
                            Thread.currentThread().isInterrupted
                    },
                    onProgress = { percent ->
                        main.post {
                            if (!closed && generation.get() == token &&
                                selectedUri == uri) {
                                onStatus("Generating waveform… $percent%")
                            }
                        }
                    }
                ).also { waveform ->
                    // Cache errors should not discard an otherwise usable waveform.
                    runCatching { cache.write(uri, waveform) }
                }
            }
            main.post {
                if (closed || generation.get() != token || selectedUri != uri) {
                    return@post
                }
                result.onSuccess { value ->
                    onWaveform(value)
                    onStatus("")
                }.onFailure { error ->
                    if (error !is CancellationException) {
                        onStatus(error.message ?: "Waveform unavailable")
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
