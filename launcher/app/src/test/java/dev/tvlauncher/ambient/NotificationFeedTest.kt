package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotificationFeedTest {
    private fun n(
        key: String, pkg: String = "com.app", time: Long = 1L, ongoing: Boolean = false,
        media: Boolean = false, summary: Boolean = false, visibility: Int = NotifInfo.VISIBILITY_PUBLIC,
        title: String? = "Title $key", text: String? = "Body"
    ) = NotifInfo(key, pkg, "App $pkg", title, text, time, ongoing, media, summary, visibility)

    @Test fun filter_rejectsOngoingMediaSummary() {
        assertTrue(NotificationFilter.accept(n("a")))
        assertFalse(NotificationFilter.accept(n("a", ongoing = true)))
        assertFalse(NotificationFilter.accept(n("a", media = true)))
        assertFalse(NotificationFilter.accept(n("a", summary = true)))
    }

    @Test fun filter_rejectsOwnPackage_andEmptyContent() {
        assertFalse(NotificationFilter.accept(n("a", pkg = "dev.tvlauncher")))
        assertFalse(NotificationFilter.accept(n("a", title = null, text = null)))
        assertFalse(NotificationFilter.accept(n("a", title = " ", text = "")))
    }

    @Test fun feed_newestFirst_boundedAtFive() {
        val f = NotificationFeed()
        for (i in 1..8) f.post(n("k$i", time = i.toLong()))
        val s = f.snapshot()
        assertEquals(5, s.size)
        assertEquals(listOf("k8", "k7", "k6", "k5", "k4"), s.map { it.key })
    }

    @Test fun feed_dedupesByKey_movingToFront() {
        val f = NotificationFeed()
        f.post(n("a", time = 1)); f.post(n("b", time = 2)); f.post(n("a", time = 3, title = "new"))
        val s = f.snapshot()
        assertEquals(listOf("a", "b"), s.map { it.key })
        assertEquals("new", s[0].title)
    }

    @Test fun feed_ignoresRejected() {
        val f = NotificationFeed()
        f.post(n("a", ongoing = true))
        assertTrue(f.snapshot().isEmpty())
    }

    @Test fun feed_rejectedUpdate_removesExisting() {
        val f = NotificationFeed()
        f.post(n("a")); f.post(n("a", ongoing = true))
        assertTrue(f.snapshot().isEmpty())
    }

    @Test fun feed_removeAndClear() {
        val f = NotificationFeed()
        f.post(n("a")); f.post(n("b"))
        f.remove("a")
        assertEquals(listOf("b"), f.snapshot().map { it.key })
        f.clear()
        assertTrue(f.snapshot().isEmpty())
    }

    @Test fun chips_privateAndSecret_areAppNameOnly_evenWithShowText() {
        val f = NotificationFeed()
        f.post(n("s", visibility = NotifInfo.VISIBILITY_SECRET, time = 1))
        f.post(n("p", visibility = NotifInfo.VISIBILITY_PRIVATE, time = 2))
        val chips = f.chips(appNameOnly = false, showText = true, nowMs = 2)
        assertEquals(2, chips.size)
        chips.forEach { assertNull(it.title) }
        f.snapshot().forEach { assertNull(it.title); assertNull(it.text) }
    }

    @Test fun chips_publicShowsTitle_privacyModeShowsAppOnly() {
        val f = NotificationFeed()
        f.post(n("a", pkg = "com.mail", time = 1_000))
        val full = f.chips(appNameOnly = false, nowMs = 1_000 + 5 * 60_000)[0]
        assertEquals("App com.mail", full.app)
        assertEquals("Title a", full.title)
        assertEquals("5m", full.time)
        assertNull(f.chips(appNameOnly = true)[0].title)
    }

    @Test fun chips_textOnlyWhenOwnerChoseShowText_andPublic() {
        val f = NotificationFeed()
        f.post(n("a", title = "Ann", text = "See you at 6"))
        assertEquals("Ann", f.chips(false, showText = false)[0].title)
        assertEquals("Ann: See you at 6", f.chips(false, showText = true)[0].title)
        f.post(n("b", title = null, text = "Only text"))
        assertNull(f.chips(false, showText = false)[0].title)
        assertEquals("Only text", f.chips(false, showText = true)[0].title)
    }

    @Test fun chips_capAtThree() {
        val f = NotificationFeed()
        for (i in 1..5) f.post(n("x$i"))
        assertEquals(3, f.chips(false).size)
    }

    @Test fun titleAndText_cappedAt80Chars() {
        val f = NotificationFeed()
        f.post(n("a", title = "T".repeat(500), text = "B".repeat(500)))
        val stored = f.snapshot()[0]
        assertEquals(80, stored.title!!.length)
        assertEquals(80, stored.text!!.length)
        assertTrue(f.chips(false, showText = true)[0].title!!.length <= 80)
    }

    @Test fun disabledFeed_ignoresPosts_andDisablingClears() {
        val f = NotificationFeed(enabled = false)
        f.post(n("a"))
        assertTrue(f.snapshot().isEmpty())
        f.setEnabled(true)
        f.post(n("a"))
        assertEquals(1, f.snapshot().size)
        f.setEnabled(false)
        assertTrue(f.snapshot().isEmpty())
        assertFalse(f.isEnabled)
    }

    @Test fun sharedFeed_startsDisabled() {
        assertFalse(NotificationFeed.shared.isEnabled)
    }

    @Test fun chips_blankAppLabel_fallsBackToPackage() {
        val f = NotificationFeed()
        f.post(NotifInfo("k", "com.x", "  ", "T", null, 1, false, false, false, NotifInfo.VISIBILITY_PUBLIC))
        assertEquals("com.x", f.chips(true)[0].app)
    }

    @Test fun listener_isNotifiedOnChange_andCanBeRemoved() {
        val f = NotificationFeed()
        var calls = 0
        val l: () -> Unit = { calls++ }
        f.addListener(l)
        f.post(n("a")); f.remove("a"); f.remove("missing")
        assertEquals(2, calls)
        f.removeListener(l)
        f.post(n("b"))
        assertEquals(2, calls)
    }

    @Test fun feed_isThreadSafe() {
        val f = NotificationFeed()
        val pool = Executors.newFixedThreadPool(8)
        val done = CountDownLatch(8)
        repeat(8) { t ->
            pool.execute {
                repeat(1000) { i -> f.post(n("t$t-$i", time = i.toLong())); if (i % 3 == 0) f.remove("t$t-${i - 1}") }
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        pool.shutdown()
        val s = f.snapshot()
        assertTrue(s.size <= 5)
        assertEquals(s.size, s.map { it.key }.toSet().size)
    }

    @Test fun nullTitle_privateVisibility_noTextLeaks() {
        val f = NotificationFeed()
        f.post(n("a", visibility = NotifInfo.VISIBILITY_PRIVATE, title = null, text = "secret code 1234"))
        assertNull(f.chips(false, showText = true)[0].title)
        assertNull(f.chips(true)[0].title)
    }
}
