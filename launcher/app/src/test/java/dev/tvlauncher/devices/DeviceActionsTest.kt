package dev.tvlauncher.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceActionsTest {
    @Test fun tvInputDevice_hasInputId() =
        assertEquals("com.x/.Hdmi2", DeviceActions.passthroughInputId(ConnectedDevice("tv:com.x/.Hdmi2", "PS5", DeviceKind.CONSOLE_PLAYSTATION, "HDMI 2", null)))

    @Test fun usbDevice_hasNone() =
        assertNull(DeviceActions.passthroughInputId(ConnectedDevice("usb:/dev/1", "Drive", DeviceKind.USB_STORAGE, "USB", null)))

    @Test fun emptyId_none() =
        assertNull(DeviceActions.passthroughInputId(ConnectedDevice("tv:", "x", DeviceKind.HDMI_OTHER, null, null)))
}
