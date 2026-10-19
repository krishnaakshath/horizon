package dev.tvlauncher.ui

import android.view.KeyEvent
import dev.tvlauncher.ui.SearchKeyPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SearchKeyPolicyTest {
    @Test fun enter_whenImeHidden_showsIme() {
        assertEquals(Decision.SHOW_IME, SearchKeyPolicy.decide(KeyEvent.KEYCODE_ENTER, imeVisible = false, queryEmpty = false))
        assertEquals(Decision.SHOW_IME, SearchKeyPolicy.decide(KeyEvent.KEYCODE_NUMPAD_ENTER, imeVisible = false, queryEmpty = true))
    }

    @Test fun dpadCenter_whenImeHidden_showsIme() {
        assertEquals(Decision.SHOW_IME, SearchKeyPolicy.decide(KeyEvent.KEYCODE_DPAD_CENTER, imeVisible = false, queryEmpty = true))
    }

    @Test fun enter_whenImeVisibleAndQueryNotEmpty_submits() {
        assertEquals(
            Decision.SUBMIT_AND_FOCUS_RESULTS,
            SearchKeyPolicy.decide(KeyEvent.KEYCODE_ENTER, imeVisible = true, queryEmpty = false)
        )
    }

    @Test fun enter_whenImeVisibleAndQueryEmpty_staysOpen() {
        val d = SearchKeyPolicy.decide(KeyEvent.KEYCODE_ENTER, imeVisible = true, queryEmpty = true)
        assertNotEquals(Decision.SUBMIT_AND_FOCUS_RESULTS, d)
        assertEquals(Decision.SHOW_IME, d)
    }

    @Test fun otherKeys_passThrough() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_BACK)) {
            assertEquals(Decision.PASS_THROUGH, SearchKeyPolicy.decide(k, imeVisible = false, queryEmpty = true))
            assertEquals(Decision.PASS_THROUGH, SearchKeyPolicy.decide(k, imeVisible = true, queryEmpty = false))
        }
    }
}
