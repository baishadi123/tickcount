package io.github.zzpby.tickcount.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The capsule record on its journey out and back: a voice letter's fields
 * survive the codec, and a record written before voices existed decodes into
 * a capsule with none — the old JSON can never be a stranger.
 */
class CapsuleCodecTest {

    private fun capsule(
        voice: String? = "cv-x-1.m4a",
        seconds: Int = 95,
        bars: List<Int> = listOf(6, 40, 90),
    ) = TimeCapsule(
        id = "cap-1",
        message = "给那一天的话",
        title = "一封信",
        opensEpochDay = 20700,
        photos = listOf("bg-a.jpg", "bg-b.jpg"),
        createdAt = 1234L,
        voice = voice,
        voiceSeconds = seconds,
        voiceBars = bars,
    )

    @Test
    fun `a voice letter travels the codec whole`() {
        val decoded = CapsuleCodec.decode(CapsuleCodec.encode(listOf(capsule()))).single()

        assertEquals("cv-x-1.m4a", decoded.voice)
        assertEquals(95, decoded.voiceSeconds)
        assertEquals(listOf(6, 40, 90), decoded.voiceBars)
        assertEquals("给那一天的话", decoded.message)
        assertEquals(listOf("bg-a.jpg", "bg-b.jpg"), decoded.photos)
    }

    @Test
    fun `a capsule without a voice decodes without one`() {
        val decoded = CapsuleCodec.decode(CapsuleCodec.encode(listOf(capsule(voice = null)))).single()

        assertNull(decoded.voice)
        assertEquals(0, decoded.voiceSeconds)
        assertTrue(decoded.voiceBars.isEmpty())
    }

    @Test
    fun `json written before voices existed still decodes`() {
        val legacy = JSONArray(
            """[{"i":"cap-0","m":"旧信","o":20000,"a":9}]""",
        )

        val decoded = CapsuleCodec.decode(legacy).single()

        assertEquals("旧信", decoded.message)
        assertNull(decoded.voice)
        assertTrue(decoded.voiceBars.isEmpty())
    }

    @Test
    fun `bars are clamped and capped`() {
        val many = List(500) { 999 }
        val decoded = CapsuleCodec.decode(CapsuleCodec.encode(listOf(capsule(bars = many)))).single()

        assertTrue("bars must be capped", decoded.voiceBars.size <= 160)
        assertTrue(decoded.voiceBars.all { it in 0..100 })
    }

    @Test
    fun `removing a voice removes it from the record too`() {
        val sealed = CapsuleCodec.encode(listOf(capsule(voice = null, bars = listOf(10, 20)))).toString()

        assertTrue(!sealed.contains("\"v\""))
    }
}
