package dev.tvlauncher.apps

import dev.tvlauncher.settings.LauncherSettings

/**
 * Hides OEM/setup clutter by default and puts well-known streaming apps first.
 * A default-hidden package is shown again once the user has placed it (favorites or saved
 * order); [unhideAll] does that for every installed default-hidden package.
 */
object AppFilter {
    val DEFAULT_HIDDEN: Set<String> = setOf(
        "com.tcl.initsetup",
        "com.tcl.copydatatotv",
        "com.google.android.tungsten.setupwraith",
        "com.tcl.factory.view",
        "com.tcl.autopair",
        "com.tcl.micmanager",
        "com.tcl.useragreement",
        "com.android.htmlviewer",
        "com.google.android.apps.nbu.smartconnect.tv",
        "com.google.android.partnersetup",
        "com.android.settings.intelligence"
        // com.tcl.ui_mediaCenter deliberately stays visible: media players are never hidden.
    )

    val PREFERRED: List<String> = listOf(
        "com.netflix.ninja",
        "com.google.android.youtube.tv",
        "com.amazon.amazonvideo.livingroom",
        "com.apple.atve.androidtv.appletv",
        "com.disney.disneyplus",
        "in.startv.hotstar",
        "com.spotify.tv.android",
        "com.plexapp.android",
        "com.android.vending"
    )

    fun isDefaultHidden(pkg: String, s: LauncherSettings) =
        pkg in DEFAULT_HIDDEN && pkg !in s.favorites && pkg !in s.order

    fun arrange(apps: List<AppEntry>, s: LauncherSettings): List<AppEntry> =
        AppOrdering.arrange(apps.filterNot { isDefaultHidden(it.packageName, s) }, s, PREFERRED)

    /**
     * Clears the user's hidden set and restores installed default-hidden packages by freezing
     * the current [visible] order and appending them at its end.
     */
    fun unhideAll(s: LauncherSettings, visible: List<String>, installed: List<AppEntry>): LauncherSettings {
        val restore = installed.map { it.packageName }.filter { isDefaultHidden(it, s) }.distinct()
        if (restore.isEmpty()) return s.copy(hidden = emptySet())
        val order = (visible + restore).distinct()
        return s.copy(hidden = emptySet(), order = order)
    }

    /**
     * What Spotlight searches: the [shown] shelf (in shelf order) followed by apps the user hid.
     * Default-hidden setup clutter is never searchable.
     */
    fun searchable(all: List<AppEntry>, shown: List<AppEntry>, s: LauncherSettings): List<AppEntry> {
        val shownPkgs = shown.mapTo(HashSet()) { it.packageName }
        return shown + all.filter { it.packageName !in shownPkgs && !isDefaultHidden(it.packageName, s) }
    }
}
