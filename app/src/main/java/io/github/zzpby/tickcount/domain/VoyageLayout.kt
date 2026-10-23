package io.github.zzpby.tickcount.domain

import io.github.zzpby.tickcount.data.CountdownEvent
import java.time.LocalDate

/**
 * One station on the voyage: a saved countdown at a point on the river.
 */
data class VoyageStop(
    val event: CountdownEvent,
    /** How far along the journey the station sits, in the layout's own unit. */
    val position: Float,
)

/** How far apart two neighbouring stations sit, in the layout's own unit. */
const val VOYAGE_STEP = 220f

/**
 * Every saved countdown as a journey: earliest first, one station each, equally
 * spaced along the river.
 *
 * Equal spacing rather than a scale of dates, because the voyage is a reading
 * of the list rather than a chart of it: what a reading needs is that every
 * name gets its own stretch of water, and a ten-year-old countdown beside next
 * week's would squeeze everything recent into one bend. The day a station
 * marks is [effectiveDate] — a repeating countdown counts to its next
 * occurrence, the same day every other reading of the list counts to.
 */
fun voyageStops(
    events: List<CountdownEvent>,
    today: LocalDate,
    step: Float = VOYAGE_STEP,
): List<VoyageStop> =
    events
        .sortedWith(
            compareBy({ it.effectiveDate(today).toEpochDay() }, { it.time }, { it.title })
        )
        .mapIndexed { index, event -> VoyageStop(event, index * step) }

/** How far the boat travels: from the first station to the last. */
fun voyageLength(stops: List<VoyageStop>): Float =
    if (stops.isEmpty()) 0f else stops.last().position

/**
 * Whether the boat has reached this station. Standing exactly on it counts —
 * the name comes up as the bow touches it, which is when it gets read.
 */
fun VoyageStop.passed(boatPosition: Float): Boolean = boatPosition >= position
