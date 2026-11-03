package com.wombatriy.androidplayer.ui

import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.playlists.PlaylistStore
import com.wombatriy.androidplayer.playlists.UserPlaylist

/**
 * Persistent playlist editor. Rows are recycled; the order on screen is the
 * exact stored URI order, never filename or BPM sorting.
 */
class PlaylistsScreen(
    context: Context,
    private val store: PlaylistStore,
    private val resolve: (String) -> Track?,
    private val onPlayTrack: (Track, UserPlaylist, List<Track>) -> Unit,
    private val onCatalogChanged: (String?) -> Unit
) : LinearLayout(context) {
    private data class Row(val uri: String, val track: Track?)
    private data class DragToken(val playlistId: String, val uri: String)
    private class RowHolder(val title: TextView, val handle: TextView)

    private val chooser = Spinner(context)
    private val chooserAdapter = ArrayAdapter<String>(
        context, android.R.layout.simple_spinner_item
    ).apply {
        setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
    }
    private val rows = ArrayList<Row>()
    private var hoveredRow = -1
    private var lastAutoScrollAt = 0L
    private val listAdapter = PlaylistRowsAdapter()
    private val tracksList = ListView(context).apply {
        dividerHeight = context.dp(1)
        adapter = listAdapter
        setOnDragListener { _, event -> handleDrag(event) }
        setOnItemClickListener { _, _, index, _ -> play(index) }
        setOnItemLongClickListener { _, _, index, _ ->
            showTrackActions(index)
            true
        }
    }
    private val help = context.textLine("Create a playlist to get started.", 12f)
    private val renameButton = context.actionButton("Rename") { renameSelected() }
    private val deleteButton = context.actionButton("Delete") { deleteSelected() }
    private var selectedId: String? = null
    private var refreshing = false

    init {
        orientation = VERTICAL
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))
        setBackgroundColor(Color.rgb(249, 250, 252))

        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        listOf(
            context.actionButton("+ New") { showNameDialog("New playlist", "") { name ->
                val added = store.create(name)
                selectedId = added.id
                changed(added.id)
            } },
            renameButton,
            deleteButton
        ).forEach { button ->
            button.minWidth = 0
            button.minimumWidth = 0
            button.textSize = 12f
            actions.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(actions)
        chooser.adapter = chooserAdapter
        addView(chooser, LayoutParams(-1, -2))
        addView(help)
        addView(tracksList, LayoutParams(-1, 0, 1f))

        chooser.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(
                parent: AdapterView<*>?, view: View?, position: Int, id: Long
            ) {
                if (refreshing) return
                val next = store.playlists.getOrNull(position)?.id ?: return
                if (selectedId != next) {
                    selectedId = next
                    updateRows()
                }
            }
        }
        refresh()
    }

    val activePlaylist: UserPlaylist? get() =
        selectedId?.let(store::find)

    fun refresh() {
        val catalog = store.playlists
        if (catalog.none { it.id == selectedId }) selectedId = catalog.firstOrNull()?.id
        refreshing = true
        chooserAdapter.clear()
        chooserAdapter.addAll(catalog.map { "${it.name} (${it.trackUris.size})" })
        chooserAdapter.notifyDataSetChanged()
        val index = catalog.indexOfFirst { it.id == selectedId }
        if (index >= 0) chooser.setSelection(index, false)
        chooser.isEnabled = catalog.isNotEmpty()
        renameButton.isEnabled = catalog.isNotEmpty()
        deleteButton.isEnabled = catalog.isNotEmpty()
        refreshing = false
        updateRows()
    }

    /**
     * From Library's long-press context menu. Allow creating the first playlist
     * right here rather than bouncing between tabs.
     */
    fun addTrack(track: Track) {
        val lists = store.playlists
        if (lists.isEmpty()) {
            showNameDialog("Create playlist and add track", "") { name ->
                val created = store.create(name)
                store.add(created.id, track.uri.toString())
                selectedId = created.id
                changed(created.id)
            }
            return
        }
        AlertDialog.Builder(context)
            .setTitle("Add to playlist")
            .setItems(lists.map { "${it.name} (${it.trackUris.size})" }.toTypedArray()) { _, which ->
                val selected = lists[which]
                val duplicate = track.uri.toString() in selected.trackUris
                store.add(selected.id, track.uri.toString())
                selectedId = selected.id
                changed(selected.id)
                notice(if (duplicate) "Already in ${selected.name}" else "Added to ${selected.name}")
            }
            .show()
    }

    private fun play(index: Int) {
        val playlist = activePlaylist ?: return
        val chosen = rows.getOrNull(index)?.track
        if (chosen == null) {
            notice("This file is unavailable. Choose its folder again or remove it.")
            return
        }
        // A missing URI is visible in the editor but excluded from playback.
        val playable = rows.mapNotNull { it.track }
        onPlayTrack(chosen, playlist, playable)
    }

    /**
     * Dragging only edits this playlist's saved URI order. The current playing
     * song and playback position are never changed by a reordering.
     */
    private fun handleDrag(event: DragEvent): Boolean {
        val token = event.localState as? DragToken
        val playlistId = activePlaylist?.id ?: return false
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                highlightRow(-1)
                return token != null && token.playlistId == playlistId
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                if (token == null || token.playlistId != playlistId) return false
                highlightRow(tracksList.pointToPosition(event.x.toInt(), event.y.toInt()))
                val now = SystemClock.uptimeMillis()
                if (now - lastAutoScrollAt >= 160L) {
                    val edge = context.dp(56)
                    val direction = when {
                        event.y < edge -> -1
                        event.y > tracksList.height - edge -> 1
                        else -> 0
                    }
                    if (direction != 0) {
                        tracksList.smoothScrollBy(direction * context.dp(66), 140)
                        lastAutoScrollAt = now
                    }
                }
                return true
            }
            DragEvent.ACTION_DROP -> {
                highlightRow(-1)
                if (token == null || token.playlistId != playlistId) return false
                val from = rows.indexOfFirst { it.uri == token.uri }
                val to = tracksList.pointToPosition(event.x.toInt(), event.y.toInt())
                if (from < 0 || to !in rows.indices || from == to) return false
                val moved = store.moveRelative(
                    playlistId, token.uri, rows[to].uri, from < to
                )
                if (moved) changed(playlistId)
                return moved
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                highlightRow(-1)
                return true
            }
        }
        return true
    }

    private fun highlightRow(index: Int) {
        if (hoveredRow == index) return
        hoveredRow = index
        listAdapter.notifyDataSetChanged()
    }

    private fun showTrackActions(index: Int) {
        val playlist = activePlaylist ?: return
        val row = rows.getOrNull(index) ?: return
        val options = arrayOf("Play", "Move up", "Move down", "Remove from playlist")
        AlertDialog.Builder(context)
            .setTitle(row.track?.name ?: "Unavailable track")
            .setItems(options) { _, choice ->
                when (choice) {
                    0 -> play(index)
                    1 -> { store.move(playlist.id, row.uri, -1); changed(playlist.id) }
                    2 -> { store.move(playlist.id, row.uri, 1); changed(playlist.id) }
                    3 -> {
                        // This removes only the reference, not the music file.
                        store.remove(playlist.id, row.uri)
                        changed(playlist.id)
                    }
                }
            }
            .show()
    }

    private fun renameSelected() {
        val current = activePlaylist ?: return
        showNameDialog("Rename playlist", current.name) { name ->
            store.rename(current.id, name)
            changed(current.id)
        }
    }

    private fun deleteSelected() {
        val selected = activePlaylist ?: return
        AlertDialog.Builder(context)
            .setTitle("Delete playlist?")
            .setMessage("Delete '${selected.name}'? Music files will not be deleted.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Delete") { _, _ ->
                store.delete(selected.id)
                selectedId = null
                changed(selected.id)
            }
            .show()
    }

    private fun showNameDialog(title: String, initial: String, action: (String) -> Unit) {
        val field = EditText(context).apply {
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setText(initial)
            selectAll()
            hint = "Playlist name"
        }
        val margin = context.dp(18)
        val wrapper = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(margin, 0, margin, 0)
            addView(field)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(wrapper)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = field.text.toString()
                val result = runCatching { action(name) }
                result.onSuccess { dialog.dismiss() }
                    .onFailure { error -> field.error = error.message ?: "Invalid name" }
            }
            field.requestFocus()
            dialog.window?.setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            )
        }
        dialog.show()
    }

    private fun changed(playlistId: String) {
        refresh()
        onCatalogChanged(playlistId)
    }

    private fun updateRows() {
        val playlist = activePlaylist
        val anchor = rows.getOrNull(tracksList.firstVisiblePosition)?.uri
        val offset = tracksList.getChildAt(0)?.top ?: 0
        rows.clear()
        playlist?.trackUris?.forEach { uri -> rows.add(Row(uri, resolve(uri))) }
        hoveredRow = -1
        listAdapter.notifyDataSetChanged()
        val newIndex = rows.indexOfFirst { it.uri == anchor }
        if (newIndex >= 0) tracksList.post { tracksList.setSelectionFromTop(newIndex, offset) }
        help.text = when {
            playlist == null -> "Create a playlist, then add tracks from Library."
            rows.isEmpty() -> "Empty playlist. Long-press a Library track to add it."
            else -> "${rows.size} tracks in saved order · drag ≡ or hold row for options"
        }
    }

    private fun notice(message: String) =
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private inner class PlaylistRowsAdapter : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Row = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row: LinearLayout
            val holder: RowHolder
            if (convertView is LinearLayout && convertView.tag is RowHolder) {
                row = convertView
                holder = row.tag as RowHolder
            } else {
                row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = context.dp(56)
                }
                val title = TextView(context).apply {
                    textSize = 15f
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(context.dp(10), context.dp(6), context.dp(4), context.dp(6))
                    maxLines = 2
                }
                val handle = TextView(context).apply {
                    text = "≡"
                    textSize = 28f
                    gravity = Gravity.CENTER
                    contentDescription = "Long press and drag to reorder playlist"
                    tag = "playlist-drag-handle"
                    isLongClickable = true
                }
                row.addView(title, LayoutParams(0, -2, 1f))
                row.addView(handle, LayoutParams(context.dp(52), context.dp(54)))
                holder = RowHolder(title, handle)
                row.tag = holder
            }
            val item = getItem(position)
            row.setBackgroundColor(
                if (position == hoveredRow) Color.rgb(221, 246, 241)
                else Color.TRANSPARENT
            )
            holder.title.text = if (item.track != null) {
                "${position + 1}.  ${item.track.name}"
            } else {
                "${position + 1}.  Unavailable: ${item.uri.substringAfterLast('/')}"
            }
            holder.title.setTextColor(
                if (item.track != null) Color.rgb(31, 37, 43)
                else Color.rgb(154, 85, 67)
            )
            holder.handle.setOnLongClickListener {
                val id = activePlaylist?.id ?: return@setOnLongClickListener false
                val token = DragToken(id, item.uri)
                val clip = ClipData.newPlainText("playlist", "")
                it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                it.startDragAndDrop(clip, View.DragShadowBuilder(row), token, 0)
            }
            return row
        }
    }
}
