package dev.tvlauncher.volumecover

import dev.tvlauncher.volume.core.CoverGeometry
import dev.tvlauncher.volume.core.PxRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverGeometryTest {
    @Test fun popup_at_reference_size_is_the_measured_frame() {
        assertEquals(PxRect(1150, 227, 1230, 493), CoverGeometry.tvPopup(1280, 720))
    }

    @Test fun cover_overhangs_the_measured_popup_on_every_side() {
        val c = CoverGeometry.cover(1280, 720)!!
        assertEquals(PxRect(1138, 215, 1242, 505), c)
        assertTrue(c.contains(CoverGeometry.TV_POPUP_REF))
    }

    @Test fun scales_to_1080p_and_4k_keeping_the_same_place() {
        val p = CoverGeometry.tvPopup(1920, 1080)!!
        assertEquals(PxRect(1725, 341, 1845, 740), p)
        val k = CoverGeometry.tvPopup(3840, 2160)!!
        assertEquals(PxRect(3450, 681, 3690, 1479), k)
        for ((w, h) in listOf(1280 to 720, 1920 to 1080, 3840 to 2160)) {
            val pop = CoverGeometry.tvPopup(w, h)!!
            val cov = CoverGeometry.cover(w, h)!!
            assertTrue("$w x $h", cov.contains(pop))
            assertTrue("$w x $h corners", CoverGeometry.cornersCover(cov, pop, CoverGeometry.radius(w, h)))
            // Vertically centred, right inset in proportion.
            assertEquals(h / 2f, (pop.top + pop.bottom) / 2f, 1f)
            assertEquals(50f * w / 1280, (w - pop.right).toFloat(), 1f)
        }
    }

    @Test fun odd_aspect_uses_the_smaller_ratio_and_stays_on_screen() {
        val w = 1280
        val h = 1024
        val p = CoverGeometry.tvPopup(w, h)!!
        assertEquals(80, p.width)
        assertEquals(266, p.height)
        val c = CoverGeometry.cover(w, h)!!
        assertTrue(c.left >= 0 && c.top >= 0 && c.right <= w && c.bottom <= h)
    }

    @Test fun nonsense_sizes_give_nothing() {
        assertNull(CoverGeometry.tvPopup(0, 720))
        assertNull(CoverGeometry.cover(1280, -1))
        assertEquals(0f, CoverGeometry.scale(0, 0))
    }

    @Test fun tiny_screen_cover_is_clamped_to_the_screen() {
        val c = CoverGeometry.cover(64, 36)
        assertNotNull(c)
        assertTrue(c!!.left >= 0 && c.right <= 64 && c.top >= 0 && c.bottom <= 36)
    }

    @Test fun corner_radius_check() {
        val pop = PxRect(100, 100, 180, 366)
        val cov = PxRect(88, 88, 192, 378)
        assertTrue(CoverGeometry.cornersCover(cov, pop, 22f))
        // Overhang 12, radius 60: the arc cuts into the popup's square corner.
        assertFalse(CoverGeometry.cornersCover(cov, pop, 60f))
        // Not containing at all.
        assertFalse(CoverGeometry.cornersCover(PxRect(100, 100, 150, 150), pop, 0f))
        // Square panel exactly on the popup.
        assertTrue(CoverGeometry.cornersCover(pop, pop, 0f))
    }

    @Test fun only_tcl_is_measured() {
        assertTrue(CoverGeometry.measuredOn("TCL", "TCL"))
        assertTrue(CoverGeometry.measuredOn("tcl", null))
        assertTrue(CoverGeometry.measuredOn(null, "TCL"))
        assertFalse(CoverGeometry.measuredOn("Google", "google"))
        assertFalse(CoverGeometry.measuredOn(null, null))
    }
}
