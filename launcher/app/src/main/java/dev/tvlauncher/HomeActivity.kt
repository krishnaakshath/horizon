package dev.tvlauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import dev.tvlauncher.apps.AppEntry
import dev.tvlauncher.apps.AppFilter
import dev.tvlauncher.apps.AppRepository
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.home.HomeAppearance
import dev.tvlauncher.home.HomeLook
import dev.tvlauncher.home.HomeNetwork
import dev.tvlauncher.home.HomeSounds
import dev.tvlauncher.home.ModeTracker
import dev.tvlauncher.home.RecentMode
import dev.tvlauncher.launch.AndroidLaunchBackend
import dev.tvlauncher.launch.AppLauncher
import dev.tvlauncher.safemode.SafeMode
import dev.tvlauncher.settings.Prefs
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
    private lateinit var net: HomeNetwork
    private val loader = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var firstLoad = true
    private var splash: SplashOverlay? = null
    @Volatile private var installed: List<AppEntry> = emptyList()
    /** Where focus returns when Spotlight closes: a non-shelf view, else the tile at [restoreIndex]. */
    private var restoreFocus: View? = null
    private var restoreIndex = 0
    /** Spotlight was requested before the first app load; opened once it completes. */
    private var pendingSpotlight = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (SafeMode.redirectIfTripped(this)) return // repeated crashes: the tiny safe-mode launcher
        repo = AppRepository(applicationContext)
        prefs = Prefs(applicationContext)
        look = HomeLook(this, DeviceProfileReader.read(this))
        sounds = HomeSounds(this)
        buildUi()
        modes = ModeTracker(this) { heroBox.hero.setModeChip(RecentMode.chipText(it)) }
        val wantsSpotlight = intent?.action == SpotlightKeys.ACTION
        if (wantsSpotlight) setIntent(Intent(intent).setAction(null)) // a recreation must not re-open it
        if (look.showSplash && !wantsSpotlight && !HomeLook.isRebuild(intent)) {
            splash = SplashOverlay(this, look.profile.full).also {
                (content.parent as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
                it.prepare(content.revealTargets())
                it.start()
                shelfBox.loader.visibility = View.GONE // the splash shows its own dots
            }
        }
        if (wantsSpotlight) openSpotlight() // deferred until the first app list arrives
        repo.register { reload() }
        reload()
    }

    private fun buildUi() {
        val full = look.profile.full
        root = FrameLayout(this)
        backdrop = BackdropView(this, look.backdropMs)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        topBar = TopBar(
            this,
            onNetwork = { v -> openPanel { net.showWifi(v) } },
            onBluetooth = { v -> openPanel { net.showBluetooth(v) } },
            onInputs = { sounds.action(SystemActions.inputs(this)) },
            onSettings = { if (SystemActions.launcherSettings(this)) sounds.action(true) else openSpotlight() },
            onSettingsLongPress = { tileMenu.showDockMenu(visiblePackages(), installed); sounds.menuOpen() },
            onDown = ::onTopBarDown
        )
        heroBox = HeroBox(this, packageManager)
        launcher = AppLauncher(look.profile.device, AndroidLaunchBackend(this))
        tileMenu = TileMenu(this, prefs) { reload() }.also(sounds::bind)
        adapter = ShelfAdapter(
            packageManager, look.profile,
            onFocused = { pos, app, tile -> onAppFocused(app); strip.onTileFocused(pos, tile, app) },
            onClick = ::launch,
            onLongPress = { app -> tileMenu.show(app, visiblePackages()); sounds.menuOpen() },
            onDown = { onTileDown() },
            onDpad = { strip.onTileDpad() }
        )
        shelfBox = Shelf(this, adapter, dp(Theme.MARGIN_H))
        shelf = shelfBox.list
        strip = QuickStrip(this, full, shelf, isFavorite = { tileMenu.isFavorite(it) }) { action, app ->
            sounds.stripAction(opensMenu = action == QuickStrip.Action.MORE)
            tileMenu.stripAction(action, app, visiblePackages())
        }
        content = HomeContent(this, topBar, heroBox, shelfBox, strip)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        spotlight = SpotlightPanel(
            this, packageManager, full,
            onAction = { sounds.action(SystemActions.quickAction(this, it)) },
            onActionLongPress = { tileMenu.showDockMenu(visiblePackages(), installed); sounds.menuOpen() },
            onLaunch = ::launch,
            onClosed = ::onSpotlightClosed
        )
        root.addView(spotlight, FrameLayout.LayoutParams(-1, -1))
        launchOverlay = LaunchOverlay(this)
        root.addView(launchOverlay, FrameLayout.LayoutParams(-1, -1))
        net = HomeNetwork(this, root, lite = !full, fallbackFocus = ::focusHome, onOpenChanged = {})
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> strip.onGlobalFocus(now); sounds.onFocusChanged() }
        setContentView(root)
    }

    private fun launch(app: AppEntry, v: View) = launcher.launch(app, v).let { sounds.launch(it); if (it) launchOverlay.show() }

    private fun visiblePackages() = adapter.getItems().map { it.packageName }

    private fun onAppFocused(app: AppEntry) {
        backdrop.setAccent(look.accent { adapter.accentOf(app) })
        heroBox.hero.show(app, backdrop.heroColor)
    }

    private fun onTileDown(): Boolean = when (SpotlightKeys.onTileDown(strip.isStripShown)) {
        SpotlightKeys.TileDown.STRIP -> strip.focusFirst()
        SpotlightKeys.TileDown.SPOTLIGHT -> { openSpotlight(); true }
    }

    private fun onTopBarDown(): Boolean = when (TopBarNav.onDown(shelfEmpty = adapter.itemCount == 0)) {
        TopBarNav.Action.FOCUS_SHELF -> focusTile(adapter.lastFocused)
        TopBarNav.Action.OPEN_SPOTLIGHT -> { openSpotlight(); true }
    }

    /** Panels and Spotlight are mutually exclusive: Spotlight closes first, without animation. */
    private inline fun openPanel(open: () -> Boolean) {
        if (spotlight.isOpen) spotlight.close(animate = false)
        if (open()) sounds.panel(opened = true)
    }

    private fun focusHome() {
        if (!focusTile(adapter.lastFocused)) topBar.inputsButton.requestFocus()
    }

    private fun openSpotlight() {
        if (SpotlightKeys.onRequest(appsLoaded = !firstLoad) == SpotlightKeys.Request.DEFER) {
            pendingSpotlight = true
            return
        }
        if (spotlight.isOpen || splash != null || isDestroyed || net.isShowing) return // not under the splash or a panel
        val f = currentFocus
        restoreFocus = f?.takeIf { it.parent !== shelf && !strip.hasFocus() }
        restoreIndex = adapter.lastFocused
        strip.setSpotlightOpen(true)
        spotlight.open()
        sounds.panel(opened = true)
        content.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS // nothing leaks to the shelf
    }

    private fun onSpotlightClosed() {
        content.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        strip.setSpotlightOpen(false)
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
                shown to AppFilter.searchable(all, shown, s)
            } catch (e: Exception) {
                emptyList<AppEntry>() to emptyList()
            }
            runOnUiThread { if (gen == generation.get() && !isDestroyed) applyApps(result.first, result.second) }
        }
    }

    private fun applyApps(apps: List<AppEntry>, searchable: List<AppEntry>) {
        val old = adapter.getItems()
        val hadFocus = shelf.hasFocus() || strip.hasFocus() || (spotlight.isOpen && restoreFocus == null)
        val oldPos = if (spotlight.isOpen) restoreIndex else adapter.lastFocused
        val focusedPkg = old.getOrNull(oldPos)?.packageName
        strip.reset()
        adapter.submit(apps)
        spotlight.setApps(searchable, installed.mapTo(HashSet()) { it.packageName })
        shelfBox.loader.visibility = View.GONE
        heroBox.setEmpty(apps.isEmpty())
        val wasFirst = firstLoad
        firstLoad = false
        if (wasFirst) {
            sounds.readyIn(if (splash != null) SplashReveal.totalMs(look.profile.full) else 0L)
            splash?.let { s -> shelf.post { s.reveal(content.revealTargets()) } }
            splash = null
            if (pendingSpotlight) shelf.post { pendingSpotlight = false; openSpotlight() }
        }
        if (apps.isEmpty()) {
            if ((hadFocus || wasFirst) && !spotlight.isOpen) topBar.inputsButton.requestFocus()
            return
        }
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
        if (adapter.itemCount == 0) return false
        val i = index.coerceIn(0, adapter.itemCount - 1)
        shelf.scrollToPosition(i)
        shelf.post { shelf.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus() }
        return true
    }

    /** MENU / SEARCH toggle Spotlight from anywhere on the home screen (including its search field). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        sounds.onKey(event)
        if (!SpotlightKeys.isToggleKey(event.keyCode)) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (spotlight.isOpen) { spotlight.close(); sounds.panel(opened = false) } else openSpotlight()
        }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == SpotlightKeys.ACTION) {
            openSpotlight()
        } else {
            spotlight.close(animate = false) // HOME press: back to a clean shelf
            net.release()
            focusTile(0)
        }
    }

    override fun onPause() {
        launchOverlay.hide()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (isChangingConfigurations || isFinishing) return // e.g. the appearance rebuild in onStart
        sounds.reload()
        modes.onResume()
    }

    /** Also picks up Settings > Appearance edits when coming back from Settings. */
    override fun onStart() {
        super.onStart()
        if (look.reload() == HomeAppearance.Change.REBUILD) return HomeLook.rebuild(this)
        topBar.applyAppearance(look.settings)
        adapter.getItems().getOrNull(adapter.lastFocused)?.let(::onAppFocused)
        topBar.startStatus()
        net.start()
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing) {
            launchOverlay.hide()
            spotlight.close(animate = false)
        }
        topBar.stopStatus() // always paired with startStatus (TopBar tracks it)
        net.stop() // releases an open panel, stops the Wi-Fi and Bluetooth controllers
        super.onStop()
    }

    /** BACK closes a panel or Spotlight, or leaves the quick-strip for its tile. The launcher never finishes. */
    @Deprecated("Home screen never exits on Back")
    override fun onBackPressed() {
        val closed = when {
            net.onBack() -> true
            spotlight.isOpen -> { spotlight.close(); true }
            else -> strip.hasFocus() && strip.returnToTile()
        }
        sounds.back(closed)
    }

    override fun onDestroy() {
        if (!::modes.isInitialized) return super.onDestroy() // redirected to safe mode
        tileMenu.dismiss()
        net.release()
        repo.unregister()
        loader.shutdownNow()
        sounds.release()
        modes.release()
        super.onDestroy()
    }
}
