package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherFormatTest {
    @Test fun temp_celsiusRounds() {
        assertEquals("21°", WeatherFormat.temp(21.0, "C"))
        assertEquals("21°", WeatherFormat.temp(20.6, "C"))
        assertEquals("20°", WeatherFormat.temp(20.4, "C"))
        assertEquals("0°", WeatherFormat.temp(-0.4, "C"))
        assertEquals("0°", WeatherFormat.temp(-0.0, "C"))
        assertEquals("-1°", WeatherFormat.temp(-0.6, "C"))
        assertEquals("-12°", WeatherFormat.temp(-12.2, "C"))
    }

    @Test fun temp_fahrenheitConverts() {
        assertEquals("32°", WeatherFormat.temp(0.0, "F"))
        assertEquals("70°", WeatherFormat.temp(21.0, "F"))
        assertEquals("-40°", WeatherFormat.temp(-40.0, "f"))
        assertEquals("0°", WeatherFormat.temp(-17.9, "F"))
    }

    @Test fun temp_unknownUnit_isCelsius_andNaNIsDash() {
        assertEquals("21°", WeatherFormat.temp(21.0, "K"))
        assertEquals("21°", WeatherFormat.temp(21.0, null))
        assertEquals("–", WeatherFormat.temp(Double.NaN, "C"))
    }

    @Test fun highLow() {
        assertEquals("H 24°  L 13°", WeatherFormat.highLow(24.2, 12.8, "C"))
    }

    @Test fun iconFor_everyWmoGroup() {
        assertEquals(WxIcon.CLEAR_DAY, WeatherFormat.iconFor(0, true))
        assertEquals(WxIcon.CLEAR_NIGHT, WeatherFormat.iconFor(0, false))
        for (c in listOf(1, 2)) {
            assertEquals(WxIcon.PARTLY_DAY, WeatherFormat.iconFor(c, true))
            assertEquals(WxIcon.PARTLY_NIGHT, WeatherFormat.iconFor(c, false))
        }
        assertEquals(WxIcon.CLOUDY, WeatherFormat.iconFor(3, true))
        assertEquals(WxIcon.CLOUDY, WeatherFormat.iconFor(3, false))
        for (c in listOf(45, 48)) assertEquals(WxIcon.FOG, WeatherFormat.iconFor(c, true))
        for (c in listOf(51, 53, 55, 56, 57)) assertEquals(WxIcon.DRIZZLE, WeatherFormat.iconFor(c, false))
        for (c in listOf(61, 63, 65, 66, 67, 80, 81, 82)) assertEquals(WxIcon.RAIN, WeatherFormat.iconFor(c, true))
        for (c in listOf(71, 73, 75, 77, 85, 86)) assertEquals(WxIcon.SNOW, WeatherFormat.iconFor(c, true))
        for (c in listOf(95, 96, 99)) assertEquals(WxIcon.STORM, WeatherFormat.iconFor(c, false))
    }

    @Test fun iconFor_unknownCode_isCloudy() {
        assertEquals(WxIcon.CLOUDY, WeatherFormat.iconFor(-1, true))
        assertEquals(WxIcon.CLOUDY, WeatherFormat.iconFor(42, true))
        assertEquals(WxIcon.CLOUDY, WeatherFormat.iconFor(100, false))
    }

    @Test fun everyIconKey_hasAVectorDrawable() {
        val dir = listOf(java.io.File("src/main/res/drawable"), java.io.File("app/src/main/res/drawable")).first { it.isDirectory }
        for (icon in WxIcon.values()) {
            val f = java.io.File(dir, icon.key + ".xml")
            org.junit.Assert.assertTrue("missing ${f.path}", f.isFile)
            org.junit.Assert.assertTrue(f.readText().contains("<vector"))
        }
    }

    @Test fun everyWmoCode_mapsToAnIcon_dayAndNightDifferOnlyForClearAndPartly() {
        for (code in -1..100) {
            val d = WeatherFormat.iconFor(code, true); val n = WeatherFormat.iconFor(code, false)
            if (code in 0..2) org.junit.Assert.assertNotEquals(d, n) else assertEquals(d, n)
        }
    }

    @Test fun conditionFor_fallbackText() {
        assertEquals("Clear", WeatherFormat.conditionFor(0))
        assertEquals("Partly cloudy", WeatherFormat.conditionFor(2))
        assertEquals("Thunderstorm", WeatherFormat.conditionFor(95))
        assertEquals("", WeatherFormat.conditionFor(-5))
    }
}
