package com.wombatriy.androidplayer.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.bpm.BpmRepository
import com.wombatriy.androidplayer.waveform.WaveformCache
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Destructive SAF operations, always run off the UI thread and only after
 * explicit confirmation from MainActivity.
 *
 * "Exclude" is a verified copy into a separately authorised parent/excluded,
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
    private val waveform = WaveformCache(app)
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun perform(
        action: Action,
        track: Track,
        libraryTree: Uri,
        destinationParentTree: Uri?,
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
                        val parentTree = requireNotNull(destinationParentTree) {
                            "Select an excluded destination first."
                        }
                        checkWritePermission(parentTree)
                        check(parentTree != libraryTree) {
                            "Choose the parent of your music folder, not the library itself."
                        }
                        moveWithVerifiedCopy(track, libraryTree, parentTree)
                    }
                }

                // The provider has committed the operation: never turn a failed
                // cache write into a false 'file not removed' error.
                val remaining = tracks.filterNot { it.uri == track.uri }
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
     * A user-selected parent (e.g. music/) contains or receives excluded/.
     * The library grant for music/jazz/ cannot create the sibling itself.
     */
    private fun moveWithVerifiedCopy(track: Track, libraryTree: Uri, parentTree: Uri): String {
        val parent = DocumentFile.fromTreeUri(app, parentTree)
            ?: error("Cannot access the selected destination parent.")
        require(parent.isDirectory && parent.canWrite()) {
            "The selected destination parent is not writable."
        }
        // Confirm this folder directly contains the selected Library root.
        // A writable grant to an unrelated folder must not silently relocate audio.
        val libraryRootId = DocumentsContract.getTreeDocumentId(libraryTree)
        val siblings = parent.listFiles()
        require(siblings.any { candidate ->
            candidate.isDirectory &&
                runCatching { DocumentsContract.getDocumentId(candidate.uri) }
                    .getOrNull() == libraryRootId
        }) {
            "Choose the direct parent of the Library folder " +
                "(for example music/ for music/jazz/)."
        }
        val existing = siblings.firstOrNull {
            it.name.equals("excluded", ignoreCase = true)
        }
        val excluded = when {
            existing == null -> parent.createDirectory("excluded")
                ?: error("Could not create the excluded folder.")
            existing.isDirectory -> existing
            else -> error("An item named excluded exists but is not a folder.")
        }
        require(excluded.canWrite()) { "Excluded folder is read-only." }
        require(excluded.listFiles().none {
            it.name.equals(track.name, ignoreCase = true)
        }) { "A file with the same name already exists in excluded. Nothing was moved." }

        val mimeType = resolver.getType(track.uri) ?: "application/octet-stream"
        val newFile = excluded.createFile(mimeType, track.name)
            ?: error("The provider refused to create the destination file.")
        var copiedAndVerified = false
        try {
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
