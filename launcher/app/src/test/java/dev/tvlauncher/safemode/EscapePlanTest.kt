package dev.tvlauncher.safemode

import dev.tvlauncher.safemode.EscapePlan.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscapePlanTest {
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

    private fun trip() { repeat(3) { guard.recordCrash("Home"); now += 2_000 } }

    @Test
    fun notTripped_isHome() {
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun tripped_isFullSafeMode() {
        trip()
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
        assertTrue(guard.shouldRedirect())
    }

    @Test
    fun crashInSafeMode_isRecordedApartFromHomeCrashes() {
        trip()
        guard.recordSafeModeCrash("InflateException")
        assertEquals(1, store.saved.safeModeCrashTimes.size)
        assertEquals(3, store.saved.times.size)
        assertEquals("InflateException", store.saved.lastException)
    }

    @Test
    fun safeModeCrashOnce_forcesMinimalScreen() {
        trip()
        guard.recordSafeModeCrash("X"); now += 2_000
        assertEquals(Level.SAFE_MODE_MINIMAL, guard.decide())
        assertEquals(Level.SAFE_MODE_MINIMAL, guard.decide()) // stable on repeated HOME presses
    }

    @Test
    fun safeModeCrashTwiceWithinTenMinutes_homeRedirectsToSystemSettings() {
        trip()
        guard.recordSafeModeCrash("X"); now += 5 * 60_000
        guard.recordSafeModeCrash("X"); now += 2_000
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        assertTrue(store.saved.escapedToSettings)
        assertEquals(boot, store.saved.escapeBoot)
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide()) // every HOME press until reboot / 24 h
    }

    @Test
    fun safeModeCrashesMoreThanTenMinutesApart_staysMinimal() {
        trip()
        guard.recordSafeModeCrash("X"); now += 10 * 60_000 + 1
        guard.recordSafeModeCrash("X")
        assertEquals(Level.SAFE_MODE_MINIMAL, guard.decide())
    }

    @Test
    fun escapedToSettings_clearsAfterReboot() {
        trip(); guard.recordSafeModeCrash("X"); guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        boot += 1
        assertEquals(Level.HOME, guard.decide())
        assertFalse(store.saved.escapedToSettings)
        assertFalse(store.saved.tripped)
        assertTrue(store.saved.safeModeCrashTimes.isEmpty())
    }

    @Test
    fun escapedToSettings_clearsAfter24h() {
        trip(); guard.recordSafeModeCrash("X"); guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        now += 24 * hour - 1
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        now += 1
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun clockMovedBackwards_expires() {
        trip()
        now -= 10 * hour
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun hugeForwardJump_expires() {
        trip()
        now += 365L * 24 * hour
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun escapeLadder_neverLoopsForever() {
        // Stubborn worst case: the home screen, the full safe mode and the minimal screen all crash
        // every time they run. Each step is one HOME press.
        val seen = ArrayList<Level>()
        repeat(50) {
            val level = guard.decide()
            seen += level
            when (level) {
                Level.HOME -> guard.recordCrash("Home")
                Level.SAFE_MODE_FULL, Level.SAFE_MODE_MINIMAL -> guard.recordSafeModeCrash("Safe")
                Level.SYSTEM_SETTINGS -> Unit // the system Settings app is the usable end point
            }
            now += 2_000
        }
        val first = seen.indexOf(Level.SYSTEM_SETTINGS)
        assertEquals(listOf(Level.HOME, Level.HOME, Level.HOME, Level.SAFE_MODE_FULL, Level.SAFE_MODE_MINIMAL), seen.take(first))
        assertTrue(seen.drop(first).all { it == Level.SYSTEM_SETTINGS })
        // Only a reboot (or 24 h) gives the normal launcher another try.
        boot += 1
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun tryNormalAgain_keepsSafeModeCrashes_soTheLadderContinues() {
        trip(); guard.recordSafeModeCrash("X")
        guard.reset() // user picked "Try normal launcher again" on the minimal screen
        assertEquals(Level.HOME, guard.decide())
        trip()
        guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
    }

    @Test
    fun brokenStore_failsOpenToHome() {
        val broken = object : CrashStore {
            override fun read(): CrashHistory = throw IllegalStateException("disk")
            override fun write(history: CrashHistory) = throw IllegalStateException("disk")
        }
        assertEquals(Level.HOME, CrashGuard(broken, { now }, { throw IllegalStateException() }).decide())
    }

    @Test
    fun pureDecide_matrix() {
        val day = 24 * hour
        val tripped = CrashHistory(listOf(1L), "A", tripped = true, trippedAt = now, tripBoot = boot)
        assertEquals(Level.HOME, EscapePlan.decide(CrashHistory(), now, boot).level)
        assertEquals(Level.SAFE_MODE_FULL, EscapePlan.decide(tripped, now + day - 1, boot).level)
        assertEquals(Level.HOME, EscapePlan.decide(tripped, now + day, boot).level)
        assertEquals(Level.HOME, EscapePlan.decide(tripped, now, boot + 1).level)
        assertEquals(Level.SAFE_MODE_FULL, EscapePlan.decide(tripped.copy(tripBoot = -1), now, boot).level)
        assertEquals(Level.SAFE_MODE_FULL, EscapePlan.decide(tripped, now, -1).level)
        assertEquals(Level.HOME, EscapePlan.decide(tripped, now - 1, boot).level) // clock went back
        val once = tripped.copy(safeModeCrashTimes = listOf(now))
        assertEquals(Level.SAFE_MODE_MINIMAL, EscapePlan.decide(once, now + 1_000, boot).level)
        val twice = tripped.copy(safeModeCrashTimes = listOf(now, now + 1_000))
        assertEquals(Level.SYSTEM_SETTINGS, EscapePlan.decide(twice, now + 2_000, boot).level)
    }
}
