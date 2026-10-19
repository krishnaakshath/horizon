package dev.tvlauncher.spotlight

import android.view.KeyEvent
import dev.tvlauncher.spotlight.TriggerDetector.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TriggerKeysTest {
    private val MENU = KeyEvent.KEYCODE_MENU
    private val SEARCH = KeyEvent.KEYCODE_SEARCH
    private val BOOKMARK = KeyEvent.KEYCODE_BOOKMARK
    private val INFO = KeyEvent.KEYCODE_INFO

    @Test fun conflicts_navigationOkVolumePower_areReserved() {
        val must = listOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_TV_POWER, KeyEvent.KEYCODE_SLEEP, 0, -1
        )
        for (k in must) assertEquals("$k", TriggerKeys.Check.RESERVED, TriggerKeys.check(k))
        assertEquals(TriggerKeys.Check.HAS_OWN_SWITCH, TriggerKeys.check(MENU))
        assertEquals(TriggerKeys.Check.HAS_OWN_SWITCH, TriggerKeys.check(SEARCH))
        for (k in listOf(BOOKMARK, INFO, KeyEvent.KEYCODE_TV, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_PROG_RED, KeyEvent.KEYCODE_5)) {
            assertEquals(TriggerKeys.Check.OK, TriggerKeys.check(k))
        }
    }

    @Test fun captureProblem_messages() {
        assertNull(TriggerKeys.captureProblem(BOOKMARK, "Bookmark"))
        assertTrue(TriggerKeys.captureProblem(KeyEvent.KEYCODE_BACK, "Back")!!.contains("moving around"))
        assertTrue(TriggerKeys.captureProblem(MENU, "Menu")!!.contains("own switch"))
    }

    @Test fun singleAndDoubleKeys() {
        val d = SpotlightConfig.DEFAULT
        assertEquals(setOf(MENU, SEARCH), TriggerKeys.singleKeys(d))
        assertEquals(0, TriggerKeys.doubleKey(d))
        val c = d.copy(extraKey = BOOKMARK, doublePress = DoublePress.MENU)
        assertEquals(setOf(SEARCH, BOOKMARK), TriggerKeys.singleKeys(c))
        assertEquals(MENU, TriggerKeys.doubleKey(c))
        val e = d.copy(menuKey = false, searchKey = false, extraKey = BOOKMARK, doublePress = DoublePress.EXTRA)
        assertEquals(emptySet<Int>(), TriggerKeys.singleKeys(e))
        assertEquals(BOOKMARK, TriggerKeys.doubleKey(e))
        assertFalse(TriggerKeys.noKeys(e))
        assertTrue(TriggerKeys.noKeys(d.copy(menuKey = false, searchKey = false)))
    }

    @Test fun keyTester() {
        val c = SpotlightConfig.DEFAULT.copy(extraKey = BOOKMARK, doublePress = DoublePress.EXTRA, searchKey = false)
        assertEquals(TriggerKeys.TestResult.OPENS, TriggerKeys.test(MENU, c))
        assertEquals(TriggerKeys.TestResult.OPENS_ON_DOUBLE, TriggerKeys.test(BOOKMARK, c))
        assertEquals(TriggerKeys.TestResult.NOTHING, TriggerKeys.test(SEARCH, c))
        assertEquals(TriggerKeys.TestResult.RESERVED, TriggerKeys.test(KeyEvent.KEYCODE_BACK, c))
        assertEquals("Menu opens Spotlight", TriggerKeys.testMessage(MENU, c, "Menu"))
        assertEquals("Press Bookmark twice quickly to open Spotlight", TriggerKeys.testMessage(BOOKMARK, c, "Bookmark"))
    }

    @Test fun names_andSummary() {
        assertEquals("Bookmark", TriggerKeys.name(BOOKMARK))
        assertEquals("7", TriggerKeys.name(KeyEvent.KEYCODE_7))
        assertEquals("Media top menu", TriggerKeys.name(9999, "KEYCODE_MEDIA_TOP_MENU"))
        assertEquals("Key 9999", TriggerKeys.name(9999))
        assertEquals("Menu, Search", TriggerKeys.summary(SpotlightConfig.DEFAULT) { TriggerKeys.name(it) })
        val c = SpotlightConfig.DEFAULT.copy(searchKey = false, extraKey = INFO, doublePress = DoublePress.EXTRA)
        assertEquals("Menu, Info (twice)", TriggerKeys.summary(c) { TriggerKeys.name(it) })
        assertEquals("None", TriggerKeys.summary(SpotlightConfig.DEFAULT.copy(menuKey = false, searchKey = false)) { TriggerKeys.name(it) })
    }

    // --- TriggerDetector (home-screen routing) ---

    private var t = 1_000L
    private val det = TriggerDetector(now = { t })

    private fun down(code: Int, c: SpotlightConfig, rep: Int = 0, blocked: Boolean = false) = det.onKey(code, true, rep, c, blocked)
    private fun up(code: Int, c: SpotlightConfig) = det.onKey(code, false, 0, c, false)

    @Test fun defaults_behaveLikeBefore() {
        val c = SpotlightConfig.DEFAULT
        assertEquals(Decision.TOGGLE, down(MENU, c))
        assertEquals(Decision.CONSUME, down(MENU, c, rep = 1))
        assertEquals(Decision.CONSUME, up(MENU, c))
        assertEquals(Decision.TOGGLE, down(SEARCH, c))
        assertEquals(Decision.PASS, down(KeyEvent.KEYCODE_DPAD_DOWN, c))
        assertEquals(Decision.PASS, down(MENU, c, blocked = true)) // tile menu up
    }

    @Test fun menuOff_passesThrough() {
        val c = SpotlightConfig.DEFAULT.copy(menuKey = false)
        assertEquals(Decision.PASS, down(MENU, c))
        assertEquals(Decision.PASS, up(MENU, c))
    }

    @Test fun extraKey_toggles() {
        val c = SpotlightConfig.DEFAULT.copy(extraKey = BOOKMARK)
        assertEquals(Decision.TOGGLE, down(BOOKMARK, c))
        assertEquals(Decision.CONSUME, up(BOOKMARK, c))
    }

    @Test fun doublePress_firstPassesSecondToggles() {
        val c = SpotlightConfig.DEFAULT.copy(doublePress = DoublePress.MENU)
        assertEquals(Decision.PASS, down(MENU, c))
        assertEquals(Decision.PASS, up(MENU, c))
        t += 300
        assertEquals(Decision.TOGGLE, down(MENU, c))
        assertEquals(Decision.CONSUME, down(MENU, c, rep = 1))
        assertEquals(Decision.CONSUME, up(MENU, c))
        // A third press starts over.
        t += 100
        assertEquals(Decision.PASS, down(MENU, c))
    }

    @Test fun doublePress_tooSlow_orInterrupted_doesNotToggle() {
        val c = SpotlightConfig.DEFAULT.copy(doublePress = DoublePress.MENU)
        down(MENU, c); up(MENU, c)
        t += TriggerDetector.DOUBLE_MS + 1
        assertEquals(Decision.PASS, down(MENU, c))
        up(MENU, c)
        t += 100
        assertEquals(Decision.PASS, down(KeyEvent.KEYCODE_DPAD_LEFT, c))
        t += 100
        assertEquals(Decision.PASS, down(MENU, c))
    }

    // --- Cross-checks with the helper app (source files read from the repo) ---

    private fun helperSource(name: String): String? {
        val candidates = listOf(File("../helper/src/main/java/dev/tvlauncher/helper/$name"), File("helper/src/main/java/dev/tvlauncher/helper/$name"))
        return candidates.firstOrNull { it.exists() }?.readText()
    }

    @Test fun reservedList_matchesTheHelpersShortcutKeys() {
        val src = helperSource("ShortcutKeys.kt") ?: return // module layout changed: nothing to compare
        val helperNames = Regex("KeyEvent\\.(KEYCODE_[A-Z0-9_]+)").findAll(src).map { it.groupValues[1] }.toSet()
        val ours = File("src/main/java/dev/tvlauncher/spotlight/TriggerKeys.kt").takeIf { it.exists() }?.readText() ?: return
        val block = ours.substringAfter("val RESERVED").substringBefore(")\n")
        val launcherNames = Regex("KeyEvent\\.(KEYCODE_[A-Z0-9_]+)").findAll(block).map { it.groupValues[1] }.toSet()
        assertEquals(launcherNames, helperNames)
    }

    @Test fun protocolStrings_matchTheHelper() {
        val src = helperSource("SpotlightSync.kt")
        assertNotNull("helper SpotlightSync.kt not found", src)
        for (s in listOf(
            HelperLink.PERMISSION, HelperLink.ACTION_SYNC, HelperLink.ACTION_STATE, HelperLink.LAUNCHER_PACKAGE,
            HelperLink.EXTRA_KEY, HelperLink.EXTRA_DOUBLE, HelperLink.EXTRA_WINDOW_MS, HelperLink.EXTRA_REASON,
            HelperLink.EXTRA_SERVICE_ON, HelperLink.EXTRA_SYNCED_KEY, HelperLink.EXTRA_SYNCED_DOUBLE, HelperLink.EXTRA_EFFECTIVE_KEY,
            HelperLink.REASON_HELLO, HelperLink.REASON_REPLY
        )) assertTrue(s, src!!.contains("\"$s\""))
        assertTrue(src!!.contains("DEFAULT_WINDOW_MS = ${TriggerDetector.DOUBLE_MS}L"))
    }
}
