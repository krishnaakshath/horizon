package dev.tvlauncher.browser.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Minimal HTTPS GET: https only, manual redirects (max 3, http upgraded), hard byte cap, deadline. */
object Http {
    class Result(val bytes: ByteArray, val contentType: String, val finalUrl: String, val truncated: Boolean)

    const val UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0 Safari/537.36"

    /**
     * @param truncateOk when true, bodies over [maxBytes] are cut (HTML head); otherwise rejected.
     * @param onConnection lets callers keep a handle to cancel (disconnect) a request in flight.
     */
    fun get(
        url: String, maxBytes: Int, timeoutMs: Int, accept: (String) -> Boolean, truncateOk: Boolean = false,
        onConnection: ((HttpURLConnection) -> Unit)? = null
    ): Result? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var u = url
        var redirects = 0
        while (true) {
            if (!u.startsWith("https://")) return null
            val left = (deadline - System.currentTimeMillis()).toInt()
            if (left <= 50) return null
            val c = try { URL(u).openConnection() as? HttpsURLConnection ?: return null } catch (e: Exception) { return null }
            try {
                onConnection?.invoke(c)
                c.instanceFollowRedirects = false
                c.connectTimeout = left
                c.readTimeout = left
                c.useCaches = false
                c.setRequestProperty("User-Agent", UA)
                c.setRequestProperty("Accept-Encoding", "identity")
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: return null
                    if (++redirects > 3) return null
                    val next = URL(URL(u), loc).toString()
                    u = if (next.startsWith("http://")) "https://" + next.removePrefix("http://") else next
                    continue
                }
                if (code != 200) return null
                val type = c.contentType ?: ""
                if (!accept(type)) return null
                val len = c.contentLengthLong
                if (!truncateOk && len > maxBytes) return null
                val out = ByteArrayOutputStream(minOf(maxBytes, if (len > 0) len.toInt() else 16 * 1024))
                val buf = ByteArray(8 * 1024)
                var truncated = false
                c.inputStream.use { ins ->
                    while (true) {
                        if (System.currentTimeMillis() > deadline) return null
                        val n = ins.read(buf)
                        if (n < 0) break
                        if (out.size() + n > maxBytes) {
                            if (!truncateOk) return null
                            out.write(buf, 0, maxBytes - out.size()); truncated = true; break
                        }
                        out.write(buf, 0, n)
                    }
                }
                return Result(out.toByteArray(), type, u, truncated)
            } catch (e: Exception) {
                return null
            } finally {
                c.disconnect()
            }
        }
    }
}
