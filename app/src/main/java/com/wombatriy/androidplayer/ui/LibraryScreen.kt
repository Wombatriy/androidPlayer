package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wombatriy.androidplayer.Track

/**
 * A long-lived tab backed by ListView's recycled rows.
 * Showing/hiding this tab never rebuilds rows or resets the scroll position.
 */
class LibraryScreen(
    context: Context,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit,
    private val onTrackSelected: (Track) -> Unit
) : LinearLayout(context) {
    private val adapter = TrackAdapter(context)
    private val list = ListView(context).apply {
        dividerHeight = context.dp(1)
        setBackgroundColor(Color.WHITE)
        isFastScrollEnabled = true
        setOnItemClickListener { _, _, position, _ -> onTrackSelected(this@LibraryScreen.adapter.getItem(position)) }
    }
    private val info = context.textLine("Choose a music folder to begin.", 13f)
    private var restoreIndex: Int? = null
    private var restoreOffset = 0

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.rgb(249, 250, 252))
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        addView(context.textLine("Music Library", 23f))

        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        actions.addView(context.actionButton("Choose folder", onChooseFolder),
            LayoutParams(0, -2, 1f))
        actions.addView(context.actionButton("Rescan", onRescan),
            LayoutParams(0, -2, 1f))
        addView(actions)
        addView(info)
        list.adapter = adapter
        addView(list, LayoutParams(-1, 0, 1f))
    }

    val trackCount: Int get() = adapter.count
    val firstVisibleIndex: Int get() = list.firstVisiblePosition
    val firstVisibleOffset: Int get() = list.getChildAt(0)?.top ?: 0

    /** Restore after an Activity recreation, once tracks are scanned again. */
    fun restorePosition(index: Int, offsetPx: Int) {
        restoreIndex = index.coerceAtLeast(0)
        restoreOffset = offsetPx
    }

    fun setLoading(loading: Boolean) {
        info.text = if (loading) "Scanning folder…" else {
            if (adapter.count == 0) "No audio files found. Choose a folder to begin."
            else "${adapter.count} tracks • tap to play"
        }
    }

    fun submitTracks(tracks: List<Track>) {
        // Keep the visible anchor across a rescan, even if the adapter changes.
        val index = restoreIndex ?: list.firstVisiblePosition
        val offset = if (restoreIndex != null) restoreOffset else firstVisibleOffset
        adapter.submit(tracks)
        val target = index.coerceIn(0, (tracks.size - 1).coerceAtLeast(0))
        if (tracks.isNotEmpty()) list.setSelectionFromTop(target, offset)
        restoreIndex = null
        setLoading(false)
    }

    private class TrackAdapter(private val context: Context) : BaseAdapter() {
        private var items: List<Track> = emptyList()
        fun submit(newItems: List<Track>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Track = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row: LinearLayout
            val holder: RowHolder
            if (convertView is LinearLayout) {
                row = convertView
                holder = row.tag as RowHolder
            } else {
                row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = context.dp(50)
                    setPadding(context.dp(8), context.dp(6), context.dp(8), context.dp(6))
                }
                val name = TextView(context).apply {
                    textSize = 15f
                    maxLines = 2
                    ellipsize = TextUtils.TruncateAt.END
                }
                val bpm = TextView(context).apply {
                    textSize = 12f
                    setTextColor(Color.rgb(16, 120, 105))
                    setPadding(context.dp(8), 0, 0, 0)
                }
                row.addView(name, LayoutParams(0, -2, 1f))
                row.addView(bpm, LayoutParams(-2, -2))
                holder = RowHolder(name, bpm)
                row.tag = holder
            }
            val track = getItem(position)
            holder.name.text = track.name
            val bpm = track.filenameBpm
            holder.bpm.visibility = if (bpm == null) GONE else VISIBLE
            holder.bpm.text = if (bpm != null) "$bpm BPM" else ""
            return row
        }

        private class RowHolder(val name: TextView, val bpm: TextView)
    }
}
