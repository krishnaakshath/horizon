package dev.tvlauncher.link.spotify

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.tvlauncher.link.core.LinkContract
import dev.tvlauncher.link.net.SafeHttp
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Collections
import java.util.concurrent.Executors

/**
 * The user's own Spotify app key (Client ID + Client Secret), kept only in Link's private
 * SharedPreferences (allowBackup is off; never exported, never logged, never returned except as
 * a masked id hint).
 */
class SpotifyKeyStore(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("spotify_key", Context.MODE_PRIVATE)

    val clientId: String get() = p.getString("id", "") ?: ""
    val clientSecret: String get() = p.getString("secret", "") ?: ""
    val configured: Boolean get() = clientId.isNotEmpty() && clientSecret.isNotEmpty()

    var state: String
        get() = p.getString("state", LinkContract.SP_UNKNOWN) ?: LinkContract.SP_UNKNOWN
        set(v) { p.edit().putString("state", v).apply() }

    fun save(id: String, secret: String) {
        p.edit().putString("id", id).putString("secret", secret).putString("state", LinkContract.SP_UNKNOWN).commit()
    }

    fun wipe() { p.edit().clear().commit() }

    /** "1a2b…9z" for the settings row; empty when not configured. */
    fun idHint(): String = clientId.let { if (it.length < 8) "" else it.take(4) + "…" + it.takeLast(2) }
}

/**
 * Album covers and artist pictures for the launcher's "Artist banner" style, looked up with the
 * Client Credentials flow. One request chain per track at most ([TrackLimiter]), global backoff on
 * 429 and errors, https only, a 2 MB download cap, a decode-bomb check and a 1280x720 output cap.
 * Results are JPEG files in Link's cache dir (LRU, 20 files), read by the launcher through
 * [dev.tvlauncher.link.LinkProvider.openFile]. Everything runs on one background thread.
 */
object SpotifyArt {
    private val io = Executors.newSingleThreadExecutor()
    private val limiter = TrackLimiter()
    private val pending: MutableSet<String> = Collections.synchronizedSet(HashSet())
    @Volatile private var token: SpotifyCore.Token? = null

    fun dir(context: Context): File = File(context.cacheDir, "spotify_art").also { it.mkdirs() }

    fun file(context: Context, key: String): File? =
        if (SpotifyCore.validKey(key)) File(dir(context), "$key.jpg") else null

    /** Never blocks: READY (cached), PENDING (fetch queued or running) or NONE. */
    fun request(context: Context, key: String, title: String?, artist: String?, album: String?, kind: SpotifyCore.Kind): String {
        val app = context.applicationContext
        val f = file(app, key) ?: return LinkContract.ART_NONE
        if (f.isFile && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return LinkContract.ART_READY }
        val store = SpotifyKeyStore(app)
        if (!store.configured || store.state == LinkContract.SP_INVALID) return LinkContract.ART_NONE
        if (key in pending) return LinkContract.ART_PENDING
        val q = SpotifyCore.query(title, artist, album) ?: return LinkContract.ART_NONE
        if (!limiter.mayRequest(key, System.currentTimeMillis())) return LinkContract.ART_NONE
        pending += key
        io.execute {
            try { fetch(app, store, key, q, kind) } catch (e: Throwable) { } finally { pending -= key }
        }
        return LinkContract.ART_PENDING
    }

    /** Fetches a fresh token: OK, INVALID or OFFLINE (blocking; call off the main thread). */
    fun test(context: Context): String {
        val store = SpotifyKeyStore(context)
        if (!store.configured) return LinkContract.SP_NOT_CONFIGURED
        token = null
        val r = try { bearer(store) ?: LinkContract.SP_INVALID } catch (e: IOException) { LinkContract.SP_OFFLINE }
        val state = if (r == LinkContract.SP_INVALID || r == LinkContract.SP_OFFLINE) r else LinkContract.SP_OK
        store.state = state
        if (state == LinkContract.SP_OK) limiter.reset()
        return state
    }

    fun save(context: Context, id: String, secret: String): String {
        if (!SpotifyCore.validCredential(id) || !SpotifyCore.validCredential(secret)) return LinkContract.SP_INVALID
        SpotifyKeyStore(context).save(id, secret)
        token = null
        limiter.reset()
        return test(context)
    }

    fun remove(context: Context) {
        SpotifyKeyStore(context).wipe()
        token = null
        limiter.reset()
        try { dir(context).listFiles()?.forEach { it.delete() } } catch (e: Exception) { }
    }

    /** The bearer (cached until shortly before expiry), or null when the key is refused. */
    private fun bearer(store: SpotifyKeyStore): String? {
        val now = System.currentTimeMillis()
        token?.takeIf { it.valid(now) }?.let { return it.bearer }
        val r = SafeHttp.request("POST", SpotifyCore.TOKEN_URL,
            headers = mapOf(
                "Authorization" to SpotifyCore.basicAuth(store.clientId, store.clientSecret),
                "Content-Type" to "application/x-www-form-urlencoded",
            ),
            body = SpotifyCore.TOKEN_BODY.toByteArray(), maxBytes = 16 * 1024)
        if (r.status == 400 || r.status == 401) return null
        if (r.status != 200) throw IOException("token ${r.status}")
        val t = SpotifyCore.parseToken(r.text(), now) ?: return null
        token = t
        return t.bearer
    }

    private fun api(store: SpotifyKeyStore, url: String): String? {
        val b = bearer(store) ?: run { store.state = LinkContract.SP_INVALID; return null }
        val r = SafeHttp.request("GET", url, headers = mapOf("Authorization" to "Bearer $b"), maxBytes = 256 * 1024)
        when {
            r.status == 200 -> return r.text()
            r.status == 401 -> { token = null; throw IOException("401") }
            r.status == 429 -> { limiter.failure(System.currentTimeMillis(), SpotifyCore.retryAfterMs(r.retryAfter)); return null }
            r.status == 404 -> return null
            else -> throw IOException("api ${r.status}")
        }
    }

    private fun fetch(context: Context, store: SpotifyKeyStore, key: String, q: String, kind: SpotifyCore.Kind) {
        try {
            val hit = SpotifyCore.parseSearch(api(store, SpotifyCore.searchUrl(q)) ?: return) ?: return
            val url = when (kind) {
                SpotifyCore.Kind.ALBUM -> hit.albumImage
                SpotifyCore.Kind.ARTIST -> hit.artistId?.let(SpotifyCore::artistUrl)
                    ?.let { api(store, it) }?.let(SpotifyCore::parseArtistImage) ?: hit.albumImage
            } ?: return
            val bytes = download(url) ?: return
            val bmp = decode(bytes) ?: return
            write(context, key, bmp)
            limiter.success()
            if (store.state != LinkContract.SP_OK) store.state = LinkContract.SP_OK
        } catch (e: IOException) {
            // Offline or a server error: back off, and let this track try again later.
            limiter.forget(key)
            limiter.failure(System.currentTimeMillis())
            if (store.state == LinkContract.SP_UNKNOWN) store.state = LinkContract.SP_OFFLINE
        }
    }

    private fun download(url: String): ByteArray? {
        if (!url.startsWith("https://")) return null
        val r = SafeHttp.request("GET", url, maxBytes = SpotifyCore.MAX_IMAGE_BYTES)
        return if (r.status == 200 && r.body.isNotEmpty()) r.body else null
    }

    /** Header first: refuses huge claimed sizes, then decodes subsampled and scales to fit 1280x720. */
    fun decode(bytes: ByteArray): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        val sample = SpotifyCore.sampleSize(o.outWidth, o.outHeight) ?: return null
        val raw = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val (w, h) = SpotifyCore.fit(raw.width, raw.height)
        if (w == raw.width && h == raw.height) return raw
        val out = Bitmap.createScaledBitmap(raw, w, h, true)
        if (out !== raw) raw.recycle()
        return out
    }

    private fun write(context: Context, key: String, bmp: Bitmap) {
        val dir = dir(context)
        val target = file(context, key) ?: return
        val tmp = File(dir, "$key.tmp")
        try {
            FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            if (!tmp.renameTo(target)) tmp.delete()
        } finally {
            bmp.recycle()
            tmp.delete()
        }
        val files = dir.listFiles()?.filter { it.name.endsWith(".jpg") }.orEmpty()
        val drop = SpotifyCore.evict(files.map { it.name to it.lastModified() }).toSet()
        files.filter { it.name in drop }.forEach { it.delete() }
    }
}
