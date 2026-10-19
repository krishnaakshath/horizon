package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TimeoutWeatherSourceTest {
    private val row = listOf(mapOf<String, Any?>("temp_c" to 20.0, "updated_ms" to 1L))

    @Test fun fastSource_passesThrough() {
        val s = TimeoutWeatherSource(object : WeatherSource {
            override fun queryCurrent() = row
            override fun queryDaily(): List<Map<String, Any?>>? = null
        }, timeoutMs = 1_000)
        assertEquals(row, s.queryCurrent())
        assertNull(s.queryDaily())
    }

    @Test fun hungSource_timesOut_andIsInterrupted() {
        val never = CountDownLatch(1)
        val interrupted = CountDownLatch(1)
        val s = TimeoutWeatherSource(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? {
                try { never.await() } catch (e: InterruptedException) { interrupted.countDown() }
                return row
            }
            override fun queryDaily(): List<Map<String, Any?>>? = row
        }, timeoutMs = 100)
        val t0 = System.nanoTime()
        assertNull(s.queryCurrent())
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000)
        assertTrue(interrupted.await(2, TimeUnit.SECONDS))
        // A hung query does not block the next one.
        assertEquals(row, s.queryDaily())
    }

    @Test fun throwingSource_isNull() {
        val s = TimeoutWeatherSource(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? = throw SecurityException("no")
            override fun queryDaily(): List<Map<String, Any?>>? = throw IllegalStateException("no")
        })
        assertNull(s.queryCurrent()); assertNull(s.queryDaily())
    }

    @Test fun readerOverHungSource_returnsNullWithinTimeout() {
        val s = TimeoutWeatherSource(object : WeatherSource {
            override fun queryCurrent(): List<Map<String, Any?>>? { Thread.sleep(10_000); return row }
            override fun queryDaily(): List<Map<String, Any?>>? = null
        }, timeoutMs = 50)
        assertNull(WeatherReader(s).read())
    }
}
