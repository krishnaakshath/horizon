package dev.tvlauncher.volume

import android.app.Activity
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.EffectiveMode
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.volume.core.BarMotion
import dev.tvlauncher.volume.core.VolumeEngine

/**
 * Our volume bar while a launcher screen is in front, with no help from Launcher Helper: the
 * activity passes its key events to [dispatch] first. If a volume key arrives here (Launcher
 * Helper did not take it already) we apply it ourselves and draw the bar in a small window of
 * the activity; whenever we cannot (switch off, fixed volume, the change does not stick)
 * [dispatch] returns false and the key goes on to the system as before.
 */
class LauncherVolume(private val activity: Activity) {
    private val prefs = LauncherVolumePrefs(activity)
    private val device: EffectsMode? by lazy {
        try { DeviceProfileReader.read(activity).effectsMode } catch (e: Throwable) { null }
    }
    private var engine: VolumeEngine? = null

    fun dispatch(event: KeyEvent): Boolean {
        if (!VolumeEngine.isVolumeKey(event.keyCode)) return false
        return try {
            val e = engine ?: VolumeEngine(
                context = activity,
                windowType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                face = Theme.medium,
                enabled = { prefs.enabled },
                motion = { motion(activity, device) },
            ).also { engine = it }
            e.onKey(event)
        } catch (e: Throwable) {
            false
        }
    }

    /** Shows the bar for a change made in our own UI (Settings > Volume & output). */
    fun showCurrent() {
        try { engine?.showCurrent() } catch (e: Throwable) { }
    }

    /** onStop / onDestroy: the bar's window belongs to the activity. */
    fun release() {
        engine?.release()
    }

    companion object {
        fun motion(context: Context, device: EffectsMode?): BarMotion {
            val scale = try {
                Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            } catch (e: Throwable) {
                1f
            }
            val level = try { AppearancePrefs(context).load().animationLevel } catch (e: Throwable) { null }
            val mode = if (level == null) device else EffectiveMode.resolve(level, device ?: EffectsMode.LITE, scale)
            return VolumeCopy.barMotion(mode, scale)
        }
    }
}

/** The launcher's copy of the "Volume bar" switch (the helper keeps its own; Settings sets both). */
class LauncherVolumePrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    private companion object {
        const val NAME = "volume_bar"
        const val KEY_ENABLED = "enabled"
    }
}
