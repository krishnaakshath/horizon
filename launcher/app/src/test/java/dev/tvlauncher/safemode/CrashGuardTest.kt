package dev.tvlauncher.safemode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashGuardTest {
    private class MemoryStore : CrashStore {
        var saved = CrashHistory()
        override fun read() = saved
        override fun write(history: CrashHistory) { saved = history }
    }

    private var now = 1_000_000_000L
    private var boot = 7
    private val store = MemoryStore()
    private val guard = CrashGuard(store, { now }, { boot })
    private val hour = 3_600_000L

    private fun trip() { repeat(3) { guard.recordCrash("A"); now += 1_000 } }

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
    fun threeCrashesWithin3min_trips_exactlyAtWindow() {
        guard.recordCrash("A"); now += 90_000
        guard.recordCrash("A"); now += 90_000 // third crash exactly 180_000 ms after the first
        guard.recordCrash("A")
        assertEquals(180_000L, CrashGuard.WINDOW_MS)
        assertTrue(guard.isTripped)
    }

    @Test
    fun threeCrashesSpanning180001ms_noTrip() {
        guard.recordCrash("A"); now += 90_000
        guard.recordCrash("A"); now += 90_001
        guard.recordCrash("A")
        assertFalse(guard.isTripped)
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
    fun fiveMinuteOldCrashes_doNotCount() {
        guard.recordCrash("A"); guard.recordCrash("A")
        now += 5 * 60_000
        guard.recordCrash("A")
        assertFalse(guard.isTripped)
    }

    @Test
    fun reset_clearsHistory() {
        trip()
        assertTrue(guard.isTripped)
        guard.reset()
        assertFalse(guard.isTripped)
        assertTrue(store.saved.times.isEmpty())
        assertNull(store.saved.lastException)
        guard.recordCrash("A")
        assertFalse(guard.isTripped)
    }

    @Test
    fun trip_recordsTimeAndBoot() {
        trip()
        assertEquals(boot, store.saved.tripBoot)
        assertEquals(now - 1_000, store.saved.trippedAt)
    }

    @Test
    fun trip_staysWithin24h_sameBoot() {
        trip()
        now += 23 * hour
        assertTrue(guard.shouldRedirect())
        assertTrue(CrashGuard(store, { now }, { boot }).shouldRedirect()) // a new process sees it too
        assertTrue(guard.isTripped)
    }

    @Test
    fun trip_clearsAfterReboot() {
        trip()
        boot += 1
        assertFalse(guard.shouldRedirect())
        assertFalse(guard.isTripped)
        assertTrue(store.saved.times.isEmpty())
        assertEquals(now, store.saved.retriedAt)
        assertFalse(guard.shouldRedirect()) // cleared once, stays cleared
    }

    @Test
    fun trip_clearsAfter24h() {
        trip()
        now += 24 * hour
        assertFalse(guard.shouldRedirect())
        assertFalse(guard.isTripped)
    }

    @Test
    fun retryTripsAgain_withinSeconds_ifLoopContinues() {
        trip()
        boot += 1
        assertFalse(guard.shouldRedirect()) // automatic retry of the normal launcher
        guard.recordCrash("A")
        assertFalse(guard.isTripped) // one crash after the retry is not enough
        now += 2_000; guard.recordCrash("A")
        now += 2_000; guard.recordCrash("A")
        assertTrue(guard.shouldRedirect()) // the 3-crash rule trips it again, on this boot
        now += 23 * hour
        assertTrue(guard.shouldRedirect()) // and no second automatic retry on the same boot within 24 h
    }

    @Test
    fun bootCountUnavailable_fallsBackToTimeOnly() {
        boot = -1
        trip()
        assertTrue(guard.shouldRedirect())
        now += 23 * hour
        assertTrue(guard.shouldRedirect())
        now += hour
        assertFalse(guard.shouldRedirect())
    }

    @Test
    fun crashInSafeMode_setsFlag() {
        trip()
        guard.recordSafeModeCrash("InflateException")
        assertTrue(store.saved.safeModeCrashed)
        assertEquals("InflateException", store.saved.lastException)
        assertEquals(3, store.saved.times.size) // not counted as a home crash
    }

    @Test
    fun flagSet_redirectClearsTripAndFlag() {
        trip()
        guard.recordSafeModeCrash("X")
        assertFalse(guard.shouldRedirect()) // HomeActivity runs instead of a broken safe mode
        assertFalse(store.saved.safeModeCrashed)
        assertFalse(store.saved.tripped)
        assertTrue(store.saved.times.isEmpty())
    }

    @Test
    fun flagSet_noInfiniteLoop() {
        // Worst case: home always crashes and safe mode always crashes. Every HOME press must make
        // progress: safe mode is shown only after three fresh home crashes, never twice in a row.
        var redirects = 0
        var homeRuns = 0
        repeat(10) {
            repeat(3) {
                assertFalse("home runs until it trips", guard.shouldRedirect())
                homeRuns++
                guard.recordCrash("HomeCrash"); now += 2_000
            }
            assertTrue(guard.shouldRedirect()); redirects++
            guard.recordSafeModeCrash("SafeCrash"); now += 2_000
            assertFalse("a crashed safe mode is not shown again", guard.shouldRedirect())
            assertFalse(store.saved.tripped)
        }
        assertEquals(10, redirects)
        assertEquals(30, homeRuns)
    }

    @Test
    fun history_isCapped_andNamesAreShort() {
        repeat(20) { guard.recordCrash("X".repeat(500)); now += 200_000 }
        assertEquals(CrashGuard.MAX_TIMES, store.saved.times.size)
        assertEquals(CrashGuard.MAX_NAME, store.saved.lastException!!.length)
    }

    @Test
    fun brokenStore_neverThrows() {
        val broken = object : CrashStore {
            override fun read(): CrashHistory = throw IllegalStateException("disk")
            override fun write(history: CrashHistory) = throw IllegalStateException("disk")
        }
        val g = CrashGuard(broken, { now }, { boot })
        g.recordCrash("A")
        g.recordSafeModeCrash("A")
        g.reset()
        assertFalse(g.isTripped)
        assertFalse(g.shouldRedirect())
    }

    @Test
    fun historyCodec_roundTrips_andToleratesGarbage() {
        val h = CrashHistory(listOf(1L, 2L, 3L), "Boom", tripped = true)
        assertEquals(h.times, CrashHistory.decodeTimes(CrashHistory.encodeTimes(h.times)))
        assertEquals(listOf(5L, 7L), CrashHistory.decodeTimes("5,x,,7"))
        assertTrue(CrashHistory.decodeTimes(null).isEmpty())
    }

    @Test
    fun gate_decisions() {
        val day = 24 * hour
        assertEquals(SafeModeGate.Decision.HOME, SafeModeGate.decide(CrashHistory(), now, boot))
        val tripped = CrashHistory(listOf(1L), "A", tripped = true, trippedAt = now, tripBoot = boot)
        assertEquals(SafeModeGate.Decision.SAFE_MODE, SafeModeGate.decide(tripped, now + day - 1, boot))
        assertEquals(SafeModeGate.Decision.RETRY_HOME, SafeModeGate.decide(tripped, now + day, boot))
        assertEquals(SafeModeGate.Decision.RETRY_HOME, SafeModeGate.decide(tripped, now, boot + 1))
        assertEquals(SafeModeGate.Decision.RETRY_HOME, SafeModeGate.decide(tripped.copy(safeModeCrashed = true), now, boot))
        assertEquals(SafeModeGate.Decision.SAFE_MODE, SafeModeGate.decide(tripped.copy(tripBoot = -1), now, boot))
    }
}
