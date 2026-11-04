package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.player.PlaybackController

/**
 * Source-aware playback queue:
 * - All Tracks: BPM-sorted, read-only order
 * - User playlist: its saved order is shown here, editing happens in Playlists
 *
 * ListView retains recycled rows; no audio or disk I/O is performed here.
 */
class QueueScreen(
    context: Context,
    private val playback: PlaybackController,
    private val onChooseTrack: (Int) -> Unit
) : LinearLayout(context) {

    // Must be initialized BEFORE the adapter is assigned to ListView.
    private var tracks: List<Track> = emptyList()
    private var shownSource: String? = null
    private val description = context.textLine("No active queue", 13f).apply {
        maxLines = 2
    }
    private val currentButton = context.actionButton("Current") { jumpToCurrent() }.apply {
        minWidth = 0
        minimumWidth = 0
        textSize = 12f
    }
    private val adapter = QueueAdapter()
    private val list = ListView(context).apply {
        dividerHeight = context.dp(1)
        adapter = this@QueueScreen.adapter
        setOnItemClickListener { _, _, index, _ -> onChooseTrack(index) }
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(PlayerPalette.background)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(description, LayoutParams(0, -2, 1f))
        header.addView(currentButton, LayoutParams(context.dp(78), context.dp(42)))
        addView(header)
        addView(list, LayoutParams(-1, 0, 1f))
        refresh()
    }

    /** Preserve the visible anchor during edits; jump to current on source change. */
    fun refresh() {
        val source = playback.activePlaylistId ?: "all-tracks"
        val changedSource = shownSource != source
        val topUri = tracks.getOrNull(list.firstVisiblePosition)?.uri?.toString()
        val offset = list.getChildAt(0)?.top ?: 0
        shownSource = source
        tracks = playback.queueSnapshot()
        val editable = playback.activePlaylistId != null
        description.text = when {
            tracks.isEmpty() -> "Queue is empty. Choose a track in Library or Playlists."
            editable -> "${playback.queueSourceLabel} · ${tracks.size} tracks · tap to play"
            else -> "All Tracks · ${tracks.size} tracks · sorted by BPM"
        }
        currentButton.isEnabled = playback.queuePosition in tracks.indices
        adapter.notifyDataSetChanged()

        if (changedSource) {
            jumpToCurrent()
        } else if (topUri != null) {
            val newIndex = tracks.indexOfFirst { it.uri.toString() == topUri }
            if (newIndex >= 0 && newIndex != list.firstVisiblePosition) {
                list.post { list.setSelectionFromTop(newIndex, offset) }
            }
        }
    }

    private fun jumpToCurrent() {
        val current = playback.queuePosition
        if (current in tracks.indices) {
            list.post { list.setSelectionFromTop(current, context.dp(16)) }
        }
    }

    private inner class QueueAdapter : BaseAdapter() {
        override fun getCount(): Int = tracks.size
        override fun getItem(position: Int): Track = tracks[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val label = (convertView as? TextView) ?: TextView(context).apply {
                textSize = 15f
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = context.dp(52)
                setPadding(context.dp(10), context.dp(6), context.dp(10), context.dp(6))
                maxLines = 2
            }
            val item = getItem(position)
            val playing = position == playback.queuePosition
            label.text = "${if (playing) "▶ " else ""}${position + 1}.  ${item.name}"
            label.setTextColor(
                if (playing) Color.rgb(0, 118, 108) else Color.rgb(37, 40, 44)
            )
            return label
        }
    }
}
