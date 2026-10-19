package dev.tvlauncher.safemode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    private class MemoryStore : CrashStore {
        var saved = CrashHistory()
        var writes = 0
        override fun read() = saved
        override fun write(history: CrashHistory) { saved = history; writes++ }
    }

    private var now = 1_000_000L
    private val store = MemoryStore()
    private val guard = CrashGuard(store) { now }

    @Test
    fun threeCrashesWithin60s_tripsSafeMode() {
        guard.recordCrash("IllegalStateException"); now += 20_000
        guard.recordCrash("IllegalStateException"); now += 39_000
        assertFalse(guard.isTripped)
        guard.recordCrash("NullPointerException")
        assertTrue(guard.isTripped)
        assertEquals("NullPointerException", store.saved.lastException)
    }

    @Test
    fun twoCrashes_noTrip() {
        guard.recordCrash("A"); now += 1_000
        guard.recordCrash("B")
        assertFalse(guard.isTripped)
        assertEquals(2, store.saved.times.size)
    }

    @Test
    fun crashesOver5min_noTrip() {
        for (i in 0 until 4) { guard.recordCrash("A"); now += 100_000 } // 4 crashes over 5 minutes
        assertFalse(guard.isTripped)
    }

    @Test
    fun threeSpanningJustOver60s_noTrip() {
        guard.recordCrash("A"); now += 30_000
        guard.recordCrash("A"); now += 30_001
        guard.recordCrash("A")
        assertFalse(guard.isTripped)
    }

    @Test
    fun reset_clearsHistory() {
        repeat(3) { guard.recordCrash("A"); now += 1_000 }
        assertTrue(guard.isTripped)
        guard.reset()
        assertFalse(guard.isTripped)
        assertTrue(store.saved.times.isEmpty())
        assertNull(store.saved.lastException)
        // A fresh crash after reset does not trip on its own.
        guard.recordCrash("A")
        assertFalse(guard.isTripped)
    }

    @Test
    fun trip_staysUntilReset_evenAfterTime() {
        repeat(3) { guard.recordCrash("A"); now += 1_000 }
        now += 24L * 3_600_000
        assertTrue(CrashGuard(store) { now }.isTripped) // a new process sees it too
    }

    @Test
    fun history_isCapped_andNamesAreShort() {
        repeat(20) { guard.recordCrash("X".repeat(500)); now += 120_000 }
        assertEquals(CrashGuard.MAX_TIMES, store.saved.times.size)
        assertEquals(CrashGuard.MAX_NAME, store.saved.lastException!!.length)
    }

    @Test
    fun brokenStore_neverThrows() {
        val broken = object : CrashStore {
            override fun read(): CrashHistory = throw IllegalStateException("disk")
            override fun write(history: CrashHistory) = throw IllegalStateException("disk")
        }
        val g = CrashGuard(broken) { now }
        g.recordCrash("A")
        g.reset()
        assertFalse(g.isTripped)
    }

    @Test
    fun historyCodec_roundTrips_andToleratesGarbage() {
        val h = CrashHistory(listOf(1L, 2L, 3L), "Boom", tripped = true)
        assertEquals(h.times, CrashHistory.decodeTimes(CrashHistory.encodeTimes(h.times)))
        assertEquals(listOf(5L, 7L), CrashHistory.decodeTimes("5,x,,7"))
        assertTrue(CrashHistory.decodeTimes(null).isEmpty())
    }

    @Test
    fun redirect_onlyWhenTripped() {
        assertFalse(SafeModeGate.shouldRedirect(CrashHistory()))
        assertFalse(SafeModeGate.shouldRedirect(CrashHistory(listOf(1L, 2L), "A", tripped = false)))
        assertTrue(SafeModeGate.shouldRedirect(CrashHistory(listOf(1L, 2L, 3L), "A", tripped = true)))
    }

    @Test
    fun redirect_afterGuardTrips_andNotAfterReset() {
        repeat(3) { guard.recordCrash("A"); now += 1_000 }
        assertTrue(SafeModeGate.shouldRedirect(store.read()))
        guard.reset()
        assertFalse(SafeModeGate.shouldRedirect(store.read()))
    }
}
