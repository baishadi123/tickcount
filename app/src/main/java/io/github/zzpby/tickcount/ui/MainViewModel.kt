package io.github.zzpby.tickcount.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.zzpby.tickcount.data.AppLanguage
import io.github.zzpby.tickcount.data.AppSettings
import io.github.zzpby.tickcount.data.Backup
import io.github.zzpby.tickcount.data.BackupCodec
import io.github.zzpby.tickcount.data.BackupCrypto
import io.github.zzpby.tickcount.data.CapsuleStore
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.data.EventBackgrounds
import io.github.zzpby.tickcount.data.EventStore
import io.github.zzpby.tickcount.data.Footprint
import io.github.zzpby.tickcount.data.FootprintMedia
import io.github.zzpby.tickcount.data.FootprintStore
import io.github.zzpby.tickcount.data.ImportMode
import io.github.zzpby.tickcount.data.ReadOutcome
import io.github.zzpby.tickcount.data.SettingsStore
import io.github.zzpby.tickcount.data.ThemePreset
import io.github.zzpby.tickcount.data.TimeCapsule
import io.github.zzpby.tickcount.data.Wish
import io.github.zzpby.tickcount.data.WishStore
import io.github.zzpby.tickcount.data.WidgetPanel
import io.github.zzpby.tickcount.data.WidgetTextSize
import io.github.zzpby.tickcount.data.previewOf
import io.github.zzpby.tickcount.domain.SortOrder
import io.github.zzpby.tickcount.domain.icsFor
import io.github.zzpby.tickcount.notify.Reminders
import io.github.zzpby.tickcount.ui.widget.refreshAllWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.crypto.AEADBadTagException

/**
 * Owns the whole app state: the saved countdowns, the month the calendar is
 * showing, and the clock.
 *
 * The clock lives here rather than in a composition so that rotating the device
 * never restarts or stutters it, and so there is exactly one timer in the process.
 *
 * Countdowns are held as a list rather than a date-keyed map because a day may hold
 * more than one, which is also why every lookup is by id.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = EventStore(application)

    /**
     * Read before anything else, because the theme is applied above this view model
     * and has to be right on the first frame rather than one frame later.
     */
    private val settingsStore = SettingsStore(application)

    private val _events = MutableStateFlow(store.load())
    val events: StateFlow<List<CountdownEvent>> = _events.asStateFlow()

    private val wishStore = WishStore(application)
    private val _wishes = MutableStateFlow(wishStore.load())
    val wishes: StateFlow<List<Wish>> = _wishes.asStateFlow()
    private val capsuleStore = CapsuleStore(application)
    private val _capsules = MutableStateFlow(capsuleStore.load())
    val capsules: StateFlow<List<TimeCapsule>> = _capsules.asStateFlow()

    private val footprintStore = FootprintStore(application)
    private val _footprints = MutableStateFlow(footprintStore.load())
    val footprints: StateFlow<List<Footprint>> = _footprints.asStateFlow()

    private val _settings = MutableStateFlow(settingsStore.load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    /** True until the first-run card has been read once. */
    private val _showGuide = MutableStateFlow(!settingsStore.hasSeenGuide())
    val showGuide: StateFlow<Boolean> = _showGuide.asStateFlow()

    /**
     * The clock every screen reads.
     *
     * Ticking while somebody is watching and stopping when nobody is, rather than for the
     * life of the process: an app left in the background has no one looking at a countdown,
     * and a coroutine waking every second to say so is work for nothing. The five seconds of
     * grace are for the gaps a configuration change makes.
     */
    val now: StateFlow<ZonedDateTime> = flow {
        while (true) {
            emit(ZonedDateTime.now())
            // Land on the next whole second. The displayed value is always recomputed from
            // the wall clock, so a late tick can never make the clock drift — it just
            // redraws the correct time.
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = ZonedDateTime.now(),
    )

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    init {
        // Placed widgets are redrawn on every launch. It keeps them in step after the
        // app has been away, and it doubles as the way out of a launcher that stopped
        // telling the widget its size: opening the app pushes the current arrangement
        // back out, without the user having to delete the widget and add it again.
        //
        // Off the main thread because it reads the store and talks to the widget
        // service once per placed widget, neither of which belongs in a constructor.
        viewModelScope.launch(Dispatchers.IO) {
            refreshAllWidgets(application)
            // A force stop wipes the one alarm with the rest of the process, and
            // nothing else puts it back until the data changes or the phone
            // reboots — so every launch re-arms it from what is stored.
            reschedule()
        }
    }

    // ------------------------------------------------------------- countdowns

    fun event(id: String?): CountdownEvent? =
        id?.let { wanted -> _events.value.firstOrNull { it.id == wanted } }

    /** The countdowns on [date], earliest time first, which is the order a day reads in. */
    fun eventsOn(date: LocalDate): List<CountdownEvent> =
        _events.value
            .filter { it.date == date }
            .sortedWith(compareBy({ it.time }, { it.title }))

    fun eventsByDate(): Map<LocalDate, List<CountdownEvent>> = _events.value.groupBy { it.date }

    /** Adds [event], or replaces the stored one carrying the same id. */
    fun saveEvent(event: CountdownEvent) {
        val index = _events.value.indexOfFirst { it.id == event.id }
        val updated = if (index >= 0) {
            _events.value.toMutableList().also { it[index] = event }
        } else {
            _events.value + event
        }
        persist(updated)
    }

    fun removeEvent(id: String) {
        if (_events.value.none { it.id == id }) return
        persist(_events.value.filterNot { it.id == id })
    }

    /**
     * Adds several countdowns at once, which is what the editor's batch add produces.
     *
     * One write and one round of widget refreshes for the whole batch rather than one per
     * countdown: the document is rewritten whole on every save, so saving them one at a time
     * would rewrite it — and redraw every placed widget — a hundred times over for a single
     * answer from the user.
     */
    fun saveEvents(events: List<CountdownEvent>) {
        if (events.isEmpty()) return
        val byId = _events.value.associateBy { it.id }.toMutableMap()
        events.forEach { byId[it.id] = it }
        persist(byId.values.toList())
    }

    /**
     * Pins or unpins a countdown, which is what keeps it at the top of any list it
     * appears in.
     */
    fun togglePin(id: String) {
        val event = _events.value.firstOrNull { it.id == id } ?: return
        saveEvent(event.copy(pinned = !event.pinned))
    }

    /**
     * A blank countdown on [date], ready for the editor.
     *
     * The id is minted here rather than on save so that the editor, the delete
     * confirmation and the widget all refer to the same thing from the start. The
     * colour is left unset: the default is the theme's own, and nothing is assigned
     * on the user's behalf. The lunar day follows the solar date — the two are the
     * same day written two ways — and reads as unset past the tables (1900–2100).
     * The added-at stamp is taken now, because that is the moment it is being added.
     */
    fun draftEvent(date: LocalDate): CountdownEvent {
        val lunar = io.github.zzpby.tickcount.domain.Lunar.fromSolar(date)
        return CountdownEvent(
            id = CountdownEvent.newId(),
            date = date,
            title = "",
            createdAt = System.currentTimeMillis(),
            lunarMonth = lunar?.month,
            lunarDay = lunar?.day,
            lunarLeap = lunar?.leap ?: false,
        )
    }

    private fun persist(events: List<CountdownEvent>) {
        _events.value = events
        store.save(events)
        // Any placed widget is showing one of these countdowns and has no way of
        // knowing one was renamed, re-timed, moved, or removed.
        refreshAllWidgets(getApplication())
        // The reminder is for the next thing worth a word, countdown or capsule,
        // so both lists are in it and a change to either re-picks.
        if (_settings.value.remind) {
            Reminders.schedule(
                getApplication(),
                events,
                _capsules.value,
                _settings.value.remindMinutes,
                _settings.value.remindLeadMinutes,
            )
        }
    }

    // -------------------------------------------------------------- backup

    /**
     * The whole app as one file, encrypted only when [password] is given.
     *
     * Encryption is the caller's choice rather than the default: a forgotten password
     * cannot be recovered from anywhere, because there is no server and no account to
     * recover it from.
     */
    fun exportBackup(password: String?): String {
        val document = BackupCodec.encode(
            Backup(_events.value, _settings.value, _wishes.value, _capsules.value, _footprints.value),
        )
        return if (password.isNullOrEmpty()) {
            document
        } else {
            BackupCrypto.encrypt(document, password.toCharArray())
        }
    }

    /**
     * The same countdowns as an iCalendar file, for a calendar the app does not own.
     *
     * A separate export rather than the backup one: the backup restores this app, this one
     * hands the dates to another program, and they cannot be the same file.
     */
    fun exportIcs(): String {
        val zone = ZoneId.systemDefault()
        return icsFor(_events.value, zone, ZonedDateTime.now(zone))
    }

    /**
     * Opens a backup file without putting anything back.
     *
     * Kept apart from applying it so that the file, and any password on it, are settled
     * before the screen asks whether to replace or merge. Asking first and reporting a
     * wrong password afterwards throws away an answer the user has already given.
     */
    fun readBackup(text: String, password: String?): ReadOutcome {
        val document = try {
            if (BackupCrypto.isEncrypted(text)) {
                val given = password ?: return ReadOutcome.WrongPassword
                BackupCrypto.decrypt(text, given.toCharArray())
            } else {
                text
            }
        } catch (e: AEADBadTagException) {
            // GCM authenticates as it decrypts, so a wrong password fails the tag rather
            // than yielding plausible nonsense.
            return ReadOutcome.WrongPassword
        } catch (e: Exception) {
            return ReadOutcome.Unreadable
        }

        val restored = try {
            BackupCodec.decode(document)
        } catch (e: Exception) {
            return ReadOutcome.NotABackup
        }

        return ReadOutcome.Read(restored, previewOf(restored, _events.value))
    }

    /** Puts a backup that [readBackup] accepted back, replacing or merging by [mode]. */
    fun applyBackup(backup: Backup, mode: ImportMode): Int {
        val events = when (mode) {
            ImportMode.REPLACE -> backup.events
            // Same id means the same countdown, so the file wins; anything else is
            // added alongside what is already here.
            ImportMode.MERGE -> merged(_events.value, backup.events) { it.id }
        }
        // The other three lists honour the same promise: a merge never throws
        // away what is already on the device.
        val replacing = mode == ImportMode.REPLACE
        val wishes = if (replacing) backup.wishes else merged(_wishes.value, backup.wishes) { it.id }
        val capsules =
            if (replacing) backup.capsules else merged(_capsules.value, backup.capsules) { it.id }
        val footprints =
            if (replacing) backup.footprints else merged(_footprints.value, backup.footprints) { it.id }

        persist(events)
        _settings.value = backup.settings
        settingsStore.save(backup.settings)
        _wishes.value = wishes
        wishStore.save(wishes)
        _capsules.value = capsules
        capsuleStore.save(capsules)
        _footprints.value = footprints
        footprintStore.save(footprints)
        // persist() scheduled from the pre-import capsules and settings, so the
        // one alarm goes back in step with what is stored now.
        reschedule()
        // The restored settings decide the widgets' panel and text size too, and the
        // refresh inside persist() ran before they were saved.
        refreshAllWidgets(getApplication())

        return events.size
    }

    /** Incoming wins over current, id by id — the merge rule every list follows. */
    private fun <T> merged(current: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val byId = current.associateBy(idOf).toMutableMap()
        incoming.forEach { byId[idOf(it)] = it }
        return byId.values.toList()
    }

    // ------------------------------------------------------------------ wishes

    fun wish(id: String?): Wish? =
        id?.let { wanted -> _wishes.value.firstOrNull { it.id == wanted } }

    /** A new wish: an id minted once per editing session, and nothing said yet. */
    fun draftWish(): Wish = Wish(id = Wish.newId(), title = "", createdAt = System.currentTimeMillis())

    /** Adds a wish, or replaces the stored one carrying the same id. */
    fun saveWish(wish: Wish) {
        val index = _wishes.value.indexOfFirst { it.id == wish.id }
        val updated = if (index >= 0) {
            _wishes.value.toMutableList().also { it[index] = wish }
        } else {
            _wishes.value + wish
        }
        _wishes.value = updated
        wishStore.save(updated)
    }

    /** Ticks or unticks a wish, which stamps the day it was done on. */
    fun toggleWish(id: String) {
        val wish = _wishes.value.firstOrNull { it.id == id } ?: return
        val updated = if (wish.done) {
            wish.copy(done = false, doneEpochDay = null)
        } else {
            wish.copy(done = true, doneEpochDay = LocalDate.now().toEpochDay())
        }
        saveWish(updated)
    }

    fun removeWish(id: String) {
        if (_wishes.value.none { it.id == id }) return
        val updated = _wishes.value.filterNot { it.id == id }
        _wishes.value = updated
        wishStore.save(updated)
    }

    // ---------------------------------------------------------------- capsules

    fun capsule(id: String?): TimeCapsule? =
        id?.let { wanted -> _capsules.value.firstOrNull { it.id == wanted } }

    /** A new capsule: an id minted once per editing session, and nothing sealed yet. */
    fun draftCapsule(): TimeCapsule = TimeCapsule(id = TimeCapsule.newId(), message = "", opensEpochDay = LocalDate.now().toEpochDay(), createdAt = System.currentTimeMillis())

    /** Adds a capsule, or replaces the stored one carrying the same id. */
    fun saveCapsule(capsule: TimeCapsule) {
        val index = _capsules.value.indexOfFirst { it.id == capsule.id }
        val updated = if (index >= 0) {
            _capsules.value.toMutableList().also { it[index] = capsule }
        } else {
            _capsules.value + capsule
        }
        _capsules.value = updated
        capsuleStore.save(updated)
        // A capsule's opening day is a reminder's business the same way a
        // countdown's date is, so the next one is re-picked.
        reschedule()
    }

    /**
     * Opens a capsule whose day has come, stamping the day it was first opened.
     * Sealed capsules refuse: the words stay away until the day arrives.
     */
    fun openCapsule(id: String): TimeCapsule? {
        val capsule = _capsules.value.firstOrNull { it.id == id } ?: return null
        if (capsule.opensEpochDay > LocalDate.now().toEpochDay()) return null
        val updated = if (capsule.openedEpochDay != null) {
            capsule
        } else {
            capsule.copy(openedEpochDay = LocalDate.now().toEpochDay())
        }
        if (updated != capsule) saveCapsule(updated)
        return updated
    }

    fun removeCapsule(id: String) {
        val capsule = _capsules.value.firstOrNull { it.id == id } ?: return
        _capsules.value = _capsules.value - capsule
        capsuleStore.save(_capsules.value)
        // The letter's photos belong to it alone: with the record gone they
        // would sit in the private directory forever.
        capsule.photos.forEach { EventBackgrounds.delete(getApplication(), it) }
        // A deleted capsule leaves its reminder behind, which would fire for
        // nothing — the next one is re-picked without it.
        reschedule()
    }

    // -------------------------------------------------------------- footprints

    fun footprint(id: String?): Footprint? =
        id?.let { wanted -> _footprints.value.firstOrNull { it.id == wanted } }

    /** A new footprint: an id minted once per editing session, today as its day. */
    fun draftFootprint(): Footprint = Footprint(
        id = Footprint.newId(),
        place = "",
        epochDay = LocalDate.now().toEpochDay(),
        createdAt = System.currentTimeMillis(),
    )

    /** Adds a footprint, or replaces the stored one carrying the same id. */
    fun saveFootprint(footprint: Footprint) {
        val index = _footprints.value.indexOfFirst { it.id == footprint.id }
        val updated = if (index >= 0) {
            _footprints.value.toMutableList().also { it[index] = footprint }
        } else {
            _footprints.value + footprint
        }
        _footprints.value = updated
        footprintStore.save(updated)
    }

    /** Removes the record; its media files go with it, the visit itself is the user's to keep. */
    fun removeFootprint(id: String) {
        val removed = _footprints.value.firstOrNull { it.id == id } ?: return
        val updated = _footprints.value.filterNot { it.id == id }
        _footprints.value = updated
        footprintStore.save(updated)
        removed.photos.forEach { FootprintMedia.delete(getApplication(), it) }
    }

    // ------------------------------------------------------------------ theme

    fun chooseTheme(preset: ThemePreset) {
        updateSettings(_settings.value.copy(preset = preset))
    }

    /**
     * Stores a mixed hue. The preset is left alone, because the strip is only on
     * screen while [ThemePreset.CUSTOM] is already the choice.
     */
    fun chooseCustomHue(hue: Float) {
        updateSettings(_settings.value.copy(customHue = hue))
    }

    /**
     * The order the home list is drawn in. Kept beside the theme because it is the
     * same kind of thing: a preference that outlives the process.
     */
    fun chooseSortOrder(order: SortOrder) {
        updateSettings(_settings.value.copy(sortOrder = order))
    }

    /** The day 「在一起第 N 天」 counts from; null clears it. */
    fun chooseAnniversary(epochDay: Long?) {
        updateSettings(_settings.value.copy(anniversaryEpochDay = epochDay))
    }

    /**
     * The language the interface is in. The activity watches this and rebuilds itself,
     * because the strings come from resources and those are fixed once it has started.
     */
    fun chooseLanguage(language: AppLanguage) {
        updateSettings(_settings.value.copy(language = language))
    }

    /**
     * The panel the home-screen widgets wear.
     *
     * Redrawn at once rather than left for the half-hourly widget update: the widgets are
     * what this setting is about, and the user may well be looking at one through the
     * widget page.
     */
    fun chooseWidgetPanel(panel: WidgetPanel) {
        updateSettings(_settings.value.copy(widgetPanel = panel))
        refreshAllWidgets(getApplication())
    }

    /** The list widget's text size; applied the same way, and redrawn at once. */
    fun chooseWidgetTextSize(size: WidgetTextSize) {
        updateSettings(_settings.value.copy(widgetTextSize = size))
        refreshAllWidgets(getApplication())
    }

    /** The first-run card has been read; it is not shown again on this install. */
    fun dismissGuide() {
        settingsStore.markGuideSeen()
        _showGuide.value = false
    }

    /**
     * Turns the reminder on or off.
     *
     * The permission itself is asked for by the screen, which owns the activity result: by
     * the time this is called, the answer is already known.
     */
    fun chooseRemind(on: Boolean) {
        updateSettings(_settings.value.copy(remind = on))
        reschedule()
    }

    /** The time of day the reminder arrives, as minutes after midnight. */
    fun chooseRemindMinutes(minutes: Int) {
        updateSettings(_settings.value.copy(remindMinutes = minutes))
        reschedule()
    }

    /**
     * How many minutes before the countdown the reminder arrives; 0 is at that moment.
     *
     * Minutes rather than days: the offset is open-ended, so "half an hour before" and "two
     * days before" are the same setting with a different number.
     */
    fun chooseRemindLead(minutes: Int) {
        updateSettings(
            _settings.value.copy(
                remindLeadMinutes = minutes.coerceIn(0, Reminders.MAX_LEAD_MINUTES),
            )
        )
        reschedule()
    }

    /** A notification to prove that the permission and the channel work. */
    fun sendTestNotification() {
        Reminders.deliverTest(getApplication())
    }

    /**
     * This countdown's own reminder offset, or null to follow the app-wide one.
     *
     * A copy of the event rather than a field somewhere else: the offset belongs to the
     * countdown, is exported with it, and disappears when it does.
     */
    fun chooseEventLead(id: String, leadMinutes: Int?) {
        val event = _events.value.firstOrNull { it.id == id } ?: return
        saveEvent(
            event.copy(
                remindLeadMinutes = leadMinutes?.coerceIn(0, Reminders.MAX_LEAD_MINUTES),
            )
        )
    }

    private fun reschedule() {
        val settings = _settings.value
        if (settings.remind) {
            Reminders.schedule(
                getApplication(),
                _events.value,
                _capsules.value,
                settings.remindMinutes,
                settings.remindLeadMinutes,
            )
        } else {
            Reminders.cancel(getApplication())
        }
    }

    private fun updateSettings(next: AppSettings) {
        _settings.value = next
        settingsStore.save(next)
    }

    // ------------------------------------------------------------------ month

    fun showMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    fun stepMonth(delta: Long) {
        _visibleMonth.value = _visibleMonth.value.plusMonths(delta)
    }

    fun goToToday() {
        _visibleMonth.value = YearMonth.now()
    }
}
