package dev.tvlauncher.routines

import android.content.Context
import android.content.Intent
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.power.PowerOn
import dev.tvlauncher.profiles.ProfileManager
import dev.tvlauncher.system.InsistGate
import dev.tvlauncher.system.PowerActions
import dev.tvlauncher.system.SleepTimer
import dev.tvlauncher.system.SystemPrefs
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Row keys and choices of the Routines pages (JVM-tested). */
object RoutinesPage {
    const val TIMES = "rt.times"
    const val ADD = "rtl.add"
    fun slot(i: Int) = "rtl.slot${i + 1}"
    val LIST_SPEC = PageSpec("routines.list", (0 until RoutineModel.MAX).map(::slot) + ADD)

    const val ON = "rte.on"
    const val DAYS = "rte.days"
    const val TIME = "rte.time"
    const val ACTION = "rte.action"
    const val DETAIL = "rte.detail"
    const val RUN = "rte.run"
    const val DELETE = "rte.delete"
    val EDIT_SPEC = PageSpec("routines.edit", listOf(ON, DAYS, TIME, ACTION, DETAIL, RUN, DELETE))

    /** What a routine can do, in the order the Action row offers it. */
    val KINDS = listOf(
        RoutineAction.Kind.AMBIENT, RoutineAction.Kind.ROOM, RoutineAction.Kind.PROFILE,
        RoutineAction.Kind.BEDTIME, RoutineAction.Kind.BEDTIME_OFF, RoutineAction.Kind.LAUNCH,
        RoutineAction.Kind.FOCUS_START, RoutineAction.Kind.FOCUS_STOP
    )

    /** A fresh action of [kind]; [firstProfile] / [firstApp] fill the detail, or null when there is nothing to pick. */
    fun actionFor(kind: RoutineAction.Kind, firstProfile: String?, firstApp: Pair<String, String>?): RoutineAction? = when (kind) {
        RoutineAction.Kind.AMBIENT -> RoutineAction.Ambient
        RoutineAction.Kind.BEDTIME -> RoutineAction.BedtimeOn
        RoutineAction.Kind.BEDTIME_OFF -> RoutineAction.BedtimeOff
        RoutineAction.Kind.ROOM -> RoutineAction.Room("CINEMA")
        RoutineAction.Kind.PROFILE -> firstProfile?.let { RoutineAction.Profile(it) }
        RoutineAction.Kind.LAUNCH -> firstApp?.let { RoutineAction.Launch(it.first, it.second) }
        RoutineAction.Kind.FOCUS_START -> RoutineAction.Focus(true)
        RoutineAction.Kind.FOCUS_STOP -> RoutineAction.Focus(false)
    }

    /** Index of [days] among RoutineModel.DAY_PRESETS (0 = every day when it is a custom set). */
    fun daysIndex(days: Set<Int>): Int = RoutineModel.DAY_PRESETS.indexOf(days).coerceAtLeast(0)
}

/** Settings > System > Routines: the sleep timer, Bedtime mode and up to five time routines. */
object RoutinesSection : SettingsSection, SpecifiedPage {
    const val ID = "routines"
    const val SLEEP = "rt.sleep"
    const val BEDTIME = "rt.bedtime"
    override val id = ID
    override val title = "Routines"
    override val iconRes = R.drawable.ic_routine
    override val group = SettingsGroup.SYSTEM
    override val description = "Sleep timer, Bedtime mode and time routines."
    override val spec = PageSpec(ID, listOf(SLEEP, BEDTIME, RoutinesPage.TIMES))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.lead("Wind down without the remote: a sleep timer, Bedtime mode in one press, and routines at set times.")
        val minutes = if (PowerActions.sleepMinutesLeft(context) > 0) SystemPrefs(context).sleepMinutes else 0
        val idx = SleepTimer.CHOICES.indexOf(minutes).coerceAtLeast(0)
        page.row(SLEEP, "Sleep timer", RowKind.Choice(SleepTimer.CHOICES.map { SleepTimer.label(it) }, idx) { i ->
            val m = SleepTimer.CHOICES[i]
            PowerActions.setSleepTimer(context, m)
            nav.notify(if (m == 0) "Sleep timer off" else "Sleep in ${SleepTimer.label(m)}")
        }, subtitle = if (PowerActions.helperEnabled(context)) "Then the screen dims and the TV's power menu opens"
            else "Then the screen dims and the sound mutes", iconRes = R.drawable.ic_qa_sleep)
        page.row(BEDTIME, "Bedtime mode", RowKind.Toggle(Bedtime.isOn(context)) { on ->
            if (on) Bedtime.on(context) else Bedtime.off(context)
        }, subtitle = "Dark room lighting, volume a step down, ambient sooner, no device cards", iconRes = R.drawable.ic_bedtime)
        val count = RoutineStore(context).load().size
        page.row(RoutinesPage.TIMES, "Time routines", RowKind.Action(if (count == 0) "None" else "$count of ${RoutineModel.MAX}") {
            nav.push("Time routines") { c, n -> listPage(c, n) }
        }, subtitle = "Open ambient, change room lighting, switch profile and more at a set time", iconRes = R.drawable.ic_set_clock)
        page.note("A sleeping TV may run the timer a little late: Android delays alarms while the TV is in standby.")
        return page
    }

    private fun h24(context: Context) = android.text.format.DateFormat.is24HourFormat(context)

    private fun stamp(context: Context) = SimpleDateFormat("EEE " + if (h24(context)) "HH:mm" else "h:mm a", Locale.getDefault())

    private fun listPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, RoutinesPage.LIST_SPEC)
        val store = RoutineStore(context)
        val list = store.load()
        val state = ProfileManager.load(context)
        val fmt = stamp(context)
        page.lead("Up to ${RoutineModel.MAX} routines. Each one runs at its time on the days you pick.")
        list.forEachIndexed { i, r ->
            val last = RoutinePlan.lastLine(store.lastRun(r.id)) { fmt.format(Date(it)) }
            page.row(RoutinesPage.slot(i), RoutineModel.summary(r, h24(context)) { id -> state.byId(id)?.name },
                RowKind.Action(if (r.enabled) "On" else "Off") { nav.push("Routine") { c, n -> editPage(c, n, r.id) } },
                subtitle = last, iconRes = R.drawable.ic_routine)
        }
        RoutineModel.nextId(list)?.let { id ->
            page.row(RoutinesPage.ADD, "Add a routine", RowKind.Action(chevron = false) {
                store.upsert(RoutineModel.defaultRoutine(id))
                RoutineScheduler.arm(context)
                nav.refresh()
                nav.push("Routine") { c, n -> editPage(c, n, id) }
            }, subtitle = "Starts as weekdays at 07:00, open ambient", iconRes = R.drawable.ic_disc_plus)
        }
        if (android.os.Build.VERSION.SDK_INT >= 29 && !PowerOn.overlayAllowed(context)) {
            page.note("To open ambient or an app while another app is in front, allow M02 to display over other apps " +
                "(Settings > TV & system > Power & sleep > Power on).")
        }
        return page
    }

    private val deleteGate = InsistGate()

    private fun editPage(context: Context, nav: SettingsNav, id: Int): View {
        val page = SettingsPage(context, nav.style, RoutinesPage.EDIT_SPEC)
        val store = RoutineStore(context)
        val r = store.load().firstOrNull { it.id == id } ?: return page.also { page.note("This routine was deleted.") }
        fun current() = store.load().firstOrNull { it.id == id }
        fun save(change: (Routine) -> Routine, rebuild: Boolean = false) {
            val now = current() ?: return
            store.upsert(change(now))
            RoutineScheduler.arm(context)
            if (rebuild) nav.refresh()
        }
        val state = ProfileManager.load(context)
        val profiles = state.profiles
        val apps = tvApps(context)
        page.row(RoutinesPage.ON, "Routine on", RowKind.Toggle(r.enabled) { on -> save({ it.copy(enabled = on) }, rebuild = true) },
            subtitle = if (r.enabled) nextLine(context, r) else "Off: it will not run", iconRes = R.drawable.ic_set_check)
        page.row(RoutinesPage.DAYS, "Days", RowKind.Choice(RoutineModel.DAY_PRESETS.map(RoutineModel::daysLabel), RoutinesPage.daysIndex(r.days)) { i ->
            save({ it.copy(days = RoutineModel.DAY_PRESETS[i]) })
        }, iconRes = R.drawable.ic_set_calendar)
        page.row(RoutinesPage.TIME, "Time", RowKind.Stepper(r.minuteOfDay / RoutineModel.STEP_MIN, 0, RoutineModel.SLOTS - 1,
            { RoutineModel.timeLabel(it * RoutineModel.STEP_MIN, h24(context)) }) { v ->
            save({ it.copy(minuteOfDay = v * RoutineModel.STEP_MIN) })
        }, iconRes = R.drawable.ic_set_clock)
        val kinds = RoutinesPage.KINDS
        page.row(RoutinesPage.ACTION, "Action", RowKind.Choice(kinds.map { it.label }, kinds.indexOf(r.action.kind).coerceAtLeast(0)) { i ->
            val other = profiles.firstOrNull { it.id != state.activeId && !it.hasPin }?.id
            val a = RoutinesPage.actionFor(kinds[i], other, apps.firstOrNull())
            if (a == null) nav.notify(if (kinds[i] == RoutineAction.Kind.PROFILE) "Add a profile without a PIN first" else "No TV apps found")
            else save({ it.copy(action = a) }, rebuild = true)
        }, iconRes = R.drawable.ic_routine)
        when (val a = r.action) {
            is RoutineAction.Room -> page.row(RoutinesPage.DETAIL, "Room lighting", RowKind.Choice(RoutineModel.ROOM_LABELS,
                RoutineModel.ROOM_CHOICES.indexOf(a.choice).coerceAtLeast(0)) { i ->
                save({ it.copy(action = RoutineAction.Room(RoutineModel.ROOM_CHOICES[i])) })
            }, iconRes = R.drawable.ic_room_auto)
            is RoutineAction.Profile -> page.row(RoutinesPage.DETAIL, "Profile", RowKind.Choice(profiles.map { it.name },
                profiles.indexOfFirst { it.id == a.id }.coerceAtLeast(0)) { i ->
                save({ it.copy(action = RoutineAction.Profile(profiles[i].id)) })
            }, subtitle = "A routine never opens a profile that has a PIN", iconRes = R.drawable.ic_profile)
            is RoutineAction.Launch -> if (apps.isNotEmpty()) page.row(RoutinesPage.DETAIL, "App", RowKind.Choice(apps.map { it.second },
                apps.indexOfFirst { it.first == a.pkg }.coerceAtLeast(0)) { i ->
                save({ it.copy(action = RoutineAction.Launch(apps[i].first, apps[i].second)) })
            }, iconRes = R.drawable.ic_set_tiles)
            else -> Unit
        }
        page.row(RoutinesPage.RUN, "Try it now", RowKind.Action(chevron = false) {
            val now = current() ?: return@Action
            nav.notify(RoutineRunner.run(context, now, 0L).label)
        }, iconRes = R.drawable.ic_qa_ambient)
        page.row(RoutinesPage.DELETE, "Delete routine", RowKind.Action(chevron = false) {
            if (!deleteGate.press("rt$id", System.currentTimeMillis())) { nav.notify("Press OK again to delete"); return@Action }
            store.delete(id)
            RoutineScheduler.arm(context)
            nav.notify("Routine deleted")
            nav.pop()
            nav.refresh()
        }, iconRes = R.drawable.ic_set_reset)
        val fmt = stamp(context)
        page.note(RoutinePlan.lastLine(store.lastRun(id)) { fmt.format(Date(it)) } +
            ". Ambient and apps open only while the TV is on; settings changes still apply in standby.")
        return page
    }

    private fun nextLine(context: Context, r: Routine): String {
        val t = RoutineModel.nextFire(r, System.currentTimeMillis(), TimeZone.getDefault()) ?: return "Pick at least one day"
        return "Next: ${stamp(context).format(Date(t))}"
    }

    /** (package, label) of the TV apps, by label; the launcher itself is left out. */
    private fun tvApps(context: Context): List<Pair<String, String>> = try {
        val pm = context.packageManager
        pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != context.packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase(Locale.getDefault()) }
            .take(40)
    } catch (e: Exception) {
        emptyList()
    }
}
