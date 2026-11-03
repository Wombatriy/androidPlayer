package com.wombatriy.androidplayer.ui

import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
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
 * - User playlist: long-press the right-hand handle and drag onto another row
 *
 * ListView retains recycled rows; no audio or disk I/O is performed here.
 */
class QueueScreen(
    context: Context,
    private val playback: PlaybackController,
    private val onChooseTrack: (Int) -> Unit,
    private val onReorder: (String, String, Boolean) -> Boolean
) : LinearLayout(context) {

    private data class DragToken(val playlistId: String, val uri: String)
    private class Holder(val name: TextView, val handle: TextView)

    // Must be initialized BEFORE the adapter is assigned to ListView.
    private var tracks: List<Track> = emptyList()
    private var shownSource: String? = null
    private var lastScrollAt = 0L
    private var hoverPosition = -1
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
        setOnDragListener { _, event -> handleDrag(event) }
    }

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(Color.rgb(249, 250, 252))
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
            editable -> "${playback.queueSourceLabel} · ${tracks.size} tracks · hold ≡ to drag"
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

    private fun highlightDropRow(index: Int) {
        if (index == hoverPosition) return
        hoverPosition = index
        // Notify only when crossing rows; recycled ListView rows stay cheap.
        adapter.notifyDataSetChanged()
    }

    private fun handleDrag(event: DragEvent): Boolean {
        val token = event.localState as? DragToken
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                highlightDropRow(-1)
                return token != null && token.playlistId == playback.activePlaylistId
            }

            DragEvent.ACTION_DRAG_LOCATION -> {
                if (token == null || token.playlistId != playback.activePlaylistId) return false
                highlightDropRow(list.pointToPosition(event.x.toInt(), event.y.toInt()))
                val now = SystemClock.uptimeMillis()
                if (now - lastScrollAt >= 160L) {
                    val edge = context.dp(56)
                    val direction = when {
                        event.y < edge -> -1
                        event.y > list.height - edge -> 1
                        else -> 0
                    }
                    if (direction != 0) {
                        list.smoothScrollBy(direction * context.dp(66), 140)
                        lastScrollAt = now
                    }
                }
                return true
            }

            DragEvent.ACTION_DROP -> {
                highlightDropRow(-1)
                if (token == null || token.playlistId != playback.activePlaylistId) return false
                val from = tracks.indexOfFirst { it.uri.toString() == token.uri }
                val to = list.pointToPosition(event.x.toInt(), event.y.toInt())
                if (from < 0 || to !in tracks.indices || from == to) return false
                // Moving downward inserts after the target; upward before it.
                return onReorder(
                    token.uri, tracks[to].uri.toString(), from < to
                )
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                highlightDropRow(-1)
                return true
            }
        }
        return true
    }

    private inner class QueueAdapter : BaseAdapter() {
        override fun getCount(): Int = tracks.size
        override fun getItem(position: Int): Track = tracks[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row: LinearLayout
            val holder: Holder
            if (convertView is LinearLayout && convertView.tag is Holder) {
                row = convertView
                holder = row.tag as Holder
            } else {
                row = LinearLayout(context).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = context.dp(56)
                }
                val title = TextView(context).apply {
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    maxLines = 2
                    setPadding(context.dp(10), context.dp(6), context.dp(4), context.dp(6))
                }
                val dragHandle = TextView(context).apply {
                    text = "≡"
                    textSize = 28f
                    gravity = Gravity.CENTER
                    contentDescription = "Long press and drag to reorder"
                    tag = "queue-drag-handle"
                    isLongClickable = true
                }
                row.addView(title, LayoutParams(0, -2, 1f))
                row.addView(dragHandle, LayoutParams(context.dp(52), context.dp(54)))
                holder = Holder(title, dragHandle)
                row.tag = holder
            }
            val track = getItem(position)
            row.setBackgroundColor(
                if (position == hoverPosition) Color.rgb(221, 246, 241)
                else Color.TRANSPARENT
            )
            val playing = position == playback.queuePosition
            holder.name.text = "${if (playing) "▶ " else ""}${position + 1}.  ${track.name}"
            holder.name.setTextColor(
                if (playing) Color.rgb(0, 118, 108) else Color.rgb(37, 40, 44)
            )
            val editable = playback.activePlaylistId != null
            holder.handle.visibility = if (editable) View.VISIBLE else View.GONE
            holder.handle.setOnLongClickListener {
                val id = playback.activePlaylistId ?: return@setOnLongClickListener false
                val payload = DragToken(id, track.uri.toString())
                val clip = ClipData.newPlainText("queue", "")
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                it.startDragAndDrop(clip, View.DragShadowBuilder(row), payload, 0)
            }
            return row
        }
    }
}
