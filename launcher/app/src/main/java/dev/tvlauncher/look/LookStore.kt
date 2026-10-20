package dev.tvlauncher.look

import android.content.Context
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.system.SystemPrefs
import dev.tvlauncher.ui.GlassBackdrop
import java.io.File

/**
 * Where the look lives: next to the rest of Appearance in "appearance_settings" (a per-profile
 * store, so each person has their own look, and part of every configuration backup). Glass style
 * and accent stay in [AppearanceSettings]; the other look values and the saved looks are two extra
 * keys of the same file. Text size is the household Accessibility setting ([SystemPrefs]).
 */
class LookStore(context: Context) {
    private val app = context.applicationContext ?: context
    private val sp = app.getSharedPreferences(AppearancePrefs.FILE, Context.MODE_PRIVATE)
    private val appearance = AppearancePrefs(app)

    /** The current look, glass style and accent included. */
    fun load(): Look {
        val a = appearance.load()
        val l = LookCodec.currentOf(sp.getString(KEY_LOOK, null))
        return l.copy(
            glassStyle = a.glassStyle,
            accent = if (a.accentMode == AccentMode.FIXED) a.fixedAccent else null,
            textSize = SystemPrefs(app).textSize
        )
    }

    /** Saves [look]; null glass style/accent/text size leave those settings as they are. */
    fun save(look: Look) {
        sp.edit().putString(KEY_LOOK, LookCodec.encode(look.copy(glassStyle = null, accent = null, textSize = null))).apply()
        if (look.glassStyle != null || look.accent != null) appearance.update { a ->
            var n = a
            look.glassStyle?.let { n = n.copy(glassStyle = it) }
            look.accent?.let { n = n.copy(accentMode = AccentMode.FIXED, fixedAccent = it) }
            n
        }
        look.textSize?.let { SystemPrefs(app).textSize = it }
    }

    fun update(change: (Look) -> Look): Look = change(load()).also { save(it) }

    fun saved(): List<NamedLook> = LookCodec.decodeSaved(sp.getString(KEY_SAVED, null))

    fun setSaved(list: List<NamedLook>) {
        sp.edit().putString(KEY_SAVED, LookCodec.encodeSaved(list)).apply()
    }

    /** Pushes the glass values of the saved look to every glass surface (live, no restart). */
    fun applyGlass(a: AppearanceSettings = appearance.load()) {
        val l = LookCodec.currentOf(sp.getString(KEY_LOOK, null))
        val accent = if (a.accentMode == AccentMode.FIXED) a.fixedAccent else AppearanceSettings.DEFAULT_ACCENT
        GlassBackdrop.setLook(l.amount, l.corners, l.tint, accent)
    }

    companion object {
        const val KEY_LOOK = "look_json"
        const val KEY_SAVED = "saved_looks"
        const val FOLDER = "H02 Looks"
        /** Folder name used by the short-lived M02 builds: still read, never written. */
        const val LEGACY_FOLDER = "M02 Looks" // legacy name
    }
}

/** Look files on a USB stick or in Pictures/H02 Looks. Off the main thread; never throws. */
object LookFiles {
    /** Folders to write to / read from, most specific first: USB sticks, then this TV's Pictures. */
    @Suppress("DEPRECATION")
    fun folders(context: Context): List<File> {
        val app = try { context.getExternalFilesDirs(null).filterNotNull() } catch (e: Exception) { emptyList() }
        val usb = app.drop(1).mapNotNull { it.parentFile?.parentFile?.parentFile?.parentFile }.map { File(it, LookStore.FOLDER) }
        val pictures = try {
            File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), LookStore.FOLDER)
        } catch (e: Exception) { null }
        return usb + app.drop(1).map { File(it, LookStore.FOLDER) } + listOfNotNull(pictures) + app.take(1).map { File(it, LookStore.FOLDER) }
    }

    /** Writes [look] as "<name>.json"; the file, or null when no folder was writable. */
    fun export(context: Context, name: String, look: Look): File? {
        val text = LookCodec.encode(look, name)
        val file = LookFileName.of(name)
        for (dir in folders(context)) {
            try {
                dir.mkdirs()
                val f = File(dir, file)
                f.writeText(text)
                return f
            } catch (e: Exception) {
                // next folder
            }
        }
        return null
    }

    /** Every readable look file (newest first), parsed and validated; also from the pre-rename folders. */
    fun list(context: Context): List<NamedLook> = try {
        folders(context).flatMap { d -> listOfNotNull(d, d.parentFile?.let { File(it, LookStore.LEGACY_FOLDER) }) }
            .flatMap { d -> (try { d.listFiles() } catch (e: Exception) { null })?.toList() ?: emptyList() }
            .filter { it.isFile && it.name.endsWith(".json") && it.length() in 1..LookCodec.MAX_BYTES.toLong() }
            .sortedByDescending { it.lastModified() }
            .mapNotNull { f -> try { LookCodec.decode(f.readText())?.let { n -> if (n.name.isEmpty()) n.copy(name = f.nameWithoutExtension.take(LookCodec.MAX_NAME)) else n } } catch (e: Exception) { null } }
            .distinctBy { it.name.lowercase() }
            .take(12)
    } catch (e: Exception) {
        emptyList()
    }
}

/** Safe file names for exported looks (pure). */
object LookFileName {
    fun of(name: String): String {
        val n = LookCodec.cleanName(name).map { if (it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_') it else '_' }
            .joinToString("").trim()
        return (n.ifEmpty { "Look" }) + ".json"
    }
}
