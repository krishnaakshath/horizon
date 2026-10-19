package dev.tvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppFilter
import dev.tvlauncher.apps.AppRepository
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.discover.DiscoverStore
import dev.tvlauncher.discover.DiscoverTileSpec
import dev.tvlauncher.home.HomeAppearance
import dev.tvlauncher.home.HomeLook
import dev.tvlauncher.home.HomeModules
import dev.tvlauncher.home.HomeSounds
import dev.tvlauncher.home.ModeTracker
import dev.tvlauncher.home.RecentMode
import dev.tvlauncher.homelayout.HomeLayoutController
import dev.tvlauncher.homelayout.NavResult
import dev.tvlauncher.input.GamepadInput
import dev.tvlauncher.input.PadCommand
import dev.tvlauncher.launch.AndroidLaunchBackend
import dev.tvlauncher.launch.AppLauncher
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.spotlight.SpotlightConfig
import dev.tvlauncher.spotlight.SpotlightPrefs
import dev.tvlauncher.spotlight.TriggerDetector
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.FocusRules
import dev.tvlauncher.ui.HeroBox
import dev.tvlauncher.ui.HomeContent
import dev.tvlauncher.ui.LaunchOverlay
import dev.tvlauncher.ui.QuickStrip
import dev.tvlauncher.ui.Shelf
import dev.tvlauncher.ui.ShelfAdapter
import dev.tvlauncher.ui.SplashOverlay
import dev.tvlauncher.ui.SplashReveal
import dev.tvlauncher.ui.SpotlightKeys
import dev.tvlauncher.ui.SpotlightPanel
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.TileMenu
import dev.tvlauncher.ui.TopBar
import dev.tvlauncher.ui.TopBarNav
import dev.tvlauncher.ui.dp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HomeActivity : Activity() {
    private val volume by lazy { dev.tvlauncher.volume.LauncherVolume(this) }
    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private lateinit var look: HomeLook
    private lateinit var launcher: AppLauncher
    private lateinit var tileMenu: TileMenu
    private lateinit var adapter: ShelfAdapter
    private lateinit var backdrop: BackdropView
    private lateinit var content: HomeContent
    private lateinit var shelf: RecyclerView
    private lateinit var topBar: TopBar
    private lateinit var strip: QuickStrip
    private lateinit var spotlight: SpotlightPanel
    private lateinit var heroBox: HeroBox
    private lateinit var shelfBox: Shelf
    private lateinit var launchOverlay: LaunchOverlay
    private lateinit var sounds: HomeSounds
    private lateinit var modes: ModeTracker
    private lateinit var root: FrameLayout
    private lateinit var modules: HomeModules
    /** Profiles: the top-bar avatar chip, the switcher panel and the live switch (profiles.ProfileHome). */
    private lateinit var profiles: dev.tvlauncher.profiles.ProfileHome
    /** Routines and Bedtime mode applied while Home is in front (room lighting repaints at once). */
    private val routineListener = dev.tvlauncher.routines.RoutineSignals.Listener { if (!redirected) modules.room.refresh() }
    /** Settings > Home layout: the style, its list layout and its D-pad rules. */
    private lateinit var layout: HomeLayoutController
    /** The focused tile may show the quick-strip (bottom row of a multi-row style, strip turned on). */
    private var stripHere = true
    private val loader = Executors.newSingleThreadExecutor()
    private val discoverStore by lazy { DiscoverStore(applicationContext) }
    private val generation = AtomicInteger()
    private var firstLoad = true
    private var splash: SplashOverlay? = null
    @Volatile private var installed: List<AppEntry> = emptyList()
    /** Leading favorites in the arranged list (the top row of "Two rows"); written by the loader. */
    @Volatile private var favoriteCount = 0
    /** Where focus returns when Spotlight closes: a non-shelf view, else the tile at [restoreIndex]. */
    private var restoreFocus: View? = null
    private var restoreIndex = 0
    /** Spotlight was requested before the first app load; opened once it completes. */
    private var pendingSpotlight = false
    /** Settings > Spotlight keys, re-read in onStart (they are edited in the Settings screen). */
    private val spotlightPrefs by lazy { SpotlightPrefs(applicationContext) }
    private var spotlightConfig = SpotlightConfig.DEFAULT
    private val spotlightKeys = TriggerDetector(now = { android.os.SystemClock.uptimeMillis() })

    /** Game controllers: Y/Start toggle Spotlight, Select opens Settings, Guide resets the shelf. */
    private val pad = GamepadInput(this, object : GamepadInput.Host {
        override fun onPadCommand(command: PadCommand, repeat: Int): Boolean = when {
            redirected -> false
            command == PadCommand.SEARCH || command == PadCommand.START -> { toggleSpotlight(); true }
            command == PadCommand.SELECT -> { if (SystemActions.launcherSettings(this@HomeActivity)) sounds.action(true); true }
            command == PadCommand.HOME -> { goHome(); true }
            command == PadCommand.PAGE_PREV || command == PadCommand.PAGE_NEXT -> pageShelf(command == PadCommand.PAGE_NEXT)
            else -> false
        }
    })

    /** True when onCreate returned early (redirected to safe mode): nothing is built, so lifecycle callbacks must not touch it. */
    private val redirected get() = !::modes.isInitialized

    /** Settings > Accessibility > Text size scales every launcher text. */
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(dev.tvlauncher.system.A11yScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SafeMode.redirectIfTripped(this)) return // repeated crashes: the tiny safe-mode launcher
        repo = AppRepository(applicationContext)
        prefs = Prefs(applicationContext)
        look = HomeLook(this, DeviceProfileReader.read(this))
        sounds = HomeSounds(this)
        buildUi()
        profiles.onCreated() // after a profile switch: fade in and "Hi, <name>"
        modes = ModeTracker(this) { heroBox.hero.setModeChip(RecentMode.chipText(it)) }
        val wantsSpotlight = intent?.action == SpotlightKeys.ACTION
        if (wantsSpotlight) setIntent(Intent(intent).setAction(null)) // a recreation must not re-open it
        if (look.showSplash && !wantsSpotlight && !HomeLook.isRebuild(intent)) {
            splash = SplashOverlay(this, look.profile.full, look.accent { dev.tvlauncher.ui.SplashLook.DEFAULT_ACCENT }).also {
                (content.parent as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
                it.prepare(content.revealTargets())
                it.start()
                shelfBox.loader.visibility = View.GONE // the splash is the loading moment
            }
        }
        if (wantsSpotlight) openSpotlight() // deferred until the first app list arrives
        repo.register { reload() }
        reload()
        dev.tvlauncher.setup.SetupStatus.onHomeCreated(this, wantsSpotlight) // first run only; never blocks Home
    }

    private fun buildUi() {
        val full = look.profile.full
        root = FrameLayout(this)
        backdrop = BackdropView(this, look.backdropMs).apply { glassBlur = full }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        topBar = TopBar(
            this,
            onNetwork = { v -> modules.openWifi(v) },
            onBluetooth = { v -> modules.openBluetooth(v) },
            // The Inputs pill opens the Inputs panel; a TV without inputs keeps the system switch.
            onInputs = { if (!modules.panels.openInputs(topBar.inputsButton)) sounds.action(SystemActions.inputs(this)) },
            onSettings = { if (SystemActions.launcherSettings(this)) sounds.action(true) else openSpotlight() },
            onSettingsLongPress = { tileMenu.showDockMenu(visiblePackages(), installed); sounds.menuOpen() },
            onDown = ::onTopBarDown,
            onProfile = { if (!spotlight.isOpen && !modules.isShowing && profiles.open()) sounds.panel(opened = true) }
        )
        heroBox = HeroBox(this, packageManager)
        launcher = AppLauncher(look.profile.device, AndroidLaunchBackend(this))
        tileMenu = TileMenu(this, prefs) { reload() }.also(sounds::bind)
        adapter = ShelfAdapter(
            packageManager, look.profile,
            onFocused = { pos, app, tile -> onAppFocused(app); onTileFocusedForStrip(pos, tile, app) },
            onClick = ::launch,
            onLongPress = { app -> tileMenu.show(app, visiblePackages()); sounds.menuOpen() },
            onDown = { onTileDown() },
            onDpad = { if (stripHere) strip.onTileDpad() },
            onFooterClick = { modules.openDiscover() },
            onFooterFocused = ::onFooterFocused
        )
        shelfBox = Shelf(this, adapter, dp(Theme.MARGIN_H))
        shelf = shelfBox.list
        strip = QuickStrip(this, full, shelf, isFavorite = { tileMenu.isFavorite(it) }) { action, app ->
            sounds.stripAction(opensMenu = action == QuickStrip.Action.MORE)
            tileMenu.stripAction(action, app, visiblePackages())
        }
        content = HomeContent(this, topBar, heroBox, shelfBox, strip)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        layout = HomeLayoutController(this, look, adapter, shelfBox, content, heroBox).also { it.applyInitial() }
        adapter.onNavKey = ::onShelfKey
        spotlight = SpotlightPanel(
            this, packageManager, full,
            onAction = { modules.quickAction(it) },
            onActionLongPress = { tileMenu.showDockMenu(visiblePackages(), installed); sounds.menuOpen() },
            onLaunch = ::launch,
            onClosed = ::onSpotlightClosed,
            iconFor = { modules.actionIcon(it) }
        )
        root.addView(spotlight, FrameLayout.LayoutParams(-1, -1))
        launchOverlay = LaunchOverlay(this)
        root.addView(launchOverlay, FrameLayout.LayoutParams(-1, -1))
        modules = HomeModules(
            this, root, look, sounds, topBar, backdrop, spotlight, { splash != null }, ::focusHome,
            heroBox, installedPackages = { installed.mapTo(HashSet()) { it.packageName } },
            onDiscoverClosed = { focusPosition(adapter.footerPosition) || focusTile(adapter.lastFocused) }
        )
        profiles = dev.tvlauncher.profiles.ProfileHome(this, root, lite = !full, chip = topBar.profileChip) { open ->
            modules.ambient.setPaused(dev.tvlauncher.home.HomeAmbient.PAUSE_PANEL, open)
        }
        dev.tvlauncher.routines.RoutineSignals.add(routineListener)
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> strip.onGlobalFocus(now); sounds.onFocusChanged() }
        setContentView(root)
    }

    private fun launch(app: AppEntry, v: View) = launcher.launch(app, v).let {
        sounds.launch(it)
        if (it) {
            launchOverlay.show()
            try { spotlightPrefs.recordLaunch(app.packageName) } catch (e: RuntimeException) { } // Spotlight's "recent"
        }
    }

    private fun visiblePackages() = adapter.getItems().map { it.packageName }

    /** The "Get more apps" tile: no app accent, a plain hero line, and no quick-strip. */
    private fun onFooterFocused() {
        strip.reset()
        heroBox.hero.showText(DiscoverTileSpec.LABEL, HERO_DISCOVER_SUBTITLE, backdrop.heroColor)
    }

    private fun onAppFocused(app: AppEntry) {
        backdrop.setAccent(look.accent { adapter.accentOf(app) })
        heroBox.hero.show(app, backdrop.heroColor)
        layout.onAppFocused(app, adapter.accentOf(app))
    }

    /** The quick-strip follows tiles on the bottom row only, and not at all when it is turned off. */
    private fun onTileFocusedForStrip(pos: Int, tile: View, app: AppEntry) {
        stripHere = layout.stripAllowed(pos)
        if (stripHere) strip.onTileFocused(pos, tile, app) else strip.reset()
    }

    /** Arrow and page keys on a tile: two rows and the grid move focus themselves; the single row passes. */
    private fun onShelfKey(pos: Int, keyCode: Int): Boolean = applyNav(layout.navKey(pos, keyCode))

    private fun applyNav(r: NavResult?): Boolean = when (r) {
        is NavResult.Move -> { if (stripHere) strip.onTileDpad(); focusPosition(r.position) }
        NavResult.ExitDown -> onTileDown()
        NavResult.Stay -> true
        NavResult.ExitUp, NavResult.Pass, null -> false
    }

    /** L1/R1 on a controller: a page of the grid or of two rows; false = the default page jump. */
    private fun pageShelf(forward: Boolean): Boolean {
        if (spotlight.isOpen || modules.isShowing) return false
        val child = shelf.focusedChild ?: return false
        val pos = shelf.getChildAdapterPosition(child).takeIf { it != RecyclerView.NO_POSITION } ?: return false
        return when (val r = layout.pageKey(pos, forward)) {
            NavResult.Pass -> false
            else -> applyNav(r)
        }
    }

    private fun onTileDown(): Boolean = when (SpotlightKeys.onTileDown(strip.isStripShown)) {
        SpotlightKeys.TileDown.STRIP -> strip.focusFirst()
        SpotlightKeys.TileDown.SPOTLIGHT -> { openSpotlight(); true }
    }

    private fun onTopBarDown(): Boolean = when (TopBarNav.onDown(shelfEmpty = adapter.appCount == 0)) {
        TopBarNav.Action.FOCUS_SHELF -> focusTile(adapter.lastFocused)
        TopBarNav.Action.OPEN_SPOTLIGHT -> { openSpotlight(); true }
    }

    private fun focusHome() { if (!focusTile(adapter.lastFocused)) topBar.inputsButton.requestFocus() }

    private fun openSpotlight() {
        if (SpotlightKeys.onRequest(appsLoaded = !firstLoad) == SpotlightKeys.Request.DEFER) {
            pendingSpotlight = true
            return
        }
        if (spotlight.isOpen || splash != null || isDestroyed || modules.isShowing || profiles.isShowing) return // not under the splash or a panel
        val f = currentFocus
        restoreFocus = f?.takeIf { it.parent !== shelf && !strip.hasFocus() }
        restoreIndex = adapter.lastFocused
        strip.setSpotlightOpen(true)
        modules.spotlightChanged(open = true)
        spotlight.open()
        sounds.panel(opened = true)
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS // nothing leaks to the shelf
    }

    private fun onSpotlightClosed() {
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        strip.setSpotlightOpen(false)
        modules.spotlightChanged(open = false)
        val back = restoreFocus?.takeIf { it.isAttachedToWindow }?.requestFocus() ?: false
        if (!back && !focusTile(restoreIndex)) topBar.inputsButton.requestFocus()
        restoreFocus = null
    }

    private fun reload() {
        val gen = generation.incrementAndGet()
        loader.execute {
            val result = try {
                val all = repo.load()
                installed = all
                val s = prefs.load()
                val shown = AppFilter.arrange(all, s).also { adapter.preload(it, ShelfAdapter.PRELOAD) }
                val favs = s.favorites.toHashSet()
                favoriteCount = shown.takeWhile { it.packageName in favs }.size
                Triple(shown, AppFilter.searchable(all, shown, s), DiscoverTileSpec.shouldAppend(discoverStore.load(), editing = false))
            } catch (e: Exception) {
                Triple(emptyList<AppEntry>(), emptyList<AppEntry>(), false)
            }
            runOnUiThread { if (gen == generation.get() && !isDestroyed) applyApps(result.first, result.second, result.third) }
        }
    }

    private fun applyApps(apps: List<AppEntry>, searchable: List<AppEntry>, footer: Boolean) {
        val old = adapter.getItems()
        val footerFocused = adapter.footer && shelf.focusedChild?.let { shelf.getChildAdapterPosition(it) } == adapter.footerPosition
        val hadFocus = shelf.hasFocus() || strip.hasFocus() || (spotlight.isOpen && restoreFocus == null)
        val oldPos = if (spotlight.isOpen) restoreIndex else adapter.lastFocused
        val focusedPkg = old.getOrNull(oldPos)?.packageName
        strip.reset()
        adapter.submit(apps, footer, favoriteCount)
        layout.listChanged()
        spotlight.setApps(searchable, installed.mapTo(HashSet()) { it.packageName })
        shelfBox.loader.visibility = View.GONE
        layout.setEmpty(apps.isEmpty())
        val wasFirst = firstLoad
        firstLoad = false
        if (wasFirst) {
            sounds.readyIn(if (splash != null) SplashReveal.totalMs(look.profile.full) else 0L)
            splash?.let { s -> shelf.post { s.reveal(content.revealTargets()) } }
            splash = null
            if (pendingSpotlight) shelf.post { pendingSpotlight = false; openSpotlight() }
            else shelf.post { if (!isDestroyed && !spotlight.isOpen && !modules.isShowing) profiles.maybeAskOnStart() }
        }
        if (apps.isEmpty()) {
            if ((hadFocus || wasFirst) && !spotlight.isOpen) topBar.inputsButton.requestFocus()
            return
        }
        if (footerFocused && !spotlight.isOpen && adapter.footer) { focusPosition(adapter.footerPosition); return }
        val target = when {
            wasFirst -> 0
            !hadFocus -> null
            else -> apps.indexOfFirst { it.packageName == focusedPkg }.takeIf { it >= 0 }
                ?: FocusRules.nextFocusIndexAfterRemoval(oldPos, apps.size)
        } ?: return
        val i = target.coerceIn(0, apps.size - 1)
        if (spotlight.isOpen) restoreIndex = i else focusTile(i)
        // A reload can reorder apps under the same index; keep hero/backdrop in sync.
        onAppFocused(apps[i])
    }

    private fun focusTile(index: Int): Boolean {
        if (adapter.appCount == 0) return false
        return focusPosition(adapter.positionOf(index.coerceIn(0, adapter.appCount - 1)))
    }

    /**
     * Any shelf position, the footer tile included; false when there is none. A tile already laid
     * out takes focus at once (the list scrolls it into view); otherwise the list scrolls (or the
     * grid flips to its page) and focus follows once it is laid out.
     */
    private fun focusPosition(i: Int): Boolean {
        if (i < 0 || i >= adapter.itemCount) return false
        val paged = layout.showPageOf(i)
        val now = shelf.findViewHolderForAdapterPosition(i)?.itemView
        if (!paged && now != null && now.isAttachedToWindow && now.isLaidOut && now.requestFocus()) return true
        if (!paged) shelf.scrollToPosition(i)
        focusWhenLaidOut(i, FOCUS_TRIES)
        return true
    }

    private fun focusWhenLaidOut(i: Int, tries: Int) {
        shelf.post {
            val v = shelf.findViewHolderForAdapterPosition(i)?.itemView
            if (v != null && !shelf.isLayoutRequested) v.requestFocus()
            else if (tries > 1) focusWhenLaidOut(i, tries - 1)
            else v?.requestFocus()
        }
    }

    /** Volume keys first (our volume bar), then the controller mapping; remote keys reach [handleKey] unchanged. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (redirected) return super.dispatchKeyEvent(event)
        if (volume.dispatch(event)) return true // our volume bar; false = the TV handles the key
        return pad.dispatch(event, ::handleKey)
    }

    /** Controller sticks; a real push counts as activity (onUserInteraction), drift does not. */
    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        (!redirected && pad.motion(ev)) || super.dispatchGenericMotionEvent(ev)

    /** The controller's Y / Start: the same toggle as MENU / SEARCH (not over the tile menu). */
    private fun toggleSpotlight() {
        if (tileMenu.isVisible) return
        if (spotlight.isOpen) { spotlight.close(); sounds.panel(opened = false) } else openSpotlight()
    }

    /** The controller's Guide button, like a HOME press: back to a clean shelf. */
    private fun goHome() {
        spotlight.close(animate = false)
        modules.closeAll()
        profiles.closeAll()
        tileMenu.dismiss()
        focusTile(0)
    }

    /**
     * The Spotlight keys (Settings > Spotlight: Menu and Search by default, an extra key, a
     * double-press key) toggle it from anywhere on the home screen (including its search
     * field), not over the tile menu.
     */
    private fun handleKey(event: KeyEvent): Boolean {
        if (redirected) return super.dispatchKeyEvent(event)
        modules.onUserActivity() // any key, repeats included, resets the ambient idle timer
        sounds.onKey(event)
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && event.action != KeyEvent.ACTION_UP) return super.dispatchKeyEvent(event)
        return when (spotlightKeys.onKey(event.keyCode, down, event.repeatCount, spotlightConfig, tileMenu.isVisible)) {
            TriggerDetector.Decision.PASS -> super.dispatchKeyEvent(event)
            TriggerDetector.Decision.CONSUME -> true
            TriggerDetector.Decision.TOGGLE -> {
                if (spotlight.isOpen) { spotlight.close(); sounds.panel(opened = false) } else openSpotlight()
                true
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (redirected) return
        if (intent?.action == SpotlightKeys.ACTION) {
            openSpotlight()
        } else {
            spotlight.close(animate = false) // HOME press: back to a clean shelf
            modules.closeAll()
            profiles.closeAll()
            focusTile(0)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        if (!redirected) modules.onUserActivity()
    }

    override fun onPause() {
        if (redirected) return super.onPause()
        modules.pause()
        launchOverlay.hide()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (redirected || isChangingConfigurations || isFinishing) return // e.g. the appearance rebuild in onStart
        sounds.reload()
        modes.onResume()
        modules.resume()
    }

    /** Also picks up Settings > Appearance edits when coming back from Settings. */
    override fun onStart() {
        super.onStart()
        if (redirected) return
        if (profiles.start()) return // switched elsewhere (Settings, a routine): rebuilt for that profile
        dev.tvlauncher.routines.Bedtime.onHomeStart(this) // ends an expired Bedtime mode
        if (look.reload() == HomeAppearance.Change.REBUILD) return HomeLook.rebuild(this)
        spotlightConfig = try { spotlightPrefs.load() } catch (e: RuntimeException) { SpotlightConfig.DEFAULT }
        spotlightKeys.reset()
        topBar.applyAppearance(look.settings)
        // Settings > Home layout (style, names, hero, strip) and tile size: relaid out in place.
        if (layout.reload()) {
            strip.reset()
            if (adapter.appCount > 0 && !firstLoad) focusTile(adapter.lastFocused)
        }
        // Settings > Home screen may have turned the "Get more apps" tile on or off.
        val footer = DiscoverTileSpec.shouldAppend(discoverStore.load(), editing = false)
        if (!firstLoad && footer != adapter.footer && adapter.appCount > 0) adapter.submit(adapter.getItems(), footer)
        adapter.getItems().getOrNull(adapter.lastFocused)?.let(::onAppFocused)
        topBar.startStatus()
        modules.start()
    }

    override fun onStop() {
        if (redirected) return super.onStop()
        if (!isChangingConfigurations && !isFinishing) {
            launchOverlay.hide()
            // "Close after an action" off: Spotlight is still there when the owner comes back.
            if (!spotlight.keepOpenOnStop) spotlight.close(animate = false)
        }
        volume.release()
        profiles.stop()
        topBar.stopStatus() // always paired with startStatus (TopBar tracks it)
        modules.stop() // panels closed; controllers, device monitor and idle timer stopped
        super.onStop()
    }

    private companion object {
        const val HERO_DISCOVER_SUBTITLE = "Popular apps for this TV, from the Play Store"
        /** Frames to wait for a scrolled list before focusing its tile. */
        const val FOCUS_TRIES = 4
    }

    /** BACK closes a panel or Spotlight, or leaves the quick-strip for its tile. The launcher never finishes. */
    @Deprecated("Home screen never exits on Back")
    override fun onBackPressed() {
        if (redirected) return super.onBackPressed()
        val closed = when {
            profiles.onBack() -> true
            modules.onBack() -> true
            spotlight.isOpen -> { spotlight.close(); true }
            else -> strip.hasFocus() && strip.returnToTile()
        }
        sounds.back(closed)
    }

    override fun onDestroy() {
        if (redirected) return super.onDestroy()
        tileMenu.dismiss()
        profiles.release()
        dev.tvlauncher.routines.RoutineSignals.remove(routineListener)
        modules.destroy()
        repo.unregister()
        loader.shutdownNow()
        sounds.release()
        modes.release()
        super.onDestroy()
    }
}
