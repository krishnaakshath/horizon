package dev.tvlauncher.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLifecycleTest {
    private val lc = PanelLifecycle()

    @Test fun showDismissFinish() {
        val g = lc.show()!!
        assertTrue(lc.isOpen)
        assertEquals(g, lc.dismiss())
        assertTrue(lc.isVisible); assertFalse(lc.isOpen)
        assertTrue(lc.finishClose(g))
        assertFalse(lc.isVisible)
    }

    @Test fun showWhileOpen_isIgnored() {
        lc.show()
        assertNull(lc.show())
    }

    @Test fun fastReopen_staleFinishIgnored() {
        lc.show()
        val closing = lc.dismiss()!!
        val fresh = lc.show()
        assertNotNull(fresh)
        assertFalse(lc.finishClose(closing))
        assertTrue(lc.isOpen)
    }

    @Test fun backDuringClose_isConsumed() {
        lc.show(); lc.dismiss()
        assertTrue(lc.isVisible)
        assertNull(lc.dismiss())
    }

    @Test fun release_idempotent() {
        lc.show()
        assertTrue(lc.release())
        assertFalse(lc.release())
        assertFalse(lc.isVisible)
    }

    @Test fun showAfterRelease_freshGeneration() {
        val g1 = lc.show()!!
        lc.release()
        assertFalse(lc.finishClose(g1))
        val g2 = lc.show()!!
        assertTrue(g2 > g1)
        assertTrue(lc.isOpen)
    }
}
