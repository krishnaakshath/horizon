package dev.tvlauncher.browser.data

import dev.tvlauncher.browser.net.UrlPolicy
import org.json.JSONArray
import org.json.JSONObject

/** One tab's saved state. url == null means the start page. */
class Tab(val id: Long, var url: String?, var title: String = "", var scrollY: Int = 0, var desktop: Boolean = false)

/**
 * Up to [max] tabs, exactly one active. Opening beyond the limit fails; closing the active tab
 * selects its right neighbour (else left); closing the last tab leaves a fresh start-page tab.
 */
class TabModel(val max: Int = 3) {
    private val list = ArrayList<Tab>()
    private var nextId = 1L
    var activeIndex = 0; private set
    val tabs: List<Tab> get() = list
    val active: Tab get() = list[activeIndex]

    init { list.add(newTab(null)) }

    private fun newTab(url: String?) = Tab(nextId++, url)

    fun canOpen(): Boolean = list.size < max

    fun open(url: String?, activate: Boolean = true): Tab? {
        if (!canOpen()) return null
        val t = newTab(url)
        list.add(activeIndex + 1, t)
        if (activate) activeIndex += 1
        return t
    }

    fun close(id: Long): Tab {
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return active
        if (list.size == 1) {
            list[0] = newTab(null)
            activeIndex = 0
            return active
        }
        list.removeAt(i)
        activeIndex = when {
            i < activeIndex -> activeIndex - 1
            i == activeIndex -> i.coerceAtMost(list.size - 1)
            else -> activeIndex
        }
        return active
    }

    fun switchTo(index: Int): Tab {
        activeIndex = index.coerceIn(0, list.size - 1)
        return active
    }

    fun indexOf(id: Long): Int = list.indexOfFirst { it.id == id }

    fun encode(): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("url", it.url ?: JSONObject.NULL).put("title", it.title)
                .put("scrollY", it.scrollY).put("desktop", it.desktop))
        }
        return JSONObject().put("active", activeIndex).put("tabs", arr).toString()
    }

    companion object {
        /** Tolerant: bad URLs become start-page tabs, extra tabs dropped, active clamped. */
        fun decode(json: String?): TabModel {
            val m = TabModel()
            try {
                val o = JSONObject(json ?: return m)
                val arr = o.optJSONArray("tabs") ?: return m
                val tabs = ArrayList<Tab>()
                for (i in 0 until arr.length()) {
                    if (tabs.size >= m.max) break
                    val t = arr.opt(i) as? JSONObject ?: continue
                    val url = (t.opt("url") as? String)?.takeIf { UrlPolicy.isStorable(it) }
                    tabs.add(Tab(m.nextId++, url, (t.opt("title") as? String ?: "").take(200),
                        t.optInt("scrollY", 0).coerceAtLeast(0), t.optBoolean("desktop", false)))
                }
                if (tabs.isNotEmpty()) {
                    m.list.clear(); m.list.addAll(tabs)
                    m.activeIndex = o.optInt("active", 0).coerceIn(0, tabs.size - 1)
                }
            } catch (e: Exception) {
                // Corrupt session: start fresh.
            }
            return m
        }
    }
}
