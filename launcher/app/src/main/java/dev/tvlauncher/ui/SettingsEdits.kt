package dev.tvlauncher.ui

import dev.tvlauncher.settings.LauncherSettings

/** Pure transforms behind the tile menu. [current] is the visible, ordered package list. */
object SettingsEdits {
    fun toggleFavorite(s: LauncherSettings, pkg: String, current: List<String>): LauncherSettings =
        if (pkg in s.favorites) s.copy(favorites = s.favorites - pkg)
        else s.copy(favorites = s.favorites + pkg)

    fun moveLeft(s: LauncherSettings, pkg: String, current: List<String>) = move(s, pkg, current, -1)

    fun moveRight(s: LauncherSettings, pkg: String, current: List<String>) = move(s, pkg, current, 1)

    fun hide(s: LauncherSettings, pkg: String, current: List<String>): LauncherSettings =
        s.copy(hidden = s.hidden + pkg, favorites = s.favorites - pkg, order = s.order - pkg)

    private fun move(s: LauncherSettings, pkg: String, current: List<String>, delta: Int): LauncherSettings {
        val i = current.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in current.indices) return s
        val order = current.toMutableList()
        order[i] = order[j].also { order[j] = order[i] }
        // Favorites are shown first, so keep their relative order consistent with the new layout.
        val favs = s.favorites.sortedBy { f -> order.indexOf(f).let { x -> if (x < 0) Int.MAX_VALUE else x } }
        return s.copy(order = order, favorites = favs)
    }
}
