package com.wombatriy.androidplayer.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.wombatriy.androidplayer.MusicFolder
import com.wombatriy.androidplayer.Track
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Loads an instant local snapshot or scans SAF on one worker thread.
 * Only the newest request is delivered; old scans cannot replace newer data.
 */
class LibraryRepository(context: Context) {
    data class Snapshot(val tracks: List<Track>, val fromCache: Boolean)

    private val appContext = context.applicationContext
    private val cache = LibraryCache(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()

    fun load(folder: Uri, forceRefresh: Boolean, onLoaded: (Result<Snapshot>) -> Unit) {
        val request = generation.incrementAndGet()
        executor.execute {
            val result = runCatching {
                val cached = if (forceRefresh) null else cache.read(folder)
                if (cached != null) {
                    Snapshot(cached, fromCache = true)
                } else {
                    val fresh = MusicFolder.load(appContext, folder)
                    // Snapshot failure must not invalidate a successful scan.
                    if (generation.get() == request) runCatching { cache.write(folder, fresh) }
                    Snapshot(fresh, fromCache = false)
                }
            }
            mainThread.post {
                if (generation.get() == request) onLoaded(result)
            }
        }
    }

    fun close() {
        generation.incrementAndGet()
        executor.shutdownNow()
    }
}
