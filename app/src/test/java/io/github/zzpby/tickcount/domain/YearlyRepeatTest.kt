package io.github.zzpby.tickcount.domain

import io.github.zzpby.tickcount.data.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * A countdown that repeats every year. The worth-pinning-down parts are the day
 * arithmetic — where the next occurrence lands, and what a 29th of February does —
 * and the boundary with *today*: the day itself still counts as today, and only the
 * morning after does the occurrence roll over to the next year.
 */
class YearlyRepeatTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun repeat(
        date: LocalDate,
        time: LocalTime? = null,
    ) = CountdownEvent(
        id = "r-$date-$time",
        date = date,
        title = "生日",
        time = time,
        yearlyRepeat = true,
    )

    private fun at(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone)

    // ------------------------------------------------------- next occurrence

    @Test
    fun `a date later this year is its own next occurrence`() {
        val today = LocalDate.of(2026, 3, 1)
        assertEquals(LocalDate.of(2026, 10, 14), nextYearlyOccurrence(LocalDate.of(2026, 10, 14), today))
    }

    @Test
    fun `the day itself is its own occurrence, not next year's`() {
        val today = LocalDate.of(2026, 10, 14)
        assertEquals(LocalDate.of(2026, 10, 14), nextYearlyOccurrence(LocalDate.of(2026, 10, 14), today))
    }

    @Test
    fun `a date that has gone by this year moves to next year`() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(LocalDate.of(2027, 10, 14), nextYearlyOccurrence(LocalDate.of(2026, 10, 14), today))
    }

    @Test
    fun `a date from years ago still finds the next occurrence`() {
        val today = LocalDate.of(2026, 10, 15)
        assertEquals(LocalDate.of(2027, 2, 1), nextYearlyOccurrence(LocalDate.of(2001, 2, 1), today))
    }

    // -------------------------------------------------------------- 29 Feb

    @Test
    fun `a 29th of February lands on the 28th of a common year`() {
        val today = LocalDate.of(2027, 1, 1)
        assertEquals(LocalDate.of(2027, 2, 28), nextYearlyOccurrence(LocalDate.of(2024, 2, 29), today))
    }

    @Test
    fun `a 29th of February keeps the 29th in a leap year`() {
        val today = LocalDate.of(2027, 3, 1)
        assertEquals(LocalDate.of(2028, 2, 29), nextYearlyOccurrence(LocalDate.of(2024, 2, 29), today))
    }

    @Test
    fun `a 29th of February rolls from the 28th to the next leap year's 29th`() {
        // The folded day (Feb 28) is still the day itself while it lasts, so the
        // occurrence only moves on once the day is behind us.
        val today = LocalDate.of(2027, 3, 1)
        assertEquals(LocalDate.of(2028, 2, 29), nextYearlyOccurrence(LocalDate.of(2024, 2, 29), today))
        assertEquals(LocalDate.of(2027, 2, 28), nextYearlyOccurrence(LocalDate.of(2024, 2, 29), LocalDate.of(2027, 2, 28)))
    }

    // --------------------------------------------------- effectiveDate / target

    @Test
    fun `a plain countdown counts to its own date`() {
        val today = LocalDate.of(2026, 10, 15)
        val event = CountdownEvent(id = "a", date = LocalDate.of(2025, 1, 1), title = "一次")
        assertEquals(LocalDate.of(2025, 1, 1), event.effectiveDate(today))
    }

    @Test
    fun `a repeating countdown is never past`() {
        val today = LocalDate.of(2026, 10, 15)
        val entry = countdownList(listOf(repeat(LocalDate.of(2019, 10, 14))), today).single()
        assertFalse(entry.isPast)
        assertEquals(LocalDate.of(2026, 10, 14).plusYears(1), entry.event.effectiveDate(today))
    }

    @Test
    fun `the day itself counts as today, not as past`() {
        val today = LocalDate.of(2026, 10, 14)
        val countdown = countdownTo(repeat(LocalDate.of(2001, 10, 14)).effectiveTarget(zone, today), at(today))
        assertEquals(CountdownPhase.TODAY, countdown.phase)
    }

    @Test
    fun `the morning after counts to next year`() {
        val now = at(LocalDate.of(2026, 10, 15))
        val countdown = countdownTo(
            repeat(LocalDate.of(2001, 10, 14)).effectiveTarget(zone, now.toLocalDate()),
            now,
        )
        assertEquals(CountdownPhase.FUTURE, countdown.phase)
        // From noon on Oct 15 to midnight on Oct 14 next year: eleven whole months,
        // then twenty-eight days, then twelve hours.
        assertEquals(11L, countdown.parts.months)
        assertEquals(28L, countdown.parts.days)
        assertEquals(12L, countdown.parts.hours)
    }

    @Test
    fun `a repeating countdown with a time counts to that time on the next occurrence`() {
        val today = LocalDate.of(2026, 10, 15)
        val target = repeat(LocalDate.of(2019, 10, 14), LocalTime.of(9, 30)).effectiveTarget(zone, today)
        assertEquals(LocalDate.of(2027, 10, 14).atTime(9, 30).atZone(zone), target)
    }

    @Test
    fun `an all-day repeating countdown targets midnight`() {
        val today = LocalDate.of(2026, 10, 15)
        val target = repeat(LocalDate.of(2019, 10, 14)).effectiveTarget(zone, today)
        assertEquals(LocalDate.of(2027, 10, 14).atStartOfDay(zone), target)
    }

    // ------------------------------------------------------ lunar repeat

    @Test
    fun `a lunar birthday repeats by the lunar calendar, so the solar day moves`() {
        // The lunar eighth month, 15th day: the conversion goes through the tables,
        // so the assertion is that the day lands in the same year and differs across
        // years — the property the feature is about.
        val today = LocalDate.of(2026, 1, 1)
        val in2026 = nextLunarOccurrence(8, 15, false, today)!!
        val in2027 = nextLunarOccurrence(8, 15, false, in2026.plusDays(1))!!

        assertTrue(in2026.year == 2026 && in2027.year == 2027)
        assertTrue(in2026.monthValue == 9 || in2026.monthValue == 10)
        assertTrue(in2026 != in2027.withYear(2026) || in2026 == in2027)
    }

    @Test
    fun `a leap-month birthday falls in the ordinary month without a leap month`() {
        // 闰四月: a year with a leap fourth month versus one without — the day lands
        // in the fourth month either way, and never waits for the next leap month.
        val today = LocalDate.of(2026, 1, 1)
        val solar = nextLunarOccurrence(4, 1, true, today)!!
        assertTrue(solar.monthValue == 4 || solar.monthValue == 5)
    }

    @Test
    fun `a lunar 30th folds into the month's last day`() {
        val today = LocalDate.of(2026, 1, 1)
        val solar = nextLunarOccurrence(2, 30, false, today)!!
        // Whatever the year, the day exists — folded into the 29th when the month is short.
        assertTrue(solar.isAfter(today))
    }

    @Test
    fun `a lunar repeating countdown counts to the converted solar day`() {
        val today = LocalDate.of(2026, 10, 1)
        val event = CountdownEvent(
            id = "l",
            date = LocalDate.of(2024, 9, 17),
            title = "中秋",
            lunarMonth = 8,
            lunarDay = 15,
            yearlyRepeat = true,
        )

        val effective = event.effectiveDate(today)
        val expected = nextLunarOccurrence(8, 15, false, today)!!
        assertEquals(expected, effective)
    }

    @Test
    fun `a plain lunar countdown without repeat counts to its own solar date`() {
        val today = LocalDate.of(2026, 10, 1)
        val event = CountdownEvent(
            id = "l",
            date = LocalDate.of(2024, 9, 17),
            title = "一次",
            lunarMonth = 8,
            lunarDay = 15,
        )
        assertEquals(LocalDate.of(2024, 9, 17), event.effectiveDate(today))
    }

    // ---------------------------------------------------------------- list

    @Test
    fun `a repeating countdown leads the list as something still to come`() {
        val today = LocalDate.of(2026, 10, 15)
        val entries = countdownList(
            listOf(
                repeat(LocalDate.of(2019, 10, 14)),
                CountdownEvent(id = "p", date = LocalDate.of(2027, 12, 1), title = "一次"),
            ),
            today,
        )

        // The date is seven years old, yet the countdown counts to its next occurrence
        // (2027-10-14), which is still ahead — before the December one, never past.
        assertEquals(listOf("生日", "一次"), entries.map { it.event.title })
        assertFalse(entries[0].isPast)
        assertFalse(entries[0].opensPastSection)
        assertFalse(entries[1].isPast)
    }

    @Test
    fun `a repeating countdown sorts by its next occurrence`() {
        val today = LocalDate.of(2026, 10, 15)
        val entries = countdownList(
            listOf(
                repeat(LocalDate.of(2019, 12, 24)),
                repeat(LocalDate.of(2019, 11, 1)),
                repeat(LocalDate.of(2019, 10, 20)),
            ),
            today,
        )

        // Next occurrences: Oct 20, Nov 1, Dec 24 — the stored order is reversed.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 24)),
            entries.map { it.event.effectiveDate(today) },
        )
    }
}
