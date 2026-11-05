package io.github.zzpby.tickcount.notify

import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.TimeCapsule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * When a reminder falls.
 *
 * The rule is one line — a countdown's own time, or the reminder time of day —
 * but the arithmetic is the whole feature, so it is tested away from the
 * calendar provider.
 */
class RemindersTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun allDay(
        id: String = "a",
        date: LocalDate = LocalDate.of(2026, 10, 26),
    ) = CountdownEvent(id = id, date = date, title = "旅行")

    private fun timed(
        id: String = "b",
        date: LocalDate = LocalDate.of(2026, 10, 26),
        time: LocalTime = LocalTime.of(14, 30),
    ) = CountdownEvent(id = id, date = date, title = "开会", time = time)

    private fun local(instant: Instant) = instant.atZone(zone).toLocalDateTime()

    @Test
    fun `an all-day countdown is reminded at the app's time of day`() {
        val moment = Reminders.momentOf(allDay(), minutes = 9 * 60, zone = zone)
        assertEquals(LocalDate.of(2026, 10, 26).atTime(9, 0), local(moment))
    }

    @Test
    fun `a countdown with its own time ignores the app's time of day`() {
        val moment = Reminders.momentOf(timed(), minutes = 9 * 60, zone = zone)
        assertEquals(LocalDate.of(2026, 10, 26).atTime(14, 30), local(moment))
    }

    @Test
    fun `the next reminder is the first that has not already passed`() {
        val past = allDay(id = "past", date = LocalDate.of(2026, 10, 17))
        val soon = allDay(id = "soon", date = LocalDate.of(2026, 10, 26))
        val now = LocalDate.of(2026, 10, 18).atTime(12, 0).atZone(zone).toInstant()

        val planned = Reminders.planned(listOf(soon, past), 9 * 60, now, zone)

        assertEquals("soon", planned?.event?.id)
        assertEquals(LocalDate.of(2026, 10, 26).atTime(9, 0), local(planned!!.at))
    }

    @Test
    fun `two countdowns on one day are ordered by the clock, not the list`() {
        val late = timed(id = "late", time = LocalTime.of(21, 30))
        val early = timed(id = "early", time = LocalTime.of(9, 0))
        val now = LocalDate.of(2026, 10, 26).atTime(8, 0).atZone(zone).toInstant()

        assertEquals("early", Reminders.planned(listOf(late, early), 9 * 60, now, zone)?.event?.id)
    }

    @Test
    fun `a list whose reminders have all passed has nothing to schedule`() {
        val past = allDay(date = LocalDate.of(2026, 10, 20))
        val now = LocalDate.of(2026, 10, 21).atTime(12, 0).atZone(zone).toInstant()
        assertNull(Reminders.planned(listOf(past), 9 * 60, now, zone))
    }

    // ----------------------------------------------------------------- capsules

    private fun capsule(
        id: String = "c",
        opens: LocalDate = LocalDate.of(2026, 10, 26),
    ) = TimeCapsule(id = id, message = "信", opensEpochDay = opens.toEpochDay())

    @Test
    fun `a capsule is reminded at the app's time of day on its open day`() {
        val moment = Reminders.capsuleMomentOf(capsule(), minutes = 9 * 60, zone = zone)
        assertEquals(LocalDate.of(2026, 10, 26).atTime(9, 0), local(moment))
    }
}
