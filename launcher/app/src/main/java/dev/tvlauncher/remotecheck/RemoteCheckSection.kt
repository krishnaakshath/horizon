package dev.tvlauncher.remotecheck

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.ui.dp

/**
 * Settings > System > Remote: a live check of what every remote button sends, so the owner can
 * see that all buttons work and which ones the TV keeps for itself.
 */
object RemoteCheckSection : SettingsSection, SpecifiedPage {
    const val ID = "remote_check"
    override val id = ID
    override val title = "Remote"
    override val iconRes = R.drawable.ic_set_remote
    override val group = SettingsGroup.SYSTEM
    override val description = "See what each button on the remote sends."

    const val CHECK = "remote.check"
    const val SEEN = "remote.seen"
    const val RESET = "remote.reset"

    override val spec = PageSpec(ID, listOf(CHECK, SEEN, RESET))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val m = RemoteCheckSession.model
        page.row(CHECK, "Check the remote", RowKind.Action {
            nav.push("Remote check") { c, n -> livePage(c, n) }
        }, subtitle = "Press each button and watch it light up", iconRes = R.drawable.ic_set_remote)
        page.row(SEEN, "Buttons seen", RowKind.Info(if (m.seenCount() == 0) "None yet" else "${m.seenCount()}"),
            subtitle = if (m.seenCount() == 0) "Start a check to see them here" else m.summary(), iconRes = R.drawable.ic_set_check)
        page.row(RESET, "Start over", RowKind.Action(chevron = false) {
            m.reset()
            nav.refresh()
        }, subtitle = "Clears the buttons seen so far", iconRes = R.drawable.ic_set_reset)
        page.note(RemoteCheckModel.NOTE)
        return page
    }

    private fun livePage(context: Context, nav: SettingsNav): View {
        val box = FrameLayout(context)
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = context.dp(24)
            setPadding(pad, context.dp(8), pad, pad)
        }
        val view = RemoteCheckView(context, nav.style.accentOnDark) { nav.pop() }
        col.addView(view, LinearLayout.LayoutParams(-1, -2))
        box.addView(col, FrameLayout.LayoutParams(-1, -2))
        view.post { view.requestFocus() }
        return box
    }
}
