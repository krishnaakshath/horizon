package dev.tvlauncher.switcher

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
import dev.tvlauncher.insights.UsageAccess
import dev.tvlauncher.setup.SetupStatus

/** Settings > System > Recent apps: how the strip opens, how many apps it shows, its access. */
object SwitcherSection : SettingsSection, SpecifiedPage {
    const val ID = "recent_apps"
    override val id = ID
    override val title = SwitcherText.TITLE
    override val iconRes = R.drawable.ic_qa_recents
    override val group = SettingsGroup.SYSTEM
    override val description = "Jump back to the apps you used last."

    const val DOUBLE_HOME = "switcher.double_home"
    const val PAD_Y = "switcher.pad_y"
    const val COUNT = "switcher.count"
    const val ACCESS = "switcher.access"
    const val FORGET = "switcher.forget"

    override val spec = PageSpec(ID, listOf(DOUBLE_HOME, PAD_Y, COUNT, ACCESS, FORGET))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val app = context.applicationContext
        val store = SwitcherStore(app)
        val page = SettingsPage(context, nav.style, spec)
        page.row(DOUBLE_HOME, "Press Home twice", RowKind.Toggle(store.doubleHome) { store.doubleHome = it },
            subtitle = "Two quick Home presses show your recent apps", iconRes = R.drawable.ic_set_home)
        page.row(PAD_Y, "Controller Y button", RowKind.Choice(listOf("Spotlight", SwitcherText.TITLE), if (store.padY) 1 else 0) {
            store.padY = it == 1
        }, subtitle = "Start always opens Spotlight", iconRes = R.drawable.ic_dev_controller)
        val counts = RecentsOrder.COUNTS
        page.row(COUNT, "How many apps", RowKind.Choice(counts.map { "$it" }, counts.indexOf(store.count).coerceAtLeast(0)) { i ->
            store.count = counts[i]
        }, iconRes = R.drawable.ic_set_tiles)
        val access = UsageAccess.hasAccess(app)
        page.row(ACCESS, "Usage access", if (access) RowKind.Info("Allowed") else RowKind.Action("Not allowed") {
            if (!SetupStatus.openPermissions(context)) nav.notify("Setup could not be opened")
        }, subtitle = if (access) "The list follows every app you use" else "Without it the list shows apps opened from Home",
            iconRes = R.drawable.ic_set_eye)
        page.row(FORGET, "Clear the list", RowKind.Action(chevron = false) {
            store.forget()
            nav.notify("The list starts fresh")
        }, subtitle = "Apps you open next show up again", iconRes = R.drawable.ic_set_reset)
        return page
    }
}
