package com.wombatriy.androidplayer.player.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Forces decoded PCM through R3 and deliberately removes Sonic from the
 * AudioProcessorChain. Keep float output and AudioTrack PlaybackParams off:
 * either would bypass/duplicate the intended time-stretch path.
 */
@OptIn(markerClass = [UnstableApi::class])
internal class RubberBandRenderersFactory(context: Context) :
    DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(false)
        .setEnableAudioOutputPlaybackParameters(false)
        .setAudioProcessorChain(RubberBandAudioProcessorChain())
        .build()
}
