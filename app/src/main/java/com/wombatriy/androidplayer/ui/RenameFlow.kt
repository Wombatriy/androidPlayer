package com.wombatriy.androidplayer.ui

import android.app.Activity
import android.app.AlertDialog
import android.net.Uri
import android.widget.Toast
import com.wombatriy.androidplayer.Track
import com.wombatriy.androidplayer.library.SafeRenameService

/**
 * UI flow for a user-confirmed SAF rename. No provider I/O on the UI thread.
 * Keeps Activity focused on navigation rather than confirmation/dialog logic.
 */
class RenameFlow(
    private val activity: Activity,
    private val onBeforeCommit: (Track) -> Boolean,
    private val onRenamed: (Track, SafeRenameService.Outcome, Boolean) -> Unit,
    private val onFailed: (Track, Boolean) -> Unit
) {
    private val service = SafeRenameService(activity)
    var busy: Boolean = false
        private set
    private var committing = false

    fun start(track: Track, folder: Uri, snapshot: List<Track>) {
        if (busy) return
        busy = true
        service.preview(track, folder) { response ->
            if (activity.isFinishing || activity.isDestroyed) return@preview
            response.onSuccess { preview ->
                val dialog = AlertDialog.Builder(activity)
                    .setTitle("Rename audio file?")
                    .setMessage(
                        "Current: ${track.name}\n\n" +
                            "New: ${preview.proposedName}\n\n" +
                            "BPM source: ${preview.source}. No audio will be copied."
                    )
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Rename") { _, _ ->
                        committing = true
                        // Pause and release the loaded media only at confirmation,
                        // after preview and initial SAF checks have succeeded.
                        val restartCurrent = onBeforeCommit(track)
                        toast("Renaming…")
                        service.rename(preview, folder, snapshot) { result ->
                            committing = false
                            busy = false
                            if (activity.isFinishing || activity.isDestroyed) return@rename
                            result.onSuccess { updated ->
                                onRenamed(track, updated, restartCurrent)
                                val warnings = buildList {
                                    if (!updated.cacheSaved) add("Rescan to refresh the saved library.")
                                    if (!updated.metadataPreserved) add("BPM metadata could not be moved.")
                                    if (updated.providerAdjustedName) add("Provider adjusted the name.")
                                }
                                toast(
                                    "Renamed: ${updated.replacement.name}" +
                                        if (warnings.isEmpty()) "" else "\n${warnings.joinToString(" ")}",
                                    long = true
                                )
                            }.onFailure { error ->
                                onFailed(track, restartCurrent)
                                toast("Rename failed: ${error.message}", long = true)
                            }
                        }
                    }
                    .create()
                dialog.setOnDismissListener { if (!committing) busy = false }
                dialog.show()
            }.onFailure { error ->
                busy = false
                toast("Rename unavailable: ${error.message}", long = true)
            }
        }
    }

    private fun toast(text: String, long: Boolean = false) {
        Toast.makeText(
            activity, text, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
        ).show()
    }

    fun close() {
        service.close()
    }
}
