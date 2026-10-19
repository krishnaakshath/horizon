package dev.tvlauncher.appsettings

import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.EffectiveMode
import dev.tvlauncher.ui.BrandColors
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.TileSpec

/** Accent and motion for the Settings screen. [motion] null means "no animations". */
data class SettingsStyle(val accent: Int, val motion: EffectsMode?) {
    val animate: Boolean get() = motion != null
    val full: Boolean get() = motion == EffectsMode.FULL

    /** Row/sidebar focus transition. */
    val focusMs: Long get() = when (motion) {
        null -> 0L
        EffectsMode.LITE -> 120L
        EffectsMode.FULL -> TileSpec.FOCUS_MS
    }

    /** Screen entry animation; with [entryStaggerMs] the total stays at or under 250 ms. */
    val entryMs: Long get() = when (motion) {
        null -> 0L
        EffectsMode.LITE -> 180L
        EffectsMode.FULL -> 210L
    }

    /** Delay of the content pane behind the sidebar during entry (FULL only). */
    val entryStaggerMs: Long get() = if (motion == EffectsMode.FULL) 40L else 0L

    /** Page swap inside the content pane. */
    val pageMs: Long get() = when (motion) {
        null -> 0L
        EffectsMode.LITE -> 110L
        EffectsMode.FULL -> 160L
    }

    /** Accent blended into the glass surface; [t] 0 = surface, 1 = accent. */
    fun tint(t: Float): Int = BrandColors.blend(Theme.SURFACE, accent, t)

    /** The one focus style (rows, sidebar entries, headers): accent-lit glass and a 2 dp accent ring. */
    fun focusDrawable(context: android.content.Context): android.graphics.drawable.Drawable =
        Glass.rect(context, focusFill, Glass.RADIUS_ROW, accentOnDark, 2)

    /** Opaque fill of a focused row/entry: [FOCUS_TINT] of the accent over the surface. */
    val focusFill: Int get() = tint(FOCUS_TINT)

    /** Accent lifted for text/stroke legibility on near-black. */
    val accentOnDark: Int get() = BrandColors.backdropAccent(accent)

    companion object {
        const val FOCUS_TINT = 0.26f

        /** Settings has no focused app, so "auto from brand" uses the launcher's default accent. */
        fun accentOf(a: AppearanceSettings): Int =
            if (a.accentMode == AccentMode.FIXED) a.fixedAccent else AppearanceSettings.DEFAULT_ACCENT

        fun from(a: AppearanceSettings, device: EffectsMode, systemAnimatorScale: Float) =
            SettingsStyle(accentOf(a), EffectiveMode.resolve(a.animationLevel, device, systemAnimatorScale))
    }
}
