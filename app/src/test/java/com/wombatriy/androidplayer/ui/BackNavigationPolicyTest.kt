package com.wombatriy.androidplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationPolicyTest {
    @Test fun playerBackFromAllTracksReturnsToLibrary() {
        assertEquals(
            BackNavigationPolicy.LIBRARY,
            BackNavigationPolicy.destination(BackNavigationPolicy.PLAYER, false)
        )
    }

    @Test fun playerBackFromUserPlaylistReturnsToQueue() {
        assertEquals(
            BackNavigationPolicy.QUEUE,
            BackNavigationPolicy.destination(BackNavigationPolicy.PLAYER, true)
        )
    }

    @Test fun libraryBackBackgroundsApp() {
        assertEquals(
            BackNavigationPolicy.BACKGROUND_APP,
            BackNavigationPolicy.destination(BackNavigationPolicy.LIBRARY, false)
        )
    }

    @Test fun playlistAndQueueBackReturnToLibrary() {
        assertEquals(
            BackNavigationPolicy.LIBRARY,
            BackNavigationPolicy.destination(BackNavigationPolicy.PLAYLISTS, true)
        )
        assertEquals(
            BackNavigationPolicy.LIBRARY,
            BackNavigationPolicy.destination(BackNavigationPolicy.QUEUE, true)
        )
    }
}
