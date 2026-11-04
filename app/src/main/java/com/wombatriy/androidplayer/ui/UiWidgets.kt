package com.wombatriy.androidplayer.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.Drawable
import android.widget.Button
import android.widget.ImageButton
import android.graphics.Color
import android.util.TypedValue
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.roundToInt


/** Dark slate navigation with comfortably readable pale surfaces. */
internal object PlayerPalette {
    val background = Color.rgb(218, 225, 233)
    val surface = Color.rgb(239, 244, 248)
    val nav = Color.rgb(30, 43, 55)
    val selected = Color.rgb(11, 124, 118)
    val text = Color.rgb(30, 45, 55)
    val border = Color.rgb(166, 184, 195)
}

internal fun Context.roundedSurface(color: Int = PlayerPalette.surface, cornerDp: Int = 12, stroke: Int = PlayerPalette.border): Drawable {
    val shape = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(cornerDp).toFloat()
        setStroke(dp(1), stroke)
    }
    return RippleDrawable(ColorStateList.valueOf(Color.argb(45, 6, 133, 126)), shape, null)
}

/** In-place update; never rebuild tab buttons on navigation. */
internal fun Button.setTabLook(selected: Boolean) {
    background = context.roundedSurface(
        if (selected) PlayerPalette.selected else PlayerPalette.nav,
        12,
        if (selected) PlayerPalette.selected else PlayerPalette.nav
    )
    setTextColor(if (selected) Color.WHITE else Color.rgb(208, 220, 227))
    elevation = if (selected) context.dp(2).toFloat() else 0f
}

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
        setTextColor(PlayerPalette.text)
        background = roundedSurface()
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
