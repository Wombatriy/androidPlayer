package com.wombatriy.androidplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.wombatriy.androidplayer.Track

/**
 * Owns ExoPlayer, playback controls and A–B state.
 * The UI never calls ExoPlayer directly, so all seeks respect loop bounds.
 */
class PlaybackController(context: Context, private val listener: Listener) {
    interface Listener {
        fun onTrackChanged()
        fun onPlaybackControlsChanged()
        fun onPositionChanged(positionMs: Long, durationMs: Long)
        fun onLoopChanged()
        fun onPlaybackError(message: String)
    }

    private val player = ExoPlayer.Builder(context).build()
    private val handler = Handler(Looper.getMainLooper())
    private var released = false

    var currentTrack: Track? = null
        private set
    var pointA: Long? = null
        private set
    var pointB: Long? = null
        private set

    val speed: Float get() = player.playbackParameters.speed
    val currentPosition: Long get() = player.currentPosition.coerceAtLeast(0L)
    val duration: Long get() = player.duration.takeIf { it > 0L } ?: 0L
    val showsPause: Boolean get() = player.playWhenReady && player.playbackState != Player.STATE_ENDED

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
            // isPlaying briefly becomes false during buffering/seeking; playWhenReady
            // represents user intent and avoids flashing the Play/Pause button.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                listener.onPlaybackControlsChanged()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener.onPlaybackControlsChanged()
            }
            override fun onPlayerError(error: PlaybackException) {
                listener.onPlaybackError(error.localizedMessage ?: "Playback failed")
                listener.onPlaybackControlsChanged()
            }
        })
        handler.post(ticker)
    }

    fun load(track: Track) {
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

    fun togglePlayPause() {
        if (showsPause) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(activeLoop?.startMs ?: 0L)
            }
            player.play()
        }
        listener.onPlaybackControlsChanged()
    }

    fun seekTo(requestedMs: Long) {
        val bounded = requestedMs.coerceAtLeast(0L).let {
            if (duration > 0) it.coerceAtMost(duration) else it
        }
        val target = activeLoop?.constrainSeek(bounded) ?: bounded
        player.seekTo(target)
        listener.onPositionChanged(target, duration)
    }

    fun skipBy(deltaMs: Long) = seekTo(currentPosition + deltaMs)

    fun setA() {
        pointA = currentPosition
        listener.onLoopChanged()
        // Once A–B becomes active, always keep the playhead inside it.
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
    }

    fun release() {
        released = true
        handler.removeCallbacks(ticker)
        player.release()
    }
}
