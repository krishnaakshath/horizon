package dev.tvlauncher.store.core

/**
 * Per-source host allowlists, layered on top of [UrlPolicy]. Matching is exact or on a whole-label suffix,
 * hosts must be plain ASCII and punycode (xn--) labels are refused so look-alike domains cannot slip through.
 */
object HostAllowlist {

    private const val GITHUB = "github.com"
    /** Where github.com release downloads redirect to (documented asset storage hosts). Exact names only. */
    private val GITHUB_REDIRECT_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")
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
        return h == GITHUB || h in GITHUB_REDIRECT_HOSTS
    }

    /** A redirect hop of a GitHub asset download: only the asset storage hosts (not raw.githubusercontent.com). */
    fun isGithubRedirectTarget(url: String?): Boolean = clean(allowedHost(url)) in GITHUB_REDIRECT_HOSTS

    /**
     * True only for https://github.com/<owner>/<repo>/releases/download/<tag>/<assetName> of the repository the
     * user asked for (owner/repo compared case-insensitively), without query or fragment.
     */
    fun isGithubReleaseDownloadUrl(url: String?, owner: String, repo: String, tag: String, assetName: String): Boolean {
        if (clean(allowedHost(url)) != GITHUB) return false
        val uri = try { java.net.URI(url!!.trim()) } catch (_: Exception) { return false }
        if (uri.rawQuery != null || uri.rawFragment != null) return false
        val raw = uri.rawPath ?: return false
        if (!raw.startsWith("/")) return false
        val segs = raw.substring(1).split('/')
        if (segs.size < 6 || segs.any { it.isEmpty() }) return false
        val dec = try { segs.map { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") } } catch (_: Exception) { return false }
        if (dec.any { it == "." || it == ".." }) return false
        return dec[0].equals(owner, ignoreCase = true) &&
            dec[1].equals(repo, ignoreCase = true) &&
            dec[2] == "releases" && dec[3] == "download" &&
            dec.subList(4, dec.size - 1).joinToString("/") == tag &&
            dec.last() == assetName
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
