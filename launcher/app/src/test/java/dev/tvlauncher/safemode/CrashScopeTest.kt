package dev.tvlauncher.safemode

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashScopeTest {
    private val home = "dev.tvlauncher.HomeActivity"
    private val settings = "dev.tvlauncher.appsettings.AppSettingsActivity"
    private val insights = "dev.tvlauncher.insights.InsightsActivity"
    private val safe = "dev.tvlauncher.safemode.SafeModeActivity"
    private fun scope() = CrashScope(home, safe)

    private fun CrashScope.open(name: String) { onCreated(name); onStarted(name); onResumed(name) }

    @Test
    fun crashAtProcessStart_recorded() {
        assertTrue(scope().shouldRecordCrash())
    }

    @Test
    fun crashInHomeOnCreate_recorded() {
        val s = scope()
        s.onCreated(home) // dispatched from Activity.onCreate's super call, before the rest of onCreate
        assertTrue(s.shouldRecordCrash())
    }

    @Test
    fun crashWhileSettingsResumed_notRecorded() {
        val s = scope()
        s.open(home)
        s.onPaused(home)
        s.open(settings)
        assertFalse(s.shouldRecordCrash())
    }

    @Test
    fun crashAfterHomePaused_thenSettingsResumed_notRecorded() {
        val s = scope()
        s.open(home)
        s.onPaused(home)
        s.onCreated(settings) // crash while Settings is still being created counts as Settings
        assertFalse(s.shouldRecordCrash())
        s.onStarted(settings); s.onResumed(settings); s.onStopped(home)
        assertFalse(s.shouldRecordCrash())
    }

    @Test
    fun crashWhileInsightsResumed_notRecorded() {
        val s = scope()
        s.open(home); s.onPaused(home); s.open(insights)
        assertFalse(s.shouldRecordCrash())
    }

    @Test
    fun crashInHomeAfterReturningFromSettings_recorded() {
        val s = scope()
        s.open(home); s.onPaused(home); s.open(settings)
        s.onPaused(settings)
        s.onStarted(home); s.onResumed(home)
        s.onStopped(settings); s.onDestroyed(settings)
        assertTrue(s.shouldRecordCrash())
    }

    @Test
    fun homeInBackgroundBehindAnotherApp_recorded() {
        // Another app is in front (not ours): home was the last of our activities to resume.
        val s = scope()
        s.open(home); s.onPaused(home); s.onStopped(home)
        assertTrue(s.shouldRecordCrash())
    }

    /** Unknown or future screens of ours are not the home screen: their crashes must not lock the owner out. */
    @Test
    fun unknownActivity_defaultsToNotRecorded() {
        val s = scope()
        s.open("dev.tvlauncher.devices.DevicesActivity")
        assertFalse(s.shouldRecordCrash())
    }

    @Test
    fun crashInSafeMode_isSafeModeCrash_notHomeCrash() {
        val s = scope()
        s.onCreated(home); s.onCreated(safe) // redirect: home finishes inside onCreate
        assertTrue(s.inSafeMode())
        assertFalse(s.shouldRecordCrash())
        s.onDestroyed(home)
        assertTrue(s.inSafeMode())
    }
}
