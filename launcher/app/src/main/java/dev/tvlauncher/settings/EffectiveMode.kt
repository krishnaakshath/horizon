package dev.tvlauncher.settings

import dev.tvlauncher.device.EffectsMode

/** Combines the user's animation level with the device's own effects mode. */
object EffectiveMode {
    /**
     * The effects mode to animate with, or null for "no animations" ([AnimationLevel.OFF], or the
     * system animator duration scale set to 0 in Developer options).
     */
    fun resolve(level: AnimationLevel, device: EffectsMode, systemAnimatorScale: Float = 1f): EffectsMode? {
        if (systemAnimatorScale == 0f) return null
        return when (level) {
            AnimationLevel.AUTO -> device
            AnimationLevel.FULL -> EffectsMode.FULL
            AnimationLevel.LITE -> EffectsMode.LITE
            AnimationLevel.OFF -> null
        }
    }
}
