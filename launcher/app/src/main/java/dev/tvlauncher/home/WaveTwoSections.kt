package dev.tvlauncher.home

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ScrollView
import dev.tvlauncher.R
import dev.tvlauncher.ambient.AmbientSettingsRows
import dev.tvlauncher.ambient.NotificationAccess
import dev.tvlauncher.ambient.NotificationAccessCard
import dev.tvlauncher.ambient.WallpaperPickerPanel
import dev.tvlauncher.ambient.WallpaperSettings
import dev.tvlauncher.ambient.WallpaperStore
import dev.tvlauncher.ambient.WeatherCompanion
import dev.tvlauncher.ambient.WeatherContract
import dev.tvlauncher.appsettings.ModulePlaceholderSection
import dev.tvlauncher.appsettings.PageSpec
import dev.tvlauncher.appsettings.RowKind
import dev.tvlauncher.appsettings.SettingsGroup
import dev.tvlauncher.appsettings.SettingsNav
import dev.tvlauncher.appsettings.SettingsPage
import dev.tvlauncher.appsettings.SettingsRegistry
import dev.tvlauncher.appsettings.SettingsSection
import dev.tvlauncher.appsettings.SpecifiedPage
import dev.tvlauncher.devices.DeviceMonitor
import dev.tvlauncher.devices.DevicesPanel
import dev.tvlauncher.discover.DiscoverSettingsRows
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.discover.GetAppsLink
import dev.tvlauncher.discover.StoreLauncher
import dev.tvlauncher.discover.RegionGuess
import dev.tvlauncher.nowplaying.NowPlayingAccess
import java.util.Locale
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

    /** How to pick Ambient as the TV's screensaver, in plain words. */
    val SCREENSAVER_NOTE: String = dev.tvlauncher.setup.SetupCopy.SCREENSAVER_NOTE

    fun registerAll(registry: SettingsRegistry = SettingsRegistry.shared) {
        registry.register(AmbientSection)
        registry.register(DevicesSection)
        registry.register(HomeExtrasSection)
        registry.register(RoomSection)
    }

    /** Value of the "Weather" row: the companion's settings open when it is installed. */
    /** Installed: "Open" (its settings). Missing: "Get Weather" through Get Apps, else "Not installed". */
    fun weatherRowValue(installed: Boolean, getAppsInstalled: Boolean = false): String = when {
        installed -> "Open"
        getAppsInstalled -> "Get Weather"
        else -> "Not installed"
    }

    enum class PageKind { ACTION, TOGGLE, STEPPER, CARD, INFO }

    /** Ambient model rows shown on the page itself; every other model row sits in "Advanced". */
    val AMBIENT_PRIMARY_MODEL_KEYS = listOf("wallpaper", "slideshowInterval", "dim", "idleMinutes", "nightDim")

    /** Ambient model rows inside the page's "Advanced" block (the CARD row is a view, not a guarded row). */
    val AMBIENT_ADVANCED_MODEL_KEYS = listOf("notificationsEnabled", "showNotificationText", "clock24h", "clockPosition", "pixelShift")

    val AMBIENT_SPEC = PageSpec(
        AMBIENT_ID,
        listOf("ambient.start") + AMBIENT_PRIMARY_MODEL_KEYS.map { "ambient.$it" } + "ambient.homeWallpaper",
        AMBIENT_ADVANCED_MODEL_KEYS.map { "ambient.$it" }
    )

    val HOME_EXTRAS_SPEC = PageSpec(
        HOME_EXTRAS_ID,
        listOf("home.weatherTopBar", "home.weather", "home.getMoreAppsTile", "home.nowPlaying"),
        listOf("home.discoverRegion", "home.restoreHidden")
    )

    /**
     * Rows of the Home screen page in order, as (key, advanced). The notification-access card is a
     * plain view shown under "home.nowPlaying" when [accessCardShown]; "home.restoreHidden" only
     * appears while suggestions are hidden.
     */
    fun homeExtrasRows(hiddenSuggestions: Int): List<Pair<String, Boolean>> =
        listOf("home.weatherTopBar", "home.weather", "home.getMoreAppsTile", "home.nowPlaying").map { it to false } +
            listOfNotNull("home.discoverRegion", "home.restoreHidden".takeIf { hiddenSuggestions > 0 }).map { it to true }

    /** The access card shows while Now Playing is on but the listener is not granted. */
    fun accessCardShown(nowPlayingEnabled: Boolean, granted: Boolean): Boolean = nowPlayingEnabled && !granted

    /** Region stepper labels ([DiscoverSettingsRows.REGIONS]; null = automatic). */
    fun regionLabels(detected: String?): List<String> = DiscoverSettingsRows.REGIONS.map { r ->
        r ?: RegionGuess.normalize(detected)?.let { "Automatic ($it)" } ?: "Automatic"
    }

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
object AmbientSection : SettingsSection, SpecifiedPage {
    override val id = WaveTwoSections.AMBIENT_ID
    override val title = "Ambient"
    override val iconRes = R.drawable.ic_qa_ambient
    override val group = SettingsGroup.HOME
    override val spec = WaveTwoSections.AMBIENT_SPEC

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
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
        val rows = WaveTwoSections.ambientRows(s, NotificationAccess.isEnabled(context), is24h)
        fun render(block: SettingsPage.Block, r: WaveTwoSections.PageRow) {
            val key = "ambient.${r.key}"
            when (r.kind) {
                WaveTwoSections.PageKind.ACTION -> block.row(key, r.title, RowKind.Action(r.value) {
                    nav.push("Wallpaper") { c, n -> pickerPage(c, n) }
                }, subtitle = r.hint)
                WaveTwoSections.PageKind.TOGGLE -> block.row(key, r.title, RowKind.Toggle(r.value == "On") {
                    save(AmbientSettingsRows.toggle(store.load(), r.key))
                }, subtitle = r.hint)
                // A centred three-step stepper: LEFT/RIGHT/OK step the model by -1/+1, then the page rebuilds.
                WaveTwoSections.PageKind.STEPPER -> block.row(key, r.title, RowKind.Stepper(1, 0, 2, { r.value }) { v ->
                    save(AmbientSettingsRows.step(store.load(), r.key, if (v < 1) -1 else 1))
                }, subtitle = r.hint)
                WaveTwoSections.PageKind.CARD -> block.add(NotificationAccessCard(context))
                WaveTwoSections.PageKind.INFO -> block.row(key, r.title, RowKind.Info(r.value), subtitle = r.hint)
            }
        }
        for (r in rows) if (r.key in WaveTwoSections.AMBIENT_PRIMARY_MODEL_KEYS) render(page.body, r)
        val extras = HomeExtrasPrefs(context)
        page.row("ambient.homeWallpaper", "Use wallpaper on home", RowKind.Toggle(extras.wallpaperOnHome) { on ->
            extras.wallpaperOnHome = on
        }, subtitle = "Shows this wallpaper under the home colors (uses more memory)")
        page.advanced(nav, "ambient.advanced", "Clock, notification chips, burn-in and screensaver") { adv ->
            for (r in rows) if (r.key !in WaveTwoSections.AMBIENT_PRIMARY_MODEL_KEYS) render(adv, r)
            adv.group("Screensaver")
            adv.note(WaveTwoSections.SCREENSAVER_NOTE, maxLines = 3)
        }
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
    override val group = SettingsGroup.CONNECTIONS

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

/**
 * Settings > Home screen: the top-bar weather chip, the weather companion's settings, the shelf's
 * "Get more apps" tile and the Now Playing card (with the notification-access card when needed).
 */
object HomeExtrasSection : SettingsSection, SpecifiedPage {
    override val id = WaveTwoSections.HOME_EXTRAS_ID
    override val title = "Home screen"
    override val iconRes = R.drawable.ic_set_home
    override val group = SettingsGroup.HOME
    override val spec = WaveTwoSections.HOME_EXTRAS_SPEC

    override fun buildPage(context: Context, nav: SettingsNav): View {
        val page = SettingsPage(context, nav.style, spec)
        val extras = HomeExtrasPrefs(context)
        page.lead("Extras for the home screen.")
        page.row("home.weatherTopBar", "Weather in top bar", RowKind.Toggle(extras.weatherInTopBar) { on ->
            extras.weatherInTopBar = on
        }, subtitle = "Icon and temperature next to the clock; hidden when the data is over 3 hours old")
        val intent = WeatherCompanion.settingsIntent(context)
        val getApps = intent == null && StoreLauncher.isGetAppsInstalled(context)
        val value = WaveTwoSections.weatherRowValue(intent != null, getApps)
        if (intent != null) {
            page.row("home.weather", "Weather", RowKind.Action(value) {
                if (!SystemActions.start(context, intent)) nav.notify("Weather settings could not be opened")
            }, subtitle = "City and units, in the Weather app")
        } else if (getApps) {
            page.row("home.weather", "Weather", RowKind.Action(value) {
                if (!StoreLauncher.openApp(context, GetAppsLink.PACKAGE)) nav.notify(StoreLauncher.GET_APPS_MISSING_TEXT)
            }, subtitle = "Install the Weather app from Get Apps to show weather here")
        } else {
            page.row("home.weather", "Weather", RowKind.Info(value),
                subtitle = "Install the weather companion (${WeatherContract.PACKAGE}) to use it")
        }
        val store = DiscoverStore(context)
        val d = store.load()
        page.row("home.getMoreAppsTile", "\"Get more apps\" tile", RowKind.Toggle(d.showGetMoreAppsTile) { _ ->
            store.save(DiscoverSettingsRows.toggle(store.load(), "showGetMoreAppsTile"))
        }, subtitle = "At the end of the shelf: popular apps, installed from the Play Store")
        val np = store.loadNowPlaying()
        val granted = NowPlayingAccess.isGranted(context)
        page.row("home.nowPlaying", "Now Playing", RowKind.Toggle(np.nowPlayingEnabled) { _ ->
            store.saveNowPlaying(DiscoverSettingsRows.toggleNowPlaying(store.loadNowPlaying(), "nowPlayingEnabled"))
            nav.refresh()
        }, subtitle = "What's playing, with play and skip, next to the app name")
        if (WaveTwoSections.accessCardShown(np.nowPlayingEnabled, granted)) {
            page.add(NotificationAccessCard(context, NotificationAccessCard.NOW_PLAYING_TITLE, NotificationAccessCard.NOW_PLAYING_BODY))
        }
        page.advanced(nav, "home.advanced", "Suggestion region and hidden suggestions") { adv ->
            val detected = RegionGuess.fromLocale(Locale.getDefault(), null)
            val regions = DiscoverSettingsRows.REGIONS
            val index = regions.indexOf(d.region).coerceAtLeast(0)
            adv.row("home.discoverRegion", "Region for suggestions", RowKind.Choice(WaveTwoSections.regionLabels(detected), index) { i ->
                store.save(store.load().copy(region = regions[i.coerceIn(0, regions.size - 1)]))
            })
            if (d.hiddenCatalogIds.isNotEmpty()) {
                adv.row("home.restoreHidden", "Show hidden suggestions again", RowKind.Action("${d.hiddenCatalogIds.size} hidden") {
                    store.save(DiscoverSettingsRows.restoreHidden(store.load()))
                    nav.refresh()
                })
            }
        }
        return page
    }
}
