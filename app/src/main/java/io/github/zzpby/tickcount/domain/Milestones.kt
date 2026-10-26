package io.github.zzpby.tickcount.domain

import java.time.LocalDate

/**
 * The days on the road from the anniversary that are worth marking: the round
 * hundred, the year, and the numbers Chinese reads as love — 520, 999, 1314.
 *
 * Derived wherever they are read rather than stored as countdowns: a milestone
 * is a fact about the anniversary, not a thing of its own, and there is nothing
 * to edit, delete or drift out of step when the anniversary itself is the one
 * number they all come from.
 */
val MILESTONE_DAYS = listOf(100L, 365L, 520L, 999L, 1314L)

/**
 * The date the Nth day lands on: the anniversary itself is day 1, the same
 * convention [dayNumberOf] counts by, so the two never disagree.
 */
fun milestoneDate(anniversary: LocalDate, day: Long): LocalDate =
    anniversary.plusDays(day - 1)

/** Every milestone as the day it lands on, in day order: the day number and its date. */
fun milestones(anniversary: LocalDate): List<Pair<Long, LocalDate>> =
    MILESTONE_DAYS.sorted().map { it to milestoneDate(anniversary, it) }
