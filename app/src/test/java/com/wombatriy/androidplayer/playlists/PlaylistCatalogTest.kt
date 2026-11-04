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

    @Test fun dragDownInsertsAfterTargetAndKeepsOtherOrder() {
        var all = base()
        listOf("a", "b", "c", "d").forEach {
            all = PlaylistCatalog.add(all, "id-1", it)
        }
        all = PlaylistCatalog.moveRelative(all, "id-1", "a", "c", afterTarget = true)
        assertEquals(listOf("b", "c", "a", "d"), all.single().trackUris)
    }

    @Test fun dragUpInsertsBeforeTarget() {
        var all = base()
        listOf("a", "b", "c", "d").forEach {
            all = PlaylistCatalog.add(all, "id-1", it)
        }
        all = PlaylistCatalog.moveRelative(all, "id-1", "d", "b", afterTarget = false)
        assertEquals(listOf("a", "d", "b", "c"), all.single().trackUris)
    }

    @Test fun dragRetainsUnavailableUriBetweenPlayableEntries() {
        var all = base()
        listOf("a", "missing", "b", "c").forEach {
            all = PlaylistCatalog.add(all, "id-1", it)
        }
        all = PlaylistCatalog.moveRelative(all, "id-1", "c", "b", afterTarget = false)
        assertEquals(listOf("a", "missing", "c", "b"), all.single().trackUris)
    }

    @Test fun dragIsNoOpForMissingOrSameItem() {
        var all = base()
        listOf("a", "b").forEach { all = PlaylistCatalog.add(all, "id-1", it) }
        assertEquals(all,
            PlaylistCatalog.moveRelative(all, "id-1", "a", "a", afterTarget = true))
        assertEquals(all,
            PlaylistCatalog.moveRelative(all, "id-1", "gone", "a", afterTarget = true))
        assertEquals(all,
            PlaylistCatalog.moveRelative(all, "id-1", "a", "gone", afterTarget = true))
    }

    @Test fun safRenamePreservesSlotsAndDeduplicates() {
        var all = PlaylistCatalog.add(base(), "id-1", "content://old")
        all = PlaylistCatalog.add(all, "id-1", "content://next")
        all = PlaylistCatalog.replaceUri(all, "content://old", "content://new")
        assertEquals(listOf("content://new", "content://next"), all[0].trackUris)
    }

    @Test fun shufflePersistsPermutationWithoutChangingOtherPlaylists() {
        var all = PlaylistCatalog.create(base(), "id-2", "Swing")
        listOf("a", "b", "c", "missing", "d").forEach { uri ->
            all = PlaylistCatalog.add(all, "id-1", uri)
        }
        all = PlaylistCatalog.add(all, "id-2", "other")
        val before = all[0].trackUris
        val shuffled = PlaylistCatalog.shuffle(all, "id-1", kotlin.random.Random(42))
        assertEquals(before.toSet(), shuffled[0].trackUris.toSet())
        assertEquals(before.size, shuffled[0].trackUris.size)
        assertNotEquals(before, shuffled[0].trackUris)
        assertEquals(all[1], shuffled[1])
        assertEquals("id-1", shuffled[0].id)
    }

    @Test fun shuffleDoesNotChangeEmptyOrSingleTrackPlaylist() {
        val empty = base()
        assertEquals(empty, PlaylistCatalog.shuffle(empty, "id-1"))
        val single = PlaylistCatalog.add(empty, "id-1", "only")
        assertEquals(single, PlaylistCatalog.shuffle(single, "id-1"))
    }

    @Test fun shuffleFailsForMissingPlaylist() {
        assertThrows(IllegalArgumentException::class.java) {
            PlaylistCatalog.shuffle(base(), "absent")
        }
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
