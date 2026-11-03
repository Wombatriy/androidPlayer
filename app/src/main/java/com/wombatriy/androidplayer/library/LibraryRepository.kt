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
 * One background lane for cache reads and SAF scans.
 * Progress + results from an obsolete folder selection are discarded.
 */
class LibraryRepository(context: Context) {
    data class Snapshot(val tracks: List<Track>, val fromCache: Boolean)

    private val appContext = context.applicationContext
    private val cache = LibraryCache(appContext)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainThread = Handler(Looper.getMainLooper())
    private val generation = AtomicInteger()

    fun load(
        folder: Uri,
        forceRefresh: Boolean,
        onProgress: (MusicFolder.ScanProgress) -> Unit = {},
        onLoaded: (Result<Snapshot>) -> Unit
    ) {
        val token = generation.incrementAndGet()
        executor.execute {
            val result = runCatching {
                val cached = if (forceRefresh) null else cache.read(folder)
                if (cached != null) {
                    Snapshot(cached, fromCache = true)
                } else {
                    val fresh = MusicFolder.load(
                        appContext,
                        folder,
                        onProgress = { progress ->
                            mainThread.post {
                                if (generation.get() == token) onProgress(progress)
                            }
                        },
                        cancelled = { generation.get() != token }
                    )
                    // Never replace the snapshot with a cancelled partial scan.
                    if (generation.get() == token) runCatching { cache.write(folder, fresh) }
                    Snapshot(fresh, fromCache = false)
                }
            }
            mainThread.post {
                if (generation.get() == token) onLoaded(result)
            }
        }
    }

    fun close() {
        generation.incrementAndGet()
        executor.shutdownNow()
    }
}
