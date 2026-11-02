package com.wombatriy.androidplayer.bpm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.wombatriy.androidplayer.Track
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * Orchestrates one-track BPM estimation, manual corrections and persistent data.
 * The audio decoder never runs on the UI thread and cannot modify playback.
 */
class BpmCoordinator(context: Context, private val onChanged: () -> Unit) {
    private val application = context.applicationContext
    private val store = BpmRepository(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val tapTempo = TapTempo()

    var track: Track? = null
        private set
    var analyzing: Boolean = false
        private set
    var message: String? = null
        private set

    fun selectTrack(next: Track?) {
        if (track?.uri == next?.uri) return
        generation.incrementAndGet()
        track = next
        analyzing = false
        message = null
        tapTempo.reset()
        onChanged()
    }

    fun resolved(): BpmRecord? = track?.let(store::resolved)
    fun hasStoredValue(): Boolean = track?.let { store.stored(it) != null } ?: false

    fun analyze() {
        val selected = track ?: return
        // Filename BPM takes precedence; use correction controls to override it.
        if (selected.filenameBpm != null || analyzing) return
        val token = generation.incrementAndGet()
        analyzing = true
        message = "Analyzing up to 70 seconds of audio…"
        onChanged()

        worker.execute {
            val outcome = runCatching {
                AndroidBpmDecoder(application).analyze(selected.uri) {
                    generation.get() != token || Thread.currentThread().isInterrupted
                }
            }
            ui.post {
                if (generation.get() != token) return@post
                analyzing = false
                outcome.onSuccess { estimate ->
                    if (estimate == null) {
                        message = "No clear beat detected. Try Tap BPM."
                    } else {
                        store.save(selected, BpmRecord(
                            estimate.bpm, BpmSource.DETECTED,
                            estimate.confidence, estimate.alternativeBpm
                        ))
                        message = "Estimated from audio — verify by ear."
                    }
                }.onFailure {
                    message = "Analysis unavailable: ${it.localizedMessage ?: "unsupported audio"}"
                }
                onChanged()
            }
        }
    }

    fun multiply(factor: Double) {
        val selected = track ?: return
        val previous = store.resolved(selected) ?: return
        val corrected = (previous.bpm * factor * 10).roundToInt() / 10.0
        if (corrected !in 20.0..800.0) {
            message = "Adjusted BPM would exceed the supported range."
            onChanged()
            return
        }
        store.save(selected, BpmRecord(corrected, BpmSource.MANUAL))
        message = "Manual BPM correction saved."
        onChanged()
    }

    fun setManual(bpm: Double) {
        val selected = track ?: return
        if (!bpm.isFinite() || bpm !in 20.0..800.0) return
        val rounded = (bpm * 10).roundToInt() / 10.0
        store.save(selected, BpmRecord(rounded, BpmSource.MANUAL))
        message = "Manual BPM saved."
        onChanged()
    }

    fun tap() {
        val measured = tapTempo.tap(SystemClock.elapsedRealtime())
        if (measured == null) {
            message = "Tap at least three times on the beat."
            onChanged()
            return
        }
        setManual(measured)
        message = "Tap Tempo saved (${BpmFormatting.bpm(measured)} BPM)."
        onChanged()
    }

    fun reset() {
        val selected = track ?: return
        generation.incrementAndGet()
        analyzing = false
        tapTempo.reset()
        store.clear(selected)
        message = "Correction cleared."
        onChanged()
    }

    fun close() {
        generation.incrementAndGet()
        worker.shutdownNow()
    }
}
