package dev.tvlauncher.appsettings

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppFilter
import dev.tvlauncher.settings.LauncherSettings

/** Pure transforms behind the Home layout page (JVM-tested). */
object HomeLayoutEdits {
    fun removeFavorite(s: LauncherSettings, pkg: String): LauncherSettings =
        s.copy(favorites = s.favorites - pkg)

    /** Installed apps the home row does not show: user-hidden first, then default-hidden setup apps. */
    fun hiddenApps(s: LauncherSettings, installed: List<AppEntry>): List<AppEntry> {
        val user = installed.filter { it.packageName in s.hidden }
        val byDefault = installed.filter { it.packageName !in s.hidden && AppFilter.isDefaultHidden(it.packageName, s) }
        return (user + byDefault).distinctBy { it.packageName }
    }

    /**
     * Shows [pkg] again. A user-hidden app just leaves the hidden set; a default-hidden one is
     * placed by freezing the current visible order and appending it (as [AppFilter.unhideAll] does).
     */
    fun unhide(s: LauncherSettings, pkg: String, installed: List<AppEntry>): LauncherSettings {
        val shown = s.copy(hidden = s.hidden - pkg)
        if (!AppFilter.isDefaultHidden(pkg, shown)) return shown
        val visible = AppFilter.arrange(installed, shown).map { it.packageName }
        return shown.copy(order = (visible + pkg).distinct())
    }

    fun unhideAll(s: LauncherSettings, installed: List<AppEntry>): LauncherSettings =
        AppFilter.unhideAll(s, AppFilter.arrange(installed, s).map { it.packageName }, installed)

    /** Clears favorites, order and hidden apps; keeps everything else (theme, version). */
    fun reset(s: LauncherSettings): LauncherSettings =
        s.copy(favorites = emptyList(), order = emptyList(), hidden = emptySet())
}
