package com.wombatriy.androidplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wombatriy.androidplayer.Track

/**
 * Single owner of ExoPlayer, navigation and A–B loop.
 * At track end: Stop / Next / Repeat. A–B looping overrides the end mode.
 */
class PlaybackController(context: Context, private val listener: Listener) {
    interface Listener {
        fun onTrackChanged()
        fun onPlaybackControlsChanged()
        fun onPositionChanged(positionMs: Long, durationMs: Long)
        fun onLoopChanged()
        fun onPlaybackError(message: String)
        fun onSpeedChanged()
    }

    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val player = ExoPlayer.Builder(context).build()
    private val queue = PlaybackQueue<Track>()
    private val handler = Handler(Looper.getMainLooper())
    private var released = false

    var currentTrack: Track? = null
        private set
    var pointA: Long? = null
        private set
    var pointB: Long? = null
        private set

    var endMode: EndMode = runCatching {
        EndMode.valueOf(prefs.getString(KEY_END_MODE, EndMode.NEXT.name) ?: EndMode.NEXT.name)
    }.getOrDefault(EndMode.NEXT)
        private set

    val hasPrevious: Boolean get() = queue.hasPrevious
    val hasNext: Boolean get() = queue.hasNext
    val queuePosition: Int get() = queue.position
    val queueSize: Int get() = queue.size
    val speed: Float get() = player.playbackParameters.speed
    val currentPosition: Long get() = player.currentPosition.coerceAtLeast(0L)
    val duration: Long get() = player.duration.takeIf { it > 0L } ?: 0L
    val showsPause: Boolean get() =
        player.playWhenReady && player.playbackState != Player.STATE_ENDED

    private val activeLoop: LoopRange?
        get() {
            val a = pointA ?: return null
            val b = pointB ?: return null
            return if (b > a) LoopRange(a, b) else null
        }

    private val ticker = object : Runnable {
        override fun run() {
            if (released) return
            var position = currentPosition
            val loop = activeLoop
            if (loop != null && player.playWhenReady && player.playbackState != Player.STATE_ENDED) {
                val corrected = loop.constrainSeek(position)
                if (corrected != position) {
                    player.seekTo(corrected)
                    position = corrected
                }
            }
            listener.onPositionChanged(position, duration)
            handler.postDelayed(this, 120L)
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                listener.onPlaybackControlsChanged()
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                // Reflect Media3's accepted playback speed in the UI.
                listener.onSpeedChanged()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onTrackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                listener.onPlaybackError(error.localizedMessage ?: "Playback failed")
                listener.onPlaybackControlsChanged()
            }
        })
        handler.post(ticker)
    }

    /**
     * Library's alphabetical order acts as the initial playback queue.
     * Playlist-based queues will reuse this controller in a later iteration.
     */
    fun load(track: Track, libraryTracks: List<Track>) {
        queue.select(track, libraryTracks)
        playTrack(track)
    }

    private fun playTrack(track: Track) {
        currentTrack = track
        pointA = null
        pointB = null
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        listener.onTrackChanged()
        listener.onLoopChanged()
        listener.onPlaybackControlsChanged()
    }

    /** Keep a queued, not-current file playable if its SAF URI changes on rename. */
    fun onLibraryTrackRenamed(previous: Track, replacement: Track) {
        queue.replaceReference(previous, replacement)
    }

    fun next() {
        queue.next()?.let(::playTrack)
    }

    fun previous() {
        queue.previous()?.let(::playTrack)
    }

    fun selectEndMode(mode: EndMode) {
        if (endMode == mode) return
        endMode = mode
        prefs.edit().putString(KEY_END_MODE, mode.name).apply()
        listener.onPlaybackControlsChanged()
    }

    private fun onTrackEnded() {
        // A loop whose B is at the end of a track may reach STATE_ENDED before
        // the periodic loop check; keep repeating A–B instead of advancing.
        val loop = activeLoop
        if (loop != null) {
            player.seekTo(loop.startMs)
            player.play()
        } else when (endMode) {
            EndMode.STOP -> Unit // STATE_ENDED already stops playback.
            EndMode.REPEAT -> {
                player.seekTo(0L)
                player.play()
            }
            EndMode.NEXT -> {
                val next = queue.next()
                if (next != null) playTrack(next)
            }
        }
        listener.onPlaybackControlsChanged()
    }

    fun togglePlayPause() {
        if (showsPause) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(activeLoop?.startMs ?: 0L)
            }
            player.play()
        }
        listener.onPlaybackControlsChanged()
    }

    fun seekTo(requestedMs: Long) {
        val bounded = requestedMs.coerceAtLeast(0L).let {
            if (duration > 0L) it.coerceAtMost(duration) else it
        }
        val target = activeLoop?.constrainSeek(bounded) ?: bounded
        player.seekTo(target)
        listener.onPositionChanged(target, duration)
    }

    fun skipBy(deltaMs: Long) = seekTo(currentPosition + deltaMs)

    fun setA() {
        pointA = currentPosition
        listener.onLoopChanged()
        normalizeCurrentPosition()
    }

    fun setB() {
        pointB = currentPosition
        listener.onLoopChanged()
        normalizeCurrentPosition()
    }

    fun clearLoop() {
        pointA = null
        pointB = null
        listener.onLoopChanged()
    }

    private fun normalizeCurrentPosition() {
        val loop = activeLoop ?: return
        val corrected = loop.constrainSeek(currentPosition)
        if (corrected != currentPosition) seekTo(corrected)
    }

    fun setSpeed(value: Float) {
        require(value in 0.25f..1.50f)
        player.setPlaybackSpeed(value)
        listener.onSpeedChanged()
    }

    fun release() {
        released = true
        handler.removeCallbacks(ticker)
        player.release()
    }

    companion object { private const val KEY_END_MODE = "endMode" }
}
