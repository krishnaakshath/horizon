package dev.tvlauncher.browser.net

import java.net.URI
import java.util.Locale

/**
 * Finds icon links in (the first 64 KB of) an HTML document. Result is best-first: apple-touch
 * icons before plain icons, larger declared sizes first, unknown sizes last. Only https URLs
 * are returned (http upgraded); data:, svg and non-http hrefs are dropped. Never throws.
 */
object HtmlIconParser {
    const val MAX_HTML = 64 * 1024
    private val LINK = Regex("<link\\b([^>]{0,2000})>", RegexOption.IGNORE_CASE)
    private val BASE = Regex("<base\\b([^>]{0,2000})>", RegexOption.IGNORE_CASE)
    private val ATTR = Regex("([a-zA-Z_:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s\"'>]+))")
    private val SIZE = Regex("(\\d{1,4})[xX](\\d{1,4})")

    private class Found(val url: String, val apple: Boolean, val size: Int, val order: Int)

    fun parse(html: String?, baseUrl: String): List<String> = try {
        parseUnsafe(html, baseUrl)
    } catch (e: Exception) {
        emptyList()
    } catch (e: StackOverflowError) {
        emptyList()
    }

    private fun parseUnsafe(html: String?, baseUrl: String): List<String> {
        if (html.isNullOrEmpty()) return emptyList()
        val doc = if (html.length > MAX_HTML) html.substring(0, MAX_HTML) else html
        var base = toBase(baseUrl) ?: return emptyList()
        BASE.find(doc)?.let { m -> attrs(m.groupValues[1])["href"]?.let { h -> resolve(base, h)?.let { base = URI(it) } } }
        val found = ArrayList<Found>()
        for ((i, m) in LINK.findAll(doc).withIndex()) {
            if (i > 200) break
            val a = attrs(m.groupValues[1])
            val rels = (a["rel"] ?: continue).lowercase(Locale.ROOT).split(' ', '\t', '\n').filter { it.isNotEmpty() }
            val apple = rels.any { it == "apple-touch-icon" || it == "apple-touch-icon-precomposed" }
            val icon = apple || rels.contains("icon")
            if (!icon) continue
            if ((a["type"] ?: "").lowercase(Locale.ROOT).contains("svg")) continue
            val href = a["href"]?.trim() ?: continue
            if (href.isEmpty() || href.startsWith("data:", ignoreCase = true)) continue
            val url = resolve(base, href) ?: continue
            if (url.substringBefore('?').lowercase(Locale.ROOT).endsWith(".svg")) continue
            found.add(Found(url, apple, maxSize(a["sizes"]), i))
        }
        return found
            .sortedWith(compareBy<Found>({ if (it.apple) 0 else 1 }, { -it.size }, { it.order }))
            .map { it.url }
            .distinct()
    }

    private fun maxSize(sizes: String?): Int {
        if (sizes == null) return 0
        var best = 0
        for (m in SIZE.findAll(sizes)) {
            val w = m.groupValues[1].toIntOrNull() ?: continue
            if (w in 1..1024 && w > best) best = w
        }
        return best
    }

    private fun attrs(s: String): Map<String, String> {
        val map = HashMap<String, String>()
        for (m in ATTR.findAll(s)) {
            val k = m.groupValues[1].lowercase(Locale.ROOT)
            val v = m.groups[3]?.value ?: m.groups[4]?.value ?: m.groups[5]?.value ?: ""
            map.putIfAbsent(k, v)
        }
        return map
    }

    private fun toBase(url: String): URI? = try {
        val u = URI(url)
        if ((u.scheme == "http" || u.scheme == "https") && !u.host.isNullOrEmpty()) u else null
    } catch (e: Exception) {
        null
    }

    /** Resolves [href] against [base]; returns https URL or null. */
    internal fun resolve(base: URI, href: String): String? = try {
        val r = base.resolve(href.trim())
        val scheme = r.scheme?.lowercase(Locale.ROOT)
        if ((scheme == "http" || scheme == "https") && !r.host.isNullOrEmpty()) {
            val s = r.toString()
            "https" + s.substring(s.indexOf(':'))
        } else null
    } catch (e: Exception) {
        null
    }
}
