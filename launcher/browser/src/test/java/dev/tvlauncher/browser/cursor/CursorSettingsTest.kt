package dev.tvlauncher.browser.cursor

import org.junit.Assert.assertEquals
import org.junit.Test

class CursorSettingsTest {
    @Test fun roundTrip() {
        val s = CursorSettings(
            style = CursorStyle.RING, sizeDp = 36, color = 0xFF22C55E.toInt(), opacity = 0.7f, speed = 1.5f,
            magnet = Magnet.HIGH, trail = true, autoHideMs = 5000L
        )
        assertEquals(s, CursorSettings.decode(s.encode()))
    }

    @Test fun corruptJson_givesDefaults() {
        listOf(null, "", "{", "[]", "null", "\u0000").forEach { assertEquals(CursorSettings(), CursorSettings.decode(it)) }
    }

    @Test fun unknownStyle_isArrow() {
        assertEquals(CursorStyle.ARROW, CursorSettings.decode("""{"style":"SPARKLE"}""").style)
    }

    @Test fun sizeAndSpeedAndOpacity_clamped() {
        val s = CursorSettings.decode("""{"size":500,"speed":9,"opacity":-1,"magnet":"NOPE"}""")
        assertEquals(48, s.sizeDp)
        assertEquals(2f, s.speed, 0.0001f)
        assertEquals(CursorSettings.MIN_OPACITY, s.opacity, 0.0001f)
        assertEquals(Magnet.MED, s.magnet)
        val t = CursorSettings.decode("""{"size":3,"speed":0.01}""")
        assertEquals(20, t.sizeDp)
        assertEquals(0.5f, t.speed, 0.0001f)
    }

    @Test fun size_snapsToSteps() {
        assertEquals(28, CursorSettings.decode("""{"size":30}""").sizeDp)
        assertEquals(listOf(20, 28, 36, 48), CursorSettings.SIZES)
    }

    @Test fun defaults() {
        val d = CursorSettings()
        assertEquals(CursorStyle.ARROW, d.style)
        assertEquals(28, d.sizeDp)
        assertEquals(-1, d.color)
        assertEquals(Magnet.MED, d.magnet)
        assertEquals(1f, d.speed, 0f)
    }
}
