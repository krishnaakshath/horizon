package dev.tvlauncher.appsettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.settings.Branding
import dev.tvlauncher.ui.FocusRules
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The launcher's own Settings: a sidebar of [SettingsRegistry] sections and a glass content pane.
 * D-pad: focusing a sidebar entry shows its page; RIGHT enters the page (restoring the last
 * focused row); LEFT at a page's edge returns to the sidebar. BACK goes up one level: sub-page,
 * then sidebar, then closes.
 */
class AppSettingsActivity : Activity(), SettingsNav {
    private class Level(val title: String, val build: (Context, SettingsNav) -> View, val returnKey: String?)
    private class FocusTarget(val key: String?, val index: Int)

    override lateinit var profile: DeviceProfile
        private set
    private val device: EffectsMode get() = profile.effectsMode
    override var style = SettingsStyle(0, null)
        private set

    private val sections = SettingsRegistry.shared.sections()
    private val items = ArrayList<SidebarItem>()
    private lateinit var root: FrameLayout
    private lateinit var glow: View
    private lateinit var sidebar: LinearLayout
    private lateinit var sidebarList: LinearLayout
    private lateinit var panel: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var crumb: TextView
    private lateinit var heading: TextView
    private lateinit var toast: TextView

    private var selected = -1
    private var pendingSelect = -1
    private val stack = ArrayList<Level>()
    private var current: View? = null
    private var generation = 0
    private var pendingFocus: FocusTarget? = null
    private val lastKey = HashMap<String, String>()
    private val main = Handler(Looper.getMainLooper())
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val selectRunnable = Runnable { if (pendingSelect >= 0) select(pendingSelect) }
    private val hideToast = Runnable { fade(toast, 0f) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = DeviceProfileReader.read(this)
        style = readStyle()
        buildUi()
        setContentView(root)
        val wanted = savedInstanceState?.getString(STATE_SECTION) ?: intent?.getStringExtra(EXTRA_SECTION)
        val start = sections.indexOfFirst { it.id == wanted }.coerceAtLeast(0)
        select(start)
        items.getOrNull(start)?.requestFocus()
        overridePendingTransition(0, 0)
        playEntry()
    }

    override fun onRestart() {
        super.onRestart()
        // Back from a system page or the home screen: status and lists may have changed.
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        sections.getOrNull(selected)?.let { outState.putString(STATE_SECTION, it.id) }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(0, if (style.animate) android.R.anim.fade_out else 0)
    }

    private fun readStyle(): SettingsStyle {
        val scale = try {
            Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (e: RuntimeException) {
            1f
        }
        return SettingsStyle.from(AppearancePrefs(this).load(), device, scale)
    }

    // ---- Layout ----

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Theme.BASE) }
        glow = View(this)
        root.addView(glow, FrameLayout.LayoutParams(-1, -1))
        applyGlow()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(Theme.MARGIN_H), dp(Theme.MARGIN_V), dp(Theme.MARGIN_H), dp(Theme.MARGIN_V))
        }
        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        sidebar.addView(TextView(this).apply {
            text = Branding.NAME.uppercase()
            letterSpacing = 0.12f
            style(13f, Theme.medium, Theme.TEXT_2)
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14); topMargin = dp(6) })
        sidebar.addView(TextView(this).apply {
            text = "Settings"
            style(36f, Theme.light)
        }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(12); topMargin = dp(6); bottomMargin = dp(18) })
        sidebarList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(6), dp(4), dp(10), dp(12))
        }
        sections.forEachIndexed { i, s ->
            val item = SidebarItem(this, style, s)
            item.setOnFocusChangeListener { _, has -> if (has) onSidebarFocus(i) }
            item.setOnClickListener {
                select(i)
                focusContent()
            }
            items.add(item)
            sidebarList.addView(item, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(4) })
        }
        val sideScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipChildren = false
            clipToPadding = false
            addView(sidebarList, FrameLayout.LayoutParams(-1, -2))
        }
        sidebar.addView(sideScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        row.addView(sidebar, LinearLayout.LayoutParams(dp(SIDEBAR_DP), -1).apply { marginEnd = dp(Theme.S5) })

        panel = FrameLayout(this).apply {
            background = Glass.rect(this@AppSettingsActivity, Glass.PANEL, Glass.RADIUS_PANEL, Glass.HAIRLINE)
            clipChildren = false
        }
        val panelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(0, dp(24), 0, 0)
        }
        crumb = TextView(this).apply {
            style(14f, Theme.medium, Theme.TEXT_2)
            letterSpacing = 0.04f
            visibility = View.GONE
        }
        heading = TextView(this).apply { style(30f, Theme.light) }
        panelColumn.addView(crumb, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(30); bottomMargin = dp(4) })
        panelColumn.addView(heading, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(30); bottomMargin = dp(10) })
        pageHost = FrameLayout(this).apply {
            clipChildren = false
            isFocusable = false
        }
        panelColumn.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f))
        panel.addView(panelColumn, FrameLayout.LayoutParams(-1, -1))

        toast = TextView(this).apply {
            style(16f, Theme.medium)
            background = Glass.pill(this@AppSettingsActivity, Glass.SCRIM, Glass.HAIRLINE)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            alpha = 0f
        }
        panel.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(22)
        })
        row.addView(panel, LinearLayout.LayoutParams(0, -1, 1f))
    }

    /** A static accent glow in the top-left corner: one gradient, drawn once, no blur. */
    private fun applyGlow() {
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            colors = intArrayOf((style.accent and 0x00FFFFFF) or Theme.GLOW_ALPHA, 0)
            gradientRadius = dp(GLOW_RADIUS_DP).toFloat()
            setGradientCenter(0.12f, 0.0f)
        }
    }

    private fun playEntry() {
        if (!style.animate) return
        val d = DecelerateInterpolator(1.6f)
        sidebar.alpha = 0f
        sidebar.translationX = -dp(24).toFloat()
        panel.alpha = 0f
        panel.translationX = dp(32).toFloat()
        val s = sidebar.animate().alpha(1f).translationX(0f).setDuration(style.entryMs).setInterpolator(d)
        val p = panel.animate().alpha(1f).translationX(0f).setDuration(style.entryMs)
            .setStartDelay(style.entryStaggerMs).setInterpolator(d)
        s.start(); p.start()
    }

    // ---- Sections and pages ----

    private fun onSidebarFocus(i: Int) {
        main.removeCallbacks(selectRunnable)
        if (i == selected) return
        pendingSelect = i
        main.postDelayed(selectRunnable, SELECT_DEBOUNCE_MS)
    }

    private fun select(i: Int) {
        main.removeCallbacks(selectRunnable)
        pendingSelect = -1
        if (i == selected || i !in sections.indices) return
        selected = i
        items.forEachIndexed { k, item -> item.isCurrent = k == i }
        stack.clear()
        pendingFocus = null
        showLevel(animate = true)
    }

    /** Rebuilds the top of the stack (or the section root) into the content pane. */
    private fun showLevel(animate: Boolean) {
        val section = sections.getOrNull(selected) ?: return
        val top = stack.lastOrNull()
        heading.text = top?.title ?: section.title
        crumb.text = (listOf(section.title) + stack.dropLast(1).map { it.title }).joinToString("  ›  ")
        crumb.visibility = if (top == null) View.GONE else View.VISIBLE
        generation++
        val old = current
        if (old != null && old.hasFocus()) parkFocus()
        val view = try {
            top?.build?.invoke(this, this) ?: section.buildPage(this, this)
        } catch (e: RuntimeException) {
            SettingsPage(this, style).apply { lead("This page could not be shown.") }
        }
        old?.let { it.animate().cancel(); pageHost.removeView(it) }
        pageHost.addView(view, FrameLayout.LayoutParams(-1, -1))
        current = view
        if (animate && style.animate) {
            view.alpha = 0f
            view.translationX = dp(16).toFloat()
            view.animate().alpha(1f).translationX(0f).setDuration(style.pageMs)
                .setInterpolator(DecelerateInterpolator()).start()
        }
        applyPendingFocus()
    }

    override fun push(title: String, build: (Context, SettingsNav) -> View) {
        val key = focusedKey()
        stack.add(Level(title, build, key))
        pendingFocus = FocusTarget(null, 0)
        showLevel(animate = true)
    }

    override fun pop(): Boolean {
        val level = stack.removeLastOrNull() ?: return false
        pendingFocus = FocusTarget(level.returnKey, 0)
        showLevel(animate = true)
        return true
    }

    override fun refresh() {
        val page = current ?: return
        val f = currentFocus
        if (f != null && isInside(f, page)) {
            pendingFocus = FocusTarget(keyOf(f, page), focusables(page).indexOf(f).coerceAtLeast(0))
        }
        showLevel(animate = false)
    }

    override fun appearanceChanged() {
        style = readStyle()
        items.forEach { it.applyStyle(style) }
        applyGlow()
        refresh()
    }

    override fun contentReady() = applyPendingFocus()

    override fun refreshCurrent() {
        if (!isDestroyed) refresh()
    }

    override fun <T> task(work: () -> T, onResult: (T) -> Unit) {
        try {
            worker.execute {
                val result = try {
                    work()
                } catch (e: Exception) {
                    main.post { if (!isDestroyed) notify("Something went wrong") }
                    return@execute
                }
                main.post { if (!isDestroyed) onResult(result) }
            }
        } catch (e: RuntimeException) {
            // Executor already shut down: the screen is closing.
        }
    }

    override fun notify(message: String) {
        toast.text = message
        main.removeCallbacks(hideToast)
        fade(toast, 1f)
        main.postDelayed(hideToast, TOAST_MS)
    }

    override fun <T> background(work: () -> T, onResult: (T) -> Unit) {
        val gen = generation
        try {
            worker.execute {
                val result = try {
                    work()
                } catch (e: Exception) {
                    main.post { if (!isDestroyed) notify("Something went wrong") }
                    return@execute
                }
                main.post { if (!isDestroyed && gen == generation) onResult(result) }
            }
        } catch (e: RuntimeException) {
            // Executor already shut down: the screen is closing.
        }
    }

    private fun fade(v: View, to: Float) {
        v.animate().cancel()
        if (style.animate) v.animate().alpha(to).setDuration(style.pageMs).start() else v.alpha = to
    }

    // ---- Focus ----

    /** Keeps focus inside the pane while its page is swapped, so the sidebar never steals it. */
    private fun parkFocus() {
        pageHost.isFocusable = true
        pageHost.requestFocus()
    }

    private fun unpark() {
        if (pageHost.isFocusable && !pageHost.isFocused) pageHost.isFocusable = false
    }

    private fun applyPendingFocus() {
        val target = pendingFocus ?: return unpark()
        val page = current ?: return
        val list = focusables(page)
        if (list.isEmpty()) return // async page: contentReady() will try again
        val byKey = target.key?.let { k -> list.firstOrNull { it.tag == k } }
        val v = byKey ?: list[FocusRules.nextFocusIndexAfterRemoval(target.index, list.size) ?: 0]
        pendingFocus = null
        v.requestFocus()
        pageHost.isFocusable = false
    }

    private fun focusables(page: View): ArrayList<View> {
        val out = ArrayList<View>()
        page.addFocusables(out, View.FOCUS_DOWN)
        out.remove(pageHost)
        return out
    }

    private fun focusedKey(): String? {
        val page = current ?: return null
        val f = currentFocus ?: return null
        return if (isInside(f, page)) keyOf(f, page) else null
    }

    /** The nearest String tag from [v] up to [page] (rows and swatches carry their keys as tags). */
    private fun keyOf(v: View, page: View): String? {
        var x: View? = v
        while (x != null) {
            (x.tag as? String)?.let { return it }
            if (x === page) return null
            x = x.parent as? View
        }
        return null
    }

    private fun isInside(v: View, ancestor: View): Boolean {
        var x: View? = v
        while (x != null) {
            if (x === ancestor) return true
            x = x.parent as? View
        }
        return false
    }

    private fun focusSidebar() {
        val page = current
        val f = currentFocus
        val section = sections.getOrNull(selected)
        if (page != null && f != null && section != null && isInside(f, page)) {
            keyOf(f, page)?.let { lastKey[section.id] = it }
        }
        items.getOrNull(selected)?.requestFocus()
    }

    private fun focusContent() {
        val page = current ?: return
        val section = sections.getOrNull(selected)
        val key = if (stack.isEmpty()) section?.let { lastKey[it.id] } else null
        pendingFocus = FocusTarget(key, 0)
        applyPendingFocus()
        if (pendingFocus != null) parkFocus()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (super.dispatchKeyEvent(event)) return true
        if (event.action != KeyEvent.ACTION_DOWN) return false
        val f = currentFocus ?: return false
        val inPanel = isInside(f, panel)
        val inSidebar = isInside(f, sidebarList)
        val dir = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> View.FOCUS_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> View.FOCUS_RIGHT
            KeyEvent.KEYCODE_DPAD_UP -> View.FOCUS_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> View.FOCUS_DOWN
            else -> return false
        }
        if (inSidebar) {
            if (dir == View.FOCUS_RIGHT) { focusContent(); return true }
            if (dir == View.FOCUS_LEFT) return true
            return FocusFinder.getInstance().findNextFocus(sidebarList, f, dir) == null
        }
        if (inPanel) {
            val next = FocusFinder.getInstance().findNextFocus(panel as ViewGroup, f, dir)
            if (next == null || next === pageHost) {
                if (dir == View.FOCUS_LEFT) focusSidebar()
                return true // never fall out of the pane into a random sidebar entry
            }
        }
        return false
    }

    @Deprecated("Activity BACK handling for API 24-32")
    override fun onBackPressed() {
        val f = currentFocus
        if (f != null && isInside(f, panel)) {
            if (!pop()) focusSidebar()
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    companion object {
        const val ACTION_SETTINGS = "dev.tvlauncher.action.SETTINGS"
        /** Optional section id to open first, e.g. [AppearanceSection.ID]. */
        const val EXTRA_SECTION = "dev.tvlauncher.extra.SECTION"
        private const val STATE_SECTION = "section"
        private const val SIDEBAR_DP = 272
        private const val SELECT_DEBOUNCE_MS = 110L
        private const val TOAST_MS = 2600L
        private const val GLOW_RADIUS_DP = 560

        /** Intent the home screen uses to open Settings (optionally on [sectionId]). */
        fun intent(context: Context, sectionId: String? = null): Intent =
            Intent(ACTION_SETTINGS).setPackage(context.packageName).apply {
                sectionId?.let { putExtra(EXTRA_SECTION, it) }
            }
    }
}
