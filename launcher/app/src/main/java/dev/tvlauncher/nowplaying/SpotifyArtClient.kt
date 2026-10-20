package dev.tvlauncher.nowplaying

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dev.tvlauncher.link.core.LinkContract
import java.util.concurrent.Executors

/**
 * The launcher's side of the optional Spotify artwork: asks M02 Link (which holds the user's key
 * and the network) for a picture of the playing song through the signature-protected provider,
 * then reads the cached file as a read-only descriptor. Never blocks the main thread; pictures
 * stay in memory only ([SpotifyArtPolicy.MEMORY_BYTES]). Without Link, without a key or offline,
 * [request] simply delivers null and the session's own art is used.
 */
object SpotifyArtClient {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = ByteLru<Bitmap>(SpotifyArtPolicy.MEMORY_BYTES, { it.allocationByteCount })
    @Volatile private var configured: Boolean? = null
    @Volatile private var generation = 0

    /** Re-reads whether a key is set (Home/Ambient start, after Settings). Background thread. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        try { io.execute { configured = status(app).first } } catch (e: RuntimeException) { }
    }

    fun knownConfigured(): Boolean = configured == true

    /** (configured, state, id hint) from Link's /spotify row; (false, null, "") without Link. Blocking. */
    fun status(context: Context): Triple<Boolean, String?, String> = try {
        context.contentResolver.query(Uri.parse(LinkContract.URI_SPOTIFY), null, null, null, null)?.use { c ->
            if (!c.moveToFirst()) Triple(false, null, "") else {
                fun s(col: String) = c.getColumnIndex(col).let { if (it < 0) null else c.getString(it) }
                val on = c.getColumnIndex(LinkContract.COL_SP_CONFIGURED).let { it >= 0 && c.getInt(it) != 0 }
                Triple(on, s(LinkContract.COL_SP_STATE), s(LinkContract.COL_SP_ID_HINT).orEmpty())
            }
        } ?: Triple(false, null, "")
    } catch (e: Exception) {
        Triple(false, null, "")
    }.also { configured = it.first }

    fun call(context: Context, method: String, arg: String?, extras: Bundle? = null): Bundle? = try {
        context.contentResolver.call(Uri.parse(LinkContract.URI_BASE), method, arg, extras)
    } catch (e: Exception) {
        null
    }

    /** Cancels outstanding deliveries (Home/Ambient stopped). */
    fun cancel() { generation++ }

    fun cached(item: NowPlaying, style: NowPlayingStyle): Bitmap? = cache.get(SpotifyArtPolicy.key(item, style))

    /**
     * The Spotify picture for [item] in [style], delivered on the main thread (null = none; keep the
     * session's art). Only asks Link when [SpotifyArtPolicy.wanted].
     */
    fun request(context: Context, item: NowPlaying, style: NowPlayingStyle, onResult: (Bitmap?) -> Unit) {
        if (!SpotifyArtPolicy.wanted(style, knownConfigured(), item)) { onResult(null); return }
        val key = SpotifyArtPolicy.key(item, style)
        cache.get(key)?.let { onResult(it); return }
        val app = context.applicationContext
        val g = generation
        val extras = Bundle().apply {
            putString(LinkContract.EXTRA_TITLE, item.title)
            putString(LinkContract.EXTRA_ARTIST, item.artist)
            putString(LinkContract.EXTRA_ALBUM, item.album)
            putString(LinkContract.EXTRA_ART_KIND, SpotifyArtPolicy.kind(style))
        }
        fun attempt(tries: Int) {
            try {
                io.execute {
                    if (g != generation) return@execute
                    val state = call(app, LinkContract.METHOD_SPOTIFY_ART, key, extras)?.getString(LinkContract.EXTRA_STATE)
                    when (state) {
                        LinkContract.ART_READY -> {
                            val bmp = load(app, key)
                            if (bmp != null) cache.put(key, bmp)
                            main.post { if (g == generation) onResult(bmp) }
                        }
                        LinkContract.ART_PENDING -> if (tries < SpotifyArtPolicy.POLL_TRIES) {
                            main.postDelayed({ if (g == generation) attempt(tries + 1) }, SpotifyArtPolicy.POLL_MS)
                        } else main.post { if (g == generation) onResult(null) }
                        else -> main.post { if (g == generation) onResult(null) }
                    }
                }
            } catch (e: RuntimeException) {
                onResult(null)
            }
        }
        attempt(0)
    }

    /** Reads Link's cached JPEG (already capped at 1280x720 there; checked again here). */
    private fun load(context: Context, key: String): Bitmap? {
        val uri = Uri.parse(SpotifyArtPolicy.uri(key) ?: return null)
        return try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, o)
            } ?: return null
            if (o.outWidth <= 0 || o.outHeight <= 0 || o.outWidth > 4096 || o.outHeight > 4096) return null
            var sample = 1
            while (o.outWidth / sample > 1280 || o.outHeight / sample > 720 * 2) sample *= 2
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                BitmapFactory.decodeFileDescriptor(pfd.fileDescriptor, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }
}
