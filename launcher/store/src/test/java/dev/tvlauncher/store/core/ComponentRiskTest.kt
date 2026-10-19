package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.ComponentRisk.Kind
import dev.tvlauncher.store.core.ComponentRisk.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hostile APK shapes: capabilities granted by declaring protected components, not by uses-permission. */
class ComponentRiskTest {

    private fun only(kind: Kind, perm: String?) = ComponentRisk.collect(listOf(kind to perm))

    @Test fun apkWithAccessibilityService_showsHighRisk() {
        val r = only(Kind.SERVICE, "android.permission.BIND_ACCESSIBILITY_SERVICE")
        assertEquals(1, r.size)
        assertEquals(Severity.HIGH, r[0].severity)
        assertEquals("Can read the screen and act for you (accessibility service)", r[0].text)
    }

    @Test fun apkWithDeviceAdminReceiver_showsHighRisk() {
        val r = only(Kind.RECEIVER, "android.permission.BIND_DEVICE_ADMIN")
        assertEquals(Severity.HIGH, r.single().severity)
        assertEquals("Can lock/wipe the device (device administrator)", r.single().text)
    }

    @Test fun apkWithNotificationListener_showsHighRisk() {
        val r = only(Kind.SERVICE, "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE")
        assertEquals(Severity.HIGH, r.single().severity)
        assertEquals("Can read your notifications", r.single().text)
    }

    @Test fun apkWithVpnService_showsHighRisk() {
        val r = only(Kind.SERVICE, "android.permission.BIND_VPN_SERVICE")
        assertEquals(Severity.HIGH, r.single().severity)
        assertEquals("Can route all network traffic (VPN)", r.single().text)
    }

    @Test fun apkWithInputMethod_showsHighRisk() {
        val r = only(Kind.SERVICE, "android.permission.BIND_INPUT_METHOD")
        assertEquals(Severity.HIGH, r.single().severity)
        assertEquals("Can read everything you type (keyboard)", r.single().text)
    }

    @Test fun mediumAndInfoCapabilities() {
        assertEquals(Severity.MEDIUM, only(Kind.SERVICE, "android.permission.BIND_AUTOFILL_SERVICE").single().severity)
        assertEquals(Severity.MEDIUM, only(Kind.SERVICE, "android.permission.BIND_VOICE_INTERACTION").single().severity)
        assertEquals(Severity.INFO, only(Kind.SERVICE, "android.permission.BIND_WALLPAPER").single().severity)
        assertEquals(Severity.INFO, only(Kind.SERVICE, "android.permission.BIND_QUICK_SETTINGS_TILE").single().severity)
    }

    @Test fun noRiskyComponents_showsNone() {
        val r = ComponentRisk.collect(listOf(
            Kind.SERVICE to null,
            Kind.SERVICE to "android.permission.BIND_JOB_SERVICE",
            Kind.RECEIVER to "android.permission.RECEIVE_BOOT_COMPLETED",
        ))
        assertTrue(r.isEmpty())
    }

    @Test fun duplicateComponents_dedupedBySeverity() {
        val r = ComponentRisk.collect(listOf(
            Kind.SERVICE to "android.permission.BIND_WALLPAPER",
            Kind.SERVICE to "android.permission.BIND_ACCESSIBILITY_SERVICE",
            Kind.SERVICE to "android.permission.BIND_ACCESSIBILITY_SERVICE",
            Kind.SERVICE to "android.permission.BIND_AUTOFILL_SERVICE",
            Kind.RECEIVER to "android.permission.BIND_DEVICE_ADMIN",
        ))
        assertEquals(4, r.size)
        assertEquals(listOf(Severity.HIGH, Severity.HIGH, Severity.MEDIUM, Severity.INFO), r.map { it.severity })
        assertEquals(1, r.count { it.key == "BIND_ACCESSIBILITY_SERVICE" })
    }

    @Test fun nullPermission_ignored() {
        assertNull(ComponentRisk.classify(Kind.SERVICE, null))
        assertNull(ComponentRisk.classify(Kind.RECEIVER, ""))
    }

    @Test fun unknownCustomPermission_ignored() {
        assertNull(ComponentRisk.classify(Kind.SERVICE, "com.evil.permission.BIND_ACCESSIBILITY_SERVICE"))
        assertNull(ComponentRisk.classify(Kind.SERVICE, "android.permission.BIND_SOMETHING_NEW"))
        assertNull(ComponentRisk.classify(Kind.SERVICE, "BIND_ACCESSIBILITY_SERVICE"))
    }

    @Test fun usesPermissionSpecialSetIsComplete() {
        for (p in listOf("REQUEST_INSTALL_PACKAGES", "MANAGE_EXTERNAL_STORAGE", "SYSTEM_ALERT_WINDOW", "WRITE_SETTINGS",
            "PACKAGE_USAGE_STATS", "QUERY_ALL_PACKAGES", "READ_LOGS", "INSTALL_PACKAGES", "DELETE_PACKAGES", "BIND_DEVICE_ADMIN")) {
            assertEquals(p, PermissionRisk.Level.SPECIAL, PermissionRisk.classify("android.permission.$p"))
        }
        assertEquals(PermissionRisk.Level.NORMAL, PermissionRisk.classify("android.permission.USE_BIOMETRIC"))
    }
}
