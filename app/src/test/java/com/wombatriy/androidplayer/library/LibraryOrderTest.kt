package com.wombatriy.androidplayer.library

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrderTest {
    private data class Song(val name: String, val bpm: Double?)

    @Test fun sortsNumericallyNotLexicographically() {
        val tracks = listOf(
            Song("90 - Tune", 90.0),
            Song("312 - Fast Jazz", 312.0),
            Song("120 - Song", 120.0),
            Song("100 - Song", 100.0)
        )
        assertEquals(listOf(90.0, 100.0, 120.0, 312.0),
            LibraryOrder.sorted(tracks, { it.bpm }, { it.name }).map { it.bpm })
    }

    @Test fun detectedFilenameAndManualShareOneOrder() {
        val tracks = listOf(
            Song("unknown filename A", 140.0),
            Song("128 - file prefix", 128.0),
            Song("manual edited", 100.0),
            Song("unknown filename B", null)
        )
        assertEquals(listOf("manual edited", "128 - file prefix", "unknown filename A",
            "unknown filename B"), LibraryOrder.sorted(tracks, { it.bpm }, { it.name })
                .map { it.name })
    }

    @Test fun unknownValuesSortAlphabeticallyAfterKnown() {
        val tracks = listOf(Song("zebra", null), Song("known", 90.0), Song("Alpha", null))
        assertEquals(listOf("known", "Alpha", "zebra"),
            LibraryOrder.sorted(tracks, { it.bpm }, { it.name }).map { it.name })
    }

    @Test fun sameBpmBreaksTiesByTitle() {
        val tracks = listOf(Song("Zoo", 120.0), Song("Alpha", 120.0))
        assertEquals(listOf("Alpha", "Zoo"),
            LibraryOrder.sorted(tracks, { it.bpm }, { it.name }).map { it.name })
    }

    @Test fun invalidNumericValuesAreUnknown() {
        val tracks = listOf(Song("NaN", Double.NaN), Song("valid", 110.0))
        assertEquals(listOf("valid", "NaN"),
            LibraryOrder.sorted(tracks, { it.bpm }, { it.name }).map { it.name })
    }
}
