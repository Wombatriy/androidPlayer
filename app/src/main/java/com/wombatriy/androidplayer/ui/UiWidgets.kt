package com.wombatriy.androidplayer.ui

import android.content.Context
import android.widget.Button
import android.widget.ImageButton
import android.graphics.Color
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()

internal fun Context.textLine(textValue: String = "", textSizeSp: Float = 15f): TextView =
    TextView(this).apply {
        text = textValue
        textSize = textSizeSp
        setPadding(0, dp(7), 0, dp(7))
    }

internal fun Context.actionButton(caption: String, onClick: () -> Unit): Button =
    Button(this).apply {
        text = caption
        isAllCaps = false
        setOnClickListener { onClick() }
    }

/**
 * Small icon-only button with a borderless ripple and an accessible label.
 * The PNG itself contains the common rounded-square outline.
 */
internal fun Context.iconButton(
    drawableId: Int,
    description: String,
    onClick: () -> Unit
): ImageButton = ImageButton(this).apply {
    setImageResource(drawableId)
    scaleType = ImageView.ScaleType.FIT_CENTER
    setPadding(dp(7), dp(7), dp(7), dp(7))
    contentDescription = description
    val ripple = TypedValue()
    if (theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        && ripple.resourceId != 0) {
        setBackgroundResource(ripple.resourceId)
    } else {
        setBackgroundColor(Color.TRANSPARENT)
    }
    setOnClickListener { onClick() }
}
