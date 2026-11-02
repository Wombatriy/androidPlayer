package com.wombatriy.androidplayer.player

/**
 * Ordered selection independent of Android and the media engine.
 * Navigation never wraps at the beginning or end.
 */
class PlaybackQueue<T> {
    private var items: List<T> = emptyList()
    var position: Int = -1
        private set

    val size: Int get() = items.size
    val hasPrevious: Boolean get() = position > 0
    val hasNext: Boolean get() = position >= 0 && position < items.lastIndex
    val current: T? get() = items.getOrNull(position)

    fun select(selected: T, source: List<T>) {
        val snapshot = source.toList()
        val index = snapshot.indexOf(selected)
        if (index >= 0) {
            items = snapshot
            position = index
        } else {
            items = listOf(selected)
            position = 0
        }
    }

    fun previous(): T? {
        if (!hasPrevious) return null
        return items[--position]
    }

    fun next(): T? {
        if (!hasNext) return null
        return items[++position]
    }
}

enum class EndMode(val caption: String) {
    STOP("Stop"),
    NEXT("Next"),
    REPEAT("Repeat")
}
