package dev.tvlauncher.spotlight

import android.content.Context
import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.View
import android.widget.LinearLayout
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsLayout
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRow
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.ui.QuickActions
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.dp

/** Row keys of Settings > Home > Spotlight (seven primary rows, nothing in Advanced). */
object SpotlightPage {
    const val ID = "spotlight"
    const val ACTIONS = "spotlight.actions"
    const val KEYS = "spotlight.keys"
    const val SCOPE = "spotlight.scope"
    const val HINTS = "spotlight.hints"
    const val CLOSE_AFTER = "spotlight.closeAfter"
    const val ANY_APP = "spotlight.anyApp"
    const val RESET = "spotlight.reset"
    val SPEC = PageSpec(ID, listOf(ACTIONS, KEYS, SCOPE, HINTS, CLOSE_AFTER, ANY_APP, RESET))

    /** Choice index -> [DoublePress]; Extra key is offered only while one is set. */
    fun doubleOptions(c: SpotlightConfig): List<DoublePress> =
        if (c.extraKey != 0) DoublePress.values().toList() else DoublePress.values().filter { it != DoublePress.EXTRA }

    fun doubleLabels(c: SpotlightConfig, keyName: (Int) -> String): List<String> =
        doubleOptions(c).map { if (it == DoublePress.EXTRA) keyName(c.extraKey) else it.label }

    /** A newly captured extra key; a double-press on the old extra key follows it. */
    fun withExtraKey(c: SpotlightConfig, code: Int): SpotlightConfig = c.copy(extraKey = code)

    /** Removing the extra key also turns off its double-press. */
    fun withoutExtraKey(c: SpotlightConfig): SpotlightConfig =
        c.copy(extraKey = 0, doublePress = if (c.doublePress == DoublePress.EXTRA) DoublePress.OFF else c.doublePress)
}

/**
 * Settings > Home > Spotlight: quick actions (which and in what order), the keys that open it,
 * search scope, hints, closing after an action, the "Open from any app" helper status and a
 * guarded reset. Registered from LauncherApp through [dev.tvlauncher.appsettings.SettingsRegistry].
 */
object SpotlightSection : SettingsSection, SpecifiedPage {
    override val id = SpotlightPage.ID
    override val title = "Spotlight"
    override val iconRes = R.drawable.ic_search
    override val group = SettingsGroup.HOME
    override val spec = SpotlightPage.SPEC
    override val description = "The search and quick-actions panel: what it shows and which keys open it"

    fun keyName(code: Int): String = TriggerKeys.name(code, try { KeyEvent.keyCodeToString(code) } catch (e: RuntimeException) { null })

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val prefs = SpotlightPrefs(context)
        val c = prefs.load()

        page.row(SpotlightPage.ACTIONS, "Quick actions", RowKind.Action(QuickActionEdits.summary(c)) {
            nav.push("Quick actions") { ctx, n -> actionsPage(ctx, n) }
        }, subtitle = "Choose the round buttons and their order", iconRes = R.drawable.ic_set_tiles)
        page.row(SpotlightPage.KEYS, "Keys that open Spotlight", RowKind.Action(TriggerKeys.summary(c, ::keyName)) {
            nav.push("Keys that open Spotlight") { ctx, n -> keysPage(ctx, n) }
        }, subtitle = "Down on the home row always opens it too", iconRes = R.drawable.ic_set_tune)
        val scopes = SearchScope.values().toList()
        page.row(SpotlightPage.SCOPE, "Search", RowKind.Choice(scopes.map { it.label }, scopes.indexOf(c.scope)) { i ->
            prefs.update { it.copy(scope = scopes[i]) }
        }, subtitle = "What typing in Spotlight looks through", iconRes = R.drawable.ic_search)
        page.row(SpotlightPage.HINTS, "Show hints", RowKind.Toggle(c.showHints) { on ->
            prefs.update { it.copy(showHints = on) }
        }, subtitle = "Button names and a short line on how to type", iconRes = R.drawable.ic_info)
        page.row(SpotlightPage.CLOSE_AFTER, "Close after an action", RowKind.Toggle(c.closeAfterAction) { on ->
            prefs.update { it.copy(closeAfterAction = on) }
        }, subtitle = "Off: Spotlight is still open when you come back", iconRes = R.drawable.ic_set_check)

        val status = { helperDisplay(context, prefs) }
        val shown = status()
        page.row(SpotlightPage.ANY_APP, "Open from any app", RowKind.Action(shown.value, chevron = shown.action != HelperStatus.Action.NONE) {
            when (status().action) {
                HelperStatus.Action.OPEN_HELPER -> HelperBridge.helperScreen(context)?.let { SystemActions.start(context, it) }
                HelperStatus.Action.OPEN_KEYS -> nav.push("Keys that open Spotlight") { ctx, n -> keysPage(ctx, n) }
                HelperStatus.Action.NONE -> Unit
            }
        }, subtitle = shown.subtitle, iconRes = R.drawable.ic_set_helper)
        // The helper answers a push with its state; rebuild only when what the row says changes.
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in SpotlightPrefs.HELPER_KEYS && status() != shown) nav.refreshCurrent()
        }
        prefs.sp.registerOnSharedPreferenceChangeListener(listener)
        page.doOnDetach { prefs.sp.unregisterOnSharedPreferenceChangeListener(listener) }
        HelperBridge.push(context, c)

        lateinit var resetRow: SettingsRow
        resetRow = page.row(SpotlightPage.RESET, "Reset to default", RowKind.Action(chevron = false) {
            showConfirm(context, page, nav, prefs, resetRow)
        }, subtitle = "Original buttons, Menu and Search keys, no extra key", iconRes = R.drawable.ic_set_reset)
        return page
    }

    private fun helperDisplay(context: Context, prefs: SpotlightPrefs): HelperStatus.Display {
        val installed = HelperBridge.isInstalled(context)
        return HelperStatus.resolve(
            installed = installed,
            serviceOn = installed && HelperBridge.serviceEnabled(context),
            helperScreen = installed && HelperBridge.helperScreen(context) != null,
            payload = HelperLink.payload(prefs.load()),
            report = prefs.report(),
            keyName = ::keyName
        )
    }

    /** Two rows under Reset: confirm and cancel; focus lands on cancel. */
    private fun showConfirm(context: Context, page: SettingsPage, nav: SettingsNav, prefs: SpotlightPrefs, anchor: SettingsRow) {
        if (page.findViewWithTag<View>(CONFIRM) != null) return
        val at = page.column.indexOfChild(anchor) + 1
        lateinit var confirm: SettingsRow
        lateinit var cancel: SettingsRow
        confirm = SettingsRow(context, nav.style, CONFIRM, "Yes, reset Spotlight", "Your order, keys and choices here are cleared",
            R.drawable.ic_set_check, RowKind.Action(chevron = false) {
                prefs.reset()
                nav.notify("Spotlight reset")
                nav.refresh()
            })
        cancel = SettingsRow(context, nav.style, "spotlight.reset.cancel", "Cancel", null, 0, RowKind.Action(chevron = false) {
            anchor.requestFocus()
            page.column.removeView(confirm)
            page.column.removeView(cancel)
        })
        val lp = { LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(SettingsLayout.ROW_GAP_DP) } }
        page.column.addView(confirm, at, lp())
        page.column.addView(cancel, at + 1, lp())
        cancel.requestFocus()
    }

    private const val CONFIRM = "spotlight.reset.confirm"

    /** One row per action: OK shows or hides it, LEFT / RIGHT move it earlier or later in the row. */
    private fun actionsPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val prefs = SpotlightPrefs(context)
        val c = prefs.load()
        val availability = QuickActions.Availability(
            installedPackages = setOf(QuickActions.BROWSER_PACKAGE, QuickActions.GET_APPS_PACKAGE).filterTo(HashSet()) { installed(context, it) },
            sleepTimer = SystemActions.sleepTimerIntent(context) != null
        )
        page.note("OK shows or hides a button. Left and Right move it in the row.")
        for (item in c.order) {
            val unavailable = QuickActions.unavailableReason(item, availability)
            val row = page.row("qa.${item.name}", item.title, RowKind.Toggle(item !in c.hidden) { on ->
                prefs.update { QuickActionEdits.setShown(it, item, on) }
                nav.refresh()
            }, subtitle = QuickActionEdits.subtitle(c, item, unavailable), iconRes = QuickActions.iconRes(item))
            row.setOnKeyListener { _, code, ev ->
                val delta = when (code) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> -1
                    KeyEvent.KEYCODE_DPAD_RIGHT -> 1
                    else -> return@setOnKeyListener false
                }
                if (ev.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
                val moved = QuickActionEdits.move(prefs.load(), item, delta)
                if (moved == null) {
                    if (ev.repeatCount == 0) nav.notify(if (delta < 0) "${item.title} is already first" else "${item.title} is already last")
                } else {
                    prefs.save(moved)
                    nav.refresh() // focus stays on this action's row (same tag)
                }
                true
            }
        }
        page.row("qa.reset", "Reset buttons", RowKind.Action(chevron = false) {
            prefs.update { QuickActionEdits.resetActions(it) }
            nav.notify("Buttons back to the original row")
            nav.refresh()
        }, subtitle = "Keys and other choices stay as they are", iconRes = R.drawable.ic_set_reset)
        return page
    }

    private fun installed(context: Context, pkg: String): Boolean = try {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: Exception) {
        false
    }

    /** Menu, Search, one captured extra key, the double-press choice and a key tester. */
    private fun keysPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val prefs = SpotlightPrefs(context)
        val c = prefs.load()
        page.row("keys.menu", "Menu key", RowKind.Toggle(c.menuKey) { on ->
            prefs.update { it.copy(menuKey = on) }
            nav.refresh()
        }, subtitle = "One press opens or closes Spotlight", iconRes = R.drawable.ic_more)
        page.row("keys.search", "Search key", RowKind.Toggle(c.searchKey) { on ->
            prefs.update { it.copy(searchKey = on) }
            nav.refresh()
        }, subtitle = "Only if your remote has one; the mic button belongs to the TV", iconRes = R.drawable.ic_search)

        val extraTitle = "Extra key"
        val extraSub = "Also opens Spotlight from other apps with Launcher Helper"
        val extra = page.row("keys.extra", extraTitle, RowKind.Action(if (c.extraKey != 0) keyName(c.extraKey) else "None") {},
            subtitle = extraSub, iconRes = R.drawable.ic_set_tune)
        listenForKey(extra, nav,
            start = { extra.setTitle("Press the key you want"); extra.setValue("…"); extra.setSubtitle("Back cancels") },
            stop = { extra.setTitle(extraTitle); extra.setSubtitle(extraSub) }
        ) { code ->
            val name = keyName(code)
            val problem = TriggerKeys.captureProblem(code, name)
            if (problem != null) {
                nav.notify(problem)
                extra.setValue(prefs.load().extraKey.let { if (it != 0) keyName(it) else "None" })
            } else {
                prefs.update { SpotlightPage.withExtraKey(it, code) }
                nav.notify("$name now opens Spotlight")
                nav.refresh()
            }
        }
        if (c.extraKey != 0) {
            page.row("keys.clear", "Remove extra key", RowKind.Action(chevron = false) {
                prefs.update { SpotlightPage.withoutExtraKey(it) }
                nav.notify("Extra key removed")
                nav.refresh()
            }, subtitle = keyName(c.extraKey), iconRes = R.drawable.ic_set_reset)
        }

        val options = SpotlightPage.doubleOptions(c)
        page.row("keys.double", "Open on double-press", RowKind.Choice(SpotlightPage.doubleLabels(c, ::keyName), options.indexOf(c.doublePress).coerceAtLeast(0)) { i ->
            prefs.update { it.copy(doublePress = options[i]) }
        }, subtitle = "Two quick presses open it; one press keeps its usual job", iconRes = R.drawable.ic_set_motion)

        val testTitle = "Test a key"
        val testSub = "Press it to see whether it opens Spotlight"
        val test = page.row("keys.test", testTitle, RowKind.Action(chevron = false) {}, subtitle = testSub, iconRes = R.drawable.ic_set_check)
        listenForKey(test, nav,
            start = { test.setTitle("Press any key"); test.setSubtitle("Back cancels") },
            stop = { test.setTitle(testTitle); test.setSubtitle(testSub) }
        ) { code -> nav.notify(TriggerKeys.testMessage(code, prefs.load(), keyName(code))) }

        if (TriggerKeys.noKeys(c)) page.note("No key opens Spotlight now. Press Down on the home row to open it.")
        return page
    }

    /**
     * OK on [row] starts listening; the next key press goes to [onKey] (BACK cancels). Both the
     * press and its release are swallowed, so OK / BACK / arrows never act on the page meanwhile.
     */
    private fun listenForKey(row: SettingsRow, nav: SettingsNav, start: () -> Unit, stop: () -> Unit, onKey: (Int) -> Unit) {
        var listening = false
        var swallowUp = 0
        row.setOnClickListener {
            listening = true
            start()
        }
        row.setOnKeyListener { _, code, ev ->
            if (!listening) {
                if (swallowUp == code && ev.action == KeyEvent.ACTION_UP) { swallowUp = 0; return@setOnKeyListener true }
                return@setOnKeyListener false
            }
            if (ev.action != KeyEvent.ACTION_DOWN || ev.repeatCount > 0) return@setOnKeyListener true
            listening = false
            swallowUp = code
            stop()
            if (code == KeyEvent.KEYCODE_BACK) nav.notify("Cancelled") else onKey(code)
            true
        }
        row.setOnFocusChangeListener { _, has -> if (!has && listening) { listening = false; stop() } }
    }
}
