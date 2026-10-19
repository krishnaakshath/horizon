package dev.tvlauncher.appsettings

import android.content.Context
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.sound.SoundEvent
import dev.tvlauncher.sound.SoundPlayer
import dev.tvlauncher.sound.SoundPrefs
import dev.tvlauncher.sound.SoundSettings
import dev.tvlauncher.sound.SoundThemes
import java.util.concurrent.atomic.AtomicReference

/**
 * The Sounds page, built from [SettingsRow]s: Sound effects, Navigation and Launch sounds
 * (each opens a chooser that plays a theme when it is focused), Volume with a level bar; the
 * per-event switches, the sound lab and your own sound files are under Advanced.
 * Every page level owns its preview player (released when the page leaves the screen). Changes
 * are saved at once; the home screen re-reads them in onResume (HomeSounds.reload).
 */
object SoundsPage {
    val SPEC = PageSpec(
        ModulePlaceholderSection.SOUNDS,
        listOf("sounds.enabled", "sounds.nav", "sounds.launch", "sounds.volume"),
        listOf("sounds.events", "sounds.lab", "sounds.custom", "sounds.folder")
    )

    private const val PREVIEW_DELAY_MS = 140L

    /** Which theme a chooser edits. */
    private enum class Slot(val title: String, val preview: SoundEvent) {
        NAV("Navigation sounds", SoundEvent.SELECT),
        LAUNCH("Launch sounds", SoundEvent.LAUNCH);

        fun current(s: SoundSettings) = if (this == NAV) s.navThemeId else s.launchThemeId
        fun with(s: SoundSettings, id: String) = if (this == NAV) s.copy(navThemeId = id) else s.copy(launchThemeId = id)
    }

    /** A page with its own preview player; [latest] is what the player plays with. */
    private class Host(context: Context, nav: SettingsNav) {
        val prefs = SoundPrefs(context)
        val latest = AtomicReference(prefs.load())
        val player = SoundPlayer(context.applicationContext, settings = { latest.get() })
        val page = SettingsPage(context, nav.style)

        init {
            player.prewarm()
            page.doOnDetach { player.release() }
        }

        val s: SoundSettings get() = latest.get()

        fun save(n: SoundSettings) {
            latest.set(n)
            prefs.save(n)
            player.prewarm()
        }
    }

    fun build(context: Context, nav: SettingsNav): View {
        val h = Host(context, nav)
        val page = h.page
        val s = h.s
        page.row("sounds.enabled", "Sound effects", RowKind.Toggle(s.enabled) { on ->
            h.save(h.s.copy(enabled = on))
            if (on) h.player.preview(SoundEvent.SELECT)
        }, subtitle = "Clicks and chimes on the home screen", iconRes = R.drawable.ic_set_sounds)
        for (slot in Slot.values()) {
            page.row("sounds.${slot.name.lowercase()}", slot.title, RowKind.Action(SoundThemes.byId(slot.current(s)).name) {
                nav.push(slot.title) { c, n -> chooser(c, n, slot) }
            }, iconRes = if (slot == Slot.NAV) R.drawable.ic_set_motion else R.drawable.ic_set_tiles)
        }
        val volumeStep = (s.volume + 5) / 10
        page.row("sounds.volume", "Volume", RowKind.Stepper(volumeStep, 0, 10, { "${it * 10}%" }, bar = true) { v ->
            h.save(h.s.copy(volume = v * 10))
            h.player.preview(SoundEvent.SELECT)
        }, iconRes = R.drawable.ic_set_status)

        page.advanced(nav, "sounds.advanced", "Sound events, sound lab, your own files") { adv ->
            val on = SoundEvent.values().count { s.isEventOn(it) }
            adv.row("sounds.events", "Sound events", RowKind.Action("$on of ${SoundEvent.values().size} on") {
                nav.push("Sound events") { c, n -> events(c, n) }
            }, subtitle = "Turn single sounds on or off", iconRes = R.drawable.ic_set_check)
            adv.row("sounds.lab", "Sound lab", RowKind.Action {
                nav.push("Sound lab") { c, n -> lab(c, n) }
            }, subtitle = "Play every sound of your themes", iconRes = R.drawable.ic_set_insights)
            adv.row("sounds.custom", "Use my own sound files", RowKind.Toggle(s.useCustomFolder) { use ->
                h.save(h.s.copy(useCustomFolder = use))
            }, subtitle = "WAV files replace the built-in sounds", iconRes = R.drawable.ic_set_import)
            lateinit var help: android.widget.TextView
            lateinit var helpRow: SettingsRow
            helpRow = adv.row("sounds.folder", "Where to put the files", RowKind.Action {
                val open = help.visibility != View.VISIBLE
                help.visibility = if (open) View.VISIBLE else View.GONE
                helpRow.setExpanded(open)
            }, iconRes = R.drawable.ic_set_about)
            val folder = h.player.customDir()?.path ?: "Android/data/${context.packageName}/files/sounds"
            help = adv.note(
                "Folder: $folder\nFiles: " + SoundEvent.values().joinToString(", ") { it.name.lowercase() + ".wav" } +
                    "\n16-bit PCM WAV, mono or stereo, 8-48 kHz, under 500 KB",
                maxLines = 6
            )
            help.visibility = View.GONE
            helpRow.setExpanded(false)
        }
        return page
    }

    /** One row per theme; focusing a row plays it (debounced), OK picks it and goes back. */
    private fun chooser(context: Context, nav: SettingsNav, slot: Slot): View {
        val h = Host(context, nav)
        val saved = h.prefs.load()
        val chosen = slot.current(saved)
        var pending: Runnable? = null
        h.page.doOnDetach { pending?.let { h.page.removeCallbacks(it) } }
        for (theme in SoundThemes.all) {
            val row = h.page.row("sounds.theme.${theme.id}", theme.name,
                RowKind.Action(if (theme.id == chosen) "Current" else null, chevron = false) {
                    h.save(slot.with(h.prefs.load(), theme.id))
                    nav.notify("${slot.title}: ${theme.name}")
                    nav.pop()
                })
            row.setOnFocusChangeListener { _, has ->
                if (!has) return@setOnFocusChangeListener
                pending?.let { h.page.removeCallbacks(it) }
                val r = Runnable {
                    // Preview only: the player hears the focused theme, prefs keep the saved one.
                    h.latest.set(slot.with(h.prefs.load(), theme.id))
                    h.player.preview(slot.preview)
                }
                pending = r
                h.page.postDelayed(r, PREVIEW_DELAY_MS)
            }
        }
        h.page.preferredFocus = "sounds.theme.$chosen"
        return h.page
    }

    private fun events(context: Context, nav: SettingsNav): View {
        val h = Host(context, nav)
        for (e in SoundEvent.values()) {
            h.page.row("sounds.event.${e.name.lowercase()}", label(e), RowKind.Toggle(h.s.isEventOn(e)) { on ->
                h.save(h.s.copy(perEvent = h.s.perEvent + (e to on)))
                if (on) h.player.preview(e)
            })
        }
        return h.page
    }

    private fun lab(context: Context, nav: SettingsNav): View {
        val h = Host(context, nav)
        val queued = ArrayList<Runnable>()
        h.page.doOnDetach { queued.forEach { h.page.removeCallbacks(it) } }
        h.page.row("sounds.lab.all", "Play all", RowKind.Action(chevron = false) {
            queued.forEach { h.page.removeCallbacks(it) }
            queued.clear()
            SoundEvent.values().forEachIndexed { i, e ->
                val r = Runnable { h.player.preview(e) }
                queued += r
                h.page.postDelayed(r, i * 450L)
            }
        }, iconRes = R.drawable.ic_set_sounds)
        for (e in SoundEvent.values()) {
            h.page.row("sounds.lab.${e.name.lowercase()}", label(e),
                RowKind.Action(SoundThemes.byId(h.s.themeIdFor(e)).name, chevron = false) { h.player.preview(e) })
        }
        return h.page
    }

    private fun label(e: SoundEvent) = when (e) {
        SoundEvent.MOVE -> "Navigation move"
        SoundEvent.SELECT -> "Select"
        SoundEvent.BACK -> "Back"
        SoundEvent.LAUNCH -> "App launch"
        SoundEvent.MENU_OPEN -> "Menu open"
        SoundEvent.ERROR -> "Error"
    }
}
