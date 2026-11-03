package com.wombatriy.androidplayer.library

import java.util.Locale

/**
 * One order for the virtual All Tracks playlist:
 * numeric active BPM ascending, then files with no BPM by title.
 * Source (filename/manual/detected) does not alter ordering.
 */
object LibraryOrder {
    private data class Item<T>(
        val value: T,
        val bpm: Double?,
        val name: String,
        val originalPosition: Int
    )

    fun <T> sorted(
        tracks: List<T>,
        effectiveBpm: (T) -> Double?,
        displayName: (T) -> String
    ): List<T> {
        val indexed = tracks.mapIndexed { index, track ->
            val bpm = effectiveBpm(track)?.takeIf { it.isFinite() && it > 0.0 }
            Item(track, bpm, displayName(track).lowercase(Locale.ROOT), index)
        }
        return indexed.sortedWith(
            compareBy<Item<T>> { it.bpm == null }
                .thenBy { it.bpm ?: Double.POSITIVE_INFINITY }
                .thenBy { it.name }
                .thenBy { it.originalPosition }
        ).map { it.value }
    }
}
