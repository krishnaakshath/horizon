package dev.tvlauncher.volumecover

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import dev.tvlauncher.volume.core.CoverGeometry

/**
 * Debug builds only: tries every window type an app could ask for at the cover rectangle and logs
 * which ones the window manager accepts (`dumpsys window windows` then shows their layer next to
 * the TV's popup). Started by `run-as dev.tvlauncher` writing `probe=true` into
 * shared_prefs/volume_cover.xml before the process starts; the flag is cleared at once.
 */
internal object CoverProbe {
    private val TYPES = linkedMapOf(
        "APPLICATION_OVERLAY" to 2038,
        "SYSTEM_ERROR" to 2010,
        "SYSTEM_OVERLAY" to 2006,
        "SYSTEM_ALERT" to 2003,
        "PRIORITY_PHONE" to 2007,
        "PHONE" to 2002,
        "TOAST" to 2005,
        "VOLUME_OVERLAY" to 2020,
        "SECURE_SYSTEM_OVERLAY" to 2015,
        "ACCESSIBILITY_OVERLAY" to 2032,
        "NAVIGATION_BAR_PANEL" to 2024,
        "SCREENSHOT" to 2036,
    )

    fun maybeRun(context: Context) {
        val sp = context.getSharedPreferences("volume_cover", Context.MODE_PRIVATE)
        if (!sp.getBoolean("probe", false)) return
        sp.edit().remove("probe").apply()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val size = android.graphics.Point().also {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(it)
        }
        val rect = CoverGeometry.cover(size.x, size.y) ?: return
        val added = ArrayList<View>()
        for ((name, type) in TYPES) {
            val v = View(context).apply { setBackgroundColor(0xFFE0457B.toInt()) }
            val lp = WindowManager.LayoutParams(
                rect.width, rect.height, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = rect.left
                y = rect.top
                title = "CoverProbe-$name"
            }
            val result = try {
                wm.addView(v, lp)
                added.add(v)
                "added"
            } catch (e: Throwable) {
                "refused: ${e.javaClass.simpleName}: ${e.message}"
            }
            Log.i(VolumeCover.TAG, "probe $name ($type): $result")
        }
        Handler(Looper.getMainLooper()).postDelayed({
            for (v in added) try { wm.removeViewImmediate(v) } catch (e: Throwable) { }
            Log.i(VolumeCover.TAG, "probe windows removed")
        }, 20_000L)
    }
}
