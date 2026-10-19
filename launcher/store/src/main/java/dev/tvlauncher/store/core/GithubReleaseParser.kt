package dev.tvlauncher.store.core

import org.json.JSONArray
import org.json.JSONObject

data class GithubAsset(
    val name: String,
    val size: Long,
    val url: String,
    /**
     * The asset's published "sha256:…" digest exactly as GitHub sent it (validated later — a malformed value makes
     * the download fail closed), or null when no sha256 digest was published.
     */
    val sha256: String?,
)

data class GithubRelease(
    /** Raw tag (used to bind asset URLs and to compare releases). Display it via [displayTag]. */
    val tag: String,
    /** Sanitised release name for display. */
    val name: String,
    val prerelease: Boolean,
    val apkAssets: List<GithubAsset>,
) {
    val displayTag: String get() = DisplayText.sanitize(tag, DisplayText.TAG)
}

/**
 * Tolerant parser for GitHub's release JSON (a single release or an array of releases). Drafts are always
 * ignored, pre-releases unless asked for; only uploaded `.apk` assets whose download URL is exactly
 * https://github.com/<owner>/<repo>/releases/download/<tag>/<name> of the REQUESTED repository survive
 * (so a release can't point the user at another repo, raw.githubusercontent.com or any other host).
 * Never throws: junk yields null.
 */
object GithubReleaseParser {

    private const val MAX_INPUT = 4 * 1024 * 1024
    private const val MAX_DEPTH = 32

    fun parse(json: String?, ref: GithubRepoRef, includePrerelease: Boolean = false): GithubRelease? = try {
        if (json == null || json.length > MAX_INPUT || !depthOk(json)) null else {
            when (val root = org.json.JSONTokener(json).nextValue()) {
                is JSONObject -> release(root, ref, includePrerelease)
                is JSONArray -> (0 until minOf(root.length(), 100)).asSequence()
                    .mapNotNull { root.optJSONObject(it) }
                    .mapNotNull { release(it, ref, includePrerelease) }
                    .firstOrNull()
                else -> null
            }
        }
    } catch (_: Exception) {
        null
    }

    /** The "message" of a GitHub API error body, if any. */
    fun errorMessage(json: String?): String? = try {
        if (json == null || json.length > MAX_INPUT || !depthOk(json)) null
        else (org.json.JSONTokener(json).nextValue() as? JSONObject)?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    private fun release(o: JSONObject, ref: GithubRepoRef, includePrerelease: Boolean): GithubRelease? {
        if (o.optBoolean("draft", false)) return null
        val pre = o.optBoolean("prerelease", false)
        if (pre && !includePrerelease) return null
        val name = str(o, "name")
        val tag = (str(o, "tag_name") ?: name ?: "latest").take(200)
        val assets = o.optJSONArray("assets")
        val list = ArrayList<GithubAsset>()
        if (assets != null) {
            for (i in 0 until minOf(assets.length(), 200)) {
                val a = assets.optJSONObject(i) ?: continue
                asset(a, ref, tag)?.let { list += it }
            }
        }
        val display = DisplayText.sanitize(name ?: tag, DisplayText.RELEASE_NAME)
        return GithubRelease(tag = tag, name = display, prerelease = pre, apkAssets = list)
    }

    private fun asset(a: JSONObject, ref: GithubRepoRef, tag: String): GithubAsset? {
        val name = str(a, "name") ?: return null
        if (!name.lowercase().endsWith(".apk")) return null
        val state = str(a, "state")
        if (state != null && state != "uploaded") return null
        val url = str(a, "browser_download_url") ?: return null
        if (!HostAllowlist.isGithubReleaseDownloadUrl(url, ref.owner, ref.repo, tag, name)) return null
        val size = (a.opt("size") as? Number)?.toLong()?.takeIf { it >= 0 } ?: -1L
        // Keep a sha256 digest verbatim; a malformed one must fail the download, not silently skip verification.
        val digest = (a.opt("digest") as? String)?.trim()?.takeIf { it.lowercase().startsWith("sha256:") }?.take(100)
        return GithubAsset(name = name.take(200), size = size, url = url, sha256 = digest)
    }

    private fun str(o: JSONObject, key: String): String? = (o.opt(key) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    private fun depthOk(s: String): Boolean {
        var depth = 0
        var inStr = false
        var esc = false
        for (c in s) {
            if (inStr) {
                if (esc) esc = false else if (c == '\\') esc = true else if (c == '"') inStr = false
                continue
            }
            when (c) {
                '"' -> inStr = true
                '{', '[' -> { depth++; if (depth > MAX_DEPTH) return false }
                '}', ']' -> depth--
            }
        }
        return true
    }
}
