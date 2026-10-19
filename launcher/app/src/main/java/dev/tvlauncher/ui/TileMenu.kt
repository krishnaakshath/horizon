package dev.tvlauncher.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import dev.tvlauncher.R
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppFilter
import dev.tvlauncher.panel.PanelContent
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.panel.PanelHeader
import dev.tvlauncher.panel.PanelShell
import dev.tvlauncher.settings.LauncherSettings
import dev.tvlauncher.settings.Prefs

/**
 * Long-press menu for a tile, as a centred panel in the shared anatomy ([PanelShell]) instead of a
 * system dialog: app icon and name, then [TileMenuModel] rows. Updates [Prefs] and calls
 * [onChanged] so the shelf reloads. The menu handles BACK itself and returns focus to the view
 * that had it when the menu opened.
 */
class TileMenu(private val context: Context, private val prefs: Prefs, private val onChanged: () -> Unit) {
    private val shell = PanelShell(context, lite = false, anchor = PanelShell.Anchor.CENTER)
    private var actions: Map<String, () -> Unit> = emptyMap()
    private var opener: View? = null

    /** Feedback hooks (sounds): an entry was picked / the menu was dismissed with Back. */
    var onPicked: () -> Unit = {}
    var onCancelled: () -> Unit = {}

    init {
        shell.onBackKey = { onCancelled(); close() }
        shell.onRowClick = { row ->
            val run = actions[row.key]
            onPicked()
            close()
            run?.invoke()
        }
    }

    /** True while the menu is on screen (MENU/SEARCH then leave Spotlight alone). */
    val isVisible: Boolean get() = shell.isVisible

    /** Closes the menu at once (no animation); used when the activity goes away. */
    fun dismiss() {
        if (!shell.isVisible) return
        shell.release()
        restoreFocus()
    }

    fun show(app: AppEntry, current: List<String>) {
        val s = prefs.load()
        val pkg = app.packageName
        actions = mapOf(
            TileMenuModel.FAVORITE to { toggleFavorite(app, current) },
            TileMenuModel.MOVE_LEFT to { edit(SettingsEdits.moveLeft(prefs.load(), pkg, current)) },
            TileMenuModel.MOVE_RIGHT to { edit(SettingsEdits.moveRight(prefs.load(), pkg, current)) },
            TileMenuModel.HIDE to { hide(app, current) },
            TileMenuModel.INFO to { SystemActions.appInfo(context, pkg); Unit },
            TileMenuModel.UNINSTALL to { SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))); Unit }
        )
        open(PanelContent(PanelHeader(R.drawable.ic_more, app.label, "App options"), TileMenuModel.entries(s, pkg, current)))
        appIcon(pkg)?.let { shell.header.setIcon(it) }
    }

    fun isFavorite(app: AppEntry) = app.packageName in prefs.load().favorites

    /** Shared with the quick-strip so both paths edit and reload identically. */
    fun toggleFavorite(app: AppEntry, current: List<String>) =
        edit(SettingsEdits.toggleFavorite(prefs.load(), app.packageName, current))

    fun hide(app: AppEntry, current: List<String>) = edit(SettingsEdits.hide(prefs.load(), app.packageName, current))

    /** Quick-strip buttons: the same edits and reload as the menu entries. */
    fun stripAction(action: QuickStrip.Action, app: AppEntry, current: List<String>) {
        when (action) {
            QuickStrip.Action.FAVORITE -> toggleFavorite(app, current)
            QuickStrip.Action.HIDE -> hide(app, current)
            QuickStrip.Action.INFO -> SystemActions.appInfo(context, app.packageName)
            QuickStrip.Action.MORE -> show(app, current)
        }
    }

    /** [visible] is the current shelf order; [installed] every launchable app (for restoring clutter). */
    fun showDockMenu(visible: List<String>, installed: List<AppEntry>) {
        actions = mapOf(TileMenuModel.UNHIDE_ALL to { edit(AppFilter.unhideAll(prefs.load(), visible, installed)) })
        open(PanelContent(PanelHeader(R.drawable.ic_set_home, "Home screen", "Apps on the home row"), TileMenuModel.dockEntries()))
    }

    private fun open(content: PanelContent) {
        val host = (context as? Activity)?.findViewById<ViewGroup>(android.R.id.content) ?: return
        if (shell.isVisible) shell.release() else opener = (context as? Activity)?.currentFocus
        shell.show(host)
        shell.render(content)
    }

    private fun close() {
        shell.dismiss()
        restoreFocus()
    }

    private fun restoreFocus() {
        val v = opener
        opener = null
        v?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }?.requestFocus()
    }

    private fun appIcon(pkg: String) = try {
        context.packageManager.getApplicationIcon(pkg)
    } catch (e: Exception) { null }

    private fun edit(s: LauncherSettings) {
        prefs.save(s)
        onChanged()
    }
}
