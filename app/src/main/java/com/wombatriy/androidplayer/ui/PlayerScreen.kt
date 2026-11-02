package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.wombatriy.androidplayer.player.PlaybackController
import java.util.Locale
import kotlin.math.roundToInt

/** Retained view: never recreate seekbars or buttons on playback callbacks. */
class PlayerScreen(context: Context, private val playback: PlaybackController) : ScrollView(context) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(24))
    }
    private val nameLabel = context.textLine("Choose a track in Library", 18f)
    private val bpmLabel = context.textLine()
    private val errorLabel = context.textLine()
    private val positionLabel = context.textLine("00:00 / 00:00")
    private val positionBar = SeekBar(context)
    private val playButton = context.actionButton("Play") { playback.togglePlayPause() }
    private val speedLabel = context.textLine("1.00×")
    private val speedBar = SeekBar(context)
    private val loopLabel = context.textLine()
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var isScrubbing = false

    init {
        isFillViewport = true
        setBackgroundColor(Color.rgb(249, 250, 252))
        addView(column)

        column.addView(context.textLine("Player", 23f))
        column.addView(nameLabel)
        column.addView(bpmLabel)
        column.addView(errorLabel)
        column.addView(controls)

        controls.addView(positionLabel)
        controls.addView(positionBar)
        positionBar.max = 1000
        positionBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) { isScrubbing = true }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val at = (playback.duration * progress.toLong()) / 1000L
                    positionLabel.text = "${format(at)} / ${format(playback.duration)}"
                }
            }
            override fun onStopTrackingTouch(bar: SeekBar) {
                val target = (playback.duration * bar.progress.toLong()) / 1000L
                isScrubbing = false
                playback.seekTo(target)
                updatePosition(playback.currentPosition, playback.duration)
            }
        })

        val seekControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        seekControls.addView(context.actionButton("−10s") { playback.skipBy(-10_000L) },
            LinearLayout.LayoutParams(0, -2, 1f))
        seekControls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        seekControls.addView(context.actionButton("+10s") { playback.skipBy(10_000L) },
            LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(seekControls)

        controls.addView(context.textLine("Speed (pitch preserved)"))
        controls.addView(speedLabel)
        speedBar.max = 125 // 0 -> 0.25×, 125 -> 1.50×
        speedBar.progress = ((playback.speed - 0.25f) * 100).roundToInt().coerceIn(0, 125)
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val newSpeed = (25 + progress) / 100f
                    playback.setSpeed(newSpeed)
                    updateSpeedLabel()
                }
            }
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        controls.addView(speedBar)
        controls.addView(SpeedRulerView(context), LinearLayout.LayoutParams(-1, context.dp(58)))
        controls.addView(context.actionButton("Reset speed to 1.00×") {
            playback.setSpeed(1f)
            speedBar.progress = 75
            updateSpeedLabel()
        })

        controls.addView(loopLabel)
        val loopControls = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        loopControls.addView(context.actionButton("Set A") { playback.setA() },
            LinearLayout.LayoutParams(0, -2, 1f))
        loopControls.addView(context.actionButton("Set B") { playback.setB() },
            LinearLayout.LayoutParams(0, -2, 1f))
        loopControls.addView(context.actionButton("Clear A–B") { playback.clearLoop() },
            LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(loopControls)

        refreshTrack()
    }

    fun refreshTrack() {
        val track = playback.currentTrack
        nameLabel.text = track?.name ?: "Choose a track in Library"
        bpmLabel.text = track?.filenameBpm?.let { "Filename BPM: $it" } ?: ""
        bpmLabel.visibility = if (track?.filenameBpm == null) View.GONE else View.VISIBLE
        errorLabel.visibility = View.GONE
        controls.visibility = if (track == null) View.GONE else View.VISIBLE
        updatePosition(playback.currentPosition, playback.duration)
        updatePlaybackState()
        updateLoop()
        updateSpeedLabel()
    }

    fun showPlaybackError(message: String) {
        errorLabel.text = "Playback error: $message"
        errorLabel.visibility = View.VISIBLE
    }

    /** Called from the playback ticker without replacing any controls. */
    fun updatePosition(positionMs: Long, durationMs: Long) {
        if (isScrubbing) return
        positionBar.progress = if (durationMs > 0) {
            ((positionMs.coerceAtLeast(0L).toDouble() / durationMs) * 1000)
                .toInt().coerceIn(0, 1000)
        } else 0
        positionLabel.text = "${format(positionMs)} / ${format(durationMs)}"
    }

    fun updatePlaybackState() {
        // playWhenReady does not flicker as ExoPlayer re-buffers during a seek.
        val expected = if (playback.showsPause) "Pause" else "Play"
        if (playButton.text != expected) playButton.text = expected
    }

    fun updateLoop() {
        val a = playback.pointA
        val b = playback.pointB
        val state = when {
            a == null || b == null -> "Loop inactive"
            b <= a -> "Set B after A to enable looping"
            else -> "A–B loop active"
        }
        loopLabel.text = "A = ${a?.let(::format) ?: "unset"}    B = ${b?.let(::format) ?: "unset"}\n$state"
    }

    private fun updateSpeedLabel() {
        speedLabel.text = String.format(Locale.US, "%.2f×", playback.speed)
    }

    private fun format(ms: Long): String {
        val sec = ms.coerceAtLeast(0L) / 1000L
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
}
