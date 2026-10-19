package dev.tvlauncher.weather

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {
    @Test fun celsius() {
        assertEquals("21°", Units.format(21.0, "C"))
        assertEquals("21°", Units.format(20.5, "C"))
        assertEquals("-21°", Units.format(-20.5, "C"))
        assertEquals("21°", Units.format(21.4, "C"))
    }

    @Test fun noNegativeZero() {
        assertEquals("0°", Units.format(-0.4, "C"))
        assertEquals("0°", Units.format(-0.0, "C"))
        assertEquals("0°", Units.format(0.0, "C"))
    }

    @Test fun fahrenheit() {
        assertEquals("32°", Units.format(0.0, "F"))
        assertEquals("212°", Units.format(100.0, "F"))
        assertEquals("-40°", Units.format(-40.0, "F"))
        assertEquals("70°", Units.format(21.0, "F"))
    }

    @Test fun badInput() {
        assertEquals("--", Units.format(Double.NaN, "C"))
        assertEquals("--", Units.format(Double.POSITIVE_INFINITY, "F"))
    }

    @Test fun unknownUnitIsCelsius() {
        assertEquals("21°", Units.format(21.0, "X"))
        assertEquals("70°", Units.format(21.0, "f"))
    }
}
