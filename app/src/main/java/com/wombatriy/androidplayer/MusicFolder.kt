package com.wombatriy.androidplayer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque

/** Recursively enumerates a SAF tree off the UI thread. */
object MusicFolder {
    private val extensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")

    fun load(context: Context, treeUri: Uri): List<Track> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = ArrayList<Track>()
        val pending = ArrayDeque<DocumentFile>()
        pending.addLast(root)

        while (pending.isNotEmpty()) {
            val folder = pending.removeLast()
            folder.listFiles().forEach { file ->
                if (file.isDirectory) {
                    pending.addLast(file)
                } else if (file.isFile) {
                    val name = file.name ?: return@forEach
                    if (name.substringAfterLast('.', "").lowercase() in extensions) {
                        result.add(Track(file.uri, name))
                    }
                }
            }
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        return result
    }
}
