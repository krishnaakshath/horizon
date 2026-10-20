package dev.tvlauncher.link.update

import org.json.JSONObject

/**
 * "Is there a newer launcher?" from a GitHub releases feed. Pure parsing and version rules.
 *
 * Off by default: nothing is checked until the owner switches it on in Link's settings AND a
 * repository is set ([DEFAULT_REPO] is empty on purpose: the project is private until the owner
 * says otherwise). The check only reads the public release JSON; downloading and installing is
 * left to H02 Store, which verifies the published SHA-256 and the signing certificate itself.
 */
object UpdateCheck {
    /** "owner/repo" of the launcher's releases; empty = disabled. */
    const val DEFAULT_REPO = ""
    const val MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L
    const val MAX_BYTES = 512 * 1024

    private val SLUG = Regex("^[A-Za-z0-9_.-]{1,39}/[A-Za-z0-9_.-]{1,100}$")
    private val SHA256 = Regex("^[0-9a-f]{64}$")

    fun validRepo(s: String?): Boolean {
        if (s == null || !SLUG.matches(s)) return false
        return s.split('/').none { p -> p.all { it == '.' } }
    }

    fun latestUrl(repo: String): String? = if (validRepo(repo)) "https://api.github.com/repos/$repo/releases/latest" else null

    data class Asset(val name: String, val url: String, val size: Long, val sha256: String?)
    data class Release(val tag: String, val draft: Boolean, val prerelease: Boolean, val apk: Asset?)

    fun parse(json: String): Release? {
        val o = try { JSONObject(json) } catch (e: Exception) { return null }
        val tag = o.optString("tag_name", "").trim()
        if (tag.isEmpty() || tag.length > 64 || tag.any { it.isISOControl() }) return null
        val assets = o.optJSONArray("assets")
        val list = ArrayList<Asset>()
        val names = HashMap<String, String>()
        if (assets != null) for (i in 0 until minOf(assets.length(), 100)) {
            val a = assets.optJSONObject(i) ?: continue
            val name = a.optString("name", "")
            val url = a.optString("browser_download_url", "")
            if (name.isEmpty() || !url.startsWith("https://")) continue
            names[name] = url
            val digest = a.optString("digest", "").lowercase()
            val sha = digest.removePrefix("sha256:").takeIf { digest.startsWith("sha256:") && SHA256.matches(it) }
            if (name.endsWith(".apk", ignoreCase = true)) list += Asset(name, url, a.optLong("size", 0L), sha)
        }
        val apk = pickApk(list)
        return Release(tag, o.optBoolean("draft", false), o.optBoolean("prerelease", false), apk)
    }

    /**
     * The launcher's APK: release files are named "H02-app-<version>.apk" (1.0.0 used
     * "H02-h02-<version>.apk", the short-lived M02 builds "M02-app-<version>.apk"); else one named like
     * "launcher"/"horizon"/"app"; else the only APK.
     */
    fun pickApk(apks: List<Asset>): Asset? {
        if (apks.size <= 1) return apks.firstOrNull()
        val preferred = listOf("h02-app-", "h02-h02-", "m02-app-", "launcher", "horizon", "mintaka", "app-release", "app") // legacy name
        for (p in preferred) apks.firstOrNull { it.name.lowercase().contains(p) }?.let { return it }
        return null
    }

    /** Numeric parts of "v1.4.0-beta" -> [1, 4, 0]; empty when there are none. */
    fun versionParts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.takeWhile { it.isDigit() } }.takeWhile { it.isNotEmpty() }
            .map { it.take(9).toInt() }

    /** True when [latest] is a strictly higher version than [installed]. Unparseable = false. */
    fun isNewer(latest: String, installed: String): Boolean {
        val a = versionParts(latest)
        val b = versionParts(installed)
        if (a.isEmpty() || b.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** An update worth showing: not a draft or pre-release, newer, and with an APK to install. */
    fun available(r: Release?, installed: String): Boolean =
        r != null && !r.draft && !r.prerelease && r.apk != null && isNewer(r.tag, installed)

    fun due(lastCheckMs: Long, nowMs: Long, force: Boolean): Boolean =
        force || lastCheckMs <= 0L || nowMs - lastCheckMs >= MIN_INTERVAL_MS || nowMs < lastCheckMs
}
