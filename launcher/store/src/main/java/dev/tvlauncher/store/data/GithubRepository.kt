package dev.tvlauncher.store.data

import dev.tvlauncher.store.core.GithubRelease
import dev.tvlauncher.store.core.GithubReleaseParser
import dev.tvlauncher.store.core.GithubRepoRef
import dev.tvlauncher.store.core.HostAllowlist
import dev.tvlauncher.store.core.HttpError
import dev.tvlauncher.store.core.SafeFetcher
import dev.tvlauncher.store.net.SecureHttp
import java.io.IOException
import java.text.DateFormat
import java.util.Date

/** Unauthenticated GitHub REST: latest release of a repository. Rate-limit aware. */
class GithubRepository(private val http: SecureHttp) {

    class FriendlyError(message: String) : IOException(message)

    fun latest(ref: GithubRepoRef): GithubRelease {
        val body = try {
            val conn = http.open(
                ref.latestReleaseApiUrl,
                rule = { url, _ -> HostAllowlist.isGithubApiUrl(url) },
                headers = mapOf("Accept" to "application/vnd.github+json", "X-GitHub-Api-Version" to "2022-11-28"),
            )
            try {
                SafeFetcher.readBounded(conn.body(), 2 * 1024 * 1024)
            } finally {
                conn.close()
            }
        } catch (e: HttpError) {
            throw FriendlyError(describe(e, ref))
        }
        return GithubReleaseParser.parse(body, ref)
            ?: throw FriendlyError("${ref.slug} has no published release yet.")
    }

    private fun describe(e: HttpError, ref: GithubRepoRef): String {
        val remaining = e.header("X-RateLimit-Remaining")
        val reset = e.header("X-RateLimit-Reset")?.toLongOrNull()
        return when {
            e.code == 429 || (e.code == 403 && (remaining == "0" ||
                GithubReleaseParser.errorMessage(e.body)?.contains("rate limit", ignoreCase = true) == true)) -> {
                val at = reset?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
                "GitHub limits how often apps can look things up without an account. " +
                    (if (at != null) "Please try again after $at." else "Please try again in a little while.")
            }
            e.code == 404 -> "Couldn't find releases for ${ref.slug}. Check the spelling, or the project may not publish releases."
            e.code == 403 -> "GitHub refused the request (error 403). Please try again later."
            e.code >= 500 -> "GitHub is having trouble right now (error ${e.code}). Please try again later."
            else -> "GitHub answered with error ${e.code}."
        }
    }
}
