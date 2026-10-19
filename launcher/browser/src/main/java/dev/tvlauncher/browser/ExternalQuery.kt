package dev.tvlauncher.browser

/**
 * A search handed over by the launcher's Spotlight ("Search the web"): the text arrives in
 * [EXTRA_QUERY] on the launch intent and is opened like text typed in the address bar
 * ([UrlResolver]: words search, an address opens). Only text is accepted, trimmed to one line
 * of at most [MAX_LEN] characters; control characters are dropped.
 */
object ExternalQuery {
    const val EXTRA_QUERY = "dev.tvlauncher.browser.extra.QUERY"
    const val MAX_LEN = 512

    fun clean(raw: String?): String? {
        if (raw == null) return null
        val one = raw.map { if (it == '\n' || it == '\r' || it == '\t') ' ' else it }
            .filter { !Character.isISOControl(it) }.joinToString("")
            .replace(Regex("\\s+"), " ").trim()
        return one.take(MAX_LEN).trim().takeIf { it.isNotEmpty() }
    }

    /** The page to open for [raw], or null when there is nothing to open. */
    fun target(raw: String?, engine: dev.tvlauncher.browser.net.SearchEngine): String? =
        clean(raw)?.let { UrlResolver.resolve(it, engine) }?.takeIf { it != UrlResolver.HOME_PAGE }
}
