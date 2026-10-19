package dev.tvlauncher.watch

import dev.tvlauncher.appsettings.RowGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchModelTest {
    private fun p(id: Long, pkg: String = "com.netflix.ninja", title: String = "Show $id", t: Long = id * 1000, pos: Long = 0, dur: Long = 0,
                  src: WatchSource = WatchSource.WATCH_NEXT, cid: String? = null, browsable: Boolean = true, weight: Int = 0) =
        WatchProgram(id, pkg, title, positionMs = pos, durationMs = dur, lastEngagementMs = t, source = src, contentId = cid, browsable = browsable, weight = weight)

    @Test fun ordersByLastEngagement_newestFirst_previewAfter() {
        val out = WatchModel.clean(listOf(p(1), p(3), p(2), p(9, src = WatchSource.PREVIEW, weight = 1), p(8, src = WatchSource.PREVIEW, weight = 5)), emptySet(), emptySet())
        assertEquals(listOf(3L, 2L, 1L, 8L, 9L), out.map { it.id })
    }

    @Test fun previewDroppedWhenSuggestionsOff() {
        val out = WatchModel.clean(listOf(p(1), p(2, src = WatchSource.PREVIEW)), emptySet(), emptySet(), includePreview = false)
        assertEquals(listOf(1L), out.map { it.id })
    }

    @Test fun dedupe_keepsNewest_andWatchNextBeatsPreview() {
        val out = WatchModel.clean(listOf(p(1, cid = "x"), p(5, cid = "x"), p(9, cid = "x", src = WatchSource.PREVIEW)), emptySet(), emptySet())
        assertEquals(listOf(5L), out.map { it.id })
        val byTitle = WatchModel.clean(listOf(p(1, title = "Dark"), p(2, title = " dark ")), emptySet(), emptySet())
        assertEquals(1, byTitle.size)
    }

    @Test fun filters_hiddenApps_blocked_unbrowsable_untitled() {
        val a = p(1); val b = p(2, pkg = "kids.app"); val c = p(3, browsable = false); val d = p(4, title = " ")
        val out = WatchModel.clean(listOf(a, b, c, d, p(5)), setOf("kids.app"), setOf(a.key))
        assertEquals(listOf(5L), out.map { it.id })
    }

    @Test fun grouping_andRowFor() {
        val list = WatchModel.clean((1L..12L).map { p(it, pkg = if (it % 2 == 0L) "a" else "b") }, emptySet(), emptySet())
        val g = WatchModel.byApp(list, 3)
        assertEquals(listOf("a", "b"), g.keys.toList())
        assertEquals(listOf(12L, 10L, 8L), g.getValue("a").map { it.id })
        assertEquals(6, WatchModel.rowFor(list, "b", false, 8).size)
        assertEquals(8, WatchModel.rowFor(list, null, true, 8).size)
        assertTrue(WatchModel.rowFor(list, null, false, 8).isEmpty())
    }

    @Test fun progressMath() {
        assertNull(WatchModel.progress(0, 100))
        assertNull(WatchModel.progress(10, 0))
        assertNull(WatchModel.progress(500, 100))
        assertEquals(0.25f, WatchModel.progress(25, 100)!!, 1e-6f)
        assertEquals(1f, WatchModel.progress(105, 100)!!, 1e-6f)
        assertEquals("23 min left", WatchModel.remainingLabel(p(1, pos = 60_000L * 22 + 1, dur = 60_000L * 45)))
        assertEquals("1 h 5 min left", WatchModel.remainingLabel(p(1, pos = 1, dur = 65 * 60_000L)))
        assertEquals("Watched", WatchModel.remainingLabel(p(1, pos = 99, dur = 100)))
        assertNull(WatchModel.remainingLabel(p(1)))
    }

    @Test fun episodeLine() {
        assertEquals("S2 · E5 · Pilot", WatchModel.episodeLine(WatchProgram(1, "a", "Show", episodeTitle = "Pilot", season = "2", episode = "5")))
        assertNull(WatchModel.episodeLine(WatchProgram(1, "a", "Show", episodeTitle = "show")))
    }

    @Test fun blocklist_uniqueCappedAndRoundTrips() {
        var s = WatchSettings()
        repeat(WatchSettings.MAX_BLOCKED + 5) { s = s.block("k$it") }
        s = s.block("k10")
        assertEquals(WatchSettings.MAX_BLOCKED, s.blocked.size)
        assertEquals("k10", s.blocked.last())
        assertEquals(s, WatchSettings.fromJson(s.toJson()))
        assertEquals(WatchSettings.DEFAULT, WatchSettings.fromJson("{broken"))
        assertTrue(s.unblockAll().blocked.isEmpty())
    }

    @Test fun dwellTimer() {
        val d = DwellTimer(400)
        assertEquals(-1, d.remaining(0))
        d.focus("a", 1000)
        assertFalse(d.due(1399))
        d.focus("a", 1300) // same key keeps its start
        assertTrue(d.due(1400))
        assertEquals("a", d.fire(1400))
        assertNull(d.fire(1500)) // once per focus
        d.focus("b", 1500)
        assertEquals(400, d.remaining(1500))
        d.clear()
        assertNull(d.fire(5000))
    }

    @Test fun thumbsAndLaunchRules() {
        assertTrue(ThumbRules.canOpen("content://x/y"))
        assertTrue(ThumbRules.canOpen("android.resource://pkg/1"))
        assertFalse(ThumbRules.canOpen("https://cdn/x.jpg"))
        assertFalse(ThumbRules.canOpen("file:///sdcard/x"))
        assertFalse(ThumbRules.canOpen(null))
        assertEquals(4, ThumbRules.sampleSize(1920, 1080, 352))
        assertTrue(ThumbRules.cacheBytes(true) < ThumbRules.cacheBytes(false))
        assertTrue(WatchLaunchRule.allowed("a", null, null))
        assertTrue(WatchLaunchRule.allowed("a", "a", "a"))
        assertFalse(WatchLaunchRule.allowed("a", "b", null))
        assertFalse(WatchLaunchRule.allowed("a", null, "b"))
        assertFalse(WatchLaunchRule.parseable("javascript:alert(1)"))
    }

    @Test fun access_andPageRows() {
        assertEquals(WatchAccess.UNAVAILABLE, WatchAccess.resolve(25, true, true, true, 3))
        assertEquals(WatchAccess.NEEDS_PERMISSION, WatchAccess.resolve(30, true, false, false, 0))
        assertEquals(WatchAccess.UNAVAILABLE, WatchAccess.resolve(30, true, true, false, 0))
        assertEquals(WatchAccess.EMPTY, WatchAccess.resolve(30, true, true, true, 0))
        assertEquals(WatchAccess.OK, WatchAccess.resolve(30, true, true, true, 2))
        val g = RowGuard(WatchPage.SPEC)
        WatchPage.SPEC.primaryRows.forEach { g.claim(it, advanced = false) }
        assertTrue(WatchPage.SPEC.withinBudget)
    }
}
