package dev.tvlauncher.home

import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.EffectiveMode
import dev.tvlauncher.settings.TileSize

/**
 * The [DeviceProfile] as the home screen should use it: [motion] is the user's animation level
 * resolved against the device (null = no animations). [effectsMode] keeps the old
 * `profile.effectsMode == FULL` call sites working; memory budgets stay on [device].
 */
data class EffectiveProfile(val device: DeviceProfile, val motion: EffectsMode?, val tileScale: Float = 1f) {
    val animate: Boolean get() = motion != null
    val full: Boolean get() = motion == EffectsMode.FULL
    val effectsMode: EffectsMode get() = motion ?: EffectsMode.LITE
    val appOpenMs: Long get() = device.appOpenMs
}

/** Pure mapping from [AppearanceSettings] to what the home screen draws. */
object HomeAppearance {
    enum class Change { NONE, LIVE, REBUILD }

    fun tileScale(size: TileSize): Float = when (size) {
        TileSize.SMALL -> 0.85f
        TileSize.MEDIUM -> 1.0f
        TileSize.LARGE -> 1.2f
    }

    fun profile(device: DeviceProfile, a: AppearanceSettings, systemAnimatorScale: Float): EffectiveProfile =
        EffectiveProfile(
            device,
            EffectiveMode.resolve(a.animationLevel, device.effectsMode, systemAnimatorScale),
            tileScale(a.rowTileSize)
        )

    /** Backdrop/hero accent: the fixed color, or the app's brand color ([brand] is not called when fixed). */
    inline fun accent(a: AppearanceSettings, brand: () -> Int): Int =
        if (a.accentMode == AccentMode.FIXED) a.fixedAccent else brand()

    fun backdropMs(motion: EffectsMode?): Long = when (motion) {
        null -> 0L
        EffectsMode.FULL -> DeviceProfile.FULL_APP_OPEN_MS
        EffectsMode.LITE -> DeviceProfile.LITE_APP_OPEN_MS
    }

    fun showSplash(motion: EffectsMode?): Boolean = motion != null

    /**
     * What returning from Settings needs: a rebuild when the tile size or the resolved motion
     * changed (sizes and durations are fixed when views are built), a live update for the rest.
     */
    fun change(old: AppearanceSettings, oldMotion: EffectsMode?, new: AppearanceSettings, newMotion: EffectsMode?): Change = when {
        old.rowTileSize != new.rowTileSize || oldMotion != newMotion -> Change.REBUILD
        old != new -> Change.LIVE
        else -> Change.NONE
    }
}
