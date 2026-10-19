package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.InstallOutcomeResolver.Reported
import org.junit.Assert.assertEquals
import org.junit.Test

class InstallStateTest {

    @Test fun lookupDecisionMatrix() {
        val found = InstalledLookupResult.Found("info")
        val nf = InstalledLookupResult.NotFound
        val failed = InstalledLookupResult.Failed("SecurityException")
        // found is always INSTALLED
        assertEquals(InstalledState.INSTALLED, InstalledLookupResult.state(found, 34, false))
        assertEquals(InstalledState.INSTALLED, InstalledLookupResult.state(found, 24, true))
        // not found: trusted before API 30, or when the APK is launchable (our <queries> would see it)
        assertEquals(InstalledState.NOT_INSTALLED, InstalledLookupResult.state(nf, 29, false))
        assertEquals(InstalledState.NOT_INSTALLED, InstalledLookupResult.state(nf, 30, true))
        assertEquals(InstalledState.NOT_INSTALLED, InstalledLookupResult.state(nf, 34, true))
        assertEquals(InstalledState.UNKNOWN, InstalledLookupResult.state(nf, 30, false))
        // a failed lookup is never "not installed"
        assertEquals(InstalledState.UNKNOWN, InstalledLookupResult.state(failed, 24, true))
        assertEquals(InstalledState.UNKNOWN, InstalledLookupResult.state(failed, 34, true))
    }

    private fun resolve(reported: Reported, prev: Long?, now: Long?, updated: Long?, expected: Long = 10, committed: Long = 1000) =
        InstallOutcomeResolver.resolve(reported, expected, prev, now, updated, committed)

    @Test fun cancelledOrTimedOutButActuallyInstalledIsSuccess() {
        assertEquals(Reported.SUCCESS, resolve(Reported.TIMED_OUT, prev = null, now = 10, updated = 2000))
        assertEquals(Reported.SUCCESS, resolve(Reported.CANCELLED, prev = 9, now = 10, updated = 500))
        // same-version reinstall: only the update time can prove it happened
        assertEquals(Reported.SUCCESS, resolve(Reported.TIMED_OUT, prev = 10, now = 10, updated = 1500))
    }

    @Test fun notInstalledStaysCancelledOrTimedOut() {
        assertEquals(Reported.TIMED_OUT, resolve(Reported.TIMED_OUT, prev = null, now = null, updated = null))
        assertEquals(Reported.CANCELLED, resolve(Reported.CANCELLED, prev = 9, now = 9, updated = 500))
        assertEquals(Reported.CANCELLED, resolve(Reported.CANCELLED, prev = 10, now = 10, updated = 500)) // untouched
        assertEquals(Reported.TIMED_OUT, resolve(Reported.TIMED_OUT, prev = null, now = 11, updated = 2000)) // other version
    }

    @Test fun successAndFailureAreNotRewritten() {
        assertEquals(Reported.FAILED, resolve(Reported.FAILED, prev = null, now = 10, updated = 2000))
        assertEquals(Reported.SUCCESS, resolve(Reported.SUCCESS, prev = null, now = null, updated = null))
    }

    @Test fun sessionJanitorAbandonsOnlyOurIdleSessions() {
        val s = listOf(
            SessionJanitor.SessionView(1, "dev.tvlauncher.store"),
            SessionJanitor.SessionView(2, "dev.tvlauncher.store"),
            SessionJanitor.SessionView(3, "com.other.store"),
            SessionJanitor.SessionView(4, null),
        )
        assertEquals(listOf(1, 2), SessionJanitor.toAbandon(s, "dev.tvlauncher.store", null))
        assertEquals(listOf(1), SessionJanitor.toAbandon(s, "dev.tvlauncher.store", 2))
        assertEquals(emptyList<Int>(), SessionJanitor.toAbandon(emptyList(), "dev.tvlauncher.store", null))
    }
}
