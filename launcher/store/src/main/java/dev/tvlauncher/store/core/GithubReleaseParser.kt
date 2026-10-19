package dev.tvlauncher.store.core

import org.json.JSONArray
import org.json.JSONObject

data class GithubAsset(
    val name: String,
    val size: Long,
    val url: String,
    /** Lower-case hex SHA-256 from the asset's "digest" field, or null when GitHub did not publish one. */
    val sha256: String?,
)

data class GithubRelease(
    val tag: String,
    val name: String,
    val prerelease: Boolean,
    val apkAssets: List<GithubAsset>,
)

/**
 * Tolerant parser for GitHub's release JSON (a single release or an array of releases). Drafts are always
 * ignored, pre-releases unless asked for; only uploaded `.apk` assets whose download URL is an allowlisted
 * https GitHub host survive. Never throws: junk yields null.
 */
object GithubReleaseParser {

    private const val MAX_INPUT = 4 * 1024 * 1024
    private const val MAX_DEPTH = 32

    fun parse(json: String?, includePrerelease: Boolean = false): GithubRelease? = try {
        if (json == null || json.length > MAX_INPUT || !depthOk(json)) null else {
            when (val root = org.json.JSONTokener(json).nextValue()) {
                is JSONObject -> release(root, includePrerelease)
                is JSONArray -> (0 until minOf(root.length(), 100)).asSequence()
                    .mapNotNull { root.optJSONObject(it) }
                    .mapNotNull { release(it, includePrerelease) }
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

    private fun release(o: JSONObject, includePrerelease: Boolean): GithubRelease? {
        if (o.optBoolean("draft", false)) return null
        val pre = o.optBoolean("prerelease", false)
        if (pre && !includePrerelease) return null
        val name = str(o, "name")
        val tag = str(o, "tag_name") ?: name ?: "latest"
        val assets = o.optJSONArray("assets")
        val list = ArrayList<GithubAsset>()
        if (assets != null) {
            for (i in 0 until minOf(assets.length(), 200)) {
                val a = assets.optJSONObject(i) ?: continue
                asset(a)?.let { list += it }
            }
        }
        return GithubRelease(tag = tag, name = name ?: tag, prerelease = pre, apkAssets = list)
    }

    private fun asset(a: JSONObject): GithubAsset? {
        val name = str(a, "name") ?: return null
        if (!name.lowercase().endsWith(".apk")) return null
        val state = str(a, "state")
        if (state != null && state != "uploaded") return null
        val url = str(a, "browser_download_url") ?: return null
        if (!HostAllowlist.isGithubAssetUrl(url)) return null
        val size = (a.opt("size") as? Number)?.toLong()?.takeIf { it >= 0 } ?: -1L
        val digest = str(a, "digest")?.let { d ->
            if (d.trim().lowercase().startsWith("sha256:")) Sha256Stream.normalise(d) else null
        }
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
