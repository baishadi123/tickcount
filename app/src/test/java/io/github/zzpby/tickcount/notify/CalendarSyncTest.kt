package io.github.zzpby.tickcount.notify

import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.TimeCapsule
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the calendar ought to hold, and how it is brought in step.
 *
 * The diff is the feature: a wrong row is a reminder that never comes, or one
 * that comes for a day already gone. It is tested away from the provider, the
 * way the moments are.
 */
class CalendarSyncTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = LocalDate.of(2026, 11, 5).atTime(12, 0).atZone(zone).toInstant()

    private fun allDay(
        id: String = "a",
        date: LocalDate = LocalDate.of(2026, 11, 9),
        lead: Int? = null,
    ) = CountdownEvent(id = id, date = date, title = "旅行", remindLeadMinutes = lead)

    private fun capsule(
        id: String = "c",
        opens: LocalDate = LocalDate.of(2026, 11, 20),
        opened: LocalDate? = null,
        title: String = "一封信",
    ) = TimeCapsule(
        id = id,
        message = "信",
        title = title,
        opensEpochDay = opens.toEpochDay(),
        openedEpochDay = opened?.toEpochDay(),
    )

    private fun local(instant: Instant) = instant.atZone(zone).toLocalDateTime()

    // ------------------------------------------------------------ desiredEntries

    @Test
    fun `a future countdown is one entry at its anchor moment, lead as the reminder`() {
        val entries = Reminders.desiredEntries(
            listOf(allDay()), emptyList(), minutes = 9 * 60, leadMinutes = 30,
            appName = "App", now = now, zone = zone,
        )
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("event/a", entry.key)
        assertEquals("旅行", entry.title)
        assertEquals(LocalDate.of(2026, 11, 9).atTime(9, 0), local(entry.start))
        assertEquals(30, entry.reminderMinutes)
    }

    @Test
    fun `a countdown's own lead is the entry's reminder, not the app-wide one`() {
        val entries = Reminders.desiredEntries(
            listOf(allDay(lead = 3 * 24 * 60)), emptyList(), 9 * 60, 30,
            "App", now, zone,
        )
        assertEquals(3 * 24 * 60, entries.first().reminderMinutes)
    }

    @Test
    fun `a countdown whose reminder moment has passed is not written`() {
        // The anchor is tomorrow 09:00, the lead two days: the reminder was due
        // yesterday, and yesterday is not worth a reminder.
        val past = allDay(date = LocalDate.of(2026, 11, 6), lead = 2 * 24 * 60)
        val entries = Reminders.desiredEntries(listOf(past), emptyList(), 9 * 60, 0, "App", now, zone)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `an opened capsule is never written`() {
        val opened = capsule(opened = LocalDate.of(2026, 11, 1))
        val entries = Reminders.desiredEntries(emptyList(), listOf(opened), 9 * 60, 0, "App", now, zone)
        assertTrue(entries.isEmpty())
    }

    @Test
    fun `an unopened capsule with a future day is written at the app's time of day`() {
        val entries = Reminders.desiredEntries(
            emptyList(), listOf(capsule()), 9 * 60, 60, "App", now, zone,
        )
        val entry = entries.first()
        assertEquals("capsule/c", entry.key)
        assertEquals(LocalDate.of(2026, 11, 20).atTime(9, 0), local(entry.start))
        assertEquals(60, entry.reminderMinutes)
    }

    @Test
    fun `a capsule without a title is written under the app's own name`() {
        val entries = Reminders.desiredEntries(
            emptyList(), listOf(capsule(title = "")), 9 * 60, 0, "倒数日", now, zone,
        )
        assertEquals("倒数日", entries.first().title)
    }

    @Test
    fun `a repeating countdown is written at its next occurrence`() {
        val birthday = CountdownEvent(
            id = "b",
            date = LocalDate.of(2023, 11, 9),
            title = "生日",
            yearlyRepeat = true,
        )
        val entries = Reminders.desiredEntries(listOf(birthday), emptyList(), 9 * 60, 0, "App", now, zone)
        assertEquals(LocalDate.of(2026, 11, 9).atTime(9, 0), local(entries.first().start))
    }

    // ---------------------------------------------------------------- syncPlan

    private fun existing(
        key: String,
        title: String = "旅行",
        start: Instant = LocalDate.of(2026, 11, 9).atTime(9, 0).atZone(zone).toInstant(),
        lead: Int = 30,
        rowId: Long = 1,
    ) = Reminders.ExistingEntry(rowId, key, title, start, lead)

    @Test
    fun `a key the calendar does not hold is inserted`() {
        val plan = Reminders.syncPlan(
            listOf(Reminders.CalendarEntry("event/a", "旅行", now.plusSeconds(60), 30)),
            listOf(existing("event/other")),
        )
        assertEquals(listOf("event/a"), plan.insert.map { it.key })
        assertEquals(listOf(1L), plan.delete.map { it.rowId })
        assertTrue(plan.update.isEmpty())
    }

    @Test
    fun `a changed moment, title or lead rewrites the row in place`() {
        val entry = Reminders.CalendarEntry("event/a", "旅行(改)", now.plusSeconds(120), 45)
        val plan = Reminders.syncPlan(listOf(entry), listOf(existing("event/a")))
        assertTrue(plan.insert.isEmpty() && plan.delete.isEmpty())
        assertEquals(listOf("event/a"), plan.update.map { it.second.key })
        assertEquals(45, plan.update.first().second.reminderMinutes)
    }

    @Test
    fun `a row that already says the right thing is left alone`() {
        val entry = Reminders.CalendarEntry("event/a", "旅行", existing("event/a").start, 30)
        val plan = Reminders.syncPlan(listOf(entry), listOf(existing("event/a")))
        assertTrue(plan.insert.isEmpty() && plan.update.isEmpty() && plan.delete.isEmpty())
    }

    @Test
    fun `a row whose countdown is gone is deleted, not recreated`() {
        val plan = Reminders.syncPlan(emptyList(), listOf(existing("event/a"), existing("capsule/c")))
        assertEquals(listOf("event/a", "capsule/c"), plan.delete.map { it.key })
        assertTrue(plan.insert.isEmpty() && plan.update.isEmpty())
    }
}
