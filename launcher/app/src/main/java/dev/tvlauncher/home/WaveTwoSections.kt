package dev.tvlauncher.home

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import dev.tvlauncher.R
import dev.tvlauncher.ambient.AmbientDream
import dev.tvlauncher.ambient.AmbientSettingsRows
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.ambient.NotificationAccessCard
import dev.tvlauncher.ambient.WallpaperPickerPanel
import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.ambient.WeatherContract
import dev.tvlauncher.appsettings.ModulePlaceholderSection
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.devices.DeviceMonitor
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.settings.AppearanceFormat
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.ui.SystemActions

/**
 * Settings pages of the wave-2 modules, registered through the existing [SettingsRegistry] hook
 * (LauncherApp.onCreate calls [registerAll]): "Ambient" and "Home screen" (new ids) and "Devices"
 * (takes the placeholder's slot). Rows are built from the modules' declarative models.
 */
object WaveTwoSections {
    const val AMBIENT_ID = "ambient"
    const val HOME_EXTRAS_ID = "home_extras"

    val SCREENSAVER_ADB: String = listOf(AmbientDream.ADB_SELECT, AmbientDream.ADB_ENABLE, AmbientDream.ADB_ON_SLEEP).joinToString("\n")

    fun registerAll(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(AmbientSection)
        registry.register(DevicesSection)
        registry.register(HomeExtrasSection)
    }

    /** Value of the "Weather" row: the companion's settings open when it is installed. */
    fun weatherRowValue(installed: Boolean): String = if (installed) "Open" else "Not installed"

    enum class PageKind { ACTION, TOGGLE, STEPPER, CARD, INFO }

    data class PageRow(val key: String, val title: String, val kind: PageKind, val value: String, val hint: String?)

    /**
     * The visible [AmbientSettingsRows] in model order, mapped to page rows. The ambient clock's
     * 12/24-hour format follows Settings > Appearance, so "clock24h" is an info row, not a toggle.
     */
    fun ambientRows(s: WallpaperSettings, accessGranted: Boolean, is24h: Boolean): List<PageRow> =
        AmbientSettingsRows.rows(s, accessGranted).filter { it.visible }.map { r ->
            if (r.key == "clock24h") {
                PageRow(r.key, "Clock format", PageKind.INFO, "Follows Appearance (${if (is24h) "24" else "12"}-hour)", null)
            } else {
                val kind = when (r.kind) {
                    AmbientSettingsRows.Kind.ACTION -> PageKind.ACTION
                    AmbientSettingsRows.Kind.TOGGLE -> PageKind.TOGGLE
                    AmbientSettingsRows.Kind.STEPPER, AmbientSettingsRows.Kind.CHOICE -> PageKind.STEPPER
                    AmbientSettingsRows.Kind.CARD -> PageKind.CARD
                }
                PageRow(r.key, r.title, kind, r.value, r.hint)
            }
        }
}

/** Settings > Ambient: wallpaper, dimming, idle time, burn-in, notification chips and screensaver setup. */
object AmbientSection : SettingsSection {
    override val id = WaveTwoSections.AMBIENT_ID
    override val title = "Ambient"
    override val iconRes = R.drawable.ic_qa_ambient

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val store = WallpaperStore(context)
        val a = AppearancePrefs(context).load()
        val is24h = AppearanceFormat.is24h(a.clockFormat, android.text.format.DateFormat.is24HourFormat(context))
        // Display only: the stored clock format is synced when Home starts ambient (HomeAmbient.startNow).
        val s = AmbientGate.withClock(store.load(), is24h)
        val save = { n: WallpaperSettings -> store.save(n); nav.refresh() }
        page.lead("A calm full-screen clock, weather and wallpaper when the home screen sits idle.")
        page.row("ambient.start", "Start ambient now", RowKind.Action {
            if (!SystemActions.ambient(context)) nav.notify("Ambient could not be opened")
        }, iconRes = R.drawable.ic_qa_ambient)
        for (r in WaveTwoSections.ambientRows(s, NotificationAccess.isEnabled(context), is24h)) {
            val key = "ambient.${r.key}"
            when (r.kind) {
                WaveTwoSections.PageKind.ACTION -> page.row(key, r.title, RowKind.Action(r.value) {
                    nav.push("Wallpaper") { c, n -> pickerPage(c, n) }
                }, subtitle = r.hint)
                WaveTwoSections.PageKind.TOGGLE -> page.row(key, r.title, RowKind.Toggle(r.value == "On") {
                    save(AmbientSettingsRows.toggle(store.load(), r.key))
                }, subtitle = r.hint)
                // A centred three-step stepper: LEFT/RIGHT/OK step the model by -1/+1, then the page rebuilds.
                WaveTwoSections.PageKind.STEPPER -> page.row(key, r.title, RowKind.Stepper(1, 0, 2, { r.value }) { v ->
                    save(AmbientSettingsRows.step(store.load(), r.key, if (v < 1) -1 else 1))
                }, subtitle = r.hint)
                WaveTwoSections.PageKind.CARD -> page.add(NotificationAccessCard(context))
                WaveTwoSections.PageKind.INFO -> page.row(key, r.title, RowKind.Info(r.value), subtitle = r.hint)
            }
        }
        val extras = HomeExtrasPrefs(context)
        page.group("Home screen")
        page.row("ambient.homeWallpaper", "Use wallpaper on home", RowKind.Toggle(extras.wallpaperOnHome) { on ->
            extras.wallpaperOnHome = on
        }, subtitle = "Shows this wallpaper under the home colors (uses more memory)")
        page.group("Screensaver")
        page.note("To use Ambient as the TV's screensaver, run these once from a computer:\n${WaveTwoSections.SCREENSAVER_ADB}")
        return page
    }

    /**
     * Hosts [WallpaperPickerPanel] in the content pane. The panel stacks taller than a 720p pane
     * (title, grid, slideshow/interval/dim and notification rows, path text), so it sits in a
     * ScrollView capped at the pane height; focus moves scroll it. The picker is shown once the
     * host has its real width (the pane is narrower than the screen), and dismissed on detach.
     */
    private fun pickerPage(context: Context, nav: SettingsNav): View {
        val picker = WallpaperPickerPanel(context, nav.profile, WallpaperStore(context)) { }
        val host = object : FrameLayout(context) {
            override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
                super.onSizeChanged(w, h, oldw, oldh)
                if (w > 0 && !picker.isShowing) post { if (isAttachedToWindow && !picker.isShowing) picker.show(this) }
            }

            override fun onDetachedFromWindow() {
                picker.dismiss() // cancels jobs and recycles thumbnails
                super.onDetachedFromWindow()
            }
        }
        return ScrollView(context).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(host, FrameLayout.LayoutParams(-1, -2))
        }
    }
}

/** Settings > Devices: the live [DevicesPanel] fed by its own [DeviceMonitor] while the page is shown. */
object DevicesSection : SettingsSection {
    override val id = ModulePlaceholderSection.DEVICES
    override val title = "Devices"
    override val iconRes = R.drawable.ic_set_devices

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val panel = DevicesPanel(context)
        panel.onSwitchResult = { ok -> if (!ok) nav.notify("Could not open input") }
        val monitor = DeviceMonitor(context) { } // the list only; cards belong to the home screen
        return object : FrameLayout(context) {
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                monitor.onChanged = { panel.setDevices(monitor.devices()) }
                monitor.start()
                panel.setDevices(monitor.devices())
            }

            override fun onDetachedFromWindow() {
                monitor.onChanged = null
                monitor.stop()
                super.onDetachedFromWindow()
            }
        }.apply { addView(panel, FrameLayout.LayoutParams(-1, -1)) }
    }
}

/** Settings > Home screen: the top-bar weather chip and the weather companion's own settings. */
object HomeExtrasSection : SettingsSection {
    override val id = WaveTwoSections.HOME_EXTRAS_ID
    override val title = "Home screen"
    override val iconRes = R.drawable.ic_set_home

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style)
        val extras = HomeExtrasPrefs(context)
        page.lead("Extras for the home screen's top bar.")
        page.row("home.weatherTopBar", "Weather in top bar", RowKind.Toggle(extras.weatherInTopBar) { on ->
            extras.weatherInTopBar = on
        }, subtitle = "Icon and temperature next to the clock; hidden when the data is over 3 hours old")
        val intent = WeatherCompanion.settingsIntent(context)
        val value = WaveTwoSections.weatherRowValue(intent != null)
        if (intent != null) {
            page.row("home.weather", "Weather", RowKind.Action(value) {
                if (!SystemActions.start(context, intent)) nav.notify("Weather settings could not be opened")
            }, subtitle = "City and units, in the weather companion")
        } else {
            page.row("home.weather", "Weather", RowKind.Info(value),
                subtitle = "Install the weather companion (${WeatherContract.PACKAGE}) to use it")
        }
        return page
    }
}
