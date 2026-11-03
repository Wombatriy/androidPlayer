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
import kotlin.math.ceil
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
        val groupSize: Int
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
        val buffered = WaveformMath.bufferedTileWindow(window, durationMs)
        val firstMs = buffered.startMs
        val lastMs = buffered.endMs
        val key = Request(
            waveform, firstMs, lastMs, window.lengthMs,
            contentWidthPx, viewHeight, groupSize
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
        val contentLength = request.endMs - request.startMs
        val pixelsPerMs = request.viewWidth.toDouble() / request.windowMs
        val bitmapWidth = ceil(contentLength * pixelsPerMs)
            .toInt().coerceIn(1, request.viewWidth * 2 + 2)
        val mutedBitmap = Bitmap.createBitmap(
            bitmapWidth, request.viewHeight, Bitmap.Config.ARGB_8888
        )
        val playedBitmap = Bitmap.createBitmap(
            bitmapWidth, request.viewHeight, Bitmap.Config.ARGB_8888
        )
        val mutedCanvas = Canvas(mutedBitmap)
        val playedCanvas = Canvas(playedBitmap)
        val stroke = max(1f,
            (WaveformMath.BIN_DURATION_MS * request.groupSize *
                pixelsPerMs * 0.82).toFloat()
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // Rasterize the actual colors once in the worker. Applying
            // PorterDuff color filters to a moving mask on every UI frame
            // can exhibit uneven GPU blending/flicker on some devices.
            color = Color.rgb(153, 184, 184)
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = stroke
        }
        val centerY = request.viewHeight * 0.5f
        val heightScale = request.viewHeight * 0.29f
        val levels = request.source.levels
        for (first in WaveformMath.visibleGroupStarts(
            WaveformMath.Window(request.startMs, request.endMs),
            levels.size, request.groupSize
        )) {
            if (Thread.currentThread().isInterrupted ||
                generation != sequence.get() || closed) {
                throw java.util.concurrent.CancellationException("Stale waveform tile")
            }
            val last = (first + request.groupSize - 1).coerceAtMost(levels.lastIndex)
            var peak = 0
            for (index in first..last) peak = maxOf(peak, levels[index].toInt() and 255)
            val centerTime = (first.toLong() + (last - first + 1) / 2.0) *
                WaveformMath.BIN_DURATION_MS
            val x = ((centerTime - request.startMs) * pixelsPerMs).toFloat()
            val half = max(1f, heightScale * peak / 255f)
            mutedCanvas.drawLine(x, centerY - half, x, centerY + half, paint)
            paint.color = Color.rgb(0, 119, 116)
            playedCanvas.drawLine(x, centerY - half, x, centerY + half, paint)
            paint.color = Color.rgb(153, 184, 184)
        }
        return Tile(
            request.source, request.startMs, request.endMs, request.windowMs,
            request.viewWidth, request.viewHeight, request.groupSize,
            mutedBitmap, playedBitmap
        )
    }
}
