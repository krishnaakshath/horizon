package dev.tvlauncher.browser.cursor

import dev.tvlauncher.input.GamepadMapper
import dev.tvlauncher.input.PadKeyResult
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StickCursorTest {
    @Test fun drift_doesNotMoveThePointer() {
        assertTrue(StickCursor.step(0.2f, 0.2f, 16, 2f).isZero)
        assertTrue(StickCursor.step(0f, -0.34f, 16, 2f).isZero)
        assertFalse(StickCursor.active(0.3f, 0f))
        assertTrue(StickCursor.active(0.3f, 0.3f)) // radial: 0.42
    }

    @Test fun fullTilt_movesAtMaxSpeed_scaledByDensityAndSetting() {
        val m = StickCursor.step(1f, 0f, 1000, 1f)
        assertEquals(StickCursor.MAX_DP_PER_SEC, m.dx, 0.01f)
        assertEquals(0f, m.dy, 0f)
        val fast = StickCursor.step(0f, -1f, 500, 2f, speedFactor = 1.5f)
        assertEquals(-StickCursor.MAX_DP_PER_SEC * 2f * 1.5f / 2f, fast.dy, 0.01f)
    }

    @Test fun curve_isGentleNearTheCentre() {
        val half = StickCursor.step(0.675f, 0f, 1000, 1f).dx
        assertEquals(StickCursor.MAX_DP_PER_SEC / 4f, half, 1f)
        assertTrue(StickCursor.step(0.4f, 0f, 1000, 1f).dx < 10f)
    }

    @Test fun diagonal_keepsDirection() {
        val m = StickCursor.step(0.7f, 0.7f, 100, 1f)
        assertEquals(m.dx, m.dy, 0.001f)
        assertTrue(m.dx > 0f)
    }

    @Test fun noTime_noMove() {
        assertTrue(StickCursor.step(1f, 1f, 0, 1f).isZero)
    }

    @Test fun edgeScroll_isTheOvershoot() {
        assertEquals(-5f, StickCursor.edgeScroll(3f, -8f, 1079f), 0f)
        assertEquals(0f, StickCursor.edgeScroll(30f, -8f, 1079f), 0f)
        assertEquals(6f, StickCursor.edgeScroll(1075f, 10f, 1079f), 0f)
        assertEquals(0f, StickCursor.edgeScroll(500f, 10f, 1079f), 0f)
    }

    @Test fun sharedMapper_isCompiledIntoTheBrowser() {
        val m = GamepadMapper()
        assertEquals(PadKeyResult.Translate(KeyEvent.KEYCODE_DPAD_CENTER), m.onKey(KeyEvent.KEYCODE_BUTTON_A, true, 0, 0x401, 1))
    }
}
