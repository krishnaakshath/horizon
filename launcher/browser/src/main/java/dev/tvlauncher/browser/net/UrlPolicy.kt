package dev.tvlauncher.browser.net

import java.net.URI
import java.util.Locale

/** Which URLs may be loaded top-level or stored (bookmarks, history, session). */
object UrlPolicy {
    const val MAX_URL = 2048

    /** http/https with a host, or about:blank. Everything else (file:, content:, intent:, javascript:, data:, ...) is blocked. */
    fun isNavigationAllowed(url: String): Boolean {
        if (url == "about:blank") return true
        return isHttpWithHost(url)
    }

    fun isStorable(url: String): Boolean = url.length <= MAX_URL && isHttpWithHost(url)

    private fun isHttpWithHost(url: String): Boolean {
        if (url.isEmpty() || url.length > 8 * MAX_URL || url[0].isWhitespace()) return false
        val colon = url.indexOf(':')
        if (colon <= 0) return false
        val scheme = url.substring(0, colon).lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return false
        if (!url.startsWith("://", colon)) return false
        return try {
            !URI(url).host.isNullOrEmpty()
        } catch (e: Exception) {
            // Lenient fallback for URLs with unescaped characters: host is between :// and the next / ? #.
            val rest = url.substring(colon + 3)
            val host = rest.takeWhile { it != '/' && it != '?' && it != '#' }.substringAfterLast('@').substringBefore(':')
            host.isNotEmpty() && host.none { it.isWhitespace() }
        }
    }

    /** Lowercase host without "www.", or "" for unparseable input. */
    fun hostOf(url: String): String = try {
        (URI(url).host ?: "").lowercase(Locale.ROOT).removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
}
