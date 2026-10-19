package dev.tvlauncher.sound

import org.junit.Assert.*
import org.junit.Test

class ReverbTest {
    @Test fun impulse_produces_taps_at_delays_with_gains() {
        val x = DoubleArray(4000); x[0] = 1.0
        val y = Reverb.apply(x, 1000, TailSpec(listOf(35 to 0.35f, 90 to 0.12f)))
        assertEquals(4000, y.size)
        assertEquals(1.0, y[0], 1e-9)
        assertEquals(0.35, y[35], 1e-6)
        assertEquals(0.12, y[90], 1e-6)
        assertEquals(0.0, y[50], 1e-9)
    }

    @Test fun input_not_mutated_and_tail_truncated_to_length() {
        val x = DoubleArray(50); x[40] = 1.0; x[3] = 0.25
        val before = x.copyOf()
        val y = Reverb.apply(x, 1000, TailSpec(listOf(35 to 0.5f)))
        assertArrayEquals(before, x, 0.0)
        assertEquals(0.125, y[38], 1e-9)
        assertEquals(50, y.size)
        assertEquals(1.0, y[40], 1e-9)
    }
}
