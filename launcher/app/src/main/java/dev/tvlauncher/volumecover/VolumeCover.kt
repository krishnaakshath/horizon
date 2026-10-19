package dev.tvlauncher.volumecover

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import android.view.WindowManager
import dev.tvlauncher.brand.Brand
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.volume.LauncherVolume
import dev.tvlauncher.volume.VolumeCopy
import dev.tvlauncher.volume.core.CoverGeometry
import dev.tvlauncher.volume.core.VolumeWatcher

/**
 * The launcher's side of "Replace the TV's volume popup".
 *
 * The cover itself runs in Launcher Helper (volumecore VolumeWatcher in a
 * TYPE_ACCESSIBILITY_OVERLAY window): measured on the TCL 32S5403AF, the TV's popup is a system
 * window of layer 27 and every window type the launcher may add stays below it
 * (APPLICATION_OVERLAY is layer 12; SYSTEM_ERROR/OVERLAY/ALERT, PHONE, TOAST and the rest are
 * refused), so a launcher window would only peek out from under the TV's popup. Here: the
 * switch ([CoverPrefs], synced to the helper by Settings > Sound > Volume & output) and two
 * debug-build diagnostics ([CoverProbe], and a measuring watcher in an application overlay).
 */
object VolumeCover {
    const val TAG = "VolumeCover"

    @SuppressLint("StaticFieldLeak") // holds the Application context only
    private var measuring: VolumeWatcher? = null

    /** True where the cover can apply (the popup's place is known for this TV). */
    val supported: Boolean get() = CoverGeometry.measuredOn(Build.MANUFACTURER, Build.BRAND)

    /** Application.onCreate: debug builds only, and only when asked through run-as prefs. */
    fun install(app: Application) {
        if ((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        try {
            CoverProbe.maybeRun(app)
        } catch (e: Throwable) {
        }
        try {
            if (CoverPrefs(app).measure) startMeasuring(app)
        } catch (e: Throwable) {
        }
    }

    /**
     * Debug: the same watcher as the helper's, in an application overlay, logging when each
     * change reached us. It sits under the TV's popup; it exists to measure latency and to see
     * the column once the TV's popup has gone.
     */
    @Suppress("DEPRECATION")
    private fun startMeasuring(app: Application) {
        if (measuring != null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!android.provider.Settings.canDrawOverlays(app)) return
        val device = try { DeviceProfileReader.read(app).effectsMode } catch (e: Throwable) { null }
        val w = VolumeWatcher(
            app, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, Theme.medium,
            enabled = { true },
            motion = { LauncherVolume.motion(app, device) },
            onShown = { s, t0 ->
                Log.d(TAG, "show ${s.level}/${s.max} muted=${s.muted} at=$t0 call=${android.os.SystemClock.uptimeMillis() - t0}ms")
            },
        )
        if (w.start()) measuring = w
        Log.d(TAG, "measuring=${measuring != null}")
    }
}

/** The "Replace the TV's volume popup" switch (default on), plus the debug measuring flag. */
class CoverPrefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = sp.getBoolean(KEY_ENABLED, true)
        set(v) { sp.edit().putBoolean(KEY_ENABLED, v).apply() }

    val measure: Boolean get() = sp.getBoolean(KEY_MEASURE, false)

    companion object {
        const val NAME = "volume_cover"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_MEASURE = "measure"
    }
}

/** Words for the "Replace the TV's volume popup" row (pure, JVM-tested). */
object CoverCopy {
    const val TITLE = "Replace the TV's volume popup"

    fun subtitle(on: Boolean, h: VolumeCopy.HelperState): String = when {
        !on -> "Off: the TV shows its own volume popup"
        !h.installed -> "Needs ${Brand.HELPER}: only it may draw above the TV's popup"
        !h.resolved -> "Our volume bar covers the TV's popup"
        h.bound -> "Our volume bar covers the TV's popup in every app"
        h.serviceOn -> "Paused: the TV has stopped ${Brand.HELPER}; it returns when the helper restarts"
        else -> "Turn on ${Brand.HELPER} to cover the TV's popup"
    }
}
