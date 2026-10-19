package dev.tvlauncher.home

import dev.tvlauncher.ambient.AmbientSettingsRows
import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.appsettings.ModulePlaceholderSection
import dev.tvlauncher.appsettings.SettingsRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveTwoSectionsTest {
    @Test fun registerAll_replacesDevicesPlaceholder_andAppendsAmbient() {
        val r = SettingsRegistry.newDefault()
        val before = r.sections().map { it.id }
        WaveTwoSections.registerAll(r)
        val after = r.sections()
        val ids = after.map { it.id }
        assertEquals(before.indexOf(ModulePlaceholderSection.DEVICES), ids.indexOf(ModulePlaceholderSection.DEVICES))
        assertTrue(after.first { it.id == ModulePlaceholderSection.DEVICES } !is ModulePlaceholderSection)
        assertTrue(WaveTwoSections.AMBIENT_ID in ids)
        WaveTwoSections.registerAll(r) // idempotent
        assertEquals(ids, r.sections().map { it.id })
    }

    @Test fun ambientRows_clockFollowsAppearance_notAToggle() {
        val rows = WaveTwoSections.ambientRows(WallpaperSettings(), accessGranted = false, is24h = true)
        val clock = rows.first { it.key == "clock24h" }
        assertEquals(WaveTwoSections.PageKind.INFO, clock.kind)
        assertEquals(WaveTwoSections.PageKind.TOGGLE, rows.first { it.key == "nightDim" }.kind)
        assertEquals(WaveTwoSections.PageKind.STEPPER, rows.first { it.key == "idleMinutes" }.kind)
        assertEquals(WaveTwoSections.PageKind.ACTION, rows.first { it.key == "wallpaper" }.kind)
        assertEquals("Follows Appearance (24-hour)", clock.value)
        val twelve = WaveTwoSections.ambientRows(WallpaperSettings(), accessGranted = false, is24h = false)
        assertEquals("Follows Appearance (12-hour)", twelve.first { it.key == "clock24h" }.value)
    }

    @Test fun ambientRows_keepModelOrder_andVisibility() {
        val off = WaveTwoSections.ambientRows(WallpaperSettings(notificationsEnabled = false), accessGranted = false, is24h = true)
        assertFalse(off.any { it.key == "notificationAccess" })
        assertFalse(off.any { it.key == "showNotificationText" })
        val on = WaveTwoSections.ambientRows(WallpaperSettings(notificationsEnabled = true), accessGranted = false, is24h = true)
        assertTrue(on.any { it.key == "notificationAccess" })
        assertTrue(on.any { it.key == "showNotificationText" })
        val granted = WaveTwoSections.ambientRows(WallpaperSettings(notificationsEnabled = true), accessGranted = true, is24h = true)
        assertFalse(granted.any { it.key == "notificationAccess" })
        val keys = AmbientSettingsRows.rows(WallpaperSettings(notificationsEnabled = true), false).filter { it.visible }.map { it.key }
        assertEquals(keys, on.map { it.key })
    }

    @Test fun screensaverText_hasTheExactAdbLines() {
        val t = WaveTwoSections.SCREENSAVER_ADB
        assertTrue(t.contains("adb shell settings put secure screensaver_components dev.tvlauncher/dev.tvlauncher.ambient.AmbientDream"))
        assertTrue(t.contains("adb shell settings put secure screensaver_enabled 1"))
        assertTrue(t.contains("adb shell settings put secure screensaver_activate_on_sleep 1"))
    }
}
