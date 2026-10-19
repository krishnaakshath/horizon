package dev.tvlauncher.browser

import dev.tvlauncher.browser.TrimPolicy.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class TrimPolicyTest {
    @Test fun runningModerate5_none() = assertEquals(Action.NONE, TrimPolicy.actionFor(5))
    @Test fun runningLow10_dropCaches() = assertEquals(Action.DROP_CACHES, TrimPolicy.actionFor(10))
    @Test fun runningCritical15_dropAll() = assertEquals(Action.DROP_ALL, TrimPolicy.actionFor(15))
    @Test fun uiHidden20_none() = assertEquals(Action.NONE, TrimPolicy.actionFor(20))
    @Test fun background40_dropCaches() = assertEquals(Action.DROP_CACHES, TrimPolicy.actionFor(40))
    @Test fun moderate60_dropCaches() = assertEquals(Action.DROP_CACHES, TrimPolicy.actionFor(60))
    @Test fun complete80_dropAll() = assertEquals(Action.DROP_ALL, TrimPolicy.actionFor(80))
    @Test fun unknownLevels() {
        assertEquals(Action.NONE, TrimPolicy.actionFor(0))
        assertEquals(Action.NONE, TrimPolicy.actionFor(-1))
        assertEquals(Action.NONE, TrimPolicy.actionFor(30))
        assertEquals(Action.DROP_CACHES, TrimPolicy.actionFor(50))
        assertEquals(Action.DROP_ALL, TrimPolicy.actionFor(100))
    }
}
