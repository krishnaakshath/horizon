package dev.tvlauncher.system

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonkeyGuardTest {
    private val original = MonkeyGuard.probe

    @After fun restore() { MonkeyGuard.probe = original }

    @Test fun defaultProbe_neverThrows_andIsOffOutsideAMonkeyRun() {
        // On the JVM the platform call is a stub: the guard must swallow it and allow changes.
        assertFalse(MonkeyGuard.blocks())
    }

    @Test fun blocks_whileAMonkeyDrivesTheUi() {
        MonkeyGuard.probe = { true }
        assertTrue(MonkeyGuard.blocks())
        MonkeyGuard.probe = { false }
        assertFalse(MonkeyGuard.blocks())
    }
}
