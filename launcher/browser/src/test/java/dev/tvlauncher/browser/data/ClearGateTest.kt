package dev.tvlauncher.browser.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearGateTest {
    @Test fun clearing_suppressesHistoryAndSession() {
        val g = ClearGate()
        g.begin()
        assertFalse(g.shouldRecordHistory("https://a.com/"))
        assertFalse(g.shouldScheduleSession())
    }

    @Test fun flagReset_afterFinish() {
        val g = ClearGate()
        g.begin(); g.end()
        assertTrue(g.shouldRecordHistory("https://a.com/"))
        assertTrue(g.shouldScheduleSession())
    }

    @Test fun aboutBlank_neverRecorded() {
        val g = ClearGate()
        assertFalse(g.shouldRecordHistory("about:blank"))
        assertFalse(g.shouldRecordHistory("javascript:x"))
        assertTrue(g.shouldRecordHistory("https://a.com/"))
    }
}
