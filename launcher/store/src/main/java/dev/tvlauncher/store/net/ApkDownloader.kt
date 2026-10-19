package dev.tvlauncher.store.net

import android.content.Context
import dev.tvlauncher.store.core.ApkFetch
import dev.tvlauncher.store.core.DownloadLimiter
import dev.tvlauncher.store.core.Format
import dev.tvlauncher.store.core.HashExpectation
import dev.tvlauncher.store.core.HttpError
import dev.tvlauncher.store.core.JsonLimitExceeded
import dev.tvlauncher.store.core.PolicyError
import dev.tvlauncher.store.core.SafeFetcher
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Downloads an APK into the app-private cacheDir/downloads/<random>.apk (never external storage). All checks
 * (fail-closed hash, cap, verification, delete on failure) are in the pure [ApkFetch].
 */
class ApkDownloader(private val context: Context, http: SecureHttp) {

    private val fetch = ApkFetch(http.safeFetcher)

    fun download(
        url: String,
        rule: SafeFetcher.HopRule,
        published: HashExpectation,
        declaredSize: Long,
        isCancelled: () -> Boolean,
        onProgress: (done: Long, total: Long) -> Unit,
    ): ApkFetch.Result {
        val file = File(downloadsDir(context), UUID.randomUUID().toString() + ".apk")
        return fetch.fetchTo(file, url, rule, published, declaredSize, isCancelled, onProgress)
    }

    companion object {
        fun downloadsDir(context: Context): File = File(context.cacheDir, "downloads").apply { mkdirs() }

        /** Removes leftovers from earlier runs (crash, process death, abandoned install). */
        fun cleanStale(context: Context) {
            downloadsDir(context).listFiles()?.forEach { it.delete() }
        }

        fun describe(e: Throwable): String = when (e) {
            is ApkFetch.MalformedHash -> "The published checksum is malformed, so the download was refused."
            is ApkFetch.HashMismatch -> "The downloaded file doesn't match the checksum published by the source, so it was deleted. " +
                "It may have been corrupted or tampered with."
            is DownloadLimiter.TooLarge -> "This file is larger than the ${Format.size(e.cap)} limit, so it wasn't downloaded."
            is DownloadLimiter.Cancelled -> "Download cancelled."
            is JsonLimitExceeded -> "The catalog contained unexpected data, so it was ignored."
            is PolicyError -> e.message ?: "That address isn't allowed."
            is HttpError -> when (e.code) {
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
