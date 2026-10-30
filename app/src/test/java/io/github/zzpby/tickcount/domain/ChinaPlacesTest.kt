package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The offline map that names a visit's spot: the parse rules, the nearest
 * rule, and the honesty rule — beyond its reach the map answers nothing at
 * all, which is why it may be trusted when it does answer.
 */
class ChinaPlacesTest {

    private val hangzhou = ChinaPlaces.Place(30.24, 120.16, "杭州·西湖")
    private val beijing = ChinaPlaces.Place(39.93, 116.42, "北京·东城")

    @Test
    fun `parses the table's rows and skips the rest`() {
        val places = ChinaPlaces.parse(
            """
            # a comment
            30.24|120.16|杭州,西湖
            not a row
            39.93|116.42|北京,东城
            """.trimIndent(),
        )

        assertEquals(2, places.size)
        assertEquals("杭州·西湖", places[0].display)
        assertEquals(30.24, places[0].latitude, 1e-9)
    }

    @Test
    fun `the nearest district wins`() {
        val named = ChinaPlaces.nearest(listOf(hangzhou, beijing), 30.30, 120.10)
        assertEquals("杭州·西湖", named?.display)
    }

    @Test
    fun `beyond sixty kilometres the map stays silent`() {
        // Half a degree of latitude is about 55 km — inside; seven tenths is
        // about 77 km — outside, and honesty beats a wrong label.
        val inside = ChinaPlaces.nearest(listOf(beijing), 39.50, 116.42)
        val outside = ChinaPlaces.nearest(listOf(beijing), 38.80, 116.42)
        assertNotNull(inside)
        assertNull(outside)
    }

    @Test
    fun `an empty map names nothing`() {
        assertNull(ChinaPlaces.nearest(emptyList(), 30.0, 120.0))
    }

    @Test
    fun `the embedded table holds the country`() {
        val file = listOf(
            File("src/main/res/raw/china_places.md"),
            File("app/src/main/res/raw/china_places.md"),
        ).firstOrNull { it.isFile } ?: error("china_places.md not found from ${File(".").absolutePath}")

        val text = file.readText()
        // A U+FFFD in the table means a character was lost to a wrong
        // transcode on the way in — the first table shipped 742 such lines.
        assertTrue(
            "replacement characters found: ${text.count { it == '�' }}",
            !text.contains('�'),
        )

        val places = ChinaPlaces.parse(text)

        assertTrue("expected ~3000 districts, got ${places.size}", places.size in 2500..4000)
        places.forEach { place ->
            assertTrue("lat ${place.latitude}", place.latitude in 3.0..54.0)
            assertTrue("lng ${place.longitude}", place.longitude in 73.0..136.0)
            // Most read 省·市·区; a bare city name is honest too. A name
            // without a single CJK char is a transcode scar, not a place.
            assertTrue("display ${place.display}", place.display.isNotBlank())
            assertTrue(
                "display without CJK: ${place.display}",
                place.display.any { it in '\u4E00'..'\u9FFF' },
            )
            assertTrue("question mark in ${place.display}", !place.display.contains('?'))
        }
        // Names the first table actually damaged — they stay as anchors.
        val displays = places.map { it.display }
        assertTrue("石景山 missing", displays.any { it.contains("石景山") })
        assertTrue("门头沟 missing", displays.any { it.contains("门头沟") })
        assertTrue("杭州 missing", displays.any { it.contains("杭州") && it.contains("西湖") })
    }
}
