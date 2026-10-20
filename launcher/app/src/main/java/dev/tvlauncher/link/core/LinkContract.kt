package dev.tvlauncher.link.core

/**
 * The contract between the launcher (:app, no INTERNET), the Link companion (:link, owns the
 * network) and the Helper (:helper, accessibility). Pure constants and tiny pure helpers only:
 * this file is compiled into both :app and :link (see link/build.gradle.kts), so the names can
 * never drift apart.
 *
 * Every channel is guarded by [PERMISSION] (protectionLevel signature): only apps signed with our
 * certificate can query the providers, call their methods or send the broadcasts.
 */
object LinkContract {
    const val LINK_PACKAGE = "dev.tvlauncher.link"
    const val LAUNCHER_PACKAGE = "dev.tvlauncher"
    const val HELPER_PACKAGE = "dev.tvlauncher.helper"
    const val BROWSER_PACKAGE = "dev.tvlauncher.browser"

    /** Signature permission declared by :app, :link and :helper (install order does not matter). */
    const val PERMISSION = "dev.tvlauncher.permission.LINK"

    /** Fixed port of the phone page (local network only). */
    const val PORT = 8765

    // ---- :link's provider (read by the launcher)

    const val AUTHORITY = "dev.tvlauncher.link"
    const val PATH_STATUS = "status"
    const val PATH_HOME = "home"
    const val PATH_UPDATE = "update"
    const val URI_STATUS = "content://$AUTHORITY/$PATH_STATUS"
    const val URI_HOME = "content://$AUTHORITY/$PATH_HOME"
    const val URI_UPDATE = "content://$AUTHORITY/$PATH_UPDATE"
    const val URI_BASE = "content://$AUTHORITY"

    // /status: one row.
    const val COL_ENABLED = "enabled"
    const val COL_RUNNING = "running"
    const val COL_ADDRESS = "address"
    const val COL_PAIRING_REQUIRED = "pairing_required"
    const val COL_PIN = "pin"
    const val COL_PIN_EXPIRES_MS = "pin_expires_ms"
    const val COL_PAIRED = "paired"
    const val COL_HA_CONFIGURED = "ha_configured"
    const val COL_HA_HOST = "ha_host"
    val STATUS_COLUMNS = arrayOf(
        COL_ENABLED, COL_RUNNING, COL_ADDRESS, COL_PAIRING_REQUIRED, COL_PIN, COL_PIN_EXPIRES_MS,
        COL_PAIRED, COL_HA_CONFIGURED, COL_HA_HOST,
    )

    // /home: one row per favourite entity (at most [MAX_HOME_ROWS]), in the user's order.
    const val COL_ENTITY_ID = "entity_id"
    const val COL_NAME = "name"
    const val COL_CONTROL = "control"
    const val COL_STATE = "state"
    const val COL_ON = "is_on"
    const val COL_AVAILABLE = "available"
    const val COL_TARGET = "target"
    const val COL_STEP = "step"
    /** Overall connection state of the snapshot (same value on every row; see [HA_OK] etc.). */
    const val COL_HA_STATE = "ha_state"
    const val COL_UPDATED_MS = "updated_ms"
    val HOME_COLUMNS = arrayOf(
        COL_ENTITY_ID, COL_NAME, COL_CONTROL, COL_STATE, COL_ON, COL_AVAILABLE, COL_TARGET, COL_STEP,
        COL_HA_STATE, COL_UPDATED_MS,
    )
    const val MAX_HOME_ROWS = 8

    /** Values of [COL_CONTROL]. */
    const val CONTROL_TOGGLE = "toggle"
    const val CONTROL_SCENE = "scene"
    const val CONTROL_CLIMATE = "climate"
    const val CONTROL_MEDIA = "media"

    /** Values of [COL_HA_STATE]; on an empty cursor the extras carry it under [COL_HA_STATE]. */
    const val HA_OK = "ok"
    const val HA_NOT_CONFIGURED = "not_configured"
    const val HA_UNREACHABLE = "unreachable"
    const val HA_AUTH = "auth"
    const val HA_LOADING = "loading"

    // /update: one row.
    const val COL_UPDATE_ENABLED = "update_enabled"
    const val COL_UPDATE_REPO = "repo"
    const val COL_UPDATE_LATEST = "latest"
    const val COL_UPDATE_AVAILABLE = "available"
    const val COL_UPDATE_CHECKED_MS = "checked_ms"
    const val COL_UPDATE_SHA256 = "sha256_published"
    val UPDATE_COLUMNS = arrayOf(
        COL_UPDATE_ENABLED, COL_UPDATE_REPO, COL_UPDATE_LATEST, COL_UPDATE_AVAILABLE, COL_UPDATE_CHECKED_MS,
        COL_UPDATE_SHA256,
    )

    // ---- call() methods on :link's provider (same permission check inside).

    const val METHOD_SET_ENABLED = "set_enabled"
    const val METHOD_SET_PAIRING_REQUIRED = "set_pairing_required"
    /** Opens a pairing window (10 minutes) with a fresh PIN; returns it in [EXTRA_PIN]. */
    const val METHOD_OPEN_PAIRING = "open_pairing"
    const val METHOD_CLOSE_PAIRING = "close_pairing"
    const val METHOD_FORGET_PHONES = "forget_phones"
    /** Fetches /api/states now (blocking, a few seconds at most); result in [EXTRA_STATE]. */
    const val METHOD_HA_REFRESH = "ha_refresh"
    /** arg = entity id; [EXTRA_ACTION] (+ [EXTRA_VALUE]); result in [EXTRA_OK]. */
    const val METHOD_HA_ACTION = "ha_action"
    /** arg = Home Assistant address; [EXTRA_VALUE] = token (absent keeps the saved one). Tests it; result in [EXTRA_STATE]. */
    const val METHOD_HA_CONFIGURE = "ha_configure"
    const val METHOD_HA_FORGET = "ha_forget"
    /** Checks the release feed when enabled and stale (arg "force" skips the day limit). */
    const val METHOD_UPDATE_CHECK = "update_check"

    const val EXTRA_OK = "ok"
    const val EXTRA_PIN = "pin"
    const val EXTRA_STATE = "state"
    const val EXTRA_ACTION = "action"
    const val EXTRA_VALUE = "value"
    const val EXTRA_MESSAGE = "message"

    /** Smart-home actions ([EXTRA_ACTION]). */
    const val ACTION_TOGGLE = "toggle"
    const val ACTION_ACTIVATE = "activate"
    const val ACTION_TEMP_UP = "temp_up"
    const val ACTION_TEMP_DOWN = "temp_down"
    const val ACTION_PLAY_PAUSE = "play_pause"

    // ---- :app's provider (read by :link for the encrypted backup)

    const val CONFIG_AUTHORITY = "dev.tvlauncher.linkconfig"
    const val URI_CONFIG = "content://$CONFIG_AUTHORITY"
    const val METHOD_EXPORT_CONFIG = "export_config"
    const val METHOD_IMPORT_CONFIG = "import_config"
    const val EXTRA_JSON = "json"

    // ---- Remote commands: :link -> :app (ordered broadcast, result code = [Outcome]).

    const val ACTION_COMMAND = "dev.tvlauncher.action.LINK_COMMAND"
    const val EXTRA_KIND = "kind"
    const val EXTRA_KEY = "key"
    const val EXTRA_TEXT = "text"
    const val EXTRA_URL = "url"
    const val EXTRA_PACKAGE = "package"

    const val KIND_KEY = "key"
    const val KIND_TEXT = "text"
    const val KIND_ENTER = "enter"
    const val KIND_OPEN_URL = "open_url"
    const val KIND_LAUNCH = "launch"
    /** Media transport through the launcher's Now Playing access; [EXTRA_ACTION] is one of [MEDIA_ACTIONS]. */
    const val KIND_MEDIA = "media"
    /** What is playing: the result extras carry the NP_* keys (empty when nothing plays). */
    const val KIND_NOW_PLAYING = "now_playing"
    /** The TV's power menu (through the Helper's existing power-dialog action). */
    const val KIND_POWER_MENU = "power_menu"
    /** A home-screen quick action; [EXTRA_ITEM] is one of [QUICK_ITEMS]. */
    const val KIND_QUICK = "quick"

    const val EXTRA_ITEM = "item"

    const val MEDIA_PLAY_PAUSE = "play_pause"
    const val MEDIA_NEXT = "next"
    const val MEDIA_PREV = "prev"
    const val MEDIA_FORWARD = "forward"
    const val MEDIA_REWIND = "rewind"
    val MEDIA_ACTIONS = setOf(MEDIA_PLAY_PAUSE, MEDIA_NEXT, MEDIA_PREV, MEDIA_FORWARD, MEDIA_REWIND)
    /** Seek step of [MEDIA_FORWARD] / [MEDIA_REWIND]. */
    const val SEEK_STEP_MS = 10_000L

    const val NP_TITLE = "np_title"
    const val NP_ARTIST = "np_artist"
    const val NP_APP = "np_app"
    const val NP_PLAYING = "np_playing"
    const val NP_POSITION_MS = "np_position_ms"
    const val NP_DURATION_MS = "np_duration_ms"
    /** False when the launcher has no notification access (Now Playing is off). */
    const val NP_ACCESS = "np_access"

    const val QUICK_SPOTLIGHT = "spotlight"
    const val QUICK_INPUTS = "inputs"
    const val QUICK_SETTINGS = "settings"
    const val QUICK_AMBIENT = "ambient"
    const val QUICK_FOCUS = "focus"
    const val QUICK_BEDTIME = "bedtime"
    val QUICK_ITEMS = listOf(QUICK_SPOTLIGHT, QUICK_INPUTS, QUICK_SETTINGS, QUICK_AMBIENT, QUICK_FOCUS, QUICK_BEDTIME)

    /** Starts HomeActivity with [EXTRA_ITEM] (internal: explicit component, launcher only). */
    const val ACTION_QUICK_HOME = "dev.tvlauncher.action.LINK_QUICK"

    // ---- Provisioning start of :link (adb shell only: guarded by WRITE_SECURE_SETTINGS, which
    // the shell holds and ordinary apps cannot).
    const val ACTION_PROVISION_START = "dev.tvlauncher.link.action.PROVISION_START"
    const val PROVISION_START_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    // ---- :app -> :helper (sent with the existing HELPER_ACTIONS signature permission).
    // Only "back" and "home" ([EXTRA_KEY]): the Helper's global actions, nothing that reads screens.

    const val ACTION_HELPER_INPUT = "dev.tvlauncher.helper.action.LINK_INPUT"
    const val HELPER_PERMISSION = "dev.tvlauncher.permission.HELPER_ACTIONS"

    /** Result codes of the command broadcast. */
    object Outcome {
        /** Nobody answered (launcher missing or too slow). */
        const val NONE = 0
        /** Done by the launcher itself (home screen or its Settings in front). */
        const val LAUNCHER = 1
        /** Handed to the Helper's accessibility service (another app in front). */
        const val HELPER = 2
        /** Another app is in front and the Helper is off: nothing could be done. */
        const val NEEDS_HELPER = 3
        /** Rejected (invalid command, app missing, link not allowed). */
        const val FAILED = 4

        fun name(code: Int): String = when (code) {
            LAUNCHER -> "launcher"
            HELPER -> "helper"
            NEEDS_HELPER -> "needs_helper"
            FAILED -> "failed"
            else -> "none"
        }
    }

    /** The remote's keys (names used on the wire, in [EXTRA_KEY] and by the phone page). */
    enum class Key(val wire: String, val keyCode: Int) {
        UP("up", 19),
        DOWN("down", 20),
        LEFT("left", 21),
        RIGHT("right", 22),
        OK("ok", 23),
        BACK("back", 4),
        HOME("home", 3),
        MENU("menu", 82),
        PLAY_PAUSE("play_pause", 85),
        /** Backspace in the focused text box (live typing from the phone). */
        DEL("del", 67),
        /** OK held down: a tile's options. */
        OK_LONG("ok_long", 23);

        companion object {
            fun fromWire(s: String?): Key? = values().firstOrNull { it.wire == s }
        }
    }

    /** Text typed from the phone: at most this many characters per message. */
    const val MAX_TEXT = 500
    const val MAX_URL = 2048

    private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

    fun isValidPackage(s: String?): Boolean = s != null && s.length <= 200 && PACKAGE.matches(s)

    /**
     * A link the phone may open on the TV: http or https only, no credentials, printable ASCII
     * without spaces, at most [MAX_URL] characters. Anything else (javascript:, intent:, file:,
     * content:) is refused.
     */
    fun isOpenableUrl(s: String?): Boolean {
        if (s == null || s.isEmpty() || s.length > MAX_URL) return false
        if (s.any { it.code <= 0x20 || it.code >= 0x7f }) return false
        val lower = s.lowercase()
        val rest = when {
            lower.startsWith("https://") -> s.substring(8)
            lower.startsWith("http://") -> s.substring(7)
            else -> return false
        }
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        return authority.isNotEmpty() && '@' !in authority && '\\' !in rest
    }

    /** Seek target: clamped to 0..duration (duration <= 0 = unknown, then only the lower bound). */
    fun seekTarget(positionMs: Long, durationMs: Long, forward: Boolean): Long {
        val t = positionMs + if (forward) SEEK_STEP_MS else -SEEK_STEP_MS
        val upper = if (durationMs > 0) durationMs else Long.MAX_VALUE
        return t.coerceIn(0L, upper)
    }

    /** Removes control characters (keeps newlines out too) and caps the length. */
    fun cleanText(s: String?): String =
        (s ?: "").filter { it == ' ' || !it.isISOControl() }.take(MAX_TEXT)
}
