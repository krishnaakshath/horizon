package dev.tvlauncher.home

import dev.tvlauncher.ambient.AmbientSettingsRows
import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.appsettings.ModulePlaceholderSection
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SpecifiedPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveTwoSectionsTest {
    @Test fun registerAll_replacesDevicesPlaceholder_andAppendsAmbientAndHome() {
        val r = SettingsRegistry.newDefault()
        val before = r.sections().map { it.id }
        WaveTwoSections.registerAll(r)
        val after = r.sections()
        val ids = after.map { it.id }
        assertEquals(before.indexOf(ModulePlaceholderSection.DEVICES), ids.indexOf(ModulePlaceholderSection.DEVICES))
        assertTrue(after.first { it.id == ModulePlaceholderSection.DEVICES } !is ModulePlaceholderSection)
        assertTrue(WaveTwoSections.AMBIENT_ID in ids)
        assertTrue(WaveTwoSections.HOME_EXTRAS_ID in ids)
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

    @Test fun sections_declareTheirSidebarGroups() {
        assertEquals(SettingsGroup.HOME, AmbientSection.group)
        assertEquals(SettingsGroup.HOME, HomeExtrasSection.group)
        assertEquals(SettingsGroup.CONNECTIONS, DevicesSection.group)
    }

    @Test fun specs_stayWithinTheBudget_andCoverEveryModelRow() {
        for (s in listOf(AmbientSection, HomeExtrasSection)) {
            val spec = (s as SpecifiedPage).spec
            assertEquals(s.id, spec.id)
            assertTrue("${s.id}: ${spec.primaryRows.size}", spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
            val keys = spec.primaryRows + spec.advancedRows
            assertEquals(keys.size, keys.toSet().size)
        }
        val primary = WaveTwoSections.AMBIENT_PRIMARY_MODEL_KEYS.toSet()
        // Every guarded model row (CARD is a plain view) is declared on the side it is rendered on.
        for (notif in listOf(true, false)) for (slide in listOf(true, false)) {
            val ws = WallpaperSettings(notificationsEnabled = notif).let {
                if (slide) it.copy(source = dev.tvlauncher.ambient.WallpaperSource.Slideshow(java.io.File("/tmp"), 60)) else it
            }
            val guard = RowGuard(WaveTwoSections.AMBIENT_SPEC)
            guard.claim("ambient.start", advanced = false)
            for (r in WaveTwoSections.ambientRows(ws, accessGranted = false, is24h = true)) {
                if (r.kind == WaveTwoSections.PageKind.CARD) continue
                guard.claim("ambient.${r.key}", advanced = r.key !in primary)
            }
            guard.claim("ambient.homeWallpaper", advanced = false)
        }
    }
}
