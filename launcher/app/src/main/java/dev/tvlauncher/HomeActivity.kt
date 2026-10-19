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
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.launch.AndroidLaunchBackend
import dev.tvlauncher.launch.AppLauncher
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
    private lateinit var profile: DeviceProfile
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
        repo = AppRepository(applicationContext)
        prefs = Prefs(applicationContext)
        profile = DeviceProfileReader.read(this)
        buildUi()
        val wantsSpotlight = intent?.action == SpotlightKeys.ACTION
        if (wantsSpotlight) setIntent(Intent(intent).setAction(null)) // a recreation must not re-open it
        if (SplashOverlay.enabled(this) && !wantsSpotlight) {
            splash = SplashOverlay(this, profile.effectsMode == EffectsMode.FULL).also {
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
        val full = profile.effectsMode == EffectsMode.FULL
        val root = FrameLayout(this)
        backdrop = BackdropView(this, if (full) 350L else 250L)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        topBar = TopBar(
            this,
            onInputs = { SystemActions.inputs(this) },
            onSettings = { if (!SystemActions.launcherSettings(this)) openSpotlight() },
            onSettingsLongPress = { tileMenu.showDockMenu(visiblePackages(), installed) },
            onDown = ::onTopBarDown
        )
        heroBox = HeroBox(this, packageManager)
        launcher = AppLauncher(profile, AndroidLaunchBackend(this))
        tileMenu = TileMenu(this, prefs) { reload() }
        adapter = ShelfAdapter(
            packageManager, profile,
            onFocused = { pos, app, tile -> onAppFocused(app); strip.onTileFocused(pos, tile, app) },
            onClick = { app, v -> if (launcher.launch(app, v)) launchOverlay.show() },
            onLongPress = { app -> tileMenu.show(app, visiblePackages()) },
            onDown = { onTileDown() },
            onDpad = { strip.onTileDpad() }
        )
        shelfBox = Shelf(this, adapter, dp(Theme.MARGIN_H))
        shelf = shelfBox.list
        strip = QuickStrip(this, full, shelf, isFavorite = { tileMenu.isFavorite(it) }) { action, app ->
            tileMenu.stripAction(action, app, visiblePackages())
        }
        content = HomeContent(this, topBar, heroBox, shelfBox, strip)
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        spotlight = SpotlightPanel(
            this, packageManager, full,
            onAction = { SystemActions.quickAction(this, it) },
            onActionLongPress = { tileMenu.showDockMenu(visiblePackages(), installed) },
            onLaunch = { app, v -> if (launcher.launch(app, v)) launchOverlay.show() },
            onClosed = ::onSpotlightClosed
        )
        root.addView(spotlight, FrameLayout.LayoutParams(-1, -1))
        launchOverlay = LaunchOverlay(this)
        root.addView(launchOverlay, FrameLayout.LayoutParams(-1, -1))
        root.viewTreeObserver.addOnGlobalFocusChangeListener { _, now -> strip.onGlobalFocus(now) }
        setContentView(root)
    }

    private fun visiblePackages() = adapter.getItems().map { it.packageName }

    private fun onAppFocused(app: AppEntry) {
        backdrop.setAccent(adapter.accentOf(app))
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

    private fun openSpotlight() {
        if (SpotlightKeys.onRequest(appsLoaded = !firstLoad) == SpotlightKeys.Request.DEFER) {
            pendingSpotlight = true
            return
        }
        if (spotlight.isOpen || splash != null || isDestroyed) return // not under the cold-start splash
        val f = currentFocus
        restoreFocus = f?.takeIf { it.parent !== shelf && !strip.hasFocus() }
        restoreIndex = adapter.lastFocused
        strip.setSpotlightOpen(true)
        spotlight.open()
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
        if (!SpotlightKeys.isToggleKey(event.keyCode)) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            if (spotlight.isOpen) spotlight.close() else openSpotlight()
        }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == SpotlightKeys.ACTION) {
            openSpotlight()
        } else {
            spotlight.close(animate = false) // HOME press: back to a clean shelf
            focusTile(0)
        }
    }

    override fun onPause() {
        launchOverlay.hide()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        topBar.startStatus()
    }

    override fun onStop() {
        launchOverlay.hide()
        spotlight.close(animate = false)
        topBar.stopStatus()
        super.onStop()
    }

    /** BACK closes Spotlight, or leaves the quick-strip for its tile. The launcher never finishes. */
    @Deprecated("Home screen never exits on Back")
    override fun onBackPressed() {
        if (spotlight.isOpen) spotlight.close() else if (strip.hasFocus()) strip.returnToTile()
    }

    override fun onDestroy() {
        tileMenu.dismiss()
        repo.unregister()
        loader.shutdownNow()
        super.onDestroy()
    }
}
