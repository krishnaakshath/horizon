package dev.tvlauncher.store.core

import java.io.ByteArrayInputStream
import java.io.InputStream

/** Scripted transport for JVM tests: URL → response; host → addresses (public by default). */
class FakeConnector : HttpConnector {
    class Spec(val code: Int, val location: String? = null, val body: ByteArray = ByteArray(0), val contentLength: Long = -2,
               val headers: Map<String, List<String>> = emptyMap(), val stream: (() -> InputStream)? = null)

    val routes = HashMap<String, Spec>()
    val dns = HashMap<String, List<ByteArray>>()
    val opened = ArrayList<String>()
    val resolved = ArrayList<String>()

    fun redirect(from: String, to: String, code: Int = 302) { routes[from] = Spec(code, location = to) }
    fun ok(url: String, body: ByteArray) { routes[url] = Spec(200, body = body) }

    override fun resolve(host: String): List<ByteArray> {
        resolved += host
        return dns[host] ?: listOf(byteArrayOf(93, 184.toByte(), 216.toByte(), 34))
    }

    override fun open(url: String, headers: Map<String, String>): HopResponse {
        opened += url
        val s = routes[url] ?: Spec(404)
        val len = if (s.contentLength == -2L) s.body.size.toLong() else s.contentLength
        return HopResponse(s.code, s.location, len, null, s.headers,
            bodyOpener = { s.stream?.invoke() ?: ByteArrayInputStream(s.body) },
            errorOpener = { ByteArrayInputStream("""{"message":"nope"}""".toByteArray()) },
            closer = {})
    }
}
