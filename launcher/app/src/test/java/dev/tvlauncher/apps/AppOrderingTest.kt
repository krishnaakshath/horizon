package dev.tvlauncher.apps

import dev.tvlauncher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class AppOrderingTest {
    private fun app(pkg: String, label: String = pkg) = AppEntry(pkg, label, "$pkg.Main")
    private fun pkgs(l: List<AppEntry>) = l.map { it.packageName }

    @Test
    fun favorites_firstInSavedOrder() {
        val apps = listOf(app("a"), app("b"), app("c"), app("d"))
        val s = LauncherSettings(favorites = listOf("c", "a"), order = listOf("d"))
        assertEquals(listOf("c", "a", "d", "b"), pkgs(AppOrdering.arrange(apps, s)))
    }

    @Test
    fun hidden_removed() {
        val apps = listOf(app("a"), app("b"), app("c"))
        val s = LauncherSettings(favorites = listOf("a"), order = listOf("b"), hidden = setOf("a", "c"))
        assertEquals(listOf("b"), pkgs(AppOrdering.arrange(apps, s)))
    }

    @Test
    fun settingsNamingUninstalledApp_ignored() {
        val apps = listOf(app("a"), app("b"))
        val s = LauncherSettings(favorites = listOf("ghost", "b"), order = listOf("ghost2", "a"))
        assertEquals(listOf("b", "a"), pkgs(AppOrdering.arrange(apps, s)))
    }

    @Test
    fun emptyApps_returnsEmptyList() {
        val s = LauncherSettings(favorites = listOf("a"))
        assertEquals(emptyList<AppEntry>(), AppOrdering.arrange(emptyList(), s))
    }

    @Test
    fun newApp_notInSettings_appendedAlphabetically() {
        val apps = listOf(app("z", "Zeta"), app("m", "mango"), app("k", "Kiwi"), app("o", "Old"))
        val s = LauncherSettings(order = listOf("o"))
        assertEquals(listOf("o", "k", "m", "z"), pkgs(AppOrdering.arrange(apps, s)))
    }
}
