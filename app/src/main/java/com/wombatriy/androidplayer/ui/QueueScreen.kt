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
 * Live view of the actual playback queue, whether sourced from the virtual
 * All Tracks list or a saved user playlist. No implicit sorting or reshuffling.
 */
class QueueScreen(
    context: Context,
    private val playback: PlaybackController,
    private val onChooseTrack: (Int) -> Unit
) :
    LinearLayout(context) {

    private val description = context.textLine("No active queue", 15f)
    private val adapter = QueueAdapter()
    private val list = ListView(context).apply {
        dividerHeight = context.dp(1)
        adapter = this@QueueScreen.adapter
        setOnItemClickListener { _, _, index, _ -> onChooseTrack(index) }
    }
    private var tracks: List<Track> = emptyList()

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(Color.rgb(249, 250, 252))
        addView(description)
        addView(list, LayoutParams(-1, 0, 1f))
        refresh()
    }

    fun refresh() {
        tracks = playback.queueSnapshot()
        description.text = when {
            tracks.isEmpty() -> "Queue is empty. Choose a track in Library or Playlists."
            else -> "${playback.queueSourceLabel} · ${tracks.size} tracks · tap to play"
        }
        adapter.notifyDataSetChanged()
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
            label.setTextColor(if (playing) Color.rgb(0, 118, 108)
                else Color.rgb(37, 40, 44))
            return label
        }
    }
}
