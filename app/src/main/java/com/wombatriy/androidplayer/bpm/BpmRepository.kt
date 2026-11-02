package com.wombatriy.androidplayer.bpm

import android.content.Context
import com.wombatriy.androidplayer.Track
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.roundToInt

enum class BpmSource(val caption: String) {
    FILENAME("filename"),
    DETECTED("detected (estimate)"),
    MANUAL("manual")
}

data class BpmRecord(
    val bpm: Double,
    val source: BpmSource,
    val confidence: Double? = null,
    val alternative: Double? = null
)

object BpmFormatting {
    fun bpm(value: Double): String {
        val tenths = (value * 10).roundToInt()
        return if (tenths % 10 == 0) "${tenths / 10}"
            else "${tenths / 10}.${tenths % 10}"
    }

    fun adjusted(value: Double, speed: Float): String = bpm(value * speed)
}

/**
 * Detected and manual values are separate, so comparing an auto-estimate with
 * filename BPM never changes the preferred value. A deliberate manual override
 * can still supersede the filename. Reads legacy single-record preferences.
 */
class BpmRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bpm-results-v1", Context.MODE_PRIVATE)

    fun resolved(track: Track): BpmRecord? =
        manual(track)
            ?: track.filenameBpm?.let { BpmRecord(it.toDouble(), BpmSource.FILENAME) }
            ?: detected(track)

    fun manual(track: Track): BpmRecord? =
        read(track, ".manual")
            ?: legacy(track)?.takeIf { it.source == BpmSource.MANUAL }

    fun detected(track: Track): BpmRecord? =
        read(track, ".detected")
            ?: legacy(track)?.takeIf { it.source == BpmSource.DETECTED }

    fun hasManual(track: Track): Boolean = manual(track) != null

    fun saveManual(track: Track, record: BpmRecord) {
        require(record.source == BpmSource.MANUAL)
        save(track, ".manual", record)
    }

    fun saveDetected(track: Track, record: BpmRecord) {
        require(record.source == BpmSource.DETECTED)
        save(track, ".detected", record)
    }

    fun clearManual(track: Track) {
        val base = key(track)
        val editor = prefs.edit().remove(base + ".manual")
        if (legacy(track)?.source == BpmSource.MANUAL) editor.remove(base)
        editor.apply()
    }

    private fun read(track: Track, suffix: String): BpmRecord? =
        decode(prefs.getString(key(track) + suffix, null))

    private fun legacy(track: Track): BpmRecord? =
        decode(prefs.getString(key(track), null))

    private fun decode(serialized: String?): BpmRecord? {
        if (serialized == null) return null
        return runCatching {
            val json = JSONObject(serialized)
            val value = json.getDouble("bpm")
            require(value in 20.0..800.0 && value.isFinite())
            val source = BpmSource.valueOf(json.getString("source"))
            val confidence = json.optDouble("confidence", Double.NaN).takeIf { it.isFinite() }
            val alternative = json.optDouble("alternative", Double.NaN).takeIf { it.isFinite() }
            BpmRecord(value, source, confidence, alternative)
        }.getOrNull()
    }

    private fun save(track: Track, suffix: String, record: BpmRecord) {
        require(record.bpm.isFinite() && record.bpm in 20.0..800.0)
        val data = JSONObject().put("bpm", record.bpm).put("source", record.source.name)
        record.confidence?.let { data.put("confidence", it) }
        record.alternative?.let { data.put("alternative", it) }
        prefs.edit().putString(key(track) + suffix, data.toString()).apply()
    }

    private fun key(track: Track): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(track.uri.toString().toByteArray(Charsets.UTF_8))
        val hex = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (byte in bytes) {
                val unsigned = byte.toInt() and 0xff
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0f])
            }
        }
    }
}
