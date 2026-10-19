package dev.tvlauncher.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Locale
import java.util.TimeZone

class HistoryStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(max: Int = 200) = HistoryStore(java.io.File(tmp.root, "h.json"), max)

    @Test fun record_newestFirst_dedupesConsecutive() {
        val s = store()
        s.record("https://a.com/", "A", 1)
        s.record("https://a.com/", "A again", 2)
        s.record("https://b.com/", "B", 3)
        s.record("https://a.com/", "A", 4)
        assertEquals(listOf("https://a.com/", "https://b.com/", "https://a.com/"), s.items.map { it.url })
        assertEquals("A again", s.items[2].title)
        assertEquals(2L, s.items[2].time)
    }

    @Test fun record_capsAt200_dropsOldest() {
        val s = store()
        for (i in 1..250) s.record("https://s$i.com/", "S$i", i.toLong())
        assertEquals(200, s.items.size)
        assertEquals("https://s250.com/", s.items.first().url)
        assertEquals("https://s51.com/", s.items.last().url)
    }

    @Test fun record_rejectsNonHttp() {
        val s = store()
        s.record("about:blank", "", 1)
        s.record("javascript:alert(1)", "", 1)
        s.record("https://x.com/" + "q".repeat(3000), "", 1)
        assertTrue(s.items.isEmpty())
    }

    @Test fun updateTitle_latestEntry() {
        val s = store()
        s.record("https://a.com/", "", 1)
        s.updateTitle("https://a.com/", "Real title")
        assertEquals("Real title", s.items[0].title)
    }

    @Test fun persist_clear_andCorrupt() {
        val s = store()
        s.record("https://a.com/", "A", 1)
        s.save()
        assertEquals(1, store().items.size)
        s.clear(); s.save()
        assertTrue(store().items.isEmpty())
        java.io.File(tmp.root, "h.json").writeText("\u0000garbage")
        assertTrue(store().items.isEmpty())
    }

    @Test fun daySections_todayYesterdayThenDates() {
        val tz = TimeZone.getTimeZone("UTC")
        val day = 86_400_000L
        val now = 20_000 * day + 10 * 3_600_000L // 10:00 UTC
        val s = store()
        s.record("https://old.com/", "Old", now - 3 * day)
        s.record("https://y.com/", "Y", now - day)
        s.record("https://t1.com/", "T1", now - 3_600_000L)
        s.record("https://t2.com/", "T2", now)
        val sec = s.daySections(now, tz, Locale.US)
        assertEquals(listOf("Today", "Yesterday"), sec.take(2).map { it.first })
        assertEquals(listOf("T2", "T1"), sec[0].second.map { it.title })
        assertEquals(3, sec.size)
        assertTrue(sec[2].first.isNotBlank())
    }

    @Test fun recentHosts_uniqueNewestFirst() {
        val s = store()
        s.record("https://a.com/1", "", 1); s.record("https://b.com/", "", 2); s.record("https://www.a.com/2", "", 3)
        assertEquals(listOf("https://www.a.com/2", "https://b.com/"), s.recentSites(5).map { it.url })
    }
}
