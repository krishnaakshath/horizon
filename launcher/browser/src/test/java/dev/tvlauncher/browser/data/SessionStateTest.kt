package dev.tvlauncher.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SessionStateTest {
    private fun model(): TabModel {
        val m = TabModel()
        m.active.url = "https://a.com/"; m.active.title = "A"; m.active.scrollY = 50
        m.open("https://b.com/")!!.title = "B"
        m.open("https://c.com/")!!.title = "C"
        m.switchTo(1)
        return m
    }

    @Test fun webNull_afterRendererGone_keepsLastUrl() {
        val m = model()
        SessionSnapshot.apply(m, web = null, showingStart = false)
        assertEquals("https://b.com/", m.active.url)
        assertEquals("B", m.active.title)
    }

    @Test fun liveWeb_updatesActiveTab() {
        val m = model()
        SessionSnapshot.apply(m, SessionSnapshot.Web("https://b.com/page", "Page", 300), showingStart = false)
        assertEquals("https://b.com/page", m.active.url)
        assertEquals(300, m.active.scrollY)
    }

    @Test fun liveWeb_withBadUrl_keepsPrevious() {
        val m = model()
        SessionSnapshot.apply(m, SessionSnapshot.Web("about:blank", null, 0), showingStart = false)
        assertEquals("https://b.com/", m.active.url)
    }

    @Test fun showingStart_blanksOnlyActiveTab() {
        val m = model()
        SessionSnapshot.apply(m, null, showingStart = true)
        assertNull(m.active.url)
        assertEquals(listOf("https://a.com/", null, "https://c.com/"), m.tabs.map { it.url })
    }

    @Test fun destroyWithin2sOfLoad_keepsLoadedUrl() {
        // Page loaded -> delayed save queued; user closes within 2 s -> onStop saves now and cancels the queue.
        val m = model()
        val queue = FakeQueue()
        val written = ArrayList<String>()
        val saver = DebouncedSaver(queue::post, queue::remove) { written.add(it) }
        SessionSnapshot.apply(m, SessionSnapshot.Web("https://b.com/loaded", "L", 0), false)
        saver.schedule(2000) { m.encode() }
        // onStop: snapshot while the WebView still exists, flush synchronously, cancel pending.
        SessionSnapshot.apply(m, SessionSnapshot.Web("https://b.com/loaded", "L", 0), false)
        saver.flushNow { m.encode() }
        saver.cancel()
        // onDestroy: WebView gone (web == null) - a late snapshot must not blank it either.
        SessionSnapshot.apply(m, null, showingStart = false)
        queue.runAll()
        assertEquals(1, written.size)
        assertEquals("https://b.com/loaded", TabModel.decode(written.last()).tabs[1].url)
        assertEquals("https://b.com/loaded", m.active.url)
    }

    @Test fun atomicWrite_usesTempAndRename() {
        val ops = RecordingOps()
        AtomicText.write(ops, File("/x/session.json"), "{}")
        assertEquals(listOf("write /x/session.json.tmp", "rename /x/session.json.tmp -> /x/session.json"), ops.log)
    }

    @Test fun atomicWrite_renameFails_replacesTarget() {
        val ops = RecordingOps(renameOk = false)
        AtomicText.write(ops, File("/x/s.json"), "{}")
        assertTrue(ops.log.contains("delete /x/s.json"))
        assertEquals("rename /x/s.json.tmp -> /x/s.json", ops.log.last())
    }

    @Test fun corruptSession_loadsEmpty() {
        val m = TabModel.decode("{\"tabs\":[{\"url\":")
        assertEquals(1, m.tabs.size)
        assertNull(m.active.url)
    }

    @Test fun afterClear_blanksInactiveTabs_keepsActiveUrl() {
        val m = model()
        val r = SessionState.afterClear(m)
        assertEquals(listOf(null, "https://b.com/", null), r.tabs.map { it.url })
        assertTrue(r.tabs.all { it.scrollY == 0 })
        assertEquals("", r.tabs[0].title)
        assertEquals(1, r.activeIndex)
        assertFalse(r.tabs[1].desktop)
    }

    @Test fun afterClear_onStartPage_allBlank() {
        val m = model()
        m.active.url = null
        assertTrue(SessionState.afterClear(m).tabs.all { it.url == null })
    }

    class FakeQueue {
        private val items = ArrayList<Runnable>()
        fun post(r: Runnable, delay: Long) { items.add(r) }
        fun remove(r: Runnable) { items.remove(r) }
        fun runAll() { val copy = ArrayList(items); items.clear(); copy.forEach { it.run() } }
    }

    class RecordingOps(private val renameOk: Boolean = true) : FileOps {
        val log = ArrayList<String>()
        private var renames = 0
        override fun write(file: File, text: String) { log.add("write ${file.path}") }
        override fun rename(from: File, to: File): Boolean {
            log.add("rename ${from.path} -> ${to.path}")
            return renameOk || renames++ > 0
        }
        override fun delete(file: File) { log.add("delete ${file.path}") }
    }
}
