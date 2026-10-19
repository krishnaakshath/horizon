package dev.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
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
import dev.tvlauncher.ui.BouncingDots
import dev.tvlauncher.ui.LaunchOverlay
import dev.tvlauncher.ui.Dock
import dev.tvlauncher.ui.FocusRules
import dev.tvlauncher.ui.HeroView
import dev.tvlauncher.ui.RevealTargets
import dev.tvlauncher.ui.ShelfAdapter
import dev.tvlauncher.ui.SplashOverlay
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.TileMenu
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.TileView
import dev.tvlauncher.ui.TopBar
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
    private lateinit var content: LinearLayout
    private lateinit var shelf: RecyclerView
    private lateinit var topBar: TopBar
    private lateinit var dock: Dock
    private lateinit var hero: HeroView
    private lateinit var emptyText: TextView
    private lateinit var shelfLoader: BouncingDots
    private lateinit var launchOverlay: LaunchOverlay
    private val loader = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var firstLoad = true
    private var splash: SplashOverlay? = null
    @Volatile private var installed: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(applicationContext)
        prefs = Prefs(applicationContext)
        profile = DeviceProfileReader.read(this)
        buildUi()
        if (SplashOverlay.enabled(this)) {
            splash = SplashOverlay(this, profile.effectsMode == EffectsMode.FULL).also {
                (content.parent as FrameLayout).addView(it, FrameLayout.LayoutParams(-1, -1))
                it.prepare(revealTargets())
                it.start()
                shelfLoader.visibility = View.GONE // the splash shows its own dots

            }
        }
        repo.register { reload() }
        reload()
    }

    private fun buildUi() {
        val full = profile.effectsMode == EffectsMode.FULL
        val root = FrameLayout(this)
        backdrop = BackdropView(this, if (full) 350L else 250L)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        // Alpha fades on these full-screen containers must not allocate offscreen layers (LITE).
        content = object : LinearLayout(this) {
            override fun hasOverlappingRendering() = false
        }.apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(Theme.MARGIN_V), 0, dp(Theme.MARGIN_V))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
        val h = dp(Theme.MARGIN_H)

        topBar = TopBar(this) { SystemActions.inputs(this) }
        content.addView(topBar, margins(LinearLayout.LayoutParams(-1, -2), h))

        hero = HeroView(this, packageManager)
        emptyText = TextView(this).apply {
            text = "No apps found — open the Play Store"
            setTextColor(Theme.TEXT_2)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val heroBox = FrameLayout(this)
        heroBox.addView(hero, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
        heroBox.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        content.addView(heroBox, margins(LinearLayout.LayoutParams(-1, 0, 1f), h))

        launcher = AppLauncher(profile, AndroidLaunchBackend(this))
        tileMenu = TileMenu(this, prefs) { reload() }
        adapter = ShelfAdapter(
            packageManager, profile,
            onFocused = { _, app -> onAppFocused(app) },
            onClick = { app, v -> if (launcher.launch(app, v)) launchOverlay.show() },
            onLongPress = { app -> tileMenu.show(app, adapter.getItems().map { it.packageName }) },
            onDown = { dock.focusFirst() }
        )
        val pad = TileSpec.PAD_DP
        shelf = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@HomeActivity.adapter
            clipToPadding = false
            clipChildren = false
            itemAnimator = null
            setPadding(h - dp(pad), dp(pad), h - dp(pad), dp(pad))
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: Rect, v: View, p: RecyclerView, s: RecyclerView.State) {
                    out.right = dp(TileSpec.GAP_DP - 2 * pad)
                }
            })
        }
        shelfLoader = BouncingDots(this)
        val shelfBox = FrameLayout(this).apply { clipChildren = false; clipToPadding = false }
        shelfBox.addView(shelf, FrameLayout.LayoutParams(-1, -1))
        shelfBox.addView(shelfLoader, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        content.addView(shelfBox, LinearLayout.LayoutParams(-1, dp(TileSpec.VIEW_HEIGHT_DP + 2 * pad)))

        dock = Dock(
            this, translucent = full,
            onInputs = { SystemActions.inputs(this) },
            onSettings = { SystemActions.settings(this) },
            onSettingsLongPress = {
                tileMenu.showDockMenu(adapter.getItems().map { it.packageName }, installed)
            },
            onUp = { focusTile(adapter.lastFocused) }
        )
        content.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Theme.S3) })
        launchOverlay = LaunchOverlay(this)
        root.addView(launchOverlay, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    private fun margins(lp: LinearLayout.LayoutParams, h: Int) = lp.apply { marginStart = h; marginEnd = h }

    private fun revealTargets() = RevealTargets(content, topBar, hero, shelf, dock) {
        (0 until shelf.childCount).map { shelf.getChildAt(it) }
            .sortedBy { it.left }
            .mapNotNull { (it as? TileView)?.content }
    }

    private fun onAppFocused(app: AppEntry) {
        backdrop.setAccent(adapter.accentOf(app))
        hero.show(app, backdrop.heroColor)
    }

    private fun reload() {
        val gen = generation.incrementAndGet()
        loader.execute {
            val apps = try {
                val all = repo.load()
                installed = all
                AppFilter.arrange(all, prefs.load()).also { adapter.preload(it, PRELOAD) }
            } catch (e: Exception) {
                emptyList()
            }
            runOnUiThread { if (gen == generation.get() && !isDestroyed) applyApps(apps) }
        }
    }

    private fun applyApps(apps: List<AppEntry>) {
        val old = adapter.getItems()
        val hadFocus = shelf.hasFocus()
        val oldPos = adapter.lastFocused
        val focusedPkg = old.getOrNull(oldPos)?.packageName
        adapter.submit(apps)
        shelfLoader.visibility = View.GONE
        emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        hero.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        val wasFirst = firstLoad
        firstLoad = false
        if (wasFirst) {
            apps.firstOrNull()?.let { app ->
                backdrop.setAccent(adapter.accentOf(app))
                hero.show(app, backdrop.heroColor)
            }
            splash?.let { s -> shelf.post { s.reveal(revealTargets()) } }
            splash = null
        }
        if (apps.isEmpty()) {
            if (hadFocus || wasFirst) dock.focusFirst()
            return
        }
        val target = when {
            wasFirst -> 0
            !hadFocus -> null
            else -> apps.indexOfFirst { it.packageName == focusedPkg }.takeIf { it >= 0 }
                ?: FocusRules.nextFocusIndexAfterRemoval(oldPos, apps.size)
        }
        if (target != null) focusTile(target)
    }

    private fun focusTile(index: Int): Boolean {
        if (adapter.itemCount == 0) return false
        val i = index.coerceIn(0, adapter.itemCount - 1)
        shelf.scrollToPosition(i)
        shelf.post { shelf.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus() }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        focusTile(0)
    }

    override fun onStart() {
        super.onStart()
        topBar.startStatus()
    }

    override fun onStop() {
        launchOverlay.hide()
        topBar.stopStatus()
        super.onStop()
    }

    @Deprecated("Home screen never exits on Back")
    override fun onBackPressed() {
        // Intentionally empty: the launcher must not finish.
    }

    private companion object {
        /** Tiles whose art/accent are prepared off the main thread before the first bind. */
        const val PRELOAD = 8
    }

    override fun onDestroy() {
        tileMenu.dismiss()
        repo.unregister()
        loader.shutdownNow()
        super.onDestroy()
    }
}
