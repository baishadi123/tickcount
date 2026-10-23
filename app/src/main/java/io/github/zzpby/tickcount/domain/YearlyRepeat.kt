package io.github.zzpby.tickcount.domain

import io.github.zzpby.tickcount.data.CountdownEvent
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The next occurrence of a date that repeats every year, as seen from [today].
 *
 * The occurrence in the current year is preferred, so the day itself still counts as
 * *today* — a birthday counts up from its own morning and rolls over to the next
 * year the day after, which is what [CountdownPhase.TODAY] means everywhere else in
 * the app. Only once the day is behind us does the occurrence move to the next year,
 * which is why a repeating countdown never shows as past.
 *
 * A 29th of February lands on the 28th of a common year: `withYear` keeps the month
 * and folds the day into the last valid one, which is the convention calendars and
 * people both use.
 */
fun nextYearlyOccurrence(date: LocalDate, today: LocalDate): LocalDate {
    val thisYear = date.withYear(today.year)
    return if (thisYear.isBefore(today)) date.withYear(today.year + 1) else thisYear
}

/**
 * The next occurrence of a **lunar** month and day, as seen from [today].
 *
 * The tables are walked forward a year at a time from the current year, and the first
 * year whose solar day has not gone by wins. A birthday on a lunar 15th of the eighth
 * month therefore lands on a different solar day every year — which is the point.
 *
 * Two conventions are folded in, the ones people actually keep birthdays by:
 * - A leap-month birthday falls in the ordinary month when the year has no leap month
 *   — 闰四月 counts in 四月, rather than waiting years for the next 闰四月.
 * - A 30th folds into the month's last day, which may be the 29th — the same way a
 *   solar 29th of February folds into the 28th.
 *
 * Null when [today] is past the tables (1900–2100); the caller falls back to the solar
 * repeat, which is a better answer than nothing.
 */
fun nextLunarOccurrence(month: Int, day: Int, leap: Boolean, today: LocalDate): LocalDate? {
    var year = today.year
    while (Lunar.inRange(year)) {
        val solar = solarLunarDay(year, month, day, leap)
        if (solar != null && !solar.isBefore(today)) return solar
        year++
    }
    return null
}

/** The solar day a lunar month and day lands on in [year], with the two folds applied. */
internal fun solarLunarDay(year: Int, month: Int, day: Int, leap: Boolean): LocalDate? {
    val effectiveLeap = leap && Lunar.leapMonth(year) == month
    val effectiveDay = day.coerceAtMost(Lunar.daysIn(year, month, effectiveLeap))
    return Lunar.toSolar(Lunar.Date(year, month, effectiveDay, effectiveLeap))
}

/**
 * The day this countdown counts to, as seen from [today].
 *
 * A repeating countdown always counts to its *next* occurrence, which is why it
 * never lands in the past section of a list; a plain countdown counts to its own
 * date, whatever day that is.
 *
 * One entered as a lunar date repeats **by the lunar calendar**, so the solar day
 * moves every year; the solar date is only the day it was entered on. The lunar
 * fields alone do not carry that intent — the solar picker stamps them too, so
 * the card can name the lunar day — and [CountdownEvent.enteredAsLunar] says
 * which picker the editor last used. Null from the lunar tables (a device clock
 * past 2100) falls back to the solar repeat.
 */
fun CountdownEvent.effectiveDate(today: LocalDate): LocalDate = when {
    !yearlyRepeat -> date
    enteredAsLunar && lunarMonth != null && lunarDay != null ->
        nextLunarOccurrence(lunarMonth, lunarDay, lunarLeap, today) ?: nextYearlyOccurrence(date, today)
    else -> nextYearlyOccurrence(date, today)
}

/**
 * The instant this countdown counts to, as observed in [zone] on [today].
 *
 * The same definition [CountdownEvent.target] keeps — the saved time of day, or
 * midnight for an all-day countdown — applied to the next occurrence rather than
 * to the stored date, so a repeating countdown with a time counts to that time
 * on the next time the day comes round.
 */
fun CountdownEvent.effectiveTarget(zone: ZoneId, today: LocalDate): ZonedDateTime =
    effectiveDate(today).atTime(time ?: LocalTime.MIDNIGHT).atZone(zone)
