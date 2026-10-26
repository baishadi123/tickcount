package io.github.zzpby.tickcount.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.zzpby.tickcount.MainActivity
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.CapsuleStore
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.EventStore
import io.github.zzpby.tickcount.data.TimeCapsule
import io.github.zzpby.tickcount.domain.effectiveDate
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The one reminder TickCount can give without asking for an alarm permission.
 *
 * Only the *next* countdown is scheduled, not every one: an alarm for a date six months out
 * would have to survive a reboot, a time-zone change and a clock change to be worth anything,
 * and the receiver reschedules the following one the moment this one fires. That keeps one
 * alarm in the system instead of thirty.
 *
 * The alarm is `setAndAllowWhileIdle`, which is inexact on purpose. Exact alarms need
 * `SCHEDULE_EXACT_ALARM` from Android 12, and a countdown is not a train: a reminder that
 * arrives within the hour is enough, and the permission is not worth asking for.
 */
object Reminders {

    const val CHANNEL_ID = "reminders"
    const val EXTRA_ID = "event_id"

    /** What the one alarm is for: a countdown, or a capsule coming due. */
    const val EXTRA_KIND = "kind"
    const val KIND_EVENT = "event"
    const val KIND_CAPSULE = "capsule"

    private const val REQUEST_CODE = 1077

    /** How far ahead a reminder may be set: a month, which is as far as anyone plans ahead. */
    const val MAX_LEAD_MINUTES = 30 * 24 * 60

    /** When the reminder falls, for the countdown it belongs to. */
    data class Planned(val event: CountdownEvent, val at: Instant)

    /** Creates the channel, which is idempotent and must happen before any notification. */
    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notify_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        channel.description = context.getString(R.string.notify_channel_description)
        manager.createNotificationChannel(channel)
    }

    /** Whether the app may post a notification at all on this version of Android. */
    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

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
    ): Instant {
        val today = now.atZone(zone).toLocalDate()
        val date = event.effectiveDate(today)
        val fallback = LocalTime.of(minutes / 60, minutes % 60)
        val base = event.time?.let { date.atTime(it) } ?: date.atTime(fallback)
        return base.minusMinutes((event.remindLeadMinutes ?: leadMinutes).toLong())
            .atZone(zone).toInstant()
    }

    fun planned(
        events: List<CountdownEvent>,
        minutes: Int,
        leadMinutes: Int,
        now: Instant = Instant.now(),
        // A parameter rather than a straight read of the default zone: the tests assert an
        // exact instant, and a machine set to a different time zone would otherwise be
        // testing a different question. The app always passes its own zone.
        zone: ZoneId = ZoneId.systemDefault(),
    ): Planned? {
        val today = now.atZone(zone).toLocalDate()
        return events
            .sortedBy { it.effectiveDate(today) }
            .map { event -> Planned(event, momentOf(event, minutes, leadMinutes, zone, now)) }
            .firstOrNull { it.at.isAfter(now) }
    }

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
    ): Instant {
        val fallback = LocalTime.of(minutes / 60, minutes % 60)
        return LocalDate.ofEpochDay(capsule.opensEpochDay)
            .atTime(fallback)
            .minusMinutes(leadMinutes.toLong())
            .atZone(zone).toInstant()
    }

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

    /**
     * Replaces whatever was scheduled with the next reminder among the countdowns
     * and the capsules.
     *
     * Still one alarm: whichever comes first carries it, and the receiver
     * reschedules the moment it fires — the same single-alarm economy the
     * countdowns alone always ran on.
     */
    fun schedule(
        context: Context,
        events: List<CountdownEvent>,
        capsules: List<TimeCapsule>,
        minutes: Int,
        leadMinutes: Int,
    ) {
        val host = context.applicationContext
        val alarms = host.getSystemService(AlarmManager::class.java) ?: return
        val nextEvent = planned(events, minutes, leadMinutes)
        val nextCapsule = plannedCapsule(capsules, minutes, leadMinutes)
        val at = listOfNotNull(
            nextEvent?.at,
            nextCapsule?.let { capsuleMomentOf(it, minutes, leadMinutes) },
        ).minOrNull()
        if (at == null) {
            alarms.cancel(pendingIntent(host, KIND_EVENT))
            alarms.cancel(pendingIntent(host, KIND_CAPSULE))
            return
        }
        ensureChannel(host)
        val kind = if (nextEvent != null && at == nextEvent.at) KIND_EVENT else KIND_CAPSULE
        alarms.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            at.toEpochMilli(),
            pendingIntent(host, kind),
        )
    }

    fun cancel(context: Context) {
        val host = context.applicationContext
        val alarms = host.getSystemService(AlarmManager::class.java) ?: return
        alarms.cancel(pendingIntent(host, KIND_EVENT))
        alarms.cancel(pendingIntent(host, KIND_CAPSULE))
    }

    /** Posts the reminder for [event] and schedules whatever comes next. */
    fun deliver(context: Context, event: CountdownEvent) {
        val host = context.applicationContext
        ensureChannel(host)
        val now = Instant.now()
        val today = now.atZone(host.zone()).toLocalDate()
        val due = event.effectiveDate(today)
        val text = when {
            due.atStartOfDay(host.zone()).toInstant().isAfter(now) ->
                host.getString(R.string.notify_later, due.toString())

            else -> host.getString(R.string.notify_today)
        }
        post(host, event.id.hashCode(), event.title, text)
        rescheduleFromStore(host)
    }

    /**
     * Posts the reminder for the capsule that came due and schedules whatever
     * comes next — the capsule's own counterpart of [deliver].
     *
     * The capsule is looked up where the alarm left it rather than carried in
     * the intent, for the same reason the countdown is: it may have been
     * resealed to a later day or deleted since the alarm was set. A capsule
     * already opened says nothing — the letter has been read.
     */
    fun deliverCapsule(context: Context) {
        val host = context.applicationContext
        val settings = io.github.zzpby.tickcount.data.SettingsStore(host).load()
        val today = LocalDate.now(host.zone())
        val due = CapsuleStore(host).load()
            .filter { it.opensEpochDay <= today.toEpochDay() && it.openedEpochDay == null }
            .minByOrNull { it.opensEpochDay }
            ?: return rescheduleFromStore(host)
        ensureChannel(host)
        post(
            host,
            due.id.hashCode(),
            due.title.ifBlank { host.getString(R.string.app_name) },
            host.getString(R.string.notify_capsule),
        )
        rescheduleFromStore(host)
    }

    /** The next reminder from whatever is stored now, not what was scheduled then. */
    private fun rescheduleFromStore(host: Context) {
        val settings = io.github.zzpby.tickcount.data.SettingsStore(host).load()
        schedule(
            host,
            EventStore(host).load(),
            CapsuleStore(host).load(),
            settings.remindMinutes,
            settings.remindLeadMinutes,
        )
    }

    /** A notification to confirm that the permission, channel and popup all work. */
    fun deliverTest(context: Context) {
        val host = context.applicationContext
        ensureChannel(host)
        post(host, TEST_ID, host.getString(R.string.app_name), host.getString(R.string.notify_test_body))
    }

    private fun post(context: Context, id: Int, title: String, text: String) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private const val TEST_ID = 77

    private fun Context.zone(): ZoneId = ZoneId.systemDefault()

    private fun pendingIntent(context: Context, kind: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KIND, kind),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
