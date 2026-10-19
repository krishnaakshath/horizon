package dev.tvlauncher.settings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Clock/date strings matching the top bar, driven by [AppearanceSettings.clock24h]. */
object AppearanceFormat {
    const val PATTERN_24H = "HH:mm"
    const val PATTERN_12H = "h:mm a"
    const val PATTERN_DATE = "EEE, d MMM"

    fun clockText(now: Long, is24h: Boolean, locale: Locale, tz: TimeZone = TimeZone.getDefault()): String =
        format(if (is24h) PATTERN_24H else PATTERN_12H, now, locale, tz)

    fun dateText(now: Long, locale: Locale, tz: TimeZone = TimeZone.getDefault()): String =
        format(PATTERN_DATE, now, locale, tz)

    private fun format(pattern: String, now: Long, locale: Locale, tz: TimeZone): String =
        SimpleDateFormat(pattern, locale).apply { timeZone = tz }.format(Date(now))
}
