package dev.tvlauncher.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AppearanceFormatTest {
    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(h: Int, m: Int): Long = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.OCTOBER, 19, h, m, 0)
    }.timeInMillis

    @Test
    fun clock24h_zeroPadsMidnight() {
        assertEquals("00:05", AppearanceFormat.clockText(at(0, 5), true, Locale.US, utc))
        assertEquals("13:30", AppearanceFormat.clockText(at(13, 30), true, Locale.US, utc))
    }

    @Test
    fun clock12h_midnightIsTwelveAm() {
        assertEquals("12:05 AM", AppearanceFormat.clockText(at(0, 5), false, Locale.US, utc))
        assertEquals("1:30 PM", AppearanceFormat.clockText(at(13, 30), false, Locale.US, utc))
        assertEquals("9:07 AM", AppearanceFormat.clockText(at(9, 7), false, Locale.US, utc))
    }

    @Test
    fun dateText_shortWeekdayDayMonth() {
        assertEquals("Mon, 19 Oct", AppearanceFormat.dateText(at(10, 0), Locale.US, utc))
    }

    @Test
    fun is24h_autoFollowsSystem_otherwiseUserChoice() {
        assertEquals(true, AppearanceFormat.is24h(ClockFormat.AUTO, system24h = true))
        assertEquals(false, AppearanceFormat.is24h(ClockFormat.AUTO, system24h = false))
        assertEquals(true, AppearanceFormat.is24h(ClockFormat.H24, system24h = false))
        assertEquals(false, AppearanceFormat.is24h(ClockFormat.H12, system24h = true))
    }
}
