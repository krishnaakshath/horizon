package dev.tvlauncher.browser

import java.net.URLEncoder
import java.util.Locale

/**
 * Turns what the user typed into something safe to load. Only http(s) ever leaves this object:
 * http is upgraded to https, other schemes (javascript:, file:, content:, intent:, data:, ...)
 * fall back to the start page. Bare words search [SEARCH_PREFIX]; text with a dot and no spaces
 * is treated as an address.
 */
object UrlResolver {
    /** Sentinel for the native start page. */
    const val HOME_PAGE = "about:home"
    const val SEARCH_PREFIX = "https://duckduckgo.com/?q="

    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(.*)$", RegexOption.DOT_MATCHES_ALL)
    private val HOST_PORT = Regex("^[0-9]+(/.*)?$", RegexOption.DOT_MATCHES_ALL)

    fun resolve(input: String): String {
        val text = input.trim()
        if (text.isEmpty()) return HOME_PAGE
        val m = SCHEME.matchEntire(text)
        if (m != null && !HOST_PORT.matches(m.groupValues[2])) {
            return navigationTarget(text) ?: HOME_PAGE
        }
        val hasSpace = text.any { it.isWhitespace() }
        return if (!hasSpace && text.contains('.') && !text.startsWith('.') && !text.endsWith('.')) {
            "https://$text"
        } else {
            SEARCH_PREFIX + URLEncoder.encode(text, "UTF-8")
        }
    }

    /**
     * What an in-page navigation to [url] may load: https as-is, http upgraded to https, and
     * null (block, open nothing) for every other scheme.
     */
    fun navigationTarget(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val scheme = url.substring(0, colon).lowercase(Locale.ROOT)
        val rest = url.substring(colon)
        return when (scheme) {
            "https" -> if (rest.startsWith("://")) "https$rest" else null
            "http" -> if (rest.startsWith("://")) "https$rest" else null
            else -> null
        }
    }
}
