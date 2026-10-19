package dev.tvlauncher.controllers

import android.view.InputDevice
import dev.tvlauncher.devices.BuiltInDevices
import dev.tvlauncher.devices.DeviceClassifier
import dev.tvlauncher.devices.DeviceKind

/** One input device as the Controllers page sees it (plain values, so the rules are JVM-testable). */
data class PadDevice(
    val id: Int,
    val descriptor: String,
    val name: String,
    val sources: Int,
    val virtual: Boolean = false,
    val vibrates: Boolean = false,
)

/**
 * The connected game controllers, decided exactly like the Devices page: [DeviceClassifier] says
 * "gamepad" (remotes by name are already excluded there) and the TV's own receivers
 * ([BuiltInDevices]) never count. One entry per physical pad: a DualSense also shows up as a
 * touchpad and motion sensors, which are not gamepads, and duplicates share a descriptor.
 */
object ControllerList {
    fun filter(all: List<PadDevice>): List<PadDevice> = all
        .filter { it.id >= 0 && !it.virtual }
        .filter { DeviceClassifier.fromInputDevice(it.sources, it.name, isExternal = false, isVirtual = it.virtual) == DeviceKind.GAMEPAD }
        .filter { !BuiltInDevices.isTvInternal(it.name) }
        .distinctBy { it.descriptor.ifEmpty { "#" + it.id } }

    fun value(pads: List<PadDevice>): String = when (pads.size) {
        0 -> "None"
        else -> "${pads.size} connected"
    }

    fun subtitle(pads: List<PadDevice>): String =
        if (pads.isEmpty()) "Pair one with the Bluetooth button on the home screen"
        else pads.joinToString(", ") { it.name.trim().ifEmpty { "Controller" } }

    /** The button guide shown on the page, following the A/B choice. */
    fun legend(swapAB: Boolean): String =
        (if (swapAB) "B select · A back" else "A select · B back") + " · X options · Y search · L1 R1 page · Select settings"

    fun vibrationSubtitle(pads: List<PadDevice>): String = when {
        pads.isEmpty() || pads.any { it.vibrates } -> "A light buzz when focus moves"
        else -> "This controller cannot vibrate"
    }

    /** The input devices on the TV right now, controllers only. */
    fun scan(): List<PadDevice> = filter(
        try {
            InputDevice.getDeviceIds().toList().mapNotNull { id ->
                val d = InputDevice.getDevice(id) ?: return@mapNotNull null
                @Suppress("DEPRECATION")
                val vib = try { d.vibrator?.hasVibrator() == true } catch (t: Throwable) { false }
                PadDevice(id, d.descriptor.orEmpty(), d.name.orEmpty(), d.sources, d.isVirtual, vib)
            }
        } catch (t: Throwable) {
            emptyList()
        }
    )
}
