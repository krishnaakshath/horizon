package dev.tvlauncher.ambient

import dev.tvlauncher.ambient.StuckProviderPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StuckProviderPolicyTest {
    private val p = StuckProviderPolicy(breakerMs = 60_000, maxAbandoned = 2)

    @Test fun healthy_runs_thenBusyWhileOutstanding() {
        assertEquals(Decision.RUN, p.beforeQuery(0))
        assertEquals(Decision.REJECT_BUSY, p.beforeQuery(1))
        assertTrue(p.tryStart(p.generation))
        p.onFinished(p.generation)
        assertEquals(Decision.RUN, p.beforeQuery(2))
    }

    @Test fun timeout_opensBreakerFor60s() {
        p.beforeQuery(0); val g = p.generation; p.tryStart(g)
        p.onTimeout(g, 1_000)
        assertEquals(Decision.REJECT_BREAKER, p.beforeQuery(60_999))
        p.onFinished(g) // the call came back late (interruptible provider)
        assertEquals(Decision.RUN, p.beforeQuery(61_000))
        assertEquals(0, p.abandonedAlive)
    }

    @Test fun stuckAfterBreaker_abandonsOntoFreshWorker_upToTwo_thenDegraded() {
        var now = 0L
        p.beforeQuery(now); p.tryStart(p.generation); p.onTimeout(p.generation, now)
        now += 60_000
        assertEquals(Decision.RUN_ON_FRESH_WORKER, p.beforeQuery(now))
        assertEquals(1, p.abandonedAlive)
        p.tryStart(p.generation); p.onTimeout(p.generation, now)
        now += 60_000
        assertEquals(Decision.RUN_ON_FRESH_WORKER, p.beforeQuery(now))
        assertEquals(2, p.abandonedAlive)
        p.tryStart(p.generation); p.onTimeout(p.generation, now)
        now += 60_000
        assertEquals(Decision.DEGRADED, p.beforeQuery(now))
        assertTrue(p.isDegraded)
        assertEquals("permanent", Decision.DEGRADED, p.beforeQuery(now + 10 * 3_600_000L))
    }

    @Test fun abandonedCallFinishing_freesASlot_lateResultIgnored() {
        var now = 0L
        p.beforeQuery(now); val g1 = p.generation; p.tryStart(g1); p.onTimeout(g1, now)
        now += 60_000
        assertEquals(Decision.RUN_ON_FRESH_WORKER, p.beforeQuery(now))
        val g2 = p.generation
        p.tryStart(g2)
        p.onFinished(g1) // the abandoned thread finally returned
        assertEquals(0, p.abandonedAlive)
        assertEquals("g2 still in flight", Decision.REJECT_BUSY, p.beforeQuery(now + 1))
        p.onFinished(g2)
        assertEquals(Decision.RUN, p.beforeQuery(now + 2))
    }

    @Test fun timeoutBeforePickUp_neverRunsLater_andAdmitsNoSecondCall() {
        // SUBMITTED -> timeout before the worker picked it up: the task must not start later.
        p.beforeQuery(0); val g = p.generation
        p.onTimeout(g, 0)
        assertFalse(p.tryStart(g))
        assertEquals(StuckProviderPolicy.Call.IDLE, p.call)
        // After the breaker: a normal RUN on the same worker, nothing abandoned.
        assertEquals(Decision.RUN, p.beforeQuery(60_000))
        assertEquals(0, p.abandonedAlive)
    }

    @Test fun staleGeneration_cannotStartOrFinishTheCurrentCall() {
        p.beforeQuery(0); val g1 = p.generation; p.tryStart(g1); p.onTimeout(g1, 0)
        p.beforeQuery(60_000); val g2 = p.generation
        assertFalse(p.tryStart(g1))
        assertTrue(p.tryStart(g2))
        p.onTimeout(g1, 60_001) // late timeout report for the old call: ignored
        assertEquals(Decision.REJECT_BUSY, p.beforeQuery(60_002))
    }
}
