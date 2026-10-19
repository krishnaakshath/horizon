package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class TimeoutWeatherSourceTest {
    private val row = listOf(mapOf<String, Any?>("temp_c" to 20.0, "updated_ms" to 1L))
    private var now = 1_000_000L
    private val created = AtomicInteger()
    private val factory = ThreadFactory { r -> created.incrementAndGet(); Thread(r).apply { isDaemon = true } }

    /** Blocks until released; ignores interrupts when [stubborn] (a provider stuck in native code). */
    private inner class Blocking(val stubborn: Boolean) : WeatherSource {
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        override fun queryCurrent(): List<Map<String, Any?>>? {
            calls.incrementAndGet()
            while (true) {
                try { release.await(); return row } catch (e: InterruptedException) { if (!stubborn) return null }
            }
        }
        override fun queryDaily(): List<Map<String, Any?>>? = queryCurrent()
    }

    private fun source(d: WeatherSource, timeout: Long = 50) =
        TimeoutWeatherSource(d, timeoutMs = timeout, clock = { now }, threadFactory = factory)

    private fun waitIdle(s: TimeoutWeatherSource) {
        val until = System.nanoTime() + 2_000_000_000L
        while (s.isInFlight && System.nanoTime() < until) Thread.sleep(5)
        assertTrue("worker still busy", !s.isInFlight)
    }

    @Test fun fastSource_passesThrough_andCachesLastResult() {
        var answer: List<Map<String, Any?>>? = row
        val s = source(object : WeatherSource {
            override fun queryCurrent() = answer
            override fun queryDaily(): List<Map<String, Any?>>? = null
        }, timeout = 1_000)
        assertEquals(row, s.queryCurrent())
        assertNull(s.queryDaily())
        answer = null
        assertNull(s.queryCurrent()) // a real answer (provider gone) is passed through, not cached over
    }

    @Test fun timeout_opensBreaker_for60s() {
        val d = Blocking(stubborn = false)
        val s = source(d)
        assertNull(s.queryCurrent())
        assertEquals(1, d.calls.get())
        d.release.countDown()
        waitIdle(s)
        now += 59_999
        assertNull(s.queryCurrent())
        assertEquals("breaker open: delegate not called", 1, d.calls.get())
        assertTrue(s.isBreakerOpen)
    }

    @Test fun afterBreaker_queryAllowed() {
        val d = Blocking(stubborn = false)
        val s = source(d)
        assertNull(s.queryCurrent())
        d.release.countDown()
        waitIdle(s)
        now += 60_000
        assertEquals(row, s.queryCurrent())
        assertEquals(2, d.calls.get())
    }

    @Test fun whileInFlight_returnsLastCachedResult() {
        var block = false
        val gate = CountDownLatch(1)
        val s = source(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? {
                if (block) while (true) { try { gate.await(); return row } catch (e: InterruptedException) { } }
                return row
            }
            override fun queryDaily(): List<Map<String, Any?>>? = null
        })
        assertEquals(row, s.queryCurrent())
        block = true
        assertEquals("timed out: last good answer", row, s.queryCurrent())
        now += 61_000
        assertEquals("in flight: last good answer", row, s.queryCurrent())
        gate.countDown()
        waitIdle(s)
    }

    @Test fun throwingSource_isNull() {
        val s = source(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? = throw SecurityException("no")
            override fun queryDaily(): List<Map<String, Any?>>? = throw IllegalStateException("no")
        }, timeout = 1_000)
        assertNull(s.queryCurrent()); assertNull(s.queryDaily())
    }

    @Test fun readerOverHungSource_returnsNullWithinTimeout() {
        val d = Blocking(stubborn = false)
        val t0 = System.nanoTime()
        assertNull(WeatherReader(source(d)).read())
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0) < 2_000)
        d.release.countDown()
    }

    @Test fun stuckCall_afterBreaker_replacesWorker_secondThread() {
        val d = Blocking(stubborn = true)
        val s = source(d)
        assertNull(s.queryCurrent())
        assertEquals(1, created.get())
        now += 60_000
        assertNull(s.queryCurrent())
        assertEquals("fresh worker for the next call", 2, created.get())
        assertEquals(2, d.calls.get())
        d.release.countDown()
    }

    @Test fun stuckCall_thirdAbandon_entersDegraded_noMoreThreads() {
        val d = Blocking(stubborn = true)
        val s = source(d)
        repeat(3) { assertNull(s.queryCurrent()); now += 60_000 }
        assertEquals(3, created.get())
        assertNull(s.queryCurrent())
        assertTrue(s.isDegraded)
        assertEquals(3, created.get())
        assertEquals(3, d.calls.get())
        d.release.countDown()
    }

    @Test fun breakerExpiry_withoutStuckCall_noNewThread() {
        val d = Blocking(stubborn = false)
        val s = source(d)
        assertNull(s.queryCurrent())
        waitIdle(s)
        now += 60_000
        d.release.countDown()
        assertEquals(row, s.queryCurrent())
        assertEquals(1, created.get())
    }

    @Test fun threadsCreated_atMost3_over1000Timeouts_stubborn() {
        val d = Blocking(stubborn = true)
        val s = source(d, timeout = 1)
        repeat(1000) { s.queryCurrent(); now += 60_001 }
        assertTrue("threads created: ${created.get()}", created.get() <= 3)
        d.release.countDown()
    }

    @Test fun staleCache_olderThan3h_notServed_whenDegraded() {
        var stuck = false
        val gate = CountDownLatch(1)
        val s = source(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? {
                if (stuck) while (true) { try { gate.await(); return row } catch (e: InterruptedException) { } }
                return row
            }
            override fun queryDaily(): List<Map<String, Any?>>? = null
        })
        assertEquals(row, s.queryCurrent())
        stuck = true
        assertEquals("fresh cache served while the breaker is open", row, s.queryCurrent())
        now += 3 * 3_600_000L + 1
        // Cache is now older than 3 h: never served, whether busy, broken or degraded.
        repeat(4) { assertNull(s.queryCurrent()); now += 60_000 }
        assertTrue(s.isDegraded)
        assertNull(s.queryCurrent())
        gate.countDown()
    }
}
