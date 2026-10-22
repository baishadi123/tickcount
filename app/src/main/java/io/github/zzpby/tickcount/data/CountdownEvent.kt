package io.github.zzpby.tickcount.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * A single saved countdown: a name on a calendar date, optionally on a time of
 * day, carrying any number of free-form tags, in a colour of its own.
 *
 * The date is stored as a date rather than an instant because a calendar day is a
 * local-time concept, and "the 6th" has to keep meaning the 6th wherever the
 * device is. [time] is null for an all-day countdown, whose target is then 00:00
 * on [date] — which is what every countdown was before times existed.
 *
 * [id] exists because a day may hold several countdowns, so the date can no longer
 * serve as the identity. It is also what a home-screen widget stores, which means
 * editing a countdown's date no longer orphans its widget.
 *
 * [colorHue] is a hue in degrees, or null for the default. A hue rather than a
 * packed colour so that one choice can be rendered as a tint on a light surface
 * and a different one on a dark surface, instead of washing out in one of them.
 *
 * [createdAt] is when the countdown was added, which is a different thing from the
 * moment it counts to. It exists so the list can be ordered by what was added most
 * recently. Records written before it existed load as zero, which the sort treats as
 * "older than everything" and breaks the tie with the date — so an old list keeps
 * the order it was showing.
 */
data class CountdownEvent(
    val id: String,
    val date: LocalDate,
    val title: String,
    val time: LocalTime? = null,
    val tags: List<String> = emptyList(),
    val colorHue: Float? = null,
    val createdAt: Long = 0L,
    /** Pinned countdowns lead the list whatever order it is in. */
    val pinned: Boolean = false,
    /**
     * True for a countdown that comes round every year — a birthday or an anniversary —
     * which always counts to the *next* occurrence of its day and never shows as past.
     *
     * The date stays as entered; the repeat is applied where the countdown is read, through
     * [io.github.zzpby.tickcount.domain.effectiveDate]. One entered as a lunar date repeats
     * by the lunar calendar, so the solar day it counts to moves every year. A 29th of
     * February lands on the 28th of a common year, and a lunar 30th folds into the month's
     * last day. Records written before the field existed load as false.
     */
    val yearlyRepeat: Boolean = false,
    /**
     * The detail screen's own background image, as a file name inside the app's private
     * backgrounds directory — the image itself is copied there when it is picked, so the
     * app still needs no storage permission and the file cannot be revoked by the system.
     *
     * The name rather than the path, so the directory can move; null for no background.
     * A backup carries the name but not the pixels, so a restored countdown whose image
     * was not copied along renders without one.
     */
    val backgroundUri: String? = null,
    /**
     * The manual framing of the [backgroundUri], set by dragging and pinching the
     * editor's preview: [backgroundZoom] multiplies the cover crop the detail
     * screen draws, and the point at [backgroundFocusX] [backgroundFocusY] of the
     * picture is the one the screen centres on. Defaults mean "plain cover",
     * which is what a freshly picked picture shows.
     */
    val backgroundZoom: Float = DEFAULT_BACKGROUND_ZOOM,
    val backgroundFocusX: Float = DEFAULT_BACKGROUND_FOCUS,
    val backgroundFocusY: Float = DEFAULT_BACKGROUND_FOCUS,
    /**
     * The gallery photos associated with this countdown, as file names inside the
     * app's private directory — copied in by the same route as [backgroundUri], so
     * the app still asks for no permission and nothing can be revoked.
     *
     * The detail page shows them as one stacked pile, WeChat-style, and opens the
     * full-screen flip-through on a tap. Empty, as every record written before
     * the field existed reads, means no pile at all.
     */
    val photoUris: List<String> = emptyList(),
    /**
     * How early to be reminded about this one, in minutes, or null to follow the app-wide
     * setting.
     *
     * Minutes rather than days because the useful offsets are unevenly spaced — half an hour
     * before a deadline, two days before a birthday — and null rather than a sentinel so
     * "nobody has said anything about this one" is a state the code can test for.
     */
    val remindLeadMinutes: Int? = null,
    /**
     * The lunar date this countdown was entered as, when it was entered that way.
     *
     * Kept beside the solar [date] rather than instead of it: the countdown still counts down
     * to one solar day, and the lunar fields exist so the card can say which lunar day that
     * is. Null when the date was chosen in the ordinary solar way.
     */
    val lunarMonth: Int? = null,
    val lunarDay: Int? = null,
    val lunarLeap: Boolean = false,
) {
    val epochDay: Long get() = date.toEpochDay()

    /** True when this countdown names a time of day rather than the whole day. */
    val hasTime: Boolean get() = time != null

    /** The instant this countdown counts to, as observed in [zone]. */
    fun target(zone: ZoneId): ZonedDateTime =
        date.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone)

    companion object {
        const val MAX_TITLE_LENGTH = 60
        const val MAX_TAG_LENGTH = 24
        const val MAX_TAG_COUNT = 8

        /** The plain-cover framing every fresh background starts at. */
        const val DEFAULT_BACKGROUND_ZOOM = 1f
        const val DEFAULT_BACKGROUND_FOCUS = 0.5f

        /** How far the manual framing may enlarge a background. */
        const val MAX_BACKGROUND_ZOOM = 5f

        /** How many gallery photos one countdown may carry, WeChat's own ceiling. */
        const val MAX_PHOTOS = 9

        fun newId(): String = UUID.randomUUID().toString()
    }
}
