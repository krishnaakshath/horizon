package dev.tvlauncher.helper

import android.view.KeyEvent
import dev.tvlauncher.helper.Decision.CONSUME
import dev.tvlauncher.helper.Decision.PASS_THROUGH
import dev.tvlauncher.helper.KeyAction.DOWN
import dev.tvlauncher.helper.KeyAction.UP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The launcher's Spotlight key reaching the helper, and the helper acting on it. */
class SpotlightSyncTest {
    private val BOOKMARK = KeyEvent.KEYCODE_BOOKMARK
    private val MENU = KeyEvent.KEYCODE_MENU

    @Test fun accept_storesAValidKey() {
        val s = SpotlightSync.accept(HelperSettings(), 1, BOOKMARK, true, 400)
        assertEquals(BOOKMARK, s.launcherKey)
        assertTrue(s.launcherDouble)
        assertEquals(400, s.launcherWindowMs)
        assertEquals(BOOKMARK, s.effectiveKey)
        assertTrue(s.effectiveDouble)
    }

    @Test fun accept_rejectsReservedKeys_andMissingVersion() {
        for (k in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_SLEEP, -1)) {
            val s = SpotlightSync.accept(HelperSettings(launcherKey = BOOKMARK), 1, k, true, 400)
            assertEquals(0, s.launcherKey)
            assertFalse(s.launcherDouble)
        }
        val before = HelperSettings(launcherKey = BOOKMARK)
        assertEquals(before, SpotlightSync.accept(before, 0, KeyEvent.KEYCODE_INFO, false, 400))
    }

    @Test fun accept_clampsTheWindow_andKeepsTheHelpersOwnKey() {
        val s = SpotlightSync.accept(HelperSettings(shortcutKey = MENU), 1, BOOKMARK, false, 99_999)
        assertEquals(SpotlightSync.MAX_WINDOW_MS, s.launcherWindowMs)
        assertEquals(MENU, s.shortcutKey)
        assertEquals(BOOKMARK, s.effectiveKey) // the launcher's key wins
        val cleared = SpotlightSync.accept(s, 1, 0, false, 1)
        assertEquals(MENU, cleared.effectiveKey) // back to the helper's own
        assertEquals(SpotlightSync.MIN_WINDOW_MS, cleared.launcherWindowMs)
    }

    @Test fun settingsJson_roundTrip_andOldJsonStillReads() {
        val s = HelperSettings(shortcutKey = MENU, launcherKey = BOOKMARK, launcherDouble = true, launcherWindowMs = 500)
        assertEquals(s, HelperSettings.fromJson(s.toJson()))
        val old = """{"doubleBack":true,"windowMs":400,"shortcutKey":82}"""
        val o = HelperSettings.fromJson(old)
        assertEquals(0, o.launcherKey)
        assertEquals(MENU, o.effectiveKey)
        assertFalse(o.effectiveDouble)
        // Tampered stored values are dropped.
        assertEquals(0, HelperSettings.fromJson("""{"launcherKey":4,"launcherDouble":true}""").launcherKey)
    }

    @Test fun serviceListed() {
        val cls = "dev.tvlauncher.helper.HelperService"
        assertTrue(SpotlightSync.serviceListed("dev.tvlauncher.helper/$cls", "dev.tvlauncher.helper", cls))
        assertTrue(SpotlightSync.serviceListed("x/y:dev.tvlauncher.helper/.HelperService", "dev.tvlauncher.helper", cls))
        assertFalse(SpotlightSync.serviceListed(null, "dev.tvlauncher.helper", cls))
        assertFalse(SpotlightSync.serviceListed("dev.tvlauncher.helperx/$cls", "dev.tvlauncher.helper", cls))
    }

    // --- KeyStateMachine with the launcher's key ---

    private var t = 50_000L
    private var settings = HelperSettings(launcherKey = BOOKMARK)
    private var spotlights = 0
    private val m = KeyStateMachine(now = { t }, settings = { settings }, goHome = { true }, openSpotlight = { spotlights++ })
    private fun key(code: Int, a: KeyAction, rep: Int = 0, f: String? = "com.example.app") = m.handle(KeyInput(code, a, rep, false, f))

    @Test fun launcherKey_singlePress_opensSpotlightInOtherApps() {
        assertEquals(CONSUME, key(BOOKMARK, DOWN))
        assertEquals(CONSUME, key(BOOKMARK, UP))
        assertEquals(1, spotlights)
    }

    @Test fun launcherKey_passesThroughInTheLauncher_whichHandlesItItself() {
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN, f = "dev.tvlauncher"))
        assertEquals(PASS_THROUGH, key(BOOKMARK, UP, f = "dev.tvlauncher"))
        assertEquals(0, spotlights)
        // The helper's own key keeps working everywhere, as before.
        settings = HelperSettings(shortcutKey = MENU)
        assertEquals(CONSUME, key(MENU, DOWN, f = "dev.tvlauncher"))
        assertEquals(CONSUME, key(MENU, UP, f = "dev.tvlauncher"))
        assertEquals(1, spotlights)
    }

    @Test fun doublePress_firstReachesTheApp_secondOpens() {
        settings = HelperSettings(launcherKey = BOOKMARK, launcherDouble = true)
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN))
        assertEquals(PASS_THROUGH, key(BOOKMARK, UP))
        t += 200
        assertEquals(CONSUME, key(BOOKMARK, DOWN))
        assertEquals(CONSUME, key(BOOKMARK, DOWN, rep = 1))
        assertEquals(0, spotlights)
        assertEquals(CONSUME, key(BOOKMARK, UP))
        assertEquals(1, spotlights)
        t += 100
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN)) // a third press starts over
    }

    @Test fun doublePress_tooSlow_longPress_orInterrupted_doesNothing() {
        settings = HelperSettings(launcherKey = BOOKMARK, launcherDouble = true, launcherWindowMs = 450)
        key(BOOKMARK, DOWN); key(BOOKMARK, UP)
        t += 451
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN))
        // held: repeats, then up -> not armed
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN, rep = 1))
        key(BOOKMARK, UP)
        t += 100
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN))
        key(BOOKMARK, UP)
        t += 100
        key(KeyEvent.KEYCODE_DPAD_LEFT, DOWN)
        t += 50
        assertEquals(PASS_THROUGH, key(BOOKMARK, DOWN))
        assertEquals(0, spotlights)
    }

    @Test fun reservedListMatchesTheLaunchersWording() {
        // DPAD/OK/BACK/HOME/volume/power can never be the key.
        for (k in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_WAKEUP)) assertFalse(ShortcutKeys.isAllowed(k))
        assertTrue(ShortcutKeys.isAllowed(BOOKMARK))
    }
}
