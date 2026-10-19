package dev.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
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
import dev.tvlauncher.apps.AppOrdering
import dev.tvlauncher.apps.AppRepository
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.launch.AndroidLaunchBackend
import dev.tvlauncher.launch.AppLauncher
import dev.tvlauncher.settings.Prefs
import dev.tvlauncher.ui.Dock
import dev.tvlauncher.ui.FocusRules
import dev.tvlauncher.ui.ShelfAdapter
import dev.tvlauncher.ui.SystemActions
import dev.tvlauncher.ui.TileMenu
import dev.tvlauncher.ui.TileSpec
import dev.tvlauncher.ui.TopBar
import dev.tvlauncher.ui.dp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class HomeActivity : Activity() {
    private lateinit var repo: AppRepository
    private lateinit var prefs: Prefs
    private lateinit var launcher: AppLauncher
    private lateinit var tileMenu: TileMenu
    private lateinit var adapter: ShelfAdapter
    private lateinit var shelf: RecyclerView
    private lateinit var topBar: TopBar
    private lateinit var dock: Dock
    private lateinit var heroTitle: TextView
    private lateinit var emptyText: TextView
    private val loader = Executors.newSingleThreadExecutor()
    private val generation = AtomicInteger()
    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repo = AppRepository(applicationContext)
        prefs = Prefs(applicationContext)
        buildUi()
        repo.register { reload() }
        reload()
    }

    private fun buildUi() {
        window.setBackgroundDrawable(
            GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xFF1B2433.toInt(), 0xFF0B0F17.toInt())
            )
        )
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(dp(48), dp(27), dp(48), dp(27))
        }
        topBar = TopBar(this) { SystemActions.inputs(this) }
        root.addView(topBar, LinearLayout.LayoutParams(-1, -2))

        heroTitle = TextView(this).apply {
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            gravity = Gravity.BOTTOM or Gravity.START
            maxLines = 2
        }
        emptyText = TextView(this).apply {
            text = "No apps found — open the Play Store"
            setTextColor(0xFFCCCCCC.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        val hero = FrameLayout(this)
        hero.addView(heroTitle, FrameLayout.LayoutParams(-1, -1))
        hero.addView(emptyText, FrameLayout.LayoutParams(-1, -1))
        root.addView(hero, LinearLayout.LayoutParams(-1, 0, 1f))

        val profile = DeviceProfileReader.read(this)
        launcher = AppLauncher(profile, AndroidLaunchBackend(this))
        tileMenu = TileMenu(this, prefs) { reload() }
        adapter = ShelfAdapter(
            packageManager, profile,
            onFocused = { _, app -> heroTitle.text = app.label },
            onClick = { app, v -> launcher.launch(app, v) },
            onLongPress = { app -> tileMenu.show(app, adapter.getItems().map { it.packageName }) },
            onDown = { dock.focusFirst() }
        )
        shelf = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@HomeActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = this@HomeActivity.adapter
            clipToPadding = false
            clipChildren = false
            itemAnimator = null
            setPadding(0, dp(20), 0, dp(20))
            addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(out: Rect, v: View, p: RecyclerView, s: RecyclerView.State) {
                    out.right = dp(16)
                }
            })
        }
        root.addView(shelf, LinearLayout.LayoutParams(-1, dp(TileSpec.HEIGHT_DP + 40)))

        dock = Dock(
            this,
            onSettings = { SystemActions.settings(this) },
            onInputs = { SystemActions.inputs(this) },
            onAllApps = { focusTile(0) },
            onAssistant = { SystemActions.assistant(this) },
            onSettingsLongPress = { tileMenu.showDockMenu() },
            onUp = { focusTile(adapter.lastFocused) }
        )
        root.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        setContentView(root)
    }

    private fun reload() {
        val gen = generation.incrementAndGet()
        loader.execute {
            val apps = try {
                AppOrdering.arrange(repo.load(), prefs.load())
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
        emptyText.visibility = if (apps.isEmpty()) View.VISIBLE else View.GONE
        heroTitle.visibility = if (apps.isEmpty()) View.GONE else View.VISIBLE
        if (apps.isEmpty()) {
            if (hadFocus) dock.focusFirst()
            firstLoad = false
            return
        }
        val target = when {
            firstLoad -> 0
            !hadFocus -> null
            else -> apps.indexOfFirst { it.packageName == focusedPkg }.takeIf { it >= 0 }
                ?: FocusRules.nextFocusIndexAfterRemoval(oldPos, apps.size)
        }
        firstLoad = false
        if (target != null) focusTile(target)
    }

    private fun focusTile(index: Int): Boolean {
        if (adapter.itemCount == 0) return false
        val i = index.coerceIn(0, adapter.itemCount - 1)
        shelf.scrollToPosition(i)
        shelf.post {
            heroTitle.text = adapter.getItems().getOrNull(i)?.label.orEmpty()
            shelf.findViewHolderForAdapterPosition(i)?.itemView?.requestFocus()
        }
        return true
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        focusTile(0)
    }

    override fun onResume() {
        super.onResume()
        topBar.refreshNetwork()
    }

    @Deprecated("Home screen never exits on Back")
    override fun onBackPressed() {
        // Intentionally empty: the launcher must not finish.
    }

    override fun onDestroy() {
        tileMenu.dismiss()
        repo.unregister()
        loader.shutdownNow()
        super.onDestroy()
    }
}
