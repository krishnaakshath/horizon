package dev.tvlauncher.helper

import android.view.KeyEvent
import dev.tvlauncher.helper.PadKeys.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PadKeysTest {
    private val pad = 0x401
    private val joystick = 0x1000010
    private val remote = 0x201 or 0x101

    @Test fun guide_goesHomeOnce_andConsumesThePress() {
        assertEquals(Verdict.HOME, PadKeys.decide(KeyEvent.KEYCODE_BUTTON_MODE, pad, true, 0, guideHome = true))
        assertEquals(Verdict.CONSUME, PadKeys.decide(KeyEvent.KEYCODE_BUTTON_MODE, pad, true, 3, guideHome = true))
        assertEquals(Verdict.CONSUME, PadKeys.decide(KeyEvent.KEYCODE_BUTTON_MODE, pad, false, 0, guideHome = true))
        assertEquals(Verdict.HOME, PadKeys.decide(KeyEvent.KEYCODE_BUTTON_MODE, joystick, true, 0, guideHome = true))
    }

    @Test fun guideOff_isTheAppsKey() {
        assertEquals(Verdict.PASS, PadKeys.decide(KeyEvent.KEYCODE_BUTTON_MODE, pad, true, 0, guideHome = false))
    }

    @Test fun padBackAndVolume_bypassTheRemoteRules() {
        for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_MUTE)) {
            assertEquals("$code", Verdict.PASS, PadKeys.decide(code, pad, true, 0, guideHome = true))
            assertEquals("$code", Verdict.PASS, PadKeys.decide(code, pad, false, 0, guideHome = true))
        }
    }

    @Test fun otherPadButtons_keepTheUsualRules_soAShortcutStillWorks() {
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_BUTTON_A)) {
            assertEquals("$code", Verdict.NOT_PAD, PadKeys.decide(code, pad, true, 0, guideHome = true))
        }
    }

    @Test fun remoteKeys_areNotPadKeys() {
        for (code in listOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_BUTTON_MODE)) {
            assertEquals("$code", Verdict.NOT_PAD, PadKeys.decide(code, remote, true, 0, guideHome = true))
        }
        assertFalse(PadKeys.isPad(remote))
        assertTrue(PadKeys.isPad(pad or 0x201))
    }

    @Test fun guideSetting_fromTheLauncherText() {
        assertTrue(PadKeys.guideHomeFrom(null))
        assertTrue(PadKeys.guideHomeFrom("swap=0;sens=1;guide=1;vib=0"))
        assertFalse(PadKeys.guideHomeFrom("swap=0;sens=1;guide=0;vib=0"))
        assertTrue(PadKeys.guideHomeFrom("nonsense"))
    }
}
