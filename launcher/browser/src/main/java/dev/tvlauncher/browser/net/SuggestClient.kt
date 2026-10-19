package dev.tvlauncher.browser.net

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.util.concurrent.Executors

/** DuckDuckGo autocomplete: 150 ms debounce, 3 s timeout, stale requests cancelled. */
class SuggestClient {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "suggest").apply { isDaemon = true } }
    @Volatile private var gen = 0
    @Volatile private var conn: HttpURLConnection? = null
    private var pending: Runnable? = null

    fun request(query: String, cb: (List<String>) -> Unit) {
        cancel()
        val g = gen
        val q = query.trim()
        if (q.isEmpty()) return
        val r = Runnable {
            exec.execute {
                if (g != gen) return@execute
                val res = Http.get(SearchEngines.suggestUrl(q), 64 * 1024, 3000, { true }) { conn = it }
                val list = res?.let { SuggestParser.parse(String(it.bytes, Charsets.UTF_8)) } ?: emptyList()
                main.post { if (g == gen) cb(list) }
            }
        }
        pending = r
        main.postDelayed(r, 150)
    }

    fun cancel() {
        gen++
        pending?.let { main.removeCallbacks(it) }
        pending = null
        val c = conn
        conn = null
        if (c != null) Thread { try { c.disconnect() } catch (e: Exception) { } }.start()
    }
}
