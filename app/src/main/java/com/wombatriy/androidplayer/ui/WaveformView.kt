package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.wombatriy.androidplayer.waveform.WaveformData
import com.wombatriy.androidplayer.waveform.WaveformMath
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight waveform: one cached series of peaks, playhead, and draggable
 * A/B handles. All drawing happens in-place without reallocating a bitmap.
 *
 * Tap: seek. Drag near A/B: move marker. Long-press elsewhere: choose A or B.
 */
class WaveformView(
    context: Context,
    private val onSeek: (Long) -> Unit,
    private val onMarkerChanged: (Marker, Long, Boolean) -> Unit,
    private val onLongPressAt: (Long) -> Unit
) : View(context) {
    enum class Marker { A, B }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teal = Color.rgb(0, 119, 116)
    private val muted = Color.rgb(153, 184, 184)
    private val markerAColor = Color.rgb(37, 123, 210)
    private val markerBColor = Color.rgb(222, 108, 44)
    private val playheadColor = Color.rgb(32, 46, 54)
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val closeEnough = context.dp(26).toFloat()
    private val minimumBar = context.dp(1).toFloat()
    private val labelSize = 12f * resources.displayMetrics.scaledDensity

    private var waveform: WaveformData? = null
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var pointA: Long? = null
    private var pointB: Long? = null

    private var grabbed: Marker? = null
    private var downX = 0f
    private var downY = 0f
    private var dragged = false
    private var longPressed = false
    private val longPressRunnable = Runnable {
        if (!dragged && grabbed == null && durationMs > 0L) {
            longPressed = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onLongPressAt(timeForX(downX))
        }
    }

    init {
        minimumHeight = context.dp(100)
        isClickable = true
        isFocusable = true
        contentDescription = "Waveform. Tap to seek; long press to set A or B; drag markers."
        setBackgroundColor(Color.rgb(241, 247, 247))
    }

    fun setWaveform(value: WaveformData?) {
        waveform = value
        if (durationMs <= 0L && value != null) durationMs = value.durationMs
        invalidate()
    }

    fun setPlayback(position: Long, duration: Long) {
        val oldPosition = positionMs
        val oldDuration = durationMs
        durationMs = duration.takeIf { it > 0L } ?: waveform?.durationMs ?: 0L
        positionMs = position.coerceAtLeast(0L)
        if (oldPosition != positionMs || oldDuration != durationMs) invalidate()
    }

    fun setMarkers(a: Long?, b: Long?) {
        if (pointA == a && pointB == b) return
        pointA = a
        pointB = b
        invalidate()
    }

    private fun leftEdge(): Float = paddingLeft + context.dp(12).toFloat()
    private fun rightEdge(): Float = max(leftEdge() + 1f, width - paddingRight - context.dp(12).toFloat())
    private fun xAt(timeMs: Long): Float =
        leftEdge() + WaveformMath.fractionAtMillis(timeMs, durationMs) *
            (rightEdge() - leftEdge())

    private fun timeForX(x: Float): Long {
        val fraction = ((x - leftEdge()) / (rightEdge() - leftEdge())).coerceIn(0f, 1f)
        return WaveformMath.millisAtFraction(fraction, durationMs)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = leftEdge()
        val right = rightEdge()
        val widthPx = right - left
        val center = height * 0.5f
        val maxAmplitude = height * 0.36f

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dp(1).toFloat()
        paint.color = Color.rgb(218, 229, 229)
        canvas.drawLine(left, center, right, center, paint)

        val a = pointA
        val b = pointB
        if (a != null && b != null && b > a && durationMs > 0L) {
            paint.style = Paint.Style.FILL
            paint.color = Color.argb(45, 55, 164, 159)
            canvas.drawRect(xAt(a), center - maxAmplitude - context.dp(5),
                xAt(b), center + maxAmplitude + context.dp(5), paint)
        }

        val levels = waveform?.levels
        if (levels != null && levels.isNotEmpty()) {
            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = max(1f, widthPx / levels.size * 0.78f)
            val progressFraction = WaveformMath.fractionAtMillis(positionMs, durationMs)
            for (index in levels.indices) {
                val fraction = (index + 0.5f) / levels.size
                val x = left + fraction * widthPx
                val level = (levels[index].toInt() and 0xFF) / 255f
                val half = max(minimumBar, maxAmplitude * level)
                paint.color = if (fraction <= progressFraction) teal else muted
                canvas.drawLine(x, center - half, x, center + half, paint)
            }
        }

        fun drawMarker(markerMs: Long?, label: String, color: Int) {
            if (markerMs == null || durationMs <= 0L) return
            val x = xAt(markerMs)
            paint.color = color
            paint.strokeWidth = context.dp(2).toFloat()
            paint.style = Paint.Style.STROKE
            canvas.drawLine(x, context.dp(6).toFloat(),
                x, height - context.dp(6).toFloat(), paint)
            paint.style = Paint.Style.FILL
            paint.textSize = labelSize
            val textWidth = paint.measureText(label)
            val textX = (x + context.dp(4)).coerceIn(left, right - textWidth)
            canvas.drawText(label, textX, context.dp(15).toFloat(), paint)
        }

        drawMarker(pointA, "A", markerAColor)
        drawMarker(pointB, "B", markerBColor)

        if (durationMs > 0L) {
            paint.color = playheadColor
            paint.strokeWidth = context.dp(1).toFloat()
            paint.style = Paint.Style.STROKE
            val x = xAt(positionMs)
            canvas.drawLine(x, 0f, x, height.toFloat(), paint)
        }
    }

    private fun findMarker(touchX: Float): Marker? {
        if (durationMs <= 0L) return null
        val candidates = listOfNotNull(
            pointA?.let { Marker.A to abs(touchX - xAt(it)) },
            pointB?.let { Marker.B to abs(touchX - xAt(it)) }
        )
        return candidates.minByOrNull { it.second }?.takeIf { it.second < closeEnough }?.first
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (durationMs <= 0L) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downX = event.x
                downY = event.y
                grabbed = findMarker(event.x)
                dragged = false
                longPressed = false
                removeCallbacks(longPressRunnable)
                if (grabbed == null) {
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > slop || abs(event.y - downY) > slop) {
                    dragged = true
                    removeCallbacks(longPressRunnable)
                }
                val marker = grabbed
                if (marker != null && dragged) {
                    onMarkerChanged(marker, timeForX(event.x), false)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                val marker = grabbed
                if (!longPressed) {
                    if (marker != null && dragged) {
                        onMarkerChanged(marker, timeForX(event.x), true)
                    } else if (marker == null) {
                        onSeek(timeForX(event.x))
                    }
                }
                grabbed = null
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                // Release marker-edit mode even if Android interrupts the drag,
                // e.g. when a system gesture or dialog takes ownership.
                val marker = grabbed
                if (marker != null && dragged) {
                    onMarkerChanged(marker, timeForX(event.x), true)
                }
                grabbed = null
                dragged = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(longPressRunnable)
        super.onDetachedFromWindow()
    }
}
