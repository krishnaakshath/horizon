package dev.tvlauncher.type

import android.content.Context
import android.graphics.Typeface
import java.util.concurrent.atomic.AtomicReferenceArray

/**
 * Loads the bundled Manrope instances from assets/fonts (Typeface.createFromAsset works on
 * API 24; res/font needs API 26 or androidx). Each weight is loaded once and cached; a missing
 * or corrupt asset degrades to the system family from [FontPlan].
 */
object Fonts {
    private val cache = AtomicReferenceArray<Typeface>(FontWeight.values().size)

    fun typeface(context: Context, weight: FontWeight): Typeface {
        val i = weight.ordinal
        cache.get(i)?.let { return it }
        synchronized(this) {
            cache.get(i)?.let { return it }
            val loaded = load(context.applicationContext, FontPlan.entry(weight))
            cache.set(i, loaded)
            return loaded
        }
    }

    private fun load(app: Context, e: FontPlan.Entry): Typeface =
        try {
            Typeface.createFromAsset(app.assets, e.assetName)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            Typeface.create(e.fallbackFamily, e.fallbackStyle)
        }

    /** Preloads every weight off the main thread so first use never blocks a frame. */
    fun warmUp(context: Context) {
        val app = context.applicationContext
        Thread({ FontWeight.values().forEach { typeface(app, it) } }, "font-warmup").apply {
            isDaemon = true
            start()
        }
    }
}
