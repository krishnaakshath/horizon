package dev.tvlauncher.browser.net

enum class SearchEngine(val label: String, val prefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave", "https://search.brave.com/search?q=")
}

object SearchEngines {
    fun urlFor(engine: SearchEngine, query: String): String = engine.prefix + encode(query)

    fun fromId(id: String?): SearchEngine = SearchEngine.values().firstOrNull { it.name == id } ?: SearchEngine.DUCKDUCKGO

    fun suggestUrl(query: String): String = "https://duckduckgo.com/ac/?q=${encode(query)}&type=list"

    /** RFC 3986 percent-encoding of UTF-8 bytes; only unreserved characters stay literal. */
    fun encode(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_' || ch == '.' || ch == '~') {
                sb.append(ch)
            } else {
                sb.append('%').append(HEX[c shr 4]).append(HEX[c and 0xF])
            }
        }
        return sb.toString()
    }

    private const val HEX = "0123456789ABCDEF"
}
