package com.wombatriy.androidplayer.player

import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.wombatriy.androidplayer.MainActivity

/**
 * Long-lived owner of ExoPlayer and the app's queue/loop/speed state.
 *
 * Media3 manages foreground playback notifications, media buttons, audio
 * controls and session visibility. MainActivity only attaches an observer
 * through a same-process local Binder; closing the UI never releases audio.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class MusicPlaybackService : MediaSessionService() {
    private lateinit var playbackController: PlaybackController
    private lateinit var mediaSession: MediaSession
    private val localBinder = LocalBinder()

    inner class LocalBinder : Binder() {
        val playback: PlaybackController get() = playbackController
    }

    override fun onCreate() {
        super.onCreate()
        playbackController = PlaybackController(applicationContext)

        val openApp = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            action = MainActivity.ACTION_SHOW_PLAYER
        }
        val sessionIntent = PendingIntent.getActivity(
            this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val sessionPlayer = SessionQueuePlayer(playbackController)
        mediaSession = MediaSession.Builder(this, sessionPlayer)
            .setSessionActivity(sessionIntent)
            .build()

        // The UI uses a *local* binder instead of creating a MediaController.
        // Register the session proactively so Media3 can start a foreground
        // notification when playback begins even without a remote controller.
        addSession(mediaSession)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        mediaSession

    override fun onBind(intent: Intent?): IBinder? {
        val sessionBinder = super.onBind(intent)
        return if (intent?.action == ACTION_LOCAL_BIND) {
            if (LocalBindAuth.matches(this, intent.getStringExtra(EXTRA_LOCAL_BIND_TOKEN))) {
                localBinder
            } else null
        } else sessionBinder
    }

    // Default MediaSessionService.onTaskRemoved keeps foreground playback
    // running when the user swipes away the task. No Activity reference kept.
    override fun onDestroy() {
        mediaSession.release()
        playbackController.release()
        super.onDestroy()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "com.wombatriy.androidplayer.LOCAL_PLAYBACK_BIND"
        const val EXTRA_LOCAL_BIND_TOKEN = "com.wombatriy.androidplayer.INTERNAL_BIND_TOKEN"
    }
}
