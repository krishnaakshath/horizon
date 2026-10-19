package dev.tvlauncher.brand

import android.content.Context
import android.graphics.Typeface

/**
 * The wordmark face (Unbounded Medium, SIL OFL 1.1, subset to Latin letters, digits and a little
 * punctuation; see fonts/FONTS.md). Used for the "H02" / "HORIZON" marks only; every other text stays
 * on the Inter UI faces. Loaded once; a missing or broken asset falls back to the system sans.
 */
object BrandFont {
    const val ASSET = "brand/brand_display.ttf"
    /** Characters the subset holds (BrandFontTest checks the marks only use these). */
    const val CHARSET = " !&'(),-./0123456789:ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz·’–—"

    @Volatile private var cached: Typeface? = null

    fun typeface(context: Context): Typeface {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val t = try {
                Typeface.createFromAsset(context.applicationContext.assets, ASSET)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (e: Throwable) {
                Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            cached = t
            return t
        }
    }

    /** True when every character of [text] is in the bundled subset. */
    fun covers(text: String): Boolean = text.all { CHARSET.indexOf(it) >= 0 }
}
