package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.ComponentRisk.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewModelTest {

    private val a = "aa".repeat(32)
    private val base = ApkFacts(
        packageName = "org.example.app", versionName = "1.0", versionCode = 10, minSdk = 21, signers = setOf(a),
        permissions = emptyList(), tvReady = true, installedVersionCode = null, installedSigners = null,
        expectedPackage = null, expectedSigner = null, sourceHadHash = true, deviceSdk = 28,
    )

    private fun caps(vararg p: String) = ComponentRisk.collect(p.map { Kind.SERVICE to "android.permission.$it" })

    @Test fun hostileVersionName_sanitized() {
        val m = ReviewModel.build(base.copy(versionName = "1.0‮\n✓ Verified by Get Apps" + "x".repeat(200)))
        assertFalse(m.versionText.contains('‮'))
        assertFalse(m.versionText.contains('\n'))
        assertTrue(m.versionText.endsWith("… (10)"))
        assertTrue(m.versionText.length <= ReviewModel.VERSION_MAX + 5)
        assertEquals("? (10)", ReviewModel.build(base.copy(versionName = null)).versionText)
    }

    @Test fun fakeCheckmarkPermissionName_notRenderedRaw() {
        val m = ReviewModel.build(base.copy(permissions = listOf(
            "android.permission.INTERNET",
            "✓ checked: no sensitive capabilities",
            "OK - safe app",
        )))
        assertEquals(listOf("android.permission.INTERNET"), m.permissions.map { it.name })
        assertEquals(2, m.unrecognizedPermissions)
        assertEquals(3, m.totalPermissions)
        assertFalse(m.permissions.any { it.label.contains("✓") || it.name.contains("checked") })
    }

    @Test fun permissionNameWithBidi_rejected() {
        val m = ReviewModel.build(base.copy(permissions = listOf("android.permission.CAMERA‮", "com.x​.Y")))
        assertTrue(m.permissions.isEmpty())
        assertEquals(2, m.unrecognizedPermissions)
        assertFalse(ReviewModel.isRecognizedPermissionName(null))
        assertTrue(ReviewModel.isRecognizedPermissionName("com.example.permission.C2D_MESSAGE"))
    }

    @Test fun manyPermissions_capped() {
        val perms = (1..100).map { "com.example.permission.P$it" } + "android.permission.SYSTEM_ALERT_WINDOW"
        val m = ReviewModel.build(base.copy(permissions = perms))
        assertEquals(ReviewModel.MAX_PERMISSIONS, m.permissions.size)
        assertEquals(101 - 40, m.permissionsMore)
        assertEquals("android.permission.SYSTEM_ALERT_WINDOW", m.permissions.first().name) // riskiest first survives the cap
    }

    @Test fun capabilitiesLayout_leftShowsUpToFiveSensitiveThenMore() {
        val m = ReviewModel.build(base.copy(capabilities = caps(
            "BIND_ACCESSIBILITY_SERVICE", "BIND_DEVICE_ADMIN", "BIND_NOTIFICATION_LISTENER_SERVICE", "BIND_VPN_SERVICE",
            "BIND_INPUT_METHOD", "BIND_AUTOFILL_SERVICE", "BIND_WALLPAPER")))
        assertEquals(6, m.sensitiveCapabilities.size)
        assertEquals(5, m.leftCapabilities.size)
        assertEquals(1, m.leftMore)
        assertTrue(m.leftCapabilities.all { it.severity == ComponentRisk.Severity.HIGH })
        assertEquals(listOf("BIND_WALLPAPER"), m.infoCapabilities.map { it.key })
    }

    @Test fun capabilitiesLayout_infoOnlyNeverInLeftColumn() {
        val m = ReviewModel.build(base.copy(capabilities = caps("BIND_WALLPAPER", "BIND_DREAM_SERVICE")))
        assertTrue(m.leftCapabilities.isEmpty())
        assertEquals(0, m.leftMore)
        assertTrue(m.sensitiveCapabilities.isEmpty())
        assertEquals(2, m.infoCapabilities.size)
    }

    @Test fun permissionLabelsAreSanitisedAndBounded() {
        val long = "com.example.permission." + "A".repeat(300)
        val m = ReviewModel.build(base.copy(permissions = listOf(long)))
        assertEquals(0, m.permissions.size) // > 255 chars is not a credible permission name
        assertEquals(1, m.unrecognizedPermissions)
    }
}
