package dev.tvlauncher.browser

import android.animation.ValueAnimator
import android.app.Activity
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.tvlauncher.browser.CursorModel.Dir
import dev.tvlauncher.browser.ui.BouncingDots
import dev.tvlauncher.browser.ui.CursorView
import dev.tvlauncher.browser.ui.Hint
import dev.tvlauncher.browser.ui.MenuPanel
import dev.tvlauncher.browser.ui.MessagePanel
import dev.tvlauncher.browser.ui.ProgressLine
import dev.tvlauncher.browser.ui.RoundButton
import dev.tvlauncher.browser.ui.StartPage
import dev.tvlauncher.browser.ui.Theme
import dev.tvlauncher.browser.ui.UrlBar
import dev.tvlauncher.browser.ui.dp

/**
 * Single-WebView TV browser. D-pad drives a virtual mouse cursor ([CursorModel]) by default; MENU
 * or a long-press on BACK switches to focus mode (normal focus traversal). The WebView is created
 * lazily on the first navigation so the start page stays light.
 */
class BrowserActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var stage: LinearLayout
    private lateinit var backBtn: RoundButton
    private lateinit var forwardBtn: RoundButton
    private lateinit var reloadBtn: RoundButton
    private lateinit var homeBtn: RoundButton
    private lateinit var moreBtn: RoundButton
    private lateinit var urlBar: UrlBar
    private lateinit var content: FrameLayout
    private lateinit var progress: ProgressLine
    private lateinit var startPage: StartPage
    private lateinit var message: MessagePanel
    private lateinit var dots: BouncingDots
    private lateinit var menu: MenuPanel
    private lateinit var hint: Hint
    private lateinit var cursorView: CursorView
    private var cursor: CursorModel? = null

    private var web: WebView? = null
    private var webFailed = false
    private var cursorMode = true
    private var showingStart = true
    private var firstLoad = false
    private var clearHistoryOnFinish = false
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var touchDownTime = 0L
    private var touching = false
    private var backLongHandled = false
    private var scrollAnim: ValueAnimator? = null

    private val prefs by lazy { getSharedPreferences("browser", MODE_PRIVATE) }
    private var desktop: Boolean
        get() = prefs.getBoolean(KEY_DESKTOP, false)
        set(v) { prefs.edit().putBoolean(KEY_DESKTOP, v).apply() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        setContentView(root)
        showStart()
        root.post { hint.show("Arrows move the pointer  ·  OK clicks  ·  Hold BACK for focus mode", 4500) }
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(Theme.BASE)
            isFocusable = true
            isFocusableInTouchMode = true
            noFocusHighlight()
        }
        stage = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Theme.MARGIN_H), dp(Theme.MARGIN_V), dp(Theme.MARGIN_H), dp(14))
            clipChildren = false
            clipToPadding = false
        }
        backBtn = RoundButton(this, R.drawable.ic_back, "Back") { navBack() }
        forwardBtn = RoundButton(this, R.drawable.ic_forward, "Forward") { web?.takeIf { it.canGoForward() }?.goForward() }
        reloadBtn = RoundButton(this, R.drawable.ic_reload, "Reload") { reload() }
        homeBtn = RoundButton(this, R.drawable.ic_home, "Home") { showStart() }
        moreBtn = RoundButton(this, R.drawable.ic_more, "More") { openMenu() }
        urlBar = UrlBar(this) { text -> navigate(UrlResolver.resolve(text)); refocusRoot() }
        val size = dp(BUTTON_DP)
        listOf(backBtn, forwardBtn, reloadBtn, homeBtn).forEachIndexed { i, b ->
            bar.addView(b, LinearLayout.LayoutParams(size, size).apply { if (i > 0) marginStart = dp(10) })
        }
        bar.addView(urlBar, LinearLayout.LayoutParams(0, size, 1f).apply { marginStart = dp(18); marginEnd = dp(18) })
        bar.addView(moreBtn, LinearLayout.LayoutParams(size, size))
        stage.addView(bar, LinearLayout.LayoutParams(-1, -2))

        content = FrameLayout(this)
        startPage = StartPage(this, onSearch = { urlBar.edit.requestFocus(); showIme() }, onOpen = { navigate(it) })
        content.addView(startPage, FrameLayout.LayoutParams(-1, -1))
        message = MessagePanel(this).apply { visibility = View.GONE }
        content.addView(message, FrameLayout.LayoutParams(-1, -1))
        dots = BouncingDots(this).apply { visibility = View.GONE }
        content.addView(dots, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        progress = ProgressLine(this)
        content.addView(progress, FrameLayout.LayoutParams(-1, dp(3), Gravity.TOP))
        stage.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))

        menu = MenuPanel(this)
        root.addView(menu, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
            topMargin = dp(Theme.MARGIN_V + BUTTON_DP + 10)
            marginEnd = dp(Theme.MARGIN_H)
        })
        hint = Hint(this)
        root.addView(hint, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(32)
        })
        cursorView = CursorView(this)
        root.addView(cursorView, FrameLayout.LayoutParams(-1, -1))

        root.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = (r - l).toFloat(); val h = (b - t).toFloat()
            val c = cursor
            if (c == null) {
                cursor = CursorModel(w, h, resources.displayMetrics.density).also {
                    cursorView.moveTo(it.x, it.y, animate = false)
                }
            } else c.resize(w, h)
        }
    }

    private fun View.noFocusHighlight() {
        if (Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
    }

    private fun refocusRoot() {
        if (cursorMode) root.requestFocus()
    }

    private fun showIme() {
        urlBar.edit.post {
            getSystemService(android.view.inputmethod.InputMethodManager::class.java)
                .showSoftInput(urlBar.edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun updateNav() {
        val w = web
        backBtn.isEnabled = !showingStart
        forwardBtn.isEnabled = !showingStart && w?.canGoForward() == true
        reloadBtn.isEnabled = !showingStart
    }

    // ---------------------------------------------------------------- navigation

    private fun navigate(url: String) {
        closeMenu()
        if (url == UrlResolver.HOME_PAGE) { showStart(); return }
        val w = ensureWeb() ?: run { showNoWebView(); return }
        if (showingStart) {
            clearHistoryOnFinish = true
            firstLoad = true
            w.setBackgroundColor(Theme.BASE)
        }
        showingStart = false
        startPage.visibility = View.GONE
        message.visibility = View.GONE
        w.visibility = View.VISIBLE
        dots.visibility = if (firstLoad) View.VISIBLE else View.GONE
        urlBar.showUrl(url)
        w.loadUrl(url)
        updateNav()
        if (!cursorMode) w.requestFocus()
    }

    private fun showStart() {
        closeMenu()
        web?.let {
            it.stopLoading()
            it.loadUrl("about:blank") // stops audio/video of the page being left
            it.visibility = View.GONE
        }
        showingStart = true
        firstLoad = false
        startPage.visibility = View.VISIBLE
        message.visibility = View.GONE
        dots.visibility = View.GONE
        progress.setProgress(100)
        urlBar.showUrl(null)
        updateNav()
        if (!cursorMode) startPage.tiles.first().requestFocus()
    }

    private fun reload() {
        val w = web ?: return
        if (message.visibility == View.VISIBLE) {
            message.visibility = View.GONE
            w.reload()
        } else w.reload()
    }

    /** Toolbar Back: page history first, then the start page. */
    private fun navBack() {
        val w = web
        when {
            showingStart -> Unit
            w != null && w.canGoBack() -> { message.visibility = View.GONE; w.goBack() }
            else -> showStart()
        }
    }

    /** BACK key: leave fullscreen, close menu/keyboard, page back, start page, then exit. */
    private fun onBackKey() {
        when {
            customView != null -> exitFullscreen()
            menu.isOpen -> closeMenu()
            urlBar.edit.isFocused -> { urlBar.edit.clearFocus(); refocusRoot() }
            !showingStart -> navBack()
            else -> finish()
        }
    }

    // ---------------------------------------------------------------- WebView

    private fun ensureWeb(): WebView? {
        web?.let { return it }
        if (webFailed) return null
        if (simulateNoWebView()) { webFailed = true; return null }
        val w = try {
            WebView(this)
        } catch (t: Throwable) {
            // No (or a broken) WebView provider: AndroidRuntimeException / MissingWebViewPackageException.
            Log.w(TAG, "WebView unavailable", t)
            webFailed = true
            return null
        }
        configure(w)
        content.addView(w, 0, FrameLayout.LayoutParams(-1, -1))
        web = w
        return w
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
            userAgentString = if (desktop) DESKTOP_UA else null
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(w, false)
        }
        w.setDownloadListener { _, _, _, _, _ -> hint.show("Downloads are not supported") }
        w.webViewClient = Client()
        w.webChromeClient = Chrome()
    }

    private inner class Client : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val url = request.url.toString()
            val target = UrlResolver.navigationTarget(url)
            if (target == null) {
                hint.show("This link can't be opened in the browser")
                return true // intent:, market:, tel:, ... open nothing external
            }
            if (target != url) { view.loadUrl(target); return true } // http -> https
            return false
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (showingStart) return
            urlBar.showUrl(url)
            progress.setProgress(5)
            updateNav()
        }

        override fun onPageCommitVisible(view: WebView, url: String) {
            if (showingStart) return
            firstLoaded(view)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (showingStart) return
            if (clearHistoryOnFinish && url != "about:blank") {
                clearHistoryOnFinish = false
                view.clearHistory() // so BACK from the first page returns to the start page
            }
            firstLoaded(view)
            updateNav()
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            if (showingStart) return
            urlBar.showUrl(url)
            updateNav()
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (request.isForMainFrame && !showingStart) showError(request.url.toString(), error.description?.toString())
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            handler.cancel()
            if (!showingStart) showError(error.url, "SSL")
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            handler.cancel()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            // Low-RAM TVs may kill the renderer; drop this WebView instead of crashing.
            if (view === web) {
                val last = view.url
                content.removeView(view)
                view.destroy()
                web = null
                showError(last ?: "", "CRASH")
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
            if (showingStart) return
            progress.setProgress(newProgress)
            if (newProgress >= 80) firstLoaded(view)
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
            stage.visibility = View.INVISIBLE
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun onHideCustomView() {
            val v = customView ?: return
            (v.parent as? ViewGroup)?.let { host -> host.removeView(v); root.removeView(host) }
            customView = null
            customCallback = null
            stage.visibility = View.VISIBLE
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            callback.invoke(origin, false, false)
        }

        override fun onPermissionRequest(request: PermissionRequest) = request.deny()

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams
        ): Boolean = false
    }

    private fun exitFullscreen() {
        val cb = customCallback
        (web?.webChromeClient as? Chrome)?.onHideCustomView()
        cb?.onCustomViewHidden()
    }

    private fun showError(url: String, raw: String?) {
        dots.visibility = View.GONE
        progress.setProgress(100)
        val host = Uri.parse(url).host ?: url
        val (title, body) = when {
            raw == "SSL" -> "Connection isn't secure" to "$host has a certificate problem, so the page was blocked to keep you safe."
            raw == "CRASH" -> "This page stopped" to "The page used too much memory and was closed. You can try loading it again."
            raw?.contains("INTERNET_DISCONNECTED") == true -> "You're offline" to "Check the TV's Wi-Fi or Ethernet connection, then try again."
            raw?.contains("NAME_NOT_RESOLVED") == true -> "Site not found" to "$host couldn't be found. Check the address and try again."
            raw?.contains("CLEARTEXT") == true -> "Not a secure page" to "$host doesn't support HTTPS, so it can't be opened here."
            raw?.contains("TIMED_OUT") == true -> "Taking too long" to "$host didn't respond in time."
            else -> "Can't open this page" to "$host couldn't be loaded" + (raw?.let { " ($it)." } ?: ".")
        }
        message.show(
            R.drawable.ic_globe, Theme.TEXT_2, title, body,
            "Try again" to { navigate(url) },
            "Start page" to { showStart() }
        )
        if (!cursorMode) message.firstAction?.requestFocus()
    }

    private fun showNoWebView() {
        showingStart = false
        startPage.visibility = View.GONE
        dots.visibility = View.GONE
        message.show(
            R.drawable.ic_globe, Theme.TEXT_2, "Web browsing isn't available",
            "This TV's Android System WebView is missing or turned off, so web pages can't be shown. " +
                "Turn it on in Settings › Apps (show system apps), then open Browser again.",
            "Back to start" to { showStart() },
            "Close" to { finish() }
        )
        updateNav()
        if (!cursorMode) message.firstAction?.requestFocus()
    }

    // ---------------------------------------------------------------- menu

    private fun openMenu() {
        if (menu.isOpen) { closeMenu(); return }
        cursorView.visibility = View.INVISIBLE
        menu.open(
            listOf(
                MenuPanel.Item({ "Desktop site:  " + if (desktop) "On" else "Off" }) { toggleDesktop() },
                MenuPanel.Item({ "Clear browsing data" }) { clearData() },
                MenuPanel.Item({ if (cursorMode) "Switch to focus mode" else "Switch to cursor mode" }) { toggleMode() },
                MenuPanel.Item({ "Close browser" }) { finish() }
            )
        ) { closeMenu() }
    }

    private fun closeMenu() {
        if (!::menu.isInitialized || !menu.isOpen) return
        menu.close()
        if (cursorMode) { cursorView.visibility = View.VISIBLE; refocusRoot() } else moreBtn.requestFocus()
    }

    private fun toggleDesktop() {
        desktop = !desktop
        web?.let {
            it.settings.userAgentString = if (desktop) DESKTOP_UA else null
            if (!showingStart) it.reload()
        }
        hint.show(if (desktop) "Desktop site on" else "Desktop site off")
    }

    private fun clearData() {
        try {
            web?.apply { clearCache(true); clearHistory(); clearFormData() }
            CookieManager.getInstance().apply { removeAllCookies(null); flush() }
            WebStorage.getInstance().deleteAllData()
        } catch (t: Throwable) {
            Log.w(TAG, "clear data", t)
        }
        hint.show("Browsing data cleared")
    }

    private fun toggleMode() {
        cursorMode = !cursorMode
        if (cursorMode) {
            cursorView.visibility = View.VISIBLE
            cursor?.let { cursorView.moveTo(it.x, it.y) }
            root.requestFocus()
            hint.show("Cursor mode  ·  arrows move the pointer")
        } else {
            cursorView.visibility = View.INVISIBLE
            when {
                message.visibility == View.VISIBLE -> message.firstAction?.requestFocus()
                showingStart -> startPage.tiles.first().requestFocus()
                else -> reloadBtn.requestFocus()
            }
            hint.show("Focus mode  ·  arrows move between controls")
        }
    }

    // ---------------------------------------------------------------- keys / virtual mouse

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        // Keys typed by the on-screen keyboard go straight to the focused field.
        if ((ev.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0) return super.dispatchKeyEvent(ev)
        val code = ev.keyCode
        val down = ev.action == KeyEvent.ACTION_DOWN
        when (code) {
            KeyEvent.KEYCODE_BACK -> { handleBack(ev); return true }
            KeyEvent.KEYCODE_MENU -> { if (!down) toggleMode(); return true }
        }
        if (menu.isOpen || !cursorMode) return super.dispatchKeyEvent(ev)
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
                val s = c.move(dir)
                cursorView.moveTo(s.x, s.y)
                hover(s.x, s.y)
                if (s.scrollY != 0f) scrollPage(s.scrollY.toInt())
            } else c.release()
            return true
        }
        when (code) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_BUTTON_A -> {
                if (down && ev.repeatCount == 0) touch(MotionEvent.ACTION_DOWN)
                else if (!down && touching) touch(MotionEvent.ACTION_UP)
                return true // repeats while held let the page see a real long press
            }
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) scrollPage(-pageStep()); return true
            }
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) scrollPage(pageStep()); return true
            }
        }
        return super.dispatchKeyEvent(ev)
    }

    private fun handleBack(ev: KeyEvent) {
        if (ev.action == KeyEvent.ACTION_DOWN) {
            if (ev.repeatCount == 0) backLongHandled = false
            else if (!backLongHandled && ev.eventTime - ev.downTime >= LONG_PRESS_MS) {
                backLongHandled = true
                toggleMode()
            }
            return
        }
        if (ev.action == KeyEvent.ACTION_UP) {
            if (backLongHandled) { backLongHandled = false; return }
            if (!ev.isCanceled) onBackKey()
        }
    }

    private fun hover(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val e = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0)
        e.source = InputDevice.SOURCE_MOUSE
        root.dispatchGenericMotionEvent(e)
        e.recycle()
    }

    /** Synthesizes a finger tap/press at the cursor (down on OK down, up on OK up). */
    private fun touch(action: Int) {
        val c = cursor ?: return
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) { touchDownTime = now; touching = true }
        else touching = false
        cursorView.setPressed2(action == MotionEvent.ACTION_DOWN)
        val e = MotionEvent.obtain(touchDownTime, now, action, c.x, c.y, 0)
        e.source = InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun pageStep(): Int = ((web?.height ?: 0) * 0.8f).toInt()

    private fun scrollPage(dy: Int) {
        val w = web ?: return
        if (showingStart || dy == 0 || w.visibility != View.VISIBLE || message.visibility == View.VISIBLE) return
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

    /** For pages that scroll an inner element: a slow synthetic swipe under the cursor (no fling). */
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

    // ---------------------------------------------------------------- lifecycle

    override fun onStart() {
        super.onStart()
        web?.let { it.onResume(); it.resumeTimers() }
    }

    override fun onPause() {
        if (touching) touch(MotionEvent.ACTION_CANCEL)
        super.onPause()
    }

    override fun onStop() {
        web?.let { it.onPause(); it.pauseTimers() }
        super.onStop()
    }

    override fun onDestroy() {
        if (customView != null) exitFullscreen()
        web?.let {
            content.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        web = null
        super.onDestroy()
    }

    private companion object {
        const val TAG = "Browser"
        const val BUTTON_DP = 44
        const val LONG_PRESS_MS = 600L
        const val KEY_DESKTOP = "desktop_site"
        const val DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36"
    }
}
