package dev.tvlauncher.speed

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.ui.BouncingDots
import dev.tvlauncher.ui.dp
import java.text.DateFormat
import java.util.Date

/** Plain words for the Speed page. Pure. */
object SpeedText {
    const val AUTO_SUBTITLE = "Overnight while the TV is off. Caches and background apps only"
    const val HOME_SUBTITLE = "Closes apps you have not used for a while"
    const val HOME_NEEDS_ACCESS = "Needs usage access (Advanced)"
    const val CLEAN_SUBTITLE = "Clears caches and closes background apps. Your data stays"

    fun autoSubtitle(enabled: Boolean, hour: Int, every: Int): String =
        if (!enabled) "Off" else "${SpeedSchedule.everyLabel(every)} around ${SpeedSchedule.hourLabel(hour)} while the TV is off"

    fun lastLine(r: CleanupResult?, time: (Long) -> String): String =
        if (r == null) "Nothing yet"
        else "${FreedBytes.headline(r)} ${if (r.trigger == Trigger.AUTO) "overnight" else "by you"} at ${time(r.at)}"
}

/** Settings > System > Speed. */
object SpeedSection : SettingsSection, SpecifiedPage {
    const val ID = "speed"
    override val id = ID
    override val title = "Speed"
    override val iconRes = R.drawable.ic_set_speed
    override val group = SettingsGroup.SYSTEM
    override val description = "Keeps the TV quick. Only caches and background apps, never your data."
    override val spec = PageSpec(
        ID,
        listOf("speed.hint", "speed.clean", "speed.auto", "speed.home", "speed.keep", "speed.storage"),
        listOf("speed.hour", "speed.every", "speed.access", "speed.last")
    )

    private val main by lazy { Handler(Looper.getMainLooper()) }

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val prefs = SpeedPrefs(app)
        val page = SettingsPage(context, nav.style, spec)
        val access = UsageAccess.hasAccess(app)

        val hint = page.row("speed.hint", "Why is my TV slow?", RowKind.Info("…"), subtitle = "Checking…", iconRes = R.drawable.ic_set_status)

        lateinit var clean: SettingsRow
        clean = page.row("speed.clean", "Clean up now", RowKind.Action(chevron = false) {
            if (SpeedCleaner.isRunning) return@Action
            clean.setValue(SpeedCleaner.Step.CACHES.label)
            Speed.cleanNow(app, progress = { step -> main.post { clean.setValue(step.label) } }) { r ->
                if (r == null) {
                    clean.setValue("Just done")
                    clean.setSubtitle("Try again in a moment")
                } else {
                    clean.setValue(FreedBytes.headline(r))
                    clean.setSubtitle(FreedBytes.detail(r))
                    nav.background({ facts(app) }) { f -> hint.setValue(SlowHint.value(f)); hint.setSubtitle(SlowHint.subtitle(f)) }
                }
            }
        }, subtitle = SpeedText.CLEAN_SUBTITLE, iconRes = R.drawable.ic_set_speed)

        page.row("speed.auto", "Automatic cleanup", RowKind.Toggle(prefs.autoEnabled) { on ->
            prefs.autoEnabled = on
            Speed.schedule(app)
            nav.refresh()
        }, subtitle = SpeedText.autoSubtitle(prefs.autoEnabled, prefs.quietHour, prefs.everyDays), iconRes = R.drawable.ic_set_clock)

        page.row("speed.home", "Free memory when you go Home", RowKind.Toggle(prefs.freeOnHome) { on ->
            prefs.freeOnHome = on
        }, subtitle = if (access) SpeedText.HOME_SUBTITLE else SpeedText.HOME_NEEDS_ACCESS, iconRes = R.drawable.ic_set_home)

        page.row("speed.keep", "Never close", RowKind.Action(prefs.keep.size.let { if (it == 0) "Defaults" else "+$it" }) {
            nav.push("Never close") { c, n -> keepPage(c, n) }
        }, subtitle = "Live TV, players, keyboards and this launcher are always kept", iconRes = R.drawable.ic_set_star)

        val storage = page.row("speed.storage", "Storage", RowKind.Action("…") {
            nav.push("Storage") { c, n -> storagePage(c, n) }
        }, subtitle = "Free space and the biggest caches", iconRes = R.drawable.ic_sc_storage)

        page.advanced(nav, "speed.advanced", "Quiet hour, how often, usage access") { adv ->
            adv.row("speed.hour", "Quiet hour", RowKind.Stepper(prefs.quietHour, 0, 23, { SpeedSchedule.hourLabel(it) }) { h ->
                prefs.quietHour = h
                Speed.schedule(app)
            }, subtitle = "When the nightly cleanup may run", iconRes = R.drawable.ic_set_clock)
            val every = SpeedSchedule.EVERY_DAYS
            adv.row("speed.every", "How often", RowKind.Choice(every.map { SpeedSchedule.everyLabel(it) }, every.indexOf(prefs.everyDays).coerceAtLeast(0)) { i ->
                prefs.everyDays = every[i]
            }, iconRes = R.drawable.ic_set_calendar)
            adv.row("speed.access", "Usage access", if (access) RowKind.Info("Granted") else RowKind.Action("Not granted") {
                if (!UsageAccess.openSettings(context)) nav.notify("Finish setup to enable")
            }, subtitle = "Lets cleanup see which apps you used recently", iconRes = R.drawable.ic_set_eye)
            val fmt = DateFormat.getTimeInstance(DateFormat.SHORT)
            adv.row("speed.last", "Last cleanup", RowKind.Info(),
                subtitle = SpeedText.lastLine(prefs.lastResult()) { fmt.format(Date(it)) }, iconRes = R.drawable.ic_set_check)
        }

        nav.background({ facts(app) to SpeedSystem.storage() }) { (f, st) ->
            hint.setValue(SlowHint.value(f))
            hint.setSubtitle(SlowHint.subtitle(f))
            storage.setValue("${Bytes.short(st.first)} free")
        }
        return page
    }

    private fun facts(app: Context): SlowHint.Facts {
        val now = System.currentTimeMillis()
        val (avail, total, low) = SpeedSystem.memory(app)
        val (free, size) = SpeedSystem.storage()
        val lastUsed = SpeedSystem.lastUsed(app, now)
        val count = lastUsed?.let {
            val keep = SpeedSystem.keepContext(app, now, SpeedSystem.media(app), it)
            ClosePlan.backgroundCount(keep, it, now)
        }
        return SlowHint.Facts(avail, total, low, free, size, count)
    }

    private fun loading(context: Context, nav: SettingsNav, work: () -> Any?, fill: (SettingsPage, Any?) -> Unit): View {
        val page = SettingsPage(context, nav.style)
        val dots = BouncingDots(context)
        page.column.addView(dots, LinearLayout.LayoutParams(-2, -2).apply { topMargin = context.dp(24); marginStart = context.dp(8) })
        nav.background(work) { data ->
            page.column.removeView(dots)
            fill(page, data)
            nav.contentReady()
        }
        return page
    }

    /** Every app with a launcher entry: built-in keeps are fixed, the rest are the owner's switches. */
    private fun keepPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        return loading(context, nav, {
            val now = System.currentTimeMillis()
            val keep = SpeedSystem.keepContext(app, now, SpeedSystem.Media(emptySet(), false), null)
            keep to keep.launchable.map { it to SpeedSystem.label(app, it) }.sortedBy { it.second.lowercase() }
        }) { page, data ->
            @Suppress("UNCHECKED_CAST")
            val pair = data as Pair<KeepRules.Context, List<Pair<String, String>>>
            val (keep, apps) = pair
            val prefs = SpeedPrefs(app)
            page.note("Apps switched on here are never closed by cleanup. Apps that are playing or on screen are always left alone.")
            for ((pkg, label) in apps) {
                val fixed = KeepRules.fixedReason(pkg, keep)
                if (fixed != null) {
                    page.row("speed.keep.$pkg", label, RowKind.Info("Always kept"), subtitle = fixed.label)
                } else {
                    page.row("speed.keep.$pkg", label, RowKind.Toggle(pkg in prefs.keep) { on ->
                        prefs.keep = if (on) prefs.keep + pkg else prefs.keep - pkg
                    })
                }
            }
        }
    }

    /** Free space, all caches, and the biggest caches by app when the TV lets us measure them. */
    private fun storagePage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        return loading(context, nav, {
            val (free, total) = SpeedSystem.storage()
            val byApp = SpeedSystem.cacheByApp(app, SpeedSystem.launchable(app)).take(8)
                .map { Triple(it.first, SpeedSystem.label(app, it.first), it.second) }
            StorageReport(free, total, SpeedSystem.totalCache(app), byApp)
        }) { page, data ->
            val r = data as StorageReport
            page.row("speed.st.free", "Free space", RowKind.Info(Bytes.short(r.free)),
                subtitle = if (r.total > 0) "of ${Bytes.short(r.total)}" else null, iconRes = R.drawable.ic_sc_storage)
            page.row("speed.st.cache", "App caches", RowKind.Info(r.cache?.let { Bytes.short(it) } ?: "Not reported"),
                subtitle = "Clean up now clears what the TV allows", iconRes = R.drawable.ic_set_speed)
            if (r.byApp.isEmpty()) {
                page.note(if (UsageAccess.hasAccess(app)) "No app has a cache worth mentioning."
                    else "Allow usage access (Speed > Advanced) to see which apps use the most.")
            } else {
                page.group("Biggest caches")
                for ((pkg, label, bytes) in r.byApp) page.row("speed.st.$pkg", label, RowKind.Info(Bytes.short(bytes)))
            }
        }
    }

    private data class StorageReport(val free: Long, val total: Long, val cache: Long?, val byApp: List<Triple<String, String, Long>>)
}
