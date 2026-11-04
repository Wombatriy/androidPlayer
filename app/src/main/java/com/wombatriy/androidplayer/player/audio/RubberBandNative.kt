package com.wombatriy.androidplayer.player.audio

import java.nio.ByteBuffer

/**
 * JNI adapter for Rubber Band R3. This class is used ONLY in an opt-in
 * -Prubberband=true build. No Rubber Band native library is bundled
 * in the regular Sonic APK.
 *
 * All methods are invoked on Media3's audio rendering thread, not in parallel.
 */
internal object RubberBandNative {
    init {
        System.loadLibrary("rubberband_player")
    }

    external fun create(sampleRate: Int, channels: Int, timeRatio: Double): Long
    external fun close(handle: Long)
    external fun setTimeRatio(handle: Long, ratio: Double)

    /** Input is direct, 16-bit interleaved PCM with native byte order. */
    external fun process(
        handle: Long,
        input: ByteBuffer?,
        frames: Int,
        finalBlock: Boolean
    )

    /** How many audio frames can be read, or -1 when the final block is drained. */
    external fun available(handle: Long): Int

    /**
     * Reads into a native-order direct PCM16 buffer. Returns valid frames
     * AFTER start-delay compensation; zero means that more input is needed.
     */
    external fun retrieve(handle: Long, output: ByteBuffer, capacityFrames: Int): Int
}
