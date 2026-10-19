package dev.tvlauncher.ambient

import android.content.Context
import android.graphics.drawable.Drawable
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import java.io.File

/**
 * Wallpaper for the home backdrop. Returns immediately with a [WallpaperDrawable] painted in the
 * wallpaper's base color; the real bitmap is rendered/decoded off-thread and swapped in (the
 * drawable invalidates itself). A slideshow uses its first image as a static home wallpaper.
 *
 * Intended compositing (the controller owns BackdropView; this class does not touch it):
 *   1. this drawable, full-bleed, at the bottom;
 *   2. the existing brand-color gradient drawn on top at 55 % alpha (so the focused app's color
 *      still tints the screen);
 *   3. the existing bottom scrim for shelf/text legibility.
 * Call [WallpaperDrawable.release] when the backdrop is torn down or the wallpaper changes.
 */
object HomeWallpaperProvider {
    const val BRAND_TINT_ALPHA = 0.55f

    fun drawableFor(context: Context, settings: WallpaperSettings, profile: DeviceProfile, accent: Int = BuiltInWallpapers.DEFAULT_ACCENT): WallpaperDrawable {
        val lite = profile.effectsMode == EffectsMode.LITE
        val size = WallpaperPlan.targetSize(profile.widthPx, profile.heightPx, lite)
        return when (val s = settings.source) {
            is WallpaperSource.BuiltIn -> builtIn(s.id, size, accent)
            is WallpaperSource.UserImage -> file(context, s.file, size, accent)
            is WallpaperSource.Slideshow -> {
                val d = WallpaperDrawable(BuiltInWallpapers.meta(WallpaperSource.DEFAULT_ID).baseColor)
                WallpaperLoader.background({ UserWallpapers.list(s.folder).firstOrNull() }) { first ->
                    val job = if (first == null) WallpaperLoader.builtIn(WallpaperSource.DEFAULT_ID, size, accent) { d.setBitmap(it) }
                    else WallpaperLoader.file(first, size) { d.setBitmap(it) }
                    d.attachJob(job)
                }
                d
            }
        }
    }

    /** Convenience for callers that only need a Drawable reference. */
    fun asDrawable(context: Context, settings: WallpaperSettings, profile: DeviceProfile): Drawable =
        drawableFor(context, settings, profile)

    private fun builtIn(id: String, size: WallpaperPlan.Size, accent: Int): WallpaperDrawable {
        val d = WallpaperDrawable(BuiltInWallpapers.meta(id).baseColor)
        d.attachJob(WallpaperLoader.builtIn(id, size, accent) { d.setBitmap(it) })
        return d
    }

    private fun file(context: Context, f: File, size: WallpaperPlan.Size, accent: Int): WallpaperDrawable {
        val d = WallpaperDrawable(BuiltInWallpapers.meta(WallpaperSource.DEFAULT_ID).baseColor)
        d.attachJob(WallpaperLoader.file(f, size) { b ->
            if (b != null) d.setBitmap(b)
            else d.attachJob(WallpaperLoader.builtIn(WallpaperSource.DEFAULT_ID, size, accent) { d.setBitmap(it) })
        })
        return d
    }
}
