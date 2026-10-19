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
    /** After "Clear browsing data": inactive tabs become blank start pages; the active tab keeps its page. */
    fun afterClear(model: TabModel): TabModel {
        val tabs = model.tabs.mapIndexed { i, t ->
            if (i == model.activeIndex) Tab(t.id, t.url, if (t.url == null) "" else t.title, 0, false)
            else Tab(t.id, null, "", 0, false)
        }
        return TabModel.of(tabs, model.activeIndex)
    }
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

    object Real : FileOps {
        override fun write(file: File, text: String) { file.parentFile?.mkdirs(); file.writeText(text) }
        override fun rename(from: File, to: File) = from.renameTo(to)
        override fun delete(file: File) { file.delete() }
    }
}

/** Write to "<name>.tmp" then rename over the target, so a kill mid-write never truncates the file. */
object AtomicText {
    fun write(ops: FileOps, file: File, text: String) {
        try {
            val tmp = File(file.path + ".tmp")
            ops.write(tmp, text)
            if (!ops.rename(tmp, file)) {
                ops.delete(file)
                ops.rename(tmp, file)
            }
        } catch (e: Exception) {
            // Keep the previous file.
        }
    }
}
