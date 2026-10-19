package dev.tvlauncher.spotlight

import dev.tvlauncher.ui.QuickActions.Item
import org.json.JSONArray
import org.json.JSONObject

/** What the Spotlight search field looks through. */
enum class SearchScope(val label: String) {
    APPS("Apps only"),
    ALL("Apps, settings and recent"),
    /** Apps, Continue watching, Settings, quick actions, popular apps, a calculator and web search (search package). */
    EVERYTHING("Everything")
}

/** Which key opens Spotlight on a quick double-press (that key's single press keeps its usual job). */
enum class DoublePress(val label: String) {
    OFF("Off"),
    MENU("Menu"),
    SEARCH("Search"),
    EXTRA("Extra key")
}

/**
 * Settings > Home > Spotlight, saved as one JSON document ([SpotlightPrefs]).
 *
 * [order] always holds every [Item] exactly once (the order the quick-actions row uses);
 * [hidden] are the ones left out. Defaults reproduce the fixed row from before this page existed:
 * the ten original actions in their old order, the newer ones hidden.
 */
data class SpotlightConfig(
    val order: List<Item> = DEFAULT_ORDER,
    val hidden: Set<Item> = DEFAULT_HIDDEN,
    val menuKey: Boolean = true,
    val searchKey: Boolean = true,
    /** One extra remote key (a KeyEvent keycode) recorded with "Press the key you want"; 0 = none. */
    val extraKey: Int = 0,
    val doublePress: DoublePress = DoublePress.OFF,
    val scope: SearchScope = SearchScope.APPS,
    val showHints: Boolean = true,
    val closeAfterAction: Boolean = true
) {
    /** Actions the row shows, in order (before availability: Browser and Get Apps need their app). */
    val shown: List<Item> get() = order.filter { it !in hidden }

    fun toJson(): String = JSONObject()
        .put("v", VERSION)
        .put("order", JSONArray(order.map { it.name }))
        .put("hidden", JSONArray(order.filter { it in hidden }.map { it.name }))
        .put("menuKey", menuKey)
        .put("searchKey", searchKey)
        .put("extraKey", extraKey)
        .put("doublePress", doublePress.name)
        .put("scope", scope.name)
        .put("showHints", showHints)
        .put("closeAfterAction", closeAfterAction)
        .toString()

    companion object {
        const val VERSION = 1

        val DEFAULT_ORDER: List<Item> = Item.values().toList()

        /** Added with this page; off until the owner turns them on. */
        val DEFAULT_HIDDEN: Set<Item> = setOf(
            Item.ACCESSIBILITY, Item.POWER, Item.SLEEP_TIMER, Item.RESTART, Item.GET_APPS, Item.DISCOVER,
            Item.RECENTS, Item.FOCUS
        )

        val DEFAULT = SpotlightConfig()

        /**
         * Tolerant reader: missing or broken JSON gives [DEFAULT]; unknown action names are
         * dropped, duplicates keep their first place, and actions the saved order does not
         * mention (added by a later build) are appended, hidden if they are hidden by default.
         * An extra key that is reserved (or has its own switch) is cleared.
         */
        fun fromJson(json: String?): SpotlightConfig {
            if (json.isNullOrBlank()) return DEFAULT
            val o = try {
                JSONObject(json)
            } catch (e: Exception) {
                return DEFAULT
            }
            val d = DEFAULT
            val saved = names(o.optJSONArray("order"))
            val order = LinkedHashSet<Item>()
            saved.forEach { n -> item(n)?.let { order.add(it) } }
            val added = DEFAULT_ORDER.filter { it !in order }
            order.addAll(added)
            val hidden = HashSet<Item>()
            names(o.optJSONArray("hidden")).forEach { n -> item(n)?.let { hidden.add(it) } }
            added.filterTo(hidden) { it in DEFAULT_HIDDEN }
            val extra = (o.opt("extraKey") as? Number)?.toInt()?.takeIf { TriggerKeys.check(it) == TriggerKeys.Check.OK } ?: 0
            var double = enumOr(o.optString("doublePress"), d.doublePress)
            if (double == DoublePress.EXTRA && extra == 0) double = DoublePress.OFF
            return SpotlightConfig(
                order = order.toList(),
                hidden = hidden,
                menuKey = o.opt("menuKey") as? Boolean ?: d.menuKey,
                searchKey = o.opt("searchKey") as? Boolean ?: d.searchKey,
                extraKey = extra,
                doublePress = double,
                scope = enumOr(o.optString("scope"), d.scope),
                showHints = o.opt("showHints") as? Boolean ?: d.showHints,
                closeAfterAction = o.opt("closeAfterAction") as? Boolean ?: d.closeAfterAction
            )
        }

        private fun names(a: JSONArray?): List<String> =
            if (a == null) emptyList() else (0 until a.length()).map { a.optString(it, "") }

        private fun item(name: String): Item? = Item.values().firstOrNull { it.name == name }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback
    }
}
