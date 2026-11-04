package com.wombatriy.androidplayer.playlists

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Tiny app-private catalog. SharedPreferences.apply() keeps updates visible
 * immediately and persists them asynchronously, avoiding UI-thread disk writes.
 * The only stored track data is a list of content URI strings in chosen order.
 */
class PlaylistStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("user-playlists-v1", Context.MODE_PRIVATE)

    private var entries: List<UserPlaylist> = read()
    val playlists: List<UserPlaylist> get() = entries

    fun find(id: String): UserPlaylist? = entries.firstOrNull { it.id == id }

    fun create(name: String): UserPlaylist {
        val id = UUID.randomUUID().toString()
        set(PlaylistCatalog.create(entries, id, name))
        return find(id)!!
    }

    fun rename(id: String, name: String) = set(PlaylistCatalog.rename(entries, id, name))
    fun delete(id: String) = set(PlaylistCatalog.delete(entries, id))
    fun add(id: String, uri: String) = set(PlaylistCatalog.add(entries, id, uri))
    fun remove(id: String, uri: String) = set(PlaylistCatalog.remove(entries, id, uri))
    fun move(id: String, uri: String, delta: Int) =
        set(PlaylistCatalog.move(entries, id, uri, delta))

    /** @return true when the stored order really changed. */
    fun moveRelative(
        id: String, movingUri: String, targetUri: String, afterTarget: Boolean
    ): Boolean {
        val updated = PlaylistCatalog.moveRelative(
            entries, id, movingUri, targetUri, afterTarget
        )
        if (updated == entries) return false
        set(updated)
        return true
    }

    /** Saves one randomized URI order; no audio files are touched. */
    fun shuffle(id: String): Boolean {
        val updated = PlaylistCatalog.shuffle(entries, id)
        if (updated == entries) return false
        set(updated)
        return true
    }

    /** Remove stale entries from every playlist after a file is gone. */
    fun removeUriEverywhere(uri: String): Set<String> {
        val affected = entries.filter { uri in it.trackUris }.map { it.id }.toSet()
        if (affected.isNotEmpty()) {
            set(entries.map { playlist ->
                if (playlist.id in affected) {
                    playlist.copy(trackUris = playlist.trackUris.filterNot { it == uri })
                } else playlist
            })
        }
        return affected
    }

    fun replaceUri(before: String, after: String) =
        set(PlaylistCatalog.replaceUri(entries, before, after))

    private fun set(next: List<UserPlaylist>) {
        if (next == entries) return
        entries = next
        val json = JSONObject().put("version", 1)
        val all = JSONArray()
        for (playlist in entries) {
            val uris = JSONArray()
            playlist.trackUris.forEach { uris.put(it) }
            all.put(JSONObject().put("id", playlist.id)
                .put("name", playlist.name)
                .put("tracks", uris))
        }
        json.put("lists", all)
        prefs.edit().putString(KEY_CATALOG, json.toString()).apply()
    }

    private fun read(): List<UserPlaylist> {
        val saved = prefs.getString(KEY_CATALOG, null) ?: return emptyList()
        return runCatching {
            val json = JSONObject(saved)
            require(json.getInt("version") == 1)
            val lists = json.getJSONArray("lists")
            buildList {
                for (index in 0 until lists.length()) {
                    val item = lists.getJSONObject(index)
                    val tracks = item.getJSONArray("tracks")
                    val uris = ArrayList<String>(tracks.length())
                    for (j in 0 until tracks.length()) uris.add(tracks.getString(j))
                    add(UserPlaylist(item.getString("id"), item.getString("name"), uris.distinct()))
                }
            }
        }.getOrElse { emptyList() }
    }

    companion object { private const val KEY_CATALOG = "catalog" }
}
