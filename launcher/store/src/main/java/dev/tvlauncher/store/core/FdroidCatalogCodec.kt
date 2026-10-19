package dev.tvlauncher.store.core

import org.json.JSONArray
import org.json.JSONObject

/** The compact F-Droid catalog cached in filesDir (≤ 3 MB), its freshness rule and search. */
object FdroidCatalogCodec {

    const val MAX_AGE_MILLIS = 24L * 60 * 60 * 1000
    const val MAX_FILE_BYTES = 3L * 1024 * 1024

    /**
     * Ids verified to exist in the main F-Droid repository (checked against f-droid.org/api/v1/packages on
     * 2026-10-19). Always kept in the catalog and shown among the TV picks.
     */
    val CURATED: Set<String> = linkedSetOf(
        "org.jellyfin.androidtv",
        "org.videolan.vlc",
        "org.xbmc.kodi",
        "is.xyz.mpv",
        "org.schabi.newpipe",
        "com.github.libretube",
        "de.danoeh.antennapod",
    )

    data class Catalog(val savedAtMillis: Long, val apps: List<FdroidApp>)

    fun isStale(savedAt: Long, now: Long): Boolean = now < savedAt || now - savedAt >= MAX_AGE_MILLIS

    fun encode(apps: List<FdroidApp>, savedAtMillis: Long): String {
        val arr = JSONArray()
        for (a in apps) {
            arr.put(JSONObject().apply {
                put("p", a.packageName); put("n", a.name); put("s", a.summary)
                put("vn", a.versionName); put("vc", a.versionCode); put("f", a.apkName)
                put("h", a.sha256); put("z", a.size); a.signerSha256?.let { put("g", it) }
                put("m", a.minSdk); put("tv", a.tvReady); put("c", a.curated)
                put("af", JSONArray(a.antiFeatures)); put("cat", JSONArray(a.categories))
            })
        }
        return JSONObject().put("v", 1).put("t", savedAtMillis).put("apps", arr).toString()
    }

    fun decode(json: String?): Catalog? = try {
        val root = JSONObject(json ?: "")
        val arr = root.getJSONArray("apps")
        val apps = ArrayList<FdroidApp>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val sha = Sha256Stream.normalise(o.optString("h")) ?: continue
            val file = o.optString("f")
            if (!file.endsWith(".apk") || file.contains('/') || file.contains("..")) continue
            apps += FdroidApp(
                packageName = o.getString("p"),
                name = DisplayText.sanitize(o.optString("n"), DisplayText.NAME).ifEmpty { o.getString("p") },
                summary = DisplayText.sanitize(o.optString("s"), DisplayText.SUMMARY),
                versionName = DisplayText.sanitize(o.optString("vn"), 40),
                versionCode = o.optLong("vc"),
                apkName = file,
                sha256 = sha,
                size = o.optLong("z", -1),
                signerSha256 = if (o.has("g")) Sha256Stream.normalise(o.optString("g")) else null,
                minSdk = o.optInt("m", 1),
                tvReady = o.optBoolean("tv"),
                curated = o.optBoolean("c"),
                antiFeatures = strings(o.optJSONArray("af")),
                categories = strings(o.optJSONArray("cat")),
            )
        }
        Catalog(root.getLong("t"), apps)
    } catch (_: Exception) {
        null
    }

    private fun strings(a: JSONArray?): List<String> =
        if (a == null) emptyList() else (0 until a.length()).mapNotNull { a.opt(it) as? String }

    /**
     * Empty query: TV picks (TV-ready or curated), curated first. Otherwise every cached app whose name or
     * package id contains the query, TV-ready first.
     */
    fun search(apps: List<FdroidApp>, query: String, limit: Int = 60): List<FdroidApp> {
        val q = query.trim().lowercase()
        val hits = if (q.isEmpty()) apps.filter { it.tvReady || it.curated }
        else apps.filter { it.packageName.lowercase().contains(q) || it.name.lowercase().contains(q) }
        return hits.sortedWith(
            compareByDescending<FdroidApp> { it.curated }
                .thenByDescending { it.tvReady }
                .thenByDescending { q.isNotEmpty() && it.name.lowercase().startsWith(q) }
                .thenBy { it.name.lowercase() },
        ).take(limit)
    }
}
