package dev.tvlauncher.appsettings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.FocusFinder
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.text.TextUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import dev.tvlauncher.device.DeviceProfile
import dev.tvlauncher.device.DeviceProfileReader
import dev.tvlauncher.device.EffectsMode
import dev.tvlauncher.audit.AuditTags
import dev.tvlauncher.audit.auditTag
import dev.tvlauncher.input.FocusJump
import dev.tvlauncher.input.GamepadInput
import dev.tvlauncher.input.PadCommand
import dev.tvlauncher.room.RoomTheme
import dev.tvlauncher.settings.AppearancePrefs
import dev.tvlauncher.ui.BackdropView
import dev.tvlauncher.ui.FocusRules
import dev.tvlauncher.ui.Theme
import dev.tvlauncher.ui.Theme.style
import dev.tvlauncher.ui.dp
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The launcher's own Settings: a grouped sidebar ([SidebarState] over [GroupedRegistry]) and a
 * content pane, sized by [SettingsLayout]. Both are frosted glass cards (dev.tvlauncher.ui.Glass)
 * floating over the same room- and accent-aware backdrop as the home screen ([BackdropView]). D-pad: focusing a sidebar entry shows its page;
 * OK on a group header expands or collapses it (one group open at a time); RIGHT enters the page
 * (restoring the last focused row); LEFT at a page's edge returns to the sidebar. BACK goes up
 * one level: sub-page, then sidebar, then closes. The last page is remembered.
 */
class AppSettingsActivity : Activity(), SettingsNav {
    private class Level(val title: String, val build: (Context, SettingsNav) -> View, val returnKey: String?)
    private class FocusTarget(val key: String?, val index: Int)

    override lateinit var profile: DeviceProfile
        private set
    private val device: EffectsMode get() = profile.effectsMode
    override var style = SettingsStyle(0, null)
        private set

    private val sectionsById = LinkedHashMap<String, SettingsSection>()
    private lateinit var sidebarState: SidebarState
    private val sidebarViews = ArrayList<View>()
    private lateinit var layout: SettingsLayout
    private lateinit var root: FrameLayout
    private lateinit var backdrop: BackdropView
    private lateinit var sidebar: LinearLayout
    private lateinit var sidebarList: LinearLayout
    private lateinit var sidebarTitle: TextView
    private lateinit var panel: FrameLayout
    private lateinit var pageHost: FrameLayout
    private lateinit var crumb: TextView
    private lateinit var heading: TextView
    private lateinit var desc: TextView
    private lateinit var toast: TextView

    private var selected: String? = null
    private var pendingSelect: String? = null
    private val stack = ArrayList<Level>()
    private var current: View? = null
    private var generation = 0
    private var pendingFocus: FocusTarget? = null
    private val lastKey = HashMap<String, String>()
    private val advancedOpen = HashSet<String>()
    private val main = Handler(Looper.getMainLooper())

    /** Game controllers: L1/R1 step through the sidebar's pages, Select closes Settings. */
    private val pad = GamepadInput(this, object : GamepadInput.Host {
        override fun onPadCommand(command: PadCommand, repeat: Int): Boolean = when (command) {
            PadCommand.PAGE_PREV -> { padSection(forward = false); true }
            PadCommand.PAGE_NEXT -> { padSection(forward = true); true }
            PadCommand.SELECT -> { finish(); true }
            else -> false
        }
    })
    private val worker: ExecutorService = Executors.newSingleThreadExecutor()
    private val selectRunnable = Runnable { pendingSelect?.let { select(it) } }
    private val hideToast = Runnable { fade(toast, 0f) }

    /** Settings > Accessibility > Text size scales every launcher text. */
    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(dev.tvlauncher.system.A11yScale.wrap(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profile = DeviceProfileReader.read(this)
        style = readStyle()
        val m = resources.displayMetrics
        layout = SettingsLayout.compute(m.widthPixels, m.heightPixels, m.density, hasScrollbar = true)
        val groups = loadGroups()
        val start = SidebarState.startPage(
            saved = savedInstanceState?.getString(STATE_SECTION),
            requested = intent?.getStringExtra(EXTRA_SECTION),
            last = uiPrefs().getString(PREF_LAST_PAGE, null),
            available = groups.flatMap { it.second }
        )
        sidebarState = SidebarState.initial(groups, start)
        buildUi()
        setContentView(root)
        renderSidebar(null)
        start?.let { select(it) }
        start?.let { focusSidebarEntry(sidebarState.indexOfPage(it)) }
        overridePendingTransition(0, 0)
        playEntry()
    }

    override fun onRestart() {
        super.onRestart()
        // Back from a system page, an installer or the home screen: apps and status may have changed.
        val groups = loadGroups()
        if (groups != sidebarState.groups) {
            // Remember what had focus in the sidebar (a header stays a header).
            val focused = currentFocus?.takeIf { isInside(it, sidebarList) }
            val entry = focused?.let { f -> sidebarViews.indexOfFirst { isInside(f, it) } }
                ?.let { sidebarState.entries.getOrNull(it) }
            val cur = selected?.takeIf { id -> groups.any { id in it.second } } ?: groups.firstOrNull()?.second?.firstOrNull()
            sidebarState = SidebarState.regroup(sidebarState, groups, cur)
            val index = entry?.let { sidebarState.indexOf(it) }?.takeIf { it >= 0 }
            renderSidebar(if (focused != null) index ?: 0 else null)
            if (cur != null && cur != selected) { applyBackdrop(); select(cur); return }
        }
        applyBackdrop()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        selected?.let { outState.putString(STATE_SECTION, it) }
    }

    private fun uiPrefs() = getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)

    /** Registered sections grouped for the sidebar, without uninstalled companions and unfilled slots. */
    private fun loadGroups(): List<Pair<SettingsGroup, List<String>>> {
        val probe = object : SectionAvailability.Probe {
            override fun appInstalled(pkg: String) = ModuleProbe.isAppInstalled(this@AppSettingsActivity, pkg)
            override fun classPresent(name: String) = ModuleProbe.isPresent(name)
        }
        val groups = SettingsRegistry.shared.groups(probe)
        sectionsById.clear()
        SettingsRegistry.shared.sections().forEach { sectionsById[it.id] = it }
        return groups.map { g -> g.group to g.sections.map { it.id } }
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
        val appearance = AppearancePrefs(this).load()
        dev.tvlauncher.ui.GlassBackdrop.fullTier = device == EffectsMode.FULL
        dev.tvlauncher.ui.GlassBackdrop.setStyle(
            dev.tvlauncher.settings.GlassStyle.resolve(appearance.glassStyle, device == EffectsMode.FULL))
        return SettingsStyle.from(appearance, device, scale)
    }

    // ---- Layout ----

    private val full: Boolean get() = device == EffectsMode.FULL

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Theme.BASE) }
        // The home screen's backdrop: room palette gradient tinted by the accent (full-bleed by design).
        backdrop = BackdropView(this, 0L).apply { glassBlur = full }
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))
        applyBackdrop()

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            // Overscan-safe margins from the pure layout model (never less than Theme.MARGIN_H/V).
            setPadding(layout.marginH, layout.marginV, layout.marginH, layout.marginV)
        }
        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        sidebar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val p = dp(SettingsLayout.SIDEBAR_PAD_DP)
            setPadding(p, p, p, p)
        }
        Glass.frost(sidebar, full)
        val listPad = dp(SettingsLayout.SIDEBAR_LIST_PAD_DP)
        // A fixed title row; the list region starts below it and clips (SidebarMetrics.regions).
        // The title is the first row of the scrolling list (added in renderSidebar), so scrolled entries can never be drawn over it.
        sidebarTitle = TextView(this).apply {
            text = "Settings"
            style(26f, Theme.semibold)
            isSingleLine = true
            gravity = Gravity.CENTER_VERTICAL
        }
        sidebarList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(listPad, dp(4), listPad, dp(8))
        }
        val sideScroll = FocusScrollView(this, dp(SIDEBAR_FOCUS_MARGIN_DP)).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // Clips: entries scrolled past the top never draw over the "Settings" title; a soft fade
            // marks the clip edges (FULL; LITE clips hard) instead of scroll chevrons.
            clipChildren = true
            clipToPadding = true
            // The fade also runs on LITE: a hard clip leaves half a line sticking out of the glass card.
            isVerticalFadingEdgeEnabled = true
            setFadingEdgeLength(dp(36))
            addView(sidebarList, FrameLayout.LayoutParams(-1, -2))
        }
        sidebar.addView(sideScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        row.addView(sidebar, LinearLayout.LayoutParams(layout.sidebar.width, -1).apply { marginEnd = layout.gap })

        panel = FrameLayout(this).apply { clipChildren = false }
        Glass.frost(panel, full)
        val panelColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            setPadding(0, dp(PaneStack.TOP_DP), 0, 0)
        }
        val inset = layout.paneInset
        crumb = TextView(this).apply {
            style(14f, Theme.medium, Theme.TEXT_2)
            letterSpacing = 0.04f
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        heading = TextView(this).apply {
            style(28f, Theme.semibold)
            isSingleLine = true
            ellipsize = TextUtils.TruncateAt.END
        }
        desc = TextView(this).apply {
            style(15f, Theme.regular, Theme.TEXT_2)
            maxLines = 2 // section descriptions wrap rather than run off the pane
            ellipsize = TextUtils.TruncateAt.END
            setLineSpacing(0f, 1.1f)
        }
        fun header(v: View, bottom: Int) = panelColumn.addView(v, LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = inset; marginEnd = inset; bottomMargin = dp(bottom)
        })
        // A fixed header (PaneStack): the page below is the only scrolling part and clips itself.
        header(crumb, PaneStack.CRUMB_GAP_DP)
        header(heading, PaneStack.HEADING_GAP_DP)
        header(desc, 0)
        pageHost = FrameLayout(this).apply {
            clipChildren = false
            isFocusable = false
        }
        panelColumn.addView(pageHost, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(PaneStack.DESC_GAP_DP) })
        panel.addView(panelColumn, FrameLayout.LayoutParams(-1, -1))

        toast = TextView(this).apply {
            style(16f, Theme.medium)
            background = Glass.pill(this@AppSettingsActivity, Glass.SCRIM, Glass.HAIRLINE)
            setPadding(dp(22), dp(12), dp(22), dp(12))
            maxLines = 2
            alpha = 0f
            // A transient notice floating over the rows on purpose; elevated above the page.
            auditTag(AuditTags.IGNORE)
            elevation = dp(6).toFloat()
        }
        panel.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(22)
        })
        row.addView(panel, LinearLayout.LayoutParams(0, -1, 1f))
    }

    /** The room palette the home screen last used, tinted by Settings' accent. No animation. */
    private fun applyBackdrop() {
        val p = RoomTheme.palette
        backdrop.setRoom(p.backdropTop, p.backdropBottom, p.backdropFloor, p.accentBlend, p.scrimStrength)
        backdrop.setAccent(style.accent, animate = false)
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

    // ---- Sidebar ----

    /** Rebuilds the sidebar from [sidebarState]; focuses entry [focusIndex] if given. */
    private fun renderSidebar(focusIndex: Int?) {
        val hadFocus = currentFocus?.let { isInside(it, sidebarList) } == true
        if (hadFocus) {
            // Park focus on the list itself so removing the focused entry cannot push it into the pane.
            sidebarList.isFocusable = true
            sidebarList.requestFocus()
        }
        sidebarList.removeAllViews()
        sidebarList.addView(sidebarTitle, LinearLayout.LayoutParams(-1, dp(SidebarMetrics.TITLE_DP)).apply {
            marginStart = dp(6); bottomMargin = dp(SidebarMetrics.TITLE_GAP_DP)
        })
        sidebarViews.clear()
        sidebarState.entries.forEachIndexed { i, e ->
            // One rhythm on the 8 dp grid: 48 dp entries, 40 dp headers, 8 dp before each later group.
            val v: View = when (e) {
                is SidebarEntry.Header -> SidebarHeader(this, style, e.group, e.expanded).apply {
                    setOnFocusChangeListener { _, has -> if (has) main.removeCallbacks(selectRunnable) }
                    setOnClickListener { toggleGroup(e.group) }
                }
                is SidebarEntry.Page -> {
                    val section = sectionsById.getValue(e.id)
                    SidebarItem(this, style, section).apply {
                        isCurrent = e.id == selected
                        setOnFocusChangeListener { _, has -> if (has) onSidebarFocus(e.id) }
                        setOnClickListener {
                            select(e.id)
                            focusContent()
                        }
                    }
                }
            }
            val header = e is SidebarEntry.Header
            sidebarViews.add(v)
            sidebarList.addView(v, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = if (header && i > 0) dp(SidebarMetrics.GROUP_GAP_DP) else 0
                bottomMargin = dp(SidebarMetrics.ENTRY_GAP_DP)
            })
        }
        if (focusIndex != null) focusSidebarEntry(focusIndex)
        else if (hadFocus) focusSidebarEntry(selected?.let { sidebarState.indexOfPage(it) }?.takeIf { it >= 0 } ?: 0)
        sidebarList.isFocusable = false
    }

    private fun focusSidebarEntry(i: Int) {
        (sidebarViews.getOrNull(i) ?: sidebarViews.firstOrNull())?.requestFocus()
    }

    private fun toggleGroup(group: SettingsGroup) {
        sidebarState = sidebarState.toggle(group)
        renderSidebar(sidebarState.indexOfHeader(group))
    }

    private fun onSidebarFocus(id: String) {
        main.removeCallbacks(selectRunnable)
        if (id == selected) return
        pendingSelect = id
        main.postDelayed(selectRunnable, SELECT_DEBOUNCE_MS)
    }

    // ---- Sections and pages ----

    private fun select(id: String) {
        main.removeCallbacks(selectRunnable)
        pendingSelect = null
        if (id == selected || id !in sectionsById) return
        selected = id
        val next = sidebarState.select(id)
        val regroup = next.expanded != sidebarState.expanded
        sidebarState = next
        if (regroup) renderSidebar(null)
        sidebarViews.forEach { (it as? SidebarItem)?.isCurrent = it.section.id == id }
        try {
            uiPrefs().edit().putString(PREF_LAST_PAGE, id).apply()
        } catch (e: RuntimeException) {
        }
        stack.clear()
        advancedOpen.clear()
        pendingFocus = null
        showLevel(animate = true)
    }

    /** Rebuilds the top of the stack (or the section root) into the content pane. */
    private fun showLevel(animate: Boolean) {
        val section = selected?.let { sectionsById[it] } ?: return
        val top = stack.lastOrNull()
        heading.text = top?.title ?: section.title
        val groupTitle = (sidebarState.groupOf(section.id) ?: section.group).title
        crumb.text = Breadcrumb.of(groupTitle, section.title, stack.map { it.title })
        crumb.visibility = if (crumb.text.isEmpty()) View.GONE else View.VISIBLE
        val d = if (top == null) section.description else ""
        desc.text = d
        desc.visibility = if (d.isEmpty()) View.GONE else View.VISIBLE
        generation++
        val old = current
        if (old != null && old.hasFocus()) parkFocus()
        val view = try {
            top?.build?.invoke(this, this) ?: section.buildPage(this, this)
        } catch (e: RuntimeException) {
            SettingsPage(this, style).apply { note("This page could not be shown.") }
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
        sidebarViews.forEach { (it as? SidebarItem)?.applyStyle(style) }
        applyBackdrop()
        refresh()
    }

    override fun isAdvancedOpen(key: String) = key in advancedOpen

    override fun setAdvancedOpen(key: String, open: Boolean) {
        if (open) advancedOpen.add(key) else advancedOpen.remove(key)
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
        val wanted = target.key ?: (page as? SettingsPage)?.preferredFocus
        val byKey = wanted?.let { k -> list.firstOrNull { it.tag == k } }
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
        val id = selected
        if (page != null && f != null && id != null && isInside(f, page)) {
            keyOf(f, page)?.let { lastKey[id] = it }
        }
        val revealed = sidebarState.reveal()
        if (revealed.expanded != sidebarState.expanded) {
            sidebarState = revealed
            renderSidebar(null)
        }
        val i = id?.let { sidebarState.indexOfPage(it) } ?: -1
        focusSidebarEntry(if (i >= 0) i else 0)
    }

    private fun focusContent() {
        val page = current ?: return
        val key = if (stack.isEmpty()) selected?.let { lastKey[it] } else null
        pendingFocus = FocusTarget(key, 0)
        applyPendingFocus()
        if (pendingFocus != null) parkFocus()
    }

    /** L1/R1: the previous / next page in sidebar order (across groups), focused in the sidebar. */
    private fun padSection(forward: Boolean) {
        val id = FocusJump.adjacent(sidebarState.groups.flatMap { it.second }, selected, forward) ?: return
        select(id)
        focusSidebarEntry(sidebarState.indexOfPage(id))
    }

    /** The controller mapping; remote keys reach [handleKey] unchanged (volume keys stay with the TV). */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (dev.tvlauncher.remotecheck.RemoteCapture.dispatch(event)) return true // Settings > System > Remote, live page only
        return pad.dispatch(event, ::handleKey)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)

    private fun handleKey(event: KeyEvent): Boolean {
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
        private const val PREFS_UI = "settings_ui"
        private const val PREF_LAST_PAGE = "last_page"
        private const val SIDEBAR_FOCUS_MARGIN_DP = 16
        private const val SELECT_DEBOUNCE_MS = 110L
        private const val TOAST_MS = 2600L

        /** Intent the home screen uses to open Settings (optionally on [sectionId]). */
        fun intent(context: Context, sectionId: String? = null): Intent =
            Intent(ACTION_SETTINGS).setPackage(context.packageName).apply {
                sectionId?.let { putExtra(EXTRA_SECTION, it) }
            }
    }
}
