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
        assertEquals(SafeModeList.Row.SystemSettings, rows[0]) // default focus
        assertEquals(SafeModeList.Row.TryNormal, rows[1])
        val apps = rows.filterIsInstance<SafeModeList.Row.App>().map { it.app.label }
        // Leanback apps sorted case-insensitively, then launcher-only apps; duplicates dropped.
        assertEquals(listOf("apple tv", "Netflix", "YouTube", "Files"), apps)
    }

    @Test
    fun ownPackage_isExcluded() {
        val rows = SafeModeList.build(listOf(app("dev.tvlauncher", "H02"), app("x", "X")), emptyList(), "dev.tvlauncher", false)
        assertEquals(listOf("X"), rows.filterIsInstance<SafeModeList.Row.App>().map { it.app.label })
    }

    @Test
    fun stockLauncherRow_onlyWhenAvailable() {
        val without = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", false)
        assertFalse(SafeModeList.Row.StockLauncher in without)
        val with = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", true)
        assertEquals(listOf(SafeModeList.Row.SystemSettings, SafeModeList.Row.TryNormal, SafeModeList.Row.StockLauncher), with)
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
        assertEquals(SafeModeList.Row.SystemSettings, rows[0])
        assertEquals(SafeModeList.Row.Notice(SafeModeList.APPS_FAILED), rows.last())
        assertEquals("Could not list apps", SafeModeList.label(rows.last()))
    }

    @Test
    fun minimalScreen_hasSettingsRow_evenWhenAppListFails() {
        val rows = SafeModeList.minimal(stockLauncherAvailable = false, stockLabel = "Google TV Home")
        assertEquals(SafeModeList.Row.SystemSettings, rows.first())
        assertTrue(SafeModeList.Row.TryNormal in rows)
        assertTrue(rows.none { it is SafeModeList.Row.App })
        assertTrue(SafeModeList.Row.Notice(SafeModeList.enableStockHint("Google TV Home")) in rows)
        assertFalse(SafeModeList.Row.StockLauncher in rows)
        val full = SafeModeList.build(emptyList(), emptyList(), "dev.tvlauncher", false, appsFailed = true)
        assertTrue(SafeModeList.Row.SystemSettings in full)
    }

    @Test
    fun minimalScreen_offersStockLauncher_whenAvailable() {
        val rows = SafeModeList.minimal(stockLauncherAvailable = true, stockLabel = "Android TV Home")
        assertEquals(listOf(SafeModeList.Row.SystemSettings, SafeModeList.Row.StockLauncher, SafeModeList.Row.TryNormal), rows)
    }

    @Test
    fun systemSettingsTargets_inOrder() {
        assertEquals(
            listOf(
                SettingsEscape.Target.Action("android.settings.SETTINGS"),
                SettingsEscape.Target.Action("android.settings.APPLICATION_SETTINGS"),
                SettingsEscape.Target.Package("com.android.tv.settings")
            ),
            SettingsEscape.TARGETS
        )
    }

    @Test
    fun enableStockHint_namesTheStockLauncher_neverOurApp() {
        assertEquals(
            "To get it back: Settings → Apps → See all apps → Show system apps → Google TV Home → Enable",
            SafeModeList.enableStockHint("Google TV Home")
        )
        assertEquals("Android TV Home", SafeModeList.STOCK_LABEL_FALLBACK)
        assertTrue(SafeModeList.enableStockHint(SafeModeList.STOCK_LABEL_FALLBACK).contains("Android TV Home"))
        assertFalse(SafeModeList.enableStockHint("Android TV Home").contains("TV launcher"))
    }

    @Test fun subtitle_isOneCalmLine() {
        assertEquals("The launcher closed unexpectedly several times. Last error: NullPointerException.",
            SafeModeList.subtitle("NullPointerException", minimal = false))
        assertEquals("The launcher and its safe mode closed unexpectedly.", SafeModeList.subtitle("  ", minimal = true))
        assertEquals("The launcher closed unexpectedly several times.", SafeModeList.subtitle(null, minimal = false))
    }

    @Test fun actionsAreTheWaysOut() {
        assertEquals(true, SafeModeList.isAction(SafeModeList.Row.SystemSettings))
        assertEquals(true, SafeModeList.isAction(SafeModeList.Row.TryNormal))
        assertEquals(true, SafeModeList.isAction(SafeModeList.Row.StockLauncher))
        assertEquals(false, SafeModeList.isAction(SafeModeList.Row.Notice("x")))
        assertEquals(false, SafeModeList.isAction(SafeModeList.Row.App(app("n", "Netflix"))))
    }
}
