package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.text.InputType
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.wombatriy.androidplayer.bpm.BpmCoordinator
import com.wombatriy.androidplayer.bpm.BpmFormatting
import com.wombatriy.androidplayer.bpm.BpmSource
import kotlin.math.abs

/**
 * A small standalone BPM widget, independent of transport, A–B and audio state.
 * All callbacks operate on the current track through BpmCoordinator.
 */
class BpmPanel(
    context: Context,
    private val bpm: BpmCoordinator,
    private val speedProvider: () -> Float
) : LinearLayout(context) {
    private val headline = context.textLine("BPM: unknown", 16f).apply {
        setTextColor(Color.rgb(16, 120, 105))
    }
    private val detail = context.textLine("", 12f)
    private val analyzeButton = context.actionButton("Detect BPM") { bpm.analyze() }
    private val tapButton = context.actionButton("Tap BPM") { bpm.tap() }
    private val manualButton = context.actionButton("Enter BPM") { enterBpm() }
    private val corrections = listOf(
        "×0.5" to 0.5, "×1.5" to 1.5, "×2" to 2.0, "×4" to 4.0
    ).map { (caption, factor) ->
        context.actionButton(caption) { bpm.multiply(factor) }
    }
    private val resetButton = context.actionButton("Reset BPM") { bpm.reset() }

    init {
        orientation = VERTICAL
        addView(headline)
        val mainControls = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(analyzeButton, tapButton, manualButton).forEach { button ->
            compact(button)
            mainControls.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(mainControls)

        val multiplierControls = LinearLayout(context).apply { orientation = HORIZONTAL }
        corrections.forEach { button ->
            compact(button)
            multiplierControls.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(multiplierControls)
        compact(resetButton)
        addView(resetButton)
        addView(detail)
        refresh()
    }

    fun refresh() {
        val track = bpm.track
        visibility = if (track == null) GONE else VISIBLE
        if (track == null) return
        val result = bpm.resolved()
        val speed = speedProvider()
        val adjusted = if (result != null && abs(speed - 1.0f) > 0.001f) {
            " → ${BpmFormatting.adjusted(result.bpm, speed)} BPM"
        } else ""
        headline.text = if (result == null) "BPM: unknown" else {
            "BPM: ${BpmFormatting.bpm(result.bpm)} (${result.source.caption})$adjusted"
        }

        val alternative = if (result?.source == BpmSource.DETECTED) {
            result.alternative?.let { "Possible half/double tempo: ${BpmFormatting.bpm(it)} BPM." }
        } else null
        detail.text = listOfNotNull(bpm.message, alternative).joinToString("\n")
        detail.visibility = if (detail.text.isBlank()) GONE else VISIBLE

        analyzeButton.text = if (bpm.analyzing) "Analyzing…" else "Detect BPM"
        analyzeButton.isEnabled = !bpm.analyzing && track.filenameBpm == null
        tapButton.isEnabled = !bpm.analyzing
        manualButton.isEnabled = !bpm.analyzing
        corrections.forEach { it.isEnabled = result != null && !bpm.analyzing }
        resetButton.visibility = if (bpm.hasStoredValue() && !bpm.analyzing) VISIBLE else GONE
    }

    private fun compact(button: Button) {
        button.minWidth = 0
        button.minimumWidth = 0
        button.textSize = 11f
        button.setPadding(0, 0, 0, 0)
        button.isAllCaps = false
    }

    private fun enterBpm() {
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "e.g. 128 or 127.5"
        }
        val pad = context.dp(18)
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(pad, 0, pad, 0)
            addView(field)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Set original BPM")
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            fun save() {
                val value = field.text.toString().replace(',', '.').toDoubleOrNull()
                if (value == null || value !in 20.0..800.0 || !value.isFinite()) {
                    field.error = "Enter BPM between 20 and 800"
                } else {
                    bpm.setManual(value)
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            field.setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_DONE) { save(); true } else false
            }
            field.requestFocus()
        }
        dialog.show()
    }
}
