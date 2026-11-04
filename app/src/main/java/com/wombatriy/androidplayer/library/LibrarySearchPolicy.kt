package com.wombatriy.androidplayer.library

/**
 * Small pure-Kotlin search policy. The underlying library order is never
 * changed, and no filesystem or BPM lookup occurs while typing.
 */
object LibrarySearchPolicy {
    data class Entry<T>(val item: T, val title: String, val detected: Boolean)

    fun <T> results(entries: List<Entry<T>>, query: String): List<Entry<T>> {
        val text = query.trim()
        if (text.isNotEmpty()) {
            return entries.filter { it.title.contains(text, ignoreCase = true) }
        }
        // Stable partition: detected BPM sources first, retaining relative
        // library order in both sections.
        val detected = ArrayList<Entry<T>>()
        val other = ArrayList<Entry<T>>()
        entries.forEach { if (it.detected) detected.add(it) else other.add(it) }
        detected.addAll(other)
        return detected
    }
}
