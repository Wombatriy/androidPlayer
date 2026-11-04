package com.wombatriy.androidplayer.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchPolicyTest {
    private val entries = listOf(
        LibrarySearchPolicy.Entry("a", "128 - Autumn Jazz.mp3"),
        LibrarySearchPolicy.Entry("b", "Night Swing.mp3"),
        LibrarySearchPolicy.Entry("c", "99 - Evening Jazz.mp3"),
        LibrarySearchPolicy.Entry("d", "236 - Blue Tango.flac"),
        LibrarySearchPolicy.Entry("e", "Jazz Solo.wav")
    )

    @Test fun emptySearchPutsMissingPrefixFirstInStableOrder() {
        assertEquals(listOf("b", "c", "e", "a", "d"),
            LibrarySearchPolicy.results(entries, "").map { it.item })
        assertEquals(listOf("b", "c", "e", "a", "d"),
            LibrarySearchPolicy.results(entries, "  ").map { it.item })
    }

    @Test fun prefixMustHaveExactlyThreeDigitsAndDash() {
        assertFalse(LibrarySearchPolicy.needsFilenameBpm("128 - Song.mp3"))
        assertFalse(LibrarySearchPolicy.needsFilenameBpm("236 - Song.flac"))
        assertTrue(LibrarySearchPolicy.needsFilenameBpm("99 - Song.mp3"))
        assertTrue(LibrarySearchPolicy.needsFilenameBpm("Song.mp3"))
        assertTrue(LibrarySearchPolicy.needsFilenameBpm("1234 - Song.mp3"))
        assertTrue(LibrarySearchPolicy.needsFilenameBpm("128 Song.mp3"))
    }

    @Test fun typedQueryPreservesOriginalLibraryOrderAndIgnoresCase() {
        assertEquals(listOf("a", "c", "e"),
            LibrarySearchPolicy.results(entries, "jAzZ").map { it.item })
        assertEquals(listOf("b"),
            LibrarySearchPolicy.results(entries, "SWING").map { it.item })
        assertEquals(emptyList<String>(),
            LibrarySearchPolicy.results(entries, "unmatched").map { it.item })
    }

    @Test fun sourceListNeverChanges() {
        LibrarySearchPolicy.results(entries, "")
        assertEquals(listOf("a", "b", "c", "d", "e"), entries.map { it.item })
    }
}
