package dev.tvlauncher.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingTest {
    @Test fun smallTextIsUntouchedAndLargerGetsTighter() {
        assertEquals(0f, Tracking.forSp(13f), 0f)
        assertTrue(Tracking.forSp(30f) < Tracking.forSp(18f))
        assertTrue(Tracking.forSp(46f) < Tracking.forSp(30f))
        listOf(10f, 16f, 30f, 44f, 72f, 112f, 140f).forEach { assertTrue(it.toString(), Tracking.forSp(it) in -0.03f..0f) }
    }
}
