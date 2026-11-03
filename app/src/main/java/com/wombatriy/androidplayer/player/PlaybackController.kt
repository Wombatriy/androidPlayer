package com.wombatriy.androidplayer.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.wombatriy.androidplayer.player.audio.AudioEngineFactory
import com.wombatriy.androidplayer.player.audio.TimeStretchPlanner
import com.wombatriy.androidplayer.Track

/**
 * Single owner of ExoPlayer, navigation and A–B loop.
 * At track end: Stop / Next / Repeat. A–B looping overrides the end mode.
 */
class PlaybackController(context: Context) {
    interface Listener {
        fun onTrackChanged()
        fun onPlaybackControlsChanged()
        fun onPositionChanged(positionMs: Long, durationMs: Long)
        /** Intent is known before Media3 has necessarily updated currentPosition. */
        fun onSeekRequested(targetMs: Long)
        /** Media3 acknowledged a seek; may follow the request asynchronously. */
        fun onSeekApplied(actualMs: Long)
        fun onLoopChanged()
        fun onPlaybackError(message: String)
        fun onSpeedChanged()
        fun onQueueChanged()
    }

    /**
     * The MediaSessionService owns this model. Activity listeners may come and
     * go without releasing the audio engine or retaining the old Activity.
     */
    private val listeners = LinkedHashSet<Listener>()

    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    private fun notifyListeners(event: (Listener) -> Unit) {
        listeners.toList().forEach(event)
    }

    private val prefs = context.getSharedPreferences("playback", Context.MODE_PRIVATE)
    private val player = AudioEngineFactory.create(context)
    /** Exposed only to the MediaSessionService's forwarding session player. */
    val sessionPlayer: Player get() = player
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
    /** True only when ExoPlayer is actually advancing its media clock. */
    val isPlaying: Boolean get() = player.isPlaying
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
                    seekPlayer(corrected)
                    position = corrected
                }
            }
            val now = SystemClock.elapsedRealtime()
            if (listeners.isNotEmpty() &&
                (loop == null || now - lastUiTick >= 110L)) {
                notifyListeners { it.onPositionChanged(position, duration) }
                lastUiTick = now
            }
            // Check A–B boundaries quickly, even with no Activity attached.
            // Otherwise a detached service only needs a quiet 1Hz heartbeat;
            // track endings are handled by Player.Listener, not the ticker.
            handler.postDelayed(this, when {
                loop != null && player.playWhenReady -> 25L
                listeners.isNotEmpty() -> 120L
                else -> 1_000L
            })
        }
    }

    /**
     * React immediately to external play/pause (lock screen or headphones).
     * Without this, after a background pause the next 1Hz tick could delay
     * resuming A–B boundary checks for almost a second.
     */
    private fun wakeTicker() {
        if (released) return
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                wakeTicker()
                notifyListeners { it.onPlaybackControlsChanged() }
            }

            // Resumes the waveform clock after buffering and stops it during
            // buffering, pause and track end without requiring the 120ms ticker.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                wakeTicker()
                notifyListeners { it.onPlaybackControlsChanged() }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK ||
                    reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                    notifyListeners { it.onSeekApplied(newPosition.positionMs) }
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                // Reflect Media3's accepted playback speed in the UI.
                notifyListeners { it.onSpeedChanged() }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) onTrackEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                notifyListeners { it.onPlaybackError(error.localizedMessage ?: "Playback failed") }
                notifyListeners { it.onPlaybackControlsChanged() }
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
        notifyListeners { it.onQueueChanged() }
        if (alreadyPlaying) {
            notifyListeners { it.onPlaybackControlsChanged() }
        } else {
            playTrack(track)
        }
    }

    private fun playTrack(track: Track) {
        currentTrack = track
        pointA = null
        pointB = null
        player.setMediaItem(
            MediaItem.Builder()
                .setUri(track.uri)
                .setMediaId(track.uri.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(track.name).build()
                )
                .build()
        )
        player.prepare()
        player.play()
        notifyListeners { it.onTrackChanged() }
        notifyListeners { it.onQueueChanged() }
        notifyListeners { it.onLoopChanged() }
        notifyListeners { it.onPlaybackControlsChanged() }
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
        notifyListeners { it.onPlaybackControlsChanged() }
        return true
    }

    /** BPM resorting only affects the virtual All Tracks queue. */
    fun updateLibraryOrder(tracks: List<Track>) {
        if (activePlaylistId != null) return
        queue.updateOrder(tracks) { a, b -> a.uri == b.uri }
        notifyListeners { it.onQueueChanged() }
        notifyListeners { it.onPlaybackControlsChanged() }
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
        notifyListeners { it.onQueueChanged() }
        notifyListeners { it.onPlaybackControlsChanged() }
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
        } else notifyListeners { it.onQueueChanged() }
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
        notifyListeners { it.onPlaybackControlsChanged() }
    }

    private fun onTrackEnded() {
        // A loop whose B is at the end of a track may reach STATE_ENDED before
        // the periodic loop check; keep repeating A–B instead of advancing.
        val loop = activeLoop
        if (loop != null) {
            seekPlayer(loop.startMs)
            player.play()
        } else when (endMode) {
            EndMode.STOP -> Unit // STATE_ENDED already stops playback.
            EndMode.REPEAT -> {
                seekPlayer(0L)
                player.play()
            }
            EndMode.NEXT -> {
                val next = queue.next()
                if (next != null) playTrack(next)
            }
        }
        notifyListeners { it.onPlaybackControlsChanged() }
    }

    fun togglePlayPause() {
        if (showsPause) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_ENDED) {
                seekPlayer(activeLoop?.startMs ?: 0L)
            }
            player.play()
        }
        notifyListeners { it.onPlaybackControlsChanged() }
    }

    fun seekTo(requestedMs: Long) {
        val bounded = requestedMs.coerceAtLeast(0L).let {
            if (duration > 0L) it.coerceAtMost(duration) else it
        }
        val target = activeLoop?.constrainSeek(bounded) ?: bounded
        seekPlayer(target)
        notifyListeners { it.onPositionChanged(target, duration) }
    }

    /** All explicit seeks announce their intended destination before Media3 sees it. */
    private fun seekPlayer(targetMs: Long) {
        notifyListeners { it.onSeekRequested(targetMs) }
        player.seekTo(targetMs)
    }

    fun skipBy(deltaMs: Long) = seekTo(currentPosition + deltaMs)

    fun setA() = setAAt(currentPosition)
    fun setB() = setBAt(currentPosition)

    fun setAAt(ms: Long) {
        pointA = clampToTrack(ms)
        notifyListeners { it.onLoopChanged() }
        normalizeCurrentPosition()
    }

    fun setBAt(ms: Long) {
        pointB = clampToTrack(ms)
        notifyListeners { it.onLoopChanged() }
        normalizeCurrentPosition()
    }

    private fun clampToTrack(ms: Long): Long {
        val atLeastZero = ms.coerceAtLeast(0L)
        return if (duration > 0L) atLeastZero.coerceAtMost(duration) else atLeastZero
    }

    fun clearLoop() {
        pointA = null
        pointB = null
        notifyListeners { it.onLoopChanged() }
    }

    private fun normalizeCurrentPosition() {
        val loop = activeLoop ?: return
        val corrected = loop.constrainSeek(currentPosition)
        if (corrected != currentPosition) seekTo(corrected)
    }

    fun setSpeed(value: Float) {
        // Preserves the existing Media3/Sonic behavior; the plan also defines
        // the time-ratio contract for the future optional native processor.
        TimeStretchPlanner.fromSpeed(value)
        player.setPlaybackSpeed(value)
        notifyListeners { it.onSpeedChanged() }
    }

    fun release() {
        released = true
        handler.removeCallbacks(ticker)
        player.release()
    }

    companion object { private const val KEY_END_MODE = "endMode" }
}
