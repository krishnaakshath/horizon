package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRiskTest {

    @Test fun dangerousRuntimePermissions() {
        for (p in listOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.CALL_PHONE",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.BODY_SENSORS",
        )) assertEquals(p, PermissionRisk.Level.DANGEROUS, PermissionRisk.classify(p))
    }

    @Test fun specialHighImpactPermissions() {
        for (p in listOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES",
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.PACKAGE_USAGE_STATS",
            "android.permission.WRITE_SETTINGS",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.READ_LOGS",
        )) assertEquals(p, PermissionRisk.Level.SPECIAL, PermissionRisk.classify(p))
    }

    @Test fun normalPermissions() {
        for (p in listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "com.example.app.permission.C2D_MESSAGE",
        )) assertEquals(p, PermissionRisk.Level.NORMAL, PermissionRisk.classify(p))
    }

    @Test fun junkInputIsNormalAndDoesNotThrow() {
        assertEquals(PermissionRisk.Level.NORMAL, PermissionRisk.classify(""))
        assertEquals(PermissionRisk.Level.NORMAL, PermissionRisk.classify("CAMERA"))
        assertEquals(PermissionRisk.Level.NORMAL, PermissionRisk.classify("android.permission.camera"))
    }

    @Test fun sortPutsRiskiestFirstAndDeduplicates() {
        val sorted = PermissionRisk.sort(listOf(
            "android.permission.INTERNET",
            "android.permission.CAMERA",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.CAMERA",
        ))
        assertEquals(listOf(
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
        ), sorted)
    }

    @Test fun friendlyLabels() {
        assertEquals("Microphone", PermissionRisk.label("android.permission.RECORD_AUDIO"))
        assertEquals("Draw over other apps", PermissionRisk.label("android.permission.SYSTEM_ALERT_WINDOW"))
        assertEquals("FOO_BAR", PermissionRisk.label("com.x.permission.FOO_BAR"))
        assertTrue(PermissionRisk.label("").isNotEmpty())
    }
}
