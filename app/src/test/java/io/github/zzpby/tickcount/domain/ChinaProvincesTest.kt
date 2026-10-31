package io.github.zzpby.tickcount.domain

import io.github.zzpby.tickcount.domain.ChinaProvinces.Province
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The province table: parsing the embedded format, and the point-in-ring
 * decision the lit map is made of. The shapes here are synthetic — the
 * real table is a resource, and what matters about it is the reading, not
 * the copying.
 */
class ChinaProvincesTest {

    /** A closed rectangle from (minLat, minLng) to (maxLat, maxLng). */
    private fun box(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double) = listOf(
        minLat to minLng,
        maxLat to minLng,
        maxLat to maxLng,
        minLat to maxLng,
    )

    // ------------------------------------------------------------ the lookup

    @Test
    fun `a point inside a ring names its province`() {
        val provinces = listOf(
            Province("甲", listOf(box(0.0, 0.0, 2.0, 2.0))),
            Province("乙", listOf(box(3.0, 3.0, 5.0, 5.0), box(10.0, 10.0, 11.0, 11.0))),
        )

        assertEquals("甲", ChinaProvinces.provinceOf(provinces, 1.0, 1.0))
        assertEquals("乙", ChinaProvinces.provinceOf(provinces, 4.0, 4.0))
        // The second ring of 乙 — an island — is as much 乙 as the mainland.
        assertEquals("乙", ChinaProvinces.provinceOf(provinces, 10.5, 10.5))
    }

    @Test
    fun `a point outside every ring names nothing`() {
        val provinces = listOf(Province("甲", listOf(box(0.0, 0.0, 2.0, 2.0))))

        assertNull(ChinaProvinces.provinceOf(provinces, 2.5, 2.5))
        assertNull(ChinaProvinces.provinceOf(provinces, 89.0, 179.0))
    }

    @Test
    fun `a point inside the bounding box but outside the shape names nothing`() {
        // An L: its box covers (1.5, 1.5), the shape does not.
        val ell = listOf(
            0.0 to 0.0,
            2.0 to 0.0,
            2.0 to 1.0,
            1.0 to 1.0,
            1.0 to 2.0,
            0.0 to 2.0,
        )
        val provinces = listOf(Province("乙", listOf(ell)))

        assertNull(ChinaProvinces.provinceOf(provinces, 1.5, 1.5))
        assertEquals("乙", ChinaProvinces.provinceOf(provinces, 1.5, 0.5))
    }

    // -------------------------------------------------------------- parsing

    @Test
    fun `rings that share a name are one province`() {
        val text = """
            # 注释行
            河北|40.0,116.0 40.0,117.0 41.0,117.0 41.0,116.0
            河北|42.0,118.0 42.0,118.5 42.5,118.5 42.5,118.0
            山东|36.0,120.0 36.0,121.0 37.0,121.0 37.0,120.0
        """.trimIndent()

        val provinces = ChinaProvinces.parse(text)

        assertEquals(listOf("河北", "山东"), provinces.map { it.name })
        assertEquals(2, provinces[0].rings.size)
        assertEquals(1, provinces[1].rings.size)
    }

    @Test
    fun `lines that are not rings are skipped rather than guessed from`() {
        val text = """
            甲|1.0,1.0 2.0,2.0
            乙
            |1.0,1.0 2.0,1.0 2.0,2.0 1.0,2.0
            丙|不是数字 2.0,1.0 2.0,2.0 1.0,2.0
            丁|0.0,0.0 0.0,1.0 1.0,1.0 1.0,0.0
        """.trimIndent()

        val provinces = ChinaProvinces.parse(text)

        assertEquals(listOf("丁"), provinces.map { it.name })
    }
}
