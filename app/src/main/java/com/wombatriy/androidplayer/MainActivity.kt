package com.wombatriy.androidplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.roundToInt

/**
 * Iteration 01: folder browser + functioning player with speed and A/B loop.
 * Playlist and queue screens are intentionally placeholders until later iterations.
 */
class MainActivity : Activity() {
    private lateinit var player: ExoPlayer
    private lateinit var container: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("library", MODE_PRIVATE) }
    private var tracks = emptyList<Track>()
    private var current: Track? = null
    private var tab = 1
    private var aMs: Long? = null
    private var bMs: Long? = null
    private var seek: SeekBar? = null
    private var seekText: TextView? = null
    private var scrubbing = false
    private var playbackError: String? = null
    private var loadingFolder = false

    private val tick = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val end = bMs
                val start = aMs
                if (player.isPlaying && start != null && end != null && end > start && pos >= end) {
                    player.seekTo(start)
                }
                val duration = player.duration.takeIf { it > 0 } ?: 0L
                if (!scrubbing) seek?.progress = if (duration > 0) {
                    ((pos.toDouble() / duration) * 1000).toInt().coerceIn(0, 1000)
                } else 0
                seekText?.text = "${format(pos)} / ${format(duration)}"
            }
            handler.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                playbackError = error.localizedMessage ?: "Playback failed"
                if (tab == 0) render()
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (tab == 0) render()
            }
        })
        createLayout()
        restoreFolder()
        render()
        handler.post(tick)
    }

    private fun createLayout() {
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249, 250, 252))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Player", "Library", "Playlists", "Queue").forEachIndexed { index, name ->
            header.addView(Button(this).apply {
                text = name
                textSize = 11f
                isAllCaps = false
                setOnClickListener { tab = index; render() }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
        }
        container.addView(header)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(content)
        container.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        status = TextView(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 12f
        }
        container.addView(status)
        setContentView(container)
    }

    private fun render() {
        content.removeAllViews()
        seek = null
        seekText = null
        when (tab) {
            0 -> renderPlayer()
            1 -> renderLibrary()
            2 -> placeholder("Playlists", "Creating and editing playlists will be added in a later iteration.")
            3 -> placeholder("Queue", "Playlist track ordering will be added in a later iteration.")
        }
        status.text = if (loadingFolder) "Scanning music folder…" else "${tracks.size} tracks loaded"
    }

    private fun renderLibrary() {
        title("Music Library")
        button("Choose music folder") { pickFolder() }
        button("Rescan selected folder") { restoreFolder() }
        if (tracks.isEmpty()) {
            label(if (loadingFolder) "Scanning…" else "Choose a folder containing MP3, FLAC, WAV or other supported audio.")
        }
        tracks.forEach { track ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val name = TextView(this).apply {
                text = track.name
                textSize = 15f
                setPadding(dp(8), dp(10), dp(8), dp(10))
                setOnClickListener { openTrack(track) }
            }
            row.addView(name, LinearLayout.LayoutParams(0, -2, 1f))
            track.filenameBpm?.let { bpm ->
                row.addView(TextView(this).apply {
                    text = "$bpm BPM"
                    setTextColor(Color.rgb(16, 120, 105))
                    textSize = 12f
                })
            }
            content.addView(row)
        }
    }

    private fun renderPlayer() {
        title("Player")
        val track = current
        if (track == null) {
            label("Open a track from Library to start playing.")
            return
        }
        label(track.name)
        track.filenameBpm?.let { label("Filename BPM: $it") }
        playbackError?.let { label("Playback error: $it") }

        seekText = label("00:00 / 00:00")
        seek = SeekBar(this).apply {
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onStartTrackingTouch(bar: SeekBar) { scrubbing = true }
                override fun onStopTrackingTouch(bar: SeekBar) {
                    val duration = player.duration
                    if (duration > 0) player.seekTo((duration * bar.progress.toLong()) / 1000L)
                    scrubbing = false
                }
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) = Unit
            })
        }
        content.addView(seek)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "−10s"
            setOnClickListener { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = if (player.isPlaying) "Pause" else "Play"
            setOnClickListener { if (player.isPlaying) player.pause() else player.play(); render() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "+10s"
            setOnClickListener { player.seekTo(player.currentPosition + 10_000L) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(controls)

        label("Speed (pitch preserved)")
        val speedLabel = label("${"%.2f".format(java.util.Locale.US, player.playbackParameters.speed)}×")
        content.addView(SeekBar(this).apply {
            max = 150
            progress = ((player.playbackParameters.speed - 0.5f) * 100).roundToInt().coerceIn(0, 150)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val speed = 0.5f + value / 100f
                        player.setPlaybackSpeed(speed)
                        speedLabel.text = "${"%.2f".format(java.util.Locale.US, speed)}×"
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        })
        label("A = ${aMs?.let(::format) ?: "unset"}    B = ${bMs?.let(::format) ?: "unset"}")
        val loopControls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Set A", "Set B", "Clear A–B").forEachIndexed { idx, text ->
            loopControls.addView(Button(this).apply {
                this.text = text
                isAllCaps = false
                setOnClickListener {
                    when (idx) {
                        0 -> aMs = player.currentPosition.coerceAtLeast(0L)
                        1 -> bMs = player.currentPosition.coerceAtLeast(0L)
                        2 -> { aMs = null; bMs = null }
                    }
                    // Invalid or reversed endpoints do not activate looping.
                    render()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(loopControls)
        val a = aMs
        val b = bMs
        if (a != null && b != null && b <= a) label("Set B after A to enable looping.")
        else if (a != null && b != null) label("A–B loop active")
    }

    private fun openTrack(track: Track) {
        current = track
        aMs = null; bMs = null; playbackError = null
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        tab = 0
        render()
    }

    private fun pickFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivityForResult(intent, FOLDER_REQUEST)
    }

    @Deprecated("Legacy Activity result API for a small framework-only first iteration")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FOLDER_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            prefs.edit().putString("treeUri", uri.toString()).apply()
            scan(uri)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Folder permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFolder() {
        prefs.getString("treeUri", null)?.let { scan(Uri.parse(it)) }
    }

    private fun scan(uri: Uri) {
        loadingFolder = true
        render()
        Thread {
            try {
                val loaded = MusicFolder.load(this, uri)
                runOnUiThread {
                    tracks = loaded
                    loadingFolder = false
                    render()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    loadingFolder = false
                    render()
                    Toast.makeText(this, "Unable to scan folder: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun placeholder(heading: String, description: String) {
        title(heading)
        label(description)
    }
    private fun title(value: String) { content.addView(TextView(this).apply {
        text = value; textSize = 23f; setTextColor(Color.rgb(30, 40, 55))
        setPadding(0, 0, 0, dp(12))
    }) }
    private fun label(value: String): TextView = TextView(this).apply {
        text = value; textSize = 15f; setPadding(0, dp(8), 0, dp(8))
        content.addView(this)
    }
    private fun button(text: String, action: () -> Unit) {
        content.addView(Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { action() } })
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).roundToInt()
    private fun format(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000
        return "%02d:%02d".format(java.util.Locale.US, total / 60, total % 60)
    }
    override fun onDestroy() {
        handler.removeCallbacks(tick)
        player.release()
        super.onDestroy()
    }
    companion object { private const val FOLDER_REQUEST = 41 }
}
