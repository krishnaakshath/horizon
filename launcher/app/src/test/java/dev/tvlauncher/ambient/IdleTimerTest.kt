package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdleTimerTest {
    private var now = 1_000L
    private fun timer(ms: Long = 10_000L) = IdleTimer(ms) { now }

    @Test fun notIdleBeforeTimeout_idleExactlyAtTimeout() {
        val t = timer()
        now += 9_999
        assertFalse(t.isIdle())
        assertEquals(1L, t.remainingMs())
        now += 1
        assertTrue(t.isIdle())
        assertEquals(0L, t.remainingMs())
    }

    @Test fun activity_resetsTheCountdown() {
        val t = timer()
        now += 9_000
        t.onActivity()
        now += 9_000
        assertFalse(t.isIdle())
        assertEquals(1_000L, t.remainingMs())
        now += 1_000
        assertTrue(t.isIdle())
    }

    @Test fun pausedNeverIdle_andResumeRestartsFullTimeout() {
        val t = timer()
        t.setPaused(IdleTimer.PAUSE_SPOTLIGHT, true)
        now += 60_000
        assertFalse(t.isIdle())
        assertEquals(-1L, t.remainingMs())
        t.setPaused(IdleTimer.PAUSE_SPOTLIGHT, false)
        assertFalse(t.isIdle())
        assertEquals(10_000L, t.remainingMs())
        now += 10_000
        assertTrue(t.isIdle())
    }

    @Test fun multiplePauseFlags_allMustClear() {
        val t = timer()
        t.setPaused(IdleTimer.PAUSE_SPOTLIGHT, true)
        t.setPaused(IdleTimer.PAUSE_DIALOG, true)
        t.setPaused(IdleTimer.PAUSE_SPOTLIGHT, false)
        now += 60_000
        assertFalse(t.isIdle())
        t.setPaused(IdleTimer.PAUSE_DIALOG, false)
        now += 10_000
        assertTrue(t.isIdle())
    }

    @Test fun backgroundStop_disables_untilStart() {
        val t = timer()
        t.stop()
        now += 60_000
        assertFalse(t.isIdle())
        t.start()
        assertEquals(10_000L, t.remainingMs())
    }

    @Test fun consume_firesOnce_untilActivity() {
        val t = timer()
        now += 10_000
        assertTrue(t.consumeIdle())
        assertFalse(t.consumeIdle())
        assertFalse(t.isIdle())
        t.onActivity()
        now += 10_000
        assertTrue(t.consumeIdle())
    }

    @Test fun zeroOrNegativeTimeout_disabled() {
        val t = IdleTimer(0) { now }
        now += 1_000_000
        assertFalse(t.isIdle())
        assertEquals(-1L, t.remainingMs())
    }

    @Test fun clockGoingBackwards_doesNotTrigger() {
        val t = timer()
        now -= 50_000
        assertFalse(t.isIdle())
        assertEquals(10_000L, t.remainingMs())
    }

    @Test fun setTimeout_appliesFromLastActivity() {
        val t = timer()
        now += 5_000
        t.setTimeout(4_000)
        assertTrue(t.isIdle())
    }
}
