package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Locale

/**
 * Fixed speed ruler matching the player slider range (0.25x..1.50x).
 * Every 0.1x has a tick; 1.0x is highlighted. Labels are staggered to
 * stay legible on small phone screens.
 */
class SpeedRulerView(context: Context) : View(context) {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.scaledDensity
        color = Color.rgb(90, 105, 120)
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(145, 156, 168)
        strokeWidth = 1f * density
    }

    private fun dp(value: Float) = value * density

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val left = dp(19f)
        val right = width.toFloat() - dp(19f)
        val span = right - left
        if (span <= 0f) return

        fun coordinate(speed: Float): Float =
            left + ((speed - 0.25f) / 1.25f) * span

        val bottomY = dp(34f)
        val tickY = dp(15f)
        val tickBottomY = dp(22f)

        // The beginning of the range isn't aligned to a 0.1x interval.
        canvas.drawLine(left, tickY, left, tickBottomY, tickPaint)
        canvas.drawText("0.25", left, bottomY, textPaint)

        for (i in 3..15) {
            val speed = i / 10f
            val x = coordinate(speed)
            val normal = i == 10
            tickPaint.color = if (normal) Color.rgb(14, 128, 125) else Color.rgb(145, 156, 168)
            tickPaint.strokeWidth = dp(if (normal) 2.4f else 1f)
            canvas.drawLine(x, if (normal) dp(13f) else tickY, x, tickBottomY, tickPaint)
            if (normal) {
                textPaint.color = Color.rgb(14, 128, 125)
                textPaint.isFakeBoldText = true
                canvas.drawText("1.0×", x, dp(11f), textPaint)
                textPaint.isFakeBoldText = false
            } else if (i % 2 == 1 && i >= 5) {
                textPaint.color = Color.rgb(90, 105, 120)
                canvas.drawText(String.format(Locale.US, "%.1f", speed), x, bottomY, textPaint)
            }
        }
    }
}
