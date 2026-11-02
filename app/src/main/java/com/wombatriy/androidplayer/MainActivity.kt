package com.wombatriy.androidplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowInsets
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
    private var playPauseButton: Button? = null
    private var loopStatus: TextView? = null
    private var errorText: TextView? = null
    private var speedBar: SeekBar? = null
    private var speedValue: TextView? = null

    private val tick = object : Runnable {
        override fun run() {
            if (::player.isInitialized) {
                var pos = player.currentPosition.coerceAtLeast(0L)
                val end = bMs
                val start = aMs
                if (player.isPlaying && start != null && end != null && end > start && pos >= end) {
                    pos = start
                    player.seekTo(start)
                }
                updatePlaybackProgress(pos)
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
                errorText?.apply {
                    text = "Playback error: ${playbackError}"
                    visibility = View.VISIBLE
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playPauseButton?.text = if (isPlaying) "Pause" else "Play"
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
        // Android 15+ draws application content behind the system bars by default.
        // Apply real status/navigation bar insets instead of hard-coded padding.
        container.setOnApplyWindowInsetsListener { root, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                top = insets.getInsets(WindowInsets.Type.statusBars()).top
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            root.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(container)
        container.requestApplyInsets()
    }

    private fun updatePlaybackProgress(position: Long = player.currentPosition) {
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        if (!scrubbing) seek?.progress = if (duration > 0) {
            ((position.coerceAtLeast(0L).toDouble() / duration) * 1000).toInt().coerceIn(0, 1000)
        } else 0
        seekText?.text = "${format(position)} / ${format(duration)}"
    }

    private fun updateLoopStatus() {
        val a = aMs
        val b = bMs
        val state = when {
            a == null || b == null -> "Loop inactive"
            b <= a -> "Set B after A to enable looping"
            else -> "A–B loop active"
        }
        loopStatus?.text = "A = ${a?.let(::format) ?: "unset"}    B = ${b?.let(::format) ?: "unset"}\n$state"
    }

    private fun render() {
        content.removeAllViews()
        seek = null
        seekText = null
        playPauseButton = null
        loopStatus = null
        errorText = null
        speedBar = null
        speedValue = null
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
        errorText = label(playbackError?.let { "Playback error: $it" } ?: "").apply {
            visibility = if (playbackError == null) View.GONE else View.VISIBLE
        }
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
        updatePlaybackProgress()
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(Button(this).apply {
            text = "−10s"
            setOnClickListener {
                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                player.seekTo(target)
                updatePlaybackProgress(target)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        playPauseButton = Button(this).apply {
            text = if (player.isPlaying) "Pause" else "Play"
            setOnClickListener { if (player.isPlaying) player.pause() else player.play() }
        }
        controls.addView(playPauseButton, LinearLayout.LayoutParams(0, -2, 1f))
        controls.addView(Button(this).apply {
            text = "+10s"
            setOnClickListener {
                val duration = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
                val target = (player.currentPosition + 10_000L).coerceAtMost(duration)
                player.seekTo(target)
                updatePlaybackProgress(target)
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(controls)

        label("Speed (pitch preserved)")
        speedValue = label("${"%.2f".format(java.util.Locale.US, player.playbackParameters.speed)}×")
        speedBar = SeekBar(this).apply {
            // One progress unit = 0.01x; 0 = 0.25x and 125 = 1.50x.
            max = 125
            progress = ((player.playbackParameters.speed - 0.25f) * 100).roundToInt().coerceIn(0, 125)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar, value: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val speed = (25 + value) / 100f
                        player.setPlaybackSpeed(speed)
                        speedValue?.text = "${"%.2f".format(java.util.Locale.US, speed)}×"
                    }
                }
                override fun onStartTrackingTouch(bar: SeekBar) = Unit
                override fun onStopTrackingTouch(bar: SeekBar) = Unit
            })
        }
        content.addView(speedBar)
        content.addView(SpeedRulerView(this), LinearLayout.LayoutParams(-1, dp(58)))
        button("Reset speed to 1.00×") {
            player.setPlaybackSpeed(1.0f)
            speedBar?.progress = 75
            speedValue?.text = "1.00×"
        }
        loopStatus = label("")
        updateLoopStatus()
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
                    // Update only the label; recreating the entire player made controls jump.
                    updateLoopStatus()
                }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        content.addView(loopControls)
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
