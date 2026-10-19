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
    fun show(app: AppEntry, current: List<String>) {
        val s = prefs.load()
        val pkg = app.packageName
        val fav = if (pkg in s.favorites) "Remove from favorites" else "Add to favorites"
        val entries = listOf<Pair<String, () -> Unit>>(
            fav to { edit(SettingsEdits.toggleFavorite(s, pkg, current)) },
            "Move left" to { edit(SettingsEdits.moveLeft(s, pkg, current)) },
            "Move right" to { edit(SettingsEdits.moveRight(s, pkg, current)) },
            "Hide" to { edit(SettingsEdits.hide(s, pkg, current)) },
            "App info" to {
                SystemActions.start(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
            },
            "Uninstall" to { SystemActions.start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg"))) }
        )
        AlertDialog.Builder(context).setTitle(app.label)
            .setItems(entries.map { it.first }.toTypedArray()) { _, i -> entries[i].second() }
            .show()
    }

    fun showDockMenu() {
        AlertDialog.Builder(context).setItems(arrayOf("Unhide all apps")) { _, _ ->
            edit(prefs.load().copy(hidden = emptySet()))
        }.show()
    }

    private fun edit(s: LauncherSettings) {
        prefs.save(s)
        onChanged()
    }
}
