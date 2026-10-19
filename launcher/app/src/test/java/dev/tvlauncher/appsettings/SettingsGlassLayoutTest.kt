package dev.tvlauncher.appsettings

import dev.tvlauncher.settings.AppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Layout models behind the glass Settings: nothing overlaps, nothing leaves its card or the safe area. */
class SettingsGlassLayoutTest {
    private class Screen(val w: Int, val h: Int, val density: Float)

    private val screens = listOf(Screen(1280, 720, 1f), Screen(1280, 720, 213 / 160f), Screen(1280, 720, 1.5f),
        Screen(1920, 1080, 2f), Screen(3840, 2160, 4f))

    private fun px(dp: Int, s: Screen) = (dp * s.density).roundToInt()

    /** [b] scaled by [scale] around its centre. */
    private fun scaled(b: Box, scale: Float): Box {
        val dx = ((b.width * (scale - 1f)) / 2f).toInt() + 1
        val dy = ((b.height * (scale - 1f)) / 2f).toInt() + 1
        return Box(b.left - dx, b.top - dy, b.right + dx, b.bottom + dy)
    }

    private fun safe(s: Screen) = Box(px(48, s), px(27, s), s.w - px(48, s), s.h - px(27, s))

    @Test
    fun focusedSidebarEntry_staysInsideTheCard_andTheSafeArea() {
        for (s in screens) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, hasScrollbar = true)
            val entries = l.sidebarEntries(s.density)
            val entry = Box(entries.left, entries.top, entries.right, entries.top + px(SidebarMetrics.ENTRY_DP, s))
            val focused = scaled(entry, SidebarItem.FOCUS_SCALE)
            val card = l.sidebar
            assertTrue("${s.w}@${s.density}: $focused in $card", card.contains(focused))
            assertTrue(safe(s).contains(focused))
        }
    }

    @Test
    fun sidebarTitle_neverIntersectsTheList() {
        for (s in screens) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, hasScrollbar = true)
            val (title, list) = SidebarMetrics.regions(l.sidebar, px(SettingsLayout.SIDEBAR_PAD_DP, s), s.density)
            assertFalse(title.intersects(list))
            assertTrue(list.top - title.bottom >= px(SidebarMetrics.TITLE_GAP_DP, s) - 1)
            // A focused first entry (scaled) still clears the title.
            val first = Box(list.left, list.top, list.right, list.top + px(SidebarMetrics.ENTRY_DP, s))
            assertFalse(scaled(first, SidebarItem.FOCUS_SCALE).intersects(title))
            assertTrue(l.sidebar.contains(title))
            assertTrue(l.sidebar.contains(list))
        }
    }

    @Test
    fun sidebarRhythm_isOnThe8dpGrid_withEqualGroupGaps() {
        val flags = listOf(true, false, false, true, false, true, false, false, false, true)
        val tops = SidebarMetrics.tops(flags)
        for (t in tops) assertEquals("top $t", 0, t % 8)
        assertEquals(0, SidebarMetrics.ENTRY_DP % 8)
        assertEquals(0, SidebarMetrics.HEADER_DP % 8)
        // Every later group header has the same gap above it.
        val gaps = flags.indices.filter { flags[it] && it > 0 }.map { i ->
            val prevBottom = tops[i - 1] + if (flags[i - 1]) SidebarMetrics.HEADER_DP else SidebarMetrics.ENTRY_DP
            tops[i] - prevBottom
        }
        assertEquals(1, gaps.toSet().size)
        // Entries never overlap the next one, even scaled.
        for (i in 0 until tops.size - 1) {
            val h = if (flags[i]) SidebarMetrics.HEADER_DP else SidebarMetrics.ENTRY_DP
            assertTrue(tops[i] + h <= tops[i + 1])
        }
    }

    @Test
    fun focusedRow_staysInsideThePane() {
        for (s in screens) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, hasScrollbar = true)
            val rows = l.rows(s.density)
            val row = Box(rows.left, rows.top + px(40, s), rows.right, rows.top + px(40 + SettingsLayout.ROW_MIN_DP, s))
            val focused = scaled(row, 1.015f)
            assertTrue(l.pane.contains(focused))
            // The scroll thumb lives right of the rows.
            assertTrue(l.scrollbar!!.left >= focused.right)
            assertTrue(safe(s).contains(l.pane))
        }
    }

    @Test
    fun rowValue_neverStarvesTheTitle() {
        for (row in listOf(300, 505, 760, 1500)) for (fixed in listOf(0, 60, 160, 400)) {
            val v = RowMetrics.valueMax(row, fixed)
            assertTrue(v >= 0)
            assertTrue(v <= (row * SettingsLayout.VALUE_MAX_FRACTION).toInt())
            if (v > 0) assertTrue(row - fixed - v >= (row * RowMetrics.TITLE_MIN_FRACTION).toInt())
        }
    }

    @Test
    fun swatchStrip_fitsEveryPane_withTheFocusScale() {
        val n = AppearanceSettings.PRESETS.size
        for (s in screens) {
            val l = SettingsLayout.compute(s.w, s.h, s.density, hasScrollbar = true)
            val avail = l.rows(s.density).width - px(36, s) // the strip's own side padding
            val (size, gap) = SwatchFit.fit(avail, n, px(46, s), px(14, s), 1.12f)
            val width = n * size + (n - 1) * gap + Math.ceil(size * 0.12).toInt()
            assertTrue("${s.w}@${s.density}: $width > $avail", width <= avail)
            assertTrue(size >= px(32, s))
        }
        // Wide panes keep the preferred size.
        assertEquals(46 to 14, SwatchFit.fit(2000, n, 46, 14, 1.12f))
        assertEquals(46 to 14, SwatchFit.fit(0, n, 46, 14, 1.12f))
    }
}

class PaneStackTest {
    private val densities = listOf(1f, 213 / 160f, 2f, 4f)

    @Test
    fun rowsStartBelowTheHeader_andScrolledRowsNeverReachIt() {
        for (d in densities) {
            val w = (1280 * d / (213 / 160f)).toInt()
            val h = (720 * d / (213 / 160f)).toInt()
            val l = SettingsLayout.compute(w, h, d, hasScrollbar = true)
            for (crumb in listOf(0, (19 * d).toInt())) for (desc in listOf(0, (40 * d).toInt())) {
                val r = PaneStack.layout(l.pane, d, crumb, (36 * d).toInt(), desc)
                assertFalse(r.header.intersects(r.body))
                assertTrue(r.body.top >= r.header.bottom)
                assertTrue(r.body.height > 0)
                val rowH = (SettingsLayout.ROW_MIN_DP * d).toInt()
                // A row at every scroll offset, including one scrolled half under the header.
                var top = r.body.top - rowH * 2
                while (top < r.body.bottom + rowH) {
                    val row = Box(l.rows(d).left, top, l.rows(d).right, top + rowH)
                    val drawn = PaneStack.visible(row, r.body)
                    if (drawn != null) {
                        assertFalse("row at $top over the header", drawn.intersects(r.header))
                        assertTrue(r.body.contains(drawn))
                    }
                    top += rowH / 3
                }
            }
        }
    }

    @Test
    fun scrollThumb_livesInTheCardPadding_rightOfEveryRow() {
        for (d in densities) {
            val l = SettingsLayout.compute((960 * d).toInt(), (540 * d).toInt(), d, hasScrollbar = true)
            val bar = l.scrollbar!!
            assertTrue(l.pane.contains(bar))
            assertTrue(bar.left >= l.rows(d).right)
            assertTrue(bar.right <= l.pane.right)
        }
    }
}
