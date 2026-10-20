package dev.tvlauncher.link.ha

import dev.tvlauncher.link.net.UrlPolicy

/** Home Assistant URLs and the auth header. Pure. */
object HaUrls {
    private val NAME = Regex("^[a-z_]{1,64}$")

    /**
     * The base URL as typed ("homeassistant.local:8123", "http://192.168.1.10:8123/") normalised
     * to "scheme://host[:port]" (path kept without the trailing slash). A missing scheme means
     * http (Home Assistant's default on the home network). Null when it is not a usable URL.
     */
    fun normalizeBase(input: String?): String? {
        var s = input?.trim() ?: return null
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "http://$s"
        val p = UrlPolicy.parse(s) ?: return null
        if (s.contains('?')) return null
        val path = p.path.trimEnd('/')
        if (path.contains("..")) return null
        return p.origin + path
    }

    fun states(base: String): String = "$base/api/states"

    fun api(base: String): String = "$base/api/"

    /** Null when [domain] or [service] are not plain names (nothing may reach the path unchecked). */
    fun service(base: String, domain: String, service: String): String? =
        if (NAME.matches(domain) && NAME.matches(service)) "$base/api/services/$domain/$service" else null

    /**
     * A long-lived access token is a JWT: printable ASCII, no spaces. Anything else is refused
     * before it can reach a header (no header injection).
     */
    fun isValidToken(t: String?): Boolean =
        t != null && t.length in 20..1024 && t.all { it.code in 0x21..0x7e }

    fun authHeader(token: String): String {
        require(isValidToken(token)) { "invalid token" }
        return "Bearer $token"
    }

    /** "homeassistant.local:8123" for display (never the token). */
    fun displayHost(base: String?): String = base?.let { UrlPolicy.parse(it) }?.let { p ->
        if (p.port == -1) p.host else "${p.host}:${p.port}"
    } ?: ""
}

/**
 * Retries and polling cadence.
 *
 * - [shouldRetry]: only idempotent reads (GET /api/states) are retried, at most [MAX_RETRIES]
 *   times on a network error or a 5xx; a service call is never retried (a toggle sent twice
 *   would undo itself).
 * - [Backoff]: the panel polls every [Backoff.BASE_MS] while it is open; each failure doubles the
 *   wait up to [Backoff.MAX_MS], and a success resets it.
 */
object HaRetry {
    const val MAX_RETRIES = 2
    val DELAYS_MS = longArrayOf(300, 900)

    fun shouldRetry(idempotent: Boolean, attempt: Int, status: Int?, networkError: Boolean): Boolean {
        if (!idempotent || attempt >= MAX_RETRIES) return false
        return networkError || (status != null && status in 500..599)
    }
}

class Backoff(private val baseMs: Long = BASE_MS, private val maxMs: Long = MAX_MS) {
    var failures = 0
        private set

    fun nextDelayMs(): Long {
        if (failures == 0) return baseMs
        val shift = (failures - 1).coerceAtMost(10)
        return (baseMs shl (shift + 1)).coerceAtMost(maxMs)
    }

    fun success() { failures = 0 }
    fun failure() { failures++ }

    companion object {
        const val BASE_MS = 5_000L
        const val MAX_MS = 60_000L
    }
}
