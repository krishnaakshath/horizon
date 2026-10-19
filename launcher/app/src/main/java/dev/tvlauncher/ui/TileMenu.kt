package dev.tvlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.tvlauncher.apps.AppEntry
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
        entries += fav to { edit(SettingsEdits.toggleFavorite(prefs.load(), pkg, current)) }
        if (SettingsEdits.canMoveLeft(s, pkg, current)) {
            entries += "Move left" to { edit(SettingsEdits.moveLeft(prefs.load(), pkg, current)) }
        }
        if (SettingsEdits.canMoveRight(s, pkg, current)) {
            entries += "Move right" to { edit(SettingsEdits.moveRight(prefs.load(), pkg, current)) }
        }
        entries += "Hide" to { edit(SettingsEdits.hide(prefs.load(), pkg, current)) }
        entries += "App info" to {
            SystemActions.start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
        }
        entries += "Uninstall" to { SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
        dialog = AlertDialog.Builder(context).setTitle(app.label)
            .setItems(entries.map { it.first }.toTypedArray()) { _, i -> entries[i].second() }
            .show()
    }

    fun showDockMenu() {
        dismiss()
        dialog = AlertDialog.Builder(context).setItems(arrayOf("Unhide all apps")) { _, _ ->
            edit(prefs.load().copy(hidden = emptySet()))
        }.show()
    }

    private fun edit(s: LauncherSettings) {
        prefs.save(s)
        onChanged()
    }
}
