package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Paint
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.wombatriy.androidplayer.bpm.BpmCoordinator
import com.wombatriy.androidplayer.player.EndMode
import com.wombatriy.androidplayer.player.TempoMath
import com.wombatriy.androidplayer.player.PlaybackController
import java.util.Locale
import kotlin.math.roundToInt

/** Retained view: never recreate seekbars or buttons on playback callbacks. */
class PlayerScreen(context: Context, private val playback: PlaybackController, private val bpmCoordinator: BpmCoordinator) : ScrollView(context) {
    private val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(24))
    }
    private val nameLabel = context.textLine("Choose a track in Library", 18f)
    private val bpmSummary = context.textLine("BPM: —", 14f)
    private var bpmDialog: AlertDialog? = null
    private var bpmPanel: BpmPanel? = null
    private val bpmButton = context.actionButton("BPM adjustments") { showBpmDialog() }
    private val queueLabel = context.textLine()
    private val errorLabel = context.textLine()
    private val positionLabel = context.textLine("00:00 / 00:00")
    private val positionBar = SeekBar(context)
    private val playButton = context.actionButton("Play") { playback.togglePlayPause() }
    private val previousButton = context.actionButton("Previous") { playback.previous() }
    private val nextButton = context.actionButton("Next") { playback.next() }
    private val modeButton = context.actionButton("⏭") { cycleEndMode() }
    private val speedLabel = context.textLine("1.00×", 24f).apply {
        textAlignment = View.TEXT_ALIGNMENT_CENTER
        setTextColor(Color.rgb(0, 113, 112))
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        isFocusable = true
        setOnClickListener { showManualSpeedInput() }
    }
    private val speedBar = SeekBar(context)
    private val loopLabel = context.textLine()
    private val controls = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private var isScrubbing = false

    init {
        isFillViewport = true
        setBackgroundColor(Color.rgb(249, 250, 252))
        addView(column)

        column.addView(nameLabel)
        column.addView(bpmSummary)
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

        // Five equally weighted controls remain in one row, even on narrow screens.
        val transport = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addTransport(button: Button) {
            button.minWidth = 0
            button.minimumWidth = 0
            button.setPadding(0, 0, 0, 0)
            button.textSize = 11f
            transport.addView(button, LinearLayout.LayoutParams(0, -2, 1f))
        }
        previousButton.text = "Prev"
        nextButton.text = "Next"
        addTransport(previousButton)
        addTransport(context.actionButton("−10s") { playback.skipBy(-10_000L) })
        addTransport(playButton)
        addTransport(context.actionButton("+10s") { playback.skipBy(10_000L) })
        addTransport(nextButton)
        controls.addView(transport)

        // Center the speed value while keeping Reset as a small right-side action.
        val speedHeading = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        speedHeading.addView(View(context), LinearLayout.LayoutParams(context.dp(44), 1))
        speedHeading.addView(speedLabel, LinearLayout.LayoutParams(0, -2, 1f))
        speedHeading.addView(context.actionButton("↺") {
            setSpeedPercent(TempoMath.DEFAULT_PERCENT)
        }.apply {
            contentDescription = "Reset speed to 1.00×"
            textSize = 20f
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(context.dp(44), context.dp(40)))
        controls.addView(speedHeading)

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
        controls.addView(SpeedRulerView(context), LinearLayout.LayoutParams(-1, context.dp(36)))

        // End-of-track mode and A–B loop share one compact row.
        val loopControls = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun addSmallControl(button: Button) {
            button.minWidth = 0
            button.minimumWidth = 0
            button.textSize = 17f
            button.setPadding(0, 0, 0, 0)
            loopControls.addView(button, LinearLayout.LayoutParams(0, context.dp(44), 1f))
        }
        addSmallControl(modeButton)
        addSmallControl(context.actionButton("A") { playback.setA() }.apply {
            contentDescription = "Set loop point A"
        })
        addSmallControl(context.actionButton("B") { playback.setB() }.apply {
            contentDescription = "Set loop point B"
        })
        addSmallControl(context.actionButton("🗑") { playback.clearLoop() }.apply {
            contentDescription = "Clear A–B loop"
        })
        controls.addView(loopControls)
        controls.addView(loopLabel)
        bpmButton.isAllCaps = false
        controls.addView(bpmButton)
        updatePlaybackState()

        refreshTrack()
    }

    fun refreshTrack() {
        bpmDialog?.dismiss()
        val track = playback.currentTrack
        nameLabel.text = track?.name ?: "Choose a track in Library"
        refreshBpm()
        queueLabel.text = if (track != null) "Track ${playback.queuePosition + 1} of ${playback.queueSize}" else ""
        queueLabel.visibility = if (track == null) View.GONE else View.VISIBLE
        errorLabel.visibility = View.GONE
        controls.visibility = if (track == null) View.GONE else View.VISIBLE
        updatePosition(playback.currentPosition, playback.duration)
        updatePlaybackState()
        updateLoop()
        refreshSpeed()
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
        modeButton.text = when (playback.endMode) {
            EndMode.STOP -> "⏹"
            EndMode.NEXT -> "⏭"
            EndMode.REPEAT -> "🔁"
        }
        modeButton.contentDescription = "After track: ${playback.endMode.caption}. Tap to change."
        modeButton.tooltipText = "After track: ${playback.endMode.caption}"
    }

    fun updateLoop() {
        val a = playback.pointA
        val b = playback.pointB
        loopLabel.visibility = if (a == null && b == null) View.GONE else View.VISIBLE
        loopLabel.text = buildString {
            if (a != null) append("A ${format(a)}")
            if (b != null) {
                if (isNotEmpty()) append("    ")
                append("B ${format(b)}")
            }
            if (a != null && b != null && b <= a) append("  (B ≤ A)")
        }
    }

    /** Called after slider, keyboard and Media3 playback-parameter changes. */
    fun refreshSpeed() {
        val percent = TempoMath.toPercent(playback.speed)
        val newProgress = percent - TempoMath.MIN_PERCENT
        if (speedBar.progress != newProgress) speedBar.progress = newProgress
        speedLabel.text = String.format(Locale.US, "%.2f×", playback.speed)
        speedLabel.contentDescription = "Playback speed $percent percent. Tap to enter manually."
        refreshBpm()
    }

    fun refreshBpm() {
        val result = bpmCoordinator.resolved()
        val adjusted = if (result != null && kotlin.math.abs(playback.speed - 1f) > 0.001f) {
            " → ${com.wombatriy.androidplayer.bpm.BpmFormatting.adjusted(result.bpm, playback.speed)}"
        } else ""
        bpmSummary.text = if (result == null) {
            if (bpmCoordinator.analyzing) "BPM: detecting…" else "BPM: —"
        } else {
            "BPM ${com.wombatriy.androidplayer.bpm.BpmFormatting.bpm(result.bpm)}" +
                " (${result.source.caption})$adjusted"
        }
        bpmPanel?.refresh()
    }

    private fun cycleEndMode() {
        val modes = EndMode.entries
        val next = modes[(modes.indexOf(playback.endMode) + 1) % modes.size]
        playback.selectEndMode(next)
    }

    private fun showBpmDialog() {
        if (playback.currentTrack == null || bpmDialog != null) return
        val content = BpmPanel(context, bpmCoordinator) { playback.speed }
        val scroll = ScrollView(context).apply {
            isFillViewport = true
            addView(content)
        }
        bpmPanel = content
        val dialog = AlertDialog.Builder(context)
            .setTitle("BPM adjustments")
            .setView(scroll)
            .setPositiveButton("Close", null)
            .create()
        dialog.setOnDismissListener {
            bpmCoordinator.stopTapSession()
            bpmDialog = null
            bpmPanel = null
        }
        bpmDialog = dialog
        bpmCoordinator.beginTapSession()
        dialog.show()
    }

    private fun setSpeedPercent(percent: Int) {
        playback.setSpeed(TempoMath.toSpeed(percent))
        refreshSpeed()
    }

    private fun showManualSpeedInput() {
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            // Deliberately blank: the user can enter a new speed immediately.
            setText("")
        }
        val margin = context.dp(20)
        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(margin, 0, margin, 0)
            addView(field, LinearLayout.LayoutParams(-1, -2))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Playback speed (%)")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", null)
            .create()
        dialog.setOnShowListener {
            fun applyInput() {
                // 24 -> 25, 160 -> 150; empty input simply keeps the current speed.
                TempoMath.parseClampedPercent(field.text.toString())?.let(::setSpeedPercent)
                dialog.dismiss()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { applyInput() }
            field.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    applyInput()
                    true
                } else false
            }
            field.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            field.post {
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private fun format(ms: Long): String {
        val sec = ms.coerceAtLeast(0L) / 1000L
        return String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)
    }
}
