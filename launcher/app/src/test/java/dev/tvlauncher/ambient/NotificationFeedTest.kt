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

    @Test fun chips_privateAndSecret_showGenericTitleWithoutText() {
        val f = NotificationFeed()
        f.post(n("s", visibility = NotifInfo.VISIBILITY_SECRET, time = 1))
        f.post(n("p", visibility = NotifInfo.VISIBILITY_PRIVATE, time = 2))
        val chips = f.chips(appNameOnly = false)
        assertEquals(2, chips.size)
        chips.forEach { assertEquals("Notification", it.title) }
    }

    @Test fun chips_publicShowsTitle_privacyModeShowsAppOnly() {
        val f = NotificationFeed()
        f.post(n("a", pkg = "com.mail"))
        assertEquals(NotificationChip("App com.mail", "Title a"), f.chips(appNameOnly = false)[0])
        assertEquals(NotificationChip("App com.mail", null), f.chips(appNameOnly = true)[0])
    }

    @Test fun chips_missingTitle_usesText_andCapsAtThree() {
        val f = NotificationFeed()
        f.post(n("a", title = null, text = "Only text"))
        assertEquals("Only text", f.chips(false)[0].title)
        for (i in 1..5) f.post(n("x$i"))
        assertEquals(3, f.chips(false).size)
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
        val c = f.chips(false)[0]
        assertEquals("Notification", c.title)
        assertNull(f.chips(true)[0].title)
    }
}
