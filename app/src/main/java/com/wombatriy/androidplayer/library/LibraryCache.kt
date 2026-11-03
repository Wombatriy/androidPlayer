package com.wombatriy.androidplayer.library

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import com.wombatriy.androidplayer.Track
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Last successfully scanned folder, stored in private app storage.
 * Always use on a worker thread. Adding/removing files requires Rescan.
 */
class LibraryCache(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "library-snapshot.json"))

    fun read(folder: Uri): List<Track>? = try {
        val root = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
        if (root.optInt("version") != VERSION || root.optString("folder") != folder.toString()) {
            null
        } else {
            val array = root.getJSONArray("tracks")
            ArrayList<Track>(array.length()).also { list ->
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    val parent = item.optString("parentUri").takeIf { it.isNotBlank() }
                    list.add(Track(
                        Uri.parse(item.getString("uri")),
                        item.getString("name"),
                        parent?.let(Uri::parse)
                    ))
                }
            }
        }
    } catch (_: Exception) {
        null // Missing or corrupted cache: perform a real scan.
    }

    fun write(folder: Uri, tracks: List<Track>) {
        val items = JSONArray()
        for (track in tracks) {
            items.put(JSONObject()
                .put("uri", track.uri.toString())
                .put("name", track.name)
                .put("parentUri", track.parentUri?.toString() ?: "")
            )
        }
        val bytes = JSONObject()
            .put("version", VERSION)
            .put("folder", folder.toString())
            .put("tracks", items)
            .toString()
            .toByteArray(Charsets.UTF_8)

        var output: FileOutputStream? = null
        try {
            output = file.startWrite()
            output.write(bytes)
            file.finishWrite(output)
        } catch (e: Exception) {
            output?.let(file::failWrite)
            throw e
        }
    }

    companion object { private const val VERSION = 2 }
}
