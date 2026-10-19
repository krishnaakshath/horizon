package dev.tvlauncher.store.core

import dev.tvlauncher.store.core.InstallFallbackPolicy.Candidate
import dev.tvlauncher.store.core.InstallFallbackPolicy.Stage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class InstallPolicyTest {

    @Test fun receiverAcceptsOnlyTheCurrentFreshToken() {
        assertTrue(ReceiverTokenPolicy.accepts("t1", "t1", 1000, 2000))
        assertFalse(ReceiverTokenPolicy.accepts("t2", "t1", 1000, 2000))
        assertFalse(ReceiverTokenPolicy.accepts(null, "t1", 1000, 2000))
        assertFalse(ReceiverTokenPolicy.accepts("t1", null, 1000, 2000))
        assertFalse(ReceiverTokenPolicy.accepts("", "", 1000, 2000))
    }

    @Test fun receiverTokenOutlivesTheUiTimeout() {
        val t0 = 1_000_000L
        assertEquals(12L * 60 * 1000, ReceiverTokenPolicy.SESSION_TIMEOUT_MILLIS)
        assertTrue(ReceiverTokenPolicy.MAX_PENDING_MILLIS > ReceiverTokenPolicy.SESSION_TIMEOUT_MILLIS)
        // a confirmation at 9:59 (or even at 13:59) is still accepted
        assertTrue(ReceiverTokenPolicy.accepts("t", "t", t0, t0 + 9 * 60 * 1000 + 59_000))
        assertTrue(ReceiverTokenPolicy.accepts("t", "t", t0, t0 + 14 * 60 * 1000))
        assertFalse(ReceiverTokenPolicy.accepts("t", "t", t0, t0 + 14 * 60 * 1000 + 1))
        assertFalse("clock went backwards", ReceiverTokenPolicy.accepts("t", "t", t0, t0 - 1))
    }

    @Test fun fallbackOnlyWhenSessionCreationIsUnsupportedOrForbidden() {
        assertTrue(InstallFallbackPolicy.allowed(Stage.CREATE_SESSION, SecurityException("no")))
        assertTrue(InstallFallbackPolicy.allowed(Stage.CREATE_SESSION, UnsupportedOperationException()))
        assertFalse(InstallFallbackPolicy.allowed(Stage.CREATE_SESSION, IOException("disk")))
        assertFalse(InstallFallbackPolicy.allowed(Stage.WRITE, SecurityException("no")))
        assertFalse(InstallFallbackPolicy.allowed(Stage.COMMIT, SecurityException("no")))
        assertFalse(InstallFallbackPolicy.allowed(Stage.COMMIT, IllegalStateException()))
    }

    @Test fun fallbackTargetsOnlyASystemInstaller() {
        assertEquals("com.google.android.packageinstaller", InstallFallbackPolicy.pickSystemInstaller(listOf(
            Candidate("com.evil.installer", false),
            Candidate("com.google.android.packageinstaller", true),
        )))
        assertNull(InstallFallbackPolicy.pickSystemInstaller(listOf(Candidate("com.evil.installer", false))))
        assertNull(InstallFallbackPolicy.pickSystemInstaller(emptyList()))
    }
}
