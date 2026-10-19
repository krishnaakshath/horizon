package dev.tvlauncher.store.core

import java.net.URI

/**
 * Decides whether a URL may be fetched at all. Applied to the URL the user types AND to every redirect hop.
 *
 * Rules: https only; ASCII-only, no whitespace/control/backslash characters; no user-info; port 443 or default;
 * a real DNS-style host (≥ 2 labels, LDH labels) that is not localhost / mDNS / internal names; IP literals only
 * when they are a strict dotted quad in public space (obfuscated forms such as 2130706433, 0x7f.1, 0177.0.0.1
 * and every IPv6 literal are rejected); length ≤ [maxLength] (2048 for typed URLs).
 */
object UrlPolicy {

    const val MAX_LENGTH = 2048

    sealed class Verdict {
        data class Allowed(val host: String, val uri: URI) : Verdict()
        data class Rejected(val reason: String) : Verdict()
    }

    private val blockedSuffixes = listOf(".localhost", ".local", ".internal", ".home.arpa", ".lan", ".intranet", ".corp")
    private val blockedHosts = setOf("localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback")
    private val label = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

    fun isAllowedApkUrl(url: String?): Boolean = check(url) is Verdict.Allowed

    fun check(url: String?, maxLength: Int = MAX_LENGTH): Verdict {
        if (url == null) return Verdict.Rejected("Enter a link.")
        val s = url.trim()
        if (s.isEmpty()) return Verdict.Rejected("Enter a link.")
        if (s.length > maxLength) return Verdict.Rejected("That link is too long.")
        for (c in s) {
            if (c.code < 0x21 || c.code > 0x7e || c == '\\') {
                return Verdict.Rejected("The link contains characters that aren't allowed (spaces or non-Latin letters).")
            }
        }
        if (!s.regionMatches(0, "https://", 0, 8, ignoreCase = true)) {
            return Verdict.Rejected("Only secure https:// links are allowed.")
        }
        val uri = try { URI(s) } catch (_: Exception) { return Verdict.Rejected("That doesn't look like a valid link.") }
        if (!"https".equals(uri.scheme, ignoreCase = true)) return Verdict.Rejected("Only secure https:// links are allowed.")
        val authority = uri.rawAuthority ?: return Verdict.Rejected("The link has no website name.")
        if (authority.contains('@')) return Verdict.Rejected("Links with a user name or password are not allowed.")
        if (authority.startsWith("[")) return Verdict.Rejected("Links to raw IPv6 addresses are not allowed.")
        if (authority.endsWith(":")) return Verdict.Rejected("Only the standard https port is allowed.")
        val port = uri.port
        if (port != -1 && port != 443) return Verdict.Rejected("Only the standard https port is allowed.")
        val rawHost = uri.host ?: authority.substringBefore(':')
        var host = rawHost.lowercase()
        if (host.endsWith(".")) host = host.dropLast(1)
        if (host.isEmpty()) return Verdict.Rejected("The link has no website name.")
        hostProblem(host)?.let { return Verdict.Rejected(it) }
        return Verdict.Allowed(host, uri)
    }

    /** Null when the (lower-case, ASCII) host is acceptable; otherwise a user-facing reason. */
    internal fun hostProblem(host: String): String? {
        if (host in blockedHosts || blockedSuffixes.any { host.endsWith(it) }) {
            return "Links to this device or your local network are not allowed."
        }
        val labels = host.split('.')
        if (labels.size < 2) return "Links to local network names are not allowed."
        if (labels.any { !label.matches(it) }) return "That website name isn't valid."
        val last = labels.last()
        val numericish = last.all { it.isDigit() } || last.startsWith("0x")
        if (numericish) {
            val quad = parseStrictQuad(host) ?: return "Unusual numeric addresses are not allowed."
            if (IpRanges.isBlocked(quad)) return "Links to this device or your local network are not allowed."
        }
        return null
    }

    /** "a.b.c.d" with each part 0..255 written in plain decimal without leading zeros. */
    internal fun parseStrictQuad(host: String): ByteArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val out = ByteArray(4)
        for ((i, p) in parts.withIndex()) {
            if (p.isEmpty() || p.length > 3 || !p.all { it.isDigit() }) return null
            if (p.length > 1 && p[0] == '0') return null
            val v = p.toInt()
            if (v > 255) return null
            out[i] = v.toByte()
        }
        return out
    }
}
