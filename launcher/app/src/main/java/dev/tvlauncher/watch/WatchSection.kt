package dev.tvlauncher.watch

import android.app.Activity
import android.content.Context
import android.os.Build
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

/** Row keys of Settings > Home > Continue watching (seven primary rows, nothing in Advanced). */
object WatchPage {
    const val ID = "continue_watching"
    const val SHOW = "watch.show"
    const val SCOPE = "watch.scope"
    const val POSTERS = "watch.posters"
    const val SUGGESTIONS = "watch.suggestions"
    const val LONG_PRESS = "watch.longPress"
    const val HIDDEN = "watch.hidden"
    const val ACCESS = "watch.access"
    val SPEC = PageSpec(ID, listOf(SHOW, SCOPE, POSTERS, SUGGESTIONS, LONG_PRESS, HIDDEN, ACCESS))
}

/**
 * Settings > Home > Continue watching: the row on the home screen, which cards it shows, posters,
 * app suggestions, long-press hiding and its hidden titles, and whether this TV lets Home read
 * what apps share (OK asks for the TV's permission when it has not been given yet).
 */
object WatchSection : SettingsSection, SpecifiedPage {
    override val id = WatchPage.ID
    override val title = "Continue watching"
    override val iconRes = R.drawable.ic_np_play
    override val group = SettingsGroup.HOME
    override val spec = WatchPage.SPEC
    override val description = "Hold focus on an app to see what you were watching in it"

    fun register(r: SettingsRegistry = SettingsRegistry.shared) = r.register(this)

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val prefs = WatchPrefs(context)
        val s = prefs.load()
        page.row(WatchPage.SHOW, "Show on Home", RowKind.Toggle(s.showRow) { on ->
            prefs.update { it.copy(showRow = on) }
        }, subtitle = "Replaces the big app name after a short pause on a tile", iconRes = R.drawable.ic_np_play)
        val scopes = WatchScope.values().toList()
        page.row(WatchPage.SCOPE, "Cards", RowKind.Choice(scopes.map { it.label }, scopes.indexOf(s.scope)) { i ->
            prefs.update { it.copy(scope = scopes[i]) }
        }, subtitle = "Only the focused app's titles, or every app's together", iconRes = R.drawable.ic_set_tiles)
        page.row(WatchPage.POSTERS, "Show posters", RowKind.Toggle(s.showPosters) { on ->
            prefs.update { it.copy(showPosters = on) }
        }, subtitle = "Off: plain cards in the app's colour use less memory", iconRes = R.drawable.ic_set_appearance)
        page.row(WatchPage.SUGGESTIONS, "Include app suggestions", RowKind.Toggle(s.includeSuggestions) { on ->
            prefs.update { it.copy(includeSuggestions = on) }
        }, subtitle = "Titles apps recommend, after the ones you started", iconRes = R.drawable.ic_set_star)
        page.row(WatchPage.LONG_PRESS, "Hide with a long press", RowKind.Toggle(s.longPressHides) { on ->
            prefs.update { it.copy(longPressHides = on) }
        }, subtitle = "Long-press OK on a card to remove it from the row", iconRes = R.drawable.ic_visibility_off)
        page.row(WatchPage.HIDDEN, "Hidden titles", RowKind.Action(WatchCopy.hiddenValue(s.blocked.size), chevron = false) {
            if (prefs.load().blocked.isEmpty()) {
                nav.notify("Nothing is hidden")
            } else {
                prefs.update { it.unblockAll() }
                nav.notify("Hidden titles are back")
                nav.refresh()
            }
        }, subtitle = if (s.blocked.isEmpty()) "Titles you hide are listed here" else "OK shows them all again", iconRes = R.drawable.ic_set_eye)
        val access = page.row(WatchPage.ACCESS, "Access", RowKind.Action("…", chevron = false) {
            askPermission(context, page, nav)
        }, subtitle = "Checking…", iconRes = R.drawable.ic_set_shield)
        nav.background({ WatchReader(context).read(includePreview = false).access }) { a ->
            access.setValue(WatchCopy.accessValue(a))
            access.setSubtitle(WatchCopy.accessSubtitle(a))
        }
        return page
    }

    /** Asks the TV for the program-information permission, then re-checks for a while. */
    private fun askPermission(context: Context, page: SettingsPage, nav: SettingsNav) {
        val reader = WatchReader(context)
        if (Build.VERSION.SDK_INT < 26 || !reader.providerPresent()) { nav.notify("This TV doesn't share watched titles"); return }
        if (reader.permissionGranted()) { nav.refreshCurrent(); return }
        val a = context as? Activity ?: return
        try {
            a.requestPermissions(arrayOf(WatchReader.PERMISSION), REQUEST_CODE)
        } catch (e: RuntimeException) {
            nav.notify("This TV doesn't allow it")
            return
        }
        var tries = 0
        val check = object : Runnable {
            override fun run() {
                if (!page.isAttachedToWindow) return
                if (reader.permissionGranted()) { nav.refreshCurrent(); return }
                if (++tries < POLL_TRIES) page.postDelayed(this, POLL_MS)
            }
        }
        page.postDelayed(check, POLL_MS)
    }

    private const val REQUEST_CODE = 4107
    private const val POLL_MS = 1000L
    private const val POLL_TRIES = 60
}
