package dev.tvlauncher.homesections

import org.json.JSONArray
import org.json.JSONObject

/**
 * What one block of the home screen shows (Settings > Home layout > Home sections). Only types
 * with [drawn] are drawn on Home today, listed in Settings and used by presets; the rest stay in
 * the model and JSON for later and always load turned off.
 */
enum class SectionType(val label: String, val appRow: Boolean) {
    APPS("All apps", true),
    FAVORITES("Favorites", true),
    RECENT("Recent apps", true),
    CONTINUE("Continue watching", false),
    NOW_PLAYING("Now playing", false),
    WEATHER("Weather", false),
    SMART_HOME("Smart home", false),
    QUICK_ACTIONS("Quick actions", false),
    GROUP("Group", true),
    DISCOVER("Get more apps", false),
    CLOCK("Clock and date", false);

    val drawn: Boolean get() = appRow
}

/** How a section lays out its items. */
enum class SectionStyle(val label: String) {
    ROW("Row"), TWO_ROWS("Two rows"), GRID("Grid"), BIG_CARDS("Big cards"), COMPACT("Compact icons")
}

enum class SectionSize(val label: String, val scale: Float) { S("Small", 0.8f), M("Medium", 1f), L("Large", 1.2f) }

/**
 * One home section. [id] is the type name for built-ins and "group.N" for a custom group, whose
 * [name] and [apps] (package names, in order) belong to the owner.
 */
data class HomeSection(
    val id: String,
    val type: SectionType,
    val enabled: Boolean = false,
    val style: SectionStyle = SectionStyle.ROW,
    val size: SectionSize = SectionSize.M,
    val showTitle: Boolean = true,
    val maxItems: Int = DEFAULT_MAX,
    val name: String = "",
    val apps: List<String> = emptyList()
) {
    /** The title shown above the section (a group's own name). */
    val title: String get() = if (type == SectionType.GROUP) name else type.label

    companion object {
        const val MIN_MAX = 1
        const val MAX_MAX = 50
        const val DEFAULT_MAX = 20
    }
}

/** The named starting points the Home sections page offers. */
enum class SectionPreset(val label: String) {
    CLASSIC("Classic"), FAVORITES_APPS("Favorites + Apps"), FAVORITES_RECENT("Favorites + Recent"),
    /** Favorites, Recent apps, Apps and every custom group. */
    EVERYTHING("Everything")
}

/**
 * The ordered list of home sections. Pure Kotlin + org.json (JVM-tested). Every built-in type is
 * always present exactly once (order + enabled flag); custom groups (at most [MAX_GROUPS]) follow
 * their own order. At least one section is always enabled: an empty home falls back to Apps.
 * [CLASSIC] (only Apps) is the default and what an install without the store gets, so the home
 * stays the single row it was.
 */
data class HomeSections(val sections: List<HomeSection>, val preset: SectionPreset? = SectionPreset.CLASSIC) {

    val enabled: List<HomeSection> get() = sections.filter { it.enabled }
    val groups: List<HomeSection> get() = sections.filter { it.type == SectionType.GROUP }

    /** Only the Apps section is on: the home is today's single row. */
    val isClassic: Boolean get() = enabled.size == 1 && enabled[0].type == SectionType.APPS

    /** The section that carries the hero text (the first enabled one). */
    val focusRow: HomeSection get() = enabled.first()

    fun byId(id: String): HomeSection? = sections.firstOrNull { it.id == id }

    private fun replace(id: String, f: (HomeSection) -> HomeSection): HomeSections =
        copy(sections = sections.map { if (it.id == id) f(it) else it }, preset = null).normalized()

    fun setEnabled(id: String, on: Boolean) = replace(id) { it.copy(enabled = on) }
    fun toggle(id: String) = replace(id) { it.copy(enabled = !it.enabled) }
    fun setStyle(id: String, s: SectionStyle) = replace(id) { it.copy(style = s) }
    fun setSize(id: String, s: SectionSize) = replace(id) { it.copy(size = s) }
    fun setShowTitle(id: String, on: Boolean) = replace(id) { it.copy(showTitle = on) }
    fun setMaxItems(id: String, n: Int) = replace(id) { it.copy(maxItems = n) }

    /** Moves section [id] by [delta] places (clamped to the list). */
    fun move(id: String, delta: Int): HomeSections {
        val from = sections.indexOfFirst { it.id == id }
        if (from < 0 || delta == 0) return this
        val to = (from + delta).coerceIn(0, sections.size - 1)
        if (to == from) return this
        val list = sections.toMutableList()
        val s = list.removeAt(from)
        list.add(to, s)
        return copy(sections = list, preset = null)
    }

    /** A new custom group (enabled, at the end), or null when [MAX_GROUPS] exist or the name is blank. */
    fun addGroup(name: String, apps: List<String> = emptyList()): HomeSections? {
        val clean = cleanName(name) ?: return null
        if (groups.size >= MAX_GROUPS) return null
        val used = groups.map { it.id }.toSet()
        val id = (1..MAX_GROUPS + groups.size + 1).map { "$GROUP_PREFIX$it" }.first { it !in used }
        val g = HomeSection(id, SectionType.GROUP, enabled = true, name = clean, apps = cleanApps(apps))
        return copy(sections = sections + g, preset = null).normalized()
    }

    fun renameGroup(id: String, name: String): HomeSections? {
        val clean = cleanName(name) ?: return null
        if (byId(id)?.type != SectionType.GROUP) return null
        return replace(id) { it.copy(name = clean) }
    }

    /** Adds or removes [pkg] in group [id]. */
    fun toggleGroupApp(id: String, pkg: String): HomeSections = replace(id) { g ->
        if (g.type != SectionType.GROUP) g
        else g.copy(apps = if (pkg in g.apps) g.apps - pkg else cleanApps(g.apps + pkg))
    }

    fun deleteGroup(id: String): HomeSections =
        if (byId(id)?.type != SectionType.GROUP) this
        else copy(sections = sections.filter { it.id != id }, preset = null).normalized()

    /** Which preset these sections match (by enabled types and order), or null for a custom mix. */
    fun matchingPreset(): SectionPreset? = SectionPreset.values().firstOrNull { p ->
        presetTypes(p) == enabled.filter { it.type != SectionType.GROUP }.map { it.type } &&
            (if (p == SectionPreset.EVERYTHING) groups.all { it.enabled } else groups.none { it.enabled })
    }

    /** Ensures every built-in exists once, groups are valid and capped, and something is on. */
    fun normalized(): HomeSections {
        val seen = HashSet<String>()
        val out = ArrayList<HomeSection>()
        var groupCount = 0
        for (s in sections) {
            if (!seen.add(s.id)) continue
            if (s.type == SectionType.GROUP) {
                if (!s.id.startsWith(GROUP_PREFIX) || groupCount >= MAX_GROUPS) continue
                val n = cleanName(s.name) ?: continue
                groupCount++
                out += clamp(s).copy(name = n, apps = cleanApps(s.apps))
            } else {
                if (s.id != s.type.name) continue
                // A type Home does not draw yet loads turned off (forward-compatible JSON).
                val base = if (s.type == SectionType.APPS) HomeSection(s.id, s.type, enabled = s.enabled) else clamp(s)
                out += base.copy(name = "", apps = emptyList(), enabled = s.enabled && s.type.drawn)
            }
        }
        for (t in SectionType.values()) {
            if (t != SectionType.GROUP && out.none { it.type == t }) out += HomeSection(t.name, t)
        }
        if (out.none { it.enabled }) {
            val i = out.indexOfFirst { it.type == SectionType.APPS }
            out[i] = out[i].copy(enabled = true)
        }
        return HomeSections(out, preset)
    }

    fun toJson(): String {
        val arr = JSONArray()
        for (s in sections) {
            val o = JSONObject()
                .put("id", s.id).put("type", s.type.name).put("enabled", s.enabled)
                .put("style", s.style.name).put("size", s.size.name).put("title", s.showTitle)
                .put("max", s.maxItems)
            if (s.type == SectionType.GROUP) o.put("name", s.name).put("apps", JSONArray(s.apps))
            arr.put(o)
        }
        return JSONObject().put("version", VERSION).put("preset", preset?.name ?: JSONObject.NULL)
            .put("sections", arr).toString()
    }

    companion object {
        const val VERSION = 1
        const val MAX_GROUPS = 4
        const val MAX_GROUP_APPS = 50
        const val MAX_NAME = 24
        const val GROUP_PREFIX = "group."
        private const val MAX_SECTIONS = 64
        private val PACKAGE = Regex("^[A-Za-z0-9_.]{1,255}$")

        val CLASSIC: HomeSections = preset(SectionPreset.CLASSIC)

        /** The enabled types of [p], top to bottom. */
        fun presetTypes(p: SectionPreset): List<SectionType> = when (p) {
            SectionPreset.CLASSIC -> listOf(SectionType.APPS)
            SectionPreset.FAVORITES_APPS -> listOf(SectionType.FAVORITES, SectionType.APPS)
            SectionPreset.FAVORITES_RECENT -> listOf(SectionType.FAVORITES, SectionType.RECENT, SectionType.APPS)
            SectionPreset.EVERYTHING -> listOf(SectionType.FAVORITES, SectionType.RECENT, SectionType.APPS)
        }

        /** [p] applied to built-ins; [keepGroups] stay at the end (on for Everything, else off) so a preset never deletes them. */
        fun preset(p: SectionPreset, keepGroups: List<HomeSection> = emptyList()): HomeSections {
            val on = presetTypes(p)
            val first = on.map { HomeSection(it.name, it, enabled = true) }
            val rest = SectionType.values().filter { it != SectionType.GROUP && it !in on }.map { HomeSection(it.name, it) }
            val list = first + rest
            val on2 = p == SectionPreset.EVERYTHING
            return HomeSections(list + keepGroups.map { it.copy(enabled = on2) }, p).normalized()
        }

        fun cleanName(raw: String?): String? {
            val s = raw?.filter { !it.isISOControl() }?.trim()?.take(MAX_NAME) ?: return null
            return s.ifBlank { null }
        }

        fun cleanApps(raw: List<String>): List<String> =
            raw.filter { PACKAGE.matches(it) }.distinct().take(MAX_GROUP_APPS)

        private fun clamp(s: HomeSection) = s.copy(maxItems = s.maxItems.coerceIn(HomeSection.MIN_MAX, HomeSection.MAX_MAX))

        /** Never throws: missing, corrupt or future data falls back to [CLASSIC] (or per field). */
        fun fromJson(text: String?): HomeSections {
            if (text.isNullOrBlank() || text.length > 64 * 1024) return CLASSIC
            return try {
                val json = JSONObject(text)
                val version = json.optInt("version", -1)
                if (version < 1 || version > VERSION) return CLASSIC
                val arr = json.optJSONArray("sections") ?: return CLASSIC
                val list = ArrayList<HomeSection>()
                for (i in 0 until minOf(arr.length(), MAX_SECTIONS)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val type = enumOf<SectionType>(o.opt("type")) ?: continue
                    val apps = o.optJSONArray("apps")?.let { a -> (0 until minOf(a.length(), MAX_GROUP_APPS * 2)).mapNotNull { a.opt(it) as? String } }
                    list += HomeSection(
                        id = o.opt("id") as? String ?: continue,
                        type = type,
                        enabled = o.opt("enabled") as? Boolean ?: false,
                        style = enumOf<SectionStyle>(o.opt("style")) ?: SectionStyle.ROW,
                        size = enumOf<SectionSize>(o.opt("size")) ?: SectionSize.M,
                        showTitle = o.opt("title") as? Boolean ?: true,
                        maxItems = (o.opt("max") as? Number)?.toInt() ?: HomeSection.DEFAULT_MAX,
                        name = o.opt("name") as? String ?: "",
                        apps = apps ?: emptyList()
                    )
                }
                if (list.isEmpty()) return CLASSIC
                HomeSections(list, enumOf<SectionPreset>(json.opt("preset"))).normalized()
            } catch (e: Exception) {
                CLASSIC
            }
        }

        private inline fun <reified E : Enum<E>> enumOf(raw: Any?): E? {
            val s = raw as? String ?: return null
            return enumValues<E>().firstOrNull { it.name == s }
        }
    }
}
