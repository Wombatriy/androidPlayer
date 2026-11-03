package com.wombatriy.androidplayer.player

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * The UI's PlaybackQueue remains authoritative for Next/Previous.
 *
 * ExoPlayer holds one decoded media item at a time to preserve playlist
 * rearrangement, BPM-order changes and A–B markers without restarting sound.
 * The session forwards standard system/Bluetooth transport commands into
 * the same queue operations as the UI buttons.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class SessionQueuePlayer(
    private val playback: PlaybackController
) : ForwardingPlayer(playback.sessionPlayer) {
    override fun seekToNext() = playback.next()
    override fun seekToPrevious() = playback.previous()
    override fun seekToNextMediaItem() = playback.next()
    override fun seekToPreviousMediaItem() = playback.previous()

    // System progress scrubbing must respect the same active A–B range and
    // seek announcements as the in-app sliders and waveform.
    override fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex == 0) playback.seekTo(positionMs)
    }

    // Advertise standard transport commands to Media3's notification and
    // lock-screen controller even though ExoPlayer decodes only one item.
    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean =
        command == Player.COMMAND_SEEK_TO_NEXT ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS ||
            command == Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ||
            command == Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM ||
            super.isCommandAvailable(command)
}
