package io.github.zzpby.tickcount.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.zzpby.tickcount.R
import io.github.zzpby.tickcount.data.AppLanguage
import io.github.zzpby.tickcount.data.CountdownEvent
import io.github.zzpby.tickcount.domain.SearchScope
import io.github.zzpby.tickcount.domain.SortOrder
import io.github.zzpby.tickcount.domain.solarLunarDay
import io.github.zzpby.tickcount.notify.Reminders
import io.github.zzpby.tickcount.domain.countdownList
import io.github.zzpby.tickcount.ui.calendar.MonthYearPickerDialog
import io.github.zzpby.tickcount.ui.components.EditIcon
import io.github.zzpby.tickcount.ui.components.PlusIcon
import io.github.zzpby.tickcount.ui.components.rememberDateFormatter
import io.github.zzpby.tickcount.ui.theme.XingKai
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.time.LocalDate

/** Sentinel for "no day chosen", which is not a valid epoch day. */
private const val NO_DAY = Long.MIN_VALUE

/**
 * The whole app: one screen at a time, a drawer that slides in over them, and the
 * dialogs that belong to whichever screen raised them.
 *
 * Navigation is a single piece of state rather than a navigation library. There is
 * exactly one level of it 閳?a screen, plus optionally one countdown opened from a
 * list 閳?so a back stack would be more machinery than the thing it models.
 */
@Composable
fun TickCountApp(
    viewModel: MainViewModel = viewModel(),
    /** Countdowns something outside the app asked to open 閳?a home-screen widget, today. */
    openRequests: Flow<String> = emptyFlow(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val wishes by viewModel.wishes.collectAsStateWithLifecycle()
    val capsules by viewModel.capsules.collectAsStateWithLifecycle()
    val footprints by viewModel.footprints.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val showGuide by viewModel.showGuide.collectAsStateWithLifecycle()

    var screenName by rememberSaveable { mutableStateOf(AppScreen.HOME.name) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var drawerOpen by rememberSaveable { mutableStateOf(false) }
    var monthPickerOpen by rememberSaveable { mutableStateOf(false) }
    var calendarEpochDay by rememberSaveable { mutableLongStateOf(NO_DAY) }
    var pendingDeleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var languageOpen by rememberSaveable { mutableStateOf(false) }

    // The launch page: up for a beat on a cold start, then gone for as long as
    // the process lives — `rememberSaveable`, so a rotation does not bring it back.
    var splashGone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        splashGone = true
    }
    var actionsForId by rememberSaveable { mutableStateOf<String?>(null) }

    // The batch the editor handed over, waiting on one more question before it is added.
    // Held here rather than in the editor because the editor closes first: the confirmation
    // is the next question, and two dialogs stacked would bury the name being confirmed.
    // Not `rememberSaveable`: the list is what the question is about, and a process death
    // with the question on screen is a question worth asking again from the start.
    var pendingBatch by remember { mutableStateOf<List<CountdownEvent>?>(null) }

    // Held here rather than in the screens so that opening a countdown and coming
    // back leaves the search as it was. The calendar keeps its own, because it is
    // searching a different list.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchScopeName by rememberSaveable { mutableStateOf(SearchScope.NAME.name) }
    var calendarQuery by rememberSaveable { mutableStateOf("") }
    var calendarScopeName by rememberSaveable { mutableStateOf(SearchScope.NAME.name) }

    val searchScope = remember(searchScopeName) { searchScopeName.toScope() }
    val calendarScope = remember(calendarScopeName) { calendarScopeName.toScope() }

    // The editor is described by what it was opened for, not by a copy of the
    // countdown: an id for an existing one, or a day for a new one.
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editorIsNew by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableStateOf("") }
    var editorNewOn by rememberSaveable { mutableLongStateOf(0L) }

    val screen = remember(screenName) {
        runCatching { AppScreen.valueOf(screenName) }.getOrDefault(AppScreen.HOME)
    }
    val today = now.toLocalDate()
    // The wish editor, wired the way the countdown's is: the title bar's plus opens
    // it on a new mint, the wish screen's long press opens it on one wish, and what
    // it edits is described by an id rather than by a copy of the wish.
    var wishEditorOpen by rememberSaveable { mutableStateOf(false) }
    var wishEditorIsNew by rememberSaveable { mutableStateOf(false) }
    var wishEditorId by rememberSaveable { mutableStateOf("") }
    var pendingWishDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val wishEditorWish = remember(wishEditorOpen, wishEditorIsNew, wishEditorId) {
        when {
            !wishEditorOpen -> null
            wishEditorIsNew -> viewModel.draftWish()
            else -> viewModel.wish(wishEditorId)
        }
    }

    fun startNewWish() {
        wishEditorIsNew = true
        wishEditorId = ""
        wishEditorOpen = true
    }

    fun startEditWish(id: String) {
        wishEditorIsNew = false
        wishEditorId = id
        wishEditorOpen = true
    }

    // The capsule editor, wired the same way: the bar's plus on a new mint, the
    // capsule screen's long press on one capsule, an id describing what it edits.
    var capsuleEditorOpen by rememberSaveable { mutableStateOf(false) }
    var capsuleEditorId by rememberSaveable { mutableStateOf("") }
    var pendingCapsuleDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val capsuleEditorCapsule = remember(capsuleEditorOpen, capsuleEditorId) {
        when {
            !capsuleEditorOpen -> null
            capsuleEditorId.isEmpty() -> viewModel.draftCapsule()
            else -> viewModel.capsule(capsuleEditorId)
        }
    }

    fun startNewCapsule() {
        capsuleEditorId = ""
        capsuleEditorOpen = true
    }

    fun startEditCapsule(id: String) {
        capsuleEditorId = id
        capsuleEditorOpen = true
    }

    // The footprint editor, wired the same way as the other three: the bar's
    // plus on a new mint, the page's long press on one footprint.
    var footprintEditorOpen by rememberSaveable { mutableStateOf(false) }
    var footprintEditorId by rememberSaveable { mutableStateOf("") }
    var pendingFootprintDeleteId by rememberSaveable { mutableStateOf<String?>(null) }

    val footprintEditorFootprint = remember(footprintEditorOpen, footprintEditorId) {
        when {
            !footprintEditorOpen -> null
            footprintEditorId.isEmpty() -> viewModel.draftFootprint()
            else -> viewModel.footprint(footprintEditorId)
        }
    }

    fun startNewFootprint() {
        footprintEditorId = ""
        footprintEditorOpen = true
    }

    fun startEditFootprint(id: String) {
        footprintEditorId = id
        footprintEditorOpen = true
    }
    // A repeating countdown marks the same month and day in the years around today, not
    // only the year it was entered in — that is what repeating means on a calendar. One
    // entered as a lunar date walks the lunar calendar instead: the solar day it marks
    // moves every year, so each year is translated through the tables. The range
    // matches the ±10 years the month picker can reach in a single step.
    val byDate = remember(events, today.year) {
        buildMap {
            events.forEach { event ->
                val dates = when {
                    !event.yearlyRepeat -> listOf(event.date)
                    event.enteredAsLunar && event.lunarMonth != null && event.lunarDay != null ->
                        (today.year - 10..today.year + 10).mapNotNull { year ->
                            solarLunarDay(year, event.lunarMonth!!, event.lunarDay!!, event.lunarLeap)
                        }
                    else ->
                        (today.year - 10..today.year + 10).map { event.date.withYear(it) }
                }
                dates.forEach { date ->
                    getOrPut(date) { mutableListOf() }.add(event)
                }
            }
        }.mapValues { (_, onDay) -> onDay.sortedWith(compareBy({ it.time }, { it.title })) }
    }
    // The capsules by the day they open on, which the calendar marks and a
    // day's list holds beside the countdowns — a capsule is a day's business
    // the same way a countdown is.
    val capsulesByDate = remember(capsules) {
        capsules.groupBy { LocalDate.ofEpochDay(it.opensEpochDay) }
    }
    // The wishes by the day they were ticked off: a thing done is a memory of
    // a day, which is what the calendar keeps.
    val wishesByDate = remember(wishes) {
        wishes
            .filter { it.doneEpochDay != null }
            .groupBy { LocalDate.ofEpochDay(it.doneEpochDay!!) }
    }
    // The milestones the anniversary counts towards: derived, never stored —
    // the calendar marks them, the voyage carries them, and nothing has to be
    // kept in step with the anniversary but the anniversary itself.
    val anniversaryDate = settings.anniversaryEpochDay?.let(LocalDate::ofEpochDay)
    val milestonesByDate = remember(anniversaryDate) {
        anniversaryDate
            ?.let { io.github.zzpby.tickcount.domain.milestones(it) }
            ?.associate { (day, date) -> date to day }
            ?: emptyMap()
    }
    val detailEvent = viewModel.event(detailId)
    val calendarDate = calendarEpochDay.takeIf { it != NO_DAY }?.let(LocalDate::ofEpochDay)

    // A new countdown's id is random, so it has to be minted once per editing
    // session rather than on every recomposition.
    val editorEvent = remember(editorOpen, editorIsNew, editorId, editorNewOn) {
        when {
            !editorOpen -> null
            editorIsNew -> viewModel.draftEvent(LocalDate.ofEpochDay(editorNewOn))
            else -> viewModel.event(editorId)
        }
    }

    fun startNewEvent(date: LocalDate) {
        editorIsNew = true
        editorNewOn = date.toEpochDay()
        editorId = ""
        editorOpen = true
    }

    fun startEditEvent(id: String) {
        editorIsNew = false
        editorId = id
        editorOpen = true
    }

    // A widget names the countdown it is showing, and tapping it asks for that one.
    // Collected rather than read once, so tapping the same widget twice opens it twice.
    // A request for a countdown that has since been deleted is simply dropped.
    LaunchedEffect(Unit) {
        openRequests.collect { id ->
            if (viewModel.event(id) != null) {
                screenName = AppScreen.HOME.name
                detailId = id
            }
        }
    }

    // Enabled while there is anywhere to go back to; disabled lets the system have
    // the press, which is what closes the app from the home screen. Each press goes
    // up exactly one level, matching the arrow the title bar shows.
    BackHandler(
        enabled = drawerOpen || editorOpen || wishEditorOpen || capsuleEditorOpen ||
            footprintEditorOpen || detailId != null || screen != AppScreen.HOME
    ) {
        when {
            drawerOpen -> drawerOpen = false
            editorOpen -> editorOpen = false
            wishEditorOpen -> wishEditorOpen = false
            capsuleEditorOpen -> capsuleEditorOpen = false
            footprintEditorOpen -> footprintEditorOpen = false
            detailId != null -> detailId = null
            // A settings page goes back to the settings it was opened from, the
            // way a page behind the gear should.
            screen.inSettings -> screenName = AppScreen.SETTINGS.name
            else -> screenName = AppScreen.HOME.name
        }
    }

    val uriHandler = LocalUriHandler.current
    val linkUrl = stringResource(R.string.about_link_url)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                // The voyage page is the sky itself: no bar over it, the system
                // bars hidden by the screen, the back key the way out. A
                // countdown opened from a star brings its own bar back.
                val skyAlone = screen == AppScreen.VOYAGE && detailEvent == null &&
                    !editorOpen && !wishEditorOpen && !capsuleEditorOpen && !footprintEditorOpen
                if (!skyAlone) {
                    AppTitleBar(
                    // The home screen keeps its name for the screen itself, where it is set
                    // at display size: the bar there is only a place to put the buttons.
                    title = when {
                        editorOpen -> stringResource(
                            if (editorIsNew) R.string.dialog_new_title else R.string.dialog_edit_title
                        )
                        wishEditorOpen -> stringResource(
                            if (wishEditorIsNew) R.string.things_new_title else R.string.things_edit_title
                        )
                        capsuleEditorOpen -> stringResource(R.string.capsule_editor_title)
                        footprintEditorOpen -> stringResource(R.string.footprint_editor_title)
                        detailEvent != null -> null
                        // These set their own name large in the content, so the bar keeps
                        // only the buttons.
                        screen == AppScreen.HOME || screen == AppScreen.REMINDERS ||
                            screen == AppScreen.THINGS || screen == AppScreen.CAPSULE ||
                            screen == AppScreen.WANJIANG -> null
                        else -> stringResource(screen.labelRes)
                    },
                    // Editing a countdown is a step into it, like its own screen: the button
                    // goes back to whatever list it came from rather than opening the drawer.
                    // A settings page is a step into it too — back to the settings, not out.
                    navigation = if (detailEvent != null || editorOpen || wishEditorOpen ||
                        capsuleEditorOpen || footprintEditorOpen || screen.inSettings
                    ) {
                        TitleBarNavigation.BACK
                    } else {
                        TitleBarNavigation.MENU
                    },
                    onNavigate = {
                        when {
                            editorOpen -> editorOpen = false
                            wishEditorOpen -> wishEditorOpen = false
                            capsuleEditorOpen -> capsuleEditorOpen = false
                            footprintEditorOpen -> footprintEditorOpen = false
                            detailEvent != null -> detailId = null
                            screen.inSettings -> screenName = AppScreen.SETTINGS.name
                            else -> drawerOpen = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    when {
                        // Both editors save from their own header row: each is a whole
                        // screen now, and the slot here belongs to one action over a list.
                        editorOpen -> Unit
                        wishEditorOpen -> Unit
                        capsuleEditorOpen -> Unit
                        footprintEditorOpen -> Unit

                        detailEvent != null -> IconButton(onClick = { startEditEvent(detailEvent.id) }) {
                            EditIcon()
                        }

                        // The order is chosen from a pill next to the search box, which is
                        // where the list it orders can see it.
                        screen == AppScreen.HOME -> {
                            IconButton(onClick = { startNewEvent(today) }) { PlusIcon() }
                        }

                        // A wish is added the same way: the slot opens the wish editor,
                        // a screen of its own like the countdown's.
                        screen == AppScreen.THINGS -> {
                            IconButton(onClick = { startNewWish() }) { PlusIcon() }
                        }

                        // A capsule is added the same way: the slot opens the capsule editor.
                        screen == AppScreen.CAPSULE -> {
                            IconButton(onClick = { startNewCapsule() }) { PlusIcon() }
                        }

                        // A footprint is added the same way: the slot opens the
                        // footprint editor.
                        screen == AppScreen.WANJIANG -> {
                            IconButton(onClick = { startNewFootprint() }) { PlusIcon() }
                        }

                        // On the calendar the plus belongs to the chosen day, and
                        // there is nothing to add to until one is chosen.
                        screen == AppScreen.CALENDAR -> {
                            if (calendarDate != null) {
                                IconButton(onClick = { startNewEvent(calendarDate) }) { PlusIcon() }
                            }
                        }
                    }
                }
                }
            },
        ) { insets ->
            Box(modifier = Modifier.padding(insets)) {
                when {
                    // The editor is a screen of its own, with the screen's own title bar
                    // above it. It was a dialog until the batch section made it taller than a
                    // dialog can be: a dialog is sized by its content, so the fields that
                    // appeared and disappeared inside it resized the window, which re-measured
                    // the content, and the two chased each other every frame.
                    editorOpen -> EventEditorScreen(
                        initial = editorEvent!!,
                        isNew = editorIsNew,
                        onSave = { edited ->
                            viewModel.saveEvent(edited)
                            editorOpen = false
                        },
                        onSaveMany = { batch ->
                            editorOpen = false
                            pendingBatch = batch
                        },
                        // Editing is the only way in with a delete: a countdown that does not
                        // exist yet has nothing to delete. The editor steps aside first, the
                        // same way it did while it was a dialog: the confirmation is the next
                        // question, and it belongs over the list rather than over the form.
                        onDelete = editorEvent.takeIf { !editorIsNew }?.let {
                            {
                                editorOpen = false
                                pendingDeleteId = it.id
                            }
                        },
                        onDismiss = { editorOpen = false },
                    )

                    // The wish editor takes its turn as a screen of its own too, before
                    // the list: saving closes it, deleting hands the id to the same
                    // confirmation the countdowns use, over the list underneath.
                    wishEditorWish != null -> WishEditorScreen(
                        initial = wishEditorWish!!,
                        isNew = wishEditorIsNew,
                        onSave = {
                            viewModel.saveWish(it)
                            wishEditorOpen = false
                        },
                        onDelete = if (!wishEditorIsNew) {
                            {
                                wishEditorOpen = false
                                pendingWishDeleteId = wishEditorWish!!.id
                            }
                        } else {
                            null
                        },
                        // Unticking a done wish lives here rather than on the
                        // card's face: a stray tap should not take the day back.
                        onUntick = if (wishEditorWish!!.done) {
                            {
                                viewModel.toggleWish(wishEditorWish!!.id)
                                wishEditorOpen = false
                            }
                        } else {
                            null
                        },
                        onDismiss = { wishEditorOpen = false },
                    )

                    // The capsule editor takes its turn as a screen of its own too:
                    // the words, the day they open, the photos that travel along.
                    capsuleEditorCapsule != null -> CapsuleEditorScreen(
                        initial = capsuleEditorCapsule!!,
                        onSave = {
                            viewModel.saveCapsule(it)
                            capsuleEditorOpen = false
                        },
                        onDelete = if (capsuleEditorId.isNotEmpty()) {
                            {
                                capsuleEditorOpen = false
                                pendingCapsuleDeleteId = capsuleEditorCapsule!!.id
                            }
                        } else {
                            null
                        },
                        onDismiss = { capsuleEditorOpen = false },
                    )

                    // The footprint editor takes its turn as a screen of its own.
                    footprintEditorFootprint != null -> FootprintEditorScreen(
                        initial = footprintEditorFootprint!!,
                        onSave = {
                            viewModel.saveFootprint(it)
                            footprintEditorOpen = false
                        },
                        onDelete = if (footprintEditorId.isNotEmpty()) {
                            {
                                footprintEditorOpen = false
                                pendingFootprintDeleteId = footprintEditorFootprint!!.id
                            }
                        } else {
                            null
                        },
                        onDismiss = { footprintEditorOpen = false },
                    )

                    detailEvent != null -> EventDetailScreen(
                        event = detailEvent,
                        now = now,
                        onTogglePin = { viewModel.togglePin(detailEvent.id) },
                    )

                     screen == AppScreen.HOME -> HomeScreen(
                         entries = countdownList(events, today, settings.sortOrder),
                         today = today,
                         query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        scope = searchScope,
                        onScopeChange = { searchScopeName = it.name },
                        sortOrder = settings.sortOrder,
                        onSortChange = viewModel::chooseSortOrder,
                        onOpen = { detailId = it },
                        onLongPress = { actionsForId = it },
                    )

                    screen == AppScreen.THINGS -> ThingsScreen(
                        wishes = wishes,
                        today = today,
                        onToggle = viewModel::toggleWish,
                        onEdit = { startEditWish(it) },
                    )

                    screen == AppScreen.CAPSULE -> CapsuleScreen(
                        capsules = capsules,
                        today = today,
                        onOpen = viewModel::openCapsule,
                        onEdit = { startEditCapsule(it) },
                    )

                    screen == AppScreen.WANJIANG -> WanJiangScreen(
                        footprints = footprints,
                        onEdit = { startEditFootprint(it) },
                    )

                    screen == AppScreen.SETTINGS -> SettingsScreen(
                        current = screen,
                        language = settings.language,
                        onLanguage = { languageOpen = true },
                        onSelect = { screenName = it.name },
                    )

                    screen == AppScreen.CALENDAR -> CalendarScreen(
                        month = visibleMonth,
                        today = today,
                        selectedDate = calendarDate,
                        eventsByDate = byDate,
                        capsulesByDate = capsulesByDate,
                        wishesByDate = wishesByDate,
                        anniversaryDate = anniversaryDate,
                        milestonesByDate = milestonesByDate,
                        query = calendarQuery,
                        onQueryChange = { calendarQuery = it },
                        scope = calendarScope,
                        onScopeChange = { calendarScopeName = it.name },
                        sortOrder = settings.sortOrder,
                        onSortChange = viewModel::chooseSortOrder,
                        onSelectDate = { calendarEpochDay = it.toEpochDay() },
                        onStepMonth = viewModel::stepMonth,
                        onMonthClick = { monthPickerOpen = true },
                        onToday = viewModel::goToToday,
                        onOpenEvent = { detailId = it },
                        onLongPressEvent = { actionsForId = it },
                        onOpenCapsule = { screenName = AppScreen.CAPSULE.name },
                        onOpenWishes = { screenName = AppScreen.THINGS.name },
                        onSetAnniversary = { viewModel.chooseAnniversary(it.toEpochDay()) },
                    )

                    screen == AppScreen.VOYAGE -> VoyageScreen(
                        events = events,
                        today = today,
                        milestones = milestonesByDate,
                        footprints = footprints,
                        onOpen = { detailId = it },
                        musicFile = settings.voyageMusicFile,
                        onMusicPicked = viewModel::chooseVoyageMusic,
                    )

                    screen == AppScreen.REMINDERS -> NotificationsScreen(
                        settings = settings,
                        next = Reminders.planned(
                            events,
                            settings.remindMinutes,
                            settings.remindLeadMinutes,
                        ),
                        onRemind = viewModel::chooseRemind,
                        onMinutes = viewModel::chooseRemindMinutes,
                        onLead = viewModel::chooseRemindLead,
                        onEventLead = viewModel::chooseEventLead,
                        events = events,
                        onTest = viewModel::sendTestNotification,
                    )

                    screen == AppScreen.APPEARANCE -> AppearanceScreen(
                        settings = settings,
                        onPreset = viewModel::chooseTheme,
                        onCustomHue = viewModel::chooseCustomHue,
                    )

                    screen == AppScreen.WIDGETS -> WidgetSettingsScreen(
                        settings = settings,
                        onPanel = viewModel::chooseWidgetPanel,
                        onTextSize = viewModel::chooseWidgetTextSize,
                    )

                    screen == AppScreen.DATA -> DataScreen(
                        onExport = viewModel::exportBackup,
                        onRead = viewModel::readBackup,
                        onApply = viewModel::applyBackup,
                        onExportIcs = viewModel::exportIcs,
                    )

                    screen == AppScreen.CHANGELOG -> ChangelogScreen()

                    else -> ProjectIntroScreen(
                        // The link is the one thing here that leaves the app, and it
                        // is handed to the browser: TickCount has no INTERNET
                        // permission of its own.
                        onOpenLink = { runCatching { uriHandler.openUri(linkUrl) } },
                    )
                }
            }
        }

        // The drawer, over the screen rather than beside it. Fading the scrim and
        // sliding the panel are separate animations so the two can differ.
        AnimatedVisibility(visible = drawerOpen, enter = fadeIn(), exit = fadeOut()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { drawerOpen = false },
                    ),
            )
        }

        AnimatedVisibility(
            visible = drawerOpen,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
        ) {
            AppDrawerContent(
                // Nothing is highlighted while a countdown's own screen is open,
                // because that screen belongs to no drawer entry.
                current = if (detailEvent == null) screen else null,
                onSelect = { picked ->
                    screenName = picked.name
                    detailId = null
                    drawerOpen = false
                },
                modifier = Modifier.fillMaxWidth(0.5f),
            )
        }

        // The launch page: the mark the launcher wears, and under it the line the
        // app is really about. Cold start only — a rotation keeps it gone, and it
        // leaves with a fade rather than a cut.
        AnimatedVisibility(visible = !splashGone, exit = fadeOut(tween(450))) {
            LaunchPage()
        }
    }

    if (monthPickerOpen) {
        MonthYearPickerDialog(
            initial = visibleMonth,
            onSelect = { picked ->
                viewModel.showMonth(picked)
                monthPickerOpen = false
            },
            onDismiss = { monthPickerOpen = false },
        )
    }

    if (languageOpen) {
        LanguageDialog(
            current = settings.language,
            onPick = {
                languageOpen = false
                // Changing this rebuilds the activity, so nothing after it in this
                // composition is guaranteed to run against the old resources.
                viewModel.chooseLanguage(it)
            },
            onDismiss = { languageOpen = false },
        )
    }

    // The batch as it will be: how many countdowns, and the day the last one lands on.
    pendingBatch?.let { batch ->
        val lastDate = batch.lastOrNull()?.date
        if (lastDate != null) {
            BatchConfirmDialog(
                count = batch.size,
                lastDate = lastDate,
                onConfirm = {
                    viewModel.saveEvents(batch)
                    pendingBatch = null
                },
                onDismiss = { pendingBatch = null },
            )
        }
    }

    // Only rendered while the countdown still exists, so one that has already gone
    // 閳?deleted from another path, say 閳?cannot raise a dialog about itself.
    val pendingDeleteName = viewModel.event(pendingDeleteId)?.title

    if (pendingDeleteId != null && pendingDeleteName != null) {
        DeleteConfirmDialog(
            name = pendingDeleteName,
            onConfirm = {
                val removed = pendingDeleteId
                viewModel.removeEvent(removed!!)
                if (detailId == removed) detailId = null
                pendingDeleteId = null
            },
            onDismiss = { pendingDeleteId = null },
        )
    }

    // The wish editor asks the same question on its way out, over the list it came from.
    val pendingWishDeleteName = wishes.firstOrNull { it.id == pendingWishDeleteId }?.title

    if (pendingWishDeleteId != null && pendingWishDeleteName != null) {
        DeleteConfirmDialog(
            name = pendingWishDeleteName,
            onConfirm = {
                viewModel.removeWish(pendingWishDeleteId!!)
                pendingWishDeleteId = null
            },
            onDismiss = { pendingWishDeleteId = null },
        )
    }

    // The capsule editor asks the same question on its way out.
    val pendingCapsuleDeleteName = capsules.firstOrNull { it.id == pendingCapsuleDeleteId }
        ?.let { it.title.ifBlank { it.message.take(20) } }

    if (pendingCapsuleDeleteId != null && pendingCapsuleDeleteName != null) {
        DeleteConfirmDialog(
            name = pendingCapsuleDeleteName,
            onConfirm = {
                viewModel.removeCapsule(pendingCapsuleDeleteId!!)
                pendingCapsuleDeleteId = null
            },
            onDismiss = { pendingCapsuleDeleteId = null },
        )
    }

    // The footprint editor asks the same question on its way out.
    val pendingFootprintDeleteName = footprints.firstOrNull { it.id == pendingFootprintDeleteId }?.place

    if (pendingFootprintDeleteId != null && pendingFootprintDeleteName != null) {
        DeleteConfirmDialog(
            name = pendingFootprintDeleteName,
            onConfirm = {
                viewModel.removeFootprint(pendingFootprintDeleteId!!)
                pendingFootprintDeleteId = null
            },
            onDismiss = { pendingFootprintDeleteId = null },
        )
    }

    // Read once, on the first launch after installing: three sentences about where things
    // are, and then never again.
    if (showGuide) {
        GuideDialog(onDismiss = viewModel::dismissGuide)
    }

    val actionsEvent = viewModel.event(actionsForId)

    if (actionsEvent != null) {
        EventActionsDialog(
            name = actionsEvent.title,
            pinned = actionsEvent.pinned,
            onPin = {
                viewModel.togglePin(actionsEvent.id)
                actionsForId = null
            },
            onEdit = {
                actionsForId = null
                startEditEvent(actionsEvent.id)
            },
            // The dialog closes first for the same reason the editor's does: the
            // confirmation is the next question, and stacking two would bury the name.
            onDelete = {
                actionsForId = null
                pendingDeleteId = actionsEvent.id
            },
            onDismiss = { actionsForId = null },
        )
    }
}

/**
 * What a long press on a card offers.
 *
 * The three things that used to need the countdown's own screen: pin it, change it, or
 * do away with it. Opening a countdown to pin it was a step that existed only because
 * there was nowhere else to put the switch.
 */
@Composable
private fun EventActionsDialog(
    name: String,
    pinned: Boolean,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column {
                ActionRow(
                    label = stringResource(
                        if (pinned) R.string.action_unpin else R.string.field_pinned
                    ),
                    onClick = onPin,
                )
                ActionRow(label = stringResource(R.string.action_edit), onClick = onEdit)
                ActionRow(label = stringResource(R.string.action_delete), onClick = onDelete)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The first-run card: where the buttons are, and nothing else.
 *
 * A dialog rather than coach marks over the controls: the screen it would point at is behind
 * this one, and a card that can be read in one go needs no arrows to follow.
 */
@Composable
private fun GuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.guide_title)) },
        text = {
            Text(
                text = stringResource(R.string.guide_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/**
 * The launch page: the deer, drawn in a few lines, and under it the line the
 * app is really about — the two names the whole thing counts for, in the brush
 * the pages write them with. The deer settles first, the line follows.
 */
@Composable
private fun LaunchPage(modifier: Modifier = Modifier) {
    var textIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(450)
        textIn = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(R.color.window_background)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.launch_deer),
                contentDescription = null,
                modifier = Modifier.size(190.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.launch_line),
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = XingKai,
                color = Color(0xFF3A3226),
                modifier = Modifier.graphicsLayer {
                    alpha = if (textIn) 1f else 0f
                    translationY = if (textIn) 0f else 14.dp.toPx()
                },
            )
        }
    }
}

/**
 * The one question a batch add asks before it adds anything.
 *
 * It leads with the two things that make a batch a batch rather than a single countdown:
 * how many are about to appear, and the day the last of them lands on. Both are read off the
 * list that will be saved rather than off the two fields that produced it, so the answer
 * cannot describe something other than what the button does.
 *
 * The count is the whole batch, the countdown that was filled in included 閳?that is what the
 * list will hold after this, and it is the number someone is agreeing to.
 */
@Composable
private fun BatchConfirmDialog(
    count: Int,
    lastDate: LocalDate,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormatter = rememberDateFormatter(R.string.date_format_full)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_batch_title)) },
        text = {
            Text(
                text = pluralStringResource(
                    R.plurals.dialog_batch_message,
                    count,
                    count,
                    lastDate.format(dateFormatter),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** One of those actions: a whole row is the target, not just the word in it. */
@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

private fun String.toScope(): SearchScope =
    runCatching { SearchScope.valueOf(this) }.getOrDefault(SearchScope.NAME)

/**
 * Which language the interface is in.
 *
 * Each option is named in its own language rather than in the current one, which is the
 * only way the choice makes sense to someone who cannot read the language they are
 * currently stuck in.
 */
@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onPick: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.drawer_language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(option) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == current,
                            onClick = { onPick(option) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
