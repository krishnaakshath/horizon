package dev.tvlauncher.store.net

import dev.tvlauncher.store.core.HopResponse
import dev.tvlauncher.store.core.HttpConnector
import dev.tvlauncher.store.core.PolicyError
import dev.tvlauncher.store.core.SafeFetcher
import java.io.IOException
import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Device transport for [SafeFetcher]: one HttpsURLConnection per hop, redirects disabled, 15 s connect /
 * 30 s read timeouts, no caches. All policy (https, hosts, redirects, private addresses) lives in SafeFetcher.
 */
class SecureHttp(private val userAgent: String) : HttpConnector {

    private val fetcher = SafeFetcher(this)

    override fun resolve(host: String): List<ByteArray> = InetAddress.getAllByName(host).map { it.address }

    override fun open(url: String, headers: Map<String, String>): HopResponse {
        val conn = URL(url).openConnection() as? HttpsURLConnection
            ?: throw PolicyError("Only secure https:// connections are allowed.")
        conn.instanceFollowRedirects = false
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", userAgent)
        // Setting Accept-Encoding ourselves disables transparent gzip, so callers see the real wire bytes.
        conn.setRequestProperty("Accept-Encoding", "identity")
        for ((k, v) in headers) conn.setRequestProperty(k, v)
        val code = try { conn.responseCode } catch (e: IOException) { conn.disconnect(); throw e }
        return HopResponse(
            code = code,
            location = conn.getHeaderField("Location"),
            contentLength = conn.contentLengthLong,
            contentEncoding = conn.contentEncoding,
            headers = conn.headerFields.filterKeys { it != null },
            bodyOpener = { conn.inputStream },
            errorOpener = { conn.errorStream },
            closer = { conn.disconnect() },
        )
    }

    /** Opens [url] following safe redirects; [rule] decides which URL is allowed at each hop index. */
    fun open(url: String, rule: SafeFetcher.HopRule, headers: Map<String, String> = emptyMap()): HopResponse =
        fetcher.open(url, rule, headers)

    val safeFetcher: SafeFetcher get() = fetcher

    companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
