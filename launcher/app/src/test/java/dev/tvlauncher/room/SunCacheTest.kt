package dev.tvlauncher.room

import org.junit.Assert.*
import org.junit.Test
import java.util.TimeZone

class SunCacheTest {
    private val ist = TimeZone.getTimeZone("Asia/Kolkata")
    private val day = 1792454400000L - 19_800_000L
    private val noon = day + 12 * 3_600_000L
    private val mumbai = SunRow(day, 1792458060000L, 1792499700000L, "Mumbai", 1L)
    private val mumbaiTimes = SunTimes(391, 1085)

    private class Harness(var next: () -> SunRead, start: Long) {
        var wall = start
        var elapsed = 1_000_000L
        var reads = 0
        val queued = ArrayList<Runnable>()
        var events = 0
        val cache = SunCache(
            reader = { reads++; next() },
            executor = { queued += it },
            wallClock = { wall },
            elapsed = { elapsed },
            zone = { TimeZone.getTimeZone("Asia/Kolkata") },
            post = { it.run() }
        ).also { it.addListener { events++ } }

        fun drain() { while (queued.isNotEmpty()) queued.removeAt(0).run() }
    }

    @Test fun neverReadsOnTheCallingThread() {
        val h = Harness({ SunRead(true, true, listOf(mumbai)) }, noon)
        assertNull(h.cache.times())        // nothing cached yet; read only queued
        assertEquals(0, h.reads)
        assertEquals(1, h.queued.size)
        assertNull(h.cache.times())        // no second read while one is in flight
        assertEquals(1, h.queued.size)
        h.drain()
        assertEquals(mumbaiTimes, h.cache.times())
        assertEquals(1, h.events)
    }

    @Test fun cachesUntilTtlOrNewDay() {
        val h = Harness({ SunRead(true, true, listOf(mumbai)) }, noon)
        h.cache.times(); h.drain()
        repeat(5) { h.elapsed += 60_000; h.wall += 60_000; h.cache.times() }
        assertTrue(h.queued.isEmpty())
        h.elapsed += 6 * 3_600_000L
        h.cache.times(); h.drain()
        assertEquals(2, h.reads)
        assertEquals(1, h.events)          // same data: no listener call
        h.wall = day + 86_400_000L + 60_000 // just after midnight
        h.elapsed += 60_000
        h.cache.times(); h.drain()
        assertEquals(3, h.reads)
    }

    @Test fun invalidateRereadsAndNotifiesOnChange() {
        var rows = listOf(mumbai)
        val h = Harness({ SunRead(true, true, rows) }, noon)
        h.cache.times(); h.drain()
        rows = listOf(mumbai.copy(sunriseMs = mumbai.sunriseMs!! + 60_000, location = "Pune"))
        h.cache.invalidate(); h.drain()
        assertEquals(SunTimes(392, 1085), h.cache.times())
        assertEquals("Pune", h.cache.status().city)
        assertEquals(2, h.events)
    }

    @Test fun failedReadKeepsLastGoodRowsAndRetriesLater() {
        var r = SunRead(true, true, listOf(mumbai))
        val h = Harness({ r }, noon)
        h.cache.times(); h.drain()
        r = SunRead.FAILED
        h.cache.invalidate(); h.drain()
        assertEquals(mumbaiTimes, h.cache.times())
        h.drain()
        assertEquals(2, h.reads)           // no hammering right after a failure
        h.elapsed += 11 * 60_000L
        h.cache.invalidate(); h.drain()
        assertEquals(3, h.reads)
    }

    @Test fun readerExceptionCountsAsFailure() {
        val h = Harness({ throw IllegalStateException("provider died") }, noon)
        h.cache.times(); h.drain()
        assertNull(h.cache.times())
        assertEquals(WeatherSunState.NO_TIMES, h.cache.status().state)
    }

    @Test fun uninstallOrNoCityClears() {
        var r = SunRead(true, true, listOf(mumbai))
        val h = Harness({ r }, noon)
        h.cache.times(); h.drain()
        r = SunRead(true, true, emptyList())
        h.cache.invalidate(); h.drain()
        assertNull(h.cache.times())
        assertEquals(WeatherSunState.NO_CITY, h.cache.status().state)
        r = SunRead.MISSING
        h.cache.invalidate(); h.drain()
        assertEquals(WeatherSunState.NOT_INSTALLED, h.cache.status().state)
    }

    @Test fun rejectedExecutorDoesNotWedge() {
        var reject = true
        val q = ArrayList<Runnable>()
        var elapsed = 0L
        val cache = SunCache(
            reader = { SunRead(true, true, listOf(mumbai)) },
            executor = { if (reject) throw java.util.concurrent.RejectedExecutionException() else q += it },
            wallClock = { noon }, elapsed = { elapsed }, zone = { ist }
        )
        assertNull(cache.times())
        reject = false
        elapsed += 11 * 60_000L
        cache.times()
        q.forEach { it.run() }
        assertEquals(mumbaiTimes, cache.times())
    }
}
