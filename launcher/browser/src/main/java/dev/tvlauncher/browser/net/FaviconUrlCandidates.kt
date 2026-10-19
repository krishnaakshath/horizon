package dev.tvlauncher.browser.net

import java.net.URI

/** Ordered icon URLs to try for a page: apple-touch-icon.png, icons declared in HEAD, favicon.ico. */
object FaviconUrlCandidates {
    fun build(pageUrl: String, htmlHead: String?): List<String> {
        val origin = origin(pageUrl) ?: return emptyList()
        val out = LinkedHashSet<String>()
        out.add("$origin/apple-touch-icon.png")
        out.addAll(HtmlIconParser.parse(htmlHead, pageUrl))
        out.add("$origin/favicon.ico")
        return out.toList()
    }

    /** https origin (scheme://host[:port]) of an http(s) page, else null. */
    fun origin(pageUrl: String): String? = try {
        val u = URI(pageUrl)
        if ((u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrEmpty()) {
            "https://" + u.host + if (u.port > 0) ":${u.port}" else ""
        } else null
    } catch (e: Exception) {
        null
    }
}
