package dev.tvlauncher.browser.net

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeLoopGuardTest {
    @Test fun bounce_httpHttpsHttpHttps_isLoop() {
        val g = UpgradeLoopGuard()
        assertFalse(g.isLoop("http://a.com/x", now = 0))
        g.onFinished("https://a.com/x", now = 300)   // https page finishes, then the site bounces back
        assertTrue(g.isLoop("http://a.com/x", now = 600))
    }

    @Test fun stableFinish_for2s_resets() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://a.com/x", now = 0)
        g.onFinished("https://a.com/x", now = 500)
        assertFalse(g.isLoop("http://a.com/x", now = 2600))
    }

    @Test fun finishOfOtherPage_doesNotReset() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://a.com/x", now = 0)
        g.onFinished("https://other.com/", now = 10)
        assertTrue(g.isLoop("http://a.com/x", now = 5000))
    }

    @Test fun userNavigation_resets() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://a.com/x", now = 0)
        g.onUserNavigation()
        assertFalse(g.isLoop("http://a.com/x", now = 1))
    }

    @Test fun differentUrls_noLoop() {
        val g = UpgradeLoopGuard()
        assertFalse(g.isLoop("http://a.com/x", 0))
        assertFalse(g.isLoop("http://a.com/y", 1))
    }

    @Test fun normalizesFragmentCaseAndDefaultPort() {
        val g = UpgradeLoopGuard()
        g.isLoop("http://A.com/x#one", 0)
        assertTrue(g.isLoop("HTTP://a.com:80/x#two", 1))
    }
}
