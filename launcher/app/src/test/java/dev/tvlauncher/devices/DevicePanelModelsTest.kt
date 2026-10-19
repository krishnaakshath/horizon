package dev.tvlauncher.devices

import dev.tvlauncher.R
import dev.tvlauncher.panel.Trailing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicePanelModelsTest {
    private fun dev(id: String, name: String, kind: DeviceKind, port: String? = null, detail: String? = null) =
        ConnectedDevice(id, name, kind, port, detail)

    @Test fun devices_keepOrder_inputsGetChevron_settingsLast() {
        val c = DevicesPanelModel.build(listOf(
            dev("tv:hdmi1", "PlayStation 5", DeviceKind.CONSOLE_PLAYSTATION, "HDMI 1"),
            dev("usb:1", "SanDisk", DeviceKind.USB_STORAGE, "USB")
        ))
        assertEquals(listOf("dev:tv:hdmi1", "dev:usb:1", DevicesPanelModel.KEY_SETTINGS), c.rows.map { it.key })
        assertEquals(Trailing.Chevron, c.rows[0].trailing)
        assertEquals(Trailing.None, c.rows[1].trailing)
        assertEquals("HDMI 1", c.rows[0].value)
        assertEquals("2 connected", c.header.subtitle)
        assertTrue(c.messages.isEmpty())
    }

    @Test fun devices_emptyAndNotice() {
        val c = DevicesPanelModel.build(emptyList(), notice = "Couldn't switch")
        assertEquals(listOf("msg:notice", "msg:empty"), c.messages.map { it.key })
        assertEquals(listOf(DevicesPanelModel.KEY_SETTINGS), c.rows.map { it.key })
        assertEquals("Nothing connected", c.header.subtitle)
    }

    @Test fun devices_blankNameUsesKindLabel_andNoValueEchoingTheLabel() {
        val r = DevicesPanelModel.row(dev("x", " ", DeviceKind.KEYBOARD))
        assertEquals("Keyboard", r.label)
        assertNull(r.value)
        assertEquals(R.drawable.ic_dev_keyboard, r.icon)
    }

    private fun raw(id: String, label: String, state: Int = 0, parent: String? = null, type: Int = InputsModel.TYPE_HDMI) =
        RawInput(id, parent, label, type, state)

    @Test fun inputs_naturalOrder_andChildrenFoldIntoPorts() {
        val items = InputsModel.merge(listOf(
            raw("hw10", "HDMI 10", 2),
            raw("hw2", "HDMI 2", 2),
            raw("hw1", "HDMI 1", 2),
            raw("hw2/HDMI2000", "PS5", 0, parent = "hw2")
        ))
        assertEquals(listOf("HDMI 1", "HDMI 2", "HDMI 10"), items.map { it.label })
        val two = items[1]
        assertEquals("PS5", two.device)
        assertEquals(0, two.state)
        assertNull(items[0].device)
    }

    @Test fun inputs_genericNamesIgnored_monitorNamesUsed() {
        val items = InputsModel.merge(
            listOf(raw("a", "HDMI 1"), raw("b", "HDMI 2"), raw("c", "HDMI 3")),
            mapOf("a" to "HDMI 1", "b" to "Apple TV", "c" to "HDMI device")
        )
        assertEquals(listOf(null, "Apple TV", null), items.map { it.device })
    }

    @Test fun inputs_rows_showDeviceOrState() {
        val c = InputsModel.build(listOf(
            TvInputItem("a", "HDMI 1", InputsModel.TYPE_HDMI, 0, "PS5"),
            TvInputItem("b", "HDMI 2", InputsModel.TYPE_HDMI, 2, "Old name"),
            TvInputItem("c", "AV", InputsModel.TYPE_COMPOSITE, 1, null)
        ))
        assertEquals(listOf("in:a", "in:b", "in:c"), c.rows.map { it.key })
        // AV has no plug detection: "Available", never "Connected"/"Standby", and not counted.
        assertEquals(listOf("PS5", "No signal", "Available"), c.rows.map { it.value })
        assertEquals(R.drawable.ic_dev_av, c.rows[2].icon)
        assertEquals("1 connected", c.header.subtitle)
    }

    @Test fun inputs_empty() {
        val c = InputsModel.build(emptyList())
        assertEquals(listOf("msg:empty"), c.messages.map { it.key })
        assertEquals("None found", c.header.subtitle)
        assertTrue(c.rows.isEmpty()) // no TV-settings row any more
    }

    @Test fun natural_comparator() {
        val sorted = listOf("HDMI 10", "hdmi 2", "AV", "HDMI 1", "HDMI").sortedWith(InputsModel.NATURAL)
        assertEquals(listOf("AV", "HDMI", "HDMI 1", "hdmi 2", "HDMI 10"), sorted)
    }
}
