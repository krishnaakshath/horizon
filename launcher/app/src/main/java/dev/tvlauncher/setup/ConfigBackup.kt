package dev.tvlauncher.setup

import android.content.Context
import dev.tvlauncher.settings.SettingsBackup
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The whole launcher configuration as one JSON file: favorites/order/hidden apps, appearance,
 * home layout style, home screen extras, room lighting, wallpaper and ambient, sounds and Discover. Pure core
 * ([encode]/[decode]); [ConfigBackupIo] does the files. Import is strict: an unknown store, a
 * wrong type or an invalid launcher list rejects the whole file and nothing changes.
 */
object ConfigBackup {
    const val FILE_NAME = "tvlauncher-config.json"
    const val FOLDER = "TVLauncher"
    const val FORMAT = "tvlauncher-config"
    const val VERSION = 1
    const val MAX_BYTES = 512 * 1024L
    private const val MAX_STRING = 128 * 1024

    /** The stores that make up "the configuration" (crash, setup and UI state are not included). */
    val STORES = listOf(
        "launcher_settings", "appearance_settings", "home_extras", "room_settings",
        "wallpaper_settings", "sound_settings", "discover_settings", "home_layout"
    )

    fun encode(stores: Map<String, Map<String, Any?>>): String {
        val root = JSONObject().put("format", FORMAT).put("version", VERSION)
        val all = JSONObject()
        for ((name, values) in stores) {
            if (name !in STORES) continue
            val o = JSONObject()
            for ((k, v) in values) {
                val (t, jv) = when (v) {
                    is String -> "s" to v
                    is Boolean -> "b" to v
                    is Int -> "i" to v
                    is Long -> "l" to v
                    is Float -> "f" to v.toDouble()
                    is Set<*> -> "ss" to JSONArray(v.filterIsInstance<String>().sorted())
                    else -> continue
                }
                o.put(k, JSONObject().put("t", t).put("v", jv))
            }
            all.put(name, o)
        }
        return root.put("stores", all).toString(2)
    }

    /** The stores of a fully valid file, or null. */
    fun decode(text: String): Map<String, Map<String, Any>>? {
        if (text.length > MAX_BYTES) return null
        val root = try { JSONObject(text) } catch (e: Exception) { return null }
        if (root.optString("format") != FORMAT || root.optInt("version", -1) !in 1..VERSION) return null
        val stores = root.optJSONObject("stores") ?: return null
        val out = LinkedHashMap<String, Map<String, Any>>()
        for (name in stores.keys()) {
            if (name !in STORES) return null
            val o = stores.optJSONObject(name) ?: return null
            val values = LinkedHashMap<String, Any>()
            for (k in o.keys()) {
                val e = o.optJSONObject(k) ?: return null
                val v = e.opt("v")
                values[k] = when (e.optString("t")) {
                    "s" -> (v as? String)?.takeIf { it.length <= MAX_STRING } ?: return null
                    "b" -> v as? Boolean ?: return null
                    "i" -> (v as? Int) ?: return null
                    "l" -> (v as? Number)?.toLong() ?: return null
                    "f" -> (v as? Number)?.toFloat()?.takeIf { it.isFinite() } ?: return null
                    "ss" -> (v as? JSONArray)?.let { a -> (0 until a.length()).map { a.opt(it) as? String ?: return null }.toSet() }
                        ?: return null
                    else -> return null
                }
            }
            out[name] = values
        }
        // The app lists must pass the same strict check as the older launcher-only backup.
        val launcher = out["launcher_settings"]?.get("settings_json") as? String
        if (launcher != null && SettingsBackup.parse(launcher) == null) return null
        return out
    }

    /** Where to look for a backup, most specific first: USB sticks, then this TV. Pure. */
    fun candidates(usbRoots: List<File>, appDirs: List<File>, sharedRoot: File?): List<File> =
        usbRoots.flatMap { listOf(File(File(it, FOLDER), FILE_NAME), File(it, FILE_NAME)) } +
            appDirs.map { File(it, FILE_NAME) } +
            listOfNotNull(sharedRoot).flatMap { r ->
                listOf("Pictures", "Documents", "Download").map { File(File(File(r, it), FOLDER), FILE_NAME) }
            }
}

/** File side of [ConfigBackup]. Run off the main thread; never throws. */
object ConfigBackupIo {
    private fun snapshot(context: Context): Map<String, Map<String, Any?>> =
        ConfigBackup.STORES.associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all }

    /** App-specific folders: on a USB stick these are writable and readable without any permission. */
    private fun appDirs(context: Context): List<File> = try {
        context.getExternalFilesDirs(null).filterNotNull()
    } catch (e: Exception) {
        emptyList()
    }

    private fun usbRoots(context: Context): List<File> = appDirs(context).drop(1).mapNotNull { d ->
        // <volume>/Android/data/<pkg>/files -> <volume>
        d.parentFile?.parentFile?.parentFile?.parentFile
    }

    @Suppress("DEPRECATION")
    private fun sharedRoot(): File? = try { android.os.Environment.getExternalStorageDirectory() } catch (e: Exception) { null }

    /** Newest readable backup, or null. */
    fun find(context: Context): File? = try {
        ConfigBackup.candidates(usbRoots(context), appDirs(context), sharedRoot())
            .filter { f -> try { f.isFile && f.canRead() } catch (e: Exception) { false } }
            .maxByOrNull { it.lastModified() }
    } catch (e: Exception) {
        null
    }

    /** Writes to a USB stick when one is plugged in, else to this TV. Returns the file, or null. */
    fun export(context: Context): File? {
        val text = try { ConfigBackup.encode(snapshot(context)) } catch (e: Exception) { return null }
        val dirs = appDirs(context)
        for (dir in dirs.drop(1) + dirs.take(1)) {
            try {
                dir.mkdirs()
                val f = File(dir, ConfigBackup.FILE_NAME)
                val tmp = File(dir, ConfigBackup.FILE_NAME + ".tmp")
                tmp.writeText(text)
                if (!tmp.renameTo(f)) { f.writeText(text); tmp.delete() }
                return f
            } catch (e: Exception) {
                // next volume
            }
        }
        return null
    }

    /** Applies a valid file (each store replaced as a whole); false when invalid or unreadable. */
    fun restore(context: Context, file: File): Boolean {
        val stores = try {
            if (!file.isFile || file.length() > ConfigBackup.MAX_BYTES) return false
            ConfigBackup.decode(file.readText())
        } catch (e: Exception) {
            null
        } ?: return false
        return try {
            for ((name, values) in stores) {
                val e = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                @Suppress("UNCHECKED_CAST")
                for ((k, v) in values) when (v) {
                    is String -> e.putString(k, v)
                    is Boolean -> e.putBoolean(k, v)
                    is Int -> e.putInt(k, v)
                    is Long -> e.putLong(k, v)
                    is Float -> e.putFloat(k, v)
                    is Set<*> -> e.putStringSet(k, v as Set<String>)
                }
                e.commit()
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
