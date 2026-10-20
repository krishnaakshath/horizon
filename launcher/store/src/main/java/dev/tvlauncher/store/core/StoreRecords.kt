package dev.tvlauncher.store.core

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** An app installed through M02 Store, and where it came from (so "Check update" can re-query the source). */
data class InstalledRecord(
    val packageName: String,
    val label: String,
    val source: Source,
    /** F-Droid package id, GitHub "owner/repo" or the https link. */
    val ref: String,
    /** versionCode (F-Droid / link) or release tag (GitHub) at install time. */
    val version: String,
    val installedAt: Long,
) {
    enum class Source { LINK, FDROID, GITHUB }
}

/** Tiny JSON registry of installed-by-us packages. Tolerant decoding; invalid package ids are dropped. */
object InstalledRegistry {

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    const val MAX = 200

    fun isValidPackageName(p: String?): Boolean = p != null && p.length <= 255 && PACKAGE.matches(p)

    fun upsert(list: List<InstalledRecord>, r: InstalledRecord): List<InstalledRecord> =
        (listOf(r) + list.filter { it.packageName != r.packageName }).take(MAX)

    fun remove(list: List<InstalledRecord>, packageName: String): List<InstalledRecord> =
        list.filter { it.packageName != packageName }

    fun encode(list: List<InstalledRecord>): String {
        val arr = JSONArray()
        for (r in list) arr.put(JSONObject().apply {
            put("pkg", r.packageName); put("label", r.label); put("src", r.source.name)
            put("ref", r.ref); put("ver", r.version); put("at", r.installedAt)
        })
        return arr.toString()
    }

    fun decode(json: String?): List<InstalledRecord> = try {
        val arr = JSONArray(json ?: "")
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val pkg = o.optString("pkg")
            if (!isValidPackageName(pkg)) return@mapNotNull null
            val src = runCatching { InstalledRecord.Source.valueOf(o.optString("src")) }.getOrDefault(InstalledRecord.Source.LINK)
            InstalledRecord(pkg, DisplayText.sanitize(o.optString("label"), DisplayText.LABEL).ifEmpty { pkg }, src, o.optString("ref"), o.optString("ver"), o.optLong("at"))
        }
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Recently used install links: most recent first, deduplicated, only URLs that pass [UrlPolicy]. Only
 * scheme + host + path are stored — query strings and fragments (often one-time tokens) are dropped.
 */
object LinkHistory {
    const val MAX = 10

    /** scheme://host/path only: query, fragment and any user-info are removed. */
    fun strip(url: String): String {
        var u = url.trim()
        val cut = u.indexOfFirst { it == '?' || it == '#' }
        if (cut >= 0) u = u.substring(0, cut)
        val schemeEnd = u.indexOf("://")
        if (schemeEnd >= 0) {
            val authStart = schemeEnd + 3
            val pathStart = u.indexOf('/', authStart).let { if (it < 0) u.length else it }
            val at = u.lastIndexOf('@', pathStart - 1)
            if (at >= authStart) u = u.substring(0, authStart) + u.substring(at + 1)
        }
        return u
    }

    fun add(list: List<String>, url: String): List<String> {
        val u = strip(url)
        if (!UrlPolicy.isAllowedApkUrl(u)) return list
        return (listOf(u) + list.filter { it != u }).take(MAX)
    }

    fun encode(list: List<String>): String = JSONArray(list).toString()

    fun decode(json: String?): List<String> = try {
        val arr = JSONArray(json ?: "")
        (0 until arr.length()).mapNotNull { (arr.opt(it) as? String)?.let { s -> strip(s) } }
            .filter { UrlPolicy.isAllowedApkUrl(it) }.distinct().take(MAX)
    } catch (_: Exception) {
        emptyList()
    }
}

/** GitHub repositories the user added, as validated "owner/repo" slugs. */
object RepoList {
    const val MAX = 30

    fun add(list: List<String>, slug: String): List<String> {
        val s = GithubRepoRef.parse(slug)?.slug ?: return list
        return (listOf(s) + list.filter { !it.equals(s, ignoreCase = true) }).take(MAX)
    }

    fun encode(list: List<String>): String = JSONArray(list).toString()

    fun decode(json: String?): List<String> = try {
        val arr = JSONArray(json ?: "")
        (0 until arr.length()).mapNotNull { (arr.opt(it) as? String)?.let { s -> GithubRepoRef.parse(s)?.slug } }.distinct().take(MAX)
    } catch (_: Exception) {
        emptyList()
    }
}

object Format {
    fun size(bytes: Long): String {
        if (bytes < 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    /** 0..100, or -1 when the total is unknown. */
    fun percent(done: Long, total: Long): Int {
        if (total <= 0) return -1
        return ((done * 100) / total).coerceIn(0, 100).toInt()
    }

    fun sameRelease(a: String?, b: String?): Boolean {
        if (a == null || b == null) return false
        fun n(s: String) = s.trim().lowercase().removePrefix("v")
        return n(a) == n(b)
    }
}
