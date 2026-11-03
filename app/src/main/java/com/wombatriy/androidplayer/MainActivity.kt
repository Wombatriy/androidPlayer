package com.wombatriy.androidplayer

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wombatriy.androidplayer.bpm.BpmCoordinator
import com.wombatriy.androidplayer.bpm.BpmBatchAnalyzer
import com.wombatriy.androidplayer.library.LibraryRepository
import com.wombatriy.androidplayer.playlists.PlaylistCoordinator
import com.wombatriy.androidplayer.player.PlaybackController
import com.wombatriy.androidplayer.player.MusicPlaybackService
import com.wombatriy.androidplayer.player.BackNavigationPolicy
import com.wombatriy.androidplayer.ui.LibraryScreen
import com.wombatriy.androidplayer.ui.PlayerScreen
import com.wombatriy.androidplayer.ui.PlaylistsScreen
import com.wombatriy.androidplayer.ui.QueueScreen
import com.wombatriy.androidplayer.ui.RenameFlow
import kotlin.math.roundToInt

/**
 * Activity is only the composition root: navigation, SAF permission and folder loading.
 * Playback behavior lives in PlaybackController; individual tabs own their views.
 */
class MainActivity : Activity() {
    private lateinit var playback: PlaybackController
    private var serviceBound = false
    private var pendingUiState: Bundle? = null
    private var backCallback: OnBackInvokedCallback? = null

    private val playbackListener = object : PlaybackController.Listener {
        override fun onTrackChanged() {
            bpmCoordinator.selectTrack(playback.currentTrack)
            playerScreen.refreshTrack()
        }
        override fun onPlaybackControlsChanged() = playerScreen.updatePlaybackState()
        override fun onPositionChanged(positionMs: Long, durationMs: Long) =
            playerScreen.updatePosition(positionMs, durationMs)
        override fun onSeekRequested(targetMs: Long) =
            playerScreen.onSeekRequested(targetMs)
        override fun onSeekApplied(actualMs: Long) =
            playerScreen.onSeekApplied(actualMs)
        override fun onLoopChanged() = playerScreen.updateLoop()
        override fun onPlaybackError(message: String) = playerScreen.showPlaybackError(message)
        override fun onSpeedChanged() = playerScreen.refreshSpeed()
        override fun onQueueChanged() {
            if (::queueScreen.isInitialized) queueScreen.refresh()
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (isFinishing || isDestroyed) return
            val local = binder as? MusicPlaybackService.LocalBinder ?: run {
                Toast.makeText(this@MainActivity, "Playback connection failed", Toast.LENGTH_LONG).show()
                return
            }
            // If Android restarted the service while this Activity still
            // exists, rebuild every view against its new playback instance.
            if (::playerScreen.isInitialized) {
                recreate()
                return
            }
            playback = local.playback
            createLayout()
            val saved = pendingUiState
            pendingUiState = null
            libraryScreen.restorePosition(
                saved?.getInt(STATE_SCROLL_INDEX) ?: prefs.getInt(KEY_SCROLL_INDEX, 0),
                saved?.getInt(STATE_SCROLL_OFFSET) ?: prefs.getInt(KEY_SCROLL_OFFSET, 0),
                saved?.getString(STATE_SCROLL_URI) ?: prefs.getString(KEY_SCROLL_URI, null)
            )
            showTab(activeTab)
            playback.addListener(playbackListener)
            // A MediaSession may already be playing when the user opens
            // the app again from its notification.
            bpmCoordinator.selectTrack(playback.currentTrack)
            playerScreen.refreshTrack()
            queueScreen.refresh()
            restoreFolder()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            if (::status.isInitialized) {
                status.text = "Playback service restarting…"
            }
        }
    }

    private lateinit var playerScreen: PlayerScreen
    private lateinit var libraryScreen: LibraryScreen
    private lateinit var playlistsScreen: PlaylistsScreen
    private lateinit var queueScreen: QueueScreen
    private lateinit var status: TextView
    private val playlists: PlaylistCoordinator by lazy { PlaylistCoordinator(this, playback) }
    private lateinit var pages: List<View>
    private val prefs by lazy { getSharedPreferences("library", MODE_PRIVATE) }
    private val libraryRepository by lazy { LibraryRepository(this) }
    private val renameFlow: RenameFlow by lazy {
        RenameFlow(
            activity = this,
            onBeforeCommit = { track -> playback.suspendForRename(track) },
            onRenamed = { previous, result, restart ->
                // Keep all playlist references valid even if SAF returns a new URI.
                playlists.onTrackRenamed(previous, result.replacement, result.tracks, restart)
                libraryScreen.submitTracks(result.tracks)
                batchAnalyzer.refreshSources(result.tracks)
                playlistsScreen.refresh()
                loadedFromCache = result.cacheSaved
                updateStatus()
            },
            onFailed = { previous, restart ->
                playback.restoreAfterFailedRename(previous, restart)
            }
        )
    }
    private val bpmCoordinator: BpmCoordinator by lazy {
        BpmCoordinator(this, ::onBpmChanged)
    }

    private fun onBpmChanged() {
        if (::playerScreen.isInitialized) playerScreen.refreshBpm()
        if (::libraryScreen.isInitialized) {
            val selected = bpmCoordinator.track
            if (selected != null) {
                libraryScreen.updateOneBpm(selected, bpmCoordinator.resolved())
            }
        }
    }
    private val batchAnalyzer by lazy {
        BpmBatchAnalyzer(
            this,
            onState = { state ->
                if (::libraryScreen.isInitialized) libraryScreen.updateBatchState(state)
            },
            onRowsChanged = { records, replace ->
                if (::libraryScreen.isInitialized) libraryScreen.updateBpmRecords(records, replace)
                val playing = bpmCoordinator.track
                if (playing != null && records.containsKey(playing.uri.toString()) &&
                    ::playerScreen.isInitialized) {
                    playerScreen.refreshBpm()
                }
            }
        )
    }

    private val tabSwipe by lazy { com.wombatriy.androidplayer.ui.TabSwipeNavigator(this) }

    private var activeTab = 1
    private var loadedFromCache = false
    private var loadingFolder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingUiState = savedInstanceState
        activeTab = if (intent?.action == ACTION_SHOW_PLAYER) {
            0
        } else savedInstanceState?.getInt(STATE_TAB, 1) ?: 1

        // Android 13+ predictive back requires the platform dispatcher.
        // Older devices use onBackPressed below.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { handleBackNavigation() }
            backCallback = callback
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback
            )
        }
        setContentView(TextView(this).apply {
            text = "Connecting to playback…"
            gravity = android.view.Gravity.CENTER
        })
        val bindIntent = Intent(this, MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_LOCAL_BIND
        }
        serviceBound = bindService(bindIntent, connection, Context.BIND_AUTO_CREATE)
        if (!serviceBound) {
            Toast.makeText(this, "Cannot start playback service", Toast.LENGTH_LONG).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_SHOW_PLAYER) {
            activeTab = 0
            if (::pages.isInitialized) showTab(0)
        }
    }

    private fun createLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249, 250, 252))
        }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Player", "Library", "Playlists", "Queue").forEachIndexed { index, title ->
            nav.addView(Button(this).apply {
                text = title
                textSize = 11f
                isAllCaps = false
                setOnClickListener { showTab(index) }
            }, LinearLayout.LayoutParams(0, dp(52), 1f))
            if (index < 3) {
                // Vertical divider between each pair of adjacent tabs.
                nav.addView(View(this).apply {
                    setBackgroundColor(Color.rgb(18, 21, 25))
                }, LinearLayout.LayoutParams(dp(2), dp(52)))
            }
        }
        root.addView(nav)
        // Two narrow horizontal rules separated by a light gap, as in the mock-up.
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(18, 21, 25)) },
            LinearLayout.LayoutParams(-1, dp(2)))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(249, 250, 252)) },
            LinearLayout.LayoutParams(-1, dp(3)))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(18, 21, 25)) },
            LinearLayout.LayoutParams(-1, dp(2)))

        playerScreen = PlayerScreen(this, playback, bpmCoordinator)
        libraryScreen = LibraryScreen(
            this,
            onChooseFolder = ::chooseFolder,
            onRescan = ::rescanSelectedFolder,
            onBatchToggle = ::toggleBatchAnalysis,
            onTrackSelected = ::playTrack,
            onTrackLongPressed = ::showLibraryTrackActions,
            onOrderChanged = playlists::onLibraryOrder
        )
        playlistsScreen = PlaylistsScreen(
            this,
            store = playlists.store,
            resolve = playlists::resolve,
            onPlayTrack = { track, playlist, tracks ->
                if (!renameFlow.busy) playlists.play(track, playlist, tracks)
                else Toast.makeText(this, "Finish renaming first", Toast.LENGTH_SHORT).show()
            },
            onCatalogChanged = playlists::onPlaylistChanged
        )
        queueScreen = QueueScreen(
            this, playback,
            onChooseTrack = { index ->
                if (!renameFlow.busy) playback.playQueueIndex(index)
                else Toast.makeText(this, "Finish renaming first", Toast.LENGTH_SHORT).show()
            }
        )
        pages = listOf(playerScreen, libraryScreen, playlistsScreen, queueScreen)
        val host = FrameLayout(this)
        pages.forEach { page ->
            host.addView(page, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            textSize = 12f
        }
        root.addView(status)
        updateStatus()

        // API 35 edge-to-edge policy can put views behind system bars unless
        // the root consumes its safe insets. Also respects gesture navigation.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val top: Int
            val bottom: Int
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                top = insets.getInsets(WindowInsets.Type.statusBars()).top
                bottom = insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION")
                top = insets.systemWindowInsetTop
                @Suppress("DEPRECATION")
                bottom = insets.systemWindowInsetBottom
            }
            view.setPadding(0, top, 0, bottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
    }

    /**
     * Recognize horizontal navigation before child views handle the event.
     * Cancel the previous touch target on a recognized swipe, so a track tap
     * never fires in addition to changing tabs.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!::pages.isInitialized) return super.dispatchTouchEvent(event)
        return when (val step = tabSwipe.onTouch(event, activeTab, pages.size)) {
            null -> super.dispatchTouchEvent(event)
            0 -> true
            else -> {
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
                showTab(activeTab + step)
                true
            }
        }
    }

    private fun showTab(index: Int) {
        if (activeTab == 1 && index != 1) saveScrollPosition()
        activeTab = index.coerceIn(0, pages.lastIndex)
        // Keep tab instances and their state in memory; never reconstruct lists.
        pages.forEachIndexed { i, page ->
            page.visibility = if (i == activeTab) View.VISIBLE else View.GONE
        }
        // Reconcile a queue drag with the Playlists editor whenever it becomes
        // visible. Do not recreate either screen or lose its scroll position.
        when (activeTab) {
            2 -> playlistsScreen.refresh()
            3 -> queueScreen.refresh()
        }
    }

    private fun playTrack(track: Track) {
        if (renameFlow.busy) {
            Toast.makeText(this, "Finish renaming before selecting another track",
                Toast.LENGTH_SHORT).show()
            return
        }
        // Stay on the initiating tab. Tapping the already selected track is a no-op.
        playback.load(track, libraryScreen.tracksSnapshot())
    }

    private fun showLibraryTrackActions(track: Track) {
        if (renameFlow.busy) {
            Toast.makeText(this, "Finish renaming first", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(track.name)
            .setItems(arrayOf("Add to playlist", "Rename with BPM")) { _, action ->
                when (action) {
                    0 -> playlistsScreen.addTrack(track)
                    1 -> previewRename(track)
                }
            }
            .show()
    }

    private fun chooseFolder() {
        if (renameFlow.busy) {
            Toast.makeText(this, "Finish renaming before switching folders", Toast.LENGTH_SHORT).show()
            return
        }
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }, FOLDER_REQUEST)
    }

    @Deprecated("Legacy folder picker is retained for this framework-only iteration.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FOLDER_REQUEST || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        try {
            contentResolver.takePersistableUriPermission(uri, flags)
            val previous = prefs.getString(KEY_TREE_URI, null)
            if (previous != uri.toString()) {
                // Do not apply the old folder's scroll position to a new library.
                libraryScreen.restorePosition(0, 0)
                prefs.edit().putInt(KEY_SCROLL_INDEX, 0).putInt(KEY_SCROLL_OFFSET, 0).apply()
            }
            prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
            loadLibrary(uri, forceRefresh = true)
        } catch (e: SecurityException) {
            Toast.makeText(this, "Folder permission denied: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFolder() {
        prefs.getString(KEY_TREE_URI, null)?.let {
            loadLibrary(Uri.parse(it), forceRefresh = false)
        }
    }

    private fun rescanSelectedFolder() {
        if (renameFlow.busy) {
            Toast.makeText(this, "Finish renaming before rescanning", Toast.LENGTH_SHORT).show()
            return
        }
        prefs.getString(KEY_TREE_URI, null)?.let {
            loadLibrary(Uri.parse(it), forceRefresh = true)
        }
    }

    private fun toggleBatchAnalysis() {
        if (renameFlow.busy) return
        if (batchAnalyzer.state.running) {
            batchAnalyzer.cancel()
        } else {
            batchAnalyzer.start(libraryScreen.tracksSnapshot())
        }
    }

    private fun previewRename(track: Track) {
        if (renameFlow.busy || loadingFolder || batchAnalyzer.state.running) {
            Toast.makeText(this, "Finish scanning or BPM analysis first",
                Toast.LENGTH_SHORT).show()
            return
        }
        // The current track is supported: RenameFlow will suspend it only
        // after the user confirms, then restart it using the renamed URI.
        val folder = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse) ?: return
        renameFlow.start(track, folder, libraryScreen.tracksSnapshot())
    }

    private fun loadLibrary(treeUri: Uri, forceRefresh: Boolean) {
        val hasReadGrant = contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission
        }
        if (!hasReadGrant) {
            libraryScreen.setLoading(false)
            Toast.makeText(this, "Folder access expired. Choose it again.", Toast.LENGTH_LONG).show()
            return
        }
        // A folder selection or rescan invalidates the old batch queue.
        batchAnalyzer.resetForLibraryChange()
        loadingFolder = true
        libraryScreen.setLoading(true)
        updateStatus()

        libraryRepository.load(
            treeUri,
            forceRefresh,
            onProgress = { progress ->
                if (!isFinishing && !isDestroyed) libraryScreen.updateScanProgress(progress)
            }
        ) { result ->
            if (isFinishing || isDestroyed) return@load
            loadingFolder = false
            result.onSuccess { snapshot ->
                loadedFromCache = snapshot.fromCache
                playlists.onLibraryLoaded(snapshot.tracks)
                libraryScreen.submitTracks(snapshot.tracks)
                playlistsScreen.refresh()
                batchAnalyzer.refreshSources(snapshot.tracks)
                libraryScreen.updateBatchState(batchAnalyzer.state)
            }.onFailure { error ->
                libraryScreen.setLoading(false)
                Toast.makeText(this, "Unable to load folder: ${error.message}", Toast.LENGTH_LONG).show()
            }
            updateStatus()
        }
    }

    private fun saveScrollPosition() {
        prefs.edit()
            .putInt(KEY_SCROLL_INDEX, libraryScreen.firstVisibleIndex)
            .putInt(KEY_SCROLL_OFFSET, libraryScreen.firstVisibleOffset)
            .putString(KEY_SCROLL_URI, libraryScreen.firstVisibleUri)
            .apply()
    }

    override fun onPause() {
        if (::libraryScreen.isInitialized) saveScrollPosition()
        super.onPause()
    }

    private fun updateStatus() {
        status.text = if (loadingFolder) "Scanning music folder…"
        else "${libraryScreen.trackCount} tracks loaded${if (loadedFromCache) " (cached)" else ""}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, activeTab)
        if (::libraryScreen.isInitialized) {
            outState.putInt(STATE_SCROLL_INDEX, libraryScreen.firstVisibleIndex)
            outState.putInt(STATE_SCROLL_OFFSET, libraryScreen.firstVisibleOffset)
            outState.putString(STATE_SCROLL_URI, libraryScreen.firstVisibleUri)
        }
        super.onSaveInstanceState(outState)
    }

    /** Navigate inside the app; never release the service-owned player. */
    private fun handleBackNavigation() {
        if (!::pages.isInitialized) {
            moveTaskToBack(true)
            return
        }
        val target = BackNavigationPolicy.destination(
            currentTab = activeTab,
            fromPlaylist = playback.activePlaylistId != null
        )
        if (target == BackNavigationPolicy.BACKGROUND_APP) {
            moveTaskToBack(true)
        } else {
            showTab(target)
        }
    }

    @Deprecated("Platform OnBackInvokedDispatcher handles API 33+.")
    override fun onBackPressed() {
        handleBackNavigation()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            backCallback?.let {
                onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
            backCallback = null
        }
        if (::playerScreen.isInitialized) {
            playback.removeListener(playbackListener)
            playerScreen.close()
            renameFlow.close()
            batchAnalyzer.close()
            bpmCoordinator.close()
            libraryRepository.close()
        }
        if (serviceBound) {
            unbindService(connection)
            serviceBound = false
        }
        // NEVER release playback here: it belongs to the MediaSessionService.
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW_PLAYER = "com.wombatriy.androidplayer.SHOW_PLAYER"
        private const val FOLDER_REQUEST = 41
        private const val KEY_TREE_URI = "treeUri"
        private const val KEY_SCROLL_INDEX = "scrollIndex"
        private const val KEY_SCROLL_OFFSET = "scrollOffset"
        private const val KEY_SCROLL_URI = "scrollUri"
        private const val STATE_TAB = "tab"
        private const val STATE_SCROLL_INDEX = "libraryIndex"
        private const val STATE_SCROLL_OFFSET = "libraryOffset"
        private const val STATE_SCROLL_URI = "libraryUri"
    }
}
