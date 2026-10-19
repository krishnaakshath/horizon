package dev.tvlauncher.focus

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

/** Settings > System > Focus mode: on/off, how long, and what it quiets. */
object FocusSection : SettingsSection, SpecifiedPage {
    const val ID = "focus_mode"
    override val id = ID
    override val title = "Focus mode"
    override val iconRes = R.drawable.ic_qa_focus
    override val group = SettingsGroup.SYSTEM
    override val description = FocusModel.DESCRIPTION

    const val STATE = "focus.state"
    const val LENGTH = "focus.length"
    const val QUIETS = "focus.quiets"

    override val spec = PageSpec(ID, listOf(STATE, LENGTH, QUIETS))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val page = SettingsPage(context, nav.style, spec)
        val now = System.currentTimeMillis()
        val s = FocusMode.state(app)
        lateinit var state: dev.tvlauncher.appsettings.SettingsRow
        state = page.row(STATE, "Focus mode", RowKind.Toggle(s.isOn(now)) { on ->
            if (on) FocusMode.start(app) else FocusMode.stop(app)
            state.setSubtitle(FocusModel.status(FocusMode.state(app), System.currentTimeMillis()))
        }, subtitle = FocusModel.status(s, now), iconRes = R.drawable.ic_qa_focus)
        page.row(LENGTH, "Turn off after", RowKind.Choice(FocusModel.LENGTHS.map { FocusModel.lengthLabel(it) }, FocusMode.lengthIndex(app)) { i ->
            FocusMode.setLengthIndex(app, i)
            // A running Focus restarts with the new length.
            if (FocusMode.isOn(app)) FocusMode.start(app)
            state.setSubtitle(FocusModel.status(FocusMode.state(app), System.currentTimeMillis()))
        }, subtitle = "Also used by the Spotlight action", iconRes = R.drawable.ic_set_clock)
        page.row(QUIETS, "While it is on", RowKind.Info(), subtitle = FocusModel.QUIETS, iconRes = R.drawable.ic_set_about)
        return page
    }
}
