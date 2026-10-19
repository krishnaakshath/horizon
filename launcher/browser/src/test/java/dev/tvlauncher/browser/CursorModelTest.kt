package dev.tvlauncher.browser

import dev.tvlauncher.browser.CursorModel.Dir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorModelTest {
    private val eps = 0.001f

    @Test fun cursor_startsAtCenter() {
        val m = CursorModel(1920f, 1080f)
        assertEquals(960f, m.x, eps)
        assertEquals(540f, m.y, eps)
    }

    @Test fun cursor_startsAtCenter_scaledByDensity_stillCenter() {
        val m = CursorModel(1280f, 720f, density = 1.33f)
        assertEquals(640f, m.x, eps)
        assertEquals(360f, m.y, eps)
    }

    @Test fun speed_ramps_whileHeld_andResetsOnRelease() {
        val m = CursorModel(4000f, 4000f, density = 2f)
        assertEquals(CursorModel.START_DP * 2f, m.speed, eps)
        val x0 = m.x
        m.move(Dir.RIGHT)
        assertEquals(x0 + CursorModel.START_DP * 2f, m.x, eps)
        val after1 = m.speed
        assertTrue("speed grows while held", after1 > CursorModel.START_DP * 2f)
        m.move(Dir.RIGHT)
        assertTrue("speed keeps growing", m.speed > after1)
        repeat(50) { m.move(Dir.RIGHT) }
        assertEquals("speed caps at max", CursorModel.MAX_DP * 2f, m.speed, eps)
        m.release()
        assertEquals("release resets speed", CursorModel.START_DP * 2f, m.speed, eps)
        // A direction change also starts slow again.
        repeat(5) { m.move(Dir.DOWN) }
        val y = m.y
        m.move(Dir.UP)
        assertEquals(y - CursorModel.START_DP * 2f, m.y, eps)
    }

    @Test fun cursor_clampsToScreen() {
        val m = CursorModel(800f, 600f)
        repeat(100) { m.move(Dir.LEFT) }
        assertEquals(0f, m.x, eps)
        repeat(100) { m.move(Dir.RIGHT) }
        assertEquals(799f, m.x, eps)
        repeat(100) { m.move(Dir.UP) }
        assertEquals(0f, m.y, eps)
        repeat(100) { m.move(Dir.DOWN) }
        assertEquals(599f, m.y, eps)
    }

    @Test fun cursor_resize_keepsInsideBounds() {
        val m = CursorModel(800f, 600f)
        repeat(100) { m.move(Dir.RIGHT) }
        m.resize(400f, 300f)
        assertEquals(399f, m.x, eps)
        assertTrue(m.y <= 299f)
    }

    @Test fun edgeScroll_triggersAtTopAndBottom_only() {
        val d = 1.5f
        val m = CursorModel(900f, 600f, density = d)
        // Moving in the middle never scrolls.
        assertEquals(0f, m.move(Dir.UP).scrollY, eps)
        m.release()
        assertEquals(0f, m.move(Dir.DOWN).scrollY, eps)
        m.release()
        // Left/right edges never scroll.
        repeat(100) { assertEquals(0f, m.move(Dir.LEFT).scrollY, eps) }
        repeat(100) { assertEquals(0f, m.move(Dir.RIGHT).scrollY, eps) }
        m.release()
        // Top edge: once the cursor reaches it, pushing further scrolls the page up.
        var steps = m.move(Dir.UP)
        var guard = 0
        while (steps.scrollY == 0f && guard++ < 200) steps = m.move(Dir.UP)
        assertEquals(0f, m.y, eps)
        assertEquals(-CursorModel.EDGE_SCROLL_DP * d, steps.scrollY, eps)
        assertEquals(-CursorModel.EDGE_SCROLL_DP * d, m.move(Dir.UP).scrollY, eps)
        m.release()
        // Bottom edge scrolls down.
        steps = m.move(Dir.DOWN)
        guard = 0
        while (steps.scrollY == 0f && guard++ < 200) steps = m.move(Dir.DOWN)
        assertEquals(599f, m.y, eps)
        assertEquals(CursorModel.EDGE_SCROLL_DP * d, steps.scrollY, eps)
    }
}
