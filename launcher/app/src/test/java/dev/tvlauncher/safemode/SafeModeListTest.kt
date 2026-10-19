package dev.tvlauncher.safemode

import dev.tvlauncher.apps.AppEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeModeListTest {
    private fun app(pkg: String, label: String) = AppEntry(pkg, label, "$pkg.Main")

    @Test
    fun fixedRowsFirst_thenLeanbackApps_thenOthers() {
        val rows = SafeModeList.build(
            leanback = listOf(app("b.netflix", "Netflix"), app("a.youtube", "YouTube"), app("c.apple", "apple tv")),
            launcher = listOf(app("d.files", "Files"), app("b.netflix", "Netflix phone")),
            ownPackage = "dev.tvlauncher",
            stockLauncherAvailable = false
        )
        assertEquals(SafeModeList.Row.TryNormal, rows[0])
        assertEquals(SafeModeList.Row.SystemSettings, rows[1])
        val apps = rows.filterIsInstance<SafeModeList.Row.App>().map { it.app.label }
        // Leanback apps sorted case-insensitively, then launcher-only apps; duplicates dropped.
        assertEquals(listOf("apple tv", "Netflix", "YouTube", "Files"), apps)
    }

    @Test
    fun ownPackage_isExcluded() {
        val rows = SafeModeList.build(listOf(app("dev.tvlauncher", "TV Launcher"), app("x", "X")), emptyList(), "dev.tvlauncher", false)
        assertEquals(listOf("X"), rows.filterIsInstance<SafeModeList.Row.App>().map { it.app.label })
    }

    @Test
    fun stockLauncherRow_onlyWhenAvailable() {
        val without = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", false)
        assertFalse(SafeModeList.Row.StockLauncher in without)
        val with = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", true)
        assertEquals(listOf(SafeModeList.Row.TryNormal, SafeModeList.Row.SystemSettings, SafeModeList.Row.StockLauncher), with)
    }

    @Test
    fun stockLauncher_neverListedAsAnApp() {
        val rows = SafeModeList.build(listOf(app(SafeModeList.STOCK_LAUNCHER, "Google TV Home")), emptyList(), "dev.tvlauncher", true)
        assertTrue(rows.filterIsInstance<SafeModeList.Row.App>().isEmpty())
    }

    @Test
    fun labels_areReadable() {
        assertEquals("Try normal launcher again", SafeModeList.label(SafeModeList.Row.TryNormal))
        assertEquals("Open system settings", SafeModeList.label(SafeModeList.Row.SystemSettings))
        assertEquals("Open stock launcher", SafeModeList.label(SafeModeList.Row.StockLauncher))
        assertEquals("Netflix", SafeModeList.label(SafeModeList.Row.App(app("n", "Netflix"))))
        assertEquals("Safe mode — the main launcher crashed repeatedly", SafeModeList.HEADER)
    }

    @Test
    fun stockAvailable_needsInstalledEnabledAndHome() {
        assertTrue(SafeModeList.stockAvailable(installed = true, enabled = true, hasHome = true))
        assertFalse(SafeModeList.stockAvailable(installed = true, enabled = false, hasHome = true))
        assertFalse(SafeModeList.stockAvailable(installed = false, enabled = true, hasHome = true))
        assertFalse(SafeModeList.stockAvailable(installed = true, enabled = true, hasHome = false))
    }

    @Test
    fun appsFailed_showsNoticeRow_afterFixedRows() {
        val rows = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", stockLauncherAvailable = false, appsFailed = true)
        assertEquals(SafeModeList.Row.TryNormal, rows[0])
        assertEquals(SafeModeList.Row.Notice(SafeModeList.APPS_FAILED), rows.last())
        assertEquals("Could not list apps", SafeModeList.label(rows.last()))
    }
}
