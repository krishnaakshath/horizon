package dev.tvlauncher.spotlight

import android.view.KeyEvent

/**
 * Which remote keys open Spotlight on the home screen, and which keys may never be used.
 * Pure (keycode constants only), so the rules are JVM-tested. The helper app keeps the same
 * reserved list in its ShortcutKeys.
 */
object TriggerKeys {
    /** Navigation, OK, volume and power keys: taking them over would break the remote. */
    val RESERVED: Set<Int> = setOf(
        KeyEvent.KEYCODE_UNKNOWN,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_TV_POWER,
        KeyEvent.KEYCODE_SLEEP,
        KeyEvent.KEYCODE_SOFT_SLEEP,
        KeyEvent.KEYCODE_WAKEUP,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_DPAD_UP_LEFT,
        KeyEvent.KEYCODE_DPAD_UP_RIGHT,
        KeyEvent.KEYCODE_DPAD_DOWN_LEFT,
        KeyEvent.KEYCODE_DPAD_DOWN_RIGHT,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_MUTE
    )

    enum class Check { OK, RESERVED, HAS_OWN_SWITCH }

    /** Whether [code] can be the extra key. Menu and Search have their own switches. */
    fun check(code: Int): Check = when {
        code <= 0 || code in RESERVED -> Check.RESERVED
        code == KeyEvent.KEYCODE_MENU || code == KeyEvent.KEYCODE_SEARCH -> Check.HAS_OWN_SWITCH
        else -> Check.OK
    }

    /** The key a [DoublePress] choice names, or 0. */
    fun doubleKey(c: SpotlightConfig): Int = when (c.doublePress) {
        DoublePress.OFF -> 0
        DoublePress.MENU -> KeyEvent.KEYCODE_MENU
        DoublePress.SEARCH -> KeyEvent.KEYCODE_SEARCH
        DoublePress.EXTRA -> c.extraKey
    }

    /** Keys whose single press opens or closes Spotlight (a double-press key is never one). */
    fun singleKeys(c: SpotlightConfig): Set<Int> {
        val s = HashSet<Int>()
        if (c.menuKey) s += KeyEvent.KEYCODE_MENU
        if (c.searchKey) s += KeyEvent.KEYCODE_SEARCH
        if (c.extraKey != 0) s += c.extraKey
        s -= doubleKey(c)
        return s
    }

    /** True when no key opens Spotlight at all (Down from the home row still does). */
    fun noKeys(c: SpotlightConfig): Boolean = singleKeys(c).isEmpty() && doubleKey(c) == 0

    enum class TestResult { OPENS, OPENS_ON_DOUBLE, NOTHING, RESERVED }

    /** What pressing [code] on the home screen would do ("Test a key"). */
    fun test(code: Int, c: SpotlightConfig): TestResult = when {
        code in singleKeys(c) -> TestResult.OPENS
        code != 0 && code == doubleKey(c) -> TestResult.OPENS_ON_DOUBLE
        code <= 0 || code in RESERVED -> TestResult.RESERVED
        else -> TestResult.NOTHING
    }

    fun testMessage(code: Int, c: SpotlightConfig, name: String): String = when (test(code, c)) {
        TestResult.OPENS -> "$name opens Spotlight"
        TestResult.OPENS_ON_DOUBLE -> "Press $name twice quickly to open Spotlight"
        TestResult.NOTHING -> "$name does not open Spotlight"
        TestResult.RESERVED -> "$name is kept for moving around and volume"
    }

    /** Message after a key was pressed in "Press the key you want"; null when it was accepted. */
    fun captureProblem(code: Int, name: String): String? = when (check(code)) {
        Check.OK -> null
        Check.RESERVED -> "$name is needed for moving around, so it can't open Spotlight"
        Check.HAS_OWN_SWITCH -> "$name has its own switch on this page"
    }

    /** Short summary for the page row, e.g. "Menu, Search" or "Bookmark (twice)". */
    fun summary(c: SpotlightConfig, name: (Int) -> String): String {
        val parts = ArrayList<String>()
        val dk = doubleKey(c)
        if (c.menuKey || dk == KeyEvent.KEYCODE_MENU) parts += label(KeyEvent.KEYCODE_MENU, dk, name)
        if (c.searchKey || dk == KeyEvent.KEYCODE_SEARCH) parts += label(KeyEvent.KEYCODE_SEARCH, dk, name)
        if (c.extraKey != 0) parts += label(c.extraKey, dk, name)
        return if (parts.isEmpty()) "None" else parts.joinToString(", ")
    }

    private fun label(code: Int, dk: Int, name: (Int) -> String) = if (code == dk) "${name(code)} (twice)" else name(code)

    /** Friendly names for keys TV remotes commonly have; [raw] (KeyEvent.keyCodeToString) otherwise. */
    fun name(code: Int, raw: String? = null): String = NAMES[code] ?: pretty(raw) ?: "Key $code"

    /** "KEYCODE_PROG_RED" -> "Prog red". */
    fun pretty(raw: String?): String? {
        if (raw.isNullOrBlank() || !raw.startsWith("KEYCODE_")) return null
        val s = raw.removePrefix("KEYCODE_").replace('_', ' ').lowercase()
        return s.replaceFirstChar { it.uppercase() }
    }

    private val NAMES: Map<Int, String> = mapOf(
        KeyEvent.KEYCODE_MENU to "Menu",
        KeyEvent.KEYCODE_SEARCH to "Search",
        KeyEvent.KEYCODE_BOOKMARK to "Bookmark",
        KeyEvent.KEYCODE_TV to "TV",
        KeyEvent.KEYCODE_INFO to "Info",
        KeyEvent.KEYCODE_GUIDE to "Guide",
        KeyEvent.KEYCODE_SETTINGS to "Settings",
        KeyEvent.KEYCODE_TV_INPUT to "Input",
        KeyEvent.KEYCODE_CAPTIONS to "Subtitles",
        KeyEvent.KEYCODE_PROG_RED to "Red",
        KeyEvent.KEYCODE_PROG_GREEN to "Green",
        KeyEvent.KEYCODE_PROG_YELLOW to "Yellow",
        KeyEvent.KEYCODE_PROG_BLUE to "Blue",
        KeyEvent.KEYCODE_ASSIST to "Assistant",
        KeyEvent.KEYCODE_VOICE_ASSIST to "Voice",
        KeyEvent.KEYCODE_ALL_APPS to "All apps",
        KeyEvent.KEYCODE_APP_SWITCH to "Recent apps",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause",
        KeyEvent.KEYCODE_CHANNEL_UP to "Channel up",
        KeyEvent.KEYCODE_CHANNEL_DOWN to "Channel down",
        KeyEvent.KEYCODE_LAST_CHANNEL to "Last channel",
        KeyEvent.KEYCODE_DVR to "Record list",
        KeyEvent.KEYCODE_BACK to "Back",
        KeyEvent.KEYCODE_HOME to "Home",
        KeyEvent.KEYCODE_DPAD_CENTER to "OK",
        KeyEvent.KEYCODE_ENTER to "OK",
        KeyEvent.KEYCODE_DPAD_UP to "Up",
        KeyEvent.KEYCODE_DPAD_DOWN to "Down",
        KeyEvent.KEYCODE_DPAD_LEFT to "Left",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Right",
        KeyEvent.KEYCODE_VOLUME_UP to "Volume up",
        KeyEvent.KEYCODE_VOLUME_DOWN to "Volume down",
        KeyEvent.KEYCODE_VOLUME_MUTE to "Mute",
        KeyEvent.KEYCODE_POWER to "Power"
    ) + (0..9).associate { (KeyEvent.KEYCODE_0 + it) to "$it" }
}

/**
 * Home-screen key routing for Spotlight with [SpotlightConfig]: single-press keys toggle on
 * DOWN (UP and repeats are consumed too, as before); the double-press key lets its first press
 * through and toggles on a second DOWN within [windowMs] (that press's UP is consumed).
 * Any other key in between cancels a pending double-press.
 */
class TriggerDetector(private val now: () -> Long, private val windowMs: Long = DOUBLE_MS) {
    enum class Decision { TOGGLE, CONSUME, PASS }

    private var lastTapAt = -1L
    private var lastTapKey = 0
    private var swallowUpOf = 0

    fun reset() {
        lastTapAt = -1L
        lastTapKey = 0
        swallowUpOf = 0
    }

    /** [blocked]: the tile menu is up (Spotlight keys do nothing over it). */
    fun onKey(code: Int, down: Boolean, repeat: Int, c: SpotlightConfig, blocked: Boolean): Decision {
        if (blocked) {
            reset()
            return Decision.PASS
        }
        if (code in TriggerKeys.singleKeys(c)) {
            return if (down && repeat == 0) Decision.TOGGLE else Decision.CONSUME
        }
        val dk = TriggerKeys.doubleKey(c)
        if (dk != 0 && code == dk) {
            if (!down) {
                if (swallowUpOf == code) {
                    swallowUpOf = 0
                    return Decision.CONSUME
                }
                return Decision.PASS
            }
            if (repeat > 0) return if (swallowUpOf == code) Decision.CONSUME else Decision.PASS
            val t = now()
            if (lastTapKey == code && lastTapAt >= 0 && t - lastTapAt <= windowMs) {
                lastTapAt = -1L
                lastTapKey = 0
                swallowUpOf = code
                return Decision.TOGGLE
            }
            lastTapAt = t
            lastTapKey = code
            return Decision.PASS
        }
        if (down) {
            lastTapAt = -1L
            lastTapKey = 0
        }
        return Decision.PASS
    }

    companion object {
        const val DOUBLE_MS = 450L
    }
}
