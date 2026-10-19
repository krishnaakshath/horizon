package dev.tvlauncher.helper.volume

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.content.Context
import android.graphics.Typeface
import android.provider.Settings
import android.view.KeyEvent
import android.view.WindowManager
import dev.tvlauncher.volume.core.BarMotion
import dev.tvlauncher.volume.core.VolumeEngine
import dev.tvlauncher.volume.core.VolumeWatcher

/**
 * The helper's volume bar: our bar over every app, drawn in a TYPE_ACCESSIBILITY_OVERLAY window
 * of [service] (no extra permission). The same window type is the only one above a TCL TV's own
 * volume popup, so the cover ([VolumeWatcher]) lives here too. Lives as long as the service is bound; when the service is
 * not running nothing here runs and the TV handles the keys itself.
 */
class HelperVolume(service: AccessibilityService) {
    private val prefs = VolumePrefs(service)
    private val resolver = service.contentResolver
    private val deviceLite: Pair<Boolean, Long> = try {
        val am = service.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice to mi.totalMem / 1024
    } catch (e: Throwable) {
        true to 0L
    }

    /** "Replace the TV's volume popup": our column over the TV's own popup (TCL builds only). */
    private val watcher = VolumeWatcher(
        context = service,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        face = font(service),
        enabled = { prefs.cover },
        motion = { motion() },
    ).also { it.start() }

    private val engine = VolumeEngine(
        context = service,
        windowType = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        face = font(service),
        enabled = { prefs.enabled },
        motion = { motion() },
        onSeen = { key, r -> prefs.recordSeen(key, r) },
        onApplied = { watcher.noteOwnChange() },
    )

    private fun motion(): BarMotion {
        val scale = try {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Throwable) {
            1f
        }
        return BarMotion.resolve(deviceLite.first, deviceLite.second, scale, prefs.launcherMotion)
    }

    /** True when the key was applied by us (consume it); false lets the system handle it. */
    fun onKey(event: KeyEvent): Boolean = try {
        VolumeEngine.isVolumeKey(event.keyCode) && engine.onKey(event)
    } catch (e: Throwable) {
        false
    }

    fun release() {
        watcher.stop()
        engine.release()
    }

    private companion object {
        fun font(c: Context): Typeface? = try {
            Typeface.createFromAsset(c.assets, "fonts/ui_medium.ttf")
        } catch (e: Throwable) {
            null
        }
    }
}
