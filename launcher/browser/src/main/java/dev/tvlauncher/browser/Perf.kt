package dev.tvlauncher.browser

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings

/** FULL vs LITE effects (LITE on low-RAM TVs) and the system animator scale. */
object Perf {
    @Volatile var lite = false; private set
    @Volatile var animScale = 1f; private set

    fun init(context: Context) {
        val am = context.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        lite = am.isLowRamDevice || mi.totalMem < 1_500L * 1024 * 1024
        animScale = try {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: Exception) { 1f }
    }

    val animationsOff: Boolean get() = animScale == 0f
}
