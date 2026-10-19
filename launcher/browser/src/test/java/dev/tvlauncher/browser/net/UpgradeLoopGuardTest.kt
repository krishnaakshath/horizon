package dev.tvlauncher.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeLoopGuardTest {
    @Test fun firstUpgrade_ok_secondSameUrl_isLoop() {
        val g = UpgradeLoopGuard()
        assertFalse(g.isLoop("http://a.com/x"))
        assertTrue(g.isLoop("http://a.com/x"))
    }

    @Test fun differentUrls_noLoop() {
        val g = UpgradeLoopGuard()
        assertFalse(g.isLoop("http://a.com/x"))
        assertFalse(g.isLoop("http://a.com/y"))
    }

    @Test fun reset_afterSuccessfulLoad() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://a.com/x")
        g.reset()
        assertFalse(g.isLoop("http://a.com/x"))
    }

    @Test fun normalizesFragmentAndCase() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://A.com/x#one")
        assertTrue(g.isLoop("HTTP://a.com/x#two"))
    }
}
