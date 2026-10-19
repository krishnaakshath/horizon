package dev.tvlauncher.homelayout

import dev.tvlauncher.appsettings.HomeLayoutStylePage
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.ui.TileSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutSettingsTest {
    @Test fun noStore_keepsTodaysSingleRow() {
        for (raw in listOf(null, "", "   ", "not json", "[]", "{}")) {
            val s = HomeLayoutSettings.fromJson(raw)
            assertEquals(raw.toString(), HomeLayoutSettings.DEFAULT, s)
        }
        val d = HomeLayoutSettings.DEFAULT
        assertEquals(HomeStyle.SINGLE_ROW, d.style)
        assertEquals(LabelMode.FOCUS, d.labels)
        assertTrue(d.heroSubtitle && d.quickStrip)
    }

    @Test fun roundTrip_everyChoice() {
        for (st in HomeStyle.values()) for (l in LabelMode.values()) for (b in listOf(true, false)) {
            val s = HomeLayoutSettings(st, l, heroSubtitle = b, quickStrip = !b)
            assertEquals(s, HomeLayoutSettings.fromJson(s.toJson()))
        }
    }

    @Test fun badFields_fallBackOneByOne() {
        val s = HomeLayoutSettings.fromJson("""{"style":"GRID","labels":"SIDEWAYS","heroSubtitle":"yes","quickStrip":false,"extra":1}""")
        assertEquals(HomeStyle.GRID, s.style)
        assertEquals(LabelMode.FOCUS, s.labels)
        assertTrue(s.heroSubtitle)
        assertFalse(s.quickStrip)
    }

    @Test fun labelModes() {
        assertEquals(0f, TileSpec.labelAlpha(false, LabelMode.FOCUS), 0f)
        assertEquals(1f, TileSpec.labelAlpha(true, LabelMode.FOCUS), 0f)
        assertEquals(1f, TileSpec.labelAlpha(false, LabelMode.ALWAYS), 0f)
        assertEquals(0f, TileSpec.labelAlpha(true, LabelMode.NEVER), 0f)
    }

    @Test fun stylePage_staysWithinTheRowBudget() {
        val spec = HomeLayoutStylePage.spec
        assertTrue(spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
        val guard = RowGuard(spec)
        spec.primaryRows.forEach { guard.claim(it, advanced = false) }
        spec.advancedRows.forEach { guard.claim(it, advanced = true) }
        assertEquals((spec.primaryRows + spec.advancedRows).size, (spec.primaryRows + spec.advancedRows).toSet().size)
    }

    @Test fun styleNames_andCaptions_arePlainWords() {
        assertEquals(listOf("Single row", "Two rows", "Grid", "Big hero", "Minimal"), HomeStyle.values().map { it.label })
        for (st in HomeStyle.values()) {
            val c = HomeLayoutStylePage.caption(LayoutStyle.plan(HomeLayoutSettings(style = st), 1f, ScreenDp.TV))
            assertTrue(c, c.matches(Regex("[0-9A-Za-z ]+")))
        }
        assertEquals("3 rows of 5 tiles per page",
            HomeLayoutStylePage.caption(LayoutStyle.plan(HomeLayoutSettings(style = HomeStyle.GRID), 1f, ScreenDp.TV)))
    }
}
