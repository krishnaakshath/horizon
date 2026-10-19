package dev.tvlauncher.store.core

/**
 * Per-source host allowlists, layered on top of [UrlPolicy]. Matching is exact or on a whole-label suffix,
 * hosts must be plain ASCII and punycode (xn--) labels are refused so look-alike domains cannot slip through.
 */
object HostAllowlist {

    private const val GITHUB = "github.com"
    private const val GITHUB_CONTENT_SUFFIX = ".githubusercontent.com"
    private const val GITHUB_API = "api.github.com"
    private const val FDROID = "f-droid.org"

    private fun clean(host: String?): String? {
        if (host.isNullOrEmpty()) return null
        if (host.any { it.code > 0x7e || it.code < 0x21 }) return null
        val h = host.lowercase()
        if (h.endsWith(".")) return null
        if (h.split('.').any { it.isEmpty() || it.startsWith("xn--") }) return null
        return h
    }

    fun isGithubAssetHost(host: String?): Boolean {
        val h = clean(host) ?: return false
        return h == GITHUB || (h.endsWith(GITHUB_CONTENT_SUFFIX) && h.length > GITHUB_CONTENT_SUFFIX.length)
    }

    private fun allowedHost(url: String?): String? {
        val v = UrlPolicy.check(url, maxLength = 8192)
        if (v !is UrlPolicy.Verdict.Allowed) return null
        // Re-check the raw authority: the host UrlPolicy reports must be exactly what precedes the path.
        if (v.uri.rawAuthority?.substringBefore(':')?.lowercase() != v.host) return null
        return v.host
    }

    fun isGithubAssetUrl(url: String?): Boolean = isGithubAssetHost(allowedHost(url))

    fun isGithubApiUrl(url: String?): Boolean = clean(allowedHost(url)) == GITHUB_API

    fun isFdroidUrl(url: String?): Boolean = clean(allowedHost(url)) == FDROID
}
