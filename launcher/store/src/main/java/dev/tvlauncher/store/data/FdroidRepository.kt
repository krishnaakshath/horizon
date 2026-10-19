package dev.tvlauncher.store.data

import android.os.Build
import android.util.JsonReader
import dev.tvlauncher.store.core.FdroidApp
import dev.tvlauncher.store.core.FdroidCatalogCodec
import dev.tvlauncher.store.core.FdroidIndexParser
import dev.tvlauncher.store.core.HostAllowlist
import dev.tvlauncher.store.net.AndroidJsonTokenSource
import dev.tvlauncher.store.net.SecureHttp
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * F-Droid catalog: streams https://f-droid.org/repo/index-v2.json (gzip on the wire, ~21 MB; ~66 MB of JSON)
 * through android.util.JsonReader into the bounded pure parser and caches the compact result (≤ 3 MB) in
 * filesDir. A refresh happens only when the user opens F-Droid and the cache is older than 24 h (or on demand).
 */
class FdroidRepository(private val files: StoreFiles, private val http: SecureHttp) {

    fun cached(): FdroidCatalogCodec.Catalog? = FdroidCatalogCodec.decode(files.readCatalogJson())

    fun refresh(isCancelled: () -> Boolean, onProgress: (done: Long, total: Long) -> Unit): FdroidCatalogCodec.Catalog {
        val conn = http.open(
            FdroidIndexParser.INDEX_URL,
            hopAllowed = { HostAllowlist.isFdroidUrl(it) },
            accept = "application/json",
            allowGzip = true,
        )
        try {
            val total = conn.contentLengthLong
            if (total > MAX_WIRE_BYTES) throw IOException("The F-Droid index is unexpectedly large.")
            val wire = CountingStream(BufferedInputStream(conn.inputStream, 64 * 1024), MAX_WIRE_BYTES) { onProgress(it, total) }
            val body: InputStream = if ("gzip".equals(conn.contentEncoding, ignoreCase = true)) GZIPInputStream(wire, 64 * 1024) else wire
            val json = CountingStream(body, MAX_JSON_BYTES) {}
            val config = FdroidIndexParser.Config(
                abis = Build.SUPPORTED_ABIS.toList(),
                sdkInt = Build.VERSION.SDK_INT,
                curated = FdroidCatalogCodec.CURATED,
            )
            val result = JsonReader(InputStreamReader(json, Charsets.UTF_8)).use { reader ->
                FdroidIndexParser(config).parse(AndroidJsonTokenSource(reader), isCancelled)
            }
            if (result.error == "cancelled") throw IOException("Cancelled")
            if (result.error != null || result.apps.isEmpty()) {
                throw IOException("The F-Droid index couldn't be read" + (result.error?.let { " ($it)" } ?: "") + ".")
            }
            val now = System.currentTimeMillis()
            val text = fit(result.apps, now)
            files.write(CATALOG_FILE, text)
            return FdroidCatalogCodec.decode(text) ?: FdroidCatalogCodec.Catalog(now, result.apps)
        } finally {
            conn.disconnect()
        }
    }

    /** Encodes the catalog, dropping lowest-priority entries until it fits in the 3 MB budget. */
    private fun fit(apps: List<FdroidApp>, now: Long): String {
        var list = apps
        var text = FdroidCatalogCodec.encode(list, now)
        while (text.length > FdroidCatalogCodec.MAX_FILE_BYTES && list.isNotEmpty()) {
            val keep = list.filter { it.tvReady || it.curated }
            val rest = list.filter { !(it.tvReady || it.curated) }
            list = keep + rest.take((rest.size * 0.8).toInt())
            if (rest.isEmpty()) list = list.take((list.size * 0.8).toInt())
            text = FdroidCatalogCodec.encode(list, now)
        }
        return text
    }

    /** Counts bytes, reports progress and aborts beyond [max] (also bounds gzip decompression). */
    private class CountingStream(input: InputStream, private val max: Long, private val progress: (Long) -> Unit) :
        FilterInputStream(input) {
        private var count = 0L
        private var lastReport = 0L

        private fun add(n: Int) {
            if (n <= 0) return
            count += n
            if (count > max) throw IOException("The F-Droid index is unexpectedly large.")
            if (count - lastReport >= 256 * 1024) { lastReport = count; progress(count) }
        }

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { add(it) }
        override fun skip(n: Long): Long = super.skip(n).also { add(it.toInt()) }
    }

    companion object {
        const val CATALOG_FILE = "fdroid-catalog.json"
        private const val MAX_WIRE_BYTES = 120L * 1024 * 1024
        private const val MAX_JSON_BYTES = 400L * 1024 * 1024
    }
}
