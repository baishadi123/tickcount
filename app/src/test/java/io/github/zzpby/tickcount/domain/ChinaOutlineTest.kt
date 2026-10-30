package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The border the map masks beyond: the parse rules, and the embedded
 * outline's own honesty — a garbled or hollowed file must fail here rather
 * than paint a wrong country.
 */
class ChinaOutlineTest {

    @Test
    fun `parses rings and skips comments and malformed points`() {
        val rings = ChinaOutline.rings(
            """
            # a comment
            30.00,120.00 30.01,120.01 30.02,120.00 30.00,119.99
            not,a ring
            40.00,100.00 40.00,100.01
            """.trimIndent(),
        )

        assertEquals(1, rings.size)
        assertEquals(4, rings[0].size)
        assertEquals(30.0, rings[0][0].first, 1e-9)
        assertEquals(120.0, rings[0][0].second, 1e-9)
    }

    @Test
    fun `the embedded outline holds the country`() {
        val file = listOf(
            File("src/main/res/raw/china_outline.md"),
            File("app/src/main/res/raw/china_outline.md"),
        ).firstOrNull { it.isFile } ?: error("china_outline.md not found from ${File(".").absolutePath}")

        val text = file.readText()
        assertTrue(
            "replacement characters found: ${text.count { it == '�' }}",
            !text.contains('�'),
        )

        val rings = ChinaOutline.rings(text)

        // The mainland plus the larger islands; islets were dropped on purpose.
        assertTrue("expected ~55 rings, got ${rings.size}", rings.size in 30..120)
        val points = rings.sumOf { it.size }
        assertTrue("expected ~5800 points, got $points", points > 4000)
        rings.forEach { ring ->
            ring.forEach { (lat, lng) ->
                assertTrue("lat $lat", lat in 0.0..60.0)
                assertTrue("lng $lng", lng in 70.0..140.0)
            }
        }
        // The mainland is the long walk by far.
        assertTrue(
            "largest ring suspiciously short",
            rings.maxOf { it.size } > 1000,
        )
    }
}
