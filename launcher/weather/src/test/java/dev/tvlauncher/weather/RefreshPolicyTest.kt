package dev.tvlauncher.weather

import dev.tvlauncher.weather.RefreshPolicy.Decision
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class RefreshPolicyTest {
    private val min = 60_000L
    private val now = 10_000L * min

    private fun d(updatedAgoMin: Long?, attempts: List<Long> = emptyList(), failures: Int = 0, place: Boolean = true) =
        RefreshPolicy.decide(now, place, updatedAgoMin?.let { now - it * min }, attempts, failures)

    @Test fun staleness() {
        assertFalse(RefreshPolicy.isStale(now - 20 * min, now))
        assertTrue(RefreshPolicy.isStale(now - 20 * min - 1, now))
        assertTrue(RefreshPolicy.isStale(null, now))
        assertTrue(RefreshPolicy.isStale(0L, now))
        assertTrue("clock moved back", RefreshPolicy.isStale(now + min, now))
    }

    @Test fun decisions() {
        assertEquals(Decision.NO_PLACE, d(60, place = false))
        assertEquals(Decision.FRESH, d(5))
        assertEquals(Decision.REFRESH, d(21))
        assertEquals(Decision.REFRESH, d(null))
    }

    @Test fun minGapBetweenNetworkCalls() {
        assertEquals(Decision.TOO_SOON, d(60, listOf(now - 4 * min)))
        assertEquals(Decision.REFRESH, d(60, listOf(now - 5 * min)))
        assertEquals("future attempt (clock change) is ignored", Decision.REFRESH, d(60, listOf(now + 3 * min)))
    }

    @Test fun backoffGrowsAndCaps() {
        assertEquals(0L, RefreshPolicy.backoffMs(0))
        assertEquals(5 * min, RefreshPolicy.backoffMs(1))
        assertEquals(10 * min, RefreshPolicy.backoffMs(2))
        assertEquals(40 * min, RefreshPolicy.backoffMs(4))
        assertEquals(60 * min, RefreshPolicy.backoffMs(5))
        assertEquals(60 * min, RefreshPolicy.backoffMs(500))
        assertEquals(Decision.BACKING_OFF, d(60, listOf(now - 9 * min), failures = 2))
        assertEquals(Decision.REFRESH, d(60, listOf(now - 10 * min), failures = 2))
    }

    @Test fun neverMoreThanTwelvePerHour() {
        val eleven = (0 until 11).map { now - 6 * min - it * 4 * min }
        assertEquals(Decision.REFRESH, d(60, eleven.filter { now - it < 60 * min }))
        val twelve = (0 until 12).map { now - 6 * min - it * 4 * min }.filter { now - it < 60 * min }
        assertEquals(12, twelve.size)
        assertEquals(Decision.HOURLY_CAP, d(60, twelve))
    }

    @Test fun triggerActionNames() {
        assertEquals("tcl.sys.intent.action.STR_RESUME_COMPLETED", TriggerActions.TCL_WAKE)
        assertEquals("dev.tvlauncher.weather.action.REFRESH_IF_STALE", WeatherContract.ACTION_REFRESH_IF_STALE)
        assertEquals("dev.tvlauncher.permission.WEATHER_REFRESH", WeatherContract.PERMISSION_REFRESH)
    }

    @Test fun receiverGating() {
        assertTrue(TriggerActions.triggersRefresh("tcl.sys.intent.action.STR_RESUME_COMPLETED"))
        assertTrue(TriggerActions.triggersRefresh("android.intent.action.BOOT_COMPLETED"))
        assertTrue(TriggerActions.triggersRefresh("android.intent.action.SCREEN_ON"))
        assertTrue(TriggerActions.triggersRefresh("android.intent.action.USER_PRESENT"))
        assertTrue(TriggerActions.triggersRefresh("dev.tvlauncher.weather.action.REFRESH_IF_STALE"))
        assertFalse(TriggerActions.triggersRefresh(null))
        assertFalse(TriggerActions.triggersRefresh(""))
        assertFalse(TriggerActions.triggersRefresh("android.intent.action.SCREEN_OFF"))
        assertFalse(TriggerActions.triggersRefresh("dev.tvlauncher.weather.action.REFRESH"))
    }

    private class MemBacking : StoreBacking {
        private val m = ConcurrentHashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
    }

    @Test fun storeKeepsOnlyRecentAttemptsAndFailures() {
        val s = WeatherStore(MemBacking())
        assertEquals(emptyList<Long>(), s.attempts())
        s.recordAttempt(now - 2 * RefreshPolicy.HOUR_MS)
        s.recordAttempt(now - 10 * min)
        s.recordAttempt(now)
        assertEquals(listOf(now - 10 * min, now), s.attempts())
        assertEquals(0, s.failures())
        s.setFailures(3)
        assertEquals(3, s.failures())
        s.setFailures(-1)
        assertEquals(0, s.failures())
    }

    @Test fun refresherDecisionUsesStoredState() {
        val s = WeatherStore(MemBacking())
        assertEquals(Decision.NO_PLACE, Refresher.decide(s, now))
        s.setPlace(Place("A", null, null, 1.0, 1.0))
        assertEquals(Decision.REFRESH, Refresher.decide(s, now))
        s.recordAttempt(now - min)
        assertEquals(Decision.TOO_SOON, Refresher.decide(s, now))
    }
}
