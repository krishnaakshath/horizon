package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateGateTest {
    @Test fun first_passes_then_limited_with_trailing_latest() {
        val g = RateGate(2_000)
        assertTrue(g.offer(1f, 0))
        assertFalse(g.offer(2f, 500))
        assertFalse(g.offer(3f, 1_500))
        assertEquals(2_000L, g.trailingDueAt())
        assertEquals(3f, g.takeTrailing(2_000)!!, 0f)
        assertNull(g.trailingDueAt())
        assertFalse(g.offer(4f, 3_000))
        assertTrue(g.offer(5f, 4_000))
        assertNull(g.trailingDueAt())
    }

    @Test fun clock_backwards_passes() {
        val g = RateGate(2_000)
        assertTrue(g.offer(1f, 10_000))
        assertTrue(g.offer(1f, 5_000))
    }

    @Test fun reset_forgets() {
        val g = RateGate(2_000)
        g.offer(1f, 0); g.offer(2f, 1)
        g.reset()
        assertNull(g.takeTrailing(2))
        assertTrue(g.offer(3f, 3))
    }
}
