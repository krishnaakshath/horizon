package dev.tvlauncher.ui

import dev.tvlauncher.R
import dev.tvlauncher.panel.PanelEntry
import dev.tvlauncher.settings.LauncherSettings

/** Pure entries of the tile options menu, in display order. */
object TileMenuModel {
    const val FAVORITE = "favorite"
    const val MOVE_LEFT = "left"
    const val MOVE_RIGHT = "right"
    const val HIDE = "hide"
    const val INFO = "info"
    const val UNINSTALL = "uninstall"
    const val UNHIDE_ALL = "unhide"

    /** Favorite, Move left/right (only when possible), Hide, App info, Uninstall last. */
    fun entries(s: LauncherSettings, pkg: String, current: List<String>): List<PanelEntry.Row> {
        val fav = pkg in s.favorites
        val rows = ArrayList<PanelEntry.Row>()
        rows += PanelEntry.Row(FAVORITE, if (fav) R.drawable.ic_star else R.drawable.ic_star_border,
            if (fav) "Remove from favorites" else "Add to favorites")
        if (SettingsEdits.canMoveLeft(s, pkg, current)) rows += PanelEntry.Row(MOVE_LEFT, R.drawable.ic_set_chevron_left, "Move left")
        if (SettingsEdits.canMoveRight(s, pkg, current)) rows += PanelEntry.Row(MOVE_RIGHT, R.drawable.ic_set_chevron, "Move right")
        rows += PanelEntry.Row(HIDE, R.drawable.ic_visibility_off, "Hide from home")
        rows += PanelEntry.Row(INFO, R.drawable.ic_info, "App info")
        rows += PanelEntry.Row(UNINSTALL, R.drawable.ic_net_close, "Uninstall")
        return rows
    }

    fun dockEntries(): List<PanelEntry.Row> = listOf(PanelEntry.Row(UNHIDE_ALL, R.drawable.ic_set_eye, "Unhide all apps"))
}
