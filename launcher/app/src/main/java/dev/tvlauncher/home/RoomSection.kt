package dev.tvlauncher.home

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.room.RoomChoice
import dev.tvlauncher.room.RoomChoiceCycler
import dev.tvlauncher.room.RoomPrefs
import dev.tvlauncher.room.RoomSettings
import dev.tvlauncher.room.RoomSettingsRows

/**
 * Pure model of Settings > Room lighting, on top of [RoomSettingsRows]: the page's [SPEC] (four
 * primary rows, the Auto schedule in Advanced) and the option lists / indices its rows show.
 */
object RoomPage {
    const val ID = "room_lighting"
    const val STEP_MIN = 15
    private const val NOT_SET = "Not set"

    val PRIMARY = listOf("room.choice", "room.location", "room.transitionSeconds", "room.boostTextInBright")
    val ADVANCED = listOf("room.dayStart", "room.eveningStart", "room.nightStart")
    val SPEC = PageSpec(ID, PRIMARY, ADVANCED)

    /** Model key ([RoomSettingsRows]) behind a page key. */
    fun modelKey(pageKey: String): String = pageKey.removePrefix("room.")

    fun choices(): List<RoomChoice> = RoomChoice.values().toList()
    fun choiceLabels(): List<String> = choices().map { RoomChoiceCycler.label(it) }
    fun choiceIndex(s: RoomSettings): Int = choices().indexOf(s.choice).coerceAtLeast(0)

    /** "Not set", the preset cities and, for a value entered elsewhere (backup), that value. */
    fun locationLabels(s: RoomSettings): List<String> {
        val base = listOf(NOT_SET) + RoomSettingsRows.CITIES.map { it.label }
        return if (customLocation(s)) base + RoomSettingsRows.summary(s, "location") else base
    }

    fun locationIndex(s: RoomSettings): Int {
        if (s.latitudeDeg == null) return 0
        val v = RoomSettingsRows.valueOf(s, "location")
        val i = RoomSettingsRows.CITIES.indexOfFirst { it.value == v }
        return if (i >= 0) i + 1 else RoomSettingsRows.CITIES.size + 1
    }

    /** Settings after picking location label [index]; the custom entry keeps the current value. */
    fun applyLocation(s: RoomSettings, index: Int): RoomSettings = when {
        index <= 0 -> RoomSettingsRows.apply(s, "location", null)
        index <= RoomSettingsRows.CITIES.size -> RoomSettingsRows.apply(s, "location", RoomSettingsRows.CITIES[index - 1].value)
        else -> s
    }

    private fun customLocation(s: RoomSettings): Boolean {
        if (s.latitudeDeg == null) return false
        val v = RoomSettingsRows.valueOf(s, "location")
        return RoomSettingsRows.CITIES.none { it.value == v }
    }

    fun transitionOptions(): List<RoomSettingsRows.Option> = RoomSettingsRows.row("transitionSeconds")!!.options

    fun transitionIndex(s: RoomSettings): Int {
        val opts = transitionOptions()
        val values = opts.map { it.value.toFloat() }
        // Nearest preset (a backup may hold any 0..5 s value).
        return values.indices.minByOrNull { kotlin.math.abs(values[it] - s.transitionSeconds) } ?: 0
    }

    /** Schedule rows step in [STEP_MIN]-minute slots: 0..95. */
    fun slot(minute: Int): Int = (minute.coerceIn(0, 1439) / STEP_MIN)
    fun minuteOf(slot: Int): Int = slot.coerceIn(0, 1440 / STEP_MIN - 1) * STEP_MIN
    val MAX_SLOT: Int get() = 1440 / STEP_MIN - 1
}

/** Settings > Room lighting (HOME group): how the screen adapts to the room's light. */
object RoomSection : SettingsSection, SpecifiedPage {
    override val id = RoomPage.ID
    override val title = "Room lighting"
    override val iconRes = R.drawable.ic_room_auto
    override val group = SettingsGroup.HOME
    override val spec = RoomPage.SPEC
    override val description = "Brightness, contrast and glass follow your room"

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val prefs = RoomPrefs(context)
        val s = prefs.load()
        fun save(n: RoomSettings, rebuild: Boolean = false) {
            prefs.save(n)
            if (rebuild) nav.refresh()
        }
        val rows = RoomSettingsRows
        page.row("room.choice", rows.row("choice")!!.label, RowKind.Choice(RoomPage.choiceLabels(), RoomPage.choiceIndex(s)) { i ->
            save(prefs.load().copy(choice = RoomPage.choices()[i]))
        }, subtitle = "Auto uses a light sensor if the TV has one, else the time of day", iconRes = R.drawable.ic_room_auto)
        page.row("room.location", "Sunrise and sunset for", RowKind.Choice(RoomPage.locationLabels(s), RoomPage.locationIndex(s)) { i ->
            save(RoomPage.applyLocation(prefs.load(), i))
        }, subtitle = "Optional: Auto follows the real sun there. Nothing leaves the TV")
        val transitions = RoomPage.transitionOptions()
        page.row("room.transitionSeconds", rows.row("transitionSeconds")!!.label,
            RowKind.Choice(transitions.map { it.label }, RoomPage.transitionIndex(s)) { i ->
                save(rows.apply(prefs.load(), "transitionSeconds", transitions[i].value))
            }, subtitle = rows.row("transitionSeconds")!!.subtitle)
        page.row("room.boostTextInBright", rows.row("boostTextInBright")!!.label, RowKind.Toggle(s.boostTextInBright) { on ->
            save(rows.apply(prefs.load(), "boostTextInBright", on.toString()))
        }, subtitle = rows.row("boostTextInBright")!!.subtitle)
        page.advanced(nav, "room.advanced", "Auto schedule when sunrise and sunset are unknown") { adv ->
            for (key in RoomPage.ADVANCED) {
                val model = RoomPage.modelKey(key)
                val row = rows.row(model)!!
                val minute = rows.valueOf(s, model)?.toIntOrNull() ?: 0
                adv.row(key, row.label, RowKind.Stepper(RoomPage.slot(minute), 0, RoomPage.MAX_SLOT, { v -> RoomSettingsRows.hhmm(RoomPage.minuteOf(v)) }) { v ->
                    save(rows.apply(prefs.load(), model, RoomPage.minuteOf(v).toString()))
                }, subtitle = row.subtitle)
            }
        }
        return page
    }
}
