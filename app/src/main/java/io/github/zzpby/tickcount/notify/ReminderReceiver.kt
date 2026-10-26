package io.github.zzpby.tickcount.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.zzpby.tickcount.data.CapsuleStore
import io.github.zzpby.tickcount.data.EventStore
import io.github.zzpby.tickcount.data.SettingsStore
import java.time.LocalDate

/**
 * Fires at the reminder time and shows the notification.
 *
 * The countdown is looked up in the store rather than carried in the intent: between the
 * alarm being set and it firing, the countdown may have been renamed, moved or deleted, and
 * a reminder for yesterday's date would be worse than none.
 *
 * The alarm may have been for a capsule instead — the same single alarm carries whichever
 * comes first — so the kind it was set for says which notification to post, and both kinds
 * reschedule from what is stored now.
 */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra(Reminders.EXTRA_KIND)
        val settings = SettingsStore(context).load()
        val events = EventStore(context).load()

        if (kind == Reminders.KIND_CAPSULE) {
            if (Reminders.canNotify(context)) {
                Reminders.deliverCapsule(context)
            } else {
                Reminders.schedule(
                    context,
                    events,
                    CapsuleStore(context).load(),
                    settings.remindMinutes,
                    settings.remindLeadMinutes,
                )
            }
            return
        }

        val id = intent.getStringExtra(Reminders.EXTRA_ID)
        val event = if (id == null) events.firstOrNull { it.date >= LocalDate.now() }
        else events.firstOrNull { it.id == id }
        if (event != null && !event.date.isBefore(LocalDate.now()) && Reminders.canNotify(context)) {
            Reminders.deliver(context, event)
        } else {
            // Still reschedule, or a deleted countdown would silence every later reminder.
            Reminders.schedule(
                context,
                events,
                CapsuleStore(context).load(),
                settings.remindMinutes,
                settings.remindLeadMinutes,
            )
        }
    }
}
