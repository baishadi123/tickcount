package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 第几天：纪念日当天算第 1 天，跨年、跨闰日照日历翻。 */
class DayNumberTest {

    private val anniversary = LocalDate.of(2024, 2, 14)

    @Test
    fun `the anniversary itself is day one`() {
        assertEquals(1L, dayNumberOf(anniversary, anniversary))
    }

    @Test
    fun `the next day is day two`() {
        assertEquals(2L, dayNumberOf(anniversary, anniversary.plusDays(1)))
    }

    @Test
    fun `a year later is day three hundred and sixty-seven`() {
        // 2024 is a leap year, so the span crosses 366 calendar days.
        assertEquals(367L, dayNumberOf(anniversary, LocalDate.of(2025, 2, 14)))
    }

    @Test
    fun `the same day of a later year counts by the calendar`() {
        // 2024 is a leap year (366 days), 2025 is not (365), so 731 in all.
        assertEquals(732L, dayNumberOf(anniversary, LocalDate.of(2026, 2, 14)))
    }
}
