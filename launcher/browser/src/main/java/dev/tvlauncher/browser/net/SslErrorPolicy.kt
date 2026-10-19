package dev.tvlauncher.browser.net

import java.net.URI
import java.util.Locale

/**
 * Certificate errors are always cancelled; the full-page error is shown only when the failing
 * URL is the main frame (current or being loaded, e.g. after a redirect). Sub-resources such as
 * ads/trackers with bad certificates are silently blocked. Unknown URLs fail safe (show).
 */
object SslErrorPolicy {
    fun shouldShowPageError(errorUrl: String?, mainFrameUrl: String?, loadingUrl: String?): Boolean {
        val e = normalize(errorUrl) ?: return true
        val main = normalize(mainFrameUrl)
        val loading = normalize(loadingUrl)
        if (main == null && loading == null) return true
        return e == main || e == loading
    }

    /** scheme://host[:port]/path, lowercase scheme/host, no fragment, no trailing slash. */
    internal fun normalize(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            val u = URI(url.trim())
            val host = u.host?.lowercase(Locale.ROOT) ?: return null
            val scheme = u.scheme?.lowercase(Locale.ROOT) ?: return null
            val port = when {
                u.port <= 0 -> ""
                scheme == "https" && u.port == 443 -> ""
                scheme == "http" && u.port == 80 -> ""
                else -> ":${u.port}"
            }
            val path = (u.rawPath ?: "").trimEnd('/')
            val q = u.rawQuery?.let { "?$it" } ?: ""
            "$scheme://$host$port$path$q"
        } catch (e: Exception) {
            null
        }
    }

    /** Human text for android.net.http.SslError primary error codes. */
    fun reason(primaryError: Int): String = when (primaryError) {
        0 -> "its certificate is not valid yet"
        1 -> "its certificate has expired"
        2 -> "its certificate doesn't match the site name"
        3 -> "its certificate isn't from a trusted authority"
        4 -> "its certificate has an invalid date"
        else -> "its certificate is invalid"
    }
}


/**
 * A suppressed (non-matching) SSL error becomes a page error when the main frame never commits
 * within [timeoutMs] or finishes in a failed state; reported at most once.
 */
class SslFallback(private val timeoutMs: Long = 8000) {
    data class Pending(val url: String, val primaryError: Int)

    private var pending: Pending? = null
    private var since = 0L

    fun onSuppressed(url: String, primaryError: Int, now: Long) {
        if (pending == null) { pending = Pending(url, primaryError); since = now }
    }

    fun onCommitted() { pending = null }

    fun onFinished(failed: Boolean): Pending? {
        val p = pending
        pending = null
        return if (failed) p else null
    }

    fun due(now: Long): Pending? {
        val p = pending ?: return null
        if (now - since < timeoutMs) return null
        pending = null
        return p
    }
}
