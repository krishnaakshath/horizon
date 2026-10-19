package dev.tvlauncher.power

import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.power.PowerOnStatus.Readiness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOnStatusTest {
    @Test fun overlayWinsThenHelperThenAndroid12() {
        assertEquals(Readiness.OVERLAY, PowerOnStatus.readiness(helperReady = true, overlayAllowed = true, sdk = 30))
        assertEquals(Readiness.HELPER, PowerOnStatus.readiness(helperReady = true, overlayAllowed = false, sdk = 30))
        assertEquals(Readiness.OVERLAY, PowerOnStatus.readiness(helperReady = false, overlayAllowed = true, sdk = 30))
        assertEquals(Readiness.ANDROID, PowerOnStatus.readiness(helperReady = false, overlayAllowed = false, sdk = 31))
        assertEquals(Readiness.NEEDS_PERMISSION, PowerOnStatus.readiness(helperReady = false, overlayAllowed = false, sdk = 30))
    }

    @Test fun wordingIsPlainAndHasNoAdb() {
        for (r in Readiness.values()) {
            val text = PowerOnStatus.value(r) + " " + PowerOnStatus.subtitle(r)
            assertFalse(text, text.contains("adb", ignoreCase = true))
            assertFalse(text, text.contains("settings put", ignoreCase = true))
        }
        assertTrue(PowerOnStatus.ready(Readiness.HELPER))
        assertFalse(PowerOnStatus.ready(Readiness.NEEDS_PERMISSION))
    }

    @Test fun sectionLivesUnderSystemAndKeepsItsBudget() {
        val r = SettingsRegistry.newDefault()
        PowerOnSection.register(r)
        val s = r.sections().single { it.id == PowerOnSection.ID }
        assertEquals(SettingsGroup.SYSTEM, s.group)
        assertTrue(PowerOnSection.spec.withinBudget)
        assertTrue(PowerOnSection.spec.primaryRows.all { it.startsWith("power_on.") })
    }
}
