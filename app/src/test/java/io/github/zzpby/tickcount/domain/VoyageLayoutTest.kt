package io.github.zzpby.tickcount.domain

import io.github.zzpby.tickcount.data.CountdownEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * The voyage layout: order, spacing, and the boundary at which a station's
 * name comes up. The boundary is worth pinning down for the same reason every
 * other past/future edge in the app is — an off-by-one there floats a name too
 * early or leaves the station the boat is standing on unlabelled.
 */
class VoyageLayoutTest {

    private val today = LocalDate.of(2026, 9, 25)

    private fun event(
        date: LocalDate,
        title: String,
        time: LocalTime? = null,
        yearlyRepeat: Boolean = false,
    ) = CountdownEvent(
        id = "$date-$title",
        date = date,
        title = title,
        time = time,
        yearlyRepeat = yearlyRepeat,
    )

    // ------------------------------------------------------------------ order

    @Test
    fun `an empty store produces an empty journey`() {
        val stops = voyageStops(emptyList(), today)
        assertEquals(emptyList<VoyageStop>(), stops)
        assertEquals(0f, voyageLength(stops))
    }

    @Test
    fun `stations come out earliest first`() {
        val events = listOf(
            event(LocalDate.of(2027, 1, 1), "new year"),
            event(LocalDate.of(2026, 5, 2), "old meeting"),
            event(LocalDate.of(2026, 10, 1), "birthday"),
        )

        val titles = voyageStops(events, today).map { it.event.title }

        assertEquals(listOf("old meeting", "birthday", "new year"), titles)
    }

    @Test
    fun `same-day stations come out in time order with the all-day one first`() {
        val date = LocalDate.of(2026, 10, 1)
        val events = listOf(
            event(date, "evening", time = LocalTime.of(19, 0)),
            event(date, "morning", time = LocalTime.of(9, 0)),
            event(date, "all day"),
        )

        val titles = voyageStops(events, today).map { it.event.title }

        assertEquals(listOf("all day", "morning", "evening"), titles)
    }

    @Test
    fun `a repeating countdown stations by its next occurrence`() {
        val events = listOf(
            // A birthday entered last year repeats: its next occurrence is next
            // April, which is after the December countdown despite the earlier
            // stored date.
            event(LocalDate.of(2025, 4, 3), "birthday", yearlyRepeat = true),
            event(LocalDate.of(2026, 12, 20), "solstice"),
        )

        val titles = voyageStops(events, today).map { it.event.title }

        assertEquals(listOf("solstice", "birthday"), titles)
    }

    // ---------------------------------------------------------------- spacing

    @Test
    fun `stations are equally spaced along the river`() {
        val events = listOf(
            event(LocalDate.of(2026, 10, 1), "a"),
            event(LocalDate.of(2026, 10, 2), "b"),
            event(LocalDate.of(2026, 10, 3), "c"),
        )

        val positions = voyageStops(events, today).map { it.position }

        assertEquals(listOf(0f, VOYAGE_STEP, 2 * VOYAGE_STEP), positions)
    }

    @Test
    fun `a single station sits at the start and needs no travelling`() {
        val stops = voyageStops(listOf(event(LocalDate.of(2026, 10, 1), "only")), today)

        assertEquals(0f, stops.single().position)
        assertEquals(0f, voyageLength(stops))
    }

    @Test
    fun `the journey runs from the first station to the last`() {
        val events = listOf(
            event(LocalDate.of(2026, 10, 1), "a"),
            event(LocalDate.of(2026, 10, 2), "b"),
            event(LocalDate.of(2026, 10, 3), "c"),
            event(LocalDate.of(2026, 10, 4), "d"),
        )

        assertEquals(3 * VOYAGE_STEP, voyageLength(voyageStops(events, today)))
    }

    // --------------------------------------------------------------- boundary

    @Test
    fun `a station is passed the moment the boat stands on it`() {
        val stops = voyageStops(
            listOf(
                event(LocalDate.of(2026, 10, 1), "a"),
                event(LocalDate.of(2026, 10, 2), "b"),
                event(LocalDate.of(2026, 10, 3), "c"),
            ),
            today,
        )

        val boatAtSecond = VOYAGE_STEP

        assertTrue(stops[0].passed(boatAtSecond))
        assertTrue(stops[1].passed(boatAtSecond))
        assertFalse(stops[2].passed(boatAtSecond))
    }

    @Test
    fun `a station is not passed just before the boat reaches it`() {
        val stops = voyageStops(listOf(event(LocalDate.of(2026, 10, 1), "a")), today)

        assertFalse(stops.single().passed(-0.01f))
        assertTrue(stops.single().passed(0f))
    }
}
