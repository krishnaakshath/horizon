package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.net.UrlPolicy
import java.io.File

/** Copies live WebView state into the active tab. Never blanks a URL unless the start page is showing. */
object SessionSnapshot {
    data class Web(val url: String?, val title: String?, val scrollY: Int)

    fun apply(model: TabModel, web: Web?, showingStart: Boolean) {
        val t = model.active
        if (showingStart) { t.url = null; t.title = ""; t.scrollY = 0; return }
        if (web == null) return // renderer gone / no WebView / destroyed: keep the last known page
        val url = web.url
        if (url != null && UrlPolicy.isStorable(url)) {
            t.url = url
            web.title?.let { t.title = it.take(200) }
            t.scrollY = web.scrollY.coerceAtLeast(0)
        }
    }
}

object SessionState {
    /** After "Clear browsing data": exactly one blank start-page tab (no URL, title, scroll or desktop flag). */
    fun afterClear(@Suppress("UNUSED_PARAMETER") model: TabModel): TabModel = TabModel()
}

/** One pending delayed save; flushNow writes immediately and drops the pending one. */
class DebouncedSaver(
    private val post: (Runnable, Long) -> Unit,
    private val remove: (Runnable) -> Unit,
    private val write: (String) -> Unit
) {
    private var pending: Runnable? = null

    fun schedule(delayMs: Long, snapshot: () -> String) {
        cancel()
        val r = Runnable { pending = null; write(snapshot()) }
        pending = r
        post(r, delayMs)
    }

    fun flushNow(snapshot: () -> String) {
        cancel()
        write(snapshot())
    }

    fun cancel() {
        pending?.let { remove(it) }
        pending = null
    }
}

interface FileOps {
    fun write(file: File, text: String)
    fun rename(from: File, to: File): Boolean
    fun delete(file: File)
    fun exists(file: File): Boolean = file.exists()

    object Real : FileOps {
        override fun write(file: File, text: String) { file.parentFile?.mkdirs(); file.writeText(text) }
        override fun rename(from: File, to: File) = from.renameTo(to)
        override fun delete(file: File) { file.delete() }
    }
}

/**
 * Atomic text write: write "<name>.tmp", then rename over the target. If the platform refuses to
 * rename over an existing file, the old file is moved to "<name>.bak" first and restored if the
 * second rename fails. The existing destination is never deleted; a failed write removes the temp.
 */
object AtomicText {
    fun write(ops: FileOps, file: File, text: String): Boolean {
        val tmp = File(file.path + ".tmp")
        try {
            ops.write(tmp, text)
        } catch (e: Exception) {
            ops.delete(tmp)
            return false
        }
        if (ops.rename(tmp, file)) return true
        val bak = File(file.path + ".bak")
        val movedAside = ops.rename(file, bak)
        if (movedAside && ops.rename(tmp, file)) {
            ops.delete(bak)
            return true
        }
        if (movedAside) ops.rename(bak, file)
        ops.delete(tmp)
        return false
    }
}

/** Session writes from several threads: only a write newer than the last one written lands. */
class SequencedWriter(private val sink: (String) -> Unit) {
    private var issued = 0L
    private var written = 0L

    @Synchronized fun next(): Long = ++issued

    @Synchronized fun write(seq: Long, text: String): Boolean {
        if (seq <= written) return false
        written = seq
        sink(text)
        return true
    }
}

/** While browsing data is being cleared, nothing may be re-recorded (history, session). */
class ClearGate {
    @Volatile var clearing = false; private set

    fun begin() { clearing = true }
    fun end() { clearing = false }

    fun shouldRecordHistory(url: String): Boolean = !clearing && url != "about:blank" && UrlPolicy.isStorable(url)
    fun shouldScheduleSession(): Boolean = !clearing
}
