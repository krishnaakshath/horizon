package dev.tvlauncher.appsettings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutTest {
    private class Screen(val w: Int, val h: Int, val dpi: Int) { val density get() = dpi / 160f }

    private val screens = listOf(Screen(1280, 720, 213), Screen(1920, 1080, 320), Screen(3840, 2160, 640))

    private fun dp(v: Int, s: Screen) = v * s.density

    @Test
    fun sidebar_isBetween30And36PercentOfTheWidth() {
        for (s in screens) for (bar in listOf(false, true)) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, bar)
            val f = l.sidebar.width / s.w.toFloat()
            assertTrue("${s.w}: $f", f >= 0.30f && f <= 0.36f)
        }
    }

    @Test
    fun everythingStaysInsideTheOverscanSafeArea() {
        for (s in screens) for (bar in listOf(false, true)) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, bar)
            for (b in listOf(l.sidebar, l.pane)) {
                assertTrue(b.left >= dp(48, s) - 1)
                assertTrue(b.right <= s.w - dp(48, s) + 1)
                assertTrue(b.top >= dp(27, s) - 1)
                assertTrue(b.bottom <= s.h - dp(27, s) + 1)
            }
        }
    }

    @Test
    fun sidebarAndPaneNeverOverlap_andKeepAFocusGap() {
        for (s in screens) for (bar in listOf(false, true)) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, bar)
            assertFalse(l.sidebar.intersects(l.pane))
            assertTrue(l.pane.left - l.sidebar.right >= dp(8, s))
            assertTrue(l.pane.width > l.sidebar.width)
        }
    }

    @Test
    fun paneContent_isInsideThePane_andReservesTheScrollbar() {
        for (s in screens) {
            val plain = SettingsLayout.compute(s.w, s.h, s.density, false)
            val bar = SettingsLayout.compute(s.w, s.h, s.density, true)
            assertTrue(plain.pane.contains(plain.paneContent))
            assertTrue(bar.pane.contains(bar.paneContent))
            assertTrue(bar.paneContent.right <= plain.paneContent.right)
            assertTrue(bar.scrollbar!!.left - bar.paneContent.right >= dp(8, s) - 1)
            assertTrue(bar.pane.contains(bar.scrollbar!!))
            assertNull(plain.scrollbar)
        }
    }

    @Test
    fun rowsAreTallEnough_andSpacedForFocus() {
        for (s in screens) {
            assertTrue(SettingsLayout.ROW_MIN_DP * s.density >= dp(56, s))
            assertTrue(SettingsLayout.ROW_GAP_DP * s.density >= dp(8, s))
        }
        assertTrue(SettingsLayout.ROW_MIN_DP >= 56)
        assertTrue(SettingsLayout.ROW_GAP_DP >= 8)
    }

    @Test
    fun valueColumn_isCappedAt35PercentOfTheRow() {
        val l = SettingsLayout.compute(1280, 720, 213 / 160f, true)
        assertEquals(350, l.valueMaxWidth(1000))
        assertTrue(SettingsLayout.VALUE_MAX_FRACTION <= 0.35f)
    }

    @Test
    fun sameDpGeometry_atAllThreeResolutions() {
        val a = SettingsLayout.compute(1280, 720, 1280 / 960f, true)
        val b = SettingsLayout.compute(3840, 2160, 4f, true)
        assertEquals(a.sidebar.width / (1280 / 960f), b.sidebar.width / 4f, 2f)
    }

    @Test
    fun tinyWidths_doNotThrow() {
        for (w in 0..8) SettingsLayout.compute(w, 10, 1f, true)
    }

    @Test
    fun paneInset_matchesTheContentBox() {
        val l = SettingsLayout.compute(1280, 720, 213 / 160f, true)
        assertEquals(l.paneContent.left - l.pane.left, l.paneInset)
        assertTrue(l.paneInset >= dp(24, screens[0]) - 1)
    }

    @Test
    fun scrollIndicator_thumb() {
        assertNull(ScrollIndicator.thumb(range = 400, extent = 400, offset = 0, track = 300, minThumb = 20))
        assertEquals(0 to 150, ScrollIndicator.thumb(800, 400, 0, 300, 20))
        assertEquals(150 to 150, ScrollIndicator.thumb(800, 400, 400, 300, 20))
        val tiny = ScrollIndicator.thumb(100_000, 400, 99_600, 300, 20)!!
        assertEquals(20, tiny.second)
        assertEquals(280, tiny.first)
    }
}
