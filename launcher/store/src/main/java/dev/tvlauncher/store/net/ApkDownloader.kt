package dev.tvlauncher.store.net

import android.content.Context
import dev.tvlauncher.store.core.DownloadLimiter
import dev.tvlauncher.store.core.Format
import dev.tvlauncher.store.core.Sha256Stream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID

/**
 * Downloads an APK into the app-private cacheDir/downloads/<random>.apk (never external storage), with the
 * 300 MB cap, SHA-256 computed while streaming and verified when the source published a hash. Any failure,
 * cancellation or hash mismatch deletes the partial file.
 */
class ApkDownloader(private val context: Context, private val http: SecureHttp) {

    class HashMismatch(val expected: String, val actual: String) :
        IOException("The downloaded file doesn't match the checksum published by the source.")

    data class Downloaded(val file: File, val sha256: String, val size: Long, val hashVerified: Boolean)

    fun download(
        url: String,
        hopAllowed: (String) -> Boolean,
        expectedSha256: String?,
        declaredSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (done: Long, total: Long) -> Unit,
    ): Downloaded {
        val limiter = DownloadLimiter()
        if (declaredSize > limiter.cap) throw DownloadLimiter.TooLarge(limiter.cap)
        val expected = expectedSha256?.let { Sha256Stream.normalise(it) }
        val file = File(downloadsDir(context), UUID.randomUUID().toString() + ".apk")
        try {
            val conn = http.open(url, hopAllowed, accept = "application/vnd.android.package-archive,application/octet-stream,*/*")
            try {
                val length = conn.contentLengthLong
                if (!limiter.acceptsDeclared(length)) throw DownloadLimiter.TooLarge(limiter.cap)
                val total = if (length > 0) length else declaredSize
                onProgress(0, total)
                val result = conn.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        limiter.copy(input, out, onProgress = { done -> onProgress(done, total) }, isCancelled = isCancelled)
                    }
                }
                if (expected != null && !Sha256Stream.matches(expected, result.sha256)) {
                    throw HashMismatch(expected, result.sha256)
                }
                if (result.bytes == 0L) throw IOException("The server sent an empty file.")
                return Downloaded(file, result.sha256, result.bytes, hashVerified = expected != null)
            } finally {
                conn.disconnect()
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }

    companion object {
        fun downloadsDir(context: Context): File = File(context.cacheDir, "downloads").apply { mkdirs() }

        /** Removes leftovers from earlier runs (crash, process death) except [keep]. */
        fun cleanStale(context: Context, keep: File? = null) {
            downloadsDir(context).listFiles()?.forEach { f ->
                if (keep == null || f.canonicalPath != keep.canonicalPath) f.delete()
            }
        }

        fun describe(e: Throwable): String = when (e) {
            is HashMismatch -> "The downloaded file doesn't match the checksum published by the source, so it was deleted. " +
                "It may have been corrupted or tampered with."
            is DownloadLimiter.TooLarge -> "This file is larger than the ${Format.size(e.cap)} limit, so it wasn't downloaded."
            is DownloadLimiter.Cancelled -> "Download cancelled."
            is SecureHttp.PolicyError -> e.message ?: "That address isn't allowed."
            is SecureHttp.HttpError -> when (e.code) {
                404, 410 -> "The file wasn't found on the server (error ${e.code}). The link may be out of date."
                401, 403 -> "The server refused the download (error ${e.code})."
                429 -> "The server is busy (too many requests). Please try again later."
                in 500..599 -> "The server had a problem (error ${e.code}). Please try again later."
                else -> "The server answered with error ${e.code}."
            }
            is java.net.SocketTimeoutException -> "The connection timed out. Check your internet connection and try again."
            is java.net.UnknownHostException -> "Couldn't reach the website. Check the link and your internet connection."
            is javax.net.ssl.SSLException -> "A secure connection couldn't be established, so nothing was downloaded."
            is IOException -> e.message ?: "The download failed."
            else -> "Something went wrong: ${e.javaClass.simpleName}"
        }
    }
}
