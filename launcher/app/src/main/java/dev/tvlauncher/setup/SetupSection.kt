package dev.tvlauncher.setup

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

/** Settings > System > Setup: re-runs the first-time checklist. Registered from LauncherApp. */
object SetupSection : SettingsSection, SpecifiedPage {
    const val ID = "setup"
    override val id = ID
    override val title = SetupCopy.SECTION_TITLE
    override val iconRes = R.drawable.ic_sc_settings
    override val group = SettingsGroup.SYSTEM
    override val description = SetupCopy.SECTION_DESCRIPTION
    override val spec = PageSpec(ID, listOf("setup.run", "setup.resume", "setup.status"))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(this)
        registry.register(ConfigBackupSection) // same id as the built-in page: takes its slot
    }

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val m = SetupStore(context).oobe()
        if (m.current != null && m.current != OobePage.DONE) {
            page.row("setup.resume", "Continue setup", RowKind.Action {
                if (!SetupStatus.open(context)) nav.notify("Setup could not be opened")
            }, subtitle = OobeCopy.title(m.current), iconRes = R.drawable.ic_sc_settings)
        }
        page.row("setup.run", SetupCopy.SECTION_RUN, RowKind.Action {
            if (!SetupStatus.open(context, restart = true)) nav.notify("Setup could not be opened")
        }, subtitle = "Language, network, apps, look and permissions", iconRes = R.drawable.ic_sc_settings)
        val status = page.row("setup.status", "Checklist", RowKind.Info(""))
        nav.background({
            SetupStatus.autoConfigure(context.applicationContext)
            SetupPlan.progress(SetupPlan.steps(SetupProber.probe(context), SetupStore(context).memory()))
        }) { status.setValue(it) }
        return page
    }
}
