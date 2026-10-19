package dev.tvlauncher.settings

import org.json.JSONObject
import java.io.File

/**
 * Launcher settings export/import as a JSON file. Pure (java.io + org.json); callers run it off
 * the main thread. A file that is unreadable, not a JSON object, or has none of the
 * [LauncherSettings] keys is rejected and the current settings are kept.
 */
object SettingsBackup {
    const val FILE_NAME = "launcher-settings.json"
    private val KNOWN_KEYS = listOf("favorites", "order", "hidden", "theme", "version")

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

    /** Validated parse: null unless [text] is a JSON object carrying at least one known key. */
    fun parse(text: String): LauncherSettings? {
        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        if (KNOWN_KEYS.none { json.has(it) }) return null
        return LauncherSettings.fromJson(text)
    }

    private const val MAX_BYTES = 512 * 1024L
}
