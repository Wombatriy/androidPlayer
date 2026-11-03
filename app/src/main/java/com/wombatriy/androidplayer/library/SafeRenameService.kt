package com.wombatriy.androidplayer.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import com.wombatriy.androidplayer.FilenameBpm
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.bpm.BpmRepository
import java.util.concurrent.Executors

/**
 * SAF rename runs on a worker. No file copy or overwrite is performed.
 * We look up siblings through the parent Document ID, so even a huge
 * library requires only one directory query per rename.
 */
class SafeRenameService(context: Context) {
    data class Preview(
        val oldTrack: Track,
        val proposedName: String,
        val source: String,
        val sourceBpm: Double
    )

    data class Outcome(
        val replacement: Track,
        val tracks: List<Track>,
        val metadataPreserved: Boolean,
        val cacheSaved: Boolean,
        val providerAdjustedName: Boolean
    )

    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val bpm = BpmRepository(app)
    private val cache = LibraryCache(app)
    private val background = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    fun preview(track: Track, treeUri: Uri, callback: (Result<Preview>) -> Unit) {
        background.execute {
            val result = runCatching {
                verifyWriteAccess(treeUri)
                require(track.parentUri != null) {
                    "Parent directory is unknown. Press Rescan before renaming."
                }
                val resolved = bpm.resolved(track)
                    ?: error("BPM is unknown. Detect or enter BPM before renaming.")
                val requested = FilenameBpm.propose(track.name, resolved.bpm)
                verifyRenameSupport(track.uri)
                require(requested != track.name) { "Filename already contains this BPM." }
                require(!hasSiblingCollision(track, treeUri, requested)) {
                    "A file named '$requested' already exists in this folder."
                }
                Preview(track, requested, resolved.source.caption, resolved.bpm)
            }
            ui.post { callback(result) }
        }
    }

    fun rename(
        preview: Preview,
        treeUri: Uri,
        libraryTracks: List<Track>,
        callback: (Result<Outcome>) -> Unit
    ) {
        background.execute {
            val result = runCatching {
                verifyWriteAccess(treeUri)
                require(preview.oldTrack.parentUri != null) { "Missing parent directory" }
                // Always repeat collision/rename-support checks just before commit.
                require(!hasSiblingCollision(preview.oldTrack, treeUri, preview.proposedName)) {
                    "A file with this name appeared while confirming. Nothing changed."
                }
                verifyRenameSupport(preview.oldTrack.uri)
                val newUri = DocumentsContract.renameDocument(
                    resolver, preview.oldTrack.uri, preview.proposedName
                ) ?: error("Storage provider declined the rename")

                // The SAF rename is already committed. A failing metadata
                // query must not report the rename as failed and replay an old URI.
                val actualName = runCatching { readName(newUri) }.getOrNull()
                    ?: preview.proposedName
                val newTrack = preview.oldTrack.copy(uri = newUri, name = actualName)
                // Keep the Library snapshot order here. LibraryScreen will sort
                // numerically by active BPM, then update visible rows and anchor.
                val updated = libraryTracks.map { current ->
                    if (current.uri == preview.oldTrack.uri) newTrack else current
                }

                val metadataOk = runCatching {
                    bpm.migrate(preview.oldTrack, newTrack)
                }.getOrDefault(false)
                val cacheOk = runCatching {
                    cache.write(treeUri, updated)
                    true
                }.getOrDefault(false)
                Outcome(
                    replacement = newTrack,
                    tracks = updated,
                    metadataPreserved = metadataOk,
                    cacheSaved = cacheOk,
                    providerAdjustedName = actualName != preview.proposedName
                )
            }
            ui.post { callback(result) }
        }
    }

    private fun verifyWriteAccess(treeUri: Uri) {
        check(resolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isWritePermission
        }) { "This folder is read-only. Choose a folder with write permission." }
    }

    private fun verifyRenameSupport(uri: Uri) {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_FLAGS),
            null, null, null
        )?.use { cursor ->
            check(cursor.moveToFirst()) { "The original file cannot be found." }
            val flags = cursor.getInt(0)
            check(flags and DocumentsContract.Document.FLAG_SUPPORTS_RENAME != 0) {
                "This storage provider does not support renaming the file."
            }
        } ?: error("Cannot read permissions for the selected file")
    }

    /**
     * SAF document identifiers are opaque. Never derive a parent by splitting
     * the file's URI or its display name.
     */
    private fun hasSiblingCollision(track: Track, treeUri: Uri, proposedName: String): Boolean {
        val parent = track.parentUri ?: error("Missing parent directory")
        val parentId = DocumentsContract.getDocumentId(parent)
        val ownId = DocumentsContract.getDocumentId(track.uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        return resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID
            )
            val nameIndex = cursor.getColumnIndexOrThrow(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME
            )
            var found = false
            while (cursor.moveToNext()) {
                val id = cursor.getString(idIndex)
                val name = cursor.getString(nameIndex)
                if (id != ownId && name.equals(proposedName, ignoreCase = true)) {
                    found = true
                    break
                }
            }
            found
        } ?: error("Cannot check files in the parent folder")
    }

    private fun readName(uri: Uri): String? =
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    fun close() {
        background.shutdownNow()
    }
}
