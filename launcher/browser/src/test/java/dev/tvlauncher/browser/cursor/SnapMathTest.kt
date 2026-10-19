package dev.tvlauncher.browser.cursor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SnapMathTest {
    private val r = 30f

    @Test fun noPull_outsideRadius_orWhenOff() {
        assertEquals(SnapMath.Offset.ZERO, SnapMath.pull(0f, 0f, 31f, 0f, r, Magnet.HIGH))
        assertEquals(SnapMath.Offset.ZERO, SnapMath.pull(0f, 0f, 10f, 0f, r, Magnet.OFF))
    }

    @Test fun pull_pointsTowardTarget() {
        val o = SnapMath.pull(0f, 0f, 20f, 0f, r, Magnet.MED)
        assertTrue(o.dx > 0f)
        assertEquals(0f, o.dy, 0.0001f)
    }

    @Test fun pull_monotoneWithDistance_insideRadius() {
        // Closer targets inside the radius are pulled at least as hard (relative to remaining gap), never past it.
        var last = Float.MAX_VALUE
        for (d in listOf(5f, 10f, 20f, 29f)) {
            val o = SnapMath.pull(0f, 0f, d, 0f, r, Magnet.HIGH)
            val frac = o.dx / d
            assertTrue("fraction should not increase with distance", frac <= last + 1e-4f)
            assertTrue(o.dx <= d)
            last = frac
        }
    }

    @Test fun strength_ordering() {
        val low = SnapMath.pull(0f, 0f, 15f, 15f, r, Magnet.LOW)
        val med = SnapMath.pull(0f, 0f, 15f, 15f, r, Magnet.MED)
        val high = SnapMath.pull(0f, 0f, 15f, 15f, r, Magnet.HIGH)
        assertTrue(hypot(low.dx, low.dy) < hypot(med.dx, med.dy))
        assertTrue(hypot(med.dx, med.dy) < hypot(high.dx, high.dy))
    }

    @Test fun step_isBounded() {
        val o = SnapMath.pull(0f, 0f, 200f, 0f, 500f, Magnet.HIGH, maxStep = 6f)
        assertTrue(hypot(o.dx, o.dy) <= 6f + 1e-4f)
    }

    @Test fun noJitter_atCenter() {
        assertEquals(SnapMath.Offset.ZERO, SnapMath.pull(10f, 10f, 10.3f, 9.8f, r, Magnet.HIGH))
        // Repeated application converges and stops.
        var x = 0f; var y = 0f
        repeat(200) { val o = SnapMath.pull(x, y, 12f, -7f, r, Magnet.HIGH); x += o.dx; y += o.dy }
        val o = SnapMath.pull(x, y, 12f, -7f, r, Magnet.HIGH)
        assertEquals(SnapMath.Offset.ZERO, o)
        assertTrue(hypot(12f - x, -7f - y) <= SnapMath.DEAD_ZONE + 1e-4f)
    }

    @Test fun radiusFor_magnetLevels() {
        assertEquals(0f, SnapMath.radiusDp(Magnet.OFF), 0f)
        assertEquals(12f, SnapMath.radiusDp(Magnet.LOW), 0f)
        assertEquals(20f, SnapMath.radiusDp(Magnet.MED), 0f)
        assertEquals(32f, SnapMath.radiusDp(Magnet.HIGH), 0f)
    }
}
