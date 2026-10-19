package dev.tvlauncher.appsettings

import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.settings.LauncherSettings
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutEditsTest {
    private fun app(p: String) = AppEntry(p, p.substringAfterLast('.'), "$p.Main")
    private val installed = listOf(app("com.netflix.ninja"), app("com.x.user"), app("com.tcl.initsetup"))

    @Test
    fun removeFavorite_dropsOnlyThatPackage() {
        val s = LauncherSettings(favorites = listOf("a", "b"), order = listOf("a", "b"))
        assertEquals(LauncherSettings(favorites = listOf("b"), order = listOf("a", "b")), HomeLayoutEdits.removeFavorite(s, "a"))
    }

    @Test
    fun hiddenApps_listsUserAndDefaultHiddenInstalled() {
        val s = LauncherSettings(hidden = setOf("com.x.user", "com.gone"))
        assertEquals(listOf("com.x.user", "com.tcl.initsetup"), HomeLayoutEdits.hiddenApps(s, installed).map { it.packageName })
    }

    @Test
    fun unhide_userHidden_removesFromHidden() {
        val s = LauncherSettings(hidden = setOf("com.x.user"))
        assertEquals(emptySet<String>(), HomeLayoutEdits.unhide(s, "com.x.user", installed).hidden)
    }

    @Test
    fun unhide_defaultHidden_isPlacedAtEndOfOrder() {
        val s = LauncherSettings()
        val out = HomeLayoutEdits.unhide(s, "com.tcl.initsetup", installed)
        assertEquals("com.tcl.initsetup", out.order.last())
        assertEquals(listOf("com.netflix.ninja", "com.x.user", "com.tcl.initsetup"), out.order)
    }

    @Test
    fun reset_clearsLayoutButKeepsTheme() {
        val s = LauncherSettings(favorites = listOf("a"), order = listOf("a"), hidden = setOf("b"), theme = "midnight")
        assertEquals(LauncherSettings(theme = "midnight"), HomeLayoutEdits.reset(s))
    }
}
