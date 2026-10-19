package dev.tvlauncher.devices

import java.util.Locale

/**
 * The TV's own input devices, learned from the owner's TV probe: "MStar Smart TV IR Receiver",
 * "MTK TV KEYPAD", "gIrTouch_Mouse", "virtual-search", "sim-mouse", "sim-kb". They re-enumerate on
 * wake and must never announce. Pure.
 *
 * Markers (case-insensitive; '_' and '-' count as spaces for the word rules):
 * - "ir receiver" (also "ir_receiver", "IR-Receiver") and "keypad" as whole words;
 * - "girtouch" at the start of a word;
 * - "virtual" only as the whole name or as a system-style prefix "virtual-" / "virtual_", so a
 *   real "Virtual Reality Headset" still announces;
 * - "sim-" / "sim_" as a name prefix (emulated devices), so "Simple Keyboard" is not matched.
 */
object BuiltInDevices {
    private val words = Regex("""\bir ?receiver\b|\bkeypad\b|\bgirtouch""")
    private val prefixes = Regex("""^(virtual([-_].*)?|sim[-_].*)$""")

    fun isTvInternal(name: String?): Boolean {
        val raw = name?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (raw.isEmpty()) return false
        if (prefixes.matches(raw)) return true
        val n = raw.replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")
        return words.containsMatchIn(n)
    }
}
