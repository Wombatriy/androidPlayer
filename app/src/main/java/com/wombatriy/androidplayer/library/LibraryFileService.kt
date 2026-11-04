package com.wombatriy.androidplayer.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.bpm.BpmRepository
import com.wombatriy.androidplayer.playlists.PlaylistStore
import com.wombatriy.androidplayer.waveform.WaveformCache
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Destructive SAF operations, always run off the UI thread and only after
 * explicit confirmation from MainActivity.
 *
 * "Exclude" is a verified copy into a user-selected destination folder,
 * followed by deletion of the original. A failed or unverified copy NEVER
 * causes deletion of the original source file.
 */
class LibraryFileService(context: Context) {
    enum class Action { DELETE, EXCLUDE }
    data class Outcome(
        val action: Action,
        val updated: List<Track>,
        val cacheSaved: Boolean,
        val metadataCleared: Boolean,
        val destinationName: String? = null
    )

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val cache = LibraryCache(app)
    private val bpm = BpmRepository(app)
    private val playlistStore = PlaylistStore(app)
    private val waveform = WaveformCache(app)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun perform(
        action: Action,
        track: Track,
        libraryTree: Uri,
        destinationTree: Uri?,
        tracks: List<Track>,
        callback: (Result<Outcome>) -> Unit
    ) {
        executor.execute {
            val result = runCatching {
                require(tracks.any { it.uri == track.uri }) {
                    "Track was already removed. Rescan the library."
                }
                checkWritePermission(libraryTree)
                check(!Thread.currentThread().isInterrupted) { "Operation cancelled" }
                val destinationName = when (action) {
                    Action.DELETE -> {
                        require(DocumentsContract.deleteDocument(resolver, track.uri)) {
                            "The provider refused to delete the file."
                        }
                        null
                    }
                    Action.EXCLUDE -> {
                        val targetTree = requireNotNull(destinationTree) {
                            "Choose the folder where excluded files should be saved."
                        }
                        checkWritePermission(targetTree)
                        // SAF URIs may differ while referring to the same
                        // document root; compare provider identity, not paths.
                        val sameRoot = targetTree.authority == libraryTree.authority &&
                            DocumentsContract.getTreeDocumentId(targetTree) ==
                            DocumentsContract.getTreeDocumentId(libraryTree)
                        check(!sameRoot) {
                            "Destination cannot be the music Library root. " +
                                "Select a separate folder, e.g. music/excluded."
                        }
                        moveWithVerifiedCopy(track, targetTree)
                    }
                }

                // The provider has committed the operation: never turn a failed
                // cache write into a false 'file not removed' error.
                val remaining = tracks.filterNot { it.uri == track.uri }
                // Must persist even if Android destroys the Activity before
                // the completion callback gets a chance to update its UI.
                playlistStore.removeUriEverywhere(track.uri.toString())
                val bpmOk = runCatching { bpm.forget(track) }.getOrDefault(false)
                val waveformOk = runCatching { waveform.delete(track.uri) }.getOrDefault(false)
                val metadataOk = bpmOk && waveformOk
                val cacheOk = runCatching {
                    cache.write(libraryTree, remaining)
                    true
                }.getOrDefault(false)
                Outcome(action, remaining, cacheOk, metadataOk, destinationName)
            }
            main.post { callback(result) }
        }
    }

    /**
     * Destination is the exact folder the user selected. Never infer a parent,
     * create an extra "excluded" level, or parse SAF document IDs as paths.
     * A separately granted destination can be anywhere the provider allows.
     */
    private fun moveWithVerifiedCopy(track: Track, destinationTree: Uri): String {
        val excluded = DocumentFile.fromTreeUri(app, destinationTree)
            ?: error("Cannot access the selected destination folder.")
        require(excluded.isDirectory && excluded.canWrite()) {
            "The selected destination folder is not writable. Choose another one."
        }
        require(excluded.listFiles().none {
            it.name.equals(track.name, ignoreCase = true)
        }) { "A file with the same name already exists in excluded. Nothing was moved." }

        val mimeType = resolver.getType(track.uri) ?: "application/octet-stream"
        val newFile = excluded.createFile(mimeType, track.name)
            ?: error("The provider refused to create the destination file.")
        var copiedAndVerified = false
        try {
            // Some providers silently append or rewrite a name during
            // createFile. Abort instead of silently moving under a new name.
            require(newFile.name.equals(track.name, ignoreCase = true)) {
                "The storage provider changed the destination filename. Original kept."
            }
            val sourceDigest = MessageDigest.getInstance("SHA-256")
            var sourceBytes = 0L
            resolver.openInputStream(track.uri)?.use { input ->
                resolver.openOutputStream(newFile.uri, "wt")?.use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        check(!Thread.currentThread().isInterrupted) {
                            "Copy cancelled before deleting the source."
                        }
                        val n = input.read(buffer)
                        if (n < 0) break
                        if (n == 0) continue
                        sourceDigest.update(buffer, 0, n)
                        output.write(buffer, 0, n)
                        sourceBytes += n
                    }
                    output.flush()
                } ?: error("Cannot write destination file.")
            } ?: error("Cannot read source file.")

            require(sourceBytes > 0L) {
                "Source file is empty. Refusing to delete it."
            }
            val verifyDigest = MessageDigest.getInstance("SHA-256")
            var targetBytes = 0L
            resolver.openInputStream(newFile.uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    check(!Thread.currentThread().isInterrupted) {
                        "Verification cancelled before deleting the source."
                    }
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    verifyDigest.update(buffer, 0, n)
                    targetBytes += n
                }
            } ?: error("Cannot verify the new file.")
            val originalHash = sourceDigest.digest()
            require(sourceBytes == targetBytes &&
                originalHash.contentEquals(verifyDigest.digest())) {
                "The copied file does not match the original. Original left untouched."
            }
            // Detect a file changing during the copy (e.g. an in-progress
            // download). Never remove a newly modified original.
            val freshDigest = MessageDigest.getInstance("SHA-256")
            var freshBytes = 0L
            resolver.openInputStream(track.uri)?.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    check(!Thread.currentThread().isInterrupted) {
                        "Source recheck cancelled before deletion."
                    }
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (n == 0) continue
                    freshDigest.update(buffer, 0, n)
                    freshBytes += n
                }
            } ?: error("Original source cannot be rechecked.")
            require(freshBytes == sourceBytes &&
                originalHash.contentEquals(freshDigest.digest())) {
                "The original file changed during copy. It was NOT deleted."
            }
            copiedAndVerified = true
            require(DocumentsContract.deleteDocument(resolver, track.uri)) {
                "Copy verified but Android refused to delete the original."
            }
            return newFile.name ?: track.name
        } catch (failure: Exception) {
            // If copied successfully but deletion was refused, leave the valid
            // copy in excluded as a backup and clearly report the situation.
            if (!copiedAndVerified) {
                runCatching { newFile.delete() }
            }
            throw failure
        }
    }

    private fun checkWritePermission(tree: Uri) {
        check(resolver.persistedUriPermissions.any {
            it.uri == tree && it.isWritePermission && it.isReadPermission
        }) { "Read/write folder permission is missing. Select the folder again." }
    }

    fun close() {
        executor.shutdownNow()
    }
}
