package io.github.zzpby.tickcount.notify

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.TimeCapsule
import io.github.zzpby.tickcount.domain.effectiveDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executors

/**
 * The reminder, delivered by the system's own calendar.
 *
 * Every countdown and capsule becomes one event in a calendar of the app's own,
 * local to the device and synchronized with nothing, and the calendar's reminder
 * — offset by the same lead the app has always used — is what arrives. The
 * calendar stack is privileged where an app's alarms are not: it is never killed
 * for the app being swiped away, and its reminders are the punctual kind. The
 * app itself holds no alarm, no receiver and no channel any more; its whole job
 * is keeping the calendar a faithful copy of the list, which it does with a
 * diff on every change.
 *
 * The moments themselves are the same arithmetic the app's own alarms ran on,
 * kept as pure functions and tested away from the provider.
 */
object Reminders {

    /** How far ahead a reminder may be set: a month, which is as far as anyone plans ahead. */
    const val MAX_LEAD_MINUTES = 30 * 24 * 60

    /** What the one alarm is for: a countdown, or a capsule coming due. */
    data class Planned(val event: CountdownEvent, val at: Instant)

    /**
     * The instant a countdown's reminder anchors to, before any lead is taken
     * off — the calendar event's own start. The reminder offset is applied by
     * the calendar, so the moment it fires stays [momentOf] either way.
     */
    fun baseMomentOf(
        event: CountdownEvent,
        minutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Instant {
        val today = now.atZone(zone).toLocalDate()
        val date = event.effectiveDate(today)
        val fallback = LocalTime.of(minutes / 60, minutes % 60)
        return (event.time?.let { date.atTime(it) } ?: date.atTime(fallback))
            .atZone(zone).toInstant()
    }

    /**
     * When the reminder for [event] falls, in [zone], as seen from [now].
     *
     * Anchored to the countdown itself — its own time when it names one, the reminder time
     * of day when it is all day — and then moved back by its own offset if it has one, or by
     * the app-wide [leadMinutes] if it does not. The day it anchors to is
     * [effectiveDate]: a repeating countdown is reminded about its next occurrence, which
     * is what keeps a birthday reminded about every year.
     */
    fun momentOf(
        event: CountdownEvent,
        minutes: Int,
        leadMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
        now: Instant = Instant.now(),
    ): Instant =
        baseMomentOf(event, minutes, zone, now)
            .minusSeconds((event.remindLeadMinutes ?: leadMinutes).toLong() * 60)

    fun planned(
        events: List<CountdownEvent>,
        minutes: Int,
        leadMinutes: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Planned? {
        return events
            .map { event -> Planned(event, momentOf(event, minutes, leadMinutes, zone, now)) }
            // Ordered by the moment itself, not by day: two countdowns can share
            // a date with different times of day, and the earlier clock has to
            // win however the list happens to be stored.
            .sortedBy { it.at }
            .firstOrNull { it.at.isAfter(now) }
    }

    /** The instant a capsule's reminder anchors to, before any lead is taken off. */
    fun capsuleBaseMomentOf(
        capsule: TimeCapsule,
        minutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant =
        LocalDate.ofEpochDay(capsule.opensEpochDay)
            .atTime(LocalTime.of(minutes / 60, minutes % 60))
            .atZone(zone).toInstant()

    /**
     * When the reminder for a capsule falls: the reminder time of day on the day
     * it opens, moved back by the app-wide lead — the same rule an all-day
     * countdown follows, because a capsule is a whole day's business too.
     */
    fun capsuleMomentOf(
        capsule: TimeCapsule,
        minutes: Int,
        leadMinutes: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Instant =
        capsuleBaseMomentOf(capsule, minutes, zone)
            .minusSeconds(leadMinutes.toLong() * 60)

    /** The first capsule whose reminder has not already passed, earliest to open first. */
    fun plannedCapsule(
        capsules: List<TimeCapsule>,
        minutes: Int,
        leadMinutes: Int,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): TimeCapsule? =
        capsules
            .sortedBy { it.opensEpochDay }
            .firstOrNull { capsuleMomentOf(it, minutes, leadMinutes, zone).isAfter(now) }

    // ------------------------------------------------------ the diff, pure

    /** One event the calendar ought to hold: keyed by what it mirrors, so a row
     *  is matched to its countdown across every later sync. */
    data class CalendarEntry(
        val key: String,
        val title: String,
        val start: Instant,
        val reminderMinutes: Int,
    )

    /** One row the calendar does hold, as the provider read it back. */
    data class ExistingEntry(
        val rowId: Long,
        val key: String,
        val title: String,
        val start: Instant,
        val reminderMinutes: Int,
    )

    /** What to write, rewrite and remove to make the calendar match the list. */
    data class SyncPlan(
        val insert: List<CalendarEntry>,
        val update: List<Pair<ExistingEntry, CalendarEntry>>,
        val delete: List<ExistingEntry>,
    )

    /**
     * The events the calendar should hold right now: one per countdown and one
     * per unopened capsule, each at its anchor moment with its own lead as the
     * reminder offset. Anything whose reminder moment has already passed is
     * left out — a reminder for a moment gone is noise, not news. Repeating
     * countdowns appear at their next occurrence only; when that day comes and
     * goes, the next sync moves the event on to the following year.
     */
    fun desiredEntries(
        events: List<CountdownEvent>,
        capsules: List<TimeCapsule>,
        minutes: Int,
        leadMinutes: Int,
        appName: String = "TickCount",
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<CalendarEntry> {
        val wanted = ArrayList<CalendarEntry>()
        for (event in events) {
            val start = baseMomentOf(event, minutes, zone, now)
            val lead = (event.remindLeadMinutes ?: leadMinutes).toLong()
            if (!start.minusSeconds(lead * 60).isAfter(now)) continue
            wanted += CalendarEntry("event/${event.id}", event.title, start, lead.toInt())
        }
        for (capsule in capsules) {
            if (capsule.openedEpochDay != null) continue
            val start = capsuleBaseMomentOf(capsule, minutes, zone)
            if (!start.minusSeconds(leadMinutes * 60L).isAfter(now)) continue
            wanted += CalendarEntry(
                "capsule/${capsule.id}",
                capsule.title.ifBlank { appName },
                start,
                leadMinutes,
            )
        }
        return wanted
    }

    /**
     * Rows are matched by key alone. A row that differs in moment, title or
     * lead is rewritten rather than recreated, which keeps the calendar app's
     * idea of "the same event" intact; a row whose countdown is gone is
     * deleted — including one the user deleted in the calendar app itself,
     * because a row we put back after its owner died would be a ghost.
     */
    fun syncPlan(desired: List<CalendarEntry>, existing: List<ExistingEntry>): SyncPlan {
        val have = existing.associateBy { it.key }
        val want = desired.associateBy { it.key }
        return SyncPlan(
            insert = desired.filter { it.key !in have },
            update = desired.mapNotNull { entry ->
                have[entry.key]
                    ?.takeIf {
                        it.title != entry.title ||
                            it.start != entry.start ||
                            it.reminderMinutes != entry.reminderMinutes
                    }
                    ?.let { it to entry }
            },
            delete = existing.filter { it.key !in want },
        )
    }

    // ------------------------------------------------------ the calendar itself

    /** Whether the app may read and write the calendar, which is the whole of
     *  what the reminder needs from the system. */
    fun canUseCalendar(context: Context): Boolean {
        val host = context.applicationContext
        return host.checkSelfPermission(Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            host.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
    }

    /** Puts the calendar in step with the list: one call, on one worker, in order. */
    fun sync(
        context: Context,
        events: List<CountdownEvent>,
        capsules: List<TimeCapsule>,
        minutes: Int,
        leadMinutes: Int,
    ) {
        val host = context.applicationContext
        worker.execute {
            runCatching {
                if (!canUseCalendar(host)) return@execute
                val calendarId = ensureCalendar(host) ?: return@execute
                val desired = desiredEntries(
                    events, capsules, minutes, leadMinutes,
                    host.getString(R.string.app_name),
                )
                apply(host, calendarId, syncPlan(desired, readExisting(host, calendarId)))
            }
        }
    }

    /** Removes everything the app ever wrote: the events first, then the
     *  calendar itself. Nothing is created on the way — a purge must not
     *  leave a shell behind for a user who never turned the reminder on. */
    fun purge(context: Context) {
        val host = context.applicationContext
        worker.execute {
            runCatching {
                if (!canUseCalendar(host)) return@execute
                val calendarId = findCalendar(host) ?: return@execute
                val cr = host.contentResolver
                cr.delete(
                    Events.CONTENT_URI,
                    "${Events.CALENDAR_ID}=?",
                    arrayOf(calendarId.toString()),
                )
                cr.delete(
                    ContentUris.withAppendedId(syncAdapterUri(), calendarId),
                    null, null,
                )
            }
        }
    }

    /**
     * One event about a minute out, so the whole chain can be proven in front
     * of the user: the app writes, the calendar reminds. An earlier test event
     * is removed first, so trying again never stacks them.
     */
    fun writeTestEntry(context: Context) {
        val host = context.applicationContext
        worker.execute {
            runCatching {
                if (!canUseCalendar(host)) return@execute
                val calendarId = ensureCalendar(host) ?: return@execute
                val cr = host.contentResolver
                readExisting(host, calendarId)
                    .filter { it.key == TEST_KEY }
                    .forEach {
                        cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, it.rowId), null, null)
                    }
                val rowId = cr.insert(
                    Events.CONTENT_URI,
                    eventValues(host, calendarId, CalendarEntry(TEST_KEY, host.getString(R.string.app_name), Instant.now().plusSeconds(90), 0)),
                )?.lastPathSegment?.toLongOrNull() ?: return@execute
                cr.insert(
                    Reminders.CONTENT_URI,
                    ContentValues().apply {
                        put(Reminders.EVENT_ID, rowId)
                        put(Reminders.MINUTES, 0)
                        put(Reminders.METHOD, Reminders.METHOD_ALERT)
                    },
                )
            }
        }
    }

    private const val ACCOUNT = "io.github.zzpby.tickcount.reminders"
    private const val URI_SCHEME = "tickcount"
    private const val TEST_KEY = "test"

    /** One worker, so syncs queue in the order the app asked for them. */
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tickcount-calendar").apply { isDaemon = true }
    }

    private fun findCalendar(host: Context): Long? =
        host.contentResolver.query(
            Calendars.CONTENT_URI,
            arrayOf(Calendars._ID),
            "${Calendars.ACCOUNT_NAME}=? AND ${Calendars.ACCOUNT_TYPE}=? AND ${Calendars.NAME}=?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL, ACCOUNT),
            null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    /**
     * A calendar of the app's own, created once and then only found again. It
     * is local to the device — no account behind it, nothing to synchronize —
     * which is the calendar-provider's own way of saying "this stays on this
     * phone", the way every other store in the app does.
     */
    private fun ensureCalendar(host: Context): Long? {
        findCalendar(host)?.let { return it }
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, ACCOUNT)
            put(Calendars.CALENDAR_DISPLAY_NAME, host.getString(R.string.calendar_name))
            // The app's own emerald, so the calendar reads as a member of the
            // family next to the system's own colours.
            put(Calendars.CALENDAR_COLOR, 0xFF2E9E8F.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, ACCOUNT)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 0)
        }
        return host.contentResolver.insert(syncAdapterUri(), values)
            ?.lastPathSegment?.toLongOrNull()
    }

    /** Only a sync adapter may insert or delete a calendar; the parameter is
     *  the provider's own sanctioned way for an app to act as one for a local
     *  account it owns. */
    private fun syncAdapterUri() =
        Calendars.CONTENT_URI.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
            .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            .build()

    private fun readExisting(host: Context, calendarId: Long): List<ExistingEntry> {
        val cr = host.contentResolver
        val rows = ArrayList<ExistingEntry>()
        cr.query(
            Events.CONTENT_URI,
            arrayOf(Events._ID, Events.CUSTOM_APP_URI, Events.TITLE, Events.DTSTART),
            "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0",
            arrayOf(calendarId.toString()),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val rowId = cursor.getLong(0)
                val key = keyOf(cursor.getString(1) ?: continue) ?: continue
                val minutes = cr.query(
                    Reminders.CONTENT_URI,
                    arrayOf(Reminders.MINUTES),
                    "${Reminders.EVENT_ID}=?",
                    arrayOf(rowId.toString()),
                    null,
                )?.use { reminder -> if (reminder.moveToFirst()) reminder.getInt(0) else null } ?: 0
                rows += ExistingEntry(
                    rowId = rowId,
                    key = key,
                    title = cursor.getString(2) ?: "",
                    start = Instant.ofEpochMilli(cursor.getLong(3)),
                    reminderMinutes = minutes,
                )
            }
        }
        return rows
    }

    private fun apply(host: Context, calendarId: Long, plan: SyncPlan) {
        val cr = host.contentResolver
        plan.delete.forEach { row ->
            cr.delete(ContentUris.withAppendedId(Events.CONTENT_URI, row.rowId), null, null)
        }
        plan.update.forEach { (row, entry) ->
            cr.update(
                ContentUris.withAppendedId(Events.CONTENT_URI, row.rowId),
                eventValues(host, calendarId, entry),
                null, null,
            )
            // A reminder is replaced rather than updated: the provider treats
            // the pair (event, minutes) as the row's identity.
            cr.delete(Reminders.CONTENT_URI, "${Reminders.EVENT_ID}=?", arrayOf(row.rowId.toString()))
            cr.insert(
                Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, row.rowId)
                    put(Reminders.MINUTES, entry.reminderMinutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
        plan.insert.forEach { entry ->
            val rowId = cr.insert(Events.CONTENT_URI, eventValues(host, calendarId, entry))
                ?.lastPathSegment?.toLongOrNull() ?: return@forEach
            cr.insert(
                Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(Reminders.EVENT_ID, rowId)
                    put(Reminders.MINUTES, entry.reminderMinutes)
                    put(Reminders.METHOD, Reminders.METHOD_ALERT)
                },
            )
        }
    }

    private fun eventValues(host: Context, calendarId: Long, entry: CalendarEntry): ContentValues =
        ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, entry.title)
            // A timed event rather than an all-day one: all-day reminders are
            // read differently by every calendar app, and the app has already
            // decided the moment precisely. An hour of presence is plenty —
            // the reminder is the point, not the block.
            put(Events.DTSTART, entry.start.toEpochMilli())
            put(Events.DTEND, entry.start.plusSeconds(3600).toEpochMilli())
            put(Events.ALL_DAY, 0)
            put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            // The pair says whose row this is, so later syncs can find it and
            // the calendar app can offer to open the app that wrote it.
            put(Events.CUSTOM_APP_PACKAGE, host.packageName)
            put(Events.CUSTOM_APP_URI, "$URI_SCHEME://${entry.key}")
        }

    private fun keyOf(customAppUri: String): String? {
        val prefix = "$URI_SCHEME://"
        return customAppUri.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
    }
}
