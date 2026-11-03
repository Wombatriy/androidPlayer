package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.wombatriy.androidplayer.waveform.WaveformAnimationPolicy
import com.wombatriy.androidplayer.waveform.WaveformFrameClock
import com.wombatriy.androidplayer.waveform.WaveformData
import com.wombatriy.androidplayer.waveform.WaveformMath
import com.wombatriy.androidplayer.waveform.WaveformSeekFence
import com.wombatriy.androidplayer.waveform.WaveformTileRenderer
import kotlin.math.abs
import kotlin.math.max

/**
 * Playback-centered waveform: tap to seek in the visible window, or drag
 * left/right to scrub. Loop markers are display-only (set by A/B buttons).
 * TabSwipeNavigator excludes this view from page-switch gestures.
 */
class WaveformView(
    context: Context,
    private val onSeek: (Long) -> Unit,
    private val playbackPosition: () -> Long,
    private val playbackRunning: () -> Boolean,
    private val playbackSpeed: () -> Float
) : View(context) {
    companion object {
        /** Change this to 5 for a total 10-second window, or 15 for 30 seconds. */
        const val WINDOW_RADIUS_SECONDS = 10L
        private const val SEEK_THROTTLE_MS = 80L
        // 30 fps is smooth enough for a 20-second viewport, and lets the
        // UI thread prioritize Play/Pause, speed and transport input.
        private const val FRAME_INTERVAL_MS = 32L
        private const val PRELOAD_AROUND_WINDOW_MS = 10_000L
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teal = Color.rgb(0, 119, 116)
    private val muted = Color.rgb(153, 184, 184)
    private val markerAColor = Color.rgb(37, 123, 210)
    private val markerBColor = Color.rgb(222, 108, 44)
    private val ink = Color.rgb(32, 46, 54)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val labelSize = 11f * resources.displayMetrics.scaledDensity

    private var waveform: WaveformData? = null
    private var durationMs = 0L
    private var positionMs = 0L
    private var pointA: Long? = null
    private var pointB: Long? = null
    private var tracking = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var originalPosition = 0L
    private var previewPosition = 0L
    private var dragWindow = WaveformMath.Window(0L, 0L)
    private var lastSeekAt = 0L
    private var externalScrubbing = false
    private var frameScheduled = false
    private var stopped = false
    private val seekFence = WaveformSeekFence()
    private var lastFrameNanos = 0L
    private var lastWarningNanos = 0L
    private val smoothClock = WaveformFrameClock()
    private var tile: WaveformTileRenderer.Tile? = null
    private val tiles = WaveformTileRenderer { ready ->
        if (!stopped && waveform === ready.source) {
            tile = ready
            invalidate()
        }
    }
    private val mutedBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(muted, PorterDuff.Mode.SRC_IN)
    }
    private val playedBitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(teal, PorterDuff.Mode.SRC_IN)
    }
    private var firstLabelSecond = -1L
    private var lastLabelSecond = -1L
    private var firstLabel = "0:00"
    private var lastLabel = "0:00"

    /**
     * PlayerScreen's normal ticker runs every ~120 ms. Use ExoPlayer's
     * live media clock once per display frame for smooth waveform movement.
     * This animates only the waveform, not the entire Player UI.
     */
    private val animationFrame = object : Runnable {
        override fun run() {
            frameScheduled = false
            if (!canAnimate()) return
            // Move continuously by a monotonic clock, not by the potentially
            // quantized media position returned by a particular device.
            val now = SystemClock.elapsedRealtimeNanos()
            if (lastFrameNanos > 0L) {
                val gapMs = (now - lastFrameNanos) / 1_000_000L
                warnIfSlow(gapMs, "vsync gap")
            }
            lastFrameNanos = now
            val current = smoothClock.positionAt(now)
            if (positionMs != current) {
                positionMs = current
                postInvalidateOnAnimation()
            }
            scheduleFrame()
        }
    }

    private fun canAnimate(): Boolean = WaveformAnimationPolicy.shouldAnimate(
        playing = playbackRunning(),
        visible = !stopped && isAttachedToWindow && isShown,
        hasWaveform = waveform != null && durationMs > 0L,
        interactionBlocked = tracking || externalScrubbing
    )

    private fun scheduleFrame() {
        if (!frameScheduled && canAnimate()) {
            frameScheduled = true
            postOnAnimationDelayed(animationFrame, FRAME_INTERVAL_MS)
        }
    }

    private fun stopFrames() {
        if (frameScheduled) {
            removeCallbacks(animationFrame)
            frameScheduled = false
        }
        lastFrameNanos = 0L
    }

    /** Log only substantial latency, at most once per ten seconds. */
    private fun warnIfSlow(ms: Long, reason: String) {
        if (ms < 90L) return
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastWarningNanos >= 10_000_000_000L) {
            lastWarningNanos = now
            Log.w("WaveformPerf", "$reason: ${ms}ms (playback active, no user scrub)")
        }
    }

    /**
     * Tell the visual clock where a seek WILL go before Media3 updates
     * currentPosition. Otherwise a stale sample can move the waveform
     * backwards just after tapping or releasing a swipe.
     */
    fun onSeekRequested(targetMs: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        val target = targetMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        seekFence.begin(target, now)
        smoothClock.sync(target, now, playbackSpeed(), playbackRunning(),
            durationMs, force = true)
        if (!tracking && !externalScrubbing) {
            positionMs = target
            invalidate()
        }
    }

    /** Media3 confirmed a discontinuity. Do not snap for tiny corrections. */
    fun onSeekApplied(actualMs: Long) {
        val now = SystemClock.elapsedRealtimeNanos()
        val actual = actualMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val requested = seekFence.confirmed(now)
        val expected = requested ?: smoothClock.positionAt(now)
        smoothClock.sync(
            actual, now, playbackSpeed(), playbackRunning(), durationMs,
            // Media3 may send SEEK followed by SEEK_ADJUSTMENT for the same
            // operation. A second small adjustment must not reintroduce
            // the visible backwards snap.
            force = kotlin.math.abs(actual - expected) > 1_200L
        )
        if (!tracking && !externalScrubbing) {
            positionMs = smoothClock.positionAt(now)
            invalidate()
        }
    }

    private fun shouldIgnoreRawPosition(nowNanos: Long): Boolean =
        seekFence.ignoreOldSample(nowNanos)

    private fun sampledPosition(nowNanos: Long): Long =
        if (shouldIgnoreRawPosition(nowNanos)) smoothClock.positionAt(nowNanos)
        else playbackPosition()

    /** Pausing/resuming changes the clock slope, never its visual origin. */
    fun refreshAnimationState() {
        val now = SystemClock.elapsedRealtimeNanos()
        val running = playbackRunning()
        val animate = canAnimate()
        if (!tracking && !externalScrubbing) {
            val observed = sampledPosition(now)
            smoothClock.sync(observed, now, playbackSpeed(), running, durationMs)
            val current = smoothClock.positionAt(now)
            if (current != positionMs) {
                positionMs = current
                invalidate()
            }
        }
        if (animate) scheduleFrame() else stopFrames()
    }

    /** The full-track SeekBar must win over waveform auto-animation. */
    fun setExternalScrubbing(active: Boolean) {
        if (externalScrubbing == active) return
        externalScrubbing = active
        if (active) stopFrames() else refreshAnimationState()
    }

    fun close() {
        stopped = true
        stopFrames()
        tiles.close()
        tile = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshAnimationState()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) refreshAnimationState() else stopFrames()
    }

    override fun onDetachedFromWindow() {
        stopFrames()
        super.onDetachedFromWindow()
    }

    init {
        minimumHeight = context.dp(100)
        isClickable = true
        isFocusable = true
        contentDescription = "Zoomed waveform. Swipe left to go forward, right to go back; tap to seek. Use A/B buttons for loop markers."
        setBackgroundColor(Color.rgb(241, 247, 247))
    }

    fun setWaveform(value: WaveformData?) {
        waveform = value
        // Old tile must never be displayed for a different audio file.
        tiles.cancel()
        tile = null
        if (value == null) seekFence.clear()
        if (durationMs <= 0L && value != null) durationMs = value.durationMs
        invalidate()
        refreshAnimationState()
    }

    fun setPlayback(position: Long, duration: Long) {
        val oldDuration = durationMs
        val oldPosition = positionMs
        durationMs = duration.takeIf { it > 0L } ?: waveform?.durationMs ?: 0L
        val now = SystemClock.elapsedRealtimeNanos()
        if (externalScrubbing) {
            positionMs = position.coerceAtLeast(0L)
        } else if (!tracking) {
            val running = playbackRunning()
            // A controller seek callback may carry the new desired position
            // before Media3 publishes it. While settling, preserve the
            // explicit destination instead of reading the old media clock.
            val observed = if (shouldIgnoreRawPosition(now)) {
                smoothClock.positionAt(now)
            } else if (running) playbackPosition() else position
            smoothClock.sync(
                observedMs = observed,
                timeNanos = now,
                playbackSpeed = playbackSpeed(),
                isPlaying = running,
                mediaDurationMs = durationMs
            )
            positionMs = smoothClock.positionAt(now)
        }
        if (oldPosition != positionMs || oldDuration != durationMs) invalidate()
        // Keep the vsync loop alive, but don't re-anchor to the raw player
        // position every 120ms: doing so reintroduces the original jitter.
        if (canAnimate()) scheduleFrame() else stopFrames()
    }

    fun setMarkers(a: Long?, b: Long?) {
        if (pointA == a && pointB == b) return
        pointA = a
        pointB = b
        invalidate()
    }

    private fun left() = paddingLeft + context.dp(12).toFloat()
    private fun right() = max(left() + 1f, width - paddingRight - context.dp(12).toFloat())
    private fun displayedPosition() = if (tracking && dragging) previewPosition else positionMs
    private fun viewport() = WaveformMath.windowAround(
        displayedPosition(), durationMs, WINDOW_RADIUS_SECONDS * 1_000L
    )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        tiles.cancel()
        tile = null
    }

    /**
     * The only rendering preparation on the main thread is checking a small
     * cache key. A dedicated worker builds the next tile asynchronously.
     */
    private fun requestTileIfNeeded(
        window: WaveformMath.Window, contentWidth: Int
    ): WaveformTileRenderer.Tile? {
        val data = waveform ?: return null
        if (window.lengthMs <= 0 || contentWidth <= 0 || height <= 0) return null
        val size = WaveformMath.drawGroupSize(window, contentWidth)
        val current = tile
        val compatible = current != null && current.source === data &&
            current.windowMs == window.lengthMs &&
            current.viewWidth == contentWidth &&
            current.viewHeight == height && current.groupSize == size
        val covered = compatible && current != null &&
            window.startMs >= current.startMs && window.endMs <= current.endMs
        val needsPrefetch = if (!covered || current == null) {
            true
        } else {
            WaveformMath.peakPathNeedsRebuild(
                viewport = window,
                cached = WaveformMath.Window(current.startMs, current.endMs),
                trackDurationMs = durationMs,
                preloadMs = minOf(10_000L, window.lengthMs / 2L),
                configurationChanged = !compatible
            )
        }
        if (needsPrefetch) {
            tiles.request(data, window, durationMs, contentWidth, height, size)
        }
        return if (covered) current else null
    }

    private fun xFor(ms: Long, window: WaveformMath.Window): Float =
        left() + WaveformMath.fractionAtTime(ms, window) * (right() - left())
    private fun timeAt(x: Float, window: WaveformMath.Window): Long =
        WaveformMath.timeAtFraction(((x - left()) / (right() - left())).coerceIn(0f, 1f), window)

    override fun onDraw(canvas: Canvas) {
        val drawStarted = SystemClock.elapsedRealtimeNanos()
        super.onDraw(canvas)
        val window = viewport()
        val l = left()
        val r = right()
        val span = r - l
        val middle = height * 0.5f
        val heightScale = height * 0.29f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(1).toFloat()
        paint.color = Color.rgb(218, 229, 229)
        canvas.drawLine(l, middle, r, middle, paint)
        if (window.lengthMs == 0L) return

        val a = pointA
        val b = pointB
        if (a != null && b != null && b > a) {
            val start = maxOf(a, window.startMs)
            val end = minOf(b, window.endMs)
            if (start < end) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(45, 55, 164, 159)
                canvas.drawRect(xFor(start, window), middle - heightScale,
                    xFor(end, window), middle + heightScale, paint)
            }
        }

        val prepared = requestTileIfNeeded(window, span.toInt())
        if (prepared != null) {
            val translationX = l -
                ((window.startMs - prepared.startMs).toDouble() *
                    span / window.lengthMs).toFloat()
            val playhead = xFor(displayedPosition(), window)

            // Off-thread rasterization: onDraw() never walks PCM bins or
            // draws Paths. Two small bitmap draws with a clip are all that
            // remain in the main-thread hot path.
            canvas.save()
            canvas.clipRect(l, 0f, r, height.toFloat())
            canvas.drawBitmap(prepared.bitmap, translationX, 0f, mutedBitmapPaint)
            canvas.restore()
            if (playhead > l) {
                canvas.save()
                canvas.clipRect(l, 0f, playhead.coerceAtMost(r), height.toFloat())
                canvas.drawBitmap(prepared.bitmap, translationX, 0f, playedBitmapPaint)
                canvas.restore()
            }
        }

        fun marker(time: Long?, label: String, color: Int) {
            if (time == null || time !in window.startMs..window.endMs) return
            val x = xFor(time, window)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dp(1).toFloat()
            paint.color = color
            canvas.drawLine(x, context.dp(5).toFloat(),
                x, height - context.dp(18).toFloat(), paint)
            paint.style = Paint.Style.FILL
            paint.textSize = labelSize
            canvas.drawText(label,
                (x + context.dp(3)).coerceIn(l, r - paint.measureText(label)),
                context.dp(14).toFloat(), paint)
        }
        marker(pointA, "A", markerAColor)
        marker(pointB, "B", markerBColor)

        paint.color = ink
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(1).toFloat()
        val x = xFor(displayedPosition(), window)
        canvas.drawLine(x, 0f, x, height - context.dp(17).toFloat(), paint)

        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(91, 105, 109)
        paint.textSize = labelSize
        // Format text only when the displayed whole second changes.
        // String.format on every vsync used to allocate heavily and cause
        // intermittent GC pauses while the waveform was animating.
        val startSecond = window.startMs / 1_000L
        val endSecond = window.endMs / 1_000L
        if (startSecond != firstLabelSecond) {
            firstLabelSecond = startSecond
            firstLabel = "${startSecond / 60}:${(startSecond % 60).toString().padStart(2, '0')}"
        }
        if (endSecond != lastLabelSecond) {
            lastLabelSecond = endSecond
            lastLabel = "${endSecond / 60}:${(endSecond % 60).toString().padStart(2, '0')}"
        }
        canvas.drawText(firstLabel, l, height - context.dp(4).toFloat(), paint)
        canvas.drawText(lastLabel, r - paint.measureText(lastLabel),
            height - context.dp(4).toFloat(), paint)
        warnIfSlow(
            (SystemClock.elapsedRealtimeNanos() - drawStarted) / 1_000_000L,
            "onDraw"
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                tracking = true
                stopFrames()
                dragging = false
                downX = event.x
                downY = event.y
                originalPosition = positionMs
                previewPosition = positionMs
                dragWindow = viewport()
                lastSeekAt = 0L
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tracking) return false
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragging && abs(dx) > slop && abs(dx) >= abs(dy)) dragging = true
                if (dragging) {
                    previewPosition = WaveformMath.swipedPosition(
                        originalPosition, dx, right() - left(),
                        dragWindow.lengthMs, durationMs
                    )
                    invalidate()
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastSeekAt >= SEEK_THROTTLE_MS) {
                        onSeek(previewPosition)
                        lastSeekAt = now
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!tracking) return false
                val tap = !dragging && abs(event.x - downX) <= slop &&
                    abs(event.y - downY) <= slop
                val longHold = event.eventTime - event.downTime >=
                    ViewConfiguration.getLongPressTimeout()
                val wasDragging = dragging
                val result = if (dragging) previewPosition else timeAt(event.x, dragWindow)
                tracking = false
                dragging = false
                if (wasDragging || tap && !longHold) {
                    // PlaybackController.onSeekRequested() gives us the
                    // loop-constrained target *before* asking Media3 to seek.
                    // Never read currentPosition here: it may still reflect
                    // the previous song location for several frames.
                    onSeek(result)
                    if (tap) performClick()
                }
                invalidate()
                refreshAnimationState()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                tracking = false
                dragging = false
                invalidate()
                refreshAnimationState()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
