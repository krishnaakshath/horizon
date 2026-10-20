package dev.tvlauncher.link.net

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** One parsed HTTP/1.1 request. Header names are lower-case. */
class HttpRequest(
    val method: String,
    val path: String,
    val query: String,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    fun header(name: String): String? = headers[name.lowercase()]

    /** Value of cookie [name], or null. */
    fun cookie(name: String): String? {
        val raw = header("cookie") ?: return null
        for (part in raw.split(';')) {
            val p = part.trim()
            val eq = p.indexOf('=')
            if (eq > 0 && p.substring(0, eq) == name) return p.substring(eq + 1)
        }
        return null
    }

    fun bodyText(): String = String(body, Charsets.UTF_8)
}

class HttpError(val status: Int, message: String) : Exception(message)

/**
 * A strict, small HTTP/1.1 request reader for the phone page: one request per connection,
 * bounded everywhere so a client can neither exhaust memory nor hold a thread for long.
 *
 * Limits: request line [MAX_LINE] bytes, all headers [MAX_HEADER_BYTES] and [MAX_HEADERS];
 * a body only with Content-Length (no chunked uploads) and at most the [maxBody] the caller
 * allows for this path. Anything else is an [HttpError] with the status to answer.
 */
object HttpParser {
    const val MAX_LINE = 2048
    const val MAX_HEADER_BYTES = 8192
    const val MAX_HEADERS = 48

    private val METHODS = setOf("GET", "POST", "HEAD")
    private val TOKEN = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")

    /** [maxBody] picks the body limit from the request path (so uploads can be larger than commands). */
    fun read(input: InputStream, maxBody: (String) -> Int): HttpRequest {
        val line = readLine(input, MAX_LINE) ?: throw HttpError(400, "empty request")
        val parts = line.split(' ')
        if (parts.size != 3) throw HttpError(400, "bad request line")
        val (method, target, version) = parts
        if (method !in METHODS) throw HttpError(405, "method")
        if (version != "HTTP/1.1" && version != "HTTP/1.0") throw HttpError(505, "version")
        if (!target.startsWith("/") || target.any { it.code <= 0x20 || it.code >= 0x7f }) throw HttpError(400, "target")
        val path = target.substringBefore('?')
        val query = if ('?' in target) target.substringAfter('?') else ""
        if (path.contains("..") || path.contains("//")) throw HttpError(400, "path")

        val headers = LinkedHashMap<String, String>()
        var total = 0
        while (true) {
            val h = readLine(input, MAX_HEADER_BYTES - total) ?: throw HttpError(400, "headers")
            total += h.length + 2
            if (h.isEmpty()) break
            if (headers.size >= MAX_HEADERS) throw HttpError(431, "too many headers")
            val colon = h.indexOf(':')
            if (colon <= 0) throw HttpError(400, "header")
            val name = h.substring(0, colon)
            if (!TOKEN.matches(name)) throw HttpError(400, "header name")
            val key = name.lowercase()
            val value = h.substring(colon + 1).trim()
            // Duplicates of the headers we rely on are refused (no smuggling, no ambiguity).
            if (key in headers && key in SINGLE) throw HttpError(400, "duplicate $key")
            headers[key] = if (key in headers) headers[key] + ", " + value else value
        }
        if (headers.containsKey("transfer-encoding")) throw HttpError(411, "length required")
        val lengthText = headers["content-length"]
        val length = when {
            lengthText == null -> 0
            lengthText.isEmpty() || lengthText.length > 9 || !lengthText.all { it in '0'..'9' } -> throw HttpError(400, "length")
            else -> lengthText.toInt()
        }
        if (method != "POST" && length > 0) throw HttpError(400, "body")
        if (length > maxBody(path)) throw HttpError(413, "too large")
        val body = ByteArray(length)
        var off = 0
        while (off < length) {
            val n = input.read(body, off, length - off)
            if (n < 0) throw HttpError(400, "short body")
            off += n
        }
        return HttpRequest(method, path, query, headers, body)
    }

    private val SINGLE = setOf("host", "content-length", "content-type", "cookie", "origin", "x-m02-link")

    /** One CRLF (or bare LF) terminated line of at most [max] bytes, as Latin-1; null at end of stream. */
    private fun readLine(input: InputStream, max: Int): String? {
        val out = ByteArrayOutputStream(64)
        while (true) {
            val b = input.read()
            if (b < 0) return if (out.size() == 0) null else throw HttpError(400, "eof")
            if (b == '\n'.code) break
            out.write(b)
            if (out.size() > max) throw HttpError(431, "line too long")
        }
        val bytes = out.toByteArray()
        val len = if (bytes.isNotEmpty() && bytes.last() == '\r'.code.toByte()) bytes.size - 1 else bytes.size
        return String(bytes, 0, len, Charsets.ISO_8859_1)
    }
}

/** A response; [write] adds the security headers every answer carries. */
class HttpResponse(
    val status: Int,
    val contentType: String,
    val body: ByteArray,
    val extraHeaders: List<Pair<String, String>> = emptyList(),
) {
    fun header(name: String): String? = extraHeaders.firstOrNull { it.first.equals(name, true) }?.second

    fun bodyText(): String = String(body, Charsets.UTF_8)

    fun write(out: OutputStream, head: Boolean = false) {
        val sb = StringBuilder()
        sb.append("HTTP/1.1 ").append(status).append(' ').append(reason(status)).append("\r\n")
        sb.append("Content-Type: ").append(contentType).append("\r\n")
        sb.append("Content-Length: ").append(body.size).append("\r\n")
        for ((k, v) in SECURITY_HEADERS + extraHeaders) {
            require(k.none { it == '\r' || it == '\n' } && v.none { it == '\r' || it == '\n' })
            sb.append(k).append(": ").append(v).append("\r\n")
        }
        sb.append("\r\n")
        out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
        if (!head) out.write(body)
        out.flush()
    }

    companion object {
        /**
         * No CORS headers, ever: other sites' scripts cannot read our answers. The page may only
         * talk to its own origin and embeds nothing from outside.
         */
        val SECURITY_HEADERS = listOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "X-Frame-Options" to "DENY",
            "Referrer-Policy" to "no-referrer",
            "Content-Security-Policy" to "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; " +
                "connect-src 'self'; img-src 'self' data: blob:; manifest-src 'self' data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
            "Connection" to "close",
        )

        fun json(status: Int, text: String, headers: List<Pair<String, String>> = emptyList()) =
            HttpResponse(status, "application/json; charset=utf-8", text.toByteArray(Charsets.UTF_8), headers)

        fun error(status: Int, code: String) = json(status, "{\"ok\":false,\"error\":\"$code\"}")

        fun reason(status: Int): String = when (status) {
            200 -> "OK"
            204 -> "No Content"
            400 -> "Bad Request"
            401 -> "Unauthorized"
            403 -> "Forbidden"
            404 -> "Not Found"
            405 -> "Method Not Allowed"
            411 -> "Length Required"
            413 -> "Payload Too Large"
            415 -> "Unsupported Media Type"
            421 -> "Misdirected Request"
            429 -> "Too Many Requests"
            431 -> "Request Header Fields Too Large"
            500 -> "Internal Server Error"
            502 -> "Bad Gateway"
            503 -> "Service Unavailable"
            505 -> "HTTP Version Not Supported"
            else -> "Status"
        }
    }
}
