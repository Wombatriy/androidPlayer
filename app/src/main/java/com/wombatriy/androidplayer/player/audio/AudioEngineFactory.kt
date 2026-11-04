package com.wombatriy.androidplayer.player.audio

import android.content.Context
import com.wombatriy.androidplayer.BuildConfig
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory

/**
 * One place to construct the service-owned audio engine.
 *
 * By default this is precisely the existing Media3/Sonic player: no new DSP,
 * output-mode changes, or native code in regular Android builds.
 *
 * A future real-time Rubber Band adapter can supply a RenderersFactory with
 * a custom PCM AudioSink + AudioProcessorChain. Do not swap ExoPlayer or
 * rebuild MediaSession on every speed change: that breaks queue, A–B,
 * waveform synchronization and background transport.
 */
object AudioEngineFactory {
    val engineLabel: String
        get() = if (BuildConfig.WITH_RUBBERBAND) "Rubber Band R3 (experimental)" else "Media3 Sonic"
    @OptIn(markerClass = [UnstableApi::class])
    fun create(
        context: Context,
        customRenderers: RenderersFactory? = null
    ): ExoPlayer {
        val chosenRenderers = customRenderers ?: if (BuildConfig.WITH_RUBBERBAND) {
            RubberBandRenderersFactory(context)
        } else null
        val builder = if (chosenRenderers == null) {
            ExoPlayer.Builder(context)
        } else {
            ExoPlayer.Builder(context, chosenRenderers)
        }

        return builder.build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            setWakeMode(C.WAKE_MODE_LOCAL)
        }
    }
}
