package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.settings.AccentMode
import dev.tvlauncher.settings.AnimationLevel
import dev.tvlauncher.settings.AppearanceFormat
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.AppearanceSettings
import dev.tvlauncher.settings.ClockFormat
import dev.tvlauncher.settings.TileSize
import java.util.Locale

/** Accent, motion, top bar and tile size. Every change is saved at once. */
object AppearanceSection : SettingsSection {
    const val ID = "appearance"
    override val id = ID
    override val title = "Appearance"
    override val iconRes = R.drawable.ic_set_appearance

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val prefs = AppearancePrefs(context)
        val a = prefs.load()
        val page = SettingsPage(context, nav.style)
        page.lead("How the home screen looks and moves. Changes apply when you go back home.")

        page.group("Color")
        page.row(
            "appearance.accentMode", "Backdrop and highlights",
            RowKind.Choice(listOf("Auto from app", "Fixed"), a.accentMode.ordinal) { i ->
                prefs.update { it.copy(accentMode = AccentMode.values()[i]) }
                nav.appearanceChanged()
            },
            subtitle = if (a.accentMode == AccentMode.AUTO_BRAND) "Follows the brand color of the focused app"
            else "One color for the backdrop and the hero; tiles keep their app colors",
            iconRes = R.drawable.ic_set_palette
        )
        if (a.accentMode == AccentMode.FIXED) {
            page.add(SwatchStrip(context, nav.style, "appearance.swatch", AppearanceSettings.PRESETS, a.fixedAccent) { c ->
                prefs.update { it.copy(fixedAccent = c) }
                nav.appearanceChanged()
            })
        }

        page.group("Motion")
        val device = nav.profile.effectsMode
        page.row(
            "appearance.animation", "Animations",
            RowKind.Choice(listOf("Auto", "Full", "Lite", "Off"), a.animationLevel.ordinal) { i ->
                prefs.update { it.copy(animationLevel = AnimationLevel.values()[i]) }
                nav.appearanceChanged()
            },
            subtitle = "Auto picks ${AboutText.motion(device)} for this TV. Off also follows the system animator scale.",
            iconRes = R.drawable.ic_set_motion
        )

        page.group("Top bar")
        lateinit var clockRow: SettingsRow
        clockRow = page.row(
            "appearance.clockFormat", "Clock",
            RowKind.Choice(listOf("Auto", "24-hour", "12-hour"), a.clockFormat.ordinal) { i ->
                val f = ClockFormat.values()[i]
                prefs.update { it.copy(clockFormat = f) }
                clockRow.setSubtitle(clockExample(context, f))
            },
            subtitle = clockExample(context, a.clockFormat),
            iconRes = R.drawable.ic_set_clock
        )
        page.row(
            "appearance.showDate", "Show date",
            RowKind.Toggle(a.showDate) { on -> prefs.update { it.copy(showDate = on) } },
            subtitle = AppearanceFormat.dateText(System.currentTimeMillis(), Locale.getDefault()),
            iconRes = R.drawable.ic_set_calendar
        )
        page.row(
            "appearance.showStatus", "Show status icons",
            RowKind.Toggle(a.showStatusIcons) { on -> prefs.update { it.copy(showStatusIcons = on) } },
            subtitle = "Wi-Fi, Ethernet and Bluetooth next to the Inputs button",
            iconRes = R.drawable.ic_set_status
        )

        page.group("Home row")
        page.row(
            "appearance.tileSize", "Tile size",
            RowKind.Choice(listOf("Small", "Medium", "Large"), a.rowTileSize.ordinal) { i ->
                prefs.update { it.copy(rowTileSize = TileSize.values()[i]) }
            },
            subtitle = "Larger tiles show fewer apps at once",
            iconRes = R.drawable.ic_set_tiles
        )
        return page
    }

    private fun clockExample(context: Context, f: ClockFormat): String {
        val is24h = AppearanceFormat.is24h(f, android.text.format.DateFormat.is24HourFormat(context))
        val now = AppearanceFormat.clockText(System.currentTimeMillis(), is24h, Locale.getDefault())
        return if (f == ClockFormat.AUTO) "Now: $now (follows the TV's setting)" else "Now: $now"
    }
}
