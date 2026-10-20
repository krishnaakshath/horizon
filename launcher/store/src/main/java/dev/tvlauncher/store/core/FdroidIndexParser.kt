package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.JsonTokenSource.Token

/** One installable F-Droid app, reduced to what H02 Store needs (a compact catalog entry). */
data class FdroidApp(
    val packageName: String,
    val name: String,
    val summary: String,
    val versionName: String,
    val versionCode: Long,
    /** Bare file name inside https://f-droid.org/repo/, validated to be a plain "*.apk" name. */
    val apkName: String,
    /** Lower-case hex SHA-256 of the APK file, from the index. */
    val sha256: String,
    val size: Long,
    /** SHA-256 of the signing certificate as recorded by F-Droid, or null. */
    val signerSha256: String?,
    val minSdk: Int,
    val tvReady: Boolean,
    val curated: Boolean,
    val antiFeatures: List<String>,
    val categories: List<String>,
) {
    val apkUrl: String get() = FdroidIndexParser.REPO + apkName
}

/**
 * Streaming parser for F-Droid's index-v2.json (~66 MB uncompressed). Memory stays bounded: one package's
 * metadata and its single best version are held at a time, long fields (descriptions, screenshots, whatsNew)
 * are skipped token by token, and the kept list is capped by entry count and an estimated byte budget.
 *
 * For each package it picks the highest versionCode that is compatible with this device (minSdk/maxSdk, native
 * ABIs) and not on a beta release channel. "TV-ready" = metadata has a tvBanner or TV screenshots, or a
 * version declares the android.software.leanback feature. Priority entries (TV-ready, curated, matching the
 * query) are always kept; other compatible apps are kept only while the byte budget lasts (when
 * [Config.keepAllCompatible]). Malformed entries are dropped; a broken stream returns the partial result.
 */
class FdroidIndexParser(private val config: Config) {

    data class Config(
        val abis: List<String>,
        val sdkInt: Int,
        val curated: Set<String> = emptySet(),
        val query: String? = null,
        val keepAllCompatible: Boolean = true,
        val maxEntries: Int = 6000,
        val maxBytes: Long = 2_500_000,
        val includeBeta: Boolean = false,
        val maxDepth: Int = 64,
        val maxString: Int = 64 * 1024,
    )

    data class Result(
        val apps: List<FdroidApp>,
        val packagesSeen: Int,
        val truncated: Boolean,
        val error: String?,
    )

    private class Version(
        val code: Long, val name: String, val apk: String, val sha: String, val size: Long,
        val signer: String?, val minSdk: Int, val abiRank: Int, val anti: List<String>,
    )

    private val abis = config.abis.map { it.lowercase() }
    private val query = config.query?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun parse(source: JsonTokenSource, isCancelled: () -> Boolean = { false }): Result {
        // Structural limits on untrusted input: nesting ≤ 64, any string/name ≤ 64 KB (abort beyond).
        val src = BoundedTokenSource(source, maxDepth = config.maxDepth, maxString = config.maxString)
        val out = ArrayList<FdroidApp>()
        var seen = 0
        var truncated = false
        var bytes = 0L
        var error: String? = null
        try {
            src.forEachMember { key ->
                if (key != "packages") { src.skipValue(); return@forEachMember }
                src.forEachMember { pkg ->
                    if (isCancelled()) throw CancelledParse()
                    seen++
                    val app = readPackage(src, pkg)
                    if (app != null) {
                        val priority = app.tvReady || app.curated || matchesQuery(app)
                        val cost = estimate(app)
                        when {
                            out.size >= config.maxEntries -> truncated = true
                            priority -> { out += app; bytes += cost }
                            !config.keepAllCompatible -> Unit
                            bytes + cost <= config.maxBytes -> { out += app; bytes += cost }
                            else -> truncated = true
                        }
                    }
                }
            }
        } catch (_: CancelledParse) {
            error = "cancelled"
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
        } catch (e: StackOverflowError) {
            error = "index too deeply nested"
        }
        return Result(out, seen, truncated, error)
    }

    private class CancelledParse : RuntimeException()

    private fun matchesQuery(app: FdroidApp): Boolean {
        val q = query ?: return false
        return app.packageName.lowercase().contains(q) || app.name.lowercase().contains(q)
    }

    private fun readPackage(src: JsonTokenSource, pkg: String): FdroidApp? {
        var name: String? = null
        var summary: String? = null
        var categories: List<String> = emptyList()
        var tv = false
        var best: Version? = null
        src.forEachMember { key ->
            when (key) {
                "metadata" -> src.forEachMember { m ->
                    when (m) {
                        "name" -> name = localized(src)
                        "summary" -> summary = localized(src)
                        "categories" -> categories = stringList(src, 8)
                        "tvBanner" -> { tv = true; src.skipValue() }
                        "screenshots" -> src.forEachMember { k -> if (k == "tv") tv = true; src.skipValue() }
                        else -> src.skipValue()
                    }
                }
                "versions" -> src.forEachMember { _ ->
                    val (v, leanback) = readVersion(src)
                    if (leanback) tv = true
                    if (v != null) {
                        val b = best
                        if (b == null || v.code > b.code || (v.code == b.code && v.abiRank < b.abiRank)) best = v
                    }
                }
                else -> src.skipValue()
            }
        }
        if (!PACKAGE.matches(pkg)) return null
        val v = best ?: return null
        return FdroidApp(
            packageName = pkg,
            name = DisplayText.sanitize(name ?: pkg, DisplayText.NAME).ifEmpty { pkg },
            summary = DisplayText.sanitize(summary, DisplayText.SUMMARY),
            versionName = DisplayText.sanitize(v.name, 40),
            versionCode = v.code,
            apkName = v.apk,
            sha256 = v.sha,
            size = v.size,
            signerSha256 = v.signer,
            minSdk = v.minSdk,
            tvReady = tv,
            curated = pkg in config.curated,
            antiFeatures = v.anti,
            categories = categories,
        )
    }

    /** Returns the version if it is installable here, plus whether it declares the leanback feature. */
    private fun readVersion(src: JsonTokenSource): Pair<Version?, Boolean> {
        var apk: String? = null
        var sha: String? = null
        var size = -1L
        var code: Long? = null
        var vName: String? = null
        var minSdk = 1
        var maxSdk = Int.MAX_VALUE
        var native: List<String> = emptyList()
        var signer: String? = null
        var leanback = false
        var beta = false
        var anti: List<String> = emptyList()
        src.forEachMember { key ->
            when (key) {
                "file" -> src.forEachMember { f ->
                    when (f) {
                        "name" -> apk = src.stringOrNull()
                        "sha256" -> sha = src.stringOrNull()
                        "size" -> size = src.longOrNull() ?: -1L
                        else -> src.skipValue()
                    }
                }
                "manifest" -> src.forEachMember { m ->
                    when (m) {
                        "versionCode" -> code = src.longOrNull()
                        "versionName" -> vName = src.stringOrNull()
                        "nativecode" -> native = stringList(src, 16)
                        "usesSdk" -> src.forEachMember { u ->
                            when (u) {
                                "minSdkVersion" -> minSdk = src.longOrNull()?.toInt() ?: 1
                                "maxSdkVersion" -> maxSdk = src.longOrNull()?.toInt() ?: Int.MAX_VALUE
                                else -> src.skipValue()
                            }
                        }
                        "maxSdkVersion" -> maxSdk = src.longOrNull()?.toInt() ?: Int.MAX_VALUE
                        "signer" -> src.forEachMember { s ->
                            if (s == "sha256") {
                                val l = stringList(src, 4, maxLen = 128)
                                signer = l.firstNotNullOfOrNull { Sha256Stream.normalise(it) }
                            } else src.skipValue()
                        }
                        "features" -> src.forEachElement {
                            val f = when (src.peek()) {
                                Token.STRING -> src.nextString()
                                Token.BEGIN_OBJECT -> {
                                    var n: String? = null
                                    src.forEachMember { k -> if (k == "name") n = src.stringOrNull() else src.skipValue() }
                                    n
                                }
                                else -> { src.skipValue(); null }
                            }
                            if (f == LEANBACK) leanback = true
                        }
                        else -> src.skipValue()
                    }
                }
                "releaseChannels" -> beta = stringList(src, 8).isNotEmpty()
                "antiFeatures" -> {
                    val keys = ArrayList<String>()
                    src.forEachMember { k -> if (keys.size < 8) keys += k.take(40); src.skipValue() }
                    anti = keys.map { DisplayText.sanitize(it, 40) }.filter { it.isNotEmpty() }
                }
                else -> src.skipValue()
            }
        }
        val fileName = apk?.removePrefix("/") ?: return null to leanback
        if (!APK_NAME.matches(fileName) || fileName.contains("..")) return null to leanback
        val hash = Sha256Stream.normalise(sha) ?: return null to leanback
        val c = code ?: return null to leanback
        if (beta && !config.includeBeta) return null to leanback
        if (minSdk > config.sdkInt || maxSdk < config.sdkInt) return null to leanback
        val abiRank = if (native.isEmpty()) abis.size else {
            val ranks = native.map { abis.indexOf(it.lowercase()) }.filter { it >= 0 }
            if (ranks.isEmpty()) return null to leanback
            ranks.min()
        }
        return Version(c, vName ?: c.toString(), fileName, hash, size, signer, minSdk, abiRank, anti) to leanback
    }

    private fun localized(src: JsonTokenSource): String? {
        return when (src.peek()) {
            Token.STRING -> src.nextString()
            Token.BEGIN_OBJECT -> {
                var en: String? = null
                var enAny: String? = null
                var first: String? = null
                src.forEachMember { lang ->
                    val v = src.stringOrNull()
                    if (v != null) {
                        if (lang == "en-US") en = v
                        if (enAny == null && lang.startsWith("en")) enAny = v
                        if (first == null) first = v
                    }
                }
                en ?: enAny ?: first
            }
            else -> { src.skipValue(); null }
        }
    }

    private fun stringList(src: JsonTokenSource, max: Int, maxLen: Int = 60): List<String> {
        val out = ArrayList<String>()
        src.forEachElement {
            val s = src.stringOrNull()
            if (s != null && out.size < max) out += s.take(maxLen)
        }
        return out
    }

    private fun estimate(a: FdroidApp): Long =
        (200 + a.packageName.length + a.name.length + a.summary.length + a.versionName.length + a.apkName.length +
            a.categories.sumOf { it.length + 3 } + a.antiFeatures.sumOf { it.length + 3 }).toLong()

    companion object {
        const val REPO = "https://f-droid.org/repo/"
        const val INDEX_URL = REPO + "index-v2.json"
        const val LEANBACK = "android.software.leanback"
        private val APK_NAME = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,200}\\.apk$")
        private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")
    }
}
