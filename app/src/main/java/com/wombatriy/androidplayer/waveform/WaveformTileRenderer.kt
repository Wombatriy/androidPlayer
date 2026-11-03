package com.wombatriy.androidplayer.waveform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max

/**
 * Prepares a small, transparent waveform tile OFF the UI thread.
 *
 * The View only translates a prepared bitmap while playing. Neither PCM
 * aggregation nor Path rasterization is done in View.onDraw(). Only one tile
 * is retained at a time; tiles are discarded when switching tracks/zoom.
 */
class WaveformTileRenderer(private val onReady: (Tile) -> Unit) {
    data class Tile(
        val source: WaveformData,
        val startMs: Long,
        val endMs: Long,
        val windowMs: Long,
        val viewWidth: Int,
        val viewHeight: Int,
        val groupSize: Int,
        val mutedBitmap: Bitmap,
        val playedBitmap: Bitmap
    )

    private data class Request(
        val source: WaveformData,
        val startMs: Long,
        val endMs: Long,
        val windowMs: Long,
        val viewWidth: Int,
        val viewHeight: Int,
        val groupSize: Int,
        val firstPixel: Long,
        val pixelCount: Int
    )

    private val main = Handler(Looper.getMainLooper())
    private val sequence = AtomicInteger()
    // At most one running and one pending rasterization. Rapid scrubbing
    // discards obsolete queued tiles instead of accumulating dozens of MB.
    private val worker = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(1),
        { task -> Thread(task, "waveform-tile").apply { priority = Thread.MIN_PRIORITY } },
        ThreadPoolExecutor.DiscardOldestPolicy()
    )
    private var pending: Request? = null
    @Volatile private var closed = false

    /** Called only from the Android main thread. */
    fun request(
        waveform: WaveformData,
        window: WaveformMath.Window,
        durationMs: Long,
        contentWidthPx: Int,
        viewHeight: Int,
        groupSize: Int
    ) {
        if (closed || window.lengthMs <= 0L || contentWidthPx <= 0 ||
            viewHeight <= 0) return
        val grid = WaveformMath.rasterGrid(
            window, durationMs, contentWidthPx
        )
        if (grid.pixelCount <= 0) return
        val key = Request(
            waveform, grid.window.startMs, grid.window.endMs,
            window.lengthMs, contentWidthPx, viewHeight, groupSize,
            grid.firstPixel, grid.pixelCount
        )
        // A pending image covering the moving viewport is good enough.
        val previous = pending
        if (previous != null &&
            previous.source === waveform &&
            previous.windowMs == key.windowMs &&
            previous.viewWidth == key.viewWidth &&
            previous.viewHeight == key.viewHeight &&
            previous.groupSize == key.groupSize &&
            window.startMs >= previous.startMs &&
            window.endMs <= previous.endMs) return

        pending = key
        val generation = sequence.incrementAndGet()
        worker.execute {
            if (generation != sequence.get() || closed) return@execute
            runCatching {
                android.os.Process.setThreadPriority(
                    android.os.Process.THREAD_PRIORITY_BACKGROUND
                )
            }
            val rendered = runCatching { render(key, generation) }.getOrNull()
            main.post {
                if (generation == sequence.get()) pending = null
                if (!closed && generation == sequence.get() && rendered != null) {
                    onReady(rendered)
                }
            }
        }
    }

    /** Invalidate jobs after track changes, without blocking the UI. */
    fun cancel() {
        sequence.incrementAndGet()
        pending = null
    }

    fun close() {
        closed = true
        cancel()
        worker.shutdownNow()
    }

    private fun render(request: Request, generation: Int): Tile {
        if (generation != sequence.get() || closed) {
            throw java.util.concurrent.CancellationException("Stale waveform tile")
        }
        val bitmapWidth = request.pixelCount
        val mutedBitmap = Bitmap.createBitmap(
            bitmapWidth, request.viewHeight, Bitmap.Config.ARGB_8888
        )
        val playedBitmap = Bitmap.createBitmap(
            bitmapWidth, request.viewHeight, Bitmap.Config.ARGB_8888
        )
        val mutedCanvas = Canvas(mutedBitmap)
        val playedCanvas = Canvas(playedBitmap)
        /*
         * Draw a solid vertical envelope at each destination pixel rather
         * than hundreds of isolated antialiased 1–2px strokes.
         *
         * At the default zoom, a 50ms bin occupies only a few pixels.
         * Moving the previous striped bitmap by a fractional pixel made
         * transparent gaps flicker as the GPU bilinearly resampled it.
         * Contiguous fully-filled columns keep interior pixels opaque and
         * retain smooth subpixel scrolling (no integer snapping / judder).
         */
        val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(153, 184, 184)
            style = Paint.Style.FILL
        }
        val playedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(0, 119, 116)
            style = Paint.Style.FILL
        }
        val centerY = request.viewHeight * 0.5f
        val heightScale = request.viewHeight * 0.29f
        val levels = request.source.levels
        val msPerPixel = request.windowMs.toDouble() / request.viewWidth
        for (column in 0 until bitmapWidth) {
            if (column % 64 == 0 &&
                (Thread.currentThread().isInterrupted ||
                    generation != sequence.get() || closed)) {
                throw java.util.concurrent.CancellationException("Stale waveform tile")
            }
            // All tiles use the same absolute source-pixel grid. A peak
            // won't subtly change shape when a newer tile replaces one.
            val sampleStartMs = (request.firstPixel + column) * msPerPixel
            val sampleEndMs = minOf(
                request.endMs.toDouble(), sampleStartMs + msPerPixel
            )
            val peak = WaveformMath.peakForPixel(
                levels, sampleStartMs, sampleEndMs
            )
            val half = max(1f, heightScale * peak / 255f)
            val left = column.toFloat()
            val right = left + 1f
            mutedCanvas.drawRect(
                left, centerY - half, right, centerY + half, mutedPaint
            )
            playedCanvas.drawRect(
                left, centerY - half, right, centerY + half, playedPaint
            )
        }
        return Tile(
            request.source, request.startMs, request.endMs, request.windowMs,
            request.viewWidth, request.viewHeight, request.groupSize,
            mutedBitmap, playedBitmap
        )
    }
}
