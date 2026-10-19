package dev.tvlauncher.browser.net

import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** DuckDuckGo autocomplete: 150 ms debounce, 3 s timeout, stale requests cancelled. */
class SuggestClient {
    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor { r -> Thread(r, "suggest").apply { isDaemon = true } }
    @Volatile private var gen = 0
    @Volatile private var conn: HttpConn? = null
    private var pending: Runnable? = null

    fun request(query: String, cb: (List<String>) -> Unit) {
        cancel()
        val g = gen
        val q = query.trim()
        if (!SuggestionGate.shouldQuery(q)) return // URLs, hosts and IPs never leave the device
        val r = Runnable {
            exec.execute {
                if (g != gen) return@execute
                val res = Http.default.get(SearchEngines.suggestUrl(q), 64 * 1024, 3000, { true }) { conn = it }
                val list = res?.let { SuggestParser.parse(String(it.bytes, Charsets.UTF_8)) } ?: emptyList()
                main.post { if (g == gen) cb(list) }
            }
        }
        pending = r
        main.postDelayed(r, 150)
    }

    fun shutdown() { cancel(); exec.shutdownNow() }

    fun cancel() {
        gen++
        pending?.let { main.removeCallbacks(it) }
        pending = null
        val c = conn
        conn = null
        if (c != null) Thread { try { c.disconnect() } catch (e: Exception) { } }.start()
    }
}
