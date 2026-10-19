package dev.tvlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppFilter
import dev.tvlauncher.settings.LauncherSettings
import dev.tvlauncher.settings.Prefs

/** Long-press menu for a tile; updates [Prefs] and calls [onChanged] so the shelf reloads. */
class TileMenu(private val context: Context, private val prefs: Prefs, private val onChanged: () -> Unit) {
    private var dialog: AlertDialog? = null

    fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    fun show(app: AppEntry, current: List<String>) {
        dismiss()
        val s = prefs.load()
        val pkg = app.packageName
        val fav = if (pkg in s.favorites) "Remove from favorites" else "Add to favorites"
        val entries = ArrayList<Pair<String, () -> Unit>>()
        entries += fav to { toggleFavorite(app, current) }
        if (SettingsEdits.canMoveLeft(s, pkg, current)) {
            entries += "Move left" to { edit(SettingsEdits.moveLeft(prefs.load(), pkg, current)) }
        }
        if (SettingsEdits.canMoveRight(s, pkg, current)) {
            entries += "Move right" to { edit(SettingsEdits.moveRight(prefs.load(), pkg, current)) }
        }
        entries += "Hide" to { hide(app, current) }
        entries += "App info" to { SystemActions.appInfo(context, pkg) }
        entries += "Uninstall" to { SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
        dialog = AlertDialog.Builder(context).setTitle(app.label)
            .setItems(entries.map { it.first }.toTypedArray()) { _, i -> entries[i].second() }
            .show()
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
        dismiss()
        dialog = AlertDialog.Builder(context).setItems(arrayOf("Unhide all apps")) { _, _ ->
            edit(AppFilter.unhideAll(prefs.load(), visible, installed))
        }.show()
    }

    private fun edit(s: LauncherSettings) {
        prefs.save(s)
        onChanged()
    }
}
