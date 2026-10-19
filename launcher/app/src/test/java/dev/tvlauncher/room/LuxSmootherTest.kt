package dev.tvlauncher.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LuxSmootherTest {
    private var now = 0L
    private fun smoother(alpha: Float = 0.3f) = LuxSmoother(alpha, clock = { now })

    @Test fun first_sample_is_taken_as_is() {
        val s = smoother()
        assertNull(s.value())
        assertEquals(120f, s.add(120f), 0.01f)
        assertEquals(120f, s.value()!!, 0.01f)
    }

    @Test fun converges_to_a_new_level() {
        val s = smoother()
        s.add(10f)
        var v = 0f
        repeat(40) { now += 2_000; v = s.add(300f) }
        assertEquals(300f, v, 3f)
    }

    @Test fun moves_monotonically_towards_target() {
        val s = smoother()
        s.add(10f)
        var prev = 10f
        repeat(20) {
            now += 1_000
            val v = s.add(500f)
            assertTrue(v >= prev)
            assertTrue(v <= 500f)
            prev = v
        }
    }

    @Test fun single_spike_is_ignored() {
        val s = smoother()
        repeat(5) { now += 2_000; s.add(50f) }
        now += 2_000
        val atSpike = s.add(30_000f)
        assertEquals(50f, atSpike, 0.5f)
        now += 2_000
        assertEquals(50f, s.add(50f), 0.5f)
        // A single dropout to zero (hand over the sensor) too.
        now += 2_000
        assertEquals(50f, s.add(0f), 0.5f)
    }

    @Test fun sustained_change_gets_through() {
        val s = smoother()
        repeat(5) { now += 2_000; s.add(50f) }
        var v = 0f
        repeat(10) { now += 2_000; v = s.add(5_000f) }
        assertTrue(v > 1_000f)
    }

    @Test fun deterministic() {
        val a = smoother(); val b = smoother()
        val series = listOf(3f, 8f, 120f, 7f, 9f, 0f, 400f, 401f, 399f)
        val ra = series.map { now += 1_500; a.add(it) }
        now = 0
        val rb = series.map { now += 1_500; b.add(it) }
        assertEquals(ra, rb)
    }

    @Test fun invalid_samples_ignored() {
        val s = smoother()
        s.add(40f)
        now += 1_000
        assertEquals(40f, s.add(Float.NaN), 0.01f)
        assertEquals(40f, s.add(-1f), 0.01f)
        assertEquals(40f, s.add(Float.POSITIVE_INFINITY), 0.01f)
    }

    @Test fun stale_value_resets_after_a_long_gap() {
        val s = smoother()
        s.add(5f)
        now += 10 * 60_000L
        assertEquals(800f, s.add(800f), 0.01f)
    }

    @Test fun reset_clears() {
        val s = smoother()
        s.add(5f)
        s.reset()
        assertNull(s.value())
    }
}
