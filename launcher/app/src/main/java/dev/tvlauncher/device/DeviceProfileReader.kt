package dev.tvlauncher.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceProfileReader {
    fun read(context: Context): DeviceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // Get total RAM in kilobytes
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamKb = memInfo.totalMem / 1024

        // Get low RAM flag
        val lowRamFlag = activityManager.isLowRamDevice() || getSystemPropertyLowRam()

        // Get display metrics (width and height)
        val displayMetrics = context.resources.displayMetrics
        val widthPx = displayMetrics.widthPixels
        val heightPx = displayMetrics.heightPixels

        // Get ABI
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

        return DeviceProfile(
            totalRamKb = totalRamKb,
            lowRamFlag = lowRamFlag,
            widthPx = widthPx,
            heightPx = heightPx,
            abi = abi
        )
    }

    private fun getSystemPropertyLowRam(): Boolean {
        return try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java, String::class.java)
            val lowRamValue = getMethod.invoke(null, "ro.config.low_ram", "false") as String
            lowRamValue == "true"
        } catch (e: Exception) {
            false
        }
    }
}
