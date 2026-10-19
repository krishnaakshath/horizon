package dev.tvlauncher.browser.net

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Locale

/** Parses DuckDuckGo autocomplete JSON (list form `["q",["a","b"]]` or `[{"phrase":"a"}]`). Never throws. */
object SuggestParser {
    const val MAX = 8
    const val MAX_LEN = 200

    fun parse(json: String?): List<String> = try {
        if (json == null || json.length > 64 * 1024) emptyList() else parseUnsafe(json)
    } catch (e: Exception) {
        emptyList()
    } catch (e: StackOverflowError) {
        emptyList()
    }

    private fun parseUnsafe(json: String): List<String> {
        val root = JSONTokener(json).nextValue() as? JSONArray ?: return emptyList()
        val out = ArrayList<String>()
        fun take(v: Any?) {
            val s = (v as? String)?.trim() ?: return
            if (s.isNotEmpty() && s.length <= MAX_LEN && out.size < MAX && out.none { it == s }) out.add(s)
        }
        val second = root.opt(1)
        if (root.opt(0) is String && second is JSONArray) {
            for (i in 0 until second.length()) take(second.opt(i))
        } else {
            for (i in 0 until root.length()) take((root.opt(i) as? JSONObject)?.opt("phrase"))
        }
        return out
    }
}

/** Merges local (bookmark, then history) matches ahead of remote search suggestions. */
object SuggestionMerger {
    enum class Kind { BOOKMARK, HISTORY, SEARCH }
    data class Local(val title: String, val url: String)
    data class Suggestion(val kind: Kind, val text: String, val url: String?)

    const val MAX_LOCAL = 3

    fun merge(query: String, bookmarks: List<Local>, history: List<Local>, remote: List<String>, max: Int = 8): List<Suggestion> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Suggestion>()
        val urls = HashSet<String>()
        fun local(list: List<Local>, kind: Kind) {
            for (l in list) {
                if (out.size >= MAX_LOCAL) return
                if (l.url in urls) continue
                if (l.title.lowercase(Locale.ROOT).contains(q) || l.url.lowercase(Locale.ROOT).contains(q)) {
                    urls.add(l.url)
                    out.add(Suggestion(kind, l.title.ifBlank { l.url }, l.url))
                }
            }
        }
        local(bookmarks, Kind.BOOKMARK)
        local(history, Kind.HISTORY)
        val texts = HashSet<String>()
        for (r in remote) {
            if (out.size >= max) break
            val key = r.lowercase(Locale.ROOT)
            if (texts.add(key)) out.add(Suggestion(Kind.SEARCH, r, null))
        }
        return out.take(max)
    }
}
