package com.wombatriy.androidplayer.playlists

import android.content.Context
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.player.PlaybackController

/**
 * Bridges the local playlist catalog with the actual media playback queue.
 * Keeps MainActivity free of playlist lookup, source, and URI-migration logic.
 *
 * Library is a virtual "All Tracks" playlist. User playlists are intentionally
 * ordered by insertion/manual movement, never by BPM or filename.
 */
class PlaylistCoordinator(
    context: Context,
    private val playback: PlaybackController
) {
    val store = PlaylistStore(context)
    private var available: Map<String, Track> = emptyMap()
    private var libraryOrder: List<Track> = emptyList()

    fun resolve(uri: String): Track? = available[uri]

    fun onLibraryLoaded(tracks: List<Track>) {
        available = tracks.associateBy { it.uri.toString() }
        refreshActivePlaylist()
    }

    fun onLibraryOrder(sorted: List<Track>) {
        libraryOrder = sorted
        playback.updateLibraryOrder(sorted)
    }

    fun play(track: Track, playlist: UserPlaylist, orderedAvailable: List<Track>) {
        playback.load(track, orderedAvailable, playlist.id, playlist.name)
    }

    /** Called after add/remove/move/rename/delete a playlist. */
    fun onPlaylistChanged(id: String?) {
        if (id == null) return
        val playlist = store.find(id)
        if (playlist == null) {
            playback.onPlaylistDeleted(id, libraryOrder)
        } else {
            playback.updatePlaylistOrder(
                playlist.id,
                playlist.name,
                playlist.trackUris.mapNotNull(available::get)
            )
        }
    }

    /**
     * Only user playlists are editable; the virtual All Tracks list stays
     * deterministically sorted by BPM. Playback is not restarted.
     */
    fun reorderActive(movingUri: String, targetUri: String, afterTarget: Boolean): Boolean {
        val id = playback.activePlaylistId ?: return false
        val changed = store.moveRelative(id, movingUri, targetUri, afterTarget)
        if (changed) onPlaylistChanged(id)
        return changed
    }

    fun onTrackRenamed(before: Track, after: Track, tracks: List<Track>, restart: Boolean) {
        store.replaceUri(before.uri.toString(), after.uri.toString())
        available = tracks.associateBy { it.uri.toString() }
        playback.onLibraryTrackRenamed(before, after, restart)
        refreshActivePlaylist()
    }

    private fun refreshActivePlaylist() {
        playback.activePlaylistId?.let(::onPlaylistChanged)
    }
}
