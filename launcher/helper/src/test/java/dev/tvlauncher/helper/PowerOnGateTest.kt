package dev.tvlauncher.helper

import android.view.KeyEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOnGateTest {
    @Test fun goesHomeFromTvSourceWithNoKeys() {
        assertTrue(PowerOnGate.allow("com.tcl.tv", -1L, 50_000L))
        assertTrue(PowerOnGate.allow(null, -1L, 50_000L))
    }

    @Test fun neverWhenLauncherAlreadyInFront() {
        assertFalse(PowerOnGate.allow(PowerOnGate.LAUNCHER_PACKAGE, -1L, 50_000L))
    }

    @Test fun neverRightAfterTheUserPressedAKey() {
        assertFalse(PowerOnGate.allow("com.netflix.ninja", 48_000L, 50_000L))
        assertTrue(PowerOnGate.allow("com.tcl.tv", 50_000L - PowerOnGate.RECENT_KEY_MS - 1, 50_000L))
    }

    @Test fun powerKeysAreNotNavigation() {
        assertFalse(PowerOnGate.countsAsNavigation(KeyEvent.KEYCODE_POWER))
        assertFalse(PowerOnGate.countsAsNavigation(KeyEvent.KEYCODE_WAKEUP))
        assertTrue(PowerOnGate.countsAsNavigation(KeyEvent.KEYCODE_DPAD_CENTER))
    }
}
