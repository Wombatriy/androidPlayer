package com.wombatriy.androidplayer.bpm

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Light-weight autocorrelation of attack strengths calculated from PCM energy.
 *
 * This is an INITIAL tempo estimate, not beat tracking. Half- and double-time
 * hypotheses cannot always be distinguished, particularly in syncopated music.
 * All numerical analysis is independent of Android and is JVM-testable.
 */
object BeatEstimator {
    data class Estimate(
        val bpm: Double,
        val confidence: Double,
        val alternativeBpm: Double?
    )

    fun estimate(energies: DoubleArray, framesPerSecond: Int = 100): Estimate? {
        if (framesPerSecond <= 0 || energies.size < framesPerSecond * 8) return null

        val attacks = DoubleArray(energies.size)
        var previous = 0.0
        var total = 0.0
        for (i in energies.indices) {
            val level = ln(1.0 + max(0.0, energies[i]) * 20.0)
            val attack = max(0.0, level - previous)
            attacks[i] = attack
            total += attack
            previous = level
        }

        val localFloor = total / attacks.size * 0.6
        var attackEnergy = 0.0
        for (i in attacks.indices) {
            attacks[i] = max(0.0, attacks[i] - localFloor)
            attackEnergy += attacks[i] * attacks[i]
        }
        if (attackEnergy < 1e-6) return null

        // The 55–205 BPM search covers most dance tempos; manual multipliers
        // are available for tempos and metrical interpretations outside it.
        val minLag = (framesPerSecond * 60.0 / 205).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSecond * 60.0 / 55).toInt()
            .coerceAtMost(attacks.size / 4)
        if (maxLag <= minLag) return null

        val correlation = DoubleArray(maxLag * 3 + 2)
        for (lag in minLag..(maxLag * 3)) {
            var score = 0.0
            for (i in lag until attacks.size) {
                score += attacks[i] * attacks[i - lag]
            }
            correlation[lag] = score / attackEnergy
        }

        var winningLag = minLag
        var winningScore = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val bpm = framesPerSecond * 60.0 / lag
            val harmonic = correlation[lag] +
                0.35 * correlation[2 * lag] +
                0.15 * correlation[3 * lag]
            // Only a slight conventional-tempo preference. Do not conceal
            // possible half-time/double-time errors with fake confidence.
            val preference = 1.0 - 0.045 * abs(ln(bpm / 120.0) / ln(2.0))
            val score = harmonic * preference
            if (score > winningScore) {
                winningLag = lag
                winningScore = score
            }
        }

        // Sub-bin peak interpolation improves resolution above 150 BPM.
        val left = correlation[(winningLag - 1).coerceAtLeast(minLag)]
        val center = correlation[winningLag]
        val right = correlation[(winningLag + 1).coerceAtMost(maxLag)]
        val curvature = left - 2.0 * center + right
        val delta = if (curvature < -1e-8) {
            (0.5 * (left - right) / curvature).coerceIn(-0.5, 0.5)
        } else 0.0
        val tempo = (framesPerSecond * 60.0 / (winningLag + delta))
            .coerceIn(55.0, 205.0)
        val confidence = center.coerceIn(0.0, 1.0)
        if (confidence < 0.06) return null

        val bpm = (tempo * 10).roundToInt() / 10.0
        val alternate = if (bpm >= 110) bpm / 2 else bpm * 2
        return Estimate(bpm, confidence, (alternate * 10).roundToInt() / 10.0)
    }
}
