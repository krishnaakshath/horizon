package dev.tvlauncher.store.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallReviewTest {

    private val a = "aa".repeat(32)
    private val b = "bb".repeat(32)

    private val base = ApkFacts(
        packageName = "org.example.app",
        versionName = "1.0",
        versionCode = 10,
        minSdk = 21,
        signers = setOf(a),
        permissions = listOf("android.permission.INTERNET"),
        tvReady = true,
        installedVersionCode = null,
        installedSigners = null,
        expectedPackage = null,
        expectedSigner = null,
        sourceHadHash = true,
        deviceSdk = 28,
    )

    @Test fun cleanNewInstallHasNoBlockersOrWarnings() {
        val r = InstallReview.evaluate(base)
        assertTrue(r.canInstall)
        assertFalse(r.isUpdate)
        assertTrue(r.warnings.isEmpty())
    }

    @Test fun differentSignerOnUpdateIsBlockedWithExactWording() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = setOf(b)))
        assertFalse(r.canInstall)
        assertTrue(r.isUpdate)
        assertEquals("Signature differs: this will not install over the existing app", r.blockers.first())
    }

    @Test fun sameSignerUpdateAllowed() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = setOf(a)))
        assertTrue(r.canInstall)
        assertTrue(r.isUpdate)
    }

    @Test fun downgradeIsBlocked() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 11, installedSigners = setOf(a)))
        assertFalse(r.canInstall)
        assertTrue(r.blockers.any { it.contains("newer version", ignoreCase = true) })
    }

    @Test fun sameVersionIsReinstallWarning() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 10, installedSigners = setOf(a)))
        assertTrue(r.canInstall)
        assertTrue(r.warnings.any { it.contains("reinstall", ignoreCase = true) })
    }

    @Test fun notTvReadyWarns() {
        val r = InstallReview.evaluate(base.copy(tvReady = false))
        assertTrue(r.canInstall)
        assertTrue(r.warnings.any { it.startsWith("Not designed for TV") && it.contains("mouse/touch") })
    }

    @Test fun invalidOrUnsignedApkIsBlocked() {
        assertFalse(InstallReview.evaluate(base.copy(packageName = null)).canInstall)
        assertFalse(InstallReview.evaluate(base.copy(signers = emptySet())).canInstall)
    }

    @Test fun packageMismatchWithSourceIsBlocked() {
        val r = InstallReview.evaluate(base.copy(expectedPackage = "org.videolan.vlc"))
        assertFalse(r.canInstall)
        assertTrue(r.blockers.any { it.contains("org.videolan.vlc") })
    }

    @Test fun signerMismatchWithSourceIsBlocked() {
        assertFalse(InstallReview.evaluate(base.copy(expectedSigner = b)).canInstall)
        assertTrue(InstallReview.evaluate(base.copy(expectedSigner = a)).canInstall)
    }

    @Test fun tooNewForDeviceIsBlocked() {
        assertFalse(InstallReview.evaluate(base.copy(minSdk = 30)).canInstall)
    }

    @Test fun missingSourceHashAndDangerousPermissionsWarn() {
        val r = InstallReview.evaluate(base.copy(sourceHadHash = false,
            permissions = listOf("android.permission.RECORD_AUDIO", "android.permission.SYSTEM_ALERT_WINDOW")))
        assertTrue(r.canInstall)
        assertTrue(r.warnings.any { it.contains("checksum", ignoreCase = true) })
        assertTrue(r.warnings.any { it.contains("Contains 2 sensitive capabilities") })
    }

    @Test fun unreadableInstalledSignerBlocks() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = emptySet()))
        assertFalse(r.canInstall)
        assertTrue(r.blockers.contains(InstallReview.INSTALLED_SIGNER_UNREADABLE))
        val n = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = null))
        assertFalse(n.canInstall)
    }

    @Test fun installedStateInstalled() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = setOf(a)))
        assertEquals(InstalledState.INSTALLED, r.installedState)
        assertTrue(r.isUpdate)
    }

    @Test fun installedStateNotInstalled() {
        val r = InstallReview.evaluate(base)
        assertEquals(InstalledState.NOT_INSTALLED, r.installedState)
        assertFalse(r.warnings.contains(InstallReview.INSTALL_STATE_UNKNOWN))
    }

    @Test fun installedStateUnknownIsPhrasedHonestly() {
        val r = InstallReview.evaluate(base.copy(installedState = InstalledState.UNKNOWN))
        assertEquals(InstalledState.UNKNOWN, r.installedState)
        assertFalse(r.isUpdate)
        assertTrue(r.canInstall)
        assertTrue(r.warnings.contains(
            "Couldn't check whether this app is already installed — Android will still block a different signer."))
    }

    @Test fun rotatedSignerUpdateAllowedViaLineage() {
        val r = InstallReview.evaluate(base.copy(installedVersionCode = 5, installedSigners = setOf(b),
            signers = setOf(a), signerHistory = setOf(b, a)))
        assertTrue(r.canInstall)
    }

    @Test fun capabilitiesCountTowardsSummary() {
        val caps = ComponentRisk.collect(listOf(ComponentRisk.Kind.SERVICE to "android.permission.BIND_ACCESSIBILITY_SERVICE"))
        val r = InstallReview.evaluate(base.copy(capabilities = caps))
        assertEquals(1, r.sensitiveCount)
        assertTrue(r.warnings.contains("Contains 1 sensitive capability (highlighted)."))
    }
}
