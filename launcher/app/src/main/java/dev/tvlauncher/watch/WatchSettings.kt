package dev.tvlauncher.watch

import org.json.JSONArray
import org.json.JSONObject

/** Which cards the Continue watching row shows. */
enum class WatchScope(val label: String) {
    FOCUSED_APP("Focused app"),
    ALL_APPS("All apps in one row")
}

/**
 * Settings > Home > Continue watching, saved as one JSON document ([WatchPrefs]).
 * [blocked] holds [WatchProgram.key]s hidden with a long press (at most [MAX_BLOCKED], newest last).
 */
data class WatchSettings(
    val showRow: Boolean = true,
    val scope: WatchScope = WatchScope.FOCUSED_APP,
    val longPressHides: Boolean = true,
    val includeSuggestions: Boolean = false,
    val showPosters: Boolean = true,
    val blocked: List<String> = emptyList()
) {
    fun toJson(): String = JSONObject()
        .put("v", 1)
        .put("showRow", showRow)
        .put("scope", scope.name)
        .put("longPressHides", longPressHides)
        .put("includeSuggestions", includeSuggestions)
        .put("showPosters", showPosters)
        .put("blocked", JSONArray(blocked))
        .toString()

    fun block(key: String): WatchSettings = copy(blocked = Blocklist.add(blocked, key))

    fun unblockAll(): WatchSettings = copy(blocked = emptyList())

    companion object {
        const val MAX_BLOCKED = 200
        val DEFAULT = WatchSettings()

        /** Tolerant reader: broken JSON gives [DEFAULT]; each field falls back on its own. */
        fun fromJson(s: String?): WatchSettings {
            if (s.isNullOrBlank()) return DEFAULT
            val o = try { JSONObject(s) } catch (e: Exception) { return DEFAULT }
            val d = DEFAULT
            val arr = o.optJSONArray("blocked")
            val blocked = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                (arr.opt(i) as? String)?.takeIf { it.isNotBlank() }
            }
            return WatchSettings(
                showRow = o.opt("showRow") as? Boolean ?: d.showRow,
                scope = WatchScope.values().firstOrNull { it.name == o.optString("scope") } ?: d.scope,
                longPressHides = o.opt("longPressHides") as? Boolean ?: d.longPressHides,
                includeSuggestions = o.opt("includeSuggestions") as? Boolean ?: d.includeSuggestions,
                showPosters = o.opt("showPosters") as? Boolean ?: d.showPosters,
                blocked = Blocklist.normalize(blocked)
            )
        }
    }
}

/** The hidden-entries list: unique keys, oldest dropped first past [WatchSettings.MAX_BLOCKED]. */
object Blocklist {
    fun add(list: List<String>, key: String): List<String> {
        if (key.isBlank()) return list
        return normalize(list.filter { it != key } + key)
    }

    fun normalize(list: List<String>): List<String> {
        val unique = LinkedHashSet<String>()
        // Later duplicates win their place (most recently hidden last).
        for (k in list.asReversed()) if (k.isNotBlank()) unique.add(k)
        return unique.toList().asReversed().takeLast(WatchSettings.MAX_BLOCKED)
    }
}

/** What the system TV list allows the launcher to read on this TV. */
enum class WatchAccess {
    /** Read works and at least one app published something. */
    OK,
    /** Read works but no app has published anything yet (or every entry is hidden). */
    EMPTY,
    /** The TV's permission for program information has not been given to the launcher. */
    NEEDS_PERMISSION,
    /** This TV has no TV program list (older Android, or the provider is missing or refused us). */
    UNAVAILABLE;

    companion object {
        /** [sdk] < 26 has no Watch Next table; a provider that throws or returns no cursor is unavailable. */
        fun resolve(sdk: Int, providerPresent: Boolean, permissionGranted: Boolean, queryOk: Boolean, rows: Int): WatchAccess = when {
            sdk < 26 || !providerPresent -> UNAVAILABLE
            !permissionGranted -> NEEDS_PERMISSION
            !queryOk -> UNAVAILABLE
            rows == 0 -> EMPTY
            else -> OK
        }
    }
}

/** Settings copy for [WatchAccess] (plain words, no instructions for a computer). */
object WatchCopy {
    fun accessValue(a: WatchAccess): String = when (a) {
        WatchAccess.OK -> "Working"
        WatchAccess.EMPTY -> "Nothing yet"
        WatchAccess.NEEDS_PERMISSION -> "Allow"
        WatchAccess.UNAVAILABLE -> "Not on this TV"
    }

    fun accessSubtitle(a: WatchAccess): String = when (a) {
        WatchAccess.OK -> "Apps share what you watched; it stays on this TV"
        WatchAccess.EMPTY -> "Apps add titles here after you watch something in them"
        WatchAccess.NEEDS_PERMISSION -> "OK asks the TV to let Home read what apps share"
        WatchAccess.UNAVAILABLE -> "This TV doesn't let Home read what apps share"
    }

    fun hiddenValue(n: Int): String = if (n == 0) "None" else n.toString()
}
