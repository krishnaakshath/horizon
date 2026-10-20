package dev.tvlauncher.ambient.widgets

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import dev.tvlauncher.R
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage

/** Settings > Home screen > Ambient widgets: calendar, photo frame and album art on the ambient screen. */
object AmbientWidgetsSection : SettingsSection, SpecifiedPage {
    const val ID = "ambient_widgets"
    const val CALENDAR = "aw.calendar"
    const val PHOTOS = "aw.photos"
    const val PHOTO_TIME = "aw.photoTime"
    const val ALBUM_ART = "aw.albumArt"
    const val FOLLOW_MUSIC = "aw.followMusic"
    override val id = ID
    override val title = "Ambient widgets"
    override val iconRes = R.drawable.ic_widgets
    override val group = SettingsGroup.HOME
    override val description = "Calendar, photo frame and album art on the ambient screen."
    override val spec = PageSpec(ID, listOf(CALENDAR, PHOTOS, PHOTO_TIME, ALBUM_ART, FOLLOW_MUSIC))

    fun register(registry: SettingsRegistry = SettingsRegistry.shared) = registry.register(this)

    private fun secondsLabel(s: Int) = if (s < 60) "$s s" else "${s / 60} min"

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val store = AmbientWidgetsStore(context)
        val s = store.load()
        page.lead("Extra things to see on the ambient screen. They drift and dim with it, like the clock.")
        when (AmbientWidgetsModel.calendarRow(AmbientWidgetsStore.calendarGranted(context))) {
            AmbientWidgetsModel.CalendarRow.ALLOW -> page.row(CALENDAR, "Show my calendar", RowKind.Action("Allow") {
                askCalendar(context, nav)
            }, subtitle = "Allow H02 to read the calendar on this TV to see your next three events", iconRes = R.drawable.ic_set_calendar)
            AmbientWidgetsModel.CalendarRow.TOGGLE -> page.row(CALENDAR, "Calendar", RowKind.Toggle(s.calendar) { on ->
                store.save(store.load().copy(calendar = on))
            }, subtitle = "Your next three events, at the top right", iconRes = R.drawable.ic_set_calendar)
        }
        page.row(PHOTOS, "Photo frame", RowKind.Toggle(s.photos) { on ->
            store.save(store.load().copy(photos = on)); nav.refresh()
        }, subtitle = "Pictures on this TV and DCIM or Pictures on a USB stick, with the date taken", iconRes = R.drawable.ic_widgets)
        if (s.photos) {
            val opts = AmbientWidgetsModel.PHOTO_SECONDS
            page.row(PHOTO_TIME, "Next photo every", RowKind.Choice(opts.map(::secondsLabel), opts.indexOf(s.photoSeconds).coerceAtLeast(0)) { i ->
                store.save(store.load().copy(photoSeconds = opts[i]))
            }, iconRes = R.drawable.ic_set_clock)
        }
        page.row(ALBUM_ART, "Album art while music plays", RowKind.Toggle(s.albumArt) { on ->
            store.save(store.load().copy(albumArt = on))
        }, subtitle = "Fills the screen with the cover. Needs Now Playing on ambient (Home screen > Now Playing)",
            iconRes = R.drawable.ic_qa_ambient)
        // Same setting as Home screen > Now Playing > "Screensaver follows the music".
        val np = dev.tvlauncher.discover.DiscoverStore(context)
        page.row(FOLLOW_MUSIC, "Screensaver follows the music", RowKind.Toggle(np.loadNowPlaying().ambientFollows) { on ->
            val cur = np.loadNowPlaying()
            np.saveNowPlaying(dev.tvlauncher.nowplaying.AmbientMusicPlan.withMode(cur,
                if (on) dev.tvlauncher.nowplaying.AmbientMusicPlan.Mode.FULL
                else if (cur.showOnAmbient) dev.tvlauncher.nowplaying.AmbientMusicPlan.Mode.CARD
                else dev.tvlauncher.nowplaying.AmbientMusicPlan.Mode.OFF))
        }, subtitle = "While a song plays, the screensaver shows it: cover, title and progress", iconRes = R.drawable.ic_qa_ambient)
        return page
    }

    /** Asks for READ_CALENDAR, then refreshes this page once the answer is in (polls briefly; the dialog pauses Settings). */
    private fun askCalendar(context: Context, nav: SettingsNav) {
        val activity = context as? Activity
        if (activity == null || Build.VERSION.SDK_INT < 23) { nav.refresh(); return }
        try { activity.requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), REQUEST) } catch (e: Throwable) { return }
        val main = Handler(Looper.getMainLooper())
        var tries = 0
        val poll = object : Runnable {
            override fun run() {
                if (AmbientWidgetsStore.calendarGranted(context)) {
                    val st = AmbientWidgetsStore(context)
                    st.save(st.load().copy(calendar = true))
                    nav.notify("Calendar on")
                    nav.refreshCurrent()
                } else if (++tries < 60 && !activity.isFinishing) main.postDelayed(this, 1000L)
            }
        }
        main.postDelayed(poll, 1000L)
    }

    private const val REQUEST = 4207
}
