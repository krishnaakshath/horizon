package dev.tvlauncher.browser.net

import dev.tvlauncher.browser.net.CachePolicy.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class CachePolicyTest {
    private val day = 24L * 60 * 60 * 1000

    @Test fun expiredEntries_evicted() {
        val now = 100 * day
        val e = listOf(Entry("old", 10, now - 31 * day), Entry("new", 10, now - 1 * day))
        assertEquals(listOf("old"), CachePolicy.evict(e, maxBytes = 1000, ttlMs = 30 * day, now = now))
    }

    @Test fun overBudget_evictsLeastRecentlyUsedFirst() {
        val now = 100 * day
        val e = listOf(Entry("a", 400, now - 3000), Entry("b", 400, now - 1000), Entry("c", 400, now - 2000))
        assertEquals(listOf("a"), CachePolicy.evict(e, maxBytes = 1000, ttlMs = 30 * day, now = now))
        assertEquals(listOf("a", "c"), CachePolicy.evict(e, maxBytes = 500, ttlMs = 30 * day, now = now))
    }

    @Test fun withinBudget_keepsAll() {
        assertEquals(emptyList<String>(), CachePolicy.evict(listOf(Entry("a", 1, 5)), 4L * 1024 * 1024, 30 * day, 10))
    }
}
