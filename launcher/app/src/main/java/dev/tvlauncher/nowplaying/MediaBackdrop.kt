package dev.tvlauncher.nowplaying

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import dev.tvlauncher.room.RoomTheme
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.BoxBlur
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.GlassBackdrop
import dev.tvlauncher.ui.Theme

/**
 * "Backdrop follows what's playing": feeds the home [BackdropView] a picture made from the
 * playing item's art and decides when it shows ([MediaBackdropPolicy]).
 *
 * The picture is made once per art (and per room palette), never per frame, on the glass worker:
 * the art is scaled to cover a 1/8-screen bitmap (FULL, then box-blurred) or a 1/4-screen bitmap
 * (LITE, no blur but a strong dark scrim and an accent wash from the art's dominant colour), then
 * darkened so launcher text keeps 4.5:1 over every pixel ([MediaArtMath.clampContrast]). Memory:
 * 130 KB at 1080p FULL, 520 KB at 1080p LITE; at most two during a crossfade. The backdrop owns
 * and recycles them. No art = the brand gradient (nothing is rendered).
 *
 * Main thread only. Host: [update] from the tracker callbacks, [onKey] for every key,
 * [onFocus] for every focus change, [setEnabled] from onStart, [stop] from onStop.
 */
class MediaBackdrop(
    private val backdrop: BackdropView,
    private val full: Boolean,
    fadeMs: Long,
    /** The colour under the hero changed (art shown or hidden, new picture): re-pick text colours. */
    private val onHeroColorChanged: () -> Unit = {}
) {
    private var shownLayer: MediaBackdropPolicy.Layer? = null
    private val main = Handler(Looper.getMainLooper())
    private var enabled = false
    private var item: NowPlaying? = null
    private var source: Bitmap? = null
    private var renderedSource: Bitmap? = null
    private var renderedTarget = -1.0
    private var hasPicture = false
    private var token = 0
    private var focusInShelf = false
    private var lastShelfMoveAt = Long.MIN_VALUE
    private var lastKeyAt = Long.MIN_VALUE
    private var listening = false
    private val recheck = Runnable { evaluate() }
    private val dropPicture = Runnable {
        hasPicture = false
        renderedSource = null
        token++
        backdrop.setMedia(null, null, animate = true)
        evaluate()
    }
    // A field: RoomTheme holds listeners weakly.
    private val roomListener = RoomTheme.Listener { _, _, settled -> if (settled) rerenderForRoom() }

    init {
        backdrop.mediaFadeMs = fadeMs
    }

    /** Settings and access, re-read in onStart. Off = brand colours only, picture dropped. */
    fun setEnabled(on: Boolean) {
        enabled = on
        if (on && !listening) { RoomTheme.addListener(roomListener); listening = true }
        if (!on) clear()
        evaluate()
    }

    /** The tracker's item and art (art null = none yet / none). */
    fun update(n: NowPlaying?, art: Bitmap?) {
        item = n
        if (!enabled) return
        if (n == null) {
            source = null
            main.removeCallbacks(dropPicture)
            if (hasPicture) dropPicture.run() else evaluate()
            return
        }
        if (art == null) {
            source = null
            // A new track's art usually arrives a moment later: keep the old picture briefly.
            if (hasPicture) { main.removeCallbacks(dropPicture); main.postDelayed(dropPicture, ART_GRACE_MS) }
            evaluate()
            return
        }
        main.removeCallbacks(dropPicture)
        source = art
        if (art !== renderedSource) render(art)
        evaluate()
    }

    /** Times use elapsedRealtime, the clock of [NowPlaying.updatedAtMs]. */
    fun onKey(now: Long = SystemClock.elapsedRealtime()) { lastKeyAt = now }

    fun onFocus(inShelf: Boolean, now: Long = SystemClock.elapsedRealtime()) {
        if (MediaBackdropPolicy.isShelfMove(inShelf, lastKeyAt, now)) lastShelfMoveAt = now
        if (!inShelf) lastShelfMoveAt = Long.MIN_VALUE
        focusInShelf = inShelf
        evaluate()
    }

    fun stop() {
        clear()
        if (listening) { RoomTheme.removeListener(roomListener); listening = false }
        lastShelfMoveAt = Long.MIN_VALUE
        lastKeyAt = Long.MIN_VALUE
    }

    private fun clear() {
        token++
        main.removeCallbacks(recheck)
        main.removeCallbacks(dropPicture)
        item = null
        source = null
        renderedSource = null
        renderedTarget = -1.0
        hasPicture = false
        backdrop.setMedia(null, null, animate = false)
    }

    private fun evaluate() {
        main.removeCallbacks(recheck)
        val d = MediaBackdropPolicy.decide(MediaBackdropPolicy.Input(
            enabled, item, hasPicture, SystemClock.elapsedRealtime(), focusInShelf, lastShelfMoveAt
        ))
        backdrop.showMedia(d.layer == MediaBackdropPolicy.Layer.MEDIA)
        if (d.layer != shownLayer) { shownLayer = d.layer; onHeroColorChanged() }
        d.recheckInMs?.let { main.postDelayed(recheck, it + 20) }
    }

    private fun target(): Double = MediaArtMath.targetLuminance(RoomTheme.palette, Theme.TEXT_2)

    private fun rerenderForRoom() {
        val s = source ?: return
        if (!enabled || Math.abs(target() - renderedTarget) < 1e-6) return
        render(s)
    }

    private fun render(src: Bitmap) {
        val dm = backdrop.resources.displayMetrics
        val w = if (backdrop.width > 0) backdrop.width else dm.widthPixels
        val h = if (backdrop.height > 0) backdrop.height else dm.heightPixels
        val (sw, sh) = MediaArtMath.size(w, h, full)
        val target = target()
        val t = ++token
        renderedSource = src
        renderedTarget = target
        try {
            GlassBackdrop.executor.execute {
                val out = try { process(src, sw, sh, target, full) } catch (e: Throwable) { null }
                main.post {
                    if (t != token || !enabled) { out?.first?.recycle(); return@post }
                    if (out == null) {
                        if (hasPicture) dropPicture.run()
                        return@post
                    }
                    backdrop.setMedia(out.first, out.second, animate = true)
                    hasPicture = true
                    evaluate()
                    if (shownLayer == MediaBackdropPolicy.Layer.MEDIA) onHeroColorChanged()
                }
            }
        } catch (e: RuntimeException) {
            renderedSource = null
        }
    }

    companion object {
        /** How long the previous track's picture stays while the next one's art loads. */
        const val ART_GRACE_MS = 1_500L

        /** Worker: cover-scale, blur (FULL) or wash (LITE), darken, clamp contrast. Null on any failure. */
        fun process(src: Bitmap, sw: Int, sh: Int, target: Double, full: Boolean): Pair<Bitmap, IntArray>? {
            if (src.isRecycled || src.width <= 0 || src.height <= 0) return null
            val readable = if (Build.VERSION.SDK_INT >= 26 && src.config == Bitmap.Config.HARDWARE) {
                src.copy(Bitmap.Config.ARGB_8888, false) ?: return null
            } else src
            val out = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
            val crop = MediaArtMath.coverCrop(readable.width, readable.height, sw, sh)
            Canvas(out).drawBitmap(readable, Rect(crop[0], crop[1], crop[2], crop[3]), Rect(0, 0, sw, sh),
                Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
            if (readable !== src) readable.recycle() // our own copy, never drawn
            val px = IntArray(sw * sh)
            out.getPixels(px, 0, sw, 0, 0, sw, sh)
            if (full) BoxBlur.blur(px, sw, sh, MediaArtMath.BLUR_RADIUS)
            val dominant = BrandColors.dominantColor(px)
            if (!full) MediaArtMath.accentWash(px, sw, sh, BrandColors.backdropAccent(dominant))
            MediaArtMath.scrim(px, if (full) MediaArtMath.SCRIM_FULL else MediaArtMath.SCRIM_LITE)
            MediaArtMath.clampContrast(px, target)
            out.setPixels(px, 0, sw, 0, 0, sw, sh)
            return out to MediaArtMath.stops(px, sw, sh)
        }
    }
}
