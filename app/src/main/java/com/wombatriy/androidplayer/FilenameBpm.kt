package com.wombatriy.androidplayer

import kotlin.math.roundToInt

/** Filename BPM and non-destructive rename naming policy, free of Android APIs. */
object FilenameBpm {
    // The BPM must be a leading whole number followed by " - " and a title.
    private val prefix = Regex("""^(\d{2,3})\s+-\s+(.+)$""")

    fun parse(filename: String): Int? {
        val name = filename.substringBeforeLast('.', filename)
        val match = prefix.matchEntire(name.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 20..800 }
    }

    /**
     * Generate a preview without changing the file or inferring missing artist.
     * Original extension is retained exactly, and an existing BPM prefix is
     * replaced rather than stacked.
     */
    fun propose(filename: String, bpm: Double): String {
        require(bpm.isFinite() && bpm in 20.0..800.0)
        require(filename.isNotBlank() && !filename.contains('/') &&
            !filename.contains('\\') && !filename.contains('\n') && !filename.contains('\r'))
        val dot = filename.lastIndexOf('.')
        require(dot > 0 && dot < filename.lastIndex) { "File must have an extension" }
        val stem = filename.substring(0, dot).trim()
        val extension = filename.substring(dot)
        val title = prefix.matchEntire(stem)?.groupValues?.get(2) ?: stem
        require(title.isNotBlank())
        return "${bpm.roundToInt()} - $title$extension"
    }

    /** A file provider may be case-insensitive; reject case-only collisions. */
    fun collides(candidate: String, original: String, siblings: Iterable<String>): Boolean =
        siblings.any { it.equals(candidate, ignoreCase = true) &&
            !it.equals(original, ignoreCase = true) }
}
