package dev.tvlauncher.store.core

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI

/** The address, a redirect or the source's host rule is not allowed. Nothing was fetched from it. */
class PolicyError(message: String) : IOException(message)

/** A non-2xx, non-redirect HTTP answer. */
class HttpError(val code: Int, val body: String?, val headers: Map<String, List<String>>) : IOException("HTTP $code") {
    fun header(name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
}

/** One HTTP exchange (no redirect following), as produced by an [HttpConnector]. */
class HopResponse(
    val code: Int,
    val location: String?,
    val contentLength: Long,
    val contentEncoding: String?,
    val headers: Map<String, List<String>>,
    private val bodyOpener: () -> InputStream,
    private val errorOpener: () -> InputStream?,
    private val closer: () -> Unit,
) {
    fun body(): InputStream = bodyOpener()
    fun errorBody(): InputStream? = errorOpener()
    fun close() = closer()
}

/** Transport seam: real HttpsURLConnection on device, fakes in JVM tests. */
interface HttpConnector {
    /** Every address the host resolves to (raw 4- or 16-byte form). */
    fun resolve(host: String): List<ByteArray>
    /** Exactly one request to [url]; must not follow redirects itself. */
    fun open(url: String, headers: Map<String, String>): HopResponse
}

/**
 * HTTPS-only fetching with manual redirects. Before ANY request (first and every redirect hop) the URL must
 * pass [UrlPolicy], the caller's per-source rule for that hop index, and its host must not resolve to a
 * private/loopback/link-local address. At most [MAX_REDIRECTS] redirects; a redirect target is resolved as a
 * strict URI (so "/\evil" style tricks fail) and must stay https.
 *
 * Remaining gap (documented): the transport resolves the host again when connecting, so DNS that changes
 * between our check and the connect (rebinding) could aim the TCP connection elsewhere. TLS hostname
 * verification still requires a valid certificate for the checked name, so no response body from such a host
 * is accepted.
 */
class SafeFetcher(private val connector: HttpConnector) {

    fun interface HopRule { fun allowed(url: String, hop: Int): Boolean }

    fun open(startUrl: String, rule: HopRule, headers: Map<String, String> = emptyMap()): HopResponse {
        var url = startUrl
        var hop = 0
        while (true) {
            val v = UrlPolicy.check(url, maxLength = 8192)
            if (v !is UrlPolicy.Verdict.Allowed) throw PolicyError((v as UrlPolicy.Verdict.Rejected).reason)
            if (!rule.allowed(url, hop)) {
                throw PolicyError(
                    if (hop == 0) "This source doesn't allow downloads from ${v.host}."
                    else "The download was redirected to a website this source doesn't allow (${v.host}).",
                )
            }
            val addrs = try { connector.resolve(v.host) } catch (e: IOException) {
                throw IOException("Couldn't find the website “${v.host}”. Check the link and your internet connection.", e)
            }
            if (addrs.isEmpty() || addrs.any { IpRanges.isBlocked(it) }) {
                throw PolicyError("“${v.host}” points to this device or your local network, which isn't allowed.")
            }
            val r = connector.open(url, headers)
            when (r.code) {
                in 200..299 -> return r
                301, 302, 303, 307, 308 -> {
                    val location = r.location
                    r.close()
                    if (location.isNullOrBlank()) throw PolicyError("The server sent a broken redirect.")
                    if (++hop > MAX_REDIRECTS) throw PolicyError("Too many redirects.")
                    url = resolveRedirect(url, location)
                }
                else -> {
                    val body = try { readBounded(r.errorBody(), 64 * 1024) } catch (_: Exception) { null }
                    val h = r.headers
                    r.close()
                    throw HttpError(r.code, body, h)
                }
            }
        }
    }

    companion object {
        const val MAX_REDIRECTS = 5

        fun resolveRedirect(base: String, location: String): String {
            val next = try { URI(base).resolve(URI(location.trim())) } catch (_: Exception) {
                throw PolicyError("The server sent a broken redirect.")
            }
            if (!"https".equals(next.scheme, ignoreCase = true)) {
                throw PolicyError("The download was redirected to an insecure (non-https) address.")
            }
            return next.toString()
        }

        fun readBounded(input: InputStream?, max: Int): String? {
            if (input == null) return null
            input.use {
                val out = ByteArrayOutputStream()
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
