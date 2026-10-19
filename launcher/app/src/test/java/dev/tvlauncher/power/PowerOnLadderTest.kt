package dev.tvlauncher.power

import dev.tvlauncher.power.PowerOnLadder.Step
import org.junit.Assert.assertEquals
import org.junit.Test

class PowerOnLadderTest {
    @Test fun overlayGrantedTriesDirectThenHelper() {
        assertEquals(listOf(Step.DIRECT, Step.HELPER), PowerOnLadder.steps(overlayAllowed = true, helperReady = true, sdk = 30))
    }

    @Test fun withoutOverlayHelperComesFirstAndDirectIsTheLastTry() {
        assertEquals(listOf(Step.HELPER, Step.DIRECT), PowerOnLadder.steps(overlayAllowed = false, helperReady = true, sdk = 30))
        assertEquals(listOf(Step.DIRECT), PowerOnLadder.steps(overlayAllowed = false, helperReady = false, sdk = 30))
    }

    @Test fun android12AllowsDirect() {
        assertEquals(listOf(Step.DIRECT), PowerOnLadder.steps(overlayAllowed = false, helperReady = false, sdk = 31))
    }

    @Test fun ladderIsFiniteAndHasNoRepeats() {
        for (o in listOf(true, false)) for (h in listOf(true, false)) for (sdk in listOf(30, 31)) {
            val s = PowerOnLadder.steps(o, h, sdk)
            assertEquals(s.toSet().size, s.size)
            assert(s.size in 1..2)
        }
    }
}
