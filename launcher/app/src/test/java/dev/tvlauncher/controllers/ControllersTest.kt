package dev.tvlauncher.controllers

import android.view.KeyEvent
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowGuard
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.input.PadAxes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControllersTest {
    private val gamepad = 0x401 or 0x1000010

    @Test fun list_keepsRealPadsOnly() {
        val all = listOf(
            PadDevice(5, "d-xbox", "Xbox Wireless Controller", gamepad),
            PadDevice(6, "d-ds", "Wireless Controller", gamepad),
            PadDevice(7, "d-ds", "Wireless Controller", gamepad), // same pad twice
            PadDevice(8, "d-ds-t", "Wireless Controller Touchpad", 0x100008), // touchpad: not a pad
            PadDevice(2, "d-ir", "MStar Smart TV IR Receiver", gamepad), // the TV's receiver
            PadDevice(3, "d-rc", "Voice Remote", gamepad), // a remote by name
            PadDevice(4, "d-kb", "Keyboard", 0x101),
            PadDevice(9, "d-v", "virtual-gamepad", gamepad, virtual = true),
            PadDevice(-1, "d-neg", "Virtual", gamepad),
        )
        assertEquals(listOf(5, 6), ControllerList.filter(all).map { it.id })
    }

    @Test fun summaries() {
        assertEquals("None", ControllerList.value(emptyList()))
        assertTrue(ControllerList.subtitle(emptyList()).contains("Bluetooth"))
        val two = listOf(PadDevice(1, "a", "Xbox Wireless Controller", gamepad), PadDevice(2, "b", " ", gamepad))
        assertEquals("2 connected", ControllerList.value(two))
        assertEquals("Xbox Wireless Controller, Controller", ControllerList.subtitle(two))
        assertTrue(ControllerList.legend(false).startsWith("A select · B back"))
        assertTrue(ControllerList.legend(true).startsWith("B select · A back"))
        assertEquals("This controller cannot vibrate", ControllerList.vibrationSubtitle(two))
        assertEquals("A light buzz when focus moves", ControllerList.vibrationSubtitle(two + PadDevice(3, "c", "DualSense", gamepad, vibrates = true)))
    }

    @Test fun page_fitsTheBudget_inSystem() {
        val spec = ControllersSection.spec
        assertTrue(spec.withinBudget)
        assertEquals(7, spec.primaryRows.size)
        assertTrue(spec.primaryRows.size <= PageSpec.MAX_PRIMARY)
        assertTrue(spec.primaryRows.all { it.startsWith("controllers.") })
        assertEquals(spec.primaryRows.size, spec.primaryRows.toSet().size)
        assertEquals(SettingsGroup.SYSTEM, ControllersSection.group)
        val guard = RowGuard(spec)
        spec.primaryRows.forEach { guard.claim(it, advanced = false) }
    }

    @Test fun page_registersOnce() {
        val r = SettingsRegistry.newDefault()
        ControllersSection.register(r)
        ControllersSection.register(r)
        assertEquals(1, r.sections().count { it.id == ControllersSection.ID })
    }

    @Test fun testModel_lightsButtonsHatAndTriggers() {
        val m = PadTestModel()
        m.onKey(KeyEvent.KEYCODE_BUTTON_A, true, "Pad", 0)
        assertTrue(m.isLit(PadPart.A))
        assertEquals("Pad", m.deviceName)
        m.onKey(KeyEvent.KEYCODE_BUTTON_A, false, null, 10)
        assertFalse(m.isLit(PadPart.A))
        m.onAxes(PadAxes(0f, 0f, 0f, 0f, 0.5f, 0f, -1f, 1f), null)
        assertTrue(m.isLit(PadPart.L2))
        assertFalse(m.isLit(PadPart.R2))
        assertTrue(m.isLit(PadPart.LEFT))
        assertTrue(m.isLit(PadPart.DOWN))
        assertFalse(m.isLit(PadPart.UP))
        m.onKey(KeyEvent.KEYCODE_DPAD_UP, true, null, 20)
        assertTrue(m.isLit(PadPart.UP))
        m.onKey(KeyEvent.KEYCODE_VOLUME_UP, true, null, 20) // ignored
    }

    @Test fun testModel_holdBLeaves_tapDoesNot() {
        val m = PadTestModel()
        m.onKey(KeyEvent.KEYCODE_BUTTON_B, true, null, 1000)
        assertFalse(m.shouldLeave(1500))
        m.onKey(KeyEvent.KEYCODE_BUTTON_B, true, null, 1500) // key repeat keeps the first press time
        assertTrue(m.shouldLeave(1000 + PadTestModel.LEAVE_MS))
        m.onKey(KeyEvent.KEYCODE_BUTTON_B, false, null, 2000)
        assertFalse(m.shouldLeave(5000))
        m.onKey(KeyEvent.KEYCODE_BUTTON_A, true, null, 6000)
        assertFalse(m.shouldLeave(9000))
    }
}
