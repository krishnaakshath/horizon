package dev.tvlauncher.browser.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** Seam over one HTTP exchange (real: HttpsURLConnection; tests: fakes). */
interface HttpConn {
    fun configure(timeoutMs: Int)
    val responseCode: Int
    fun header(name: String): String?
    val contentType: String?
    val contentLength: Long
    fun body(): InputStream
    fun disconnect()
}

/**
 * Minimal HTTPS GET for icons and suggestions: https only; manual redirects (max 3, http
 * Location upgraded to https); every hop's host must resolve only to public addresses
 * ([IpPolicy]); content-type filter; hard byte cap (declared length checked before reading,
 * stream aborted at the cap); overall deadline.
 */
class Http(
    private val open: (String) -> HttpConn?,
    private val resolve: (String) -> List<ByteArray>?
) {
    class Result(val bytes: ByteArray, val contentType: String, val finalUrl: String, val truncated: Boolean)

    fun get(
        url: String, maxBytes: Int, timeoutMs: Int, accept: (String) -> Boolean, truncateOk: Boolean = false,
        onConnection: ((HttpConn) -> Unit)? = null
    ): Result? {
        val deadline = System.currentTimeMillis() + timeoutMs
        var u = url
        var redirects = 0
        while (true) {
            if (!u.startsWith("https://")) return null
            if (!hostIsPublic(u)) return null
            val left = (deadline - System.currentTimeMillis()).toInt()
            if (left <= 50) return null
            val c = try { open(u) } catch (e: Exception) { null } ?: return null
            try {
                onConnection?.invoke(c)
                c.configure(left)
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.header("Location") ?: return null
                    if (++redirects > MAX_REDIRECTS) return null
                    val next = try { URI(u).resolve(loc.trim()).toString() } catch (e: Exception) { return null }
                    u = when {
                        next.startsWith("https://") -> next
                        next.startsWith("http://") -> "https://" + next.removePrefix("http://")
                        else -> return null
                    }
                    continue
                }
                if (code != 200) return null
                val type = c.contentType ?: return null
                if (!accept(type)) return null
                val len = c.contentLength
                if (!truncateOk && len > maxBytes) return null
                val out = ByteArrayOutputStream(minOf(maxBytes, if (len in 1..maxBytes) len.toInt() else 16 * 1024))
                val buf = ByteArray(8 * 1024)
                var truncated = false
                c.body().use { ins ->
                    while (true) {
                        if (System.currentTimeMillis() > deadline) return null
                        val want = minOf(buf.size, maxBytes - out.size() + 1)
                        val n = ins.read(buf, 0, want)
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

    private fun hostIsPublic(url: String): Boolean {
        val host = try { URI(url).host } catch (e: Exception) { null } ?: return false
        val addrs = try { resolve(host.removePrefix("[").removeSuffix("]")) } catch (e: Exception) { null }
        return !addrs.isNullOrEmpty() && addrs.all { IpPolicy.isPublic(it) }
    }

    companion object {
        const val MAX_REDIRECTS = 3
        const val UA = "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0 Safari/537.36"

        /** Real network: HttpsURLConnection without automatic redirects or caches; DNS via InetAddress. */
        val default = Http(
            open = { u -> (URL(u).openConnection() as? HttpsURLConnection)?.let { RealConn(it) } },
            resolve = { host -> InetAddress.getAllByName(host).map { it.address } }
        )
    }

    private class RealConn(private val c: HttpsURLConnection) : HttpConn {
        override fun configure(timeoutMs: Int) {
            c.instanceFollowRedirects = false
            c.connectTimeout = timeoutMs
            c.readTimeout = timeoutMs
            c.useCaches = false
            c.setRequestProperty("User-Agent", UA)
            c.setRequestProperty("Accept-Encoding", "identity")
        }
        override val responseCode: Int get() = c.responseCode
        override fun header(name: String): String? = c.getHeaderField(name)
        override val contentType: String? get() = c.contentType
        override val contentLength: Long get() = c.contentLengthLong
        override fun body(): InputStream = c.inputStream
        override fun disconnect() = c.disconnect()
    }
}
