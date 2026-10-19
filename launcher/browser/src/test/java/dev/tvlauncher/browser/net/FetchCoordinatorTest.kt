package dev.tvlauncher.browser.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

class FetchCoordinatorTest {
    private class Env {
        val work = ArrayList<Runnable>()
        val main = ArrayList<Runnable>()
        var now = 0L
        var shut = false
        val cache = HashMap<String, String>()
        val c = FetchCoordinator<String>(
            execute = { r -> if (shut) throw RejectedExecutionException() else work.add(r) },
            post = { r -> main.add(r) },
            clock = { now },
            cache = object : FetchCoordinator.Cache<String> {
                override fun get(key: String) = cache[key]
                override fun put(key: String, value: String) { cache[key] = value }
                override fun clear() = cache.clear()
            },
            failTtlMs = 600_000
        )
        fun runAll() { while (work.isNotEmpty() || main.isNotEmpty()) { work.toList().also { work.clear() }.forEach { it.run() }; main.toList().also { main.clear() }.forEach { it.run() } } }
    }

    @Test fun result_cachedAndDelivered_once_forConcurrentRequests() {
        val e = Env()
        val got = ArrayList<String?>()
        var calls = 0
        e.c.load("k", { got.add(it) }) { calls++; "v" }
        e.c.load("k", { got.add(it) }) { calls++; "v2" }
        e.runAll()
        assertEquals(listOf("v", "v"), got)
        assertEquals(1, calls)
        assertEquals("v", e.cache["k"])
    }

    @Test fun clear_duringFetch_discardsResult() {
        val e = Env()
        val got = ArrayList<String?>()
        e.c.load("k", { got.add(it) }) { "stale" }
        e.c.clear()
        e.runAll()
        assertEquals(listOf<String?>(null), got)
        assertNull(e.cache["k"])
    }

    @Test fun failure_retriedAfterTtl_andClearForgetsFailures() {
        val e = Env()
        var calls = 0
        e.c.load("k", {}) { calls++; null }
        e.runAll()
        e.c.load("k", {}) { calls++; null }
        e.runAll()
        assertEquals(1, calls)
        e.now += 600_001
        e.c.load("k", {}) { calls++; null }
        e.runAll()
        assertEquals(2, calls)
        e.c.clear()
        e.c.load("k", {}) { calls++; null }
        e.runAll()
        assertEquals(3, calls)
    }

    @Test fun failedAt_isBounded() {
        val e = Env()
        repeat(500) { i -> e.c.load("k$i", {}) { null } }
        e.runAll()
        assertTrue(e.c.failedCount() <= FetchCoordinator.MAX_FAILED)
    }

    @Test fun afterShutdown_loadDeliversNull_noCrash() {
        val e = Env()
        e.shut = true
        val got = ArrayList<String?>()
        e.c.load("k", { got.add(it) }) { "v" }
        e.runAll()
        assertEquals(listOf<String?>(null), got)
    }
}
