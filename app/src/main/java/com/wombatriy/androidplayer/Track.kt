package com.wombatriy.androidplayer

import android.net.Uri

data class Track(val uri: Uri, val name: String) {
    val filenameBpm: Int?
        get() = BPM_PREFIX_REGEX
            .matchEntire(name.substringBeforeLast('.', name))
            ?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 30..300 }

    companion object {
        private val BPM_PREFIX_REGEX = Regex("""^(\d{2,3})\s+-\s+.+""")
    }
}
