package dev.tvlauncher.routines

import android.content.Context
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
import dev.tvlauncher.system.PowerActions
import dev.tvlauncher.system.SleepTimer
import dev.tvlauncher.system.SystemPrefs

/** Settings > System > Routines: the sleep timer and Bedtime mode (also Spotlight quick actions). */
object RoutinesSection : SettingsSection, SpecifiedPage {
    const val ID = "routines"
    const val SLEEP = "rt.sleep"
    const val BEDTIME = "rt.bedtime"
    override val id = ID
    override val title = "Routines"
    override val iconRes = R.drawable.ic_routine
    override val group = SettingsGroup.SYSTEM
    override val description = "Sleep timer and Bedtime mode."
    override val spec = PageSpec(ID, listOf(SLEEP, BEDTIME))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        page.lead("Wind down without the remote: a sleep timer, and Bedtime mode in one press.")
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
        page.note("A sleeping TV may run the timer a little late: Android delays alarms while the TV is in standby.")
        return page
    }
}
