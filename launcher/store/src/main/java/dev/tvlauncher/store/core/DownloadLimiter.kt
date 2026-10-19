package dev.tvlauncher.store.core

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Hard size cap for downloads. A declared Content-Length above the cap is refused before reading, and the
 * copy loop aborts as soon as the running total would exceed the cap, so a server that lies about (or omits)
 * the length can never fill the disk. The copy also hashes (SHA-256) and reports progress while streaming.
 */
class DownloadLimiter(val cap: Long = DEFAULT_CAP) {

    class TooLarge(val cap: Long) : IOException("Download is larger than the ${cap / (1024 * 1024)} MB limit")
    class Cancelled : IOException("Download cancelled")

    data class Result(val bytes: Long, val sha256: String)

    var total: Long = 0
        private set

    fun acceptsDeclared(contentLength: Long): Boolean = contentLength < 0 || contentLength <= cap

    /** Accounts for [n] more bytes; throws [TooLarge] when the cap would be exceeded. */
    fun onChunk(n: Int) {
        if (n <= 0) return
        if (total + n > cap) throw TooLarge(cap)
        total += n
    }

    fun copy(
        input: InputStream,
        output: OutputStream,
        bufferSize: Int = 64 * 1024,
        onProgress: (Long) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Result {
        val buf = ByteArray(bufferSize)
        val hash = Sha256Stream()
        while (true) {
            if (isCancelled()) throw Cancelled()
            // Never ask for more than one byte beyond the cap, so an over-long stream is caught on this read.
            val room = cap - total
            val want = if (room >= bufferSize) bufferSize else (room + 1).toInt()
            val n = input.read(buf, 0, want)
            if (n < 0) break
            onChunk(n)
            output.write(buf, 0, n)
            hash.update(buf, 0, n)
            onProgress(total)
        }
        return Result(total, hash.hexDigest())
    }

    companion object {
        const val DEFAULT_CAP: Long = 300L * 1024 * 1024
    }
}
