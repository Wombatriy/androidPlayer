package com.wombatriy.androidplayer.waveform

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Small persistent waveform files, independent of the library-scan cache.
 * Each song uses one byte per 50 ms (~4.8 KB for a 4-minute song).
 * Atomic writes survive crashes; format v2 never mistakes old 512-bin files for zoomable data.
 */
class WaveformCache(context: Context) {
    private val folder = File(context.applicationContext.filesDir, "waveforms-v2")
    private fun file(uri: Uri): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uri.toString().toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(folder, "$name.wfm")
    }

    fun read(uri: Uri, expectedDurationMs: Long = 0L): WaveformData? {
        val target = file(uri)
        if (!target.exists()) return null
        return runCatching {
            DataInputStream(target.inputStream().buffered()).use { stream ->
                require(stream.readInt() == MAGIC)
                val duration = stream.readLong()
                val count = stream.readInt()
                require(duration > 0 && count == WaveformMath.binsForDuration(duration))
                // Avoid reusing a waveform if the provider replaced a file
                // while retaining its content URI.
                require(expectedDurationMs <= 0L ||
                    kotlin.math.abs(duration - expectedDurationMs) <= 1500L)
                val levels = ByteArray(count)
                stream.readFully(levels)
                WaveformData(duration, levels)
            }
        }.getOrNull()
    }

    fun write(uri: Uri, waveform: WaveformData) {
        check(folder.exists() || folder.mkdirs()) { "Cannot create waveform cache" }
        val atomic = AtomicFile(file(uri))
        val output = atomic.startWrite()
        try {
            val writer = DataOutputStream(output)
            writer.writeInt(MAGIC)
            writer.writeLong(waveform.durationMs)
            writer.writeInt(waveform.levels.size)
            writer.write(waveform.levels)
            writer.flush()
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    companion object { private const val MAGIC = 0x57415632 }
}
