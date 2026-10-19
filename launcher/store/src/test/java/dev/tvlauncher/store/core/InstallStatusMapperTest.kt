package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.InstallStatusMapper.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStatusMapperTest {

    @Test fun platformConstantValues() {
        // Values of android.content.pm.PackageInstaller.STATUS_* (stable public API).
        assertEquals(-1, InstallStatusMapper.STATUS_PENDING_USER_ACTION)
        assertEquals(0, InstallStatusMapper.STATUS_SUCCESS)
        assertEquals(1, InstallStatusMapper.STATUS_FAILURE)
        assertEquals(2, InstallStatusMapper.STATUS_FAILURE_BLOCKED)
        assertEquals(3, InstallStatusMapper.STATUS_FAILURE_ABORTED)
        assertEquals(4, InstallStatusMapper.STATUS_FAILURE_INVALID)
        assertEquals(5, InstallStatusMapper.STATUS_FAILURE_CONFLICT)
        assertEquals(6, InstallStatusMapper.STATUS_FAILURE_STORAGE)
        assertEquals(7, InstallStatusMapper.STATUS_FAILURE_INCOMPATIBLE)
        assertEquals(8, InstallStatusMapper.STATUS_FAILURE_TIMEOUT)
    }

    @Test fun pendingAndSuccess() {
        assertEquals(Kind.NEEDS_USER, InstallStatusMapper.map(-1, null).kind)
        val ok = InstallStatusMapper.map(0, null)
        assertEquals(Kind.SUCCESS, ok.kind)
        assertEquals("Installed", ok.title)
    }

    @Test fun abortedIsCancelledNotError() {
        val r = InstallStatusMapper.map(3, "INSTALL_FAILED_ABORTED: User rejected permissions")
        assertEquals(Kind.CANCELLED, r.kind)
        assertTrue(r.message.contains("cancel", ignoreCase = true))
    }

    @Test fun failuresHavePlainLanguage() {
        val cases = mapOf(
            1 to "couldn't",
            2 to "blocked",
            4 to "damaged",
            5 to "signed",
            6 to "space",
            7 to "isn't compatible",
            8 to "too long",
        )
        for ((code, word) in cases) {
            val r = InstallStatusMapper.map(code, null)
            assertEquals(Kind.FAILURE, r.kind)
            assertTrue("$code -> ${r.message}", r.message.contains(word, ignoreCase = true))
        }
    }

    @Test fun conflictAndDowngradeHintsFromSystemMessage() {
        assertTrue(InstallStatusMapper.map(5, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match").message
            .contains("uninstall", ignoreCase = true))
        assertTrue(InstallStatusMapper.map(4, "INSTALL_FAILED_VERSION_DOWNGRADE").message.contains("newer", ignoreCase = true))
        assertTrue(InstallStatusMapper.map(7, "INSTALL_FAILED_NO_MATCHING_ABIS").message.contains("processor", ignoreCase = true))
        assertTrue(InstallStatusMapper.map(7, "INSTALL_FAILED_OLDER_SDK").message.contains("Android version", ignoreCase = true))
    }

    @Test fun unknownCodeIsFailureAndKeepsDetail() {
        val r = InstallStatusMapper.map(99, "weird thing")
        assertEquals(Kind.FAILURE, r.kind)
        assertEquals("weird thing", r.detail)
        assertFalse(r.message.isBlank())
    }
}
