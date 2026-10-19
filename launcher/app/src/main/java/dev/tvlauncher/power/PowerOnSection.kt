package dev.tvlauncher.power

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.Chip
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.ui.SystemActions

/**
 * Settings > TV & system > Power & sleep > Power on: the "Open home after power on" switch and what
 * it needs. Opened from the Power & sleep page (dev.tvlauncher.system.SystemHubSection), not listed
 * in the sidebar on its own, so Power has a single place in Settings.
 */
object PowerOnSection : SettingsSection, SpecifiedPage {
    const val ID = "power_on"
    override val id = ID
    override val title = "Power on"
    override val iconRes = R.drawable.ic_set_home
    override val group = SettingsGroup.SYSTEM
    override val description = "What the TV shows when you turn it on."
    override val spec = PageSpec(ID, listOf("power_on.open", "power_on.allow", "power_on.last"))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val prefs = PowerOnPrefs(app)
        val page = SettingsPage(context, nav.style, spec)
        page.row("power_on.open", "Open home after power on", RowKind.Toggle(prefs.enabled) { on ->
            prefs.enabled = on
            nav.refresh()
        }, subtitle = "Instead of the last input or app", iconRes = R.drawable.ic_set_home)
        val r = PowerOn.readiness(app)
        page.row("power_on.allow", "Permission", RowKind.Action(
            value = PowerOnStatus.value(r),
            chip = if (PowerOnStatus.ready(r)) Chip.POSITIVE else Chip.NEUTRAL
        ) {
            if (!SystemActions.start(context, PowerOn.overlaySettingsIntent(context))) nav.notify("This TV has no Display over other apps page")
        }, subtitle = PowerOnStatus.subtitle(r), iconRes = R.drawable.ic_set_check)
        page.row("power_on.last", "Last power on", RowKind.Info(), subtitle = prefs.lastOutcome ?: "Nothing yet", iconRes = R.drawable.ic_set_clock)
        return page
    }
}
