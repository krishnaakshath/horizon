package dev.tvlauncher.link.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * The one outgoing HTTP client (Home Assistant, WebDAV, the release feed). Plain
 * HttpURLConnection, no redirects followed (a redirect could lead to a public http host),
 * [UrlPolicy] checked before every connection, short timeouts and a cap on every body read.
 */
object SafeHttp {
    class Response(val status: Int, val body: ByteArray, val retryAfter: String? = null) {
        fun text(): String = String(body, Charsets.UTF_8)
    }

    class Refused(message: String) : IOException(message)

    val systemResolver = UrlPolicy.Resolver { host -> InetAddress.getAllByName(host).map { it.address } }

    fun request(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: ByteArray? = null,
        maxBytes: Int = 1 shl 20,
        connectMs: Int = 4000,
        readMs: Int = 8000,
        resolver: UrlPolicy.Resolver = systemResolver,
    ): Response {
        val p = UrlPolicy.parse(url) ?: throw Refused("bad url")
        if (!UrlPolicy.allowed(p, resolver)) throw Refused("plain http is only allowed on the home network")
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.instanceFollowRedirects = false
            c.useCaches = false
            c.connectTimeout = connectMs
            c.readTimeout = readMs
            c.requestMethod = method
            c.setRequestProperty("User-Agent", "H02Link/1")
            for ((k, v) in headers) {
                require(k.none { it == '\r' || it == '\n' } && v.none { it == '\r' || it == '\n' })
                c.setRequestProperty(k, v)
            }
            if (body != null) {
                c.doOutput = true
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val status = c.responseCode
            val stream = try { if (status >= 400) c.errorStream else c.inputStream } catch (e: IOException) { null }
            val bytes = stream?.use { s -> readCapped(s, maxBytes) } ?: ByteArray(0)
            return Response(status, bytes, c.getHeaderField("Retry-After"))
        } finally {
            c.disconnect()
        }
    }

    fun readCapped(s: java.io.InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            if (out.size() + n > maxBytes) throw Refused("response too large")
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
