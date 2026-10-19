package dev.tvlauncher.home

import dev.tvlauncher.ambient.WeatherFormat
import dev.tvlauncher.ambient.WeatherFreshness
import dev.tvlauncher.ambient.WeatherRead
import dev.tvlauncher.ambient.WeatherSnapshot
import dev.tvlauncher.ambient.WeatherStatus
import dev.tvlauncher.ambient.WxIcon

/** What the top bar's weather chip shows; [visible] false hides it entirely. */
data class WeatherChip(
    val visible: Boolean,
    val text: String,
    val icon: WxIcon?,
    val description: String,
    /** A one-line setup hint rather than data (drawn in the secondary text color). */
    val hint: Boolean = false
)

/**
 * Pure decision for the compact weather chip next to the clock: hidden when the user turned it off
 * (Settings > Home screen > "Weather in top bar"), the companion is missing (null snapshot) or the
 * data is older than 12 h or has no usable temperature; otherwise the condition icon and the rounded temperature in the
 * provider's unit (values are Celsius; high/low are not used, so null ones are fine).
 */
object WeatherChipModel {
    val HIDDEN = WeatherChip(false, "", null, "")
    val SET_LOCATION = WeatherChip(true, WeatherFreshness.SET_LOCATION, null, WeatherFreshness.SET_LOCATION, hint = true)

    /** Like [from], plus the first-run hint when the companion is installed but has no city yet. */
    fun from(read: WeatherRead, nowMs: Long, enabled: Boolean): WeatherChip = when {
        !enabled -> HIDDEN
        read.status == WeatherStatus.NEEDS_LOCATION -> SET_LOCATION
        read.status == WeatherStatus.MISSING -> HIDDEN
        else -> from(read.snapshot, nowMs, enabled)
    }

    fun from(snap: WeatherSnapshot?, nowMs: Long, enabled: Boolean): WeatherChip {
        val c = snap?.current ?: return HIDDEN
        if (!enabled || !WeatherFreshness.isUsable(c.updatedMs, nowMs)) return HIDDEN
        val text = WeatherFormat.temp(c.tempC, c.unit)
        if (!text.endsWith("°")) return HIDDEN // no usable temperature: no chip
        val description = if (c.condition.isBlank()) text else "$text, ${c.condition}"
        return WeatherChip(true, text, WeatherFormat.iconFor(c.code, c.isDay), description)
    }
}
