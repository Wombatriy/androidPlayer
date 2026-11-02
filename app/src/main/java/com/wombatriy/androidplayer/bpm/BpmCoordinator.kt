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
 * Single-track BPM model. Detection runs off the UI thread. Filename, detected,
 * and manual values persist separately; old decoder results are never shown
 * for a different track.
 */
class BpmCoordinator(context: Context, private val onChanged: () -> Unit) {
    private val application = context.applicationContext
    private val store = BpmRepository(application)
    private val worker = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()
    private val tapTempo = TapTempo()
    private var tapSessionOpen = false
    private val tapTimeout = Runnable {
        if (tapSessionOpen && tapTempo.clearIfTimedOut(SystemClock.elapsedRealtimeNanos())) {
            message = "No taps for 3 seconds — Tap BPM window reset."
            onChanged()
        }
    }

    var track: Track? = null
        private set
    var analyzing: Boolean = false
        private set
    var message: String? = null
        private set

    fun resolved(): BpmRecord? = track?.let(store::resolved)
    fun manual(): BpmRecord? = track?.let(store::manual)
    fun detected(): BpmRecord? = track?.let(store::detected)
    fun filenameBpm(): Int? = track?.filenameBpm
    fun hasManual(): Boolean = track?.let(store::hasManual) ?: false

    fun selectTrack(next: Track?) {
        if (track?.uri == next?.uri) return
        generation.incrementAndGet()
        stopTapSession()
        track = next
        analyzing = false
        message = null
        onChanged()
        // Detect even when there is a BPM prefix. Its value remains preferred
        // unless the user has explicitly entered a manual correction.
        if (next != null && store.detected(next) == null) analyze(force = false)
    }

    fun analyze(force: Boolean = true) {
        val selected = track ?: return
        if (analyzing) return
        if (!force && store.detected(selected) != null) return
        val token = generation.incrementAndGet()
        analyzing = true
        message = "Analyzing audio in the background…"
        onChanged()

        worker.execute {
            val result = runCatching {
                AndroidBpmDecoder(application).analyze(selected.uri) {
                    token != generation.get() || Thread.currentThread().isInterrupted
                }
            }
            ui.post {
                if (token != generation.get() || selected.uri != track?.uri) return@post
                analyzing = false
                result.onSuccess { estimate ->
                    if (estimate == null) {
                        message = "No clear beat detected. You can try Tap BPM."
                    } else {
                        store.saveDetected(
                            selected,
                            BpmRecord(
                                estimate.bpm, BpmSource.DETECTED,
                                estimate.confidence, estimate.alternativeBpm
                            )
                        )
                        message = "Audio BPM estimated — compare against the filename."
                    }
                }.onFailure { exception ->
                    message = "Detection unavailable: ${exception.localizedMessage ?: "unsupported audio"}"
                }
                onChanged()
            }
        }
    }

    fun multiply(factor: Double) {
        val selected = track ?: return
        val previous = store.resolved(selected) ?: return
        val value = (previous.bpm * factor * 10.0).roundToInt() / 10.0
        if (value !in 20.0..800.0) {
            message = "Adjusted BPM would be outside 20–800."
            onChanged()
            return
        }
        store.saveManual(selected, BpmRecord(value, BpmSource.MANUAL))
        message = "Manual BPM correction saved."
        onChanged()
    }

    fun setManual(bpm: Double) {
        val selected = track ?: return
        if (!bpm.isFinite() || bpm !in 20.0..800.0) return
        val value = (bpm * 10.0).roundToInt() / 10.0
        store.saveManual(selected, BpmRecord(value, BpmSource.MANUAL))
        message = "Manual BPM saved."
        onChanged()
    }

    /** The tap sequence exists only while the BPM adjustments dialog is open. */
    fun beginTapSession() {
        tapSessionOpen = true
        tapTempo.reset()
        ui.removeCallbacks(tapTimeout)
    }

    fun stopTapSession() {
        tapSessionOpen = false
        tapTempo.reset()
        ui.removeCallbacks(tapTimeout)
    }

    fun tap() {
        if (!tapSessionOpen) return
        val sample = tapTempo.tap(SystemClock.elapsedRealtimeNanos())
        ui.removeCallbacks(tapTimeout)
        ui.postDelayed(tapTimeout, 3000L)
        if (sample.bpm != null) {
            setManual(sample.bpm)
            message = "Tap BPM: ${BpmFormatting.bpm(sample.bpm)} (middle 50% of ${sample.intervals} intervals)."
        } else {
            message = if (sample.restartedAfterPause) {
                "Tap sequence reset after pause. Keep tapping (${sample.intervals + 1}/${TapTempo.REQUIRED_TAPS})."
            } else {
                "Tap BPM: ${sample.intervals + 1}/${TapTempo.REQUIRED_TAPS} taps. Waiting for a stable window."
            }
        }
        onChanged()
    }

    /** Removes only an explicit override, retaining saved filename and detected values. */
    fun reset() {
        val selected = track ?: return
        store.clearManual(selected)
        message = "Manual correction cleared."
        onChanged()
    }

    fun close() {
        generation.incrementAndGet()
        stopTapSession()
        worker.shutdownNow()
    }
}
