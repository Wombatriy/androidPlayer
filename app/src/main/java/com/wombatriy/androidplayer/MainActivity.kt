package com.wombatriy.androidplayer

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.wombatriy.androidplayer.library.LibraryRepository
import com.wombatriy.androidplayer.player.PlaybackController
import com.wombatriy.androidplayer.ui.LibraryScreen
import com.wombatriy.androidplayer.ui.PlayerScreen
import kotlin.math.roundToInt

/**
 * Activity is only the composition root: navigation, SAF permission and folder loading.
 * Playback behavior lives in PlaybackController; individual tabs own their views.
 */
class MainActivity : Activity() {
    private lateinit var playback: PlaybackController
    private lateinit var playerScreen: PlayerScreen
    private lateinit var libraryScreen: LibraryScreen
    private lateinit var status: TextView
    private lateinit var pages: List<View>
    private val prefs by lazy { getSharedPreferences("library", MODE_PRIVATE) }
    private val libraryRepository by lazy { LibraryRepository(this) }

    private var activeTab = 1
    private var loadedFromCache = false
    private var loadingFolder = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playback = PlaybackController(this, object : PlaybackController.Listener {
            override fun onTrackChanged() = playerScreen.refreshTrack()
            override fun onPlaybackControlsChanged() = playerScreen.updatePlaybackState()
            override fun onPositionChanged(positionMs: Long, durationMs: Long) =
                playerScreen.updatePosition(positionMs, durationMs)
            override fun onLoopChanged() = playerScreen.updateLoop()
            override fun onPlaybackError(message: String) = playerScreen.showPlaybackError(message)
        })
        createLayout()
        if (savedInstanceState != null) {
            activeTab = savedInstanceState.getInt(STATE_TAB, 1)
        }
        libraryScreen.restorePosition(
            savedInstanceState?.getInt(STATE_SCROLL_INDEX) ?: prefs.getInt(KEY_SCROLL_INDEX, 0),
            savedInstanceState?.getInt(STATE_SCROLL_OFFSET) ?: prefs.getInt(KEY_SCROLL_OFFSET, 0)
        )
        showTab(activeTab)
        restoreFolder()
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
        }
        root.addView(nav)

        playerScreen = PlayerScreen(this, playback)
        libraryScreen = LibraryScreen(
            this,
            onChooseFolder = ::chooseFolder,
            onRescan = ::rescanSelectedFolder,
            onTrackSelected = ::playTrack
        )
        pages = listOf(
            playerScreen,
            libraryScreen,
            placeholder("Playlists", "Playlist management will be added in a later iteration."),
            placeholder("Queue", "Playlist track ordering will be added in a later iteration.")
        )
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

    private fun placeholder(heading: String, text: String): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(16), 0)
            addView(TextView(this@MainActivity).apply {
                this.text = heading
                textSize = 23f
            })
            addView(TextView(this@MainActivity).apply {
                this.text = text
                textSize = 15f
            })
        }

    private fun showTab(index: Int) {
        if (activeTab == 1 && index != 1) saveScrollPosition()
        activeTab = index.coerceIn(0, pages.lastIndex)
        // Keep tab instances and their state in memory; never reconstruct lists.
        pages.forEachIndexed { i, page ->
            page.visibility = if (i == activeTab) View.VISIBLE else View.GONE
        }
    }

    private fun playTrack(track: Track) {
        playback.load(track, libraryScreen.tracksSnapshot())
        showTab(0)
    }

    private fun chooseFolder() {
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
        prefs.getString(KEY_TREE_URI, null)?.let {
            loadLibrary(Uri.parse(it), forceRefresh = true)
        }
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
        loadingFolder = true
        libraryScreen.setLoading(true)
        updateStatus()

        libraryRepository.load(treeUri, forceRefresh) { result ->
            if (isFinishing || isDestroyed) return@load
            loadingFolder = false
            result.onSuccess { snapshot ->
                loadedFromCache = snapshot.fromCache
                libraryScreen.submitTracks(snapshot.tracks)
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
            .apply()
    }

    override fun onPause() {
        saveScrollPosition()
        super.onPause()
    }

    private fun updateStatus() {
        status.text = if (loadingFolder) "Scanning music folder…"
        else "${libraryScreen.trackCount} tracks loaded${if (loadedFromCache) " (cached)" else ""}"
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, activeTab)
        outState.putInt(STATE_SCROLL_INDEX, libraryScreen.firstVisibleIndex)
        outState.putInt(STATE_SCROLL_OFFSET, libraryScreen.firstVisibleOffset)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        playback.release()
        libraryRepository.close()
        super.onDestroy()
    }

    companion object {
        private const val FOLDER_REQUEST = 41
        private const val KEY_TREE_URI = "treeUri"
        private const val KEY_SCROLL_INDEX = "scrollIndex"
        private const val KEY_SCROLL_OFFSET = "scrollOffset"
        private const val STATE_TAB = "tab"
        private const val STATE_SCROLL_INDEX = "libraryIndex"
        private const val STATE_SCROLL_OFFSET = "libraryOffset"
    }
}
