package io.github.zzpby.tickcount.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The wish list's stored form: a round trip, and what an older record loads as. */
class WishTest {

    private val wishes = listOf(
        Wish(id = "a", title = "一起去看海", createdAt = 2000L),
        Wish(
            id = "b",
            title = "一起去看演唱会",
            done = true,
            doneEpochDay = LocalDate.of(2026, 5, 1).toEpochDay(),
            createdAt = 1000L,
        ),
    )

    @Test
    fun `a wish list survives a round trip`() {
        val restored = WishCodec.decode(WishCodec.encode(wishes))

        assertEquals(wishes, restored)
    }

    @Test
    fun `a record without the added-at stamp still loads`() {
        val legacy = JSONArray().put(
            org.json.JSONObject().put("i", "c").put("t", "一起去看海")
        )
        val restored = WishCodec.decode(legacy)

        assertEquals("c", restored[0].id)
        assertEquals("一起去看海", restored[0].title)
        assertFalse(restored[0].done)
        assertEquals(0L, restored[0].createdAt)
    }

    @Test
    fun `an unticked wish carries no done day`() {
        val restored = WishCodec.decode(WishCodec.encode(wishes))
        assertTrue(restored[0].doneEpochDay == null)
    }

    @Test
    fun `encoding is stable for the same list`() {
        assertEquals(WishCodec.encode(wishes).toString(), WishCodec.encode(wishes).toString())
    }
}
