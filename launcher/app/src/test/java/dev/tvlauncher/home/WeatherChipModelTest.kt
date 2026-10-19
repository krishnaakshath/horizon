package dev.tvlauncher.home

import dev.tvlauncher.ambient.WeatherCurrent
import dev.tvlauncher.ambient.WeatherSnapshot
import dev.tvlauncher.ambient.WxIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherChipModelTest {
    private val now = 1_700_000_000_000L
    private val hour = 3_600_000L

    private fun snap(
        tempC: Double = 24.2, unit: String = "C", code: Int = 0, isDay: Boolean = true,
        updated: Long = now - 10 * 60_000L, high: Double? = 27.0, low: Double? = 15.0, condition: String = "Clear"
    ) = WeatherSnapshot(
        WeatherCurrent("Lisbon", tempC, tempC, code, condition, isDay, high, low, 50, 10.0, updated, unit),
        emptyList()
    )

    @Test fun fresh_showsIconAndRoundedTemp() {
        val c = WeatherChipModel.from(snap(), now, enabled = true)
        assertTrue(c.visible)
        assertEquals("24°", c.text)
        assertEquals(WxIcon.CLEAR_DAY, c.icon)
        assertEquals("24°, Clear", c.description)
    }

    @Test fun missingProvider_hidden() {
        val c = WeatherChipModel.from(null, now, enabled = true)
        assertFalse(c.visible)
        assertNull(c.icon)
        assertEquals("", c.text)
    }

    @Test fun stale_over3h_hidden_exactly3h_shown() {
        assertFalse(WeatherChipModel.from(snap(updated = now - 3 * hour - 1), now, true).visible)
        assertTrue(WeatherChipModel.from(snap(updated = now - 3 * hour), now, true).visible)
        assertFalse(WeatherChipModel.from(snap(updated = 0L), now, true).visible)
    }

    @Test fun disabledInSettings_hidden() {
        assertFalse(WeatherChipModel.from(snap(), now, enabled = false).visible)
    }

    @Test fun nullHighLow_tolerated() {
        val c = WeatherChipModel.from(snap(high = null, low = null), now, true)
        assertTrue(c.visible)
        assertEquals("24°", c.text)
    }

    @Test fun unit_fromProvider_valuesAreCelsius() {
        assertEquals("75°", WeatherChipModel.from(snap(tempC = 24.0, unit = "F"), now, true).text)
        assertEquals("24°", WeatherChipModel.from(snap(tempC = 24.0, unit = "c"), now, true).text)
    }

    @Test fun negativeZero_neverShown_andNightIcon() {
        val c = WeatherChipModel.from(snap(tempC = -0.4, isDay = false), now, true)
        assertEquals("0°", c.text)
        assertEquals(WxIcon.CLEAR_NIGHT, c.icon)
    }

    @Test fun weatherSettingsRow_installedOrNot() {
        assertEquals("Open", WaveTwoSections.weatherRowValue(installed = true))
        assertEquals("Not installed", WaveTwoSections.weatherRowValue(installed = false))
    }
}
