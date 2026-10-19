package dev.tvlauncher.browser

import android.animation.ValueAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlauncher.browser.CursorModel.Dir
import dev.tvlauncher.browser.cursor.CursorSettings
import dev.tvlauncher.browser.cursor.CursorState
import dev.tvlauncher.browser.cursor.CursorStateMapper
import dev.tvlauncher.browser.cursor.HitInfo
import dev.tvlauncher.browser.cursor.Magnet
import dev.tvlauncher.browser.cursor.SnapMath
import dev.tvlauncher.browser.cursor.StickCursor
import dev.tvlauncher.input.GamepadInput
import dev.tvlauncher.input.PadCommand
import dev.tvlauncher.browser.data.Bookmark
import dev.tvlauncher.browser.data.BookmarkStore
import dev.tvlauncher.browser.data.DefaultFavorites
import dev.tvlauncher.browser.data.HistoryStore
import dev.tvlauncher.browser.data.Tab
import dev.tvlauncher.browser.data.TabModel
import dev.tvlauncher.browser.net.FaviconLoader
import dev.tvlauncher.browser.net.SearchEngine
import dev.tvlauncher.browser.net.SearchEngines
import dev.tvlauncher.browser.net.SuggestClient
import dev.tvlauncher.browser.net.SuggestionMerger
import dev.tvlauncher.browser.net.UrlPolicy
import dev.tvlauncher.browser.ui.BouncingDots
import dev.tvlauncher.browser.ui.CursorSettingsPage
import dev.tvlauncher.browser.ui.CursorView
import dev.tvlauncher.browser.ui.FindBar
import dev.tvlauncher.browser.ui.Hint
import dev.tvlauncher.browser.ui.MenuCard
import dev.tvlauncher.browser.ui.MessagePanel
import dev.tvlauncher.browser.ui.ProgressLine
import dev.tvlauncher.browser.ui.RoundButton
import dev.tvlauncher.browser.ui.SidePanel
import dev.tvlauncher.browser.ui.SplashOverlay
import dev.tvlauncher.browser.ui.StartPage
import dev.tvlauncher.browser.ui.SuggestionList
import dev.tvlauncher.browser.ui.TabSwitcher
import dev.tvlauncher.browser.ui.TabsButton
import dev.tvlauncher.browser.ui.Theme
import dev.tvlauncher.browser.ui.UrlBar
import dev.tvlauncher.browser.ui.dp
import java.io.File
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import dev.tvlauncher.browser.data.AtomicText
import dev.tvlauncher.browser.data.ClearPlan
import dev.tvlauncher.browser.data.DebouncedSaver
import dev.tvlauncher.browser.data.FileOps
import dev.tvlauncher.browser.data.SessionSnapshot
import dev.tvlauncher.browser.data.SessionState
import dev.tvlauncher.browser.net.SslErrorPolicy
import dev.tvlauncher.browser.net.UpgradeLoopGuard
import dev.tvlauncher.browser.net.SslFallback
import dev.tvlauncher.browser.data.ClearGate
import dev.tvlauncher.browser.data.SequencedWriter
import dev.tvlauncher.browser.ui.Menus
import dev.tvlauncher.browser.ui.ThumbCache

/**
 * TV browser. D-pad drives a virtual mouse cursor by default (hold BACK for focus mode); MENU
 * opens the browser menu. Up to 3 tabs with ONE live WebView: inactive tabs keep URL, title,
 * scroll, saved WebView state and a small thumbnail, and are recreated on switch. The start
 * page is native and needs no WebView at all.
 */
class BrowserActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var content: FrameLayout
    private lateinit var toolbarBox: FrameLayout
    private lateinit var backBtn: RoundButton
    private lateinit var forwardBtn: RoundButton
    private lateinit var reloadBtn: RoundButton
    private lateinit var homeBtn: RoundButton
    private lateinit var starBtn: RoundButton
    private lateinit var tabsBtn: TabsButton
    private lateinit var menuBtn: RoundButton
    private lateinit var urlBar: UrlBar
    private lateinit var progress: ProgressLine
    private lateinit var startPage: StartPage
    private lateinit var message: MessagePanel
    private lateinit var dots: BouncingDots
    private lateinit var suggestions: SuggestionList
    private lateinit var findBar: FindBar
    private lateinit var menu: MenuCard
    private lateinit var ctxMenu: MenuCard
    private lateinit var sidePanel: SidePanel
    private lateinit var switcher: TabSwitcher
    private lateinit var cursorPage: CursorSettingsPage
    private lateinit var hint: Hint
    private lateinit var cursorView: CursorView
    private var cursor: CursorModel? = null

    private lateinit var loader: FaviconLoader
    private lateinit var bookmarks: BookmarkStore
    private lateinit var history: HistoryStore
    private val suggest = SuggestClient()
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "browser-io").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())

    private var tabs = TabModel()
    private val savedStates = HashMap<Long, Bundle>()
    private val thumbs by lazy { ThumbCache(File(cacheDir, "tabs")) }
    private var pendingScroll = 0
    /** Main-frame URL currently being loaded (after redirects), for SSL error attribution. */
    private var loadingUrl: String? = null
    private val upgradeGuard = UpgradeLoopGuard()
    private val sslFallback = SslFallback(8000)
    private var committed = false
    private val clearGate = ClearGate()
    private val sslCheck = Runnable {
        sslFallback.due(SystemClock.uptimeMillis())?.let { p -> if (web != null && !showingStart) showError(p.url, "SSL:${p.primaryError}") }
    }

    private var web: WebView? = null
    private var webFailed = false
    private var cursorMode = true
    private var showingStart = true
    private var firstLoad = false
    private var loading = false
    private var clearHistoryOnFinish = false
    private var toolbarShown = true
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    /** The current WebView's chrome client; WebView.getWebChromeClient() is API 26+ (minSdk 24). */
    private var chrome: Chrome? = null
    private var touchDownTime = 0L
    private var touching = false
    private var longFired = false
    private var backLongHandled = false
    private var scrollAnim: ValueAnimator? = null
    private var cursorSettings = CursorSettings()

    // Hit testing / magnet.
    private var lastHit = HitInfo.EMPTY
    private var lastHitAt = 0L
    private var hitSeq = 0
    private var edgeUntil = 0L
    private var snapAnim: ValueAnimator? = null

    private val prefs by lazy { getSharedPreferences("browser", MODE_PRIVATE) }
    private val engine get() = SearchEngines.fromId(prefs.getString(KEY_ENGINE, null))
    private val textZoom get() = prefs.getInt(KEY_ZOOM, 100)
    private val restoreSession get() = prefs.getBoolean(KEY_RESTORE, true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Perf.init(this)
        loader = FaviconLoader(this)
        bookmarks = BookmarkStore(File(filesDir, "bookmarks.json"), DefaultFavorites.list)
        history = HistoryStore(File(filesDir, "history.json"))
        cursorSettings = CursorSettings.decode(prefs.getString(KEY_CURSOR, null))
        buildUi()
        setContentView(root)
        if (savedInstanceState == null) root.addView(SplashOverlay(this), FrameLayout.LayoutParams(-1, -1))
        restoreTabs()
        root.postDelayed({ hint.show("Arrows move the pointer  ·  OK clicks  ·  MENU for options", 4500) }, 700)
    }

    // ================================================================ UI

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Theme.BASE)
            isFocusable = true
            isFocusableInTouchMode = true
            noFocusHighlight()
        }
        content = FrameLayout(this)
        startPage = StartPage(this, loader, TOOLBAR_DP,
            onSearch = { urlBar.focusAndType() },
            onOpen = { navigate(it) },
            onTileMenu = { b, i -> tileMenu(b, i) })
        content.addView(startPage, FrameLayout.LayoutParams(-1, -1))
        message = MessagePanel(this).apply { visibility = View.GONE }
        content.addView(message, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(TOOLBAR_DP) })
        dots = BouncingDots(this).apply { visibility = View.GONE }
        content.addView(dots, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        toolbarBox = FrameLayout(this).apply { setBackgroundColor(Theme.BASE); isClickable = true }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.MARGIN_H), dp(Theme.MARGIN_V), dp(Theme.MARGIN_H), dp(14))
            clipChildren = false; clipToPadding = false
        }
        backBtn = RoundButton(this, R.drawable.ic_back, "Back") { navBack() }
        forwardBtn = RoundButton(this, R.drawable.ic_forward, "Forward") { web?.takeIf { it.canGoForward() }?.goForward() }
        reloadBtn = RoundButton(this, R.drawable.ic_reload, "Reload") { reload() }
        homeBtn = RoundButton(this, R.drawable.ic_home, "Home") { showStart() }
        starBtn = RoundButton(this, R.drawable.ic_star, "Bookmark") { toggleBookmark() }.apply {
            setOnLongClickListener { openBookmarks(); true }
        }
        tabsBtn = TabsButton(this) { openSwitcher() }
        menuBtn = RoundButton(this, R.drawable.ic_menu, "Menu") { openMenu() }
        urlBar = UrlBar(this,
            onSubmit = { text -> suggestions.hide(); navigate(UrlResolver.resolve(text, engine)); refocusRoot() },
            onQuery = { q -> querySuggestions(q) },
            onFocus = { has -> if (has) showToolbar(true) else main.postDelayed({ if (!urlBar.edit.isFocused && suggestions.findFocus() == null) suggestions.hide() }, 120) })
        val size = dp(BUTTON_DP)
        listOf(backBtn, forwardBtn, reloadBtn, homeBtn).forEachIndexed { i, b ->
            bar.addView(b, LinearLayout.LayoutParams(size, size).apply { if (i > 0) marginStart = dp(10) })
        }
        bar.addView(urlBar, LinearLayout.LayoutParams(0, size, 1f).apply { marginStart = dp(18); marginEnd = dp(18) })
        listOf(starBtn, tabsBtn, menuBtn).forEachIndexed { i, b ->
            bar.addView(b, LinearLayout.LayoutParams(size, size).apply { if (i > 0) marginStart = dp(10) })
        }
        toolbarBox.addView(bar, FrameLayout.LayoutParams(-1, -1))
        progress = ProgressLine(this)
        toolbarBox.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.BOTTOM))
        root.addView(toolbarBox, FrameLayout.LayoutParams(-1, dp(TOOLBAR_DP), Gravity.TOP))

        suggestions = SuggestionList(this) { s -> pickSuggestion(s) }
        root.addView(suggestions, FrameLayout.LayoutParams(dp(480), -2, Gravity.TOP or Gravity.START).apply { topMargin = dp(TOOLBAR_DP - 8) })
        findBar = FindBar(this,
            onQuery = { q -> web?.findAllAsync(q) },
            onNext = { fwd -> web?.findNext(fwd) },
            onDone = { closeFind() })
        root.addView(findBar, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(TOOLBAR_DP + 6) })
        menu = MenuCard(this).apply { onClose = { overlayClosed(menuBtn) } }
        root.addView(menu, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(TOOLBAR_DP - 6); marginEnd = dp(Theme.MARGIN_H); bottomMargin = dp(16)
        })
        ctxMenu = MenuCard(this).apply { onClose = { overlayClosed(null) } }
        root.addView(ctxMenu, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        sidePanel = SidePanel(this, loader).apply { onClose = { overlayClosed(menuBtn) } }
        root.addView(sidePanel, FrameLayout.LayoutParams(-1, -1))
        switcher = TabSwitcher(this, loader).apply { onClose = { overlayClosed(tabsBtn) } }
        root.addView(switcher, FrameLayout.LayoutParams(-1, -1))
        cursorPage = CursorSettingsPage(this, cursorSettings) { s -> applyCursorSettings(s) }.apply { onClose = { overlayClosed(menuBtn) } }
        root.addView(cursorPage, FrameLayout.LayoutParams(-1, -1))
        hint = Hint(this)
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12) })
        cursorView = CursorView(this)
        root.addView(cursorView, FrameLayout.LayoutParams(-1, -1))
        cursorView.applySettings(cursorSettings)

        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = (r - l).toFloat(); val h = (b - t).toFloat()
            val c = cursor
            if (c == null) {
                cursor = CursorModel(w, h, resources.displayMetrics.density).also {
                    it.speedFactor = cursorSettings.speed
                    cursorView.moveTo(it.x, it.y, animate = false)
                }
            } else c.resize(w, h)
        }
        refreshStart()
    }

    private fun View.noFocusHighlight() {
        if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    }

    private fun refocusRoot() { if (cursorMode) root.requestFocus() }

    private fun refreshStart() {
        startPage.bind(bookmarks.items, history.recentSites(5))
        startPage.setEngine(engine.label)
    }

    private fun updateNav() {
        val w = web
        backBtn.isEnabled = !showingStart
        forwardBtn.isEnabled = !showingStart && w?.canGoForward() == true
        reloadBtn.isEnabled = !showingStart
        val url = currentUrl()
        starBtn.isEnabled = url != null
        starBtn.setIcon(if (url != null && bookmarks.contains(url)) R.drawable.ic_star_filled else R.drawable.ic_star)
        tabsBtn.count = tabs.tabs.size
    }

    private fun currentUrl(): String? = if (showingStart) null else web?.url?.takeIf { UrlPolicy.isStorable(it) }

    private fun showToolbar(show: Boolean) {
        val want = show || showingStart || !cursorMode
        if (want == toolbarShown) return
        toolbarShown = want
        val h = dp(TOOLBAR_DP).toFloat()
        toolbarBox.animate().translationY(if (want) 0f else -h).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
        web?.let { w ->
            (w.layoutParams as FrameLayout.LayoutParams).topMargin = if (want) dp(TOOLBAR_DP) else 0
            w.requestLayout()
        }
    }

    // ================================================================ tabs & navigation

    private fun restoreTabs() {
        if (restoreSession) {
            val f = File(filesDir, "session.json")
            if (f.exists()) tabs = TabModel.decode(try { f.readText() } catch (e: Exception) { null })
        }
        activate(tabs.active)
    }

    private val sessionFile by lazy { File(filesDir, "session.json") }

    /** Delayed save after page loads; writes go through the single io thread (ordered, atomic). */
    /** Only the newest session snapshot lands on disk, whichever thread writes it. */
    private val sessionWriter by lazy { SequencedWriter { json -> AtomicText.write(FileOps.Real, sessionFile, json) } }

    private val sessionSaver = DebouncedSaver(
        post = { r, ms -> main.postDelayed(r, ms) },
        remove = { r -> main.removeCallbacks(r) },
        write = { json -> val seq = sessionWriter.next(); io.execute { sessionWriter.write(seq, json) } }
    )

    private fun sessionJson(): String {
        snapshotActive(false)
        return tabs.encode()
    }

    private fun scheduleSessionSave() {
        if (restoreSession && clearGate.shouldScheduleSession()) sessionSaver.schedule(2000) { sessionJson() }
    }

    /**
     * onStop: small synchronous atomic write on the main thread (not queued behind thumbnail
     * compression); any older queued write is skipped by the sequence check.
     */
    private fun saveSessionNow() {
        sessionSaver.cancel()
        if (!restoreSession) { io.execute { sessionFile.delete() }; return }
        sessionWriter.write(sessionWriter.next(), sessionJson())
    }


    /** Copies the live WebView's state into the active tab (and optionally a thumbnail). Never blanks a page URL. */
    private fun snapshotActive(thumb: Boolean) {
        val w = web
        SessionSnapshot.apply(tabs, w?.let { SessionSnapshot.Web(it.url, it.title, it.scrollY) }, showingStart)
        if (showingStart || w == null) return
        savedStates[tabs.active.id] = Bundle().also { w.saveState(it) }
        if (thumb) captureThumb(tabs.active.id, w)
    }

    private fun captureThumb(id: Long, w: WebView) {
        if (w.width <= 0 || w.height <= 0) return
        try {
            val bmp = Bitmap.createBitmap(THUMB_W, THUMB_H, Bitmap.Config.RGB_565)
            val c = Canvas(bmp)
            val s = THUMB_W.toFloat() / w.width
            c.scale(s, s)
            c.translate(-w.scrollX.toFloat(), -w.scrollY.toFloat())
            w.draw(c)
            thumbs.put(id, bmp) // saved to cacheDir/tabs off-thread; reloadable after memory trims
        } catch (t: Throwable) {
            Log.w(TAG, "thumbnail", t)
        }
    }


    /** Shows [tab] in the single live WebView (recreated), or the start page. */
    private fun activate(tab: Tab) {
        destroyWeb()
        if (tab.url == null) { showStart(); return }
        val w = ensureWeb() ?: run { showNoWebView(); return }
        showWebUi(firstTime = true)
        val state = savedStates.remove(tab.id)
        pendingScroll = tab.scrollY
        val restored = state != null && w.restoreState(state) != null
        if (!restored) w.loadUrl(tab.url!!)
        urlBar.show(tab.url, tab.title)
        updateNav()
    }

    private fun openInNewTab(url: String) {
        if (!UrlPolicy.isNavigationAllowed(url)) return
        if (!tabs.canOpen()) { hint.show("Up to 3 tabs — close one first"); return }
        snapshotActive(true)
        val t = tabs.open(url) ?: return
        activate(t)
        hint.show("Opened in a new tab  ·  ${tabs.tabs.size} of 3")
    }

    private fun switchTo(index: Int) {
        if (index == tabs.activeIndex) { switcher.close(); return }
        snapshotActive(true)
        switcher.close()
        activate(tabs.switchTo(index))
    }

    private fun closeTab(t: Tab) {
        val wasActive = t.id == tabs.active.id
        savedStates.remove(t.id)
        thumbs.remove(t.id)
        if (wasActive) {
            val next = tabs.close(t.id)
            activate(next)
        } else tabs.close(t.id)
        updateNav()
        openSwitcher(reopen = true)
    }

    private fun navigate(url: String) {
        closeOverlays()
        if (url == UrlResolver.HOME_PAGE) { showStart(); return }
        if (!UrlPolicy.isNavigationAllowed(url)) { hint.show("That address can't be opened"); return }
        val fromStart = showingStart || web == null
        val w = ensureWeb() ?: run { showNoWebView(); return }
        if (fromStart) clearHistoryOnFinish = true
        showWebUi(firstTime = fromStart)
        tabs.active.url = url
        urlBar.show(url, null)
        loadingUrl = url
        upgradeGuard.onUserNavigation()
        w.loadUrl(url)
        updateNav()
        if (!cursorMode) w.requestFocus()
    }

    private fun showWebUi(firstTime: Boolean) {
        val w = web ?: return
        showingStart = false
        if (firstTime) { firstLoad = true; w.setBackgroundColor(Theme.BASE); dots.visibility = View.VISIBLE }
        startPage.visibility = View.GONE
        message.visibility = View.GONE
        w.visibility = View.VISIBLE
        showToolbar(true)
    }

    private fun showStart() {
        closeOverlays()
        destroyWeb() // the start page needs no WebView; frees the renderer
        tabs.active.url = null
        tabs.active.title = ""
        savedStates.remove(tabs.active.id)
        showingStart = true
        firstLoad = false
        loading = false
        refreshStart()
        startPage.visibility = View.VISIBLE
        startPage.scrollTo(0, 0)
        message.visibility = View.GONE
        dots.visibility = View.GONE
        progress.setProgress(100)
        urlBar.show(null, null)
        showToolbar(true)
        updateNav()
        updateCursorState()
        if (!cursorMode) startPage.hero.requestFocus()
    }

    private fun reload() {
        val w = web ?: return
        message.visibility = View.GONE
        w.reload()
    }

    private fun navBack() {
        val w = web
        when {
            showingStart -> Unit
            w != null && w.canGoBack() -> { message.visibility = View.GONE; w.goBack() }
            else -> showStart()
        }
    }

    private fun onBackKey() {
        when {
            customView != null -> exitFullscreen()
            closeOverlays() -> Unit
            urlBar.edit.isFocused -> { urlBar.edit.clearFocus(); suggestions.hide(); refocusRoot() }
            !showingStart -> navBack()
            tabs.tabs.size > 1 -> { val t = tabs.active; tabs.close(t.id); activate(tabs.active); hint.show("Tab closed") }
            else -> finish()
        }
    }

    // ================================================================ WebView

    private fun ensureWeb(): WebView? {
        web?.let { return it }
        if (webFailed) return null
        if (simulateNoWebView()) { webFailed = true; return null }
        val w = try {
            WebView(this)
        } catch (t: Throwable) {
            Log.w(TAG, "WebView unavailable", t)
            webFailed = true
            return null
        }
        configure(w)
        content.addView(w, 0, FrameLayout.LayoutParams(-1, -1).apply { topMargin = if (toolbarShown) dp(TOOLBAR_DP) else 0 })
        web = w
        return w
    }

    private fun destroyWeb() {
        val w = web ?: return
        web = null
        content.removeView(w)
        w.stopLoading()
        w.webChromeClient = null
        chrome = null
        w.destroy()
    }

    private fun simulateNoWebView(): Boolean =
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 &&
            intent?.getBooleanExtra("simulateNoWebView", false) == true

    private fun configure(w: WebView) {
        w.noFocusHighlight()
        w.isFocusable = true
        w.isFocusableInTouchMode = true
        w.setBackgroundColor(Theme.BASE)
        w.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
            setGeolocationEnabled(false)
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            textZoom = this@BrowserActivity.textZoom
            userAgentString = if (tabs.active.desktop) DESKTOP_UA else null
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, false)
        }
        w.setDownloadListener { _, _, _, _, _ -> hint.show("Downloads are not supported") }
        w.setFindListener { active, total, _ -> findBar.setCount(active, total) }
        w.webViewClient = Client()
        w.webChromeClient = Chrome().also { chrome = it }
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            val target = UrlResolver.navigationTarget(url)
            if (target == null || !UrlPolicy.isNavigationAllowed(target)) {
                hint.show("This link can't be opened in the browser")
                return true // intent:, market:, tel:, javascript:, ... open nothing external
            }
            if (target != url) { // http -> https, unless the site keeps bouncing back to http
                if (upgradeGuard.isLoop(url, SystemClock.uptimeMillis())) { view.stopLoading(); showError(url, "CLEARTEXT"); return true }
                loadingUrl = target
                view.loadUrl(target)
                return true
            }
            loadingUrl = url
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (view !== web) return
            loading = true
            loadingUrl = url
            committed = false
            urlBar.show(url, null)
            progress.setProgress(5)
            showToolbar(true)
            updateNav()
            updateCursorState()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (view !== web) return
            committed = true
            sslFallback.onCommitted()
            main.removeCallbacks(sslCheck)
            firstLoaded(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (view !== web) return
            loading = false
            upgradeGuard.onFinished(url, SystemClock.uptimeMillis())
            main.removeCallbacks(sslCheck)
            sslFallback.onFinished(failed = !committed && url != "about:blank")?.let { p -> showError(p.url, "SSL:${p.primaryError}"); return }
            if (clearHistoryOnFinish && url != "about:blank") {
                clearHistoryOnFinish = false
                view.clearHistory() // BACK from the first page returns to the start page
            }
            firstLoaded(view)
            if (pendingScroll > 0) { val y = pendingScroll; pendingScroll = 0; view.postDelayed({ view.scrollTo(0, y) }, 300) }
            if (clearGate.shouldRecordHistory(url)) {
                history.record(url, view.title ?: "", System.currentTimeMillis())
                saveHistorySoon()
                scheduleSessionSave()
                tabs.active.url = url
                tabs.active.title = view.title ?: ""
            }
            urlBar.show(url, view.title)
            updateNav()
            updateCursorState()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (view !== web) return
            urlBar.show(url, view.title)
            updateNav()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (view === web && request.isForMainFrame) showError(request.url.toString(), error.description?.toString())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel() // always refuse the bad certificate
            // Only a main-frame failure replaces the page; sub-resources (ads, trackers) are just blocked.
            if (view !== web) return
            if (SslErrorPolicy.shouldShowPageError(error.url, view.url, loadingUrl)) {
                showError(error.url, "SSL:${error.primaryError}")
            } else if (!committed) {
                // Possibly the main frame under an unexpected URL: show it if the page never commits.
                sslFallback.onSuppressed(error.url, error.primaryError, SystemClock.uptimeMillis())
                main.removeCallbacks(sslCheck); main.postDelayed(sslCheck, 8000)
            }
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            if (view === web) {
                val last = view.url
                destroyWeb()
                showError(last ?: "", "CRASH")
            } else {
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
            }
            return true
        }
    }

    private fun firstLoaded(view: WebView) {
        if (!firstLoad) return
        firstLoad = false
        view.setBackgroundColor(Color.WHITE)
        dots.visibility = View.GONE
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            if (view !== web) return
            progress.setProgress(newProgress)
            if (newProgress >= 80) firstLoaded(view)
            val was = loading
            loading = newProgress < 100
            if (was != loading) updateCursorState()
        }

        override fun onReceivedTitle(view: WebView, title: String?) {
            if (view !== web || title == null) return
            urlBar.show(view.url, title)
            view.url?.let { if (clearGate.shouldRecordHistory(it)) history.updateTitle(it, title) }
            tabs.active.title = title
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            if (customView != null) { callback.onCustomViewHidden(); return }
            customView = view
            customCallback = callback
            val host = FrameLayout(this@BrowserActivity).apply {
                setBackgroundColor(Color.BLACK)
                addView(view, FrameLayout.LayoutParams(-1, -1))
            }
            root.addView(host, root.indexOfChild(cursorView), FrameLayout.LayoutParams(-1, -1))
            content.visibility = View.INVISIBLE
            toolbarBox.visibility = View.INVISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onHideCustomView() {
            val v = customView ?: return
            (v.parent as? ViewGroup)?.let { host -> host.removeView(v); root.removeView(host) }
            customView = null
            customCallback = null
            content.visibility = View.VISIBLE
            toolbarBox.visibility = View.VISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>, fileChooserParams: FileChooserParams): Boolean = false
    }

    private fun exitFullscreen() {
        val cb = customCallback
        if (web != null) chrome?.onHideCustomView()
        cb?.onCustomViewHidden()
    }

    private fun showError(url: String, raw: String?) {
        dots.visibility = View.GONE
        progress.setProgress(100)
        loading = false
        val host = try { Uri.parse(url).host } catch (e: Exception) { null } ?: url
        val (title, body) = when {
            raw?.startsWith("SSL") == true -> "Connection isn't secure" to
                "$host was blocked to keep you safe: " + SslErrorPolicy.reason(raw.substringAfter(':', "-1").toIntOrNull() ?: -1) + "."
            raw == "CRASH" -> "This page stopped" to "The page used too much memory and was closed. You can try loading it again."
            raw?.contains("INTERNET_DISCONNECTED") == true -> "You're offline" to "Check the TV's Wi-Fi or Ethernet connection, then try again."
            raw?.contains("NAME_NOT_RESOLVED") == true -> "Site not found" to "$host couldn't be found. Check the address and try again."
            raw?.contains("CLEARTEXT") == true -> "Not a secure page" to "$host doesn't support HTTPS, so it can't be opened here."
            raw?.contains("TIMED_OUT") == true -> "Taking too long" to "$host didn't respond in time."
            else -> "Can't open this page" to "$host couldn't be loaded" + (raw?.let { " ($it)." } ?: ".")
        }
        showingStart = false
        startPage.visibility = View.GONE
        message.show(R.drawable.ic_warn, Theme.TEXT_2, title, body,
            "Try again" to { if (url.isNotEmpty()) navigate(url) else showStart() },
            "Start page" to { showStart() })
        if (!cursorMode) message.firstAction?.requestFocus()
        updateCursorState()
    }

    private fun showNoWebView() {
        showingStart = false
        startPage.visibility = View.GONE
        dots.visibility = View.GONE
        message.show(R.drawable.ic_warn, Theme.TEXT_2, "Web browsing isn't available",
            "This TV's Android System WebView is missing or turned off, so web pages can't be shown. " +
                "Turn it on in Settings › Apps (show system apps), then open Browser again.",
            "Back to start" to { showStart() },
            "Close" to { finish() })
        updateNav()
        if (!cursorMode) message.firstAction?.requestFocus()
    }

    // ================================================================ bookmarks, history, suggestions

    private fun toggleBookmark() {
        val url = currentUrl() ?: return
        if (bookmarks.contains(url)) {
            bookmarks.remove(url); hint.show("Bookmark removed")
        } else if (bookmarks.add(url, web?.title ?: "")) {
            hint.show(if (bookmarks.items.size <= 12) "Added to Favorites" else "Bookmarked")
        } else hint.show("Bookmarks are full")
        saveBookmarks()
        updateNav()
    }

    private fun saveBookmarks() { val s = bookmarks.encode(); io.execute { writeFile("bookmarks.json", s) } }

    private val historySaver = Runnable { val s = history.encode(); io.execute { writeFile("history.json", s) } }
    private fun saveHistorySoon() { main.removeCallbacks(historySaver); main.postDelayed(historySaver, 1500) }

    private fun writeFile(name: String, text: String) {
        try {
            val f = File(filesDir, name); val tmp = File(filesDir, "$name.tmp")
            tmp.writeText(text); if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        } catch (e: Exception) { }
    }

    private fun openBookmarks() {
        closeOverlays()
        enterOverlay()
        sidePanel.showBookmarks(bookmarks.items,
            onOpen = { navigate(it) },
            onMove = { a, b -> bookmarks.move(a, b); saveBookmarks(); openBookmarks() },
            onDelete = { b ->
                confirm(Menus.DELETE_BOOKMARK, b.title, onCancel = { openBookmarks() }) {
                    bookmarks.remove(b.url); saveBookmarks(); updateNav(); openBookmarks()
                }
            })
    }

    private fun openHistory() {
        closeOverlays()
        enterOverlay()
        sidePanel.showHistory(history.daySections(System.currentTimeMillis(), TimeZone.getDefault(), Locale.getDefault()),
            onOpen = { navigate(it) },
            onClear = {
                confirm(Menus.CLEAR_HISTORY, null, onCancel = { openHistory() }) {
                    history.clear(); saveHistorySoon(); refreshStart(); openHistory(); hint.show("History cleared")
                }
            })
    }

    private fun tileMenu(b: Bookmark, index: Int) {
        val n = bookmarks.items.size
        menuFrom(Menus.TILE, b.title, mapOf(
            "open" to { navigate(b.url) },
            "new_tab" to { openInNewTab(b.url) },
            "left" to { if (index > 0) { bookmarks.move(index, index - 1); saveBookmarks(); refreshStart() } },
            "right" to { if (index < n - 1) { bookmarks.move(index, index + 1); saveBookmarks(); refreshStart() } },
            "remove" to {
                main.post {
                    confirm(Menus.REMOVE_FAVORITE, b.title) { bookmarks.remove(b.url); saveBookmarks(); refreshStart(); updateNav() }
                }
            }
        ))
    }

    private fun linkMenu(href: String) {
        menuFrom(Menus.LINK, UrlPolicy.hostOf(href).ifEmpty { "Link" }, mapOf(
            "new_tab" to { openInNewTab(href) },
            "open" to { navigate(href) },
            "copy" to {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Link", href))
                hint.show("Link copied")
            },
            "bookmark" to {
                if (bookmarks.add(href, UrlPolicy.hostOf(href))) { saveBookmarks(); hint.show("Bookmarked") } else hint.show("Already bookmarked")
            }
        ))
    }

    private fun iconFor(e: Menus.Entry): Int = when (e.id) {
        "cancel" -> R.drawable.ic_close
        "confirm", "remove" -> R.drawable.ic_trash
        "new_tab" -> R.drawable.ic_tabs
        "open" -> R.drawable.ic_forward
        "left" -> R.drawable.ic_left
        "right" -> R.drawable.ic_right
        "copy" -> R.drawable.ic_bookmarks
        "bookmark" -> R.drawable.ic_star
        else -> R.drawable.ic_menu
    }

    private fun menuFrom(def: Menus.Def, title: String?, actions: Map<String, () -> Unit>) {
        showContextMenu(title ?: def.title, def.entries.map { e ->
            MenuCard.Item(iconFor(e), e.label, kind = e.kind) { actions[e.id]?.invoke() }
        })
    }

    /** Destructive confirmation: Cancel first and focused; BACK cancels too. */
    private fun confirm(def: Menus.Def, subject: String?, onCancel: () -> Unit = {}, onConfirm: () -> Unit) {
        menuFrom(def, if (subject != null) "${def.title.removeSuffix("?")}: $subject?" else def.title,
            mapOf("cancel" to { main.post(onCancel) }, "confirm" to onConfirm))
    }

    private fun showContextMenu(title: String, items: List<MenuCard.Item>) {
        closeOverlays()
        enterOverlay()
        val c = cursor
        val lp = ctxMenu.layoutParams as FrameLayout.LayoutParams
        val w = dp(300); val h = dp(44) * items.size + dp(56)
        lp.leftMargin = ((c?.x ?: 0f).toInt() + dp(12)).coerceIn(dp(16), root.width - w - dp(16))
        lp.topMargin = ((c?.y ?: 0f).toInt() - dp(20)).coerceIn(dp(16), (root.height - h - dp(16)).coerceAtLeast(dp(16)))
        ctxMenu.layoutParams = lp
        ctxMenu.open(title, items)
    }

    private fun querySuggestions(q: String) {
        if (q.isBlank()) { suggest.cancel(); suggestions.hide(); return }
        val bm = bookmarks.items.map { SuggestionMerger.Local(it.title, it.url) }
        val hist = history.items.map { SuggestionMerger.Local(it.title, it.url) }
        placeSuggestions()
        suggestions.show(SuggestionMerger.merge(q, bm, hist, emptyList(), 6))
        suggest.request(q) { remote ->
            if (urlBar.edit.isFocused && urlBar.edit.text.toString() == q) suggestions.show(SuggestionMerger.merge(q, bm, hist, remote, 6))
        }
    }

    private fun placeSuggestions() {
        val lp = suggestions.layoutParams as FrameLayout.LayoutParams
        val loc = IntArray(2).also { urlBar.getLocationInWindow(it) }
        val rl = IntArray(2).also { root.getLocationInWindow(it) }
        lp.leftMargin = loc[0] - rl[0]
        lp.width = urlBar.width
        suggestions.layoutParams = lp
    }

    private fun pickSuggestion(s: SuggestionMerger.Suggestion) {
        suggest.cancel()
        suggestions.hide()
        urlBar.edit.clearFocus()
        navigate(s.url ?: SearchEngines.urlFor(engine, s.text))
        refocusRoot()
    }

    // ================================================================ menu & overlays

    private fun anyOverlay() = menu.isOpen || ctxMenu.isOpen || sidePanel.isOpen || switcher.isOpen || cursorPage.isOpen || findBar.isOpen

    private fun enterOverlay() {
        cursorView.visibility = View.INVISIBLE
        snapAnim?.cancel()
    }

    private fun overlayClosed(returnFocus: View?) {
        if (anyOverlay()) return
        if (cursorMode) { cursorView.visibility = View.VISIBLE; cursorView.wake(); refocusRoot() } else returnFocus?.requestFocus()
    }

    /** Closes whichever overlay is open; true if one was. */
    private fun closeOverlays(): Boolean {
        var any = false
        if (ctxMenu.isOpen) { ctxMenu.close(); any = true }
        if (menu.isOpen) { menu.close(); any = true }
        if (sidePanel.isOpen) { sidePanel.close(); any = true }
        if (switcher.isOpen) { switcher.close(); any = true }
        if (cursorPage.isOpen) { cursorPage.close(); any = true }
        if (findBar.isOpen) { closeFind(); any = true }
        return any
    }

    private fun openMenu() {
        if (menu.isOpen) { menu.close(); return }
        closeOverlays()
        showToolbar(true)
        enterOverlay()
        val onWeb = !showingStart && web != null
        val items = ArrayList<MenuCard.Item>()
        items += MenuCard.Item(R.drawable.ic_plus, "New tab", value = { "${tabs.tabs.size} of 3" }) {
            if (tabs.canOpen()) { snapshotActive(true); activate(tabs.open(null)!!) } else hint.show("Up to 3 tabs — close one first")
        }
        items += MenuCard.Item(R.drawable.ic_tabs, "Tabs") { openSwitcher() }
        items += MenuCard.Item(R.drawable.ic_bookmarks, "Bookmarks") { openBookmarks() }
        items += MenuCard.Item(R.drawable.ic_history, "History") { openHistory() }
        if (onWeb) items += MenuCard.Item(R.drawable.ic_find, "Find in page") { openFind() }
        items += MenuCard.Item(R.drawable.ic_zoom, "Text size", value = { "$textZoom%" }, adjust = { d -> stepZoom(d) }, keepOpen = true) { stepZoom(1) }
        if (onWeb) items += MenuCard.Item(R.drawable.ic_desktop, "Desktop site", value = { if (tabs.active.desktop) "On" else "Off" }) { toggleDesktop() }
        items += MenuCard.Item(R.drawable.ic_search, "Search engine", value = { engine.label }, adjust = { d -> stepEngine(d) }, keepOpen = true) { stepEngine(1) }
        items += MenuCard.Item(R.drawable.ic_cursor, "Cursor") { openCursorPage() }
        items += MenuCard.Item(R.drawable.ic_reload, "Reopen tabs on start", value = { if (restoreSession) "On" else "Off" }, keepOpen = true) {
            prefs.edit().putBoolean(KEY_RESTORE, !restoreSession).apply()
        }
        items += MenuCard.Item(R.drawable.ic_trash, "Clear browsing data") { main.post { confirmClear(false) } }
        items += MenuCard.Item(R.drawable.ic_trash, "Clear everything incl. bookmarks") { main.post { confirmClear(true) } }
        items += MenuCard.Item(R.drawable.ic_cursor, if (cursorMode) "Switch to focus mode" else "Switch to cursor mode") { toggleMode() }
        items += MenuCard.Item(R.drawable.ic_power, "Close browser") { finish() }
        (menu.layoutParams as FrameLayout.LayoutParams).height = (root.height - dp(TOOLBAR_DP) - dp(10)).coerceAtLeast(dp(200))
        menu.requestLayout()
        menu.open("Browser", items)
    }

    private fun stepZoom(d: Int) {
        val i = ZOOMS.indexOf(textZoom).let { if (it < 0) ZOOMS.indexOf(100) else it }
        val z = ZOOMS[(i + d).coerceIn(0, ZOOMS.size - 1)]
        prefs.edit().putInt(KEY_ZOOM, z).apply()
        web?.settings?.textZoom = z
    }

    private fun stepEngine(d: Int) {
        val all = SearchEngine.values()
        val e = all[Math.floorMod(all.indexOf(engine) + d, all.size)]
        prefs.edit().putString(KEY_ENGINE, e.name).apply()
        startPage.setEngine(e.label)
    }

    private fun toggleDesktop() {
        val t = tabs.active
        t.desktop = !t.desktop
        web?.let { it.settings.userAgentString = if (t.desktop) DESKTOP_UA else null; it.reload() }
        hint.show(if (t.desktop) "Requesting desktop site" else "Requesting mobile site")
    }

    private fun openSwitcher(reopen: Boolean = false) {
        if (!reopen) closeOverlays() else if (switcher.isOpen) switcher.visibility = View.GONE
        snapshotActive(true)
        enterOverlay()
        // Previews come from memory or are reloaded from cacheDir/tabs off-thread (never lost after a trim).
        thumbs.load(tabs.tabs.map { it.id }) { previews ->
            switcher.open(tabs.tabs, tabs.activeIndex, previews, tabs.canOpen(),
                onPick = { i -> switchTo(i) },
                onCloseTab = { t -> closeTab(t) },
                onNew = { switcher.close(); snapshotActive(true); tabs.open(null)?.let { activate(it) } })
        }
    }

    private fun openFind() {
        closeOverlays()
        enterOverlay()
        findBar.open()
    }

    private fun closeFind() {
        web?.clearMatches()
        findBar.close()
        overlayClosed(menuBtn)
    }

    private fun openCursorPage() {
        closeOverlays()
        enterOverlay()
        cursorPage.open(cursorSettings)
    }

    private fun applyCursorSettings(s: CursorSettings) {
        cursorSettings = s
        prefs.edit().putString(KEY_CURSOR, s.encode()).apply()
        cursorView.applySettings(s)
        cursor?.speedFactor = s.speed
    }

    /** Confirmation card (Cancel focused) stating what is cleared and what stays. */
    private fun confirmClear(includeBookmarks: Boolean) {
        val plan = ClearPlan.forOption(includeBookmarks)
        confirm(if (includeBookmarks) Menus.CLEAR_EVERYTHING else Menus.CLEAR_DATA, null) { clearData(plan) }
        hint.show(ClearPlan.confirmText(includeBookmarks), 7000)
    }

    /**
     * Stop everything that could re-record data, leave the page, then clear. The gate keeps a
     * late onPageFinished from writing history/session back.
     */
    private fun clearData(plan: Set<ClearPlan.Item>) {
        clearGate.begin()
        try {
            sessionSaver.cancel()
            main.removeCallbacks(historySaver)
            main.removeCallbacks(sslCheck)
            loadingUrl = null
            web?.let { it.stopLoading(); it.loadUrl("about:blank") }
            BrowsingDataCleaner(this).clearWebData(plan, web)
            if (ClearPlan.Item.HISTORY in plan) { history.clear(); historySaver.run() }
            if (ClearPlan.Item.FAVICONS in plan) loader.clear()
            if (ClearPlan.Item.TAB_THUMBNAILS in plan) thumbs.clearAll()
            if (ClearPlan.Item.TAB_STATES in plan) savedStates.clear()
            if (ClearPlan.Item.OPEN_TABS in plan) {
                tabs = SessionState.afterClear(tabs) // one blank tab; per-tab desktop flag gone
                showStart() // destroys the WebView, so its desktop UA goes with it
            }
            if (ClearPlan.Item.SESSION_FILE in plan) sessionWriter.write(sessionWriter.next(), tabs.encode())
            if (ClearPlan.Item.BOOKMARKS in plan) { bookmarks.reset(DefaultFavorites.list); saveBookmarks() }
        } finally {
            clearGate.end()
        }
        refreshStart()
        updateNav()
        hint.show(if (ClearPlan.Item.BOOKMARKS in plan) "Everything cleared" else "Browsing data cleared")
    }



    private fun toggleMode() {
        cursorMode = !cursorMode
        snapAnim?.cancel()
        if (cursorMode) {
            cursorView.visibility = View.VISIBLE
            cursor?.let { cursorView.moveTo(it.x, it.y) }
            root.requestFocus()
            hint.show("Cursor mode  ·  arrows move the pointer")
        } else {
            cursorView.visibility = View.INVISIBLE
            showToolbar(true)
            when {
                message.visibility == View.VISIBLE -> message.firstAction?.requestFocus()
                showingStart -> startPage.hero.requestFocus()
                else -> reloadBtn.requestFocus()
            }
            hint.show("Focus mode  ·  hold BACK for the pointer")
        }
    }

    // ================================================================ game controllers

    /**
     * Controllers: A = OK (click at the pointer), B = BACK (hold: pointer / focus mode), X = the
     * pointer's long-press (link options), Y = type an address, Start = menu, Select = pointer /
     * focus mode, L1/R1 = back / forward, L2/R2 = page up / down, left stick = analog pointer,
     * right stick = scroll. Overlays (menus, panels) get the plain D-pad behaviour.
     */
    private val pad = GamepadInput(this, object : GamepadInput.Host {
        override fun onPadCommand(command: PadCommand, repeat: Int): Boolean = padCommand(command)
        override fun onPadStick(right: Boolean, x: Float, y: Float): Boolean = !right && padStick(x, y)
    })
    private var stickX = 0f
    private var stickY = 0f
    private var stickLast = 0L
    private val stickFrame = Runnable { stickStep() }

    private fun padCommand(c: PadCommand): Boolean = when (c) {
        PadCommand.SEARCH -> { closeOverlays(); urlBar.focusAndType(); true }
        PadCommand.START -> { if (anyOverlay()) closeOverlays() else openMenu(); true }
        PadCommand.SELECT -> { closeOverlays(); toggleMode(); true }
        PadCommand.OPTIONS -> {
            val c0 = cursor
            if (cursorMode && !anyOverlay() && c0 != null) { longPressAt(c0.x, c0.y); true } else false
        }
        PadCommand.PAGE_PREV -> if (anyOverlay()) false else { if (!showingStart) navBack(); true }
        PadCommand.PAGE_NEXT -> if (anyOverlay()) false else { web?.takeIf { !showingStart && it.canGoForward() }?.goForward(); true }
        PadCommand.SCROLL_UP, PadCommand.SCROLL_DOWN -> if (anyOverlay()) false else {
            val dy = if (c == PadCommand.SCROLL_DOWN) pageStep() else -pageStep()
            showToolbar(dy < 0)
            scrollPage(dy)
            true
        }
        else -> false
    }

    /** Left stick in pointer mode: analog movement (a frame loop runs while it is pushed). */
    private fun padStick(x: Float, y: Float): Boolean {
        if (!cursorMode || anyOverlay() || cursor == null || urlBar.edit.isFocused) { stopStick(); return false }
        val was = StickCursor.active(stickX, stickY)
        stickX = x; stickY = y
        if (StickCursor.active(x, y) && !was) {
            stickLast = SystemClock.uptimeMillis()
            snapAnim?.cancel()
            main.removeCallbacks(stickFrame)
            main.post(stickFrame)
        }
        return true
    }

    private fun stopStick() {
        stickX = 0f; stickY = 0f
        main.removeCallbacks(stickFrame)
    }

    private fun stickStep() {
        val c = cursor ?: return stopStick()
        if (!StickCursor.active(stickX, stickY) || !cursorMode || anyOverlay()) return
        val now = SystemClock.uptimeMillis()
        val m = StickCursor.step(stickX, stickY, (now - stickLast).coerceIn(0L, 50L), resources.displayMetrics.density, cursorSettings.speed)
        stickLast = now
        if (!m.isZero) {
            val edge = StickCursor.edgeScroll(c.y, m.dy, (root.height - 1).toFloat())
            c.nudge(m.dx, m.dy)
            cursorView.wake()
            cursorView.moveTo(c.x, c.y, animate = false)
            hover(c.x, c.y)
            if (c.y < dp(TOOLBAR_DP) * 0.5f) showToolbar(true)
            if (edge != 0f) stickEdge(edge.toInt())
            scheduleHitTest()
        }
        main.postDelayed(stickFrame, 16L)
    }

    /** Pushing past the top or bottom with the stick scrolls the page smoothly (no paging animation). */
    private fun stickEdge(dy: Int) {
        if (dy == 0) return
        if (dy < 0 && !toolbarShown) showToolbar(true)
        if (dy > 0) showToolbar(false)
        if (showingStart) { startPage.scrollBy(0, dy); return }
        val w = web ?: return
        if (w.visibility == View.VISIBLE && w.canScrollVertically(if (dy > 0) 1 else -1)) w.scrollBy(0, dy)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean = pad.motion(ev) || super.dispatchGenericMotionEvent(ev)

    // ================================================================ keys

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean = pad.dispatch(ev, ::handleKey)

    private fun handleKey(ev: KeyEvent): Boolean {
        if ((ev.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return super.dispatchKeyEvent(ev)
        val code = ev.keyCode
        val down = ev.action == KeyEvent.ACTION_DOWN
        when (code) {
            KeyEvent.KEYCODE_BACK -> { handleBack(ev); return true }
            KeyEvent.KEYCODE_MENU -> { if (!down && ev.repeatCount == 0) { if (anyOverlay()) closeOverlays() else openMenu() }; return true }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
            KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                if (web != null && !showingStart) { if (down && ev.repeatCount == 0) mediaKey(code); return true }
            }
        }
        if (cursorMode) cursorView.wake()
        if (anyOverlay() || !cursorMode || customViewNeedsKeys()) return super.dispatchKeyEvent(ev)
        if (urlBar.edit.isFocused && code == KeyEvent.KEYCODE_ENTER) return super.dispatchKeyEvent(ev)
        val c = cursor ?: return super.dispatchKeyEvent(ev)
        val dir = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> Dir.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Dir.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Dir.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Dir.RIGHT
            else -> null
        }
        if (dir != null) {
            if (down) {
                snapAnim?.cancel()
                val s = c.move(dir)
                cursorView.moveTo(s.x, s.y)
                hover(s.x, s.y)
                if (s.y < dp(TOOLBAR_DP) * 0.5f) showToolbar(true)
                if (s.scrollY != 0f) edgeScroll(s.scrollY.toInt())
                scheduleHitTest()
            } else c.release()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (down && ev.repeatCount == 0) okDown()
                else if (down && !longFired && touching) { main.removeCallbacks(longRunnable); longRunnable.run() } // held key repeats
                else if (!down) okUp()
                return true
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> { if (down) { showToolbar(true); scrollPage(-pageStep()) }; return true }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> { if (down) { showToolbar(false); scrollPage(pageStep()) }; return true }
        }
        return super.dispatchKeyEvent(ev)
    }

    private fun customViewNeedsKeys() = false

    private fun handleBack(ev: KeyEvent) {
        if (ev.action == KeyEvent.ACTION_DOWN) {
            if (ev.repeatCount == 0) backLongHandled = false
            else if (!backLongHandled && ev.eventTime - ev.downTime >= LONG_PRESS_MS) {
                backLongHandled = true
                closeOverlays()
                toggleMode()
            }
            return
        }
        if (ev.action == KeyEvent.ACTION_UP) {
            if (backLongHandled) { backLongHandled = false; return }
            if (!ev.isCanceled) onBackKey()
        }
    }

    private fun mediaKey(code: Int) {
        val js = when (code) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> "v.play()"
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> "v.pause()"
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> "v.currentTime+=10"
            KeyEvent.KEYCODE_MEDIA_REWIND -> "v.currentTime-=10"
            else -> "(v.paused?v.play():v.pause())"
        }
        web?.evaluateJavascript("(function(){var v=document.querySelector('video');if(v){$js;}})()", null)
    }

    // ================================================================ virtual mouse

    private val longRunnable = Runnable {
        longFired = true
        if (touching) touch(MotionEvent.ACTION_CANCEL)
        cursorView.press(false)
        cursorView.longPressEnd(true)
        cursor?.let { longPressAt(it.x, it.y) }
    }

    private fun okDown() {
        snapAnim?.cancel()
        longFired = false
        touch(MotionEvent.ACTION_DOWN)
        cursorView.press(true)
        cursorView.longPressStart(LONG_CLICK_MS)
        main.postDelayed(longRunnable, LONG_CLICK_MS)
    }

    private fun okUp() {
        main.removeCallbacks(longRunnable)
        if (longFired) { longFired = false; return }
        cursorView.press(false)
        cursorView.longPressEnd(false)
        if (touching) { touch(MotionEvent.ACTION_UP); cursorView.click() }
    }

    private fun longPressAt(x: Float, y: Float) {
        val w = web
        if (w != null && w.visibility == View.VISIBLE && !showingStart && y >= toolbarBottom()) {
            // Navigation targets come only from WebView's own hit test (never from page JS).
            val r = w.hitTestResult
            when (r.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> offerLink(r.extra)
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val msg = hrefHandler.obtainMessage()
                    w.requestFocusNodeHref(msg)
                }
                else -> hint.show("Hold OK on a link for link options")
            }
            return
        }
        val v = viewAt(root, x, y) { it.isLongClickable }
        if (v != null) v.performLongClick() else hint.show("Nothing to do here")
    }

    private val hrefHandler = Handler(Looper.getMainLooper()) { m -> offerLink(m.data?.getString("url")); true }

    private fun offerLink(href: String?) {
        if (href != null && UrlPolicy.isStorable(href)) linkMenu(href) else hint.show("Hold OK on a link for link options")
    }

    private fun toolbarBottom(): Float = if (toolbarShown) dp(TOOLBAR_DP).toFloat() else 0f

    /** Deepest visible view under (x, y) in root coordinates matching [pred]. */
    private fun viewAt(v: View, x: Float, y: Float, pred: (View) -> Boolean): View? {
        if (v.visibility != View.VISIBLE || v === cursorView || v.alpha < 0.05f) return null
        val loc = IntArray(2).also { v.getLocationInWindow(it) }
        val rl = IntArray(2).also { root.getLocationInWindow(it) }
        val lx = x - (loc[0] - rl[0]); val ly = y - (loc[1] - rl[1])
        if (lx < 0 || ly < 0 || lx >= v.width || ly >= v.height) return null
        if (v is ViewGroup && v !is WebView) {
            for (i in v.childCount - 1 downTo 0) viewAt(v.getChildAt(i), x, y, pred)?.let { return it }
        }
        return if (pred(v)) v else null
    }

    private fun hover(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0)
        e.source = InputDevice.SOURCE_MOUSE
        root.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    private fun touch(action: Int) {
        val c = cursor ?: return
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) { touchDownTime = now; touching = true } else touching = false
        val e = MotionEvent.obtain(touchDownTime, now, action, c.x, c.y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun pageStep(): Int = ((web?.height ?: startPage.height) * 0.8f).toInt()

    private fun edgeScroll(dy: Int) {
        if (dy < 0 && !toolbarShown) { showToolbar(true) }
        if (dy > 0) showToolbar(false)
        edgeUntil = SystemClock.uptimeMillis() + 450
        cursorView.edge(if (dy < 0) -1 else 1)
        scrollPage(dy)
        updateCursorState()
        main.postDelayed({ updateCursorState() }, 500)
    }

    private fun scrollPage(dy: Int) {
        if (showingStart) { startPage.smoothScrollBy(0, dy); return }
        val w = web ?: return
        if (dy == 0 || w.visibility != View.VISIBLE || message.visibility == View.VISIBLE) return
        if (w.canScrollVertically(if (dy > 0) 1 else -1)) {
            scrollAnim?.cancel()
            var done = 0
            scrollAnim = ValueAnimator.ofInt(0, dy).apply {
                duration = 220
                interpolator = DecelerateInterpolator()
                addUpdateListener { val v = it.animatedValue as Int; w.scrollBy(0, v - done); done = v }
                start()
            }
        } else dragScroll(w, dy)
    }

    /** Pages that scroll an inner element: a slow synthetic swipe under the cursor (no fling). */
    private fun dragScroll(w: WebView, dy: Int) {
        val c = cursor ?: return
        val loc = IntArray(2).also { w.getLocationInWindow(it) }
        val rootLoc = IntArray(2).also { root.getLocationInWindow(it) }
        val x = (c.x + rootLoc[0] - loc[0]).coerceIn(1f, w.width - 1f)
        val amount = dy.coerceIn(-w.height / 2, w.height / 2)
        val y0 = w.height / 2f + amount / 2f
        val t0 = SystemClock.uptimeMillis()
        fun send(action: Int, t: Long, y: Float) {
            val e = MotionEvent.obtain(t0, t, action, x, y, 0)
            e.source = InputDevice.SOURCE_TOUCHSCREEN
            w.dispatchTouchEvent(e)
            e.recycle()
        }
        send(MotionEvent.ACTION_DOWN, t0, y0)
        for (i in 1..6) send(MotionEvent.ACTION_MOVE, t0 + i * 16L, y0 - amount * i / 6f)
        send(MotionEvent.ACTION_UP, t0 + 6 * 16L + 200L, y0 - amount)
    }

    // ---------------------------------------------------------------- hit testing, states, magnet

    private val hitNow = Runnable { hitTest(snap = false) }
    private val hitRest = Runnable { hitTest(snap = true) }

    private fun scheduleHitTest() {
        main.removeCallbacks(hitRest)
        main.postDelayed(hitRest, 150)
        val since = SystemClock.uptimeMillis() - lastHitAt
        main.removeCallbacks(hitNow)
        if (since >= 80) hitTest(false) else main.postDelayed(hitNow, 80 - since)
    }

    private fun overWeb(c: CursorModel): WebView? {
        val w = web ?: return null
        if (showingStart || w.visibility != View.VISIBLE || message.visibility == View.VISIBLE) return null
        return if (c.y >= toolbarBottom()) w else null
    }

    private fun hitTest(snap: Boolean) {
        lastHitAt = SystemClock.uptimeMillis()
        val c = cursor ?: return
        val w = overWeb(c)
        if (w == null) { lastHit = HitInfo.EMPTY; updateCursorState(); return }
        @Suppress("DEPRECATION") val scale = w.scale.takeIf { it > 0.05f && it < 20f } ?: resources.displayMetrics.density
        val loc = IntArray(2).also { w.getLocationInWindow(it) }
        val rl = IntArray(2).also { root.getLocationInWindow(it) }
        val ox = (loc[0] - rl[0]).toFloat(); val oy = (loc[1] - rl[1]).toFloat()
        val cssX = (c.x - ox) / scale; val cssY = (c.y - oy) / scale
        val magnet = cursorSettings.magnet
        val radiusCss = SnapMath.radiusDp(magnet) * resources.displayMetrics.density / scale
        val doSnap = snap && magnet != Magnet.OFF
        val seq = ++hitSeq
        val px = c.x; val py = c.y
        w.evaluateJavascript(String.format(Locale.US, HIT_JS, cssX, cssY, if (doSnap) "true" else "false", radiusCss)) { res ->
            if (seq != hitSeq || w !== web) return@evaluateJavascript
            val h = HitInfo.parse(res)
            lastHit = h
            updateCursorState()
            val sx = h.snapX; val sy = h.snapY
            val cur = cursor ?: return@evaluateJavascript
            if (doSnap && sx != null && sy != null && cur.x == px && cur.y == py) startSnap(ox + sx * scale, oy + sy * scale)
        }
    }

    private fun startSnap(tx: Float, ty: Float) {
        snapAnim?.cancel()
        val c = cursor ?: return
        val radius = SnapMath.radiusDp(cursorSettings.magnet) * resources.displayMetrics.density * 1.5f
        val step = 4f * resources.displayMetrics.density
        snapAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            addUpdateListener { a ->
                val o = SnapMath.pull(c.x, c.y, tx, ty, radius, cursorSettings.magnet, step)
                if (o == SnapMath.Offset.ZERO) { a.cancel(); return@addUpdateListener }
                c.nudge(o.dx, o.dy)
                cursorView.moveTo(c.x, c.y)
            }
            start()
        }
    }

    private fun updateCursorState() {
        val c = cursor ?: return
        val atEdge = SystemClock.uptimeMillis() < edgeUntil
        val w = overWeb(c)
        val state = if (w != null) {
            CursorStateMapper.map(lastHit, loading, atEdge, false)
        } else {
            val v = viewAt(root, c.x, c.y) { it.isClickable || it is EditText }
            when {
                atEdge -> CursorState.SCROLL
                v is EditText -> CursorState.TEXT
                v != null && v !== root && v !== toolbarBox && v !== message && v.isEnabled -> CursorState.LINK
                v != null && !v.isEnabled -> CursorState.DISABLED
                loading && !showingStart -> CursorState.BUSY
                else -> CursorState.DEFAULT
            }
        }
        cursorView.setState(state)
    }

    // ================================================================ lifecycle

    override fun onStart() {
        super.onStart()
        web?.let { it.onResume(); it.resumeTimers() }
    }

    override fun onPause() {
        stopStick()
        if (touching) touch(MotionEvent.ACTION_CANCEL)
        main.removeCallbacks(longRunnable)
        super.onPause()
    }

    override fun onStop() {
        saveSessionNow() // while the WebView still exists
        main.removeCallbacks(historySaver); historySaver.run()
        web?.let { it.onPause(); it.pauseTimers() }
        super.onStop()
    }

    override fun onDestroy() {
        if (customView != null) exitFullscreen()
        main.removeCallbacksAndMessages(null) // no late session/history saves after this point
        hrefHandler.removeCallbacksAndMessages(null)
        thumbs.shutdown()
        sessionSaver.cancel()
        suggest.shutdown()
        loader.shutdown()
        destroyWeb()
        io.shutdown() // let queued atomic writes finish; nothing new is accepted
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (TrimPolicy.actionFor(level)) {
            TrimPolicy.Action.NONE -> Unit
            TrimPolicy.Action.DROP_CACHES -> { thumbs.trim(); loader.trimMemory() }
            TrimPolicy.Action.DROP_ALL -> {
                thumbs.trim(); loader.trimMemory()
                savedStates.keys.retainAll(setOf(tabs.active.id)) // inactive tabs keep URLs and reload
            }
        }
    }


    private companion object {
        const val TAG = "Browser"
        const val BUTTON_DP = 44
        const val TOOLBAR_DP = Theme.MARGIN_V + BUTTON_DP + 14
        const val LONG_PRESS_MS = 600L
        const val LONG_CLICK_MS = 450L
        const val THUMB_W = 320
        const val THUMB_H = 180
        const val KEY_ENGINE = "engine"
        const val KEY_ZOOM = "text_zoom"
        const val KEY_RESTORE = "restore_session"
        const val KEY_CURSOR = "cursor"
        val ZOOMS = listOf(80, 90, 100, 110, 125, 150, 175, 200)
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"

        /** Hit-test script (display hints only; never used to navigate). Args: x, y (CSS px), snap, radius. */
        const val HIT_JS = "(function(x,y,snap,r){try{var o={};var e=document.elementFromPoint(x,y);" +
            "if(e){var a=e.closest?e.closest('a[href],button,[role=button],[role=link],input,textarea,select,[onclick],summary,label'):null;" +
            "var t=a||e;o.tag=t.tagName;o.href=(a&&a.href)?String(a.href):'';o.role=(t.getAttribute&&t.getAttribute('role'))||'';" +
            "var ty=(t.type||'').toLowerCase();o.editable=!!(e.isContentEditable||t.tagName=='TEXTAREA'||(t.tagName=='INPUT'&&!/^(button|submit|checkbox|radio|reset|image|file|range|color)$/.test(ty)));" +
            "o.clickable=!!a;try{o.cursor=getComputedStyle(e).cursor;}catch(_){}}" +
            "if(snap){var best=null,bd=r*r,n=0;var c=document.querySelectorAll('a[href],button,input,[role=button],[onclick]');" +
            "for(var i=0;i<c.length&&i<3000&&n<40;i++){var b=c[i].getBoundingClientRect();" +
            "if(b.width<1||b.bottom<y-r||b.top>y+r||b.right<x-r||b.left>x+r)continue;n++;" +
            "if(b.width>r*8||b.height>r*3)continue;var cx=b.left+b.width/2,cy=b.top+b.height/2,dx=cx-x,dy=cy-y,d=dx*dx+dy*dy;" +
            "if(d<bd){bd=d;best={x:cx,y:cy};}}if(best)o.snap=best;}" +
            "return JSON.stringify(o);}catch(err){return '{}';}})(%.1f,%.1f,%s,%.1f)"
    }
}
