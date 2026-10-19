package dev.tvlauncher.safemode

import dev.tvlauncher.safemode.EscapePlan.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RebootDetectionTest {
    private class MemoryStore : CrashStore {
        var saved = CrashHistory()
        override fun read() = saved
        override fun write(history: CrashHistory) { saved = history }
    }

    private val min = 60_000L
    private var now = 1_000_000_000L
    private var boot = -1
    private var elapsed = 10 * min
    private var bootId: String? = null
    private val store = MemoryStore()
    private val guard = CrashGuard(store, { now }, { boot }, { elapsed }, { bootId })

    private fun tick(ms: Long) { now += ms; elapsed += ms }
    private fun trip() { repeat(3) { guard.recordCrash("Home"); tick(1_000) } }

    @Test
    fun rebootWhenNewUptimeExceedsOld_detectedViaBootStartDrift() {
        trip() // tripped at about 10 min of uptime
        tick(5 * min)
        // Reboot 5 minutes later; HOME pressed after 20 minutes of the new uptime.
        now += 20 * min; elapsed = 20 * min
        assertTrue(elapsed > store.saved.tripElapsed)
        assertEquals(Level.HOME, guard.decide())
        assertFalse(store.saved.tripped)
    }

    @Test
    fun smallClockDrift_notReboot() {
        trip()
        tick(30 * min)
        now += 20_000 // NTP step of 20 s
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
    }

    /** A big wall-clock step looks like a reboot and expires the trip. That is safe: a real loop re-trips in seconds. */
    @Test
    fun bigClockStepForward_treatedAsExpiry_safe() {
        trip()
        now += 2 * 60 * min // clock set two hours ahead, no reboot
        assertEquals(Level.HOME, guard.decide())
        repeat(3) { guard.recordCrash("Home"); tick(1_000) }
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
    }

    @Test
    fun bootIdDiffers_reboot() {
        bootId = "1b2c3d4e-0000-4000-8000-000000000001"
        trip()
        assertEquals(bootId, store.saved.tripBootId)
        tick(min)
        bootId = "1b2c3d4e-0000-4000-8000-000000000002"
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun bootIdSame_noReboot_evenIfBootCountMinusOne() {
        bootId = "1b2c3d4e-0000-4000-8000-000000000001"
        trip()
        now += 10 * min // clock step that the arithmetic alone would call a reboot
        elapsed += min
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
    }

    @Test
    fun bootIdUnreadable_fallsBackToArithmetic() {
        bootId = "1b2c3d4e-0000-4000-8000-000000000001"
        trip()
        bootId = null // unreadable now
        now += 20 * min; elapsed = 20 * min
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun oldStateWithoutNewFields_loads_andDoesNotExpireSpuriously() {
        // State written before uptime, boot id and boot count were stored.
        store.saved = CrashHistory(listOf(now - 2_000, now - 1_000, now), "Home", tripped = true, trippedAt = now)
        bootId = "1b2c3d4e-0000-4000-8000-000000000001"; boot = 12
        tick(23 * 60 * min)
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
        tick(60 * min)
        assertEquals(Level.HOME, guard.decide()) // only the 24 h rule applies
    }

    @Test
    fun bootCountDiffers_stillWins() {
        boot = 5; bootId = "1b2c3d4e-0000-4000-8000-000000000001"
        trip()
        boot = 6 // same boot id, same uptime line: BOOT_COUNT decides first
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun escape_usesTheSameRules() {
        trip(); guard.recordSafeModeCrash("X"); guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        tick(10 * min)
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        now += 30 * min; elapsed = 30 * min // reboot, new uptime above the old one
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun bootIdParse_validatesShape() {
        assertEquals("1b2c3d4e-0000-4000-8000-000000000001", BootId.parse(" 1b2c3d4e-0000-4000-8000-000000000001\n"))
        assertNull(BootId.parse(null))
        assertNull(BootId.parse(""))
        assertNull(BootId.parse("abc"))
        assertNull(BootId.parse("not-a-boot-id!"))
        assertNull(BootId.parse("a".repeat(65)))
    }

    @Test
    fun scopeResetsWhenSafeModeStopped() {
        val home = "dev.tvlauncher.HomeActivity"
        val safe = "dev.tvlauncher.safemode.SafeModeActivity"
        val scope = CrashScope(home, safe)
        scope.onCreated(home); scope.onCreated(safe); scope.onResumed(safe)
        scope.onPaused(safe); scope.onStopped(safe) // the user opened another app from safe mode
        assertEquals(CrashAttribution.Kind.IGNORE, CrashAttribution.classify(scope, tripped = true))
        scope.onStarted(safe); scope.onResumed(safe)
        assertEquals(CrashAttribution.Kind.SAFE_MODE, CrashAttribution.classify(scope, tripped = true))
        // "Try normal launcher again": home is already in charge when safe mode stops.
        scope.onCreated(home); scope.onStopped(safe)
        assertEquals(CrashAttribution.Kind.HOME, CrashAttribution.classify(scope, tripped = false))
    }
}
