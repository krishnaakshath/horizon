package dev.tvlauncher.ambient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationAccessTextTest {
    @Test fun adbLine_matchesTheDocumentedCommand() {
        assertEquals(
            "adb shell cmd notification allow_listener dev.tvlauncher/dev.tvlauncher.ambient.AmbientNotificationService",
            NotificationAccessText.adbLine("dev.tvlauncher", "dev.tvlauncher.ambient.AmbientNotificationService")
        )
    }

    @Test fun adbLine_shortensClassInOwnPackageIsNotUsed_fullClassAlwaysWritten() {
        assertEquals("adb shell cmd notification allow_listener a.b/c.D", NotificationAccessText.adbLine("a.b", "c.D"))
    }

    @Test fun instructions_whenSettingsOpened_vsFallback() {
        val opened = NotificationAccessText.instructions(settingsOpened = true, adbLine = "X")
        assertTrue(opened.contains("TV Launcher"))
        assertTrue(!opened.contains("adb"))
        val fallback = NotificationAccessText.instructions(settingsOpened = false, adbLine = "adb shell X")
        assertTrue(fallback.contains("adb shell X"))
        assertTrue(fallback.contains("computer"))
    }
}
