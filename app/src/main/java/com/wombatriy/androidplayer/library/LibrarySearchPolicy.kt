package com.wombatriy.androidplayer.library

/**
 * Pure search logic. With no query, list filenames missing the requested
 * three-digit BPM prefix first; keep the existing Library order in both groups.
 */
object LibrarySearchPolicy {
    data class Entry<T>(val item: T, val title: String)

    private val bpmPrefix = Regex("""^\d{3}\s+-\s+""")

    fun needsFilenameBpm(filename: String): Boolean =
        !bpmPrefix.containsMatchIn(filename)

    fun <T> results(entries: List<Entry<T>>, query: String): List<Entry<T>> {
        val text = query.trim()
        if (text.isNotEmpty()) {
            return entries.filter { it.title.contains(text, ignoreCase = true) }
        }
        val withoutPrefix = ArrayList<Entry<T>>()
        val withPrefix = ArrayList<Entry<T>>()
        entries.forEach { if (needsFilenameBpm(it.title)) withoutPrefix.add(it) else withPrefix.add(it) }
        withoutPrefix.addAll(withPrefix)
        return withoutPrefix
    }
}
