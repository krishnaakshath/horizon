package dev.tvlauncher.power

import dev.tvlauncher.power.PowerOnDecision.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerOnPolicyTest {
    private val ok = PowerOnSnapshot(enabled = true, isHomeApp = true, safeModeClear = true)

    private fun armed(at: Long = 1_000L, t: PowerOnTrigger = PowerOnTrigger.WAKE) = PowerOnPolicy.arm(PowerOnState(), t, at)

    private fun reason(d: PowerOnDecision) = (d as PowerOnDecision.Skip).reason

    @Test fun goesHomeOnceRightAfterWake() {
        val (d, s) = PowerOnPolicy.decide(armed(), ok, 3_000L)
        assertEquals(PowerOnDecision.GoHome, d)
        assertTrue(s.done)
        val (d2, _) = PowerOnPolicy.decide(s, ok, 3_500L)
        assertEquals(Reason.ALREADY_DONE, reason(d2))
    }

    @Test fun notArmedDoesNothing() {
        assertEquals(Reason.NOT_ARMED, reason(PowerOnPolicy.decide(PowerOnState(), ok, 5_000L).first))
    }

    @Test fun onlyWithinWindow() {
        val st = armed(1_000L)
        assertEquals(PowerOnDecision.GoHome, PowerOnPolicy.decide(st, ok, 1_000L + PowerOnPolicy.WINDOW_MS).first)
        val (d, s) = PowerOnPolicy.decide(st, ok, 1_001L + PowerOnPolicy.WINDOW_MS)
        assertEquals(Reason.TOO_LATE, reason(d))
        assertTrue(s.done)
    }

    @Test fun secondSignalOfSameWakeDoesNotReArm() {
        val (_, fired) = PowerOnPolicy.decide(armed(1_000L), ok, 2_000L)
        // TCL: SCREEN_ON then STR_RESUME_COMPLETED (twice) a moment later.
        val again = PowerOnPolicy.arm(fired, PowerOnTrigger.WAKE, 4_000L)
        assertTrue(again.done)
        assertEquals(1_000L, again.armedAt)
        assertEquals(Reason.ALREADY_DONE, reason(PowerOnPolicy.decide(again, ok, 4_100L).first))
    }

    @Test fun laterWakeArmsAgain() {
        val (_, fired) = PowerOnPolicy.decide(armed(1_000L), ok, 2_000L)
        val next = PowerOnPolicy.arm(fired, PowerOnTrigger.WAKE, 1_000L + PowerOnPolicy.SAME_WAKE_MS + 1)
        assertFalse(next.done)
        assertEquals(PowerOnDecision.GoHome, PowerOnPolicy.decide(next, ok, next.armedAt + 100).first)
    }

    @Test fun bootSignalWinsWithinSameWake() {
        val s = PowerOnPolicy.arm(armed(1_000L, PowerOnTrigger.WAKE), PowerOnTrigger.BOOT, 2_000L)
        assertEquals(PowerOnTrigger.BOOT, s.trigger)
    }

    @Test fun rebootResetsState() {
        val (_, fired) = PowerOnPolicy.decide(armed(500_000L), ok, 500_100L)
        val afterReboot = PowerOnPolicy.arm(fired, PowerOnTrigger.BOOT, 30_000L) // uptime went down
        assertFalse(afterReboot.done)
        assertTrue(afterReboot.fires.isEmpty())
    }

    @Test fun disabledNotHomeOrSafeModeNeverAct() {
        assertEquals(Reason.DISABLED, reason(PowerOnPolicy.decide(armed(), ok.copy(enabled = false), 2_000L).first))
        assertEquals(Reason.NOT_HOME_APP, reason(PowerOnPolicy.decide(armed(), ok.copy(isHomeApp = false), 2_000L).first))
        val (d, s) = PowerOnPolicy.decide(armed(), ok.copy(safeModeClear = false), 2_000L)
        assertEquals(Reason.SAFE_MODE, reason(d))
        assertTrue("settled: no retry that could fight safe mode", s.done)
    }

    @Test fun neverAfterUserNavigated() {
        val (d, s) = PowerOnPolicy.decide(armed(), ok.copy(navigatedAfterWake = true), 2_000L)
        assertEquals(Reason.USER_NAVIGATED, reason(d))
        assertTrue(s.done)
    }

    @Test fun nothingWhenHomeAlreadyInFront() {
        assertEquals(Reason.ALREADY_HOME, reason(PowerOnPolicy.decide(armed(), ok.copy(homeInFront = true), 2_000L).first))
    }

    @Test fun unknownForegroundStillGoesHome() {
        val s = ok.copy(homeInFront = null, foreground = null, navigatedAfterWake = null)
        assertEquals(PowerOnDecision.GoHome, PowerOnPolicy.decide(armed(), s, 2_000L).first)
    }

    @Test fun loopGuardCapsPushes() {
        var st = PowerOnState()
        var t = 10_000L
        val results = (1..5).map {
            st = PowerOnPolicy.arm(st, PowerOnTrigger.WAKE, t)
            val (d, n) = PowerOnPolicy.decide(st, ok, t + 100)
            st = n
            t += PowerOnPolicy.SAME_WAKE_MS + 1
            d
        }
        assertEquals(PowerOnPolicy.MAX_FIRES, results.count { it == PowerOnDecision.GoHome })
        assertEquals(Reason.LOOP_GUARD, reason(results.last()))
    }

    @Test fun loopGuardForgetsOldPushes() {
        var st = PowerOnState()
        repeat(PowerOnPolicy.MAX_FIRES) { i ->
            val t = 10_000L + i * (PowerOnPolicy.SAME_WAKE_MS + 1)
            st = PowerOnPolicy.decide(PowerOnPolicy.arm(st, PowerOnTrigger.WAKE, t), ok, t).second
        }
        val later = 10_000L + PowerOnPolicy.FIRE_WINDOW_MS + 60_000L
        st = PowerOnPolicy.arm(st, PowerOnTrigger.WAKE, later)
        assertEquals(PowerOnDecision.GoHome, PowerOnPolicy.decide(st, ok, later + 10).first)
    }
}
