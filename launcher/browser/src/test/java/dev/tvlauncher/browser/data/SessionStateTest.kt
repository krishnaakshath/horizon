package dev.tvlauncher.browser.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException

class SessionStateTest {
    private fun model(): TabModel {
        val m = TabModel()
        m.active.url = "https://a.com/"; m.active.title = "A"; m.active.scrollY = 50
        m.open("https://b.com/")!!.title = "B"
        m.open("https://c.com/")!!.title = "C"
        m.switchTo(1)
        m.active.desktop = true
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

    /** Production path: DebouncedSaver + SequencedWriter on a virtual clock (as wired in the activity). */
    @Test fun destroyWithin2sOfLoad_keepsLoadedUrl() {
        val m = model()
        val clock = FakeScheduler()
        val disk = ArrayList<String>()
        val ioQueue = ArrayList<Runnable>()
        val writer = SequencedWriter { disk.add(it) }
        val saver = DebouncedSaver(clock::post, clock::remove) { json ->
            val seq = writer.next()
            ioQueue.add(Runnable { writer.write(seq, json) }) // io thread, possibly late
        }
        SessionSnapshot.apply(m, SessionSnapshot.Web("https://b.com/loaded", "L", 0), false)
        saver.schedule(2000) { m.encode() }
        clock.advance(1000)            // closed after 1 s: pending save not yet fired
        assertTrue(disk.isEmpty())
        // onStop: snapshot while the WebView still exists, cancel pending, write synchronously.
        SessionSnapshot.apply(m, SessionSnapshot.Web("https://b.com/loaded", "L", 0), false)
        saver.cancel()
        writer.write(writer.next(), m.encode())
        // onDestroy: WebView gone; a late snapshot must not blank the tab.
        SessionSnapshot.apply(m, null, showingStart = false)
        clock.advance(5000)
        ioQueue.forEach { it.run() }
        assertEquals(1, disk.size)
        assertEquals("https://b.com/loaded", TabModel.decode(disk.last()).tabs[1].url)
    }

    @Test fun sequencedWriter_skipsStaleWrites() {
        val disk = ArrayList<String>()
        val w = SequencedWriter { disk.add(it) }
        val old = w.next()
        val new = w.next()
        assertTrue(w.write(new, "new"))
        assertFalse(w.write(old, "old"))
        assertEquals(listOf("new"), disk)
    }

    @Test fun atomicWrite_usesTempAndRename() {
        val ops = RecordingOps()
        assertTrue(AtomicText.write(ops, File("/x/session.json"), "{}"))
        assertEquals(listOf("write /x/session.json.tmp", "rename /x/session.json.tmp -> /x/session.json"), ops.log)
    }

    @Test fun atomicWrite_diskFull_deletesTemp_keepsDestination() {
        val ops = RecordingOps(failWrite = true)
        assertFalse(AtomicText.write(ops, File("/x/s.json"), "{}"))
        assertEquals(listOf("write /x/s.json.tmp", "delete /x/s.json.tmp"), ops.log)
    }

    @Test fun atomicWrite_renameFails_usesBackup_andRestoresOnFailure() {
        val ops = RecordingOps(renameResults = mutableListOf(false, true, false, true))
        assertFalse(AtomicText.write(ops, File("/x/s.json"), "{}"))
        assertEquals(listOf(
            "write /x/s.json.tmp",
            "rename /x/s.json.tmp -> /x/s.json",      // direct replace fails
            "rename /x/s.json -> /x/s.json.bak",       // move old aside
            "rename /x/s.json.tmp -> /x/s.json",      // still fails
            "rename /x/s.json.bak -> /x/s.json",       // old file restored
            "delete /x/s.json.tmp"
        ), ops.log)
        assertFalse(ops.log.contains("delete /x/s.json"))
    }

    @Test fun atomicWrite_renameFallbackSucceeds_dropsBackup() {
        val ops = RecordingOps(renameResults = mutableListOf(false, true, true))
        assertTrue(AtomicText.write(ops, File("/x/s.json"), "{}"))
        assertEquals("delete /x/s.json.bak", ops.log.last())
    }

    @Test fun corruptSession_loadsEmpty() {
        val m = TabModel.decode("{\"tabs\":[{\"url\":")
        assertEquals(1, m.tabs.size)
        assertNull(m.active.url)
    }

    @Test fun afterClear_isExactlyOneBlankTab() {
        val r = SessionState.afterClear(model())
        assertEquals(1, r.tabs.size)
        assertNull(r.active.url)
        assertEquals("", r.active.title)
        assertEquals(0, r.active.scrollY)
        assertEquals(0, r.activeIndex)
        assertEquals(1, TabModel.decode(r.encode()).tabs.size)
    }

    @Test fun afterClear_resetsDesktopFlag() {
        assertFalse(SessionState.afterClear(model()).active.desktop)
    }

    class FakeScheduler {
        private var now = 0L
        private val tasks = ArrayList<Pair<Long, Runnable>>()
        fun post(r: Runnable, delay: Long) { tasks.add(now + delay to r) }
        fun remove(r: Runnable) { tasks.removeAll { it.second === r } }
        fun advance(ms: Long) {
            now += ms
            val due = tasks.filter { it.first <= now }
            tasks.removeAll(due)
            due.forEach { it.second.run() }
        }
    }

    class RecordingOps(private val failWrite: Boolean = false, private val renameResults: MutableList<Boolean> = mutableListOf()) : FileOps {
        val log = ArrayList<String>()
        override fun write(file: File, text: String) {
            log.add("write ${file.path}")
            if (failWrite) throw IOException("ENOSPC")
        }
        override fun rename(from: File, to: File): Boolean {
            log.add("rename ${from.path} -> ${to.path}")
            return if (renameResults.isEmpty()) true else renameResults.removeAt(0)
        }
        override fun delete(file: File) { log.add("delete ${file.path}") }
        override fun exists(file: File) = true
    }
}
