package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PixelShiftTest {
    @Test fun startsCentered() {
        val p = PixelShift(maxDp = 12, seed = 3)
        assertEquals(0, p.dx); assertEquals(0, p.dy)
    }

    @Test fun walk_staysWithinBounds_andMovesEveryStep() {
        val p = PixelShift(maxDp = 12, seed = 99)
        repeat(20_000) {
            val bx = p.dx; val by = p.dy
            p.step()
            assertTrue(abs(p.dx) <= 12); assertTrue(abs(p.dy) <= 12)
            assertTrue(p.dx != bx || p.dy != by)
            // Slow: one step never jumps more than 4 dp per axis.
            assertTrue(abs(p.dx - bx) <= PixelShift.MAX_STEP_DP)
            assertTrue(abs(p.dy - by) <= PixelShift.MAX_STEP_DP)
        }
    }

    @Test fun deterministic_forSameSeed() {
        val a = PixelShift(12, 7); val b = PixelShift(12, 7)
        repeat(500) { a.step(); b.step(); assertEquals(a.dx, b.dx); assertEquals(a.dy, b.dy) }
    }

    @Test fun differentSeeds_diverge() {
        val a = PixelShift(12, 1); val b = PixelShift(12, 2)
        val pa = (0 until 50).map { a.step(); a.dx to a.dy }
        val pb = (0 until 50).map { b.step(); b.dx to b.dy }
        assertNotEquals(pa, pb)
    }

    @Test fun coversRange_overTime() {
        val p = PixelShift(12, 5)
        var minX = 0; var maxX = 0; var minY = 0; var maxY = 0
        repeat(5_000) { p.step(); minX = minOf(minX, p.dx); maxX = maxOf(maxX, p.dx); minY = minOf(minY, p.dy); maxY = maxOf(maxY, p.dy) }
        assertTrue(minX <= -8 && maxX >= 8 && minY <= -8 && maxY >= 8)
    }

    @Test fun pxConversion() {
        val p = PixelShift(12, 5)
        repeat(10) { p.step() }
        assertEquals(Math.round(p.dx * 2f), p.dxPx(2f))
        assertEquals(Math.round(p.dy * 1.5f), p.dyPx(1.5f))
    }

    @Test fun dimming_fullThen60AfterTwoMinutes() {
        assertEquals(1f, AmbientDimming.contentAlpha(0, 14, nightDimEnabled = true), 0f)
        assertEquals(1f, AmbientDimming.contentAlpha(119_999, 14, true), 0f)
        assertEquals(0.6f, AmbientDimming.contentAlpha(120_000, 14, true), 0f)
        assertEquals(0.6f, AmbientDimming.contentAlpha(10_000_000, 22, true), 0f)
    }

    @Test fun dimming_nightWindow_23to06_whenEnabled() {
        for (h in listOf(23, 0, 3, 5)) assertEquals(0.35f, AmbientDimming.contentAlpha(0, h, true), 0f)
        for (h in listOf(6, 12, 22)) assertEquals(1f, AmbientDimming.contentAlpha(0, h, true), 0f)
        assertEquals(1f, AmbientDimming.contentAlpha(0, 2, nightDimEnabled = false), 0f)
        assertEquals(0.35f, AmbientDimming.contentAlpha(500_000, 2, true), 0f)
        assertTrue(AmbientDimming.isNight(23)); assertTrue(!AmbientDimming.isNight(6))
    }

    @Test fun dimming_nextChangeDelay() {
        assertEquals(120_000L, AmbientDimming.msUntilIdleDim(0))
        assertEquals(1_000L, AmbientDimming.msUntilIdleDim(119_000))
        assertEquals(-1L, AmbientDimming.msUntilIdleDim(120_000))
    }
}
