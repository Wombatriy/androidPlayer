package com.wombatriy.androidplayer.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer

/**
 * Two Android/Media3 time-stretching paths for controlled listening comparisons.
 *
 * DEVICE is NOT promised to improve quality: it delegates speed to Android's
 * AudioTrack mixer where supported and can reject specific speeds.
 * Using a preferred backend takes effect only when a new ExoPlayer is created.
 */
enum class AudioBackend(val label: String) {
    SONIC("Sonic (default)"),
    DEVICE("Android mixer (experimental)")
}

internal object AudioEngineFactory {
    @OptIn(UnstableApi::class)
    fun create(context: Context, backend: AudioBackend): ExoPlayer {
        val renderers = DefaultRenderersFactory(context)
            .setEnableAudioOutputPlaybackParameters(backend == AudioBackend.DEVICE)
        return ExoPlayer.Builder(context, renderers).build()
    }
}
