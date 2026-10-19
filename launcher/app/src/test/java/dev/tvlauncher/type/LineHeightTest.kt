package dev.tvlauncher.type

import org.junit.Assert.assertEquals
import org.junit.Test

class LineHeightTest {
    @Test fun derivation() {
        assertEquals(1.0f, LineHeight.multiplierFor(1.366f, 1.366f), 1e-6f)
        assertEquals(0.5f, LineHeight.multiplierFor(2f, 1f), 1e-6f)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsZero() { LineHeight.multiplierFor(0f, 1f) }

    @Test fun effectiveHeightsHitTargets() {
        val targets = mapOf(
            TextStyles.Display to 1.125f, TextStyles.Title to 1.20f, TextStyles.Subtitle to 1.30f,
            TextStyles.Body to 1.40f, TextStyles.Caption to 1.35f, TextStyles.Label to 1.20f,
            TextStyles.ClockLarge to 1.0f, TextStyles.ClockSmall to 1.0f,
        )
        targets.forEach { (s, t) ->
            val eff = s.lineHeightMult * LineHeight.MANROPE_NATURAL_EM
            assertEquals(s.name, t, eff, 0.02f)
        }
    }

    @Test fun fontPaddingOffWhereRequested() {
        listOf(TextStyles.ClockLarge, TextStyles.ClockSmall, TextStyles.Display, TextStyles.Label).forEach {
            assertEquals(it.name, false, it.includeFontPadding)
        }
    }
}
