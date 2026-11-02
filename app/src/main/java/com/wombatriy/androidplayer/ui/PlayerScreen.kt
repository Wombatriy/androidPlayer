package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.text.InputFilter
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.wombatriy.androidplayer.player.AudioBackend
import com.wombatriy.androidplayer.player.EndMode
import com.wombatriy.androidplayer.player.TempoMath
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
    private val queueLabel = context.textLine()
    private val errorLabel = context.textLine()
    private val positionLabel = context.textLine("00:00 / 00:00")
    private val positionBar = SeekBar(context)
    private val playButton = context.actionButton("Play") { playback.togglePlayPause() }
    private val previousButton = context.actionButton("Previous") { playback.previous() }
    private val nextButton = context.actionButton("Next") { playback.next() }
    private val modeButtons = mutableMapOf<EndMode, Button>()
    private val speedLabel = context.textLine("1.00×", 24f).apply {
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTextColor(Color.rgb(0, 113, 112))
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        isFocusable = true
        setOnClickListener { showManualSpeedInput() }
    }
    private val audioBackendButton = context.actionButton("Audio: Sonic") { showAudioBackendPicker() }
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
        column.addView(queueLabel)
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
        seekControls.addView(previousButton, LinearLayout.LayoutParams(0, -2, 1f))
        seekControls.addView(playButton, LinearLayout.LayoutParams(0, -2, 1f))
        seekControls.addView(nextButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(seekControls)

        val jumps = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        jumps.addView(context.actionButton("−10s") { playback.skipBy(-10_000L) },
            LinearLayout.LayoutParams(0, -2, 1f))
        jumps.addView(context.actionButton("+10s") { playback.skipBy(10_000L) },
            LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(jumps)

        // Tap the centered speed value for precise input in integer percent.
        controls.addView(speedLabel)
        speedBar.max = TempoMath.MAX_PERCENT - TempoMath.MIN_PERCENT
        speedBar.progress = TempoMath.toPercent(playback.speed) - TempoMath.MIN_PERCENT
        speedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) = Unit
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) setSpeedPercent(TempoMath.MIN_PERCENT + progress)
            }
            override fun onStopTrackingTouch(bar: SeekBar) = Unit
        })
        controls.addView(speedBar)
        controls.addView(SpeedRulerView(context), LinearLayout.LayoutParams(-1, context.dp(58)))
        controls.addView(context.actionButton("Reset speed to 1.00×") {
            setSpeedPercent(TempoMath.DEFAULT_PERCENT)
        })

        controls.addView(audioBackendButton)
        controls.addView(context.textLine(
            "Compare Sonic and the Android mixer at 0.80×–1.10×. Switching requires restarting the app.",
            12f
        ))
        controls.addView(context.textLine("At track end (A–B loop takes priority)"))
        val modes = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        EndMode.entries.forEach { mode ->
            val button = context.actionButton(mode.caption) { playback.selectEndMode(mode) }
            modeButtons[mode] = button
            modes.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        controls.addView(modes)

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
        updateBpmLabel()
        queueLabel.text = if (track != null) "Track ${playback.queuePosition + 1} of ${playback.queueSize}" else ""
        queueLabel.visibility = if (track == null) View.GONE else View.VISIBLE
        errorLabel.visibility = View.GONE
        controls.visibility = if (track == null) View.GONE else View.VISIBLE
        updatePosition(playback.currentPosition, playback.duration)
        updatePlaybackState()
        updateLoop()
        refreshSpeed()
        updateBackendLabel()
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
        previousButton.isEnabled = playback.hasPrevious
        nextButton.isEnabled = playback.hasNext
        for ((mode, button) in modeButtons) {
            val selected = mode == playback.endMode
            button.setTextColor(if (selected) Color.rgb(0, 110, 110) else Color.rgb(45, 50, 55))
            button.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
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

    /** Called after slider, keyboard and Media3 playback-parameter changes. */
    fun refreshSpeed() {
        val percent = TempoMath.toPercent(playback.speed)
        val newProgress = percent - TempoMath.MIN_PERCENT
        if (speedBar.progress != newProgress) speedBar.progress = newProgress
        speedLabel.text = String.format(Locale.US, "%.2f×", playback.speed)
        speedLabel.contentDescription = "Playback speed $percent percent. Tap to enter manually."
        updateBpmLabel()
    }

    private fun updateBpmLabel() {
        val originalBpm = playback.currentTrack?.filenameBpm
        val percent = TempoMath.toPercent(playback.speed)
        bpmLabel.text = when {
            originalBpm == null -> ""
            percent == TempoMath.DEFAULT_PERCENT -> "Filename BPM: $originalBpm"
            else -> "Filename BPM: $originalBpm → ${TempoMath.effectiveBpm(originalBpm, percent)} BPM"
        }
        bpmLabel.visibility = if (originalBpm == null) View.GONE else View.VISIBLE
    }

    private fun setSpeedPercent(percent: Int) {
        playback.setSpeed(TempoMath.toSpeed(percent))
        refreshSpeed()
    }

    private fun showManualSpeedInput() {
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(InputFilter.LengthFilter(3))
            isSingleLine = true
            setText(TempoMath.toPercent(playback.speed).toString())
            selectAll()
        }
        val margin = context.dp(20)
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, 0, margin, 0)
            addView(field, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Playback speed (%)")
            .setMessage("Enter a whole number from 25 to 150. 100 means 1.00×.")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()
        dialog.setOnShowListener {
            val applyInput = {
                val percent = TempoMath.parsePercent(field.text.toString())
                if (percent == null) {
                    field.error = "Enter an integer from 25 to 150"
                } else {
                    setSpeedPercent(percent)
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { applyInput() }
            field.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    applyInput()
                    true
                } else false
            }
        }
        dialog.show()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    }

    private fun showAudioBackendPicker() {
        val choices = AudioBackend.entries.toTypedArray()
        val preferred = playback.preferredAudioBackend
        AlertDialog.Builder(context)
            .setTitle("Audio processing (for listening comparisons)")
            .setSingleChoiceItems(choices.map { it.label }.toTypedArray(), choices.indexOf(preferred)) { dialog, which ->
                playback.chooseAudioBackendForNextLaunch(choices[which])
                updateBackendLabel()
                dialog.dismiss()
                Toast.makeText(context, "Restart the app to apply the audio engine change", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateBackendLabel() {
        val active = playback.audioBackend
        val requested = playback.preferredAudioBackend
        audioBackendButton.text = if (active == requested) {
            "Audio: ${active.label}"
        } else {
            "Audio: ${active.label} (switch pending)"
        }
    }

    private fun format(ms: Long): String {
        val sec = ms.coerceAtLeast(0L) / 1000L
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
}
