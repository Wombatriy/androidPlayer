package com.wombatriy.androidplayer.bpm

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.wombatriy.androidplayer.Track
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Bounded batch BPM analysis for a selected Library snapshot.
 *
 * - At most two decoders compete for audio resources. AndroidBpmDecoder also
 *   limits total concurrent decoders shared with one-track analysis.
 * - All metadata access, decoding and durable writes run outside the UI thread.
 * - Saved detections are skipped, so a cancelled/partial batch can be resumed.
 * - UI notifications are throttled and grouped to avoid ListView churn.
 * - New library selections cancel old sessions, preventing stale UI updates.
 */
class BpmBatchAnalyzer(
    context: Context,
    private val onState: (State) -> Unit,
    private val onRowsChanged: (Map<String, BpmRecord>, Boolean) -> Unit
) {
    data class State(
        val running: Boolean = false,
        val processed: Int = 0,
        val total: Int = 0,
        val detected: Int = 0,
        val skipped: Int = 0,
        val failed: Int = 0,
        val message: String = ""
    )

    private val app = context.applicationContext
    private val store = BpmRepository(app)
    private val main = Handler(Looper.getMainLooper())
    private val preparation = Executors.newSingleThreadExecutor()
    private val workers = Executors.newFixedThreadPool(
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 2)
    )
    private val generation = AtomicInteger()
    private val sourcesGeneration = AtomicInteger()
    @Volatile private var closed = false

    var state = State()
        private set

    /**
     * Metadata is resolved on the preparation executor, not in ListView.getView().
     * The UI only receives one immutable map for the entire library.
     */
    fun refreshSources(tracks: List<Track>) {
        val token = sourcesGeneration.incrementAndGet()
        val snapshot = tracks.toList()
        preparation.execute {
            val rows = HashMap<String, BpmRecord>(snapshot.size)
            for (track in snapshot) {
                if (closed || sourcesGeneration.get() != token) return@execute
                store.resolved(track)?.let { rows[track.uri.toString()] = it }
            }
            main.post {
                if (!closed && sourcesGeneration.get() == token) {
                    onRowsChanged(rows, true)
                }
            }
        }
    }

    fun start(tracks: List<Track>) {
        if (closed || state.running || tracks.isEmpty()) return
        val snapshot = tracks.toList()
        val token = generation.incrementAndGet()
        state = State(running = true, total = snapshot.size, message = "Preparing BPM queue…")
        onState(state)

        preparation.execute {
            val plan = try {
                BatchBpmPolicy.selectMissing(snapshot) { track ->
                    if (!active(token)) throw java.util.concurrent.CancellationException()
                    store.detected(track) != null
                }
            } catch (_: java.util.concurrent.CancellationException) {
                return@execute
            }
            if (!active(token)) return@execute
            val pending = plan.pending
            val saved = plan.skipped

            val processed = AtomicInteger(saved)
            val succeeded = AtomicInteger()
            val failed = AtomicInteger()
            val next = AtomicInteger()
            val lastUpdate = AtomicLong(0L)
            val doneWorkers = AtomicInteger(minOf(WORKER_COUNT, pending.size))

            fun publish(complete: Boolean = false) {
                val now = SystemClock.elapsedRealtime()
                val previous = lastUpdate.get()
                if (!complete && now - previous < 250L) return
                if (!complete && !lastUpdate.compareAndSet(previous, now)) return
                val finished = processed.get()
                val good = succeeded.get()
                val errors = failed.get()
                val status = State(
                    running = !complete,
                    processed = finished,
                    total = snapshot.size,
                    detected = good,
                    skipped = saved,
                    failed = errors,
                    message = if (complete) "BPM analysis finished" else "Analyzing audio…"
                )
                main.post {
                    if (active(token)) {
                        state = status
                        onState(status)
                    }
                }
            }

            main.post {
                if (active(token)) {
                    state = State(
                        running = pending.isNotEmpty(),
                        processed = saved,
                        total = snapshot.size,
                        skipped = saved,
                        message = if (pending.isEmpty()) "All tracks already analyzed" else "Analyzing audio…"
                    )
                    onState(state)
                }
            }

            if (pending.isEmpty()) return@execute

            repeat(doneWorkers.get()) {
                workers.execute {
                    val decoder = AndroidBpmDecoder(app)
                    val batch = LinkedHashMap<Track, BpmRecord>()

                    fun flush() {
                        if (batch.isEmpty()) return
                        val savedBatch = batch.toMap()
                        batch.clear()
                        // Batched persistent write: avoids per-track disk I/O.
                        val written = runCatching { store.saveDetectedBatch(savedBatch) }
                            .getOrDefault(false)
                        if (written) {
                            succeeded.addAndGet(savedBatch.size)
                            val changes = HashMap<String, BpmRecord>(savedBatch.size)
                            for ((track, value) in savedBatch) {
                                // Keep filename/manual as preferred in the Library.
                                changes[track.uri.toString()] =
                                    store.resolved(track) ?: value
                            }
                            main.post {
                                if (active(token)) onRowsChanged(changes, false)
                            }
                        } else {
                            failed.addAndGet(savedBatch.size)
                        }
                        processed.addAndGet(savedBatch.size)
                        publish()
                    }

                    try {
                        while (active(token)) {
                            val index = next.getAndIncrement()
                            if (index >= pending.size) break
                            val track = pending[index]
                            val estimate = runCatching {
                                decoder.analyze(track.uri) { !active(token) }
                            }.getOrNull()
                            if (!active(token)) break
                            if (estimate != null) {
                                batch[track] = BpmRecord(
                                    estimate.bpm, BpmSource.DETECTED,
                                    estimate.confidence, estimate.alternativeBpm
                                )
                                if (batch.size >= 6) flush()
                            } else {
                                failed.incrementAndGet()
                                processed.incrementAndGet()
                                publish()
                            }
                        }
                    } finally {
                        flush() // Preserve completed work even after cancellation.
                        if (doneWorkers.decrementAndGet() == 0) publish(complete = true)
                    }
                }
            }
        }
    }

    fun cancel() {
        if (!state.running) return
        generation.incrementAndGet()
        state = state.copy(running = false, message = "BPM analysis cancelled; saved results kept")
        onState(state)
    }

    /** Drop old progress after switching folders; committed BPM results remain. */
    fun resetForLibraryChange() {
        if (state.running) generation.incrementAndGet()
        state = State()
        onState(state)
    }

    fun close() {
        closed = true
        generation.incrementAndGet()
        sourcesGeneration.incrementAndGet()
        workers.shutdownNow()
        preparation.shutdownNow()
    }

    private fun active(token: Int): Boolean =
        !closed && token == generation.get() && !Thread.currentThread().isInterrupted

    companion object {
        private const val WORKER_COUNT = 2
    }
}
