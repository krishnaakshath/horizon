package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.net.UrlPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Visit(val url: String, val title: String, val time: Long)

/** Newest-first visit list (cap [max], consecutive duplicates merged) in a small JSON file. */
class HistoryStore(private val file: File, private val max: Int = 200) {
    private val list = ArrayList<Visit>()
    val items: List<Visit> get() = list

    init {
        JsonFiles.read(file)?.let { list.addAll(decode(it, max)) }
    }

    fun record(url: String, title: String, time: Long) {
        if (!UrlPolicy.isStorable(url)) return
        val clean = title.trim().take(BookmarkStore.MAX_TITLE)
        val first = list.firstOrNull()
        if (first != null && first.url == url) {
            list[0] = first.copy(title = clean.ifEmpty { first.title }, time = time)
            return
        }
        list.add(0, Visit(url, clean, time))
        while (list.size > max) list.removeAt(list.size - 1)
    }

    fun updateTitle(url: String, title: String) {
        val i = list.indexOfFirst { it.url == url }
        val clean = title.trim().take(BookmarkStore.MAX_TITLE)
        if (i >= 0 && clean.isNotEmpty()) list[i] = list[i].copy(title = clean)
    }

    fun clear() = list.clear()

    /** Newest visit per host (www. ignored), newest first. */
    fun recentSites(n: Int): List<Visit> {
        val seen = HashSet<String>()
        val out = ArrayList<Visit>()
        for (v in list) {
            if (out.size >= n) break
            val h = UrlPolicy.hostOf(v.url)
            if (h.isNotEmpty() && seen.add(h)) out.add(v)
        }
        return out
    }

    /** Groups visits by local day: "Today", "Yesterday", then e.g. "Mon, 12 Oct". */
    fun daySections(now: Long, tz: TimeZone, locale: Locale): List<Pair<String, List<Visit>>> {
        val fmt = SimpleDateFormat("EEE, d MMM", locale).apply { timeZone = tz }
        val today = dayIndex(now, tz)
        val out = ArrayList<Pair<String, MutableList<Visit>>>()
        var lastDay = Long.MIN_VALUE
        for (v in list) {
            val d = dayIndex(v.time, tz)
            if (d != lastDay) {
                val label = when (today - d) { 0L -> "Today"; 1L -> "Yesterday"; else -> fmt.format(Date(v.time)) }
                out.add(label to ArrayList())
                lastDay = d
            }
            out.last().second.add(v)
        }
        return out
    }

    private fun dayIndex(t: Long, tz: TimeZone): Long {
        val c = Calendar.getInstance(tz).apply { timeInMillis = t }
        return c.get(Calendar.YEAR) * 400L + c.get(Calendar.DAY_OF_YEAR)
    }

    fun encode(): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title).put("at", it.time)) }
        return JSONObject().put("v", 1).put("items", arr).toString()
    }

    fun save() = JsonFiles.writeAtomic(file, encode())

    companion object {
        fun decode(text: String, max: Int): List<Visit> = try {
            val arr = JSONObject(text).optJSONArray("items") ?: JSONArray()
            val out = ArrayList<Visit>()
            for (i in 0 until arr.length()) {
                val o = arr.opt(i) as? JSONObject ?: continue
                val u = o.opt("u") as? String ?: continue
                if (!UrlPolicy.isStorable(u)) continue
                out.add(Visit(u, (o.opt("t") as? String ?: "").take(BookmarkStore.MAX_TITLE), o.optLong("at", 0L)))
                if (out.size >= max) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
