package com.wombatriy.androidplayer.playlists

import org.junit.Assert.*
import org.junit.Test

class PlaylistCatalogTest {
    private fun base(): List<UserPlaylist> =
        PlaylistCatalog.create(emptyList(), "id-1", "Jazz")

    @Test fun insertionOrderIsNeverSorted() {
        var all = base()
        for (uri in listOf("content://z", "content://a", "content://c")) {
            all = PlaylistCatalog.add(all, "id-1", uri)
        }
        assertEquals(listOf("content://z", "content://a", "content://c"),
            all[0].trackUris)
    }

    @Test fun addingSameFileTwiceKeepsSingleEntry() {
        val once = PlaylistCatalog.add(base(), "id-1", "content://one")
        assertEquals(once, PlaylistCatalog.add(once, "id-1", "content://one"))
    }

    @Test fun reorderingSwapsExactlyOnePosition() {
        var all = base()
        listOf("a","b","c").forEach { all = PlaylistCatalog.add(all, "id-1", it) }
        all = PlaylistCatalog.move(all, "id-1", "c", -1)
        assertEquals(listOf("a","c","b"), all[0].trackUris)
        assertEquals(all, PlaylistCatalog.move(all, "id-1", "a", -1))
    }

    @Test fun safRenamePreservesSlotsAndDeduplicates() {
        var all = PlaylistCatalog.add(base(), "id-1", "content://old")
        all = PlaylistCatalog.add(all, "id-1", "content://next")
        all = PlaylistCatalog.replaceUri(all, "content://old", "content://new")
        assertEquals(listOf("content://new", "content://next"), all[0].trackUris)
    }

    @Test fun renameAndDeleteDoNotTouchOtherPlaylists() {
        var all = PlaylistCatalog.create(base(), "id-2", "Swing")
        all = PlaylistCatalog.rename(all, "id-1", "Slow jazz")
        assertEquals("Swing", all[1].name)
        all = PlaylistCatalog.delete(all, "id-1")
        assertEquals(listOf("Swing"), all.map { it.name })
    }

    @Test fun duplicateNamesRejectedIgnoringCase() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaylistCatalog.create(base(), "id-2", " JAZZ ")
        }
    }
}
