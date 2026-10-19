package dev.tvlauncher.home

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.ui.Motion

/**
 * The home screen's view of Settings > Appearance: the [EffectiveProfile] the views are built with,
 * the accent choice, and what to do when the user comes back from Settings ([reload]).
 * Pure decisions live in [HomeAppearance].
 */
class HomeLook(context: Context, private val device: DeviceProfile) {
    private val prefs = AppearancePrefs(context.applicationContext)
    private val resolver = context.contentResolver

    var settings: AppearanceSettings = prefs.load()
        private set
    var profile: EffectiveProfile = HomeAppearance.profile(device, settings, systemScale())
        private set

    init {
        Motion.enabled = profile.animate
        applyGlass()
    }

    /** Settings > Appearance > Glass style for this TV, applied to every glass surface. */
    private fun applyGlass() {
        dev.tvlauncher.ui.GlassBackdrop.setStyle(
            dev.tvlauncher.settings.GlassStyle.resolve(settings.glassStyle, device.effectsMode == dev.tvlauncher.device.EffectsMode.FULL))
    }

    val backdropMs: Long get() = HomeAppearance.backdropMs(profile.motion)
    val showSplash: Boolean get() = HomeAppearance.showSplash(profile.motion)

    inline fun accent(brand: () -> Int): Int = HomeAppearance.accent(settings, brand)

    /** Re-reads the saved appearance (onStart). LIVE changes are applied by the caller; REBUILD needs [rebuild]. */
    fun reload(): HomeAppearance.Change {
        val s = prefs.load()
        val p = HomeAppearance.profile(device, s, systemScale())
        val change = HomeAppearance.change(settings, profile.motion, s, p.motion)
        settings = s
        applyGlass()
        if (change != HomeAppearance.Change.REBUILD) profile = p
        return change
    }

    private fun systemScale(): Float = try {
        Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: RuntimeException) {
        1f
    }

    companion object {
        private const val EXTRA_REBUILT = "dev.tvlauncher.extra.REBUILT"

        /** Recreates the home screen so tile sizes and animation durations are rebuilt; no splash. */
        fun rebuild(activity: Activity) {
            activity.intent = Intent(activity.intent).putExtra(EXTRA_REBUILT, true)
            activity.recreate()
        }

        fun isRebuild(intent: Intent?): Boolean = intent?.getBooleanExtra(EXTRA_REBUILT, false) == true
    }
}
