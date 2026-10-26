package io.github.zzpby.tickcount.data

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The footprint list as JSON: order, caps and the fields that may be absent.
 */
class FootprintTest {

    private fun footprint(
        id: String = "f1",
        place: String = "西湖",
        date: LocalDate = LocalDate.of(2026, 5, 1),
        photos: List<String> = emptyList(),
        latitude: Double? = null,
        longitude: Double? = null,
    ) = Footprint(
        id = id,
        place = place,
        epochDay = date.toEpochDay(),
        photos = photos,
        latitude = latitude,
        longitude = longitude,
        createdAt = 100L,
    )

    @Test
    fun `a footprint round-trips with its photos and coordinates`() {
        val original = listOf(
            footprint(
                photos = listOf("fp-a-1.jpg", "fp-a-2.gif", "fp-a-3.mp4"),
                latitude = 30.241,
                longitude = 120.145,
            ),
        )

        val restored = FootprintCodec.decode(FootprintCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `a footprint without photos or coordinates round-trips bare`() {
        val original = listOf(footprint())

        val restored = FootprintCodec.decode(FootprintCodec.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `footprints encode by the day, and newest first within a day`() {
        val may = footprint(id = "may", place = "a", date = LocalDate.of(2026, 5, 10))
        val april = footprint(id = "april", place = "b", date = LocalDate.of(2026, 4, 2))
        val mayLater = footprint(id = "may-later", place = "c", date = LocalDate.of(2026, 5, 10))
            .copy(createdAt = 200L)

        val encoded = FootprintCodec.encode(listOf(may, april, mayLater))
        val order = FootprintCodec.decode(encoded).map { it.id }

        // Chronological by the day; within one day, the later-created leads —
        // the reading order of a visit.
        assertEquals(listOf("april", "may-later", "may"), order)
    }

    @Test
    fun `an entry without a place or a day is dropped, not half-read`() {
        val good = footprint()
        val noPlace = JSONArray().put(org.json.JSONObject().put("d", 20000L))
        val noDay = JSONArray().put(org.json.JSONObject().put("p", "无期的"))

        assertEquals(
            listOf(good),
            FootprintCodec.decode(
                FootprintCodec.encode(listOf(good)).put(noPlace.optJSONObject(0)).put(noDay.optJSONObject(0)),
            ),
        )
    }

    @Test
    fun `photos are de-duplicated and capped at the editor's ceiling`() {
        val many = (1..20).map { "fp-f1-$it.jpg" }
        val restored = FootprintCodec.decode(
            JSONArray().put(
                org.json.JSONObject()
                    .put("i", "f1")
                    .put("p", "西湖")
                    .put("d", 20000L)
                    .put("ph", JSONArray(many + many.first())),
            ),
        )

        assertEquals(Footprint.MAX_PHOTOS, restored.single().photos.size)
    }
}
