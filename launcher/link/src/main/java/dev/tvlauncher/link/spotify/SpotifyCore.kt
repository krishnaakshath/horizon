package dev.tvlauncher.link.spotify

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Pure parts of the optional Spotify artwork lookup (Client Credentials flow, no user login):
 * token and response parsing, query building, cache keys, the per-track request limiter and
 * image-size planning. Nothing here touches the network, storage or Android.
 */
object SpotifyCore {
    const val TOKEN_URL = "https://accounts.spotify.com/api/token"
    const val API_BASE = "https://api.spotify.com/v1"
    /** Downloaded image cap (bytes) and decoded size cap. */
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024
    const val MAX_W = 1280
    const val MAX_H = 720
    /** Header-claimed dimensions above this are refused before decoding (decode bomb). */
    const val MAX_SOURCE_PIXELS = 4096L * 4096L
    const val CACHE_FILES = 20
    /** The bearer is renewed this long before Spotify says it expires. */
    const val TOKEN_MARGIN_MS = 60_000L

    enum class Kind(val wire: String) {
        ALBUM("album"), ARTIST("artist");
        companion object { fun of(s: String?): Kind? = values().firstOrNull { it.wire == s } }
    }

    data class Token(val bearer: String, val expiresAtMs: Long) {
        fun valid(now: Long) = bearer.isNotEmpty() && now < expiresAtMs - TOKEN_MARGIN_MS
        override fun toString() = "Token(expiresAt=$expiresAtMs)"
    }

    data class Image(val url: String, val width: Int, val height: Int)

    /** First search hit: its album cover (largest) and the first artist's id. */
    data class Hit(val albumImage: String?, val artistId: String?)

    /** Basic auth header value for the token request. */
    fun basicAuth(clientId: String, clientSecret: String): String =
        "Basic " + dev.tvlauncher.link.util.B64.encode("$clientId:$clientSecret".toByteArray(Charsets.UTF_8))

    const val TOKEN_BODY = "grant_type=client_credentials"

    /** Client IDs and secrets are 32 hex characters; anything else is refused before any request. */
    fun validCredential(s: String?): Boolean = s != null && s.length in 16..64 && s.all { it.isLetterOrDigit() }

    fun parseToken(json: String?, now: Long): Token? = try {
        val o = JSONObject(requireNotNull(json))
        val t = o.optString("access_token", "")
        val type = o.optString("token_type", "Bearer")
        val secs = o.optLong("expires_in", 0L)
        if (t.isEmpty() || secs <= 0 || !type.equals("bearer", true) || t.any { it == '\r' || it == '\n' }) null
        else Token(t, now + secs * 1000L)
    } catch (e: Exception) { null }

    /** Strips noise that hurts matching: "(Remastered 2011)", "- Live", "feat. X". */
    fun cleanTitle(title: String): String {
        var t = title
        t = t.replace(Regex("\\s*[\\(\\[][^\\)\\]]*(remaster|live|edit|version|mono|stereo|feat\\.?|ft\\.)[^\\)\\]]*[\\)\\]]", RegexOption.IGNORE_CASE), "")
        t = t.replace(Regex("\\s+-\\s+.*(remaster|live|edit|version|mix).*$", RegexOption.IGNORE_CASE), "")
        return t.replace('"', ' ').trim().take(100)
    }

    /** The `q` value: track:"title" artist:"artist" (album when there is no artist). Null without a title. */
    fun query(title: String?, artist: String?, album: String?): String? {
        val t = title?.let(::cleanTitle)?.takeIf { it.isNotBlank() } ?: return null
        val a = artist?.split(',', '&')?.firstOrNull()?.replace('"', ' ')?.trim()?.take(80)?.takeIf { it.isNotBlank() }
        val al = album?.replace('"', ' ')?.trim()?.take(80)?.takeIf { it.isNotBlank() }
        return buildString {
            append("track:\"").append(t).append('"')
            if (a != null) append(" artist:\"").append(a).append('"')
            else if (al != null) append(" album:\"").append(al).append('"')
        }
    }

    fun searchUrl(q: String): String = "$API_BASE/search?type=track&limit=1&q=" + URLEncoder.encode(q, "UTF-8").replace("+", "%20")

    /** Artist ids are base62, 22 characters. */
    fun validId(id: String?): Boolean = id != null && id.length in 10..40 && id.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' }

    fun artistUrl(id: String): String? = if (validId(id)) "$API_BASE/artists/$id" else null

    fun largest(images: org.json.JSONArray?): String? {
        if (images == null) return null
        var best: Image? = null
        for (i in 0 until images.length()) {
            val o = images.optJSONObject(i) ?: continue
            val url = o.optString("url", "")
            if (!url.startsWith("https://")) continue
            val img = Image(url, o.optInt("width", 0), o.optInt("height", 0))
            if (best == null || img.width.toLong() * img.height > best.width.toLong() * best.height) best = img
        }
        return best?.url
    }

    fun parseSearch(json: String?): Hit? = try {
        val items = JSONObject(requireNotNull(json)).optJSONObject("tracks")?.optJSONArray("items")
        val first = items?.optJSONObject(0)
        if (first == null) null else {
            val cover = largest(first.optJSONObject("album")?.optJSONArray("images"))
            val artist = first.optJSONArray("artists")?.optJSONObject(0)?.optString("id")?.takeIf(::validId)
            if (cover == null && artist == null) null else Hit(cover, artist)
        }
    } catch (e: Exception) { null }

    fun parseArtistImage(json: String?): String? = try {
        largest(JSONObject(requireNotNull(json)).optJSONArray("images"))
    } catch (e: Exception) { null }

    /** Normalised track identity (same key from the launcher and from Link). */
    fun trackKey(title: String?, artist: String?, album: String?, kind: Kind): String =
        dev.tvlauncher.link.core.LinkContract.spotifyKey(title, artist, album, kind.wire)

    /** Cache file names are keys only (24 hex characters): no path can be smuggled in. */
    fun validKey(k: String?): Boolean = dev.tvlauncher.link.core.LinkContract.isSpotifyKey(k)

    /** Power-of-two sample size so the decoded image fits [MAX_W]x[MAX_H]; null = refuse (bomb or nonsense). */
    fun sampleSize(w: Int, h: Int): Int? {
        if (w <= 0 || h <= 0 || w.toLong() * h > MAX_SOURCE_PIXELS) return null
        var s = 1
        while (w / s > MAX_W * 2 || h / s > MAX_H * 2) s *= 2
        return s
    }

    /** Final scale to fit inside MAX_W x MAX_H (never up). */
    fun fit(w: Int, h: Int): Pair<Int, Int> {
        val k = minOf(1.0, MAX_W.toDouble() / w, MAX_H.toDouble() / h)
        return maxOf(1, (w * k).toInt()) to maxOf(1, (h * k).toInt())
    }

    /** Least-recently-used files to delete so at most [CACHE_FILES] remain (input: name to lastModified). */
    fun evict(files: List<Pair<String, Long>>, keep: Int = CACHE_FILES): List<String> =
        if (files.size <= keep) emptyList() else files.sortedBy { it.second }.take(files.size - keep).map { it.first }

    /** "Retry-After" seconds, bounded to 1 s .. 1 h. */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toLongOrNull()?.coerceIn(1L, 3600L)?.times(1000L)
}

/**
 * At most one lookup per track key (success or failure is remembered, bounded), and a global
 * backoff after a 429 or an error: Retry-After when given, else 30 s doubling to 30 min. Pure.
 */
class TrackLimiter(private val maxKeys: Int = 200) {
    private val tried = LinkedHashMap<String, Boolean>(16, 0.75f, true)
    private var blockedUntil = 0L
    private var failures = 0

    @Synchronized
    fun mayRequest(key: String, now: Long): Boolean {
        if (now < blockedUntil) return false
        if (tried.containsKey(key)) return false
        if (tried.size >= maxKeys) tried.keys.firstOrNull()?.let { tried.remove(it) }
        tried[key] = true
        return true
    }

    @Synchronized
    fun success() { failures = 0 }

    /** A 429 or an error: back off for everything. [retryAfterMs] wins when given. */
    @Synchronized
    fun failure(now: Long, retryAfterMs: Long? = null) {
        failures++
        val wait = retryAfterMs ?: (BASE_MS shl (failures - 1).coerceAtMost(6)).coerceAtMost(MAX_MS)
        blockedUntil = now + wait
    }

    /** A transient failure for one track (offline): allow a later retry of that track. */
    @Synchronized
    fun forget(key: String) { tried.remove(key) }

    @Synchronized
    fun reset() { tried.clear(); blockedUntil = 0L; failures = 0 }

    @Synchronized
    fun blockedUntil(): Long = blockedUntil

    companion object {
        const val BASE_MS = 30_000L
        const val MAX_MS = 30 * 60_000L
    }
}
