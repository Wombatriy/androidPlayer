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

    fun adjusted(value: Double, speed: Float): String =
        bpm(value * speed)
}

/**
 * Saves per-track metadata by SAF URI. A manual adjustment overrides a BPM
 * prefix; filename BPM overrides a previously detected value.
 *
 * These preferences are for ONE-TRACK analysis in iteration 03. Batch
 * processing can move to a proper indexed database in a later iteration.
 */
class BpmRepository(context: Context) {
    private val prefs = context.getSharedPreferences("bpm-results-v1", Context.MODE_PRIVATE)

    fun resolved(track: Track): BpmRecord? {
        val saved = stored(track)
        if (saved?.source == BpmSource.MANUAL) return saved
        track.filenameBpm?.let { return BpmRecord(it.toDouble(), BpmSource.FILENAME) }
        return saved
    }

    fun stored(track: Track): BpmRecord? {
        val serialized = prefs.getString(key(track), null) ?: return null
        return runCatching {
            val json = JSONObject(serialized)
            val value = json.getDouble("bpm")
            require(value in 20.0..800.0 && value.isFinite())
            val origin = BpmSource.valueOf(json.getString("source"))
            val confidence = json.optDouble("confidence", Double.NaN).takeIf { it.isFinite() }
            val alternative = json.optDouble("alternative", Double.NaN).takeIf { it.isFinite() }
            BpmRecord(value, origin, confidence, alternative)
        }.getOrNull()
    }

    fun save(track: Track, record: BpmRecord) {
        require(record.bpm in 20.0..800.0 && record.bpm.isFinite())
        val json = JSONObject()
            .put("bpm", record.bpm)
            .put("source", record.source.name)
        record.confidence?.let { json.put("confidence", it) }
        record.alternative?.let { json.put("alternative", it) }
        prefs.edit().putString(key(track), json.toString()).apply()
    }

    fun clear(track: Track) {
        prefs.edit().remove(key(track)).apply()
    }

    private fun key(track: Track): String {
        val bytes = MessageDigest.getInstance("SHA-256")
            .digest(track.uri.toString().toByteArray(Charsets.UTF_8))
        val digits = "0123456789abcdef"
        val builder = StringBuilder(bytes.size * 2)
        for (value in bytes) {
            val unsigned = value.toInt() and 0xff
            builder.append(digits[unsigned ushr 4]).append(digits[unsigned and 15])
        }
        return builder.toString()
    }
}
