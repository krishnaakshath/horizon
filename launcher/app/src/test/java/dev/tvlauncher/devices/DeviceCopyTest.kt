package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceCopyTest {
    private fun dev(name: String, kind: DeviceKind, port: String? = null, detail: String? = null) =
        ConnectedDevice("id", name, kind, port, detail)

    @Test fun ps5_connected() {
        val d = dev("PS5", DeviceKind.CONSOLE_PLAYSTATION, "HDMI 2")
        assertEquals("PlayStation 5 connected", DeviceCopy.title(d, true))
        assertEquals("HDMI 2", DeviceCopy.subtitle(d, true))
    }

    @Test fun sonyInteractive_genericPlaystation() =
        assertEquals("PlayStation connected", DeviceCopy.title(dev("Sony Interactive Entertainment", DeviceKind.CONSOLE_PLAYSTATION), true))

    @Test fun usbDrive() = assertEquals("USB drive connected", DeviceCopy.title(dev("SanDisk Ultra", DeviceKind.USB_STORAGE), true))
    @Test fun controller() = assertEquals("Controller connected", DeviceCopy.title(dev("Wireless Controller", DeviceKind.BT_GAMEPAD), true))
    @Test fun headphonesDisconnected() = assertEquals("Headphones disconnected", DeviceCopy.title(dev("WH-1000", DeviceKind.BT_HEADPHONES), false))
    @Test fun headphonesSubtitleIsName() = assertEquals("WH-1000", DeviceCopy.subtitle(dev("WH-1000", DeviceKind.BT_HEADPHONES), false))
    @Test fun hdmiOther_usesName() = assertEquals("Capture Card connected", DeviceCopy.title(dev("Capture Card", DeviceKind.HDMI_OTHER), true))
    @Test fun hdmiOther_blankName_fallsBack() = assertEquals("HDMI device connected", DeviceCopy.title(dev("", DeviceKind.HDMI_OTHER), true))

    @Test fun neverNullText() {
        for (k in DeviceKind.values()) {
            for (name in listOf("", "  ", "x")) {
                val d = dev(name, k)
                for (c in listOf(true, false)) {
                    assertFalse(DeviceCopy.title(d, c).contains("null"))
                    assertFalse(DeviceCopy.subtitle(d, c).contains("null"))
                    assertFalse(DeviceCopy.title(d, c).isBlank())
                    assertFalse(DeviceCopy.subtitle(d, c).isBlank())
                }
            }
        }
    }
}
