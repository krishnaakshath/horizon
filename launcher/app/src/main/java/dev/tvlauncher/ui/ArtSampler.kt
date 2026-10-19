package dev.tvlauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable

/** Renders tile art into one reused 32x32 bitmap so [BrandColors.dominantColor] can read it. */
class ArtSampler {
    private val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)
    private val pixels = IntArray(SIZE * SIZE)
    private val saved = Rect()

    /** Main thread only. Returns null when the art has nothing to sample (letter tiles). */
    fun pixels(art: TileArt<Drawable>): IntArray? {
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        when (art) {
            is TileArt.Banner -> draw(art.d)
            is TileArt.Legacy -> draw(art.d)
            is TileArt.Adaptive -> { draw(art.bg); draw(art.fg) }
            is TileArt.Letter -> return null
        }
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)
        return pixels
    }

    private fun draw(d: Drawable) {
        d.copyBounds(saved)
        try {
            d.setBounds(0, 0, SIZE, SIZE)
            d.draw(canvas)
        } catch (e: RuntimeException) {
            // A broken third-party drawable must not take the home screen down.
        } finally {
            d.bounds = saved
        }
    }

    private companion object {
        const val SIZE = 32
    }
}
