package dev.tvlauncher.input

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri

/**
 * Where the controller settings come from. In the launcher process they are read straight from
 * its preferences ([save] writes them and tells the companion apps); anywhere else (browser,
 * store, weather) they are read through the launcher's read-only provider, on a background
 * thread at [refresh], and the defaults apply until (or unless) that works.
 */
class GamepadSettingsSource private constructor(context: Context) {
    private val app = context.applicationContext
    private val local = app.packageName == LAUNCHER_PACKAGE
    private val prefs: SharedPreferences? = if (local) app.getSharedPreferences(PREFS, Context.MODE_PRIVATE) else null
    @Volatile private var remote = GamepadSettings()
    @Volatile private var cachedRaw: String? = null
    @Volatile private var cached = GamepadSettings()

    fun current(): GamepadSettings {
        val p = prefs ?: return remote
        val raw = try { p.getString(KEY, null) } catch (e: RuntimeException) { null }
        if (raw != cachedRaw) { cached = GamepadSettings.decode(raw); cachedRaw = raw }
        return cached
    }

    /** Launcher only: stores [s] and notifies provider observers (the helper). */
    fun save(s: GamepadSettings) {
        val p = prefs ?: return
        p.edit().putString(KEY, s.encode()).apply()
        try { app.contentResolver.notifyChange(URI, null) } catch (e: RuntimeException) { }
    }

    fun update(change: (GamepadSettings) -> GamepadSettings) = save(change(current()))

    /** Re-reads the launcher's settings (companion apps; no-op in the launcher). */
    fun refresh() {
        if (local) return
        Thread {
            readRemote(app)?.let { remote = it }
        }.apply { isDaemon = true; name = "pad-settings" }.start()
    }

    companion object {
        const val LAUNCHER_PACKAGE = "dev.tvlauncher"
        const val AUTHORITY = "dev.tvlauncher.gamepad"
        val URI: Uri = Uri.parse("content://$AUTHORITY/settings")
        const val COLUMN = "settings"
        const val PREFS = "gamepad"
        const val KEY = "settings"

        @Volatile private var instance: GamepadSettingsSource? = null

        fun get(context: Context): GamepadSettingsSource =
            instance ?: synchronized(this) { instance ?: GamepadSettingsSource(context).also { instance = it } }

        /** One provider read; null when the launcher is missing or refuses. */
        fun readRemote(context: Context): GamepadSettings? = try {
            context.contentResolver.query(URI, arrayOf(COLUMN), null, null, null)?.use { c ->
                if (c.moveToFirst()) GamepadSettings.decode(c.getString(0)) else null
            }
        } catch (t: Throwable) {
            null
        }
    }
}

/**
 * A screen that wants the raw controller input (Settings > Controllers > Test controller). While
 * [listener] is set, every controller key and motion goes to it first, untranslated; the remote
 * is never captured, so its BACK always leaves.
 */
object PadCapture {
    interface Listener {
        fun onPadKey(keyCode: Int, down: Boolean, deviceId: Int, deviceName: String?)
        fun onPadAxes(axes: PadAxes, deviceName: String?)
    }

    @Volatile var listener: Listener? = null
}

/** One controller motion sample for [PadCapture]. */
data class PadAxes(
    val lx: Float, val ly: Float, val rx: Float, val ry: Float,
    val lt: Float, val rt: Float, val hatX: Float, val hatY: Float,
)
