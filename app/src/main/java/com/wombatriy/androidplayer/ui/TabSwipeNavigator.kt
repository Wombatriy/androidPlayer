package com.wombatriy.androidplayer.ui

import android.app.Activity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.Spinner
import kotlin.math.abs

/**
 * Non-invasive horizontal navigation for four tabs.
 *
 * Returns null to dispatch normally, 0 to consume a gesture already handled,
 * and +/-1 when a neighboring tab should be selected. Only horizontal drags
 * from non-control content qualify. Vertical ListView scrolling, SeekBars,
 * buttons, dropdowns, multi-touch and the system Back-edge region are excluded.
 */
class TabSwipeNavigator(private val activity: Activity) {
    private var downX = 0f
    private var downY = 0f
    private var eligible = false
    private var consuming = false

    fun onTouch(event: MotionEvent, currentTab: Int, totalTabs: Int): Int? {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                consuming = false
                downX = event.rawX
                downY = event.rawY
                val edge = activity.dp(28)
                eligible = event.pointerCount == 1 &&
                    event.rawX > edge &&
                    event.rawX < activity.resources.displayMetrics.widthPixels - edge &&
                    canSwipeFrom(event.rawX.toInt(), event.rawY.toInt())
            }

            MotionEvent.ACTION_POINTER_DOWN -> eligible = false

            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (consuming) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) consuming = false
                    eligible = false
                    return 0
                }
                if (eligible && event.pointerCount == 1) {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dy) > activity.dp(45) && abs(dy) > abs(dx) * 0.7f) {
                        eligible = false
                    } else {
                        val step = TabSwipePolicy.direction(
                            dx, dy, event.eventTime - event.downTime,
                            activity.dp(84).toFloat()
                        )
                        if (step != 0 && currentTab + step in 0 until totalTabs) {
                            consuming = event.actionMasked != MotionEvent.ACTION_UP
                            eligible = false
                            return step
                        }
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP) eligible = false
            }

            MotionEvent.ACTION_CANCEL -> {
                val wasConsuming = consuming
                consuming = false
                eligible = false
                if (wasConsuming) return 0
            }
        }
        return null
    }

    private fun canSwipeFrom(rawX: Int, rawY: Int): Boolean {
        var target = deepestVisible(activity.window.decorView, rawX, rawY)
        while (target != null) {
            if (target is Button || target is ImageButton ||
                target is SeekBar || target is EditText || target is Spinner ||
                target.tag == "playlist-drag-handle") return false
            target = target.parent as? View
        }
        return true
    }

    private fun deepestVisible(view: View, x: Int, y: Int): View? {
        if (view.visibility != View.VISIBLE) return null
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        if (x < point[0] || x >= point[0] + view.width ||
            y < point[1] || y >= point[1] + view.height) return null
        if (view is ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val hit = deepestVisible(view.getChildAt(index), x, y)
                if (hit != null) return hit
            }
        }
        return view
    }
}
