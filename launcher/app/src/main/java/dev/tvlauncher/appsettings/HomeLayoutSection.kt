package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppRepository
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.ui.BouncingDots
import dev.tvlauncher.ui.dp

/**
 * Favorites and hidden apps (each a sub-page with its own list) and a guarded layout reset.
 * Installed apps load off the main thread.
 */
object HomeLayoutSection : SettingsSection, SpecifiedPage {
    const val ID = "home_layout"
    override val id = ID
    override val title = "Home layout"
    override val iconRes = R.drawable.ic_set_home
    override val group = SettingsGroup.HOME
    override val description = "What the home row shows. Long-press OK on a tile for more."
    override val spec = PageSpec(ID, listOf("home.favorites", "home.hidden", "home.reset"))

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val prefs = Prefs(context.applicationContext)
        val s = prefs.load()
        page.row("home.favorites", "Favorites", RowKind.Action(count(s.favorites.size)) {
            nav.push("Favorites") { c, n -> favoritesPage(c, n) }
        }, subtitle = "Sit first on the home row", iconRes = R.drawable.ic_set_star)
        val hiddenRow = page.row("home.hidden", "Hidden apps", RowKind.Action("…") {
            nav.push("Hidden apps") { c, n -> hiddenPage(c, n) }
        }, subtitle = "Not shown on the home row", iconRes = R.drawable.ic_set_eye)
        lateinit var resetRow: SettingsRow
        resetRow = page.row("home.reset", "Reset layout", RowKind.Action(chevron = false) {
            showConfirm(context, page, nav, prefs, resetRow)
        }, subtitle = "Clears favorites, your order and hidden apps", iconRes = R.drawable.ic_set_reset)
        nav.background({ AppRepository(context.applicationContext).load() }) { apps ->
            hiddenRow.setValue(count(HomeLayoutEdits.hiddenApps(prefs.load(), apps).size))
        }
        return page
    }

    private fun count(n: Int) = if (n == 0) "None" else n.toString()

    /** Loads installed apps off the main thread, then fills [page] with [fill]. */
    private fun loading(context: Context, nav: SettingsNav, fill: (SettingsPage, List<AppEntry>) -> Unit): View {
        val page = SettingsPage(context, nav.style)
        val dots = BouncingDots(context)
        page.column.addView(dots, LinearLayout.LayoutParams(-2, -2).apply {
            topMargin = context.dp(24); marginStart = context.dp(8)
        })
        nav.background({ AppRepository(context.applicationContext).load() }) { apps ->
            page.column.removeView(dots)
            fill(page, apps)
            nav.contentReady()
        }
        return page
    }

    private fun favoritesPage(context: Context, nav: SettingsNav): View = loading(context, nav) { page, apps ->
        val prefs = Prefs(context.applicationContext)
        val s = prefs.load()
        val labels = apps.associate { it.packageName to it.label }
        if (s.favorites.isEmpty()) {
            page.row("home.fav.none", "No favorites yet", RowKind.Info(),
                subtitle = "Long-press OK on a tile and choose Favorite", iconRes = R.drawable.ic_set_star)
        }
        for (pkg in s.favorites) {
            val label = labels[pkg] ?: pkg
            page.row("home.fav.$pkg", label, RowKind.Action("Remove", chevron = false) {
                prefs.save(HomeLayoutEdits.removeFavorite(prefs.load(), pkg))
                nav.notify("$label is no longer a favorite")
                nav.refresh()
            }, subtitle = if (pkg in labels) null else "App removed", iconRes = R.drawable.ic_set_star)
        }
    }

    private fun hiddenPage(context: Context, nav: SettingsNav): View = loading(context, nav) { page, apps ->
        val prefs = Prefs(context.applicationContext)
        val s = prefs.load()
        val hidden = HomeLayoutEdits.hiddenApps(s, apps)
        if (hidden.isEmpty()) {
            page.row("home.hidden.none", "Nothing is hidden", RowKind.Info(),
                subtitle = "Every installed app is on the home row", iconRes = R.drawable.ic_set_eye)
        }
        if (hidden.size > 1) {
            page.row("home.hidden.all", "Unhide all", RowKind.Action("${hidden.size} apps", chevron = false) {
                pinGate(context, nav) {
                    prefs.save(HomeLayoutEdits.unhideAll(prefs.load(), apps))
                    nav.notify("All apps are back on the home row")
                    nav.refresh()
                }
            }, iconRes = R.drawable.ic_set_eye)
        }
        for (app in hidden) {
            val pkg = app.packageName
            page.row("home.hidden.$pkg", app.label, RowKind.Action("Unhide", chevron = false) {
                pinGate(context, nav) {
                    prefs.save(HomeLayoutEdits.unhide(prefs.load(), pkg, apps))
                    nav.notify("${app.label} is back on the home row")
                    nav.refresh()
                }
            }, subtitle = if (pkg in s.hidden) null else "Setup app, hidden by default", iconRes = R.drawable.ic_set_eye)
        }
    }

    /** Settings > Parental PIN, when set, guards bringing hidden apps back. */
    private fun pinGate(context: Context, nav: SettingsNav, action: () -> Unit) =
        dev.tvlauncher.system.PinGate.run(context, (context as? android.app.Activity)?.findViewById(android.R.id.content),
            nav.profile.effectsMode == dev.tvlauncher.device.EffectsMode.LITE, action)

    /** Two rows under Reset: confirm (destructive) and cancel; focus lands on cancel. */
    private fun showConfirm(context: Context, page: SettingsPage, nav: SettingsNav, prefs: Prefs, anchor: SettingsRow) {
        if (page.findViewWithTag<View>("home.reset.confirm") != null) return
        val at = page.column.indexOfChild(anchor) + 1
        lateinit var confirm: SettingsRow
        lateinit var cancel: SettingsRow
        confirm = SettingsRow(context, nav.style, "home.reset.confirm", "Yes, reset the home row",
            "Setup apps hidden by default stay hidden", R.drawable.ic_set_check,
            RowKind.Action(chevron = false) {
                prefs.save(HomeLayoutEdits.reset(prefs.load()))
                nav.notify("Home layout reset")
                nav.refresh()
            })
        cancel = SettingsRow(context, nav.style, "home.reset.cancel", "Cancel", null, 0,
            RowKind.Action(chevron = false) {
                anchor.requestFocus()
                page.column.removeView(confirm)
                page.column.removeView(cancel)
            })
        val lp = { LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(SettingsLayout.ROW_GAP_DP) } }
        page.column.addView(confirm, at, lp())
        page.column.addView(cancel, at + 1, lp())
        cancel.requestFocus()
    }
}
