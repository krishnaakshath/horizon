package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.net.UrlPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Bookmark(val url: String, val title: String)

/**
 * Ordered bookmarks (the first 12 are the start page's Favorites) in a small JSON file. A missing
 * file seeds [defaults]; a corrupt file yields an empty list. Only http(s) URLs up to 2048 chars.
 */
class BookmarkStore(private val file: File, defaults: List<Bookmark>, private val max: Int = 100) {
    private val list = ArrayList<Bookmark>()
    val items: List<Bookmark> get() = list

    init {
        val text = JsonFiles.read(file)
        if (text == null) list.addAll(defaults.take(max)) else list.addAll(decode(text, max))
    }

    fun add(url: String, title: String): Boolean {
        if (!UrlPolicy.isStorable(url) || contains(url) || list.size >= max) return false
        list.add(Bookmark(url, cleanTitle(title, url)))
        return true
    }

    fun remove(url: String): Boolean = list.removeAll { it.url == url }
    fun contains(url: String): Boolean = list.any { it.url == url }

    fun move(from: Int, to: Int) {
        if (from !in list.indices || to !in list.indices || from == to) return
        list.add(to, list.removeAt(from))
    }

    fun rename(url: String, title: String) {
        val i = list.indexOfFirst { it.url == url }
        if (i >= 0) list[i] = list[i].copy(title = cleanTitle(title, url))
    }

    fun encode(): String {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("u", it.url).put("t", it.title)) }
        return JSONObject().put("v", 1).put("items", arr).toString()
    }

    fun save() = JsonFiles.writeAtomic(file, encode())

    companion object {
        const val MAX_TITLE = 200

        fun cleanTitle(title: String, url: String): String =
            title.trim().take(MAX_TITLE).ifEmpty { UrlPolicy.hostOf(url).ifEmpty { url.take(MAX_TITLE) } }

        fun decode(text: String, max: Int): List<Bookmark> = try {
            val arr = JSONObject(text).optJSONArray("items") ?: JSONArray()
            val out = ArrayList<Bookmark>()
            for (i in 0 until arr.length()) {
                val o = arr.opt(i) as? JSONObject ?: continue
                val u = o.opt("u") as? String ?: continue
                if (!UrlPolicy.isStorable(u) || out.any { it.url == u }) continue
                out.add(Bookmark(u, cleanTitle(o.opt("t") as? String ?: "", u)))
                if (out.size >= max) break
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }
}
