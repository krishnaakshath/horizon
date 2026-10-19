package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecycleGateTest {
    private val released = ArrayList<String>()
    private val gate = RecycleGate<String> { released += it }

    @Test fun deferredItem_survivesTheNextFrame_releasedOnTheOneAfter() {
        gate.defer("a")
        assertTrue(released.isEmpty())
        gate.frame() // frame that may still draw "a"
        assertTrue(released.isEmpty())
        gate.frame()
        assertEquals(listOf("a"), released)
        assertEquals(0, gate.pendingCount)
    }

    @Test fun itemsDeferredLater_waitTheirOwnFrames() {
        gate.defer("a")
        gate.frame()
        gate.defer("b")
        gate.frame()
        assertEquals(listOf("a"), released)
        gate.frame()
        assertEquals(listOf("a", "b"), released)
    }

    @Test fun sameItemTwice_releasedOnce() {
        gate.defer("a"); gate.defer("a")
        gate.frame(); gate.frame(); gate.frame()
        assertEquals(listOf("a"), released)
    }

    @Test fun cancel_rescuesAnItemThatIsShownAgain() {
        gate.defer("a")
        gate.cancel("a")
        gate.frame(); gate.frame()
        assertTrue(released.isEmpty())
    }

    @Test fun flush_releasesEverything_andPendingCount() {
        gate.defer("a"); gate.defer("b")
        assertEquals(2, gate.pendingCount)
        gate.flush()
        assertEquals(listOf("a", "b"), released)
        assertEquals(0, gate.pendingCount)
    }
}
