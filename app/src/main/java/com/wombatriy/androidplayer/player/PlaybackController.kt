package com.wombatriy.androidplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
        fun onQueueChanged()
    }

    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val player = ExoPlayer.Builder(context).build()
    private val queue = PlaybackQueue<Track>()
    private val handler = Handler(Looper.getMainLooper())
    var activePlaylistId: String? = null
        private set
    var queueSourceLabel: String = "All Tracks"
        private set
    private var released = false
    private var lastUiTick = 0L

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
    fun queueSnapshot(): List<Track> = queue.snapshot()
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
            val now = SystemClock.elapsedRealtime()
            if (loop == null || now - lastUiTick >= 110L) {
                listener.onPositionChanged(position, duration)
                lastUiTick = now
            }
            // Keep the UI at ~8fps, but check active A–B boundaries more
            // frequently to reduce audible overshoot without redrawing more.
            handler.postDelayed(this,
                if (loop != null && player.playWhenReady) 25L else 120L)
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
     * Starts from the supplied source order, without ever sorting a playlist.
     * Switching the queue source on the currently playing file does not
     * restart playback or clear A–B markers.
     */
    fun load(
        track: Track,
        sourceTracks: List<Track>,
        playlistId: String? = null,
        sourceName: String = "All Tracks"
    ) {
        val alreadyPlaying = currentTrack?.uri == track.uri &&
            player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED
        val sourceChanged = activePlaylistId != playlistId
        if (alreadyPlaying && !sourceChanged) return

        queue.select(track, sourceTracks)
        activePlaylistId = playlistId
        queueSourceLabel = if (playlistId == null) "All Tracks" else sourceName
        listener.onQueueChanged()
        if (alreadyPlaying) {
            listener.onPlaybackControlsChanged()
        } else {
            playTrack(track)
        }
    }

    private fun playTrack(track: Track) {
        currentTrack = track
        pointA = null
        pointB = null
        player.setMediaItem(MediaItem.fromUri(track.uri))
        player.prepare()
        player.play()
        listener.onTrackChanged()
        listener.onQueueChanged()
        listener.onLoopChanged()
        listener.onPlaybackControlsChanged()
    }

    /**
     * Release the file descriptor only after the rename confirmation.
     * For another (non-current) track, playback continues uninterrupted.
     * Return true when the current track must restart after the operation.
     */
    fun suspendForRename(track: Track): Boolean {
        if (currentTrack?.uri != track.uri) return false
        player.pause()
        player.stop()
        player.clearMediaItems()
        listener.onPlaybackControlsChanged()
        return true
    }

    /** BPM resorting only affects the virtual All Tracks queue. */
    fun updateLibraryOrder(tracks: List<Track>) {
        if (activePlaylistId != null) return
        queue.updateOrder(tracks) { a, b -> a.uri == b.uri }
        listener.onQueueChanged()
        listener.onPlaybackControlsChanged()
    }

    /**
     * Changes to the currently active playlist are applied without resetting
     * playback. If the current song was removed, keep it until it finishes,
     * then continue with the remaining playable playlist entries.
     */
    fun updatePlaylistOrder(playlistId: String, name: String, tracks: List<Track>) {
        if (activePlaylistId != playlistId) return
        queueSourceLabel = name
        val selected = currentTrack
        val ordered = if (selected != null && tracks.none { it.uri == selected.uri }) {
            listOf(selected) + tracks.filterNot { it.uri == selected.uri }
        } else tracks
        queue.updateOrder(ordered) { a, b -> a.uri == b.uri }
        listener.onQueueChanged()
        listener.onPlaybackControlsChanged()
    }

    fun onPlaylistDeleted(playlistId: String, library: List<Track>) {
        if (activePlaylistId != playlistId) return
        activePlaylistId = null
        queueSourceLabel = "All Tracks"
        updateLibraryOrder(library)
    }

    /** Update the queue's URI; restart the renamed current track from 00:00. */
    fun onLibraryTrackRenamed(previous: Track, replacement: Track, restart: Boolean = false) {
        queue.replaceReference(previous, replacement)
        if (restart && currentTrack?.uri == previous.uri) {
            playTrack(replacement)
        } else listener.onQueueChanged()
    }

    /** Restore the original URI if the SAF provider rejected the rename. */
    fun restoreAfterFailedRename(previous: Track, restart: Boolean) {
        if (restart && currentTrack?.uri == previous.uri) playTrack(previous)
    }

    /** Tap a Queue row without losing its playlist context. */
    fun playQueueIndex(index: Int) {
        val selected = queue.snapshot().getOrNull(index) ?: return
        if (selected.uri == currentTrack?.uri &&
            player.mediaItemCount > 0 && player.playbackState != Player.STATE_ENDED) return
        queue.selectAt(index)
        playTrack(selected)
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

    fun setA() = setAAt(currentPosition)
    fun setB() = setBAt(currentPosition)

    fun setAAt(ms: Long) {
        pointA = clampToTrack(ms)
        listener.onLoopChanged()
        normalizeCurrentPosition()
    }

    fun setBAt(ms: Long) {
        pointB = clampToTrack(ms)
        listener.onLoopChanged()
        normalizeCurrentPosition()
    }

    private fun clampToTrack(ms: Long): Long {
        val atLeastZero = ms.coerceAtLeast(0L)
        return if (duration > 0L) atLeastZero.coerceAtMost(duration) else atLeastZero
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
