package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.wombatriy.androidplayer.waveform.WaveformData
import com.wombatriy.androidplayer.waveform.WaveformMath
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
    private val playbackRunning: () -> Boolean
) : View(context) {
    companion object {
        /** Change this to 5 for a total 10-second window, or 15 for 30 seconds. */
        const val WINDOW_RADIUS_SECONDS = 10L
        private const val SEEK_THROTTLE_MS = 80L
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

    /**
     * PlayerScreen's normal ticker runs every ~120 ms. Use ExoPlayer's
     * live media clock once per display frame for smooth waveform movement.
     * This animates only the waveform, not the entire Player UI.
     */
    private val animationFrame = object : Runnable {
        override fun run() {
            frameScheduled = false
            if (!canAnimate()) return
            val current = playbackPosition().coerceAtLeast(0L)
            if (positionMs != current) {
                positionMs = current
                postInvalidateOnAnimation()
            }
            scheduleFrame()
        }
    }

    private fun canAnimate(): Boolean =
        !stopped && isAttachedToWindow && isShown &&
            !tracking && !externalScrubbing && waveform != null &&
            durationMs > 0L && playbackRunning()

    private fun scheduleFrame() {
        if (!frameScheduled && canAnimate()) {
            frameScheduled = true
            postOnAnimation(animationFrame)
        }
    }

    private fun stopFrames() {
        if (frameScheduled) {
            removeCallbacks(animationFrame)
            frameScheduled = false
        }
    }

    /** React to playing/paused, buffering and tab visibility changes. */
    fun refreshAnimationState() {
        if (canAnimate()) {
            val current = playbackPosition().coerceAtLeast(0L)
            if (current != positionMs) {
                positionMs = current
                invalidate()
            }
            scheduleFrame()
        } else {
            stopFrames()
        }
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
        if (durationMs <= 0L && value != null) durationMs = value.durationMs
        invalidate()
        refreshAnimationState()
    }

    fun setPlayback(position: Long, duration: Long) {
        val oldPosition = positionMs
        val oldDuration = durationMs
        durationMs = duration.takeIf { it > 0L } ?: waveform?.durationMs ?: 0L
        // Ignore the coarse (~120 ms) ticker position during normal playback.
        // A stale ticker value must never snap the smooth animated timeline.
        if (!tracking) {
            positionMs = if (!externalScrubbing && playbackRunning()) {
                playbackPosition().coerceAtLeast(0L)
            } else position.coerceAtLeast(0L)
        }
        if (oldPosition != positionMs || oldDuration != durationMs) invalidate()
        refreshAnimationState()
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
    private fun xFor(ms: Long, window: WaveformMath.Window): Float =
        left() + WaveformMath.fractionAtTime(ms, window) * (right() - left())
    private fun timeAt(x: Float, window: WaveformMath.Window): Long =
        WaveformMath.timeAtFraction(((x - left()) / (right() - left())).coerceIn(0f, 1f), window)

    override fun onDraw(canvas: Canvas) {
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

        val levels = waveform?.levels
        if (levels != null && levels.isNotEmpty()) {
            /*
             * The original rendering recalculated "one peak per screen bar"
             * as the viewport moved. Its bin boundaries jumped every few
             * pixels, making the SAME audio look louder/quieter periodically.
             *
             * Instead, group PCM peaks by ABSOLUTE track time and translate
             * their X coordinates as the viewport moves. Group boundaries,
             * peak heights and stroke width are fixed for a given zoom.
             */
            val groupSize = WaveformMath.drawGroupSize(window, span.toInt())
            val samplesPerStroke = WaveformMath.BIN_DURATION_MS * groupSize
            val strokeWidth = max(
                1f, (samplesPerStroke.toFloat() / window.lengthMs * span) * 0.82f
            )
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = strokeWidth
            val saved = canvas.save()
            canvas.clipRect(l, 0f, r, height.toFloat())
            for (first in WaveformMath.visibleGroupStarts(
                window, levels.size, groupSize
            )) {
                val last = (first + groupSize - 1).coerceAtMost(levels.lastIndex)
                var peak = 0
                for (index in first..last) {
                    peak = maxOf(peak, levels[index].toInt() and 255)
                }
                // Always the SAME point for a given group of audio bins.
                val centerTimeMs = (first.toLong() + (last - first + 1) / 2.0) *
                    WaveformMath.BIN_DURATION_MS
                val x = l + ((centerTimeMs - window.startMs) /
                    window.lengthMs * span).toFloat()
                val half = max(context.dp(1).toFloat(), heightScale * peak / 255f)
                paint.color = if (centerTimeMs <= displayedPosition()) teal else muted
                canvas.drawLine(x, middle - half, x, middle + half, paint)
            }
            canvas.restoreToCount(saved)
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
        fun timeLabel(ms: Long): String = String.format(
            java.util.Locale.US, "%d:%02d", ms / 60_000L, ms / 1_000L % 60L
        )
        val endLabel = timeLabel(window.endMs)
        canvas.drawText(timeLabel(window.startMs), l,
            height - context.dp(4).toFloat(), paint)
        canvas.drawText(endLabel, r - paint.measureText(endLabel),
            height - context.dp(4).toFloat(), paint)
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
                    positionMs = result
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
