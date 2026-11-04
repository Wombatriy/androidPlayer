package com.wombatriy.androidplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySearchPolicyTest {
    private val entries = listOf(
        LibrarySearchPolicy.Entry("a", "Autumn Jazz", false),
        LibrarySearchPolicy.Entry("b", "Night Swing", true),
        LibrarySearchPolicy.Entry("c", "Evening Jazz", true),
        LibrarySearchPolicy.Entry("d", "Blue Tango", false)
    )

    @Test fun emptyQueryPutsDetectedFirstKeepingLibraryOrder() {
        assertEquals(listOf("b", "c", "a", "d"),
            LibrarySearchPolicy.results(entries, "").map { it.item })
        assertEquals(listOf("b", "c", "a", "d"),
            LibrarySearchPolicy.results(entries, "   ").map { it.item })
    }

    @Test fun typedQueryUsesCaseInsensitiveContainsAndOriginalOrder() {
        assertEquals(listOf("a", "c"),
            LibrarySearchPolicy.results(entries, "jAzZ").map { it.item })
        assertEquals(listOf("b"),
            LibrarySearchPolicy.results(entries, "SWING").map { it.item })
        assertEquals(emptyList<String>(),
            LibrarySearchPolicy.results(entries, "not a song").map { it.item })
    }

    @Test fun originalListIsUnchanged() {
        LibrarySearchPolicy.results(entries, "")
        assertEquals(listOf("a", "b", "c", "d"), entries.map { it.item })
    }
}
