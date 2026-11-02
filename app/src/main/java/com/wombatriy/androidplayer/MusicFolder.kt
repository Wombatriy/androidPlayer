package com.wombatriy.androidplayer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** Reads the user-selected SAF tree. No broad storage permission is required. */
object MusicFolder {
    private val extensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")

    fun load(context: Context, treeUri: Uri): List<Track> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = mutableListOf<Track>()
        fun walk(folder: DocumentFile) {
            folder.listFiles().forEach { file ->
                if (file.isDirectory) walk(file)
                else if (file.isFile && file.name.orEmpty().substringAfterLast('.', "").lowercase() in extensions) {
                    result += Track(file.uri, file.name ?: "Unknown")
                }
            }
        }
        walk(root)
        return result.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
