package com.wombatriy.androidplayer

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.DocumentsContract
import com.wombatriy.androidplayer.library.ExcludedFolderPolicy
import androidx.documentfile.provider.DocumentFile
import java.util.ArrayDeque
import java.util.concurrent.CancellationException

/**
 * Recursively enumerate a SAF tree on a worker.
 *
 * SAF providers do not expose a reliable total number of files/folders, so
 * progress is indeterminate with a continuously increasing found-file count.
 * Selecting a folder and pressing Rescan both use this same method.
 */
object MusicFolder {
    data class ScanProgress(val filesFound: Int, val foldersVisited: Int)

    private val extensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")

    fun load(
        context: Context,
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit = {},
        cancelled: () -> Boolean = { false }
    ): List<Track> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        // SAF grants refer to document identities, not reliable filesystem paths.
        // A destination may be inside this tree and have any display name.
        val excluded = context.getSharedPreferences("library", Context.MODE_PRIVATE)
            .getStringSet("excludedDestinationTrees", emptySet()).orEmpty()
            .mapNotNull { encoded ->
                runCatching {
                    val uri = Uri.parse(encoded)
                    ExcludedFolderPolicy.FolderIdentity(
                        uri.authority, DocumentsContract.getTreeDocumentId(uri)
                    )
                }.getOrNull()
            }.toSet()
        val result = ArrayList<Track>()
        val pending = ArrayDeque<DocumentFile>()
        pending.addLast(root)
        var visited = 0
        var lastNotifiedAt = 0L

        fun report(force: Boolean = false) {
            val now = SystemClock.elapsedRealtime()
            if (force || now - lastNotifiedAt >= 175L) {
                lastNotifiedAt = now
                onProgress(ScanProgress(result.size, visited))
            }
        }

        report(force = true)
        while (pending.isNotEmpty()) {
            if (cancelled() || Thread.currentThread().isInterrupted) {
                throw CancellationException("Folder scan cancelled")
            }
            val folder = pending.removeLast()
            visited++
            for (file in folder.listFiles()) {
                if (cancelled() || Thread.currentThread().isInterrupted) {
                    throw CancellationException("Folder scan cancelled")
                }
                if (file.isDirectory) {
                    // A folder named excluded is always skipped. Selected
                    // destinations with any other name are skipped by SAF ID.
                    val identity = runCatching {
                        ExcludedFolderPolicy.FolderIdentity(
                            file.uri.authority, DocumentsContract.getDocumentId(file.uri)
                        )
                    }.getOrNull()
                    if (ExcludedFolderPolicy.shouldTraverse(file.name, identity, excluded)) {
                        pending.addLast(file)
                    }
                } else if (file.isFile) {
                    val name = file.name ?: continue
                    if (name.substringAfterLast('.', "").lowercase() in extensions) {
                        result.add(Track(file.uri, name, folder.uri))
                    }
                }
                report()
            }
            report()
        }
        result.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        report(force = true)
        return result
    }
}
