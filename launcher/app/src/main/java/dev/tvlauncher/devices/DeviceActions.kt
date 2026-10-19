package dev.tvlauncher.devices

/** Pure rules for the per-row action in the Devices panel. */
object DeviceActions {
    const val TV_PREFIX = "tv:"

    /** Passthrough input id to switch to, or null when the device is not a TV input. */
    fun passthroughInputId(device: ConnectedDevice): String? {
        if (!device.id.startsWith(TV_PREFIX)) return null
        return device.id.removePrefix(TV_PREFIX).takeIf { it.isNotEmpty() }
    }
}
