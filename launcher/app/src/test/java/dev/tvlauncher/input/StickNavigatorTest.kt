package dev.tvlauncher.input

import dev.tvlauncher.input.NavStep.Kind.DOWN
import dev.tvlauncher.input.NavStep.Kind.REPEAT
import dev.tvlauncher.input.NavStep.Kind.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StickNavigatorTest {
    private var level = GamepadSettings.NORMAL
    private val nav = StickNavigator { StickTuning.forSensitivity(level) }

    @Test fun drift_insideTheDeadzone_neverMoves() {
        for ((x, y) in listOf(0.1f to 0.05f, -0.2f to 0.1f, 0.3f to 0f, 0.24f to 0.24f, 0f to -0.34f)) {
            assertEquals("$x,$y", emptyList<NavStep>(), nav.update(x, y, 0))
        }
        assertNull(nav.nextDueAt())
    }

    @Test fun deadzoneIsRadial_andThePressThresholdApplies() {
        // 0.40 is outside the deadzone but below the normal press point (0.55): nothing yet.
        assertEquals(emptyList<NavStep>(), nav.update(0.40f, 0f, 0))
        assertEquals(listOf(NavStep(PadDir.RIGHT, DOWN)), nav.update(0.60f, 0f, 10))
    }

    @Test fun directions_followAndroidAxes() {
        assertEquals(PadDir.LEFT, nav.update(-0.9f, 0f, 0).single().dir); nav.update(0f, 0f, 1)
        assertEquals(PadDir.UP, nav.update(0f, -0.9f, 2).single().dir); nav.update(0f, 0f, 3)
        assertEquals(PadDir.DOWN, nav.update(0f, 0.9f, 4).single().dir); nav.update(0f, 0f, 5)
        assertEquals(PadDir.RIGHT, nav.update(0.9f, 0f, 6).single().dir)
    }

    @Test fun hysteresis_holdsBetweenReleaseAndPress() {
        nav.update(0.8f, 0f, 0)
        assertEquals(emptyList<NavStep>(), nav.update(0.50f, 0f, 10)) // under press, over release (0.42)
        assertEquals(emptyList<NavStep>(), nav.update(0.43f, 0f, 20))
        assertEquals(listOf(NavStep(PadDir.RIGHT, UP)), nav.update(0.41f, 0f, 30))
        // Back up to 0.5 is not a new press (needs 0.55).
        assertEquals(emptyList<NavStep>(), nav.update(0.50f, 0f, 40))
    }

    @Test fun repeatTimeline_350ThenAccelerating_toNinety() {
        assertEquals(DOWN, nav.update(1f, 0f, 1000).single().kind)
        assertEquals(1350L, nav.nextDueAt())
        assertEquals(emptyList<NavStep>(), nav.tick(1349))
        val times = ArrayList<Long>()
        var t = 1000L
        while (times.size < 7) {
            t++
            val r = nav.tick(t)
            if (r.isNotEmpty()) {
                assertEquals(REPEAT, r.single().kind)
                assertEquals(times.size + 1, r.single().repeat)
                times += t
            }
        }
        assertEquals(listOf(1350L, 1500L, 1620L, 1720L, 1810L, 1900L, 1990L), times)
        assertEquals(UP, nav.update(0f, 0f, 2000).single().kind)
        assertNull(nav.nextDueAt())
        assertEquals(emptyList<NavStep>(), nav.tick(5000))
    }

    @Test fun timelineConstants() {
        assertEquals(350L, RepeatTimeline.delayAfter(0))
        assertEquals(150L, RepeatTimeline.delayAfter(1))
        assertEquals(90L, RepeatTimeline.delayAfter(4))
        assertEquals(90L, RepeatTimeline.delayAfter(400))
    }

    @Test fun newPress_restartsTheTimeline() {
        nav.update(1f, 0f, 0)
        nav.tick(350); nav.tick(500)
        nav.update(0f, 0f, 600)
        assertEquals(NavStep(PadDir.RIGHT, DOWN, 0), nav.update(1f, 0f, 700).single())
        assertEquals(1050L, nav.nextDueAt())
    }

    @Test fun diagonal_doesNotFlicker_butAClearTurnSwitches() {
        nav.update(0.9f, 0.2f, 0)
        assertEquals(PadDir.RIGHT, nav.held)
        // Rolling to a diagonal where down only slightly leads keeps RIGHT.
        assertEquals(emptyList<NavStep>(), nav.update(0.66f, 0.70f, 10))
        assertEquals(PadDir.RIGHT, nav.held)
        // Down clearly leads: release RIGHT, press DOWN.
        assertEquals(listOf(NavStep(PadDir.RIGHT, UP), NavStep(PadDir.DOWN, DOWN)), nav.update(0.3f, 0.9f, 20))
    }

    @Test fun flickThroughCentre_toTheOtherSide() {
        nav.update(1f, 0f, 0)
        assertEquals(listOf(NavStep(PadDir.RIGHT, UP), NavStep(PadDir.LEFT, DOWN)), nav.update(-1f, 0f, 5))
    }

    @Test fun sensitivityLevels_moveThePressPoint() {
        level = GamepadSettings.LOW
        assertEquals(emptyList<NavStep>(), nav.update(0.65f, 0f, 0))
        assertEquals(DOWN, nav.update(0.72f, 0f, 1).single().kind)
        nav.update(0f, 0f, 2)
        level = GamepadSettings.HIGH
        assertEquals(DOWN, nav.update(0.45f, 0f, 3).single().kind)
        for (l in 0..2) {
            val t = StickTuning.forSensitivity(l)
            assertTrue(t.release >= t.deadzone)
            assertTrue(t.press > t.release)
            assertEquals(0.35f, t.deadzone)
        }
    }

    @Test fun hatValues_pressAtOnce() {
        val hat = StickNavigator { StickTuning(0.35f, 0.5f, 0.4f) }
        assertEquals(NavStep(PadDir.UP, DOWN), hat.update(0f, -1f, 0).single())
        assertEquals(NavStep(PadDir.UP, UP), hat.update(0f, 0f, 30).single())
    }

    @Test fun reset_releasesAHeldDirection() {
        nav.update(0f, 1f, 0)
        nav.tick(350)
        assertEquals(listOf(NavStep(PadDir.DOWN, UP, 1)), nav.reset())
        assertEquals(emptyList<NavStep>(), nav.reset())
        assertNull(nav.held)
    }

    @Test fun axisButton_pressRepeatRelease() {
        val b = AxisButton()
        assertNull(b.update(0.5f, 0))
        assertEquals(0, b.update(0.7f, 10))
        assertNull(b.update(0.9f, 20)) // already pressed
        assertEquals(360L, b.nextDueAt())
        assertEquals(1, b.tick(360))
        assertNull(b.update(0.4f, 400)) // above release: still held
        assertEquals(2, b.tick(510))
        assertNull(b.update(0.2f, 520))
        assertNull(b.nextDueAt())
        assertNull(b.tick(9999))
        assertEquals(0, b.update(0.8f, 600))
    }

    @Test fun analogScroll_curve() {
        assertEquals(0f, AnalogScroll.velocity(0.3f), 0f)
        assertEquals(0f, AnalogScroll.velocity(-0.34f), 0f)
        assertEquals(AnalogScroll.MAX_DP_PER_SEC, AnalogScroll.velocity(1f), 0.01f)
        assertEquals(-AnalogScroll.MAX_DP_PER_SEC, AnalogScroll.velocity(-1.2f), 0.01f)
        val half = AnalogScroll.velocity(0.675f) // halfway between deadzone and full
        assertEquals(AnalogScroll.MAX_DP_PER_SEC / 4f, half, 1f)
    }

    @Test fun padWake_ignoresDrift() {
        val rest = PadAxes(0.1f, -0.2f, 0.3f, 0f, 0.1f, 0f, 0f, 0f)
        assertTrue(!PadWake.deliberate(rest))
        assertTrue(PadWake.deliberate(rest.copy(lx = 0.8f)))
        assertTrue(PadWake.deliberate(rest.copy(hatY = 1f)))
        assertTrue(PadWake.deliberate(rest.copy(rt = 0.9f)))
    }
}
