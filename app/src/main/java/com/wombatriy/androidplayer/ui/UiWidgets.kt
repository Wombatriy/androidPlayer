package com.wombatriy.androidplayer.ui

import android.content.Context
import android.widget.Button
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
