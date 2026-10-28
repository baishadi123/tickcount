package io.github.zzpby.tickcount.domain

import kotlin.math.floor

/**
 * Turning what the microphone heard into the wave a letter draws: raw
 * recorder amplitudes (0–32767) folded into at most [count] bars of 0–100.
 *
 * A long take is folded by peak — each bucket keeps its loudest moment, so a
 * laugh is never averaged away. A short take is stretched instead: the bars
 * repeat rather than vanish, so a whisper leaves the same shape it made.
 */
object VoiceWaveform {

    /** The loudest a microphone sample reports. */
    const val MAX_AMPLITUDE = 32767

    fun bars(raw: List<Int>, count: Int = 120): List<Int> {
        if (count <= 0) return emptyList()
        if (raw.isEmpty()) return List(count) { 0 }

        val bars = if (raw.size >= count) {
            val bucket = raw.size.toDouble() / count
            (0 until count).map { index ->
                val from = floor(bucket * index).toInt()
                val to = minOf(floor(bucket * (index + 1)).toInt(), raw.size)
                bar((from until maxOf(from + 1, to)).maxOf { raw[it] })
            }
        } else {
            // Stretched: the take is sampled evenly across the bars, so a
            // whisper leaves the same shape it made, only wider.
            (0 until count).map { index ->
                bar(raw[((index.toLong() * raw.size) / count).toInt().coerceIn(0, raw.size - 1)])
            }
        }
        return bars
    }

    private fun bar(amplitude: Int): Int {
        val scaled = amplitude.toDouble() / MAX_AMPLITUDE * 100.0
        // A floor of a few points keeps even a quiet word visible as a mark.
        return scaled.toInt().coerceIn(0, 100).coerceAtLeast(if (amplitude > 0) MIN_BAR else 0)
    }

    private const val MIN_BAR = 6
}
