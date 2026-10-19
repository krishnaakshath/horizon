package dev.tvlauncher.store.core

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Downloads one APK into [file] through a [SafeFetcher]: published hash checked for well-formedness BEFORE any
 * connection (fail closed), 300 MB cap on declared and streamed size, SHA-256 while streaming, verification
 * when the source published a hash. Any failure, cancellation or mismatch deletes the file.
 */
class ApkFetch(private val fetcher: SafeFetcher) {

    class HashMismatch(val expected: String, val actual: String) :
        IOException("The downloaded file doesn't match the checksum published by the source.")

    class MalformedHash : IOException("The published checksum is malformed")

    data class Result(val file: File, val sha256: String, val size: Long, val hashVerified: Boolean)

    fun fetchTo(
        file: File,
        url: String,
        rule: SafeFetcher.HopRule,
        published: HashExpectation,
        declaredSize: Long,
        isCancelled: () -> Boolean = { false },
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
    ): Result {
        if (published is HashExpectation.Malformed) throw MalformedHash()
        val limiter = DownloadLimiter()
        if (declaredSize > limiter.cap) throw DownloadLimiter.TooLarge(limiter.cap)
        try {
            val r = fetcher.open(url, rule, mapOf(
                "Accept" to "application/vnd.android.package-archive,application/octet-stream,*/*",
                "Accept-Encoding" to "identity",
            ))
            try {
                if (!limiter.acceptsDeclared(r.contentLength)) throw DownloadLimiter.TooLarge(limiter.cap)
                val total = if (r.contentLength > 0) r.contentLength else declaredSize
                onProgress(0, total)
                val res = r.body().use { input ->
                    FileOutputStream(file).use { out ->
                        limiter.copy(input, out, onProgress = { done -> onProgress(done, total) }, isCancelled = isCancelled)
                    }
                }
                if (published is HashExpectation.Sha256 && !Sha256Stream.matches(published.hex, res.sha256)) {
                    throw HashMismatch(published.hex, res.sha256)
                }
                if (res.bytes == 0L) throw IOException("The server sent an empty file.")
                return Result(file, res.sha256, res.bytes, hashVerified = published is HashExpectation.Sha256)
            } finally {
                r.close()
            }
        } catch (e: Throwable) {
            file.delete()
            throw e
        }
    }
}
