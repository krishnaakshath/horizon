package dev.tvlauncher.volume.core

/**
 * How the volume bar animates. FULL: fade + small slide in, animated fill, soft shadow. LITE (1 GB
 * TVs, or the launcher's Lite level): a short fade only, no shadow. OFF (animations off in the
 * launcher, or a system animator scale of 0): appears and disappears at once.
 */
enum class BarMotion {
    FULL, LITE, OFF;

    val showMs: Long get() = when (this) { FULL -> 160L; LITE -> 90L; OFF -> 0L }
    val hideMs: Long get() = when (this) { FULL -> 220L; LITE -> 120L; OFF -> 0L }
    val fillMs: Long get() = if (this == FULL) 120L else 0L
    val slide: Boolean get() = this == FULL
    val shadow: Boolean get() = this == FULL

    companion object {
        /** The launcher's LITE threshold (DeviceProfile.LITE_RAM_THRESHOLD_KB). */
        const val LITE_RAM_THRESHOLD_KB = 1_572_864L

        /**
         * [launcherLevel] is what the launcher last told the helper ("full", "lite", "off"), or
         * null; it can only make the motion calmer than the device allows, never richer.
         */
        fun resolve(lowRamFlag: Boolean, totalRamKb: Long, animatorScale: Float, launcherLevel: String?): BarMotion {
            if (animatorScale <= 0f || launcherLevel == "off") return OFF
            val device = if (lowRamFlag || (totalRamKb in 1..LITE_RAM_THRESHOLD_KB)) LITE else FULL
            return if (launcherLevel == "lite") LITE else device
        }
    }
}
