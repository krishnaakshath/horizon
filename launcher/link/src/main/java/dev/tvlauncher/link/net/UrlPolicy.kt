package dev.tvlauncher.link.net

import java.net.URI

/**
 * Rules for the servers Link talks to (Home Assistant, WebDAV, the release feed).
 *
 * - Only http and https; no user info in the URL, no fragment; a host is required.
 * - Plain http only when the host is on the home network: an IP literal that [IpPolicy] calls
 *   local, or a name whose every resolved address is local (checked with [Resolver] right before
 *   connecting). Anything public must use https, so tokens never cross the internet in clear.
 */
object UrlPolicy {
    data class Parsed(val scheme: String, val host: String, val port: Int, val path: String) {
        val https: Boolean get() = scheme == "https"
        /** scheme://host[:port] with brackets for IPv6. */
        val origin: String
            get() {
                val h = if (host.contains(':')) "[$host]" else host
                val defaultPort = if (https) 443 else 80
                return if (port == -1 || port == defaultPort) "$scheme://$h" else "$scheme://$h:$port"
            }
    }

    fun interface Resolver {
        /** Every address of [host] (raw bytes); throws or returns empty when it cannot resolve. */
        fun resolve(host: String): List<ByteArray>
    }

    /** Null when [url] is not an acceptable absolute http(s) URL. */
    fun parse(url: String?): Parsed? {
        val s = url?.trim() ?: return null
        if (s.isEmpty() || s.length > 2048 || s.any { it.code <= 0x20 || it.code >= 0x7f }) return null
        val u = try { URI(s) } catch (e: Exception) { return null }
        val scheme = u.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (u.rawUserInfo != null || u.rawFragment != null) return null
        val host = u.host?.removePrefix("[")?.removeSuffix("]")?.lowercase() ?: return null
        if (host.isEmpty()) return null
        val port = u.port
        if (port != -1 && port !in 1..65535) return null
        return Parsed(scheme, host, port, u.rawPath ?: "")
    }

    /** True when [host] is an IP literal on the home network. */
    fun isLocalLiteral(host: String): Boolean {
        IpPolicy.parseV4(host)?.let { return IpPolicy.isLocal(it) }
        if (host.contains(':')) return parseV6(host)?.let { IpPolicy.isLocal(it) } ?: false
        return false
    }

    /**
     * May we connect to [p]? https always; http only when the host is local (literal, or every
     * address [resolver] returns is local; a failed or empty lookup refuses).
     */
    fun allowed(p: Parsed, resolver: Resolver): Boolean {
        if (p.https) return true
        if (isLocalLiteral(p.host)) return true
        if (IpPolicy.parseV4(p.host) != null || p.host.contains(':')) return false
        val all = try { resolver.resolve(p.host) } catch (e: Exception) { return false }
        return all.isNotEmpty() && all.all { IpPolicy.isLocal(it) }
    }

    /** Minimal IPv6 literal parser (with :: and an optional trailing dotted IPv4). */
    fun parseV6(s: String): ByteArray? {
        val text = s.substringBefore('%')
        if (text.isEmpty() || text.count { it == ':' } < 2) return null
        val halves = text.split("::")
        if (halves.size > 2) return null
        fun groups(part: String): List<Int>? {
            if (part.isEmpty()) return emptyList()
            val out = ArrayList<Int>()
            val items = part.split(':')
            for ((i, g) in items.withIndex()) {
                if (i == items.lastIndex && g.contains('.')) {
                    val v4 = IpPolicy.parseV4(g) ?: return null
                    out += ((v4[0].toInt() and 0xff) shl 8) or (v4[1].toInt() and 0xff)
                    out += ((v4[2].toInt() and 0xff) shl 8) or (v4[3].toInt() and 0xff)
                    continue
                }
                if (g.isEmpty() || g.length > 4) return null
                out += g.toIntOrNull(16) ?: return null
            }
            return out
        }
        val head = groups(halves[0]) ?: return null
        val tail = if (halves.size == 2) groups(halves[1]) ?: return null else emptyList()
        val all = if (halves.size == 2) {
            val fill = 8 - head.size - tail.size
            if (fill < 1) return null
            head + List(fill) { 0 } + tail
        } else head
        if (all.size != 8) return null
        val out = ByteArray(16)
        for ((i, g) in all.withIndex()) { out[2 * i] = (g shr 8).toByte(); out[2 * i + 1] = g.toByte() }
        return out
    }
}
