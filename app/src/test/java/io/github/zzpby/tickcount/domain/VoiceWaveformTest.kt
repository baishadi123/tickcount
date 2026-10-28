package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wave a letter draws: raw microphone amplitudes folded into bars. The
 * shape is the letter's memory of a voice, so the folding rules — peaks kept,
 * quiet takes stretched, silence flat — are pinned down here.
 */
class VoiceWaveformTest {

    @Test
    fun `nothing recorded is a flat line`() {
        assertEquals(List(120) { 0 }, VoiceWaveform.bars(emptyList(), 120))
    }

    @Test
    fun `a short take is stretched, not shortened`() {
        val bars = VoiceWaveform.bars(listOf(100, 30000, 200), 8)
        assertEquals(8, bars.size)
        assertTrue("the loud moment must survive stretching", bars.all { it > 0 })
        assertTrue(bars.any { it > 80 })
    }

    @Test
    fun `a long take is folded by its peaks`() {
        val raw = buildList {
            repeat(1000) { index -> add(if (index % 500 == 250) 30000 else 100) }
        }
        val bars = VoiceWaveform.bars(raw, 10)
        assertEquals(10, bars.size)
        // Two loud moments, folded into ten buckets: at least two bars carry
        // the shout instead of it being averaged into the murmur.
        assertTrue("peaks were averaged away", bars.count { it > 80 } >= 2)
    }

    @Test
    fun `bars stay within the paper`() {
        val raw = listOf(0, VoiceWaveform.MAX_AMPLITUDE, -5, 999999)
        val bars = VoiceWaveform.bars(raw, 4)
        bars.forEach { bar -> assertTrue("bar $bar out of range", bar in 0..100) }
    }

    @Test
    fun `absolute silence stays flat but noise shows`() {
        val quiet = VoiceWaveform.bars(List(50) { 0 }, 10)
        assertEquals(List(10) { 0 }, quiet)
        val airy = VoiceWaveform.bars(List(50) { 300 }, 10)
        assertTrue(airy.all { it in 1..10 })
    }

    @Test
    fun `a non-positive count yields nothing`() {
        assertEquals(emptyList<Int>(), VoiceWaveform.bars(listOf(100, 200), 0))
    }
}
