package dev.tvlauncher.helper

import android.view.KeyEvent
import dev.tvlauncher.helper.KeyAction.DOWN
import dev.tvlauncher.helper.KeyAction.UP
import dev.tvlauncher.helper.Decision.CONSUME
import dev.tvlauncher.helper.Decision.PASS_THROUGH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KeyStateMachineTest {
    private val BACK = KeyEvent.KEYCODE_BACK
    private val MENU = KeyEvent.KEYCODE_MENU

    private var t = 10_000L
    private var settings = HelperSettings(doubleBackEnabled = true, shortcutKey = MENU)
    private var fg: String? = "com.example.app"
    private var homes = 0
    private var spotlights = 0
    private var homeResult = true
    private var homeThrows = false
    private var spotlightThrows = false

    private val m = KeyStateMachine(
        now = { t },
        settings = { settings },
        goHome = {
            if (homeThrows) throw IllegalStateException("boom")
            homes++
            homeResult
        },
        openSpotlight = {
            if (spotlightThrows) throw IllegalStateException("boom")
            spotlights++
        },
    )

    private fun key(code: Int, a: KeyAction, rep: Int = 0, canceled: Boolean = false, f: String? = fg) =
        m.handle(KeyInput(code, a, rep, canceled, f))

    private fun down(code: Int = BACK, rep: Int = 0, f: String? = fg) = key(code, DOWN, rep, false, f)
    private fun up(code: Int = BACK, canceled: Boolean = false, f: String? = fg) = key(code, UP, 0, canceled, f)

    @Test fun firstBack_passesThrough_bothDownAndUp() {
        assertEquals(PASS_THROUGH, down())
        t += 50
        assertEquals(PASS_THROUGH, up())
        assertEquals(0, homes)
    }

    @Test fun secondBackWithinWindow_consumesDownAndUp_andGoesHome() {
        down(); up()
        t += 200
        assertEquals(CONSUME, down())
        t += 50
        assertEquals(CONSUME, up())
        assertEquals(1, homes)
        // third press starts a fresh window
        t += 50
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
        assertEquals(1, homes)
    }

    @Test fun secondBack_afterForegroundChange_doesNotTriggerHome() {
        down(f = "app.a"); up(f = "app.a")
        t += 100
        assertEquals(PASS_THROUGH, down(f = "app.b"))
        assertEquals(PASS_THROUGH, up(f = "app.b"))
        assertEquals(0, homes)
    }

    @Test fun staleSwallow_clearedOnNewDown() {
        down(); up()
        t += 100
        assertEquals(CONSUME, down()) // swallowed, UP never arrives
        t += 5_000
        assertEquals(PASS_THROUGH, down()) // unrelated new press
        assertEquals(PASS_THROUGH, up())
        assertEquals(0, homes)
    }

    @Test fun featureDisabledMidPress_stillConsumesPendingUp_thenPassesNext() {
        down(); up()
        t += 100
        assertEquals(CONSUME, down())
        settings = settings.copy(doubleBackEnabled = false)
        assertEquals(CONSUME, up())
        assertEquals(0, homes)
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
    }

    @Test fun autoRepeatDowns_notCountedAsPresses() {
        down(); up()
        t += 100
        // a bare auto-repeat DOWN (no initial DOWN seen) must not be swallowed
        assertEquals(PASS_THROUGH, down(rep = 1))
        assertEquals(0, homes)
    }

    @Test fun autoRepeatOfSwallowedDown_isConsumed() {
        down(); up()
        t += 100
        assertEquals(CONSUME, down())
        assertEquals(CONSUME, down(rep = 1))
        assertEquals(CONSUME, up())
        assertEquals(1, homes)
    }

    @Test fun longPressUp_firstPress_doesNotArmDetector() {
        down(); down(rep = 1); down(rep = 2)
        assertEquals(PASS_THROUGH, up())
        t += 100
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
        assertEquals(0, homes)
    }

    @Test fun canceledUp_ignored() {
        down()
        assertEquals(PASS_THROUGH, up(canceled = true))
        t += 100
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
        assertEquals(0, homes)
    }

    @Test fun excludedForeground_neverTriggers() {
        val pkg = "dev.tvlauncher"
        down(f = pkg); up(f = pkg)
        t += 100
        assertEquals(PASS_THROUGH, down(f = pkg))
        assertEquals(PASS_THROUGH, up(f = pkg))
        assertEquals(0, homes)
    }

    @Test fun emptyStoredExclusions_stillExcludeDefaults() {
        settings = settings.copy(exclusions = emptySet())
        val pkg = "com.android.settings"
        down(f = pkg); up(f = pkg)
        t += 100
        assertEquals(PASS_THROUGH, down(f = pkg))
        assertEquals(0, homes)
    }

    @Test fun nullOrBlankForeground_failsClosed() {
        for (f in listOf(null, "", "  ")) {
            down(f = f); up(f = f)
            t += 100
            assertEquals(PASS_THROUGH, down(f = f))
            assertEquals(PASS_THROUGH, up(f = f))
        }
        assertEquals(0, homes)
    }

    @Test fun homeFailure_doesNotLeaveDetectorArmed() {
        homeResult = false
        down(); up()
        t += 100
        down(); up()
        assertEquals(1, homes)
        t += 100
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
        assertEquals(1, homes)
    }

    @Test fun shortcutKey_none_neverMatches() {
        settings = settings.copy(shortcutKey = 0)
        for (code in listOf(0, KeyEvent.KEYCODE_UNKNOWN, MENU)) {
            assertEquals(PASS_THROUGH, down(code))
            assertEquals(PASS_THROUGH, up(code))
        }
        assertEquals(0, spotlights)
    }

    @Test fun shortcutKey_consumesDownAndUp_andOpensSpotlightOnUpOnly() {
        assertEquals(CONSUME, down(MENU))
        assertEquals(0, spotlights)
        assertEquals(CONSUME, up(MENU))
        assertEquals(1, spotlights)
    }

    @Test fun shortcut_longPress_doesNotOpenTwice() {
        down(MENU); down(MENU, rep = 1); down(MENU, rep = 2)
        assertEquals(0, spotlights)
        up(MENU)
        assertEquals(1, spotlights)
    }

    @Test fun shortcut_ignoredInHelperItself() {
        assertEquals(PASS_THROUGH, down(MENU, f = "dev.tvlauncher.helper"))
        assertEquals(PASS_THROUGH, up(MENU, f = "dev.tvlauncher.helper"))
        assertEquals(0, spotlights)
    }

    @Test fun shortcut_upWithoutDown_passesThrough() {
        assertEquals(PASS_THROUGH, up(MENU))
        assertEquals(0, spotlights)
    }

    @Test fun exception_inGoHome_failsOpen() {
        down(); up()
        t += 100
        assertEquals(CONSUME, down())
        homeThrows = true
        assertEquals(PASS_THROUGH, up())
        homeThrows = false
        // state was reset: next press is a fresh first press
        t += 100
        assertEquals(PASS_THROUGH, down())
        assertEquals(PASS_THROUGH, up())
        assertEquals(0, homes)
    }

    @Test fun exception_inSettings_failsOpen() {
        val bad = KeyStateMachine({ t }, { throw IllegalStateException("x") }, { true }, {})
        assertEquals(PASS_THROUGH, bad.handle(KeyInput(BACK, DOWN, 0, false, "a.b")))
    }

    @Test fun exception_inSpotlight_failsOpen() {
        spotlightThrows = true
        down(MENU)
        assertEquals(PASS_THROUGH, up(MENU))
        spotlightThrows = false
        assertFalse(spotlights > 0)
    }
}
