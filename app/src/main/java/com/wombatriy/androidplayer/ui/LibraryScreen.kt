package com.wombatriy.androidplayer.ui

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.wombatriy.androidplayer.MusicFolder
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.bpm.BpmBatchAnalyzer
import com.wombatriy.androidplayer.bpm.BatchBpmPolicy
import com.wombatriy.androidplayer.bpm.BpmFormatting
import com.wombatriy.androidplayer.bpm.BpmRecord
import com.wombatriy.androidplayer.bpm.BpmSource
import com.wombatriy.androidplayer.library.LibraryOrder

/**
 * Retained library with recycled ListView rows. No file/BPM I/O runs in
 * adapter.getView(): all metadata arrives as already-resolved maps.
 */
class LibraryScreen(
    context: Context,
    onChooseFolder: () -> Unit,
    onRescan: () -> Unit,
    onBatchToggle: () -> Unit,
    private val onTrackSelected: (Track) -> Unit,
    private val onTrackLongPressed: (Track) -> Unit,
    private val onOrderChanged: (List<Track>) -> Unit
) : LinearLayout(context) {
    private val adapter = TrackAdapter(context)
    private val list = ListView(context).apply {
        dividerHeight = context.dp(1)
        setBackgroundColor(PlayerPalette.surface)
        isFastScrollEnabled = true
        setOnItemClickListener { _, _, position, _ ->
            onTrackSelected(this@LibraryScreen.adapter.getItem(position))
        }
        setOnItemLongClickListener { _, _, position, _ ->
            onTrackLongPressed(this@LibraryScreen.adapter.getItem(position))
            true
        }
    }
    private val choose = compactButton("Choose folder", onChooseFolder)
    private val rescan = compactButton("Rescan", onRescan)
    private val batch = compactButton("Batch BPM", onBatchToggle)
    private val scanProgress = thinBar(context, indeterminate = true)
    private val bpmProgress = thinBar(context, indeterminate = false)
    private val info = context.textLine("Choose a music folder to begin.", 13f)
    private val batchInfo = context.textLine("", 12f)
    private var restoreIndex: Int? = null
    private var restoreOffset = 0
    private var restoreUri: String? = null
    private var sortScheduled = false
    private var scanning = false

    init {
        orientation = VERTICAL
        setBackgroundColor(PlayerPalette.background)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))

        // Deliberately omit the redundant 'Music Library' title.
        val actions = LinearLayout(context).apply { orientation = HORIZONTAL }
        for (button in listOf(choose, rescan, batch)) {
            actions.addView(button, LayoutParams(0, -2, 1f))
        }
        addView(actions)

        scanProgress.visibility = GONE
        bpmProgress.visibility = GONE
        bpmProgress.max = 1000
        addView(scanProgress, LayoutParams(-1, context.dp(5)))
        addView(bpmProgress, LayoutParams(-1, context.dp(5)))
        addView(info)
        batchInfo.visibility = GONE
        addView(batchInfo)
        list.adapter = adapter
        addView(list, LayoutParams(-1, 0, 1f))
        batch.isEnabled = false
    }

    private fun compactButton(caption: String, action: () -> Unit): Button =
        context.actionButton(caption, action).apply {
            minWidth = 0
            minimumWidth = 0
            textSize = 12f
            setPadding(0, 0, 0, 0)
        }

    private fun thinBar(context: Context, indeterminate: Boolean): ProgressBar =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = indeterminate
        }

    val trackCount: Int get() = adapter.count
    fun tracksSnapshot(): List<Track> = adapter.snapshot()
    val firstVisibleIndex: Int get() = list.firstVisiblePosition
    val firstVisibleOffset: Int get() = list.getChildAt(0)?.top ?: 0
    val firstVisibleUri: String?
        get() = adapter.snapshot().getOrNull(list.firstVisiblePosition)?.uri?.toString()

    fun restorePosition(index: Int, offsetPx: Int, uri: String? = null) {
        restoreIndex = index.coerceAtLeast(0)
        restoreOffset = offsetPx
        restoreUri = uri
    }

    fun setLoading(loading: Boolean) {
        scanning = loading
        scanProgress.visibility = if (loading) VISIBLE else GONE
        rescan.isEnabled = !loading
        batch.isEnabled = !loading && adapter.count > 0
        info.text = if (loading) "Reading music folder…" else {
            if (adapter.count == 0) "No audio files found. Choose a folder to begin."
            else "${adapter.count} tracks · tap to play · hold to rename"
        }
    }

    fun updateScanProgress(progress: MusicFolder.ScanProgress) {
        if (!scanning) return
        info.text = "Found ${progress.filesFound} audio files in ${progress.foldersVisited} folders…"
    }

    fun updateBatchState(state: BpmBatchAnalyzer.State) {
        batch.text = if (state.running) "Cancel BPM" else "Batch BPM"
        batch.isEnabled = state.running || (!scanning && adapter.count > 0)
        bpmProgress.visibility = if (state.running) VISIBLE else GONE
        bpmProgress.progress = if (state.total > 0) {
            BatchBpmPolicy.progressThousand(state.processed, state.total)
        } else 0
        batchInfo.visibility = if (state.running || state.message.isNotBlank()) VISIBLE else GONE
        batchInfo.text = if (state.total > 0) {
            "${state.message}  ${state.processed}/${state.total} · " +
                "${state.detected} detected · ${state.skipped} saved · ${state.failed} failed"
        } else state.message
    }

    fun submitTracks(tracks: List<Track>) {
        val previousAnchor = restoreUri ?: firstVisibleUri
        val index = restoreIndex ?: list.firstVisiblePosition
        val offset = if (restoreIndex != null) restoreOffset else firstVisibleOffset
        adapter.submit(tracks)
        onOrderChanged(adapter.snapshot())
        val anchored = adapter.indexOfUri(previousAnchor)
        val target = if (anchored >= 0) anchored
            else index.coerceIn(0, (adapter.count - 1).coerceAtLeast(0))
        if (adapter.count > 0) list.post { list.setSelectionFromTop(target, offset) }
        restoreIndex = null
        restoreUri = null
        setLoading(false)
    }

    /** Initial metadata replaces the snapshot; decoded chunks update existing rows. */
    fun updateBpmRecords(records: Map<String, BpmRecord>, replace: Boolean = false) {
        adapter.updateBpm(records, replace)
        scheduleSort()
    }

    /** Update a foreground-selected song without reordering for every callback. */
    fun updateOneBpm(track: Track, record: BpmRecord?) {
        adapter.updateOne(track.uri.toString(), record)
        scheduleSort()
    }

    /**
     * Batch analysis emits results in small groups. Coalesce sorting to at most
     * once every 450ms, then restore the same first visible URI and pixel offset.
     * Scrolling does not jump to the top while BPM values are discovered.
     */
    private fun scheduleSort() {
        if (sortScheduled) return
        sortScheduled = true
        list.postDelayed({
            sortScheduled = false
            val anchor = firstVisibleUri
            val offset = firstVisibleOffset
            if (adapter.resort()) {
                onOrderChanged(adapter.snapshot())
                val target = adapter.indexOfUri(anchor)
                if (target >= 0) list.post { list.setSelectionFromTop(target, offset) }
            }
        }, 450L)
    }

    private class TrackAdapter(private val context: Context) : BaseAdapter() {
        private var items: List<Track> = emptyList()
        private val bpmValues = HashMap<String, BpmRecord>()

        fun snapshot(): List<Track> = items

        fun submit(newItems: List<Track>) {
            bpmValues.clear()
            items = sorted(newItems)
            notifyDataSetChanged()
        }

        fun indexOfUri(uri: String?): Int =
            if (uri == null) -1 else items.indexOfFirst { it.uri.toString() == uri }

        fun resort(): Boolean {
            val next = sorted(items)
            if (next == items) return false
            items = next
            notifyDataSetChanged()
            return true
        }

        private fun sorted(tracks: List<Track>): List<Track> =
            LibraryOrder.sorted(
                tracks,
                { track ->
                    bpmValues[track.uri.toString()]?.bpm ?: track.filenameBpm?.toDouble()
                },
                { it.name }
            )

        fun updateBpm(rows: Map<String, BpmRecord>, replace: Boolean) {
            if (replace) bpmValues.clear()
            bpmValues.putAll(rows)
            // Only visible recycled rows are rebound; this is not a full rebuild.
            notifyDataSetChanged()
        }

        fun updateOne(uri: String, record: BpmRecord?) {
            if (record == null) bpmValues.remove(uri) else bpmValues[uri] = record
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
                    setPadding(context.dp(8), 0, 0, 0)
                }
                row.addView(name, LayoutParams(0, -2, 1f))
                row.addView(bpm, LayoutParams(-2, -2))
                holder = RowHolder(name, bpm)
                row.tag = holder
            }
            val track = getItem(position)
            holder.name.text = track.name
            // Fallback to filename metadata while the background snapshot loads.
            val record = bpmValues[track.uri.toString()]
                ?: track.filenameBpm?.let { BpmRecord(it.toDouble(), BpmSource.FILENAME) }
            holder.bpm.visibility = if (record == null) View.GONE else View.VISIBLE
            if (record != null) {
                val prefix = when (record.source) {
                    BpmSource.FILENAME -> "F"
                    BpmSource.DETECTED -> "D"
                    BpmSource.MANUAL -> "M"
                }
                holder.bpm.text = "$prefix ${BpmFormatting.bpm(record.bpm)}"
                holder.bpm.setTextColor(when (record.source) {
                    BpmSource.FILENAME -> Color.rgb(12, 128, 105)
                    BpmSource.DETECTED -> Color.rgb(34, 92, 168)
                    BpmSource.MANUAL -> Color.rgb(168, 92, 23)
                })
            } else {
                holder.bpm.text = ""
            }
            return row
        }

        private class RowHolder(val name: TextView, val bpm: TextView)
    }
}
