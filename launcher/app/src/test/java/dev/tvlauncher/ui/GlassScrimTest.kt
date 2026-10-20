package dev.tvlauncher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassScrimTest {
    @Test fun layersComposeToTheOriginalScrimInsideTheBand() {
        // Contrast is computed against the resolved scrim alpha; inside the band all layers overlap.
        for (total in listOf(0, 40, 90, 140, 200, 255)) {
            val step = GlassScrim.stepAlpha(total, 4)
            assertTrue("total=$total", kotlin.math.abs(GlassScrim.composed(step, 4) - total) <= 2)
        }
    }

    @Test fun edgeFadesFromAQuarterBandToTheFullBand() {
        assertEquals(0.25f, GlassScrim.insetFraction(0, 4), 1e-6f)
        assertEquals(1f, GlassScrim.insetFraction(3, 4), 1e-6f)
        assertTrue(GlassScrim.insetFraction(1, 4) < GlassScrim.insetFraction(2, 4))
        assertEquals(1f, GlassScrim.insetFraction(0, 1), 0f)       // one layer: the old hard edge at the band
        assertEquals(120, GlassScrim.stepAlpha(120, 1))
    }
}
