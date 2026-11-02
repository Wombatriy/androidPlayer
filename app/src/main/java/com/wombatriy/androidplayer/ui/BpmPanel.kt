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
import com.wombatriy.androidplayer.bpm.BpmCoordinator
import com.wombatriy.androidplayer.bpm.BpmFormatting
import kotlin.math.abs

/**
 * Full BPM adjustment controls, displayed only inside the BPM dialog.
 * The Player keeps just a compact BPM summary and an 'Adjustments' button.
 */
class BpmPanel(
    context: Context,
    private val bpm: BpmCoordinator,
    private val speedProvider: () -> Float
) : LinearLayout(context) {
    private val active = context.textLine("", 16f).apply {
        setTextColor(Color.rgb(16, 120, 105))
    }
    private val filename = context.textLine("", 13f)
    private val detected = context.textLine("", 13f)
    private val alternate = context.textLine("½: —     ×2: —", 13f)
    private val manual = context.textLine("", 13f)
    // Fixed height: updates cannot shift Tap BPM or other dialog buttons.
    private val detail = context.textLine("", 12f).apply {
        minHeight = context.dp(48)
        maxLines = 2
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val analyzeButton = context.actionButton("Detect again") { bpm.analyze(force = true) }
    private val tapButton = context.actionButton("Tap BPM") { bpm.tap() }
    private val manualButton = context.actionButton("Enter BPM") { enterBpm() }
    private val multipliers = listOf(
        "×0.5" to 0.5, "×1.5" to 1.5, "×2" to 2.0, "×4" to 4.0
    ).map { (label, factor) ->
        context.actionButton(label) { bpm.multiply(factor) }
    }
    private val resetButton = context.actionButton("Clear manual override") { bpm.reset() }

    init {
        orientation = VERTICAL
        setPadding(context.dp(14), context.dp(8), context.dp(14), context.dp(10))
        addView(active)
        addView(filename)
        addView(detected)
        addView(alternate)
        addView(manual)

        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(analyzeButton, tapButton, manualButton).forEach { button ->
            compact(button)
            actions.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(actions)

        val correctionRow = LinearLayout(context).apply { orientation = HORIZONTAL }
        multipliers.forEach { button ->
            compact(button)
            correctionRow.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(correctionRow)
        compact(resetButton)
        addView(resetButton)
        addView(detail)
        refresh()
    }

    fun refresh() {
        val chosen = bpm.resolved()
        val speed = speedProvider()
        val adjusted = if (chosen != null && abs(speed - 1f) > 0.001f) {
            " → ${BpmFormatting.adjusted(chosen.bpm, speed)} BPM"
        } else ""
        active.text = if (chosen == null) "Active BPM: unknown" else
            "Active BPM: ${BpmFormatting.bpm(chosen.bpm)} (${chosen.source.caption})$adjusted"
        filename.text = "Filename: ${bpm.filenameBpm()?.toString() ?: "—"}"
        val estimate = bpm.detected()
        detected.text = when {
            bpm.analyzing -> "Detected: analyzing…"
            estimate == null -> "Detected: —"
            else -> "Detected: ${BpmFormatting.bpm(estimate.bpm)} BPM"
        }
        alternate.text = if (estimate == null) {
            "½: —     ×2: —"
        } else {
            "½: ${BpmFormatting.bpm(estimate.bpm / 2)}     ×2: ${BpmFormatting.bpm(estimate.bpm * 2)}"
        }
        manual.text = "Manual: ${bpm.manual()?.let { BpmFormatting.bpm(it.bpm) } ?: "—"}"
        // Reserve the status area even when empty, preventing resize on taps.
        detail.text = bpm.message?.takeIf {
            it.startsWith("Tap ") || it.startsWith("No taps") ||
                it.startsWith("Detection unavailable") || it.startsWith("No clear beat")
        } ?: ""
        analyzeButton.isEnabled = !bpm.analyzing
        analyzeButton.text = if (bpm.analyzing) "Analyzing…" else "Detect again"
        multipliers.forEach { it.isEnabled = chosen != null }
        // Keep its space reserved: visibility changes used to move the Tap button.
        resetButton.isEnabled = bpm.hasManual()
    }

    private fun compact(button: Button) {
        button.minWidth = 0
        button.minimumWidth = 0
        button.textSize = 11f
        button.setPadding(0, 0, 0, 0)
    }

    private fun enterBpm() {
        val field = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "e.g. 128 or 127.5"
            isSingleLine = true
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
                if (value == null || !value.isFinite() || value !in 20.0..800.0) {
                    field.error = "Enter BPM between 20 and 800"
                } else {
                    bpm.setManual(value)
                    dialog.dismiss()
                }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { save() }
            field.setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { save(); true } else false
            }
            field.requestFocus()
        }
        dialog.show()
    }
}
