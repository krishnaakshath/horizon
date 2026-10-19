package dev.tvlauncher.weather

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class WeatherStoreTest {
    private class MemBacking : StoreBacking {
        private val m = ConcurrentHashMap<String, String>()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: String) { m[key] = value }
    }

    private fun place(n: Int) = Place("City$n", null, null, n.toDouble(), n.toDouble())
    private fun snap(t: Double) = WeatherSnapshot(t, t, 1, true, 50, 5.0, t + 1, t - 1, 1L, emptyList())

    // Separate store instances over the same backing, as in the real app.
    private fun stores(b: StoreBacking) = WeatherStore(b) to WeatherStore(b)

    @Test fun concurrentSetSnapshotAndSetPlace_doesNotRevertPlace() {
        repeat(300) { i ->
            val b = MemBacking()
            val (job, ui) = stores(b)
            val old = place(1); val chosen = place(2)
            ui.setPlace(old)
            val go = CountDownLatch(1)
            val t1 = thread { go.await(); job.setSnapshotIfPlace(old, snap(i.toDouble().coerceAtMost(50.0))) }
            val t2 = thread { go.await(); ui.setPlace(chosen) }
            go.countDown(); t1.join(); t2.join()
            val s = job.load()
            assertEquals("iteration $i", chosen, s.place)
            assertNull("old city's snapshot must not survive", s.snapshot)
        }
    }

    @Test fun concurrentUnitToggle_survivesSnapshotSave() {
        repeat(300) { i ->
            val b = MemBacking()
            val (job, ui) = stores(b)
            val p = place(1)
            ui.setPlace(p)
            val go = CountDownLatch(1)
            val t1 = thread { go.await(); job.setSnapshotIfPlace(p, snap(10.0)) }
            val t2 = thread { go.await(); ui.setUnit("F") }
            go.countDown(); t1.join(); t2.join()
            val s = job.load()
            assertEquals("iteration $i", "F", s.unit)
            assertNotNull(s.snapshot)
        }
    }

    @Test fun snapshotForOldPlace_isDropped_afterPlaceChange() {
        val st = WeatherStore(MemBacking())
        st.setPlace(place(1))
        st.setPlace(place(2))
        assertFalse(st.setSnapshotIfPlace(place(1), snap(5.0)))
        assertNull(st.load().snapshot)
        assertTrue(st.setSnapshotIfPlace(place(2), snap(6.0)))
        assertEquals(6.0, st.load().snapshot!!.tempC, 0.0)
    }

    @Test fun update_isAtomic() {
        val b = MemBacking()
        val a = WeatherStore(b); val c = WeatherStore(b)
        a.setPlace(place(1))
        a.setSnapshotIfPlace(place(1), snap(0.0))
        val n = 200
        fun inc(s: WeatherStore) = s.update { it.copy(snapshot = it.snapshot!!.copy(windKmh = it.snapshot.windKmh + 1)) }
        val t1 = thread { repeat(n) { inc(a) } }
        val t2 = thread { repeat(n) { inc(c) } }
        t1.join(); t2.join()
        assertEquals(5.0 + 2 * n, a.load().snapshot!!.windKmh, 0.0)
    }

    @Test fun notifiesOnlyOnChange() {
        var n = 0
        val st = WeatherStore(MemBacking()) { n++ }
        st.setPlace(place(1)); assertEquals(1, n)
        st.setUnit("C"); assertEquals(1, n)
        st.setUnit("F"); assertEquals(2, n)
    }

    @Test fun flags() {
        val st = WeatherStore(MemBacking())
        assertFalse(st.scheduleFailed())
        st.setScheduleFailed(true); assertTrue(st.scheduleFailed())
        assertEquals(0L, st.lastRequestMs()); st.setLastRequestMs(42); assertEquals(42L, st.lastRequestMs())
    }
}
