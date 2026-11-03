package com.wombatriy.androidplayer.playlists

/**
 * Pure immutable playlist operations; an ordered list of SAF URI strings.
 * Playlists never copy, move or sort the underlying music files.
 */
data class UserPlaylist(
    val id: String,
    val name: String,
    val trackUris: List<String>
)

object PlaylistCatalog {
    fun create(all: List<UserPlaylist>, id: String, name: String): List<UserPlaylist> {
        val validName = normalizedName(name)
        require(id.isNotBlank() && all.none { it.id == id }) { "Invalid playlist ID" }
        require(all.none { it.name.equals(validName, ignoreCase = true) }) {
            "A playlist with this name already exists"
        }
        return all + UserPlaylist(id, validName, emptyList())
    }

    fun rename(all: List<UserPlaylist>, id: String, name: String): List<UserPlaylist> {
        val validName = normalizedName(name)
        require(all.any { it.id == id }) { "Playlist no longer exists" }
        require(all.none { it.id != id && it.name.equals(validName, ignoreCase = true) }) {
            "A playlist with this name already exists"
        }
        return all.map { if (it.id == id) it.copy(name = validName) else it }
    }

    fun delete(all: List<UserPlaylist>, id: String): List<UserPlaylist> =
        all.filterNot { it.id == id }

    fun add(all: List<UserPlaylist>, id: String, uri: String): List<UserPlaylist> {
        require(uri.isNotBlank())
        return edit(all, id) { playlist ->
            if (uri in playlist.trackUris) playlist
            else playlist.copy(trackUris = playlist.trackUris + uri)
        }
    }

    fun remove(all: List<UserPlaylist>, id: String, uri: String): List<UserPlaylist> =
        edit(all, id) { it.copy(trackUris = it.trackUris.filterNot { saved -> saved == uri }) }

    /** Move by one position, preserving all other items' relative order. */
    fun move(all: List<UserPlaylist>, id: String, uri: String, delta: Int): List<UserPlaylist> {
        require(delta == -1 || delta == 1)
        return edit(all, id) { playlist ->
            val index = playlist.trackUris.indexOf(uri)
            val target = index + delta
            if (index < 0 || target !in playlist.trackUris.indices) playlist
            else {
                val copy = playlist.trackUris.toMutableList()
                val entry = copy.removeAt(index)
                copy.add(target, entry)
                playlist.copy(trackUris = copy)
            }
        }
    }


    /**
     * Drag and drop into the slot BEFORE or AFTER the drop target.
     * URI identity is used instead of indices: missing/inaccessible songs stay
     * in their old relative position even if they aren't in the playback queue.
     */
    fun moveRelative(
        all: List<UserPlaylist>,
        id: String,
        movingUri: String,
        targetUri: String,
        afterTarget: Boolean
    ): List<UserPlaylist> = edit(all, id) { playlist ->
        val src = playlist.trackUris.indexOf(movingUri)
        val dst = playlist.trackUris.indexOf(targetUri)
        if (src < 0 || dst < 0 || src == dst) playlist
        else {
            val ordered = playlist.trackUris.toMutableList()
            ordered.removeAt(src)
            val index = ordered.indexOf(targetUri) + if (afterTarget) 1 else 0
            ordered.add(index, movingUri)
            if (ordered == playlist.trackUris) playlist
            else playlist.copy(trackUris = ordered)
        }
    }

    /** SAF rename may return a different URI. Keep the item in the same slot. */
    fun replaceUri(all: List<UserPlaylist>, oldUri: String, newUri: String): List<UserPlaylist> {
        if (oldUri == newUri) return all
        return all.map { playlist ->
            if (oldUri !in playlist.trackUris) playlist
            else playlist.copy(trackUris = playlist.trackUris
                .map { if (it == oldUri) newUri else it }
                .distinct())
        }
    }

    private fun edit(
        all: List<UserPlaylist>,
        id: String,
        change: (UserPlaylist) -> UserPlaylist
    ): List<UserPlaylist> {
        require(all.any { it.id == id }) { "Playlist no longer exists" }
        return all.map { if (it.id == id) change(it) else it }
    }

    private fun normalizedName(name: String): String {
        val cleaned = name.trim()
        require(cleaned.isNotEmpty() && cleaned.length <= 60) {
            "Playlist name must contain 1–60 characters"
        }
        return cleaned
    }
}
