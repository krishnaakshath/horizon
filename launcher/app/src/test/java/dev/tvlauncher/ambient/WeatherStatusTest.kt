package dev.tvlauncher.ambient

import dev.tvlauncher.home.WaveTwoSections
import dev.tvlauncher.home.WeatherChipModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherStatusTest {
    private val now = 1_800_000_000_000L
    private val fresh = mapOf<String, Any?>("location" to "Pune", "temp_c" to 21.0, "code" to 0, "updated_ms" to now - 60_000L, "unit" to "C")

    private class Src(val installed: Boolean, val rows: List<Map<String, Any?>>?) : WeatherSource {
        override fun queryCurrent() = rows
        override fun queryDaily() = emptyList<Map<String, Any?>>()
        override fun isInstalled() = installed
    }

    private fun read(installed: Boolean, rows: List<Map<String, Any?>>?) = WeatherReader(Src(installed, rows)).readState(now)

    @Test fun missingCompanion_showsNothingAnywhere() {
        val r = read(false, null)
        assertEquals(WeatherStatus.MISSING, r.status)
        val d = WeatherDisplay.from(r, now)
        assertNull(d.condition); assertNull(d.temp); assertNull(d.freshness)
        assertFalse(WeatherChipModel.from(r, now, enabled = true).visible)
    }

    @Test fun installedWithoutCity_showsOneCalmHint() {
        val r = read(true, emptyList())
        assertEquals(WeatherStatus.NEEDS_LOCATION, r.status)
        val d = WeatherDisplay.from(r, now)
        assertEquals("Set a location in Weather", d.condition)
        assertNull(d.temp); assertNull(d.freshness); assertNull(d.icon)
        val chip = WeatherChipModel.from(r, now, enabled = true)
        assertTrue(chip.visible); assertTrue(chip.hint)
        assertEquals("Set a location in Weather", chip.text)
        assertFalse(WeatherChipModel.from(r, now, enabled = false).visible)
    }

    @Test fun cityChosenButFirstUpdatePending_isUnavailableNotHint() {
        val pending = mapOf<String, Any?>("location" to "Pune", "temp_c" to null, "updated_ms" to 0L, "unit" to "C")
        val r = read(true, listOf(pending))
        assertEquals(WeatherStatus.UNAVAILABLE, r.status)
        assertEquals("Weather unavailable", WeatherDisplay.from(r, now).condition)
        assertFalse(WeatherChipModel.from(r, now, enabled = true).visible)
    }

    @Test fun queryFailure_isUnavailable() {
        assertEquals(WeatherStatus.UNAVAILABLE, read(true, null).status)
    }

    @Test fun freshData_isOk_andStaleIsUnavailable() {
        val r = read(true, listOf(fresh))
        assertEquals(WeatherStatus.OK, r.status)
        assertEquals("21°", WeatherDisplay.from(r, now).temp)
        val stale = read(true, listOf(fresh + ("updated_ms" to now - 13 * 3_600_000L)))
        assertEquals(WeatherStatus.UNAVAILABLE, stale.status)
    }

    @Test fun settingsRow_offersGetWeatherThroughGetApps() {
        assertEquals("Open", WaveTwoSections.weatherRowValue(installed = true, getAppsInstalled = true))
        assertEquals("Get Weather", WaveTwoSections.weatherRowValue(installed = false, getAppsInstalled = true))
        assertEquals("Not installed", WaveTwoSections.weatherRowValue(installed = false, getAppsInstalled = false))
    }
}
