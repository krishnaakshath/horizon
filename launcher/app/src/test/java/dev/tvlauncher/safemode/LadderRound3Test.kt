package dev.tvlauncher.safemode

import dev.tvlauncher.safemode.EscapePlan.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LadderRound3Test {
    private class MemoryStore : CrashStore {
        var saved = CrashHistory()
        override fun read() = saved
        override fun write(history: CrashHistory) { saved = history }
    }

    private var now = 1_000_000_000L
    private var boot = -1 // BOOT_COUNT unavailable
    private var elapsed = 50_000_000L
    private val store = MemoryStore()
    private val guard = CrashGuard(store, { now }, { boot }, { elapsed })
    private val home = "dev.tvlauncher.HomeActivity"
    private val safe = "dev.tvlauncher.safemode.SafeModeActivity"

    private fun tick(ms: Long) { now += ms; elapsed += ms }
    private fun trip() { repeat(3) { guard.recordCrash("Home"); tick(2_000) } }

    // I3 (a): reboot detection without BOOT_COUNT.

    @Test
    fun rebootDetectedViaElapsedRealtime_whenBootCountUnavailable() {
        trip()
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
        assertEquals(elapsed - 2_000, store.saved.tripElapsed)
        now += 60_000; elapsed = 30_000 // the TV restarted: uptime is back near zero
        assertEquals(Level.HOME, guard.decide())
        assertFalse(store.saved.tripped)
    }

    @Test
    fun elapsedRealtimeMonotonic_notRebooted() {
        trip()
        tick(5 * 3_600_000L)
        assertEquals(Level.SAFE_MODE_FULL, guard.decide())
    }

    @Test
    fun settingsEscape_alsoEndsOnRebootViaElapsedRealtime() {
        trip(); guard.recordSafeModeCrash("X"); guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        tick(10 * 60_000)
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        elapsed = 20_000
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun unknownElapsed_isIgnored() {
        assertFalse(EscapePlan.expired(now, -1, -1L, now + 1_000, -1, 5L))
        assertFalse(EscapePlan.expired(now, -1, 9_000L, now + 1_000, -1, -1L))
        assertTrue(EscapePlan.expired(now, -1, 9_000L, now + 1_000, -1, 8_999L))
    }

    // I3 (b): the terminal level prefers a usable stock launcher.

    @Test
    fun stockLauncherEnabled_levelThree_startsStockLauncherNotSettings() {
        assertEquals(
            listOf(EscapePlan.Terminal.STOCK_LAUNCHER, EscapePlan.Terminal.SYSTEM_SETTINGS, EscapePlan.Terminal.SAFE_MODE_MINIMAL),
            EscapePlan.terminalTargets(stockLauncherAvailable = true)
        )
    }

    @Test
    fun stockLauncherMissing_levelThree_startsSettings() {
        assertEquals(
            listOf(EscapePlan.Terminal.SYSTEM_SETTINGS, EscapePlan.Terminal.SAFE_MODE_MINIMAL),
            EscapePlan.terminalTargets(stockLauncherAvailable = false)
        )
    }

    // I2: attribution of crashes around the redirect.

    @Test
    fun crashBetweenRedirectAndSafeModeCreate_countsAsSafeModeCrash() {
        val scope = CrashScope(home, safe)
        scope.onCreated(home)
        scope.onCreated(safe) // the redirect announces safe mode right before starting it
        assertEquals(CrashAttribution.Kind.SAFE_MODE, CrashAttribution.classify(scope, tripped = true))
    }

    @Test
    fun crashWhileTrippedAndHomeAttributed_countsAsSafeMode() {
        val scope = CrashScope(home, safe)
        scope.onCreated(home) // e.g. a class-init or theme crash of safe mode charged to home
        assertEquals(CrashAttribution.Kind.SAFE_MODE, CrashAttribution.classify(scope, tripped = true))
        // Process start (no activity yet) while tripped: the system restoring safe mode's task.
        assertEquals(CrashAttribution.Kind.SAFE_MODE, CrashAttribution.classify(CrashScope(home, safe), tripped = true))
    }

    @Test
    fun crashWhileNotTripped_home_countsAsHome() {
        val scope = CrashScope(home, safe)
        scope.onCreated(home)
        assertEquals(CrashAttribution.Kind.HOME, CrashAttribution.classify(scope, tripped = false))
        assertEquals(CrashAttribution.Kind.HOME, CrashAttribution.classify(CrashScope(home, safe), tripped = false))
    }

    @Test
    fun crashInOtherScreen_ignored_evenWhenTripped() {
        val scope = CrashScope(home, safe)
        scope.onCreated("dev.tvlauncher.appsettings.AppSettingsActivity")
        assertEquals(CrashAttribution.Kind.IGNORE, CrashAttribution.classify(scope, tripped = true))
        assertEquals(CrashAttribution.Kind.IGNORE, CrashAttribution.classify(scope, tripped = false))
    }

    /**
     * M5, API 24-28: there is no onActivityPreCreated, so a crash in safe mode before its
     * super.onCreate returns is charged to the previous screen (home) or to process start. The
     * tripped rule still turns it into a safe-mode crash, so the ladder keeps climbing.
     */
    @Test
    fun apiBelow29_crashBeforeSuperOnCreate_fallsBackToTrippedRule() {
        val scope = CrashScope(home, safe)
        scope.onCreated(home) // HomeActivity.onCreate ran the redirect; safe mode has not reported yet
        assertFalse(scope.inSafeMode())
        assertEquals(CrashAttribution.Kind.SAFE_MODE, CrashAttribution.classify(scope, tripped = true))
        // And it climbs: two such crashes reach the terminal level.
        trip()
        guard.recordSafeModeCrash("ThemeCrash"); tick(1_000)
        assertEquals(Level.SAFE_MODE_MINIMAL, guard.decide())
        guard.recordSafeModeCrash("ThemeCrash"); tick(1_000)
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
    }

    // M4: the debug reset clears the whole ladder; the user's reset keeps recent safe-mode crashes.

    @Test
    fun clearAll_clearsEverythingInTheLadder() {
        trip(); guard.recordSafeModeCrash("X"); guard.recordSafeModeCrash("X")
        assertEquals(Level.SYSTEM_SETTINGS, guard.decide())
        guard.clearAll()
        assertEquals(CrashHistory(), store.saved)
        assertEquals(Level.HOME, guard.decide())
    }

    @Test
    fun userReset_keepsSafeModeCrashes() {
        trip(); guard.recordSafeModeCrash("X")
        guard.reset()
        assertEquals(1, store.saved.safeModeCrashTimes.size)
        assertFalse(store.saved.tripped)
        assertTrue(store.saved.times.isEmpty())
    }
}
