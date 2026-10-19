package dev.tvlauncher.input

import android.view.KeyEvent
import dev.tvlauncher.input.PadKeyResult.Command
import dev.tvlauncher.input.PadKeyResult.Pass
import dev.tvlauncher.input.PadKeyResult.Swallow
import dev.tvlauncher.input.PadKeyResult.Translate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GamepadMapperTest {
    private val pad = PadSource.GAMEPAD or PadSource.JOYSTICK
    private val remote = PadSource.DPAD or 0x101 // DPAD | KEYBOARD, like the TV's IR receiver
    private var settings = GamepadSettings()
    private val m = GamepadMapper({ settings })

    private fun down(code: Int, src: Int = pad, dev: Int = 7, repeat: Int = 0, text: Boolean = false) =
        m.onKey(code, true, repeat, src, dev, text)

    private fun up(code: Int, src: Int = pad, dev: Int = 7, text: Boolean = false) = m.onKey(code, false, 0, src, dev, text)

    private fun press(code: Int): Pair<PadKeyResult, PadKeyResult> = down(code) to up(code)

    // ---- every button

    @Test fun a_isOk_and_b_isBack() {
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER) to Translate(KeyEvent.KEYCODE_DPAD_CENTER), press(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Translate(KeyEvent.KEYCODE_BACK) to Translate(KeyEvent.KEYCODE_BACK), press(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test fun commandButtons_fireOnPress_andSwallowTheRelease() {
        val table = mapOf(
            KeyEvent.KEYCODE_BUTTON_X to PadCommand.OPTIONS,
            KeyEvent.KEYCODE_BUTTON_Y to PadCommand.SEARCH,
            KeyEvent.KEYCODE_BUTTON_START to PadCommand.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to PadCommand.SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE to PadCommand.HOME,
            KeyEvent.KEYCODE_BUTTON_L1 to PadCommand.PAGE_PREV,
            KeyEvent.KEYCODE_BUTTON_R1 to PadCommand.PAGE_NEXT,
            KeyEvent.KEYCODE_BUTTON_L2 to PadCommand.SCROLL_UP,
            KeyEvent.KEYCODE_BUTTON_R2 to PadCommand.SCROLL_DOWN,
        )
        for ((code, cmd) in table) {
            assertEquals("$code down", Command(cmd, 0), down(code))
            assertEquals("$code up", Swallow, up(code))
        }
    }

    @Test fun stickClicksAndOtherButtons_areLeftToTheSystem() {
        for (code in listOf(KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR, KeyEvent.KEYCODE_BUTTON_C, KeyEvent.KEYCODE_BUTTON_1)) {
            assertEquals(Pass to Pass, press(code))
        }
    }

    @Test fun heldPageAndScroll_repeat_otherCommandsDoNot() {
        assertEquals(Command(PadCommand.PAGE_NEXT, 0), down(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(Command(PadCommand.PAGE_NEXT, 3), down(KeyEvent.KEYCODE_BUTTON_R1, repeat = 3))
        assertEquals(Command(PadCommand.SCROLL_DOWN, 0), down(KeyEvent.KEYCODE_BUTTON_R2))
        assertEquals(Command(PadCommand.SCROLL_DOWN, 1), down(KeyEvent.KEYCODE_BUTTON_R2, repeat = 1))
        assertEquals(Command(PadCommand.SEARCH, 0), down(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_BUTTON_Y, repeat = 1))
        assertEquals(Command(PadCommand.HOME, 0), down(KeyEvent.KEYCODE_BUTTON_MODE))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_BUTTON_MODE, repeat = 5))
    }

    @Test fun heldA_repeatsAsOk_soLongPressStillWorks() {
        down(KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_A, repeat = 1))
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_A, repeat = 20))
    }

    // ---- layout swap and settings

    @Test fun nintendoLayout_swapsAandB() {
        settings = GamepadSettings(swapAB = true)
        assertEquals(Translate(KeyEvent.KEYCODE_BACK), down(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_B))
        // X and Y keep their meaning.
        assertEquals(Command(PadCommand.OPTIONS, 0), down(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(Command(PadCommand.SEARCH, 0), down(KeyEvent.KEYCODE_BUTTON_Y))
    }

    @Test fun swapChangedMidPress_releaseMatchesThePress() {
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_A))
        settings = GamepadSettings(swapAB = true)
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_A, repeat = 1))
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), up(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(Translate(KeyEvent.KEYCODE_BACK), down(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test fun guideOff_leavesTheGuideButtonAlone() {
        settings = GamepadSettings(guideHome = false)
        assertEquals(Pass to Pass, press(KeyEvent.KEYCODE_BUTTON_MODE))
    }

    @Test fun textField_letsAandBThrough_forTheOnScreenKeyboard() {
        assertEquals(Pass, down(KeyEvent.KEYCODE_BUTTON_A, text = true))
        assertEquals(Pass, up(KeyEvent.KEYCODE_BUTTON_A, text = true))
        assertEquals(Pass, down(KeyEvent.KEYCODE_BUTTON_B, text = true))
        assertEquals(Pass, up(KeyEvent.KEYCODE_BUTTON_B, text = true))
        // Commands still work (Y closes Spotlight from its search field).
        assertEquals(Command(PadCommand.SEARCH, 0), down(KeyEvent.KEYCODE_BUTTON_Y, text = true))
    }

    @Test fun releaseWithoutPress_isSwallowed() {
        assertEquals(Swallow, up(KeyEvent.KEYCODE_BUTTON_B)) // its press went to the previous screen
        assertEquals(Swallow, down(KeyEvent.KEYCODE_BUTTON_A, repeat = 4))
    }

    @Test fun pressesAreTrackedPerDevice() {
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), down(KeyEvent.KEYCODE_BUTTON_A, dev = 1))
        assertEquals(Swallow, up(KeyEvent.KEYCODE_BUTTON_A, dev = 2))
        assertEquals(Translate(KeyEvent.KEYCODE_DPAD_CENTER), up(KeyEvent.KEYCODE_BUTTON_A, dev = 1))
    }

    // ---- the remote is never touched

    @Test fun remoteKeys_passUntouched() {
        val codes = listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_SEARCH
        )
        for (c in codes) {
            assertEquals("$c", Pass, down(c, src = remote, dev = 3))
            assertEquals("$c", Pass, up(c, src = remote, dev = 3))
        }
    }

    @Test fun padsOwnBackAndVolumeKeys_pass() {
        assertEquals(Pass, down(KeyEvent.KEYCODE_BACK))
        assertEquals(Pass, down(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_CENTER))
    }

    // ---- hat + key duplicates

    @Test fun keysFirst_padUsesKeys_andItsHatIsIgnored() {
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = true))
        assertEquals(Pass, up(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertFalse(m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = false))
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test fun hatFirst_padUsesHat_andItsDpadKeysAreSwallowed() {
        assertTrue(m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = true))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertEquals(Swallow, up(KeyEvent.KEYCODE_DPAD_RIGHT))
        assertTrue("the hat's release still counts", m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = false))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_DPAD_UP))
    }

    @Test fun restingHat_doesNotClaimTheDpad() {
        assertFalse(m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = false))
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_DOWN))
        assertFalse(m.arbiter.useAxes(7, InputArbiter.Channel.DPAD, active = true))
    }

    @Test fun eachPadDecidesForItself() {
        assertTrue(m.arbiter.useAxes(1, InputArbiter.Channel.DPAD, active = true))
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_UP, dev = 2))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_DPAD_UP, dev = 1))
        // The remote is never arbitrated, even with the same id.
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_UP, src = remote, dev = 1))
    }

    @Test fun triggersAsKeys_ignoreTheTriggerAxes_andViceVersa() {
        assertEquals(Command(PadCommand.SCROLL_UP, 0), down(KeyEvent.KEYCODE_BUTTON_L2, dev = 4))
        assertFalse(m.arbiter.useAxes(4, InputArbiter.Channel.TRIGGER, active = true))
        assertTrue(m.arbiter.useAxes(5, InputArbiter.Channel.TRIGGER, active = true))
        assertEquals(Swallow, down(KeyEvent.KEYCODE_BUTTON_R2, dev = 5))
        assertEquals(Swallow, up(KeyEvent.KEYCODE_BUTTON_R2, dev = 5))
    }

    @Test fun forget_resetsADevice() {
        assertTrue(m.arbiter.useAxes(9, InputArbiter.Channel.DPAD, active = true))
        down(KeyEvent.KEYCODE_BUTTON_A, dev = 9)
        m.forget(9)
        assertEquals(Pass, down(KeyEvent.KEYCODE_DPAD_UP, dev = 9))
        assertEquals(Swallow, up(KeyEvent.KEYCODE_BUTTON_A, dev = 9))
    }

    @Test fun buttonRange_andDirections() {
        assertTrue(GamepadMapper.isButton(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(GamepadMapper.isButton(KeyEvent.KEYCODE_BUTTON_MODE))
        assertTrue(GamepadMapper.isButton(KeyEvent.KEYCODE_BUTTON_16))
        assertFalse(GamepadMapper.isButton(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(GamepadMapper.isButton(KeyEvent.KEYCODE_BACK))
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, GamepadMapper.keyCodeOf(PadDir.UP))
        assertEquals(KeyEvent.KEYCODE_DPAD_RIGHT, GamepadMapper.keyCodeOf(PadDir.RIGHT))
    }

    @Test fun padSource() {
        assertTrue(PadSource.isPad(PadSource.GAMEPAD))
        assertTrue(PadSource.isPad(PadSource.JOYSTICK))
        assertTrue(PadSource.isPad(PadSource.GAMEPAD or PadSource.DPAD))
        assertFalse(PadSource.isPad(PadSource.DPAD))
        assertFalse(PadSource.isPad(0x101))
        assertFalse(PadSource.isPad(0x2002)) // mouse
    }
}
