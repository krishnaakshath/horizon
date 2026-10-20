package dev.tvlauncher.profiles

import android.content.Context
import android.content.SharedPreferences

/**
 * Which preference files belong to a person, and where each profile keeps its copy.
 *
 * The launcher's [LauncherApp][dev.tvlauncher.LauncherApp] routes every `getSharedPreferences`
 * call through [route]: a [SCOPED] file name is swapped for the active profile's copy, so every
 * existing store (Prefs, AppearancePrefs, RoomPrefs, WallpaperStore, HomeLayoutPrefs, ...) reads and
 * writes the right person's values without knowing profiles exist. The "Home" profile keeps the
 * original file names, so the single-profile state of an existing install simply *is* Home: no
 * copy, no migration step that could lose data, and a downgrade still finds everything.
 *
 * Everything not listed (crash guard, setup, speed, sounds, Spotlight keys, controllers, the
 * parental PIN, routines and the profile list itself) is shared by the whole household.
 */
object ProfileStores {
    /** The profile list, the active id and "ask on start" (never routed). */
    const val REGISTRY = "profiles"
    private const val SEP = "__"

    /** Per-person files: favorites/order/hidden apps, look, home extras, room lighting, wallpaper and ambient, Now Playing, home layout, ambient widgets. */
    val SCOPED: Set<String> = linkedSetOf(
        "launcher_settings", "appearance_settings", "home_extras", "room_settings",
        "wallpaper_settings", "discover_settings", "home_layout", "ambient_widgets", "home_sections"
    )

    /** The active profile id; read by [route] on every call (set by [ProfileManager]). */
    @Volatile var activeId: String = ProfileModel.HOME_ID

    /** The file that holds [store] for profile [id]. Home keeps the original name. */
    fun physical(store: String, id: String): String =
        if (id == ProfileModel.HOME_ID || store !in SCOPED) store else "$store$SEP$id"

    /** The file a plain `getSharedPreferences(name)` should open right now. */
    fun route(name: String, active: String = activeId): String = physical(name, active)

    /** Every per-profile file of [id] (Home: the original names). */
    fun filesOf(id: String): List<String> = SCOPED.map { physical(it, id) }

    /** (store, profile id) when [name] is a non-Home profile copy such as "room_settings__p4k2m9a"; else null. */
    fun parseCopy(name: String): Pair<String, String>? {
        val i = name.lastIndexOf(SEP)
        if (i <= 0) return null
        val store = name.substring(0, i)
        val id = name.substring(i + SEP.length)
        if (store !in SCOPED || !ProfileModel.isValidId(id) || id == ProfileModel.HOME_ID) return null
        return store to id
    }

    /** Opens [name] exactly as written (no routing): for backup, copy and delete. */
    fun raw(context: Context, name: String): SharedPreferences {
        val app = context.applicationContext ?: context
        return (app as? RawPrefs)?.rawSharedPreferences(name) ?: app.getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    /** Implemented by the Application, whose `getSharedPreferences` routes. */
    interface RawPrefs {
        fun rawSharedPreferences(name: String): SharedPreferences
    }

    /** Replaces everything in [to] with the values of [from] (synchronous commit). */
    @Suppress("UNCHECKED_CAST")
    fun copy(from: SharedPreferences, to: SharedPreferences): Boolean = try {
        val e = to.edit().clear()
        for ((k, v) in from.all) when (v) {
            is String -> e.putString(k, v)
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is Set<*> -> e.putStringSet(k, v as Set<String>)
        }
        e.commit()
    } catch (e: Exception) {
        false
    }
}
