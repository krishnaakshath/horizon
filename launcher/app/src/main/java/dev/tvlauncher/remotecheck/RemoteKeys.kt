package dev.tvlauncher.remotecheck

/**
 * The remote check's key map: the common TV remote keys, grouped as they sit on a remote, with
 * the Android key codes they send (android.view.KeyEvent constants, inlined so the model stays
 * JVM-testable). Pure.
 */
object RemoteKeys {
    data class Key(val label: String, val codes: List<Int>) {
        val primary: Int get() = codes.first()
    }

    data class Group(val title: String, val keys: List<Key>)

    private fun k(label: String, vararg codes: Int) = Key(label, codes.toList())

    // KeyEvent.KEYCODE_* values.
    const val DPAD_UP = 19
    const val DPAD_DOWN = 20
    const val DPAD_LEFT = 21
    const val DPAD_RIGHT = 22
    const val DPAD_CENTER = 23
    const val ENTER = 66
    const val NUMPAD_ENTER = 160
    const val BACK = 4
    const val HOME = 3
    const val MENU = 82
    const val VOLUME_UP = 24
    const val VOLUME_DOWN = 25
    const val VOLUME_MUTE = 164
    const val MUTE = 91
    const val POWER = 26
    const val TV_POWER = 177
    const val CHANNEL_UP = 166
    const val CHANNEL_DOWN = 167
    const val PROG_RED = 183
    const val PROG_GREEN = 184
    const val PROG_YELLOW = 185
    const val PROG_BLUE = 186
    const val MEDIA_PLAY_PAUSE = 85
    const val MEDIA_STOP = 86
    const val MEDIA_NEXT = 87
    const val MEDIA_PREVIOUS = 88
    const val MEDIA_REWIND = 89
    const val MEDIA_FAST_FORWARD = 90
    const val MEDIA_PLAY = 126
    const val MEDIA_PAUSE = 127
    const val MEDIA_RECORD = 130
    const val NUM_0 = 7
    const val SEARCH = 84
    const val VOICE_ASSIST = 231
    const val ASSIST = 219
    const val SETTINGS = 176
    const val TV_INPUT = 178
    const val GUIDE = 172
    const val INFO = 165
    const val CAPTIONS = 175
    const val ALL_APPS = 284
    const val BOOKMARK = 174
    const val TV = 170
    const val LAST_CHANNEL = 229
    const val DVR = 173
    const val PERIOD = 56
    const val BUTTON_1 = 188

    val GROUPS: List<Group> = listOf(
        Group("Move and select", listOf(
            k("Up", DPAD_UP), k("Down", DPAD_DOWN), k("Left", DPAD_LEFT), k("Right", DPAD_RIGHT),
            k("OK", DPAD_CENTER, ENTER, NUMPAD_ENTER), k("Back", BACK), k("Home", HOME), k("Menu", MENU)
        )),
        Group("Volume and channels", listOf(
            k("Volume +", VOLUME_UP), k("Volume −", VOLUME_DOWN), k("Mute", VOLUME_MUTE, MUTE),
            k("Channel +", CHANNEL_UP), k("Channel −", CHANNEL_DOWN), k("Last channel", LAST_CHANNEL)
        )),
        Group("Colour keys", listOf(k("Red", PROG_RED), k("Green", PROG_GREEN), k("Yellow", PROG_YELLOW), k("Blue", PROG_BLUE))),
        Group("Playback", listOf(
            k("Play / pause", MEDIA_PLAY_PAUSE), k("Play", MEDIA_PLAY), k("Pause", MEDIA_PAUSE), k("Stop", MEDIA_STOP),
            k("Rewind", MEDIA_REWIND), k("Fast forward", MEDIA_FAST_FORWARD), k("Previous", MEDIA_PREVIOUS),
            k("Next", MEDIA_NEXT), k("Record", MEDIA_RECORD)
        )),
        Group("Numbers", (0..9).map { k("$it", NUM_0 + it) }),
        Group("TV and app keys", listOf(
            k("Input", TV_INPUT), k("Guide", GUIDE), k("Info", INFO), k("Settings", SETTINGS),
            k("Voice", VOICE_ASSIST, SEARCH, ASSIST), k("Subtitles", CAPTIONS), k("Apps", ALL_APPS),
            k("Live TV", TV), k("Recordings", DVR), k("Bookmark", BOOKMARK)
        ))
    )

    val ALL: List<Key> = GROUPS.flatMap { it.keys }

    private val byCode: Map<Int, Key> = HashMap<Int, Key>().apply { ALL.forEach { key -> key.codes.forEach { put(it, key) } } }

    fun keyFor(code: Int): Key? = byCode[code]

    /** Keys the TV itself takes before any app sees them (on TCL: Home, power and often volume). */
    val SYSTEM_TAKEN = setOf(HOME, POWER, TV_POWER)

    /** A readable name for any code: the map's label, else a generic "Key 123". */
    fun name(code: Int, systemName: String? = null): String =
        keyFor(code)?.label ?: systemName?.removePrefix("KEYCODE_")?.replace('_', ' ')?.lowercase()
            ?.replaceFirstChar { it.uppercase() }?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) } ?: "Key $code"
}

/** Where a key press came from, from InputDevice source bits and the device name. Pure. */
object KeySource {
    const val SOURCE_KEYBOARD = 0x00000101
    const val SOURCE_DPAD = 0x00000201
    const val SOURCE_GAMEPAD = 0x00000401
    const val SOURCE_JOYSTICK = 0x01000010
    const val SOURCE_MOUSE = 0x00002002

    fun label(source: Int, deviceName: String?, virtual: Boolean): String {
        val name = deviceName?.trim().orEmpty()
        return when {
            virtual -> "The TV"
            source and SOURCE_GAMEPAD == SOURCE_GAMEPAD || source and SOURCE_JOYSTICK == SOURCE_JOYSTICK -> withName("Controller", name)
            name.contains("keyboard", ignoreCase = true) -> withName("Keyboard", name)
            name.isNotEmpty() -> withName("Remote", name)
            source and SOURCE_DPAD == SOURCE_DPAD -> "Remote"
            source and SOURCE_KEYBOARD == SOURCE_KEYBOARD -> "Remote or keyboard"
            else -> "Unknown"
        }
    }

    private fun withName(kind: String, name: String) = if (name.isEmpty()) kind else "$kind · $name"
}

/**
 * The live state of the remote check page: the last key pressed, every map key seen, and keys
 * outside the map (so unknown buttons can be identified and mapped). Pure.
 */
class RemoteCheckModel {
    data class Press(val code: Int, val name: String, val scanCode: Int, val source: String, val repeat: Int)

    var last: Press? = null
        private set
    private val seenCodes = LinkedHashSet<Int>()
    private val others = LinkedHashMap<Int, String>()
    private var backAt = NEVER

    val seenKeys: Set<RemoteKeys.Key> get() = RemoteKeys.ALL.filterTo(LinkedHashSet()) { k -> k.codes.any { it in seenCodes } }

    /** Codes outside the map, in the order first seen, with their names. */
    val otherKeys: Map<Int, String> get() = others

    fun seen(key: RemoteKeys.Key): Boolean = key.codes.any { it in seenCodes }

    fun seenCount(): Int = seenKeys.size

    /** Records a key down. Returns true when the page should close (Back twice within [BACK_TWICE_MS]). */
    fun onKeyDown(code: Int, scanCode: Int, source: String, repeat: Int, now: Long, systemName: String? = null): Boolean {
        val name = RemoteKeys.name(code, systemName)
        last = Press(code, name, scanCode, source, repeat)
        if (repeat > 0) return false
        seenCodes += code
        if (RemoteKeys.keyFor(code) == null && code > 0) others[code] = name
        if (code == RemoteKeys.BACK) {
            val leave = backAt != NEVER && now - backAt in 0..BACK_TWICE_MS
            backAt = if (leave) NEVER else now
            return leave
        }
        backAt = NEVER
        return false
    }

    fun reset() {
        last = null
        seenCodes.clear()
        others.clear()
        backAt = NEVER
    }

    /** Volume keys pass through so the TV still changes the volume; everything else stays on the page. */
    fun passThrough(code: Int): Boolean = code == RemoteKeys.VOLUME_UP || code == RemoteKeys.VOLUME_DOWN ||
        code == RemoteKeys.VOLUME_MUTE || code == RemoteKeys.MUTE || code == RemoteKeys.POWER || code == RemoteKeys.TV_POWER

    fun summary(): String = "${seenCount()} of ${RemoteKeys.ALL.size} keys seen" +
        if (others.isEmpty()) "" else " · ${others.size} other"

    companion object {
        const val BACK_TWICE_MS = 1500L
        private const val NEVER = Long.MIN_VALUE

        const val NOTE = "Home and power go straight to the TV, so they may never show here. Volume keys still change the volume."
        const val LEAVE = "Press Back twice to leave"
        const val WAITING = "Press any button on the remote"
    }
}
