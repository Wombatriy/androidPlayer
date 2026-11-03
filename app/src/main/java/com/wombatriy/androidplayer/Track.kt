package com.wombatriy.androidplayer

import android.net.Uri

/**
 * The parent SAF document is recorded during folder scanning so rename can
 * check sibling collisions without recursively walking the entire library.
 */
data class Track(val uri: Uri, val name: String, val parentUri: Uri? = null) {
    val filenameBpm: Int? get() = FilenameBpm.parse(name)
}
