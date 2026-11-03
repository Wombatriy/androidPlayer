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

    /** Replace a renamed reference without resetting playback position. */
    fun replaceReference(before: T, after: T) {
        val index = items.indexOf(before)
        if (index >= 0) {
            val copy = items.toMutableList()
            copy[index] = after
            items = copy
        }
    }

    /**
     * The virtual All Tracks list can be reordered as batch BPM estimates arrive.
     * Keep the current track selected by identity, never restart its media.
     * A future playlist queue will only call this for its own source.
     */
    fun updateOrder(source: List<T>, sameItem: (T, T) -> Boolean) {
        val selected = current ?: return
        val snapshot = source.toList()
        val index = snapshot.indexOfFirst { sameItem(it, selected) }
        if (index < 0) return
        items = snapshot
        position = index
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
