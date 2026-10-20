package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTextTest {
    @Test fun instructions_whenSettingsOpened_vsFallback_neverShowACommand() {
        val opened = NotificationAccessText.instructions(settingsOpened = true)
        assertTrue(opened.contains("M02"))
        val fallback = NotificationAccessText.instructions(settingsOpened = false)
        assertTrue(fallback.contains("setup helper"))
        for (t in listOf(opened, fallback)) {
            assertFalse(t.lowercase().contains("adb"))
            assertFalse(t.lowercase().contains("computer"))
            assertFalse(t.contains("allow_listener"))
        }
        assertEquals(NotificationAccessText.SETUP_LINE, fallback)
    }
}
