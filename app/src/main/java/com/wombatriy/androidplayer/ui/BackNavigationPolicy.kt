package com.wombatriy.androidplayer.ui

/**
 * Android Back is UI navigation only; it must never call player.pause() or
 * release(). Tab 0 = Player, 1 = Library, 2 = Playlists, 3 = Queue.
 */
object BackNavigationPolicy {
    const val BACKGROUND_APP = -1
    const val PLAYER = 0
    const val LIBRARY = 1
    const val PLAYLISTS = 2
    const val QUEUE = 3

    fun destination(currentTab: Int, fromPlaylist: Boolean): Int =
        when (currentTab) {
            PLAYER -> if (fromPlaylist) QUEUE else LIBRARY
            LIBRARY -> BACKGROUND_APP
            PLAYLISTS, QUEUE -> LIBRARY
            else -> LIBRARY
        }
}
