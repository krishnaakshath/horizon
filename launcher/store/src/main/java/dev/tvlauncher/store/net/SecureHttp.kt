package dev.tvlauncher.store.net

import dev.tvlauncher.store.core.IpRanges
import dev.tvlauncher.store.core.UrlPolicy
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * HTTPS-only fetching with manual redirect handling. Before ANY connection (first request and each redirect
 * hop) the URL must pass [UrlPolicy] and the caller's per-source [hopAllowed] rule, and the host must not
 * resolve to a private/loopback/link-local address. At most [MAX_REDIRECTS] redirects, all https.
 */
class SecureHttp(private val userAgent: String) {

    class PolicyError(message: String) : IOException(message)

    class HttpError(val code: Int, val body: String?, val headers: Map<String, List<String>>) :
        IOException("HTTP $code") {
        fun header(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    }

    fun open(
        startUrl: String,
        hopAllowed: (String) -> Boolean,
        accept: String = "*/*",
        extraHeaders: Map<String, String> = emptyMap(),
        allowGzip: Boolean = false,
    ): HttpsURLConnection {
        var url = startUrl
        var hops = 0
        while (true) {
            val verdict = UrlPolicy.check(url, maxLength = 8192)
            if (verdict !is UrlPolicy.Verdict.Allowed) throw PolicyError((verdict as UrlPolicy.Verdict.Rejected).reason)
            if (!hopAllowed(url)) throw PolicyError("The download was redirected to a website this source doesn't allow (${verdict.host}).")
            checkResolvedAddresses(verdict.host)

            val conn = URL(url).openConnection() as? HttpsURLConnection
                ?: throw PolicyError("Only secure https:// connections are allowed.")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", accept)
            // Asking for gzip ourselves disables HttpURLConnection's transparent decompression, so the caller
            // sees compressed bytes (for honest progress) and decompresses with its own size limit.
            conn.setRequestProperty("Accept-Encoding", if (allowGzip) "gzip" else "identity")
            for ((k, v) in extraHeaders) conn.setRequestProperty(k, v)

            val code = try { conn.responseCode } catch (e: IOException) { conn.disconnect(); throw e }
            when (code) {
                in 200..299 -> return conn
                301, 302, 303, 307, 308 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (location.isNullOrBlank()) throw PolicyError("The server sent a broken redirect.")
                    if (++hops > MAX_REDIRECTS) throw PolicyError("Too many redirects.")
                    url = try { URL(URL(url), location).toString() } catch (_: Exception) {
                        throw PolicyError("The server sent a broken redirect.")
                    }
                    if (!url.startsWith("https://", ignoreCase = true)) {
                        throw PolicyError("The download was redirected to an insecure (non-https) address.")
                    }
                }
                else -> {
                    val body = try { readBounded(conn.errorStream, 64 * 1024) } catch (_: Exception) { null }
                    val headers = conn.headerFields.filterKeys { it != null }
                    conn.disconnect()
                    throw HttpError(code, body, headers)
                }
            }
        }
    }

    private fun checkResolvedAddresses(host: String) {
        val addrs = try { InetAddress.getAllByName(host) } catch (e: Exception) {
            throw IOException("Couldn't find the website “$host”. Check the link and your internet connection.", e)
        }
        if (addrs.isEmpty() || addrs.any { IpRanges.isBlocked(it.address) }) {
            throw PolicyError("“$host” points to this device or your local network, which isn't allowed.")
        }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_REDIRECTS = 5

        fun readBounded(input: InputStream?, max: Int): String? {
            if (input == null) return null
            input.use {
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (out.size() < max) {
                    val n = it.read(buf, 0, minOf(buf.size, max - out.size()))
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
                if (out.size() >= max && it.read() >= 0) throw IOException("Response too large")
                return out.toString("UTF-8")
            }
        }
    }
}
