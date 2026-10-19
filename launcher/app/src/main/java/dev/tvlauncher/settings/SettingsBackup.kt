package dev.tvlauncher.settings

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Launcher settings export/import as a JSON file. Pure (java.io + org.json); callers run it off
 * the main thread. Import is strict: anything but a fully valid file is rejected and the current
 * settings are kept, so a wrong-but-parseable file can never wipe favorites, order or hidden apps.
 */
object SettingsBackup {
    const val FILE_NAME = "launcher-settings.json"
    const val MAX_BYTES = 512 * 1024L
    const val MAX_ENTRIES = 2000
    const val MAX_PACKAGE_LENGTH = 255
    private val PACKAGE = Regex("^[A-Za-z0-9_.]+$")

    /** Writes [settings] to [file] via a temp file + rename. False on any I/O failure. */
    fun export(settings: LauncherSettings, file: File): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(JSONObject(settings.toJson()).toString(2))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
        true
    } catch (e: Exception) {
        false
    }

    /** Settings read from [file], or [current] when the file is missing or invalid. */
    fun import(file: File, current: LauncherSettings): LauncherSettings = importOrNull(file) ?: current

    /** Settings read from [file], or null when it is missing, too large or invalid. */
    fun importOrNull(file: File): LauncherSettings? {
        val text = try {
            if (!file.isFile || file.length() > MAX_BYTES) return null
            file.readText()
        } catch (e: Exception) {
            return null
        }
        return parse(text)
    }

    /**
     * Strict parse; null unless [text] is a JSON object where `favorites`, `order` and `hidden` are
     * all present as arrays of at most [MAX_ENTRIES] package names (non-empty, `[A-Za-z0-9_.]`,
     * at most [MAX_PACKAGE_LENGTH] chars), `version` (optional) is an int and `theme` (optional)
     * is a string. Unknown fields are ignored.
     */
    fun parse(text: String): LauncherSettings? {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        val favorites = packages(json, "favorites") ?: return null
        val order = packages(json, "order") ?: return null
        val hidden = packages(json, "hidden") ?: return null
        val version = when (val v = json.opt("version")) {
            null -> LauncherSettings().version
            is Int -> v
            else -> return null
        }
        val theme = when (val t = json.opt("theme")) {
            null -> LauncherSettings().theme
            is String -> t
            else -> return null
        }
        return LauncherSettings(version = version, favorites = favorites, order = order, hidden = hidden.toSet(), theme = theme)
    }

    private fun packages(json: JSONObject, key: String): List<String>? {
        val arr = json.opt(key) as? JSONArray ?: return null
        if (arr.length() > MAX_ENTRIES) return null
        val out = ArrayList<String>(arr.length())
        for (i in 0 until arr.length()) {
            val s = arr.opt(i) as? String ?: return null
            if (s.isEmpty() || s.length > MAX_PACKAGE_LENGTH || !PACKAGE.matches(s)) return null
            out.add(s)
        }
        return out
    }
}
