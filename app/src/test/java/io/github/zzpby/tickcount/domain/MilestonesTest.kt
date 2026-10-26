package io.github.zzpby.tickcount.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * The date a milestone lands on. The anniversary itself is day 1 — an
 * off-by-one here would mark the hundredth day one too early, which is the
 * kind of thing only noticed on the wrong morning.
 */
class MilestonesTest {

    private val anniversary = LocalDate.of(2026, 1, 1)

    @Test
    fun `day one is the anniversary itself`() {
        assertEquals(anniversary, milestoneDate(anniversary, 1))
    }

    @Test
    fun `the hundredth day is ninety-nine days on`() {
        // January has 31 days: day 100 of a New Year anniversary lands in April.
        assertEquals(LocalDate.of(2026, 4, 10), milestoneDate(anniversary, 100))
    }

    @Test
    fun `the set is the love numbers, in day order`() {
        assertEquals(listOf(100L, 365L, 520L, 999L, 1314L), MILESTONE_DAYS.sorted())
    }

    @Test
    fun `the milestones come out in date order`() {
        val dates = milestones(anniversary).map { it.second }
        assertEquals(dates.sorted(), dates)
    }

    @Test
    fun `each milestone carries its own day number`() {
        val days = milestones(anniversary).map { it.first }
        assertEquals(MILESTONE_DAYS.sorted(), days)
    }

    @Test
    fun `milestones cross February in a leap year correctly`() {
        val leapStart = LocalDate.of(2024, 2, 1)
        // Day 30 of a February 1st anniversary: 29 days on, over the leap day.
        assertEquals(LocalDate.of(2024, 3, 1), milestoneDate(leapStart, 30))
    }
}
