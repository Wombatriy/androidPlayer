package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.library.LibrarySearchPolicy

/** Temporary search UI. Selecting a result only navigates Library, never plays it. */
internal class LibraryTrackSearch(
    private val context: Context,
    private val entries: List<LibrarySearchPolicy.Entry<Track>>,
    private val onLocate: (Track) -> Unit
) {
    fun show() {
        val filter = EditText(context).apply {
            hint = "Track name"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(6))
        }
        val adapter = ResultsAdapter()
        val list = ListView(context).apply {
            isFastScrollEnabled = true
            this.adapter = adapter
            dividerHeight = context.dp(1)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val side = context.dp(12)
            setPadding(side, context.dp(8), side, 0)
            addView(filter, LinearLayout.LayoutParams(-1, -2))
            addView(list, LinearLayout.LayoutParams(-1, context.dp(430)))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Find in Library")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()

        adapter.submit(LibrarySearchPolicy.results(entries, ""))
        filter.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.submit(LibrarySearchPolicy.results(entries, s?.toString().orEmpty()))
                list.setSelection(0)
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        list.setOnItemClickListener { _, _, index, _ ->
            val selected = adapter.getItem(index).item
            dialog.dismiss()
            onLocate(selected)
        }
        dialog.setOnShowListener {
            filter.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            filter.post {
                (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .showSoftInput(filter, InputMethodManager.SHOW_IMPLICIT)
            }
        }
        dialog.show()
    }

    private inner class ResultsAdapter : BaseAdapter() {
        private var rows: List<LibrarySearchPolicy.Entry<Track>> = emptyList()
        fun submit(value: List<LibrarySearchPolicy.Entry<Track>>) {
            rows = value
            notifyDataSetChanged()
        }
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): LibrarySearchPolicy.Entry<Track> = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, reused: View?, parent: ViewGroup): View {
            val label = reused as? TextView ?: TextView(context).apply {
                textSize = 15f
                maxLines = 2
                setPadding(context.dp(10), context.dp(12), context.dp(10), context.dp(12))
                minHeight = context.dp(48)
            }
            val row = getItem(position)
            label.text = row.title
            return label
        }
    }
}
