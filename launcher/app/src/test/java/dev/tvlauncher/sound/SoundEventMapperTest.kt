package dev.tvlauncher.sound

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundEventMapperTest {
    @Test
    fun focusMove_afterDpad_playsMove() {
        assertEquals(SoundEvent.MOVE, SoundEventMapper.focusMoved(nowMs = 1_100, readyAtMs = 1_000, lastDpadMs = 1_050))
    }

    @Test
    fun focusMove_beforeReady_isSilent() {
        // First load / splash reveal still running.
        assertNull(SoundEventMapper.focusMoved(nowMs = 900, readyAtMs = 1_000, lastDpadMs = 890))
        assertNull(SoundEventMapper.focusMoved(nowMs = 900, readyAtMs = SoundEventMapper.NOT_READY, lastDpadMs = 890))
    }

    @Test
    fun focusMove_programmatic_isSilent() {
        // No D-pad press at all, or the last one is long ago: focus was moved by code (reload, restore).
        assertNull(SoundEventMapper.focusMoved(nowMs = 5_000, readyAtMs = 0, lastDpadMs = SoundEventMapper.NEVER))
        assertNull(SoundEventMapper.focusMoved(nowMs = 5_000, readyAtMs = 0, lastDpadMs = 5_000 - SoundEventMapper.DPAD_WINDOW_MS - 1))
        assertEquals(SoundEvent.MOVE,
            SoundEventMapper.focusMoved(nowMs = 5_000, readyAtMs = 0, lastDpadMs = 5_000 - SoundEventMapper.DPAD_WINDOW_MS))
    }

    @Test
    fun dpadKeys_only() {
        for (k in listOf(KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)) {
            assertTrue(SoundEventMapper.isDpadMove(k))
        }
        for (k in listOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU)) {
            assertFalse(SoundEventMapper.isDpadMove(k))
        }
    }

    @Test
    fun launch_startedOrFailed() {
        assertEquals(SoundEvent.LAUNCH, SoundEventMapper.launch(started = true))
        assertEquals(SoundEvent.ERROR, SoundEventMapper.launch(started = false))
    }

    @Test
    fun action_acceptedOrRejected() {
        assertEquals(SoundEvent.SELECT, SoundEventMapper.action(ok = true))
        assertEquals(SoundEvent.ERROR, SoundEventMapper.action(ok = false))
    }

    @Test
    fun panel_openAndClose() {
        assertEquals(SoundEvent.MENU_OPEN, SoundEventMapper.panel(opened = true))
        assertEquals(SoundEvent.BACK, SoundEventMapper.panel(opened = false))
    }

    @Test
    fun back_onlyWhenSomethingClosed() {
        assertEquals(SoundEvent.BACK, SoundEventMapper.back(closedSomething = true))
        assertNull(SoundEventMapper.back(closedSomething = false))
    }

    @Test
    fun stripAction_moreOpensMenu() {
        assertEquals(SoundEvent.MENU_OPEN, SoundEventMapper.stripAction(opensMenu = true))
        assertEquals(SoundEvent.SELECT, SoundEventMapper.stripAction(opensMenu = false))
    }
}
